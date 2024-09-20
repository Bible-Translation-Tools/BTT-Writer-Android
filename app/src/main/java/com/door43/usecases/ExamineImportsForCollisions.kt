package com.door43.usecases

import android.content.Context
import android.net.Uri
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.core.ArchiveDetails
import com.door43.translationstudio.core.Translator
import com.door43.usecases.ExamineImportsForCollisions.Result
import com.door43.util.FileUtilities.deleteQuietly
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import java.io.File
import javax.inject.Inject

class ExamineImportsForCollisions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: Door43Client,
    private val translator: Translator,
    private val directoryProvider: IDirectoryProvider
) {

    data class Result(
        val contentUri: Uri,
        val success: Boolean,
        val alreadyPresent: Boolean,
        val projectsFound: String?,
        val projectsFolder: File?
    )

    fun execute(contentUri: Uri): Result {
        var success = false
        var alreadyPresent = false
        var projectsFound: String? = null
        var projectsFolder: File? = null

        try {
            context.contentResolver.openInputStream(contentUri).use { input ->
                if (input != null) {
                    projectsFolder = directoryProvider.createTempFile(
                        "targettranslation",
                        "." + Translator.TSTUDIO_EXTENSION
                    )
                    projectsFolder!!.outputStream().use { output ->
                        input.copyTo(output)
                        success = true
                    }

                    val details = ArchiveDetails.newInstance(
                        projectsFolder,
                        deviceLanguageCode,
                        library
                    )
                    projectsFound = ""
                    for (td in details.targetTranslationDetails) {
                        projectsFound += td.projectName + " - " + td.targetLanguageName + ", "

                        val targetTranslationId = td.targetTranslationSlug
                        val localTargetTranslation = translator.getTargetTranslation(targetTranslationId)
                        if ((localTargetTranslation != null)) {
                            alreadyPresent = true
                        }
                    }
                    projectsFound = projectsFound!!.replace(", $".toRegex(), "")
                    success = true
                }
            }
        } catch (e: Exception) {
            Logger.e(
                this.javaClass.simpleName,
                "Error processing input file: $contentUri"
            )
        }

        return Result(
            contentUri,
            success,
            alreadyPresent,
            projectsFound,
            projectsFolder
        )
    }
}

fun Result.cleanup() {
    projectsFolder?.let { deleteQuietly(it) }
}