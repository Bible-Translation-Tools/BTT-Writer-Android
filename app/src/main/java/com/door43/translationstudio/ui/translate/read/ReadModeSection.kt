package com.door43.translationstudio.ui.translate.read

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.translate.ModeScreenTemplate
import com.door43.translationstudio.ui.translate.TranslateComponent

@Composable
fun ReadModeSection(
    component: ReadModeComponent,
    parentComponent: TranslateComponent,
    typography: Typography,
    listState: LazyListState,
    onSourceDialogOpen: () -> Unit,
    onHasMergeConflicts: (Boolean) -> Unit,
    onBeginTranslation: (chapterSlug: String) -> Unit
) {
    val sharedState by parentComponent.sharedState.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()
    val items by component.items.collectAsStateWithLifecycle()
    val hasConflicts = items.any { it.hasMergeConflict }

    LaunchedEffect(hasConflicts) {
        onHasMergeConflicts(hasConflicts)
    }

    ModeScreenTemplate(
        component = component,
        items = items,
        listState = listState
    ) { item ->
        ReadCard(
            item = item,
            sourceTabs = sharedState.sourceTabs,
            typography = typography,
            selectedSource = sharedState.resourceContainer,
            targetTranslation = parentComponent.targetTranslation,
            onSourceTabClick = parentComponent::selectSource,
            onAddNewSourceClick = onSourceDialogOpen,
            onRemoveSourceClick = parentComponent::removeSource,
            onCardsSwiped = { component.onCardsSwiped(item, it) },
            onBeginTranslation = onBeginTranslation
        )
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}
