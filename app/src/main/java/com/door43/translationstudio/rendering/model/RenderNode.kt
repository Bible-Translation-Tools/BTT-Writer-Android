package com.door43.translationstudio.rendering.model

/**
 * Platform-agnostic hierarchical representation of styled/annotated text.
 * Preserves document structure (parent-child relationships) for context-aware rendering.
 */
sealed class RenderNode {
    abstract val attributes: NodeAttributes

    data class Paragraph(
        val indented: Boolean = false,
        val children: List<RenderNode>,
        override val attributes: NodeAttributes = NodeAttributes()
    ) : RenderNode()

    data class Section(
        val text: String,
        val isMajor: Boolean,
        val children: List<RenderNode> = emptyList(),
        override val attributes: NodeAttributes = NodeAttributes()
    ) : RenderNode()

    data class PoeticLine(
        val indentLevel: Int,
        val rightAligned: Boolean = false,
        val children: List<RenderNode>,
        override val attributes: NodeAttributes = NodeAttributes()
    ) : RenderNode()

    /**
     * A verse marker parsed from the source text.
     * Leaf node - text after verse is a separate Text node.
     *
     * @param startVerse The first (or only) verse number.
     * @param endVerse The last verse in a range, or **0** if this is a single verse.
     * @param pinned When true, display as a pinned icon (tap-to-navigate UI); when false, display as plain number text.
     * @param machineReadable The raw source-format string (e.g. `\v 1 ` for USFM, `<verse number="1" style="v"/>` for USX).
     *   Empty string for synthesised missing-verse markers.
     */
    data class Verse(
        val startVerse: Int,
        val endVerse: Int = 0,
        val pinned: Boolean = false,
        val machineReadable: String = "",
        val start: Int = -1,
        val end: Int = -1,
        override val attributes: NodeAttributes = NodeAttributes()
    ) : RenderNode()

    data class Note(
        val caller: String,
        val passage: String,
        val notes: String,
        val noteStyle: NoteStyle,
        val machineReadable: String = "",
        val startPos: Int = -1,
        val endPos: Int = -1,
        override val attributes: NodeAttributes = NodeAttributes()
    ) : RenderNode()

    data class Text(
        val content: String,
        val start: Int = -1,
        val end: Int = -1,
        override val attributes: NodeAttributes = NodeAttributes()
    ) : RenderNode()

    data class StyledText(
        val content: String,
        val style: NodeStyle,
        override val attributes: NodeAttributes = NodeAttributes()
    ) : RenderNode()

    data class ChapterLabel(
        val text: String,
        override val attributes: NodeAttributes = NodeAttributes()
    ) : RenderNode()

    data class Link(
        val linkData: LinkData,
        override val attributes: NodeAttributes = NodeAttributes()
    ) : RenderNode()

    object LineBreak : RenderNode() {
        override val attributes = NodeAttributes()
    }

    object BlankLine : RenderNode() {
        override val attributes = NodeAttributes()
    }
}

data class NodeAttributes(
    val metadata: Map<String, Any> = emptyMap()
)
