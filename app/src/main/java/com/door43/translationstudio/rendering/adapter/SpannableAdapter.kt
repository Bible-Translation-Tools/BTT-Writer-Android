package com.door43.translationstudio.rendering.adapter

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import com.door43.translationstudio.R
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.NodeStyle
import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.ui.spannables.Span

/**
 * Converts a List<TextNode> to a SpannableStringBuilder for use with Android TextViews.
 * All Android-specific styling lives here — the core renderers have no Android imports.
 */
object SpannableAdapter {

    /**
     * Convert a list of TextNodes to a SpannableStringBuilder.
     *
     * @param nodes                The platform-agnostic node list from a renderer.
     * @param context              Required for color/resource lookups. Null = no color styling.
     * @param verseClickListener   Optional click handler for verse markers.
     * @param noteClickListener    Optional click handler for note markers.
     * @param searchHighlightColor ARGB color for search highlight nodes (0 = no highlight).
     *
     * **Note:** [verseClickListener] and [noteClickListener] are accepted but not yet wired to
     * click spans. Pass them to future-proof call sites; they will take effect when TODOs are resolved.
     */
    fun convert(
        nodes: List<TextNode>,
        context: Context? = null,
        verseClickListener: Span.OnClickListener? = null,
        noteClickListener: Span.OnClickListener? = null,
        searchHighlightColor: Int = 0
    ): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for (node in nodes) {
            appendNode(sb, node, context, verseClickListener, noteClickListener, searchHighlightColor)
        }
        return sb
    }

    private fun appendNode(
        sb: SpannableStringBuilder,
        node: TextNode,
        context: Context?,
        verseClickListener: Span.OnClickListener?,
        noteClickListener: Span.OnClickListener?,
        searchHighlightColor: Int
    ) {
        @Suppress("UNUSED_VARIABLE")
        val exhaustive: Unit = when (node) {
            is TextNode.Text -> { sb.append(node.content); Unit }

            is TextNode.Styled -> {
                val start = sb.length
                sb.append(node.content)
                val end = sb.length
                applyNodeStyle(sb, node.style, start, end)
            }

            TextNode.LineBreak -> { sb.append("\n"); Unit }

            TextNode.BlankLine -> { sb.append("\n\n"); Unit }

            is TextNode.Paragraph -> { sb.append(if (node.indented) "\n    " else "\n"); Unit }

            is TextNode.SectionHeading -> {
                val start = sb.length
                val text = if (node.isMajor) node.text.uppercase() else node.text
                sb.append(text)
                val end = sb.length
                sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.append("\n")
                Unit
            }

            is TextNode.ChapterLabel -> {
                val start = sb.length
                sb.append(node.text)
                val end = sb.length
                sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            is TextNode.PoeticLine -> {
                val padding = "    ".repeat(node.indentLevel)
                val start = sb.length
                sb.append(padding).append(node.content)
                val end = sb.length
                if (node.rightAligned) {
                    sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(
                        AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                Unit
            }

            is TextNode.VerseMarker -> {
                val label = if (node.endVerse > 0) "${node.startVerse}-${node.endVerse}" else "${node.startVerse}"
                val start = sb.length
                sb.append(label)
                val end = sb.length
                context?.let { ctx ->
                    sb.setSpan(RelativeSizeSpan(0.8f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(
                        ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.gray)),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                // TODO: attach verseClickListener click span when pinned=true
                Unit
            }

            is TextNode.NoteMarker -> {
                val start = sb.length
                sb.append(if (node.passage.isNotEmpty()) node.passage else node.caller)
                val end = sb.length
                context?.let { ctx ->
                    // TODO: Use a distinct color for CROSS_REFERENCE notes once a color resource is defined.
                    val bgColor = ContextCompat.getColor(ctx, R.color.footnote_yellow)
                    sb.setSpan(BackgroundColorSpan(bgColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (node.highlighted && searchHighlightColor != 0) {
                    sb.setSpan(BackgroundColorSpan(searchHighlightColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // TODO: attach noteClickListener click span
                Unit
            }

            is TextNode.SearchHighlight -> {
                val start = sb.length
                sb.append(node.content)
                val end = sb.length
                if (searchHighlightColor != 0) {
                    sb.setSpan(
                        BackgroundColorSpan(searchHighlightColor),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                Unit
            }

            is TextNode.Link -> {
                val title = when (val d = node.linkData) {
                    is LinkData.Article -> d.title
                    is LinkData.Passage -> d.title
                    is LinkData.TranslationWord -> d.id
                    is LinkData.Markdown -> d.title
                    is LinkData.ShortReference -> d.ref
                    is LinkData.AppLink -> d.title
                }
                sb.append(title)
                // TODO: attach click span for link navigation
                Unit
            }
        }
    }

    private fun applyNodeStyle(sb: SpannableStringBuilder, style: NodeStyle, start: Int, end: Int) {
        when (style) {
            NodeStyle.BOLD ->
                sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            NodeStyle.ITALIC ->
                sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            NodeStyle.BOLD_CENTER -> {
                sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            NodeStyle.ITALIC_RIGHT -> {
                sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),
                    start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            NodeStyle.NORMAL ->
                sb.setSpan(StyleSpan(Typeface.NORMAL), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
