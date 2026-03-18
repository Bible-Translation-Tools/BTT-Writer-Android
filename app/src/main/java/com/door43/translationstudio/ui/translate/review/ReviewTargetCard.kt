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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
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

private val VersePinColor = Color(0xFF00A56C)

@Composable
fun ReviewTargetCard(
    item: ReviewItem,
    typography: Typography,
    modifier: Modifier = Modifier
) {
    val titleStyle = typography.getComposeTextStyle(
        translationType = TranslationType.TARGET,
        style = TextStyleType.SUB,
        languageCode = item.chunk.target.targetLanguage.slug,
        direction = item.chunk.target.targetLanguage.direction
    )

    val bodyStyle = typography.getComposeTextStyle(
        translationType = TranslationType.TARGET,
        style = TextStyleType.NORMAL,
        languageCode = item.chunk.target.targetLanguage.slug,
        direction = item.chunk.target.targetLanguage.direction
    )

    val showUndo = false
    val showRedo = false
    val showAddNote = false
    val isEditing = false
    val isDone = false

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
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom
            )
        ) { verseLabel ->
            val pinFontSize = bodyStyle.fontSize / when {
                verseLabel.length >= 7 -> 4.5
                verseLabel.length >= 5 -> 3.5
                verseLabel.length >= 3 -> 2.8
                else -> 1.6
            }
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_verse_black_48dp),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(VersePinColor),
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = verseLabel,
                    fontSize = pinFontSize,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 24.dp)
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
                    if (showUndo) {
                        IconButton(onClick = /*onUndoClick*/{}) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_undo_secondary_24dp),
                                contentDescription = "Undo",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (showRedo) {
                        IconButton(onClick = /*onRedoClick*/{}) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_redo_secondary_24dp),
                                contentDescription = "Redo",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (showAddNote) {
                        IconButton(onClick = /*onAddNoteClick*/{}) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_note_add_secondary_24dp),
                                contentDescription = "Add note",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Text(
                        text = item.targetTitle,
                        style = titleStyle,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    IconButton(onClick = /*onEditClick*/{}) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit translation",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isEditing) {
                    BasicTextField(
                        value = item.targetText,
                        onValueChange = /*onBodyTextChange*/{},
                        textStyle = bodyStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                } else {
                    Text(
                        text = item.renderedTargetText,
                        inlineContent = inlineContentMap,
                        style = bodyStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }
            }

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
                    checked = isDone,
                    onCheckedChange = /*onDoneChanged*/{}
                )
            }
        }
    }
}