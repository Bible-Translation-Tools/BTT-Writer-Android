package com.door43.translationstudio.rendering

import com.door43.translationstudio.ui.textadapters.SpannableAdapter
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.rendering.model.NodeAttributes
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
 * so that existing callers continue to work.
 */
class HtmlRenderer(
    private val preprocessCallback: OnPreprocessLink
) : RenderingEngine() {

    /**
     * Render HTML-formatted input into a platform-agnostic hierarchical List<RenderNode>.
     * This is the primary output of the new pipeline.
     */
    override fun renderToNodes(input: String): List<RenderNode> {
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

        return convertTextNodesToRenderNodes(nodes)
    }

    /**
     * Shim: delegates to renderToNodes + conversion + SpannableAdapter.convert so that
     * RenderingEngine.start() and any direct callers of render() continue to work.
     */
    override fun render(input: CharSequence): CharSequence {
        val renderNodes = renderToNodes(input.toString())
        val textNodes = convertRenderNodesToTextNodes(renderNodes)
        return SpannableAdapter.convert(textNodes)
    }

    private fun convertTextNodesToRenderNodes(textNodes: List<TextNode>): List<RenderNode> {
        return textNodes.map { node ->
            when (node) {
                is TextNode.Text -> RenderNode.Text(node.content)
                is TextNode.Styled -> RenderNode.StyledText(node.content, node.style)
                is TextNode.VerseMarker -> RenderNode.Verse(
                    startVerse = node.startVerse,
                    endVerse = node.endVerse,
                    pinned = node.pinned,
                    machineReadable = node.machineReadable
                )
                is TextNode.NoteMarker -> RenderNode.Note(
                    caller = node.caller,
                    passage = node.passage,
                    notes = node.notes,
                    noteStyle = node.noteStyle,
                    machineReadable = node.machineReadable
                )
                is TextNode.Paragraph -> RenderNode.Paragraph(
                    indented = node.indented,
                    children = emptyList()
                )
                is TextNode.SectionHeading -> RenderNode.Section(
                    text = node.text,
                    isMajor = node.isMajor,
                    children = emptyList()
                )
                is TextNode.PoeticLine -> RenderNode.PoeticLine(
                    indentLevel = node.indentLevel,
                    rightAligned = node.rightAligned,
                    children = emptyList()
                )
                is TextNode.ChapterLabel -> RenderNode.ChapterLabel(node.text)
                is TextNode.Link -> RenderNode.Link(node.linkData)
                is TextNode.SearchHighlight -> RenderNode.Text(node.content,
                    attributes = NodeAttributes(searchHighlighted = true)
                )
                TextNode.LineBreak -> RenderNode.LineBreak
                TextNode.BlankLine -> RenderNode.BlankLine
            }
        }
    }

    private fun convertRenderNodesToTextNodes(renderNodes: List<RenderNode>): List<TextNode> {
        return renderNodes.flatMap { node ->
            when (node) {
                is RenderNode.Text -> listOf(TextNode.Text(node.content))
                is RenderNode.StyledText -> listOf(TextNode.Styled(node.content, node.style))
                is RenderNode.Verse -> listOf(TextNode.VerseMarker(
                    startVerse = node.startVerse,
                    endVerse = node.endVerse,
                    pinned = node.pinned,
                    machineReadable = node.machineReadable
                ))
                is RenderNode.Note -> listOf(TextNode.NoteMarker(
                    caller = node.caller,
                    passage = node.passage,
                    notes = node.notes,
                    noteStyle = node.noteStyle,
                    machineReadable = node.machineReadable
                ))
                is RenderNode.Paragraph -> {
                    val result = mutableListOf<TextNode>()
                    result.add(TextNode.Paragraph(indented = node.indented))
                    result.addAll(convertRenderNodesToTextNodes(node.children))
                    result
                }
                is RenderNode.Section -> {
                    val result = mutableListOf<TextNode>()
                    result.add(TextNode.SectionHeading(text = node.text, isMajor = node.isMajor))
                    result.addAll(convertRenderNodesToTextNodes(node.children))
                    result
                }
                is RenderNode.PoeticLine -> {
                    val result = mutableListOf<TextNode>()
                    result.add(TextNode.PoeticLine(content = "", indentLevel = node.indentLevel, rightAligned = node.rightAligned))
                    result.addAll(convertRenderNodesToTextNodes(node.children))
                    result
                }
                is RenderNode.ChapterLabel -> listOf(TextNode.ChapterLabel(node.text))
                is RenderNode.Link -> listOf(TextNode.Link(node.linkData))
                RenderNode.LineBreak -> listOf(TextNode.LineBreak)
                RenderNode.BlankLine -> listOf(TextNode.BlankLine)
            }
        }
    }

    private data class Token(val start: Int, val end: Int, val nodes: List<TextNode>)

    /**
     * Finds Translation Academy address links.
     * Example: [[en:ta:vol1:translate:figs_intro | Figures of Speech]]
     */
    private fun findTranslationAcademyAddresses(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = ArticleLinkSpan.ADDRESS_PATTERN.matcher(text)
        while (matcher.find()) {
            val rawAddress = matcher.group(2) ?: ""
            // fall back to the address slug, not the raw [[...]] match text
            val titleFallback = rawAddress.substringAfterLast(':').takeIf { it.isNotEmpty() } ?: rawAddress
            val title = matcher.group(4) ?: titleFallback
            val span = ArticleLinkSpan.parse(title, rawAddress)
            // only emit a Link node when the address parsed successfully (machineReadable non-empty)
            if (span.machineReadable.isNotEmpty() && preprocessCallback.onPreprocess(span)) {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Link(LinkData.Article(
                        address = span.machineReadable,
                        title = span.humanReadable
                    ))
                )))
            } else {
                // render as plain text (failed parse or preprocessor rejection)
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(span.humanReadable)
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
            // compute rawAddress once so it can be used for both title fallback and address
            val rawAddress = matcher.group(3)?.replace("/", ":") ?: ""
            val titleFallback = rawAddress.substringAfterLast(':').takeIf { it.isNotEmpty() } ?: rawAddress
            val title = matcher.group(6) ?: titleFallback
            val span = ArticleLinkSpan.parse(title, rawAddress)
            // only emit a Link node when the address parsed successfully (machineReadable non-empty)
            if (span.machineReadable.isNotEmpty() && preprocessCallback.onPreprocess(span)) {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Link(LinkData.Article(
                        address = span.machineReadable,
                        title = span.humanReadable
                    ))
                )))
            } else {
                // render as plain text (failed parse or preprocessor rejection)
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(span.humanReadable)
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
                        address = span.machineReadable,
                        title = span.humanReadable
                    ))
                )))
            } else {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(span.humanReadable)
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
                    TextNode.Link(LinkData.ShortReference(ref = span.humanReadable))
                )))
            } else {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(span.humanReadable)
                )))
            }
        }
        return tokens
    }

    /**
     * Finds Markdown titled links.
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
                        address = span.machineReadable,
                        title = span.humanReadable
                    ))
                )))
            } else {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(span.humanReadable)
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
                        // preprocessor rejected — show word ID as plain text
                        tokens.add(Token(matcher.start(), matcher.end(), listOf(
                            TextNode.Text(id)
                        )))
                    }
                }
            } else {
                // not a TW link — emit raw match text so the gap-filling doesn't show raw markup
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Text(matcher.group(0) ?: "")
                )))
            }
        }
        return tokens
    }

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

    /**
     * Used to identify which links to render.
     */
    fun interface OnPreprocessLink {
        fun onPreprocess(span: Span): Boolean
    }
}
