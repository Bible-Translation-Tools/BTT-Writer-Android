package com.door43.translationstudio.ui.translate.review

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.FileHistory
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.rendering.spannables.ArticleLinkSpan
import com.door43.translationstudio.rendering.spannables.PassageLinkSpan
import com.door43.translationstudio.rendering.spannables.TranslationWordLinkSpan
import com.door43.translationstudio.rendering.spannables.USFMNoteSpan
import com.door43.translationstudio.rendering.spannables.USFMVerseSpan
import com.door43.translationstudio.rendering.spannables.USXVerseSpan
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.translationstudio.ui.navigation.ComponentScope
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import com.door43.translationstudio.ui.translate.Footnote
import com.door43.translationstudio.ui.translate.FootnoteAction
import com.door43.translationstudio.ui.translate.ModeComponent
import com.door43.translationstudio.ui.translate.ModeComponent.Action
import com.door43.translationstudio.ui.translate.ReviewItem
import com.door43.translationstudio.ui.translate.TranslateComponent
import com.door43.translationstudio.ui.translate.TranslateComponent.Companion.SEARCH_SOURCE
import com.door43.translationstudio.ui.translate.TranslationHelp
import com.door43.usecases.RenderHelps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.revwalk.RevCommit
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.util.Locale
import java.util.regex.Pattern

private val USFM_CONSECUTIVE_VERSE_MARKERS =
    Pattern.compile("\\\\v\\s(\\d+(-\\d+)?)\\s*\\\\v\\s(\\d+(-\\d+)?)")

private val CONSECUTIVE_VERSE_MARKERS =
    Pattern.compile("(<verse [^>]+/>\\s*){2}")

private val VERSE_MARKER =
    Pattern.compile("<verse\\s+number=\"(\\d+)\"[^>]*>")

private val USFM_VERSE_MARKER =
    Pattern.compile(USFMVerseSpan.PATTERN)

data class IndexWord(
    val slug: String,
    val title: String
)

sealed class Help {
    open val title: String = ""
    open val body: AnnotatedString = AnnotatedString("")

    data class Notes(
        override val title: String,
        override val body: AnnotatedString
    ) : Help()

    data class Words(
        override val title: String,
        override val body: AnnotatedString,
        val rcSlug: String
    ) : Help()

    data class Questions(
        override val title: String,
        override val body: AnnotatedString
    ) : Help()

    data class Index(
        val rcSlug: String,
        val words: List<IndexWord>
    ) : Help()
}

enum class TargetMode {
    MARKER,
    EDIT,
    COMPLETE
}

data class SearchState(
    val query: String = "",
    val subject: SearchSubject = SearchSubject.SOURCE,
    val matchingItemIds: List<String> = emptyList(),
    val currentMatchIndex: Int = -1
) {
    val matchCount: Int
        get() = matchingItemIds.size

    val currentItemId: String?
        get() = matchingItemIds.getOrNull(currentMatchIndex)
}

sealed class MarkAllDialogState {
    data object Confirm : MarkAllDialogState()
    data class Result(val marked: Int, val total: Int) : MarkAllDialogState()
}

interface ReviewModeComponent : ModeComponent<ReviewItem> {

    override val state: StateFlow<State>
    val filteredItems: StateFlow<List<ReviewItem>>

    data class State(
        val resourcesOpen: Boolean = false,
        val help: Help? = null,
        val url: String? = null,
        val chunkToDone: ReviewItem? = null,
        val search: SearchState? = null,
        val markAllDoneState: MarkAllDialogState? = null,
        val mergeConflictFilterOn: Boolean = false,
        override val footnote: Footnote? = null
    ) : ModeComponent.State

    sealed interface Action : ModeComponent.Action {
        data class ItemTextChanged(val item: ReviewItem, val text: String) : Action
        data class OpenResources(val value: Boolean) : Action
        data class RenderHelps(val item: ReviewItem) : Action
        data class OpenHelp(val item: HelpItem) : Action
        data class OpenIndex(val rcSlug: String) : Action
        data class OpenWord(val rcSlug: String, val slug: String) : Action
        data class ToggleEdit(val item: ReviewItem) : Action
        data class ToggleDoneClicked(val item: ReviewItem) : Action
        data class ToggleDoneConfirmed(val confirm: Boolean) : Action
        data object MarkAllDoneClicked : Action
        data class MarkAllDoneConfirmed(val confirm: Boolean) : Action
        data class Undo(val item: ReviewItem) : Action
        data class Redo(val item: ReviewItem) : Action
        data class AddNoteClicked(val item: ReviewItem, val caretPosition: Int = -1) : Action
        data object ClearHelp : Action
        data object CleanUrl : Action
        data object OpenSearch : Action
        data object CloseSearch : Action
        data class UpdateSearchQuery(val query: String) : Action
        data class SetSearchSubject(val subject: SearchSubject) : Action
        data object NextMatch : Action
        data object PrevMatch : Action
        data class DragDropVerse(
            val item: ReviewItem,
            val machineReadable: String,
            val verseRawStart: Int,
            val verseRawEnd: Int,
            val targetRawPosition: Int
        ) : Action
        data class SelectConflict(val item: ReviewItem, val index: Int) : Action
        data class SetMergeConflictFilterOn(val value: Boolean) : Action
    }
}

