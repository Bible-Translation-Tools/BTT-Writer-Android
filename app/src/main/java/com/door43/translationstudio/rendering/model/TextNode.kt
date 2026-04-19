package com.door43.translationstudio.rendering.model

/**
 * Platform-agnostic intermediate representation of styled/annotated text.
 * No android.* imports allowed in this file.
 */
@Deprecated("Remove when migrated to compose")
sealed class TextNode {
    abstract val startPos: Int
    abstract val endPos: Int

    /** Raw text segment, no styling. */
    data class Text(
        val content: String,
        override val startPos: Int = -1,
        override val endPos: Int = -1
    ) : TextNode()

    /** A text segment with a specific visual style applied. */
    data class Styled(
        val content: String,
        val style: NodeStyle,
        override val startPos: Int = -1,
        override val endPos: Int = -1
    ) : TextNode()

    /**
     * A verse marker parsed from the source text.
     *
     * @param startVerse The first (or only) verse number.
     * @param endVerse The last verse in a range, or **0** if this is a single verse.
     * @param pinned When true, display as a pinned icon (tap-to-navigate UI); when false, display as plain number text.
     * @param machineReadable The raw source-format string (e.g. `\v 1 ` for USFM, `<verse number="1" style="v"/>` for USX).
     *   Empty string for synthesized missing-verse markers.
     *   the machine-readable form after drag-and-drop.
     */
    data class VerseMarker(
        val startVerse: Int,
        val endVerse: Int,
        val pinned: Boolean,
        val machineReadable: String = "",
        override val startPos: Int = -1,
        override val endPos: Int = -1
    ) : TextNode()

    /**
     * A note marker (footnote or cross-reference).
     * @param startPos Start position of the footnote in the original raw input text (-1 if unknown).
     * @param endPos End position of the footnote in the original raw input text (-1 if unknown).
     */
    data class NoteMarker(
        val caller: String,
        val passage: String,
        val notes: String,
        val noteStyle: NoteStyle,
        val machineReadable: String = "",
        override val startPos: Int = -1,
        override val endPos: Int = -1
    ) : TextNode()

    /** A paragraph break with optional indent. */
    data class Paragraph(
        val indented: Boolean = false,
        override val startPos: Int = -1,
        override val endPos: Int = -1
    ) : TextNode()

    /** A blank line / spacer. */
    object BlankLine : TextNode() {
        override val startPos: Int = -1
        override val endPos: Int = -1
    }

    /** A section heading. isMajor=true for \ms / <para style="ms">. */
    data class SectionHeading(
        val text: String,
        val isMajor: Boolean,
        override val startPos: Int = -1,
        override val endPos: Int = -1
    ) : TextNode()

    /** A poetic line. indentLevel 0 = no indent; rightAligned = true for \qr. */
    data class PoeticLine(
        val content: String,
        val indentLevel: Int,
        val rightAligned: Boolean = false,
        override val startPos: Int = -1,
        override val endPos: Int = -1
    ) : TextNode()

    /** A chapter label (bold, from \cl or <para style="cl">). */
    data class ChapterLabel(
        val text: String,
        override val startPos: Int = -1,
        override val endPos: Int = -1
    ) : TextNode()

    /** A clickable link. */
    data class Link(
        val linkData: LinkData,
        override val startPos: Int = -1,
        override val endPos: Int = -1
    ) : TextNode()

    /** A line break (\n). */
    object LineBreak : TextNode() {
        override val startPos: Int = -1
        override val endPos: Int = -1
    }
}

enum class NodeStyle {
    BOLD,
    ITALIC,
    BOLD_CENTER,
    ITALIC_RIGHT,
    NORMAL,
}

enum class NoteStyle {
    FOOTNOTE,
    CROSS_REFERENCE,
}
