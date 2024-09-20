package com.door43.usecases

import com.door43.translationstudio.core.Translator
import java.io.File
import javax.inject.Inject

class ImportProjects @Inject constructor(
    private val translator: Translator
) {
    fun execute(
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
}