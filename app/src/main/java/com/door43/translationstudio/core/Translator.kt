package com.door43.translationstudio.core

import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.SpannedString
import androidx.annotation.Nullable
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.rendering.USXtoUSFMConverter
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
import java.util.Locale
import javax.inject.Inject

/**
 * Created by joel on 8/29/2015.
 */
class Translator @Inject constructor(
    private val context: Context,
    private val profile: Profile,
    private val prefRepository: IPreferenceRepository,
    private val library: Door43Client,
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

    var lastFocusTargetTranslation: String?
        /**
         * Returns the last focused target translation
         * @return
         */
        get() = prefRepository.getPrivatePref("last_translation", null)
        /**
         * Sets the last focused target translation
         * @param targetTranslationId
         */
        set(targetTranslationId) {
            prefRepository.setPrivatePref("last_translation", targetTranslationId)
        }

    private val localCacheDir: File
        /**
         * Returns the local translations cache directory.
         * This is where import and export operations can expand files.
         * @return
         */
        get() = File(path, "cache")

    /**
     * for keeping track of project that has changed
     * @param targetTranslationId
     */
    var notifyTargetTranslationWithUpdates: String? = null

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
        var format = translationFormat
        if (format == TranslationFormat.USX) {
            format = TranslationFormat.USFM
        } else if (format == TranslationFormat.DEFAULT) {
            format = TranslationFormat.MARKDOWN
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
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                return TargetTranslation.create(
                    this.context,
                    nativeSpeaker,
                    format,
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
        if (profile.loggedIn && targetTranslation != null) {
            var name = profile.fullName
            var email: String? = ""
            profile.gogsUser?.let {
                name = it.fullName
                email = it.email
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
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
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

            context.contentResolver.openOutputStream(fileUri!!).use { out ->
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
                    TargetTranslation.updateGenerator(context, TargetTranslation.open(localDir))

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
     * Returns an array of open source translation tabs on a target translation
     * @param targetTranslationId
     * @return
     */
    fun getOpenSourceTranslations(targetTranslationId: String): Array<String?> {
        val idSet = prefRepository.getPrivatePref(
            OPEN_SOURCE_TRANSLATIONS + targetTranslationId,
            ""
        )?.trim()

        if (idSet.isNullOrEmpty()) {
            return arrayOfNulls(0)
        } else {
            val ids: Array<String?> =
                idSet.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()
            for (i in ids.indices) {
                ids[i] = Migration.migrateSourceTranslationSlug(ids[i])
            }
            return ids
        }
    }

    /**
     * Adds a source translation to the list of open tabs on a target translation
     * @param targetTranslationId
     * @param sourceTranslationId
     */
    fun addOpenSourceTranslation(targetTranslationId: String, sourceTranslationId: String?) {
        if (!sourceTranslationId.isNullOrEmpty()) {
            val sourceTranslationIds = getOpenSourceTranslations(targetTranslationId)
            var newIdSet: String? = ""
            for (id in sourceTranslationIds) {
                if (id != sourceTranslationId) {
                    newIdSet += "$id|"
                }
            }
            newIdSet += sourceTranslationId

            prefRepository.setPrivatePref(
                OPEN_SOURCE_TRANSLATIONS + targetTranslationId,
                newIdSet
            )
        }
    }

    /**
     * Returns the last view mode of the target translation.
     * The default view mode will be returned if there is no recorded last view mode
     *
     * @param targetTranslationId
     * @return
     */
    fun getLastViewMode(targetTranslationId: String): TranslationViewMode {
        try {
            val modeName = prefRepository.getPrivatePref(
                LAST_VIEW_MODE + targetTranslationId,
                TranslationViewMode.READ.name
            )
            return TranslationViewMode.valueOf(modeName!!.uppercase(Locale.getDefault()))
        } catch (e: Exception) {
        }
        return TranslationViewMode.READ
    }

    /**
     * Sets the last opened view mode for a target translation
     * @param targetTranslationId
     * @param viewMode
     */
    fun setLastViewMode(targetTranslationId: String, viewMode: TranslationViewMode) {
        prefRepository.setPrivatePref(
            LAST_VIEW_MODE + targetTranslationId,
            viewMode.name.uppercase(Locale.getDefault())
        )
    }

    /**
     * Sets the last focused chapter and frame for a target translation
     * @param targetTranslationId
     * @param chapterId
     * @param frameId
     */
    fun setLastFocus(targetTranslationId: String, chapterId: String?, frameId: String?) {
        prefRepository.setPrivatePref(LAST_FOCUS_CHAPTER + targetTranslationId, chapterId)
        prefRepository.setPrivatePref(LAST_FOCUS_FRAME + targetTranslationId, frameId)
        lastFocusTargetTranslation = targetTranslationId
    }

    /**
     * Returns the id of the chapter that was last in focus for this target translation
     * @param targetTranslationId
     * @return
     */
    fun getLastFocusChapterId(targetTranslationId: String): String? {
        return prefRepository.getPrivatePref(LAST_FOCUS_CHAPTER + targetTranslationId, null)
    }

    /**
     * Returns the id of the frame that was last in focus for this target translation
     * @param targetTranslationId
     * @return
     */
    fun getLastFocusFrameId(targetTranslationId: String): String? {
        return prefRepository.getPrivatePref(LAST_FOCUS_FRAME + targetTranslationId, null)
    }

    /**
     * Removes a source translation from the list of open tabs on a target translation
     * @param targetTranslationId
     * @param sourceTranslationId
     */
    fun removeOpenSourceTranslation(targetTranslationId: String, sourceTranslationId: String?) {
        if (!sourceTranslationId.isNullOrEmpty()) {
            val sourceTranslationIds = getOpenSourceTranslations(targetTranslationId)
            var newIdSet: String? = ""
            for (id in sourceTranslationIds) {
                if (id != sourceTranslationId) {
                    if (newIdSet!!.isEmpty()) {
                        newIdSet = id
                    } else {
                        newIdSet += "|$id"
                    }
                } else if (id == getSelectedSourceTranslationId(targetTranslationId)) {
                    // unset the selected tab if it is removed
                    setSelectedSourceTranslation(targetTranslationId, null)
                }
            }
            prefRepository.setPrivatePref(OPEN_SOURCE_TRANSLATIONS + targetTranslationId, newIdSet)
        }
    }

    /**
     * Sets or removes the selected open source translation tab on a target translation
     * @param targetTranslationId
     * @param sourceTranslationId if null the selection will be unset
     */
    fun setSelectedSourceTranslation(
        targetTranslationId: String,
        sourceTranslationId: String?
    ) {
        if (!sourceTranslationId.isNullOrEmpty()) {
            prefRepository.setPrivatePref(
                SELECTED_SOURCE_TRANSLATION + targetTranslationId,
                Migration.migrateSourceTranslationSlug(sourceTranslationId)
            )
        } else {
            prefRepository.setPrivatePref(SELECTED_SOURCE_TRANSLATION + targetTranslationId, null)
        }
    }

    /**
     * Returns the selected open source translation tab on the target translation
     * If there is no selection the first open tab will be set as the selected tab
     * @param targetTranslationId
     * @return
     */
    fun getSelectedSourceTranslationId(targetTranslationId: String): String? {
        var selectedSourceTranslationId =
            prefRepository.getPrivatePref(SELECTED_SOURCE_TRANSLATION + targetTranslationId, null)
        if (selectedSourceTranslationId.isNullOrEmpty()) {
            // default to first tab
            val openSourceTranslationIds = getOpenSourceTranslations(targetTranslationId)
            if (openSourceTranslationIds.isNotEmpty()) {
                selectedSourceTranslationId = openSourceTranslationIds[0]
                setSelectedSourceTranslation(targetTranslationId, selectedSourceTranslationId)
            }
        }
        return Migration.migrateSourceTranslationSlug(selectedSourceTranslationId)
    }

    /**
     * Removes all settings for a target translation
     * @param targetTranslationId
     */
    fun clearTargetTranslationSettings(targetTranslationId: String) {
        prefRepository.setPrivatePref(SELECTED_SOURCE_TRANSLATION + targetTranslationId, null)
        prefRepository.setPrivatePref(OPEN_SOURCE_TRANSLATIONS + targetTranslationId, null)
        prefRepository.setPrivatePref(LAST_FOCUS_FRAME + targetTranslationId, null)
        prefRepository.setPrivatePref(LAST_FOCUS_CHAPTER + targetTranslationId, null)
        prefRepository.setPrivatePref(LAST_VIEW_MODE + targetTranslationId, null)
    }

    /**
     * A temporary utility to retrieve the target language used in a target translation.
     * if the language does not exist it will be added as a temporary language if possible
     * @param t
     * @return
     */
    @Deprecated("")
    fun languageFromTargetTranslation(t: TargetTranslation): TargetLanguage? {
        var language = library.index.getTargetLanguage(t.targetLanguageId)
        if (language == null && t.targetLanguageId.isEmpty()) {
            val name = t.targetLanguageName.ifEmpty { t.targetLanguageId }
            val direction =
                if (t.targetLanguageDirection == null) "ltr" else t.targetLanguageDirection
            language = TargetLanguage(
                t.targetLanguageId,
                name,
                "",
                direction,
                "unknown",
                false
            )
            try {
                library.index.addTempTargetLanguage(language)
            } catch (e: Exception) {
                language = null
                e.printStackTrace()
            }
        }
        return language
    }

    companion object {
        private const val OPEN_SOURCE_TRANSLATIONS = "open_source_translations_"
        private const val LAST_VIEW_MODE = "last_view_mode_"
        private const val LAST_FOCUS_CHAPTER = "last_focus_chapter_"
        private const val LAST_FOCUS_FRAME = "last_focus_frame_"
        private const val SELECTED_SOURCE_TRANSLATION = "selected_source_translation_"

        const val EXTRA_SOURCE_DRAFT_TRANSLATION_ID: String = "extra_source_translation_id"
        const val EXTRA_TARGET_TRANSLATION_ID: String = "extra_target_translation_id"
        const val EXTRA_START_WITH_MERGE_FILTER: String = "start_with_merge_filter"
        const val EXTRA_CHAPTER_ID: String = "extra_chapter_id"
        const val EXTRA_FRAME_ID: String = "extra_frame_id"
        const val EXTRA_VIEW_MODE: String = "extra_view_mode_id"

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
            return compiledString.toString().trim()
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
            return compiledString.toString().trim()
        }

        /**
         * Check if file extension is supported archive extension
         * @param fileName
         * @return boolean
         */
        fun isValidArchiveExtension(fileName: String): Boolean {
            val isTstudio =
                FileUtilities.getExtension(fileName).equals(TSTUDIO_EXTENSION, ignoreCase = true)
            val isZip = FileUtilities.getExtension(fileName).equals(ZIP_EXTENSION, ignoreCase = true)

            return isTstudio || isZip
        }
    }
}
