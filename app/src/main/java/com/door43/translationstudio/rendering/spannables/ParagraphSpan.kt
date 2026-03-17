package com.door43.translationstudio.rendering.spannables

open class ParagraphSpan internal constructor(
    humanReadable: String,
    machineReadable: String
) : Span(humanReadable, machineReadable) {
    init {
        super.isClickable = false
    }
}
