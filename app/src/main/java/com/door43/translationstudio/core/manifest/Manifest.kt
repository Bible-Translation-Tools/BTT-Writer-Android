package com.door43.translationstudio.core.manifest

import com.door43.translationstudio.core.ResourceType
import com.door43.translationstudio.core.entity.SourceTranslation
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.unfoldingword.door43client.models.TargetLanguage

@Serializable
data class Manifest(
    @SerialName("package_version")
    val packageVersion: Int,
    val format: String,
    val generator: Generator,
    @SerialName("target_language")
    val targetLanguage: TargetLanguage,
    val project: Project,
    val type: Type,
    val resource: Resource,
    @SerialName("source_translations")
    val sourceTranslations: List<Source> = emptyList(),
    val translators: List<String>,
    @SerialName("finished_chunks")
    val finishedChunks: List<String> = emptyList(),
    @SerialName("parent_draft")
    @Serializable(with = DraftSerializer::class)
    val parentDraft: Draft? = null
) {
    companion object {
        const val MANIFEST_JSON = "manifest.json"
    }

    @Serializable
    data class Generator(
        val name: String,
        @Contextual
        val build: String
    )

    @Serializable
    data class Project(
        @SerialName("id")
        val slug: String,
        val name: String
    )

    @Serializable
    data class Type(
        @SerialName("id")
        val slug: String,
        val name: String
    )

    @Serializable
    data class Resource(
        @SerialName("id")
        val slug: String,
        val name: String
    )

    @Serializable
    data class Source(
        @SerialName("language_id")
        val languageSlug: String,
        @SerialName("resource_id")
        val resourceSlug: String,
        @SerialName("checking_level")
        val checkingLevel: String,
        @SerialName("date_modified")
        @Contextual
        val modifiedAt: String,
        val version: String
    ) {
        override fun equals(other: Any?): Boolean {
            return when (other) {
                is Source -> languageSlug == other.languageSlug && resourceSlug == other.resourceSlug
                else -> false
            }
        }

        override fun hashCode(): Int {
            return 31 * languageSlug.hashCode() + resourceSlug.hashCode()
        }
    }

    @Serializable
    data class Draft(
        @SerialName("resource_id")
        val resourceSlug: String = "",
        @SerialName("checking_entity")
        val checkingEntity: String = "",
        @SerialName("checking_level")
        val checkingLevel: String = "",
        val comments: String = "",
        val contributors: String = "",
        @SerialName("publish_date")
        val publishDate: Int = 0,
        @SerialName("source_text")
        val sourceText: String = "",
        @SerialName("source_text_version")
        val sourceTextVersion: String = "",
        val version: String = ""
    )
}

fun ResourceType.toType(): Manifest.Type =
    Manifest.Type(this.id, this.title)

fun SourceTranslation.toSource(): Manifest.Source =
    Manifest.Source(
        languageSlug = language.slug,
        resourceSlug = resource.slug,
        checkingLevel = resource.status.checkingLevel,
        modifiedAt = modifiedTimestamp.toString(),
        version = resource.status.version
    )
