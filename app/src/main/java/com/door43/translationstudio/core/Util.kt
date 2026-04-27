package com.door43.translationstudio.core

import org.bibletranslationtools.resourcecontainer.ResourceContainer
import java.util.Date

/**
 * Created by joel on 9/2/2015.
 */
object Util {

    /**
     * Returns a unix timestamp
     */
    val unixTime: Long
        get() = System.currentTimeMillis() / 1000L

    /**
     * Converts a unix time value to a date object
     */
    fun dateFromUnixTime(unix: Long): Date = Date(unix * 1000L)

    /**
     * do string to integer with default value on conversion error
     */
    fun strToInt(value: String?, defaultValue: Int): Int {
        return value?.toIntOrNull() ?: defaultValue
    }

    /**
     * Converts a verse id to a chunk id.
     * If an error occurs the verse will be returned
     */
    fun verseToChunk(verse: String, sortedChunks: List<String>): String {
        var match = verse
        val verseInt = verse.toIntOrNull()

        for (chunk in sortedChunks) {
            val chunkInt = chunk.toIntOrNull()
            if (chunkInt != null && verseInt != null) {
                if (chunkInt > verseInt) {
                    break
                }
                match = chunk
            } else {
                // TRICKY: some chunks are not numbers
                if (chunk == verse) {
                    match = chunk
                    break
                }
            }
        }
        return match
    }

    /**
     * Maps a verse to a chunk.
     */
    fun mapVerseToChunk(rc: ResourceContainer, chapter: String, verse: String): String {
        return try {
            val chunks = rc.chunks(chapter)
            if (chunks != null) {
                chunks.sortedWith { o1, o2 ->
                    val i1 = o1.toIntOrNull()
                    val i2 = o2.toIntOrNull()

                    // TRICKY: push strings to top
                    when {
                        i1 == null -> 1
                        i2 == null -> 1
                        else -> i1.compareTo(i2)
                    }
                }
                verseToChunk(verse, chunks)
            } else {
                verse
            }
        } catch (e: Exception) {
            verse
        }
    }
}