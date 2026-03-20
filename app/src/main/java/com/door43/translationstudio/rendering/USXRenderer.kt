package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.NodeAttributes
import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.rendering.spannables.USXChar
import com.door43.translationstudio.rendering.spannables.USXNoteSpan
import com.door43.translationstudio.rendering.spannables.USXVerseSpan
import java.util.regex.Pattern

/**
 * USX rendering engine. Produces a hierarchical List<RenderNode> via renderToNodes().
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
    override fun render(input: String): List<RenderNode> {
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
        allTokens.addAll(findParagraphCloseTokens(text))
        allTokens.addAll(findBlankLines(text))
        allTokens.addAll(findPoeticLines(text))
        allTokens.addAll(findRightAlignedPoeticLines(text))
        allTokens.addAll(findChapterLabels(text))
        if (isStopped()) return emptyList()

        // sort by position, remove overlapping tokens
        allTokens.sortBy { it.start }
        val tokens = removeOverlaps(allTokens)

        // assemble node list, filling gaps with Text nodes
        val nodes = mutableListOf<RenderNode>()
        var lastIndex = 0
        for (token in tokens) {
            if (token.start > lastIndex) {
                val gap = text.substring(lastIndex, token.start)
                val cleaned = stripRemainingMarkers(gap)
                if (cleaned.isNotBlank()) nodes.add(RenderNode.Text(cleaned, start = lastIndex, end = token.start))
            }
            nodes.addAll(token.nodes)
            lastIndex = token.end
        }
        if (lastIndex < text.length) {
            val tail = text.substring(lastIndex)
            val cleaned = stripRemainingMarkers(tail)
            if (cleaned.isNotBlank()) nodes.add(RenderNode.Text(cleaned, start = lastIndex, end = text.length))
        }

        // search highlights (post-process Text nodes)
        if (!isStopped()) applySearchHighlights(nodes)

        // insert missing expected verses
        insertMissingVerses(nodes)

        // build tree: group inline nodes into block node children
        // Filter out empty Paragraphs from </para> close tokens that had no orphan content
        return buildTree(nodes).filter { !(it is RenderNode.Paragraph && it.children.isEmpty()) }
    }

    override fun getLeadingMajorSectionHeading(input: CharSequence): String {
        val matcher = paraPattern("ms").matcher(input.toString())
        return if (matcher.find() && matcher.start() == 0) matcher.group(1) ?: "" else ""
    }

    private data class Token(val start: Int, val end: Int, val nodes: List<RenderNode>)

    private fun findMajorSectionHeadings(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val pattern = Pattern.compile("<para\\s+style=\"ms\"\\s*>\\s*([^<]*?)\\s*(?=<|$)")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            if (suppressLeadingMajorSectionHeadings && matcher.start() == 0) continue
            val content = matcher.group(1)?.trim() ?: continue
            val tokenEnd = matcher.start() + matcher.group().length
            tokens.add(
                Token(
                    matcher.start(), tokenEnd,
                    listOf(RenderNode.Section(text = content, isMajor = true), RenderNode.LineBreak)
                )
            )
        }
        return tokens
    }

    private fun findSectionHeadings(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val pattern = Pattern.compile("<para\\s+style=\"s\"\\s*>\\s*([^<]*?)\\s*(?=<|$)")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: continue
            val tokenEnd = matcher.start() + matcher.group().length
            tokens.add(
                Token(
                    matcher.start(), tokenEnd,
                    listOf(RenderNode.Section(text = content, isMajor = false), RenderNode.LineBreak)
                )
            )
        }
        return tokens
    }

    private fun findParagraphBreaks(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = beginParagraphPattern.matcher(text)
        while (matcher.find()) {
            val tagText = matcher.group() ?: continue
            if (tagText.contains("style=\"p\"") || tagText.contains("style=\"m\"")) {
                tokens.add(Token(
                    matcher.start(), matcher.end(),
                    listOf(RenderNode.Paragraph(indented = false, children = emptyList()))
                ))
            }
        }
        return tokens
    }

    /**
     * Tokenizes </para> closing tags as implicit Paragraph markers.
     * This ensures that content after a closing </para> is not absorbed into the
     * preceding block node's children. Empty implicit Paragraphs (e.g., after poetry
     * closing tags) are filtered out by buildTree.
     */
    private fun findParagraphCloseTokens(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = endParagraphPattern.matcher(text)
        while (matcher.find()) {
            tokens.add(Token(
                matcher.start(), matcher.end(),
                listOf(RenderNode.Paragraph(indented = false, children = emptyList()))
            ))
        }
        return tokens
    }

    private fun findBlankLines(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = paraShortPattern("b").matcher(text)
        while (matcher.find()) {
            tokens.add(Token(matcher.start(), matcher.end(), listOf(RenderNode.LineBreak)))
        }
        return tokens
    }

    private fun findPoeticLines(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val pattern = Pattern.compile("<para\\s+style=\"q(\\d+)\"\\s*>")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val level = matcher.group(1)?.toIntOrNull() ?: 1
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(RenderNode.PoeticLine(indentLevel = level, rightAligned = false, children = emptyList()))
                )
            )
        }
        return tokens
    }

    private fun findRightAlignedPoeticLines(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val pattern = Pattern.compile("<para\\s+style=\"qr\"\\s*>")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(
                        RenderNode.PoeticLine(indentLevel = 0, rightAligned = true, children = emptyList())
                    )
                )
            )
        }
        return tokens
    }

    private fun findChapterLabels(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val pattern = Pattern.compile("<para\\s+style=\"cl\"\\s*>\\s*([^<]*?)\\s*(?=<|$)")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: ""
            val tokenEnd = matcher.start() + matcher.group().length
            tokens.add(Token(matcher.start(), tokenEnd, listOf(RenderNode.ChapterLabel(content))))
        }
        return tokens
    }

    private fun findVerses(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = Pattern.compile(USXVerseSpan.PATTERN).matcher(text)
        val foundVerses = mutableListOf<Int>()
        while (matcher.find()) {
            if (!renderVerses) {
                tokens.add(
                    Token(
                        matcher.start(), matcher.end(),
                        listOf(RenderNode.Text(text.substring(matcher.start(), matcher.end()), start = matcher.start(), end = matcher.end()))
                    )
                )
                continue
            }
            val verseStr = matcher.group(1) ?: continue
            val parts = verseStr.split("-")
            val startVerse = parts[0].toIntOrNull() ?: continue
            val endVerse = if (parts.size == 2) parts[1].toIntOrNull() ?: 0 else 0

            val versesToAdd = if (endVerse > 0) (startVerse..endVerse).toList() else listOf(startVerse)
            val alreadyRendered = versesToAdd.any { foundVerses.contains(it) }
            if (alreadyRendered) continue
            foundVerses.addAll(versesToAdd)

            if (expectedVerseRange.isNotEmpty()) {
                val minV = expectedVerseRange[0]
                val maxV = if (expectedVerseRange.size > 1) expectedVerseRange[1] else minV
                val effectiveEnd = if (endVerse > 0) endVerse else startVerse
                if (startVerse < minV || effectiveEnd > maxV) continue
            }

            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(RenderNode.Verse(
                        startVerse = startVerse,
                        endVerse = endVerse,
                        pinned = verseDisplay == VerseDisplay.PIN,
                        machineReadable = text.substring(matcher.start(), matcher.end()),
                        start = matcher.start(),
                        end = matcher.end()
                    ))
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
            } catch (_: Exception) {
                null
            }
            if (note != null) {
                val style = if (note.style == "f") NoteStyle.FOOTNOTE else NoteStyle.CROSS_REFERENCE
                val highlighted = search != null && noteText.lowercase().contains(search!!)
                tokens.add(
                    Token(
                        matcher.start(), matcher.end(),
                        listOf(
                            RenderNode.Note(
                                caller = note.caller,
                                passage = note.passage.toString(),
                                notes = note.notes.toString(),
                                noteStyle = style,
                                machineReadable = noteText,
                                startPos = matcher.start(),
                                endPos = matcher.end(),
                                attributes = NodeAttributes(searchHighlighted = highlighted)
                            )
                        )
                    )
                )
            }
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
                        RenderNode.PoeticLine(indentLevel = 0, rightAligned = true, children = listOf(RenderNode.Text(content.trim())))
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

    private fun applySearchHighlights(nodes: MutableList<RenderNode>) {
        val term = search ?: return
        val result = mutableListOf<RenderNode>()
        for (node in nodes) {
            if (isStopped()) return
            if (node is RenderNode.Text && !node.attributes.searchHighlighted) {
                val lower = node.content.lowercase()
                var last = 0
                while (true) {
                    val pos = lower.indexOf(term, last)
                    if (pos < 0) break
                    if (pos > last) result.add(RenderNode.Text(node.content.substring(last, pos)))
                    result.add(RenderNode.Text(node.content.substring(pos, pos + term.length), attributes = NodeAttributes(searchHighlighted = true)))
                    last = pos + term.length
                }
                if (last < node.content.length) result.add(RenderNode.Text(node.content.substring(last)))
            } else {
                result.add(node)
            }
        }
        nodes.clear()
        nodes.addAll(result)
    }

    private fun insertMissingVerses(nodes: MutableList<RenderNode>) {
        if (!renderVerses || expectedVerseRange.isEmpty()) return
        if (isStopped()) return
        val existingVerses = nodes.filterIsInstance<RenderNode.Verse>()
            .flatMap { if (it.endVerse > 0) (it.startVerse..it.endVerse).toList() else listOf(it.startVerse) }
            .toSet()
        val missing = mutableListOf<RenderNode.Verse>()
        if (expectedVerseRange.size == 1) {
            val v = expectedVerseRange[0]
            if (!existingVerses.contains(v)) {
                missing.add(RenderNode.Verse(startVerse = v, endVerse = 0, pinned = verseDisplay == VerseDisplay.PIN, machineReadable = "\\v $v "))
                addedMissingVerse = true
            }
        } else if (expectedVerseRange.size == 2) {
            for (v in expectedVerseRange[0]..expectedVerseRange[1]) {
                if (!existingVerses.contains(v)) {
                    missing.add(RenderNode.Verse(startVerse = v, endVerse = 0, pinned = verseDisplay == VerseDisplay.PIN, machineReadable = "\\v $v "))
                    addedMissingVerse = true
                }
            }
        }
        if (missing.isNotEmpty()) {
            nodes.addAll(0, missing)
            if (nodes.size > missing.size) {
                nodes.add(missing.size, RenderNode.Text(" "))
            }
        }
    }

    private fun trimWhitespace(input: String): String =
        input.replace(Regex("^\\s+|\\s+$"), "")

    private fun removeLineBreaks(input: String): String =
        input.replace(Regex("\\s*\\n+\\s*"), " ")

    companion object {
        const val BEGIN_PARAGRAPH_STYLE: String = "<para\\s+style=\"\\w*\"\\s*>"
        const val END_PARAGRAPH_STYLE: String = "</para>"

        val beginParagraphPattern: Pattern = Pattern.compile(BEGIN_PARAGRAPH_STYLE)
        val endParagraphPattern: Pattern = Pattern.compile(END_PARAGRAPH_STYLE)

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
