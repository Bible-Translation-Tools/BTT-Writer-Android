package com.door43.translationstudio.ui.spannables

class USFMParagraphSpan : ParagraphSpan("\n", "\\p ") {

    companion object {
        const val PATTERN = "\\\\p\\W?"
    }
}
