package org.unfoldingword.door43client.models

import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.resourcecontainer.Language

/**
 * Represents a language that a resource exists in (for the purpose of source content)
 */
class SourceLanguage(
    slug: String,
    name: String,
    direction: String
) : Language(slug, name, direction) {

    /**
     * Creates a new source language from a language
     * @param language the language
     */
    constructor(language: Language) : this(language.slug, language.name, language.direction)

    companion object {
        /**
         * Creates a source language from JSON
         * @param json the language JSON
         * @return the new source language
         * @throws JSONException
         */
        @Throws(JSONException::class)
        fun fromJSON(json: JSONObject): SourceLanguage {
            val l = Language.fromJSON(json)
            return SourceLanguage(l.slug, l.name, l.direction)
        }
    }
}