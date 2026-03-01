package org.unfoldingword.door43client.models

/**
 * Represents a single entry in the list of project/categories
 * i.e. when you are choosing project to translate.
 */
data class CategoryEntry(
    /** the type of entry this is e.g. a project or category */
    val entryType: Type,
    /** the db id of the project/category */
    val id: Long,
    /** the slug of the project/category */
    val slug: String,
    /** the human-readable name of the project/category */
    val name: String,
    /** the slug of the source language in which the name is given (e.g. the name is translated in German, or French) */
    val sourceLanguageSlug: String,
    /** the db id of the parent category id (only used when the entry type is category */
    val parentCategoryId: Long
) {
    enum class Type {
        PROJECT,
        CATEGORY
    }
}
