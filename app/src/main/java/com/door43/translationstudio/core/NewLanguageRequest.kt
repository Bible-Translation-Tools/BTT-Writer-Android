package com.door43.translationstudio.core

import android.annotation.SuppressLint
import android.content.Context
import com.door43.questionnaire.QuestionnairePager
import com.door43.translationstudio.App
import com.door43.util.FileUtilities
import com.door43.util.Security
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.util.TreeMap
import java.util.UUID

/**
 * Created by joel on 6/1/16.
 * Instantiates a new questionnaire response
 * @param requestUUID an id that identifies this response
 * @param tempLanguageCode the temporary language code that will be assigned
 * @param questionnaireId the translationDatabase id of the questionnaire
 * @param app the name of the app generating this response
 * @param requester the name of the translator requesting the custom language code
 */

class NewLanguageRequest private constructor(
    val requestUUID: String,
    val tempLanguageCode: String,
    val questionnaireId: Long,
    val app: String,
    val requester: String,
    var submittedAt: Long,
    private val dataFields: Map<String, Long>,
    private val answers: Map<Long, String>
) {

    /**
     * Returns the temporary target language.
     * Properties of the language are derived from the questionnaire answers
     */
    val tempTargetLanguage: TargetLanguage
        get() {
            var name: String? = tempLanguageCode
            var region: String? = "unknown"
            var direction = "ltr"

            if (dataFields.containsKey("ln")) {
                name = this.getAnswer(dataFields["ln"]!!)
            }
            if (dataFields.containsKey("cc")) {
                region = this.getAnswer(dataFields["cc"]!!)
            }
            if (dataFields.containsKey("ld")) {
                val isLeftToRight = getAnswer(dataFields["ld"]!!).toBoolean()
                direction = if (isLeftToRight) "ltr" else "rtl"
            }

            return TargetLanguage(
                this.tempLanguageCode,
                name,
                "",
                direction,
                region,
                false
            )
        }

    /**
     * returns the answer by the question id
     * @param questionTdId
     * @return
     */
    fun getAnswer(questionTdId: Long): String? {
        return answers[questionTdId]
    }

    /**
     * Adds or updates an answer
     * @param questionTdId
     * @param answer
     */
    fun setAnswer(questionTdId: Long, answer: String) {
        answers as MutableMap<Long, String>
        answers[questionTdId] = answer
    }

    /**
     * Represents the questionnaire response as json
     * @return
     */
    fun toJson(): String? {
        val json = JSONObject()
        try {
            json.put("request_id", requestUUID)
            json.put("temp_code", tempLanguageCode)
            json.put("questionnaire_id", questionnaireId)
            json.put("app", app)
            json.put("requester", requester)
            json.put("submitted_at", submittedAt)

            val dataFieldsJson = JSONObject()
            for (field in dataFields.keys) {
                dataFieldsJson.put(field, dataFields[field])
            }
            json.put("data_fields", dataFieldsJson)

            val answersJson = JSONArray()
            for (key in answers.keys) {
                val answer = JSONObject()
                answer.put("question_id", key)
                answer.put("text", answers[key])
                answersJson.put(answer)
            }
            json.put("answers", answersJson)
            return json.toString()
        } catch (e: JSONException) {
            Logger.w(this.javaClass.name, "Failed to create json object", e)
        }
        return null
    }

    class Builder (private val context: Context) {
        private var requestUUID: String? = null
        private var tempLanguageCode: String? = null
        private var questionnaireId: Long = 0
        private var app: String? = null
        private var requester: String? = null
        private val dataFields = mutableMapOf<String, Long>()

        private val answers: MutableMap<Long, String> = TreeMap()
        private var submittedAt: Long = 0

        fun fromFile(requestFile: File): Builder {
            val jsonData = if (requestFile.exists() && requestFile.isFile) {
                try {
                    FileUtilities.readFileToString(requestFile)
                } catch (e: Exception) {
                    Logger.w(
                        NewLanguageRequest::class.java.name,
                        "Failed to read questionnaire file: $requestFile", e
                    )
                    null
                }
            } else null
            return fromJson(jsonData)
        }

        fun fromJson(jsonString: String?): Builder {
            // Return empty builder if json string is null
            if (jsonString == null) return this

            try {
                val json = JSONObject(jsonString)
                val requestUUID = json.getString("request_id")
                val tempCode = json.getString("temp_code")
                val questionnaireId = json.getLong("questionnaire_id")
                val app = json.getString("app")
                val requester = json.getString("requester")
                var submittedAt: Long = 0
                if (json.has("submitted_at")) {
                    submittedAt = json.getLong("submitted_at")
                }

                val dataFields: MutableMap<String, Long> = HashMap()
                if (json.has("data_fields")) {
                    val dataFieldsJson = json.getJSONObject("data_fields")
                    val fieldItr = dataFieldsJson.keys()
                    while (fieldItr.hasNext()) {
                        val field = fieldItr.next()
                        dataFields[field] = dataFieldsJson.getLong(field)
                    }
                }

                this.requestUUID = requestUUID
                this.tempLanguageCode = tempCode
                this.questionnaireId = questionnaireId
                this.app = app
                this.requester = requester
                this.dataFields.putAll(dataFields)
                this.submittedAt = submittedAt

                val answers = json.getJSONArray("answers")
                for (i in 0 until answers.length()) {
                    val answer = answers.getJSONObject(i)
                    this.answers[answer.getLong("question_id")] = answer.getString("text")
                }
            } catch (e: JSONException) {
                Logger.w(
                    NewLanguageRequest::class.java.name,
                    "Failed to parse questionnaire response json: $jsonString", e
                )
            }

            return this
        }

        @SuppressLint("HardwareIds")
        fun instance(
            questionnaire: QuestionnairePager,
            app: String,
            requester: String
        ): Builder {
            val udid = App.udid()
            val time = System.currentTimeMillis()
            val uniqueString = udid + time
            val hash = Security.sha1(uniqueString)
            val languageCode = LANGUAGE_PREFIX + hash.substring(0, 6)

            this.requestUUID = UUID.randomUUID().toString()
            this.tempLanguageCode = languageCode
            this.questionnaireId = questionnaire.questionnaire.tdId
            this.app = app
            this.requester = requester
            this.dataFields.putAll(questionnaire.questionnaire.dataFields)

            return this
        }

        fun build(): NewLanguageRequest? {
            if (requestUUID == null) {
                return null
            }

            return try {
                NewLanguageRequest(
                    requestUUID!!,
                    tempLanguageCode!!,
                    questionnaireId,
                    app!!,
                    requester!!,
                    submittedAt,
                    dataFields,
                    answers
                )
            } catch (e: Exception) {
                Logger.w(
                    NewLanguageRequest::class.java.name,
                    "Failed to build questionnaire response", e
                )
                null
            }
        }

        companion object {
            private const val LANGUAGE_PREFIX = "qaa-x-"
        }
    }
}
