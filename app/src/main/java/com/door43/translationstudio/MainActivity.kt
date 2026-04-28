package com.door43.translationstudio

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.retainedComponent
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.services.BackupService
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.navigation.DefaultRootComponent
import com.door43.translationstudio.ui.navigation.RootComponent
import com.door43.translationstudio.ui.navigation.RootContent
import org.koin.android.ext.android.inject

class MainActivity : BaseActivity() {

    companion object {
        const val EXTRA_OPEN_PROFILE = "open_profile"
    }

    private val directoryProvider: IDirectoryProvider by inject()

    override val isBootActivity: Boolean = true

    private lateinit var root: RootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startBackupService()
        handleIntent(intent)

        root = retainedComponent { componentContext ->
            DefaultRootComponent(
                componentContext = componentContext,
                onExitApp = ::finishAffinity
            )
        }

        setContent {
            val currentTheme by root.currentTheme.collectAsStateWithLifecycle()

            val lightValue = resources.getString(R.string.theme_value_light)
            val darkValue = resources.getString(R.string.theme_value_dark)
            val isDark = when (currentTheme) {
                lightValue -> false
                darkValue -> true
                else -> isSystemInDarkTheme()
            }

            AppTheme(darkTheme = isDark) {
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
}
