package com.door43.translationstudio.ui.translate;

public interface OnViewModeListener {
    void onSourceRemoveButtonClicked(String sourceTranslationId);
    void onSourceTranslationTabClick(String sourceTranslationId);
    void onNewSourceTranslationTabClick();
}
