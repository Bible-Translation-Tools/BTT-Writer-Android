package com.door43.repositories

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.data.ILanguageRequestRepository
import com.door43.questionnaire.QuestionnairePager
import com.door43.translationstudio.core.NewLanguageRequest
import com.door43.util.FileUtilities
import org.json.JSONException
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException

class LanguageRequestRepository(
    private val context: Context,
    private val directoryProvider: IDirectoryProvider,
    private val library: Door43Client
) : ILanguageRequestRepository {
    override fun requestFromJson(jsonString: String): NewLanguageRequest? {
        return NewLanguageRequest.Builder(context).fromJson(jsonString).build()
    }

    override fun requestFromFile(requestFile: File): NewLanguageRequest? {
        return NewLanguageRequest.Builder(context).fromFile(requestFile).build()
    }

    override fun requestFromQuestionnaire(
        questionnaire: QuestionnairePager,
        app: String,
        requester: String
    ): NewLanguageRequest? {
        return NewLanguageRequest.Builder(context).instance(
            questionnaire,
            app,
            requester
        ).build()
    }

    override fun getNewLanguageRequest(languageCode: String): NewLanguageRequest? {
        val requestFile = File(directoryProvider.externalAppDir, "new_languages/$languageCode.json")
        return requestFromFile(requestFile)
    }

    override fun getNewLanguageRequests(): List<NewLanguageRequest> {
        val newLanguagesDir = File(directoryProvider.externalAppDir, "new_languages/")
        val requestFiles = newLanguagesDir.listFiles()
        val requests = arrayListOf<NewLanguageRequest>()
        if (!requestFiles.isNullOrEmpty()) {
            for (f in requestFiles) {
                try {
                    val request = requestFromFile(f)
                    if (request != null) {
                        requests.add(request)
                    }
                } catch (e: IOException) {
                    Logger.e(
                        NewLanguageRequest::class.simpleName,
                        "Failed to read the language request file",
                        e
                    )
                }
            }
        }
        return requests
    }

    override fun removeNewLanguageRequest(request: NewLanguageRequest) {
        val requestFile = File(
            directoryProvider.externalAppDir,
            "new_languages/" + request.tempLanguageCode + ".json"
        )
        if (requestFile.exists()) {
            FileUtilities.safeDelete(requestFile)
        }
    }

    override fun addNewLanguageRequest(request: NewLanguageRequest): Boolean {
        val requestFile = File(
            directoryProvider.externalAppDir,
            "new_languages/" + request.tempLanguageCode + ".json"
        )
        requestFile.parentFile?.mkdirs()
        try {
            request.toJson()?.let { json ->
                FileUtilities.writeStringToFile(requestFile, json)
            } ?: throw JSONException("Could not create json object")

            return library.index.addTempTargetLanguage(request.tempTargetLanguage)
        } catch (e: Exception) {
            Logger.e(
                NewLanguageRequest::class.simpleName,
                "Failed to save the new language request",
                e
            )
        }
        return false
    }
}