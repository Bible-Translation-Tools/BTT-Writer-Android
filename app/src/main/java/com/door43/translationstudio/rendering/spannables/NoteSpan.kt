package com.door43.translationstudio.rendering.spannables

/**
 * abstract base class for NoteSpans
 */
abstract class NoteSpan : Span() {

    /**
     * returns the caller
     */
    abstract val caller: String

    /**
     * Returns the type of note this is
     */
    abstract val style: String

    /**
     * Returns the notes regarding the passage
     */
    abstract val notes: String

    /**
     * Returns the text upon which the notes are made
     */
    abstract val passage: String
}