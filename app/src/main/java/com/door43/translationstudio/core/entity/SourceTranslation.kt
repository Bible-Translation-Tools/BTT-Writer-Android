package com.door43.translationstudio.core.entity

import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource
import org.unfoldingword.resourcecontainer.ResourceContainer

class SourceTranslation : Translation {

    var modifiedTimestamp: Int = -1
        private set

    constructor(language: Language, project: Project, resource: Resource) : super(language, project, resource)

    constructor(container: ResourceContainer) : super(container)

    constructor(translation: Translation, modifiedTimestamp: Int) : super(
        translation.language,
        translation.project,
        translation.resource
    ) {
        this.modifiedTimestamp = modifiedTimestamp
    }

    /**
     * Set the modified time for this source translation
     * @param timestamp the new modified timestamp
     */
    fun setModifiedTime(timestamp: Int) {
        modifiedTimestamp = timestamp
    }
}