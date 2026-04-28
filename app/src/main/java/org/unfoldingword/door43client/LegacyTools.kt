package org.unfoldingword.door43client

import com.door43.translationstudio.network.GetRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bibletranslationtools.resourcecontainer.ContainerTools
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.unfoldingword.door43client.models.Catalog
import org.unfoldingword.door43client.models.Category
import org.unfoldingword.door43client.models.ChunkMarker
import org.unfoldingword.door43client.models.LanguageCatalog
import org.unfoldingword.door43client.models.ProjectCatalog
import org.unfoldingword.door43client.models.ResourceCatalog
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.Versification
import org.unfoldingword.door43client.models.toRcStatus

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

    /**
     * Download all source catalog data.
     */
    @Throws(Exception::class)
    suspend fun downloadCatalog(
        projects: List<ProjectCatalog>,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): List<ProjectCatalog> {
        return projects.mapIndexed { index, project ->
            onProgress(index / projects.size.toFloat(), project.slug)
            project.copy(languages = downloadLanguageCatalogs(project))
        }
    }

    private suspend fun downloadLanguageCatalogs(
        projectCatalog: ProjectCatalog
    ): List<LanguageCatalog> {
        val languageData = GetRequest(projectCatalog.languagesUrl).read()
        val languageCatalogs: List<LanguageCatalog> = Json.decodeFromString(languageData)
        return languageCatalogs.map { language ->
            language.copy(resources = downloadResourceCatalogs(language))
        }
    }

    private suspend fun downloadResourceCatalogs(
        language: LanguageCatalog
    ): List<ResourceCatalog> {
        val data = GetRequest(language.resourceUrl).read()
        val resources: List<ResourceCatalog> = Json.decodeFromString(data)
        return resources
    }

    /**
     * Index all previously-downloaded catalog data.
     */
    @Throws(Exception::class)
    fun indexCatalog(library: Library, projects: List<ProjectCatalog>) {
        for (project in projects) {
            indexLanguagesForProject(library, project, project.languages)
            library.yieldSafely()
        }
    }

    private fun indexLanguagesForProject(
        library: Library,
        project: ProjectCatalog,
        languages: List<LanguageCatalog>
    ) {
        for (language in languages) {
            val lang = language.language
            val sl = SourceLanguage(lang.slug, lang.name, lang.direction)
            val languageId = library.addSourceLanguage(sl)
            // TODO: retrieve the correct versification name(s) from the source language
            library.addVersification(
                Versification("en-US", "American English"),
                languageId
            )
            indexResourcesForLanguage(
                library,
                project,
                languageId,
                language,
                language.resources
            )
            library.yieldSafely()
        }
    }

    private fun indexResourcesForLanguage(
        library: Library,
        projectCatalog: ProjectCatalog,
        languageId: Long,
        languageCatalog: LanguageCatalog,
        resources: List<ResourceCatalog>
    ) {
        for (resourceCatalog in resources) {
            val translateMode = when (resourceCatalog.slug.lowercase()) {
                "obs", "ulb" -> "all"
                else -> "gl"
            }

            val project = Project(
                slug = projectCatalog.slug,
                name = languageCatalog.project.name,
                sort = projectCatalog.sort.toInt(),
                description = languageCatalog.project.desc,
                chunksUrl = resourceCatalog.chunksUrl,
            )

            val categories = mutableListOf<Category>()
            val meta = projectCatalog.meta
            val projectMeta = languageCatalog.project.meta
            for (j in 0 until meta.size) {
                categories.add(Category(meta[j], projectMeta[j]))
            }

            val projectId = library.addProject(project, categories, languageId)

            val resource = Resource(
                slug = resourceCatalog.slug,
                name = resourceCatalog.name,
                type = "book",
                status = resourceCatalog.status.toRcStatus(translateMode)
            ).apply {
                addLegacyData(API.LEGACY_WORDS_ASSIGNMENTS_URL, resourceCatalog.twCatUrl)
                val format = Resource.Format(
                    ResourceContainer.VERSION,
                    ContainerTools.typeToMime("book"),
                    resourceCatalog.modifiedAt.toInt(),
                    resourceCatalog.sourceUrl,
                    false
                )
                addFormat(format)
            }

            library.addResource(resource, projectId)

            // coerce notes to resource
            if (resourceCatalog.notesUrl.isNotEmpty()) {
                val sourceTranslations = listOf(
                    Resource.SourceTranslation(
                        languageSlug = languageCatalog.language.slug,
                        resourceSlug = "tn",
                        version = resource.status.version
                    )
                )
                val tnResource = Resource(
                    slug = "tn",
                    name = "translationNotes",
                    type = "help",
                    status = resourceCatalog.status
                        .toRcStatus("gl", sourceTranslations),
                ).apply {
                    val format = Resource.Format(
                        ResourceContainer.VERSION,
                        ContainerTools.typeToMime("help"),
                        resourceCatalog.modifiedAt.toInt(),
                        resourceCatalog.notesUrl,
                        false
                    )
                    addFormat(format)
                }
                library.addResource(tnResource, projectId)
            }

            // coerce questions to resource
            if (resourceCatalog.questionsUrl.isNotEmpty()) {
                val sourceTranslations = listOf(
                    Resource.SourceTranslation(
                        languageSlug = languageCatalog.language.slug,
                        resourceSlug = "tq",
                        version = resource.status.version
                    )
                )

                val tqResource = Resource(
                    slug = "tq",
                    name = "translationQuestions",
                    type = "help",
                    status = resourceCatalog.status
                        .toRcStatus("gl", sourceTranslations)
                ).apply {
                    val format = Resource.Format(
                        ResourceContainer.VERSION,
                        ContainerTools.typeToMime("help"),
                        resourceCatalog.modifiedAt.toInt(),
                        resourceCatalog.questionsUrl,
                        false
                    )
                    addFormat(format)
                }
                library.addResource(tqResource, projectId)
            }

            // add words project (insert/update so it will only be added once)
            // TRICKY: obs tw has not been unified with bible tw yet so we add it as a separate project.
            if (resourceCatalog.termsUrl.isNotEmpty()) {
                val sourceTranslations = listOf(
                    Resource.SourceTranslation(
                        languageSlug = languageCatalog.language.slug,
                        resourceSlug = "tw",
                        version = resource.status.version
                    )
                )

                val isObs = projectCatalog.slug == "obs"
                val wordsSlug = if (isObs) "bible-obs" else "bible"
                val wordsName = "translationWords" + if (isObs) " OBS" else ""
                val wordsProjectId = library.addProject(
                    Project(wordsSlug, wordsName, 100),
                    null,
                    languageId
                )

                val twResource = Resource(
                    slug = "tw",
                    name = "translationWords",
                    type = "dict",
                    status = resourceCatalog.status
                        .toRcStatus("gl", sourceTranslations)
                ).apply {
                    val format = Resource.Format(
                        ResourceContainer.VERSION,
                        ContainerTools.typeToMime("dict"),
                        resourceCatalog.modifiedAt.toInt(),
                        resourceCatalog.termsUrl,
                        false
                    )
                    addFormat(format)
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
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): Map<String, List<ChunkMarker>> {
        val result = mutableMapOf<String, List<ChunkMarker>>()
        markers.entries.forEachIndexed { index, (slug, url) ->
            onProgress((index + 1) / markers.size.toFloat(), "chunk_markers")
            val data = GetRequest(url).read()
            val chunks = Json.decodeFromString<List<MarkerChunk>>(data)
            result[slug] = chunks.map {
                ChunkMarker(it.chapter, it.firstVerse)
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
}

@Serializable
private data class MarkerChunk(
    @SerialName("chp")
    val chapter: String,
    @SerialName("firstvs")
    val firstVerse: String
)
