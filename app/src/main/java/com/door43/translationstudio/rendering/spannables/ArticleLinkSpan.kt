package com.door43.translationstudio.rendering.spannables

import org.unfoldingword.tools.logger.Logger
import java.util.regex.Pattern

open class ArticleLinkSpan protected constructor(
    private val title: String,
    sourceLanguageSlug: String,
    volume: String,
    val section: String,
    val slug: String
) : Span() {

    private val address: String = buildAddress(sourceLanguageSlug, volume, section, slug)

    init {
        init(this.title, address)
    }

    private fun buildAddress(sourceLanguageSlug: String, volume: String, manual: String, id: String): String {
        // example: en:ta:vol2:translate:figs_euphemism
        return "$sourceLanguageSlug:ta:$volume:$manual:$id"
    }

    /**
     * Changes the title of the passage link
     * @param title
     */
    fun setTitle(title: String) {
        humanReadable = title
    }

    companion object {
        // e.g. [[en:ta:vol1:translate:translate_unknown | How to Translate Unknowns]]
        // or [[:en:ta:vol1:translate:translate_unknown | How to Translate Unknowns]]
        val ADDRESS_PATTERN: Pattern = Pattern.compile("\\[\\[:?(([-a-zA-Z0-9]+:ta:[-_a-z0-9]+:[-_a-z0-9]+:[-_a-z0-9]+)( *\\|(((?!]]).)+))?)]]")

        // e.g <a href="/en/ta/vol1/translate/figs_intro" title="en:ta:vol1:translate:figs_intro">Figures of Speech</a>
        val LINK_PATTERN: Pattern = Pattern.compile("<a(((?!</a>).)*)href=\"/?([-a-zA-Z0-9]+/ta/[-_a-z0-9]+/[-_a-z0-9]+/[-_a-z0-9]+)/?\"(((?!</a>).)*)>\\s*(((?!</a>).)*)\\s*</a>")

        fun parse(address: String): ArticleLinkSpan {
            return parse("", address)
        }

        fun parse(title: String, address: String): ArticleLinkSpan {
            val parts = address.split(":")
            if (parts.size == 5) {
                // example: en:ta:vol2:translate:figs_euphemism
                val sourceLanguageSlug = parts[0]
                val taVolume = parts[2]
                val taManual = parts[3]
                val taId = parts[4].replace("_", "-")
                return ArticleLinkSpan(title, sourceLanguageSlug, taVolume, taManual, taId)
            } else {
                Logger.w(ArticleLinkSpan::class.java.name, "invalid translation academy link address $address")
            }
            return emptyArticleSpan
        }

        val emptyArticleSpan: ArticleLinkSpan
            get() = ArticleLinkSpan("", "", "", "", "")

    }
}
