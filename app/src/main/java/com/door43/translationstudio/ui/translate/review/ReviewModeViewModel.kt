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
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.spannables.ArticleLinkSpan
import com.door43.translationstudio.rendering.spannables.PassageLinkSpan
import com.door43.translationstudio.rendering.spannables.TranslationWordLinkSpan
import com.door43.translationstudio.rendering.spannables.USFMVerseSpan
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_ENABLE_TM_LINKS
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_TM_URL
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import com.door43.translationstudio.ui.translate.ModeAction
import com.door43.translationstudio.ui.translate.ModeState
import com.door43.translationstudio.ui.translate.ModeViewModel
import com.door43.translationstudio.ui.translate.ReviewItem
import com.door43.translationstudio.ui.translate.SharedState
import com.door43.translationstudio.ui.translate.TargetTranslationActivity.Companion.SEARCH_SOURCE
import com.door43.translationstudio.ui.translate.TranslationHelp
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.ResourceContainer
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

data class ReviewState(
    val resourcesOpen: Boolean = false,
    val help: Help? = null,
    val url: String? = null,
    val chunkToDone: ReviewItem? = null
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
    object ClearHelp : ReviewAction
    object CleanUrl : ReviewAction
}

class ReviewModeViewModel(
    sharedState: StateFlow<SharedState>,
    snackBar: SendChannel<String>,
    private val prefRepository: IPreferenceRepository,
    private val renderHelps: RenderHelps,
    private val renderingProvider: RenderingProvider,
    private val library: Door43Client,
) : ModeViewModel<ReviewItem>(
    sharedState,
    TranslationViewMode.REVIEW,
    snackBar
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
            ReviewAction.ClearHelp -> _state.update { it.copy(help = null) }
            ReviewAction.CleanUrl -> _state.update { it.copy(url = null) }
        }
    }

    private fun prepareItem(
        chunk: Chunk,
        targetMode: TargetMode = TargetMode.MARKER
    ): ReviewItem {
        val (pt, ct, ft) = prepareTranslations(chunk)
        val (sourceText, renderedSourceText) = prepareSource(chunk)

        val item = ReviewItem(
            id  = "${chunk.chapterSlug}-${chunk.chunkSlug}",
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
        val (targetText, renderedTargetText) = prepareTarget(chunk, realTargetMode)

        return item.copy(
            targetText = targetText,
            renderedTargetText = renderedTargetText,
            targetMode = realTargetMode
        )
    }

    private fun prepareSource(chunk: Chunk): Pair<String, AnnotatedString> {
        val text = chunk.source.readChunk(chunk.chapterSlug, chunk.chunkSlug)
        return text to renderSourceText(chunk.sourceTranslationFormat, text)
    }

    private fun prepareTarget(
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
            translationFormat = chunk.targetTranslationFormat,
            targetText = text,
            verseDisplay = verseDisplay
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
            getClosestResourceContainer(
                rc.language.slug,
                "bible",
                "tw"
            )
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

                if (targetMode == TargetMode.MARKER) {
                    item.chunk.target.commit()
                }
                updateItem(prepareItem(item.chunk, targetMode))
            }
        }
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

    /**
     * Performs some validation, and commits changes if ready.
     *
     * @throws IllegalStateException If there is an error with the chunk
     */
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

    private fun getClosestResourceContainer(
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): ResourceContainer? {
        return ContainerCache.cacheClosest(library, languageSlug, projectSlug, resourceSlug)
    }

    private fun getResourceContainer(slug: String): ResourceContainer? {
        return ContainerCache.get(slug)
    }
}