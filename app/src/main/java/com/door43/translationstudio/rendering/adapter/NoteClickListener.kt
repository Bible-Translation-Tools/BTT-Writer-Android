package com.door43.translationstudio.rendering.adapter

import android.view.View
import com.door43.translationstudio.rendering.model.TextNode

/**
 * Click callback for note markers rendered by SpannableAdapter.
 * Replaces Span.OnClickListener for the footnote/cross-reference use case.
 */
fun interface NoteClickListener {
    fun onNoteClick(view: View, marker: TextNode.NoteMarker, start: Int, end: Int)
}
