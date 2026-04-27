package com.door43.translationstudio.core.entity

import org.bibletranslationtools.resourcecontainer.Language
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.unfoldingword.door43client.models.Translation

/**
 * SourceTranslation extends the Translation data class.
 * Note: This cannot be a 'data class' because its parent is already a data class.
 */
class SourceTranslation2(
    language: Language,
    project: Project,
    resource: Resource,
    modifiedTimestamp: Int = -1
) : Translation(language, project, resource) {

    var modifiedTimestamp: Int = modifiedTimestamp
        private set

    /**
     * Creates a source translation from a resource container
     */
    constructor(container: ResourceContainer) : this(
        container.language,
        container.project,
        container.resource
    )

    /**
     * Creates a source translation from an existing translation and a timestamp
     */
    constructor(translation: Translation, modifiedTimestamp: Int) : this(
        translation.language,
        translation.project,
        translation.resource,
        modifiedTimestamp
    )

    /**
     * Set the modified time for this source translation
     * @param timestamp the new modified timestamp
     */
    fun setModifiedTime(timestamp: Int) {
        modifiedTimestamp = timestamp
    }
}

data class SourceTranslation(
    val language: Language,
    val project: Project,
    val resource: Resource,
    val modifiedTimestamp: Int = -1
)

fun Translation.toSourceTranslation(modifiedTimestamp: Int): SourceTranslation =
    SourceTranslation(
        this.language,
        this.project,
        this.resource,
        modifiedTimestamp
    )