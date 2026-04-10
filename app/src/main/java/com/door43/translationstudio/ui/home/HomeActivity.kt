package com.door43.translationstudio.ui.home

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.services.BackupService
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.dialogs.DownloadSourcesDialog
import com.door43.translationstudio.ui.profile.LoginDoor43Activity
import com.door43.translationstudio.ui.profile.ProfileActivity
import com.door43.translationstudio.ui.publish.PublishActivity
import com.door43.translationstudio.ui.settings.SettingsActivity
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.unfoldingword.tools.eventbuffer.EventBuffer
import org.unfoldingword.tools.eventbuffer.EventBuffer.OnEventTalker
import java.io.File

class HomeActivity : BaseActivity(),
    EventBuffer.OnEventListener, DialogInterface.OnCancelListener {

    val profile: Profile by inject()
    val translator: Translator by inject()

    private var targetTranslationID: String? = null
    private var updateDialog: UpdateLibraryDialog? = null
    private var backupsRunning = false

    private val viewModel: HomeViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startBackupService()

        handleIntent(intent)

//        val moreButton = findViewById<View>(R.id.action_more) as ImageButton
//        moreButton.setOnClickListener { v ->
//            val moreMenu = PopupMenu(this@HomeActivity, v)
//            ViewUtil.forcePopupMenuIcons(moreMenu)
//            moreMenu.menuInflater.inflate(R.menu.menu_home, moreMenu.menu)
//            moreMenu.setOnMenuItemClickListener { item ->
//                when (item.itemId) {
//                    R.id.action_update -> {
//                        updateDialog = UpdateLibraryDialog().apply {
//                            showDialogFragment(this, UpdateLibraryDialog.TAG)
//                        }
//                        true
//                    }
//                    R.id.action_import -> {
//                        val importDialog = ImportDialog()
//                        showDialogFragment(importDialog, ImportDialog.TAG)
//                        true
//                    }
//                    R.id.action_feedback -> {
//                        val dialog = FeedbackDialogOld()
//                        showDialogFragment(dialog, "feedback-dialog")
//                        true
//                    }
//                    R.id.action_share_apk -> {
//                        viewModel.exportApp()
//                        true
//                    }
//                    R.id.action_log_out -> {
//                        viewModel.logout()
//                        true
//                    }
//                    R.id.action_settings -> {
//                        val intent = Intent(this@HomeActivity, SettingsActivity::class.java)
//                        startActivity(intent)
//                        true
//                    }
//                    else -> false
//                }
//            }
//            moreMenu.show()
//        }

        // open last project when starting the first time
        viewModel.lastOpened?.let {
            reviewTranslation(it.id)
        }

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                HomeScreen(
                    onLogout = ::logout,
                    onSettings = {
                        startActivity(Intent(
                            this@HomeActivity,
                            SettingsActivity::class.java
                        ))
                    },
                    onOpenProject = ::openProject,
                    onShareApp = ::shareApp,
                    onLogin = ::door43Login,
                    onProjectPublish = ::publishProject,
                    onReviewTranslation = ::reviewTranslation,
                    onMergeConflict = ::reviewMergeConflict,
                    onAppExit = { finishAffinity() }
                )
            }
        }

        setupObservers()
    }

    private fun startBackupService() {
        if (!backupsRunning) {
            backupsRunning = true
            val backupIntent = Intent(baseContext, BackupService::class.java)
            baseContext.startService(backupIntent)
        }
    }

    private fun openProject(item: TranslationItem) {
        val intent = Intent(this, TargetTranslationActivity::class.java)
        intent.putExtra(Translator.EXTRA_TARGET_TRANSLATION_ID, item.translation.id)
        startActivity(intent)
    }

    private fun logout() {
        val logoutIntent = Intent(this, ProfileActivity::class.java)
        startActivity(logoutIntent)
        finish()
    }

    private fun shareApp(file: File) {
        if (file.exists()) {
            val u = FileProvider.getUriForFile(
                this,
                "${application.packageName}.fileprovider",
                file
            )
            val i = Intent(Intent.ACTION_SEND)
            i.type = "application/zip"
            i.putExtra(Intent.EXTRA_STREAM, u)
            startActivity(
                Intent.createChooser(i, resources.getString(R.string.send_to))
            )
        } else {
            // TODO Notify user the app could not be exported
        }
    }

    private fun door43Login() {
        val intent = Intent(this, LoginDoor43Activity::class.java)
        startActivity(intent)
    }

    private fun publishProject(translationId: String) {
        val publishIntent = Intent(
            this@HomeActivity,
            PublishActivity::class.java
        )
        publishIntent.putExtra(
            PublishActivity.EXTRA_TARGET_TRANSLATION_ID,
            translationId
        )
        publishIntent.putExtra(
            PublishActivity.EXTRA_CALLING_ACTIVITY,
            PublishActivity.ACTIVITY_HOME
        )
        startActivity(publishIntent)
    }

    private fun reviewTranslation(targetTranslationId: String, mergeConflict: Boolean = false) {
        val intent = Intent(this, TargetTranslationActivity::class.java)
        val args = Bundle()
        args.putString(
            Translator.EXTRA_TARGET_TRANSLATION_ID,
            targetTranslationId
        )
        args.putBoolean(Translator.EXTRA_START_WITH_MERGE_FILTER, mergeConflict)
        intent.putExtras(args)
        startActivity(intent)
    }

    private fun reviewMergeConflict(targetTranslationId: String) {
        reviewTranslation(targetTranslationId, true)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        // check if user is trying to open a tstudio file
        viewModel.onAction(HomeAction.ImportProject(uri))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }


    private fun setupObservers() {
        viewModel.updateSourceResult.observe(this) {
            it?.let { result ->
                if (result.success) {
                    // immediately go to select downloads
                    val message = resources.getString(
                        R.string.update_sources_success,
                        result.addedCount,
                        result.updatedCount
                    )
                    showUpdateResultDialog(
                        message = message,
                        onConfirm = ::selectDownloadSources
                    )
                } else {
                    showUpdateResultDialog(
                        R.string.error,
                        resources.getString(R.string.options_update_failed)
                    )
                }
            }
        }
        viewModel.uploadCatalogResult.observe(this) {
            it?.let { result ->
                if (result.success) {
                    val message = resources.getString(
                        R.string.update_languages_success,
                        result.addedCount
                    )
                    showUpdateResultDialog(
                        message = message
                    )
                } else {
                    showUpdateResultDialog(
                        R.string.error,
                        resources.getString(R.string.options_update_failed)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.lastFocusTargetTranslation = null
    }

    override fun onDestroy() {
        val dialog = supportFragmentManager.findFragmentByTag(UpdateLibraryDialog.TAG)
        if (dialog is OnEventTalker) {
            (dialog as OnEventTalker).eventBuffer.removeOnEventListener(this)
        }
        super.onDestroy()
    }

    override fun onEventBufferEvent(talker: OnEventTalker?, tag: Int, args: Bundle?) {
        if (talker is UpdateLibraryDialog) {
            updateDialog?.dismiss()

            if (!isNetworkAvailable) {
                AlertDialog.Builder(this@HomeActivity, R.style.AppTheme_Dialog)
                    .setTitle(R.string.internet_not_available)
                    .setMessage(R.string.check_network_connection)
                    .setPositiveButton(R.string.dismiss, null)
                    .show()
                return
            }

            if (tag == UpdateLibraryDialog.EVENT_SELECT_DOWNLOAD_SOURCES) {
                selectDownloadSources()
                return
            }

            when (tag) {
                UpdateLibraryDialog.EVENT_UPDATE_LANGUAGES -> {
                    viewModel.updateCatalogs(resources.getString(R.string.updating_languages))
                }
                UpdateLibraryDialog.EVENT_UPDATE_SOURCE -> {
                    viewModel.updateSource(resources.getString(R.string.updating_sources))
                }
                UpdateLibraryDialog.EVENT_DOWNLOAD_INDEX -> {
                    val intent = Intent(this, ImportIndexActivity::class.java)
                    intent.putExtra(ImportIndexActivity.IMPORT_ACTION, ImportIndexActivity.DOWNLOAD_INDEX)
                    startActivity(intent)
                    finishAffinity()
                }
                UpdateLibraryDialog.EVENT_IMPORT_INDEX -> {
                    val intent = Intent(this, ImportIndexActivity::class.java)
                    intent.putExtra(ImportIndexActivity.IMPORT_ACTION, ImportIndexActivity.IMPORT_INDEX)
                    startActivity(intent)
                    finishAffinity()
                }
                UpdateLibraryDialog.EVENT_UPDATE_APP -> {
                    viewModel.checkForLatestRelease()
                }
                else -> viewModel.checkForLatestRelease()
            }
        }
    }

    /**
     * bring up UI to select and download sources
     */
    private fun selectDownloadSources() {
        val ft = supportFragmentManager.beginTransaction()
        val prev = supportFragmentManager.findFragmentByTag(DownloadSourcesDialog.TAG)
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        val dialog = DownloadSourcesDialog()
        dialog.show(ft, DownloadSourcesDialog.TAG)
        return
    }

    override fun onCancel(dialog: DialogInterface) {
        // TODO cancel running tasks
    }

    private fun showUpdateResultDialog(
        titleId: Int = R.string.update_success,
        message: String = resources.getString(R.string.update_success),
        onConfirm: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        val dialog = AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(titleId)
            .setMessage(message)
            .setPositiveButton(
                R.string.dismiss
            ) { _, _ ->
                onConfirm()
            }
            .setOnDismissListener {
                viewModel.clearResults()
                onDismiss()
            }

        dialog.show()
    }

    companion object {
        val TAG: String = HomeActivity::class.java.simpleName
    }
}
