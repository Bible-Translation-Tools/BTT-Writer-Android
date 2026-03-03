package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.adapter.SpannableAdapter
import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.ui.spannables.USXChar
import com.door43.translationstudio.ui.spannables.USXNoteSpan
import com.door43.translationstudio.ui.spannables.USXVerseSpan
import java.util.regex.Pattern

/**
 * USX rendering engine. Produces a List<TextNode> via renderToNodes().
 * The render(CharSequence) override is a shim that calls renderToNodes + SpannableAdapter.convert
 * so that existing callers continue to work.
 *
 * No Android framework code lives in this file.
 */
class USXRenderer(
    private val pinVerses: Boolean = false
) : ClickableRenderingEngine() {

    private var renderLineBreaks = false
    private var renderParagraphs = true
    private var renderVerses = true
    private var search: String? = null
    private var highlightColor = 0
    private var expectedVerseRange = IntArray(0)
    private var suppressLeadingMajorSectionHeadings = false
    private var addedMissingVerse = false

    // -------------------------------------------------------------------------
    // Configuration setters (no Android dependencies)
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Public API — new pipeline
    // -------------------------------------------------------------------------

    /**
     * Render USX input into a platform-agnostic List<TextNode>.
     * This is the primary output of the new pipeline; Task 6 will make the base
     * class return List<TextNode> directly.
     */
    override fun renderToNodes(input: String): List<TextNode> {
        addedMissingVerse = false
        if (isStopped()) return emptyList()

        // Phase 1: string pre-processing (pure String ops, no spans)
        var text = trimWhitespace(input)
        if (isStopped()) return emptyList()
        text = removeLineBreaks(text)
        if (isStopped()) return emptyList()

        // Phase 2: collect all token matches simultaneously
        val allTokens = mutableListOf<Token>()
        allTokens.addAll(findMajorSectionHeadings(text))
        allTokens.addAll(findSectionHeadings(text))
        allTokens.addAll(findParagraphBreaks(text))
        allTokens.addAll(findBlankLines(text))
        allTokens.addAll(findPoeticLines(text))
        allTokens.addAll(findRightAlignedPoeticLines(text))
        allTokens.addAll(findChapterLabels(text))
        allTokens.addAll(findVerses(text))
        allTokens.addAll(findNotes(text))
        allTokens.addAll(findSelah(text))
        if (isStopped()) return emptyList()

        // Phase 3: sort by position, remove overlapping tokens
        allTokens.sortBy { it.start }
        val tokens = removeOverlaps(allTokens)

        // Phase 4: assemble node list, filling gaps with Text nodes
        val nodes = mutableListOf<TextNode>()
        var lastIndex = 0
        for (token in tokens) {
            if (token.start > lastIndex) {
                val gap = text.substring(lastIndex, token.start)
                val cleaned = stripRemainingMarkers(gap)
                if (cleaned.isNotEmpty()) nodes.add(TextNode.Text(cleaned))
            }
            nodes.addAll(token.nodes)
            lastIndex = token.end
        }
        if (lastIndex < text.length) {
            val tail = text.substring(lastIndex)
            val cleaned = stripRemainingMarkers(tail)
            if (cleaned.isNotEmpty()) nodes.add(TextNode.Text(cleaned))
        }

        // Phase 5: search highlights (post-process Text nodes)
        if (!isStopped()) applySearchHighlights(nodes)

        // Phase 6: insert missing expected verses
        insertMissingVerses(nodes)

        return nodes
    }

    // -------------------------------------------------------------------------
    // Shim overrides — keep existing callers compiling
    // -------------------------------------------------------------------------

    /**
     * Shim: delegates to renderToNodes + SpannableAdapter.convert so that
     * RenderingEngine.start() and any direct callers of render() continue to work.
     */
    override fun render(input: CharSequence): CharSequence {
        val nodes = renderToNodes(input.toString())
        return SpannableAdapter.convert(nodes, searchHighlightColor = highlightColor)
    }

    /**
     * Required by ClickableRenderingEngine. Returns the platform-agnostic node list
     * for the given verse input.
     */
    override fun renderVerse(input: CharSequence): List<TextNode> {
        return renderToNodes(input.toString())
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

    // -------------------------------------------------------------------------
    // getLeadingMajorSectionHeading
    // -------------------------------------------------------------------------

    override fun getLeadingMajorSectionHeading(input: CharSequence): String {
        val matcher = paraPattern("ms").matcher(input.toString())
        return if (matcher.find() && matcher.start() == 0) matcher.group(1) ?: "" else ""
    }

    // -------------------------------------------------------------------------
    // Token data class
    // -------------------------------------------------------------------------

    private data class Token(val start: Int, val end: Int, val nodes: List<TextNode>)

    // -------------------------------------------------------------------------
    // find* helpers — each returns List<Token>
    // -------------------------------------------------------------------------

    private fun findMajorSectionHeadings(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = paraPattern("ms").matcher(text)
        while (matcher.find()) {
            if (suppressLeadingMajorSectionHeadings && matcher.start() == 0) continue
            val content = matcher.group(1)?.trim() ?: continue
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(TextNode.SectionHeading(content, isMajor = true), TextNode.LineBreak)
                )
            )
        }
        return tokens
    }

    private fun findSectionHeadings(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = paraPattern("s").matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: continue
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(TextNode.SectionHeading(content, isMajor = false), TextNode.LineBreak)
                )
            )
        }
        return tokens
    }

    private fun findParagraphBreaks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = paraPattern("p").matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: ""
            val nodes = mutableListOf<TextNode>(TextNode.Paragraph(indented = true))
            if (content.isNotEmpty()) nodes.add(TextNode.Text(content))
            nodes.add(TextNode.LineBreak)
            tokens.add(Token(matcher.start(), matcher.end(), nodes))
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
        val matcher = paraPattern("q(\\d+)").matcher(text)
        while (matcher.find()) {
            val level = matcher.group(1)?.toIntOrNull() ?: 1
            // group(2) is the content when the style regex has a capture group inside it
            val content = matcher.group(2)?.trim() ?: ""
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(TextNode.PoeticLine(content, indentLevel = level, rightAligned = false))
                )
            )
        }
        return tokens
    }

    private fun findRightAlignedPoeticLines(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = paraPattern("qr").matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: ""
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(
                        TextNode.LineBreak,
                        TextNode.PoeticLine(content, indentLevel = 0, rightAligned = true)
                    )
                )
            )
        }
        return tokens
    }

    private fun findChapterLabels(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = paraPattern("cl").matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: ""
            tokens.add(Token(matcher.start(), matcher.end(), listOf(TextNode.ChapterLabel(content))))
        }
        return tokens
    }

    private fun findVerses(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = Pattern.compile(USXVerseSpan.PATTERN).matcher(text)
        val foundVerses = mutableListOf<Int>()
        while (matcher.find()) {
            if (!renderVerses) continue
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
                    listOf(TextNode.VerseMarker(startVerse, endVerse, pinVerses, text.substring(matcher.start(), matcher.end())))
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
                                highlighted = highlighted
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
            // Overlapping token (e.g. char tags inside a note span) is skipped
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Gap text cleanup
    // -------------------------------------------------------------------------

    /**
     * Strip any remaining USX para/char markers from gap text that wasn't
     * claimed by any token (broken or unknown markers).
     */
    private fun stripRemainingMarkers(text: String): String {
        var out = text
        // Remove open/close para tags
        out = out.replace(beginParagraphPattern.toRegex(), "")
        out = out.replace(endParagraphPattern.toRegex(), "")
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

    // -------------------------------------------------------------------------
    // Search highlight post-processing
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Missing verse insertion
    // -------------------------------------------------------------------------

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
                missing.add(TextNode.VerseMarker(v, 0, pinVerses))
                addedMissingVerse = true
            }
        } else if (expectedVerseRange.size == 2) {
            for (v in expectedVerseRange[1] downTo expectedVerseRange[0]) {
                if (!existingVerses.contains(v)) {
                    missing.add(TextNode.VerseMarker(v, 0, pinVerses))
                    addedMissingVerse = true
                }
            }
        }
        // Prepend missing verses at the front
        nodes.addAll(0, missing)
    }

    // -------------------------------------------------------------------------
    // String pre-processing helpers (pure String ops, no Android)
    // -------------------------------------------------------------------------

    private fun trimWhitespace(input: String): String =
        input.replace(Regex("^\\s+|\\s+$"), "")

    private fun removeLineBreaks(input: String): String =
        input.replace(Regex("\\s*\\n+\\s*"), " ")

    // -------------------------------------------------------------------------
    // Companion — patterns shared with old public methods kept for callers
    // -------------------------------------------------------------------------

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
}
