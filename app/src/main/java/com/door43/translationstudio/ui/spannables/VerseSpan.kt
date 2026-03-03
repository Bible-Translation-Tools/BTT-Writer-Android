package com.door43.translationstudio.ui.spannables

open class VerseSpan internal constructor(
    humanReadable: String,
    machineReadable: String
) : Span(humanReadable, machineReadable) {

    /**
     * Returns the start verse number
     */
    open val startVerseNumber: Int
        get() = -1

    /**
     * Returns the end verse number
     */
    open val endVerseNumber: Int
        get() = -1
}
