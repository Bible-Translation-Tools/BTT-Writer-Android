package com.door43.translationstudio.ui.translate.review

import androidx.compose.ui.text.AnnotatedString
import com.door43.translationstudio.rendering.spannables.USFMVerseSpan
import com.door43.translationstudio.ui.translate.Footnote
import com.door43.translationstudio.ui.translate.ModeComponent
import com.door43.translationstudio.ui.translate.ReviewItem
import kotlinx.coroutines.flow.StateFlow
import java.util.regex.Pattern

val USFM_CONSECUTIVE_VERSE_MARKERS: Pattern =
    Pattern.compile("\\\\v\\s(\\d+(-\\d+)?)\\s*\\\\v\\s(\\d+(-\\d+)?)")

val CONSECUTIVE_VERSE_MARKERS: Pattern =
    Pattern.compile("(<verse [^>]+/>\\s*){2}")

val VERSE_MARKER: Pattern =
    Pattern.compile("<verse\\s+number=\"(\\d+)\"[^>]*>")

val USFM_VERSE_MARKER: Pattern =
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

    fun onItemTextChanged(item: ReviewItem, text: String)
    fun openResources(value: Boolean)
    fun renderHelps(item: ReviewItem)
    fun openHelp(item: HelpItem)
    fun openIndex(rcSlug: String)
    fun openWord(rcSlug: String, chapterSlug: String)
    fun toggleEdit(item: ReviewItem)
    fun onToggleDone(item: ReviewItem)
    fun onDoneConfirmed(confirm: Boolean)
    fun toggleMarkAllDone()
    fun onMarkAllDoneConfirmed(confirm: Boolean)
    fun undo(item: ReviewItem)
    fun redo(item: ReviewItem)
    fun onAddNote(item: ReviewItem, caretPosition: Int = -1)
    fun clearHelp()
    fun cleanUrl()
    fun openSearch()
    fun closeSearch()
    fun updateSearchQuery(query: String)
    fun setSearchSubject(subject: SearchSubject)
    fun nextMatch()
    fun prevMatch()
    fun onDragDropVerse(
        item: ReviewItem,
        machineReadable: String,
        verseRawStart: Int,
        verseRawEnd: Int,
        targetRawPosition: Int
    )
    fun selectConflict(item: ReviewItem, index: Int)
    fun setConflictFilterOn(value: Boolean)

    data class State(
        val resourcesOpen: Boolean = false,
        val help: Help? = null,
        val url: String? = null,
        val chunkToDone: ReviewItem? = null,
        val search: SearchState? = null,
        val markAllDoneState: MarkAllDialogState? = null,
        val conflictFilterOn: Boolean = false,
        override val footnote: Footnote? = null
    ) : ModeComponent.State
}
