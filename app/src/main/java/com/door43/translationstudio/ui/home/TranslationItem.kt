package com.door43.translationstudio.ui.home

import com.door43.translationstudio.core.TargetTranslation
import org.bibletranslationtools.resourcecontainer.Resource

data class TranslationItem(
    val translation: TargetTranslation,
    val name: String,
    val progress: Float = 0f
) {
    val formattedProjectName: String
        get() = if (translation.resourceSlug != Resource.REGULAR_SLUG && translation.resourceSlug != "obs") {
            // display the resource type if not a regular resource e.g. this is for a gateway language
            name + " (" + translation.resourceSlug + ")"
        } else name
}