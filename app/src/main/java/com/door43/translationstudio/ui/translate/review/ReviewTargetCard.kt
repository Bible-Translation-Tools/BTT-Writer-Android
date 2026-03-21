package com.door43.translationstudio.ui.translate.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.getComposeTextStyle
import com.door43.translationstudio.ui.translate.ReviewItem
import com.door43.translationstudio.ui.translate.components.UsfmEditText
import com.door43.translationstudio.ui.translate.components.footnote.NOTE_CHAR

@Composable
fun ReviewTargetCard(
    item: ReviewItem,
    typography: Typography,
    modifier: Modifier = Modifier,
    onEditToggle: () -> Unit,
    onDoneToggle: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onAddNoteClick: (caretPosition: Int) -> Unit,
    onDragDropVerse: (machineReadable: String, verseRawStart: Int, verseRawEnd: Int, targetRawPosition: Int) -> Unit = { _, _, _, _ -> },
    searchQuery: String? = null
) {
    val currentItem by rememberUpdatedState(item)
    var cursorPosition by remember { mutableIntStateOf(0) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var highlightWordRange by remember { mutableStateOf<IntRange?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var dragVerseLabel by remember { mutableStateOf<String?>(null) }
    var dragPinRange by remember { mutableStateOf<IntRange?>(null) }

    val titleStyle = typography.getComposeTextStyle(
        translationType = TranslationType.TARGET,
        style = TextStyleType.SUB,
        languageCode = currentItem.chunk.target.targetLanguage.slug,
        direction = currentItem.chunk.target.targetLanguage.direction
    )

    val bodyStyle = typography.getComposeTextStyle(
        translationType = TranslationType.TARGET,
        style = TextStyleType.NORMAL,
        languageCode = currentItem.chunk.target.targetLanguage.slug,
        direction = currentItem.chunk.target.targetLanguage.direction
    )

    val inlineContentMap = mapOf(
        "note_icon" to InlineTextContent(
            Placeholder(
                width = bodyStyle.fontSize,
                height = bodyStyle.fontSize,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = "Note",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize()
            )
        },
        "verse_pin" to InlineTextContent(
            Placeholder(
                width = bodyStyle.fontSize * 2.0,
                height = bodyStyle.fontSize * 2.0,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) { verseLabel ->
            val pinFontSize = bodyStyle.fontSize / when {
                verseLabel.length >= 7 -> 4.5
                verseLabel.length >= 5 -> 3.5
                verseLabel.length >= 3 -> 2.8
                else -> 1.6
            }
            Box(
                contentAlignment = BiasAlignment(
                    horizontalBias = 0f,
                    verticalBias = -0.35f
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_verse_black_48dp),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = verseLabel,
                    fontSize = pinFontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    )

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentItem.targetMode == TargetMode.EDIT) {

                        if (currentItem.fileHistory?.hasPrevious == true) {
                            IconButton(onClick = onUndoClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (currentItem.fileHistory?.hasNext == true) {
                            IconButton(onClick = onRedoClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { onAddNoteClick(cursorPosition) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                contentDescription = "Add note",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = currentItem.targetTitle,
                        style = titleStyle,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    if (currentItem.targetMode != TargetMode.COMPLETE) {
                        IconButton(onClick = onEditToggle) {
                            Icon(
                                imageVector = if (currentItem.targetMode == TargetMode.EDIT) {
                                    Icons.Default.Check
                                } else Icons.Default.Edit,
                                contentDescription = "toggle edit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (currentItem.targetMode == TargetMode.EDIT) {
                    UsfmEditText(
                        text = currentItem.targetText,
                        shouldFocus = true,
                        textStyle = bodyStyle,
                        onTextChange = {
                            onTextChange(it)
                        },
                        onCursorPositionChange = { cursorPosition = it },
                        searchQuery = searchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        val secondaryColor = MaterialTheme.colorScheme.secondary
                        val onSecondaryColor = MaterialTheme.colorScheme.onSecondary

                        // Strip the dragged verse pin and apply word highlight
                        val displayText = remember(
                            currentItem.renderedTargetText,
                            dragPinRange,
                            highlightWordRange
                        ) {
                            val base = dragPinRange?.let { range ->
                                buildAnnotatedString {
                                    append(
                                        currentItem.renderedTargetText,
                                        0,
                                        range.first
                                    )
                                    append(
                                        currentItem.renderedTargetText,
                                        range.last + 1,
                                        currentItem.renderedTargetText.length
                                    )
                                }
                            } ?: currentItem.renderedTargetText

                            highlightWordRange?.let { wordRange ->
                                buildAnnotatedString {
                                    append(base)
                                    addStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            color = onSecondaryColor,
                                            background = secondaryColor
                                        ),
                                        wordRange.first.coerceAtMost(base.length),
                                        wordRange.last.coerceAtMost(base.length)
                                    )
                                }
                            } ?: base
                        }

                        Text(
                            text = displayText,
                            inlineContent = inlineContentMap,
                            style = bodyStyle,
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (currentItem.targetMode == TargetMode.MARKER) {
                                        Modifier.pointerInput(Unit) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)

                                                // Check if long-press is on a verse pin
                                                val layout = textLayoutResult ?: return@awaitEachGesture
                                                val charOffset = layout.getOffsetForPosition(down.position)
                                                val renderedText = currentItem.renderedTargetText
                                                val textLen = renderedText.length
                                                val verseAnnotations = renderedText
                                                    .getStringAnnotations(
                                                        "VERSE_MARKER",
                                                        maxOf(0, charOffset - 1),
                                                        minOf(charOffset + 2, textLen)
                                                    )
                                                // Not on a verse pin — don't consume, let note clicks through
                                                if (verseAnnotations.isEmpty()) return@awaitEachGesture

                                                // Wait for long press
                                                val longPress = awaitLongPressOrCancellation(down.id)
                                                    ?: return@awaitEachGesture

                                                // Pick the annotation whose pixel center is closest to tap
                                                val verseAnnot = verseAnnotations.minBy { annot ->
                                                    val startRect = layout.getBoundingBox(annot.start)
                                                    val endRect = layout.getBoundingBox((annot.end - 1).coerceAtLeast(annot.start))
                                                    val centerX = (startRect.left + endRect.right) / 2f
                                                    val centerY = (startRect.top + endRect.bottom) / 2f
                                                    val dx = down.position.x - centerX
                                                    val dy = down.position.y - centerY
                                                    dx * dx + dy * dy
                                                }
                                                val parts = verseAnnot.item.split("|", limit = 3)
                                                val startVerse = parts[0].toIntOrNull() ?: return@awaitEachGesture
                                                val endVerse = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                                val machineReadable = parts.getOrNull(2) ?: ""

                                                // Get verse's raw source-text position
                                                val verseRawAnnot = renderedText
                                                    .getStringAnnotations("RAW_POSITION", verseAnnot.start, verseAnnot.end)
                                                    .firstOrNull()
                                                val verseRawStart: Int
                                                val verseRawEnd: Int
                                                if (verseRawAnnot != null) {
                                                    val rawParts = verseRawAnnot.item.split("|")
                                                    verseRawStart = rawParts[0].toIntOrNull() ?: return@awaitEachGesture
                                                    verseRawEnd = rawParts[1].toIntOrNull() ?: return@awaitEachGesture
                                                } else return@awaitEachGesture

                                                // Enter drag mode — track finger position
                                                var currentPosition = longPress.position

                                                // Show floating verse pin & hide original
                                                val verseLabel = if (endVerse > 0) "$startVerse-$endVerse" else "$startVerse"
                                                dragVerseLabel = verseLabel
                                                dragPinRange = verseAnnot.start..<verseAnnot.end
                                                dragPosition = currentPosition

                                                val strippedText = renderedText.text
                                                    .removeRange(verseAnnot.start, verseAnnot.end)
                                                val pinLen = verseAnnot.end - verseAnnot.start

                                                // Track drag until release
                                                do {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull() ?: break
                                                    currentPosition = change.position
                                                    change.consume()

                                                    dragPosition = currentPosition
                                                    highlightWordRange = textLayoutResult?.let { tl ->
                                                        wordRangeAt(tl, strippedText, currentPosition)
                                                    }
                                                } while (event.changes.any { it.pressed })

                                                // Finger lifted — place verse
                                                highlightWordRange = null
                                                dragPosition = null
                                                dragVerseLabel = null
                                                dragPinRange = null
                                                val displayOffset = textLayoutResult
                                                    ?.getOffsetForPosition(currentPosition)
                                                    ?: return@awaitEachGesture

                                                // Map display offset back to original annotated string offset
                                                val originalOffset = if (displayOffset >= verseAnnot.start) {
                                                    displayOffset + pinLen
                                                } else displayOffset

                                                // Find RAW_POSITION at the drop point
                                                val targetRawAnnot = renderedText
                                                    .getStringAnnotations(
                                                        "RAW_POSITION",
                                                        originalOffset,
                                                        (originalOffset + 1).coerceAtMost(textLen)
                                                    )
                                                    .firstOrNull()

                                                val targetRawPosition = if (targetRawAnnot != null) {
                                                    val rawParts = targetRawAnnot.item.split("|")
                                                    val rawStart = rawParts[0].toIntOrNull() ?: return@awaitEachGesture
                                                    val rawEnd = rawParts[1].toIntOrNull() ?: return@awaitEachGesture
                                                    val annotLen = targetRawAnnot.end - targetRawAnnot.start
                                                    if (annotLen <= 1) {
                                                        // Inline content (note/verse pin) — insert before it
                                                        rawStart
                                                    } else {
                                                        // Text node — map proportionally within the node
                                                        val offsetInAnnot = originalOffset - targetRawAnnot.start
                                                        rawStart + ((rawEnd - rawStart) * offsetInAnnot / annotLen)
                                                    }
                                                } else {
                                                    // No annotation at drop point — insert after nearest preceding entity
                                                    val before = renderedText
                                                        .getStringAnnotations("RAW_POSITION", 0, originalOffset)
                                                        .lastOrNull()
                                                    if (before != null) {
                                                        before.item.split("|")[1].toIntOrNull() ?: return@awaitEachGesture
                                                    } else 0
                                                }

                                                onDragDropVerse(
                                                    machineReadable,
                                                    verseRawStart,
                                                    verseRawEnd,
                                                    targetRawPosition
                                                )
                                            }
                                        }
                                    } else Modifier
                                )
                        )

                        // Floating verse pin following finger
                        val floatPos = dragPosition
                        val floatLabel = dragVerseLabel
                        if (floatPos != null && floatLabel != null) {
                            val pinSize = bodyStyle.fontSize * 2.0
                            val pinSizeDp = with(LocalDensity.current) { pinSize.toDp() }
                            Box(
                                contentAlignment = BiasAlignment(
                                    horizontalBias = 0f,
                                    verticalBias = -0.35f
                                ),
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (floatPos.x - pinSizeDp.toPx() / 2).toInt(),
                                            (floatPos.y - pinSizeDp.toPx() * 1.5f).toInt()
                                        )
                                    }
                                    .size(pinSizeDp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_verse_black_48dp),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.fillMaxSize()
                                )
                                val pinFontSize = bodyStyle.fontSize / when {
                                    floatLabel.length >= 7 -> 4.5
                                    floatLabel.length >= 5 -> 3.5
                                    floatLabel.length >= 3 -> 2.8
                                    else -> 1.6
                                }
                                Text(
                                    text = floatLabel,
                                    fontSize = pinFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }

                    }
                }
            }

            if (currentItem.targetMode != TargetMode.EDIT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.mark_done),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = currentItem.targetMode == TargetMode.COMPLETE,
                        onCheckedChange = onDoneToggle
                    )
                }
            }
        }
    }
}

/**
 * Find the character range of the word under the finger position.
 * Walks outward from the character at [position] to find word boundaries.
 * Returns an IntRange suitable for applying a SpanStyle highlight.
 */
private fun wordRangeAt(layout: TextLayoutResult, text: String, position: Offset): IntRange? {
    val charOffset = layout.getOffsetForPosition(position)
    if (charOffset < 0 || charOffset >= text.length) return null

    val ch = text[charOffset]
    // Skip whitespace
    if (ch.isWhitespace()) return null

    // Inline content (footnote icon) — single-char range
    if (ch == NOTE_CHAR) return charOffset..charOffset + 1

    // Find word start
    var wordStart = charOffset
    while (wordStart > 0 && !text[wordStart - 1].isWhitespace() && text[wordStart - 1] != NOTE_CHAR) {
        wordStart--
    }

    // Find word end
    var wordEnd = charOffset
    while (wordEnd < text.length && !text[wordEnd].isWhitespace() && text[wordEnd] != NOTE_CHAR) {
        wordEnd++
    }

    if (wordStart >= wordEnd) return null

    return wordStart..wordEnd
}