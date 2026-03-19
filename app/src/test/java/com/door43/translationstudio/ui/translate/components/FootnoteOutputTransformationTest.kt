package com.door43.translationstudio.ui.translate.components

import com.door43.translationstudio.ui.translate.components.footnote.replaceFootnotesForDisplay
import org.junit.Assert.*
import org.junit.Test

class FootnoteOutputTransformationTest {

    @Test
    fun `plain text unchanged`() {
        val result = replaceFootnotesForDisplay("hello world")
        assertEquals("hello world", result.displayText)
        assertTrue(result.footnoteRanges.isEmpty())
    }

    @Test
    fun `single footnote replaced with OBJ_CHAR`() {
        val result = replaceFootnotesForDisplay("hello \\f + \\ft note \\f* world")
        assertEquals("hello \u2800 world", result.displayText)
        assertEquals(1, result.footnoteRanges.size)
        // "\f + \ft note \f*" is 17 chars starting at index 6 → range 6..22
        assertEquals(6..22, result.footnoteRanges[0])
    }

    @Test
    fun `multiple footnotes replaced`() {
        val result = replaceFootnotesForDisplay("a \\f + \\ft one \\f* b \\f + \\ft two \\f* c")
        assertEquals("a \u2800 b \u2800 c", result.displayText)
        assertEquals(2, result.footnoteRanges.size)
    }

    @Test
    fun `adjacent footnotes produce adjacent OBJ_CHARs`() {
        val result = replaceFootnotesForDisplay("\\f + \\ft one \\f*\\f + \\ft two \\f*")
        assertEquals("\u2800\u2800", result.displayText)
        assertEquals(2, result.footnoteRanges.size)
    }

    @Test
    fun `verse markers preserved`() {
        val result = replaceFootnotesForDisplay("\\v 1 text \\f + \\ft note \\f* more")
        assertEquals("\\v 1 text \u2800 more", result.displayText)
    }

    @Test
    fun `footnote at start of text`() {
        val result = replaceFootnotesForDisplay("\\f + \\ft note \\f* text")
        assertEquals("\u2800 text", result.displayText)
    }

    @Test
    fun `footnote at end of text`() {
        val result = replaceFootnotesForDisplay("text \\f + \\ft note \\f*")
        assertEquals("text \u2800", result.displayText)
    }

    @Test
    fun `footnote ranges map back to original positions`() {
        val raw = "ab \\f + \\ft note \\f* cd"
        val result = replaceFootnotesForDisplay(raw)
        assertEquals(3, result.footnoteRanges[0].first)
        assertEquals(raw.indexOf("\\f*") + 3 - 1, result.footnoteRanges[0].last)
    }
}
