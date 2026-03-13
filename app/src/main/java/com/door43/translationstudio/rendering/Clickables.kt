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
     * Setup rendering group for translation format using a pinVerses flag.
     * @param format
     * @param renderingGroup
     * @param pinVerses true if verse markers should be pinned (i.e. clickable)
     * @param target - true if rendering target translations, false if source text
     * @return
     */
    fun setupRenderingGroup(
        format: TranslationFormat,
        renderingGroup: RenderingGroup,
        pinVerses: Boolean = false,
        target: Boolean
    ): ClickableRenderingEngine {
        val defaultFormat = if (target) TranslationFormat.USFM else TranslationFormat.USX
        val renderer = ClickableRenderingEngineFactory.create(format, defaultFormat, pinVerses)
        renderingGroup.addEngine(renderer)
        return renderer
    }

}
