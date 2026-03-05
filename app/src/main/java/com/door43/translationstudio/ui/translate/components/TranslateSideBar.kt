package com.door43.translationstudio.ui.translate.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class TranslateSideBarAction(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateSideBar(
    showMergeConflict: Boolean,
    onReadClick: () -> Unit,
    onChunkClick: () -> Unit,
    onReviewClick: () -> Unit,
    onMergeConflictClick: () -> Unit,
    onSliderValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    sliderValue: Float = 0f,
    actions: List<TranslateSideBarAction>
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(56.dp)
            .background(MaterialTheme.colorScheme.primary),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(onClick = onReadClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Subject,
                    contentDescription = "Read Mode"
                )
            }
            IconButton(onClick = onChunkClick) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Chunk Mode"
                )
            }
            IconButton(onClick = onReviewClick) {
                Icon(
                    imageVector = Icons.Default.ViewWeek,
                    contentDescription = "Review Mode"
                )
            }
            if (showMergeConflict) {
                IconButton(onClick = onMergeConflictClick) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Merge Conflict Warning"
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            VerticalSeekBar(
                sliderValue = sliderValue,
                onSliderValueChange = onSliderValueChange
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = Color.White
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(text = action.title) },
                        leadingIcon = {
                            Icon(imageVector = action.icon, contentDescription = action.title)
                        },
                        onClick = {
                            showMenu = false
                            action.onClick()
                        }
                    )
                }
            }
        }
    }
}