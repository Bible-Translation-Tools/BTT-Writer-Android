package com.door43.translationstudio.core

import android.content.Context
import com.door43.translationstudio.rendering.ClickableRenderingEngine
import com.door43.translationstudio.rendering.Clickables
import com.door43.translationstudio.rendering.DefaultRenderer
import com.door43.translationstudio.rendering.HtmlRenderer
import com.door43.translationstudio.rendering.LinkToHtmlRenderer
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.ui.spannables.Span
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class RenderingProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createDefaultRenderer(): DefaultRenderer {
        return DefaultRenderer(context)
    }

    fun createLinkToHtmlRenderer(
        preprocessor: LinkToHtmlRenderer.OnPreprocessLink
    ): LinkToHtmlRenderer {
        return LinkToHtmlRenderer(context, preprocessor)
    }

    fun createHtmlRenderer(
        preprocessor: HtmlRenderer.OnPreprocessLink,
        linkListener: Span.OnClickListener
    ): HtmlRenderer {
        return HtmlRenderer(context, preprocessor, linkListener)
    }

    fun setupRenderingGroup(
        format: TranslationFormat?,
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