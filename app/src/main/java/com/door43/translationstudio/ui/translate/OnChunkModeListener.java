package com.door43.translationstudio.ui.translate;

import android.content.ContentValues;
import android.view.View;
import android.widget.EditText;

import com.door43.translationstudio.core.TranslationFormat;
import com.google.android.material.tabs.TabLayout;

public interface OnChunkModeListener {
    boolean onCheckForPromptToEditDoneTargetCard(ChunkModeAdapter.ViewHolder holder);
    void onOpenTargetTranslationCard(ChunkModeAdapter.ViewHolder holder);
    void onCloseTargetTranslationCard(ChunkModeAdapter.ViewHolder holder);
    void onEditTarget(EditText target, int position);
    View onCreateRemovableTabLayout(String tag, String title);
    void onApplyLanguageTypefaceToTab(TabLayout layout, ContentValues values, String title);
    void onSourceTranslationTabClick(String sourceId);
    void onNewSourceTranslationTabClick();
    void onTextChanged(CharSequence s, int start, int before, int count, int itemPosition);
    void onConflictButtonClicked(int position);
    CharSequence onRenderText(String text, TranslationFormat format);
}
