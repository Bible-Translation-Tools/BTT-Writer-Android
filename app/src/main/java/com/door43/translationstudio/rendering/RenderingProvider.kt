package com.door43.translationstudio.rendering

import android.content.Context
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.TranslationFormat

class RenderingProvider(
    private val context: Context
) {

    fun createDefaultRenderer(): DefaultRenderer {
        return DefaultRenderer(context)
    }

    fun createHtmlRenderer(
        preprocessor: HtmlRenderer.OnPreprocessLink
    ): HtmlRenderer {
        return HtmlRenderer(preprocessor)
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
        target: Boolean = true
    ): ClickableRenderingEngine {
        return Clickables.setupRenderingGroup(context, format, renderingGroup, pinVerses, target)
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

        fun getVerseRange(text: CharSequence, format: TranslationFormat): IntArray {
            return Frame.getVerseRange(text, format)
        }
    }
}
