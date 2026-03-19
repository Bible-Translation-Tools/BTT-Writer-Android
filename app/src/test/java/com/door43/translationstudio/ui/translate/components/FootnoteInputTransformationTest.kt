package com.door43.translationstudio.ui.translate.components

import com.door43.translationstudio.ui.translate.components.footnote.enforceFootnoteAtomicity
import org.junit.Assert.*
import org.junit.Test

class FootnoteInputTransformationTest {

    @Test
    fun `plain text passes through`() {
        val result = enforceFootnoteAtomicity("hello beautiful world")
        assertEquals("hello beautiful world", result)
    }

    @Test
    fun `well-formed footnote preserved`() {
        val text = "hello \\f + \\ft note \\f* world"
        val result = enforceFootnoteAtomicity(text)
        assertEquals(text, result)
    }

    @Test
    fun `orphaned opening marker removed`() {
        // \f + \ft note without closing \f*
        val text = "hello \\f + \\ft note world"
        val result = enforceFootnoteAtomicity(text)
        assertEquals("hello world", result)
    }

    @Test
    fun `orphaned closing marker removed`() {
        // \f* without opening \f
        val text = "hello \\f* world"
        val result = enforceFootnoteAtomicity(text)
        assertEquals("hello  world", result)
    }

    @Test
    fun `complete footnote deletion is clean`() {
        val text = "hello  world"
        val result = enforceFootnoteAtomicity(text)
        assertEquals("hello  world", result)
    }

    @Test
    fun `text with no footnote markers unchanged`() {
        val text = "hello world"
        val result = enforceFootnoteAtomicity(text)
        assertEquals(text, result)
    }

    @Test
    fun `multiple footnotes — all well-formed`() {
        val text = "a \\f + \\ft one \\f* b \\f + \\ft two \\f*"
        val result = enforceFootnoteAtomicity(text)
        assertEquals(text, result)
    }

    @Test
    fun `multiple footnotes — one orphaned`() {
        // First footnote well-formed, second missing closing
        val text = "a \\f + \\ft one \\f* b \\f + \\ft two"
        val result = enforceFootnoteAtomicity(text)
        assertEquals("a \\f + \\ft one \\f* b ", result)
    }

    @Test
    fun `pasted text with valid footnote preserved`() {
        val text = "hello \\f + \\ft pasted \\f* world"
        val result = enforceFootnoteAtomicity(text)
        assertEquals(text, result)
    }

    @Test
    fun `orphaned marker between valid footnotes removed`() {
        val text = "\\f + \\ft one \\f* orphan \\f + stuff \\f + \\ft two \\f*"
        val result = enforceFootnoteAtomicity(text)
        assertEquals("\\f + \\ft one \\f* orphan \\f + \\ft two \\f*", result)
    }

    @Test
    fun `stray backslash-f-star after valid footnote removed`() {
        val text = "\\f + \\ft note \\f* extra \\f*"
        val result = enforceFootnoteAtomicity(text)
        assertEquals("\\f + \\ft note \\f* extra ", result)
    }
}
