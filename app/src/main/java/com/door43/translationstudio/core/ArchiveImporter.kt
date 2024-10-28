package com.door43.translationstudio.core

import com.door43.util.FileUtilities.readFileToString
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import javax.inject.Inject


/**
 * Handles the importing of tstudio archives.
 * The importing is placed here to keep the Translator clean and organized.
 */
class ArchiveImporter @Inject constructor(
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
        val targetTranslationDirs: List<File>
        if (manifestFile.exists()) {
            val manifestJson = JSONObject(readFileToString(manifestFile))
            if (manifestJson.has("package_version")) {
                val packageVersion = manifestJson.getInt("package_version")
                targetTranslationDirs = when (packageVersion) {
                    1 -> v1(manifestJson, expandedArchiveDir) // just to keep the switch pretty
                    2 -> v2(manifestJson, expandedArchiveDir)
                    else -> listOf(expandedArchiveDir)
                }
            } else {
                targetTranslationDirs = v1(manifestJson, expandedArchiveDir)
            }
        } else {
            targetTranslationDirs = legacy(expandedArchiveDir)
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
     * @param packageManifest
     * @param dir
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun v2(packageManifest: JSONObject, dir: File): List<File> {
        val files = arrayListOf<File>()
        val translationsJson = packageManifest.getJSONArray("target_translations")
        for (i in 0 until translationsJson.length()) {
            val translation = translationsJson.getJSONObject(i)
            files.add(File(dir, translation.getString("path")))
        }
        return files
    }

    /**
     * targetTranslations are in directories labled by id
     * @param manifest
     * @param dir
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun v1(manifest: JSONObject, dir: File): List<File> {
        val files = arrayListOf<File>()
        val translationsJson = manifest.getJSONArray("projects")
        for (i in 0 until translationsJson.length()) {
            val translation = translationsJson.getJSONObject(i)
            files.add(File(dir, translation.getString("path")))
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
