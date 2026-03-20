package com.door43.translationstudio.ui.translate.review

import com.door43.translationstudio.core.TranslationFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class VerseMarkerDragTest {

    // --- skipMarkupAt tests ---

    @Test
    fun `skipMarkupAt detects USFM verse marker`() {
        val text = "\\v 1 hello"
        assertEquals(5, VerseMarkerDrag.skipMarkupAt(text, 0, TranslationFormat.USFM))
    }

    @Test
    fun `skipMarkupAt detects USFM verse range marker`() {
        val text = "\\v 1-3 hello"
        assertEquals(7, VerseMarkerDrag.skipMarkupAt(text, 0, TranslationFormat.USFM))
    }

    @Test
    fun `skipMarkupAt detects USFM verse marker mid-text`() {
        val text = "hello \\v 2 world"
        assertEquals(5, VerseMarkerDrag.skipMarkupAt(text, 6, TranslationFormat.USFM))
    }

    @Test
    fun `skipMarkupAt detects USFM footnote`() {
        val text = "hello \\f + \\ft note\\f* world"
        assertEquals(16, VerseMarkerDrag.skipMarkupAt(text, 6, TranslationFormat.USFM))
    }

    @Test
    fun `skipMarkupAt detects USFM paragraph marker`() {
        val text = "\\p hello"
        assertEquals(3, VerseMarkerDrag.skipMarkupAt(text, 0, TranslationFormat.USFM))
    }

    @Test
    fun `skipMarkupAt detects USFM poetic line marker`() {
        val text = "\\q1 hello"
        assertEquals(4, VerseMarkerDrag.skipMarkupAt(text, 0, TranslationFormat.USFM))
    }

    @Test
    fun `skipMarkupAt returns 0 for plain text`() {
        val text = "hello world"
        assertEquals(0, VerseMarkerDrag.skipMarkupAt(text, 0, TranslationFormat.USFM))
    }

    @Test
    fun `skipMarkupAt detects USX verse marker`() {
        val text = """<verse number="1" style="v" />hello"""
        assertEquals(30, VerseMarkerDrag.skipMarkupAt(text, 0, TranslationFormat.USX))
    }

    @Test
    fun `skipMarkupAt detects USX footnote`() {
        val text = """<note style="f" caller="+"><char style="ft">note</char></note>world"""
        assertEquals(62, VerseMarkerDrag.skipMarkupAt(text, 0, TranslationFormat.USX))
    }

    @Test
    fun `skipMarkupAt detects USX para open`() {
        val text = """<para style="p">hello"""
        assertEquals(16, VerseMarkerDrag.skipMarkupAt(text, 0, TranslationFormat.USX))
    }

    @Test
    fun `skipMarkupAt detects USX para close`() {
        val text = """</para>hello"""
        assertEquals(7, VerseMarkerDrag.skipMarkupAt(text, 0, TranslationFormat.USX))
    }

    // --- closestWordBoundary tests ---

    @Test
    fun `closestWordBoundary snaps to start of word`() {
        val text = "hello world"
        assertEquals(6, VerseMarkerDrag.closestWordBoundary(text, 8))
    }

    @Test
    fun `closestWordBoundary at start returns 0`() {
        assertEquals(0, VerseMarkerDrag.closestWordBoundary("hello", 0))
    }

    @Test
    fun `closestWordBoundary in whitespace moves to next word`() {
        val text = "hello   world"
        assertEquals(8, VerseMarkerDrag.closestWordBoundary(text, 6))
    }

    @Test
    fun `closestWordBoundary snaps to USFFC char`() {
        val text = "hello \u2800world"
        // On \u2800 — return its position (it's a token)
        assertEquals(6, VerseMarkerDrag.closestWordBoundary(text, 6))
        // On 'w' of "world" — walk back to word start (stops at \u2800)
        assertEquals(7, VerseMarkerDrag.closestWordBoundary(text, 7))
    }

    @Test
    fun `closestWordBoundary at end returns text length`() {
        val text = "hello"
        assertEquals(5, VerseMarkerDrag.closestWordBoundary(text, 10))
    }

    // --- countWordStartsBefore tests ---

    @Test
    fun `countWordStartsBefore counts words and USFFC tokens`() {
        val text = "hello \u2800 world"
        // Tokens before offset 8: "hello"=1, \u2800=2
        assertEquals(2, VerseMarkerDrag.countWordStartsBefore(text, 8))
    }

    @Test
    fun `countWordStartsBefore at start returns 0`() {
        assertEquals(0, VerseMarkerDrag.countWordStartsBefore("hello world", 0))
    }

    @Test
    fun `countWordStartsBefore counts multiple words`() {
        val text = "one two three"
        assertEquals(2, VerseMarkerDrag.countWordStartsBefore(text, 8))
    }

    // --- findNthWordStartInUsfm tests ---

    @Test
    fun `findNthWordStartInUsfm counts verse markers as tokens`() {
        val usfm = "\\v 1 hello \\v 2 world"
        // Token 0 = "\v 1 " at 0, token 1 = "hello" at 5, token 2 = "\v 2 " at 11, token 3 = "world" at 16
        assertEquals(0, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 0, TranslationFormat.USFM))
        assertEquals(5, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 1, TranslationFormat.USFM))
        assertEquals(11, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 2, TranslationFormat.USFM))
        assertEquals(16, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 3, TranslationFormat.USFM))
    }

    @Test
    fun `findNthWordStartInUsfm counts footnotes as tokens`() {
        val usfm = "hello \\f + \\ft note\\f* world"
        // Token 0 = "hello" at 0, token 1 = footnote at 6, token 2 = "world" at 23
        assertEquals(0, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 0, TranslationFormat.USFM))
        assertEquals(6, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 1, TranslationFormat.USFM))
        assertEquals(23, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 2, TranslationFormat.USFM))
    }

    @Test
    fun `findNthWordStartInUsfm counts paragraph markers as tokens`() {
        val usfm = "\\p hello"
        // Token 0 = "\p " at 0, token 1 = "hello" at 3
        assertEquals(0, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 0, TranslationFormat.USFM))
        assertEquals(3, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 1, TranslationFormat.USFM))
    }

    @Test
    fun `findNthWordStartInUsfm beyond end returns length`() {
        val usfm = "\\v 1 hello"
        assertEquals(10, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 5, TranslationFormat.USFM))
    }

    @Test
    fun `findNthWordStartInUsfm with USX format counts markup as tokens`() {
        val usfm = """<verse number="1" style="v" />hello <verse number="2" style="v" />world"""
        // Token 0 = verse tag at 0, token 1 = "hello" at 30, token 2 = verse tag at 36, token 3 = "world" at 66
        assertEquals(0, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 0, TranslationFormat.USX))
        assertEquals(30, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 1, TranslationFormat.USX))
        assertEquals(36, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 2, TranslationFormat.USX))
        assertEquals(66, VerseMarkerDrag.findNthWordStartInUsfm(usfm, 3, TranslationFormat.USX))
    }

    // --- mapByCharRatio tests ---

    @Test
    fun `mapByCharRatio maps midpoint correctly`() {
        val rendered = "\u2800\u4F60\u597D\u4E16\u754C"  // [pin]你好世界
        val usfm = "\\v 1 \u4F60\u597D\u4E16\u754C"      // \v 1 你好世界
        // Tap at offset 3 (after pin + 你好): 2 visible chars / 4 total = 0.5 ratio
        val result = VerseMarkerDrag.mapByCharRatio(rendered, 3, usfm, TranslationFormat.USFM)
        // USFM visible chars = 4 (你好世界), 0.5 * 4 = 2 visible chars
        // Position after 你好 in USFM: offset 5 + 2 = 7
        assertEquals(7, result)
    }

    @Test
    fun `mapByCharRatio at start returns first non-markup position`() {
        val rendered = "\u2800 text"
        val usfm = "\\v 1 text"
        assertEquals(5, VerseMarkerDrag.mapByCharRatio(rendered, 0, usfm, TranslationFormat.USFM))
    }

    @Test
    fun `mapByCharRatio at end returns usfm length`() {
        val rendered = "\u2800 text"
        val usfm = "\\v 1 text"
        assertEquals(9, VerseMarkerDrag.mapByCharRatio(rendered, 6, usfm, TranslationFormat.USFM))
    }

    // --- moveVerseByRawPosition tests ---

    @Test
    fun `moveVerseByRawPosition moves verse forward`() {
        val text = "\\v 1 hello \\v 2 world"
        // \v 1 at raw 0..5, \v 2 at 11..16, "world" starts at 16
        val result = VerseMarkerDrag.moveVerseByRawPosition(
            text = text,
            verseRawStart = 0,
            verseRawEnd = 5,
            marker = "\\v 1 ",
            targetRawPosition = 16  // rawStart of "world" text node
        )
        // After removing \v 1 (5 chars), target 16 adjusts to 11
        assertEquals("hello \\v 2 \\v 1 world", result)
    }

    @Test
    fun `moveVerseByRawPosition moves verse backward`() {
        val text = "hello \\v 1 world"
        // \v 1 at raw 6..11
        val result = VerseMarkerDrag.moveVerseByRawPosition(
            text = text,
            verseRawStart = 6,
            verseRawEnd = 11,
            marker = "\\v 1 ",
            targetRawPosition = 0  // rawStart of "hello" text node
        )
        assertEquals("\\v 1 hello world", result)
    }

    @Test
    fun `moveVerseByRawPosition before footnote`() {
        val text = "\\v 1 hello \\f + \\ft note\\f* world"
        // \v 1 at 0..5, footnote at 11..27
        val result = VerseMarkerDrag.moveVerseByRawPosition(
            text = text,
            verseRawStart = 0,
            verseRawEnd = 5,
            marker = "\\v 1 ",
            targetRawPosition = 11  // rawStart of footnote entity
        )
        // After removing \v 1 (5 chars), target 11 adjusts to 6
        assertEquals("hello \\v 1 \\f + \\ft note\\f* world", result)
    }

    @Test
    fun `moveVerseByRawPosition to end`() {
        val text = "\\v 1 hello world"
        val result = VerseMarkerDrag.moveVerseByRawPosition(
            text = text,
            verseRawStart = 0,
            verseRawEnd = 5,
            marker = "\\v 1 ",
            targetRawPosition = 16  // past end
        )
        assertEquals("hello world\\v 1 ", result)
    }

    @Test
    fun `moveVerseByRawPosition target inside verse range clamps to verse start`() {
        val text = "\\v 1 hello world"
        // Target inside the verse marker range itself
        val result = VerseMarkerDrag.moveVerseByRawPosition(
            text = text,
            verseRawStart = 0,
            verseRawEnd = 5,
            marker = "\\v 1 ",
            targetRawPosition = 3
        )
        // Clamped to verseRawStart (0), effectively keeps it in place
        assertEquals("\\v 1 hello world", result)
    }

    @Test
    fun `moveVerseByRawPosition before another verse marker`() {
        val text = "\\v 1 hello \\v 9 \\v 10 world"
        // Drop \v 1 before \v 10 — target = rawStart of \v 10 = 16
        val result = VerseMarkerDrag.moveVerseByRawPosition(
            text = text,
            verseRawStart = 0,
            verseRawEnd = 5,
            marker = "\\v 1 ",
            targetRawPosition = 16  // rawStart of \v 10 entity
        )
        // After removing \v 1 (5 chars), target 16 adjusts to 11
        assertEquals("hello \\v 9 \\v 1 \\v 10 world", result)
    }
}
