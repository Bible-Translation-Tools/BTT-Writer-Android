package com.door43.translationstudio.rendering.spannables

import java.util.regex.Pattern

class MarkdownLinkSpan(val title: String, val address: String) : Span(
    title,
    address
) {
    companion object {
        val PATTERN: Pattern = Pattern.compile("\\[\\[(((?!]).)*)]]")
    }
}
