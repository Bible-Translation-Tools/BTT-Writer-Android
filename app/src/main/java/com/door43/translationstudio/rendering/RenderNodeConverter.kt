package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.NodeAttributes
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.rendering.model.TextNode

/**
 * Utility functions to convert between RenderNode (hierarchical, platform-agnostic)
 * and TextNode (flat list, used by UI layer).
 *
 * This bridge allows gradual migration of the UI layer to use the new RenderNode
 * architecture. Eventually the UI adapters should be refactored to work directly
 * with RenderNode using depth-first traversal.
 */
object RenderNodeConverter {

    fun textNodesToRenderNodes(textNodes: List<TextNode>): List<RenderNode> {
        return textNodes.map { node ->
            when (node) {
                is TextNode.Text -> RenderNode.Text(node.content)
                is TextNode.Styled -> RenderNode.StyledText(node.content, node.style)
                is TextNode.VerseMarker -> RenderNode.Verse(
                    startVerse = node.startVerse,
                    endVerse = node.endVerse,
                    pinned = node.pinned,
                    machineReadable = node.machineReadable
                )
                is TextNode.NoteMarker -> RenderNode.Note(
                    caller = node.caller,
                    passage = node.passage,
                    notes = node.notes,
                    noteStyle = node.noteStyle,
                    machineReadable = node.machineReadable,
                    start = node.start,
                    end = node.end,
                    attributes = NodeAttributes(searchHighlighted = node.highlighted)
                )
                is TextNode.Paragraph -> RenderNode.Paragraph(
                    indented = node.indented,
                    children = emptyList()
                )
                is TextNode.SectionHeading -> RenderNode.Section(
                    text = node.text,
                    isMajor = node.isMajor,
                    children = emptyList()
                )
                is TextNode.PoeticLine -> {
                    val children = if (node.content.isNotEmpty()) {
                        listOf(RenderNode.Text(node.content))
                    } else {
                        emptyList()
                    }
                    RenderNode.PoeticLine(
                        indentLevel = node.indentLevel,
                        rightAligned = node.rightAligned,
                        children = children
                    )
                }
                is TextNode.ChapterLabel -> RenderNode.ChapterLabel(node.text)
                is TextNode.Link -> RenderNode.Link(node.linkData)
                is TextNode.SearchHighlight -> RenderNode.Text(node.content,
                    attributes = NodeAttributes(searchHighlighted = true)
                )
                TextNode.LineBreak -> RenderNode.LineBreak
                TextNode.BlankLine -> RenderNode.BlankLine
            }
        }
    }

    fun renderNodesToTextNodes(renderNodes: List<RenderNode>): List<TextNode> {
        return renderNodes.flatMap { node ->
            when (node) {
                is RenderNode.Text -> {
                    val textNode = if (node.attributes.searchHighlighted) {
                        TextNode.SearchHighlight(node.content)
                    } else {
                        TextNode.Text(node.content)
                    }
                    listOf(textNode)
                }
                is RenderNode.StyledText -> listOf(TextNode.Styled(node.content, node.style))
                is RenderNode.Verse -> listOf(TextNode.VerseMarker(
                    startVerse = node.startVerse,
                    endVerse = node.endVerse,
                    pinned = node.pinned,
                    machineReadable = node.machineReadable
                ))
                is RenderNode.Note -> listOf(TextNode.NoteMarker(
                    caller = node.caller,
                    passage = node.passage,
                    notes = node.notes,
                    noteStyle = node.noteStyle,
                    highlighted = node.attributes.searchHighlighted,
                    machineReadable = node.machineReadable,
                    start = node.start,
                    end = node.end
                ))
                is RenderNode.Paragraph -> {
                    val result = mutableListOf<TextNode>()
                    result.add(TextNode.Paragraph(indented = node.indented))
                    result.addAll(renderNodesToTextNodes(node.children))
                    result
                }
                is RenderNode.Section -> {
                    val result = mutableListOf<TextNode>()
                    result.add(TextNode.SectionHeading(text = node.text, isMajor = node.isMajor))
                    result.addAll(renderNodesToTextNodes(node.children))
                    result
                }
                is RenderNode.PoeticLine -> {
                    // Extract text content from children for the PoeticLine content field
                    val content = node.children.joinToString("") { child ->
                        when (child) {
                            is RenderNode.Text -> child.content
                            else -> ""
                        }
                    }
                    listOf(TextNode.PoeticLine(content = content, indentLevel = node.indentLevel, rightAligned = node.rightAligned))
                }
                is RenderNode.ChapterLabel -> listOf(TextNode.ChapterLabel(node.text))
                is RenderNode.Link -> listOf(TextNode.Link(node.linkData))
                RenderNode.LineBreak -> listOf(TextNode.LineBreak)
                RenderNode.BlankLine -> listOf(TextNode.BlankLine)
            }
        }
    }
}
