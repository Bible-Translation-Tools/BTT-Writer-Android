package com.door43.translationstudio.ui.translate.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
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
    val mapping = remember(rawText, displayText) {
        buildDisplayToRawMapping(rawText, displayText)
    }

    var textFieldValue by remember(displayText) {
        mutableStateOf(TextFieldValue(text = displayText))
    }

    var lastEmittedRaw by remember { mutableStateOf(rawText) }

    LaunchedEffect(displayText, rawText) {
        println(rawText)
        println(lastEmittedRaw)

        if (rawText != lastEmittedRaw) {
            // External sync: preserve cursor position relative to the new text length
            val currentSel = textFieldValue.selection
            textFieldValue = TextFieldValue(
                text = displayText,
                selection = TextRange(
                    currentSel.start.coerceIn(0, displayText.length),
                    currentSel.end.coerceIn(0, displayText.length)
                )
            )
            lastEmittedRaw = rawText
        } else if (textFieldValue.text != displayText) {
            // Internal edit re-rendered: clamp existing selection
            val sel = textFieldValue.selection
            textFieldValue = textFieldValue.copy(
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
            if (textFieldValue.text == new.text) {
                // Only cursor/selection changed, no text mutation
                textFieldValue = new
                return@BasicTextField
            }

            val result = handleEdit(
                oldDisplay = textFieldValue.text,
                newDisplay = new.text,
                rawText = rawText,
                mapping = mapping
            )

            textFieldValue = new

            if (result != rawText) {
                lastEmittedRaw = result
                onRawTextChange(result)
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