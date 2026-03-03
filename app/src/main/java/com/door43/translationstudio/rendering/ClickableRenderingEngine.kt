package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.TextNode

/**
 * This is an abstract base class for clickable rendering engine. This handles all of the rendering
 * for USX formatted source and translation.
 */
abstract class ClickableRenderingEngine : RenderingEngine() {

    abstract val isAddedMissingVerse: Boolean

    /**
     * if set to false verses will not be displayed in the output.
     *
     * @param enable default is true
     */
    abstract fun setVersesEnabled(enable: Boolean)

    /**
     * if set to true, then paragraphs (\p) will be rendered in the output.
     *
     * @param enable default is true
     */
    abstract fun setParagraphsEnabled(enable: Boolean)

    /**
     * If set to not empty, matched strings will be highlighted.
     *
     * @param searchString - empty string disables highlighting
     * @param highlightColor
     */
    abstract override fun setSearchString(searchString: CharSequence, highlightColor: Int)

    /**
     * Specifies an inclusive range of verses expected in the input.
     * If a verse is not found it will be inserted at the front of the input.
     * @param verseRange
     */
    abstract fun setPopulateVerseMarkers(verseRange: IntArray)

    /**
     * Set whether to suppress display of major section headers.
     *
     * <p>The intent behind this is that major section headers prior to chapter markers will be
     * displayed above chapter markers, but only in read mode.</p>
     *
     * @param suppressLeadingMajorSectionHeadings The value to set
     */
    abstract fun setSuppressLeadingMajorSectionHeadings(suppressLeadingMajorSectionHeadings: Boolean)

    /**
     * Renders all verse tags
     * @param input
     * @return platform-agnostic list of [TextNode] describing the rendered verse
     */
    abstract fun renderVerse(input: CharSequence): List<TextNode>

    abstract fun getLeadingMajorSectionHeading(input: CharSequence): String
}