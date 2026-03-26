package com.door43.translationstudio.ui.translate

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.door43.translationstudio.App
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.settings.SettingsActivity
import com.door43.translationstudio.ui.dialogs.BackupDialog
import com.door43.translationstudio.ui.dialogs.FeedbackDialog
import com.door43.translationstudio.ui.dialogs.PrintDialog
import com.door43.translationstudio.ui.draft.DraftActivity
import com.door43.translationstudio.ui.publish.PublishActivity
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.unfoldingword.tools.logger.Logger
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

        val targetTranslationId = args.getString(Translator.EXTRA_TARGET_TRANSLATION_ID, null)

        if (!viewModel.initialized) {
            viewModel.initialize(targetTranslationId)
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

        // manual location settings
        val modeIndex = args.getInt(Translator.EXTRA_VIEW_MODE, -1)
        if (modeIndex > 0 && modeIndex < TranslationViewMode.entries.size) {
            viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.entries[modeIndex]))
        }

        restartAutoCommitTimer()

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                TargetTranslationScreen(
                    onHomeClick = { finish() },
                    onNavigateToDraft = {
                        val intent = Intent(this, DraftActivity::class.java)
                        intent.putExtra(
                            DraftActivity.EXTRA_TARGET_TRANSLATION_ID,
                            viewModel.targetTranslation.id
                        )
                        startActivity(intent)
                    },
                    onProjectPreview = {
                        val publishIntent = Intent(this@TargetTranslationActivity, PublishActivity::class.java)
                        publishIntent.putExtra(PublishActivity.EXTRA_TARGET_TRANSLATION_ID, viewModel.targetTranslation.id)
                        publishIntent.putExtra(PublishActivity.EXTRA_CALLING_ACTIVITY, PublishActivity.ACTIVITY_TRANSLATION)
                        startActivity(publishIntent)
                        // TRICKY: we may move back and forth between the publisher and translation activities
                        // so we finish to avoid filling the stack.
                        finish()
                    },
                    onUploadExport = {
                        val backupFt = supportFragmentManager.beginTransaction()
                        val backupPrev = supportFragmentManager.findFragmentByTag(BackupDialog.TAG)
                        if (backupPrev != null) {
                            backupFt.remove(backupPrev)
                        }
                        backupFt.addToBackStack(null)

                        val backupDialog = BackupDialog()
                        val args = Bundle()
                        args.putString(BackupDialog.ARG_TARGET_TRANSLATION_ID, viewModel.targetTranslation.id)
                        backupDialog.arguments = args
                        backupDialog.show(backupFt, BackupDialog.TAG)
                    },
                    onPrint = {
                        val printFt = supportFragmentManager.beginTransaction()
                        val printPrev = supportFragmentManager.findFragmentByTag("printDialog")
                        if (printPrev != null) {
                            printFt.remove(printPrev)
                        }
                        printFt.addToBackStack(null)

                        val printDialog = PrintDialog()
                        val printArgs = Bundle()
                        printArgs.putString(PrintDialog.ARG_TARGET_TRANSLATION_ID, viewModel.targetTranslation.id)
                        printDialog.arguments = printArgs
                        printDialog.show(printFt, "printDialog")
                    },
                    onFeedback = {
                        val ft = supportFragmentManager.beginTransaction()
                        val prev = supportFragmentManager.findFragmentByTag("bugDialog")
                        if (prev != null) {
                            ft.remove(prev)
                        }
                        ft.addToBackStack(null)

                        val dialog = FeedbackDialog()
                        dialog.show(ft, "bugDialog")
                    },
                    onSettings = {
                        startActivity(Intent(
                            this@TargetTranslationActivity,
                            SettingsActivity::class.java
                        ))
                    },
                    onRestartAutoCommitTimer = ::restartAutoCommitTimer,
                    onUpdateSources = ::onUpdateSources
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