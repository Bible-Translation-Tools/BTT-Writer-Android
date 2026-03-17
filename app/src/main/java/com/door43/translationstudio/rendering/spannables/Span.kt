package com.door43.translationstudio.rendering.spannables

abstract class Span {
    var humanReadable: String = ""
        protected set

    var machineReadable: String = ""
        protected set

    var isClickable: Boolean = true
    var extras: Map<String, Any>? = null

    /**
     * Creates a new empty span.
     * This is useful for classes that extends this class because they may need to perform
     * some processing before fully initializing.
     * You should manually call init() if using this constructor
     */
    protected constructor() {
        init("", "")
    }

    /**
     * Creates a new span
     * @param humanReadable the human-readable title of the span
     * @param machineReadable the machine-readable definition of the span
     */
    internal constructor(humanReadable: String, machineReadable: String) {
        init(humanReadable, machineReadable)
    }

    /**
     * Initializes the span
     * @param humanReadable
     * @param machineReadable
     */
    protected fun init(humanReadable: String, machineReadable: String) {
        this.humanReadable = humanReadable
        this.machineReadable = machineReadable
    }

}
