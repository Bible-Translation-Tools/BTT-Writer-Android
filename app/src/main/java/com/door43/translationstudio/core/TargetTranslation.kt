package com.door43.translationstudio.core

import android.content.Context
import com.door43.translationstudio.Platform
import com.door43.translationstudio.core.entity.SourceTranslation
import com.door43.translationstudio.core.entity.toSourceTranslation
import com.door43.translationstudio.core.manifest.Manifest
import com.door43.translationstudio.core.manifest.ManifestAccessor
import com.door43.translationstudio.core.manifest.buildManifest
import com.door43.translationstudio.core.manifest.toSource
import com.door43.translationstudio.git.Repo
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.util.FileUtilities
import com.door43.util.NumericStringComparator
import org.bibletranslationtools.logger.Logger
import org.bibletranslationtools.resourcecontainer.ContainerTools
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.door43client.models.Translation
import java.io.File
import java.io.IOException
import java.util.Arrays
import java.util.Locale
import kotlin.concurrent.thread

class TargetTranslation private constructor(
    val path: File
) {
    val manifestAccessor = ManifestAccessor(
        File(path, Manifest.MANIFEST_JSON)
    )
    val manifest: Manifest
        get() = manifestAccessor.manifest

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
        targetLanguageId = manifest.targetLanguage.slug
        targetLanguageName = manifest.targetLanguage.name.ifEmpty { targetLanguageId.uppercase() }
        targetLanguageDirection = manifest.targetLanguage.direction

        // project
        projectId = manifest.project.slug
        projectName = manifest.project.name.ifEmpty { projectId.uppercase() }

        // translation type
        translationType = ResourceType.get(manifest.type.slug)!!
        translationTypeName = manifest.type.name.ifEmpty { translationType.title }

        if (translationType == ResourceType.TEXT) {
            // resource
            resourceSlug = manifest.resource.slug
            resourceName = manifest.resource.name.ifEmpty { manifest.resource.slug.uppercase() }
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
            slug = targetLanguageId,
            name = targetLanguageName,
            direction = targetLanguageDirection,
            anglicizedName = "",
            region = targetLanguageRegion,
            isGatewayLanguage = false
        )

    val repo: Repo
        get() = Repo(path.absolutePath)

    val isObsProject: Boolean
        get() = isObsProject(projectId)

    private fun readTranslationFormat(): TranslationFormat {
        val parsedFormat = TranslationFormat.get(manifest.format)
        if (parsedFormat == TranslationFormat.UNKNOWN) {
            val resType = ResourceType.get(manifest.type.slug)
            return if (resType != ResourceType.TEXT) {
                TranslationFormat.MARKDOWN
            } else {
                if (isObsProject(manifest.project.slug)) {
                    TranslationFormat.MARKDOWN
                } else {
                    TranslationFormat.USFM
                }
            }
        }
        return parsedFormat
    }

    fun addSourceTranslation(translation: Translation, modifiedAt: Int) {
        val sourceTranslation = translation
            .toSourceTranslation(modifiedAt)
            .toSource()
        val updatedTranslations = if (sourceTranslation !in manifest.sourceTranslations) {
            manifest.sourceTranslations + sourceTranslation
        } else manifest.sourceTranslations

        manifestAccessor.save(manifest.copy(sourceTranslations = updatedTranslations.distinct()))
    }

    fun setSourceTranslations(translations: List<SourceTranslation>) {
        val manifest = manifest.copy(
            sourceTranslations = translations.map { it.toSource() }.distinct()
        )
        manifestAccessor.save(manifest)
    }

    val sourceTranslations: List<String>
        get() = manifest.sourceTranslations.map {
            ContainerTools.makeSlug(it.languageSlug, this.projectId, it.resourceSlug)
        }

    fun addContributor(speaker: NativeSpeaker) {
        val updatedTranslators = if (speaker.name !in manifest.translators) {
            manifest.translators + speaker.name
        } else manifest.translators
        manifestAccessor.save(manifest.copy(translators = updatedTranslators.sorted()))
    }

    fun removeContributor(speaker: NativeSpeaker) {
        val updatedTranslators = manifest.translators - speaker.name
        manifestAccessor.save(manifest.copy(translators = updatedTranslators.sorted()))
    }

    fun getContributor(name: String): NativeSpeaker? {
        return contributors.find { it.name == name }
    }

    val contributors: List<NativeSpeaker>
        get() = manifest.translators.map { NativeSpeaker(it) }

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
            val updatedChunks = manifest.finishedChunks + complexId
            manifestAccessor.save(
                manifest.copy(finishedChunks = updatedChunks.sorted())
            )
        }
        return true
    }

    private fun openChunk(complexId: String): Boolean {
        val finishedChunks = manifest.finishedChunks
        val updatedChunks = mutableListOf<String>()
        try {
            for (i in 0 until finishedChunks.size) {
                val currId = finishedChunks[i]
                if (currId != complexId) {
                    updatedChunks.add(currId)
                }
            }
            manifestAccessor.save(
                manifest.copy(finishedChunks = updatedChunks.sorted())
            )
            return true
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun isChunkClosed(complexId: String): Boolean {
        return manifest.finishedChunks.any { it == complexId }
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
        val draft = Manifest.Draft(
            resourceSlug = draftTranslation.resource.slug,
            checkingLevel = draftTranslation.resource.status.checkingLevel,
            version = draftTranslation.resource.status.version
        )
        manifestAccessor.save(manifest.copy(parentDraft = draft))
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

        val importedManifest = ManifestAccessor(
            File(newDir, Manifest.MANIFEST_JSON)
        ).manifest
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
        merge.include(repo.git.repository.findRef("new"))
        val result = merge.call()

        // merge manifests
        val mergedManifest = mergeManifests(importedManifest)
        manifestAccessor.save(mergedManifest)

        return result.mergeStatus != MergeResult.MergeStatus.CONFLICTING
    }

    fun changeTargetLanguage(targetLanguage: TargetLanguage) {
        manifestAccessor.save(manifest.copy(targetLanguage = targetLanguage))
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
        get() = manifest.finishedChunks.size

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

    fun updateGenerator(build: String) {
        val manifest = manifest.copy(
            generator = manifest.generator.copy(build = build)
        )
        manifestAccessor.save(manifest)
    }

    fun mergeManifests(imported: Manifest): Manifest {
        val translators = (manifest.translators + imported.translators).distinct()
        val finishedChunks = (manifest.finishedChunks + imported.finishedChunks).distinct()
        val sources = (manifest.sourceTranslations + imported.sourceTranslations).distinct()
        val parentDraft = if (manifest.parentDraft == null) {
            imported.parentDraft
        } else manifest.parentDraft

        val mergedManifest = manifest.copy(
            translators = translators,
            finishedChunks = finishedChunks,
            sourceTranslations = sources,
            parentDraft = parentDraft
        )

        return mergedManifest
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

        fun open(targetTranslationDir: File, onError: (() -> Unit)? = null): TargetTranslation? {
            if (targetTranslationDir.exists()) {
                val manifestFile = File(targetTranslationDir, "manifest.json")
                if (manifestFile.exists()) {
                    try {
                        val manifest = ManifestAccessor(manifestFile).manifest
                        if (manifest.packageVersion == PACKAGE_VERSION) {
                            return TargetTranslation(targetTranslationDir)
                        } else {
                            Logger.w(
                                TargetTranslation::class.java.name,
                                "Unsupported target translation version ${manifest.packageVersion} in ${targetTranslationDir.name}"
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
            platform: Platform,
            translator: NativeSpeaker,
            translationFormat: TranslationFormat,
            targetLanguage: TargetLanguage,
            projectId: String,
            resourceType: ResourceType,
            resourceSlug: String,
            targetTranslationDir: File
        ): TargetTranslation {
            targetTranslationDir.mkdirs()
            val manifestAccessor = ManifestAccessor(
                File(targetTranslationDir, Manifest.MANIFEST_JSON)
            )

            val manifest = buildManifest {
                packageVersion(PACKAGE_VERSION)
                format(translationFormat.title)
                generator(APPLICATION_NAME, platform.info.versionCode.toString())
                targetLanguage(targetLanguage)
                project(projectId, "")
                type(resourceType)
                resource(resourceSlug, getResourceName(resourceSlug))
                addTranslator(translator)
            }

            manifestAccessor.save(manifest)

            val licenseFile = File(targetTranslationDir, LICENSE_FILE)
            context.assets.open(LICENSE_FILE).use { input ->
                FileUtilities.copyInputStreamToFile(input, licenseFile)
            }

            return TargetTranslation(targetTranslationDir)
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

        private fun getResourceName(resourceSlug: String): String {
            return when (resourceSlug) {
                "reg" -> "Regular"
                "ulb" -> "Unlocked Literal Bible"
                "udb" -> "Unlocked Dynamic Bible"
                "obs" -> "Open Bible Stories"
                else -> resourceSlug
            }
        }
    }
}