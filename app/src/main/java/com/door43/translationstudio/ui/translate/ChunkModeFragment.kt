package com.door43.translationstudio.ui.translate

import android.view.MotionEvent

/**
 * Displays translations in chunks
 */
class ChunkModeFragment : ViewModeFragment() {

    companion object {
        const val EXTRA_TARGET_OPEN = "extra_target_start_open"
    }

    override fun generateAdapter(): ViewModeAdapter<*> {
        return ChunkModeAdapter(
            typography,
            renderingProvider,
            assetsProvider
        )
    }

    /**
     * doTranslationCardToggle
     * @param e1 - touch event
     * @param e2 - touch event
     * @param swipeLeft - if true then swipe left, otherwise swipe right
     */
    protected fun doTranslationCardToggle(e1: MotionEvent, e2: MotionEvent, swipeLeft: Boolean) {
        val adapter = getAdapter() as? ChunkModeAdapter ?: return

        var position = findViewHolderAdapterPosition(e1.x, e1.y)
        if (position == -1) {
            position = findViewHolderAdapterPosition(e2.x, e2.y)
        }

        if (position != -1) {
            val holder = getViewHolderForAdapterPosition(position) as? ChunkModeAdapter.ViewHolder
            if (holder != null) {
                adapter.toggleTargetTranslationCard(holder, swipeLeft)
            }
        }
    }

    override fun onRightSwipe(e1: MotionEvent, e2: MotionEvent) {
        doTranslationCardToggle(e1, e2, false)
    }

    override fun onLeftSwipe(e1: MotionEvent, e2: MotionEvent) {
        doTranslationCardToggle(e1, e2, true)
    }

    override fun markAllChunksDone() {}
}