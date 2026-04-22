package com.door43.usecases

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.util.FileUtilities
import org.bibletranslationtools.logger.GithubReporter
import org.bibletranslationtools.logger.Logger
import java.io.IOException

class UploadFeedback(
    private val context: Context,
    private val prefRepository: IPreferenceRepository,
    private val directoryProvider: IDirectoryProvider
) {
    /**
     * Returns true if the upload was successful
     */
    suspend fun execute(notes: String): Boolean {
        var uploaded = false
        val logFile = directoryProvider.logFile

        // TRICKY: make sure the github_oauth2 token has been set
        val githubTokenIdentifier = context.resources.getIdentifier(
            "github_oauth2",
            "string",
            context.packageName
        )
        val githubUrl = prefRepository.getGithubBugReportRepo()

        if (githubTokenIdentifier != 0) {
            val reporter = GithubReporter(
                context = context,
                repositoryUrl = githubUrl,
                githubOauth2Token = context.resources.getString(githubTokenIdentifier)
            )
            try {
                uploaded = reporter.reportBug(notes, logFile)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            if (!uploaded) {
                Logger.e(
                    this.javaClass.name,
                    "Failed to upload bug report."
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

        return uploaded
    }
}