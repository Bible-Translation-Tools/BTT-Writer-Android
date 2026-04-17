package com.door43.translationstudio.ui.translate.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.door43.translationstudio.ui.dialogs.source.RCItem

@Composable
fun SourceHeaderRow(title: String, showStatusIcons: Boolean) {
    val inlineContent = mapOf(
        "refresh" to InlineTextContent(
            Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.Center)
        ) {
            Icon(
                Icons.Default.Refresh,
                "refresh",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        "wifi" to InlineTextContent(
            Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.Center)
        ) {
            Icon(
                Icons.Default.Wifi,
                "internet",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )

    val styledTitle = buildAnnotatedString {
        append(title.uppercase())
        if (showStatusIcons) {
            append("    ")
            appendInlineContent("refresh", "[refresh]")
            append(" REQUIRES INTERNET ")
            appendInlineContent("wifi", "[wifi]")
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = styledTitle,
            inlineContent = inlineContent,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SourceItemRow(
    item: RCItem,
    onTriggerSelected: (RCItem) -> Unit,
    onTriggerDownload: (RCItem) -> Unit,
    onTriggerDelete: (RCItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (!item.downloaded) {
                        onTriggerDownload(item)
                    } else {
                        onTriggerSelected(item)
                    }
                },
                onLongClick = { onTriggerDelete(item) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )

        if (!item.downloaded || item.hasUpdates) {
            Icon(
                imageVector = if (!item.downloaded) {
                    Icons.Default.FileDownload
                } else Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
                    .clickable {
                        if (!item.downloaded || item.hasUpdates) {
                            onTriggerDownload(item)
                        }
                    }
            )
        }

        if (item.downloaded) {
            Spacer(modifier = Modifier.width(16.dp))
            Icon(
                imageVector = if (item.selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}