package com.door43.usecases

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.setPrivatePref
import com.door43.translationstudio.core.ArchiveDetails
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.translationstudio.core.Translator
import com.door43.util.DateUtils
import com.door43.util.FileUtilities
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.io.File
import javax.inject.Inject

class BackupRC @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directoryProvider: IDirectoryProvider,
    private val migrator: TargetTranslationMigrator,
    private val exportProjects: ExportProjects,
    private val profile: Profile,
    private val library: Door43Client,
    private val preferenceRepository: IPreferenceRepository
) {
    fun backupResourceContainer(translation: Translation): File {
        val dest = File(
            directoryProvider.backupsDir,
            translation.resourceContainerSlug + "." + ResourceContainer.fileExtension
        )
        library.exportResourceContainer(
            dest,
            translation.language.slug,
            translation.project.slug,
            translation.resource.slug
        )
        return dest
    }

    @Throws(Exception::class)
    fun backupTargetTranslation(
        targetTranslation: TargetTranslation?,
        orphaned: Boolean,
        updateTimestamp: Boolean = false
    ): Boolean {
        if (targetTranslation != null) {
            var name = targetTranslation.id
            val datetime = DateUtils.getCurrentDateTime()
            if (orphaned) {
                name += ".$datetime"
            }

            var archiveExtension = Translator.TSTUDIO_EXTENSION
            if (orphaned) {
                archiveExtension = Translator.ZIP_EXTENSION
            }

            // backup locations
            val backup = File(
                directoryProvider.backupsDir,
                "$name.$archiveExtension"
            )

            // check if we need to backup
            if (!orphaned) {
                val details = ArchiveDetails.Builder(context, directoryProvider, migrator, library)
                    .fromFile(backup, "en")
                    .build()

                // TRICKY: we only generate backups with a single target translation inside.
                if (getCommitHash(details) == targetTranslation.commitHash) {
                    return false
                }
            }

            // run backup
            var temp: File? = null
            try {
                temp = directoryProvider.createTempFile(name, ".$archiveExtension")
                targetTranslation.setDefaultContributor(profile.nativeSpeaker)
                exportProjects.exportProject(targetTranslation, temp)
                if (temp.exists() && temp.isFile) {
                    // copy into backup locations
                    backup.parentFile?.mkdirs()

                    // Save datetime to preferences for this translation id,
                    // so later this value could be read in project details
                    if (updateTimestamp) {
                        val trId = targetTranslation.id
                        preferenceRepository.setPrivatePref(
                            preferenceRepository.lastBackup + trId,
                            System.currentTimeMillis()
                        )
                    }

                    FileUtilities.copyFile(temp, backup)
                    return true
                }
            } finally {
                FileUtilities.deleteQuietly(temp)
            }
        }
        return false
    }

    /**
     * Creates a backup of a project directory in all the right places
     * @param projectDir the project directory that will be backed up
     * @return true if the backup was actually performed
     */
    @Throws(Exception::class)
    fun backupTargetTranslation(projectDir: File): Boolean {
        val datetime = DateUtils.getCurrentDateTime()
        val name = "${projectDir.name}.$datetime"

        // backup locations
        val backup = File(
            directoryProvider.backupsDir,
            "$name.${Translator.ZIP_EXTENSION}"
        )

        // run backup
        var temp: File? = null
        try {
            temp = directoryProvider.createTempFile(
                name, ".${Translator.ZIP_EXTENSION}"
            )
            exportProjects.exportProject(projectDir, temp)
            if (temp.exists() && temp.isFile) {
                // copy into backup locations
                backup.parentFile?.mkdirs()

                FileUtilities.copyFile(temp, backup)
                return true
            }
        } finally {
            FileUtilities.deleteQuietly(temp)
        }

        return false
    }

    /**
     * safe fetch of commit hash
     * @param details
     * @return
     */
    private fun getCommitHash(details: ArchiveDetails?): String {
        return details?.targetTranslationDetails?.firstOrNull()?.commitHash ?: ""
    }
}