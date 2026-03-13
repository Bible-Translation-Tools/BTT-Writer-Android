package com.door43.translationstudio.rendering

/**
 * Controls how verse markers (\v) are rendered.
 */
enum class VerseDisplay {
    /** Render as clickable pin icons (for drag-and-drop in review mode). */
    PIN,
    /** Render as plain verse numbers. */
    NUMBER,
    /** Don't parse verse markers — leave the raw USFM/USX markup as-is. */
    RAW
}
