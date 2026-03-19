package com.door43.translationstudio.rendering.model

/**
 * Platform-agnostic intermediate representation of styled/annotated text.
 * No android.* imports allowed in this file.
 */
sealed class TextNode {

    /** Raw text segment, no styling. */
    data class Text(val content: String) : TextNode()

    /** A text segment with a specific visual style applied. */
    data class Styled(val content: String, val style: NodeStyle) : TextNode()

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
        val machineReadable: String = ""
    ) : TextNode()

    // TODO: Consider adding a stable id/verseRef field here if NoteMarker needs to be
    //       serialised (e.g. SavedStateHandle, analytics). Currently all fields are display strings.
    /**
     * A note marker (footnote or cross-reference). highlighted=true when search matches.
     * @param start Start position of the footnote in the original raw input text (-1 if unknown).
     * @param end End position of the footnote in the original raw input text (-1 if unknown).
     */
    data class NoteMarker(
        val caller: String,
        val passage: String,
        val notes: String,
        val noteStyle: NoteStyle,
        val highlighted: Boolean = false,
        val machineReadable: String = "",
        val start: Int = -1,
        val end: Int = -1
    ) : TextNode()

    /** A paragraph break with optional indent. */
    data class Paragraph(val indented: Boolean = false) : TextNode()

    /** A blank line / spacer. */
    object BlankLine : TextNode()

    /** A section heading. isMajor=true for \ms / <para style="ms">. */
    data class SectionHeading(val text: String, val isMajor: Boolean) : TextNode()

    /** A poetic line. indentLevel 0 = no indent; rightAligned = true for \qr. */
    data class PoeticLine(val content: String, val indentLevel: Int, val rightAligned: Boolean = false) : TextNode()

    /** A chapter label (bold, from \cl or <para style="cl">). */
    data class ChapterLabel(val text: String) : TextNode()

    /** A clickable link. */
    data class Link(val linkData: LinkData) : TextNode()

    /** Text segment highlighted because it matches the current search string. */
    data class SearchHighlight(val content: String) : TextNode()

    /** A line break (\n). */
    object LineBreak : TextNode()
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
