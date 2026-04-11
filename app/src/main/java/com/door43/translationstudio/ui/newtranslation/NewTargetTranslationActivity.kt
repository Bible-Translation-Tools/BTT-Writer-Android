package com.door43.translationstudio.ui.newtranslation

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.newlanguage.NewTempLanguageActivity
import com.door43.util.StringUtilities
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class NewTargetTranslationActivity : BaseActivity() {

    val translator: Translator by inject()

    private val viewModel: NewTargetTranslationModel by viewModel()

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onTempLanguageCreated(result.resultCode, result.data)
    }

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

        if (viewModel.createdNewLanguage && viewModel.selectedTargetLanguage != null) {
            confirmTempLanguage()
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

    private fun confirmTempLanguage() {
        viewModel.selectedTargetLanguage?.let { language ->
            val msg = resources.getString(
                R.string.new_language_confirmation,
                language.slug,
                language.name
            )
            AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setCancelable(false)
                .setTitle(R.string.language)
                .setMessage(msg)
                .setPositiveButton(R.string.label_continue) { dialog, _ ->
                    dialog.dismiss()
                    // viewModel.onLanguageSelected(language)
                }
                .setNeutralButton(R.string.copy) { _, _ ->
                    StringUtilities.copyToClipboard(this, language.slug)
                    val snack = Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.copied_to_clipboard,
                        Snackbar.LENGTH_SHORT
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()
                }
                .show()
        }
    }

    private fun onTempLanguageCreated(resultCode: Int, data: Intent?) {
        if (RESULT_OK == resultCode) {
            val rawResponse = data?.getStringExtra(NewTempLanguageActivity.EXTRA_LANGUAGE_REQUEST)
            val registered = viewModel.registerTempLanguage(rawResponse)
            if (registered) {
                confirmTempLanguage()
            } else {
                AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                    .setTitle(R.string.error)
                    .setMessage(R.string.try_again)
                    .show()
            }
        } else if (RESULT_FIRST_USER == resultCode) {
            val secondResultCode =
                data?.getIntExtra(NewTempLanguageActivity.EXTRA_RESULT_CODE, -1)
            if (secondResultCode == NewTempLanguageActivity.RESULT_MISSING_QUESTIONNAIRE) {
                val snack = Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.missing_questionnaire,
                    Snackbar.LENGTH_LONG
                )
                ViewUtil.setSnackBarTextColor(
                    snack,
                    resources.getColor(R.color.light_primary_text)
                )
                snack.show()
            } else if (secondResultCode == NewTempLanguageActivity.RESULT_USE_EXISTING_LANGUAGE) {
                val targetLanguageId =
                    data.getStringExtra(NewTempLanguageActivity.EXTRA_LANGUAGE_ID)
                val targetLanguage = targetLanguageId?.let { viewModel.getTargetLanguage(it) }
                if (targetLanguage != null) {
                    //viewModel.onLanguageSelected(targetLanguage)
                }
            } else {
                val snack = Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.error,
                    Snackbar.LENGTH_LONG
                )
                ViewUtil.setSnackBarTextColor(
                    snack,
                    resources.getColor(R.color.light_primary_text)
                )
                snack.show()
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
