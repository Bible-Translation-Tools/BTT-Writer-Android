package com.door43.translationstudio.rendering

/**
 * This is an abstract base class for clickable rendering engine. This handles all the rendering
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

    abstract fun getLeadingMajorSectionHeading(input: String): String
}