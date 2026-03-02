package org.unfoldingword.door43client

import com.door43.translationstudio.network.GetRequest
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.models.Catalog
import org.unfoldingword.door43client.models.Category
import org.unfoldingword.door43client.models.ChunkMarker
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.Versification
import org.unfoldingword.resourcecontainer.ContainerTools
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer

internal object LegacyTools {

    private var LANG_NAMES_URL = "https://langnames.bibleineverylanguage.org/langnames.json"

    @Throws(Exception::class)
    fun injectGlobalCatalogs(library: Library, host: String?) {
        val resolvedHost = if (!host.isNullOrBlank()) host else "https://td.unfoldingword.org"
        library.addCatalog(Catalog("langnames", LANG_NAMES_URL, 0))
        // TRICKY: the trailing / is required on these urls
        library.addCatalog(Catalog("new-language-questions", "$resolvedHost/api/questionnaire/", 0))
        library.addCatalog(Catalog("temp-langnames", "$resolvedHost/api/templanguages/", 0))
        // TRICKY: this catalog should always be indexed after langnames and temp-langnames otherwise the linking will fail!
        library.addCatalog(Catalog("approved-temp-langnames", "$resolvedHost/api/templanguages/assignment/changed/", 0))
    }

    fun setLangNamesUrl(url: String) {
        LANG_NAMES_URL = url
    }

    internal data class DownloadedResource(val rJson: JSONObject)
    internal data class DownloadedLanguage(val lJson: JSONObject, val resources: List<DownloadedResource>)
    internal data class DownloadedProject(val pJson: JSONObject, val languages: List<DownloadedLanguage>)

    /**
     * Download all source catalog data.
     */
    @Throws(Exception::class)
    suspend fun downloadCatalogData(
        data: String,
        listener: OnProgressListener?
    ): List<DownloadedProject> {
        val projects = JSONArray(data)
        val result = mutableListOf<DownloadedProject>()
        for (i in 0 until projects.length()) {
            val pJson = projects.getJSONObject(i)
            if (listener?.onProgress(pJson.getString("slug"), projects.length(), i + 1) == false) break
            result.add(DownloadedProject(pJson, downloadLanguageEntries(pJson)))
        }
        return result
    }

    private suspend fun downloadLanguageEntries(pJson: JSONObject): List<DownloadedLanguage> {
        val languages = JSONArray(GetRequest(pJson.getString("lang_catalog")).read())
        val result = mutableListOf<DownloadedLanguage>()
        for (i in 0 until languages.length()) {
            val lJson = languages.getJSONObject(i)
            result.add(DownloadedLanguage(lJson, downloadResourceEntries(lJson)))
        }
        return result
    }

    private suspend fun downloadResourceEntries(lJson: JSONObject): List<DownloadedResource> {
        val resources = JSONArray(GetRequest(lJson.getString("res_catalog")).read())
        return (0 until resources.length()).map { DownloadedResource(resources.getJSONObject(it)) }
    }

    /**
     * Index all previously-downloaded catalog data.
     */
    @Throws(Exception::class)
    fun indexCatalogData(library: Library, projects: List<DownloadedProject>) {
        for (project in projects) {
            indexLanguagesForProject(library, project.pJson, project.languages)
            library.yieldSafely()
        }
    }

    private fun indexLanguagesForProject(
        library: Library,
        pJson: JSONObject,
        languages: List<DownloadedLanguage>
    ) {
        for (language in languages) {
            val langJson = language.lJson.getJSONObject("language")
            val sl = SourceLanguage(
                langJson.getString("slug"),
                langJson.getString("name"),
                langJson.getString("direction")
            )
            val languageId = library.addSourceLanguage(sl)
            // TODO: retrieve the correct versification name(s) from the source language
            library.addVersification(Versification("en-US", "American English"), languageId)
            indexResourcesForLanguage(library, pJson, languageId, language.lJson, language.resources)
            library.yieldSafely()
        }
    }

