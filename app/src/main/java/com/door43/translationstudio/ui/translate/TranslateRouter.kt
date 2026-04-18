package com.door43.translationstudio.ui.translate

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.translate.chunk.ChunkModeSection
import com.door43.translationstudio.ui.translate.read.ReadModeSection
import com.door43.translationstudio.ui.translate.review.ReviewModeSection
import org.koin.compose.koinInject

@Composable
fun TranslateRouter(
    component: TranslateComponent,
    scrollCoordinator: ScrollCoordinator,
    onSourceDialogOpen: () -> Unit,
    onHasMergeConflicts: (Boolean) -> Unit,
    searchRequested: Boolean,
    onSearchConsumed: () -> Unit,
    conflictFilterOn: Boolean,
    onConflictFilterReset: () -> Unit,
    chunksDoneRequested: Boolean,
    onChunksDoneConsumed: () -> Unit
) {
    val typography: Typography = koinInject()

    Children(
        stack = component.stack,
        animation = stackAnimation(fade()),
    ) { child ->
        when (val instance = child.instance) {
            is TranslateComponent.Child.Loading -> LoadingScreen()
            is TranslateComponent.Child.Read -> ReadModeSection(
                component = instance.component,
                parentComponent = component,
                typography = typography,
                scrollCoordinator = scrollCoordinator,
                onSourceDialogOpen = onSourceDialogOpen,
                onHasMergeConflicts = onHasMergeConflicts,
                onBeginTranslation = {
                    scrollCoordinator.pendingScrollChapter = PendingScrollItem(
                        chapterId = it
                    )
                    component.openChunkMode()
                }
            )
            is TranslateComponent.Child.Chunk -> ChunkModeSection(
                component = instance.component,
                parentComponent = component,
                typography = typography,
                scrollCoordinator = scrollCoordinator,
                onSourceDialogOpen = onSourceDialogOpen,
                onHasMergeConflicts = onHasMergeConflicts,
                onConflictClick = { chapterId, chunkId ->
                    scrollCoordinator.pendingScrollChapter = PendingScrollItem(
                        chapterId = chapterId,
                        chunkId = chunkId
                    )
                    component.openReviewMode(true)
                }
            )
            is TranslateComponent.Child.Review -> ReviewModeSection(
                component = instance.component,
                parentComponent = component,
                typography = typography,
                scrollCoordinator = scrollCoordinator,
                searchRequested = searchRequested,
                onSearchConsumed = onSearchConsumed,
                onSourceDialogOpen = onSourceDialogOpen,
                onHasMergeConflicts = onHasMergeConflicts,
                conflictFilterOn = conflictFilterOn,
                onConflictFilterReset = onConflictFilterReset,
                chunksDoneRequested = chunksDoneRequested,
                onChunksDoneConsumed = onChunksDoneConsumed
            )
        }
    }
}