package com.door43.translationstudio.core

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.data.ILanguageRequestRepository
import com.door43.translationstudio.rendering.USXtoUSFMConverter
import com.door43.util.FileUtilities.copyInputStreamToFile
import com.door43.util.FileUtilities.deleteQuietly
import com.door43.util.FileUtilities.moveOrCopyQuietly
import com.door43.util.FileUtilities.readFileToString
import com.door43.util.FileUtilities.safeDelete
import com.door43.util.FileUtilities.writeStringToFile
import com.door43.util.Manifest
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.Locale
import javax.inject.Inject

/**
 * Created by joel on 11/4/2015.
 */
class TargetTranslationMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directoryProvider: IDirectoryProvider,
    private val languageRequestRepository: ILanguageRequestRepository,
    private val library: Door43Client
) {

    companion object {
        private const val MANIFEST_FILE = "manifest.json"
        const val LICENSE: String = "LICENSE"
        const val TAG: String = "TargetTranslationMigrator"
    }

    /**
     * Performs a migration on a manifest object.
     * We just throw it into a temporary directory and run the normal migration on it.
     * @param manifestJson
     * @return
     */
    fun migrateManifest(manifestJson: JSONObject): JSONObject? {
        val tempDir = directoryProvider.createTempDir(System.currentTimeMillis().toString())
        // TRICKY: the migration can change the name of the translation dir so we nest it to avoid conflicts.
        val fakeTranslationDir = File(tempDir, "translation")
        fakeTranslationDir.mkdirs()
        var migratedManifest: JSONObject? = null
        try {
            val manifestFile = File(fakeTranslationDir, MANIFEST_FILE)
            writeStringToFile(manifestFile, manifestJson.toString())
            migrate(fakeTranslationDir, manifestFile)
            migratedManifest = JSONObject(
                readFileToString(manifestFile)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // clean up
            deleteQuietly(tempDir)
        }
        return migratedManifest
    }

    /**
     * Performs necessary migration operations on a target translation
     * @param targetTranslationDir
     * @return the target translation dir. Null if the migration failed
     */
    fun migrate(
        targetTranslationDir: File,
        manifestFile: File = File(targetTranslationDir, MANIFEST_FILE)
    ): File? {
        var migratedDir: File?
        try {
            val manifest = JSONObject(readFileToString(manifestFile))
            var packageVersion = 2 // default to version 2 if no package version is available
            if (manifest.has("package_version")) {
                packageVersion = manifest.getInt("package_version")
            }
            migratedDir = when (packageVersion) {
                2 -> {
                    v2(targetTranslationDir)
                    v3(targetTranslationDir)
                    v4(targetTranslationDir)
                    v5(targetTranslationDir)
                    v6(targetTranslationDir)
                    v7(targetTranslationDir)
                }
                3 -> {
                    v3(targetTranslationDir)
                    v4(targetTranslationDir)
                    v5(targetTranslationDir)
                    v6(targetTranslationDir)
                    v7(targetTranslationDir)
                }
                4 -> {
                    v4(targetTranslationDir)
                    v5(targetTranslationDir)
                    v6(targetTranslationDir)
                    v7(targetTranslationDir)
                }
                5 -> {
                    v5(targetTranslationDir)
                    v6(targetTranslationDir)
                    v7(targetTranslationDir)
                }
                6 -> {
                    v6(targetTranslationDir)
                    v7(targetTranslationDir)
                }
                7 -> v7(targetTranslationDir)
                else -> targetTranslationDir
            }
            if (!validateTranslationType(targetTranslationDir)) {
                migratedDir = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            migratedDir = null
        }
        if (migratedDir != null) {
            // import new language requests
            val tt = TargetTranslation.open(targetTranslationDir, null)
            if (tt != null) {
                val newRequest = tt.getNewLanguageRequest(context)
                if (newRequest != null) {
                    val approvedTargetLanguage = library.index.getApprovedTargetLanguage(
                        newRequest.tempLanguageCode
                    )
                    if (approvedTargetLanguage != null) {
                        // this language request has already been approved so let's migrate it
                        try {
                            tt.setNewLanguageRequest(null)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                        val originalTargetLanguage = tt.targetLanguage
                        tt.changeTargetLanguage(approvedTargetLanguage)
                        if (tt.normalizePath()) {
                            Logger.i(
                                TAG,
                                "Migrated target language of target translation " + tt.id + " to " + approvedTargetLanguage.slug
                            )
                        } else {
                            // revert if normalization failed
                            tt.changeTargetLanguage(originalTargetLanguage)
                        }
                    } else {
                        val existingRequest = languageRequestRepository.getNewLanguageRequest(newRequest.tempLanguageCode)
                        if (existingRequest == null) {
                            // we don't have this language request
                            Logger.i(
                                TAG,
                                "Importing language request " + newRequest.tempLanguageCode + " from " + tt.id
                            )
                            languageRequestRepository.addNewLanguageRequest(newRequest)
                        } else {
                            // we already have this language request
                            if (existingRequest.submittedAt > 0 && newRequest.submittedAt == 0L) {
                                // indicated this language request has been submitted
                                newRequest.submittedAt = existingRequest.submittedAt
                                try {
                                    tt.setNewLanguageRequest(newRequest)
                                } catch (e: IOException) {
                                    e.printStackTrace()
                                }
                            } else if (existingRequest.submittedAt == 0L && newRequest.submittedAt > 0) {
                                // indicate global language request has been submitted
                                existingRequest.submittedAt = newRequest.submittedAt
                                languageRequestRepository.addNewLanguageRequest(existingRequest)
                                // TODO: 6/15/16 technically we need to look through all the existing target translations and update ones using this language.
                                // if we don't then they should get updated the next time the restart the app.
                            }
                        }
                        // store the temp language in the index so we can use it
                        try {
                            library.index.addTempTargetLanguage(newRequest.tempTargetLanguage)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    // make missing language codes usable even if we can't find the new language request
                    val tl = library.index.getTargetLanguage(tt.targetLanguageId)
                    if (tl == null) {
                        Logger.i(
                            TAG,
                            "Importing missing language code " + tt.targetLanguageId + " from " + tt.id
                        )
                        val tempLanguage = TargetLanguage(
                            tt.targetLanguageId,
                            tt.targetLanguageName,
                            "",
                            tt.targetLanguageDirection,
                            tt.targetLanguageRegion,
                            false
                        )
                        try {
                            library.index.addTempTargetLanguage(tempLanguage)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        return migratedDir
    }

    /**
     * current version
     * @param path the path to the translation directory
     * @return the path to the translation directory
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun v7(path: File): File {
        return path
    }

    /**
     * Fixes the chunk 00.txt bug and moves front matter out of the 00 directory and into the
     * front directory.
     * @param path
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun v6(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val manifest = JSONObject(readFileToString(manifestFile))
        val projectSlug = manifest.getJSONObject("project").getString("id")
        val chapters = path.listFiles { file ->
            file.isDirectory && file.name != ".git" && file.name != "cache"
        }
        // migrate 00 chunk
        // TRICKY: ts android only supports book translations right now
        val translations = library.index.findTranslations(
            "en",
            projectSlug,
            null,
            "book",
            null,
            3,
            -1
        )
        if (translations.size > 0) {
            val container = library.open(translations[0].resourceContainerSlug)
            for (dir in chapters) {
                val chunk00 = File(dir, "00.txt")
                if (chunk00.exists()) {
                    // find verse in source text

                    val chunkIds = container.chunks(dir.name)
                    val chunkId = largestIntVal(chunkIds)

                    // move the chunk
                    val chunk = File(dir, "$chunkId.txt")
                    if (moveOrCopyQuietly(chunk00, chunk)) {
                        deleteQuietly(chunk00)

                        // migrate finished chunks
                        if (manifest.has("finished_chunks")) {
                            val finished = manifest.getJSONArray("finished_chunks")
                            val finishedChunk00 = dir.name + "-00"
                            val newFinished = JSONArray()
                            for (i in 0 until finished.length()) {
                                if (finished.getString(i) == finishedChunk00) {
                                    newFinished.put(dir.name + "-" + chunkId)
                                } else {
                                    newFinished.put(finished[i])
                                }
                            }
                            manifest.put("finished_chunks", newFinished)
                        }
                    }
                }
            }
        }

        // migrate 00 chapter
        val chapter00 = File(path, "00")
        if (chapter00.exists() && chapter00.isDirectory) {
            if (moveOrCopyQuietly(chapter00, File(path, "front"))) {
                deleteQuietly(chapter00)
            }
        }

        manifest.put("package_version", 7)
        writeStringToFile(manifestFile, manifest.toString(2))
        return path
    }

    /**
     * Returns the largest numeric value in the list
     * @param list a list of strings to compare
     * @return the largest numeric string
     */
    private fun largestIntVal(list: Array<String>): String? {
        var largest: String? = null
        for (item in list) {
            try {
                if (largest == null || item.toInt() > largest.toInt()) {
                    largest = item
                }
            } catch (_: NumberFormatException) {
            }
        }
        return largest
    }

    /**
     * Updated the id format of target translations
     * @param path
     * @return
     */
    @Throws(Exception::class)
    private fun v5(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val manifest = JSONObject(readFileToString(manifestFile))

        // pull info to build id
        val targetLanguageCode = manifest.getJSONObject("target_language").getString("id")
        val projectSlug = manifest.getJSONObject("project").getString("id")
        val translationTypeSlug = manifest.getJSONObject("type").getString("id")
        var resourceSlug: String? = null
        if (translationTypeSlug == "text") {
            resourceSlug = manifest.getJSONObject("resource").getString("id")
        }

        // build new id
        var id = targetLanguageCode + "_" + projectSlug + "_" + translationTypeSlug
        if (translationTypeSlug == "text" && resourceSlug != null) {
            id += "_$resourceSlug"
        }

        // add license file
        val licenseFile = File(path, "LICENSE.md")
        if (!licenseFile.exists()) {
            try {
                val am = context.assets
                am.open("LICENSE.md").use { input ->
                    copyInputStreamToFile(input, licenseFile)
                }
            } catch (e: Exception) {
                throw Exception("Failed to open the template license file")
            }
        }

        // update package version
        manifest.put("package_version", 6)
        writeStringToFile(manifestFile, manifest.toString(2))

        // update target translation dir name
        val newPath = File(path.parentFile, id.lowercase(Locale.getDefault()))
        safeDelete(newPath)
        moveOrCopyQuietly(path, newPath)
        return newPath
    }

    /**
     * major restructuring of the manifest to provide better support for future front/back matter, drafts, rendering,
     * and resolves issues between desktop and android platforms.
     * @param path
     * @return
     */
    @Throws(Exception::class)
    private fun v4(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val manifest = JSONObject(readFileToString(manifestFile))

        // type
        run {
            var typeId = "text"
            if (manifest.has("project")) {
                try {
                    val projectJson = manifest.getJSONObject("project")
                    typeId = projectJson.getString("type")
                    projectJson.remove("type")
                    manifest.put("project", projectJson)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            val typeJson = JSONObject()
            val resourceType = ResourceType.get(typeId)
            typeJson.put("id", typeId)
            if (resourceType != null) {
                typeJson.put("name", resourceType.getName())
            } else {
                typeJson.put("name", "")
            }
            manifest.put("type", typeJson)
        }

        // update project
        // NOTE: this was actually in v3 but we missed it so we need to catch it here
        if (manifest.has("project_id")) {
            val projectId = manifest.getString("project_id")
            manifest.remove("project_id")
            val projectJson = JSONObject()
            projectJson.put("id", projectId)
            projectJson.put(
                "name",
                projectId.uppercase(Locale.getDefault())
            ) // we don't know the full name at this point
            manifest.put("project", projectJson)
        }

        // update resource
        if (manifest.getJSONObject("type").getString("id") == "text") {
            if (manifest.has("resource_id")) {
                var resourceId = manifest.getString("resource_id")
                manifest.remove("resource_id")
                val resourceJson = JSONObject()
                // TRICKY: supported resource id's (or now types) are "reg", "obs", "ulb", and "udb".
                if (resourceId == "ulb") {
                    resourceJson.put("name", "Unlocked Literal Bible")
                } else if (resourceId == "udb") {
                    resourceJson.put("name", "Unlocked Dynamic Bible")
                } else if (resourceId == "obs") {
                    resourceJson.put("name", "Open Bible Stories")
                } else {
                    // everything else changes to "reg"
                    resourceId = "reg"
                    resourceJson.put("name", "Regular")
                }
                resourceJson.put("id", resourceId)
                manifest.put("resource", resourceJson)
            } else if (!manifest.has("resource")) {
                // add missing resource
                val resourceJson = JSONObject()
                val projectJson = manifest.getJSONObject("project")
                val typeJson = manifest.getJSONObject("type")
                if (typeJson.getString("id") == "text") {
                    val resourceId = projectJson.getString("id")
                    if (resourceId == "obs") {
                        resourceJson.put("id", "obs")
                        resourceJson.put("name", "Open Bible Stories")
                    } else {
                        // everything else changes to reg
                        resourceJson.put("id", "reg")
                        resourceJson.put("name", "Regular")
                    }
                    manifest.put("resource", resourceJson)
                }
            }
        } else {
            // non-text translation types do not have resources
            manifest.remove("resource_id")
            manifest.remove("resource")
        }

        // update source translations
        if (manifest.has("source_translations")) {
            val oldSourceTranslationsJson = manifest.getJSONObject("source_translations")
            manifest.remove("source_translations")
            val newSourceTranslationsJson = JSONArray()
            val keys = oldSourceTranslationsJson.keys()
            while (keys.hasNext()) {
                try {
                    val key = keys.next()
                    val oldObj = oldSourceTranslationsJson.getJSONObject(key)
                    val sourceTranslation = JSONObject()
                    val parts = key.split("-".toRegex(), limit = 2)
                    if (parts.size == 2) {
                        val languageResourceId = parts[1]
                        val pieces =
                            languageResourceId.split("-".toRegex())
                        if (pieces.isNotEmpty()) {
                            val resId = pieces[pieces.size - 1]
                            sourceTranslation.put("resource_id", resId)
                            sourceTranslation.put(
                                "language_id",
                                languageResourceId.substring(
                                    0,
                                    languageResourceId.length - resId.length - 1
                                )
                            )
                            sourceTranslation.put(
                                "checking_level",
                                oldObj.getString("checking_level")
                            )
                            sourceTranslation.put("date_modified", oldObj.getInt("date_modified"))
                            sourceTranslation.put("version", oldObj.getString("version"))
                            newSourceTranslationsJson.put(sourceTranslation)
                        }
                    }
                } catch (e: Exception) {
                    // don't fail migration just because a source translation was invalid
                    e.printStackTrace()
                }
            }
            manifest.put("source_translations", newSourceTranslationsJson)
        }

        // update parent draft
        if (manifest.has("parent_draft_resource_id")) {
            val draftStatus = JSONObject()
            draftStatus.put("resource_id", manifest.getString("parent_draft_resource_id"))
            draftStatus.put("checking_entity", "")
            draftStatus.put("checking_level", "")
            draftStatus.put("comments", "The parent draft is unknown")
            draftStatus.put("contributors", "")
            draftStatus.put("publish_date", "")
            draftStatus.put("source_text", "")
            draftStatus.put("source_text_version", "")
            draftStatus.put("version", "")
            manifest.put("parent_draft", draftStatus)
            manifest.remove("parent_draft_resource_id")
        }

        // update finished chunks
        if (manifest.has("finished_frames")) {
            val finishedFrames = manifest.getJSONArray("finished_frames")
            manifest.remove("finished_frames")
            manifest.put("finished_chunks", finishedFrames)
        }

        // remove finished titles
        if (manifest.has("finished_titles")) {
            val finishedChunks = manifest.getJSONArray("finished_chunks")
            val finishedTitles = manifest.getJSONArray("finished_titles")
            manifest.remove("finished_titles")
            for (i in 0 until finishedTitles.length()) {
                val chapterId = finishedTitles.getString(i)
                finishedChunks.put("$chapterId-title")
            }
            manifest.put("finished_chunks", finishedChunks)
        }

        // remove finished references
        if (manifest.has("finished_references")) {
            val finishedChunks = manifest.getJSONArray("finished_chunks")
            val finishedReferences = manifest.getJSONArray("finished_references")
            manifest.remove("finished_references")
            for (i in 0 until finishedReferences.length()) {
                val chapterId = finishedReferences.getString(i)
                finishedChunks.put("$chapterId-reference")
            }
            manifest.put("finished_chunks", finishedChunks)
        }

        // remove project components
        // NOTE: this was never quite official, just in android
        if (manifest.has("finished_project_components")) {
            val finishedChunks = manifest.getJSONArray("finished_chunks")
            val finishedProjectComponents = manifest.getJSONArray("finished_project_components")
            manifest.remove("finished_project_components")
            for (i in 0 until finishedProjectComponents.length()) {
                val component = finishedProjectComponents.getString(i)
                finishedChunks.put("00-$component")
            }
            manifest.put("finished_chunks", finishedChunks)
        }

        // add format
        if (!Manifest.valueExists(
                manifest,
                "format"
            ) || manifest.getString("format") == "usx" || manifest.getString("format") == "default"
        ) {
            val typeId = manifest.getJSONObject("type").getString("id")
            val projectId = manifest.getJSONObject("project").getString("id")
            if (typeId != "text" || projectId == "obs") {
                manifest.put("format", "markdown")
            } else {
                manifest.put("format", "usfm")
            }
        }

        // update where project title is saved.
        val oldProjectTitle = File(path, "title.txt")
        val newProjectTitle = File(path, "00/title.txt")
        if (oldProjectTitle.exists()) {
            newProjectTitle.parentFile?.mkdirs()
            moveOrCopyQuietly(oldProjectTitle, newProjectTitle)
        }

        // update package version
        manifest.put("package_version", 5)

        writeStringToFile(manifestFile, manifest.toString(2))

        // migrate usx to usfm
        val format = manifest.getString("format")
        // TRICKY: we just added the new format field, anything marked as usfm may have residual usx and needs to be migrated
        if (format == "usfm") {
            val chapterDirs =
                path.listFiles { pathname -> pathname.isDirectory && pathname.name != ".git" }
            for (cDir in chapterDirs) {
                val chunkFiles = cDir.listFiles()
                for (chunkFile in chunkFiles) {
                    try {
                        val usx = readFileToString(chunkFile)
                        val usfm = USXtoUSFMConverter.doConversion(usx).toString()
                        writeStringToFile(chunkFile, usfm)
                    } catch (e: IOException) {
                        // this conversion may have failed but don't stop the rest of the migration
                        e.printStackTrace()
                    }
                }
            }
        }

        return path
    }

    /**
     * We changed how the translator information is stored
     * we no longer store sensitive information like email and phone number
     * @param path
     * @return
     */
    @Throws(Exception::class)
    private fun v3(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val manifest = JSONObject(readFileToString(manifestFile))
        if (manifest.has("translators")) {
            val legacyTranslators = manifest.getJSONArray("translators")
            val translators = JSONArray()
            for (i in 0 until legacyTranslators.length()) {
                val obj = legacyTranslators[i]
                if (obj is JSONObject) {
                    translators.put(obj.getString("name"))
                } else if (obj is String) {
                    translators.put(obj)
                }
            }
            manifest.put("translators", translators)
            manifest.put("package_version", 4)
            writeStringToFile(manifestFile, manifest.toString(2))
        }
        val projectSlug = manifest.getString("project_id")
        migrateChunkChanges(path, projectSlug)
        return path
    }

    /**
     * upgrade from v2
     * @param path
     * @return
     */
    @Throws(Exception::class)
    private fun v2(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val manifest = JSONObject(readFileToString(manifestFile))
        // fix finished frames
        if (manifest.has("frames")) {
            val legacyFrames = manifest.getJSONObject("frames")
            val keys = legacyFrames.keys()
            val finishedFrames = JSONArray()
            while (keys.hasNext()) {
                val key = keys.next()
                val frameState = legacyFrames.getJSONObject(key)
                var finished = false
                if (frameState.has("finished")) {
                    finished = frameState.getBoolean("finished")
                }
                if (finished) {
                    finishedFrames.put(key)
                }
            }
            manifest.remove("frames")
            manifest.put("finished_frames", finishedFrames)
        }
        // fix finished chapter titles and references
        if (manifest.has("chapters")) {
            val legacyChapters = manifest.getJSONObject("chapters")
            val keys = legacyChapters.keys()
            val finishedTitles = JSONArray()
            val finishedReferences = JSONArray()
            while (keys.hasNext()) {
                val key = keys.next()
                val chapterState = legacyChapters.getJSONObject(key)
                var finishedTitle = false
                val finishedReference = false
                if (chapterState.has("finished_title")) {
                    finishedTitle = chapterState.getBoolean("finished_title")
                }
                if (chapterState.has("finished_reference")) {
                    finishedTitle = chapterState.getBoolean("finished_reference")
                }
                if (finishedTitle) {
                    finishedTitles.put(key)
                }
                if (finishedReference) {
                    finishedReferences.put(key)
                }
            }
            manifest.remove("chapters")
            manifest.put("finished_titles", finishedTitles)
            manifest.put("finished_references", finishedReferences)
        }
        // fix project id
        if (manifest.has("slug")) {
            val projectSlug = manifest.getString("slug")
            manifest.remove("slug")
            manifest.put("project_id", projectSlug)
        }
        // fix target language id
        val targetLanguage = manifest.getJSONObject("target_language")
        if (targetLanguage.has("slug")) {
            val targetLanguageSlug = targetLanguage.getString("slug")
            targetLanguage.remove("slug")
            targetLanguage.put("id", targetLanguageSlug)
            manifest.put("target_language", targetLanguage)
        }

        manifest.put("package_version", 3)
        writeStringToFile(manifestFile, manifest.toString(2))
        return path
    }

    /**
     * Merges chunks found in a target translation Project that do not exist in the source translation
     * to a sibling chunk so that no data is lost.
     * @param targetTranslationDir
     * @param projectSlug
     * @return
     */
    private fun migrateChunkChanges(targetTranslationDir: File, projectSlug: String): Boolean {
        // TRICKY: calling the App here is bad practice, but we'll deprecate this soon anyway.
        val p = library.index.getProject("en", projectSlug, true)
        val resources = library.index.getResources(p.languageSlug, p.slug)
        val resourceContainer: ResourceContainer
        try {
            var resource: Resource? = null
            for (i in resources.indices) {
                val r = resources[i]
                if ("book".equals(r.type, ignoreCase = true)) {
                    resource = r
                    break
                }
            }
            resourceContainer = library.open(p.languageSlug, p.slug, resource!!.slug)
        } catch (e: Exception) {
            e.printStackTrace()
            return true
        }
        val chapterDirs = targetTranslationDir.listFiles { pathname ->
            pathname.isDirectory && pathname.name != ".git" && pathname.name != "00" // 00 contains project title translations
        }
        for (cDir in chapterDirs) {
            mergeInvalidChunksInChapter(
                File(targetTranslationDir, "manifest.json"),
                resourceContainer,
                cDir
            )
        }
        return true
    }

    /**
     * Merges invalid chunks found in the target translation with a valid sibling chunk in order
     * to preserve translation data. Merged chunks are marked as not finished to force
     * translators to review the changes.
     * @param manifestFile
     * @param resourceContainer
     * @param chapterDir
     * @return
     */
    private fun mergeInvalidChunksInChapter(
        manifestFile: File,
        resourceContainer: ResourceContainer,
        chapterDir: File
    ): Boolean {
        val manifest: JSONObject
        try {
            manifest = JSONObject(readFileToString(manifestFile))
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        val chunkMergeMarker = "\n----------\n"
        var frameFiles =
            chapterDir.listFiles { pathname -> pathname.name != "title.txt" && pathname.name != "reference.txt" }
        Arrays.sort(frameFiles)
        var invalidChunks = ""
        var lastValidFrameFile: File? = null
        val chapterId = chapterDir.name
        for (frameFile in frameFiles!!) {
            val frameFileName = frameFile.name
            val parts =
                frameFileName.split(".txt".toRegex())
            val frameId = parts[0]
            val chunkText = resourceContainer.readChunk(chapterId, frameId)
            var frameBody = ""
            try {
                frameBody = readFileToString(frameFile).trim { it <= ' ' }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (chunkText.isNotEmpty()) {
                lastValidFrameFile = frameFile
                // merge invalid frames into the existing frame
                if (invalidChunks.isNotEmpty()) {
                    try {
                        writeStringToFile(frameFile, invalidChunks + frameBody)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    invalidChunks = ""
                    try {
                        Manifest.removeValue(
                            manifest.getJSONArray("finished_frames"),
                            "$chapterId-$frameId"
                        )
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            } else if (frameBody.isNotEmpty()) {
                // collect invalid frame
                if (lastValidFrameFile == null) {
                    invalidChunks += frameBody + chunkMergeMarker
                } else {
                    // append to last valid frame
                    var lastValidFrameBody = ""
                    try {
                        lastValidFrameBody = readFileToString(lastValidFrameFile)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    try {
                        writeStringToFile(
                            lastValidFrameFile,
                            lastValidFrameBody + chunkMergeMarker + frameBody
                        )
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    try {
                        Manifest.removeValue(
                            manifest.getJSONArray("finished_frames"),
                            chapterId + "-" + lastValidFrameFile.name
                        )
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
                // delete invalid frame
                deleteQuietly(frameFile)
            }
        }
        // clean up remaining invalid chunks
        if (invalidChunks.isNotEmpty()) {
            // grab updated list of frames
            frameFiles =
                chapterDir.listFiles { pathname -> pathname.name != "title.txt" && pathname.name != "reference.txt" }
            if (frameFiles != null && frameFiles.isNotEmpty()) {
                var frameBody = ""
                try {
                    frameBody = readFileToString(frameFiles[0])
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                try {
                    writeStringToFile(frameFiles[0], invalidChunks + chunkMergeMarker + frameBody)
                    try {
                        Manifest.removeValue(
                            manifest.getJSONArray("finished_frames"),
                            chapterId + "-" + frameFiles[0].name
                        )
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        return true
    }

    /**
     * Checks if the android app can support this translation type.
     * Example: ts-desktop can translate tW but ts-android cannot.
     * @param path
     * @return
     */
    @Throws(Exception::class)
    private fun validateTranslationType(path: File): Boolean {
        val manifest = JSONObject(readFileToString(File(path, MANIFEST_FILE)))
        val typeId = manifest.getJSONObject("type").getString("id")
        // android only supports TEXT translations for now
        if (ResourceType.get(typeId) == ResourceType.TEXT) {
            return true
        } else {
            Logger.w(TAG, "Only text translation types are supported")
            return false
        }
    }
}
