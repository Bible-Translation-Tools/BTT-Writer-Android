package com.door43.translationstudio.core

import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.SpannedString
import com.door43.translationstudio.rendering.USXtoUSFMConverter
import com.door43.translationstudio.tasks.PrintPDFTask
import com.door43.util.FileUtilities
import com.door43.util.Zip
import org.json.JSONArray
import org.json.JSONObject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Created by joel on 8/29/2015.
 */
class Translator(
    private val mContext: Context, private val profile: Profile?,
    /**
     * Returns the root directory to the target translations
     * @return
     */
    val path: File
) {
    val targetTranslations: Array<TargetTranslation>
        /**
         * Returns an array of all active translations
         * @return
         */
        get() {
            val translations: MutableList<TargetTranslation> = ArrayList()
            path.list { dir, filename ->
                if (!filename.equals("cache", ignoreCase = true) && File(
                        dir,
                        filename
                    ).isDirectory
                ) {
                    val translation = getTargetTranslation(filename)
                    if (translation != null) {
                        translations.add(translation)
                    }
                }
                false
            }

            return translations.toTypedArray<TargetTranslation>()
        }

    val targetTranslationIDs: Array<String>
        /**
         * Returns an array of all active translation IDs - this does not hold in memory each manifest.  Requires less memory to just get a count of items.
         * @return
         */
        get() {
            val translations: MutableList<String> = ArrayList()
            path.list { dir, filename ->
                if (!filename.equals("cache", ignoreCase = true) && File(
                        dir,
                        filename
                    ).isDirectory
                ) {
                    val translation = getTargetTranslation(filename)
                    if (translation != null) {
                        translations.add(translation.id)
                    }
                }
                false
            }

            return translations.toTypedArray<String>()
        }

    val targetTranslationFileNames: Array<String>
        /**
         * Returns an array of all translation File names - this does not verify the list.
         * @return
         */
        get() {
            val translations: MutableList<String> = ArrayList()
            path.list { dir, filename ->
                if (!filename.equals("cache", ignoreCase = true) && File(
                        dir,
                        filename
                    ).isDirectory
                ) {
                    translations.add(filename)
                }
                false
            }

            return translations.toTypedArray<String>()
        }

    private val localCacheDir: File
        /**
         * Returns the local translations cache directory.
         * This is where import and export operations can expand files.
         * @return
         */
        get() = File(path, "cache")

    /**
     * Creates a new Target Translation. If one already exists it will return it without changing anything.
     * @param nativeSpeaker the human translator
     * @param targetLanguage the language that is being translated into
     * @param projectSlug the project that is being translated
     * @param resourceType the type of translation that is occurring
     * @param resourceSlug the resource that is being created
     * @param translationFormat the format of the translated text
     * @return A new or existing Target Translation
     */
    fun createTargetTranslation(
        nativeSpeaker: NativeSpeaker?,
        targetLanguage: TargetLanguage,
        projectSlug: String?,
        resourceType: ResourceType?,
        resourceSlug: String?,
        translationFormat: TranslationFormat
    ): TargetTranslation? {
        // TRICKY: force deprecated formats to use new formats
        var translationFormat = translationFormat
        if (translationFormat == TranslationFormat.USX) {
            translationFormat = TranslationFormat.USFM
        } else if (translationFormat == TranslationFormat.DEFAULT) {
            translationFormat = TranslationFormat.MARKDOWN
        }

        val targetTranslationId = TargetTranslation.generateTargetTranslationId(
            targetLanguage.slug,
            projectSlug,
            resourceType,
            resourceSlug
        )
        val targetTranslation = getTargetTranslation(targetTranslationId)
        if (targetTranslation == null) {
            val targetTranslationDir = File(this.path, targetTranslationId)
            try {
                val pInfo = mContext.packageManager.getPackageInfo(mContext.packageName, 0)
                return TargetTranslation.create(
                    this.mContext,
                    nativeSpeaker,
                    translationFormat,
                    targetLanguage,
                    projectSlug,
                    resourceType,
                    resourceSlug,
                    pInfo,
                    targetTranslationDir
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return targetTranslation
    }

    private fun setTargetTranslationAuthor(targetTranslation: TargetTranslation?) {
        if (profile != null && targetTranslation != null) {
            var name = profile.fullName
            var email: String? = ""
            if (profile.gogsUser != null) {
                name = profile.gogsUser.fullName
                email = profile.gogsUser.email
            }
            targetTranslation.setAuthor(name, email)
        }
    }

    /**
     * Returns a target translation if it exists
     * @param targetTranslationId
     * @return
     */
    fun getTargetTranslation(targetTranslationId: String?): TargetTranslation? {
        return targetTranslationId?.let {
            val targetTranslationDir = File(path, targetTranslationId)
            val targetTranslation = TargetTranslation.open(targetTranslationDir)
            setTargetTranslationAuthor(targetTranslation)
            targetTranslation
        }
    }

    /**
     * Deletes a target translation from the device
     * @param targetTranslationId
     */
    fun deleteTargetTranslation(targetTranslationId: String?) {
        if (targetTranslationId != null) {
            val targetTranslationDir = File(path, targetTranslationId)
            FileUtilities.safeDelete(targetTranslationDir)
        }
    }

    /**
     * Deletes a target translation from the device
     * @param projectDir
     */
    fun deleteTargetTranslation(projectDir: File) {
        if (projectDir.exists()) {
            FileUtilities.safeDelete(projectDir)
        }
    }

    /**
     * creates a JSON object that contains the manifest.
     * @param targetTranslation
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun buildArchiveManifest(targetTranslation: TargetTranslation): JSONObject {
        targetTranslation.commit()

        // build manifest
        val manifestJson = JSONObject()
        val generatorJson = JSONObject()
        generatorJson.put("name", GENERATOR_NAME)
        val pInfo = mContext.packageManager.getPackageInfo(mContext.packageName, 0)
        generatorJson.put("build", pInfo.versionCode)
        manifestJson.put("generator", generatorJson)
        manifestJson.put("package_version", TSTUDIO_PACKAGE_VERSION)
        manifestJson.put("timestamp", Util.unixTime())
        val translationsJson = JSONArray()
        val translationJson = JSONObject()
        translationJson.put("path", targetTranslation.id)
        translationJson.put("id", targetTranslation.id)
        translationJson.put("commit_hash", targetTranslation.commitHash)
        translationJson.put("direction", targetTranslation.targetLanguageDirection)
        translationJson.put("target_language_name", targetTranslation.targetLanguageName)
        translationsJson.put(translationJson)
        manifestJson.put("target_translations", translationsJson)
        return manifestJson
    }

    /**
     * Exports a single target translation in .tstudio format to File
     * @param targetTranslation
     * @param outputFile
     */
    @Throws(Exception::class)
    fun exportArchive(targetTranslation: TargetTranslation?, outputFile: File?) {
        exportArchive(targetTranslation, Uri.fromFile(outputFile))
    }

    /**
     * Exports a single target translation in .tstudio format to OutputStream
     * @param targetTranslation
     * @param fileUri
     */
    @Throws(Exception::class)
    fun exportArchive(targetTranslation: TargetTranslation?, fileUri: Uri?) {
        if (targetTranslation == null) {
            throw Exception("Not a valid target translation")
        }

        try {
            targetTranslation.commitSync(".", false)
        } catch (e: Exception) {
            // it's not the end of the world if we cannot commit.
            e.printStackTrace()
        }

        val manifestJson = buildArchiveManifest(targetTranslation)
        val tempDir = File(localCacheDir, System.currentTimeMillis().toString() + "")
        try {
            tempDir.mkdirs()
            val manifestFile = File(tempDir, "manifest.json")
            manifestFile.createNewFile()
            FileUtilities.writeStringToFile(manifestFile, manifestJson.toString())

            mContext.contentResolver.openOutputStream(fileUri!!).use { out ->
                Zip.zipToStream(
                    arrayOf(manifestFile, targetTranslation.path), out
                )
            }
        } finally {
            FileUtilities.deleteQuietly(tempDir)
        }
    }

    @Throws(Exception::class)
    fun exportArchive(projectDir: File, outputFile: File) {
        if (!isValidArchiveExtension(outputFile.path)) {
            throw Exception("Output file must have '$TSTUDIO_EXTENSION' or '$ZIP_EXTENSION' extension")
        }
        if (!projectDir.exists()) {
            throw Exception("Project directory doesn't exist.")
        }
        val outputStream = FileOutputStream(outputFile)
        val out = BufferedOutputStream(outputStream)

        try {
            Zip.zipToStream(arrayOf(projectDir), out)
        } finally {
            FileUtilities.closeQuietly(out)
        }
    }

    /**
     * Imports a draft translation into a target translation.
     * A new target translation will be created if one does not already exist.
     * This is a lengthy operation and should be ran within a task
     * @param draftTranslation the draft translation to be imported
     * @param library
     * @return
     */
    fun importDraftTranslation(
        nativeSpeaker: NativeSpeaker?,
        draftTranslation: ResourceContainer,
        library: Door43Client
    ): TargetTranslation? {
        val targetLanguage = library.index().getTargetLanguage(draftTranslation.language.slug)
        // TRICKY: for now android only supports "regular" or "obs" "text" translations
        // TODO: we should technically check if the project contains more than one resource
        //  when determining if it needs a regular slug or not.
        val resourceSlug =
            if (draftTranslation.project.slug == "obs") "obs" else Resource.REGULAR_SLUG

        val format = TranslationFormat.parse(draftTranslation.contentMimeType)
        val translation = createTargetTranslation(
            nativeSpeaker,
            targetLanguage,
            draftTranslation.project.slug,
            ResourceType.TEXT,
            resourceSlug,
            format
        )

        // convert legacy usx format to usfm
        val convertToUSFM = format == TranslationFormat.USX

        try {
            if (translation != null) {
                // commit local changes to history
                translation.commitSync()

                // begin import
                translation.applyProjectTitleTranslation(
                    draftTranslation.readChunk("front", "title")
                )
                for (cSlug in draftTranslation.chapters()) {
                    val ct = translation.getChapterTranslation(cSlug)
                    translation.applyChapterTitleTranslation(
                        ct,
                        draftTranslation.readChunk(cSlug, "title")
                    )
                    translation.applyChapterReferenceTranslation(
                        ct,
                        draftTranslation.readChunk(cSlug, "reference")
                    )
                    for (fSlug in draftTranslation.chunks(cSlug)) {
                        val body = draftTranslation.readChunk(cSlug, fSlug)
                        val text = if (convertToUSFM) USXtoUSFMConverter.doConversion(body)
                            .toString() else body
                        translation.applyFrameTranslation(
                            translation.getFrameTranslation(cSlug, fSlug, format),
                            text
                        )
                    }
                }
                // TODO: 3/23/2016 also import the front and back matter along with project title
                translation.setParentDraft(draftTranslation)
                translation.commitSync()
            }
        } catch (e: IOException) {
            Logger.e(this.javaClass.name, "Failed to import target translation", e)
            // TODO: 1/20/2016 revert changes
        } catch (e: Exception) {
            Logger.e(
                this.javaClass.name,
                "Failed to save target translation before importing target translation",
                e
            )
        }
        return translation
    }

    /**
     * Imports a tstudio archive
     * @param file
     * @param overwrite - if true then local changes are clobbered
     * @return ImportResults object
     */
    /**
     * Imports a tstudio archive, uses default of merge, not overwrite
     * @param file
     * @return ImportResults object
     */
    @JvmOverloads
    @Throws(Exception::class)
    fun importArchive(file: File?, overwrite: Boolean = false): ImportResults {
        FileInputStream(file).use { input ->
            return importArchive(input, overwrite)
        }
    }

    /**
     * Imports a tstudio archive from an input stream
     * @param `in`
     * @param overwrite - if true then local changes are clobbered
     * @return ImportResults object
     */
    @Throws(Exception::class)
    fun importArchive(inputStream: InputStream, overwrite: Boolean): ImportResults {
        val archiveDir = File(localCacheDir, System.currentTimeMillis().toString() + "")
        var importedSlug: String? = null
        var mergeConflict = false
        var alreadyExists = false
        try {
            archiveDir.mkdirs()
            Zip.unzipFromStream(inputStream, archiveDir)

            val targetTranslationDirs = ArchiveImporter.importArchive(archiveDir)
            for (newDir in targetTranslationDirs) {
                val newTargetTranslation = TargetTranslation.open(newDir)
                if (newTargetTranslation != null) {
                    // TRICKY: the correct id is pulled from the manifest
                    // to avoid propagation of bad folder names
                    val targetTranslationId = newTargetTranslation.id
                    val localDir = File(path, targetTranslationId)
                    val localTargetTranslation = TargetTranslation.open(localDir)
                    alreadyExists = localTargetTranslation != null
                    if (alreadyExists && !overwrite) {
                        // commit local changes to history
                        localTargetTranslation?.commitSync()

                        // merge translations
                        try {
                            val mergeSuccess = localTargetTranslation!!.merge(newDir)
                            if (!mergeSuccess) {
                                mergeConflict = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            continue
                        }
                    } else {
                        // import new translation
                        FileUtilities.safeDelete(localDir) // in case local was an invalid target translation
                        FileUtilities.moveOrCopyQuietly(newDir, localDir)
                    }
                    // update the generator info. TRICKY: we re-open to get the updated manifest.
                    TargetTranslation.updateGenerator(mContext, TargetTranslation.open(localDir))

                    importedSlug = targetTranslationId
                }
            }
        } catch (e: Exception) {
            throw e
        } finally {
            FileUtilities.deleteQuietly(archiveDir)
        }

        return ImportResults(importedSlug, mergeConflict, alreadyExists)
    }

    /**
     * returns the import results which includes:
     * the target translation slug that was successfully imported
     * a flag indicating a merge conflict
     */
    inner class ImportResults internal constructor(
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

    /**
     * Exports a target translation as a pdf file
     * @param targetTranslation
     * @param outputFile
     */
    @Throws(Exception::class)
    fun exportPdf(
        library: Door43Client?, targetTranslation: TargetTranslation, format: TranslationFormat?,
        targetLanguageFontPath: String?, targetLanguageFontSize: Float, licenseFontPath: String?,
        imagesDir: File?, includeImages: Boolean, includeIncompleteFrames: Boolean,
        outputFile: File, task: PrintPDFTask?
    ) {
        val targetLanguageRtl = "rtl" == targetTranslation.targetLanguageDirection
        val printer = PdfPrinter(
            mContext, library, targetTranslation, format, targetLanguageFontPath,
            targetLanguageFontSize, targetLanguageRtl, licenseFontPath, imagesDir, task
        )
        printer.includeMedia(includeImages)
        printer.includeIncomplete(includeIncompleteFrames)
        val pdf = printer.print()
        if (pdf.exists()) {
            outputFile.delete()
            FileUtilities.moveOrCopyQuietly(pdf, outputFile)
            if (!outputFile.exists()) {
                Logger.e(TAG, "Could not move '$pdf' to '$outputFile'")
            }
        }
    }

    /**
     * This will move a target translation into the root dir.
     * Any existing target translation will be replaced
     * @param tempTargetTranslation
     * @throws IOException
     */
    @Throws(IOException::class)
    fun restoreTargetTranslation(tempTargetTranslation: TargetTranslation?) {
        if (tempTargetTranslation != null) {
            val destDir = File(path, tempTargetTranslation.id)
            FileUtilities.safeDelete(destDir)
            FileUtilities.moveOrCopyQuietly(tempTargetTranslation.path, destDir)
        }
    }

    /**
     * Ensures the name of the target translation directory matches the target translation id and corrects it if not
     * If the destination already exists the file path will not be changed
     * @param tt
     */
    fun normalizePath(tt: TargetTranslation): Boolean {
        if (tt.path.name != tt.id) {
            val dest = File(tt.path.parentFile, tt.id)
            if (!dest.exists()) {
                return FileUtilities.moveOrCopyQuietly(tt.path, dest)
            }
        }
        return false
    }

    /**
     * Check if file extension is supported archive extension
     * @param fileName
     * @return boolean
     */
    private fun isValidArchiveExtension(fileName: String): Boolean {
        val isTstudio =
            FileUtilities.getExtension(fileName).equals(TSTUDIO_EXTENSION, ignoreCase = true)
        val isZip = FileUtilities.getExtension(fileName).equals(ZIP_EXTENSION, ignoreCase = true)

        return isTstudio || isZip
    }

    companion object {
        private const val TSTUDIO_PACKAGE_VERSION = 2
        private const val GENERATOR_NAME = "ts-android"
        const val TSTUDIO_EXTENSION: String = "tstudio"
        const val ZIP_EXTENSION: String = "zip"
        const val USFM_EXTENSION: String = "usfm"
        val TAG: String = Translator::class.java.name

        /**
         * Compiles all the editable text back into source that could be either USX or USFM.  It replaces
         * the displayed text in spans with their mark-ups.
         * @param text
         * @return
         */
        @JvmStatic
        fun compileTranslation(text: Editable): String {
            val compiledString = StringBuilder()
            var next: Int
            var lastIndex = 0
            var i = 0
            while (i < text.length) {
                next = text.nextSpanTransition(i, text.length, SpannedString::class.java)
                val verses = text.getSpans(i, next, SpannedString::class.java)
                for (s in verses) {
                    val sStart = text.getSpanStart(s)
                    val sEnd = text.getSpanEnd(s)
                    // attach preceding text
                    if ((lastIndex >= text.length) or (sStart >= text.length)) {
                        // out of bounds
                    }
                    compiledString.append(text.toString().substring(lastIndex, sStart))
                    // explode span
                    compiledString.append(s.toString())
                    lastIndex = sEnd
                }
                i = next
            }
            // grab the last bit of text
            compiledString.append(text.toString().substring(lastIndex, text.length))
            return compiledString.toString().trim { it <= ' ' }
        }

        /**
         * Compiles all the spannable text back into source that could be either USX or USFM.  It replaces
         * the displayed text in spans with their mark-ups.
         * @param text
         * @return
         */
        @JvmStatic
        fun compileTranslationSpanned(text: SpannedString): String {
            val compiledString = StringBuilder()
            var next: Int
            var lastIndex = 0
            var i = 0
            while (i < text.length) {
                next = text.nextSpanTransition(i, text.length, SpannedString::class.java)
                val verses = text.getSpans(i, next, SpannedString::class.java)
                for (s in verses) {
                    val sStart = text.getSpanStart(s)
                    val sEnd = text.getSpanEnd(s)
                    // attach preceding text
                    if ((lastIndex >= text.length) or (sStart >= text.length)) {
                        // out of bounds
                    }
                    compiledString.append(text.toString().substring(lastIndex, sStart))
                    // explode span
                    compiledString.append(s.toString())
                    lastIndex = sEnd
                }
                i = next
            }
            // grab the last bit of text
            compiledString.append(text.toString().substring(lastIndex, text.length))
            return compiledString.toString().trim { it <= ' ' }
        }
    }
}
