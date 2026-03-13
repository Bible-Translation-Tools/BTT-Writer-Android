package com.door43.translationstudio.rendering

/**
 * This is the default rendering engine.
 */
class DefaultRenderer : RenderingEngine() {

    private var search: String = ""
    private var highlightColor = 0
    private var renderer: USXRenderer? = null

    /**
     * Renders the input into a readable format
     * @param input the raw input string
     * @return
     */
    override fun render(input: CharSequence): CharSequence {
        var out = input

        val usxRenderer = USXRenderer()
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
}