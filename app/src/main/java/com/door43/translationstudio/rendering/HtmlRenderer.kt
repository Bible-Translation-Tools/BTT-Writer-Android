package com.door43.translationstudio.rendering

import android.content.Context  // only for backward-compat constructor
import com.door43.translationstudio.rendering.adapter.SpannableAdapter
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.ui.spannables.ArticleLinkSpan
import com.door43.translationstudio.ui.spannables.MarkdownLinkSpan
import com.door43.translationstudio.ui.spannables.MarkdownTitledLinkSpan
import com.door43.translationstudio.ui.spannables.PassageLinkSpan
import com.door43.translationstudio.ui.spannables.ShortReferenceSpan
import com.door43.translationstudio.ui.spannables.Span
import com.door43.translationstudio.ui.spannables.TranslationWordLinkSpan

/**
 * HTML rendering engine. Produces a List<TextNode> via renderToNodes().
 * The render(CharSequence) override is a shim that calls renderToNodes + SpannableAdapter.convert
 * so that existing callers continue to work until Task 6 updates the base class.
 *
 * No Android framework code lives in this file beyond the backward-compat constructor.
 * HtmlTagHandler has been moved to rendering/adapter/HtmlTagHandler.kt and is no longer
 * called from here — it is available for SpannableAdapter to use in the future.
 */
