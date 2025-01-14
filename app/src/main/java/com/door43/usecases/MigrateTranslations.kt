package com.door43.usecases

import android.content.Context
import android.net.Uri
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.util.FileUtilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class MigrateTranslations @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importProjects: ImportProjects,
    private val directoryProvider: IDirectoryProvider
) {
    fun execute(appDataFolder: Uri, progressListener: OnProgressListener? = null) {
        // Migrate translations

        val tempTranslations = directoryProvider.createTempDir("translations")
        FileUtilities.copyDirectory(
            context,
            appDataFolder,
            tempTranslations,
            directoryProvider.translationsDir.name
        )
        importTranslations(tempTranslations, progressListener)

        // Migrate backups
        val tempBackups = directoryProvider.createTempDir("backups")
        FileUtilities.copyDirectory(
            context,
            appDataFolder,
            tempBackups,
            directoryProvider.backupsDir.name
        )
        copyBackups(tempBackups, progressListener)
    }

    private fun importTranslations(translationsDir: File, progressListener: OnProgressListener? = null) {
        if (translationsDir.isDirectory) {
            val translations = arrayListOf<File>()
            translationsDir.listFiles()?.forEach { file ->
                if (file.name == "cache") return@forEach
                if (file.isDirectory) {
                    translations.add(file)
                }
            }
            importProjects.importProjects(translations, false, progressListener)
            FileUtilities.deleteQuietly(translationsDir)
        }
    }

    private fun copyBackups(backupsDir: File, progressListener: OnProgressListener? = null) {
        if (backupsDir.isDirectory) {
            backupsDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val destFile = File(directoryProvider.backupsDir, file.name)
                    FileUtilities.copyFile(file, destFile)
                    progressListener?.onProgress(
                        -1,
                        100,
                        context.getString(R.string.copying_file, destFile.name)
                    )
                }
            }
            FileUtilities.deleteQuietly(backupsDir)
        }
    }
}