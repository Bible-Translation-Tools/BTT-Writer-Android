package com.door43.translationstudio.ui.translate.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.getComposeTextStyle
import com.door43.translationstudio.ui.translate.ReviewItem

@Composable
fun MergeConflictCard(
    item: ReviewItem,
    typography: Typography,
    modifier: Modifier = Modifier,
    searchQuery: String? = null,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onConfirmClick: (Int) -> Unit
) {
    val currentItem by rememberUpdatedState(item)
    var selectedIndex by remember { mutableIntStateOf(-1) }

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

                    Text(
                        text = currentItem.targetTitle,
                        style = titleStyle,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                item.mergeItems.forEachIndexed { index, conflict ->
                    val bgColor = if (index % 2 == 1) {
                        MaterialTheme.colorScheme.tertiary
                    } else MaterialTheme.colorScheme.tertiaryContainer

                    val fontWeight = if (selectedIndex == index) {
                        FontWeight.Bold
                    } else FontWeight.Normal

                    val fontSize = if (selectedIndex == index || selectedIndex < 0) {
                        bodyStyle.fontSize
                    } else bodyStyle.fontSize / 1.2

                    val displayText = remember(conflict, searchQuery) {
                        if (searchQuery.isNullOrBlank()) {
                            AnnotatedString(conflict.toString())
                        } else {
                            buildAnnotatedString {
                                append(conflict.toString())
                                val query = searchQuery.lowercase()
                                val text = conflict.toString().lowercase()
                                var startIndex = 0
                                while (true) {
                                    val index = text.indexOf(query, startIndex)
                                    if (index == -1) break
                                    addStyle(
                                        SpanStyle(
                                            background = Color.Yellow,
                                            color = Color.Black
                                        ),
                                        index,
                                        index + searchQuery.length
                                    )
                                    startIndex = index + 1
                                }
                            }
                        }
                    }

                    Text(
                        displayText,
                        style = bodyStyle.copy(
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = fontWeight,
                            fontSize = fontSize
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(horizontal = 8.dp)
                            .clickable {
                                selectedIndex = index
                            }
                    )
                }

                if (selectedIndex > -1) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        TextButton(
                            onClick = { selectedIndex = -1 },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.title_cancel)
                            )
                        }
                        TextButton(
                            onClick = { onConfirmClick(selectedIndex) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(0)
                        ) {
                            Text(
                                text = stringResource(R.string.confirm)
                            )
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "warning",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.resolve_confict_instructions)
                        )
                    }
                }
            }
        }
    }
}