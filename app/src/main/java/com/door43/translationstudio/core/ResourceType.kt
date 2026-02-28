package com.door43.translationstudio.core

/**
 * Represents different translation types
 */
enum class ResourceType(val id: String, val title: String) {
    TEXT("text", "Text"),
    TRANSLATION_NOTE("tn", "Notes"),
    TRANSLATION_QUESTION("tq", "Questions"),
    TRANSLATION_WORD("tw", "Words");

    override fun toString(): String {
        return id
    }

    companion object {
        /**
         * Returns a format by its name
         * @param name the id or name of the resource type
         * @return the matching ResourceType or null
         */
        fun get(name: String?): ResourceType? {
            if (name == null) return null
            val searchName = name.lowercase()
            return entries.find { it.id == searchName }
        }
    }
}