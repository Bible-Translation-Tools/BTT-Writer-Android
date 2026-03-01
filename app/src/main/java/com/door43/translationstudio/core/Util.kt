package com.door43.translationstudio.core

import org.json.JSONArray
import org.json.JSONException
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Date

/**
 * Created by joel on 9/2/2015.
 */
object Util {

    fun readStream(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { it.readText() }
    }

    @Throws(Exception::class)
    fun writeStream(inputStream: InputStream, output: File) {
        output.parentFile?.mkdirs()
        inputStream.use { input ->
            FileOutputStream(output).use { out ->
                input.copyTo(out)
            }
        }
    }

    /**
     * Converts a JSON array to a string array
     */
    @Throws(JSONException::class)
    fun jsonArrayToString(json: JSONArray): Array<String> {
        return Array(json.length()) { i -> json.getString(i) }
    }

    /**
     * Returns the date_modified from a url
     * @return returns 0 if the date could not be parsed
     */
    fun getDateFromUrl(url: String): Int {
        val pieces = url.split("?")
        if (pieces.size > 1) {
            val attribute = pieces[1] // date_modified=123456
            val attrPieces = attribute.split("=")
            if (attrPieces.size > 1) {
                return attrPieces[1].toIntOrNull() ?: 0
            }
        }
        return 0
    }

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
    fun verseToChunk(verse: String, sortedChunks: Array<String>): String {
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
                chunks.sortWith { o1, o2 ->
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