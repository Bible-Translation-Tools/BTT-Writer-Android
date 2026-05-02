package com.door43.translationstudio.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

object HttpRequest {

    @PublishedApi
    internal var lastResponse: Response? = null

    @PublishedApi
    internal val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 30_000
        }
    }

    suspend inline fun <reified T> get(url: String): T? {
        return try {
            val response = httpClient.get(url)
            lastResponse = Response(
                response.status.isSuccess(),
                response.status.value,
                response.status.description
            )
            if (response.status.isSuccess()) response.body<T>() else null
        } catch (e: Exception) {
            lastResponse = Response(false, -1, e.message)
            null
        }
    }

    suspend fun download(
        url: String,
        file: File,
        onProgress: (contentLength: Long, bytesRead: Long) -> Unit = { _, _ -> }
    ) {
        httpClient.prepareGet(url).execute { response ->
            lastResponse = Response(response.status.isSuccess(), response.status.value, response.status.description)

            if (!response.status.isSuccess()) {
                throw IOException("HTTP ${response.status.value}: ${response.status.description}")
            }

            val contentLength = response.contentLength() ?: -1L
            file.parentFile?.mkdirs()

            val channel = response.bodyAsChannel()
            var bytesRead = 0L
            var updateQueue = 0L
            val updateInterval = DEFAULT_BUFFER_SIZE * 50L

            try {
                withContext(Dispatchers.IO) {
                    file.outputStream().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (!channel.isClosedForRead) {
                            val n = channel.readAvailable(buffer)
                            if (n == -1) break
                            out.write(buffer, 0, n)
                            bytesRead += n
                            updateQueue += n
                            if (updateQueue >= updateInterval) {
                                updateQueue = 0
                                onProgress(contentLength, bytesRead)
                            }
                        }
                        onProgress(contentLength, bytesRead)
                    }
                }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }
    }

    data class Response(
        val success: Boolean,
        val code: Int,
        val message: String? = null
    )
}

