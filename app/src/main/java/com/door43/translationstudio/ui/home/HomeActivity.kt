package com.door43.translationstudio.ui.home

import android.content.ContentResolver
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.App.Companion.restart
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.MergeConflictsHandler.OnMergeConflictListener
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.ActivityHomeBinding
import com.door43.translationstudio.services.BackupService
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.ProfileActivity
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.dialogs.Door43LoginDialog
import com.door43.translationstudio.ui.dialogs.DownloadSourcesDialog
import com.door43.translationstudio.ui.dialogs.FeedbackDialog
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.home.WelcomeFragment.OnCreateNewTargetTranslation
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationActivity
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.translationstudio.ui.viewmodels.HomeViewModel
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.PullTargetTranslation
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.eclipse.jgit.merge.MergeStrategy
import org.unfoldingword.tools.eventbuffer.EventBuffer
import org.unfoldingword.tools.eventbuffer.EventBuffer.OnEventTalker
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : BaseActivity(),
    OnCreateNewTargetTranslation, TargetTranslationListFragment.OnItemClickListener,
    EventBuffer.OnEventListener, DialogInterface.OnCancelListener {

    @Inject lateinit var profile: Profile
    @Inject lateinit var translator: Translator

    private var fragment: Fragment? = null
    private var alertShown = DialogShown.NONE
    private var targetTranslationID: String? = null
    private var updateDialog: UpdateLibraryDialog? = null
    private var progressDialog: ProgressHelper.ProgressDialog? = null
    private var backupsRunning = false

    private lateinit var binding: ActivityHomeBinding
    private lateinit var newTranslationLauncher: ActivityResultLauncher<Intent>
    private lateinit var translationViewRequestLauncher: ActivityResultLauncher<Intent>

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startBackupService()
        setupObservers()

        newTranslationLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            onNewTranslationRequest(result)
        }

        translationViewRequestLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            onTranslationViewRequest(result)
        }

        progressDialog = ProgressHelper.newInstance(baseContext, R.string.loading, false)

        with(binding) {
            if (savedInstanceState != null) {
                // use current fragment
                fragment = supportFragmentManager.findFragmentById(fragmentContainer.id)
            } else {
//                if (translator.targetTranslationIDs.isNotEmpty()) {
//                    fragment = TargetTranslationListFragment()
//                    fragment?.setArguments(intent.extras)
//                } else {
//                    fragment = WelcomeFragment()
//                    fragment?.setArguments(intent.extras)
//                }
//
//                supportFragmentManager.beginTransaction().add(R.id.fragment_container, fragment!!)
//                    .commit()
            }

            addTargetTranslationButton.setOnClickListener { onCreateNewTargetTranslation() }
            logoutButton.setOnClickListener { viewModel.logout() }
        }

        val moreButton = findViewById<View>(R.id.action_more) as ImageButton
        moreButton.setOnClickListener { v ->
            val moreMenu = PopupMenu(this@HomeActivity, v)
            ViewUtil.forcePopupMenuIcons(moreMenu)
            moreMenu.menuInflater.inflate(R.menu.menu_home, moreMenu.menu)
            moreMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_update -> {
                        updateDialog = UpdateLibraryDialog().apply {
                            showDialogFragment(this, UpdateLibraryDialog.TAG)
                        }
                        true
                    }
                    R.id.action_import -> {
                        val importDialog = ImportDialog()
                        showDialogFragment(importDialog, ImportDialog.TAG)
                        true
                    }
                    R.id.action_feedback -> {
                        val dialog = FeedbackDialog()
                        showDialogFragment(dialog, "feedback-dialog")
                        true
                    }
                    R.id.action_share_apk -> {
                        viewModel.exportApp()
                        true
                    }
                    R.id.action_log_out -> {
                        doLogout()
                        true
                    }
                    R.id.action_settings -> {
                        val intent = Intent(this@HomeActivity, SettingsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
            moreMenu.show()
        }

        // check if user is trying to open a tstudio file
        if (intent != null) {
            val action = intent.action
            val scheme = intent.scheme
            val contentUri = intent.data

            if (action != null && scheme != null && contentUri != null) {
                if (action.compareTo(Intent.ACTION_VIEW) == 0 || action.compareTo(Intent.ACTION_DEFAULT) == 0) {
                    Logger.i(TAG, "Opening: $contentUri")
                    if (scheme.compareTo(ContentResolver.SCHEME_CONTENT) == 0) {
                        importFromUri(contentUri)
                        return
                    } else if (scheme.compareTo(ContentResolver.SCHEME_FILE) == 0) {
                        importFromUri(contentUri)
                        return
                    }
                }
            }
        }

        // open last project when starting the first time
        if (savedInstanceState == null) {
            val targetTranslation = viewModel.lastOpened
            if (targetTranslation != null) {
                onItemClick(targetTranslation)
                return
            }
        } else {
            alertShown = DialogShown.fromInt(
                savedInstanceState.getInt(STATE_DIALOG_SHOWN, INVALID),
                DialogShown.NONE
            )
            targetTranslationID = savedInstanceState.getString(
                STATE_DIALOG_TRANSLATION_ID,
                null
            )
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackPressedHandler()
            }
        })
    }

    private fun startBackupService() {
        if (!backupsRunning) {
            backupsRunning = true
            val backupIntent = Intent(baseContext, BackupService::class.java)
            baseContext.startService(backupIntent)
        }
    }

    private fun setupObservers() {
        viewModel.progress.observe(this) {
            if (it != null) {
                progressDialog?.show()
                progressDialog?.setProgress(it.progress)
                progressDialog?.setMessage(it.message)
                progressDialog?.setMax(it.max)
            } else {
                progressDialog?.dismiss()
            }
        }
        viewModel.translations.observe(this) {
            it?.let { translations ->
                if (translations.isNotEmpty()) {
                    fragment = TargetTranslationListFragment()
                    fragment?.setArguments(intent.extras)
                } else {
                    fragment = WelcomeFragment()
                    fragment?.setArguments(intent.extras)
                }
                supportFragmentManager.beginTransaction().add(R.id.fragment_container, fragment!!)
                    .commit()
            }
        }
        viewModel.loggedOut.observe(this) {
            if (it == true) doLogout()
        }
        viewModel.exportedApp.observe(this) {
            if (it != null) {
                if (it.exists()) {
                    val u = FileProvider.getUriForFile(
                        this,
                        "${application.packageName}.fileprovider",
                        it
                    )
                    val i = Intent(Intent.ACTION_SEND)
                    i.setType("application/zip")
                    i.putExtra(Intent.EXTRA_STREAM, u)
                    startActivity(
                        Intent.createChooser(i, resources.getString(R.string.send_to))
                    )
                } else {
                    // TODO Notify user the app could not be exported
                }
            }
        }
        viewModel.examineImportsResult.observe(this) {
            it?.let { result ->
                if (result.success) {
                    displayImportVerification()
                } else {
                    Logger.e(
                        TAG,
                        "Could not process content URI: " + result.contentUri.toString()
                    )
                    showImportResults(result.contentUri.toString(), null, false)
                    viewModel.cleanupExamineImportResult()
                }
            }
        }
        viewModel.latestRelease.observe(this) {
            it?.let { result ->
                if (result.release == null) {
                    AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.check_for_updates)
                        .setMessage(R.string.have_latest_app_update)
                        .setPositiveButton(R.string.label_ok, null)
                        .show()
                } else { // have newer
                    promptUserToDownloadLatestVersion(
                        result.release
                    )
                }
            }
        }
        viewModel.importResult.observe(this) {
            it?.let { result ->
                val examineImportsResult = viewModel.examineImportsResult.value
                val hand = Handler(Looper.getMainLooper())
                hand.post {
                    val success = result.isSuccess
                    if (success && result.mergeConflict) {
                        MergeConflictsHandler.backgroundTestForConflictedChunks(
                            result.importedSlug,
                            translator,
                            object : OnMergeConflictListener {
                                override fun onNoMergeConflict(targetTranslationId: String) {
                                    showImportResults(
                                        examineImportsResult?.contentUri.toString(),
                                        examineImportsResult?.projectsFound,
                                        success
                                    )
                                }
                                override fun onMergeConflict(targetTranslationId: String) {
                                    showMergeConflict(targetTranslationId)
                                }
                            })
                    } else {
                        showImportResults(
                            examineImportsResult?.contentUri.toString(),
                            examineImportsResult?.projectsFound,
                            success
                        )
                    }
                }
                viewModel.cleanupExamineImportResult()
            }
        }
        viewModel.pullTranslationResult.observe(this) {
            it?.let { result ->
                val status = result.status
                if (status == PullTargetTranslation.Status.UP_TO_DATE || status == PullTargetTranslation.Status.UNKNOWN) {
                    AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.success)
                        .setMessage(R.string.success_translation_update)
                        .setPositiveButton(R.string.dismiss, null)
                        .show()
                } else if (status == PullTargetTranslation.Status.AUTH_FAILURE) {
                    // regenerate ssh keys
                    // if we have already tried ask the user if they would like to try again
                    if (viewModel.hasSSHKeys()) {
                        showAuthFailure()
                    } else {
                        viewModel.registerSSHKeys(false)
                    }
                } else if (status == PullTargetTranslation.Status.MERGE_CONFLICTS) {
                    AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.success)
                        .setMessage(R.string.success_translation_update_with_conflicts)
                        .setNeutralButton(R.string.review) { _, _ ->
                            val intent = Intent(this, TargetTranslationActivity::class.java)
                            intent.putExtra(
                                Translator.EXTRA_TARGET_TRANSLATION_ID,
                                viewModel.notifyTargetTranslationWithUpdates
                            )
                            translationViewRequestLauncher.launch(intent)
                        }
                        .show()
                } else {
                    notifyTranslationUpdateFailed()
                }
                viewModel.notifyTargetTranslationWithUpdates = null
            }
        }
        viewModel.registeredSSHKeys.observe(this) {
            it?.let { registered ->
                if (registered && viewModel.notifyTargetTranslationWithUpdates != null) {
                    Logger.i(this.javaClass.name, "SSH keys were registered with the server")
                    // try to pull again
                    downloadTargetTranslationUpdates()
                } else {
                    notifyTranslationUpdateFailed()
                }
            }
        }
        viewModel.indexDownloaded.observe(this) {
            it?.let { success ->
                if (success) {
                    showUpdateResultDialog(
                        message = resources.getString(R.string.download_index_success),
                        onConfirm = ::restart
                    )
                } else {
                    showUpdateResultDialog(
                        R.string.error,
                        resources.getString(R.string.options_update_failed)
                    )
                }
            }
        }
        viewModel.updateSourceResult.observe(this) {
            it?.let { result ->
                if (result.success) {
                    // immediately go to select downloads
                    val message = String.format(
                        resources.getString(R.string.update_sources_success),
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
                    val message = String.format(
                        resources.getString(R.string.update_languages_success),
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

    /**
     * do logout activity
     */
    private fun doLogout() {
        val logoutIntent = Intent(this@HomeActivity, ProfileActivity::class.java)
        startActivity(logoutIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()

        viewModel.loadTranslations()
        viewModel.lastFocusTargetTranslation = null

        val userText = resources.getString(R.string.current_user, profile.currentUser)
        binding.currentUser.text = userText

        val numTranslations = viewModel.translations.value?.size ?: 0
        if (numTranslations > 0 && fragment is WelcomeFragment) {
            // display target translations list
            fragment = TargetTranslationListFragment().apply {
                setArguments(intent.extras)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, this)
                    .commit()
            }

            /*val hand = Handler(Looper.getMainLooper())
            hand.post { // delay to load list after fragment initializes
                (fragment as TargetTranslationListFragment).reloadList()
            }*/
        } else if (numTranslations == 0 && fragment is TargetTranslationListFragment) {
            // display welcome screen
            fragment = WelcomeFragment().apply {
                setArguments(intent.extras)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, this)
                    .commit()
            }
        } else if (numTranslations > 0 && fragment is TargetTranslationListFragment) {
            // reload list
            (fragment as TargetTranslationListFragment).reloadList()
        }

        if (viewModel.notifyTargetTranslationWithUpdates != null) {
            showTranslationUpdatePrompt()
        }

        restoreDialogs()
    }

    /**
     * Restores dialogs
     */
    private fun restoreDialogs() {
        // restore alert dialogs
        when (alertShown) {
            DialogShown.IMPORT_VERIFICATION -> displayImportVerification()
            DialogShown.MERGE_CONFLICT -> showMergeConflict(targetTranslationID)
            DialogShown.NONE -> {}
            else -> Logger.e(TAG, "Unsupported restore dialog: $alertShown")
        }
        // re-connect to dialog fragments
        val dialog = supportFragmentManager.findFragmentByTag(UpdateLibraryDialog.TAG)
        if (dialog is OnEventTalker) {
            (dialog as OnEventTalker).eventBuffer.addOnEventListener(this)
        }
    }

    /**
     * Displays a dialog while replacing any duplicate dialog
     *
     * @param dialog
     * @param tag
     */
    private fun showDialogFragment(dialog: DialogFragment, tag: String) {
        var ft = supportFragmentManager.beginTransaction()
        val prev = supportFragmentManager.findFragmentByTag(tag)
        if (prev != null) {
            ft.remove(prev)
            // TODO: 10/7/16 I don't think we need this
            ft.commit()
            ft = supportFragmentManager.beginTransaction()
        }
        ft.addToBackStack(null)
        // attach to any available event buffers
        if (dialog is OnEventTalker) {
            (dialog as OnEventTalker).eventBuffer.addOnEventListener(this)
        }
        dialog.show(ft, tag)
    }

    /**
     * let user know there was a merge conflict
     * @param targetTranslationID
     */
    fun showMergeConflict(targetTranslationID: String?) {
        alertShown = DialogShown.MERGE_CONFLICT
        this.targetTranslationID = targetTranslationID
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.merge_conflict_title).setMessage(R.string.import_merge_conflict)
            .setPositiveButton(
                R.string.label_ok
            ) { _, _ ->
                alertShown = DialogShown.NONE
                doManualMerge(this.targetTranslationID)
            }.show()
    }

    /**
     * open review mode to let user resolve conflict
     */
    fun doManualMerge(mTargetTranslationID: String?) {
        // ask parent activity to navigate to a new activity
        val intent = Intent(this, TargetTranslationActivity::class.java)
        val args = Bundle()
        args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, mTargetTranslationID)
        args.putBoolean(Translator.EXTRA_START_WITH_MERGE_FILTER, true)
        args.putInt(Translator.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
        intent.putExtras(args)
        translationViewRequestLauncher.launch(intent)
    }

    private fun showAuthFailure() {
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.error).setMessage(R.string.auth_failure_retry)
            .setPositiveButton(
                R.string.yes
            ) { _, _ ->
                alertShown = DialogShown.NONE
                viewModel.registerSSHKeys(true)
            }
            .setNegativeButton(
                R.string.no
            ) { _, _ ->
                alertShown = DialogShown.NONE
                notifyTranslationUpdateFailed()
            }.show()
    }

    private fun notifyTranslationUpdateFailed() {
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.error)
            .setMessage(R.string.update_failed)
            .setNeutralButton(R.string.dismiss, null)
            .show()
    }

    /**
     * display the final import Results.
     */
    private fun showImportResults(projectPath: String?, projectNames: String?, success: Boolean) {
        alertShown = DialogShown.IMPORT_RESULTS
        val message: String
        if (success) {
            val format = resources.getString(R.string.import_project_success)
            message = String.format(format, projectNames, projectPath)
        } else {
            val format = resources.getString(R.string.import_failed)
            message = format + "\n" + projectPath
        }

        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(if (success) R.string.title_import_success else R.string.title_import_failed)
            .setMessage(message)
            .setPositiveButton(
                R.string.label_ok
            ) { _, _ ->
                alertShown = DialogShown.NONE
                viewModel.cleanupExamineImportResult()
                this@HomeActivity.finish()
            }
            .show()
    }

    /**
     * begin the uri import
     * @param contentUri
     */
    private fun importFromUri(contentUri: Uri) {
        viewModel.examineImportsForCollisions(contentUri)
    }

    /**
     * show dialog to verify that we want to import, restore or cancel.
     */
    private fun displayImportVerification() {
        viewModel.examineImportsResult.value?.let { result ->
            alertShown = DialogShown.IMPORT_VERIFICATION
            val dlg = AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            dlg.setTitle(R.string.label_import)
                .setMessage(
                    String.format(
                        resources.getString(R.string.confirm_import_target_translation),
                        result.projectsFound
                    )
                )
                .setNegativeButton(
                    R.string.title_cancel
                ) { _, _ ->
                    alertShown = DialogShown.NONE
                    viewModel.cleanupExamineImportResult()
                    this@HomeActivity.finish()
                }
                .setPositiveButton(
                    R.string.label_restore
                ) { _, _ ->
                    alertShown = DialogShown.NONE
                    doArchiveImport(true)
                }

            if (result.alreadyPresent) { // add merge option
                dlg.setNeutralButton(
                    R.string.label_import
                ) { dialog, _ ->
                    alertShown = DialogShown.NONE
                    doArchiveImport(false)
                    dialog.dismiss()
                }
            }
            dlg.show()
        }
    }

    /**
     * import specified file
     * @param overwrite
     */
    private fun doArchiveImport(overwrite: Boolean) {
        viewModel.examineImportsResult.value?.let { result ->
            result.projectsFolder?.let {
                viewModel.importProjects(it, overwrite)
            }
        }
    }

    fun onBackPressedHandler() {
        // display confirmation before closing the app
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setMessage(R.string.exit_confirmation)
            .setPositiveButton(
                R.string.yes
            ) { _, _ -> finishAffinity() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    /**
     * prompt user that project has changed
     */
    private fun showTranslationUpdatePrompt() {
        val translationId = viewModel.notifyTargetTranslationWithUpdates
        val item = viewModel.findTranslationItem(translationId)

        item?.let { translationItem ->
            val message = String.format(
                resources.getString(R.string.merge_request),
                translationItem.formattedProjectName, translationItem.translation.targetLanguageName
            )

            AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(R.string.change_detected)
                .setMessage(message)
                .setPositiveButton(
                    R.string.yes
                ) { _, _ ->
                    alertShown = DialogShown.NONE
                    downloadTargetTranslationUpdates()
                }
                .setNegativeButton(
                    R.string.no
                ) { _, _ ->
                    viewModel.notifyTargetTranslationWithUpdates = null
                    alertShown = DialogShown.NONE
                }
                .show()
        }
    }

    /**
     * Updates a single target translation
     */
    private fun downloadTargetTranslationUpdates() {
        if (isNetworkAvailable) {
            if (!viewModel.loggedIn) {
                val dialog = Door43LoginDialog()
                showDialogFragment(dialog, Door43LoginDialog.TAG)
                return
            }
            viewModel.pullTargetTranslation(MergeStrategy.RECURSIVE)
        } else {
            val snack = Snackbar.make(
                findViewById(android.R.id.content),
                R.string.internet_not_available,
                Snackbar.LENGTH_LONG
            )
            ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
            snack.show()
        }
    }

    override fun onCreateNewTargetTranslation() {
        val intent = Intent(this, NewTargetTranslationActivity::class.java)
        newTranslationLauncher.launch(intent)
    }

    override fun onItemClick(item: TranslationItem) {
        // validate project and target language

        val project = item.project
        val language = item.translation.targetLanguage

        if (language == null) {
            val snack = Snackbar.make(
                findViewById(android.R.id.content),
                R.string.missing_source,
                Snackbar.LENGTH_LONG
            )
            snack.setAction(R.string.check_for_updates) {
                updateDialog = UpdateLibraryDialog().apply {
                    showDialogFragment(this, UpdateLibraryDialog.TAG)
                }
            }
            snack.setActionTextColor(resources.getColor(R.color.light_primary_text))
            ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
            snack.show()
        } else {
            val intent = Intent(this, TargetTranslationActivity::class.java)
            intent.putExtra(Translator.EXTRA_TARGET_TRANSLATION_ID, item.translation.id)
            translationViewRequestLauncher.launch(intent)
        }
    }

    fun loadTranslations() {
        viewModel.loadTranslations()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_DIALOG_SHOWN, alertShown.value)
        outState.putString(STATE_DIALOG_TRANSLATION_ID, targetTranslationID)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        progressDialog?.dismiss()
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
                    viewModel.downloadIndex()
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

    private fun onNewTranslationRequest(result: ActivityResult) {
        if (RESULT_OK == result.resultCode) {
            if (fragment is WelcomeFragment) {
                // display target translations list
                fragment = TargetTranslationListFragment().apply {
                    setArguments(intent.extras)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, this).commit()
                }
            } else {
                (fragment as? TargetTranslationListFragment?)?.reloadList()
            }

            val intent = Intent(this, TargetTranslationActivity::class.java)
            intent.putExtra(
                Translator.EXTRA_TARGET_TRANSLATION_ID,
                result.data!!.getStringExtra(NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID)
            )
            translationViewRequestLauncher.launch(intent)
        } else if (NewTargetTranslationActivity.RESULT_DUPLICATE == result.resultCode) {
            // display duplicate notice to user
            val targetTranslationId = result.data?.getStringExtra(
                NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID
            )
            val existingTranslation = viewModel.getTargetTranslation(targetTranslationId)
            if (existingTranslation != null) {
                val project = viewModel.getProject(existingTranslation)

                val snack = Snackbar.make(
                    findViewById(android.R.id.content), String.format(
                        resources.getString(R.string.duplicate_target_translation),
                        project.name,
                        existingTranslation.targetLanguageName
                    ), Snackbar.LENGTH_LONG
                )
                ViewUtil.setSnackBarTextColor(
                    snack,
                    resources.getColor(R.color.light_primary_text)
                )
                snack.show()
            }
        } else if (NewTargetTranslationActivity.RESULT_ERROR == result.resultCode) {
            val snack = Snackbar.make(
                findViewById(android.R.id.content),
                resources.getString(R.string.error),
                Snackbar.LENGTH_LONG
            )
            ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
            snack.show()
        }
    }

    private fun onTranslationViewRequest(result: ActivityResult) {
        if (TargetTranslationActivity.RESULT_DO_UPDATE == result.resultCode) {
            viewModel.updateSource(resources.getString(R.string.updating_languages))
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        // TODO cancel running tasks
    }

    private fun showUpdateResultDialog(
        titleId: Int = R.string.update_success,
        message: String = resources.getString(R.string.update_success),
        onConfirm: () -> Unit = {}
    ) {
        val dialog = AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(titleId)
            .setMessage(message)
            .setPositiveButton(
                R.string.dismiss
            ) { _, _ ->
                onConfirm()
            }

        dialog.show()
    }

    /**
     * ask the user if they want to download the latest version
     */
    private fun promptUserToDownloadLatestVersion(
        release: CheckForLatestRelease.Release
    ) {
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.apk_update_available)
            .setMessage(R.string.download_latest_apk)
            .setPositiveButton(R.string.label_ok) { _, _ ->
                viewModel.downloadLatestRelease(release)
            }
            .setNegativeButton(R.string.title_cancel, null)
            .show()
    }

    /**
     * for keeping track if dialog is being shown for orientation changes
     */
    enum class DialogShown {
        NONE,
        IMPORT_VERIFICATION,
        OPEN_LIBRARY,
        IMPORT_RESULTS,
        MERGE_CONFLICT;

        val value: Int
            get() = this.ordinal

        companion object {
            fun fromInt(ordinal: Int, defaultValue: DialogShown): DialogShown {
                if (ordinal > 0 && ordinal < entries.size) {
                    return entries[ordinal]
                }
                return defaultValue
            }
        }
    }

    companion object {
        val TAG: String = HomeActivity::class.java.simpleName
        const val STATE_DIALOG_SHOWN: String = "state_dialog_shown"
        const val STATE_DIALOG_TRANSLATION_ID: String = "state_dialog_translationID"
        const val INVALID: Int = -1
    }
}
