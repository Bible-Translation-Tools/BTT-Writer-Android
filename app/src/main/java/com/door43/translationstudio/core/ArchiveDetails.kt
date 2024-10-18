package com.door43.translationstudio.core

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.door43.data.IDirectoryProvider
import com.door43.util.FileUtilities.copyInputStreamToFile
import com.door43.util.Zip
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.Door43Client
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject

/**
 * Holds details about the translation archive
 * TODO: this duplicates a lot of code from ArchiveImporter. Eventually it might be nice to refactor both so that there is less duplication.
 */
class ArchiveDetails private constructor(
    val createdAt: Long,
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
        private val context: Context,
        private val directoryProvider: IDirectoryProvider,
        private val migrator: TargetTranslationMigrator,
        private val library: Door43Client
    ) {
        private var archiveStream: InputStream? = null
        private var archiveFile: File? = null
        private var archiveDocument: DocumentFile? = null

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

        /**
         * Reads the details from a translationStudio archive
         * @param archive
         * @param preferredLocale
         * @return
         * @throws IOException
         */
        @Throws(Exception::class)
        fun fromDocument(
            archive: DocumentFile,
            preferredLocale: String
        ): Builder {
            this.archiveDocument = archive
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
                val json = JSONObject(rawManifest)
                if (json.has(PACKAGE_VERSION)) {
                    val manifestVersion = json.getInt(PACKAGE_VERSION)
                    when (manifestVersion) {
                        1 -> return parseV1Manifest(json)
                        2 -> return parseV2Manifest(
                            FileInputStream(tempFile),
                            json,
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
                    val json = JSONObject(rawManifest)
                    if (json.has(PACKAGE_VERSION)) {
                        val manifestVersion = json.getInt(PACKAGE_VERSION)
                        when (manifestVersion) {
                            1 -> return parseV1Manifest(json)
                            2 -> return parseV2Manifest(
                                FileInputStream(archive),
                                json,
                                preferredLocale
                            )
                        }
                    }
                }
            }
            return null
        }

        private fun processDocument(
            archive: DocumentFile,
            preferredLocale: String
        ): ArchiveDetails? {
            if (archive.exists()) {
                val ais = context.contentResolver.openInputStream(archive.uri)
                val rawManifest = Zip.readInputStream(ais, MANIFEST_JSON)
                if (rawManifest != null) {
                    val json = JSONObject(rawManifest)
                    if (json.has(PACKAGE_VERSION)) {
                        val manifestVersion = json.getInt(PACKAGE_VERSION)
                        when (manifestVersion) {
                            1 -> return parseV1Manifest(json)
                            2 -> context.contentResolver.openInputStream(archive.uri)?.let {
                                return parseV2Manifest(
                                    it,
                                    json,
                                    preferredLocale
                                )
                            }
                        }
                    }
                }
            }
            return null
        }

        private fun parseV1Manifest(json: JSONObject): ArchiveDetails? {
            return null
        }

        @Throws(JSONException::class, IOException::class)
        private fun parseV2Manifest(
            ais: InputStream,
            archiveManifest: JSONObject,
            preferredLocale: String
        ): ArchiveDetails {
            val targetDetails = arrayListOf<TargetTranslationDetails>()
            val timestamp = archiveManifest.getLong("timestamp")
            val translationsJson = archiveManifest.getJSONArray("target_translations")
            for (i in 0 until translationsJson.length()) {
                val translationRecordJson = translationsJson.getJSONObject(i)
                val path = translationRecordJson.getString("path")

                ais.use { stream ->
                    val rawTranslationManifest = Zip.readInputStream(
                        stream,
                        path.replace("/+$".toRegex(), "") + "/manifest.json"
                    )
                    if (rawTranslationManifest != null) {
                        var manifest: JSONObject? = JSONObject(rawTranslationManifest)

                        // migrate the manifest
                        manifest = migrator.migrateManifest(manifest!!)

                        if (manifest != null) {
                            val targetLanguageJson = manifest.getJSONObject("target_language")
                            val projectJson = manifest.getJSONObject("project")

                            // get target language
                            val targetLanguageName: String?
                            val targetLanguageSlug = targetLanguageJson.getString("id")
                            val targetLanguageDirection = targetLanguageJson.getString("direction")
                            val tl = library.index.getTargetLanguage(targetLanguageSlug)
                            targetLanguageName = if (tl != null) {
                                tl.name
                            } else {
                                targetLanguageSlug.uppercase(Locale.getDefault())
                            }

                            // get project
                            val projectName: String?
                            val projectSlug = projectJson.getString("id")
                            val project = library.index.getProject(
                                preferredLocale,
                                projectSlug,
                                true
                            )
                            projectName = if (project != null) {
                                project.name
                            } else {
                                projectSlug.uppercase(Locale.getDefault())
                            }

                            // git commit hash
                            val commit = translationRecordJson.getString("commit_hash")

                            // translation type
                            var resourceType = ResourceType.get(
                                manifest.getJSONObject("type").getString("id")
                            )
                            if (resourceType == null) {
                                resourceType = ResourceType.TEXT
                            }

                            // resource
                            var resourceSlug: String? = null
                            if (manifest.has("resource")) {
                                resourceSlug = manifest.getJSONObject("resource")
                                    .getString("id")
                            }

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
                                    targetLanguageName,
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
            return ArchiveDetails(
                timestamp,
                targetDetails
            )
        }

        fun build(): ArchiveDetails? {
            return try {
                when {
                    archiveStream != null -> processInputStream(archiveStream!!, preferredLocale!!)
                    archiveFile != null -> processFile(archiveFile!!, preferredLocale!!)
                    archiveDocument != null -> processDocument(archiveDocument!!, preferredLocale!!)
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
        const val PACKAGE_VERSION: String = "package_version"

        /**
         * Returns an empty archive
         * @return
         */
        fun newDummyInstance(): ArchiveDetails {
            return ArchiveDetails(0, listOf())
        }
    }
}
