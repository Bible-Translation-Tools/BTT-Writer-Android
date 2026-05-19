package com.door43.translationstudio.core

import android.content.Context
import android.os.Build
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.BuildConfig
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.util.FileUtilities
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.tools.http.PostRequestMultipart
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.net.URL
import javax.inject.Inject

class Reporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directoryProvider: IDirectoryProvider,
    private val prefRepository: IPreferenceRepository,
    private val profile: Profile
) {
    fun send(notes: String, email: String): Boolean {
        val content = buildString {
            if (notes.isNotBlank()) {
                appendLine(notes)
                appendLine()
            }
            append(environmentSection())
            appendLine()
            append(logSection())
        }
        return post(
            title = titleFrom(notes, "Bug report"),
            content = content,
            senderEmail = email.ifBlank { prefRepository.defaultHelpdeskEmail }
        )
    }

    fun sendCrash(message: String, email: String, stacktraceFile: File?): Boolean {
        val stackTrace = stacktraceFile?.runCatching { readText() }?.getOrDefault("") ?: ""
        val content = buildString {
            if (message.isNotBlank()) {
                appendLine(message)
                appendLine()
            }
            append(environmentSection())
            if (stackTrace.isNotBlank()) {
                appendLine()
                appendLine("## Stack Trace")
                appendLine("```")
                appendLine(stackTrace)
                append("```")
            }
            appendLine()
            append(logSection())
        }
        return post(
            title = titleFrom(message, "Crash report"),
            content = content,
            senderEmail = email.ifBlank { prefRepository.defaultHelpdeskEmail }
        )
    }

    private fun titleFrom(text: String, default: String) = when {
        text.length > 80 -> text.substring(0, 77) + "..."
        text.isNotBlank() -> text
        else -> default
    }

    private fun environmentSection(): String = buildString {
        val appVersion = BuildConfig.VERSION_NAME + "-" + BuildConfig.VERSION_CODE
        val username = profile.gogsUser?.username ?: "(unknown)"
        val serverValue = prefRepository.getDefaultPref(SettingsActivity.KEY_PREF_CONTENT_SERVER, "")
        val serverValues = context.resources.getStringArray(R.array.content_server_values_array)
        val serverNames = context.resources.getStringArray(R.array.content_server_names_array)
        val index = listOf(*serverValues).indexOf(serverValue)
        val server = serverNames.getOrElse(index) { "(unknown)" }
        appendLine("## Environment")
        appendLine("Version: $appVersion")
        appendLine("OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("User: $username")
        append("Server: $server")
    }

    private fun logSection(): String = buildString {
        val logTail = try {
            FileUtilities.readFileToString(directoryProvider.logFile)
        } catch (_: Exception) {
            ""
        }
        appendLine("## Recent Log")
        appendLine("```")
        appendLine(logTail.ifBlank { "(empty)" })
        append("```")
    }

    private fun post(title: String, content: String, senderEmail: String): Boolean {
        val url = prefRepository.helpdeskWebhookUrl + context.getString(R.string.helpdesk_token)
        var responseCode = -1
        try {
            val request = PostRequestMultipart(URL(url))
            request.userAgent = context.getString(R.string.gogs_user_agent)
            request.addField("title", title)
            request.addField("content", content)
            request.addField("sender[email]", senderEmail)
            request.setTimeout(30_000)
            request.read()
            responseCode = request.responseCode
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to send report", e)
        }
        return responseCode in 200..299
    }

    companion object {
        private const val TAG = "Reporter"
    }
}
