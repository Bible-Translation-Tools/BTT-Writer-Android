package com.door43.translationstudio.rendering.spannables

class USFMParagraphSpan : ParagraphSpan("\n", "\\p ") {

    companion object {
        const val PATTERN = "\\\\p\\W?"
    }
}
