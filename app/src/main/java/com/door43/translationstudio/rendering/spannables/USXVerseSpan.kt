package com.door43.translationstudio.rendering.spannables

import java.util.regex.Pattern

open class USXVerseSpan : VerseSpan {

    private var _startVerseNumber: Int = 0
    final override val startVerseNumber: Int
        get() = _startVerseNumber

    private var _endVerseNumber: Int = 0
    final override val endVerseNumber: Int
        get() = _endVerseNumber

    companion object {
        const val PATTERN = "<verse\\s+number=\"(\\d+(-\\d+)?)\"\\s+style=\"v\"\\s*/>"

        /**
         * Parses a usx string into a verse span
         * @param usx the USX string
         * @return the parsed USXVerseSpan or null
         */
        fun parseVerse(usx: String): USXVerseSpan? {
            val pattern = Pattern.compile(PATTERN)
            val matcher = pattern.matcher(usx)
            while (matcher.find()) {
                val group = matcher.group(1)
                if (group != null) {
                    return USXVerseSpan(group)
                }
            }
            return null
        }

        /**
         * Returns the range of verses that a chunk of text spans
         *
         * @param text the text to search
         * @return IntArray of size 0 if no verses, size 1 if one verse, size 2 if a range of verses
         */
        fun getVerseRange(text: String): IntArray {
            // locate verse range
            val pattern = Pattern.compile(PATTERN)
            val matcher = pattern.matcher(text)
            var numVerses = 0
            var startVerse = 0
            var endVerse = 0
            var verse: USXVerseSpan? = null

            while (matcher.find()) {
                val group = matcher.group(1) ?: continue
                verse = USXVerseSpan(group)

                if (numVerses == 0) {
                    // first verse
                    startVerse = verse.startVerseNumber
                    endVerse = verse.endVerseNumber
                }
                numVerses++
            }

            if (verse != null) {
                endVerse = if (verse.endVerseNumber > 0) {
                    verse.endVerseNumber
                } else {
                    verse.startVerseNumber
                }
            }

            return when {
                startVerse <= 0 || endVerse <= 0 -> IntArray(0) // no verse range
                startVerse == endVerse -> intArrayOf(startVerse) // single verse
                else -> intArrayOf(startVerse, endVerse) // verse range
            }
        }
    }

    /**
     * Creates a new verse span of either a single verse or range of verses
     * @param verse the verse string
     */
    constructor(verse: String) : super(verse, "<verse number=\"$verse\" style=\"v\" />") {
        val verses = verse.split("-")
        if (verses.size == 2) {
            // range of verses
            _startVerseNumber = verses[0].toIntOrNull() ?: 0
            _endVerseNumber = verses[1].toIntOrNull() ?: 0
        } else {
            // single verse
            _startVerseNumber = verse.toIntOrNull() ?: 0
        }
    }

    /**
     * Creates a new verse span
     * @param verse the verse number
     */
    constructor(verse: Int) : super(verse.toString(), "<verse number=\"$verse\" style=\"v\" />") {
        _startVerseNumber = verse
    }

    /**
     * Creates a verse span over a range of verses
     * @param startVerse the starting verse
     * @param endVerse the ending verse
     */
    constructor(startVerse: Int, endVerse: Int) : super("$startVerse-$endVerse", "<verse number=\"$startVerse-$endVerse\" style=\"v\" />") {
        _startVerseNumber = startVerse
        _endVerseNumber = endVerse
    }
}
