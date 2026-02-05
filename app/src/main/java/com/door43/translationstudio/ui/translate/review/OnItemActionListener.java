package com.door43.translationstudio.ui.translate.review;

import android.content.ContentValues;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.door43.translationstudio.ui.translate.ReviewListItem;
import com.door43.translationstudio.ui.translate.TranslationHelp;

import org.unfoldingword.resourcecontainer.Link;

public interface OnItemActionListener extends OnViewModeListener {
    void onNoteClick(TranslationHelp note, int resourceCardWidth);
    void onWordClick(String resourceContainerSlug, Link word, int resourceCardWidth);
    void onQuestionClick(TranslationHelp question, int resourceCardWidth);
    void onResourceTabNotesSelected(int position, ReviewHolder holder);
    void onResourceTabWordsSelected(int position, ReviewHolder holder);
    void onResourceTabQuestionsSelected(int position, ReviewHolder holder);
    void onTapResourceCard();
    void onNotifyItemChanged(int position);
    View onCreateRemovableTabLayout(String tag, String title);
    void onApplyLanguageTypefaceToTab(ViewGroup layout, ContentValues values, String title);
    void onEditorToggle(int position, ReviewHolder holder);
    void onApplyChangedText(CharSequence s, int position, ReviewHolder holder);
    void onUndoTextInTarget(int position, ReviewHolder holder);
    void onRedoTextInTarget(int position, ReviewHolder holder);
    void onDoneSwitchClicked(int position, ReviewHolder holder, boolean checked);
    void onCreateFootnoteAtSelection(int position, ReviewHolder holder);
    CharSequence onRenderSourceText(ReviewListItem item);
    void onSearchItemUpdated(int position, TextView view, boolean isTarget);
    void onMergeConflictItemCancel(int position);
    void onMergeConflictItemConfirm(int position);
    CharSequence onRenderTargetText(ReviewHolder holder, ReviewListItem item, boolean editable);
    CharSequence onRenderTargetText(ReviewHolder holder, ReviewListItem item);
    void onAddMissingVerses(int position);
    void onRenderHelps(ReviewListItem item);
}
