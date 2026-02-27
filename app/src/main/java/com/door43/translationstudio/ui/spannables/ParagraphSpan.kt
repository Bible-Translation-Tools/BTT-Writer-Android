package com.door43.translationstudio.ui.spannables

open class ParagraphSpan internal constructor(
    humanReadable: CharSequence,
    machineReadable: CharSequence
) : Span(humanReadable, machineReadable) {
    init {
        super.isClickable = false
    }
}
