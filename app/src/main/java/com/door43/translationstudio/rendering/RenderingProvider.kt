package com.door43.translationstudio.rendering

import android.content.Context
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.ui.spannables.Span

class RenderingProvider(
    private val context: Context
) {

    fun createDefaultRenderer(): DefaultRenderer {
        return DefaultRenderer(context)
    }

    fun createHtmlRenderer(
        preprocessor: HtmlRenderer.OnPreprocessLink,
        linkListener: Span.OnClickListener
    ): HtmlRenderer {
        return HtmlRenderer(context, preprocessor, linkListener)
    }

    fun setupRenderingGroup(
        format: TranslationFormat,
        renderingGroup: RenderingGroup,
        verseClickListener: Span.OnClickListener? = null,
        noteClickListener: Span.OnClickListener? = null,
        target: Boolean = true
    ): ClickableRenderingEngine {
        return Clickables.setupRenderingGroup(
            context,
            format,
            renderingGroup,
            verseClickListener,
            noteClickListener,
            target
        )
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