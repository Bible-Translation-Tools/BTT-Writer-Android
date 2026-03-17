package com.door43.translationstudio.rendering

import com.door43.translationstudio.ui.textadapters.SpannableAdapter
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.rendering.model.NodeAttributes
import com.door43.translationstudio.rendering.model.NodeStyle
import com.door43.translationstudio.ui.spannables.ArticleLinkSpan
import com.door43.translationstudio.ui.spannables.MarkdownLinkSpan
import com.door43.translationstudio.ui.spannables.MarkdownTitledLinkSpan
import com.door43.translationstudio.ui.spannables.PassageLinkSpan
import com.door43.translationstudio.ui.spannables.ShortReferenceSpan
import com.door43.translationstudio.ui.spannables.Span
import com.door43.translationstudio.ui.spannables.TranslationWordLinkSpan
import java.util.regex.Pattern

/**
 * HTML rendering engine for help content (translation notes, words, questions).
 *
 * Two rendering paths:
 *
 * 1. **Compose path** — [toAnnotatedHtml]: replaces wiki-style links with `<a>` tags
 *    using a custom `app://` scheme, returning valid HTML that can be passed to
 *    `AnnotatedString.fromHtml()`. The platform handles all HTML tags and entities.
 *    Use with [ComposeTextAdapter.convertHtml].
 *
 * 2. **Spannable path** — [renderToNodes] / [render]: single-pass tag+wiki parser
 *    that produces a List<RenderNode> for the legacy SpannableAdapter pipeline.
 */
