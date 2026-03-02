package com.door43.translationstudio.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpMethod

class GetRequest(url: String) : HttpRequest(url, HttpMethod.Get) {
    override fun onConfigureRequest(builder: HttpRequestBuilder) {
        // GET requests have no body
    }
}