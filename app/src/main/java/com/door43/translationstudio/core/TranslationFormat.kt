package com.door43.translationstudio.core

/**
 * Represents different text formats
 */
enum class TranslationFormat(val title: String) {
    USFM("usfm"),
    USX("usx"),
    MARKDOWN("markdown"),
    DEFAULT("default"),
    UNKNOWN("txt");

    override fun toString(): String = title

    companion object {
        /**
         * Returns a format by its name
         * @param name the name of the format
         * @return the matching TranslationFormat or null
         */
        fun get(name: String): TranslationFormat {
            val searchName = name.lowercase()
            return entries.find { it.title == searchName } ?: UNKNOWN
        }

        /**
         * Parses a mimeType into a TranslationFormat
         */
        fun parse(mimeType: String): TranslationFormat {
            return when (mimeType) {
                "text/usfm" -> USFM
                "text/markdown" -> MARKDOWN
                "text/usx" -> USX
                else -> UNKNOWN
            }
        }
    }
}