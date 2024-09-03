package com.door43.usecases

import android.content.Context
import android.net.Uri
import com.door43.OnProgressListener
import com.door43.translationstudio.App.Companion.getTranslator
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.tasks.ImportProjectFromUriTask.Companion.TAG
import com.door43.util.FileUtilities
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.tools.logger.Logger
import java.util.Locale
import javax.inject.Inject

class ImportProjectFromUri @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun execute(
        path: Uri,
        mergeOverwrite: Boolean,
        progressListener: OnProgressListener? = null
    ) : Result {
        progressListener?.onProgress(-1f, "Importing...")

        var alreadyExists = false
        var success = false
        var mergeConflict = false

        val filename = FileUtilities.getUriDisplayName(context, path)
        var importedSlug: String? = null
        val validExtension = FileUtilities.getExtension(filename)
            .lowercase(Locale.getDefault()) == Translator.TSTUDIO_EXTENSION

        if (validExtension) {
            try {
                context.contentResolver?.openInputStream(path).use {
                    it?.let { input ->
                        val translator = getTranslator()
                        Logger.i(TAG, "Importing from uri: $filename")

                        val importResults = translator.importArchive(
                            input, mergeOverwrite
                        )
                        importedSlug = importResults.importedSlug
                        alreadyExists = importResults.alreadyExists
                        success = importResults.isSuccess
                        if (success && importResults.mergeConflict) {
                            // make sure we have actual merge conflicts
                            mergeConflict =
                                MergeConflictsHandler.isTranslationMergeConflicted(
                                    importResults.importedSlug
                                )
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Exception Importing from uri", e)
            }
        }

        return Result(
            path,
            filename,
            importedSlug,
            success,
            mergeConflict,
            !validExtension,
            alreadyExists
        )
    }

    /**
     * returns the import result which includes:
     * the human readable filePath
     * the success flag
     */
    inner class Result internal constructor(
        val filePath: Uri,
        val readablePath: String,
        val importedSlug: String?,
        val success: Boolean,
        val mergeConflict: Boolean,
        val invalidFileName: Boolean,
        val alreadyExists: Boolean
    )
}