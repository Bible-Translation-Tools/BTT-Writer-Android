package com.door43.translationstudio.rendering

import android.content.Context
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.ui.spannables.Span

/**
 * ClickableRenderingEngineFactory for creating ClickableRenderingEngine based on format
 */
object ClickableRenderingEngineFactory {

    /**
     * create appropriate rendering engine for format and add click listeners
     * @param context
     * @param format
     * @param defaultFormat
     * @param verseClickListener
     * @param noteClickListener
     * @return
     */
    fun create(
        context: Context,
        format: TranslationFormat,
        defaultFormat: TranslationFormat,
        verseClickListener: Span.OnClickListener?,
        noteClickListener: Span.OnClickListener?
    ): ClickableRenderingEngine {

        val resolvedFormat = if (format != TranslationFormat.USFM && format != TranslationFormat.USX) {
            defaultFormat
        } else {
            format
        }

        return when (resolvedFormat) {
            TranslationFormat.USFM -> USFMRenderer(
                context,
                verseClickListener,
                noteClickListener
            )
            TranslationFormat.USX -> USXRenderer(
                context,
                verseClickListener,
                noteClickListener
            )
            else -> throw IllegalArgumentException("Unsupported rendering format: $resolvedFormat")
        }
    }
}