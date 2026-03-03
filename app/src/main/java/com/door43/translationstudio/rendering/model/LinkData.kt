package com.door43.translationstudio.rendering.model

/** Platform-agnostic description of a clickable link's target. */
sealed class LinkData {
    /** Translation Academy article. */
    data class Article(val address: String, val title: String) : LinkData()

    /** A cross-reference passage (chapter:verse). */
    data class Passage(val address: String, val title: String) : LinkData()

    /** A translation word entry. */
    data class TranslationWord(val id: String) : LinkData()

    /** A plain markdown link. */
    data class Markdown(val address: String, val title: String) : LinkData()

    /** A short chapter:verse reference in the current book. */
    data class ShortReference(val ref: String) : LinkData()

    /**
     * Generic app-link for cases not covered by the typed subtypes.
     * @param linkType One of: "ta" (Translation Academy), "tw" (Translation Word),
     *                 "p" (Passage), "m" (Markdown), "sr" (Short Reference).
     */
    data class AppLink(val href: String, val linkType: String, val title: String) : LinkData()
}
