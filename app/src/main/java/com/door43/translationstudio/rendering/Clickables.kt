package com.door43.translationstudio.rendering

import com.door43.translationstudio.core.TranslationFormat

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
     * Setup rendering group for translation format using a verse display mode.
     * @param format
     * @param renderingGroup
     * @param verseDisplay how verse markers should be displayed
     * @param target - true if rendering target translations, false if source text
     * @return
     */
    fun setupRenderingGroup(
        format: TranslationFormat,
        renderingGroup: RenderingGroup,
        verseDisplay: VerseDisplay = VerseDisplay.NUMBER,
        target: Boolean
    ): ClickableRenderingEngine {
        val defaultFormat = if (target) TranslationFormat.USFM else TranslationFormat.USX
        val renderer = ClickableRenderingEngineFactory.create(format, defaultFormat, verseDisplay)
        renderingGroup.addEngine(renderer)
        return renderer
    }

}
