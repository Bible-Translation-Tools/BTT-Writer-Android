package com.door43.translationstudio.rendering.spannables

class TranslationWordLinkSpan(
    title: String,
    id: String
) : Span(title, id) {

    var title: String = title
        set(value) {
            field = value
            humanReadable = value
        }
}