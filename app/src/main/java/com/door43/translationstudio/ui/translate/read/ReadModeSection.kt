package com.door43.translationstudio.ui.translate.read

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.translate.ModeScreenTemplate
import com.door43.translationstudio.ui.translate.ScrollCoordinator
import com.door43.translationstudio.ui.translate.TranslateComponent
import com.door43.translationstudio.ui.translate.ScrollBindingEffect

@Composable
fun ReadModeSection(
    component: ReadModeComponent,
    parentComponent: TranslateComponent,
    typography: Typography,
    scrollCoordinator: ScrollCoordinator,
    onSourceDialogOpen: () -> Unit,
    onHasMergeConflicts: (Boolean) -> Unit,
    onBeginTranslation: (chapterSlug: String) -> Unit
) {
    val sharedState by parentComponent.sharedState.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()
    val items by component.items.collectAsStateWithLifecycle()
    val hasConflicts = items.any { it.hasMergeConflict }

    ScrollBindingEffect(scrollCoordinator, items, parentComponent)

    LaunchedEffect(hasConflicts) {
        onHasMergeConflicts(hasConflicts)
    }

    ModeScreenTemplate(
        component = component,
        items = items.toList(),
        listState = scrollCoordinator.listState
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
