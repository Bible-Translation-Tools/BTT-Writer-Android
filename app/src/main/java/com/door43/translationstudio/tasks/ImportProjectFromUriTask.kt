package com.door43.translationstudio.tasks

import android.net.Uri
import android.os.Process
import com.door43.translationstudio.App.Companion.context
import com.door43.translationstudio.App.Companion.translator
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities
import org.unfoldingword.tools.logger.Logger
import org.unfoldingword.tools.taskmanager.ManagedTask
import java.util.Locale

/**
 * Created by blm on 2/23/17.
 */
class ImportProjectFromUriTask(
    private val path: Uri,
    private val mergeOverwrite: Boolean
) : ManagedTask() {
    private var alreadyExists = false

    init {
        setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
    }

    override fun start() {
        var success = false
        var mergeConflict = false
        alreadyExists = false
        val filename = FileUtilities.getUriDisplayName(context()!!, path)
        var importedSlug: String? = null
        val validExtension = FileUtilities.getExtension(filename)
            .lowercase(Locale.getDefault()) == Translator.TSTUDIO_EXTENSION

        if (validExtension) {
            try {
                context()?.contentResolver?.openInputStream(path).use {
                    it?.let { input ->
                        val translator = translator
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
        result = ImportResults(
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
     * returns the import results which includes:
     * the human readable filePath
     * the success flag
     */
    inner class ImportResults internal constructor(
        val filePath: Uri,
        val readablePath: String,
        val importedSlug: String?,
        val success: Boolean,
        val mergeConflict: Boolean,
        val invalidFileName: Boolean,
        val alreadyExists: Boolean
    )

    companion object {
        const val TASK_ID: String = "import_project_from_uri_task"
        val TAG: String = ImportProjectFromUriTask::class.java.simpleName
    }
}
