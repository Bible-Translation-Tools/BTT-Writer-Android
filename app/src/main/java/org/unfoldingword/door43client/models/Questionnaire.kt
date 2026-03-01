package org.unfoldingword.door43client.models

/**
 * Represents a questionnaire that can be completed in the app
 */
data class Questionnaire(
    /** the language code */
    val languageSlug: String,
    /** the name of the language in which this questionnaire is presented */
    val languageName: String,
    /** the written direction of the language */
    val languageDirection: String,
    /** the translation database id (server side) */
    val tdId: Long,
    /** a map of question ids that represent certain language data. e.g. language name, region etc. */
    val dataFields: Map<String, Long>
)
