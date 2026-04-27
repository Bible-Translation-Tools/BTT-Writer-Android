package org.unfoldingword.door43client.models

import org.bibletranslationtools.resourcecontainer.ContainerTools
import org.bibletranslationtools.resourcecontainer.Language
import org.bibletranslationtools.resourcecontainer.Project
import org.bibletranslationtools.resourcecontainer.Resource
import org.bibletranslationtools.resourcecontainer.ResourceContainer

/**
 * A Translation is a special abstraction of a ResourceContainer.
 * Made 'open' so SourceTranslation can inherit from it.
 */
open class Translation(
    val language: Language,
    val project: Project,
    val resource: Resource
) {
    /**
     * The slug of the resource container represented by this translation
     */
    val resourceContainerSlug: String =
        ContainerTools.makeSlug(
            language.slug,
            project.slug,
            resource.slug
        )

    /**
     * Creates a translation from a resource container
     */
    constructor(container: ResourceContainer) : this(
        container.language,
        container.project,
        container.resource
    )

    // Manual implementation of data class features since we can't use 'data' keyword
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Translation) return false
        return language == other.language &&
                project == other.project &&
                resource == other.resource
    }

    override fun hashCode(): Int {
        var result = language.hashCode()
        result = 31 * result + project.hashCode()
        result = 31 * result + resource.hashCode()
        return result
    }
}