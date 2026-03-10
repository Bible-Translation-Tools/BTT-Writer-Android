package com.door43.translationstudio.ui.translate.chunk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.components.StackedCardFlipper
import com.door43.translationstudio.ui.viewmodels.SourceTabItem
import org.unfoldingword.resourcecontainer.ResourceContainer

@Composable
fun ChunkCard(
    chunk: ChunkItem.ChunkMode,
    sourceTabs: List<SourceTabItem>,
    typography: Typography,
    selectedSource: ResourceContainer?,
    targetTranslation: TargetTranslation,
    onSourceTabClick: (String) -> Unit,
    onAddNewSourceClick: () -> Unit,
    onRemoveSourceClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    StackedCardFlipper(
        modifier = modifier,
        containerPadding = 8.dp,
        stackOffset = 32.dp,
        frontCard = {
            ChunkSourceCard(
                title = chunk.sourceTitle,
                text = chunk.meta.renderedSourceText,
                sourceTabs = sourceTabs,
                typography = typography,
                selectedSource = selectedSource,
                onSourceTabClick = onSourceTabClick,
                onAddNewSourceClick = onAddNewSourceClick,
                onRemoveSourceClick = onRemoveSourceClick
            )
        },
        backCard = {
            ChunkTargetCard(
                title = chunk.targetTitle,
                text = chunk.meta.renderedTargetText,
                targetTranslation = targetTranslation,
                typography = typography
            )
        }
    )
}