class HtmlRenderer(
    private val preprocessCallback: OnPreprocessLink
) : RenderingEngine() {

    // ════════════════════════════════════════════════════════════════════
    //  Compose path: keep HTML intact, convert wiki-links to <a> tags
    // ════════════════════════════════════════════════════════════════════

    /**
     * Convert wiki-style links to HTML `<a>` tags with a custom `app://` scheme,
     * preserving the original HTML structure so that `AnnotatedString.fromHtml()`
     * can parse the result correctly.
     *
     * Link scheme: `app://TYPE/data`
     * - `app://ta/ADDRESS` — Translation Academy article
     * - `app://tw/ID` — Translation Word
     * - `app://passage/ADDRESS` — Passage cross-reference
     * - `app://md/ADDRESS` — Markdown / generic link
     * - `app://ref/REF` — Short chapter:verse reference
     *
     * Links rejected by the [preprocessCallback] are replaced with their plain title text.
     *
     * @return valid HTML string ready for `AnnotatedString.fromHtml()`
     */
    fun toAnnotatedHtml(input: String): String {
        val allTokens = mutableListOf<HtmlToken>()
        allTokens.addAll(findTaAddressHtmlTokens(input))
        allTokens.addAll(findPassageHtmlTokens(input))
        allTokens.addAll(findShortRefHtmlTokens(input))
        allTokens.addAll(findMarkdownHtmlTokens(input))
        allTokens.addAll(findTwHtmlTokens(input))

        allTokens.sortBy { it.start }
        val tokens = removeHtmlTokenOverlaps(allTokens)

        if (tokens.isEmpty()) return input

        val sb = StringBuilder()
        var lastEnd = 0
        for (token in tokens) {
            sb.append(input, lastEnd, token.start)
            sb.append(token.replacement)
            lastEnd = token.end
        }
        sb.append(input, lastEnd, input.length)
        return sb.toString()
    }

    /** A range in the source text to be replaced with an HTML snippet. */
    private data class HtmlToken(val start: Int, val end: Int, val replacement: String)

    private fun removeHtmlTokenOverlaps(sorted: List<HtmlToken>): List<HtmlToken> {
        val result = mutableListOf<HtmlToken>()
        var lastEnd = 0
        for (token in sorted) {
            if (token.start >= lastEnd) {
                result.add(token)
                lastEnd = token.end
            }
        }
        return result
    }

    private fun buildAnchor(scheme: String, data: String, title: String): String {
        val escaped = title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        return """<a href="app://$scheme/${data.encodeForHref()}">$escaped</a>"""
    }

    private fun String.encodeForHref(): String =
        replace("\"", "%22").replace(" ", "%20")

    private fun findTaAddressHtmlTokens(text: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val matcher = ArticleLinkSpan.ADDRESS_PATTERN.matcher(text)
        while (matcher.find()) {
            val rawAddress = matcher.group(2) ?: ""
            val titleFallback = rawAddress.substringAfterLast(':').takeIf { it.isNotEmpty() } ?: rawAddress
            val title = matcher.group(4) ?: titleFallback
            val span = ArticleLinkSpan.parse(title, rawAddress)
            if (span.machineReadable.isNotEmpty() && preprocessCallback.onPreprocess(span)) {
                tokens.add(HtmlToken(
                    matcher.start(), matcher.end(),
                    buildAnchor("ta", span.machineReadable, span.humanReadable)
                ))
            } else {
                tokens.add(HtmlToken(matcher.start(), matcher.end(), span.humanReadable))
            }
        }
        return tokens
    }

    private fun findPassageHtmlTokens(text: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val matcher = PassageLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val title = matcher.group(3) ?: ""
            val address = matcher.group(1) ?: ""
            val span = PassageLinkSpan(title, address)
            if (preprocessCallback.onPreprocess(span)) {
                tokens.add(HtmlToken(
                    matcher.start(), matcher.end(),
                    buildAnchor("passage", span.machineReadable, span.humanReadable)
                ))
            } else {
                tokens.add(HtmlToken(matcher.start(), matcher.end(), span.humanReadable))
            }
        }
        return tokens
    }

    private fun findShortRefHtmlTokens(text: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val matcher = ShortReferenceSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val ref = matcher.group(0) ?: ""
            val span = ShortReferenceSpan(ref)
            if (preprocessCallback.onPreprocess(span)) {
                tokens.add(HtmlToken(
                    matcher.start(), matcher.end(),
                    buildAnchor("ref", span.humanReadable, span.humanReadable)
                ))
            } else {
                tokens.add(HtmlToken(matcher.start(), matcher.end(), span.humanReadable))
            }
        }
        return tokens
    }

    private fun findMarkdownHtmlTokens(text: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val matcher = MarkdownTitledLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val title = matcher.group(1) ?: ""
            val address = matcher.group(3) ?: ""
            val span = MarkdownTitledLinkSpan(title, address)
            if (preprocessCallback.onPreprocess(span)) {
                tokens.add(HtmlToken(
                    matcher.start(), matcher.end(),
                    buildAnchor("md", span.machineReadable, span.humanReadable)
                ))
            } else {
                tokens.add(HtmlToken(matcher.start(), matcher.end(), span.humanReadable))
            }
        }
        return tokens
    }

    private fun findTwHtmlTokens(text: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val matcher = MarkdownLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            var address = matcher.group(1)
                ?.replace("^:".toRegex(), "")
                ?.trim()
                ?.lowercase() ?: ""
            val addressName = address.split("\\|".toRegex())
            address = addressName[0]
            val chunks = address.split(":")
            if (chunks.size > 2 && chunks[1] == "obe") {
                val id = chunks[chunks.size - 1]
                if (id.isNotEmpty()) {
                    val span = TranslationWordLinkSpan(id, id)
                    if (preprocessCallback.onPreprocess(span)) {
                        tokens.add(HtmlToken(
                            matcher.start(), matcher.end(),
                            buildAnchor("tw", id, span.humanReadable)
                        ))
                    } else {
                        tokens.add(HtmlToken(matcher.start(), matcher.end(), id))
                    }
                }
            } else {
                tokens.add(HtmlToken(
                    matcher.start(), matcher.end(),
                    matcher.group(0) ?: ""
                ))
            }
        }
        return tokens
    }

    // ════════════════════════════════════════════════════════════════════
    //  Spannable path: single-pass tag+wiki parser → List<RenderNode>
    // ════════════════════════════════════════════════════════════════════

    /**
     * Render HTML-formatted input into a platform-agnostic hierarchical List<RenderNode>.
     *
     * Single-pass approach:
     * 1. Split input into segments (text content vs HTML tags) using a tag regex.
     * 2. Walk segments linearly, tracking formatting state (bold, italic, link accumulation).
     * 3. Within text content segments, find wiki-style links using existing finders.
     * 4. Emit appropriate TextNode types for each segment.
     */
    override fun renderToNodes(input: String): List<RenderNode> {
        val nodes = mutableListOf<TextNode>()

        val tagMatcher = HTML_TAG_PATTERN.matcher(input)
        var lastEnd = 0

        var boldDepth = 0
        var italicDepth = 0
        var linkHref: String? = null
        var linkType: String? = null
        val linkTitle = StringBuilder()

        while (tagMatcher.find()) {
            val textBefore = input.substring(lastEnd, tagMatcher.start())
            if (textBefore.isNotEmpty()) {
                if (linkHref != null) {
                    linkTitle.append(textBefore)
                } else {
                    emitTextContent(textBefore, boldDepth, italicDepth, nodes)
                }
            }

            val isClosing = tagMatcher.group(1) == "/"
            val tagName = tagMatcher.group(2)!!.lowercase()
            val attrs = tagMatcher.group(3)?.trim() ?: ""

            when (tagName) {
                "br" -> if (linkHref == null) nodes.add(TextNode.LineBreak)

                "p" -> if (linkHref == null) {
                    if (!isClosing) nodes.add(TextNode.Paragraph(indented = false))
                }

                "h1", "h2", "h3", "h4", "h5", "h6" -> if (linkHref == null) {
                    if (!isClosing) {
                        nodes.add(TextNode.LineBreak)
                        boldDepth++
                    } else {
                        boldDepth = maxOf(0, boldDepth - 1)
                        nodes.add(TextNode.LineBreak)
                    }
                }

                "b", "strong" -> {
                    if (!isClosing) boldDepth++ else boldDepth = maxOf(0, boldDepth - 1)
                }

                "i", "em" -> {
                    if (!isClosing) italicDepth++ else italicDepth = maxOf(0, italicDepth - 1)
                }

                "ul", "ol" -> { /* structure implied by <li> */ }

                "li" -> if (linkHref == null && !isClosing) {
                    nodes.add(TextNode.LineBreak)
                    nodes.add(TextNode.Text("  \u2022 "))
                }

                "a" -> {
                    if (!isClosing) {
                        linkHref = extractAttr(attrs, "href") ?: ""
                        linkType = null
                        linkTitle.clear()
                    } else if (linkHref != null) {
                        val title = linkTitle.toString().trim()
                        nodes.add(TextNode.Link(classifyHtmlLink(linkHref!!, title)))
                        linkHref = null
                    }
                }

                "app-link" -> {
                    if (!isClosing) {
                        linkHref = extractAttr(attrs, "href") ?: ""
                        linkType = extractAttr(attrs, "type") ?: ""
                        linkTitle.clear()
                    } else if (linkHref != null) {
                        val title = linkTitle.toString().trim()
                        nodes.add(TextNode.Link(LinkData.AppLink(
                            href = linkHref!!, linkType = linkType ?: "", title = title
                        )))
                        linkHref = null
                        linkType = null
                    }
                }
            }

            lastEnd = tagMatcher.end()
        }

        val remaining = input.substring(lastEnd)
        if (remaining.isNotEmpty()) {
            if (linkHref != null) linkTitle.append(remaining)
            else emitTextContent(remaining, boldDepth, italicDepth, nodes)
        }

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

    // ── Text content helpers (Spannable path) ───────────────────────────

    private fun emitTextContent(
        text: String,
        boldDepth: Int,
        italicDepth: Int,
        out: MutableList<TextNode>
    ) {
        val decoded = decodeEntities(text)
        val resolved = findLinksInText(decoded)
        for (node in resolved) {
            if (node is TextNode.Text && node.content.isNotEmpty()) {
                when {
                    boldDepth > 0 ->
                        out.add(TextNode.Styled(node.content, NodeStyle.BOLD))
                    italicDepth > 0 ->
                        out.add(TextNode.Styled(node.content, NodeStyle.ITALIC))
                    else -> out.add(node)
                }
            } else {
                out.add(node)
            }
        }
    }

    private fun findLinksInText(text: String): List<TextNode> {
        val allTokens = mutableListOf<Token>()
        allTokens.addAll(findTranslationAcademyAddresses(text))
        allTokens.addAll(findPassageLinks(text))
        allTokens.addAll(findShortReferenceLinks(text))
        allTokens.addAll(findMarkdownLinks(text))
        allTokens.addAll(findTranslationWordLinks(text))

        allTokens.sortBy { it.start }
        val tokens = removeOverlaps(allTokens)

        val nodes = mutableListOf<TextNode>()
        var lastIndex = 0
        for (token in tokens) {
            val gap = text.substring(lastIndex, token.start)
            if (gap.isNotEmpty()) nodes.add(TextNode.Text(gap))
            nodes.addAll(token.nodes)
            lastIndex = token.end
        }
        val tail = text.substring(lastIndex)
        if (tail.isNotEmpty()) nodes.add(TextNode.Text(tail))
        return nodes
    }

    private fun classifyHtmlLink(href: String, title: String): LinkData {
        if (href.contains("/ta/")) {
            val address = href.replace("/", ":").removePrefix(":")
            val span = ArticleLinkSpan.parse(title, address)
            if (span.machineReadable.isNotEmpty()) {
                return LinkData.Article(
                    address = span.machineReadable,
                    title = if (title.isNotEmpty()) title else span.humanReadable
                )
            }
        }
        return LinkData.Markdown(address = href, title = title)
    }

    // ── Conversion helpers ──────────────────────────────────────────────

    private fun convertTextNodesToRenderNodes(textNodes: List<TextNode>): List<RenderNode> {
        return textNodes.map { node ->
            when (node) {
                is TextNode.Text -> RenderNode.Text(node.content)
                is TextNode.Styled -> RenderNode.StyledText(node.content, node.style)
                is TextNode.VerseMarker -> RenderNode.Verse(
                    startVerse = node.startVerse, endVerse = node.endVerse,
                    pinned = node.pinned, machineReadable = node.machineReadable
                )
                is TextNode.NoteMarker -> RenderNode.Note(
                    caller = node.caller, passage = node.passage,
                    notes = node.notes, noteStyle = node.noteStyle,
                    machineReadable = node.machineReadable
                )
                is TextNode.Paragraph -> RenderNode.Paragraph(indented = node.indented, children = emptyList())
                is TextNode.SectionHeading -> RenderNode.Section(text = node.text, isMajor = node.isMajor, children = emptyList())
                is TextNode.PoeticLine -> RenderNode.PoeticLine(indentLevel = node.indentLevel, rightAligned = node.rightAligned, children = emptyList())
                is TextNode.ChapterLabel -> RenderNode.ChapterLabel(node.text)
                is TextNode.Link -> RenderNode.Link(node.linkData)
                is TextNode.SearchHighlight -> RenderNode.Text(node.content, attributes = NodeAttributes(searchHighlighted = true))
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
                    startVerse = node.startVerse, endVerse = node.endVerse,
                    pinned = node.pinned, machineReadable = node.machineReadable
                ))
                is RenderNode.Note -> listOf(TextNode.NoteMarker(
                    caller = node.caller, passage = node.passage,
                    notes = node.notes, noteStyle = node.noteStyle,
                    machineReadable = node.machineReadable
                ))
                is RenderNode.Paragraph -> {
                    val r = mutableListOf<TextNode>(TextNode.Paragraph(indented = node.indented))
                    r.addAll(convertRenderNodesToTextNodes(node.children)); r
                }
                is RenderNode.Section -> {
                    val r = mutableListOf<TextNode>(TextNode.SectionHeading(text = node.text, isMajor = node.isMajor))
                    r.addAll(convertRenderNodesToTextNodes(node.children)); r
                }
                is RenderNode.PoeticLine -> {
                    val r = mutableListOf<TextNode>(TextNode.PoeticLine(content = "", indentLevel = node.indentLevel, rightAligned = node.rightAligned))
                    r.addAll(convertRenderNodesToTextNodes(node.children)); r
                }
                is RenderNode.ChapterLabel -> listOf(TextNode.ChapterLabel(node.text))
                is RenderNode.Link -> listOf(TextNode.Link(node.linkData))
                RenderNode.LineBreak -> listOf(TextNode.LineBreak)
                RenderNode.BlankLine -> listOf(TextNode.BlankLine)
            }
        }
    }

    // ── Token-based wiki-link finders (Spannable path) ──────────────────

    private data class Token(val start: Int, val end: Int, val nodes: List<TextNode>)

    private fun findTranslationAcademyAddresses(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = ArticleLinkSpan.ADDRESS_PATTERN.matcher(text)
        while (matcher.find()) {
            val rawAddress = matcher.group(2) ?: ""
            val titleFallback = rawAddress.substringAfterLast(':').takeIf { it.isNotEmpty() } ?: rawAddress
            val title = matcher.group(4) ?: titleFallback
            val span = ArticleLinkSpan.parse(title, rawAddress)
            if (span.machineReadable.isNotEmpty() && preprocessCallback.onPreprocess(span)) {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Link(LinkData.Article(address = span.machineReadable, title = span.humanReadable))
                )))
            } else {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(TextNode.Text(span.humanReadable))))
            }
        }
        return tokens
    }

    private fun findPassageLinks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = PassageLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val title = matcher.group(3) ?: ""
            val address = matcher.group(1) ?: ""
            val span = PassageLinkSpan(title, address)
            if (preprocessCallback.onPreprocess(span)) {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Link(LinkData.Passage(address = span.machineReadable, title = span.humanReadable))
                )))
            } else {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(TextNode.Text(span.humanReadable))))
            }
        }
        return tokens
    }

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
                tokens.add(Token(matcher.start(), matcher.end(), listOf(TextNode.Text(span.humanReadable))))
            }
        }
        return tokens
    }

    private fun findMarkdownLinks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = MarkdownTitledLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val title = matcher.group(1) ?: ""
            val address = matcher.group(3) ?: ""
            val span = MarkdownTitledLinkSpan(title, address)
            if (preprocessCallback.onPreprocess(span)) {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(
                    TextNode.Link(LinkData.Markdown(address = span.machineReadable, title = span.humanReadable))
                )))
            } else {
                tokens.add(Token(matcher.start(), matcher.end(), listOf(TextNode.Text(span.humanReadable))))
            }
        }
        return tokens
    }

    private fun findTranslationWordLinks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = MarkdownLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            var address = matcher.group(1)
                ?.replace("^:".toRegex(), "")?.trim()?.lowercase() ?: ""
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
                        tokens.add(Token(matcher.start(), matcher.end(), listOf(TextNode.Text(id))))
                    }
                }
            } else {
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
            if (token.start >= lastEnd) { result.add(token); lastEnd = token.end }
        }
        return result
    }

    // ── Utilities ───────────────────────────────────────────────────────

    private fun extractAttr(attrs: String, name: String): String? {
        val m = Pattern.compile("""$name\s*=\s*"([^"]*?)"""", Pattern.CASE_INSENSITIVE).matcher(attrs)
        return if (m.find()) m.group(1) else null
    }

    private fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        return ENTITY_PATTERN.replace(text) { match ->
            val named = match.groupValues[1]; val decimal = match.groupValues[2]; val hex = match.groupValues[3]
            when {
                named.isNotEmpty() -> NAMED_ENTITIES[named.lowercase()] ?: match.value
                decimal.isNotEmpty() -> decimal.toIntOrNull()?.toChar()?.toString() ?: match.value
                hex.isNotEmpty() -> hex.toIntOrNull(16)?.toChar()?.toString() ?: match.value
                else -> match.value
            }
        }
    }

    fun interface OnPreprocessLink {
        fun onPreprocess(span: Span): Boolean
    }

    companion object {
        private val HTML_TAG_PATTERN: Pattern = Pattern.compile(
            """<(/?)([a-zA-Z][a-zA-Z0-9-]*)(\s[^>]*)?>""", Pattern.CASE_INSENSITIVE
        )
        private val ENTITY_PATTERN = Regex("""&(?:([a-zA-Z]+)|#(\d+)|#x([0-9a-fA-F]+));""")
        private val NAMED_ENTITIES = mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
            "nbsp" to "\u00A0", "ndash" to "\u2013", "mdash" to "\u2014",
            "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C",
            "rdquo" to "\u201D", "hellip" to "\u2026",
        )

        /** Parse an `app://TYPE/DATA` URL back into [LinkData]. */
        fun parseLinkUrl(url: String): LinkData? {
            if (!url.startsWith("app://")) return null
            val path = url.removePrefix("app://")
            val slash = path.indexOf('/')
            if (slash < 0) return null
            val type = path.substring(0, slash)
            val data = path.substring(slash + 1)
                .replace("%22", "\"").replace("%20", " ")
            return when (type) {
                "ta" -> LinkData.Article(address = data, title = "")
                "tw" -> LinkData.TranslationWord(id = data)
                "passage" -> LinkData.Passage(address = data, title = "")
                "md" -> LinkData.Markdown(address = data, title = "")
                "ref" -> LinkData.ShortReference(ref = data)
                else -> null
            }
        }
    }
}
