package com.door43.translationstudio.ui.spannables

import java.util.regex.Pattern

/**
 * Represents a char element according to the usfm specification
 * See http://ubs-icap.org/chm/usfm/2.4/index.html
 */
class USFMChar(style: String, val value: CharSequence) {

    val style: String = style.trim().lowercase()

    companion object {
        // passage styles (custom tag not defined in USFM)
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

        // selah styles
        const val STYLE_SELAH = "qs"

        /**
         * Returns the compiled pattern to match this char
         */
        @JvmStatic
        fun getPattern(style: String): Pattern {
            return Pattern.compile("\\\\f$style+\\s([^\\\\]+)", Pattern.DOTALL) // \\f(\S)+\s([^\\]+)
        }
    }
}