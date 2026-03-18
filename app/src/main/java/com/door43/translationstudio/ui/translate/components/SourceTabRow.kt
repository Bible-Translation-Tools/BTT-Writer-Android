package com.door43.translationstudio.ui.translate.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.ui.translate.dialogs.MAX_SOURCE_ITEMS
import com.door43.translationstudio.ui.translate.dialogs.SourceTabItem

@Composable
fun SourceTabRow(
    sourceTabs: List<SourceTabItem>,
    selectedTag: String?,
    onSourceTabClick: (String) -> Unit,
    onRemoveClick: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = remember(sourceTabs, selectedTag) {
        val index = sourceTabs.indexOfFirst { it.tag == selectedTag }
        if (index == -1) 0 else index
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        sourceTabs.forEachIndexed { index, tab ->
            val isSelected = (index == selectedIndex)
            val color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else MaterialTheme.colorScheme.onSurfaceVariant

            key(tab.tag) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(),
                            onClick = { onSourceTabClick(tab.tag) }
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = color,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tab",
                            tint = color,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { onRemoveClick(tab.tag) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .height(2.dp)
                            .fillMaxWidth()
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (sourceTabs.size < MAX_SOURCE_ITEMS) {
            IconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Default.LibraryAdd,
                    contentDescription = "Add Source",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}