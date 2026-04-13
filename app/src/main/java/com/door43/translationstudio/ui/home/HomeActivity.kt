package com.door43.translationstudio.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.services.BackupService
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.profile.LoginDoor43Activity
import com.door43.translationstudio.ui.profile.ProfileActivity
import com.door43.translationstudio.ui.publish.PublishActivity
import com.door43.translationstudio.ui.settings.SettingsActivity
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class HomeActivity : BaseActivity() {

    val profile: Profile by inject()
    val translator: Translator by inject()

    private var backupsRunning = false

    private val viewModel: HomeViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startBackupService()

        handleIntent(intent)

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
                    onShareApp = ::shareApp,
                    onLogin = ::door43Login,
                    onProjectPublish = ::publishProject,
                    onMergeConflict = ::reviewMergeConflict,
                    onAppExit = { finishAffinity() }
                )
            }
        }
    }

    private fun startBackupService() {
        if (!backupsRunning) {
            backupsRunning = true
            val backupIntent = Intent(baseContext, BackupService::class.java)
            baseContext.startService(backupIntent)
        }
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

    override fun onResume() {
        super.onResume()
    }

    companion object {
        val TAG: String = HomeActivity::class.java.simpleName
    }
}
