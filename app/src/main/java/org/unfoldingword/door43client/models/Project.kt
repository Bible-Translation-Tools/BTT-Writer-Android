package org.unfoldingword.door43client.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bibletranslationtools.resourcecontainer.Resource

@Serializable
internal data class ProjectCatalog(
    val slug: String,
    @SerialName("lang_catalog")
    val languagesUrl: String,
    @SerialName("date_modified")
    val modifiedAt: Int,
    val meta: List<String>,
    val sort: String,
    val languages: List<LanguageCatalog> = emptyList()
)

@Serializable
internal data class LanguageCatalog(
    val language: Language,
    val project: Project,
    @SerialName("res_catalog")
    val resourceUrl: String,
    val resources: List<ResourceCatalog> = emptyList()
)

@Serializable
internal data class Language(
    val slug: String,
    val name: String,
    val direction: String,
    @SerialName("date_modified")
    val modifiedAt: Int
)

@Serializable
internal data class Project(
    val name: String,
    val desc: String,
    val meta: List<String>,
    val sort: String
)

@Serializable
internal data class ProjectMeta(
    val slug: String,
    val icon: String,
    val sort: String,
    @SerialName("chunks_url")
    val chunksUrl: String,
    @SerialName("category_id")
    val categoryId: String
)

@Serializable
internal data class ResourceCatalog(
    val slug: String,
    val name: String,
    @SerialName("date_modified")
    val modifiedAt: String,
    @SerialName("source")
    val sourceUrl: String,
    @SerialName("chunks")
    val chunksUrl: String,
    @SerialName("usfm")
    val usfmUrl: String,
    @SerialName("notes")
    val notesUrl: String,
    @SerialName("checking_questions")
    val questionsUrl: String,
    @SerialName("terms")
    val termsUrl: String,
    @SerialName("tw_cat")
    val twCatUrl: String,
    val status: ProjectStatus
)

@Serializable
internal data class ProjectStatus(
    @SerialName("checking_entity")
    val checkingEntity: String?,
    @SerialName("checking_level")
    val checkingLevel: String,
    @SerialName("source_text")
    val sourceText: String?,
    @SerialName("source_text_version")
    val sourceTextVersion: String?,
    @SerialName("publish_date")
    val publishedAt: String,
    val version: String,
    val contributors: String,
    val comments: String?
)

internal fun ProjectStatus.toRcStatus(
    translateMode: String,
    sourceTranslations: List<Resource.SourceTranslation> = emptyList()
) = Resource.Status(
    translateMode = translateMode,
    checkingLevel = checkingLevel,
    version = version,
    license = "",
    pubDate = publishedAt,
    sourceTranslations = sourceTranslations
)