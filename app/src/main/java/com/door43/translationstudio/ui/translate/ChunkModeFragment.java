package com.door43.translationstudio.ui.translate;

import androidx.recyclerview.widget.RecyclerView;
import android.view.MotionEvent;

/**
 * Displays translations in chunks
 */
public class ChunkModeFragment extends ViewModeFragment {

    public static final String EXTRA_TARGET_OPEN = "extra_target_start_open";

    @Override
    ViewModeAdapter generateAdapter() {
        return new ChunkModeAdapter(typography, renderingProvider);
    }

    /***
     * doTranslationCardToggle
     * @param e1
     * @param e2
     * @param swipeLeft
     */
    protected void doTranslationCardToggle(final MotionEvent e1, final MotionEvent e2, final boolean swipeLeft) {
        if(getAdapter() != null) {
            int position = findViewHolderAdapterPosition(e1.getX(), e1.getY());
            if(position == -1) {
                position = findViewHolderAdapterPosition(e2.getX(), e2.getY());
            }
            if(position != -1) {
                RecyclerView.ViewHolder holder = getViewHolderForAdapterPosition(position);
                ((ChunkModeAdapter) getAdapter()).toggleTargetTranslationCard((ChunkModeAdapter.ViewHolder) holder, position, swipeLeft);
            }
        }
    }

    @Override
    protected void onRightSwipe(MotionEvent e1, MotionEvent e2) {
        doTranslationCardToggle(e1, e2, false);
    }

    @Override
    protected void onLeftSwipe(MotionEvent e1, MotionEvent e2) {
        doTranslationCardToggle(e1, e2, true);
    }

    @Override
    public void markAllChunksDone() {}
}
