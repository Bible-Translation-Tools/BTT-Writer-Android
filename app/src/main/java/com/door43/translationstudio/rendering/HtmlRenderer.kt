package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.spannables.ArticleLinkSpan
import com.door43.translationstudio.rendering.spannables.MarkdownLinkSpan
import com.door43.translationstudio.rendering.spannables.MarkdownTitledLinkSpan
import com.door43.translationstudio.rendering.spannables.PassageLinkSpan
import com.door43.translationstudio.rendering.spannables.ShortReferenceSpan
import com.door43.translationstudio.rendering.spannables.Span
import com.door43.translationstudio.rendering.spannables.TranslationWordLinkSpan

/**
 * HTML rendering engine for help content (translation notes, words, questions).
 *
 * [toAnnotatedHtml] replaces wiki-style links with `<a>` tags using a custom
 * `app://` scheme, returning valid HTML that can be passed to
 * `AnnotatedString.fromHtml()`. The platform handles all HTML tags and entities.
 */
class HtmlRenderer(
    private val preprocessCallback: OnPreprocessLink
) {

    private data class HtmlToken(val start: Int, val end: Int, val replacement: String)

    /**
     * Convert wiki-style links to HTML `<a>` tags with a custom `app://` scheme,
     * preserving the original HTML structure so that `AnnotatedString.fromHtml()`
     * can parse the result correctly.
     *
     * Link scheme: `app://TYPE/data`
     * - `app://ta/ADDRESS` — Translation Academy article
     * - `app://tw/ID` — Translation Word
     * - `app://passage/ADDRESS` — Passage cross-reference
     * - `app://md/ADDRESS` — Markdown / generic link
     * - `app://ref/REF` — Short chapter:verse reference
     *
     * Links rejected by the [preprocessCallback] are replaced with their plain title text.
     *
     * @return valid HTML string ready for `AnnotatedString.fromHtml()`
     */
    fun toAnnotatedHtml(input: String): String {
        val allTokens = mutableListOf<HtmlToken>()
        allTokens.addAll(findTaAddressTokens(input))
        allTokens.addAll(findPassageTokens(input))
        allTokens.addAll(findShortRefTokens(input))
        allTokens.addAll(findMarkdownTokens(input))
        allTokens.addAll(findTwTokens(input))

        allTokens.sortBy { it.start }
        val tokens = removeOverlaps(allTokens)

        if (tokens.isEmpty()) return input

        val sb = StringBuilder()
        var lastEnd = 0
        for (token in tokens) {
            sb.append(input, lastEnd, token.start)
            sb.append(token.replacement)
            lastEnd = token.end
        }
        sb.append(input, lastEnd, input.length)
        return sb.toString()
    }

    private fun removeOverlaps(sorted: List<HtmlToken>): List<HtmlToken> {
        val result = mutableListOf<HtmlToken>()
        var lastEnd = 0
        for (token in sorted) {
            if (token.start >= lastEnd) {
                result.add(token)
                lastEnd = token.end
            }
        }
        return result
    }

    private fun buildAnchor(scheme: String, data: String, title: String): String {
        val escaped = title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        return """<a href="app://$scheme/${data.encodeForHref()}">$escaped</a>"""
    }

    private fun String.encodeForHref(): String =
        replace("\"", "%22").replace(" ", "%20")

    private fun findTaAddressTokens(text: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val matcher = ArticleLinkSpan.ADDRESS_PATTERN.matcher(text)
        while (matcher.find()) {
            val rawAddress = matcher.group(2) ?: ""
            val titleFallback = rawAddress.substringAfterLast(':').takeIf { it.isNotEmpty() } ?: rawAddress
            val title = matcher.group(4) ?: titleFallback
            val span = ArticleLinkSpan.parse(title, rawAddress)
            if (span.machineReadable.isNotEmpty() && preprocessCallback.onPreprocess(span)) {
                tokens.add(HtmlToken(
                    matcher.start(), matcher.end(),
                    buildAnchor("ta", span.machineReadable, span.humanReadable)
                ))
            } else {
                tokens.add(HtmlToken(matcher.start(), matcher.end(), span.humanReadable))
            }
        }
        return tokens
    }

    private fun findPassageTokens(text: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val matcher = PassageLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val title = matcher.group(3) ?: ""
            val address = matcher.group(1) ?: ""
            val span = PassageLinkSpan(title, address)
            if (preprocessCallback.onPreprocess(span)) {
                tokens.add(HtmlToken(
                    matcher.start(), matcher.end(),
                    buildAnchor("passage", span.machineReadable, span.humanReadable)
                ))
            } else {
                tokens.add(HtmlToken(matcher.start(), matcher.end(), span.humanReadable))
            }
        }
        return tokens
    }

    private fun findShortRefTokens(text: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val matcher = ShortReferenceSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val ref = matcher.group(0) ?: ""
            val span = ShortReferenceSpan(ref)
            if (preprocessCallback.onPreprocess(span)) {
                tokens.add(HtmlToken(
                    matcher.start(), matcher.end(),
                    buildAnchor("ref", span.humanReadable, span.humanReadable)
                ))
            } else {
                tokens.add(HtmlToken(matcher.start(), matcher.end(), span.humanReadable))
            }
        }
        return tokens
    }

    private fun findMarkdownTokens(text: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val matcher = MarkdownTitledLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            val title = matcher.group(1) ?: ""
            val address = matcher.group(3) ?: ""
            val span = MarkdownTitledLinkSpan(title, address)
            if (preprocessCallback.onPreprocess(span)) {
                val (scheme, data) = when {
                    address.startsWith("rc://") -> "rc" to span.machineReadable
                    address.endsWith(".md") -> {
                        val wordId = address.substringAfterLast('/')
                            .substringBeforeLast('.')
                        "tw" to wordId
                    }
                    else -> "md" to span.machineReadable
                }
                tokens.add(HtmlToken(
                    matcher.start(), matcher.end(),
                    buildAnchor(scheme, data, span.humanReadable)
                ))
            } else {
                tokens.add(HtmlToken(matcher.start(), matcher.end(), span.humanReadable))
            }
        }
        return tokens
    }

    private fun findTwTokens(text: String): List<HtmlToken> {
        val tokens = mutableListOf<HtmlToken>()
        val matcher = MarkdownLinkSpan.PATTERN.matcher(text)
        while (matcher.find()) {
            var address = matcher.group(1)
                ?.replace("^:".toRegex(), "")
                ?.trim()
                ?.lowercase() ?: ""
            val addressName = address.split("\\|".toRegex())
            address = addressName[0]
            val chunks = address.split(":")
            if (chunks.size > 2 && chunks[1] == "obe") {
                val id = chunks[chunks.size - 1]
                if (id.isNotEmpty()) {
                    val span = TranslationWordLinkSpan(id, id)
                    if (preprocessCallback.onPreprocess(span)) {
                        tokens.add(HtmlToken(
                            matcher.start(), matcher.end(),
                            buildAnchor("tw", id, span.humanReadable)
                        ))
                    } else {
                        tokens.add(HtmlToken(matcher.start(), matcher.end(), id))
                    }
                }
            } else {
                tokens.add(HtmlToken(
                    matcher.start(), matcher.end(),
                    matcher.group(0) ?: ""
                ))
            }
        }
        return tokens
    }

    fun interface OnPreprocessLink {
        fun onPreprocess(span: Span): Boolean
    }

    companion object {
        /** Parse an `app://TYPE/DATA` URL back into [LinkData]. */
        fun parseLinkUrl(url: String): LinkData? {
            if (!url.startsWith("app://")) return null
            val path = url.removePrefix("app://")
            val slash = path.indexOf('/')
            if (slash < 0) return null
            val type = path.substring(0, slash)
            val data = path.substring(slash + 1)
                .replace("%22", "\"").replace("%20", " ")
            return when (type) {
                "ta" -> LinkData.Article(address = data, title = "")
                "tw" -> LinkData.TranslationWord(id = data)
                "passage" -> LinkData.Passage(address = data, title = "")
                "md" -> LinkData.Markdown(address = data, title = "")
                "rc" -> LinkData.RcLink(address = data, title = "")
                "ref" -> LinkData.ShortReference(ref = data)
                else -> null
            }
        }
    }
}
