package org.unfoldingword.tools.http

import java.net.HttpURLConnection
import java.net.URL

class PostRequestMultipart(url: URL) : Request(url, "POST") {
    private val boundary = "----bttwriter${System.currentTimeMillis().toString(16)}"
    private val fields = linkedMapOf<String, String>()
    var userAgent: String? = null

    init {
        setContentType("multipart/form-data; boundary=$boundary")
    }

    fun addField(name: String, value: String) {
        fields[name] = value
    }

    override fun onConnected(conn: HttpURLConnection) {
        userAgent?.let { conn.setRequestProperty("User-Agent", it) }
        val payload = buildString {
            for ((key, value) in fields) {
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"$key\"\r\n\r\n")
                append(value)
                append("\r\n")
            }
            append("--$boundary--\r\n")
        }.toByteArray(Charsets.UTF_8)

        conn.setRequestProperty("Content-Length", payload.size.toString())
        conn.doOutput = true
        conn.outputStream.use { it.write(payload) }
    }
}
