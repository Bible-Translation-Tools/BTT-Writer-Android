package com.door43.translationstudio.ui.spannables

import java.util.regex.Pattern

/**
 * Represents a char element according to the usx specification
 * See http://dbl.ubs-icap.org:8090/display/DBLDOCS/USX#USX-char
 */
class USXChar(style: String, val value: CharSequence) {

    val style: String = style.trim().lowercase()

    companion object {
        // passage styles (custom tag not defined in USX)
        const val STYLE_PASSAGE_TEXT = "pt"

        // footnote styles
        const val STYLE_FOOTNOTE_REFERENCE = "fr"
        const val STYLE_FOOTNOTE_TEXT = "ft"
        const val STYLE_FOOTNOTE_KEYWORD = "fk"
        const val STYLE_FOOTNOTE_QUOTATION = "fq"
        const val STYLE_FOOTNOTE_ALT_QUOTATION = "fqa"
        const val STYLE_FOOTNOTE_LABEL = "fl"
        const val STYLE_FOOTNOTE_PARAGRAPH = "fp"
        const val STYLE_FOOTNOTE_VERSE = "fv"
        const val STYLE_FOOTNOTE_DEUTEROCANONICAL_APOCRYPHA = "fdc"

        const val PATTERN = "<char\\s+((?:(?!>).)*)\\s*>\\s*((?:(?!(?:<\\/char>)).)*)\\s*<\\/char>"
        const val CHAR_ATTRIBUTES_GROUP = 1
        const val CHAR_TEXT_GROUP = 2

        // selah styles
        const val STYLE_SELAH = "qs"

        /**
         * Returns the compiled pattern to match this char
         */
        @JvmStatic
        fun getPattern(style: String): Pattern {
            return Pattern.compile(
                "<char\\s+style=\"$style\"\\s*>\\s*(((?!</char>).)*)</char>",
                Pattern.DOTALL
            )
        }
    }
}