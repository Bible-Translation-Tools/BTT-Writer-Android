package com.door43.translationstudio.ui.translate.review

import android.view.View
import android.widget.TextView
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.ui.translate.OnAdapterListener
import com.door43.translationstudio.ui.translate.ReviewListItemOld
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
    fun onRenderSourceText(item: ReviewListItemOld): List<TextNode>
    fun onSearchItemUpdated(position: Int, view: TextView, isTarget: Boolean)
    fun onMergeConflictItemCancel(position: Int)
    fun onMergeConflictItemConfirm(position: Int)
    fun onRenderTargetText(
        holder: ReviewHolder,
        item: ReviewListItemOld,
        editable: Boolean
    ): List<TextNode>

    fun onRenderTargetText(holder: ReviewHolder, item: ReviewListItemOld): List<TextNode>
    fun onAddMissingVerses(holder: ReviewHolder)
    fun onRenderHelps(item: ReviewListItemOld)
    fun onSourceNoteClick(item: ReviewListItemOld, marker: TextNode.NoteMarker)
    fun onNoteClick(holder: ReviewHolder, item: ReviewListItemOld, marker: TextNode.NoteMarker, start: Int, end: Int, editable: Boolean)
    fun onVerseClick(item: ReviewListItemOld, marker: TextNode.VerseMarker)
    fun onVerseLongClick(view: View, holder: ReviewHolder, item: ReviewListItemOld, marker: TextNode.VerseMarker, start: Int, end: Int)
}