    private fun indexResourcesForLanguage(
        library: Library,
        pJson: JSONObject,
        languageId: Long,
        lJson: JSONObject,
        resources: List<DownloadedResource>
    ) {
        for (downloadedResource in resources) {
            val rJson = downloadedResource.rJson
            val translateMode = when (rJson.getString("slug").lowercase()) {
                "obs", "ulb" -> "all"
                else -> "gl"
            }

            val project = Project(
                pJson.getString("slug"),
                lJson.getJSONObject("project").getString("name"),
                pJson.getInt("sort")
            ).apply {
                description = lJson.getJSONObject("project").getString("desc")
                chunksUrl = rJson.getString("chunks")
            }

            val categories = mutableListOf<Category>()
            if (pJson.has("meta")) {
                val metaArray = pJson.getJSONArray("meta")
                val projMetaArray = lJson.getJSONObject("project").getJSONArray("meta")
                for (j in 0 until metaArray.length()) {
                    categories.add(Category(metaArray.getString(j), projMetaArray.getString(j)))
                }
            }

            val projectId = library.addProject(project, categories, languageId)

            rJson.getJSONObject("status").apply {
                put("translate_mode", translateMode)
                put("pub_date", getString("publish_date"))
            }
            rJson.put("type", "book")

            val resource = Resource.fromJSON(rJson).apply {
                _legacyData[API.LEGACY_WORDS_ASSIGNMENTS_URL] = rJson.getString("tw_cat")
                addFormat(Resource.Format(ResourceContainer.version, ContainerTools.typeToMime("book"), rJson.getInt("date_modified"), rJson.getString("source"), false))
            }
            library.addResource(resource, projectId)

            // coerce notes to resource
            if (rJson.has("notes") && rJson.getString("notes").isNotEmpty()) {
                rJson.getJSONObject("status").put("translate_mode", "gl")
                rJson.put("slug", "tn")
                rJson.put("name", "translationNotes")
                rJson.put("type", "help")
                rJson.getJSONObject("status").put("source_translations", listOf(mapOf(
                    "language_slug" to lJson.getJSONObject("language").getString("slug"),
                    "resource_slug" to "tn",
                    "version" to resource.version
                )))
                val tnResource = Resource.fromJSON(rJson).apply {
                    addFormat(Resource.Format(ResourceContainer.version, ContainerTools.typeToMime("help"), rJson.getInt("date_modified"), rJson.getString("notes"), false))
                }
                library.addResource(tnResource, projectId)
            }

            // coerce questions to resource
            if (rJson.has("checking_questions") && rJson.getString("checking_questions").isNotEmpty()) {
                rJson.getJSONObject("status").put("translate_mode", "gl")
                rJson.put("slug", "tq")
                rJson.put("name", "translationQuestions")
                rJson.put("type", "help")
                rJson.getJSONObject("status").put("source_translations", listOf(mapOf(
                    "language_slug" to lJson.getJSONObject("language").getString("slug"),
                    "resource_slug" to "tq",
                    "version" to resource.version
                )))
                val tqResource = Resource.fromJSON(rJson).apply {
                    addFormat(Resource.Format(ResourceContainer.version, ContainerTools.typeToMime("help"), rJson.getInt("date_modified"), rJson.getString("checking_questions"), false))
                }
                library.addResource(tqResource, projectId)
            }

            // add words project (insert/update so it will only be added once)
            // TRICKY: obs tw has not been unified with bible tw yet so we add it as a separate project.
            if (rJson.has("terms") && rJson.getString("terms").isNotEmpty()) {
                val isObs = pJson.getString("slug") == "obs"
                val wordsSlug = if (isObs) "bible-obs" else "bible"
                val wordsName = "translationWords" + if (isObs) " OBS" else ""
                val wordsProjectId = library.addProject(Project(wordsSlug, wordsName, 100), null, languageId)

                rJson.getJSONObject("status").put("translate_mode", "gl")
                rJson.put("slug", "tw")
                rJson.put("name", "translationWords")
                rJson.put("type", "dict")
                rJson.getJSONObject("status").put("source_translations", listOf(mapOf(
                    "language_slug" to lJson.getJSONObject("language").getString("slug"),
                    "resource_slug" to "tw",
                    "version" to resource.version
                )))
                val twResource = Resource.fromJSON(rJson).apply {
                    addFormat(Resource.Format(ResourceContainer.version, ContainerTools.typeToMime("dict"), rJson.getInt("date_modified"), rJson.getString("terms"), false))
                }
                library.addResource(twResource, wordsProjectId)
            }
            library.yieldSafely()
        }
    }

    /**
     * Phase 1 – Download all chunk markers over the network.
     * No DB access; safe to call outside a transaction.
     */
    @Throws(Exception::class)
    suspend fun downloadAllChunks(
        markers: Map<String, String>,
        listener: OnProgressListener?
    ): Map<String, List<ChunkMarker>> {
        val result = mutableMapOf<String, List<ChunkMarker>>()
        markers.entries.forEachIndexed { index, (slug, url) ->
            if (listener?.onProgress("chunk_markers", markers.size, index + 1) == false) return result
            val data = GetRequest(url).read()
            val chunks = JSONArray(data)
            result[slug] = (0 until chunks.length()).map { i ->
                val chunk = chunks.getJSONObject(i)
                ChunkMarker(chunk.getString("chp"), chunk.getString("firstvs"))
            }
        }
        return result
    }

    /**
     * Phase 2 – Insert all previously-downloaded chunk markers into the DB.
     * No network I/O and no suspension points; safe to call inside a transaction.
     */
    fun insertAllChunks(
        library: Library,
        chunks: Map<String, List<ChunkMarker>>,
        versificationRowId: Long
    ) {
        for ((slug, markerList) in chunks) {
            for (marker in markerList) {
                library.addChunkMarker(marker, slug, versificationRowId)
            }
            library.yieldSafely()
        }
    }

    /**
     * Pads a slug to 2 significant digits.
     * Examples:
     * '1'    -> '01'
     * '001'  -> '01'
     * '12'   -> '12'
     * '123'  -> '123'
     * '0123' -> '123'
     * Words are not padded:
     * 'a' -> 'a'
     * '0word' -> '0word'
     * And as a matter of consistency:
     * '0'  -> '00'
     * '00' -> '00'
     */
    @Throws(Exception::class)
    fun normalizeSlug(slug: String?): String {
        if (slug.isNullOrEmpty()) throw Exception("slug cannot be an empty string")
        if (!isInteger(slug)) return slug
        var result = slug.replace(Regex("^(0+)"), "").trim()
        while (result.length < 2) result = "0$result"
        return result
    }

    internal fun isInteger(s: String): Boolean {
        return try {
            s.toInt()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }

    /**
     * Converts a JSONObject to a Map.
     * http://stackoverflow.com/questions/21720759/convert-a-json-string-to-a-hashmap
     */
    @Throws(JSONException::class)
    fun jsonToMap(json: JSONObject): Map<String, Any> =
        if (json != JSONObject.NULL) toMap(json) else emptyMap()

    @Throws(JSONException::class)
    fun toMap(obj: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = when (val value = obj.get(key)) {
                is JSONArray -> toList(value)
                is JSONObject -> toMap(value)
                else -> value
            }
        }
        return map
    }

    @Throws(JSONException::class)
    fun toList(array: JSONArray): List<Any> {
        return (0 until array.length()).map { i ->
            when (val value = array.get(i)) {
                is JSONArray -> toList(value)
                is JSONObject -> toMap(value)
                else -> value
            }
        }
    }
}
