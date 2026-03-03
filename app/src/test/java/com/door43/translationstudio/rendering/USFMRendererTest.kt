package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.TextNode
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for USFMRenderer.renderToNodes().
 *
 * These run on the JVM (not on device) because USFMRenderer itself has no Android imports
 * beyond android.content.Context (used only by legacy constructors).
 *
 * USFM input uses the same <para style="..."> XML wrapper as USX for most tags,
 * plus USFM-specific markers such as \v, \c, and \p.
 */
class USFMRendererTest {

    private fun renderer() = USFMRenderer()

    // -------------------------------------------------------------------------
    // Plain text
    // -------------------------------------------------------------------------

    @Test
    fun `plain text passes through as Text node`() {
        val nodes = renderer().renderToNodes("Hello world")
        val text = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertTrue(text.contains("Hello world"))
    }

    @Test
    fun `empty input returns empty list`() {
        val nodes = renderer().renderToNodes("")
        assertTrue(
            nodes.isEmpty() || nodes.all { it is TextNode.Text && (it as TextNode.Text).content.isBlank() }
        )
    }

    @Test
    fun `whitespace-only input is trimmed to empty`() {
        val nodes = renderer().renderToNodes("   \n   ")
        val hasNonBlankText = nodes.any { it is TextNode.Text && (it as TextNode.Text).content.isNotBlank() }
        assertFalse(hasNonBlankText)
    }

    // -------------------------------------------------------------------------
    // Section headings
    // -------------------------------------------------------------------------

    @Test
    fun `section heading produces SectionHeading node`() {
        val input = """<para style="s">The Beginning</para>"""
        val nodes = renderer().renderToNodes(input)
        val heading = nodes.filterIsInstance<TextNode.SectionHeading>().firstOrNull()
        assertNotNull("Expected SectionHeading node", heading)
        assertEquals("The Beginning", heading!!.text)
        assertFalse(heading.isMajor)
    }

    @Test
    fun `major section heading produces isMajor=true SectionHeading`() {
        val input = """<para style="ms">CREATION</para>"""
        val nodes = renderer().renderToNodes(input)
        val heading = nodes.filterIsInstance<TextNode.SectionHeading>().firstOrNull()
        assertNotNull(heading)
        assertTrue(heading!!.isMajor)
        assertEquals("CREATION", heading.text)
    }

    @Test
    fun `suppressed leading major section heading is excluded`() {
        val input = """<para style="ms">INTRO</para> rest of text"""
        val r = renderer()
        r.setSuppressLeadingMajorSectionHeadings(true)
        val nodes = r.renderToNodes(input)
        assertFalse(
            "Suppressed heading should not appear",
            nodes.any { it is TextNode.SectionHeading && (it as TextNode.SectionHeading).isMajor }
        )
    }

    @Test
    fun `non-leading major section heading is NOT suppressed`() {
        val input = """\v 1 text <para style="ms">MID SECTION</para>"""
        val r = renderer()
        r.setSuppressLeadingMajorSectionHeadings(true)
        val nodes = r.renderToNodes(input)
        assertTrue(
            "Non-leading major heading should still appear",
            nodes.any { it is TextNode.SectionHeading && (it as TextNode.SectionHeading).isMajor }
        )
    }

    @Test
    fun `section heading is followed by LineBreak`() {
        val input = """<para style="s">Heading</para>"""
        val nodes = renderer().renderToNodes(input)
        val headingIndex = nodes.indexOfFirst { it is TextNode.SectionHeading }
        assertTrue(headingIndex >= 0)
        assertTrue(
            "SectionHeading should be followed by LineBreak",
            nodes.getOrNull(headingIndex + 1) == TextNode.LineBreak
        )
    }

    // -------------------------------------------------------------------------
    // Verses (USFM uses \v N format)
    // -------------------------------------------------------------------------

    @Test
    fun `verse tag produces VerseMarker node`() {
        val input = """\v 1 In the beginning"""
        val nodes = renderer().renderToNodes(input)
        val verse = nodes.filterIsInstance<TextNode.VerseMarker>().firstOrNull()
        assertNotNull("Expected VerseMarker", verse)
        assertEquals(1, verse!!.startVerse)
        assertEquals(0, verse.endVerse)
    }

