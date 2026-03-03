package com.door43.translationstudio.rendering

import android.content.Context
import com.door43.translationstudio.rendering.adapter.SpannableAdapter
import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.ui.spannables.Span
import com.door43.translationstudio.ui.spannables.USFMChar
import com.door43.translationstudio.ui.spannables.USFMNoteSpan
import com.door43.translationstudio.ui.spannables.USFMParagraphSpan
import com.door43.translationstudio.ui.spannables.USFMVerseSpan
import java.util.regex.Pattern

/**
 * USFM rendering engine. Produces a List<TextNode> via renderToNodes().
 * The render(CharSequence) override is a shim that calls renderToNodes + SpannableAdapter.convert
 * so that existing callers continue to work until Task 6 updates the base class.
 *
 * No Android framework code lives in this file. The android.content.Context import is retained
 * only to allow the legacy constructors to satisfy the inherited RenderingEngine.context field
 * (which is removed in Task 6). All rendering logic is Android-free.
 */
class USFMRenderer : ClickableRenderingEngine {

    private var noteListener: Span.OnClickListener? = null
    private var verseListener: Span.OnClickListener? = null
    private var renderParagraphs = true
    private var renderVerses = true
    private var search: String? = null
    private var highlightColor = 0
    private var expectedVerseRange = IntArray(0)
    private var suppressLeadingMajorSectionHeadings = false
    private var addedMissingVerse = false

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** No-arg constructor — for Compose / unit tests (no Context needed). */
    constructor()

    /** Constructor with listeners but no Context. */
    constructor(verseListener: Span.OnClickListener?, noteListener: Span.OnClickListener?) {
        this.verseListener = verseListener
        this.noteListener = noteListener
    }

    /**
     * Legacy constructor kept for callers that supply only a Context.
     * The context is stored in the inherited RenderingEngine.context field.
     */
    constructor(context: Context) : this() {
        this.context = context
    }

    /**
     * Legacy constructor kept for existing call sites in DefaultRenderer and
     * ClickableRenderingEngineFactory. Context is stored in the inherited field.
     */
    constructor(
        context: Context,
        verseListener: Span.OnClickListener?,
        noteListener: Span.OnClickListener?
    ) : this(verseListener, noteListener) {
        this.context = context
    }

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
     * Render USFM input into a platform-agnostic List<TextNode>.
     * This is the primary output of the new pipeline; Task 6 will make the base
     * class return List<TextNode> directly.
     */
    fun renderToNodes(input: String): List<TextNode> {
        addedMissingVerse = false
        if (isStopped()) return emptyList()

        // Phase 1: string pre-processing (pure String ops, no spans)
        var text = trimWhitespace(input)
        if (isStopped()) return emptyList()
        text = stripCarriageReturns(text)
        if (isStopped()) return emptyList()
        text = stripChapterMarkers(text)   // strips \c N — USFM-specific
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
        allTokens.addAll(findUsfmParagraphMarkers(text))  // \p standalone markers — USFM-specific
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
    // Shim overrides — keep existing callers compiling (Task 6 removes these)
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
     * Shim: required by ClickableRenderingEngine. Delegates to render().
     */
    override fun renderVerse(input: CharSequence): CharSequence = render(input)

    // -------------------------------------------------------------------------
    // getLeadingMajorSectionHeading
    // -------------------------------------------------------------------------

    override fun getLeadingMajorSectionHeading(input: CharSequence): CharSequence {
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
        val matcher = Pattern.compile(USFMVerseSpan.PATTERN).matcher(text)
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

            val pinned = verseListener != null
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(TextNode.VerseMarker(startVerse, endVerse, pinned))
                )
            )
        }
        return tokens
    }

    private fun findNotes(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = Pattern.compile(USFMNoteSpan.PATTERN).matcher(text)
        while (matcher.find()) {
            try {
                val caller = matcher.group(1) ?: continue
                var noteText = matcher.group(2) ?: ""
                noteText = noteText.replace(Regex("\\s*\\n+\\s*"), " ").trim()
                val note = USFMNoteSpan.parseNote(caller, noteText)
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
            } catch (e: Exception) {
                // failed to parse note — skip
            }
        }
        return tokens
    }

    private fun findSelah(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = USFMChar.getPattern(USFMChar.STYLE_SELAH).matcher(text)
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

    /**
     * Finds standalone USFM paragraph markers (\p) and emits Paragraph(indented = false) nodes.
     * Only active when renderParagraphs is true.
     */
    private fun findUsfmParagraphMarkers(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        if (!renderParagraphs) return tokens
        val matcher = Pattern.compile(USFMParagraphSpan.PATTERN, Pattern.DOTALL).matcher(text)
        while (matcher.find()) {
            tokens.add(Token(matcher.start(), matcher.end(), listOf(TextNode.Paragraph(indented = false))))
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
            // Overlapping token is skipped
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Gap text cleanup
    // -------------------------------------------------------------------------

    /**
     * Strip any remaining USFM/para markers from gap text that wasn't
     * claimed by any token (broken or unknown markers).
     */
    private fun stripRemainingMarkers(text: String): String {
        var out = text
        // Remove open/close para tags (XML-style, shared with USX input format)
        out = out.replace(Regex("<para\\s+style=\"\\w*\"\\s*>"), "")
        out = out.replace("</para>", "")
        // Remove self-closing para tags
        out = out.replace(Regex("<para\\s+style=\"\\w*\"\\s*/>"), "")
        // Strip remaining USFM backslash markers (e.g. \fr, \ft, \fv, \fk, \fq, \fqa, \f*)
        // Pattern: backslash followed by one or more word chars, optionally ending with *
        out = out.replace(Regex("\\\\[a-zA-Z][a-zA-Z0-9]*\\*?"), "")
        return out
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
        val pinned = verseListener != null
        val missing = mutableListOf<TextNode.VerseMarker>()
        if (expectedVerseRange.size == 1) {
            val v = expectedVerseRange[0]
            if (!existingVerses.contains(v)) {
                missing.add(TextNode.VerseMarker(v, 0, pinned))
                addedMissingVerse = true
            }
        } else if (expectedVerseRange.size == 2) {
            for (v in expectedVerseRange[1] downTo expectedVerseRange[0]) {
                if (!existingVerses.contains(v)) {
                    missing.add(TextNode.VerseMarker(v, 0, pinned))
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

    /**
     * Strips carriage return characters from the input.
     */
    private fun stripCarriageReturns(input: String): String =
        input.replace("\r", "")

    /**
     * Strips chapter markers (\\c N) from the input. No node is emitted for these.
     */
    private fun stripChapterMarkers(input: String): String =
        input.replace(Regex("\\\\c +\\d+ *"), "")

    // -------------------------------------------------------------------------
    // Companion — patterns
    // -------------------------------------------------------------------------

    companion object {
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
