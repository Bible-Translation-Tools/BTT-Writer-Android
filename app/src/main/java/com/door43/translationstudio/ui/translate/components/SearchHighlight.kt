package com.door43.translationstudio.ui.translate.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

fun AnnotatedString.withSearchHighlight(query: String?): AnnotatedString {
    if (query.isNullOrBlank()) return this
    val lower = text.lowercase()
    val q = query.lowercase()
    var idx = lower.indexOf(q)
    if (idx < 0) return this
    return buildAnnotatedString {
        append(this@withSearchHighlight)
        while (idx >= 0) {
            addStyle(
                SpanStyle(background = Color.Yellow, color = Color.Black),
                idx,
                idx + q.length
            )
            idx = lower.indexOf(q, idx + q.length)
        }
    }
}
