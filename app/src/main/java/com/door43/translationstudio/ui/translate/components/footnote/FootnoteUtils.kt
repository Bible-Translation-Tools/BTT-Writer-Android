package com.door43.translationstudio.ui.translate.components.footnote

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import kotlin.text.iterator

internal const val OBJ_CHAR = '\u2800' // Braille Pattern Blank — renders as blank space

// Custom regex that won't match across \f openers (prevents greedy matching
// when user is typing a new footnote before an existing one).
// Content uses negative lookahead (?!\\f\s) at each position to stop at another opener.
internal val FOOTNOTE_REGEX = Regex("""\\f\s(\S)\s((?:(?!\\f\s)[\s\S])+?)\\f\*""")

internal fun findFootnoteBlocks(text: CharSequence): List<MatchResult> {
    return FOOTNOTE_REGEX.findAll(text).toList()
}

/**
 * Reconstruct raw USFM from visual text by replacing OBJ_CHAR placeholders
 * with their corresponding footnote blocks from the full raw text.
 */
internal fun visualToRaw(visualText: String, fullRawText: String): String {
    if (OBJ_CHAR !in visualText) return visualText

    val footnotes = findFootnoteBlocks(fullRawText)
    if (footnotes.isEmpty()) return visualText

    val fullVisual = replaceFootnotesForDisplay(fullRawText).displayText
    val copyStart = fullVisual.indexOf(visualText)
    if (copyStart == -1) return visualText

    val footnoteOffset = fullVisual.substring(0, copyStart).count { it == OBJ_CHAR }

    val sb = StringBuilder()
    var footnoteIdx = footnoteOffset
    for (ch in visualText) {
        if (ch == OBJ_CHAR && footnoteIdx < footnotes.size) {
            sb.append(footnotes[footnoteIdx].value)
            footnoteIdx++
        } else {
            sb.append(ch)
        }
    }
    return sb.toString()
}

internal data class FootnoteDisplayResult(
    val displayText: String,
    val footnoteRanges: List<IntRange>
)

internal fun replaceFootnotesForDisplay(rawText: String): FootnoteDisplayResult {
    val blocks = findFootnoteBlocks(rawText)
    if (blocks.isEmpty()) {
        return FootnoteDisplayResult(rawText, emptyList())
    }

    val sb = StringBuilder()
    val ranges = mutableListOf<IntRange>()
    var lastEnd = 0

    for (block in blocks) {
        sb.append(rawText, lastEnd, block.range.first)
        sb.append(OBJ_CHAR)
        ranges.add(block.range)
        lastEnd = block.range.last + 1
    }
    sb.append(rawText, lastEnd, rawText.length)

    return FootnoteDisplayResult(sb.toString(), ranges)
}

internal object FootnoteOutputTransformation : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val text = toString()
        val blocks = findFootnoteBlocks(text)
        for (block in blocks.reversed()) {
            replace(block.range.first, block.range.last + 1, OBJ_CHAR.toString())
        }
    }
}