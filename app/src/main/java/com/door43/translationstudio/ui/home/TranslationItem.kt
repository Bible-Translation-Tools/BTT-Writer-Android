package com.door43.translationstudio.ui.home

import com.door43.translationstudio.core.TargetTranslation
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.resourcecontainer.Resource

data class TranslationItem(
    val translation: TargetTranslation,
    val progress: Double,
    private val onGetProject: (TargetTranslation) -> Project
) {
    val project
        get() = onGetProject(translation)

    val formattedProjectName: String
        get() = if (translation.resourceSlug != Resource.REGULAR_SLUG && translation.resourceSlug != "obs") {
            // display the resource type if not a regular resource e.g. this is for a gateway language
            project.name + " (" + translation.resourceSlug + ")"
        } else {
            project.name
        }
}