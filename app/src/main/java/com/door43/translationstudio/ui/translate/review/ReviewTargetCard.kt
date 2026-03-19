package com.door43.translationstudio.ui.translate.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.getComposeTextStyle
import com.door43.translationstudio.ui.translate.ReviewItem
import com.door43.translationstudio.ui.translate.components.UsfmEditText

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
    onAddNoteClick: (caretPosition: Int) -> Unit
) {
    val currentItem by rememberUpdatedState(item)
    var cursorPosition by remember { mutableIntStateOf(0) }

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                } else {
                    Text(
                        text = currentItem.renderedTargetText,
                        inlineContent = inlineContentMap,
                        style = bodyStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
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