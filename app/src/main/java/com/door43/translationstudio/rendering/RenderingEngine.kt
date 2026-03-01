package com.door43.translationstudio.rendering

import android.content.Context
import kotlin.concurrent.thread

/**
 * Created by joel on 1/26/2015.
 */
abstract class RenderingEngine {
    protected lateinit var context: Context

    private var callback: OnRenderCallback? = null
    private var stopped = false
    private var running = false

    /**
     * Begins the rendering process
     * @param input the raw input string
     * @param callback the callback that will receive events regarding the rendering
     */
    fun start(input: CharSequence, callback: OnRenderCallback) {
        if (running) return
        this@RenderingEngine.callback = callback
        running = true
        stopped = false

        thread {
            try {
                val output = render(input)
                callback.onComplete(output)
            } catch (e: Exception) {
                callback.onError(input)
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
     * Renders the input string
     * @param input the raw input string
     * @return the rendered output
     */
    open fun render(input: CharSequence): CharSequence {
        return input
    }

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
         * @param output the rendered output
         */
        fun onComplete(output: CharSequence)

        /**
         * Called when an exception occurred during rendering
         * @param input the raw input string
         */
        fun onError(input: CharSequence)
    }
}