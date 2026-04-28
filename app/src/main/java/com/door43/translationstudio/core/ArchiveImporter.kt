package com.door43.translationstudio.core

import com.door43.translationstudio.core.ArchiveDetails.Companion.archiveJson
import com.door43.util.FileUtilities.readFileToString
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File


/**
 * Handles the importing of tstudio archives.
 * The importing is placed here to keep the Translator clean and organized.
 */
class ArchiveImporter(
    private val migrator: TargetTranslationMigrator
) {
    /**
     * Prepares an archive for import with backwards compatible support.
     * @param expandedArchiveDir
     * @return an array of target translation directories that are ready and valid for import
     * @throws Exception
     */
    @Throws(Exception::class)
    fun importArchive(expandedArchiveDir: File): List<File> {
        val validTargetTranslations = arrayListOf<File>()

        // retrieve target translations from archive
        val manifestFile = File(expandedArchiveDir, "manifest.json")
        val targetTranslationDirs = if (manifestFile.exists()) {
            val rawManifest = readFileToString(manifestFile)
            val raw = archiveJson.parseToJsonElement(readFileToString(manifestFile)).jsonObject
            val manifestVersion = raw["package_version"]?.jsonPrimitive?.intOrNull

            when (manifestVersion) {
                1 -> v1(
                    archiveJson.decodeFromString(rawManifest),
                    expandedArchiveDir
                )
                2 -> v2(
                    archiveJson.decodeFromString(rawManifest),
                    expandedArchiveDir
                )
                else -> listOf(expandedArchiveDir)
            }
        } else {
            legacy(expandedArchiveDir)
        }

        // migrate target translations
        for (dir in targetTranslationDirs) {
            val migratedDir = migrator.migrate(dir)
            if (migratedDir != null) {
                validTargetTranslations.add(migratedDir)
            }
        }
        return validTargetTranslations
    }

    /**
     * translation dirs in the archive are named after their id
     * so we only need to return the path.
     * @param manifest
     * @param dir
     * @return
     */
    private fun v2(manifest: ArchiveManifest, dir: File): List<File> {
        val files = arrayListOf<File>()
        manifest.targetTranslations.forEach { translation ->
            files.add(File(dir, translation.path))
        }
        return files
    }

    /**
     * targetTranslations are in directories labeled by id
     * @param manifest
     * @param dir
     * @return
     */
    private fun v1(manifest: ArchiveManifestV1, dir: File): List<File> {
        val files = arrayListOf<File>()
        manifest.targetTranslations.forEach { translation ->
            files.add(File(dir, translation.path))
        }
        return files
    }

    /**
     * todo: provide support for legacy archives.. if needed
     * @return
     */
    private fun legacy(dir: File): List<File> {
//        val translationDirs = dir.list() ?: return listOf()
//        for (targetTranslationId in translationDirs) {
//            val id = StringUtilities.ltrim(targetTranslationId, '\\')
//            try {
//                val projectSlug = TargetTranslation.getProjectSlugFromId(targetTranslationId)
//                val targetLanguageSlug = TargetTranslation.getTargetLanguageSlugFromId(
//                    targetTranslationId
//                )
//            } catch (e: java.lang.Exception) {
//                e.printStackTrace()
//                continue
//            }
//        }
        return listOf()
    }
}
