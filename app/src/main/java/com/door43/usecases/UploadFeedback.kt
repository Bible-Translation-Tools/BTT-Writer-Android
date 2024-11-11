package com.door43.usecases

import android.content.Context
import com.door43.data.IPreferenceRepository
import com.door43.util.FileUtilities
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.tools.http.Request
import org.unfoldingword.tools.logger.GithubReporter
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import javax.inject.Inject

class UploadFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefRepository: IPreferenceRepository
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
        val githubUrl = prefRepository.getGithubBugReportRepo()

        if (githubTokenIdentifier != 0) {
            val reporter = GithubReporter(
                context,
                githubUrl,
                context.resources.getString(githubTokenIdentifier)
            )
            try {
                val request: Request = reporter.reportBug(notes, logFile)
                responseCode = request.responseCode
            } catch (e: IOException) {
                e.printStackTrace()
            }

            if (!isSuccess(responseCode)) {
                Logger.e(
                    this.javaClass.name,
                    "Failed to upload bug report. Code: $responseCode"
                )
            } else { // success
                try {
                    FileUtilities.writeStringToFile(logFile, "")
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                Logger.i(this.javaClass.name, "Submitted bug report")
            }
        } else {
            Logger.w(this.javaClass.name, "the github oauth2 token is missing")
        }

        return isSuccess(responseCode)
    }

    private fun isSuccess(responseCode: Int): Boolean {
        return responseCode in 200..202
    }
}