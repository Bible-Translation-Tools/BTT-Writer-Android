package com.door43.translationstudio.ui.newlanguage

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import com.door43.questionnaire.QuestionnaireActivity
import com.door43.questionnaire.QuestionnairePage
import com.door43.questionnaire.QuestionnairePager
import com.door43.translationstudio.App.Companion.closeKeyboard
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NewLanguageRequest
import com.door43.translationstudio.ui.viewmodels.NewTempLanguageViewModel
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.door43client.models.Question
import org.unfoldingword.door43client.models.TargetLanguage

/**
 * Created by joel on 6/8/16.
 */
@AndroidEntryPoint
class NewTempLanguageActivity : QuestionnaireActivity(), LanguageSuggestionsDialog.OnClickListener {
    private val viewModel: NewTempLanguageViewModel by viewModels()

    private var request: NewLanguageRequest? = null
    private lateinit var questionnairePager: QuestionnairePager
    private var languageSuggestionsDialog: LanguageSuggestionsDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        questionnaire?.let { pager ->
            questionnairePager = pager
            // TRICKY: the profile can be null when running unit tests
            val translatorName = viewModel.getTranslatorName()
            if (savedInstanceState != null) {
                request = viewModel.generateNewRequest(
                    savedInstanceState.getString(
                        EXTRA_LANGUAGE_REQUEST
                    )
                )
                if (request == null) {
                    val snack = Snackbar.make(
                        findViewById(android.R.id.content),
                        resources.getString(R.string.error),
                        Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()

                    // restart
                    request = viewModel.requestFromQuestionnaire(
                        questionnairePager,
                        "android",
                        translatorName
                    )
                    restartQuestionnaire()
                    return
                }
            } else {
                // begin
                request = viewModel.requestFromQuestionnaire(
                    questionnairePager,
                    "android",
                    translatorName
                )
            }
        } ?: run {
            // missing questionnaire
            val data = Intent()
            data.putExtra(EXTRA_RESULT_CODE, RESULT_MISSING_QUESTIONNAIRE)
            setResult(RESULT_FIRST_USER, data)
            finish()
            return
        }

        val prev = supportFragmentManager.findFragmentByTag(LanguageSuggestionsDialog.TAG) as? LanguageSuggestionsDialog
        prev?.setOnClickListener(this)
    }

    override val questionnaire: QuestionnairePager?
        get() = viewModel.getQuestionnaire()

    override fun onLeavePage(page: QuestionnairePage?): Boolean {
        if (page == null) return false

        // check for a matching language that already exists
        if (questionnairePager.hasDataField("ln")) {
            val q = questionnairePager.getDataField("ln")?.let { page.getQuestionById(it) }
            if (q != null) {
                val answer: String? = request?.getAnswer(q.tdId)
                if (answer != null) {
                    val languages = viewModel.findTargetLanguages(answer.trim())
                    if (languages.isNotEmpty()) {
                        closeKeyboard(this)
                        val ft = supportFragmentManager.beginTransaction()
                        val prev = supportFragmentManager.findFragmentByTag(LanguageSuggestionsDialog.TAG)
                        if (prev != null) {
                            ft.remove(prev)
                        }
                        ft.addToBackStack(null)

                        languageSuggestionsDialog = LanguageSuggestionsDialog().apply {
                            val args = Bundle()
                            args.putString(
                                LanguageSuggestionsDialog.ARG_LANGUAGE_QUERY,
                                answer.trim())
                            arguments = args
                            setOnClickListener(this@NewTempLanguageActivity)
                            show(ft, LanguageSuggestionsDialog.TAG)
                        }
                        return false
                    }
                }
            }
        }
        return true
    }

    override fun onGetAnswer(question: Question?): String? {
        return question?.let { request?.getAnswer(it.tdId) }
    }

    override fun onAnswerChanged(question: Question?, answer: String?) {
        if (question != null && answer != null) {
            request?.setAnswer(question.tdId, answer)
        }
    }

    public override fun onFinished() {
        val data = Intent()
        data.putExtra(EXTRA_LANGUAGE_REQUEST, request?.toJson())
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(EXTRA_LANGUAGE_REQUEST, request?.toJson())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        languageSuggestionsDialog?.setOnClickListener(null)
        super.onDestroy()
    }

    override fun onAcceptLanguageSuggestion(language: TargetLanguage?) {
        val data = Intent()
        data.putExtra(EXTRA_RESULT_CODE, RESULT_USE_EXISTING_LANGUAGE)
        data.putExtra(EXTRA_LANGUAGE_ID, language?.slug)
        setResult(RESULT_FIRST_USER, data)
        finish()
    }

    override fun onDismissLanguageSuggestion() {
        nextPage()
    }

    companion object {
        const val EXTRA_LANGUAGE_REQUEST: String = "new_language_request"
        const val EXTRA_RESULT_CODE: String = "result_code"
        const val EXTRA_LANGUAGE_ID: String = "language_id"
        const val RESULT_MISSING_QUESTIONNAIRE: Int = 0
        const val RESULT_USE_EXISTING_LANGUAGE: Int = 1
    }
}
