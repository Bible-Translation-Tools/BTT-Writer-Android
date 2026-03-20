package com.door43.translationstudio.ui.translate.components

import com.door43.translationstudio.ui.translate.components.footnote.NOTE_CHAR
import com.door43.translationstudio.ui.translate.components.footnote.findFootnoteBlocks
import com.door43.translationstudio.ui.translate.components.footnote.replaceFootnotesForDisplay
import com.door43.translationstudio.ui.translate.components.footnote.visualToRaw
import org.junit.Assert.*
import org.junit.Test

class FootnoteUtilsTest {

    @Test
    fun `findFootnoteBlocks returns empty for plain text`() {
        val blocks = findFootnoteBlocks("hello world")
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `findFootnoteBlocks finds single footnote`() {
        val text = "hello \\f + \\ft note \\f* world"
        val blocks = findFootnoteBlocks(text)
        assertEquals("\\f + \\ft note \\f*", blocks[0].value)
        // "\f + \ft note \f*" is 17 chars starting at index 6 → range 6..22
        assertEquals(6..22, blocks[0].range)
    }

    @Test
    fun `findFootnoteBlocks finds multiple footnotes`() {
        val text = "\\f + \\ft first \\f* middle \\f + \\ft second \\f*"
        val blocks = findFootnoteBlocks(text)
        assertEquals(2, blocks.size)
        assertEquals("\\f + \\ft first \\f*", blocks[0].value)
        assertEquals("\\f + \\ft second \\f*", blocks[1].value)
    }

    @Test
    fun `findFootnoteBlocks finds adjacent footnotes`() {
        val text = "\\f + \\ft one \\f*\\f + \\ft two \\f*"
        val blocks = findFootnoteBlocks(text)
        assertEquals(2, blocks.size)
    }

    @Test
    fun `findFootnoteBlocks handles empty footnote`() {
        val text = "text \\f + \\f* more"
        val blocks = findFootnoteBlocks(text)
        assertEquals(0, blocks.size)
    }

    @Test
    fun `findFootnoteBlocks handles footnote with complex content`() {
        val text = "text \\f + \\ft note \\fv 1 \\fv* more \\f* end"
        val blocks = findFootnoteBlocks(text)
        assertEquals(1, blocks.size)
        assertEquals("\\f + \\ft note \\fv 1 \\fv* more \\f*", blocks[0].value)
    }

    @Test
    fun `NOTE_CHAR constant is Braille Pattern Blank`() {
        assertEquals('\u2800', NOTE_CHAR)
    }

    @Test
    fun `visualToRaw passes through text without NOTE_CHAR`() {
        assertEquals("hello world", visualToRaw("hello world", "hello world"))
    }

    @Test
    fun `visualToRaw reconstructs single footnote`() {
        val raw = "hello \\f + \\ft note \\f* world"
        val visual = "hello ${NOTE_CHAR} world"
        assertEquals(raw, visualToRaw(visual, raw))
    }

    @Test
    fun `visualToRaw reconstructs multiple footnotes`() {
        val raw = "a \\f + \\ft one \\f* b \\f + \\ft two \\f* c"
        val visual = "a ${NOTE_CHAR} b ${NOTE_CHAR} c"
        assertEquals(raw, visualToRaw(visual, raw))
    }

    @Test
    fun `visualToRaw reconstructs partial copy with footnote`() {
        val raw = "start \\f + \\ft one \\f* middle \\f + \\ft two \\f* end"
        val fullVisual = replaceFootnotesForDisplay(raw).displayText
        // Copy just the second half: "middle ⠀ end"
        val partialVisual = fullVisual.substring(fullVisual.indexOf("middle"))
        val expected = "middle \\f + \\ft two \\f* end"
        assertEquals(expected, visualToRaw(partialVisual, raw))
    }
}