class HtmlRenderer(
    private val preprocessCallback: OnPreprocessLink,
    private val linkListener: Span.OnClickListener? = null
) : RenderingEngine() {

    /**
     * Backward-compat shim constructor. Context is accepted but ignored — kept for binary
     * compatibility until Task 6 removes the Context field from RenderingEngine.
     */
    constructor(
        context: Context,  // ignored — kept for binary compat until Task 6
        preprocessCallback: OnPreprocessLink,
        linkListener: Span.OnClickListener
    ) : this(preprocessCallback, linkListener) {
        this.context = context
    }

    // -------------------------------------------------------------------------
    // Public API — new pipeline
    // -------------------------------------------------------------------------

    /**
     * Render HTML-formatted input into a platform-agnostic List<TextNode>.
     * This is the primary output of the new pipeline.
     */
    fun renderToNodes(input: String): List<TextNode> {
        val allTokens = mutableListOf<Token>()
        allTokens.addAll(findTranslationAcademyAddresses(input))
        allTokens.addAll(findTranslationAcademyLinks(input))
        allTokens.addAll(findPassageLinks(input))
        allTokens.addAll(findShortReferenceLinks(input))
        allTokens.addAll(findMarkdownLinks(input))
        allTokens.addAll(findTranslationWordLinks(input))

        allTokens.sortBy { it.start }
        val tokens = removeOverlaps(allTokens)

        val nodes = mutableListOf<TextNode>()
        var lastIndex = 0
        for (token in tokens) {
            val gap = input.substring(lastIndex, token.start)
            if (gap.isNotEmpty()) nodes.add(TextNode.Text(gap))
            nodes.addAll(token.nodes)
            lastIndex = token.end
        }
        val tail = input.substring(lastIndex)
        if (tail.isNotEmpty()) nodes.add(TextNode.Text(tail))

        return nodes
    }

    // -------------------------------------------------------------------------
    // Shim override — keeps existing callers compiling (Task 6 removes this)
    // -------------------------------------------------------------------------

    /**
     * Shim: delegates to renderToNodes + SpannableAdapter.convert so that
     * RenderingEngine.start() and any direct callers of render() continue to work.
     */
    override fun render(input: CharSequence): CharSequence {
        val nodes = renderToNodes(input.toString())
        // TODO: wire linkListener via SpannableAdapter in Task 9.
        // SpannableAdapter.convert() currently exposes verseClickListener and noteClickListener
        // but has no parameter for generic link clicks (TextNode.Link). linkListener is stored on
        // HtmlRenderer but is silently discarded here until Task 9 adds that parameter.
        return SpannableAdapter.convert(nodes)
    }

    // -------------------------------------------------------------------------
    // Token data class
    // -------------------------------------------------------------------------

    private data class Token(val start: Int, val end: Int, val nodes: List<TextNode>)

    // -------------------------------------------------------------------------
    // find* helpers — each returns List<Token>
    // -------------------------------------------------------------------------

    /**
     * Finds Translation Academy address links.
     * Example: [[en:ta:vol1:translate:figs_intro | Figures of Speech]]
     */
    private fun findTranslationAcademyAddresses(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = ArticleLinkSpan.ADDRESS_PATTERN.matcher(text)
        while (matcher.find()) {
            val rawAddress = matcher.group(2) ?: ""
            // I1: fall back to the address slug, not the raw [[...]] match text
            val titleFallback = rawAddress.substringAfterLast(':').takeIf { it.isNotEmpty() } ?: rawAddress
            val title = matcher.group(4) ?: titleFallback
            val span = ArticleLinkSpan.parse(title, rawAddress)
            // C2: only emit a Link node when the address parsed successfully (machineReadable non-empty)
            if (span.machineReadable.isNotEmpty() && preprocessCallback.onPreprocess(span)) {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Link(LinkData.Article(
                        address = span.machineReadable.toString(),
                        title = span.humanReadable.toString()
                    ))
                )))
            } else {
                // render as plain text (failed parse or preprocessor rejection)
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(span.humanReadable.toString())
                )))
            }
        }
        return tokens
    }

    /**
     * Finds Translation Academy HTML anchor links.
     * Example: <a href="/en/ta/vol1/translate/figs_intro" title="...">Figures of Speech</a>
     */
    private fun findTranslationAcademyLinks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = ArticleLinkSpan.LINK_PATTERN.matcher(text)
        while (matcher.find()) {
            // I2: compute rawAddress once so it can be used for both title fallback and address
            val rawAddress = matcher.group(3)?.replace("/", ":") ?: ""
            val titleFallback = rawAddress.substringAfterLast(':').takeIf { it.isNotEmpty() } ?: rawAddress
            val title = matcher.group(6) ?: titleFallback
            val span = ArticleLinkSpan.parse(title, rawAddress)
            // C2: only emit a Link node when the address parsed successfully (machineReadable non-empty)
            if (span.machineReadable.isNotEmpty() && preprocessCallback.onPreprocess(span)) {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Link(LinkData.Article(
                        address = span.machineReadable.toString(),
                        title = span.humanReadable.toString()
                    ))
                )))
            } else {
                // render as plain text (failed parse or preprocessor rejection)
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(span.humanReadable.toString())
                )))
            }
        }
        return tokens
    }

    /**
     * Finds links to other passages in the project.
     * Example: [[:en:bible:notes:gen:01:03|1:5]]
     */
    private fun findPassageLinks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = PassageLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val title = matcher.group(3) ?: ""
            val address = matcher.group(1) ?: ""
            val span = PassageLinkSpan(title, address)
            if (preprocessCallback.onPreprocess(span)) {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Link(LinkData.Passage(
                        address = span.machineReadable.toString(),
                        title = span.humanReadable.toString()
                    ))
                )))
            } else {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(span.humanReadable.toString())
                )))
            }
        }
        return tokens
    }

    /**
     * Finds short references (chapter:verse without a book label).
     * Example: 1:1 means chapter 1 verse 1 of the current book.
     */
    private fun findShortReferenceLinks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = ShortReferenceSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val ref = matcher.group(0) ?: ""
            val span = ShortReferenceSpan(ref)
            if (preprocessCallback.onPreprocess(span)) {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Link(LinkData.ShortReference(ref = span.humanReadable.toString()))
                )))
            } else {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(span.humanReadable.toString())
                )))
            }
        }
        return tokens
    }

    /**
     * Finds markdown titled links.
     * Example: [My Title](http://example.com)
     */
    private fun findMarkdownLinks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = MarkdownTitledLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val title = matcher.group(1) ?: ""
            val address = matcher.group(3) ?: ""
            val span = MarkdownTitledLinkSpan(title, address)
            if (preprocessCallback.onPreprocess(span)) {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Link(LinkData.Markdown(
                        address = span.machineReadable.toString(),
                        title = span.humanReadable.toString()
                    ))
                )))
            } else {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(span.humanReadable.toString())
                )))
            }
        }
        return tokens
    }

    /**
     * Finds Translation Word links (double-bracket links with "obe" structure).
     * Example: [[en:obe:other:word]]
     */
    private fun findTranslationWordLinks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = MarkdownLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            var address = matcher.group(1)
                ?.replace("^:".toRegex(), "")
                ?.trim()
                ?.lowercase() ?: ""

            // cut off title e.g. en:obe:other:stuff|title
            val addressName = address.split("\\|".toRegex())
            address = addressName[0]

            val chunks = address.split(":")
            if (chunks.size > 2 && chunks[1] == "obe") {
                val id = chunks[chunks.size - 1]
                if (id.isNotEmpty()) {
                    val span = TranslationWordLinkSpan(id, id)
                    if (preprocessCallback.onPreprocess(span)) {
                        tokens.add(Token(matcher.start(), matcher.end(), listOf(
                            TextNode.Link(LinkData.TranslationWord(id = id))
                        )))
                    } else {
                        // I4: preprocessor rejected — show word ID as plain text
                        tokens.add(Token(matcher.start(), matcher.end(), listOf(
                            TextNode.Text(id)
                        )))
                    }
                }
            } else {
                // C3: not a TW link — emit raw match text so the gap-filling doesn't show raw markup
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(matcher.group(0) ?: "")
                )))
            }
        }
        return tokens
    }

    // -------------------------------------------------------------------------
    // Overlap removal
    // -------------------------------------------------------------------------

    private fun removeOverlaps(sorted: List<Token>): List<Token> {
        val result = mutableListOf<Token>()
        var lastEnd = 0
        for (token in sorted) {
            if (token.start >= lastEnd) {
                result.add(token)
                lastEnd = token.end
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Public interfaces
    // -------------------------------------------------------------------------

    /**
     * Used to identify which links to render.
     */
    fun interface OnPreprocessLink {
        fun onPreprocess(span: Span): Boolean
    }
}
