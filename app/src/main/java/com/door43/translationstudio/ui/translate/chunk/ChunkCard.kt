package com.door43.translationstudio.ui.translate.chunk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.components.StackedCardFlipper
import com.door43.translationstudio.ui.translate.dialogs.SourceTabItem
import org.unfoldingword.resourcecontainer.ResourceContainer

@Composable
fun ChunkCard(
    item: ChunkItem,
    sourceTabs: List<SourceTabItem>,
    typography: Typography,
    selectedSource: ResourceContainer?,
    targetTranslation: TargetTranslation,
    onSourceTabClick: (String) -> Unit,
    onAddNewSourceClick: () -> Unit,
    onRemoveSourceClick: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onCardsSwiped: (Boolean) -> Unit,
    onOpenChunkClick: () -> Unit,
    onConflictClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(item.sourceOnTop) {
        if (item.sourceOnTop) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    StackedCardFlipper(
        modifier = modifier,
        containerPadding = 8.dp,
        stackOffset = 32.dp,
        frontOnTop = item.sourceOnTop,
        frontCard = {
            ChunkSourceCard(
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
            ChunkTargetCard(
                item = item,
                targetTranslation = targetTranslation,
                typography = typography,
                onTextChange = onTextChange,
                onCompleteItemClick = onOpenChunkClick,
                onConflictClick = onConflictClick
            )
        },
        onAnimationEnd = onCardsSwiped
    )
}
