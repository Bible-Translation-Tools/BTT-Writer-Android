package com.door43.translationstudio.ui.translate.components

import android.content.ClipData
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.ui.translate.components.footnote.FootnoteOutputTransformation
import com.door43.translationstudio.ui.translate.components.footnote.OBJ_CHAR
import com.door43.translationstudio.ui.translate.components.footnote.visualToRaw
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(FlowPreview::class)
@Composable
fun UsfmEditText(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    shouldFocus: Boolean = false,
    onFocusConsumed: () -> Unit = {},
    textStyle: TextStyle = TextStyle.Default,
    noteColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val density = LocalDensity.current
    val lineHeightPx = with(density) { textStyle.lineHeight.toPx() }
    val fontSizePx = with(density) { textStyle.fontSize.toPx() }
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    val textFieldState = remember { TextFieldState(text) }

    val currentOnTextChange by rememberUpdatedState(onTextChange)

    // Track the last value we emitted to the parent, so we can distinguish
    // "parent echoing our value back" from "parent changed text externally"
    var lastEmittedText by remember { mutableStateOf(text) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Icon rendering setup
    val iconPainter = rememberVectorPainter(Icons.Default.Description)
    val iconColorFilter = remember(noteColor) { ColorFilter.tint(noteColor) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    // Clipboard interception: copy raw USFM instead of visual text with placeholders
    val platformClipboard = LocalClipboard.current
    val rawClipboard = remember(platformClipboard) {
        RawUsfmClipboard(platformClipboard, textFieldState)
    }

    // Sync external text into TextFieldState only for genuine external changes
    // (e.g., footnote dialog, undo from viewmodel), not echoed-back values from our own edits
    LaunchedEffect(text) {
        if (text != lastEmittedText && textFieldState.text.toString() != text) {
            textFieldState.edit {
                replace(0, length, text)
            }
        }
        lastEmittedText = text
    }

    // Observe TextFieldState changes and notify parent
    LaunchedEffect(Unit) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .debounce(500L)
            .collect { newRaw ->
                lastEmittedText = newRaw
                currentOnTextChange(newRaw)
            }
    }

    // Focus handling
    LaunchedEffect(shouldFocus) {
        if (shouldFocus) {
            delay(300)
            focusRequester.requestFocus()
            delay(100)
            keyboardController?.show()
            onFocusConsumed()
        }
    }

    CompositionLocalProvider(LocalClipboard provides rawClipboard) {
        BasicTextField(
            state = textFieldState,
            outputTransformation = FootnoteOutputTransformation,
            textStyle = textStyle,
            cursorBrush = SolidColor(textStyle.color),
            onTextLayout = { resultProvider ->
                layoutResult = resultProvider()
            },
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .focusRequester(focusRequester)
                .drawWithContent {
                    drawContent()

                    // Draw notebook lines
                    val strokeWidth = 1.dp.toPx()
                    var y = lineHeightPx
                    while (y <= size.height + (lineHeightPx / 2)) {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeWidth
                        )
                        y += lineHeightPx
                    }

                    // Draw footnote icons at OBJ_CHAR positions
                    layoutResult?.let { result ->
                        val outputText = result.layoutInput.text.text
                        for (i in outputText.indices) {
                            if (outputText[i] == OBJ_CHAR) {
                                val rect = result.getBoundingBox(i)
                                val iconSize = Size(fontSizePx, fontSizePx)
                                val offsetX = rect.left + (rect.width - iconSize.width) / 2
                                val offsetY = rect.top + (rect.height - iconSize.height) / 2
                                translate(left = offsetX, top = offsetY) {
                                    with(iconPainter) {
                                        draw(size = iconSize, colorFilter = iconColorFilter)
                                    }
                                }
                            }
                        }
                    }
                }
        )
    }
}

/**
 * Clipboard wrapper that intercepts copy/cut operations to preserve raw USFM
 * footnote blocks instead of the visual placeholder characters.
 */
private class RawUsfmClipboard(
    private val delegate: Clipboard,
    private val textFieldState: TextFieldState
) : Clipboard {

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        if (clipEntry == null) {
            delegate.setClipEntry(null)
            return
        }
        val clipData = clipEntry.clipData
        if (clipData.itemCount > 0) {
            val visualText = clipData.getItemAt(0).text?.toString() ?: ""
            if (OBJ_CHAR in visualText) {
                val rawText = textFieldState.text.toString()
                val reconstructed = visualToRaw(visualText, rawText)
                val newClipData = ClipData.newPlainText(
                    clipData.description.label,
                    reconstructed
                )
                delegate.setClipEntry(newClipData.toClipEntry())
                return
            }
        }
        delegate.setClipEntry(clipEntry)
    }

    override suspend fun getClipEntry(): ClipEntry? = delegate.getClipEntry()

    override val nativeClipboard get() = delegate.nativeClipboard
}
