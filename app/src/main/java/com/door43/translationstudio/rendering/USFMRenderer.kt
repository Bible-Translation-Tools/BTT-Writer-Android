package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.rendering.spannables.USFMNoteSpan
import com.door43.translationstudio.rendering.spannables.USFMVerseSpan
import java.util.regex.Pattern

/**
 * USFM rendering engine. Produces a hierarchical List<RenderNode> via render().
 */
class USFMRenderer(
    private val verseDisplay: VerseDisplay = VerseDisplay.NUMBER
) : ClickableRenderingEngine() {

    private var renderParagraphs = true
    private var renderVerses = verseDisplay != VerseDisplay.RAW
    private var expectedVerseRange = IntArray(0)
    private var suppressLeadingMajorSectionHeadings = false
    private var addedMissingVerse = false

    override fun setVersesEnabled(enable: Boolean) {
        renderVerses = enable
    }

    override fun setParagraphsEnabled(enable: Boolean) {
        renderParagraphs = enable
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
     * Render USFM input into a platform-agnostic List<RenderNode>.
     */
    override fun render(input: String): List<RenderNode> {
        addedMissingVerse = false
        if (isStopped()) return emptyList()

        // string pre-processing (pure String ops, no spans)
        var text = trimWhitespace(input)
        if (isStopped()) return emptyList()
        text = stripCarriageReturns(text)
        if (isStopped()) return emptyList()

        // collect all token matches simultaneously
        val allTokens = mutableListOf<Token>()
        allTokens.addAll(findChapterMarkers(text))
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
        allTokens.addAll(findUsxParagraphBreaks(text))
        allTokens.addAll(findUsxPoeticLines(text))
        allTokens.addAll(findUsxRightAlignedPoeticLines(text))
        allTokens.addAll(findUsxParagraphCloses(text))
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

        // insert missing expected verses
        insertMissingVerses(nodes)

        // build tree: group inline nodes into block node children
        return buildTree(nodes)
    }

    override fun getLeadingMajorSectionHeading(input: String): String {
        val matcher = MAJOR_SECTION_PATTERN.matcher(input)
        return if (matcher.find() && matcher.start() == 0) matcher.group(1)?.trim() ?: "" else ""
    }

    private data class Token(val start: Int, val end: Int, val nodes: List<RenderNode>)

    private fun findMajorSectionHeadings(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = MAJOR_SECTION_PATTERN.matcher(text)
        while (matcher.find()) {
            if (suppressLeadingMajorSectionHeadings && matcher.start() == 0) continue
            val content = matcher.group(1)?.trim() ?: continue
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(RenderNode.Section(text = content, isMajor = true), RenderNode.LineBreak)
                )
            )
        }
        return tokens
    }

    private fun findSectionHeadings(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = SECTION_PATTERN.matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: continue
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(RenderNode.Section(text = content, isMajor = false), RenderNode.LineBreak)
                )
            )
        }
        return tokens
    }

    private fun findParagraphBreaks(text: String): List<Token> {
        if (!renderParagraphs) return emptyList()
        val tokens = mutableListOf<Token>()
        val matcher = PARAGRAPH_PATTERN.matcher(text)
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
        val matcher = BLANK_LINE_PATTERN.matcher(text)
        while (matcher.find()) {
            tokens.add(Token(matcher.start(), matcher.end(), listOf(RenderNode.LineBreak)))
        }
        return tokens
    }

    private fun findPoeticLines(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = POETRY_PATTERN.matcher(text)
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
        val matcher = RIGHT_ALIGNED_POETRY_PATTERN.matcher(text)
        while (matcher.find()) {
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(RenderNode.PoeticLine(indentLevel = 0, rightAligned = true, children = emptyList()))
                )
            )
        }
        return tokens
    }

    private fun findChapterLabels(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = CHAPTER_LABEL_PATTERN.matcher(text)
        while (matcher.find()) {
            val content = matcher.group(1)?.trim() ?: ""
            tokens.add(Token(matcher.start(), matcher.end(), listOf(RenderNode.ChapterLabel(content))))
        }
        return tokens
    }

    private fun findVerses(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = Pattern.compile(USFMVerseSpan.PATTERN).matcher(text)
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

            val versesToAdd = if (endVerse > 0) {
                (startVerse..endVerse).toList()
            } else listOf(startVerse)
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
        val matcher = Pattern.compile(USFMNoteSpan.PATTERN).matcher(text)
        while (matcher.find()) {
            try {
                val caller = matcher.group(1) ?: continue
                var noteText = matcher.group(2) ?: ""
                noteText = noteText.replace(Regex("\\s*\\n+\\s*"), " ").trim()
                val note = USFMNoteSpan.parseNote(caller, noteText)
                val style = if (note.style == "f") NoteStyle.FOOTNOTE else NoteStyle.CROSS_REFERENCE
                tokens.add(
                    Token(
                        matcher.start(), matcher.end(),
                        listOf(
                            RenderNode.Note(
                                caller = note.caller,
                                passage = note.passage,
                                notes = note.notes,
                                noteStyle = style,
                                machineReadable = matcher.group(),
                                startPos = matcher.start(),
                                endPos = matcher.end()
                            )
                        )
                    )
                )
            } catch (_: Exception) {
                // failed to parse note — skip
            }
        }
        return tokens
    }

    private fun findSelah(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = SELAH_PATTERN.matcher(text)
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

    private fun findUsxParagraphBreaks(text: String): List<Token> {
        if (!renderParagraphs) return emptyList()
        val tokens = mutableListOf<Token>()
        val matcher = USX_PARAGRAPH_PATTERN.matcher(text)
        while (matcher.find()) {
            tokens.add(Token(
                matcher.start(), matcher.end(),
                listOf(RenderNode.Paragraph(indented = false, children = emptyList()))
            ))
        }
        return tokens
    }

    private fun findUsxPoeticLines(text: String): List<Token> {
        if (!renderParagraphs) return emptyList()
        val tokens = mutableListOf<Token>()
        val matcher = USX_POETRY_PATTERN.matcher(text)
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

    private fun findUsxRightAlignedPoeticLines(text: String): List<Token> {
        if (!renderParagraphs) return emptyList()
        val tokens = mutableListOf<Token>()
        val matcher = USX_RIGHT_POETRY_PATTERN.matcher(text)
        while (matcher.find()) {
            tokens.add(
                Token(
                    matcher.start(), matcher.end(),
                    listOf(RenderNode.PoeticLine(indentLevel = 0, rightAligned = true, children = emptyList()))
                )
            )
        }
        return tokens
    }

    private fun findUsxParagraphCloses(text: String): List<Token> {
        if (!renderParagraphs) return emptyList()
        val tokens = mutableListOf<Token>()
        val matcher = USX_PARA_CLOSE_PATTERN.matcher(text)
        while (matcher.find()) {
            tokens.add(Token(
                matcher.start(), matcher.end(),
                listOf(RenderNode.Paragraph(indented = false, children = emptyList()))
            ))
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
            // Overlapping token is skipped
        }
        return result
    }

    /**
     * Strip any remaining USFM backslash markers from gap text that wasn't
     * claimed by any token (broken or unknown markers).
     */
    private fun stripRemainingMarkers(text: String): String {
        // Strip remaining USFM backslash markers (e.g. \fr, \ft, \fv, \fk, \fq, \fqa, \f*)
        // Pattern: backslash followed by one or more word chars, optionally ending with *
        // Also strip stray USX <para...> / </para> tags not consumed by finders (unknown styles).
        return text
            .replace(Regex("\\\\[a-zA-Z][a-zA-Z0-9]*\\*?"), "")
            .replace(Regex("</?para\\b[^>]*/?>"), "")
    }

    private fun insertMissingVerses(nodes: MutableList<RenderNode>) {
        if (!renderVerses || expectedVerseRange.isEmpty()) return
        if (isStopped()) return
        val existingVerses = nodes.filterIsInstance<RenderNode.Verse>()
            .flatMap {
                if (it.endVerse > 0) {
                    (it.startVerse..it.endVerse).toList()
                } else listOf(it.startVerse)
            }
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

    /**
     * Strips carriage return characters from the input.
     */
    private fun stripCarriageReturns(input: String): String =
        input.replace("\r", "")

    /**
     * Tokenize chapter markers (\\c N). No node is emitted — position is consumed
     * so RAW_POSITION annotations downstream stay aligned with the original input.
     */
    private fun findChapterMarkers(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matcher = CHAPTER_MARKER_PATTERN.matcher(text)
        while (matcher.find()) {
            tokens.add(Token(matcher.start(), matcher.end(), emptyList()))
        }
        return tokens
    }

    companion object {
        // \c N — chapter marker; consumed but emits no node
        private val CHAPTER_MARKER_PATTERN = Pattern.compile("\\\\c +\\d+ *")

        // \p or \m followed by non-word char or end of string
        private val PARAGRAPH_PATTERN = Pattern.compile("\\\\[pm](?=\\W|$)")

        // \q followed by digit(s), then non-word char or end
        private val POETRY_PATTERN = Pattern.compile("\\\\q(\\d+)(?=\\W|$)")

        // \qr followed by non-word char or end
        private val RIGHT_ALIGNED_POETRY_PATTERN = Pattern.compile("\\\\qr(?=\\W|$)")

        // \s followed by optional digit(s), then space and content to end of line or next marker
        private val SECTION_PATTERN = Pattern.compile("\\\\s\\d*\\s(.+?)(?=\\\\|\\n|$)", Pattern.MULTILINE)

        // \ms followed by optional digit(s), then space and content to end of line or next marker
        private val MAJOR_SECTION_PATTERN = Pattern.compile("\\\\ms\\d*\\s(.+?)(?=\\\\|\\n|$)", Pattern.MULTILINE)

        // \b followed by non-word char or end
        private val BLANK_LINE_PATTERN = Pattern.compile("\\\\b(?=\\W|$)")

        // \cl followed by space and content to end of line or next marker
        private val CHAPTER_LABEL_PATTERN = Pattern.compile("\\\\cl\\s(.+?)(?=\\\\|\\n|$)", Pattern.MULTILINE)

        // \qs content\qs* — Selah char style
        private val SELAH_PATTERN = Pattern.compile("\\\\qs\\s(.+?)\\\\qs\\*")

        // USX paragraph tags that may appear mixed inside USFM content
        private val USX_PARAGRAPH_PATTERN = Pattern.compile("<para\\s+style=\"[pm]\"\\s*>")
        private val USX_POETRY_PATTERN = Pattern.compile("<para\\s+style=\"q(\\d+)\"\\s*>")
        private val USX_RIGHT_POETRY_PATTERN = Pattern.compile("<para\\s+style=\"qr\"\\s*>")
        private val USX_PARA_CLOSE_PATTERN = Pattern.compile("</para>")
    }
}
