package com.door43.translationstudio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.ui.viewmodels.SourceTabItem

@Composable
fun SourceTabRow(
    sourceTabs: List<SourceTabItem>,
    selectedTag: String?,
    onSourceTabClick: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = remember(sourceTabs, selectedTag) {
        val index = sourceTabs.indexOfFirst { it.tag == selectedTag }
        if (index == -1) 0 else index
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sourceTabs.forEachIndexed { index, tab ->
                val isSelected = (index == selectedIndex)

                key(tab.tag) {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = { onSourceTabClick(tab.tag) }
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )

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
        }

        if (sourceTabs.size < 3) {
            IconButton(
                onClick = onAddClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryAdd,
                    contentDescription = "Add Source",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}