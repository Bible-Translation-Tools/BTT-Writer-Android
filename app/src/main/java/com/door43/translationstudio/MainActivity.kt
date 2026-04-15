package com.door43.translationstudio

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.arkivanov.decompose.retainedComponent
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.services.BackupService
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.navigation.DefaultRootComponent
import com.door43.translationstudio.ui.navigation.RootComponent
import com.door43.translationstudio.ui.navigation.RootContent

/**
 * Single-activity host for the Decompose navigation root.
 *
 * M3: LAUNCHER. Hosts the [RootComponent] whose initial child is the Splash component — the
 * library-deploy / crash / migration pipeline now lives there, not in a separate activity. Still
 * extends [BaseActivity] to inherit `isDarkTheme` + crash-redirect listener until M7 absorbs them
 * into the root.
 */
class MainActivity : BaseActivity() {

    override val isBootActivity: Boolean = true

    private lateinit var root: RootComponent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        root = retainedComponent { componentContext ->
            DefaultRootComponent(componentContext)
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
    }

    private fun startBackupService() {
        val backupIntent = Intent(baseContext, BackupService::class.java)
        baseContext.startService(backupIntent)
    }
}
