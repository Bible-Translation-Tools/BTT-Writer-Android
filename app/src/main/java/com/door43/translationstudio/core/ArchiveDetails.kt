package com.door43.translationstudio.core

import android.util.Log
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.core.manifest.Manifest
import com.door43.translationstudio.core.manifest.manifestJson
import com.door43.util.FileUtilities.copyInputStreamToFile
import com.door43.util.Zip
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecontainer.IntAsStringSerializer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/**
 * Holds details about the translation archive
 * TODO: this duplicates a lot of code from ArchiveImporter. Eventually it might be nice to refactor both so that there is less duplication.
 */
class ArchiveDetails private constructor(
    val createdAt: Int,
    val targetTranslationDetails: List<TargetTranslationDetails>
) {
    /**
     * Contains details about a target translation in the archive
     */
    class TargetTranslationDetails internal constructor(
        val targetTranslationSlug: String,
        val targetLanguageSlug: String,
        val targetLanguageName: String?,
        val projectSlug: String,
        val projectName: String?,
        val direction: String,
        val commitHash: String
    )

    class Builder(
        private val directoryProvider: IDirectoryProvider,
        private val migrator: TargetTranslationMigrator,
        private val catalogClient: ResourceCatalogClient
    ) {
        private var archiveStream: InputStream? = null
        private var archiveFile: File? = null

        private var preferredLocale: String? = null

        /**
         * Reads the details from a translationStudio archive
         * @param archiveStream
         * @param preferredLocale
         * @return
         * @throws Exception
         */
        fun fromInputStream(
            archiveStream: InputStream,
            preferredLocale: String
        ): Builder {
            this.archiveStream = archiveStream
            this.preferredLocale = preferredLocale
            return this
        }

        /**
         * Reads the details from a translationStudio archive
         * @param archive
         * @param preferredLocale
         * @return
         * @throws IOException
         */
        @Throws(Exception::class)
        fun fromFile(
            archive: File,
            preferredLocale: String
        ): Builder {
            this.archiveFile = archive
            this.preferredLocale = preferredLocale
            return this
        }

        private fun processInputStream(
            archiveStream: InputStream,
            preferredLocale: String
        ): ArchiveDetails? {
            val tempFile = directoryProvider.createTempFile(
                "targettranslation",
                "." + Translator.TSTUDIO_EXTENSION
            )
            copyInputStreamToFile(archiveStream, tempFile)

            val rawManifest = Zip.read(tempFile, MANIFEST_JSON)
            if (rawManifest != null) {
                val raw = archiveJson.parseToJsonElement(rawManifest).jsonObject
                val manifestVersion = raw["package_version"]?.jsonPrimitive?.intOrNull ?: 2

                when (manifestVersion) {
                    1 -> {
                        return parseV1Manifest(
                            archiveJson.decodeFromString(rawManifest)
                        )
                    }
                    2 -> {
                        return parseV2Manifest(
                            tempFile,
                            archiveJson.decodeFromString(rawManifest),
                            preferredLocale
                        )
                    }
                }
            }
            return null
        }

        private fun processFile(archive: File, preferredLocale: String): ArchiveDetails? {
            if (archive.exists()) {
                val rawManifest = Zip.read(archive, MANIFEST_JSON)
                if (rawManifest != null) {
                    val raw = archiveJson.parseToJsonElement(rawManifest).jsonObject
                    val manifestVersion = raw["package_version"]?.jsonPrimitive?.intOrNull ?: 2
                    when (manifestVersion) {
                        1 -> {
                            return parseV1Manifest(
                                archiveJson.decodeFromString(rawManifest)
                            )
                        }
                        2 -> {
                            return parseV2Manifest(
                                archive,
                                archiveJson.decodeFromString(rawManifest),
                                preferredLocale
                            )
                        }
                    }
                }
            }
            return null
        }

        private fun parseV1Manifest(archiveManifest: ArchiveManifestV1): ArchiveDetails? {
            return null
        }

        @Throws(IOException::class)
        private fun parseV2Manifest(
            archive: File,
            archiveManifest: ArchiveManifest,
            preferredLocale: String
        ): ArchiveDetails {
            val targetDetails = arrayListOf<TargetTranslationDetails>()
            val timestamp = archiveManifest.timestamp
            archiveManifest.targetTranslations.forEach { translation ->
                val path = translation.path

                archive.inputStream().use { stream ->
                    val rawTranslationManifest = Zip.readInputStream(
                        stream,
                        path.replace("/+$".toRegex(), "") + "/manifest.json"
                    )
                    if (rawTranslationManifest != null) {
                        // migrate the manifest
                        val manifestStr = migrator.migrateManifest(rawTranslationManifest)

                        if (manifestStr != null) {
                            val manifest = manifestJson.decodeFromString<Manifest>(manifestStr)

                            // get target language
                            val tlName: String?
                            val targetLanguageSlug = manifest.targetLanguage.slug
                            val targetLanguageDirection = manifest.targetLanguage.direction
                            val tl = catalogClient.library.getTargetLanguage(targetLanguageSlug)
                            tlName = tl?.name ?: targetLanguageSlug.uppercase(Locale.getDefault())

                            // get project
                            val projectName: String?
                            val projectSlug = manifest.project.slug
                            val project = catalogClient.library.getProject(
                                preferredLocale,
                                projectSlug,
                                true
                            )
                            projectName = project?.name ?: projectSlug.uppercase(Locale.getDefault())

                            // git commit hash
                            val commit = translation.commitHash

                            // translation type
                            var resourceType = ResourceType.get(manifest.type.slug)
                            if (resourceType == null) {
                                resourceType = ResourceType.TEXT
                            }

                            // resource
                            val resourceSlug = manifest.resource.slug

                            // build id
                            val targetTranslationId = TargetTranslation.generateTargetTranslationId(
                                targetLanguageSlug,
                                projectSlug,
                                resourceType,
                                resourceSlug
                            )

                            targetDetails.add(
                                TargetTranslationDetails(
                                    targetTranslationId,
                                    targetLanguageSlug,
                                    tlName,
                                    projectSlug,
                                    projectName,
                                    targetLanguageDirection,
                                    commit
                                )
                            )
                        }
                    }
                }
            }

            return ArchiveDetails(timestamp, targetDetails)
        }

        fun build(): ArchiveDetails? {
            return try {
                when {
                    archiveStream != null -> processInputStream(archiveStream!!, preferredLocale!!)
                    archiveFile != null -> processFile(archiveFile!!, preferredLocale!!)
                    else -> null
                }
            } catch (e: Exception) {
                Log.e(ArchiveDetails::class.simpleName, "Failed to build ArchiveDetails instance", e)
                null
            }
        }
    }

    companion object {
        const val MANIFEST_JSON: String = "manifest.json"

        val archiveJson = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            ignoreUnknownKeys = true
            encodeDefaults = true

            serializersModule = SerializersModule {
                contextual(String::class, IntAsStringSerializer)
            }
        }

        /**
         * Returns an empty archive
         * @return
         */
        fun newDummyInstance(): ArchiveDetails {
            return ArchiveDetails(0, listOf())
        }
    }
}

@Serializable
data class ArchiveManifestV1(
    @SerialName("package_version")
    val packageVersion: Int,
    val timestamp: Int,
    val generator: ArchiveGenerator,
    @SerialName("projects")
    val targetTranslations: List<ArchiveTranslation> = emptyList()
)

@Serializable
data class ArchiveManifest(
    @SerialName("package_version")
    val packageVersion: Int,
    val timestamp: Int,
    val generator: ArchiveGenerator,
    @SerialName("target_translations")
    val targetTranslations: List<ArchiveTranslation> = emptyList()
)

@Serializable
data class ArchiveGenerator(
    val name: String,
    @Contextual
    val build: String
)

@Serializable
data class ArchiveTranslation(
    val id: String,
    val path: String,
    @SerialName("commit_hash")
    val commitHash: String,
    val direction: String,
    @SerialName("target_language_name")
    val targetLanguageName: String
)
