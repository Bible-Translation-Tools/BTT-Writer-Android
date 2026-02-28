package com.door43.translationstudio.tasks.io

import android.util.Base64
import org.unfoldingword.gogsclient.Response
import org.unfoldingword.gogsclient.User
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpRequest(apiUrl: String) : RequestAPI {
    private val client: OkHttpClient
    private val readTimeout = 5000L
    private val connectionTimeout = 5000L
    private val baseUrl: String = apiUrl.replace("/+$".toRegex(), "")

    init {
        client = OkHttpClient.Builder()
            .connectTimeout(connectionTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
            .build()
    }

    override fun get(path: String, userAuth: User): Response {
        var responseCode = 0
        var responseData: String? = null
        var exception: Exception? = null

        val auth = encodeAuthHeader(userAuth)

        val request = Request.Builder()
            .url(baseUrl + path)
            .addAuthHeader(auth)
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            responseCode = response.code
            responseData = response.body?.string() ?: ""
        } catch (ex: IOException) {
            Logger.w(OkHttpRequest::class.java.name, "Request failed with an exception.", ex)
            exception = ex
        }

        return Response(responseCode, responseData, exception)
    }

    override fun post(path: String, userAuth: User, postData: String): Response {
        var responseCode = 0
        var responseData: String? = null
        var exception: Exception? = null

        val auth = encodeAuthHeader(userAuth)
        val body = postData.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(baseUrl + path)
            .addAuthHeader(auth)
            .post(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            responseCode = response.code
            responseData = response.body?.string() ?: ""
        } catch (ex: IOException) {
            Logger.w(OkHttpRequest::class.java.name, "Request failed with an exception.", ex)
            exception = ex
        }

        return Response(responseCode, responseData, exception)
    }

    override fun delete(path: String, userAuth: User): Response {
        var responseCode = 0
        var exception: Exception? = null

        val auth = encodeAuthHeader(userAuth)

        val request = Request.Builder()
            .url(baseUrl + path)
            .addAuthHeader(auth)
            .delete()
            .build()

        try {
            val response = client.newCall(request).execute()
            responseCode = response.code
        } catch (ex: IOException) {
            Logger.w(OkHttpRequest::class.java.name, "Request failed with an exception.", ex)
            exception = ex
        }

        return Response(responseCode, null, exception)
    }

    override fun put(path: String, userAuth: User, postData: String): Response {
        var responseCode = 0
        var responseData: String? = null
        var exception: Exception? = null

        val auth = encodeAuthHeader(userAuth)
        val body = postData.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(baseUrl + path)
            .addAuthHeader(auth)
            .put(body)
            .build()

        try {
            val response = client.newCall(request).execute()
            responseCode = response.code
            responseData = response.body?.string() ?: ""
        } catch (ex: IOException) {
            Logger.w(OkHttpRequest::class.java.name, "Request failed with an exception.", ex)
            exception = ex
        }

        return Response(responseCode, responseData, exception)
    }

    /**
     * Generates the authentication value from the user token or credentials
     */
    private fun encodeAuthHeader(user: User): String {
        if (user.token != null) {
            return "token " + user.token
        } else if (!user.username.isNullOrEmpty() && !user.password.isNullOrEmpty()) {
            val credentials = "${user.username}:${user.password}"
            return try {
                "Basic " + Base64.encodeToString(credentials.toByteArray(charset("UTF-8")), Base64.NO_WRAP)
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                ""
            }
        }
        return ""
    }

    /**
     * Safely adds the Authorization header only if a valid string is provided.
     */
    private fun Request.Builder.addAuthHeader(auth: String): Request.Builder {
        if (auth.isNotEmpty()) {
            this.addHeader("Authorization", auth)
        }
        return this
    }
}