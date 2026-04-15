package com.door43.translationstudio.ui.translate

import androidx.compose.ui.text.AnnotatedString
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProjectTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import kotlinx.coroutines.flow.StateFlow

enum class FootnoteAction {
    VIEW, ACTIONS, EDIT
}

data class Footnote(
    val text: String,
    val machineReadable: String,
    val chunkId: String,
    val start: Int = -1,
    val end: Int = -1,
    val insertPosition: Int = -1,
    val action: FootnoteAction
)

interface ModeComponent<ITEM: TranslateItem> {

    val state: StateFlow<State>
    val items: StateFlow<List<ITEM>>
    val progress: StateFlow<Progress?>

    fun onAction(action: Action)
    fun updateItem(item: ITEM)
    fun updateItems(items: List<ITEM>)
    fun onNoteClicked(note: RenderNode.Note, chunkId: String, action: FootnoteAction)
    fun onShowFootnote(note: Footnote)
    suspend fun handleResourceChange()

    fun onCardsSwiped(item: Swipable, sourceOnTop: Boolean){}

    fun prepareTranslations(
        chunk: Chunk
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

    fun renderSourceText(
        chunkId: String,
        translationFormat: TranslationFormat,
        sourceText: String,
        searchQuery: String? = null
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
            if (!searchQuery.isNullOrEmpty()) {
                renderingGroup.setSearchString(searchQuery)
            }
            val renderNodes = renderingGroup.start()
            ComposeTextAdapter.convert(
                renderNodes,
                onNoteClick = { note, _, _ ->
                    onNoteClicked(note, chunkId, FootnoteAction.VIEW)
                }
            )
        } catch (_: Exception) {
            AnnotatedString(sourceText)
        }
    }

    fun renderTargetText(
        chunkId: String,
        translationFormat: TranslationFormat,
        targetText: String,
        verseDisplay: VerseDisplay = VerseDisplay.RAW,
        footnoteAction: FootnoteAction,
        searchQuery: String? = null,
        onVerseClick: ((RenderNode.Verse) -> Unit)? = null
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
            if (!searchQuery.isNullOrEmpty()) {
                renderingGroup.setSearchString(searchQuery)
            }
            val renderNodes = renderingGroup.start()
            ComposeTextAdapter.convert(
                nodes = renderNodes,
                onNoteClick = { note, _, _ ->
                    onNoteClicked(note, chunkId, footnoteAction)
                },
                onVerseClick = onVerseClick
            )
        } catch (_: Exception) {
            AnnotatedString(targetText)
        }
    }

    interface State {
        val footnote: Footnote?
    }

    interface Action {
        data class CardsSwiped(val item: Swipable, val sourceOnTop: Boolean) : Action
        data class OpenFootnote(val note: Footnote) : Action
        data class DeleteNote(val note: Footnote) : Action
        data class SaveFootnote(val note: Footnote) : Action
        data object ClearFootnote : Action
    }
}
