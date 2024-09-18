package com.door43.usecases

import com.door43.translationstudio.core.Translator
import java.io.File
import javax.inject.Inject

class Import @Inject constructor(
    private val translator: Translator
) {
    fun importProjects(
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