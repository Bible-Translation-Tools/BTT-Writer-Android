package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.RenderNode

/**
 * Default rendering engine. Delegates to USXRenderer for note/search rendering.
 */
class DefaultRenderer : RenderingEngine() {

    private var search: String = ""
    private var highlightColor = 0
    private var renderer: USXRenderer? = null

    override fun render(input: String): List<RenderNode> {
        val usxRenderer = USXRenderer()
        usxRenderer.setSearchString(search, highlightColor)
        this.renderer = usxRenderer
        if (isStopped()) return listOf(RenderNode.Text(input))
        return usxRenderer.render(input)
    }

    override fun onStop() {
        renderer?.stop()
    }

    override fun setSearchString(searchString: CharSequence, highlightColor: Int) {
        this@DefaultRenderer.highlightColor = highlightColor
        search = if (searchString.isNotEmpty()) {
            searchString.toString().lowercase()
        } else {
            ""
        }
    }
}