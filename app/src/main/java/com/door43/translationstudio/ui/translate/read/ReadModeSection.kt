package com.door43.translationstudio.ui.translate.read

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.translate.ModeAction
import com.door43.translationstudio.ui.translate.ModeScreenTemplate
import com.door43.translationstudio.ui.translate.TargetAction
import com.door43.translationstudio.ui.translate.TargetTranslationViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReadModeSection(
    viewModel: TargetTranslationViewModel,
    typography: Typography,
    listState: LazyListState,
    onSourceDialogOpen: () -> Unit,
    onHasMergeConflicts: (Boolean) -> Unit,
    onBeginTranslation: (chapterSlug: String) -> Unit
) {
    val readVm: ReadModeViewModel = koinViewModel {
        parametersOf(viewModel.sharedState, viewModel.eventSender)
    }

    val sharedState by viewModel.sharedState.collectAsStateWithLifecycle()
    val items by readVm.items.collectAsStateWithLifecycle()
    val hasConflicts = items.any { it.hasMergeConflicts }

    LaunchedEffect(hasConflicts) {
        onHasMergeConflicts(hasConflicts)
    }

    ModeScreenTemplate(
        viewModel = readVm,
        items = items,
        listState = listState
    ) { item ->
        ReadCard(
            item = item,
            sourceTabs = sharedState.sourceTabs,
            typography = typography,
            selectedSource = sharedState.resourceContainer,
            targetTranslation = viewModel.targetTranslation,
            onSourceTabClick = {
                viewModel.onAction(TargetAction.SelectSource(it))
            },
            onAddNewSourceClick = onSourceDialogOpen,
            onRemoveSourceClick = {
                viewModel.onAction(TargetAction.RemoveSource(it))
            },
            onCardsSwiped = {
                readVm.onAction(ModeAction.CardsSwiped(item, it))
            },
            onBeginTranslation = onBeginTranslation
        )
    }
}
