package com.door43.usecases

import android.content.Context
import android.net.Uri
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ArchiveImporter
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities
import com.door43.util.FileUtilities.moveOrCopyQuietly
import com.door43.util.FileUtilities.safeDelete
import com.door43.util.Zip
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class ImportProjects @Inject constructor(
    @ApplicationContext private val context: Context,
    private val translator: Translator,
    private val backupRC: BackupRC,
    private val directoryProvider: IDirectoryProvider,
    private val archiveImporter: ArchiveImporter,
    private val library: Door43Client
) {
    fun importProject(
        project: File,
        overwrite: Boolean = false
    ): ImportResults? {
        return try {
            importArchive(project, overwrite)
        } catch (e: Exception) {
            Logger.e(this::class.java.simpleName, "Exception Importing from project file", e)
            null
        }
    }

    fun importProject(
        projectUri: Uri,
        overwrite: Boolean = false,
        progressListener: OnProgressListener? = null
    ): ImportUriResult {
        val max = 100
        progressListener?.onProgress(-1, max, "Importing...")

        var alreadyExists = false
        var success = false
        var mergeConflict = false

        val filename = FileUtilities.getUriDisplayName(context, projectUri)
        var importedSlug: String? = null
        val validExtension = FileUtilities.getExtension(filename)
            .lowercase(Locale.getDefault()) == Translator.TSTUDIO_EXTENSION

        if (validExtension) {
            try {
                context.contentResolver?.openInputStream(projectUri).use {
                    it?.let { input ->
                        Logger.i(this::class.java.simpleName, "Importing from uri: $filename")

                        val archiveDir = unzipFromStream(input)
                        val importResults = importArchive(archiveDir, overwrite)
                        importedSlug = importResults.importedSlug
                        alreadyExists = importResults.alreadyExists
                        success = importResults.isSuccess
                        if (success && importResults.mergeConflict) {
                            // make sure we have actual merge conflicts
                            mergeConflict =
                                MergeConflictsHandler.isTranslationMergeConflicted(
                                    importResults.importedSlug,
                                    translator
                                )
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(this::class.java.simpleName, "Exception Importing from uri", e)
            }
        }

        return ImportUriResult(
            projectUri,
            filename,
            importedSlug,
            success,
            mergeConflict,
            !validExtension,
            alreadyExists
        )
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

    fun importSource(uri: Uri): ImportSourceResult {
        val uuid = UUID.randomUUID().toString()
        val tempDir = directoryProvider.createTempDir(uuid)
        FileUtilities.copyDirectory(context, uri, tempDir)

        val externalContainer = try {
            ResourceContainer.load(tempDir)
        } catch (e: Exception) {
            Logger.e(this::class.simpleName, "Could not import RC", e)
            return ImportSourceResult(
                success = false,
                hasConflict = false,
                e.message
            )
        }

        return try {
            library.open(externalContainer.slug)
            val conflictMessage = String.format(
                context.getString(R.string.overwrite_content),
                "${externalContainer.language.name} - ${externalContainer.project.name} - ${externalContainer.resource.name}"
            )
            ImportSourceResult(
                success = false,
                hasConflict = true,
                error = conflictMessage,
                targetDir = tempDir
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // no conflicts. import
            importSource(tempDir)
        }
    }

    fun importSource(dir: File): ImportSourceResult {
        return try {
            library.importResourceContainer(dir)
            ImportSourceResult(
                success = true,
                hasConflict = false
            )
        } catch (e: Exception) {
            Logger.e(this::class.simpleName, "Could not import RC", e)
            ImportSourceResult(
                success = false,
                hasConflict = false,
                e.message
            )
        } finally {
            FileUtilities.deleteQuietly(dir)
        }
    }

    private fun deleteProject(file: File) {
        try {
            backupRC.backupTargetTranslation(file)
            translator.deleteTargetTranslation(file)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @Throws(Exception::class)
    private fun importArchive(file: File, overwrite: Boolean = false): ImportResults {
        return when {
            file.isDirectory -> importArchiveDir(file, overwrite)
            else -> importArchiveFile(file, overwrite)
        }
    }

    @Throws(Exception::class)
    private fun importArchiveFile(archiveFile: File, overwrite: Boolean = false): ImportResults {
        return FileInputStream(archiveFile).use {
            val archiveDir = unzipFromStream(it)
            importArchiveDir(archiveDir, overwrite)
        }
    }

    @Throws(Exception::class)
    private fun importArchiveDir(dir: File, overwrite: Boolean = false): ImportResults {
        var importedSlug: String? = null
        var mergeConflict = false
        var alreadyExists = false
        try {
            val targetTranslationDirs = archiveImporter.importArchive(dir)
            for (newDir in targetTranslationDirs) {
                val newTargetTranslation = TargetTranslation.open(newDir) {
                    // Try to backup and delete corrupt project
                    try {
                        backupRC.backupTargetTranslation(newDir)
                        translator.deleteTargetTranslation(newDir)
                    } catch (ex: java.lang.Exception) {
                        ex.printStackTrace()
                    }
                }
                if (newTargetTranslation != null) {
                    // TRICKY: the correct id is pulled from the manifest
                    // to avoid propagation of bad folder names
                    val targetTranslationId = newTargetTranslation.id
                    val localDir = File(translator.path, targetTranslationId)
                    val localTargetTranslation = TargetTranslation.open(localDir) {
                        // Try to backup and delete corrupt project
                        try {
                            backupRC.backupTargetTranslation(localDir)
                            translator.deleteTargetTranslation(localDir)
                        } catch (ex: java.lang.Exception) {
                            ex.printStackTrace()
                        }
                    }
                    alreadyExists = localTargetTranslation != null
                    if (alreadyExists && !overwrite) {
                        // commit local changes to history
                        localTargetTranslation!!.commitSync()

                        // merge translations
                        try {
                            val mergeSuccess = localTargetTranslation.merge(newDir) {
                                // Try to backup and delete corrupt project
                                try {
                                    backupRC.backupTargetTranslation(newDir)
                                    translator.deleteTargetTranslation(newDir)
                                } catch (ex: java.lang.Exception) {
                                    ex.printStackTrace()
                                }
                            }
                            if (!mergeSuccess) {
                                mergeConflict = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            continue
                        }
                    } else {
                        // import new translation
                        safeDelete(localDir) // in case local was an invalid target translation
                        moveOrCopyQuietly(newDir, localDir)
                    }
                    // update the generator info. TRICKY: we re-open to get the updated manifest.
                    TargetTranslation.updateGenerator(context, TargetTranslation.open(localDir) {
                        // Try to backup and delete corrupt project
                        try {
                            backupRC.backupTargetTranslation(localDir)
                            translator.deleteTargetTranslation(localDir)
                        } catch (ex: java.lang.Exception) {
                            ex.printStackTrace()
                        }
                    })

                    importedSlug = targetTranslationId
                }
            }
        } catch (e: Exception) {
            throw e
        } finally {
            FileUtilities.deleteQuietly(dir)
        }

        return ImportResults(importedSlug, mergeConflict, alreadyExists)
    }

    @Throws(Exception::class)
    private fun unzipFromStream(input: InputStream): File {
        val dir = File(
            directoryProvider.cacheDir,
            System.currentTimeMillis().toString()
        )
        dir.mkdirs()
        Zip.unzipFromStream(input, dir)
        return dir
    }

    data class ImportResults internal constructor(
        @JvmField val importedSlug: String?,
        @JvmField val mergeConflict: Boolean,
        @JvmField val alreadyExists: Boolean
    ) {
        val isSuccess: Boolean
            get() {
                val success = !importedSlug.isNullOrEmpty()
                return success
            }
    }

    data class ImportUsfmResult internal constructor(
        val success: Boolean,
        val conflictingTargetTranslation: TargetTranslation? = null
    )

    data class ImportSourceResult internal constructor(
        val success: Boolean,
        val hasConflict: Boolean,
        val error: String? = null,
        val targetDir: File? = null
    )

    /**
     * returns the import result which includes:
     * the human readable filePath
     * the success flag
     */
    data class ImportUriResult internal constructor(
        val filePath: Uri,
        val readablePath: String,
        val importedSlug: String?,
        val success: Boolean,
        val mergeConflict: Boolean,
        val invalidFileName: Boolean,
        val alreadyExists: Boolean
    )
}