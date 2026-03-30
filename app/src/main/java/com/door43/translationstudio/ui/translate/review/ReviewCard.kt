package com.door43.translationstudio.ui.translate.review

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.translate.ReviewItem
import com.door43.translationstudio.ui.translate.chunk.ChunkSourceCard
import com.door43.translationstudio.ui.translate.dialogs.SourceTabItem

@Composable
fun ReviewCard(
    item: ReviewItem,
    sourceTabs: List<SourceTabItem>,
    typography: Typography,
    onSourceTabClick: (String) -> Unit,
    onAddNewSourceClick: () -> Unit,
    onRemoveSourceClick: (String) -> Unit,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    resourcesOpen: Boolean = false,
    onRenderHelps: () -> Unit = {},
    onHelpClick: (HelpItem) -> Unit,
    onEditToggle: () -> Unit,
    onDoneToggle: (Boolean) -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onAddNoteClick: (caretPosition: Int) -> Unit,
    onDragDropVerse: (
        machineReadable: String,
        verseRawStart: Int,
        verseRawEnd: Int,
        targetRawPosition: Int
    ) -> Unit = { _, _, _, _ -> },
    onExpandedChange: (Boolean) -> Unit,
    onConflictSelected: (Int) -> Unit,
    searchQuery: String? = null
) {
    val mainWeight by animateFloatAsState(
        targetValue = if (resourcesOpen) 0.333f else 0.49f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "weight"
    )
    val peekWeight by animateFloatAsState(
        targetValue = if (resourcesOpen) 0.333f else 0.02f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "peekWeight"
    )

    val endPadding by animateDpAsState(
        targetValue = if (resourcesOpen) 16.dp else 0.dp,
        label = "endPadding"
    )

    LaunchedEffect(resourcesOpen, item.chunk.source, item.helps) {
        if (resourcesOpen) onRenderHelps()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .padding(start = 16.dp)
            .padding(end = endPadding)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -100) onExpandedChange(true)
                    if (dragAmount > 100) onExpandedChange(false)
                }
            }
    ) {
        ChunkSourceCard(
            title = item.sourceTitle,
            text = item.renderedSourceText,
            sourceTabs = sourceTabs,
            typography = typography,
            selectedSource = item.chunk.source,
            onSourceTabClick = onSourceTabClick,
            onAddNewSourceClick = onAddNewSourceClick,
            onRemoveSourceClick = onRemoveSourceClick,
            modifier = Modifier.weight(mainWeight)
                .fillMaxHeight()
        )

        if (!item.hasMergeConflict) {
            ReviewTargetCard(
                item = item,
                typography = typography,
                onEditToggle = onEditToggle,
                onDoneToggle = onDoneToggle,
                onTextChange = onTextChange,
                onUndoClick = onUndoClick,
                onRedoClick = onRedoClick,
                onAddNoteClick = onAddNoteClick,
                onDragDropVerse = onDragDropVerse,
                searchQuery = searchQuery,
                modifier = Modifier.weight(mainWeight)
                    .fillMaxHeight()
            )
        } else {
            MergeConflictCard(
                item = item,
                typography = typography,
                searchQuery = searchQuery,
                onUndoClick = onUndoClick,
                onRedoClick = onRedoClick,
                onConfirmClick = onConflictSelected,
                modifier = Modifier.weight(mainWeight)
                    .fillMaxHeight()
            )
        }

        ResourcesCard(
            helps = item.helps,
            sourceLanguage = item.chunk.source.language,
            typography = typography,
            resourcesOpen = resourcesOpen,
            onHelpClick = onHelpClick,
            modifier = Modifier.weight(peekWeight)
                .fillMaxHeight()
        )
    }
}