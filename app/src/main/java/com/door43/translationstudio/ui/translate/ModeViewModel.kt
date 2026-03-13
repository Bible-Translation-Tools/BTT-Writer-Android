package com.door43.translationstudio.ui.translate

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.ProjectTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.rendering.RenderNodeConverter
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

interface ModeState

interface ModeAction {
    object ClearNotes : ModeAction
}

data class Footnote(
    val text: String,
    val start: Int,
    val end: Int
)

abstract class ModeViewModel<ACTION, ITEM: TranslateItem>(
    private val chunks: StateFlow<List<Chunk>>
) : ViewModel(), KoinComponent {

    private val _footnote = MutableStateFlow<Footnote?>(null)
    val footnote: StateFlow<Footnote?> = _footnote.asStateFlow()

    protected val _items = MutableStateFlow<List<ITEM>>(emptyList())
    val items: StateFlow<List<ITEM>> = _items

    init {
        viewModelScope.launch {
            chunks.collect { list ->
                _items.value = mapToChildType(list)
            }
        }
    }

    abstract fun mapToChildType(chunks: List<Chunk>): List<ITEM>

    abstract fun onAction(action: ACTION)

    fun onSharedAction(action: ModeAction) {
        when(action) {
            ModeAction.ClearNotes -> { _footnote.value = null }
        }
    }

    fun updateLocalItem(updatedItem: ITEM) {
        _items.value = _items.value.map {
            if (it.id == updatedItem.id) {
                updatedItem
            } else it
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
                }
            )
        } catch (_: Exception) {
            AnnotatedString(targetText)
        }
    }
}