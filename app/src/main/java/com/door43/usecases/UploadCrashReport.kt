package com.door43.usecases

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import org.bibletranslationtools.logger.GithubReporter
import org.bibletranslationtools.logger.Logger
import java.io.IOException

class UploadCrashReport(
    private val context: Context,
    private val directoryProvider: IDirectoryProvider,
    private val prefRepository: IPreferenceRepository
) {
    suspend fun execute(message: String): Boolean {
        var uploaded = false

        val logFile = directoryProvider.logFile
        val githubTokenIdentifier = context.resources.getIdentifier(
            "github_oauth2",
            "string",
            context.packageName
        )
        val githubUrl = prefRepository.getGithubBugReportRepo()

        // TRICKY: make sure the github_oauth2 token has been set
        if (githubTokenIdentifier != 0) {
            val reporter = GithubReporter(
                context = context,
                repositoryUrl = githubUrl,
                githubOauth2Token = context.resources.getString(githubTokenIdentifier)
            )
            val stackTraces = Logger.listStacktraces()
            if (stackTraces.isNotEmpty()) {
                try {
                    // upload most recent stacktrace
                    uploaded = reporter.reportCrash(message, stackTraces[0], logFile)
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                if (!uploaded) {
                    Logger.e(
                        this::class.java.simpleName,
                        "Failed to upload crash report."
                    )
                } else { // success
                    // empty the log
                    Logger.flush()
                }
            }
        }

        return uploaded
    }
}