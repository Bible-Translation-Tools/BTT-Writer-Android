package com.door43.translationstudio.ui.translate.review

import android.widget.TextView
import com.door43.translationstudio.ui.spannables.NoteSpan
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
    fun onRenderSourceText(item: ReviewListItem): CharSequence
    fun onSearchItemUpdated(position: Int, view: TextView, isTarget: Boolean)
    fun onMergeConflictItemCancel(position: Int)
    fun onMergeConflictItemConfirm(position: Int)
    fun onRenderTargetText(
        holder: ReviewHolder,
        item: ReviewListItem,
        editable: Boolean
    ): CharSequence

    fun onRenderTargetText(holder: ReviewHolder, item: ReviewListItem): CharSequence
    fun onAddMissingVerses(holder: ReviewHolder)
    fun onRenderHelps(item: ReviewListItem)
    fun onSourceFootnoteClick(item: ReviewListItem, span: NoteSpan, start: Int, end: Int)
}
