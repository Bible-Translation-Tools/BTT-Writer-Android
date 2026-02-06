package com.door43.translationstudio.ui.translate;

public interface OnReadModeListener extends OnAdapterListener {
    void onOpenTargetTranslationCard(ReadModeAdapter.ViewHolder holder);
    void onCloseTargetTranslationCard(ReadModeAdapter.ViewHolder holder);
    CharSequence onRenderSourceText(ReadModeAdapter.ViewHolder holder);
    CharSequence onRenderTargetText(ReadModeAdapter.ViewHolder holder);
    void onOpenTranslationMode(String chapterSlug);
}
