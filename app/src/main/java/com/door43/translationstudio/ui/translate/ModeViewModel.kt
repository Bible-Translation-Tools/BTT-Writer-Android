package com.door43.translationstudio.ui.translate

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.ProjectTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.rendering.RenderNodeConverter
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import com.door43.translationstudio.ui.viewmodels.TargetEvent
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.unfoldingword.resourcecontainer.ResourceContainer

data class Footnote(
    val text: String,
    val machineReadable: String,
    val chunkId: String,
    val editable: Boolean
)

interface ModeState

data class LocalModeState(
    val footnote: Footnote? = null,
    val footnoteToEdit: Footnote? = null
) : ModeState

interface ModeAction {
    data class CardsSwiped(val item: Swipable, val sourceOnTop: Boolean) : ModeAction
    data class DeleteNote(val note: Footnote) : ModeAction
    data class OpenFootnoteEditor(val note: Footnote) : ModeAction
    data class SaveFootnote(val note: Footnote) : ModeAction
    object ClearFootnote : ModeAction
    object ClearFootnoteToEdit : ModeAction
}

data class SharedState(
    val items: List<Chunk> = emptyList(),
    val sourceContainer: ResourceContainer? = null,
    val viewMode: TranslationViewMode = TranslationViewMode.READ
)

abstract class ModeViewModel<ITEM: TranslateItem>(
    protected val sharedState: StateFlow<SharedState>,
    private val viewMode: TranslationViewMode,
    private val event: SendChannel<TargetEvent>
) : ViewModel(), KoinComponent {

    private val _modeState = MutableStateFlow(LocalModeState())
    val modeState: StateFlow<LocalModeState> = _modeState.asStateFlow()

    protected fun showSnackBar(message: String) {
        event.trySend(TargetEvent.ShowMessage(message))
    }

    protected fun restartAutoCommitTimer() {
        event.trySend(TargetEvent.RestartAutoCommitTimer)
    }

    protected val _items = MutableStateFlow<List<ITEM>>(emptyList())
    val items: StateFlow<List<ITEM>> = _items

    init {
        viewModelScope.launch {
            sharedState
                .map { it.items to it.viewMode }
                .distinctUntilChanged()
                .collect { (list, mode) ->
                    if (mode == viewMode) {
                        mapToChildType(list) { items ->
                            _items.value = items
                        }
                    } else {
                        _items.value = emptyList()
                    }
                }
        }
    }

    abstract fun mapToChildType(chunks: List<Chunk>, onReady: (List<ITEM>) -> Unit)

    open fun onAction(action: ModeAction) {
        when (action) {
            is ModeAction.CardsSwiped -> onCardsSwiped(action.item, action.sourceOnTop)
            is ModeAction.DeleteNote -> onDeleteFootnote(action.note)
            is ModeAction.OpenFootnoteEditor -> onOpenFootnoteEditor(action.note)
            is ModeAction.SaveFootnote -> onSaveFootnote(action.note)
            ModeAction.ClearFootnote -> { _modeState.update { it.copy(footnote = null) } }
            ModeAction.ClearFootnoteToEdit -> {
                _modeState.update { it.copy(footnoteToEdit = null) }
            }
        }
    }

    fun updateItem(item: ITEM) {
        _items.value = _items.value.map {
            if (it.id == item.id) item else it
        }
    }

    fun showFootnoteViewer(note: Footnote) {
        _modeState.update { it.copy(footnote = note) }
    }

    fun showFootnoteEditor(note: Footnote) {
        _modeState.update { it.copy(footnoteToEdit = note) }
    }

    protected fun prepareTranslations(
        chunk: Chunk,
    ): Triple<ProjectTranslation, ChapterTranslation, FrameTranslation> {
        val pt = chunk.target.projectTranslation
        val ct = chunk.target.getChapterTranslation(chunk.chapterSlug)
        val ft = chunk.target.getFrameTranslation(
            chunk.chapterSlug,
            chunk.chunkSlug,
            chunk.targetTranslationFormat
        )
        return Triple(pt, ct, ft)
    }

    protected fun renderSourceText(
        chunkId: String,
        translationFormat: TranslationFormat,
        sourceText: String
    ): AnnotatedString {
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(sourceText)
            RenderingProvider().setupRenderingGroup(
                format = translationFormat,
                renderingGroup = renderingGroup,
                verseDisplay = VerseDisplay.NUMBER,
                target = false
            )
            val renderNodes = renderingGroup.startNodes()
            val textNodes = RenderNodeConverter.renderNodesToTextNodes(renderNodes)
            ComposeTextAdapter.convert(
                textNodes,
                onNoteClick = { notes, _, _ ->
                    _modeState.update {
                        it.copy(footnote = Footnote(
                            text = notes.notes,
                            machineReadable = notes.machineReadable,
                            chunkId = chunkId,
                            editable = false
                        ))
                    }
                }
            )
        } catch (_: Exception) {
            AnnotatedString(sourceText)
        }
    }

    protected fun renderTargetText(
        chunkId: String,
        translationFormat: TranslationFormat,
        targetText: String,
        verseDisplay: VerseDisplay = VerseDisplay.RAW,
        footnoteEditable: Boolean
    ): AnnotatedString {
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(targetText)
            RenderingProvider().setupRenderingGroup(
                translationFormat,
                renderingGroup,
                verseDisplay,
                target = true
            )
            val renderNodes = renderingGroup.startNodes()
            val textNodes = RenderNodeConverter.renderNodesToTextNodes(renderNodes)
            ComposeTextAdapter.convert(
                nodes = textNodes,
                onNoteClick = { notes, _, _ ->
                    _modeState.update {
                        it.copy(footnote = Footnote(
                            text = notes.notes,
                            machineReadable = notes.machineReadable,
                            chunkId = chunkId,
                            editable = footnoteEditable
                        ))
                    }
                },
                onVerseClick = {
                    println(it)
                }
            )
        } catch (_: Exception) {
            AnnotatedString(targetText)
        }
    }

    protected open fun onCardsSwiped(item: Swipable, sourceOnTop: Boolean) {}

    protected open fun onDeleteFootnote(note: Footnote) {
        _modeState.update { it.copy(footnote = null) }
    }
    protected open fun onOpenFootnoteEditor(note: Footnote) {
        _modeState.update { it.copy(footnote = null) }
    }

    protected open fun onSaveFootnote(note: Footnote) {
        _modeState.update { it.copy(footnoteToEdit = null) }
    }
}