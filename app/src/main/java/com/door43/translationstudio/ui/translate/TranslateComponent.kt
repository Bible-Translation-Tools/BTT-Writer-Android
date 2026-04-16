package com.door43.translationstudio.ui.translate

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.ui.translate.chunk.ChunkModeComponent
import com.door43.translationstudio.ui.translate.dialogs.RCItem
import com.door43.translationstudio.ui.translate.dialogs.SourceTabItem
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

    val state: StateFlow<State>
    val sharedState: StateFlow<SharedState>
    val progress: StateFlow<Progress?>
    val event: Flow<Event>
    val eventSender: SendChannel<Event>

    val startWithMergeFilter: Boolean
    val targetTranslation: TargetTranslation

    fun restartAutoCommitTimer()
    fun onAction(action: Action)

    fun openHome(withUpdate: Boolean)
    fun openDraft(translationId: String)
    fun openPublishProject(translationId: String)
    fun openLogin()
    fun logout()
    fun openSettings()
    fun exportToApp(file: File)

    companion object {
        const val SEARCH_SOURCE = "search_source"
    }

    data class State(
        val viewMode: TranslationViewMode = TranslationViewMode.LOADING,
        val draftAvailable: Boolean = false,
        val showDraftAvailable: Boolean = false,
        val lastFocusChapterId: String? = null,
        val lastFocusFrameId: String? = null,
        val projectTitle: String? = null,
    )

    data class SharedState(
        val chunks: List<Chunk> = emptyList(),
        val sourceTabs: List<SourceTabItem> = emptyList(),
        val resourceContainer: ResourceContainer? = null
    )

    sealed interface Action {
        data class RemoveSource(val sourceId: String) : Action
        data class SelectSource(val sourceId: String) : Action
        data class SaveLastViewMode(val viewMode: TranslationViewMode) : Action
        data class SaveLastFocus(val chapterId: String, val frameId: String?) : Action
        data class ConfirmSelectedSources(val selectedItems: List<RCItem>) : Action
    }

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
        data object Review : Config
    }
}
