package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.ILanguageRequestRepository
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NewLanguageRequest
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import org.unfoldingword.tools.logger.Logger
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject

class SubmitNewLanguageRequests @Inject constructor(
    @ApplicationContext private val context: Context,
    languageRequestRepository: ILanguageRequestRepository,
    private val prefRepository: IPreferenceRepository,
    private val directoryProvider: IDirectoryProvider,
    private val translator: Translator
) {
    private val requests: ArrayList<NewLanguageRequest> = arrayListOf()
    private val max = 100

    init {
        // load requests that have not been submitted
        val allRequests = languageRequestRepository.getNewLanguageRequests()
        for (r in allRequests) {
            if (r.submittedAt == 0L) {
                requests.add(r)
            }
        }
    }

    fun execute(progressListener: OnProgressListener?) {
        val progressMessage = context.resources.getString(
            R.string.submitting_new_language_requests
        )
        progressListener?.onProgress(-1, max, progressMessage)

        for (i in requests.indices) {
            val request = requests[i]

            try {
                // TODO: eventually we'll be able to get the server url from the db
                val url = URL(prefRepository.getQuestionnaireApi())
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Content-Type", "application/json")
                conn.readTimeout = 10000 // 10 seconds
                conn.connectTimeout = 10000 // 10 seconds
                conn.requestMethod = "POST"

                // send payload as raw json once the server supports it.
                conn.doOutput = true
                val dos = DataOutputStream(conn.outputStream)
                val data = request.toJson()
                dos.writeBytes(data)
                dos.flush()
                dos.close()

                // read response
                val inputStream = conn.inputStream
                val bis = BufferedInputStream(inputStream)
                val baos = ByteArrayOutputStream()
                var current: Int
                while ((bis.read().also { current = it }) != -1) {
                    baos.write(current.toByte().toInt())
                }
                val response = baos.toString("UTF-8")

                // process response
                val responseJson = JSONObject(response)
                var status = "unknown"
                var message = ""
                if (responseJson.has("status")) {
                    status = responseJson.getString("status")
                }
                if (responseJson.has("message")) {
                    message = responseJson.getString("message")
                }
                if (status == "success") {
                    Logger.i(
                        this.javaClass.name,
                        "new language request '" + request.tempLanguageCode + "' successfully submitted"
                    )
                    sealRequest(request)
                } else if (status.endsWith("duplicate") || message.lowercase(Locale.getDefault())
                        .contains("duplicate key value")
                ) {
                    Logger.i(
                        this.javaClass.name,
                        "new language request '" + request.tempLanguageCode + "' has already been submitted"
                    )
                    sealRequest(request)
                } else if (message.isNotEmpty()) {
                    Logger.w(this.javaClass.name, responseJson.getString("message"))
                }
            } catch (e: Exception) {
                Logger.e(this.javaClass.name, "Failed to submit the new language request", e)
            }
            progressListener?.onProgress(
                i+1,
                requests.size,
                progressMessage
            )
        }
    }

    /**
     * Marks the request has submitted and updates any affected target translations
     * @param request
     */
    @Throws(IOException::class)
    private fun sealRequest(request: NewLanguageRequest) {
        Logger.i(
            this.javaClass.name,
            "Sealing new language request '" + request.tempLanguageCode + "'"
        )
        request.submittedAt = System.currentTimeMillis()
        val requestFile = File(
            directoryProvider.externalAppDir,
            "new_languages/" + request.tempLanguageCode + ".json"
        )
        request.toJson()?.let { json ->
            FileUtilities.writeStringToFile(requestFile, json)
        }

        // updated affected target translations
        val translations = translator.targetTranslations
        for (t in translations) {
            if (t.targetLanguageId == request.tempLanguageCode) {
                Logger.i(
                    this.javaClass.name,
                    "Updating language request in target translation '" + t.id + "'"
                )
                t.setNewLanguageRequest(request)
            }
        }
    }
}