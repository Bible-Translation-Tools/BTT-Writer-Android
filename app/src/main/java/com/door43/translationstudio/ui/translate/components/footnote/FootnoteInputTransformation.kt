package com.door43.translationstudio.ui.translate.components.footnote

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

/**
 * Removes orphaned USFM footnote markers from the input text.
 *
 * "Orphaned" means a `\f` opener without a matching `\f*` closer, or a `\f*` closer with no
 * matching `\f` opener. Well-formed `\f...\f*` blocks are preserved intact.
 *
 * The algorithm pairs openers and closers right-to-left (each `\f*` is matched to the nearest
 * preceding unpaired `\f `), so that when the user is typing or pasting inside the second of two
 * adjacent footnotes the correct block is preserved.
 *
 * Orphaned opener removal strategy:
 * - If a next valid block (or orphaned closer) follows the opener in the text: skip everything
 *   from the opener up to (but not including) that anchor.
 * - Otherwise (e.g. the user deleted `\f*` at the end): strip only the USFM marker tokens
 *   (`\f caller `, `\ft content `, etc.) and preserve any non-USFM words that follow.
 */
internal fun enforceFootnoteAtomicity(text: String): String {
    if ("\\f" !in text) return text

    val openerRegex = Regex("""\\f\s""")
    val closerRegex = Regex("""\\f\*""")

    val openerPositions = openerRegex.findAll(text).map { it.range.first }.toList()
    val closerPositions = closerRegex.findAll(text).map { it.range.first }.toList()

    if (openerPositions.isEmpty() && closerPositions.isEmpty()) return text

    // ── Right-to-left pairing ──────────────────────────────────────────────────
    // For each \f* (from rightmost to leftmost), claim the nearest unclaimed \f  to its left,
    // and validate by checking that text[opener..closer+2] matches FOOTNOTE_REGEX exactly.
    val validBlocks = mutableListOf<IntRange>()
    val claimedOpeners = mutableSetOf<Int>()

    for (closerPos in closerPositions.sortedDescending()) {
        val closerEnd = closerPos + 2  // \f* spans 3 chars: closerPos, +1, +2

        val opener = openerPositions
            .filter { it < closerPos && it !in claimedOpeners }
            .maxOrNull() ?: continue

        val candidate = text.substring(opener, closerEnd + 1)
        // Use find (non-greedy) rather than matches: we want the SHORTEST possible match to
        // span the entire candidate. If there is an intermediate \f* in the content,
        // find() will stop there and the range won't cover the full candidate — rejecting it.
        val result = FOOTNOTE_REGEX.find(candidate)
        if (result != null && result.range.first == 0 && result.range.last == candidate.length - 1) {
            validBlocks.add(opener..closerEnd)
            claimedOpeners.add(opener)
        }
    }

    validBlocks.sortBy { it.first }

    // Check whether the text is already perfectly clean
    val orphanedOpeners = openerPositions.filter { it !in claimedOpeners }
    val orphanedClosers = closerPositions.filter { pos ->
        validBlocks.none { it.last == pos + 2 }
    }
    if (orphanedOpeners.isEmpty() && orphanedClosers.isEmpty()) return text

    // ── Rebuild ────────────────────────────────────────────────────────────────
    return buildString {
        var i = 0
        val orphanedOpenerSet = orphanedOpeners.toSet()
        val orphanedCloserSet = orphanedClosers.toSet()

        while (i < text.length) {
            // 1. Valid block?
            val validBlock = validBlocks.firstOrNull { it.first == i }
            if (validBlock != null) {
                append(text, validBlock.first, validBlock.last + 1)
                i = validBlock.last + 1
                continue
            }

            // 2. Orphaned \f* closer?
            if (i in orphanedCloserSet) {
                i += 3  // skip \f*
                continue
            }

            // 3. Orphaned \f opener?
            if (i in orphanedOpenerSet) {
                // Find the nearest upcoming anchor: next valid block start, or next orphaned \f*
                val nextValidStart = validBlocks.firstOrNull { it.first > i }?.first
                val nextOrphanedCloser = orphanedCloserSet.filter { it > i }.minOrNull()
                val nextAnchor: Int? = listOfNotNull(nextValidStart, nextOrphanedCloser)
                    .minOrNull()

                // Case A: skip to the anchor (strips "orphaned opener … stuff up to next block")
                // Case B: no anchor ahead → strip only USFM marker tokens, preserve trailing body text
                i = nextAnchor ?: skipUsfmTokenChain(text, i)

                continue
            }

            // 4. Normal character
            append(text[i])
            i++
        }
    }
}

/**
 * Starting at [start] (which is a `\f` position), advances past a chain of USFM footnote marker
 * tokens and returns the position of the first non-USFM character.
 *
 * A "chain" is: `\f caller ` followed by zero or more `\ft|fq|fk… content ` groups.
 * Non-USFM words (e.g. body text that was adjacent to the footnote) are NOT consumed.
 *
 * Examples:
 *  `\f + \ft note world`  →  skips `\f + \ft note `, returns position of `w`
 *  `\f + stuff`           →  skips `\f + `, returns position of `s`
 */
private fun skipUsfmTokenChain(text: String, start: Int): Int {
    var i = start
    // Skip the opener:  \f SPACE CALLER SPACE
    // (we know text[i] == '\' and text[i+1] == 'f' and text[i+2] == ' ')
    i += 3  // skip \, f, space
    // skip caller char (single non-space)
    if (i < text.length && text[i] != ' ') i++
    // skip space after caller
    if (i < text.length && text[i] == ' ') i++

    // Now strip any following \f<field> tokens (e.g. \ft, \fq, \fk, \fl …)
    while (i < text.length) {
        // Must be \f followed by a non-space, non-star char (i.e. a field marker, not an opener/closer)
        if (i + 2 < text.length
            && text[i] == '\\'
            && text[i + 1] == 'f'
            && text[i + 2] != ' '
            && text[i + 2] != '*'
        ) {
            // Skip field name: \f + letters until space
            i += 2  // skip \f
            while (i < text.length && text[i] != ' ') i++  // skip type chars (t, q, k …)
            if (i < text.length && text[i] == ' ') i++     // skip space after field name

            // Skip one "word" of field content (everything up to the next space or \)
            while (i < text.length && text[i] != ' ' && text[i] != '\\') i++
            if (i < text.length && text[i] == ' ') i++     // skip trailing space
        } else {
            break
        }
    }

    return i
}

internal class FootnoteInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val editedText = toString()
        if ("\\f" !in editedText) return

        val cleaned = enforceFootnoteAtomicity(editedText)

        if (cleaned != editedText) {
            replace(0, length, cleaned)
        }
    }
}
