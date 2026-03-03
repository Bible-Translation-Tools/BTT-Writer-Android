package com.door43.translationstudio.rendering.adapter

import android.view.View
import com.door43.translationstudio.rendering.model.TextNode

/**
 * Click callback for verse markers rendered by SpannableAdapter.
 * Replaces Span.OnClickListener for the verse use case.
 */
fun interface VerseClickListener {
    fun onVerseClick(view: View, marker: TextNode.VerseMarker, start: Int, end: Int)
}

