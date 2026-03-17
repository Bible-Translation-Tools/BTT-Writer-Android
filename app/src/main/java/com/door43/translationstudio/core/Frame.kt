package com.door43.translationstudio.core

import com.door43.translationstudio.rendering.Clickables
import com.door43.translationstudio.rendering.spannables.USFMVerseSpan
import com.door43.translationstudio.rendering.spannables.USXVerseSpan
import org.json.JSONException
import org.json.JSONObject

data class Frame(
    val id: String,
    val chapterId: String,
    val body: String,
    val format: TranslationFormat,
    val imageUrl: String
) {
    private var verses: IntArray? = null

    companion object {
        /**
         * Generates a new frame from JSON
         */
        @Throws(JSONException::class)
        fun generate(chapterId: String, json: JSONObject?): Frame? {
            if (json == null) return null

            val format = if (json.has("format")) {
                TranslationFormat.get(json.getString("format"))
            } else {
                TranslationFormat.UNKNOWN
            }

            val img = if (json.has("img")) json.getString("img") else ""

            val complexId = json.getString("id").split("-")
            val frameId = if (complexId.size > 1) complexId[1] else complexId[0]

            return Frame(
                frameId,
                chapterId,
                json.getString("text"),
                format,
                img
            )
        }

        /**
         * Parses the text for the verse title e.g. 1-5
         */
        fun parseVerseTitle(text: String, format: TranslationFormat): String {
            val verses = getVerseRange(text, format)
            return when (verses.size) {
                1 -> "${verses[0]}"
                2 -> "${verses[0]}-${verses[1]}"
                else -> ""
            }
        }

        /**
         * Returns the formatted beginning verse in this frame.
         */
        fun getStartVerse(text: String, format: TranslationFormat): String {
            val verses = getVerseRange(text, format)
            return if (verses.isNotEmpty()) "${verses[0]}" else ""
        }

        /**
         * Returns the formatted ending verse for this frame.
         */
        fun getEndVerse(text: String, format: TranslationFormat): String {
            val verses = getVerseRange(text, format)
            return when (verses.size) {
                1 -> "${verses[0]}"
                2 -> "${verses[1]}"
                else -> ""
            }
        }

        /**
         * Returns the range of verses that a chunk of text spans
         */
        fun getVerseRange(text: CharSequence, format: TranslationFormat): IntArray {
            return when (format) {
                TranslationFormat.USX -> USXVerseSpan.getVerseRange(text)
                TranslationFormat.USFM -> USFMVerseSpan.getVerseRange(text)
                else -> intArrayOf()
            }
        }
    }

    /** Returns the complex chapter-frame id */
    val title: String
        get() {
            val fallbackId = id.toIntOrNull()?.toString() ?: id
            return if (Clickables.isClickableFormat(format)) {
                val verses = getVerseRange
                when (verses.size) {
                    1 -> "${verses[0]}"
                    2 -> "${verses[0]}-${verses[1]}"
                    else -> fallbackId
                }
            } else {
                fallbackId
            }
        }

    /** Returns the complex chapter-frame id */
    val complexId: String
        get() = "$chapterId-$id"

    /**
     * Returns the range of verses that the body spans
     */
    val getVerseRange: IntArray
        get() {
            if (verses == null) {
                verses = getVerseRange(body)
            }
            return verses!!
        }

    /**
     * Returns the range of verses that a chunk of text spans
     */
    fun getVerseRange(text: CharSequence): IntArray {
        return getVerseRange(text, format)
    }
}