package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.rendering.spannables.USXChar
import com.door43.translationstudio.rendering.spannables.USXNoteSpan
import com.door43.translationstudio.rendering.spannables.USXVerseSpan
import com.door43.translationstudio.ui.textadapters.SpannableAdapter
import java.util.regex.Pattern

/**
 * USX rendering engine. Produces a List<TextNode> via renderToNodes().
 * The render(CharSequence) override is a shim that calls renderToNodes + SpannableAdapter.convert
 * so that existing callers continue to work.
 *
 * No Android framework code lives in this file.
 */
class USXRenderer(
    private val verseDisplay: VerseDisplay = VerseDisplay.NUMBER
) : ClickableRenderingEngine() {

    private var renderLineBreaks = false
    private var renderParagraphs = true
    private var renderVerses = verseDisplay != VerseDisplay.RAW
    private var search: String? = null
    private var highlightColor = 0
    private var expectedVerseRange = IntArray(0)
    private var suppressLeadingMajorSectionHeadings = false
    private var addedMissingVerse = false

    override fun setVersesEnabled(enable: Boolean) {
        renderVerses = enable
    }

    override fun setParagraphsEnabled(enable: Boolean) {
        renderParagraphs = enable
    }

    override fun setSearchString(searchString: CharSequence, highlightColor: Int) {
        this.highlightColor = highlightColor
        search = if (searchString.isNotEmpty()) searchString.toString().lowercase() else null
    }

    override fun setPopulateVerseMarkers(verseRange: IntArray) {
        expectedVerseRange = verseRange
    }

    override fun setSuppressLeadingMajorSectionHeadings(suppressLeadingMajorSectionHeadings: Boolean) {
        this.suppressLeadingMajorSectionHeadings = suppressLeadingMajorSectionHeadings
    }

    override val isAddedMissingVerse: Boolean
        get() = addedMissingVerse

    /**
     * Render USX input into a platform-agnostic hierarchical List<RenderNode>.
     */
    override fun renderToNodes(input: String): List<RenderNode> {
        addedMissingVerse = false
        if (isStopped()) return emptyList()

        // string pre-processing (pure String ops, no spans)
        var text = trimWhitespace(input)
        if (isStopped()) return emptyList()
        text = removeLineBreaks(text)
        if (isStopped()) return emptyList()

        // collect all token matches simultaneously
        // NOTE: Selah (and other small tokens) are collected first so they don't get
        // shadowed by larger container tokens during overlap removal
        val allTokens = mutableListOf<Token>()
        allTokens.addAll(findVerses(text))
        allTokens.addAll(findNotes(text))
        allTokens.addAll(findSelah(text))
        allTokens.addAll(findMajorSectionHeadings(text))
        allTokens.addAll(findSectionHeadings(text))
        allTokens.addAll(findParagraphBreaks(text))
        allTokens.addAll(findBlankLines(text))
        allTokens.addAll(findPoeticLines(text))
        allTokens.addAll(findRightAlignedPoeticLines(text))
        allTokens.addAll(findChapterLabels(text))
        if (isStopped()) return emptyList()

        // sort by position, remove overlapping tokens
        allTokens.sortBy { it.start }
        val tokens = removeOverlaps(allTokens)

        // assemble node list, filling gaps with Text nodes
        val nodes = mutableListOf<TextNode>()
        var lastIndex = 0
        for (token in tokens) {
            if (token.start > lastIndex) {
                val gap = text.substring(lastIndex, token.start)
                val cleaned = stripRemainingMarkers(gap)
                if (cleaned.isNotBlank()) nodes.add(TextNode.Text(cleaned))
            }
            nodes.addAll(token.nodes)
            lastIndex = token.end
        }
        if (lastIndex < text.length) {
            val tail = text.substring(lastIndex)
            val cleaned = stripRemainingMarkers(tail)
            if (cleaned.isNotBlank()) nodes.add(TextNode.Text(cleaned))
        }

        // insert implicit poetry line markers before bare verse markers in poetry context
        addImplicitPoeticLineMarkers(nodes)

        // search highlights (post-process Text nodes)
        if (!isStopped()) applySearchHighlights(nodes)

        // insert missing expected verses
        insertMissingVerses(nodes)

        return convertTextNodesToRenderNodes(nodes)
    }

    /**
     * Shim: delegates to renderToNodes + conversion to TextNode + SpannableAdapter.convert so that
     * RenderingEngine.start() and any direct callers of render() continue to work.
     * TODO: Update SpannableAdapter to work directly with RenderNode
     */
    override fun render(input: CharSequence): CharSequence {
        val renderNodes = renderToNodes(input.toString())
        val textNodes = convertRenderNodesToTextNodes(renderNodes)
        return SpannableAdapter.convert(textNodes, searchHighlightColor = highlightColor)
    }

    /**
     * Shim: DefaultRenderer calls renderNote() directly on a USXRenderer instance.
     * We delegate to the full render() pipeline so the output is consistent.
     */
    fun renderNote(input: CharSequence): CharSequence {
        return render(input)
    }

    /**
     * Shim: DefaultRenderer calls renderHighlightSearch() directly.
     * We delegate to the full render() pipeline.
     */
    fun renderHighlightSearch(input: CharSequence): CharSequence {
        return render(input)
    }

    override fun getLeadingMajorSectionHeading(input: CharSequence): String {
        val matcher = paraPattern("ms").matcher(input.toString())
        return if (matcher.find() && matcher.start() == 0) matcher.group(1) ?: "" else ""
    }

    private data class Token(val start: Int, val end: Int, val nodes: List<TextNode>)

    private fun findMajorSectionHeadings(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        // Match only opening <para style="ms"> tags, extracting content after >
        val pattern = Pattern.compile("<para\\s+style=\"ms\"\\s*>\\s*([^<]*?)\\s*(?=<|$)")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            if (suppressLeadingMajorSectionHeadings && matcher.start() == 0) continue
            val content = matcher.group(1)?.trim() ?: continue
            tokens.add(
                Token(
                    matcher.start(), matcher.start() + matcher.group().length,
                    listOf(TextNode.SectionHeading(content, isMajor = true), TextNode.LineBreak)
                )
            )
        }
        return tokens
    }

    private fun findSectionHeadings(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        // Match only opening <para style="s"> tags, extracting content after >
        val pattern = Pattern.compile("<para\\s+style=\"s\"\\s*>\\s*([^<]*?)\\s*(?=<|$)")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: continue
            tokens.add(
                Token(
                    matcher.start(), matcher.start() + matcher.group().length,
                    listOf(TextNode.SectionHeading(content, isMajor = false), TextNode.LineBreak)
                )
            )
        }
        return tokens
    }

    private fun findParagraphBreaks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        // Match only opening <para> tags, not the entire block
        // This allows verses/notes inside paragraphs to be processed separately
        val matcher = beginParagraphPattern.matcher(text)
        while (matcher.find()) {
            val tagText = matcher.group() ?: continue
            // Create tokens for paragraph-type styles: "p" and "m"
            // These act as paragraph breaks (blank line) and reset poetry context.
            // No indentation is added — verse markers handle their own positioning.
            if (tagText.contains("style=\"p\"") || tagText.contains("style=\"m\"")) {
                tokens.add(Token(
                    matcher.start(), matcher.end(),
                    listOf(TextNode.Paragraph(indented = false), TextNode.LineBreak)
                ))
            }
        }
        return tokens
    }

    private fun findBlankLines(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = paraShortPattern("b").matcher(text)
        while (matcher.find()) {
            tokens.add(Token(matcher.start(), matcher.end(), listOf(TextNode.BlankLine)))
        }
        return tokens
    }

    private fun findPoeticLines(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        // Match only opening <para style="q\d+"> tags, not entire blocks
        // This allows verses inside poetic lines to be processed separately
        val pattern = Pattern.compile("<para\\s+style=\"q(\\d+)\"\\s*>")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val level = matcher.group(1)?.toIntOrNull() ?: 1
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(TextNode.PoeticLine("", indentLevel = level, rightAligned = false))
                )
            )
        }
        return tokens
    }

    private fun findRightAlignedPoeticLines(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        // Match only opening <para style="qr"> tags, not entire blocks
        val pattern = Pattern.compile("<para\\s+style=\"qr\"\\s*>")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(
                        TextNode.LineBreak,
                        TextNode.PoeticLine("", indentLevel = 0, rightAligned = true)
                    )
                )
            )
        }
        return tokens
    }

    private fun findChapterLabels(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        // Match only opening <para style="cl"> tags, extracting content after >
        val pattern = Pattern.compile("<para\\s+style=\"cl\"\\s*>\\s*([^<]*?)\\s*(?=<|$)")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: ""
            tokens.add(Token(matcher.start(), matcher.start() + matcher.group()!!.length, listOf(TextNode.ChapterLabel(content))))
        }
        return tokens
    }

    private fun findVerses(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = Pattern.compile(USXVerseSpan.PATTERN).matcher(text)
        val foundVerses = mutableListOf<Int>()
        while (matcher.find()) {
            if (!renderVerses) {
                // RAW mode: claim the range as plain text so stripRemainingMarkers won't eat it
                tokens.add(
                    Token(
                        matcher.start(), matcher.end(),
                        listOf(TextNode.Text(text.substring(matcher.start(), matcher.end())))
                    )
                )
                continue
            }
            val verseStr = matcher.group(1) ?: continue
            val parts = verseStr.split("-")
            val startVerse = parts[0].toIntOrNull() ?: continue
            val endVerse = if (parts.size == 2) parts[1].toIntOrNull() ?: 0 else 0

            // Deduplication
            val versesToAdd = if (endVerse > 0) (startVerse..endVerse).toList() else listOf(startVerse)
            val alreadyRendered = versesToAdd.any { foundVerses.contains(it) }
            if (alreadyRendered) continue
            foundVerses.addAll(versesToAdd)

            // Range filtering
            if (expectedVerseRange.isNotEmpty()) {
                val minV = expectedVerseRange[0]
                val maxV = if (expectedVerseRange.size > 1) expectedVerseRange[1] else minV
                val effectiveEnd = if (endVerse > 0) endVerse else startVerse
                if (startVerse < minV || effectiveEnd > maxV) continue
            }

            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(TextNode.VerseMarker(startVerse, endVerse, verseDisplay == VerseDisplay.PIN, text.substring(matcher.start(), matcher.end())))
                )
            )
        }
        return tokens
    }

    private fun findNotes(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = Pattern.compile(USXNoteSpan.PATTERN).matcher(text)
        while (matcher.find()) {
            val noteText = matcher.group() ?: continue
            val note = try {
                USXNoteSpan.parseNote(noteText)
            } catch (e: Exception) {
                null
            }
            if (note != null) {
                val style = if (note.style == "f") NoteStyle.FOOTNOTE else NoteStyle.CROSS_REFERENCE
                val highlighted = search != null && noteText.lowercase().contains(search!!)
                tokens.add(
                    Token(
                        matcher.start(), matcher.end(),
                        listOf(
                            TextNode.NoteMarker(
                                caller = note.caller,
                                passage = note.passage.toString(),
                                notes = note.notes.toString(),
                                noteStyle = style,
                                highlighted = highlighted,
                                machineReadable = noteText,
                                start = matcher.start(),
                                end = matcher.end()
                            )
                        )
                    )
                )
            }
            // If note parsing fails, the text will fall through as gap text and be cleaned up
        }
        return tokens
    }

    private fun findSelah(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = USXChar.getPattern(USXChar.STYLE_SELAH).matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1) ?: continue
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(
                        TextNode.LineBreak,
                        TextNode.PoeticLine(content.trim(), indentLevel = 0, rightAligned = true)
                    )
                )
            )
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
            // Overlapping token (e.g. char tags inside a note span) is skipped
        }
        return result
    }

    /**
     * Strip any remaining USX para/char markers from gap text that wasn't
     * claimed by any token (broken or unknown markers).
     */
    private fun stripRemainingMarkers(text: String): String {
        var out = text
        // Remove open/close para tags
        out = out.replace(beginParagraphPattern.toRegex(), "")
        out = out.replace(endParagraphPattern.toRegex(), "")
        // Remove verse tags (both self-closing and open)
        out = out.replace(Regex("""<verse[^>]*>"""), "")
        out = out.replace(Regex("""</verse>"""), "")
        // Remove note tags (both self-closing and open)
        out = out.replace(Regex("""<note[^>]*>"""), "")
        out = out.replace(Regex("""</note>"""), "")
        // Remove any other unparsed tags
        out = out.replace(Regex("""<[^>]*>"""), "")
        // Extract text content from char tags, discard the tags themselves
        val charPattern = Pattern.compile(USXChar.PATTERN)
        val charMatcher = charPattern.matcher(out)
        val sb = StringBuilder()
        var last = 0
        while (charMatcher.find()) {
            sb.append(out.substring(last, charMatcher.start()))
            sb.append(charMatcher.group(USXChar.CHAR_TEXT_GROUP) ?: "")
            last = charMatcher.end()
        }
        sb.append(out.substring(last))
        return sb.toString()
    }

    private fun applySearchHighlights(nodes: MutableList<TextNode>) {
        val term = search ?: return
        val result = mutableListOf<TextNode>()
        for (node in nodes) {
            if (isStopped()) return
            if (node is TextNode.Text) {
                val lower = node.content.lowercase()
                var last = 0
                while (true) {
                    val pos = lower.indexOf(term, last)
                    if (pos < 0) break
                    if (pos > last) result.add(TextNode.Text(node.content.substring(last, pos)))
                    result.add(TextNode.SearchHighlight(node.content.substring(pos, pos + term.length)))
                    last = pos + term.length
                }
                if (last < node.content.length) result.add(TextNode.Text(node.content.substring(last)))
            } else {
                result.add(node)
            }
        }
        nodes.clear()
        nodes.addAll(result)
    }

    /**
     * In USX, verse markers can appear between poetry `<para style="q">` blocks without being
     * wrapped in their own `<para style="q">` tag. These "bare" verses should still render as
     * q1-level poetic lines (with a line break before them). This function detects such cases
     * and inserts implicit PoeticLine markers before the bare verse markers.
     *
     * A verse is considered to be in a poetry context if there is a PoeticLine node either
     * before it (look-back) or after it (look-ahead), without crossing a structural boundary
     * (Paragraph, BlankLine, or SectionHeading).
     */
    private fun addImplicitPoeticLineMarkers(nodes: MutableList<TextNode>) {
        var i = 0
        while (i < nodes.size) {
            if (nodes[i] is TextNode.VerseMarker) {
                val inPoetry = hasPoeticLineInContext(nodes, i, lookBack = true)
                    || hasPoeticLineInContext(nodes, i, lookBack = false)

                if (inPoetry) {
                    // Check if there's already a PoeticLine immediately before this verse
                    // (skipping whitespace-only Text nodes)
                    var prevIdx = i - 1
                    while (prevIdx >= 0 && nodes[prevIdx] is TextNode.Text
                        && (nodes[prevIdx] as TextNode.Text).content.trim().isEmpty()) {
                        prevIdx--
                    }
                    if (prevIdx < 0 || nodes[prevIdx] !is TextNode.PoeticLine) {
                        nodes.add(i, TextNode.PoeticLine("", indentLevel = 1, rightAligned = false))
                        i++ // skip past the inserted node
                    }
                }
            }
            i++
        }
    }

    /**
     * Scans backward or forward from [fromIndex] looking for a PoeticLine node.
     * Stops at structural boundaries (Paragraph, BlankLine, SectionHeading).
     */
    private fun hasPoeticLineInContext(
        nodes: List<TextNode>, fromIndex: Int, lookBack: Boolean
    ): Boolean {
        val range = if (lookBack) (fromIndex - 1 downTo 0) else (fromIndex + 1 until nodes.size)
        for (j in range) {
            when (nodes[j]) {
                is TextNode.PoeticLine -> return true
                is TextNode.Paragraph, is TextNode.SectionHeading,
                    TextNode.BlankLine -> return false
                else -> { /* skip Text, VerseMarker, NoteMarker, etc. */ }
            }
        }
        return false
    }

    private fun insertMissingVerses(nodes: MutableList<TextNode>) {
        if (!renderVerses || expectedVerseRange.isEmpty()) return
        if (isStopped()) return
        val existingVerses = nodes.filterIsInstance<TextNode.VerseMarker>()
            .flatMap { if (it.endVerse > 0) (it.startVerse..it.endVerse).toList() else listOf(it.startVerse) }
            .toSet()
        val missing = mutableListOf<TextNode.VerseMarker>()
        if (expectedVerseRange.size == 1) {
            val v = expectedVerseRange[0]
            if (!existingVerses.contains(v)) {
                missing.add(TextNode.VerseMarker(v, 0, verseDisplay == VerseDisplay.PIN, "\\v $v "))
                addedMissingVerse = true
            }
        } else if (expectedVerseRange.size == 2) {
            for (v in expectedVerseRange[0]..expectedVerseRange[1]) {
                if (!existingVerses.contains(v)) {
                    missing.add(TextNode.VerseMarker(v, 0, verseDisplay == VerseDisplay.PIN, "\\v $v "))
                    addedMissingVerse = true
                }
            }
        }
        // Prepend missing verses at the front
        if (missing.isNotEmpty()) {
            nodes.addAll(0, missing)
            // Add space separator between inserted verses and existing content
            if (nodes.size > missing.size) {
                nodes.add(missing.size, TextNode.Text(" "))
            }
        }
    }

    private fun trimWhitespace(input: String): String =
        input.replace(Regex("^\\s+|\\s+$"), "")

    private fun removeLineBreaks(input: String): String =
        input.replace(Regex("\\s*\\n+\\s*"), " ")

    companion object {
        val beginParagraphStyle: String = "<para\\s+style=\"\\w*\"\\s*>"
        val beginParagraphPattern: Pattern = Pattern.compile(beginParagraphStyle)
        val endParagraphStyle: String = "</para>"
        val endParagraphPattern: Pattern = Pattern.compile(endParagraphStyle)

        /**
         * Returns a pattern that matches a para tag pair: <para style="STYLE">CONTENT</para>
         * Group 1 = content (or first capture inside STYLE if STYLE itself has a group).
         */
        private fun paraPattern(style: String): Pattern {
            return Pattern.compile(
                "<para\\s+style=\"$style\"\\s*>\\s*(((?!</para>).)*)</para>",
                Pattern.DOTALL
            )
        }

        /**
         * Returns a pattern that matches a self-closing para tag: <para style="STYLE"/>
         */
        private fun paraShortPattern(style: String): Pattern {
            return Pattern.compile("<para\\s+style=\"$style\"\\s*/>", Pattern.DOTALL)
        }
    }

    // -------------------------------------------------------------------------
    // Temporary conversion from TextNode to RenderNode
    // TODO: Refactor renderers to build RenderNode tree directly
    // -------------------------------------------------------------------------

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
                    machineReadable = node.machineReadable,
                    start = node.start,
                    end = node.end,
                    attributes = com.door43.translationstudio.rendering.model.NodeAttributes(
                        searchHighlighted = node.highlighted
                    )
                )
                is TextNode.Paragraph -> RenderNode.Paragraph(
                    indented = node.indented,
                    children = emptyList()  // TODO: Properly nest children
                )
                is TextNode.SectionHeading -> RenderNode.Section(
                    text = node.text,
                    isMajor = node.isMajor,
                    children = emptyList()
                )
                is TextNode.PoeticLine -> {
                    val children = if (node.content.isNotEmpty()) {
                        listOf(RenderNode.Text(node.content))
                    } else {
                        emptyList()
                    }
                    RenderNode.PoeticLine(
                        indentLevel = node.indentLevel,
                        rightAligned = node.rightAligned,
                        children = children
                    )
                }
                is TextNode.ChapterLabel -> RenderNode.ChapterLabel(node.text)
                is TextNode.Link -> RenderNode.Link(node.linkData)
                is TextNode.SearchHighlight -> RenderNode.Text(node.content,
                    attributes = com.door43.translationstudio.rendering.model.NodeAttributes(
                        searchHighlighted = true
                    )
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
                    machineReadable = node.machineReadable,
                    start = node.start,
                    end = node.end
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
                    // Extract text content from children for the PoeticLine content field
                    val content = node.children.joinToString("") { child ->
                        when (child) {
                            is RenderNode.Text -> child.content
                            else -> ""
                        }
                    }
                    listOf(TextNode.PoeticLine(content = content, indentLevel = node.indentLevel, rightAligned = node.rightAligned))
                }
                is RenderNode.ChapterLabel -> listOf(TextNode.ChapterLabel(node.text))
                is RenderNode.Link -> listOf(TextNode.Link(node.linkData))
                RenderNode.LineBreak -> listOf(TextNode.LineBreak)
                RenderNode.BlankLine -> listOf(TextNode.BlankLine)
            }
        }
    }
}
