package com.door43.translationstudio.ui.translate.read

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.components.StackedCardFlipper
import com.door43.translationstudio.ui.viewmodels.SourceTabItem

@Composable
fun ReadCard(
    chapter: ChunkItem.ReadMode,
    sourceTabs: List<SourceTabItem>,
    selectedSourceId: String?,
    onSourceTabClick: (String) -> Unit,
    onAddNewSourceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    StackedCardFlipper(
        modifier = modifier,
        containerPadding = 8.dp,
        stackOffset = 32.dp,
        frontCard = {
            ReadSourceCard(
                title = chapter.sourceTitle,
                text = chapter.meta.renderedSourceText,
                sourceTabs = sourceTabs,
                selectedSourceId = selectedSourceId,
                onSourceTabClick = onSourceTabClick,
                onAddNewSourceClick = onAddNewSourceClick
            )
        },
        backCard = {
            ReadTargetCard(
                title = chapter.targetTitle,
                text = chapter.meta.renderedTargetText
            )
        }
    )
}
