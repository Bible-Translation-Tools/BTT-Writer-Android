package com.door43.translationstudio.ui.publish

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.unfoldingword.tools.logger.Logger

class PublishActivity : BaseActivity() {
    private var callingActivity = 0

    private val viewModel: PublishViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //setSupportActionBar(binding.toolbar)
        //supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // validate parameters
        val args = checkNotNull(intent.extras)
        val targetTranslationId = args.getString(
            Translator.EXTRA_TARGET_TRANSLATION_ID,
            null
        )

        if (!viewModel.targetInitialized && !viewModel.sourceInitialized) {
            viewModel.initialize(targetTranslationId)
        }

        if (!viewModel.targetInitialized) {
            Logger.e(
                PublishActivity::class.java.simpleName,
                "A valid target translation id is required. Received $targetTranslationId but the translation could not be found"
            )
            finish()
            return
        }

        if (!viewModel.sourceInitialized) {
            val snack = Snackbar.make(
                findViewById(android.R.id.content),
                R.string.choose_source_translations,
                Snackbar.LENGTH_LONG
            )
            ViewUtil.setSnackBarTextColor(
                snack,
                resources.getColor(R.color.light_primary_text)
            )
            snack.show()
            finish()
        }

        // identify calling activity
        callingActivity = args.getInt(EXTRA_CALLING_ACTIVITY, 0)
        if (callingActivity == 0) {
            Logger.e(
                PublishActivity::class.java.simpleName,
                "you must specify the calling activity"
            )
            finish()
            return
        }

        onBackPressedDispatcher.addCallback {
            onBackPressedHandler()
        }

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PublishScreen(
                        onOpenReview = { item ->
                            openReview(
                                item.targetTranslationId,
                                item.chapterId,
                                item.frameId
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (callingActivity == ACTIVITY_TRANSLATION) {
                // TRICKY: the translation activity is finished after opening the publish activity
                // because we may have to go back and forth and don't want to fill up the stack
                val intent = Intent(this, TargetTranslationActivity::class.java)
                val args = Bundle()
                args.putString(
                    Translator.EXTRA_TARGET_TRANSLATION_ID,
                    viewModel.targetTranslation.id
                )
                intent.putExtras(args)
                startActivity(intent)
            }
            finish()
        }
        return true
    }

    private fun onBackPressedHandler() {
        // TRICKY: the translation activity is finished after opening the publish activity
        // because we may have to go back and forth and don't want to fill up the stack
        if (callingActivity == ACTIVITY_TRANSLATION) {
            val intent = Intent(this, TargetTranslationActivity::class.java)
            val args = Bundle()
            args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, viewModel.targetTranslation.id)
            intent.putExtras(args)
            startActivity(intent)
        }
        finish()
    }

    private fun openReview(targetTranslationId: String, chapterId: String, frameId: String) {
        val intent = Intent(this, TargetTranslationActivity::class.java)
        val args = Bundle()
        args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, targetTranslationId)
        args.putString(Translator.EXTRA_CHAPTER_ID, chapterId)
        args.putString(Translator.EXTRA_FRAME_ID, frameId)
        args.putInt(Translator.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
        intent.putExtras(args)

        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_TARGET_TRANSLATION_ID: String = "extra_target_translation_id"
        const val EXTRA_CALLING_ACTIVITY: String = "extra_calling_activity"
        const val ACTIVITY_HOME: Int = 1001
        const val ACTIVITY_TRANSLATION: Int = 1002
    }
}
