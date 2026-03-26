package com.door43.translationstudio.ui.translate.read

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.translate.ModeAction
import com.door43.translationstudio.ui.translate.ModeScreenTemplate
import com.door43.translationstudio.ui.translate.TargetAction
import com.door43.translationstudio.ui.translate.TargetTranslationState
import com.door43.translationstudio.ui.translate.TargetTranslationViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReadModeSection(
    viewModel: TargetTranslationViewModel,
    state: TargetTranslationState,
    typography: Typography,
    listState: LazyListState,
    onSourceDialogOpen: () -> Unit,
    onBeginTranslation: (chapterSlug: String) -> Unit
) {
    val readVm: ReadModeViewModel = koinViewModel {
        parametersOf(viewModel.sharedStateFlow, viewModel.eventSender)
    }

    ModeScreenTemplate(
        viewModel = readVm,
        listState = listState
    ) { item ->
        ReadCard(
            item = item,
            sourceTabs = state.sourceTabs,
            typography = typography,
            selectedSource = state.resourceContainer,
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
