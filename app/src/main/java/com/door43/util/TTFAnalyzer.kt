package com.door43.util

import java.io.FileNotFoundException
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Created by joel on 11/12/2014.
 * http://www.ulduzsoft.com/2012/01/enumerating-the-fonts-on-android-platform/
 */
// The class which loads the TTF file, parses it and returns the TTF font name
class TTFAnalyzer {

    // Font file; must be seekable
    private var file: RandomAccessFile? = null

    // This function parses the TTF file and returns the font name specified in the file
    fun getTtfFontName(fontFilename: String): String? {
        try {
            // Parses the TTF file format.
            // See http://developer.apple.com/fonts/ttrefman/rm06/Chap6.html
            file = RandomAccessFile(fontFilename, "r")

            // Read the version first
            val version = readDword()

            // The version must be either 'true' (0x74727565) or 0x00010000
            if (version != 0x74727565 && version != 0x00010000) {
                return null
            }

            // The TTF file consist of several sections called "tables", and we need to know how many of them are there.
            val numTables = readWord()

            // Skip the rest in the header
            readWord() // skip searchRange
            readWord() // skip entrySelector
            readWord() // skip rangeShift

            // Now we can read the tables
            for (i in 0 until numTables) {
                // Read the table entry
                val tag = readDword()
                readDword() // skip checksum
                val offset = readDword()
                val length = readDword()

                // Now here' the trick. 'name' field actually contains the textual string name.
                // So the 'name' string in characters equals to 0x6E616D65
                if (tag == 0x6E616D65) {
                    // Here's the name section. Read it completely into the allocated buffer
                    val table = ByteArray(length)

                    file!!.seek(offset.toLong())
                    read(table)

                    // This is also a table. See http://developer.apple.com/fonts/ttrefman/rm06/Chap6name.html
                    // According to Table 36, the total number of table records is stored in the second word, at the offset 2.
                    // Getting the count and string offset - remembering it's big endian.
                    val count = getWord(table, 2)
                    val stringOffset = getWord(table, 4)

                    // Record starts from offset 6
                    for (record in 0 until count) {
                        // Table 37 tells us that each record is 6 words -> 12 bytes, and that the nameID is 4th word so its offset is 6.
                        // We also need to account for the first 6 bytes of the header above (Table 36), so...
                        val nameIdOffset = record * 12 + 6
                        val platformID = getWord(table, nameIdOffset)
                        val nameIdValue = getWord(table, nameIdOffset + 6)

                        // Table 42 lists the valid name Identifiers. We're interested in 4 but not in Unicode encoding (for simplicity).
                        // The encoding is stored as PlatformID and we're interested in Mac encoding
                        if (nameIdValue == 4 && platformID == 1) {
                            // We need the string offset and length, which are the word 6 and 5 respectively
                            val nameLength = getWord(table, nameIdOffset + 8)
                            var nameOffset = getWord(table, nameIdOffset + 10)

                            // The real name string offset is calculated by adding the string_offset
                            nameOffset += stringOffset

                            // Make sure it is inside the array
                            if (nameOffset >= 0 && nameOffset + nameLength < table.size) {
                                return String(table, nameOffset, nameLength)
                            }
                        }
                    }
                }
            }

            return null
        } catch (e: FileNotFoundException) {
            // Permissions?
            return null
        } catch (e: IOException) {
            // Most likely a corrupted font file
            return null
        }
    }

    // Helper I/O functions
    @Throws(IOException::class)
    private fun readByte(): Int {
        return file!!.read() and 0xFF
    }

    @Throws(IOException::class)
    private fun readWord(): Int {
        val b1 = readByte()
        val b2 = readByte()

        return (b1 shl 8) or b2
    }

    @Throws(IOException::class)
    private fun readDword(): Int {
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        val b4 = readByte()

        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    @Throws(IOException::class)
    private fun read(array: ByteArray) {
        if (file!!.read(array) != array.size) {
            throw IOException()
        }
    }

    // Helper
    private fun getWord(array: ByteArray, offset: Int): Int {
        val b1 = array[offset].toInt() and 0xFF
        val b2 = array[offset + 1].toInt() and 0xFF

        return (b1 shl 8) or b2
    }
}