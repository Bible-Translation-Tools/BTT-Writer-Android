package com.door43.translationstudio.ui.translate.review

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewModelScope
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.FileHistory
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.spannables.ArticleLinkSpan
import com.door43.translationstudio.rendering.spannables.PassageLinkSpan
import com.door43.translationstudio.rendering.spannables.TranslationWordLinkSpan
import com.door43.translationstudio.rendering.spannables.USFMNoteSpan
import com.door43.translationstudio.rendering.spannables.USFMVerseSpan
import com.door43.translationstudio.rendering.spannables.USXVerseSpan
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_ENABLE_TM_LINKS
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_TM_URL
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import com.door43.translationstudio.ui.translate.Footnote
import com.door43.translationstudio.ui.translate.ModeAction
import com.door43.translationstudio.ui.translate.ModeState
import com.door43.translationstudio.ui.translate.ModeViewModel
import com.door43.translationstudio.ui.translate.ReviewItem
import com.door43.translationstudio.ui.translate.SharedState
import com.door43.translationstudio.ui.translate.TargetTranslationActivity.Companion.SEARCH_SOURCE
import com.door43.translationstudio.ui.translate.TranslationHelp
import com.door43.translationstudio.ui.viewmodels.TargetEvent
import com.door43.usecases.RenderHelps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val active: Boolean = false,
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

data class ReviewState(
    val resourcesOpen: Boolean = false,
    val help: Help? = null,
    val url: String? = null,
    val chunkToDone: ReviewItem? = null,
    val search: SearchState = SearchState()
) : ModeState

sealed interface ReviewAction : ModeAction {
    data class ItemTextChanged(val item: ReviewItem, val text: String) : ReviewAction
    data class OpenResources(val value: Boolean) : ReviewAction
    data class RenderHelps(val item: ReviewItem) : ReviewAction
    data class OpenHelp(val item: HelpItem) : ReviewAction
    data class OpenIndex(val rcSlug: String) : ReviewAction
    data class OpenWord(val rcSlug: String, val slug: String) : ReviewAction
    data class ToggleEdit(val item: ReviewItem) : ReviewAction
    data class ToggleDoneClicked(val item: ReviewItem) : ReviewAction
    data class ToggleDoneConfirmed(val confirm: Boolean) : ReviewAction
    data class Undo(val item: ReviewItem) : ReviewAction
    data class Redo(val item: ReviewItem) : ReviewAction
    data class AddNoteClicked(val item: ReviewItem, val caretPosition: Int = -1) : ReviewAction
    object ClearHelp : ReviewAction
    object CleanUrl : ReviewAction
    object OpenSearch : ReviewAction
    object CloseSearch : ReviewAction
    data class UpdateSearchQuery(val query: String) : ReviewAction
    data class SetSearchSubject(val subject: SearchSubject) : ReviewAction
    object NextMatch : ReviewAction
    object PrevMatch : ReviewAction
    data class DragDropVerse(
        val item: ReviewItem,
        val machineReadable: String,
        val verseRawStart: Int,
        val verseRawEnd: Int,
        val targetRawPosition: Int
    ) : ReviewAction
}

