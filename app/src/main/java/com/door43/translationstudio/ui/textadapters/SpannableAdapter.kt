package com.door43.translationstudio.ui.textadapters

import android.content.Context
import android.text.SpannableStringBuilder
import com.door43.translationstudio.rendering.adapter.NoteClickListener
import com.door43.translationstudio.rendering.adapter.VerseClickListener
import com.door43.translationstudio.rendering.adapter.VerseLongClickListener
import com.door43.translationstudio.rendering.model.TextNode

/**
 * DEPRECATED: Legacy stub. Real rendering moved to ComposeTextAdapter.
 * Returns plain text without any spans. Exists only to keep legacy adapters compilable
 * until they are migrated to Compose.
 */
@Deprecated("Use ComposeTextAdapter instead. This stub will be removed with the legacy adapters.")
object SpannableAdapter {
    fun convert(
        nodes: List<TextNode>,
        context: Context? = null,
        verseClickListener: VerseClickListener? = null,
        verseLongClickListener: VerseLongClickListener? = null,
        noteClickListener: NoteClickListener? = null,
        searchHighlightColor: Int = 0
    ): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        for (node in nodes) {
            when (node) {
                is TextNode.Text -> sb.append(node.content)
                is TextNode.Styled -> sb.append(node.content)
                is TextNode.VerseMarker -> {
                    val label = if (node.endVerse > 0) "${node.startVerse}-${node.endVerse}" else "${node.startVerse}"
                    sb.append(label)
                }
                is TextNode.NoteMarker -> sb.append("\u2020")
                is TextNode.Paragraph -> sb.append(if (node.indented) "\n    " else "\n")
                is TextNode.SectionHeading -> sb.append(node.text).append("\n")
                is TextNode.ChapterLabel -> sb.append(node.text)
                is TextNode.PoeticLine -> {
                    if (node.content.isNotEmpty()) sb.append(node.content)
                    sb.append("\n")
                }
                is TextNode.SearchHighlight -> sb.append(node.content)
                is TextNode.Link -> sb.append("")
                TextNode.LineBreak -> sb.append("\n")
                TextNode.BlankLine -> sb.append("\n\n")
            }
        }
        return sb
    }
}
