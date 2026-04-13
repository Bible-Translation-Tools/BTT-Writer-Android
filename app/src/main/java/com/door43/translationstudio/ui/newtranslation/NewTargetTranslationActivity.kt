package com.door43.translationstudio.ui.newtranslation

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class NewTargetTranslationActivity : BaseActivity() {

    val translator: Translator by inject()

    private val viewModel: NewTargetTranslationModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        var disabledLanguages = listOf<String>()
        var translationId: String? = null
        var changeLanguageOnly = false

        if (extras != null) {
            translationId = extras.getString(EXTRA_TARGET_TRANSLATION_ID, null)
            changeLanguageOnly = extras.getBoolean(EXTRA_CHANGE_TARGET_LANGUAGE_ONLY, false)
            disabledLanguages = extras.getStringArray(EXTRA_DISABLED_LANGUAGES)?.toList() ?: emptyList()
        }

        viewModel.initialize(disabledLanguages, translationId, changeLanguageOnly)

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                NewTargetTranslationScreen(
                    onNavigateBack = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onFinishOk = {
                        setResult(RESULT_OK)
                        finish()
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onDuplicate = { translationId ->
                        val data = Intent()
                        data.putExtra(EXTRA_TARGET_TRANSLATION_ID, translationId)
                        setResult(RESULT_DUPLICATE, data)
                        finish()
                    },
                    onFinishError = {
                        setResult(RESULT_ERROR, Intent())
                        finish()
                    },
                    onMergeConflict = { translationId ->
                        val data = Intent()
                        data.putExtra(EXTRA_TARGET_TRANSLATION_ID, translationId)
                        setResult(RESULT_MERGE_CONFLICT, data)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        val TAG: String = NewTargetTranslationActivity::class.java.simpleName
        const val EXTRA_TARGET_TRANSLATION_ID: String = "extra_target_translation_id"
        const val EXTRA_CHANGE_TARGET_LANGUAGE_ONLY: String = "extra_change_target_language_only"
        const val EXTRA_DISABLED_LANGUAGES: String = "extra_disabled_language_ids"
        const val RESULT_DUPLICATE: Int = 2
        const val RESULT_MERGE_CONFLICT: Int = 3
        const val RESULT_ERROR: Int = 4
    }
}
