package com.door43.translationstudio.ui.translate.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.door43.translationstudio.ui.viewmodels.RCItem

@Composable
fun SourceHeaderRow(title: CharSequence) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title.toString(), // If using buildAnnotatedString, pass that directly
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SourceItemRow(
    item: RCItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.title.toString(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )

        if (!item.downloaded || item.hasUpdates) {
            Icon(
                imageVector = if (!item.downloaded) Icons.Default.FileDownload else Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
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

@Composable
fun rememberHeaderTitle(text: String, internetText: String, showStatusIcons: Boolean): AnnotatedString {
    return remember(text, showStatusIcons) {
        buildAnnotatedString {
            append(text)
            if (showStatusIcons) {
                append("  ")
                appendInlineContent("refresh", "[refresh]")
                append(" ")
                append(internetText)
                append(" ")
                appendInlineContent("wifi", "[wifi]")
            }
        }
    }
}

val inlineContent = mapOf(
    "refresh" to InlineTextContent(
        Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.Center)
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
    },
    "wifi" to InlineTextContent(
        Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.Center)
    ) {
        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
    }
)