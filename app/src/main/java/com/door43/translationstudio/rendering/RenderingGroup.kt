package com.door43.translationstudio.rendering

/**
 * Created by joel on 1/26/2015.
 */
class RenderingGroup {
    private var mStopped = false
    private var mRunning = false
    private val mEngines: MutableList<RenderingEngine> = mutableListOf()
    private var mInput: CharSequence = ""

    /**
     * see if missing verse was added
     */
    val isAddedMissingVerse: Boolean
        get() = mEngines.any {
            it is ClickableRenderingEngine && it.isAddedMissingVerse
        }

    /**
     * Adds a rendering engine to the queue
     * @param engine
     */
    fun addEngine(engine: RenderingEngine) {
        mEngines.add(engine)
    }

    /**
     * if set to false verses will not be displayed in the output.
     *
     * @param enable default is true
     */
    fun setVersesEnabled(enable: Boolean) {
        for (engine in mEngines) {
            if (engine is ClickableRenderingEngine) {
                engine.setVersesEnabled(enable)
            }
        }
    }

    /**
     * If set to not empty matched strings will be highlighted.
     *
     * @param searchString - empty string disables highlighting
     * @param highlightColor
     */
    fun setSearchString(searchString: CharSequence, highlightColor: Int) {
        for (engine in mEngines) {
            engine.setSearchString(searchString, highlightColor)
        }
    }

    /**
     * if set to true, then paragraphs (\p) will be rendered in the output.
     *
     * @param enable default is true
     */
    fun setParagraphsEnabled(enable: Boolean) {
        for (engine in mEngines) {
            if (engine is ClickableRenderingEngine) {
                engine.setParagraphsEnabled(enable)
            }
        }
    }

    /**
     * Begins the rendering operations
     */
    fun start(): CharSequence {
        if (mRunning || mInput.isEmpty()) return ""
        mRunning = true
        mStopped = false
        var rendered = mInput

        for (engine in mEngines) {
            if (mStopped) break
            rendered = engine.render(rendered)
        }

        mRunning = false
        return rendered
    }

    /**
     * Stops the rendering operations
     */
    fun stop() {
        mStopped = true
        for (engine in mEngines) {
            engine.stop()
        }
    }

    /**
     * Initializes the rendering group
     * @param input
     */
    fun init(input: String) {
        mInput = input
    }
}