class ReviewModeViewModel(
    sharedState: StateFlow<SharedState>,
    event: SendChannel<TargetEvent>,
    private val prefRepository: IPreferenceRepository,
    private val renderHelps: RenderHelps,
    private val renderingProvider: RenderingProvider,
    private val library: Door43Client,
) : ModeViewModel<ReviewItem>(
    sharedState,
    TranslationViewMode.REVIEW,
    event
), KoinComponent {

    private val application: Application by inject()

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state

    private val sourceContainer: ResourceContainer?
        get() = sharedState.value.sourceContainer

    init {
        viewModelScope.launch {
            sharedState
                .map { it.sourceContainer }
                .distinctUntilChanged()
                .collect {
                    if (_state.value.resourcesOpen) {
                        _state.update { it.copy(help = null) }
                    }
                }
        }
    }

    override fun mapToChildType(chunks: List<Chunk>, onReady: (List<ReviewItem>) -> Unit) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.Default) {
                chunks
                    .chunked(5)
                    .flatMap { batch ->
                        batch.map { async { prepareItem(it) } }
                    }.awaitAll()
            }
            onReady(items)
        }
    }

    override fun onAction(action: ModeAction) {
        super.onAction(action)
        when (action) {
            is ReviewAction.OpenResources -> openResources(action.value)
            is ReviewAction.RenderHelps -> onRenderHelps(action.item)
            is ReviewAction.OpenHelp -> onOpenHelpItem(action.item)
            is ReviewAction.OpenIndex -> openIndex(action.rcSlug)
            is ReviewAction.OpenWord -> renderWord(action.rcSlug, action.slug)
            is ReviewAction.ToggleEdit -> toggleEdit(action.item)
            is ReviewAction.ToggleDoneClicked -> toggleDoneClicked(action.item)
            is ReviewAction.ToggleDoneConfirmed -> toggleDoneConfirmed(action.confirm)
            is ReviewAction.ItemTextChanged -> onItemTextChanged(action.item, action.text)
            is ReviewAction.Undo -> onUndo(action.item)
            is ReviewAction.Redo -> onRedo(action.item)
            is ReviewAction.AddNoteClicked -> onAddNoteClicked(action.item, action.caretPosition)
            is ReviewAction.DragDropVerse -> onDragDropVerse(action)
            ReviewAction.ClearHelp -> _state.update { it.copy(help = null) }
            ReviewAction.CleanUrl -> _state.update { it.copy(url = null) }
            ReviewAction.OpenSearch -> openSearch()
            ReviewAction.CloseSearch -> closeSearch()
            is ReviewAction.UpdateSearchQuery -> updateSearchQuery(action.query)
            is ReviewAction.SetSearchSubject -> setSearchSubjectAndSearch(action.subject)
            ReviewAction.NextMatch -> navigateMatch(forward = true)
            ReviewAction.PrevMatch -> navigateMatch(forward = false)
        }
    }

    private fun prepareItem(
        chunk: Chunk,
        targetMode: TargetMode = TargetMode.MARKER,
        loadHistory: Boolean = false
    ): ReviewItem {
        val chunkId = "${chunk.chapterSlug}-${chunk.chunkSlug}"
        val (pt, ct, ft) = prepareTranslations(chunk)
        val (sourceText, renderedSourceText) = prepareSource(chunkId, chunk)

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
        val (targetText, renderedTargetText) = prepareTarget(chunkId, chunk, realTargetMode)

        val history = if (loadHistory) {
            createFileHistory(item)?.also { it.loadCommits() }
        } else null

        val prepared = item.copy(
            targetText = targetText,
            renderedTargetText = renderedTargetText,
            targetMode = realTargetMode,
            fileHistory = history
        )
        return applySearchHighlightIfActive(prepared)
    }

    private fun applySearchHighlightIfActive(item: ReviewItem): ReviewItem {
        val search = _state.value.search
        if (!search.active || search.query.length < 2) return item

        val query = search.query
        val searchSource = search.subject == SearchSubject.SOURCE
        val renderedSource = renderSourceTextWithSearch(
            item.id, item.chunk.sourceTranslationFormat, item.sourceText,
            if (searchSource) query else null
        )
        val renderedTarget = renderTargetTextWithSearch(
            item.id, item.chunk, item.targetText, item.targetMode,
            if (!searchSource) query else null
        )
        return item.copy(
            renderedSourceText = renderedSource,
            renderedTargetText = renderedTarget
        )
    }

    private fun prepareSource(chunkId: String, chunk: Chunk): Pair<String, AnnotatedString> {
        val text = chunk.source.readChunk(chunk.chapterSlug, chunk.chunkSlug)
        return text to renderSourceText(chunkId, chunk.sourceTranslationFormat, text)
    }

    private fun prepareTarget(
        chunkId: String,
        chunk: Chunk,
        targetMode: TargetMode
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
            footnoteEditable = targetMode != TargetMode.COMPLETE,
            onVerseClick = {
                showSnackBar(application.getString(R.string.long_click_to_drag))
            }
        )
    }

    private fun onDragDropVerse(action: ReviewAction.DragDropVerse) {
        val item = action.item
        viewModelScope.launch {
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
            it.copy(search = SearchState(active = true, subject = lastSubject))
        }
    }

    private fun closeSearch() {
        _state.update { it.copy(search = SearchState()) }
        // Re-render items without search highlighting
        viewModelScope.launch {
            reRenderAllItems(searchQuery = null)
        }
    }

    private fun updateSearchQuery(query: String) {
        _state.update {
            it.copy(search = it.search.copy(query = query))
        }
        if (query.length >= 2) {
            viewModelScope.launch {
                performSearch(query, _state.value.search.subject)
            }
        } else if (query.isEmpty()) {
            _state.update {
                it.copy(search = it.search.copy(
                    matchingItemIds = emptyList(),
                    currentMatchIndex = -1
                ))
            }
            viewModelScope.launch {
                reRenderAllItems(searchQuery = null)
            }
        }
    }

    private fun setSearchSubjectAndSearch(subject: SearchSubject) {
        setLastSearchSource(subject)
        _state.update {
            it.copy(search = it.search.copy(subject = subject))
        }
        val query = _state.value.search.query
        if (query.length >= 2) {
            viewModelScope.launch {
                performSearch(query, subject)
            }
        }
    }

    private fun navigateMatch(forward: Boolean) {
        _state.update { state ->
            val search = state.search
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

    private suspend fun performSearch(query: String, subject: SearchSubject) {
        withContext(Dispatchers.Default) {
            val items = _items.value
            if (items.isEmpty()) return@withContext

            val lowerQuery = query.lowercase()
            val matchingIds = mutableListOf<String>()

            val updatedItems = items.map { item ->
                val chunkId = item.id
                val searchSource = subject == SearchSubject.SOURCE
                val renderedSource = renderSourceTextWithSearch(
                    chunkId, item.chunk.sourceTranslationFormat, item.sourceText,
                    if (searchSource) query else null
                )
                val renderedTarget = renderTargetTextWithSearch(
                    chunkId, item.chunk, item.targetText, item.targetMode,
                    if (!searchSource) query else null
                )

                // Check matches against rendered text, plus raw text for EDIT mode
                val hasMatch = if (searchSource) {
                    renderedSource.text.lowercase().contains(lowerQuery)
                } else {
                    renderedTarget.text.lowercase().contains(lowerQuery)
                            || item.targetText.lowercase().contains(lowerQuery)
                }
                if (hasMatch) matchingIds.add(item.id)

                item.copy(
                    renderedSourceText = renderedSource,
                    renderedTargetText = renderedTarget
                )
            }

            _items.value = updatedItems
            _state.update { state ->
                state.copy(search = state.search.copy(
                    matchingItemIds = matchingIds,
                    currentMatchIndex = if (matchingIds.isNotEmpty()) 0 else -1
                ))
            }
        }
    }

    private suspend fun reRenderAllItems(searchQuery: String?) {
        withContext(Dispatchers.Default) {
            val items = _items.value
            val updatedItems = items.map { item ->
                val chunkId = item.id
                val renderedSource = renderSourceTextWithSearch(
                    chunkId, item.chunk.sourceTranslationFormat, item.sourceText, searchQuery
                )
                val renderedTarget = renderTargetTextWithSearch(
                    chunkId, item.chunk, item.targetText, item.targetMode, searchQuery
                )
                item.copy(
                    renderedSourceText = renderedSource,
                    renderedTargetText = renderedTarget
                )
            }
            _items.value = updatedItems
        }
    }

    private fun renderSourceTextWithSearch(
        chunkId: String,
        translationFormat: TranslationFormat,
        sourceText: String,
        searchQuery: String?
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
                renderingGroup.setSearchString(searchQuery, android.graphics.Color.YELLOW)
            }
            val renderNodes = renderingGroup.start()
            ComposeTextAdapter.convert(
                renderNodes,
                onNoteClick = { note, _, _ ->
                    showFootnoteViewer(Footnote(
                        text = note.notes,
                        machineReadable = note.machineReadable,
                        chunkId = chunkId,
                        editable = false,
                        start = note.startPos,
                        end = note.endPos
                    ))
                }
            )
        } catch (_: Exception) {
            AnnotatedString(sourceText)
        }
    }

    private fun renderTargetTextWithSearch(
        chunkId: String,
        chunk: Chunk,
        targetText: String,
        targetMode: TargetMode,
        searchQuery: String?
    ): AnnotatedString {
        val verseDisplay = when (targetMode) {
            TargetMode.MARKER -> VerseDisplay.PIN
            TargetMode.EDIT -> VerseDisplay.RAW
            TargetMode.COMPLETE -> VerseDisplay.NUMBER
        }
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(targetText)
            RenderingProvider().setupRenderingGroup(
                chunk.targetTranslationFormat,
                renderingGroup,
                verseDisplay,
                target = true
            )
            if (!searchQuery.isNullOrEmpty()) {
                renderingGroup.setSearchString(searchQuery, android.graphics.Color.YELLOW)
            }
            val renderNodes = renderingGroup.start()
            ComposeTextAdapter.convert(
                nodes = renderNodes,
                onNoteClick = { note, _, _ ->
                    showFootnoteViewer(Footnote(
                        text = note.notes,
                        machineReadable = note.machineReadable,
                        chunkId = chunkId,
                        editable = targetMode != TargetMode.COMPLETE,
                        start = note.startPos,
                        end = note.endPos
                    ))
                },
                onVerseClick = if (targetMode == TargetMode.MARKER) { _ ->
                    showSnackBar(application.getString(R.string.long_click_to_drag))
                } else null
            )
        } catch (_: Exception) {
            AnnotatedString(targetText)
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

    private fun openResources(value: Boolean) {
        _state.update { it.copy(resourcesOpen = value) }
    }

    private fun onRenderHelps(item: ReviewItem) {
        if (!item.helps.isEmpty()) return

        viewModelScope.launch {
            val helps = withContext(Dispatchers.IO) {
                renderHelps.execute(item.chunk, item.chunkConfig)
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
        viewModelScope.launch {
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
            KEY_PREF_ENABLE_TM_LINKS,
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
                            KEY_PREF_TM_URL,
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            val shouldComplete = item.targetMode != TargetMode.COMPLETE
            if (shouldComplete) {
                _state.value = _state.value.copy(chunkToDone = item)
            } else {
                updateDoneStatus(item, false)
            }
        }
    }

    private fun toggleDoneConfirmed(confirm: Boolean) {
        viewModelScope.launch {
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

    private suspend fun updateDoneStatus(item: ReviewItem, shouldComplete: Boolean) {
        val updated = withContext(Dispatchers.IO) {
            try {
                if (shouldComplete) {
                    markChunkCompleted(item)
                } else {
                    item.reopenChunk()
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
        val success = item.closeChunk()

        if (!success) {
            throw IllegalStateException(application.getString(R.string.failed_to_commit_chunk))
        }
    }

    private fun onItemTextChanged(item: ReviewItem, text: String) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val history = item.fileHistory ?: return@withContext
                val commit = navigate(history) ?: return@withContext

                val text = try {
                    history.read(commit)
                } catch (e: IllegalStateException) {
                    Logger.w(
                        this@ReviewModeViewModel::class.simpleName,
                        "History navigation past end of file history",
                        e
                    )
                    ""
                } catch (e: Exception) {
                    Logger.w(
                        this@ReviewModeViewModel::class.simpleName,
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
                    footnoteEditable = true
                )
                updateItem(item.copy(
                    targetText = text,
                    renderedTargetText = rendered
                ))
                //updateMergeConflict() TODO Don't remove
            }
        }
    }

    private fun onAddNoteClicked(item: ReviewItem, caretPosition: Int = -1) {
        showFootnoteEditor(
            Footnote(
                text = "",
                machineReadable = "",
                chunkId = item.id,
                editable = true,
                insertPosition = caretPosition
            )
        )
    }

    private fun createFileHistory(item: ReviewItem): FileHistory? {
        return when {
            item.isChapterReference -> item.chunk.target.getChapterReferenceHistory(item.ct)
            item.isChapterTitle -> item.chunk.target.getChapterTitleHistory(item.ct)
            item.isProjectTitle -> item.chunk.target.projectTitleHistory
            item.isChunk -> item.chunk.target.getFrameHistory(item.ft)
            else -> null
        }
    }

    override fun onDeleteFootnote(note: Footnote) {
        super.onDeleteFootnote(note)
        replaceFootnoteInTarget(note, replacement = "")
    }

    override fun onOpenFootnoteEditor(note: Footnote) {
        super.onOpenFootnoteEditor(note)
        showFootnoteEditor(note)
    }

    override fun onSaveFootnote(note: Footnote) {
        super.onSaveFootnote(note)
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
        val item = _items.value.find { it.id == note.chunkId } ?: return
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
        val item = _items.value.find { it.id == note.chunkId } ?: return
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
        viewModelScope.launch {
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
}