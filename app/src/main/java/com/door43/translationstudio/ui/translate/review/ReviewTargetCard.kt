package com.door43.translationstudio.ui.translate.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
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
import com.door43.translationstudio.ui.translate.components.withSearchHighlight

private const val VERSE_MARKER_TAG = "VERSE_MARKER"
private const val RAW_POSITION_TAG = "RAW_POSITION"

private data class DragContext(
    val machineReadable: String,
    val verseRawStart: Int,
    val verseRawEnd: Int,
    val annotStart: Int,
    val annotEnd: Int,
    val label: String,
    val renderedText: AnnotatedString
)

@Composable
fun ReviewTargetCard(
    item: ReviewItem,
    typography: Typography,
    modifier: Modifier = Modifier,
    searchQuery: String? = null,
    onEditToggle: () -> Unit,
    onDoneToggle: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onAddNoteClick: (caretPosition: Int) -> Unit,
    onDragDropVerse: (
        machineReadable: String,
        verseRawStart: Int,
        verseRawEnd: Int,
        targetRawPosition: Int
    ) -> Unit,
) {
    val currentItem by rememberUpdatedState(item)
    var cursorPosition by remember { mutableIntStateOf(0) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var highlightWordRange by remember { mutableStateOf<IntRange?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var dragContext by remember { mutableStateOf<DragContext?>(null) }
    var insertionOffset by remember { mutableStateOf<Int?>(null) }

    val clearDrag: () -> Unit = {
        dragContext = null
        dragPosition = null
        insertionOffset = null
        highlightWordRange = null
    }

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
            val isGhost = dragContext?.label == verseLabel
            VersePin(
                label = verseLabel,
                bodyStyle = bodyStyle,
                isGhost = isGhost
            )
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
            modifier = Modifier.fillMaxSize()
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
                        onTextChange = onTextChange,
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
                        val highlightColor = MaterialTheme.colorScheme.primary
                        val onHighlightColor = MaterialTheme.colorScheme.onPrimary

                        val displayText = remember(
                            currentItem.renderedTargetText,
                            highlightWordRange,
                            searchQuery
                        ) {
                            val base = currentItem.renderedTargetText
                            val withWord = highlightWordRange?.let { wordRange ->
                                buildAnnotatedString {
                                    append(base)
                                    addStyle(
                                        SpanStyle(
                                            color = onHighlightColor,
                                            background = highlightColor
                                        ),
                                        wordRange.first.coerceAtMost(length),
                                        wordRange.last.coerceAtMost(length)
                                    )
                                }
                            } ?: base
                            withWord.withSearchHighlight(searchQuery)
                        }

                        Text(
                            text = displayText,
                            inlineContent = inlineContentMap,
                            style = bodyStyle,
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(currentItem.renderedTargetText, currentItem.targetMode) {
                                    if (currentItem.targetMode != TargetMode.MARKER) return@pointerInput

                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            val layout = textLayoutResult ?: return@detectDragGesturesAfterLongPress
                                            val renderedText = currentItem.renderedTargetText
                                            val charOffset = layout.getOffsetForPosition(offset)

                                            val ctx = buildDragContext(renderedText, charOffset)
                                                ?: return@detectDragGesturesAfterLongPress

                                            dragContext = ctx
                                            dragPosition = offset
                                        },

                                        onDrag = { change, _ ->
                                            change.consume()
                                            val layout = textLayoutResult ?: return@detectDragGesturesAfterLongPress
                                            val text = currentItem.renderedTargetText.text

                                            dragPosition = change.position
                                            val snapped = snapToClosestOffset(layout, text, change.position)
                                            insertionOffset = snapped
                                            highlightWordRange = wordRangeFromOffset(text, snapped)
                                        },

                                        onDragEnd = {
                                            val ctx = dragContext
                                            val displayOffset = insertionOffset
                                            if (ctx != null && displayOffset != null) {
                                                val target = computeTargetRawPosition(ctx, displayOffset)
                                                if (target != null) {
                                                    onDragDropVerse(
                                                        ctx.machineReadable,
                                                        ctx.verseRawStart,
                                                        ctx.verseRawEnd,
                                                        target
                                                    )
                                                }
                                            }
                                            clearDrag()
                                        },

                                        onDragCancel = { clearDrag() }
                                    )
                                }
                        )

                        val floatPos = dragPosition
                        val floatLabel = dragContext?.label
                        if (floatPos != null && floatLabel != null) {
                            val pinSize = bodyStyle.fontSize * 2.0
                            val pinSizeDp = with(LocalDensity.current) { pinSize.toDp() }

                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (floatPos.x - pinSizeDp.toPx() / 2).toInt(),
                                            (floatPos.y - pinSizeDp.toPx() * 1.5f).toInt()
                                        )
                                    }
                                    .size(pinSizeDp)
                            ) {
                                VersePin(
                                    label = floatLabel,
                                    bodyStyle = bodyStyle,
                                    modifier = Modifier.fillMaxSize()
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

@Composable
fun VersePin(
    label: String,
    bodyStyle: TextStyle,
    modifier: Modifier = Modifier,
    isGhost: Boolean = false
) {
    val pinFontSize = bodyStyle.fontSize / when {
        label.length >= 7 -> 4.5
        label.length >= 5 -> 3.5
        label.length >= 3 -> 2.8
        else -> 1.6
    }
    val alpha = if (isGhost) 0.3f else 1f

    Box(
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        contentAlignment = BiasAlignment(0f, -0.35f)
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_verse_black_48dp),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = label,
            fontSize = pinFontSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun buildDragContext(renderedText: AnnotatedString, charOffset: Int): DragContext? {
    val verseAnnotation = renderedText.getStringAnnotations(
        VERSE_MARKER_TAG,
        maxOf(0, charOffset - 1),
        minOf(charOffset + 2, renderedText.length)
    ).firstOrNull() ?: return null

    val verseParts = verseAnnotation.item.split("|", limit = 3)
    val startVerse = verseParts.getOrNull(0)?.toIntOrNull() ?: return null
    val endVerse = verseParts.getOrNull(1)?.toIntOrNull() ?: 0
    val machineReadable = verseParts.getOrNull(2).orEmpty()

    val rawParts = renderedText.getStringAnnotations(
        RAW_POSITION_TAG,
        verseAnnotation.start,
        verseAnnotation.end
    ).firstOrNull()?.item?.split("|") ?: return null

    val verseRawStart = rawParts.getOrNull(0)?.toIntOrNull() ?: return null
    val verseRawEnd = rawParts.getOrNull(1)?.toIntOrNull() ?: return null

    return DragContext(
        machineReadable = machineReadable,
        verseRawStart = verseRawStart,
        verseRawEnd = verseRawEnd,
        annotStart = verseAnnotation.start,
        annotEnd = verseAnnotation.end,
        label = formatVerseLabel(startVerse, endVerse),
        renderedText = renderedText
    )
}

private fun computeTargetRawPosition(ctx: DragContext, displayOffset: Int): Int? {
    val pinLen = ctx.annotEnd - ctx.annotStart
    val originalOffset = if (displayOffset >= ctx.annotStart) displayOffset + pinLen else displayOffset

    val targetRawAnnotation = ctx.renderedText.getStringAnnotations(
        RAW_POSITION_TAG,
        originalOffset,
        (originalOffset + 1).coerceAtMost(ctx.renderedText.length)
    ).firstOrNull()

    if (targetRawAnnotation != null) {
        val parts = targetRawAnnotation.item.split("|")
        val rawStart = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val rawEnd = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val annotationLen = targetRawAnnotation.end - targetRawAnnotation.start
        return if (annotationLen <= 1) {
            rawStart
        } else {
            val offsetInAnnotation = originalOffset - targetRawAnnotation.start
            rawStart + ((rawEnd - rawStart) * offsetInAnnotation / annotationLen)
        }
    }

    return ctx.renderedText
        .getStringAnnotations(RAW_POSITION_TAG, 0, originalOffset)
        .lastOrNull()
        ?.item?.split("|")?.getOrNull(1)?.toIntOrNull()
        ?: 0
}

private fun formatVerseLabel(startVerse: Int, endVerse: Int): String =
    if (endVerse > 0) "$startVerse-$endVerse" else "$startVerse"

private fun snapToClosestOffset(
    layout: TextLayoutResult,
    text: String,
    position: Offset
): Int {
    val targetLine = layout.getLineForVerticalPosition(position.y)
    val start = layout.getLineStart(targetLine)
    val end = minOf(layout.getLineEnd(targetLine, visibleEnd = true), text.length)

    return (start..end).minByOrNull { offset ->
        val dx = position.x - safeBoundingBox(layout, offset).left
        dx * dx
    } ?: start
}

private fun safeBoundingBox(layout: TextLayoutResult, offset: Int): Rect {
    val lastIndex = layout.layoutInput.text.text.length - 1
    return when {
        offset <= 0 -> layout.getBoundingBox(0)
        offset > lastIndex -> {
            val r = layout.getBoundingBox(lastIndex)
            Rect(r.right, r.top, r.right, r.bottom)
        }
        else -> layout.getBoundingBox(offset)
    }
}

private fun wordRangeFromOffset(text: String, offset: Int): IntRange? {
    if (offset < 0 || offset >= text.length) return null
    if (text[offset].isWhitespace()) return null

    var start = offset
    while (start > 0 && !text[start - 1].isWhitespace()) start--

    var end = offset
    while (end < text.length && !text[end].isWhitespace()) end++

    return start..end
}
