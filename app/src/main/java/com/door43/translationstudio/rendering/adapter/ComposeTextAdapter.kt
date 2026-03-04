// rendering/adapter/ComposeTextAdapter.kt
package com.door43.translationstudio.rendering.adapter

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.NodeStyle
import com.door43.translationstudio.rendering.model.TextNode

/**
 * Converts a List<TextNode> to Compose AnnotatedString.
 *
 * Usage in a Composable:
 *   val annotated = ComposeTextAdapter.convert(
 *     nodes,
 *     onNoteClick = { notes -> showDialog(notes) }
 *   )
 *   Text(annotated)
 *
 * Click handling: note markers include LinkAnnotation that calls onNoteClick.
 */
object ComposeTextAdapter {

    fun convert(
        nodes: List<TextNode>,
        searchHighlightColor: Color = Color.Yellow,
        verseColor: Color = Color.Gray,
        noteColor: Color = Color(0xFFFFD700),  // amber
        onNoteClick: (String) -> Unit = {}
    ): AnnotatedString = buildAnnotatedString {
        for (node in nodes) {
            appendNode(node, searchHighlightColor, verseColor, noteColor, onNoteClick)
        }
    }

    private fun AnnotatedString.Builder.appendNode(
        node: TextNode,
        searchHighlightColor: Color,
        verseColor: Color,
        noteColor: Color,
        onNoteClick: (String) -> Unit
    ) {
        when (node) {
            is TextNode.Text -> append(node.content)

            is TextNode.Styled -> {
                val style = when (node.style) {
                    NodeStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    NodeStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    NodeStyle.BOLD_CENTER -> SpanStyle(fontWeight = FontWeight.Bold)
                    NodeStyle.ITALIC_RIGHT -> SpanStyle(fontStyle = FontStyle.Italic)
                    NodeStyle.NORMAL -> SpanStyle()
                }
                pushStyle(style)
                append(node.content)
                pop()
            }

            is TextNode.LineBreak -> append("\n")
            is TextNode.BlankLine -> append("\n\n")
            is TextNode.Paragraph -> append(if (node.indented) "\n    " else "\n")

            is TextNode.SectionHeading -> {
                val text = if (node.isMajor) node.text.uppercase() else node.text
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(text)
                pop()
                append("\n")
            }

            is TextNode.ChapterLabel -> {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(node.text)
                pop()
            }

            is TextNode.PoeticLine -> {
                val padding = "    ".repeat(node.indentLevel)
                if (node.rightAligned) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append("$padding${node.content}")
                    pop()
                } else {
                    append("$padding${node.content}")
                }
            }

            is TextNode.VerseMarker -> {
                val label = if (node.endVerse > 0) "${node.startVerse}-${node.endVerse}" else "${node.startVerse}"
                pushStyle(SpanStyle(fontSize = 16.sp, color = verseColor))
                addStringAnnotation(tag = "VERSE", annotation = label, start = length, end = length + label.length)
                append(label)
                pop()
            }

            is TextNode.NoteMarker -> {
                val start = length
                pushStyle(SpanStyle(color = noteColor, fontStyle = FontStyle.Italic))
                appendInlineContent("footnote_icon", "footnote")
                val end = length
                addLink(
                    LinkAnnotation.Clickable(tag = "NOTE") { onNoteClick(node.notes) },
                    start = start,
                    end = end
                )
                pop()
            }

            is TextNode.SearchHighlight -> {
                pushStyle(SpanStyle(background = searchHighlightColor))
                append(node.content)
                pop()
            }

            is TextNode.Link -> {
                val (tag, annotation, title) = when (val d = node.linkData) {
                    is LinkData.Article -> Triple("TA", d.address, d.title)
                    is LinkData.Passage -> Triple("PASSAGE", d.address, d.title)
                    is LinkData.TranslationWord -> Triple("TW", d.id, d.id)
                    is LinkData.Markdown -> Triple("MD", d.address, d.title)
                    is LinkData.ShortReference -> Triple("REF", d.ref, d.ref)
                    is LinkData.AppLink -> Triple(d.linkType, d.href, d.title)
                }
                val start = length
                pushStyle(SpanStyle(color = Color.Blue))
                addStringAnnotation(tag = tag, annotation = annotation, start = start, end = start + title.length)
                append(title)
                pop()
            }
        }
    }
}
