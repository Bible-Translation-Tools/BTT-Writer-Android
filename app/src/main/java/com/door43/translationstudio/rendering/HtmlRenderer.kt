package com.door43.translationstudio.rendering

import android.content.Context
import android.text.Html
import android.text.TextUtils
import com.door43.translationstudio.ui.spannables.ArticleLinkSpan
import com.door43.translationstudio.ui.spannables.MarkdownLinkSpan
import com.door43.translationstudio.ui.spannables.MarkdownTitledLinkSpan
import com.door43.translationstudio.ui.spannables.PassageLinkSpan
import com.door43.translationstudio.ui.spannables.ShortReferenceSpan
import com.door43.translationstudio.ui.spannables.Span
import com.door43.translationstudio.ui.spannables.TranslationWordLinkSpan
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by joel on 12/2/2015.
 */
class HtmlRenderer(
    context: Context,
    private val preprocessCallback: OnPreprocessLink,
    private val linkListener: Span.OnClickListener
) : RenderingEngine() {

    init {
        this.context = context
    }

    override fun render(input: CharSequence): CharSequence {
        var out = input

        out = renderTranslationAcademyAddress(out)
        if (isStopped()) return input

        out = renderPassageLink(out)
        if (isStopped()) return input

        out = renderShortReferenceLink(out)
        if (isStopped()) return input

        out = renderMarkdownLink(out)
        if (isStopped()) return input

        out = renderTranslationWordLink(out)
        if (isStopped()) return input

        // TODO: 12/15/2015 it would be nice if we could pass in a private click listener and
        //  interpret the link types before calling the supplied listener.
        // this will allow calling code to use instance of rather than comparing strings.
        out = Html.fromHtml(
            out.toString(),
            Html.FROM_HTML_MODE_LEGACY,
            null,
            HtmlTagHandler(context, linkListener)
        )
        if (isStopped()) return input

        return out
    }

    private fun renderTranslationWordLink(input: CharSequence): CharSequence {
        return renderLink(input, MarkdownLinkSpan.PATTERN, "tw", object : OnCreateLink {
            override fun onCreate(matcher: Matcher): Span? {
                var address = matcher.group(1)
                    ?.replace("^:".toRegex(), "")
                    ?.trim()
                    ?.lowercase() ?: ""

                // cut off title e.g. en:obe:other:stuff|title
                val addressName = address.split("\\|".toRegex())
                address = addressName[0]

                val chunks = address.split(":")
                if (chunks.size > 2) {
                    var id = ""
                    // check for tw links
                    if (chunks[1] == "obe") {
                        id = chunks[chunks.size - 1]
                    }
                    // TODO: if there are other forms of tw links we can check for them here.

                    if (id.isNotEmpty()) {
                        return TranslationWordLinkSpan(id, id)
                    }
                }
                return null
            }
        })
    }

    private fun renderMarkdownLink(input: CharSequence): CharSequence {
        return renderLink(input, MarkdownTitledLinkSpan.PATTERN, "m", object : OnCreateLink {
            override fun onCreate(matcher: Matcher): Span {
                val title = matcher.group(1) ?: ""
                val address = matcher.group(3) ?: ""
                return MarkdownTitledLinkSpan(title, address)
            }
        })
    }

    /**
     * Renders links to other passages in the project
     */
    private fun renderPassageLink(input: CharSequence): CharSequence {
        return renderLink(input, PassageLinkSpan.PATTERN, "p", object : OnCreateLink {
            override fun onCreate(matcher: Matcher): Span {
                val title = matcher.group(3) ?: ""
                val address = matcher.group(1) ?: ""
                return PassageLinkSpan(title, address)
            }
        })
    }

    /**
     * Renders short references. that is references without a book label.
     * e.g. 1:1 indicates chapter 1 verse 1 of the current book.
     */
    private fun renderShortReferenceLink(input: CharSequence): CharSequence {
        return renderLink(input, ShortReferenceSpan.PATTERN, "sr", object : OnCreateLink {
            override fun onCreate(matcher: Matcher): Span {
                val ref = matcher.group(0) ?: ""
                return ShortReferenceSpan(ref)
            }
        })
    }

    /**
     * Renders addresses to translation academy pages as HTML
     * Example [[en:ta:vol1:translate:translate_unknown | How to Translate Unknowns]]
     */
    fun renderTranslationAcademyAddress(input: CharSequence): CharSequence {
        return renderLink(input, ArticleLinkSpan.ADDRESS_PATTERN, "ta", object : OnCreateLink {
            override fun onCreate(matcher: Matcher): Span {
                val title = matcher.group(4) ?: matcher.group(0) ?: ""
                val address = matcher.group(2) ?: ""
                return ArticleLinkSpan.parse(title, address)
            }
        })
    }

    /**
     * Renders links to translation academy pages as HTML
     * Example <a href="/en/ta/vol1/translate/figs_intro" title="en:ta:vol1:translate:figs_intro">Figures of Speech</a>
     */
    fun renderTranslationAcademyLink(input: CharSequence): CharSequence {
        return renderLink(input, ArticleLinkSpan.LINK_PATTERN, "ta", object : OnCreateLink {
            override fun onCreate(matcher: Matcher): Span {
                val title = matcher.group(6) ?: matcher.group(0) ?: ""
                val address = matcher.group(3)?.replace("/", ":") ?: ""
                return ArticleLinkSpan.parse(title, address)
            }
        })
    }

    /**
     * A generic rendering method for rendering content links as html
     */
    private fun renderLink(
        input: CharSequence,
        pattern: Pattern,
        linkType: String,
        callback: OnCreateLink
    ): CharSequence {
        var out: CharSequence = ""
        val matcher = pattern.matcher(input)
        var lastIndex = 0

        while (matcher.find()) {
            if (isStopped()) return input
            callback.onCreate(matcher)?.let { link ->
                link.onClickListener = linkListener

                if (preprocessCallback.onPreprocess(link)) {
                    // render clickable link
                    var title = link.humanReadable
                    if (title.isEmpty()) {
                        title = link.machineReadable
                    }
                    val htmlLink = "<app-link href=\"${link.machineReadable}\" type=\"$linkType\" >$title</app-link>"
                    out = TextUtils.concat(out, input.subSequence(lastIndex, matcher.start()), htmlLink)
                } else {
                    // render as plain text
                    out = TextUtils.concat(out, input.subSequence(lastIndex, matcher.start()), link.humanReadable)
                }
            } ?: run {
                // ignore link
                out = TextUtils.concat(out, input.subSequence(lastIndex, matcher.end()))
            }

            lastIndex = matcher.end()
        }
        out = TextUtils.concat(out, input.subSequence(lastIndex, input.length))
        return out
    }

    private interface OnCreateLink {
        fun onCreate(matcher: Matcher): Span?
    }

    /**
     * Used to identify which links to render
     */
    fun interface OnPreprocessLink {
        fun onPreprocess(span: Span): Boolean
    }
}