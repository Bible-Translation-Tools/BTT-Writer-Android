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
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.unfoldingword.resourcecontainer.ResourceContainer

data class Footnote(
    val text: String,
    val start: Int,
    val end: Int
)

interface ModeState

interface ModeAction {
    object ClearNotes : ModeAction
    data class CardsSwiped(val item: Swipable, val sourceOnTop: Boolean) : ModeAction
}

data class SharedState(
    val items: List<Chunk> = emptyList(),
    val sourceContainer: ResourceContainer? = null,
    val viewMode: TranslationViewMode = TranslationViewMode.READ
)

abstract class ModeViewModel<ITEM: TranslateItem>(
    protected val sharedState: StateFlow<SharedState>,
    val targetViewMode: TranslationViewMode,
    private val snackBar: SendChannel<String>
) : ViewModel(), KoinComponent {

    private val _footnote = MutableStateFlow<Footnote?>(null)
    val footnote: StateFlow<Footnote?> = _footnote.asStateFlow()

    protected fun showSnackBar(message: String) {
        snackBar.trySend(message)
    }

    protected val _items = MutableStateFlow<List<ITEM>>(emptyList())
    val items: StateFlow<List<ITEM>> = _items

    init {
        viewModelScope.launch {
            sharedState
                .map { it.items to it.viewMode }
                .distinctUntilChanged()
                .collect { (list, mode) ->
                    if (mode == targetViewMode) {
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
            ModeAction.ClearNotes -> { _footnote.value = null }
        }
    }

    fun updateItem(item: ITEM) {
        _items.value = _items.value.map {
            if (it.id == item.id) item else it
        }
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
        translationFormat: TranslationFormat,
        sourceText: String
    ): AnnotatedString {
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(sourceText)
            RenderingProvider().setupRenderingGroup(
                translationFormat, renderingGroup, verseDisplay = VerseDisplay.NUMBER, target = false
            )
            val renderNodes = renderingGroup.startNodes()
            val textNodes = RenderNodeConverter.renderNodesToTextNodes(renderNodes)
            ComposeTextAdapter.convert(
                textNodes,
                onNoteClick = { notes, start, end ->
                    _footnote.value = Footnote(notes.notes, start, end)
                }
            )
        } catch (_: Exception) {
            AnnotatedString(sourceText)
        }
    }

    protected fun renderTargetText(
        translationFormat: TranslationFormat,
        targetText: String,
        verseDisplay: VerseDisplay = VerseDisplay.RAW
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
                textNodes,
                onNoteClick = { notes, start, end ->
                    _footnote.value = Footnote(notes.notes, start, end)
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
}