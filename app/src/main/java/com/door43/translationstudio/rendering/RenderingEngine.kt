package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.TextNode
import kotlin.concurrent.thread

abstract class RenderingEngine {

    @Volatile private var stopped = false
    @Volatile private var running = false

    /**
     * Begins the rendering process
     * @param input the raw input string
     * @param callback the callback that will receive events regarding the rendering
     */
    fun start(input: CharSequence, callback: OnRenderCallback) {
        if (running) return
        running = true
        stopped = false

        thread {
            try {
                val output = renderToNodes(input.toString())
                callback.onComplete(output)
            } catch (e: Exception) {
                callback.onError(input.toString())
            } finally {
                running = false
            }
        }
    }

    /**
     * Stops the rendering process
     */
    fun stop() {
        stopped = true
        onStop()
    }

    /**
     * Checks if the rendering engine has been notified to stop
     * Implementations of this class should check the value of this method at the beginning of each loop
     * in order to provide stopping support.
     */
    fun isStopped(): Boolean {
        return stopped
    }

    /**
     * Primary rendering method. Subclasses override this to produce a platform-agnostic
     * intermediate representation. The default implementation wraps the raw input in a
     * single [TextNode.Text] node.
     *
     * @param input the raw input string
     * @return list of platform-agnostic nodes describing the rendered output
     */
    open fun renderToNodes(input: String): List<TextNode> {
        return listOf(TextNode.Text(input))
    }

    /**
     * Renders the input string to a [CharSequence].
     * Subclasses override this to apply format-specific rendering.
     * The base implementation returns [input] unchanged.
     *
     * @param input the raw input string
     * @return the rendered output as a CharSequence
     */
    open fun render(input: CharSequence): CharSequence = input

    /**
     * If set to not empty, matched strings will be highlighted.
     *
     * @param searchString - empty string disables highlighting
     * @param highlightColor
     */
    open fun setSearchString(searchString: CharSequence, highlightColor: Int) {
        // by default does nothing
    }

    /**
     * Called when the engine is stopped.
     * Override this to perform cleanup actions
     */
    protected open fun onStop() {
        // stub
    }

    /**
     * An interface for callbacks issued during the rendering process
     */
    interface OnRenderCallback {
        /**
         * Called when the rendering has finished
         * @param output the platform-agnostic rendered output
         */
        fun onComplete(output: List<TextNode>)

        /**
         * Called when an exception occurred during rendering
         * @param input the raw input string
         */
        fun onError(input: String)
    }
}