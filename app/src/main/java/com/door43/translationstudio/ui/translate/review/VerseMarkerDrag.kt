package com.door43.translationstudio.ui.translate.review

import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.ui.translate.components.footnote.NOTE_CHAR
import java.util.regex.Pattern

object VerseMarkerDrag {

    // --- USFM patterns (applied to substring starting at pos) ---
    private val USFM_VERSE = Pattern.compile("^\\\\v\\s+\\d+(-\\d+)?\\s")
    private val USFM_NOTE = Pattern.compile("^\\\\f\\s.*?\\\\f\\*", Pattern.DOTALL)
    private val USFM_PARA = Pattern.compile("^\\\\[pqm]\\d?\\s")

    // --- USX patterns ---
    private val USX_VERSE = Pattern.compile("^<verse[^>]*/>")
    private val USX_NOTE = Pattern.compile("^<note[^>]*>.*?</note>", Pattern.DOTALL)
    private val USX_PARA_OPEN = Pattern.compile("^<para[^>]*>")
    private val USX_PARA_CLOSE = Pattern.compile("^</para>")

    /**
     * If USFM/USX markup starts at [pos] in [text], return its length. Otherwise return 0.
     */
    fun skipMarkupAt(text: String, pos: Int, format: TranslationFormat): Int {
        val sub = text.substring(pos)
        val patterns = if (format == TranslationFormat.USFM) {
            listOf(USFM_VERSE, USFM_NOTE, USFM_PARA)
        } else {
            listOf(USX_VERSE, USX_NOTE, USX_PARA_OPEN, USX_PARA_CLOSE)
        }
        for (pattern in patterns) {
            val matcher = pattern.matcher(sub)
            if (matcher.find() && matcher.start() == 0) {
                return matcher.end()
            }
        }
        return 0
    }

    /**
     * Snap [offset] to the nearest token start in [text].
     * Treats NOTE_CHAR (inline content: footnotes, verse pins) as individual tokens.
     */
    fun closestWordBoundary(text: String, offset: Int): Int {
        if (offset <= 0) return 0
        if (offset >= text.length) return text.length

        val ch = text[offset]

        // Inline content (NOTE_CHAR) is its own token — snap to it
        if (ch == NOTE_CHAR) return offset

        // Whitespace — move forward to next token (word or inline content)
        if (ch.isWhitespace()) {
            var pos = offset
            while (pos < text.length && text[pos].isWhitespace()) pos++
            return pos
        }

        // In a word — move to start of this word
        var pos = offset
        while (pos > 0 && !text[pos - 1].isWhitespace() && text[pos - 1] != NOTE_CHAR) {
            pos--
        }
        return pos
    }

    /**
     * Count tokens before [offset] in rendered text.
     * A "token" is either a word start or a NOTE_CHAR char (inline content: footnote, verse pin).
     */
    fun countWordStartsBefore(text: String, offset: Int): Int {
        var count = 0
        var i = 0
        val end = offset.coerceAtMost(text.length)
        while (i < end) {
            val ch = text[i]
            if (ch == NOTE_CHAR) {
                count++
                i++
            } else if (!ch.isWhitespace()) {
                count++
                // Skip to end of word
                while (i < end && !text[i].isWhitespace() && text[i] != NOTE_CHAR) {
                    i++
                }
            } else {
                i++
            }
        }
        return count
    }

    /**
     * Find the USFM offset of the Nth token (0-indexed).
     * A "token" is either a markup sequence (verse, footnote, paragraph) or a word start.
     * Returns [usfm].length if N exceeds available tokens.
     */
    fun findNthWordStartInUsfm(usfm: String, n: Int, format: TranslationFormat): Int {
        var tokenCount = 0
        var i = 0

        while (i < usfm.length) {
            val markupLen = skipMarkupAt(usfm, i, format)
            if (markupLen > 0) {
                if (tokenCount == n) return i
                tokenCount++
                i += markupLen
                continue
            }

            val ch = usfm[i]
            if (!ch.isWhitespace()) {
                if (tokenCount == n) return i
                tokenCount++
                // Skip to end of word
                while (
                    i < usfm.length
                        && skipMarkupAt(usfm, i, format) == 0
                        && !usfm[i].isWhitespace()
                ) {
                    i++
                }
            } else {
                i++
            }
        }

        return usfm.length
    }

    /**
     * Fallback mapping for scripts without whitespace word boundaries (CJK, Thai, etc.).
     * Uses the ratio of visible characters before tap offset to map into USFM.
     */
    fun mapByCharRatio(
        renderedText: String,
        tapOffset: Int,
        usfm: String,
        format: TranslationFormat
    ): Int {
        // Count visible chars (exclude NOTE_CHAR) before tap and total
        var visibleBefore = 0
        var visibleTotal = 0
        for (i in renderedText.indices) {
            if (renderedText[i] != NOTE_CHAR) {
                if (i < tapOffset) visibleBefore++
                visibleTotal++
            }
        }

        if (visibleTotal == 0) return 0

        val ratio = visibleBefore.toFloat() / visibleTotal.toFloat()

        // Count visible chars in USFM (skipping markup)
        var usfmVisibleTotal = 0
        var i = 0
        while (i < usfm.length) {
            val markupLen = skipMarkupAt(usfm, i, format)
            if (markupLen > 0) {
                i += markupLen
                continue
            }
            usfmVisibleTotal++
            i++
        }

        val targetVisibleCount = (ratio * usfmVisibleTotal).toInt()

        // Walk USFM to find the position of the targetVisibleCount-th visible char
        var count = 0
        i = 0
        while (i < usfm.length) {
            val markupLen = skipMarkupAt(usfm, i, format)
            if (markupLen > 0) {
                i += markupLen
                continue
            }
            if (count >= targetVisibleCount) return i
            count++
            i++
        }
        return usfm.length
    }

    /**
     * Move a verse marker using raw source-text positions from RAW_POSITION annotations.
     *
     * @param text              Original raw text (USFM/USX)
     * @param verseRawStart     Raw start position of the verse marker being moved
     * @param verseRawEnd       Raw end position of the verse marker being moved
     * @param marker            The verse marker string to insert (e.g. `\v 1 `)
     * @param targetRawPosition Raw position where the verse should be inserted
     * @return Updated raw text with the verse at its new position
     */
    fun moveVerseByRawPosition(
        text: String,
        verseRawStart: Int,
        verseRawEnd: Int,
        marker: String,
        targetRawPosition: Int
    ): String {
        // Remove the verse from its current position
        val textWithout = text.removeRange(verseRawStart, verseRawEnd)

        // Adjust target position to account for the removed range
        val adjusted = when {
            targetRawPosition >= verseRawEnd -> targetRawPosition - (verseRawEnd - verseRawStart)
            targetRawPosition > verseRawStart -> verseRawStart
            else -> targetRawPosition
        }.coerceIn(0, textWithout.length)

        // Snap to word boundary — don't split words
        val snapped = snapToWordBoundary(textWithout, adjusted)

        return textWithout.substring(0, snapped) + marker + textWithout.substring(snapped)
    }

    /**
     * Snap a position in raw text to the nearest word boundary.
     * If inside a word, moves back to its start.
     */
    private fun snapToWordBoundary(text: String, pos: Int): Int {
        if (pos <= 0 || pos >= text.length) return pos
        if (text[pos - 1].isWhitespace()) return pos
        if (text[pos].isWhitespace()) return pos
        // Inside a word — walk back to its start
        var p = pos
        while (p > 0 && !text[p - 1].isWhitespace()) p--
        return p
    }
}
