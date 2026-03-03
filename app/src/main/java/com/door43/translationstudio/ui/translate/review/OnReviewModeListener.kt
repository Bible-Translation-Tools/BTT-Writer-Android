package com.door43.translationstudio.ui.translate.review

import android.view.View
import android.widget.TextView
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.ui.translate.OnAdapterListener
import com.door43.translationstudio.ui.translate.ReviewListItem
import com.door43.translationstudio.ui.translate.TranslationHelp
import org.unfoldingword.resourcecontainer.Link

interface OnReviewModeListener : OnAdapterListener {
    fun onNoteClick(note: TranslationHelp, resourceCardWidth: Int)
    fun onWordClick(resourceContainerSlug: String, word: Link, resourceCardWidth: Int)
    fun onQuestionClick(question: TranslationHelp, resourceCardWidth: Int)
    fun onResourceTabNotesSelected(holder: ReviewHolder)
    fun onResourceTabWordsSelected(holder: ReviewHolder)
    fun onResourceTabQuestionsSelected(holder: ReviewHolder)
    fun onTapResourceCard()
    fun onNotifyItemChanged(position: Int)
    fun onEditorToggle(holder: ReviewHolder)
    fun onApplyChangedText(s: CharSequence, holder: ReviewHolder)
    fun onUndoTextInTarget(holder: ReviewHolder)
    fun onRedoTextInTarget(holder: ReviewHolder)
    fun onDoneSwitchClicked(holder: ReviewHolder, checked: Boolean)
    fun onCreateFootnoteAtSelection(holder: ReviewHolder)
    fun onRenderSourceText(item: ReviewListItem): List<TextNode>
    fun onSearchItemUpdated(position: Int, view: TextView, isTarget: Boolean)
    fun onMergeConflictItemCancel(position: Int)
    fun onMergeConflictItemConfirm(position: Int)
    fun onRenderTargetText(
        holder: ReviewHolder,
        item: ReviewListItem,
        editable: Boolean
    ): List<TextNode>

    fun onRenderTargetText(holder: ReviewHolder, item: ReviewListItem): List<TextNode>
    fun onAddMissingVerses(holder: ReviewHolder)
    fun onRenderHelps(item: ReviewListItem)
    fun onSourceNoteClick(item: ReviewListItem, marker: TextNode.NoteMarker)
    fun onNoteClick(holder: ReviewHolder, item: ReviewListItem, marker: TextNode.NoteMarker, start: Int, end: Int, editable: Boolean)
    fun onVerseClick(item: ReviewListItem, marker: TextNode.VerseMarker)
    fun onVerseLongClick(view: View, holder: ReviewHolder, item: ReviewListItem, marker: TextNode.VerseMarker, start: Int, end: Int)
}
