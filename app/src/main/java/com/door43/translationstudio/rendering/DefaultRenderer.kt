package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.RenderNode

/**
 * Default rendering engine. Delegates to USXRenderer for note/search rendering.
 */
class DefaultRenderer : RenderingEngine() {

    private var search: String = ""
    private var renderer: USXRenderer? = null

    override fun render(input: String): List<RenderNode> {
        val usxRenderer = USXRenderer()
        usxRenderer.setSearchString(search)
        this.renderer = usxRenderer
        if (isStopped()) return listOf(RenderNode.Text(input))
        return usxRenderer.render(input)
    }

    override fun onStop() {
        renderer?.stop()
    }

    override fun setSearchString(searchString: String) {
        search = if (searchString.isNotEmpty()) {
            searchString.lowercase()
        } else {
            ""
        }
    }
}