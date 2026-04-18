package com.door43.translationstudio.ui.translate

import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.ui.dialogs.export.ExportComponent
import com.door43.translationstudio.ui.dialogs.feedback.FeedbackComponent
import com.door43.translationstudio.ui.dialogs.source.SelectSourcesComponent
import com.door43.translationstudio.ui.dialogs.source.SourceTabItem
import com.door43.translationstudio.ui.translate.chunk.ChunkModeComponent
import com.door43.translationstudio.ui.translate.read.ReadModeComponent
import com.door43.translationstudio.ui.translate.review.ReviewModeComponent
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.io.File

interface TranslateComponent {

    val stack: Value<ChildStack<*, Child>>
    val dialogSlot: Value<ChildSlot<*, DialogChild>>

    val state: StateFlow<State>
    val sharedState: StateFlow<SharedState>
    val progress: StateFlow<Progress?>
    val event: Flow<Event>
    val eventSender: SendChannel<Event>

    val currentViewMode: Value<TranslationViewMode>

    val targetTranslation: TargetTranslation

    fun openViewMode(viewMode: TranslationViewMode)
    fun openReadMode()
    fun openChunkMode()
    fun openReviewMode(conflictFilterOn: Boolean = false)

    fun restartAutoCommitTimer()
    fun updateMergeFilter(on: Boolean)
    fun removeSource(sourceId: String)
    fun selectSource(sourceId: String)
    fun saveLastFocus(chapterId: String, frameId: String?)

    fun openHome(withUpdate: Boolean = false)
    fun openDraft(translationId: String)
    fun openPublishProject(translationId: String)
    fun openSettings()

    fun showFeedbackDialog()
    fun showSelectSourcesDialog()
    fun showExportDialog(startFromPrint: Boolean = false)
    fun dismissDialog()

    companion object {
        const val SEARCH_SOURCE = "search_source"
    }

    data class State(
        val conflictFilterOn: Boolean = false,
        val draftAvailable: Boolean = false,
        val showDraftAvailable: Boolean = false,
        val lastFocusChapterId: String? = null,
        val lastFocusFrameId: String? = null,
        val projectTitle: String? = null,
    )

    data class SharedState(
        val sourceTabs: List<SourceTabItem> = emptyList(),
        val resourceContainer: ResourceContainer? = null
    )

    sealed interface Event {
        data class SnackbarMessage(val message: String) : Event
        data object RestartAutoCommitTimer : Event
    }

    sealed interface Child {
        data class Loading(val component: LoadingComponent) : Child
        data class Read(val component: ReadModeComponent) : Child
        data class Chunk(val component: ChunkModeComponent) : Child
        data class Review(val component: ReviewModeComponent) : Child
    }

    sealed interface Result {
        data class OpenHome(val withUpdate: Boolean) : Result
        data class OpenDraft(val translationId: String) : Result
        data class OpenPublishProject(val translationId: String) : Result
        data object Logout : Result
        data object OpenLogin : Result
        data object OpenSettings : Result
        data class ExportToApp(val file: File) : Result
        data class Error(val message: String) : Result
    }

    @Serializable
    sealed interface Config {
        @Serializable
        object Loading : Config

        @Serializable
        data object Read : Config

        @Serializable
        data object Chunk : Config

        @Serializable
        data class Review(val conflictFilterOn: Boolean) : Config
    }

    @Serializable
    sealed interface DialogConfig {
        @Serializable
        data object Feedback : DialogConfig

        @Serializable
        data class SelectSources(val translationId: String) : DialogConfig

        @Serializable
        data class Export(val translationId: String, val startFromPrint: Boolean) : DialogConfig
    }

    sealed interface DialogChild {
        data class Feedback(val component: FeedbackComponent) : DialogChild
        data class SelectSources(val component: SelectSourcesComponent) : DialogChild
        data class Export(val component: ExportComponent) : DialogChild
    }
}
