package com.door43.translationstudio.ui.translate.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import com.door43.translationstudio.R

@Composable
fun RichEditField(
    rawText: String,
    displayText: String,
    onRawTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    iconFont: FontFamily = FontFamily(Font(R.font.icons)),
    noteColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val mapping = remember(rawText, displayText) {
        buildDisplayToRawMapping(rawText, displayText)
    }

    var textFieldValue by remember(displayText) {
        mutableStateOf(TextFieldValue(text = displayText))
    }

    var lastEmittedRaw by remember { mutableStateOf(rawText) }

    LaunchedEffect(displayText, rawText) {
        if (rawText != lastEmittedRaw) {
            // External change (different item, undo, remote sync, etc.)
            textFieldValue = TextFieldValue(
                text = displayText,
                selection = TextRange(displayText.length) // or 0
            )
            lastEmittedRaw = rawText
        } else if (textFieldValue.text != displayText) {
            // Our edit came back re-rendered — keep cursor, update text
            val sel = textFieldValue.selection
            textFieldValue = TextFieldValue(
                text = displayText,
                selection = TextRange(
                    sel.start.coerceIn(0, displayText.length),
                    sel.end.coerceIn(0, displayText.length)
                ),
                composition = textFieldValue.composition?.let {
                    TextRange(
                        it.start.coerceIn(0, displayText.length),
                        it.end.coerceIn(0, displayText.length)
                    )
                }
            )
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { new ->
            val result = handleEdit(
                old = textFieldValue,
                new = new,
                rawText = rawText,
                mapping = mapping
            )

            textFieldValue = result.textFieldValue

            if (result.rawText != rawText) {
                lastEmittedRaw = result.rawText
                onRawTextChange(result.rawText)
            }
        },
        visualTransformation = IconVisualTransformation(
            iconFont = iconFont,
            iconColor = noteColor,
            iconSize = textStyle.fontSize
        ),
        textStyle = textStyle,
        modifier = modifier
    )
}

private const val OBJ_CHAR = '\uFFFC'

internal data class InlineMarker(
    val displayOffset: Int,
    val rawRange: IntRange
)

internal data class EditResult(
    val textFieldValue: TextFieldValue,
    val rawText: String
)

/**
 * Maps each display offset (0..displayText.length) to a raw offset.
 * Size is displayText.length + 1 (includes the end-of-string position).
 */
internal data class OffsetMap(
    val displayToRaw: IntArray,
    val footnoteDisplayOffsets: Set<Int> // which display positions are \uFFFC
)

internal class IconVisualTransformation(
    private val iconFont: FontFamily,
    private val iconColor: Color,
    private val iconSize: TextUnit
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder()
        for (char in text.text) {
            if (char == OBJ_CHAR) {
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = iconFont,
                        fontSize = iconSize,
                        color = iconColor
                    )
                )
                builder.append(char)
                builder.pop()
            } else {
                builder.append(char)
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

/**
 * Walks both strings in parallel to build a display→raw offset map.
 *
 * Display text and raw text share the same visible characters, except:
 * - \f ... \f* blocks in raw → single \uFFFC in display
 * - Everything else is assumed to be identical character-by-character
 *
 * If the strings drift out of sync (shouldn't happen with a correct
 * renderer), the mapping clamps to valid bounds.
 */
private fun buildDisplayToRawMapping(
    rawText: String,
    displayText: String
): OffsetMap {
    val map = IntArray(displayText.length + 1)
    val footnoteOffsets = mutableSetOf<Int>()

    var rawIdx = 0
    var dispIdx = 0

    while (dispIdx < displayText.length) {
        if (displayText[dispIdx] == OBJ_CHAR) {
            // This display char corresponds to a \f ... \f* in raw
            footnoteOffsets.add(dispIdx)
            val fStart = rawText.indexOf("\\f ", rawIdx)
            val fEnd = if (fStart >= 0) {
                val starPos = rawText.indexOf("\\f*", fStart)
                if (starPos >= 0) starPos + 3 else -1
            } else -1

            if (fStart >= 0 && fEnd >= 0) {
                map[dispIdx] = fStart
                rawIdx = fEnd
            } else {
                // Fallback: no matching footnote found, just advance
                map[dispIdx] = rawIdx.coerceAtMost(rawText.length)
                rawIdx++
            }
            dispIdx++
        } else {
            map[dispIdx] = rawIdx.coerceAtMost(rawText.length)
            rawIdx++
            dispIdx++
        }
    }

    // End-of-string sentinel
    map[dispIdx] = rawIdx.coerceAtMost(rawText.length)

    return OffsetMap(displayToRaw = map, footnoteDisplayOffsets = footnoteOffsets)
}

private fun handleEdit(
    old: TextFieldValue,
    new: TextFieldValue,
    rawText: String,
    mapping: OffsetMap
): EditResult {
    val oldDisplay = old.text
    val newDisplay = new.text

    if (oldDisplay == newDisplay) {
        // Only selection/cursor changed
        return EditResult(textFieldValue = new, rawText = rawText)
    }

    val commonPrefix = oldDisplay.commonPrefixWith(newDisplay).length
    val commonSuffix = oldDisplay.reversed()
        .commonPrefixWith(newDisplay.reversed()).length
        .coerceAtMost(newDisplay.length - commonPrefix)

    val oldEnd = oldDisplay.length - commonSuffix
    val newEnd = newDisplay.length - commonSuffix

    val insertedDisplay = newDisplay.substring(commonPrefix, newEnd)

    // Map display offsets → raw offsets using the prebuilt map
    val rawStart = mapping.displayToRaw[commonPrefix.coerceAtMost(mapping.displayToRaw.lastIndex)]
    var rawEnd = mapping.displayToRaw[oldEnd.coerceAtMost(mapping.displayToRaw.lastIndex)]

    // If the deleted range includes a \uFFFC, we need rawEnd to point
    // past the entire \f ... \f* block, not just to its start
    for (i in commonPrefix until oldEnd) {
        if (i in mapping.footnoteDisplayOffsets) {
            // Find the end of this footnote's raw range
            val fStart = mapping.displayToRaw[i]
            val fStarEnd = rawText.indexOf("\\f*", fStart)
            if (fStarEnd >= 0) {
                val blockEnd = fStarEnd + 3
                if (blockEnd > rawEnd) rawEnd = blockEnd
            }
        }
    }

    // Safety clamp
    val safeRawStart = rawStart.coerceIn(0, rawText.length)
    val safeRawEnd = rawEnd.coerceIn(safeRawStart, rawText.length)

    val newRaw = rawText.substring(0, safeRawStart) +
            insertedDisplay +
            rawText.substring(safeRawEnd)

    return EditResult(
        textFieldValue = new,
        rawText = newRaw
    )
}