package com.door43.translationstudio.ui.home

import org.json.JSONObject

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
    val notSupportedId: Int,
    val toJson: () -> JSONObject
) {
    val isSupported: Boolean
        get() = notSupportedId == 0
}