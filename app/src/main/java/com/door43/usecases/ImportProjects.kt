package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities.moveOrCopyQuietly
import com.door43.util.FileUtilities.safeDelete
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.tools.logger.Logger
import java.io.File
import javax.inject.Inject

class ImportProjects @Inject constructor(
    @ApplicationContext private val context: Context,
    private val translator: Translator,
    private val backupRC: BackupRC
) {
    fun importProject(
        projectsFolder: File,
        overwrite: Boolean
    ): Translator.ImportResults? {
        return try {
            translator.importArchive(projectsFolder, overwrite)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun importProjects(
        projects: List<File>,
        overwrite: Boolean,
        progressListener: OnProgressListener? = null
    ): ImportUsfmResult {
        progressListener?.onProgress(-1, 100, context.getString(R.string.reading_usfm))

        val max = 100
        var count = 0
        val size = projects.size
        val numSteps = 4
        val subStepSize = max / numSteps.toFloat() / size.toFloat()
        var success = true
        var conflictingTargetTranslation: TargetTranslation? = null

        try {
            for (project in projects) {
                val dirName = project.name
                val progress = max * count++ / size.toFloat()

                progressListener?.onProgress(progress.toInt(), max, dirName)

                val newTargetTranslation = TargetTranslation.open(project) {
                    deleteProject(project)
                }

                if (newTargetTranslation != null) {
                    newTargetTranslation.commitSync()

                    progressListener?.onProgress((progress + subStepSize).toInt(), max, dirName)

                    val destTargetTranslationDir = File(translator.path, newTargetTranslation.id)

                    conflictingTargetTranslation =
                        translator.getConflictingTargetTranslation(project)
                    if (conflictingTargetTranslation != null && !overwrite) {
                        // commit local changes to history
                        conflictingTargetTranslation.commitSync()

                        progressListener?.onProgress((progress + 2 * subStepSize).toInt(), max, dirName)

                        // merge translations
                        try {
                            conflictingTargetTranslation.merge(project, null)
                        } catch (e: Exception) {
                            Logger.e(this::class.simpleName, "Failed to merge import folder $project", e)
                            success = false
                            continue
                        }
                    } else {
                        // import new translation
                        safeDelete(destTargetTranslationDir) // in case local was an invalid target translation
                        moveOrCopyQuietly(project, destTargetTranslationDir)
                    }
                    // update the generator info. TRICKY: we re-open to get the updated manifest.
                    TargetTranslation.updateGenerator(
                        context,
                        TargetTranslation.open(destTargetTranslationDir, null)
                    )
                }
            }

            progressListener?.onProgress(max, max, "")
        } catch (e: Exception) {
            Logger.e(this::class.simpleName, "Failed to import folder $projects", e)
            success = false
        }

        return ImportUsfmResult(success, conflictingTargetTranslation)
    }

    private fun deleteProject(file: File) {
        try {
            backupRC.backupTargetTranslation(file)
            translator.deleteTargetTranslation(file)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    data class ImportUsfmResult(
        val success: Boolean,
        val conflictingTargetTranslation: TargetTranslation? = null
    )
}