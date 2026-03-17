package com.door43.translationstudio.ui.translate.review

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.translate.ReviewItem
import com.door43.translationstudio.ui.translate.chunk.ChunkSourceCard
import com.door43.translationstudio.ui.translate.dialogs.SourceTabItem
import org.unfoldingword.resourcecontainer.ResourceContainer

@Composable
fun ReviewCard(
    item: ReviewItem,
    sourceTabs: List<SourceTabItem>,
    typography: Typography,
    selectedSource: ResourceContainer?,
    targetTranslation: TargetTranslation,
    onSourceTabClick: (String) -> Unit,
    onAddNewSourceClick: () -> Unit,
    onRemoveSourceClick: (String) -> Unit,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    resourcesOpen: Boolean = false,
    onRenderHelps: () -> Unit = {},
    onHelpClick: (HelpItem) -> Unit,
    onExpandedChange: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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

    LaunchedEffect(resourcesOpen) {
        if (resourcesOpen) onRenderHelps()
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
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
            selectedSource = selectedSource,
            onSourceTabClick = onSourceTabClick,
            onAddNewSourceClick = onAddNewSourceClick,
            onRemoveSourceClick = onRemoveSourceClick,
            modifier = Modifier.weight(mainWeight)
                .fillMaxHeight()
        )

        Card(
            modifier = Modifier.fillMaxHeight()
                .weight(mainWeight),
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
                Text("Target")
            }
        }

        ResourcesCard(
            helps = item.helps,
            sourceLanguage = item.chunk.source.language,
            resourcesOpen = resourcesOpen,
            onHelpClick = onHelpClick,
            modifier = Modifier.weight(peekWeight)
                .fillMaxHeight()
        )
    }
}