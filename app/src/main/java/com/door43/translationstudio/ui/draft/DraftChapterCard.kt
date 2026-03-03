package com.door43.translationstudio.ui.draft

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.getComposeTextStyle
import com.door43.translationstudio.rendering.adapter.ComposeTextAdapter
import com.door43.translationstudio.ui.viewmodels.ChapterContent
import org.unfoldingword.door43client.models.SourceLanguage

@Composable
fun DraftChapterCard(
    chapterContent: ChapterContent?,
    language: SourceLanguage,
    typography: Typography,
    onNoteClick: (String) -> Unit = {}
) {
    val titleStyle = typography.getComposeTextStyle(
        translationType = TranslationType.SOURCE,
        style = TextStyleType.TITLE,
        languageCode = language.slug,
        direction = language.direction
    )

    val bodyStyle = typography.getComposeTextStyle(
        translationType = TranslationType.SOURCE,
        style = TextStyleType.NORMAL,
        languageCode = language.slug,
        direction = language.direction
    )

    val inlineIconMap = mapOf(
        "footnote_icon" to InlineTextContent(
            Placeholder(
                width = 1.2.em,
                height = 1.2.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = "Footnote",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val textColor = MaterialTheme.colorScheme.onSurface

            chapterContent?.let { content ->

                if (content.heading.isNotEmpty()) {
                    Text(
                        text = content.heading,
                        style = titleStyle,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (content.title.isNotEmpty()) {
                    Text(
                        text = content.title,
                        style = titleStyle,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (content.textNodes.isNotEmpty()) {
                    val annotatedBody = remember(content.textNodes) {
                        ComposeTextAdapter.convert(
                            content.textNodes,
                            onNoteClick = onNoteClick
                        )
                    }

                    Text(
                        text = annotatedBody,
                        style = bodyStyle,
                        color = textColor,
                        modifier = Modifier.fillMaxWidth(),
                        inlineContent = inlineIconMap
                    )
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}