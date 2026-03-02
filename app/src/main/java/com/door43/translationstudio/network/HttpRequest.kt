package com.door43.translationstudio.network

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Represents a network request
 */
abstract class HttpRequest(
    protected val url: String,
    private val requestMethod: HttpMethod
) {
    private var token: String? = null
    private var username: String? = null
    private var password: String? = null
    private var contentType: String? = null
    private var ttl: Int = 0
    private var progressListener: OnProgressListener? = null

    var responseCode: Int = -1
        private set
    var responseMessage: String? = null
        private set

    companion object {
        val client = HttpClient(OkHttp) {
            install(HttpTimeout)
            expectSuccess = false
        }
    }

    /**
     * Sets token authentication. Tokens take precedence over credentials.
     */
    fun setAuthentication(token: String) {
        this.token = token
    }

    /**
     * Sets basic (username/password) authentication.
     */
    fun setAuthentication(username: String, password: String) {
        this.username = username
        this.password = password
    }

    /**
     * Sets the connection write and read timeout in milliseconds.
     */
    fun setTimeout(ttl: Int) {
        this.ttl = ttl
    }

    /**
     * Sets the listener to receive progress updates.
     */
    fun setProgressListener(listener: OnProgressListener) {
        this.progressListener = listener
    }

    /**
     * Sets the content type to be used in the request.
     */
    fun setContentType(contentType: String) {
        this.contentType = contentType
    }

    /**
     * Generates and returns the auth header value if available.
     */
    protected fun getAuth(): String? {
        token?.let { return "token $it" }
        if (username != null && password != null) {
            val encoded = Base64.encodeToString(
                "$username:$password".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            return "Basic $encoded"
        }
        return null
    }

    /**
     * Allows subclasses to configure the request before it is sent.
     * For example: setting a POST body.
     */
    protected abstract fun onConfigureRequest(builder: HttpRequestBuilder)

    private suspend fun execute(): HttpStatement =
        client.prepareRequest(url) {
            method = requestMethod
            if (ttl > 0) {
                timeout {
                    connectTimeoutMillis = ttl.toLong()
                    requestTimeoutMillis = ttl.toLong()
                }
            }
            getAuth()?.let { header(HttpHeaders.Authorization, it) }
            contentType?.let { header(HttpHeaders.ContentType, it) }

            header(HttpHeaders.UserAgent, "btt-writer-android")

            onConfigureRequest(this)
        }

    /**
     * Reads the response as a UTF-8 string.
     */
    suspend fun read(): String {
        return execute().execute { response ->
            responseCode = response.status.value
            responseMessage = response.status.description
            response.bodyAsText(Charsets.UTF_8)
        }
    }

    /**
     * Downloads the response body to a file.
     */
    suspend fun download(destination: File) {
        execute().execute { response ->
            responseCode = response.status.value
            responseMessage = response.status.description

            if (responseCode != 200) throw IOException(responseMessage)

            val contentLength = response.contentLength() ?: -1L
            destination.parentFile?.mkdirs()

            val channel = response.bodyAsChannel()
            var bytesRead = 0L

            try {
                withContext(Dispatchers.IO) {
                    FileOutputStream(destination).use { out ->
                        val buffer = ByteArray(4096)
                        val updateInterval = 1048L * 50
                        var updateQueue = 0L

                        while (!channel.isClosedForRead) {
                            val n = channel.readAvailable(buffer)
                            if (n == -1) break
                            out.write(buffer, 0, n)
                            bytesRead += n
                            updateQueue += n
                            if (updateQueue >= updateInterval) {
                                updateQueue = 0
                                publishProgress(contentLength, bytesRead)
                            }
                        }
                        publishProgress(contentLength, bytesRead)
                    }
                }
            } catch (e: Exception) {
                if (destination.exists()) destination.delete()
                throw e
            }
        }
    }

    private fun publishProgress(totalBytes: Long, bytesRead: Long) {
        val listener = progressListener ?: return
        if (totalBytes <= 0 || bytesRead <= 0) listener.onIndeterminate()
        else listener.onProgress(totalBytes, bytesRead)
    }

    interface OnProgressListener {
        /**
         * Receives progress events.
         * @param max      the total number of bytes
         * @param progress the number of bytes read so far
         */
        fun onProgress(max: Long, progress: Long)

        /**
         * Called when progress cannot be determined.
         */
        fun onIndeterminate()
    }
}