package com.door43.translationstudio.ui.translate.components.footnote

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

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
