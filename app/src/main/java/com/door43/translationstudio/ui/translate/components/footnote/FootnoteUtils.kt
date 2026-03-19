package com.door43.translationstudio.ui.translate.components.footnote

import com.door43.translationstudio.rendering.spannables.USFMNoteSpan
import kotlin.text.iterator

internal const val OBJ_CHAR = '\u2800' // Braille Pattern Blank — renders as blank space

internal val FOOTNOTE_REGEX = Regex(USFMNoteSpan.PATTERN)

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
