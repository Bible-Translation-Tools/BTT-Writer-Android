package com.door43.translationstudio.ui.translate

import android.view.MotionEvent

/**
 * Created by joel on 9/8/2015.
 */
class ReadModeFragment : ViewModeFragment() {

    override fun generateAdapter(): ViewModeAdapter<*> {
        return ReadModeAdapter(
            typography,
            renderingProvider,
            assetsProvider
        )
    }

    /**
     * doTranslationCardToggle
     * @param e1
     * @param e2
     * @param swipeLeft
     */
    protected fun doTranslationCardToggle(e1: MotionEvent, e2: MotionEvent, swipeLeft: Boolean) {
        val adapter = getAdapter() as? ReadModeAdapter ?: return

        var position = findViewHolderAdapterPosition(e1.x, e1.y)
        if (position == -1) {
            position = findViewHolderAdapterPosition(e2.x, e2.y)
        }

        if (position != -1) {
            val holder = getViewHolderForAdapterPosition(position) as? ReadModeAdapter.ViewHolder
            if (holder != null) {
                adapter.toggleTargetTranslationCard(holder, position, swipeLeft)
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