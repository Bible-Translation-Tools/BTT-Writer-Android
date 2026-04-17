package com.door43.translationstudio.ui.translate.chunk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.getComposeTextStyle
import com.door43.translationstudio.ui.dialogs.source.SourceTabItem
import com.door43.translationstudio.ui.translate.components.SourceTabRow
import org.unfoldingword.resourcecontainer.ResourceContainer

@Composable
fun ChunkSourceCard(
    title: String,
    text: AnnotatedString,
    sourceTabs: List<SourceTabItem>,
    typography: Typography,
    selectedSource: ResourceContainer?,
    onSourceTabClick: (String) -> Unit,
    onAddNewSourceClick: () -> Unit,
    onRemoveSourceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val titleStyle = typography.getComposeTextStyle(
        translationType = TranslationType.SOURCE,
        style = TextStyleType.SUB,
        languageCode = selectedSource?.language?.slug,
        direction = selectedSource?.language?.direction
    )

    val bodyStyle = typography.getComposeTextStyle(
        translationType = TranslationType.SOURCE,
        style = TextStyleType.NORMAL,
        languageCode = selectedSource?.language?.slug,
        direction = selectedSource?.language?.direction
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )

    Card(
        modifier = modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceTabRow(
                    sourceTabs = sourceTabs,
                    selectedTag = selectedSource?.slug,
                    onSourceTabClick = onSourceTabClick,
                    onAddClick = onAddNewSourceClick,
                    onRemoveClick = onRemoveSourceClick
                )
            }

            Text(
                text = title,
                style = titleStyle,
                modifier = Modifier.align(Alignment.End)
                    .padding(end = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = text,
                inlineContent = inlineContentMap,
                style = bodyStyle,
                modifier = Modifier.fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}