package com.door43.translationstudio.rendering

import com.door43.translationstudio.core.TranslationFormat

/**
 * ClickableRenderingEngineFactory for creating ClickableRenderingEngine based on format
 */
object ClickableRenderingEngineFactory {

    /**
     * Create appropriate rendering engine for format using a pinVerses flag.
     * @param format
     * @param defaultFormat
     * @param pinVerses true if verse markers should be pinned (i.e. clickable)
     * @return
     */
    fun create(
        format: TranslationFormat,
        defaultFormat: TranslationFormat,
        pinVerses: Boolean = false
    ): ClickableRenderingEngine {
        val resolvedFormat = if (format != TranslationFormat.USFM && format != TranslationFormat.USX) {
            defaultFormat
        } else {
            format
        }
        return when (resolvedFormat) {
            TranslationFormat.USFM -> USFMRenderer(pinVerses = pinVerses)
            TranslationFormat.USX -> USXRenderer(pinVerses = pinVerses)
            else -> throw IllegalArgumentException("Unsupported rendering format: $resolvedFormat")
        }
    }

}
