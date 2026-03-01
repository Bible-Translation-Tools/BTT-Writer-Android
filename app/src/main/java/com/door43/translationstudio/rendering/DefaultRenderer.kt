package com.door43.translationstudio.rendering

import android.content.Context
import android.view.View
import com.door43.translationstudio.ui.spannables.Span

/**
 * This is the default rendering engine.
 */
class DefaultRenderer : RenderingEngine {

    private var noteListener: Span.OnClickListener
    private var search: String = ""
    private var highlightColor = 0
    private var renderer: USXRenderer? = null

    /**
     * Creates a new default rendering engine without any listeners
     */
    constructor(context: Context) {
        this.context = context
        this.noteListener = EmptyListener
    }

    /**
     * Creates a new default rendering engine with some custom click listeners
     * @param noteListener
     */
    constructor(context: Context, noteListener: Span.OnClickListener) {
        this.context = context
        this.noteListener = noteListener
    }

    /**
     * Renders the input into a readable format
     * @param input the raw input string
     * @return
     */
    override fun render(input: CharSequence): CharSequence {
        var out = input

        // Assuming USXRenderer constructor expects two listeners.
        // We pass the EmptyListener to avoid nulls.
        val usxRenderer = USXRenderer(context, EmptyListener, noteListener)
        usxRenderer.setSearchString(search, highlightColor)
        this.renderer = usxRenderer

        if (isStopped()) return input
        out = usxRenderer.renderNote(out)
        if (isStopped()) return input
        out = usxRenderer.renderHighlightSearch(out)

        return out
    }

    override fun onStop() {
        renderer?.stop()
    }

    /**
     * If set to not empty matched strings will be highlighted.
     *
     * @param searchString - empty string disables highlighting
     * @param highlightColor
     */
    override fun setSearchString(searchString: CharSequence, highlightColor: Int) {
        this@DefaultRenderer.highlightColor = highlightColor
        search = if (searchString.isNotEmpty()) {
            searchString.toString().lowercase()
        } else {
            ""
        }
    }

    private companion object {
        /**
         * A dummy listener used to replace null fallbacks.
         */
        val EmptyListener = object : Span.OnClickListener {
            override fun onClick(view: View, span: Span, start: Int, end: Int) {
                // Do nothing
            }
            override fun onLongClick(view: View, span: Span, start: Int, end: Int) {
                // Do nothing
            }
        }
    }
}