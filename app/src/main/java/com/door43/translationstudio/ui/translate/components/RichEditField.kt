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
import kotlin.math.min

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
    val mapping = remember(rawText, displayText) { buildDisplayToRawMapping(rawText, displayText) }

    // Create a stable local state that persists across recompositions
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = displayText)) }

    // Use a SideEffect to update local text when external text changes,
    // WITHOUT resetting the selection/cursor.
    LaunchedEffect(displayText) {
        if (textFieldValue.text != displayText) {
            val oldText = textFieldValue.text
            val newText = displayText

            // Heuristic: If we are not currently editing (or text changed externally),
            // calculate the offset shift to keep the cursor in the same relative position.
            val currentSel = textFieldValue.selection

            // If the user isn't typing, just sync the text
            textFieldValue = textFieldValue.copy(
                text = newText,
                selection = TextRange(
                    currentSel.start.coerceIn(0, newText.length),
                    currentSel.end.coerceIn(0, newText.length)
                )
            )
        }
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            // Update local state immediately for responsiveness
            textFieldValue = newValue

            // Only trigger upstream update if the text content actually changed
            if (newValue.text != displayText) {
                val updatedRaw = handleEdit(
                    oldDisplay = displayText, // Use the current source of truth
                    newDisplay = newValue.text,
                    rawText = rawText,
                    mapping = mapping
                )
                onRawTextChange(updatedRaw)
            }
        },
        visualTransformation = remember(iconFont, noteColor, textStyle.fontSize) {
            IconVisualTransformation(iconFont, noteColor, textStyle.fontSize)
        },
        textStyle = textStyle,
        modifier = modifier
    )
}

private const val OBJ_CHAR = '\uFFFC'

internal data class OffsetMap(
    val displayToRaw: IntArray,
    val footnoteDisplayOffsets: Set<Int>
)

internal class IconVisualTransformation(
    private val iconFont: FontFamily,
    private val iconColor: Color,
    private val iconSize: TextUnit
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text.text.length)
        for (char in text.text) {
            if (char == OBJ_CHAR) {
                builder.pushStyle(SpanStyle(fontFamily = iconFont, fontSize = iconSize, color = iconColor))
                builder.append(char)
                builder.pop()
            } else {
                builder.append(char)
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun buildDisplayToRawMapping(rawText: String, displayText: String): OffsetMap {
    val map = IntArray(displayText.length + 1)
    val footnoteOffsets = mutableSetOf<Int>()

    var rawIdx = 0
    var dispIdx = 0

    while (dispIdx < displayText.length) {
        if (displayText[dispIdx] == OBJ_CHAR) {
            footnoteOffsets.add(dispIdx)
            // More lenient parsing: finds \f even if not followed by a space
            val fStart = rawText.indexOf("\\f", rawIdx)
            val fEnd = if (fStart >= 0) {
                val starPos = rawText.indexOf("\\f*", fStart)
                if (starPos >= 0) starPos + 3 else -1
            } else -1

            if (fStart >= 0 && fEnd >= 0) {
                map[dispIdx] = fStart
                rawIdx = fEnd
            } else {
                map[dispIdx] = rawIdx.coerceAtMost(rawText.length)
                rawIdx++
            }
        } else {
            map[dispIdx] = rawIdx.coerceAtMost(rawText.length)
            rawIdx++
        }
        dispIdx++
    }

    map[dispIdx] = rawIdx.coerceAtMost(rawText.length)
    return OffsetMap(map, footnoteOffsets)
}

private fun handleEdit(
    oldDisplay: String,
    newDisplay: String,
    rawText: String,
    mapping: OffsetMap
): String {
    // Zero-allocation prefix/suffix diffing
    val minLen = min(oldDisplay.length, newDisplay.length)
    var prefix = 0
    while (prefix < minLen && oldDisplay[prefix] == newDisplay[prefix]) {
        prefix++
    }

    val maxSuffix = minLen - prefix
    var suffix = 0
    while (suffix < maxSuffix && oldDisplay[oldDisplay.length - 1 - suffix] == newDisplay[newDisplay.length - 1 - suffix]) {
        suffix++
    }

    val oldEnd = oldDisplay.length - suffix
    val newEnd = newDisplay.length - suffix
    val insertedDisplay = newDisplay.substring(prefix, newEnd)

    val rawStart = mapping.displayToRaw[prefix.coerceAtMost(mapping.displayToRaw.lastIndex)]
    var rawEnd = mapping.displayToRaw[oldEnd.coerceAtMost(mapping.displayToRaw.lastIndex)]

    // Ensure entire footnote blocks are deleted if overlapped
    for (i in prefix until oldEnd) {
        if (i in mapping.footnoteDisplayOffsets) {
            val fStart = mapping.displayToRaw[i]
            val fStarEnd = rawText.indexOf("\\f*", fStart)
            if (fStarEnd >= 0) {
                val blockEnd = fStarEnd + 3
                if (blockEnd > rawEnd) rawEnd = blockEnd
            }
        }
    }

    val safeRawStart = rawStart.coerceIn(0, rawText.length)
    val safeRawEnd = rawEnd.coerceIn(safeRawStart, rawText.length)

    // Build the new raw string
    return buildString {
        append(rawText, 0, safeRawStart)
        append(insertedDisplay)
        append(rawText, safeRawEnd, rawText.length)
    }
}