package com.door43.usecases

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.tools.logger.GithubReporter
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import javax.inject.Inject

class UploadCrashReport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directoryProvider: IDirectoryProvider
) {
    fun execute(message: String): Boolean {
        var responseCode = -1

        val logFile = directoryProvider.logFile
        val githubTokenIdentifier = context.resources.getIdentifier("github_oauth2", "string", context.packageName)
        val githubUrl = context.resources.getString(R.string.github_bug_report_repo)

        // TRICKY: make sure the github_oauth2 token has been set
        if (githubTokenIdentifier != 0) {
            val reporter = GithubReporter(
                context,
                githubUrl,
                context.resources.getString(githubTokenIdentifier)
            )
            val stackTraces = Logger.listStacktraces()
            if (stackTraces.isNotEmpty()) {
                try {
                    // upload most recent stacktrace
                    val request = reporter.reportCrash(message, stackTraces[0], logFile)
                    responseCode = request.responseCode
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                if (!isSuccess(responseCode)) {
                    Logger.e(
                        this::class.java.simpleName,
                        "Failed to upload crash report.  Code: $responseCode"
                    )
                } else { // success
                    // empty the log
                    Logger.flush()
                }
            }
        }
    }

    private fun isSuccess(responseCode: Int): Boolean {
        return (responseCode >= 200) && (responseCode <= 202)
    }
}