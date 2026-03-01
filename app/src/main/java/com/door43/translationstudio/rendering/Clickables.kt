package com.door43.translationstudio.rendering

import android.content.Context
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.ui.spannables.Span

/**
 * Class to support clickable spans
 */
object Clickables {

    /**
     * test if this is a clickable format
     * @param format
     * @return
     */
    fun isClickableFormat(format: TranslationFormat): Boolean {
        return format == TranslationFormat.USX || format == TranslationFormat.USFM
    }

    /**
     * setup rendering group for translation format
     * @param context
     * @param format
     * @param renderingGroup
     * @param verseClickListener
     * @param noteClickListener
     * @param target - true if rendering target translations, false if source text
     * @return
     */
    fun setupRenderingGroup(
        context: Context,
        format: TranslationFormat,
        renderingGroup: RenderingGroup,
        verseClickListener: Span.OnClickListener?,
        noteClickListener: Span.OnClickListener?,
        target: Boolean
    ): ClickableRenderingEngine {
        val defaultFormat = if (target) TranslationFormat.USFM else TranslationFormat.USX
        val renderer = ClickableRenderingEngineFactory.create(
            context,
            format,
            defaultFormat,
            verseClickListener,
            noteClickListener
        )
        renderingGroup.addEngine(renderer)
        return renderer
    }
}