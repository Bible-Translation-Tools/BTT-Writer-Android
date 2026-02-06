package com.door43.translationstudio.ui.translate;

import android.widget.EditText;

import com.door43.translationstudio.core.TranslationFormat;

public interface OnChunkModeListener extends OnAdapterListener {
    boolean onCheckForPromptToEditDoneTargetCard(ChunkModeAdapter.ViewHolder holder);
    void onOpenTargetTranslationCard(ChunkModeAdapter.ViewHolder holder);
    void onCloseTargetTranslationCard(ChunkModeAdapter.ViewHolder holder);
    void onEditTarget(EditText target, int position);
    void onTextChanged(CharSequence s, int start, int before, int count, int itemPosition);
    void onConflictButtonClicked(int position);
    CharSequence onRenderText(String text, TranslationFormat format);
}