class DefaultReviewModeComponent(
    componentContext: ComponentContext,
    private val sharedState: StateFlow<TranslateComponent.SharedState>,
    private val eventSender: SendChannel<TranslateComponent.Event>,
    private val loadChunks: suspend (TranslationViewMode) -> List<Chunk>
) : ReviewModeComponent,
    ComponentContext by componentContext,
    KoinComponent, ComponentScope,
    ProgressOwner, ModeComponent<ReviewItem> {

    private val application: Application by inject()
    private val prefRepository: IPreferenceRepository by inject()
    private val renderHelps: RenderHelps by inject()
    private val renderingProvider: RenderingProvider by inject()
    private val library: Door43Client by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow(ReviewModeComponent.State())
    override val state: StateFlow<ReviewModeComponent.State> = _state

    private val _items = MutableStateFlow<List<ReviewItem>>(emptyList())
    override val items: StateFlow<List<ReviewItem>> = _items

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val sourceContainer: ResourceContainer?
        get() = sharedState.value.resourceContainer

    private val searchConfig = _state.map {
        Triple(it.mergeConflictFilterOn, it.search?.query, it.search?.subject)
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val filteredItems: StateFlow<List<ReviewItem>> =
        combine(items, searchConfig) { items, config ->
            items to config
        }
            .flatMapLatest { (items, config) ->
                processSearchAndFilter(items, config)
            }
            .onEach(::updateSearchMetadata)
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    init {
        sharedState
            .map { it.resourceContainer }
            .distinctUntilChanged()
            .onEach { handleResourceChange() }
            .launchIn(coroutineScope)

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override suspend fun handleResourceChange() {
        if (_state.value.resourcesOpen && _state.value.help != null) {
            _state.update { it.copy(help = null) }
        }
        val chunks = loadChunks(TranslationViewMode.REVIEW)
        mapChunksToItems(chunks)
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.OpenFootnote -> onShowFootnote(action.note)
            is Action.DeleteNote -> onDeleteFootnote(action.note)
            is Action.SaveFootnote -> onSaveFootnote(action.note)
            is Action.ClearFootnote -> { _state.update { it.copy(footnote = null) } }
            is ReviewModeComponent.Action.OpenResources -> openResources(action.value)
            is ReviewModeComponent.Action.RenderHelps -> onRenderHelps(action.item)
            is ReviewModeComponent.Action.OpenHelp -> onOpenHelpItem(action.item)
            is ReviewModeComponent.Action.OpenIndex -> openIndex(action.rcSlug)
            is ReviewModeComponent.Action.OpenWord -> renderWord(action.rcSlug, action.slug)
            is ReviewModeComponent.Action.ToggleEdit -> toggleEdit(action.item)
            is ReviewModeComponent.Action.ToggleDoneClicked -> toggleDoneClicked(action.item)
            is ReviewModeComponent.Action.ToggleDoneConfirmed -> toggleDoneConfirmed(action.confirm)
            is ReviewModeComponent.Action.MarkAllDoneConfirmed -> markAllDoneConfirmed(action.confirm)
            is ReviewModeComponent.Action.ItemTextChanged -> onItemTextChanged(action.item, action.text)
            is ReviewModeComponent.Action.Undo -> onUndo(action.item)
            is ReviewModeComponent.Action.Redo -> onRedo(action.item)
            is ReviewModeComponent.Action.AddNoteClicked -> onAddNoteClicked(action.item, action.caretPosition)
            is ReviewModeComponent.Action.DragDropVerse -> onDragDropVerse(action)
            is ReviewModeComponent.Action.UpdateSearchQuery -> updateSearchQuery(action.query)
            is ReviewModeComponent.Action.SetSearchSubject -> setSearchSubjectAndSearch(action.subject)
            is ReviewModeComponent.Action.SelectConflict -> selectConflict(action.item, action.index)
            is ReviewModeComponent.Action.SetMergeConflictFilterOn -> setMergeConflictFilterOn(action.value)
            is ReviewModeComponent.Action.MarkAllDoneClicked -> markAllDoneClicked()
            is ReviewModeComponent.Action.ClearHelp -> _state.update { it.copy(help = null) }
            is ReviewModeComponent.Action.CleanUrl -> _state.update { it.copy(url = null) }
            is ReviewModeComponent.Action.OpenSearch -> openSearch()
            is ReviewModeComponent.Action.CloseSearch -> closeSearch()
            is ReviewModeComponent.Action.NextMatch -> navigateMatch(forward = true)
            is ReviewModeComponent.Action.PrevMatch -> navigateMatch(forward = false)
        }
    }

    override fun updateItem(item: ReviewItem) {
        _items.value =  _items.value.map {
            if (it.id == item.id) item else it
        }
    }

    override fun updateItems(items: List<ReviewItem>) {
        _items.value = items
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

    override fun onShowFootnote(note: Footnote) {
        _state.update { it.copy(footnote = note) }
    }

    private fun mapChunksToItems(chunks: List<Chunk>) {
        launchWithProgress(application.getString(R.string.loading_sources)) {
            val items = withContext(Dispatchers.Default) {
                chunks.map { prepareItem(it) }
            }
            updateItems(items)
        }
    }

    private fun prepareItem(
        chunk: Chunk,
        targetMode: TargetMode = TargetMode.MARKER,
        loadHistory: Boolean = false
    ): ReviewItem {
        val searchQuery = _state.value.search?.query
        val searchSource = _state.value.search?.subject == SearchSubject.SOURCE

        val chunkId = "${chunk.chapterSlug}-${chunk.chunkSlug}"
        val (pt, ct, ft) = prepareTranslations(chunk)
        val (sourceText, renderedSourceText) = prepareSource(
            chunkId = chunkId,
            chunk = chunk,
            searchQuery = if (searchSource) searchQuery else null
        )

        val item = ReviewItem(
            id = chunkId,
            chunk = chunk,
            sourceText = sourceText,
            targetText = "",
            renderedSourceText = renderedSourceText,
            renderedTargetText = AnnotatedString(""),
            pt = pt,
            ct = ct,
            ft = ft,
            targetMode = targetMode
        )

        // Chunk completion status overrides target mode
        val realTargetMode = if (item.isComplete) TargetMode.COMPLETE else targetMode
        val (targetText, renderedTargetText) = prepareTarget(
            chunkId = chunkId,
            chunk = chunk,
            targetMode = realTargetMode,
            searchQuery = if (!searchSource) searchQuery else null
        )

        val history = if (loadHistory) {
            createFileHistory(item)?.also { it.loadCommits() }
        } else null

        val prepared = item.copy(
            targetText = targetText,
            renderedTargetText = renderedTargetText,
            targetMode = realTargetMode,
            fileHistory = history
        )
        return prepared
    }

    private fun prepareSource(
        chunkId: String,
        chunk: Chunk,
        searchQuery: String? = null
    ): Pair<String, AnnotatedString> {
        val text = chunk.source.readChunk(chunk.chapterSlug, chunk.chunkSlug)
        return text to renderSourceText(
            chunkId = chunkId,
            translationFormat = chunk.sourceTranslationFormat,
            sourceText = text,
            searchQuery = searchQuery
        )
    }

    private fun prepareTarget(
        chunkId: String,
        chunk: Chunk,
        targetMode: TargetMode,
        searchQuery: String? = null
    ): Pair<String, AnnotatedString> {
        val text = fetchTargetText(chunk.target, chunk.chapterSlug, chunk.chunkSlug)
        val verseDisplay = when (targetMode) {
            TargetMode.MARKER -> VerseDisplay.PIN
            TargetMode.EDIT -> VerseDisplay.RAW
            TargetMode.COMPLETE -> VerseDisplay.NUMBER
        }
        return text to renderTargetText(
            chunkId = chunkId,
            translationFormat = chunk.targetTranslationFormat,
            targetText = text,
            verseDisplay = verseDisplay,
            footnoteAction = if (targetMode != TargetMode.COMPLETE) {
                FootnoteAction.ACTIONS
            } else FootnoteAction.VIEW,
            searchQuery = searchQuery,
            onVerseClick = {
                showSnackBar(application.getString(R.string.long_click_to_drag))
            }
        )
    }

    private fun onDragDropVerse(action: ReviewModeComponent.Action.DragDropVerse) {
        val item = action.item
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val originalText = fetchTargetText(
                    item.chunk.target, item.chunk.chapterSlug, item.chunk.chunkSlug
                )
                val newText = VerseMarkerDrag.moveVerseByRawPosition(
                    text = originalText,
                    verseRawStart = action.verseRawStart,
                    verseRawEnd = action.verseRawEnd,
                    marker = action.machineReadable,
                    targetRawPosition = action.targetRawPosition
                )
                item.saveTranslation(newText)
                updateItem(prepareItem(item.chunk, TargetMode.MARKER))
            }
        }
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

    private fun openSearch() {
        val lastSubject = try {
            SearchSubject.valueOf(getLastSearchSource())
        } catch (_: Exception) {
            SearchSubject.SOURCE
        }
        _state.update {
            it.copy(search = SearchState(subject = lastSubject))
        }
    }

    private fun closeSearch() {
        _state.update { it.copy(search = null) }
    }

    private fun updateSearchQuery(query: String) {
        _state.update {
            it.copy(search = it.search?.copy(query = query))
        }
    }

    private fun setSearchSubjectAndSearch(subject: SearchSubject) {
        setLastSearchSource(subject)
        _state.update {
            it.copy(search = it.search?.copy(subject = subject))
        }
    }

    private fun navigateMatch(forward: Boolean) {
        _state.update { state ->
            val search = state.search ?: return@update state
            if (search.matchingItemIds.isEmpty()) return@update state
            val newIndex = if (forward) {
                if (search.currentMatchIndex < search.matchCount - 1) {
                    search.currentMatchIndex + 1
                } else 0
            } else {
                if (search.currentMatchIndex > 0) {
                    search.currentMatchIndex - 1
                } else search.matchCount - 1
            }
            state.copy(search = search.copy(currentMatchIndex = newIndex))
        }
    }

    fun getLastSearchSource(): String {
        val defaultSource = SearchSubject.SOURCE.name.uppercase(
            Locale.getDefault()
        )
        return prefRepository.getDefaultPref(
            SEARCH_SOURCE,
            defaultSource
        )
    }

    fun setLastSearchSource(subject: SearchSubject) {
        prefRepository.setDefaultPref(
            SEARCH_SOURCE,
            subject.name.uppercase(Locale.getDefault())
        )
    }

    private fun processSearchAndFilter(
        items: List<ReviewItem>,
        config: Triple<Boolean, String?, SearchSubject?>
    ) = flow {
        val (filterOn, query, subject) = config

        val processed = withContext(Dispatchers.Default) {
            val baseItems = if (filterOn) items.filter { it.hasMergeConflict } else items
            baseItems.map { item ->
                decorateItemWithSearch(item, query, subject)
            }
        }
        emit(processed)
    }

    private fun decorateItemWithSearch(
        item: ReviewItem,
        query: String?,
        subject: SearchSubject?
    ): ReviewItem {
        val searchSource = subject == SearchSubject.SOURCE

        val renderedSource = renderSourceText(
            chunkId = item.id,
            translationFormat = item.chunk.sourceTranslationFormat,
            sourceText = item.sourceText,
            searchQuery = if (searchSource) query else null
        )

        val verseDisplay = when (item.targetMode) {
            TargetMode.MARKER -> VerseDisplay.PIN
            TargetMode.EDIT -> VerseDisplay.RAW
            TargetMode.COMPLETE -> VerseDisplay.NUMBER
        }

        val renderedTarget = renderTargetText(
            chunkId = item.id,
            translationFormat = item.chunk.targetTranslationFormat,
            targetText = item.targetText,
            verseDisplay = verseDisplay,
            footnoteAction = if (item.targetMode != TargetMode.COMPLETE) {
                FootnoteAction.ACTIONS
            } else FootnoteAction.VIEW,
            searchQuery = if (!searchSource) query else null,
            onVerseClick = {
                showSnackBar(application.getString(R.string.long_click_to_drag))
            }
        )

        return item.copy(
            renderedSourceText = renderedSource,
            renderedTargetText = renderedTarget
        )
    }

    private fun updateSearchMetadata(items: List<ReviewItem>) {
        val search = _state.value.search ?: return
        val query = search.query.lowercase()

        if (query.isNotBlank()) {
            val searchSource = search.subject == SearchSubject.SOURCE
            val matchingIds = items.filter { item ->
                val textToSearch = if (searchSource) {
                    item.renderedSourceText
                } else item.renderedTargetText
                textToSearch.text.lowercase().contains(query)
            }.map { it.id }

            val currentIndex = when {
                matchingIds.isEmpty() -> -1
                _state.value.search?.currentMatchIndex == -1 -> 0
                else -> _state.value.search?.currentMatchIndex ?: 0
            }

            _state.update { state ->
                state.copy(
                    search = state.search?.copy(
                        matchingItemIds = matchingIds,
                        currentMatchIndex = currentIndex
                    )
                )
            }
        }
    }

    private fun selectConflict(item: ReviewItem, index: Int) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                item.mergeItems.getOrNull(index)?.let { conflict ->
                    item.saveTranslation(conflict.toString())
                    updateItem(prepareItem(item.chunk))
                }
            }
        }
    }

    private fun setMergeConflictFilterOn(value: Boolean) {
        _state.update { state ->
            state.copy(mergeConflictFilterOn = value)
        }
    }

    private fun openResources(value: Boolean) {
        _state.update { it.copy(resourcesOpen = value) }
    }

    private fun onRenderHelps(item: ReviewItem) {
        if (!item.helps.isEmpty()) return

        coroutineScope.launch {
            val helps = withContext(Dispatchers.IO) {
                renderHelps.execute(item.chunk)
            }
            updateItem(item.copy(helps = helps))
        }
    }

    private fun onOpenHelpItem(item: HelpItem) {
        when (item) {
            is HelpItem.Note -> renderTranslationHelp(item.data)
            is HelpItem.Word -> renderWord(item.rcSlug, item.data.chapter)
            is HelpItem.Question -> renderTranslationHelp(item.data, true)
        }
    }

    private fun renderTranslationHelp(note: TranslationHelp, isTq: Boolean = false) {
        val title = note.title
        val body = renderHelpContents(note.body)
        val help = if (!isTq) {
            Help.Notes(title, body)
        } else Help.Questions(title, body)

        _state.update { it.copy(help = help) }
    }

    private fun renderWord(rcSlug: String, chapterSlug: String) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                getResourceContainer(rcSlug)?.let { twRc ->
                    val word = twRc.readChunk(chapterSlug, "01")
                    val pattern = Pattern.compile("#+([^\\n]+)\\n+([\\s\\S]*)")
                    val match = pattern.matcher(word)
                    var description = ""
                    var title = ""

                    if (match.find()) {
                        title = match.group(1) ?: ""
                        description = match.group(2) ?: ""
                    }

                    val body = renderHelpContents(description, twRc)

                    _state.update { it.copy(help = Help.Words(title, body, rcSlug)) }
                }
            }
        }
    }

    private fun renderHelpContents(
        contents: String,
        twRc: ResourceContainer? = null
    ): AnnotatedString {
        val enableTmLinks = prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_ENABLE_TM_LINKS,
            false
        )

        val sourceRC = sourceContainer
        val closestRc = sourceRC?.let { rc ->
            getClosestTwRc(rc.language.slug)
        }

        val renderer = renderingProvider.createHtmlRenderer { span ->
            var result = false
            when (span) {
                is ArticleLinkSpan -> {
                    val title = application.getString(
                        R.string.tm_title,
                        span.section,
                        span.slug
                    )
                    span.setTitle(title)
                    result = enableTmLinks
                }
                is PassageLinkSpan -> {
                    twRc?.let { rc ->
                        val chunk = rc.readChunk(span.chapterId, span.frameId)
                        val verseTitle = Frame.parseVerseTitle(
                            chunk,
                            TranslationFormat.parse(rc.contentMimeType)
                        )
                        val chapterId = try {
                            span.chapterId.toInt().toString()
                        } catch (_: Exception) {
                            span.chapterId
                        }
                        val title = "${rc.readChunk("front", "title")} $chapterId:$verseTitle"
                        span.setTitle(title)
                        result = chunk.isNotEmpty()
                    }
                }
                is TranslationWordLinkSpan -> {
                    if (sourceRC != null) {
                        val titlePattern = Pattern.compile("#(.*)")
                        closestRc?.let { rc ->
                            val closestWord = rc.readChunk(
                                span.machineReadable,
                                "01"
                            )
                            if (closestWord.isNotEmpty()) {
                                val linkMatch = titlePattern.matcher(closestWord.trim())
                                var title = span.machineReadable
                                if (linkMatch.find()) {
                                    title = linkMatch.group(1) ?: title
                                }
                                span.title = title
                                result = true
                            }
                        }
                    }
                }
            }
            result
        }

        val html = renderer.toAnnotatedHtml(contents)
        val body = ComposeTextAdapter.convertHtml(
            html = html,
            onLinkClick = { link ->
                when (link) {
                    is LinkData.TranslationWord -> {
                        twRc?.let { rc ->
                            _state.update { it.copy(help = null) }
                            renderWord(rc.slug, link.id)
                        }
                    }
                    is LinkData.Article -> {
                        val baseUrl = prefRepository.getDefaultPref(
                            IPreferenceRepository.KEY_PREF_TM_URL,
                            application.getString(R.string.pref_default_tm_url),
                            String::class.javaObjectType
                        )
                        val span = link.toSpan()
                        val url = "$baseUrl?section=${span.section}#${span.slug}"
                        _state.update { it.copy(url = url) }
                    }
                    else -> {}
                }
            },
            linkFilter = {
                it is LinkData.TranslationWord || it is LinkData.Article
            }
        )

        return body
    }

    private fun openIndex(rcSlug: String) {
        coroutineScope.launch {
            val words = withContext(Dispatchers.IO) {
                getResourceContainer(rcSlug)?.let { rc ->
                    val chapters = rc.chapters()
                    val words = listOf(*chapters).sorted()
                    val titlePattern = Pattern.compile("#(.*)")

                    words.map { slug ->
                        val match = titlePattern.matcher(rc.readChunk(slug, "01"))
                        val title = if (match.find()) match.group(1) else slug
                        IndexWord(slug, title)
                    }
                } ?: emptyList()
            }

            _state.update { it.copy(help = Help.Index(rcSlug, words)) }
        }
    }

    private fun toggleEdit(item: ReviewItem) {
        coroutineScope.launch {
            doToggleEdit(item)
        }
    }

    private suspend fun doToggleEdit(item: ReviewItem) {
        withContext(Dispatchers.IO) {
            val targetMode = if (item.targetMode == TargetMode.EDIT) {
                TargetMode.MARKER
            } else TargetMode.EDIT

            val loadHistory: Boolean
            if (targetMode == TargetMode.MARKER) {
                addMissingVerses(item)
                item.chunk.target.commit()
                loadHistory = false
            } else {
                loadHistory = true
            }
            val updated = prepareItem(item.chunk, targetMode, loadHistory)
            updateItem(updated)
        }
    }

    private fun addMissingVerses(item: ReviewItem) {
        if (item.isComplete) return

        val currentText = fetchTargetText(
            item.chunk.target,
            item.chunk.chapterSlug,
            item.chunk.chunkSlug
        )
        if (currentText.isEmpty()) return

        val sourceVerseRange = RenderingProvider.getVerseRange(
            item.sourceText,
            item.chunk.sourceTranslationFormat
        )
        if (sourceVerseRange.isEmpty()) return

        val format = item.chunk.targetTranslationFormat
        val versePattern = if (format == TranslationFormat.USFM) {
            Pattern.compile(USFMVerseSpan.PATTERN)
        } else {
            Pattern.compile(USXVerseSpan.PATTERN)
        }

        val existingVerses = mutableSetOf<Int>()
        val matcher = versePattern.matcher(currentText)
        while (matcher.find()) {
            val group = matcher.group(1) ?: continue
            val dashIndex = group.indexOf('-')
            if (dashIndex > 0) {
                val start = group.substring(0, dashIndex).toIntOrNull() ?: continue
                val end = group.substring(dashIndex + 1).toIntOrNull() ?: continue
                for (v in start..end) existingVerses.add(v)
            } else {
                group.toIntOrNull()?.let { existingVerses.add(it) }
            }
        }

        val min = sourceVerseRange[0]
        val max = if (sourceVerseRange.size > 1) sourceVerseRange[1] else min

        val missing = (min..max).filter { it !in existingVerses }
        if (missing.isEmpty()) return

        val prefix = missing.joinToString("") { v ->
            if (format == TranslationFormat.USFM) {
                "\\v $v "
            } else {
                "<verse number=\"$v\" style=\"v\" />"
            }
        }

        val updatedText = prefix + currentText
        item.saveTranslation(updatedText)
    }

    private fun toggleDoneClicked(item: ReviewItem) {
        coroutineScope.launch {
            val shouldComplete = item.targetMode != TargetMode.COMPLETE
            if (shouldComplete) {
                _state.value = _state.value.copy(chunkToDone = item)
            } else {
                updateDoneStatus(item, false)
            }
        }
    }

    private fun toggleDoneConfirmed(confirm: Boolean) {
        coroutineScope.launch {
            if (confirm) {
                _state.value.chunkToDone?.let { item ->
                    updateDoneStatus(item, true)
                }
            }
            _state.value = _state.value.copy(
                chunkToDone = null
            )
        }
    }

    private fun markAllDoneClicked() {
        _state.value = _state.value.copy(
            markAllDoneState = MarkAllDialogState.Confirm
        )
    }

    private fun markAllDoneConfirmed(confirm: Boolean) {
        _state.update { it.copy(markAllDoneState = null) }

        if (!confirm) {
            return
        }

        launchWithProgress(application.getString(R.string.loading)) {
            val marked = withContext(Dispatchers.IO) {
                var marked = 0
                for (item in items.value) {
                    try {
                        if (item.targetMode == TargetMode.EDIT) {
                            doToggleEdit(item)
                        }
                        markChunkCompleted(item)
                        marked++
                    } catch (e: Exception) {
                        Logger.e(
                            this::class.simpleName,
                            "Error marking chunk done: ${item.id}",
                            e
                        )
                    }
                }

                // Commit if any chunks were marked
                if (marked > 0) {
                    try {
                        items.value.firstOrNull()?.chunk?.target?.commit()
                    } catch (e: Exception) {
                        Logger.e(
                            this::class.simpleName,
                            "Failed to commit translation",
                            e
                        )
                    }
                }

                val chunks = items.value.map { it.chunk }
                mapChunksToItems(chunks)

                marked
            }

            _state.value = _state.value.copy(
                markAllDoneState = MarkAllDialogState.Result(marked, items.value.size)
            )
        }
    }

    private suspend fun updateDoneStatus(item: ReviewItem, shouldComplete: Boolean) {
        val updated = withContext(Dispatchers.IO) {
            try {
                if (shouldComplete) {
                    markChunkCompleted(item)
                } else {
                    item.chunk.reopen()
                }
                item.chunk.target.commit()
                prepareItem(item.chunk)
            } catch (e: IllegalStateException) {
                e.message?.let { showSnackBar(it) }
                item
            }
        }
        updateItem(updated)
    }

    private fun markChunkCompleted(item: ReviewItem) {
        // Check for empty translation.
        if (item.targetText.isEmpty()) {
            throw IllegalStateException(application.getString(R.string.translate_first))
        }

        var lowVerse = -1
        var highVerse = 999999999
        val range = RenderingProvider.getVerseRange(
            item.targetText,
            item.chunk.targetTranslationFormat
        )
        if (range.isNotEmpty()) {
            lowVerse = range[0]
            highVerse = lowVerse
            if (range.size > 1) {
                highVerse = range[1]
            }
        }

        // Check for contiguous verse numbers.
        var matcher = if (item.chunk.targetTranslationFormat == TranslationFormat.USFM) {
            USFM_CONSECUTIVE_VERSE_MARKERS.matcher(item.targetText)
        } else {
            CONSECUTIVE_VERSE_MARKERS.matcher(item.targetText)
        }
        if (matcher.find()) {
            throw IllegalStateException(
                application.getString(R.string.consecutive_verse_markers)
            )
        }

        // check for invalid verse markers
        var error = 0
        matcher = if (item.chunk.targetTranslationFormat == TranslationFormat.USFM) {
            USFM_VERSE_MARKER.matcher(item.targetText)
        } else {
            VERSE_MARKER.matcher(item.targetText)
        }
        val sourceVerseRange = RenderingProvider.getVerseRange(
            item.sourceText,
            item.chunk.sourceTranslationFormat
        )
        if (sourceVerseRange.isNotEmpty()) {
            val min = sourceVerseRange[0]
            var max = min
            if (sourceVerseRange.size == 2) max = sourceVerseRange[1]
            while (matcher.find()) {
                val verseStr = matcher.group(1)
                var verse = -1
                if (verseStr != null) {
                    try {
                        verse = verseStr.toInt()
                    } catch (_: Exception) {}
                }
                if (verse !in min..max) {
                    error = R.string.outofrange_verse_marker
                    break
                }
            }
        }
        if (error > 0) {
            throw IllegalStateException(application.getString(error))
        }

        // Check for out-of-order verse markers.
        matcher = if (item.chunk.targetTranslationFormat == TranslationFormat.USFM) {
            USFM_VERSE_MARKER.matcher(item.targetText)
        } else {
            VERSE_MARKER.matcher(item.targetText)
        }
        var lastVerseSeen = 0
        while (matcher.find()) {
            val verseStr = matcher.group(1)
            var currentVerse = -1
            if (verseStr != null) {
                try {
                    currentVerse = verseStr.toInt()
                } catch (_: Exception) {}
            }
            if (currentVerse <= lastVerseSeen) {
                error = if (currentVerse == lastVerseSeen) {
                    R.string.duplicate_verse_marker
                } else {
                    R.string.outoforder_verse_markers
                }
                break
            } else if (currentVerse !in lowVerse..highVerse) {
                error = R.string.outofrange_verse_marker
                break
            } else {
                lastVerseSeen = currentVerse
            }
        }
        if (error > 0) {
            throw IllegalStateException(application.getString(error))
        }

        // Everything looks good so far.
        val success = item.chunk.close()

        if (!success) {
            throw IllegalStateException(application.getString(R.string.failed_to_commit_chunk))
        }
    }

    private fun onItemTextChanged(item: ReviewItem, text: String) {
        coroutineScope.launch {
            item.saveTranslation(text)
        }
    }

    private fun onUndo(item: ReviewItem) {
        navigateHistory(item) { history ->
            if (history.atHead && !item.chunk.target.isClean) {
                try {
                    item.chunk.target.commitSync()
                    history.loadCommits()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            history.previous
        }
    }

    private fun onRedo(item: ReviewItem) {
        navigateHistory(item) { it.next }
    }

    private fun navigateHistory(
        item: ReviewItem,
        navigate: (FileHistory) -> RevCommit?
    ) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val history = item.fileHistory ?: return@withContext
                val commit = navigate(history) ?: return@withContext

                val text = try {
                    history.read(commit)
                } catch (e: IllegalStateException) {
                    Logger.w(
                        this@DefaultReviewModeComponent::class.simpleName,
                        "History navigation past end of file history",
                        e
                    )
                    ""
                } catch (e: Exception) {
                    Logger.w(
                        this@DefaultReviewModeComponent::class.simpleName,
                        "History read exception",
                        e
                    )
                    null
                } ?: return@withContext

                restartAutoCommitTimer()
                item.saveTranslation(text)

                val rendered = renderTargetText(
                    chunkId = item.id,
                    translationFormat = item.chunk.targetTranslationFormat,
                    targetText = text,
                    verseDisplay = VerseDisplay.RAW,
                    footnoteAction = FootnoteAction.ACTIONS
                )
                updateItem(item.copy(
                    targetText = text,
                    renderedTargetText = rendered
                ))
            }
        }
    }

    private fun onAddNoteClicked(item: ReviewItem, caretPosition: Int = -1) {
        onShowFootnote(
            Footnote(
                text = "",
                machineReadable = "",
                chunkId = item.id,
                insertPosition = caretPosition,
                action = FootnoteAction.EDIT
            )
        )
    }

    private fun createFileHistory(item: ReviewItem): FileHistory? {
        return when {
            item.chunk.isChapterReference -> item.chunk.target.getChapterReferenceHistory(
                item.ct
            )
            item.chunk.isChapterTitle -> item.chunk.target.getChapterTitleHistory(
                item.ct
            )
            item.chunk.isProjectTitle -> item.chunk.target.projectTitleHistory
            item.chunk.isChunk -> item.chunk.target.getFrameHistory(item.ft)
            else -> null
        }
    }

    private fun onDeleteFootnote(note: Footnote) {
        _state.update { it.copy(footnote = null) }
        replaceFootnoteInTarget(note, replacement = "")
    }

    private fun onSaveFootnote(note: Footnote) {
        _state.update { it.copy(footnote = null) }
        val newCode = if (note.text.isNotEmpty()) {
            USFMNoteSpan.generateFootnote(note.text).machineReadable
        } else ""

        if (note.machineReadable.isEmpty()) {
            insertFootnoteInTarget(note, newCode)
        } else {
            replaceFootnoteInTarget(note, newCode)
        }
    }

    private fun replaceFootnoteInTarget(note: Footnote, replacement: String) {
        val item = items.value.find { it.id == note.chunkId } ?: return
        val currentText = fetchTargetText(
            item.chunk.target, item.chunk.chapterSlug, item.chunk.chunkSlug
        )

        val start: Int
        val end: Int
        if (note.start >= 0 && note.end >= 0 &&
            note.end <= currentText.length
        ) {
            start = note.start
            end = note.end
        } else {
            // Fallback to string search
            val idx = currentText.indexOf(note.machineReadable)
            if (idx < 0) return
            start = idx
            end = idx + note.machineReadable.length
        }

        val newText = currentText.substring(0, start) +
                replacement +
                currentText.substring(end)
        saveAndRefreshItem(item, newText)
    }

    private fun insertFootnoteInTarget(note: Footnote, footnoteCode: String) {
        val item = items.value.find { it.id == note.chunkId } ?: return
        val currentText = fetchTargetText(
            item.chunk.target, item.chunk.chapterSlug, item.chunk.chunkSlug
        )
        val pos = note.insertPosition
        val newText = if (pos in 0..currentText.length) {
            currentText.substring(0, pos) + footnoteCode + currentText.substring(pos)
        } else {
            currentText + footnoteCode
        }
        saveAndRefreshItem(item, newText)
    }

    private fun saveAndRefreshItem(item: ReviewItem, newText: String) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                item.saveTranslation(newText)
                updateItem(prepareItem(item.chunk, item.targetMode))
            }
        }
    }

    private fun getClosestTwRc(languageSlug: String): ResourceContainer? {
        return ContainerCache.cacheClosest(
            library,
            languageSlug,
            "bible",
            "tw"
        )
    }

    private fun getResourceContainer(slug: String): ResourceContainer? {
        return ContainerCache.get(slug)
    }

    private fun showSnackBar(message: String) {
        eventSender.trySend(TranslateComponent.Event.SnackbarMessage(message))
    }

    private fun restartAutoCommitTimer() {
        eventSender.trySend(TranslateComponent.Event.RestartAutoCommitTimer)
    }
}