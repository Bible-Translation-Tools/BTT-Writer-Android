package com.door43.translationstudio.ui.spannables

import java.util.regex.Pattern

class MarkdownLinkSpan(val title: String, val address: String) : Span(
    title,
    address
) {
    companion object {
        @JvmField
        val PATTERN: Pattern = Pattern.compile("\\[\\[(((?!]).)*)]]")
    }
}
