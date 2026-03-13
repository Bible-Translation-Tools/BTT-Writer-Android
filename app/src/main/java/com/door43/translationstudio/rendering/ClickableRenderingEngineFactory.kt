package com.door43.translationstudio.rendering

import com.door43.translationstudio.core.TranslationFormat

/**
 * ClickableRenderingEngineFactory for creating ClickableRenderingEngine based on format
 */
object ClickableRenderingEngineFactory {

    /**
     * Create appropriate rendering engine for format using a verse display mode.
     * @param format
     * @param defaultFormat
     * @param verseDisplay how verse markers should be displayed
     * @return
     */
    fun create(
        format: TranslationFormat,
        defaultFormat: TranslationFormat,
        verseDisplay: VerseDisplay = VerseDisplay.NUMBER
    ): ClickableRenderingEngine {
        val resolvedFormat = if (format != TranslationFormat.USFM && format != TranslationFormat.USX) {
            defaultFormat
        } else {
            format
        }
        return when (resolvedFormat) {
            TranslationFormat.USFM -> USFMRenderer(verseDisplay = verseDisplay)
            TranslationFormat.USX -> USXRenderer(verseDisplay = verseDisplay)
            else -> throw IllegalArgumentException("Unsupported rendering format: $resolvedFormat")
        }
    }

}
