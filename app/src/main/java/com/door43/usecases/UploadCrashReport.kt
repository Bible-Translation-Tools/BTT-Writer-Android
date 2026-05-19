package com.door43.usecases

import com.door43.translationstudio.core.Reporter
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

class UploadCrashReport @Inject constructor(
    private val reporter: Reporter
) {
    fun execute(message: String, email: String = ""): Boolean {
        val stacktraces = Logger.listStacktraces()
        if (stacktraces.isEmpty()) return false

        val success = reporter.sendCrash(message, email, stacktraces[0])

        if (!success) {
            Logger.e(this.javaClass.name, "Failed to upload crash report")
        } else {
            Logger.flush()
            Logger.i(this.javaClass.name, "Submitted crash report")
        }

        return success
    }
}
