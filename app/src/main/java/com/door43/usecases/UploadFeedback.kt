package com.door43.usecases

import android.content.Context
import com.door43.translationstudio.R
import com.door43.util.FileUtilities.writeStringToFile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.tools.logger.GithubReporter
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import javax.inject.Inject

class UploadFeedback @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns true if the upload was successful
     */
    fun execute(notes: String): Boolean {
        var responseCode = -1
        val logFile = Logger.getLogFile()

        // TRICKY: make sure the github_oauth2 token has been set
        val githubTokenIdentifier = context.resources.getIdentifier(
            "github_oauth2",
            "string",
            context.packageName
        )
        val githubUrl = context.resources.getString(R.string.github_bug_report_repo)

        if (githubTokenIdentifier != 0) {
            val reporter = GithubReporter(
                context,
                githubUrl,
                context.resources.getString(githubTokenIdentifier)
            )
            try {
                val request = reporter.reportBug(notes, logFile)
                responseCode = request.responseCode
            } catch (e: IOException) {
                e.printStackTrace()
            }

            if (!isSuccess(responseCode)) {
                Logger.e(
                    this.javaClass.name,
                    "Failed to upload bug report.  Code: $responseCode"
                )
            } else { // success
                Logger.i(this.javaClass.name, "Submitted bug report")
            }

            try {
                writeStringToFile(logFile, "")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            Logger.w(this.javaClass.name, "the github oauth2 token is missing")
        }

        return isSuccess(responseCode)
    }

    private fun isSuccess(responseCode: Int): Boolean {
        return (responseCode >= 200) && (responseCode <= 202)
    }
}