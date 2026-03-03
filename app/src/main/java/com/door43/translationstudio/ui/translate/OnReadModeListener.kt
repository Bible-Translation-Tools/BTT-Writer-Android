package com.door43.translationstudio.ui.translate

import com.door43.translationstudio.rendering.model.TextNode

interface OnReadModeListener : OnAdapterListener {
    fun onOpenTargetTranslationCard(holder: ReadModeAdapter.ViewHolder)
    fun onCloseTargetTranslationCard(holder: ReadModeAdapter.ViewHolder)
    fun onRenderSourceText(holder: ReadModeAdapter.ViewHolder): List<TextNode>
    fun onRenderTargetText(holder: ReadModeAdapter.ViewHolder): List<TextNode>
    fun onOpenTranslationMode(chapterSlug: String)
}
