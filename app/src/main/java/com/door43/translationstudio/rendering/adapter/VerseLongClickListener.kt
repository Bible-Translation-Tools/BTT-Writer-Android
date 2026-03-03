package com.door43.translationstudio.rendering.adapter

import android.view.View
import com.door43.translationstudio.rendering.model.TextNode

/**
 * Long-click callback for verse markers (e.g., drag-and-drop in review mode).
 * Used with SpannableAdapter.convert() verseLongClickListener parameter.
 */
fun interface VerseLongClickListener {
    fun onVerseLongClick(view: View, marker: TextNode.VerseMarker, start: Int, end: Int)
}
