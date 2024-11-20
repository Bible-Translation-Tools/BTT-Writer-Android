package com.door43.translationstudio.core

import android.content.Context
import android.text.Editable
import android.text.SpannedString
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getPrivatePref
import com.door43.data.setPrivatePref
import com.door43.translationstudio.rendering.USXtoUSFMConverter
import com.door43.usecases.BackupRC
import com.door43.util.FileUtilities
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Created by joel on 8/29/2015.
 */
class Translator (
    private val context: Context,
    private val profile: Profile,
    private val prefRepository: IPreferenceRepository,
    private val directoryProvider: IDirectoryProvider,
    private val archiveImporter: ArchiveImporter,
    private val backupRC: BackupRC,
    private val library: Door43Client
) {
    /**
     * Returns the root directory to the target translations
     */
    val path
        get() = directoryProvider.translationsDir

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
        get() = prefRepository.getPrivatePref<String>("last_translation")
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
            val targetTranslation = TargetTranslation.open(targetTranslationDir) {
                // Try to backup and delete corrupt project
                try {
                    backupRC.backupTargetTranslation(targetTranslationDir)
                    deleteTargetTranslation(targetTranslationDir)
                } catch (ex: java.lang.Exception) {
                    ex.printStackTrace()
                }
            }
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
     * Imports a draft translation into a target translation.
     * A new target translation will be created if one does not already exist.
     * This is a lengthy operation and should be ran within a task
     * @param draftTranslation the draft translation to be imported
     * @return
     */
    fun importDraftTranslation(
        nativeSpeaker: NativeSpeaker?,
        draftTranslation: ResourceContainer,
    ): TargetTranslation? {
        val targetLanguage = library.index.getTargetLanguage(draftTranslation.language.slug)
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

    fun getConflictingTargetTranslation(file: File): TargetTranslation? {
        var conflictingTranslation: TargetTranslation? = null

        val targetTranslation = TargetTranslation.open(file, null)
        if (targetTranslation != null) {
            // TRICKY: the correct id is pulled from the manifest to avoid propagating bad folder names
            val targetTranslationId = targetTranslation.id
            val destTargetTranslationDir = File(path, targetTranslationId)

            // check if target already exists
            conflictingTranslation = TargetTranslation.open(destTargetTranslationDir, null)
        }
        return conflictingTranslation
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
            return TranslationViewMode.valueOf(modeName.uppercase(Locale.getDefault()))
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
        return prefRepository.getPrivatePref<String>(LAST_FOCUS_CHAPTER + targetTranslationId)
    }

    /**
     * Returns the id of the frame that was last in focus for this target translation
     * @param targetTranslationId
     * @return
     */
    fun getLastFocusFrameId(targetTranslationId: String): String? {
        return prefRepository.getPrivatePref<String>(LAST_FOCUS_FRAME + targetTranslationId)
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
            prefRepository.setPrivatePref<String>(SELECTED_SOURCE_TRANSLATION + targetTranslationId, null)
        }
    }

    /**
     * Returns the selected open source translation tab on the target translation
     * If there is no selection the first open tab will be set as the selected tab
     * @param targetTranslationId
     * @return
     */
    fun getSelectedSourceTranslationId(targetTranslationId: String): String? {
        var selectedSourceTranslationId = prefRepository.getPrivatePref<String>(
            SELECTED_SOURCE_TRANSLATION + targetTranslationId
        )
        if (selectedSourceTranslationId.isNullOrEmpty()) {
            // default to first tab
            val openSourceTranslationIds = prefRepository.getOpenSourceTranslations(targetTranslationId)
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
        prefRepository.setPrivatePref<String>(SELECTED_SOURCE_TRANSLATION + targetTranslationId, null)
        prefRepository.setPrivatePref<String>(OPEN_SOURCE_TRANSLATIONS + targetTranslationId, null)
        prefRepository.setPrivatePref<String>(LAST_FOCUS_FRAME + targetTranslationId, null)
        prefRepository.setPrivatePref<String>(LAST_FOCUS_CHAPTER + targetTranslationId, null)
        prefRepository.setPrivatePref<String>(LAST_VIEW_MODE + targetTranslationId, null)
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
        val TAG: String = Translator::class.java.name

        const val OPEN_SOURCE_TRANSLATIONS = "open_source_translations_"
        const val LAST_VIEW_MODE = "last_view_mode_"
        const val LAST_FOCUS_CHAPTER = "last_focus_chapter_"
        const val LAST_FOCUS_FRAME = "last_focus_frame_"
        const val SELECTED_SOURCE_TRANSLATION = "selected_source_translation_"

        const val EXTRA_SOURCE_DRAFT_TRANSLATION_ID: String = "extra_source_translation_id"
        const val EXTRA_TARGET_TRANSLATION_ID: String = "extra_target_translation_id"
        const val EXTRA_START_WITH_MERGE_FILTER: String = "start_with_merge_filter"
        const val EXTRA_CHAPTER_ID: String = "extra_chapter_id"
        const val EXTRA_FRAME_ID: String = "extra_frame_id"
        const val EXTRA_VIEW_MODE: String = "extra_view_mode_id"

        private const val TSTUDIO_PACKAGE_VERSION = 2
        private const val GENERATOR_NAME = "ts-android"
        const val TSTUDIO_EXTENSION = "tstudio"
        const val ZIP_EXTENSION = "zip"
        const val USFM_EXTENSION = "usfm"
        const val TXT_EXTENSION = "usfm"
        const val PDF_EXTENSION = "pdf"

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
    }
}
