package com.door43.translationstudio.ui.translate

import android.widget.EditText
import com.door43.translationstudio.core.TranslationFormat

interface OnChunkModeListener : OnAdapterListener {
    fun onCheckForPromptToEditDoneTargetCard(holder: ChunkModeAdapter.ViewHolder): Boolean
    fun onOpenTargetTranslationCard(holder: ChunkModeAdapter.ViewHolder)
    fun onCloseTargetTranslationCard(holder: ChunkModeAdapter.ViewHolder)
    fun onEditTarget(target: EditText, position: Int)
    fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int, itemPosition: Int)
    fun onConflictButtonClicked(position: Int)
    fun onRenderText(text: String, format: TranslationFormat): CharSequence
}
