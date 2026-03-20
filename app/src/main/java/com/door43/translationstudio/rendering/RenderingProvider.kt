package com.door43.translationstudio.rendering

import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.TranslationFormat

class RenderingProvider {

    fun createDefaultRenderer(): DefaultRenderer {
        return DefaultRenderer()
    }

    fun createHtmlRenderer(
        preprocessor: HtmlRenderer.OnPreprocessLink
    ): HtmlRenderer {
        return HtmlRenderer(preprocessor)
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
        target: Boolean = true
    ): ClickableRenderingEngine {
        return Clickables.setupRenderingGroup(format, renderingGroup, verseDisplay, target)
    }

    companion object {
        fun getFrameTranslation(
            frameId: String,
            chapterId: String, body: String,
            format: TranslationFormat,
            finished: Boolean
        ): FrameTranslation {
            return FrameTranslation(frameId, chapterId, body, format, finished)
        }

        fun getVerseRange(text: String, format: TranslationFormat): IntArray {
            return Frame.getVerseRange(text, format)
        }
    }
}
