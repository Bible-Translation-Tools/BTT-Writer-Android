package com.door43.translationstudio.ui.translate.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.ui.components.SidebarAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateSidebar(
    currentViewMode: TranslationViewMode,
    onReadClick: () -> Unit,
    onChunkClick: () -> Unit,
    onReviewClick: () -> Unit,
    onMergeConflictClick: () -> Unit,
    onSliderValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showMergeConflict: Boolean,
    sliderValue: Float = 0f,
    chapterLabel: String? = null,
    mergeConflictFilterOn: Boolean = false,
    actions: List<SidebarAction>
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
            SidebarIconButton(
                isSelected = currentViewMode == TranslationViewMode.READ,
                icon = Icons.AutoMirrored.Filled.Subject,
                contentDescription = "Read Mode",
                onClick = onReadClick
            )
            SidebarIconButton(
                isSelected = currentViewMode == TranslationViewMode.CHUNK,
                icon = Icons.Default.ContentCopy,
                contentDescription = "Chunk Mode",
                onClick = onChunkClick
            )
            SidebarIconButton(
                isSelected = currentViewMode == TranslationViewMode.REVIEW
                        && !mergeConflictFilterOn,
                icon = Icons.Default.ViewWeek,
                contentDescription = "Review Mode",
                onClick = onReviewClick
            )
            if (showMergeConflict) {
                SidebarIconButton(
                    isSelected = mergeConflictFilterOn,
                    icon = Icons.Default.Warning,
                    contentDescription = "Merge Conflict Warning",
                    onClick = onMergeConflictClick
                )
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
                onSliderValueChange = onSliderValueChange,
                tooltipLabel = chapterLabel
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
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.title,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
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

@Composable
private fun SidebarIconButton(
    isSelected: Boolean,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color.Black.copy(alpha = 0.2f) else Color.Transparent
    val iconTint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(backgroundColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint
        )
    }
}