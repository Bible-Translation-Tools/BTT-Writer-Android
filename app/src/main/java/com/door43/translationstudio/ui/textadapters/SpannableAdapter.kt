package com.door43.translationstudio.ui.textadapters

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.SpannedString
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentVerseMarkerBinding
import com.door43.translationstudio.rendering.adapter.NoteClickListener
import com.door43.translationstudio.rendering.adapter.VerseClickListener
import com.door43.translationstudio.rendering.adapter.VerseLongClickListener
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.NodeStyle
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.widget.LongClickableSpan
import com.door43.widget.ViewUtil

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
     * @param verseClickListener   Optional click handler for verse markers (only fires when pinned=true).
     * @param verseLongClickListener Optional long-click handler for verse markers (only fires when pinned=true).
     * @param noteClickListener    Optional click handler for note markers.
     * @param searchHighlightColor ARGB color for search highlight nodes (0 = no highlight).
     */
    fun convert(
        nodes: List<TextNode>,
        context: Context? = null,
        verseClickListener: VerseClickListener? = null,
        verseLongClickListener: VerseLongClickListener? = null,
        noteClickListener: NoteClickListener? = null,
        searchHighlightColor: Int = 0
    ): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        var currentPoeticalLineIndent = 0  // Track current poetic line indent level
        var isFirstElementOfPoeticLine = true  // Track if next element is first child of poetic line
        var verseMarkerAddedIndentation = false  // Track if verse marker already added indentation
        var lastWasPoeticLineMarker = false  // Track if previous node was a PoeticLine marker

        for (node in nodes) {
            // Update poetic line context when we encounter a PoeticLine marker
            if (node is TextNode.PoeticLine && node.content.isEmpty()) {
                // This is a poetic line marker (e.g., <para style="q1">)
                currentPoeticalLineIndent = node.indentLevel
                isFirstElementOfPoeticLine = true
                verseMarkerAddedIndentation = false
                lastWasPoeticLineMarker = true
            } else if (node is TextNode.LineBreak || node is TextNode.Paragraph) {
                // Reset context at line/paragraph boundaries
                currentPoeticalLineIndent = 0
                isFirstElementOfPoeticLine = false
                verseMarkerAddedIndentation = false
                lastWasPoeticLineMarker = false
            } else if (node is TextNode.VerseMarker && isFirstElementOfPoeticLine && currentPoeticalLineIndent > 0) {
                // Verse marker as first element in poetic line will add indentation
                verseMarkerAddedIndentation = true
                lastWasPoeticLineMarker = false
            } else if (node !is TextNode.PoeticLine) {
                // Mark that we've processed a non-poetic-marker element
                if (node !is TextNode.VerseMarker) {
                    isFirstElementOfPoeticLine = false
                }
                lastWasPoeticLineMarker = false
            }

            // Skip whitespace-only text nodes that appear right after a poetic line marker
            // (these are gap nodes from the renderer)
            if (node is TextNode.Text && lastWasPoeticLineMarker && currentPoeticalLineIndent > 0 && node.content.trim().isEmpty()) {
                continue
            }

            // Pass isFirstElementOfPoetic=true only for verse markers that are first, not for text nodes
            val isFirstForNode = if (node is TextNode.VerseMarker) isFirstElementOfPoeticLine else false
            appendNode(sb, node, context, verseClickListener, verseLongClickListener, noteClickListener, searchHighlightColor,
                      currentPoeticalLineIndent, isFirstForNode, verseMarkerAddedIndentation)
        }
        return sb
    }

    private fun appendNode(
        sb: SpannableStringBuilder,
        node: TextNode,
        context: Context?,
        verseClickListener: VerseClickListener?,
        verseLongClickListener: VerseLongClickListener?,
        noteClickListener: NoteClickListener?,
        searchHighlightColor: Int,
        poeticalLineIndent: Int = 0,
        isFirstElementOfPoetic: Boolean = false,
        verseMarkerAddedIndentation: Boolean = false
    ) {
        @Suppress("UNUSED_VARIABLE")
        val exhaustive: Unit = when (node) {
            is TextNode.Text -> {
                // Apply indentation for text in poetic lines, unless verse marker already added it
                // Don't add indentation for empty/whitespace-only text (could be a gap before verse marker)
                if (poeticalLineIndent > 0 && !verseMarkerAddedIndentation && node.content.trim().isNotEmpty()) {
                    // Indent text in poetic lines from column 0
                    val padding = "    ".repeat(poeticalLineIndent)
                    sb.append(padding)
                }
                sb.append(node.content)
                Unit
            }

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
                if (node.content.isEmpty()) {
                    // Marker node - just output a newline, indentation will be handled by following Text nodes
                    sb.append("\n")
                } else {
                    // Content node (e.g., Selah) - apply indentation from node's own level
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
                    sb.append("\n")
                }
                Unit
            }

            is TextNode.VerseMarker -> {
                val label = if (node.endVerse > 0) "${node.startVerse}-${node.endVerse}" else "${node.startVerse}"
                val start = sb.length
                if (node.pinned && context != null) {
                    // Render verse-pin bitmap using the layout
                    val inflater = LayoutInflater.from(context)
                    val pinBinding = FragmentVerseMarkerBinding.inflate(inflater)
                    pinBinding.verse.text = label
                    val bitmap = ViewUtil.convertToBitmap(pinBinding.root)
                    val drawable = BitmapDrawable(context.resources, bitmap)
                    drawable.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
                    sb.append(label)   // placeholder text (ImageSpan visually replaces it)
                    val end = sb.length
                    sb.setSpan(ImageSpan(drawable), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(0.8f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    // Add SpannedString span so Translator.compileTranslation() can reconstruct
                    // the machine-readable source format after drag-and-drop.
                    if (node.machineReadable.isNotEmpty()) {
                        sb.setSpan(
                            SpannedString(node.machineReadable),
                            start, end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                } else {
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
                    // Add SpannedString span so Translator.compileTranslation() can reconstruct
                    // the machine-readable source format after drag-and-drop.
                    if (node.machineReadable.isNotEmpty()) {
                        sb.setSpan(
                            SpannedString(node.machineReadable),
                            start, end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                if (node.pinned && (verseClickListener != null || verseLongClickListener != null)) {
                    val spanStart = start
                    val spanEnd = sb.length
                    sb.setSpan(
                        object : LongClickableSpan() {
                            override fun onClick(view: View) {
                                verseClickListener?.onVerseClick(view, node, spanStart, spanEnd)
                            }
                            override fun onLongClick(view: View) {
                                verseLongClickListener?.onVerseLongClick(view, node, spanStart, spanEnd)
                            }
                        },
                        start, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                // When verse marker is the first element of a poetic line, add indentation after it
                // for the subsequent text to appear at the poetic line indent level
                if (isFirstElementOfPoetic && poeticalLineIndent > 0) {
                    val padding = "    ".repeat(poeticalLineIndent)
                    sb.append(padding)
                }
                Unit
            }

            is TextNode.NoteMarker -> {
                val start = sb.length
                sb.append("†")  // placeholder character for note icon
                val end = sb.length
                context?.let { ctx ->
                    // Render note marker as icon
                    val drawable = ContextCompat.getDrawable(ctx, R.drawable.ic_description_secondary_24dp)
                    if (drawable != null) {
                        drawable.setBounds(0, 0, drawable.minimumWidth, drawable.minimumHeight)
                        sb.setSpan(ImageSpan(drawable), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                if (node.highlighted && searchHighlightColor != 0) {
                    sb.setSpan(BackgroundColorSpan(searchHighlightColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // Store the original USFM/USX footnote code as a span so it can be reconstructed
                // when the text is compiled back after drag-and-drop
                if (node.machineReadable.isNotEmpty()) {
                    sb.setSpan(
                        SpannedString(node.machineReadable),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                noteClickListener?.let { listener ->
                    val spanStart = start
                    val spanEnd = end
                    sb.setSpan(
                        object : LongClickableSpan() {
                            override fun onClick(view: View) {
                                listener.onNoteClick(view, node, spanStart, spanEnd)
                            }
                            override fun onLongClick(view: View) { /* notes only support click, not long-click */ }
                        },
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
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
