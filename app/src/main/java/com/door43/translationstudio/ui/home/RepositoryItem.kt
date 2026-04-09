package com.door43.translationstudio.ui.home

/**
 * Represents the loaded data of a repository that will be displayed in the list
 */
data class RepositoryItem(
    val languageName: String,
    val projectName: String,
    val targetTranslationSlug: String,
    val languageCode: String,
    val languageDirection: String,
    val repoName: String,
    val url: String,
    val isPrivate: Boolean,
    val unsupportedTag: String
) {
    val isSupported: Boolean
        get() = unsupportedTag.isEmpty()

    val projectNameAlt: String
        get() {
            var name = projectName
            if (!projectName.equals(targetTranslationSlug, ignoreCase = true)) {
                name += " ($targetTranslationSlug)" // if not same as project name, add project id
            }
            return name
        }
}