package com.door43.translationstudio.ui.translate.read

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
fun ReadCard(
    item: ChunkItem.ReadMode,
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
            ReadSourceCard(
                title = item.sourceTitle,
                text = item.meta.renderedSourceText,
                sourceTabs = sourceTabs,
                typography = typography,
                selectedSource = selectedSource,
                onSourceTabClick = onSourceTabClick,
                onAddNewSourceClick = onAddNewSourceClick,
                onRemoveSourceClick = onRemoveSourceClick
            )
        },
        backCard = {
            ReadTargetCard(
                title = item.targetTitle,
                text = item.meta.renderedTargetText,
                targetTranslation = targetTranslation,
                typography = typography
            )
        }
    )
}