    @Test
    fun `verse range produces VerseMarker with endVerse`() {
        val input = """\v 3-5 joined verses"""
        val nodes = renderer().renderToNodes(input)
        val verse = nodes.filterIsInstance<TextNode.VerseMarker>().firstOrNull()
        assertNotNull(verse)
        assertEquals(3, verse!!.startVerse)
        assertEquals(5, verse.endVerse)
    }

    @Test
    fun `duplicate verse is excluded`() {
        val input = """\v 1 text \v 1 dup"""
        val nodes = renderer().renderToNodes(input)
        assertEquals(
            "Only one verse 1 expected",
            1,
            nodes.filterIsInstance<TextNode.VerseMarker>().count { it.startVerse == 1 }
        )
    }

    @Test
    fun `verses disabled produces no VerseMarker nodes`() {
        val input = """\v 1 text"""
        val r = renderer()
        r.setVersesEnabled(false)
        val nodes = r.renderToNodes(input)
        assertFalse(nodes.any { it is TextNode.VerseMarker })
    }

    @Test
    fun `expected verse range filters out-of-range verses`() {
        val input = """\v 3 text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1, 2)) // only verses 1-2 expected
        val nodes = r.renderToNodes(input)
        assertFalse(
            "Verse 3 should be filtered out",
            nodes.filterIsInstance<TextNode.VerseMarker>().any { it.startVerse == 3 }
        )
    }

    @Test
    fun `missing expected verse is inserted`() {
        val input = """\v 2 text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1)) // expect verse 1, which is not present
        val nodes = r.renderToNodes(input)
        assertTrue(
            "Missing verse 1 should be inserted",
            nodes.filterIsInstance<TextNode.VerseMarker>().any { it.startVerse == 1 }
        )
        assertTrue(r.isAddedMissingVerse)
    }

    @Test
    fun `missing verse is prepended before existing content`() {
        val input = """\v 2 text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1))
        val nodes = r.renderToNodes(input)
        val firstVerse = nodes.filterIsInstance<TextNode.VerseMarker>().first()
        assertEquals("Missing verse should be first", 1, firstVerse.startVerse)
    }

    @Test
    fun `verse range missing all verses inserts them all`() {
        val input = "no verses here"
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1, 3))
        val nodes = r.renderToNodes(input)
        val verses = nodes.filterIsInstance<TextNode.VerseMarker>().map { it.startVerse }.toSet()
        assertTrue(verses.containsAll(listOf(1, 2, 3)))
        assertTrue(r.isAddedMissingVerse)
    }

    // -------------------------------------------------------------------------
    // Paragraph / blank line
    // -------------------------------------------------------------------------

    @Test
    fun `blank line tag produces BlankLine node`() {
        val input = """text<para style="b"/>more"""
        val nodes = renderer().renderToNodes(input)
        assertTrue(nodes.any { it is TextNode.BlankLine })
    }

    @Test
    fun `paragraph tag produces Paragraph node`() {
        val input = """<para style="p">paragraph content</para>"""
        val nodes = renderer().renderToNodes(input)
        assertTrue("Expected Paragraph node", nodes.any { it is TextNode.Paragraph })
    }

    @Test
    fun `paragraph content is preserved as Text node`() {
        val input = """<para style="p">paragraph content</para>"""
        val nodes = renderer().renderToNodes(input)
        val allText = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertTrue("Paragraph content should be in Text nodes", allText.contains("paragraph content"))
    }

    @Test
    fun `paragraph node from para tag has indented=true`() {
        val input = """<para style="p">some text</para>"""
        val nodes = renderer().renderToNodes(input)
        val para = nodes.filterIsInstance<TextNode.Paragraph>().firstOrNull()
        assertNotNull(para)
        assertTrue(para!!.indented)
    }

    // -------------------------------------------------------------------------
    // Poetic lines
    // -------------------------------------------------------------------------

    @Test
    fun `poetic line produces PoeticLine node with correct indent`() {
        val input = """<para style="q2">Praise the Lord</para>"""
        val nodes = renderer().renderToNodes(input)
        val poetic = nodes.filterIsInstance<TextNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertEquals(2, poetic!!.indentLevel)
        assertFalse(poetic.rightAligned)
    }

    @Test
    fun `q1 poetic line has indentLevel 1`() {
        val input = """<para style="q1">first indent</para>"""
        val nodes = renderer().renderToNodes(input)
        val poetic = nodes.filterIsInstance<TextNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertEquals(1, poetic!!.indentLevel)
    }

    @Test
    fun `right-aligned poetic line produces rightAligned=true PoeticLine`() {
        val input = """<para style="qr">Selah</para>"""
        val nodes = renderer().renderToNodes(input)
        val poetic = nodes.filterIsInstance<TextNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertTrue(poetic!!.rightAligned)
    }

    @Test
    fun `right-aligned poetic line is preceded by LineBreak`() {
        val input = """<para style="qr">Selah</para>"""
        val nodes = renderer().renderToNodes(input)
        val poeticIdx = nodes.indexOfFirst { it is TextNode.PoeticLine }
        assertTrue(poeticIdx > 0)
        assertEquals(TextNode.LineBreak, nodes[poeticIdx - 1])
    }

    // -------------------------------------------------------------------------
    // Chapter labels
    // -------------------------------------------------------------------------

    @Test
    fun `chapter label produces ChapterLabel node`() {
        val input = """<para style="cl">Chapter One</para>"""
        val nodes = renderer().renderToNodes(input)
        val label = nodes.filterIsInstance<TextNode.ChapterLabel>().firstOrNull()
        assertNotNull(label)
        assertEquals("Chapter One", label!!.text)
    }

    // -------------------------------------------------------------------------
    // Search highlights
    // -------------------------------------------------------------------------

    @Test
    fun `search string produces SearchHighlight nodes`() {
        val input = """\v 1 In the beginning God created"""
        val r = renderer()
        r.setSearchString("beginning", 0xFF0000)
        val nodes = r.renderToNodes(input)
        val highlights = nodes.filterIsInstance<TextNode.SearchHighlight>()
        assertTrue("Expected SearchHighlight node", highlights.isNotEmpty())
        assertEquals("beginning", highlights.first().content)
    }

    @Test
    fun `search highlight preserves surrounding text`() {
        val input = "In the beginning God"
        val r = renderer()
        r.setSearchString("beginning", 0xFF0000)
        val nodes = r.renderToNodes(input)
        val allText = nodes.joinToString("") {
            when (it) {
                is TextNode.Text -> it.content
                is TextNode.SearchHighlight -> it.content
                else -> ""
            }
        }
        assertEquals("In the beginning God", allText)
    }

    @Test
    fun `search is case-insensitive`() {
        val input = "In the Beginning God"
        val r = renderer()
        r.setSearchString("beginning", 0xFF0000)
        val nodes = r.renderToNodes(input)
        assertTrue(nodes.any { it is TextNode.SearchHighlight })
    }

    @Test
    fun `search highlight content matches original case`() {
        val input = "In the Beginning God"
        val r = renderer()
        r.setSearchString("beginning", 0xFF0000)
        val nodes = r.renderToNodes(input)
        val highlight = nodes.filterIsInstance<TextNode.SearchHighlight>().firstOrNull()
        assertNotNull(highlight)
        assertEquals("Beginning", highlight!!.content)
    }

    @Test
    fun `empty search string disables highlighting`() {
        val input = "In the beginning"
        val r = renderer()
        r.setSearchString("", 0xFF0000)
        val nodes = r.renderToNodes(input)
        assertFalse(nodes.any { it is TextNode.SearchHighlight })
    }

    // -------------------------------------------------------------------------
    // Note markers (USFM format: \f + \ft ... \f*)
    // -------------------------------------------------------------------------

    @Test
    fun `note tag produces NoteMarker node`() {
        val input = """\f + \ft footnote text \f*"""
        val nodes = renderer().renderToNodes(input)
        val note = nodes.filterIsInstance<TextNode.NoteMarker>().firstOrNull()
        assertNotNull("Expected NoteMarker node", note)
        assertEquals("+", note!!.caller)
        assertEquals(NoteStyle.FOOTNOTE, note.noteStyle)
    }

    @Test
    fun `note marker highlighted when search matches note content`() {
        val input = """\f + \ft special footnote text \f*"""
        val r = renderer()
        r.setSearchString("special", 0xFF0000)
        val nodes = r.renderToNodes(input)
        val note = nodes.filterIsInstance<TextNode.NoteMarker>().firstOrNull()
        assertNotNull(note)
        assertTrue("Note should be highlighted when search matches", note!!.highlighted)
    }

    @Test
    fun `note marker not highlighted when search does not match`() {
        val input = """\f + \ft footnote text \f*"""
        val r = renderer()
        r.setSearchString("xyz", 0xFF0000)
        val nodes = r.renderToNodes(input)
        val note = nodes.filterIsInstance<TextNode.NoteMarker>().firstOrNull()
        assertNotNull(note)
        assertFalse("Note should not be highlighted when search doesn't match", note!!.highlighted)
    }

    // -------------------------------------------------------------------------
    // Chapter marker stripped (USFM-specific)
    // -------------------------------------------------------------------------

    @Test
    fun `chapter marker is stripped from output`() {
        val input = """\c 1 \v 1 In the beginning"""
        val nodes = renderer().renderToNodes(input)
        // No chapter node should exist
        val allText = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertFalse("Chapter marker should be stripped", allText.contains("\\c"))
    }

    @Test
    fun `chapter marker with multiple digits is stripped`() {
        val input = """\c 25 \v 1 Some verse"""
        val nodes = renderer().renderToNodes(input)
        val allText = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertFalse("Chapter marker \\c 25 should be stripped", allText.contains("\\c"))
        assertFalse("Digit should not appear as stray text", allText.trim().startsWith("25"))
    }

    @Test
    fun `chapter marker produces no ChapterLabel node`() {
        val input = """\c 1 text"""
        val nodes = renderer().renderToNodes(input)
        assertFalse(
            "\\c marker should not produce a ChapterLabel",
            nodes.any { it is TextNode.ChapterLabel }
        )
    }

    @Test
    fun `text after chapter marker is preserved`() {
        val input = """\c 1 \v 1 In the beginning"""
        val nodes = renderer().renderToNodes(input)
        // The verse marker should still be present, and text after it
        assertTrue(nodes.any { it is TextNode.VerseMarker })
    }

    // -------------------------------------------------------------------------
    // USFM paragraph markers \p (USFM-specific)
    // -------------------------------------------------------------------------

    @Test
    fun `USFM paragraph marker produces Paragraph node with indented=false`() {
        // USFMParagraphSpan.PATTERN = "\\p\W?" — matches \p followed by optional non-word char
        val input = """\p some text"""
        val nodes = renderer().renderToNodes(input)
        val para = nodes.filterIsInstance<TextNode.Paragraph>().firstOrNull()
        assertNotNull("Expected Paragraph node from \\p marker", para)
        assertFalse("USFM \\p marker should produce indented=false", para!!.indented)
    }

    @Test
    fun `paragraph markers disabled suppresses USFM paragraph marker`() {
        val input = """\p some text"""
        val r = renderer()
        r.setParagraphsEnabled(false)
        val nodes = r.renderToNodes(input)
        assertFalse(
            "\\p marker should not produce a Paragraph node when paragraphs disabled",
            nodes.any { it is TextNode.Paragraph }
        )
    }

    @Test
    fun `USFM paragraph marker does not produce indented Paragraph`() {
        // Only <para style="p">...</para> tags produce indented=true
        // The standalone \p marker produces indented=false
        val input = """\p"""
        val nodes = renderer().renderToNodes(input)
        val para = nodes.filterIsInstance<TextNode.Paragraph>().firstOrNull()
        assertNotNull("Expected Paragraph node for standalone \\p marker", para)
        assertFalse("Standalone \\p should produce indented=false Paragraph", para!!.indented)
    }

    // -------------------------------------------------------------------------
    // Mixed content
    // -------------------------------------------------------------------------

    @Test
    fun `mixed content produces correct node sequence`() {
        val input = """\v 1 text<para style="b"/>more text"""
        val nodes = renderer().renderToNodes(input)
        assertTrue(nodes.any { it is TextNode.VerseMarker })
        assertTrue(nodes.any { it is TextNode.BlankLine })
        val textContent = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertTrue(textContent.contains("text"))
        assertTrue(textContent.contains("more text"))
    }

    // -------------------------------------------------------------------------
    // No-arg constructor (proves no Context is required)
    // -------------------------------------------------------------------------

    @Test
    fun `no-arg constructor works without Context`() {
        val r = USFMRenderer()
        assertNotNull(r)
        // Calling renderToNodes does not throw due to missing context
        val nodes = r.renderToNodes("hello")
        assertFalse(nodes.isEmpty())
    }

    // -------------------------------------------------------------------------
    // getLeadingMajorSectionHeading
    // -------------------------------------------------------------------------

    @Test
    fun `getLeadingMajorSectionHeading returns heading when leading`() {
        val input = """<para style="ms">GENESIS</para> rest"""
        val heading = renderer().getLeadingMajorSectionHeading(input)
        assertEquals("GENESIS", heading.toString())
    }

    @Test
    fun `getLeadingMajorSectionHeading returns empty when not leading`() {
        val input = """some text <para style="ms">GENESIS</para>"""
        val heading = renderer().getLeadingMajorSectionHeading(input)
        assertEquals("", heading.toString())
    }

    // -------------------------------------------------------------------------
    // isAddedMissingVerse reset
    // -------------------------------------------------------------------------

    @Test
    fun `isAddedMissingVerse is false when no verse is missing`() {
        val input = """\v 1 text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1)) // verse 1 is present
        r.renderToNodes(input)
        assertFalse("isAddedMissingVerse should be false when verse is present", r.isAddedMissingVerse)
    }

    // -------------------------------------------------------------------------
    // stripCarriageReturns
    // -------------------------------------------------------------------------

    @Test
    fun `carriage returns are stripped from input`() {
        val input = "line one\r\nline two"
        val nodes = renderer().renderToNodes(input)
        val allText = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertFalse("Carriage returns should be stripped", allText.contains("\r"))
    }

    // -------------------------------------------------------------------------
    // Note sub-marker leak
    // -------------------------------------------------------------------------

    @Test
    fun `note sub-markers are not leaked as stray text nodes`() {
        // USFMNoteSpan.PATTERN = "\\f\s(\S)\s([\s\S]+?)\f*"
        // A minimal valid note: \f + \fr 1:1 \ft footnote text\f*
        val input = """\f + \fr 1:1 \ft footnote text\f*"""
        val nodes = USFMRenderer().renderToNodes(input)
        val noteMarkers = nodes.filterIsInstance<TextNode.NoteMarker>()
        assertEquals("Expected one NoteMarker", 1, noteMarkers.size)
        val textNodes = nodes.filterIsInstance<TextNode.Text>()
        assertFalse("No raw note sub-markers in text nodes",
            textNodes.any { it.content.contains("\\fr") || it.content.contains("\\ft") })
    }

    // -------------------------------------------------------------------------
    // isAddedMissingVerse reset after render with all verses present
    // -------------------------------------------------------------------------

    @Test
    fun `isAddedMissingVerse is false after render that does not insert missing verse`() {
        val input = """\v 1 text"""
        val r = USFMRenderer()
        r.setPopulateVerseMarkers(intArrayOf(1)) // verse 1 is present
        r.renderToNodes(input)
        assertFalse("isAddedMissingVerse should be false when all verses are present", r.isAddedMissingVerse)
    }
}
