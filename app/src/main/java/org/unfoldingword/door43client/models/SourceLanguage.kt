package org.unfoldingword.door43client.models

import org.bibletranslationtools.resourcecontainer.Language

/**
 * Represents a language that a resource exists in (for the purpose of source content)
 */
data class SourceLanguage(
    val slug: String,
    val name: String,
    val direction: String
) {

    /**
     * Creates a new source language from a language
     * @param language the language
     */
    constructor(language: Language) : this(language.slug, language.name, language.direction)
}

fun SourceLanguage.toLanguage() = Language(slug, name, direction)
