package com.door43.translationstudio.rendering.spannables

class TermSpan(
    val termId: String,
    text: String
) : Span(text, text) {

    companion object {
        const val PATTERN = "<keyterm>(((?!</keyterm>).)*)</keyterm>"
    }
}
