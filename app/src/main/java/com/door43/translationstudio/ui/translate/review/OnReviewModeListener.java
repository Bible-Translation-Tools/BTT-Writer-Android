package com.door43.translationstudio.ui.translate.review;

import android.widget.TextView;

import com.door43.translationstudio.ui.spannables.NoteSpan;
import com.door43.translationstudio.ui.translate.OnAdapterListener;
import com.door43.translationstudio.ui.translate.ReviewListItem;
import com.door43.translationstudio.ui.translate.TranslationHelp;

import org.unfoldingword.resourcecontainer.Link;

public interface OnReviewModeListener extends OnAdapterListener {
    void onNoteClick(TranslationHelp note, int resourceCardWidth);
    void onWordClick(String resourceContainerSlug, Link word, int resourceCardWidth);
    void onQuestionClick(TranslationHelp question, int resourceCardWidth);
    void onResourceTabNotesSelected(ReviewHolder holder);
    void onResourceTabWordsSelected(ReviewHolder holder);
    void onResourceTabQuestionsSelected(ReviewHolder holder);
    void onTapResourceCard();
    void onNotifyItemChanged(int position);
    void onEditorToggle(ReviewHolder holder);
    void onApplyChangedText(CharSequence s, ReviewHolder holder);
    void onUndoTextInTarget(ReviewHolder holder);
    void onRedoTextInTarget(ReviewHolder holder);
    void onDoneSwitchClicked(ReviewHolder holder, boolean checked);
    void onCreateFootnoteAtSelection(ReviewHolder holder);
    CharSequence onRenderSourceText(ReviewListItem item);
    void onSearchItemUpdated(int position, TextView view, boolean isTarget);
    void onMergeConflictItemCancel(int position);
    void onMergeConflictItemConfirm(int position);
    CharSequence onRenderTargetText(ReviewHolder holder, ReviewListItem item, boolean editable);
    CharSequence onRenderTargetText(ReviewHolder holder, ReviewListItem item);
    void onAddMissingVerses(int position);
    void onRenderHelps(ReviewListItem item);
    void onSourceFootnoteClick(ReviewListItem item, NoteSpan span, int start, int end);
}
