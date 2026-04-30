package com.door43.translationstudio.core

import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.manifest.Manifest
import com.door43.translationstudio.core.manifest.toType
import com.door43.translationstudio.rendering.USXtoUSFMConverter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bibletranslationtools.logger.Logger
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecatalog.library.models.TargetLanguage
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import java.io.File

class TargetTranslationMigrator(
    private val directoryProvider: IDirectoryProvider,
    private val catalogClient: ResourceCatalogClient,
    private val assetProvider: AssetsProvider,
) {
    companion object {
        private const val MANIFEST_FILE = "manifest.json"
        const val LICENSE = "LICENSE"
        const val TAG = "TargetTranslationMigrator"

        private val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    fun migrateManifest(manifest: String): String? {
        val tempDir = directoryProvider.createTempDir(System.currentTimeMillis().toString())
        val fakeTranslationDir = File(tempDir, "translation")
        fakeTranslationDir.mkdirs()
        return try {
            val manifestFile = File(fakeTranslationDir, MANIFEST_FILE)
            manifestFile.writeText(manifest)
            migrate(fakeTranslationDir, manifestFile)
            manifestFile.readText()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun migrate(
        targetTranslationDir: File,
        manifestFile: File = File(targetTranslationDir, MANIFEST_FILE),
    ): File? {
        return try {
            val raw = json.parseToJsonElement(manifestFile.readText()).jsonObject
            val packageVersion = raw["package_version"]?.jsonPrimitive?.intOrNull ?: 2

            var migratedDir: File? = when (packageVersion) {
                2 -> v8(v7(v6(v5(v4(v3(v2(targetTranslationDir)))))))
                3 -> v8(v7(v6(v5(v4(v3(targetTranslationDir))))))
                4 -> v8(v7(v6(v5(v4(targetTranslationDir)))))
                5 -> v8(v7(v6(v5(targetTranslationDir))))
                6 -> v8(v7(v6(targetTranslationDir)))
                7 -> v8(v7(targetTranslationDir))
                8 -> v8(targetTranslationDir)
                else -> targetTranslationDir
            }

            if (!validateTranslationType(targetTranslationDir)) {
                migratedDir = null
            }
            migratedDir
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun v8(path: File): File = path

    private fun v7(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val v7 = json.decodeFromString<ManifestV7>(manifestFile.readText())

        val resourceName = v7.resource.name.ifEmpty {
            when (v7.resource.slug) {
                "reg" -> "Regular"
                "obs" -> "Open Bible Stories"
                "udb" -> "Unlocked Dynamic Bible"
                "ulb" -> "Unlocked Literal Bible"
                else -> v7.resource.slug
            }
        }

        val updated = v7.copy(
            packageVersion = 8,
            resource = v7.resource.copy(name = resourceName),
        )
        manifestFile.writeText(json.encodeToString(updated))
        return path
    }

    private fun v6(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val v6 = json.decodeFromString<ManifestV6>(manifestFile.readText())
        val projectSlug = v6.project.slug

        val chapters = path.listFiles { file ->
            file.isDirectory && file.name != ".git" && file.name != "cache"
        } ?: emptyArray()

        val translations = catalogClient.library.findTranslations(
            "en",
            projectSlug,
            null,
            "book",
            null,
            3,
            -1
        )
        var updatedFinishedChunks = v6.finishedChunks.toMutableList()

        if (translations.isNotEmpty()) {
            val sourceTranslation = translations.find { it.resource.slug == "ulb" } ?: translations.first()
            val container = catalogClient.openResourceContainer(sourceTranslation.resourceContainerSlug)

            for (dir in chapters) {
                val chunk00 = File(dir, "00.txt")
                if (chunk00.exists()) {
                    val chunkId = largestIntVal(container.chunks(dir.name).toList())
                    if (chunkId != null) {
                        val chunk = File(dir, "$chunkId.txt")
                        if (chunk00.renameTo(chunk)) {
                            val old = "${dir.name}-00"
                            val new = "${dir.name}-$chunkId"
                            updatedFinishedChunks = updatedFinishedChunks
                                .map { if (it == old) new else it }
                                .toMutableList()
                        }
                    }
                }
            }
        }

        // migrate 00 chapter -> front
        val chapter00 = File(path, "00")
        if (chapter00.exists() && chapter00.isDirectory) {
            chapter00.renameTo(File(path, "front"))
        }

        val updated = v6.copy(
            packageVersion = 7,
            finishedChunks = updatedFinishedChunks,
        )
        manifestFile.writeText(json.encodeToString(updated))
        return path
    }

    private fun v5(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val v5 = json.decodeFromString<ManifestV5>(manifestFile.readText())

        val targetLanguageCode = v5.targetLanguage.slug
        val projectSlug = v5.project.slug
        val translationTypeSlug = v5.type.slug
        val resourceSlug = if (translationTypeSlug == "text") v5.resource?.slug else null

        val id = buildString {
            append("${targetLanguageCode}_${projectSlug}_$translationTypeSlug")
            if (translationTypeSlug == "text" && resourceSlug != null) append("_$resourceSlug")
        }

        val licenseFile = File(path, "LICENSE.md")
        if (!licenseFile.exists()) {
            assetProvider.open("LICENSE.md").use { input ->
                input.copyTo(licenseFile.outputStream())
            }
        }

        val updated = v5.copy(packageVersion = 6)
        manifestFile.writeText(json.encodeToString(updated))

        val newPath = File(path.parentFile, id.lowercase())
        newPath.deleteRecursively()
        path.renameTo(newPath)
        return newPath
    }

    private fun v4(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val v3 = json.decodeFromString<ManifestV3>(manifestFile.readText())

        // resolve type
        val typeSlug = v3.type?.slug ?: "text"
        val type = ResourceType.get(typeSlug)?.toType() ?: Manifest.Type(typeSlug, "")

        // resolve project
        val project = v3.project
            ?: v3.projectId?.let { Manifest.Project(it, it.uppercase()) }
            ?: Manifest.Project("", "")

        // resolve resource
        val resource: Manifest.Resource? = if (type.slug == "text") {
            v3.resource ?: v3.resourceId?.let { id ->
                when (id) {
                    "ulb" -> Manifest.Resource("ulb", "Unlocked Literal Bible")
                    "udb" -> Manifest.Resource("udb", "Unlocked Dynamic Bible")
                    "obs" -> Manifest.Resource("obs", "Open Bible Stories")
                    else -> Manifest.Resource("reg", "Regular")
                }
            } ?: if (project.slug == "obs") {
                Manifest.Resource("obs", "Open Bible Stories")
            } else {
                Manifest.Resource("reg", "Regular")
            }
        } else null

        // resolve source translations
        val sourceTranslations = when (val st = v3.sourceTranslations) {
            is JsonArray -> json.decodeFromJsonElement<List<Manifest.Source>>(st)
            is JsonObject -> st.entries.mapNotNull { (key, value) ->
                runCatching {
                    val parts = key.split("-", limit = 2)
                    if (parts.size != 2) return@mapNotNull null
                    val languageResourceId = parts[1]
                    val pieces = languageResourceId.split("-")
                    if (pieces.isEmpty()) return@mapNotNull null
                    val resId = pieces.last()
                    val langId = languageResourceId.dropLast(resId.length + 1)
                    val obj = value.jsonObject
                    Manifest.Source(
                        languageSlug = langId,
                        resourceSlug = resId,
                        checkingLevel = obj["checking_level"]!!.jsonPrimitive.content,
                        modifiedAt = obj["date_modified"]!!.jsonPrimitive.content,
                        version = obj["version"]!!.jsonPrimitive.content,
                    )
                }.getOrNull()
            }
            else -> emptyList()
        }

        // resolve parent draft
        val parentDraft = v3.parentDraftResourceId?.let {
            Manifest.Draft(
                resourceSlug = it,
                comments = "The parent draft is unknown",
            )
        } ?: Manifest.Draft()

        // resolve finished chunks
        val finishedChunks = v3.finishedFrames.toMutableList().also { chunks ->
            v3.finishedTitles.forEach { chunks.add("$it-title") }
            v3.finishedReferences.forEach { chunks.add("$it-reference") }
            v3.finishedProjectComponents.forEach { chunks.add("00-$it") }
        }

        // resolve format
        val format = v3.format?.takeIf { it.isNotEmpty() && it != "usx" && it != "default" }
            ?: if (type.slug != "text" || project.slug == "obs") "markdown" else "usfm"

        // migrate project title
        val oldProjectTitle = File(path, "title.txt")
        val newProjectTitle = File(path, "00/title.txt")
        if (oldProjectTitle.exists()) {
            newProjectTitle.parentFile?.mkdirs()
            oldProjectTitle.renameTo(newProjectTitle)
        }

        val v4 = ManifestV4(
            packageVersion = 5,
            project = project,
            type = type,
            resource = resource,
            targetLanguage = v3.targetLanguage,
            translators = v3.translators,
            finishedChunks = finishedChunks,
            sourceTranslations = sourceTranslations,
            parentDraft = parentDraft,
            format = format,
        )
        manifestFile.writeText(json.encodeToString(v4))

        // migrate usx -> usfm
        if (format == "usfm") {
            path.listFiles { f -> f.isDirectory && f.name != ".git" }?.forEach { cDir ->
                cDir.listFiles()?.forEach { chunkFile ->
                    runCatching {
                        val usfm = USXtoUSFMConverter.doConversion(chunkFile.readText()).toString()
                        chunkFile.writeText(usfm)
                    }
                }
            }
        }

        return path
    }

    private fun v3(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val v2 = json.decodeFromString<ManifestV3>(manifestFile.readText())

        val translators = v2.translators.mapNotNull { element ->
            when {
                element is JsonPrimitive && element.isString -> element.content
                element is JsonObject -> element["name"]?.jsonPrimitive?.content
                else -> null
            }
        }

        val updated = v2.copy(
            packageVersion = 4,
            translators = translators,
        )
        manifestFile.writeText(json.encodeToString(updated))

        val projectSlug = v2.projectId ?: ""
        migrateChunkChanges(path, projectSlug)
        return path
    }

    private fun v2(path: File): File {
        val manifestFile = File(path, MANIFEST_FILE)
        val v2 = json.decodeFromString<ManifestV2>(manifestFile.readText())

        val finishedFrames = v2.frames
            .filter { it.value.finished }
            .map { it.key }
            .toMutableList()
            .also { it.addAll(v2.finishedFrames) }

        val finishedTitles = v2.chapters
            .filter { it.value.finishedTitle }
            .map { it.key }
            .toMutableList()
            .also { it.addAll(v2.finishedTitles) }

        val finishedReferences = v2.chapters
            .filter { it.value.finishedReference }
            .map { it.key }
            .toMutableList()
            .also { it.addAll(v2.finishedReferences) }

        val projectId = v2.projectId ?: v2.slug ?: ""
        val targetLanguageId = v2.targetLanguage.id ?: v2.targetLanguage.slug ?: ""
        val targetLanguage = TargetLanguage(
            slug = targetLanguageId,
            name = v2.targetLanguage.name,
            direction = v2.targetLanguage.direction,
        )

        val v3 = ManifestV3(
            packageVersion = 3,
            projectId = projectId,
            targetLanguage = targetLanguage,
            translators = emptyList(), // v3() will fix these
            finishedFrames = finishedFrames,
            finishedTitles = finishedTitles,
            finishedReferences = finishedReferences,
        )
        manifestFile.writeText(json.encodeToString(v3))
        return path
    }

    private fun largestIntVal(list: List<String>): String? =
        list.mapNotNull { it.toIntOrNull() }.maxOrNull()?.toString()

    private fun migrateChunkChanges(targetTranslationDir: File, projectSlug: String): Boolean {
        val p = catalogClient.library.getProject(
            "en",
            projectSlug,
            true
        ) ?: return true
        val resources = catalogClient.library.getResources(
            p.languageSlug,
            p.slug
        )
        val resource = resources.firstOrNull {
            it.type.equals("book", ignoreCase = true)
        } ?: return true

        val resourceContainer = runCatching {
            catalogClient.openResourceContainer(
                p.languageSlug,
                p.slug, resource.slug
            )
        }.getOrElse { return true }

        val chapterDirs = targetTranslationDir.listFiles { f ->
            f.isDirectory && f.name != ".git" && f.name != "00"
        } ?: return true

        val manifestFile = File(targetTranslationDir, MANIFEST_FILE)
        chapterDirs.forEach { mergeInvalidChunksInChapter(manifestFile, resourceContainer, it) }
        return true
    }

    private fun mergeInvalidChunksInChapter(
        manifestFile: File,
        resourceContainer: ResourceContainer,
        chapterDir: File,
    ): Boolean {
        val manifestV3 = runCatching {
            json.decodeFromString<ManifestV3>(manifestFile.readText())
        }.getOrElse { return false }

        val chunkMergeMarker = "\n----------\n"
        var frameFiles = chapterDir.listFiles { f ->
            f.name != "title.txt" && f.name != "reference.txt"
        }?.sortedArray() ?: return true

        var invalidChunks = ""
        var lastValidFrameFile: File? = null
        val chapterId = chapterDir.name
        val updatedFinishedFrames = manifestV3.finishedFrames.toMutableList()

        for (frameFile in frameFiles) {
            val frameId = frameFile.nameWithoutExtension
            val chunkText = resourceContainer.readChunk(chapterId, frameId)
            val frameBody = runCatching { frameFile.readText().trim() }.getOrDefault("")

            if (chunkText.isNotEmpty()) {
                lastValidFrameFile = frameFile
                if (invalidChunks.isNotEmpty()) {
                    frameFile.writeText(invalidChunks + frameBody)
                    invalidChunks = ""
                    updatedFinishedFrames.remove("$chapterId-$frameId")
                }
            } else if (frameBody.isNotEmpty()) {
                if (lastValidFrameFile == null) {
                    invalidChunks += frameBody + chunkMergeMarker
                } else {
                    val lastBody = runCatching {
                        lastValidFrameFile.readText()
                    }.getOrDefault("")
                    lastValidFrameFile.writeText(lastBody + chunkMergeMarker + frameBody)
                    updatedFinishedFrames.remove("$chapterId-${lastValidFrameFile.name}")
                }
                frameFile.delete()
            }
        }

        if (invalidChunks.isNotEmpty()) {
            frameFiles = chapterDir.listFiles { f ->
                f.name != "title.txt" && f.name != "reference.txt"
            }?.sortedArray() ?: return true

            if (frameFiles.isNotEmpty()) {
                val firstBody = runCatching { frameFiles[0].readText() }.getOrDefault("")
                frameFiles[0].writeText(invalidChunks + chunkMergeMarker + firstBody)
                updatedFinishedFrames.remove("$chapterId-${frameFiles[0].name}")
            }
        }

        val updated = manifestV3.copy(finishedFrames = updatedFinishedFrames)
        manifestFile.writeText(json.encodeToString(updated))
        return true
    }

    private fun validateTranslationType(path: File): Boolean {
        val manifest = json.decodeFromString<ManifestV7>(File(path, MANIFEST_FILE).readText())
        return if (ResourceType.get(manifest.type.slug) == ResourceType.TEXT) {
            true
        } else {
            Logger.w(TAG, "Only text translation types are supported")
            false
        }
    }
}

// Version-specific manifest shapes

@Serializable
data class ManifestV2(
    @SerialName("package_version")
    val packageVersion: Int = 2,
    val slug: String? = null,
    @SerialName("project_id")
    val projectId: String? = null,
    val frames: Map<String, FrameState> = emptyMap(),
    val chapters: Map<String, ChapterState> = emptyMap(),
    @SerialName("target_language")
    val targetLanguage: TargetLanguageV2,
    val translators: List<JsonElement> = emptyList(),
    @SerialName("finished_frames")
    val finishedFrames: List<String> = emptyList(),
    @SerialName("finished_titles")
    val finishedTitles: List<String> = emptyList(),
    @SerialName("finished_references")
    val finishedReferences: List<String> = emptyList(),
) {
    @Serializable
    data class FrameState(val finished: Boolean = false)

    @Serializable
    data class ChapterState(
        @SerialName("finished_title")
        val finishedTitle: Boolean = false,
        @SerialName("finished_reference")
        val finishedReference: Boolean = false,
    )

    @Serializable
    data class TargetLanguageV2(
        val id: String? = null,
        val slug: String? = null,
        val name: String,
        val direction: String,
    )
}

@Serializable
data class ManifestV3(
    @SerialName("package_version")
    val packageVersion: Int = 3,
    @SerialName("project_id")
    val projectId: String,
    @SerialName("target_language")
    val targetLanguage: TargetLanguage,
    val translators: List<String> = emptyList(),
    @SerialName("finished_frames")
    val finishedFrames: List<String> = emptyList(),
    @SerialName("finished_titles")
    val finishedTitles: List<String> = emptyList(),
    @SerialName("finished_references")
    val finishedReferences: List<String> = emptyList(),
    @SerialName("finished_project_components")
    val finishedProjectComponents: List<String> = emptyList(),
    @SerialName("source_translations")
    val sourceTranslations: JsonElement? = null,
    @SerialName("parent_draft_resource_id")
    val parentDraftResourceId: String? = null,
    val format: String? = null,
    val resource: Manifest.Resource? = null,
    @SerialName("resource_id")
    val resourceId: String? = null,
    val project: Manifest.Project? = null,
    val type: Manifest.Type? = null,
)

@Serializable
data class ManifestV4(
    @SerialName("package_version")
    val packageVersion: Int = 4,
    @SerialName("project_id")
    val projectId: String? = null,
    val project: Manifest.Project,
    val type: Manifest.Type,
    val resource: Manifest.Resource? = null,
    @SerialName("target_language")
    val targetLanguage: TargetLanguage,
    val translators: List<String> = emptyList(),
    @SerialName("finished_chunks")
    val finishedChunks: List<String> = emptyList(),
    @SerialName("source_translations")
    val sourceTranslations: List<Manifest.Source> = emptyList(),
    @SerialName("parent_draft")
    val parentDraft: Manifest.Draft = Manifest.Draft(),
    val format: String = "",
)

@Serializable
data class ManifestV5(
    @SerialName("package_version")
    val packageVersion: Int = 5,
    val project: Manifest.Project,
    val type: Manifest.Type,
    val resource: Manifest.Resource? = null,
    @SerialName("target_language")
    val targetLanguage: TargetLanguage,
    val translators: List<String> = emptyList(),
    @SerialName("finished_chunks")
    val finishedChunks: List<String> = emptyList(),
    @SerialName("source_translations")
    val sourceTranslations: List<Manifest.Source> = emptyList(),
    @SerialName("parent_draft")
    val parentDraft: Manifest.Draft = Manifest.Draft(),
    val format: String = "",
    val generator: Manifest.Generator = Manifest.Generator("", ""),
)

@Serializable
data class ManifestV6(
    @SerialName("package_version")
    val packageVersion: Int = 6,
    val project: Manifest.Project,
    val type: Manifest.Type,
    val resource: Manifest.Resource? = null,
    @SerialName("target_language")
    val targetLanguage: TargetLanguage,
    val translators: List<String> = emptyList(),
    @SerialName("finished_chunks")
    val finishedChunks: List<String> = emptyList(),
    @SerialName("source_translations")
    val sourceTranslations: List<Manifest.Source> = emptyList(),
    @SerialName("parent_draft")
    val parentDraft: Manifest.Draft = Manifest.Draft(),
    val format: String = "",
    val generator: Manifest.Generator = Manifest.Generator("", ""),
)

@Serializable
data class ManifestV7(
    @SerialName("package_version")
    val packageVersion: Int = 7,
    val project: Manifest.Project,
    val type: Manifest.Type,
    val resource: Manifest.Resource,
    @SerialName("target_language")
    val targetLanguage: TargetLanguage,
    val translators: List<String> = emptyList(),
    @SerialName("finished_chunks")
    val finishedChunks: List<String> = emptyList(),
    @SerialName("source_translations")
    val sourceTranslations: List<Manifest.Source> = emptyList(),
    @SerialName("parent_draft")
    val parentDraft: Manifest.Draft = Manifest.Draft(),
    val format: String = "",
    val generator: Manifest.Generator = Manifest.Generator("", ""),
)
