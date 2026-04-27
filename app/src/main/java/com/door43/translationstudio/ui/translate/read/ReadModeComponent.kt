package com.door43.translationstudio.ui.translate.read

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.ui.translate.Footnote
import com.door43.translationstudio.ui.translate.FootnoteAction
import com.door43.translationstudio.ui.translate.ModeComponent
import com.door43.translationstudio.ui.translate.ReadItem
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
import org.bibletranslationtools.resourcecontainer.ResourceContainer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface ReadModeComponent : ModeComponent<ReadItem> {
    override val state: StateFlow<State>

    data class State(
        override val footnote: Footnote? = null
    ) : ModeComponent.State
}

class DefaultReadModeComponent(
    componentContext: ComponentContext,
    sharedState: StateFlow<TranslateComponent.SharedState>,
    private val targetTranslation: TargetTranslation
) : ReadModeComponent, ProgressOwner, KoinComponent,
    ComponentContext by componentContext,
    ComponentScope, ModeComponent<ReadItem> {

    private val application: Application by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow(ReadModeComponent.State())
    override val state: StateFlow<ReadModeComponent.State> = _state

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _items = MutableStateFlow<List<ReadItem>>(emptyList())
    override val items: StateFlow<List<ReadItem>> = _items

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
            TranslationViewMode.READ,
            resourceContainer,
            targetTranslation
        )

        coroutineScope.launch {
            val items = withContext(Dispatchers.Default) {
                chunks.map { prepareItem(it) }
            }
            updateItems(items)
        }
    }

    override fun updateItem(item: ReadItem) {
        _items.update { items ->
            items.map { if (it.id == item.id) item else it }
        }
    }

    override fun updateItems(items: List<ReadItem>) {
        _items.value = items
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun onCardsSwiped(item: Swipable, sourceOnTop: Boolean) {
        updateItem(item.selfCopy(sourceOnTop = sourceOnTop) as ReadItem)
    }

    override fun openFootnote(note: Footnote) {
        _state.update { it.copy(footnote = note) }
    }

    override fun clearFootnote() {
        _state.update { it.copy(footnote = null) }
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

    private fun prepareItem(chunk: Chunk, sourceOnTop: Boolean = true): ReadItem {
        val (sourceText, renderedSourceText) = prepareSource(chunk)
        val (targetText, renderedTargetText) = prepareTarget(chunk)
        val (pt, ct, ft) = prepareTranslations(chunk)

        return ReadItem(
            id = chunk.chapterSlug,
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

    private fun prepareSource(chunk: Chunk): Pair<String, AnnotatedString> {
        val sourceText = fetchSourceText(chunk.source, chunk.chapterSlug)
        val renderedSourceText = renderSourceText(
            chunkId = chunk.chapterSlug,
            translationFormat = chunk.sourceTranslationFormat,
            sourceText = sourceText
        )

        return sourceText to renderedSourceText
    }

    private fun prepareTarget(chunk: Chunk): Pair<String, AnnotatedString> {
        val targetText = fetchTargetText(chunk.source, chunk.target, chunk.chapterSlug)
        val renderedTargetText = renderTargetText(
            chunkId = chunk.chapterSlug,
            translationFormat = chunk.targetTranslationFormat,
            targetText = targetText,
            verseDisplay = VerseDisplay.NUMBER,
            footnoteAction = FootnoteAction.VIEW
        )

        return targetText to renderedTargetText
    }

    private fun fetchSourceText(source: ResourceContainer, chapterSlug: String): String {
        var chapterBody = ""
        val sorter = SlugSorter()
        val chunks = sorter.sort(source.chunks(chapterSlug))
        for (chunk in chunks) {
            if (chunk != "title") {
                chapterBody += source.readChunk(chapterSlug, chunk)
            }
        }
        return chapterBody
    }

    private fun fetchTargetText(
        source: ResourceContainer,
        target: TargetTranslation,
        chapterSlug: String
    ): String {
        var chapterBody = ""
        val sorter = SlugSorter()
        val chunks = sorter.sort(source.chunks(chapterSlug))
        for (chunk in chunks) {
            val translation = target.getFrameTranslation(chapterSlug, chunk, target.format)
            chapterBody += " " + translation.body
        }
        return chapterBody
    }
}