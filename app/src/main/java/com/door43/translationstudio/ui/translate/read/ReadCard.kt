package com.door43.translationstudio.ui.translate.read

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.translate.ReadItem
import com.door43.translationstudio.ui.dialogs.source.SourceTabItem
import com.door43.translationstudio.ui.translate.components.StackedCardFlipper
import org.unfoldingword.resourcecontainer.ResourceContainer

@Composable
fun ReadCard(
    item: ReadItem,
    sourceTabs: List<SourceTabItem>,
    typography: Typography,
    selectedSource: ResourceContainer?,
    targetTranslation: TargetTranslation,
    onSourceTabClick: (String) -> Unit,
    onAddNewSourceClick: () -> Unit,
    onRemoveSourceClick: (String) -> Unit,
    onCardsSwiped: (Boolean) -> Unit,
    onBeginTranslation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    StackedCardFlipper(
        modifier = modifier,
        containerPadding = 8.dp,
        stackOffset = 32.dp,
        frontOnTop = item.sourceOnTop,
        frontCard = {
            ReadSourceCard(
                title = item.sourceTitle,
                text = item.renderedSourceText,
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
                text = item.renderedTargetText,
                targetTranslation = targetTranslation,
                typography = typography,
                onBeginTranslationClick = {
                    onBeginTranslation(item.chunk.chapterSlug)
                }
            )
        },
        onAnimationEnd = onCardsSwiped
    )
}
