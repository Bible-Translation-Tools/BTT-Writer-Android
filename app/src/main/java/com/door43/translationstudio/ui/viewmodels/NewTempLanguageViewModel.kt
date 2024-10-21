package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.door43.data.ILanguageRequestRepository
import com.door43.questionnaire.QuestionnairePager
import com.door43.translationstudio.core.NewLanguageRequest
import com.door43.translationstudio.core.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import javax.inject.Inject

@HiltViewModel
class NewTempLanguageViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var languageRequestRepository: ILanguageRequestRepository
    @Inject lateinit var library: Door43Client
    @Inject lateinit var profile: Profile

    fun getQuestionnaire(): QuestionnairePager? {
        // TRICKY: for now we only have one questionnaire
        val questionnaires = library.index.questionnaires
        if (questionnaires.size > 0) {
            val q = questionnaires[0]
            val questions = library.index.getQuestions(q.tdId)
            val pager = QuestionnairePager(q)
            pager.loadQuestions(questions)
            return pager
        }
        return null
    }

    fun getTranslatorName(): String {
        return profile.fullName ?: "unknown"
    }

    fun generateNewRequest(jsonString: String?): NewLanguageRequest? {
        return jsonString?.let { languageRequestRepository.requestFromJson(it) }
    }

    fun requestFromQuestionnaire(
        questionnairePager: QuestionnairePager,
        languageId: String,
        translatorName: String,
    ): NewLanguageRequest? {
        return languageRequestRepository.requestFromQuestionnaire(
            questionnairePager,
            languageId,
            translatorName
        )
    }

    fun findTargetLanguages(name: String?): List<TargetLanguage> {
        return name?.let { library.index.findTargetLanguage(it) } ?: listOf()
    }
}