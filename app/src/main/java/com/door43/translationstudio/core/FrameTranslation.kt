package com.door43.translationstudio.core

/**
 * Represents a translation of a frame
 */
data class FrameTranslation(
    val id: String,
    /** Returns the id of the chapter to which this frame belongs */
    val chapterId: String,
    val body: String,
    /** Returns the format of the text */
    val format: TranslationFormat,
    /** Returns true if the translation is finished */
    val finished: Boolean
) {

    /** Returns the complex chapter-frame id */
    val title: String
        get() {
            // get verse range
            val verses = Frame.getVerseRange(body, format)
            return when (verses.size) {
                1 -> "${verses[0]}"
                2 -> "${verses[0]}-${verses[1]}"
                else -> "${id.toIntOrNull() ?: id}"
            }
        }

    /** Returns the complex chapter-frame id */
    val complexId: String
        get() = "$chapterId-$id"

}