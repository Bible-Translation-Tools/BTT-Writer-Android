package com.door43.translationstudio

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import com.arkivanov.decompose.retainedComponent
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.services.BackupService
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.navigation.DefaultRootComponent
import com.door43.translationstudio.ui.navigation.RootComponent
import com.door43.translationstudio.ui.navigation.RootContent
import com.door43.util.FileUtilities
import org.koin.android.ext.android.inject
import java.io.File

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_OPEN_PROFILE = "open_profile"
    }

    private val directoryProvider: IDirectoryProvider by inject()

    override val isBootActivity: Boolean = true

    private lateinit var root: RootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = retainedComponent { componentContext ->
            DefaultRootComponent(
                componentContext = componentContext,
                onExportToApp = ::exportToApp,
                onShareApp = ::shareApp,
                onExitApp = ::finishAffinity
            )
        }

        startBackupService()
        handleIntent(intent)

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                RootContent(component = root)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.data?.let { root.onDeepLink(it) }

        // Activities that haven't migrated yet (Publish, NewTranslation) re-launch MainActivity
        // with the translation extras to navigate back into the Translate stack child.
        val translationId = intent.getStringExtra(Translator.EXTRA_TARGET_TRANSLATION_ID)
        if (!translationId.isNullOrEmpty()) {
            val mergeConflict = intent.getBooleanExtra(
                Translator.EXTRA_START_WITH_MERGE_FILTER,
                false,
            )
            // Consume the extras so a config change / process restart doesn't re-push Translate.
            intent.removeExtra(Translator.EXTRA_TARGET_TRANSLATION_ID)
            intent.removeExtra(Translator.EXTRA_START_WITH_MERGE_FILTER)
            root.openTranslate(translationId, mergeConflict)
        }

        if (intent.getBooleanExtra(EXTRA_OPEN_PROFILE, false)) {
            intent.removeExtra(EXTRA_OPEN_PROFILE)
            // TODO Check it!!!
            // root.openProfile(false)
        }
    }

    private fun startBackupService() {
        val backupIntent = Intent(baseContext, BackupService::class.java)
        baseContext.startService(backupIntent)
    }

    /**
     * Export app .apk
     */
    private fun shareApp() {
        val pInfo = packageManager.getPackageInfo(packageName, 0)
        pInfo.applicationInfo?.let { info ->
            val apkFile = File(info.publicSourceDir)
            val exportFile = File(
                directoryProvider.sharingDir, info.loadLabel(
                    application.packageManager
                ).toString() + "_" + pInfo.versionName + ".apk"
            )
            FileUtilities.copyFile(apkFile, exportFile)

            shareArchive(exportFile)
        }
    }

    /**
     * Export translation project to an app
     */
    private fun exportToApp(file: File) {
        shareArchive(file)
    }

    private fun shareArchive(file: File) {
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        startActivity(
            Intent.createChooser(intent, getString(R.string.send_to)),
        )
    }
}
