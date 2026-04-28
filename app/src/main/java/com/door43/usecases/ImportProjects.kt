package com.door43.usecases

import android.content.Context
import android.net.Uri
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.Platform
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ArchiveImporter
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.util.FileUtilities
import com.door43.util.FileUtilities.moveOrCopyQuietly
import com.door43.util.FileUtilities.safeDelete
import com.door43.util.Zip
import org.bibletranslationtools.logger.Logger
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.unfoldingword.door43client.Door43Client
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID

class ImportProjects(
    private val context: Context,
    private val translator: Translator,
    private val backupRC: BackupRC,
    private val directoryProvider: IDirectoryProvider,
    private val archiveImporter: ArchiveImporter,
    private val library: Door43Client,
    private val platform: Platform
) {
    fun importProject(
        project: File,
        overwrite: Boolean = false
    ): ImportFileResult? {
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
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): ImportUriResult {
        onProgress(-1f, "Importing...")

        var alreadyExists = false
        var success = false
        var hasMergeConflict = false

        val filename = FileUtilities.getFileName(context, projectUri)
        var importedSlug: String? = null

        val isTstudio = filename.contains(Translator.TSTUDIO_EXTENSION, ignoreCase = true)
        val isZip = filename.contains(Translator.ZIP_EXTENSION, ignoreCase = true)

        val validExtension = isTstudio || isZip

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
                            hasMergeConflict = MergeConflictsHandler.isTranslationMergeConflicted(
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
            hasMergeConflict,
            !validExtension,
            alreadyExists
        )
    }

    fun importProjects(
        projects: List<File>,
        overwrite: Boolean,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): ImportFilesResult {
        onProgress(-1f, context.getString(R.string.importing_file))

        var count = 0
        val size = projects.size
        val numSteps = 4
        val subStepSize = 1f / numSteps / size.toFloat()
        var success = true

        val importedTargetTranslations = arrayListOf<TargetTranslation>()
        val conflictingTargetTranslations = arrayListOf<TargetTranslation>()

        try {
            for (project in projects) {
                val dirName = project.name
                val progress = count++ / size.toFloat()

                onProgress(progress, dirName)

                val newTargetTranslation = TargetTranslation.open(project) {
                    deleteProject(project)
                }

                if (newTargetTranslation != null) {
                    newTargetTranslation.commitSync()

                    onProgress((progress + subStepSize), dirName)

                    val destTargetTranslationDir = File(translator.path, newTargetTranslation.id)

                    val conflictingTargetTranslation =
                        translator.getConflictingTargetTranslation(project)

                    if (conflictingTargetTranslation != null && !overwrite) {
                        // commit local changes to history
                        conflictingTargetTranslation.commitSync()

                        onProgress((progress + 2 * subStepSize), dirName)

                        // merge translations
                        try {
                            conflictingTargetTranslation.merge(project, null)
                            conflictingTargetTranslations.add(conflictingTargetTranslation)
                        } catch (e: Exception) {
                            Logger.e(this::javaClass.name, "Failed to merge import folder $project", e)
                            success = false
                            continue
                        }
                    } else {
                        // import new translation
                        safeDelete(destTargetTranslationDir) // in case local was an invalid target translation
                        moveOrCopyQuietly(project, destTargetTranslationDir)
                    }
                    // update the generator info. TRICKY: we re-open to get the updated manifest.
                    TargetTranslation.open(destTargetTranslationDir)?.let { targetTranslation ->
                        importedTargetTranslations.add(targetTranslation)
                        targetTranslation.updateGenerator(platform.info.versionCode.toString())
                    }
                }
            }

            onProgress(1f, "Completed!")
        } catch (e: Exception) {
            Logger.e(this::javaClass.name, "Failed to import folder $projects", e)
            success = false
        }

        return ImportFilesResult(
            success = success,
            targetTranslations = importedTargetTranslations,
            conflictingTargetTranslations = conflictingTargetTranslations
        )
    }

    suspend fun importSource(uri: Uri, overwrite: Boolean): ImportSourceResult {
        val uuid = UUID.randomUUID().toString()
        val tempDir = directoryProvider.createTempDir(uuid)
        FileUtilities.copyDirectory(context, uri, tempDir)

        val externalContainer = try {
            ResourceContainer.load(tempDir)
        } catch (e: Exception) {
            Logger.e(this::javaClass.name, "Could not import RC", e)
            return ImportSourceResult(
                success = false,
                hasConflict = false,
                error = e.message
            )
        }

        return try {
            library.open(externalContainer.slug)
            if (overwrite) {
                importSource(tempDir)
            } else {
                val conflictMessage = context.getString(
                    R.string.overwrite_content,
                    "${externalContainer.language.name} - ${externalContainer.project.name} - ${externalContainer.resource.name}"
                )
                ImportSourceResult(
                    success = false,
                    hasConflict = true,
                    error = conflictMessage,
                    uri = uri
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // no conflicts. import
            importSource(tempDir)
        } finally {
            FileUtilities.deleteQuietly(tempDir)
        }
    }

    private suspend fun importSource(dir: File): ImportSourceResult {
        return try {
            library.importResourceContainer(dir)
            ImportSourceResult(
                success = true,
                hasConflict = false
            )
        } catch (e: Exception) {
            Logger.e(this::javaClass.name, "Could not import RC", e)
            ImportSourceResult(
                success = false,
                hasConflict = false,
                error = e.message
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
    private fun importArchive(file: File, overwrite: Boolean = false): ImportFileResult {
        return when {
            file.isDirectory -> importArchiveDir(file, overwrite)
            else -> importArchiveFile(file, overwrite)
        }
    }

    @Throws(Exception::class)
    private fun importArchiveFile(archiveFile: File, overwrite: Boolean = false): ImportFileResult {
        return FileInputStream(archiveFile).use {
            val archiveDir = unzipFromStream(it)
            importArchiveDir(archiveDir, overwrite)
        }
    }

    @Throws(Exception::class)
    private fun importArchiveDir(dir: File, overwrite: Boolean = false): ImportFileResult {
        var importedSlug: String? = null
        var mergeConflict = false
        var alreadyExists = false
        try {
            val targetTranslationDirs = archiveImporter.importArchive(dir)
            for (newDir in targetTranslationDirs) {
                val newTargetTranslation = TargetTranslation.open(newDir) {
                    // Try to back up and delete corrupt project
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
                        // Try to back up and delete corrupt project
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
                                // Try to back up and delete corrupt project
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
                    TargetTranslation.open(localDir) {
                        // Try to back up and delete corrupt project
                        try {
                            backupRC.backupTargetTranslation(localDir)
                            translator.deleteTargetTranslation(localDir)
                        } catch (ex: java.lang.Exception) {
                            ex.printStackTrace()
                        }
                    }
                        ?.updateGenerator(platform.info.versionCode.toString())

                    importedSlug = targetTranslationId
                }
            }
        } catch (e: Exception) {
            throw e
        } finally {
            FileUtilities.deleteQuietly(dir)
        }

        return ImportFileResult(importedSlug, mergeConflict, alreadyExists)
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

    data class ImportFileResult(
        val importedSlug: String?,
        val mergeConflict: Boolean,
        val alreadyExists: Boolean
    ) {
        val isSuccess: Boolean
            get() {
                val success = !importedSlug.isNullOrEmpty()
                return success
            }
    }

    data class ImportFilesResult(
        val success: Boolean,
        val targetTranslations: List<TargetTranslation>,
        val conflictingTargetTranslations: List<TargetTranslation>
    )

    data class ImportSourceResult(
        val success: Boolean,
        val hasConflict: Boolean,
        val uri: Uri? = null,
        val error: String? = null
    )

    /**
     * returns the import result which includes:
     * the human-readable filePath
     * the success flag
     */
    data class ImportUriResult(
        val filePath: Uri,
        val readablePath: String,
        val importedSlug: String?,
        val success: Boolean,
        val hasMergeConflict: Boolean,
        val invalidFileName: Boolean,
        val alreadyExists: Boolean
    )
}