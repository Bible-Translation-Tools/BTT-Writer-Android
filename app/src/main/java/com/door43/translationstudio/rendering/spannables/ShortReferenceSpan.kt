package com.door43.translationstudio.rendering.spannables

import com.door43.util.StringUtilities
import java.util.regex.Pattern

class ShortReferenceSpan(reference: String) : Span(reference, reference) {

    val chapter: String
    val verse: String

    companion object {
        val PATTERN: Pattern = Pattern.compile("\\b(\\d+):(\\d+)\\b")
    }

    init {
        val pieces = reference.split(":")

        chapter = try {
            StringUtilities.normalizeSlug(pieces[0])
        } catch (e: Exception) {
            e.printStackTrace()
            pieces.getOrElse(0) { "" }
        }

        verse = try {
            StringUtilities.normalizeSlug(pieces[1])
        } catch (e: Exception) {
            e.printStackTrace()
            pieces.getOrElse(1) { "" }
        }
    }
}