package com.door43.translationstudio.core

import android.content.Context
import android.content.pm.PackageInfo
import com.door43.translationstudio.core.entity.SourceTranslation
import com.door43.translationstudio.git.Repo
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.util.FileUtilities
import com.door43.util.Manifest
import com.door43.util.NumericStringComparator
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ContainerTools
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.Locale
import kotlin.concurrent.thread

class TargetTranslation private constructor(
    val path: File
) {
    val manifest: Manifest = Manifest.generate(path)

    var targetLanguageId: String
        private set
    var targetLanguageName: String
        private set
    var targetLanguageDirection: String
        private set
    var targetLanguageRegion: String = "unknown"
        private set

    val projectId: String
    val projectName: String
    val translationType: ResourceType
    private val translationTypeName: String

    var resourceSlug: String? = null
        private set
    var resourceName: String? = null
        private set

    val format: TranslationFormat
    private var author: PersonIdent? = null

    init {
        // target language
        val targetLanguageJson = this.manifest.getJSONObject(FIELD_MANIFEST_TARGET_LANGUAGE)
        this.targetLanguageId = targetLanguageJson.getString(FIELD_MANIFEST_ID)
        this.targetLanguageName = if (Manifest.valueExists(targetLanguageJson, FIELD_MANIFEST_NAME)) {
            targetLanguageJson.getString(FIELD_MANIFEST_NAME)
        } else {
            this.targetLanguageId.uppercase(Locale.getDefault())
        }
        this.targetLanguageDirection = targetLanguageJson.getString("direction")
        if (targetLanguageJson.has("region")) {
            this.targetLanguageRegion = targetLanguageJson.getString("region")
        }

        // project
        val projectJson = this.manifest.getJSONObject(FIELD_MANIFEST_PROJECT)
        this.projectId = projectJson.getString(FIELD_MANIFEST_ID)
        this.projectName = if (Manifest.valueExists(projectJson, FIELD_MANIFEST_NAME)) {
            projectJson.getString(FIELD_MANIFEST_NAME)
        } else {
            this.projectId.uppercase(Locale.getDefault())
        }

        // translation type
        val typeJson = this.manifest.getJSONObject(FIELD_MANIFEST_TRANSLATION_TYPE)
        this.translationType = ResourceType.get(typeJson.getString(FIELD_MANIFEST_ID))!!
        this.translationTypeName = if (Manifest.valueExists(typeJson, FIELD_MANIFEST_NAME)) {
            typeJson.getString(FIELD_MANIFEST_NAME)
        } else {
            this.translationType.toString().uppercase(Locale.getDefault())
        }

        if (this.translationType == ResourceType.TEXT) {
            // resource
            val resourceJson = this.manifest.getJSONObject(FIELD_MANIFEST_RESOURCE)
            this.resourceSlug = resourceJson.getString(FIELD_MANIFEST_ID)
            this.resourceName = if (Manifest.valueExists(resourceJson, FIELD_MANIFEST_NAME)) {
                resourceJson.getString(FIELD_MANIFEST_NAME)
            } else {
                this.resourceSlug?.uppercase(Locale.getDefault())
            }
        }

        format = readTranslationFormat()
    }

    val id: String
        get() = generateTargetTranslationId(
            targetLanguageId,
            projectId,
            translationType,
            resourceSlug
        )

    val targetLanguage: TargetLanguage
        get() = TargetLanguage(
            targetLanguageId,
            targetLanguageName,
            "",
            targetLanguageDirection,
            targetLanguageRegion,
            false
        )

    val repo: Repo
        get() = Repo(path.absolutePath)

    val isObsProject: Boolean
        get() = Companion.isObsProject(projectId)

    private fun readTranslationFormat(): TranslationFormat {
        val parsedFormat = fetchTranslationFormat(manifest)
        if (parsedFormat == TranslationFormat.UNKNOWN) {
            val resType = fetchTranslationType(manifest)
            return if (resType != ResourceType.TEXT) {
                TranslationFormat.MARKDOWN
            } else {
                if (isObsProject(fetchProjectID(manifest))) {
                    TranslationFormat.MARKDOWN
                } else {
                    TranslationFormat.USFM
                }
            }
        }
        return parsedFormat
    }

    @Throws(JSONException::class)
    fun addSourceTranslation(translation: Translation, modifiedAt: Int) {
        var sourceTranslationsJson = manifest.getJSONArray(FIELD_SOURCE_TRANSLATIONS)
        sourceTranslationsJson = cleanupDuplicateSources(sourceTranslationsJson)

        // check for duplicate
        var foundDuplicate = false
        for (i in 0 until sourceTranslationsJson.length()) {
            val obj = sourceTranslationsJson.getJSONObject(i)
            if (obj.getString("language_id") == translation.language.slug && obj.getString("resource_id") == translation.resource.slug) {
                foundDuplicate = true
                break
            }
        }
        if (!foundDuplicate) {
            val translationJson = JSONObject().apply {
                put("language_id", translation.language.slug)
                put("resource_id", translation.resource.slug)
                put("checking_level", translation.resource.checkingLevel)
                put("date_modified", modifiedAt)
                put("version", translation.resource.version)
            }
            sourceTranslationsJson.put(translationJson)
            manifest.put(FIELD_SOURCE_TRANSLATIONS, sourceTranslationsJson)
        }
    }

    @Throws(JSONException::class)
    fun setSourceTranslations(translations: List<SourceTranslation>) {
        val sourcesJson = JSONArray()
        for (src in translations) {
            val translationJson = JSONObject().apply {
                put("language_id", src.language.slug)
                put("resource_id", src.resource.slug)
                put("checking_level", src.resource.checkingLevel)
                put("date_modified", src.modifiedTimestamp)
                put("version", src.resource.version)
            }
            sourcesJson.put(translationJson)
        }
        manifest.put(FIELD_SOURCE_TRANSLATIONS, sourcesJson)
    }

    @Throws(JSONException::class)
    private fun cleanupDuplicateSources(sourceTranslationsJson: JSONArray): JSONArray {
        var doCleanup = false
        val sources = HashMap<String, JSONObject>()

        val length = sourceTranslationsJson.length()
        for (i in 0 until length) {
            val obj = sourceTranslationsJson.optJSONObject(i)
            if (obj == null) {
                doCleanup = true // invalid type, skip
                continue
            }

            val sourceLanguageSlug = obj.getString("language_id")
            val resourceSlug = obj.getString("resource_id")

            val containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, this.projectId, resourceSlug)
            if (sources.containsKey(containerSlug)) {
                doCleanup = true // duplicate
            }
            sources[containerSlug] = obj // save most recent
        }

        var resultJson = sourceTranslationsJson
        if (doCleanup) {
            val newSourceTranslationsJson = JSONArray()
            for (source in sources.values) {
                newSourceTranslationsJson.put(source)
            }
            resultJson = newSourceTranslationsJson
            Logger.i(TAG, "Cleaning up from $length items to ${newSourceTranslationsJson.length()}")
            manifest.put(FIELD_SOURCE_TRANSLATIONS, resultJson)
        }
        return resultJson
    }

    val sourceTranslations: Array<String>
        get() {
            return try {
                val sources = ArrayList<String>()
                val sourceTranslationsJson = manifest.getJSONArray(FIELD_SOURCE_TRANSLATIONS)

                for (i in 0 until sourceTranslationsJson.length()) {
                    val obj = sourceTranslationsJson.getJSONObject(i)
                    val sourceLanguageSlug = obj.getString("language_id")
                    val resourceSlug = obj.getString("resource_id")
                    val containerSlug = ContainerTools.makeSlug(sourceLanguageSlug, this.projectId, resourceSlug)
                    sources.add(containerSlug)
                }
                sources.toTypedArray()
            } catch (e: Exception) {
                Logger.e(TAG, "Error reading sources", e)
                emptyArray()
            }
        }

    fun addContributor(speaker: NativeSpeaker?) {
        if (speaker != null) {
            removeContributor(speaker)
            val translatorsJson = manifest.getJSONArray(FIELD_TRANSLATORS)
            translatorsJson.put(speaker.name)
            manifest.put(FIELD_TRANSLATORS, translatorsJson)
        }
    }

    fun removeContributor(speaker: NativeSpeaker) {
        val translatorsJson = manifest.getJSONArray(FIELD_TRANSLATORS)
        manifest.put(
            FIELD_TRANSLATORS,
            Manifest.removeValue(translatorsJson, speaker.name)
        )
    }

    fun getContributor(name: String): NativeSpeaker? {
        manifest.load()
        return contributors.find { it.name == name }
    }

    val contributors: ArrayList<NativeSpeaker>
        get() {
            manifest.load()
            val translatorsJson = manifest.getJSONArray(FIELD_TRANSLATORS)
            val translators = ArrayList<NativeSpeaker>()

            for (i in 0 until translatorsJson.length()) {
                try {
                    val name = translatorsJson.getString(i)
                    if (name.isNotEmpty()) {
                        translators.add(NativeSpeaker(name))
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
            return translators
        }

    fun setDefaultContributor(speaker: NativeSpeaker?) {
        if (speaker != null) {
            if (contributors.isEmpty()) {
                addContributor(speaker)
            }
        }
    }

    fun getFrameTranslation(chapterId: String, frameId: String, format: TranslationFormat): FrameTranslation {
        val frameFile = getFrameFile(chapterId, frameId)
        if (frameFile.exists()) {
            try {
                val body = frameFile.readText()
                return RenderingProvider.getFrameTranslation(
                    frameId,
                    chapterId,
                    body,
                    format,
                    isFrameFinished("$chapterId-$frameId")
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return RenderingProvider.getFrameTranslation(frameId, chapterId, "", format, false)
    }

    fun getChapterTranslation(chapterSlug: String): ChapterTranslation {
        val referenceFile = getChapterReferenceFile(chapterSlug)
        val titleFile = getChapterTitleFile(chapterSlug)

        val reference = if (referenceFile.exists()) referenceFile.readText() else ""
        val title = if (titleFile.exists()) titleFile.readText() else ""

        return ChapterTranslation(
            title,
            reference,
            chapterSlug,
            isChapterTitleFinished(chapterSlug),
            isChapterReferenceFinished(chapterSlug),
            format
        )
    }

    val projectTranslation: ProjectTranslation
        get() {
            val titleFile = projectTitleFile
            val title = if (titleFile.exists()) titleFile.readText() else ""
            return ProjectTranslation(
                title,
                isProjectComponentFinished("title")
            )
        }

    fun applyFrameTranslation(frameTranslation: FrameTranslation, translatedText: String) {
        try {
            saveFrameTranslation(frameTranslation, translatedText)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    fun applyProjectTitleTranslation(translatedText: String) {
        val titleFile = projectTitleFile
        if (translatedText.isEmpty()) {
            titleFile.delete()
        } else {
            titleFile.parentFile?.mkdirs()
            titleFile.writeText(translatedText)
        }
    }

    @Throws(IOException::class)
    private fun saveFrameTranslation(frameTranslation: FrameTranslation, translatedText: String) {
        val frameFile = getFrameFile(frameTranslation.chapterId, frameTranslation.id)
        if (translatedText.isEmpty()) {
            frameFile.delete()
        } else {
            frameFile.parentFile?.mkdirs()
            frameFile.writeText(translatedText)
        }
    }

    @Throws(IOException::class)
    private fun saveChapterReferenceTranslation(chapterTranslation: ChapterTranslation, translatedText: String) {
        val chapterReferenceFile = getChapterReferenceFile(chapterTranslation.id)
        if (translatedText.isEmpty()) {
            chapterReferenceFile.delete()
        } else {
            chapterReferenceFile.parentFile?.mkdirs()
            chapterReferenceFile.writeText(translatedText)
        }
    }

    @Throws(IOException::class)
    private fun saveChapterTitleTranslation(chapterTranslation: ChapterTranslation, translatedText: String) {
        val chapterTitleFile = getChapterTitleFile(chapterTranslation.id)
        if (translatedText.isEmpty()) {
            chapterTitleFile.delete()
        } else {
            chapterTitleFile.parentFile?.mkdirs()
            chapterTitleFile.writeText(translatedText)
        }
    }

    fun getFrameFile(chapterId: String, frameId: String): File =
        File(path, "$chapterId/$frameId.txt")

    fun getChapterReferenceFile(chapterId: String): File =
        File(path, "$chapterId/reference.txt")

    fun getChapterTitleFile(chapterId: String): File =
        File(path, "$chapterId/title.txt")

    val projectTitleFile: File
        get() = File(path, "front/title.txt")

    fun closeProjectTitle(): Boolean {
        return if (projectTitleFile.exists()) finishProjectComponent("title") else false
    }

    fun openProjectTitle(): Boolean = openProjectComponent("title")

    private fun isProjectComponentFinished(component: String): Boolean =
        isChunkClosed("front-$component")

    private fun openProjectComponent(component: String): Boolean =
        openChunk("front-$component")

    private fun finishProjectComponent(component: String): Boolean =
        closeChunk("front-$component")

    fun finishChapterTitle(chapterSlug: String): Boolean {
        return if (getChapterTitleFile(chapterSlug).exists()) {
            closeChunk("$chapterSlug-title")
        } else false
    }

    fun reopenChapterTitle(chapterSlug: String): Boolean =
        openChunk("$chapterSlug-title")

    private fun isChapterTitleFinished(chapterSlug: String): Boolean =
        isChunkClosed("$chapterSlug-title")

    fun finishChapterReference(chapterSlug: String): Boolean {
        return if (getChapterReferenceFile(chapterSlug).exists()) {
            closeChunk("$chapterSlug-reference")
        } else false
    }

    fun reopenChapterReference(chapterSlug: String): Boolean =
        openChunk("$chapterSlug-reference")

    private fun isChapterReferenceFinished(chapterSlug: String): Boolean =
        isChunkClosed("$chapterSlug-reference")

    fun finishFrame(chapterSlug: String, chunkSlug: String): Boolean {
        return if (getFrameFile(chapterSlug, chunkSlug).exists()) {
            closeChunk("$chapterSlug-$chunkSlug")
        } else false
    }

    fun reopenFrame(chapterSlug: String, chunkSlug: String): Boolean =
        openChunk("$chapterSlug-$chunkSlug")

    private fun isFrameFinished(frameComplexId: String): Boolean = isChunkClosed(frameComplexId)

    private fun closeChunk(complexId: String): Boolean {
        if (!isChunkClosed(complexId)) {
            val finishedChunks = manifest.getJSONArray(FIELD_FINISHED_CHUNKS)
            finishedChunks.put(complexId)
            manifest.put(FIELD_FINISHED_CHUNKS, finishedChunks)
        }
        return true
    }

    private fun openChunk(complexId: String): Boolean {
        val finishedChunks = manifest.getJSONArray(FIELD_FINISHED_CHUNKS)
        val updatedChunks = JSONArray()
        try {
            for (i in 0 until finishedChunks.length()) {
                val currId = finishedChunks.getString(i)
                if (currId != complexId) {
                    updatedChunks.put(currId)
                }
            }
            manifest.put(FIELD_FINISHED_CHUNKS, updatedChunks)
            return true
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return false
    }

    private fun isChunkClosed(complexId: String): Boolean {
        val finishedChunks = manifest.getJSONArray(FIELD_FINISHED_CHUNKS)
        try {
            for (i in 0 until finishedChunks.length()) {
                if (finishedChunks.getString(i) == complexId) {
                    return true
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return false
    }

    @Throws(Exception::class)
    fun commitSync(): Boolean = commitSync(".")

    val isClean: Boolean
        get() = try {
            repo.git.status().call().isClean
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    fun setAuthor(name: String, email: String) {
        this.author = PersonIdent(name, email)
    }

    @Throws(Exception::class)
    fun commitSync(filePattern: String): Boolean = commitSync(filePattern, true)

    @Throws(Exception::class)
    fun commitSync(filePattern: String, forced: Boolean): Boolean {
        val git = repo.git

        if (isClean) return true

        val add = git.add()
        add.addFilepattern(filePattern)

        if (forced) {
            try {
                add.call()
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to stage changes for $id", e)
            }
        } else {
            add.call()
        }

        val commit = git.commit()
        commit.setAll(true)
        author?.let { commit.author = it }
        commit.message = "auto save"

        if (forced) {
            try {
                commit.call()
            } catch (e: Exception) {
                Logger.e(TargetTranslation::class.java.name, "Failed to commit changes for $id", e)
                return false
            }
        } else {
            commit.call()
        }
        return true
    }

    @Throws(Exception::class)
    fun commit() {
        commit(".", null)
    }

    @Throws(Exception::class)
    fun commit(listener: OnCommitListener?) {
        commit(".", listener)
    }

    @Throws(Exception::class)
    private fun commit(filePattern: String, listener: OnCommitListener?) {
        thread {
            try {
                val result = commitSync(filePattern)
                listener?.onCommit(result)
            } catch (e: Exception) {
                listener?.onCommit(false)
            }
        }
    }

    fun setParentDraft(draftTranslation: ResourceContainer) {
        val draftStatus = JSONObject()
        try {
            draftStatus.put("resource_id", draftTranslation.resource.slug)
            draftStatus.put("checking_level", draftTranslation.resource.checkingLevel)
            draftStatus.put("version", draftTranslation.resource.version)
            manifest.put(FIELD_PARENT_DRAFT, draftStatus)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun resetToMasterBackup(): Boolean {
        return try {
            val git = repo.git
            val resetCommand = git.reset()
            resetCommand.setMode(ResetCommand.ResetType.HARD)
                .setRef("backup-master")
                .call()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Throws(Exception::class)
    fun merge(newDir: File, onError: (() -> Unit)?): Boolean {
        val importedTargetTranslation = open(newDir, onError)
        importedTargetTranslation?.commitSync()
        commitSync()

        val importedManifest = Manifest.generate(newDir)
        val myRepo = repo
        val git = myRepo.git

        // create a backup branch
        git.branchDelete().setBranchNames("backup-master").setForce(true).call()
        git.branchCreate().setName("backup-master").setForce(true).call()

        // attach remote
        myRepo.deleteRemote("new")
        myRepo.setRemote("new", newDir.absolutePath)
        val fetch = git.fetch()
        fetch.remote = "new"
        fetch.call()

        // create branch for new changes
        git.branchDelete().setBranchNames("new").setForce(true).call()
        git.branchCreate().setName("new").setStartPoint("new/master").call()

        // perform merge
        val merge = repo.git.merge()
        merge.setFastForward(MergeCommand.FastForwardMode.NO_FF)
        merge.include(repo.git.repository.getRef("new"))
        val result = merge.call()

        // merge manifests
        mergeManifests(manifest, importedManifest)

        if (result.mergeStatus == MergeResult.MergeStatus.CONFLICTING) {
            println(result.conflicts.toString())
            return false
        }
        return true
    }

    fun getNewLanguageRequest(context: Context): NewLanguageRequest? {
        val requestFile = File(path, "new_language.json")
        return NewLanguageRequest.Builder(context).fromFile(requestFile).build()
    }

    @Throws(IOException::class)
    fun setNewLanguageRequest(request: NewLanguageRequest?) {
        val requestFile = File(path, "new_language.json")
        if (request != null) {
            request.toJson()?.let { FileUtilities.writeStringToFile(requestFile, it) }
        } else if (requestFile.exists()) {
            FileUtilities.safeDelete(requestFile)
        }
    }

    fun changeTargetLanguage(targetLanguage: TargetLanguage) {
        val languageJson = this.manifest.getJSONObject("target_language")
        try {
            languageJson.put("name", targetLanguage.name)
            languageJson.put("direction", targetLanguage.direction)
            languageJson.put("id", targetLanguage.slug)
            this.manifest.put("target_language", languageJson)
            this.targetLanguageDirection = targetLanguage.direction
            this.targetLanguageId = targetLanguage.slug
            this.targetLanguageName = targetLanguage.name
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun unlockRepo(): Boolean {
        var cleaned = false
        val gitDir = File(path.absolutePath, ".git")

        val indexLock = File(gitDir, "index.lock")
        if (indexLock.exists()) cleaned = true
        FileUtilities.deleteQuietly(indexLock)

        val headsDir = File(gitDir, "refs/heads")
        val pattern = Regex(".*\\.lock")
        val headLocks = headsDir.listFiles { _, name -> pattern.matches(name) } ?: emptyArray()

        if (headLocks.isNotEmpty()) cleaned = true
        for (lock in headLocks) {
            FileUtilities.deleteQuietly(lock)
        }

        if (cleaned) {
            Logger.i(TAG, "cleaned locks in $id")
        }
        return cleaned
    }

    val commitHash: String?
        get() = try {
            val commit = getGitHead(repo)
            commit?.name
        } catch (e: Exception) {
            Logger.e(TAG, "Could not get commit hash", e)
            null
        }

    @Throws(GitAPIException::class, IOException::class)
    private fun getGitHead(repo: Repo): RevCommit? {
        val commits = repo.git.log().setMaxCount(1).call()
        return commits.firstOrNull()
    }

    fun applyChapterReferenceTranslation(chapterTranslation: ChapterTranslation, translatedText: String) {
        try {
            saveChapterReferenceTranslation(chapterTranslation, translatedText)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun applyChapterTitleTranslation(chapterTranslation: ChapterTranslation, translatedText: String) {
        try {
            saveChapterTitleTranslation(chapterTranslation, translatedText)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    val numTranslated: Int
        get() {
            var numFiles = 0
            val chapterDirs = path.listFiles { pathname ->
                pathname.isDirectory && pathname.name != ".git" && pathname.name != "manifest.json"
            }
            chapterDirs?.forEach { dir ->
                val files = dir.list()
                if (files != null) {
                    numFiles += files.size
                }
            }
            return numFiles
        }

    val numFinished: Int
        get() = if (manifest.has(FIELD_FINISHED_CHUNKS)) {
            manifest.getJSONArray(FIELD_FINISHED_CHUNKS).length()
        } else {
            0
        }

    val chapterTranslations: Array<ChapterTranslation>
        get() {
            val chapterSlugs = path.list { dir, filename ->
                File(dir, filename).isDirectory && filename != ".git"
            } ?: return emptyArray()

            Arrays.sort(chapterSlugs, NumericStringComparator())
            val chapterTranslations = ArrayList<ChapterTranslation>()
            for (slug in chapterSlugs) {
                chapterTranslations.add(getChapterTranslation(slug))
            }
            return chapterTranslations.toTypedArray()
        }

    fun getFrameHistory(frameTranslation: FrameTranslation): FileHistory? {
        return try {
            FileHistory(repo, getFrameFile(frameTranslation.chapterId, frameTranslation.id))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getChapterTitleHistory(chapterTranslation: ChapterTranslation): FileHistory? {
        return try {
            FileHistory(repo, getChapterTitleFile(chapterTranslation.id))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getChapterReferenceHistory(chapterTranslation: ChapterTranslation): FileHistory? {
        return try {
            FileHistory(repo, getChapterReferenceFile(chapterTranslation.id))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    val projectTitleHistory: FileHistory?
        get() = try {
            FileHistory(repo, projectTitleFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    fun getFrameTranslations(chapterSlug: String, frameTranslationFormat: TranslationFormat): Array<FrameTranslation> {
        val frameFileNames = File(path, chapterSlug).list { _, filename ->
            filename != "reference.txt" && filename != "title.txt"
        } ?: return emptyArray()

        Arrays.sort(frameFileNames, NumericStringComparator())
        val frameTranslations = ArrayList<FrameTranslation>()
        for (fileName in frameFileNames) {
            val slug = fileName.split("\\.txt".toRegex()).toTypedArray()
            if (slug.isNotEmpty()) {
                val f = getFrameTranslation(chapterSlug, slug[0], frameTranslationFormat)
                frameTranslations.add(f)
            }
        }
        return frameTranslations.toTypedArray()
    }

    fun normalizePath(): Boolean {
        if (path.name != id) {
            val dest = File(path.parentFile, id)
            if (!dest.exists()) {
                return FileUtilities.moveOrCopyQuietly(path, dest)
            }
        }
        return false
    }

    interface OnCommitListener {
        fun onCommit(success: Boolean)
    }

    companion object {
        val TAG: String = TargetTranslation::class.java.simpleName
        const val PACKAGE_VERSION = 8
        const val LICENSE_FILE = "LICENSE.md"

        private const val FIELD_PARENT_DRAFT = "parent_draft"
        private const val FIELD_FINISHED_CHUNKS = "finished_chunks"
        private const val FIELD_TRANSLATORS = "translators"

        const val FIELD_MANIFEST_TARGET_LANGUAGE = "target_language"
        const val FIELD_MANIFEST_FORMAT = "format"
        const val FIELD_MANIFEST_RESOURCE = "resource"
        const val FIELD_SOURCE_TRANSLATIONS = "source_translations"
        const val FIELD_MANIFEST_PACKAGE_VERSION = "package_version"
        const val FIELD_MANIFEST_PROJECT = "project"
        const val FIELD_MANIFEST_GENERATOR = "generator"
        const val FIELD_MANIFEST_TRANSLATION_TYPE = "type"
        const val FIELD_TRANSLATION_FORMAT = "format"
        const val FIELD_MANIFEST_ID = "id"
        const val FIELD_MANIFEST_NAME = "name"
        const val FIELD_MANIFEST_BUILD = "build"
        const val APPLICATION_NAME = "ts-android"
        const val OBS_PROJECT_TYPE = "obs"

        fun generateTargetTranslationId(
            targetLanguageSlug: String,
            projectSlug: String,
            resourceType: ResourceType,
            resourceSlug: String?
        ): String {
            var id = "${targetLanguageSlug}_${projectSlug}_${resourceType}"
            if (resourceType == ResourceType.TEXT && resourceSlug != null) {
                id += "_$resourceSlug"
            }
            return id.lowercase(Locale.getDefault())
        }

        fun isObsProject(projectId: String): Boolean =
            OBS_PROJECT_TYPE.equals(projectId, ignoreCase = true)

        fun fetchTranslationFormat(manifest: Manifest): TranslationFormat {
            val formatStr = manifest.getString(FIELD_TRANSLATION_FORMAT)
            return TranslationFormat.get(formatStr)
        }

        fun fetchProjectID(manifest: Manifest): String {
            val projectIdJson = manifest.getJSONObject(FIELD_MANIFEST_PROJECT)
            return try {
                projectIdJson?.getString(FIELD_MANIFEST_ID) ?: ""
            } catch (e: Exception) {
                ""
            }
        }

        fun fetchTranslationType(manifest: Manifest): ResourceType? {
            val typeJson = manifest.getJSONObject(FIELD_MANIFEST_TRANSLATION_TYPE)
            val translationTypeStr = try {
                typeJson?.getString(FIELD_MANIFEST_ID) ?: ""
            } catch (e: Exception) {
                ""
            }
            return ResourceType.get(translationTypeStr)
        }

        fun open(targetTranslationDir: File, onError: (() -> Unit)? = null): TargetTranslation? {
            if (targetTranslationDir.exists()) {
                val manifestFile = File(targetTranslationDir, "manifest.json")
                if (manifestFile.exists()) {
                    try {
                        val manifestJson = JSONObject(manifestFile.readText())
                        val version = manifestJson.getInt(FIELD_MANIFEST_PACKAGE_VERSION)
                        if (version == PACKAGE_VERSION) {
                            return TargetTranslation(targetTranslationDir)
                        } else {
                            Logger.w(
                                TargetTranslation::class.java.name,
                                "Unsupported target translation version $version in ${targetTranslationDir.name}"
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        onError?.invoke()
                    }
                } else {
                    Logger.w(
                        TargetTranslation::class.java.name,
                        "Missing manifest file in target translation ${targetTranslationDir.name}"
                    )
                }
            }
            return null
        }

        @Throws(Exception::class)
        fun create(
            context: Context,
            translator: NativeSpeaker,
            translationFormat: TranslationFormat,
            targetLanguage: TargetLanguage,
            projectId: String,
            resourceType: ResourceType,
            resourceSlug: String,
            packageInfo: PackageInfo,
            targetTranslationDir: File
        ): TargetTranslation {
            targetTranslationDir.mkdirs()
            val manifest = Manifest.generate(targetTranslationDir)

            val projectJson = JSONObject().apply {
                put(FIELD_MANIFEST_ID, projectId)
                put(FIELD_MANIFEST_NAME, "")
            }
            manifest.put(FIELD_MANIFEST_PROJECT, projectJson)

            val typeJson = JSONObject().apply {
                put(FIELD_MANIFEST_ID, resourceType)
                put(FIELD_MANIFEST_NAME, resourceType.title)
            }
            manifest.put(FIELD_MANIFEST_TRANSLATION_TYPE, typeJson)

            val generatorJson = JSONObject().apply {
                put(FIELD_MANIFEST_NAME, APPLICATION_NAME)
                put(FIELD_MANIFEST_BUILD, packageInfo.versionCode)
            }
            manifest.put(FIELD_MANIFEST_GENERATOR, generatorJson)
            manifest.put(FIELD_MANIFEST_PACKAGE_VERSION, PACKAGE_VERSION)

            val targetLanguageJson = targetLanguage.toJSON().apply {
                put("id", targetLanguage.slug)
                remove("slug")
            }
            manifest.put(FIELD_MANIFEST_TARGET_LANGUAGE, targetLanguageJson)
            manifest.put(FIELD_MANIFEST_FORMAT, translationFormat)

            val resourceJson = JSONObject().apply {
                put(FIELD_MANIFEST_ID, resourceSlug)
            }
            manifest.put(FIELD_MANIFEST_RESOURCE, resourceJson)

            val licenseFile = File(targetTranslationDir, LICENSE_FILE)
            context.assets.open(LICENSE_FILE).use { input ->
                FileUtilities.copyInputStreamToFile(input, licenseFile)
            }

            return TargetTranslation(targetTranslationDir).apply {
                addContributor(translator)
            }
        }

        @Throws(Exception::class)
        fun updateGenerator(context: Context, targetTranslation: TargetTranslation) {
            val generatorJson = JSONObject().apply {
                put(FIELD_MANIFEST_NAME, "ts-android")
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                put(FIELD_MANIFEST_BUILD, pInfo.versionCode)
            }
            targetTranslation.manifest.put(FIELD_MANIFEST_GENERATOR, generatorJson)
        }

        @Throws(StringIndexOutOfBoundsException::class)
        fun getProjectSlugFromId(targetTranslationId: String): String {
            val complexId = targetTranslationId.split("_")
            if (complexId.size >= 3) {
                return complexId[1]
            } else {
                throw StringIndexOutOfBoundsException("malformed target translation id $targetTranslationId")
            }
        }

        @Throws(StringIndexOutOfBoundsException::class)
        fun getTargetLanguageSlugFromId(targetTranslationId: String): String {
            val complexId = targetTranslationId.split("_")
            if (complexId.size >= 3) {
                return complexId[0]
            } else {
                throw StringIndexOutOfBoundsException("malformed target translation id $targetTranslationId")
            }
        }

        @Throws(StringIndexOutOfBoundsException::class)
        fun getResourceTypeFromId(targetTranslationId: String): String {
            val complexId = targetTranslationId.split("_")
            if (complexId.size >= 3) {
                return complexId[2]
            } else {
                throw StringIndexOutOfBoundsException("malformed target translation id $targetTranslationId")
            }
        }

        fun generateTargetTranslationDir(targetTranslationId: String, rootDir: File): File {
            return File(rootDir, targetTranslationId)
        }

        fun mergeManifests(original: Manifest, imported: Manifest): Manifest {
            original.join(imported.getJSONArray(FIELD_TRANSLATORS), FIELD_TRANSLATORS)
            original.join(imported.getJSONArray(FIELD_FINISHED_CHUNKS), FIELD_FINISHED_CHUNKS)
            original.join(imported.getJSONArray(FIELD_SOURCE_TRANSLATIONS), FIELD_SOURCE_TRANSLATIONS)

            if ((!original.has(FIELD_PARENT_DRAFT) || !Manifest.valueExists(original.getJSONObject(FIELD_PARENT_DRAFT), "resource_id")) &&
                imported.has(FIELD_PARENT_DRAFT)) {
                original.put(FIELD_PARENT_DRAFT, imported.getJSONObject(FIELD_PARENT_DRAFT))
            }
            return original
        }
    }
}