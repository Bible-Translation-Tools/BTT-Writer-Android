package com.door43.translationstudio.ui.translate

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import com.door43.translationstudio.App
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.draft.DraftActivity
import com.door43.translationstudio.ui.profile.LoginDoor43Activity
import com.door43.translationstudio.ui.profile.ProfileActivity
import com.door43.translationstudio.ui.publish.PublishActivity
import com.door43.translationstudio.ui.settings.SettingsActivity
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.util.Timer
import java.util.TimerTask

class TargetTranslationActivity : BaseActivity() {

    private val viewModel: TargetTranslationViewModel by viewModel()

    private var commitTimer = Timer()

    companion object {
        const val SEARCH_SOURCE = "search_source"
        const val RESULT_DO_UPDATE = 42
        private const val COMMIT_INTERVAL = 2 * 60 * 1000L // commit changes every 2 minutes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // validate parameters
        val args = intent.extras
        requireNotNull(args)

        val targetTranslationId = args.getString(
            Translator.EXTRA_TARGET_TRANSLATION_ID,
            null
        )
        val startWithMergeFilter = args.getBoolean(
            Translator.EXTRA_START_WITH_MERGE_FILTER,
            false
        )

        // manual location settings
        val modeIndex = args.getInt(Translator.EXTRA_VIEW_MODE, -1)
        val viewMode = if (modeIndex > 0 && modeIndex < TranslationViewMode.entries.size) {
            TranslationViewMode.entries[modeIndex]
        } else null

        if (!viewModel.initialized) {
            viewModel.initialize(targetTranslationId, viewMode)
        }

        if (!viewModel.initialized) {
            Logger.e(
                this::class.simpleName,
                "A valid target translation id is required. " +
                        "Received $targetTranslationId but the translation could not be found"
            )
            finish()
            return
        }

        // open used source translations by default
        viewModel.onAction(TargetAction.OpenSourceTranslations)

        restartAutoCommitTimer()

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                TargetTranslationScreen(
                    startWithMergeFilter = startWithMergeFilter,
                    onHomeClick = { finish() },
                    onNavigateToDraft = {
                        val intent = Intent(this, DraftActivity::class.java)
                        intent.putExtra(
                            DraftActivity.EXTRA_TARGET_TRANSLATION_ID,
                            viewModel.targetTranslation.id
                        )
                        startActivity(intent)
                    },
                    onProjectPreview = ::goProjectReview,
                    onSettings = {
                        startActivity(Intent(
                            this@TargetTranslationActivity,
                            SettingsActivity::class.java
                        ))
                    },
                    onRestartAutoCommitTimer = ::restartAutoCommitTimer,
                    onUpdateSources = ::onUpdateSources,
                    onExportToApp = ::exportToApp,
                    onLoginClick = ::door43Login,
                    onLogout = ::logout
                )
            }
        }
    }

    /**
     * user has selected to update sources
     */
    private fun onUpdateSources() {
        setResult(RESULT_DO_UPDATE)
        finish()
    }

    /**
     * Restart scheduled translation commits
     */
    fun restartAutoCommitTimer() {
        commitTimer.cancel()
        commitTimer = Timer()
        commitTimer.schedule(object : TimerTask() {
            override fun run() {
                try {
                    viewModel.targetTranslation.commit()
                } catch (e: Exception) {
                    Logger.e(
                        this::class.simpleName,
                        "Failed to commit the latest translation of ${viewModel.targetTranslation.id}",
                        e
                    )
                }
            }
        }, COMMIT_INTERVAL, COMMIT_INTERVAL)
    }

    private fun exportToApp(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${application.packageName}.fileprovider",
            file
        )
        val i = Intent(Intent.ACTION_SEND)
        i.type = "application/zip"
        i.putExtra(Intent.EXTRA_STREAM, uri)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(Intent.createChooser(i, "Send to:"))
    }

    private fun door43Login() {
        val intent = Intent(this, LoginDoor43Activity::class.java)
        startActivity(intent)
    }

    private fun logout() {
        val logoutIntent = Intent(this, ProfileActivity::class.java)
        startActivity(logoutIntent)
        finish()
    }

    private fun goProjectReview() {
        val publishIntent = Intent(
            this@TargetTranslationActivity,
            PublishActivity::class.java
        )
        publishIntent.putExtra(
            PublishActivity.EXTRA_TARGET_TRANSLATION_ID,
            viewModel.targetTranslation.id
        )
        publishIntent.putExtra(
            PublishActivity.EXTRA_CALLING_ACTIVITY,
            PublishActivity.ACTIVITY_TRANSLATION
        )
        startActivity(publishIntent)
        // TRICKY: we may move back and forth between the publisher
        // and translation activities
        // so we finish to avoid filling the stack.
        finish()
    }

    override fun onDestroy() {
        commitTimer.cancel()
        try {
            viewModel.targetTranslation.commit()
        } catch (e: Exception) {
            Logger.e(
                this::class.simpleName,
                "Failed to commit changes before closing translation",
                e
            )
        }
        App.closeKeyboard(this)
        super.onDestroy()
    }
}