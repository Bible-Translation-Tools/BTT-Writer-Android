package com.door43.translationstudio.ui.translate.chunk

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.launchWithProgress
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.Footnote
import com.door43.translationstudio.ui.translate.FootnoteAction
import com.door43.translationstudio.ui.translate.ModeComponent
import com.door43.translationstudio.ui.translate.Swipable
import com.door43.translationstudio.ui.translate.TranslateComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.resourcecontainer.ResourceContainer

interface ChunkModeComponent : ModeComponent<ChunkItem> {
    override val state: StateFlow<State>

    fun onItemTextChanged(item: ChunkItem, text: String)
    fun reopenChunk(item: ChunkItem)
    fun onReopenChunkConfirmed(confirm: Boolean)

    data class State(
        val chunkToReopen: ChunkItem? = null,
        override val footnote: Footnote? = null
    ) : ModeComponent.State
}

class DefaultChunkModeComponent(
    componentContext: ComponentContext,
    sharedState: StateFlow<TranslateComponent.SharedState>,
    private val targetTranslation: TargetTranslation
) : ChunkModeComponent, ProgressOwner, KoinComponent,
    ComponentContext by componentContext,
    ComponentScope, ModeComponent<ChunkItem> {

    private val application: Application by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow(ChunkModeComponent.State())
    override val state: StateFlow<ChunkModeComponent.State> = _state

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _items = MutableStateFlow<List<ChunkItem>>(emptyList())
    override val items: StateFlow<List<ChunkItem>> = _items

    init {
        sharedState
            .map { it.resourceContainer }
            .distinctUntilChanged()
            .onEach { handleResourceChange(it) }
            .launchIn(coroutineScope)

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun handleResourceChange(resourceContainer: ResourceContainer?) {
        val chunks = loadChunks(
            TranslationViewMode.CHUNK,
            resourceContainer,
            targetTranslation
        )

        launchWithProgress(application.getString(R.string.loading_sources)) {
            val items = withContext(Dispatchers.Default) {
                chunks.map { prepareItem(it) }
            }
            updateItems(items)
        }
    }

    override fun updateItem(item: ChunkItem) {
        _items.update { items ->
            items.map { if (it.id == item.id) item else it }
        }
    }

    override fun updateItems(items: List<ChunkItem>) {
        _items.value = items
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun onCardsSwiped(item: Swipable, sourceOnTop: Boolean) {
        updateItem(item.selfCopy(sourceOnTop = sourceOnTop) as ChunkItem)
    }

    override fun openFootnote(note: Footnote) {
        _state.update { it.copy(footnote = note) }
    }

    override fun clearFootnote() {
        _state.update { it.copy(footnote = null) }
    }

    override fun onItemTextChanged(item: ChunkItem, text: String) {
        coroutineScope.launch {
            item.saveTranslation(text)
        }
    }

    override fun reopenChunk(item: ChunkItem) {
        _state.value = _state.value.copy(
            chunkToReopen = item
        )
    }

    override fun onReopenChunkConfirmed(confirm: Boolean) {
        coroutineScope.launch {
            if (confirm) {
                _state.value.chunkToReopen?.let {
                    withContext(Dispatchers.IO) {
                        it.chunk.reopen()
                    }
                    val updated = prepareItem(it.chunk, false)
                    updateItem(updated)
                }
            }
            _state.value = _state.value.copy(
                chunkToReopen = null
            )
        }
    }

    override fun onNoteClicked(
        note: RenderNode.Note,
        chunkId: String,
        action: FootnoteAction
    ) {
        _state.update {
            it.copy(
                footnote = Footnote(
                    text = note.notes,
                    machineReadable = note.machineReadable,
                    chunkId = chunkId,
                    start = note.startPos,
                    end = note.endPos,
                    action = action
                )
            )
        }
    }

    private fun prepareItem(chunk: Chunk, sourceOnTop: Boolean = true): ChunkItem {
        val chunkId = "${chunk.chapterSlug}-${chunk.chunkSlug}"

        val (sourceText, renderedSourceText) = prepareSource(chunkId, chunk)
        val (targetText, renderedTargetText) = prepareTarget(chunkId, chunk)
        val (pt, ct, ft) = prepareTranslations(chunk)

        return ChunkItem(
            id  = chunkId,
            chunk = chunk,
            sourceText = sourceText,
            targetText = targetText,
            renderedSourceText = renderedSourceText,
            renderedTargetText = renderedTargetText,
            pt = pt,
            ct = ct,
            ft = ft,
            sourceOnTop = sourceOnTop
        )
    }

    private fun prepareSource(chunkId: String, chunk: Chunk): Pair<String, AnnotatedString> {
        val text = chunk.source.readChunk(chunk.chapterSlug, chunk.chunkSlug)
        return text to renderSourceText(
            chunkId = chunkId,
            translationFormat = chunk.sourceTranslationFormat,
            sourceText = text
        )
    }

    private fun prepareTarget(chunkId: String, chunk: Chunk): Pair<String, AnnotatedString> {
        val text = fetchTargetText(chunk.target, chunk.chapterSlug, chunk.chunkSlug)
        return text to renderTargetText(
            chunkId = chunkId,
            translationFormat = chunk.targetTranslationFormat,
            targetText = text,
            verseDisplay = VerseDisplay.RAW,
            footnoteAction = FootnoteAction.VIEW
        )
    }

    private fun fetchTargetText(
        target: TargetTranslation,
        chapterSlug: String,
        chunkSlug: String
    ): String {
        return when (chapterSlug) {
            "front" -> if (chunkSlug == "title") target.projectTranslation.title else ""
            "back" -> ""
            else -> when (chunkSlug) {
                "title" -> target.getChapterTranslation(chapterSlug).title
                "reference" -> target.getChapterTranslation(chapterSlug).reference
                else -> target.getFrameTranslation(
                    chapterSlug,
                    chunkSlug,
                    target.format
                ).body
            }
        }
    }
}