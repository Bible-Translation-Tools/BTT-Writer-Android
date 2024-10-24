package com.door43.data

import com.door43.questionnaire.QuestionnairePager
import com.door43.translationstudio.core.NewLanguageRequest
import java.io.File

interface ILanguageRequestRepository {
    fun requestFromJson(jsonString: String): NewLanguageRequest?

    fun requestFromFile(requestFile: File): NewLanguageRequest?

    fun requestFromQuestionnaire(
        questionnaire: QuestionnairePager,
        app: String,
        requester: String
    ): NewLanguageRequest?

    /**
     * Returns the new language request if it exists
     * @param languageCode
     * @return
     */
    fun getNewLanguageRequest(languageCode: String): NewLanguageRequest?

    /**
     * Returns an array of new language requests
     * @return
     */
    fun getNewLanguageRequests(): List<NewLanguageRequest>

    /**
     * Deletes the new language request from the data path
     * @param request
     */
    fun removeNewLanguageRequest(request: NewLanguageRequest)

    /**
     * Adds a new language request.
     * This stores the request to the data path for later submission
     * and adds the temp language to the library for global use in the app
     * @param request
     */
    fun addNewLanguageRequest(request: NewLanguageRequest): Boolean
}