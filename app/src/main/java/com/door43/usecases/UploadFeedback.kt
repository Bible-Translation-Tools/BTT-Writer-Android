package com.door43.usecases

import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.Reporter
import com.door43.util.FileUtilities
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import javax.inject.Inject

class UploadFeedback @Inject constructor(
    private val directoryProvider: IDirectoryProvider,
    private val reporter: Reporter
) {
    fun execute(notes: String, email: String): Boolean {
        val success = reporter.send(notes, email)

        if (!success) {
            Logger.e(this.javaClass.name, "Failed to upload bug report")
        } else {
            try {
                FileUtilities.writeStringToFile(directoryProvider.logFile, "")
            } catch (_: IOException) {
            }
            Logger.i(this.javaClass.name, "Submitted bug report")
        }

        return success
    }
}
