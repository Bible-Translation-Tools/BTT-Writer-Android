package com.door43.translationstudio.ui.translate

interface OnReadModeListener : OnAdapterListener {
    fun onOpenTargetTranslationCard(holder: ReadModeAdapter.ViewHolder)
    fun onCloseTargetTranslationCard(holder: ReadModeAdapter.ViewHolder)
    fun onRenderSourceText(holder: ReadModeAdapter.ViewHolder): CharSequence
    fun onRenderTargetText(holder: ReadModeAdapter.ViewHolder): CharSequence
    fun onOpenTranslationMode(chapterSlug: String)
}
