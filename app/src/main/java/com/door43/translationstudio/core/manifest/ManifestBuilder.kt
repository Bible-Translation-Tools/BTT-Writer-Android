package com.door43.translationstudio.core.manifest

import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.core.ResourceType
import org.unfoldingword.door43client.models.TargetLanguage

class ManifestBuilder {
    private var packageVersion = 0
    private var format = ""
    private var generator = Manifest.Generator("", "")
    private var targetLanguage = TargetLanguage("", "", "")
    private var project = Manifest.Project("", "")
    private var type = Manifest.Type("", "")
    private var resource = Manifest.Resource("", "")
    private var sourceTranslations = mutableListOf<Manifest.Source>()
    private var translators = mutableListOf<String>()
    private var finishedChunks = mutableListOf<String>()
    private var parentDraft = Manifest.Draft()

    fun packageVersion(version: Int) = apply { packageVersion = version }
    fun format(format: String) = apply { this.format = format }

    fun generator(name: String, build: String) = apply {
        generator = Manifest.Generator(name, build)
    }

    fun targetLanguage(slug: String, name: String, direction: String) = apply {
        targetLanguage = TargetLanguage(slug, name, direction)
    }

    fun targetLanguage(language: TargetLanguage) = apply {
        targetLanguage = language
    }

    fun project(slug: String, name: String) = apply {
        project = Manifest.Project(slug, name)
    }

    fun type(slug: String, name: String) = apply {
        type = Manifest.Type(slug, name)
    }

    fun type(resourceType: ResourceType) = apply {
        type(resourceType.id, resourceType.title)
    }

    fun resource(slug: String, name: String) = apply {
        resource = Manifest.Resource(slug, name)
    }

    fun addSourceTranslation(
        languageSlug: String,
        resourceSlug: String,
        checkingLevel: String,
        modifiedAt: String,
        version: String
    ) = apply {
        sourceTranslations.add(
            Manifest.Source(languageSlug, resourceSlug, checkingLevel, modifiedAt, version)
        )
    }

    fun addTranslator(name: String) = apply { translators.add(name) }

    fun addTranslator(speaker: NativeSpeaker) {
        addTranslator(speaker.name)
    }

    fun translators(vararg names: String) = apply { translators.addAll(names) }

    fun addFinishedChunk(chunk: String) = apply { finishedChunks.add(chunk) }

    fun finishedChunks(vararg chunks: String) = apply { finishedChunks.addAll(chunks) }

    fun parentDraft(block: DraftBuilder.() -> Unit) = apply {
        parentDraft = DraftBuilder().apply(block).build()
    }

    fun build(): Manifest = Manifest(
        packageVersion = packageVersion,
        format = format,
        generator = generator,
        targetLanguage = targetLanguage,
        project = project,
        type = type,
        resource = resource,
        sourceTranslations = sourceTranslations,
        translators = translators,
        finishedChunks = finishedChunks,
        parentDraft = parentDraft,
    )

    class DraftBuilder {
        var resourceSlug: String = ""
        var checkingEntity: String = ""
        var checkingLevel: String = ""
        var comments: String = ""
        var contributors: String = ""
        var publishDate: Int = 0
        var sourceText: String = ""
        var sourceTextVersion: String = ""
        var version: String = ""

        fun build() = Manifest.Draft(
            resourceSlug = resourceSlug,
            checkingEntity = checkingEntity,
            checkingLevel = checkingLevel,
            comments = comments,
            contributors = contributors,
            publishDate = publishDate,
            sourceText = sourceText,
            sourceTextVersion = sourceTextVersion,
            version = version,
        )
    }
}

fun buildManifest(block: ManifestBuilder.() -> Unit): Manifest =
    ManifestBuilder().apply(block).build()