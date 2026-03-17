package com.door43.translationstudio.rendering.model

import com.door43.translationstudio.rendering.spannables.ArticleLinkSpan
import com.door43.translationstudio.rendering.spannables.MarkdownTitledLinkSpan
import com.door43.translationstudio.rendering.spannables.PassageLinkSpan
import com.door43.translationstudio.rendering.spannables.ShortReferenceSpan
import com.door43.translationstudio.rendering.spannables.TranslationWordLinkSpan

/** Platform-agnostic description of a clickable link's target. */
sealed class LinkData {
    /** Translation Academy article. */
    data class Article(val address: String, val title: String) : LinkData() {
        fun toSpan(): ArticleLinkSpan = ArticleLinkSpan.parse(title, address)
    }

    /** A cross-reference passage (chapter:verse). */
    data class Passage(val address: String, val title: String) : LinkData() {
        fun toSpan(): PassageLinkSpan = PassageLinkSpan(title, address)
    }

    /** A translation word entry (wiki-style [[:en:obe:...]] or relative ../other/word.md). */
    data class TranslationWord(val id: String, val title: String = id) : LinkData() {
        fun toSpan(): TranslationWordLinkSpan = TranslationWordLinkSpan(title, id)
    }

    /** A plain markdown link. */
    data class Markdown(val address: String, val title: String) : LinkData() {
        fun toSpan(): MarkdownTitledLinkSpan = MarkdownTitledLinkSpan(title, address)
    }

    /** A resource container link, e.g. rc://en/tn/help/gen/08/20 */
    data class RcLink(val address: String, val title: String) : LinkData()

    /** A short chapter:verse reference in the current book. */
    data class ShortReference(val ref: String) : LinkData() {
        fun toSpan(): ShortReferenceSpan = ShortReferenceSpan(ref)
    }

    /**
     * Generic app-link for cases not covered by the typed subtypes.
     * @param linkType One of: "ta" (Translation Academy), "tw" (Translation Word),
     *                 "p" (Passage), "m" (Markdown), "sr" (Short Reference).
     */
    data class AppLink(val href: String, val linkType: String, val title: String) : LinkData()
}
