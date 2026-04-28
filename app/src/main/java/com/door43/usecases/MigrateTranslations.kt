package com.door43.usecases

import android.content.Context
import android.net.Uri
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.util.FileUtilities
import java.io.File

class MigrateTranslations(
    private val context: Context,
    private val importProjects: ImportProjects,
    private val directoryProvider: IDirectoryProvider,
    private val targetTranslationMigrator: TargetTranslationMigrator
) {
    fun execute(
        appDataFolder: Uri,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ) {
        // Migrate translations

        val tempTranslations = directoryProvider.createTempDir("translations")
        FileUtilities.copyDirectory(
            context,
            appDataFolder,
            tempTranslations,
            directoryProvider.translationsDir.name
        )

        migrateTranslations(tempTranslations, onProgress)
        importTranslations(tempTranslations, onProgress)

        // Migrate backups
        val tempBackups = directoryProvider.createTempDir("backups")
        FileUtilities.copyDirectory(
            context,
            appDataFolder,
            tempBackups,
            directoryProvider.backupsDir.name
        )
        copyBackups(tempBackups, onProgress)
    }

    private fun migrateTranslations(
        translationsDir: File,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ) {
        if (translationsDir.isDirectory) {
            translationsDir.listFiles()?.forEach { file ->
                if (file.name == "cache") return@forEach
                if (file.isDirectory) {
                    onProgress(
                        -1f,
                        context.getString(R.string.migrating_translation, file.name)
                    )
                    targetTranslationMigrator.migrate(file)
                }
            }
        }
    }

    private fun importTranslations(
        translationsDir: File,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ) {
        if (translationsDir.isDirectory) {
            val translations = arrayListOf<File>()
            translationsDir.listFiles()?.forEach { file ->
                if (file.name == "cache") return@forEach
                if (file.isDirectory) {
                    translations.add(file)
                }
            }
            importProjects.importProjects(translations, false, onProgress)
            FileUtilities.deleteQuietly(translationsDir)
        }
    }

    private fun copyBackups(
        backupsDir: File,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ) {
        if (backupsDir.isDirectory) {
            backupsDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val destFile = File(directoryProvider.backupsDir, file.name)
                    FileUtilities.copyFile(file, destFile)
                    onProgress(
                        -1f,
                        context.getString(R.string.copying_file, destFile.name)
                    )
                }
            }
            FileUtilities.deleteQuietly(backupsDir)
        }
    }
}