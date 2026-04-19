package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.RenderNode

class RenderingGroup {
    @Volatile private var mStopped = false
    @Volatile private var mRunning = false
    private val mEngines: MutableList<RenderingEngine> = mutableListOf()
    private var mInput: String = ""

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
     * Runs the pipeline and returns a platform-agnostic hierarchical List<RenderNode>.
     *
     * If the first engine is a [ClickableRenderingEngine], its [RenderingEngine.render]
     * override is used directly (it handles notes, highlights, etc. natively).
     * For any other engine (e.g. DefaultRenderer), only [RenderingEngine.render] is overridden,
     * so this method calls render() and wraps the result in a plain [RenderNode.Text].
     */
    fun start(): List<RenderNode> {
        if (mRunning || mInput.isEmpty()) return emptyList()
        mRunning = true
        mStopped = false
        val result: List<RenderNode> = if (mEngines.isEmpty()) {
            listOf(RenderNode.Text(mInput))
        } else {
            mEngines.first().render(mInput)
        }
        mRunning = false
        return result
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