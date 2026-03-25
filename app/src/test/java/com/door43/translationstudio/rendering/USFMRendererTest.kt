package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.RenderNode
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for USFMRenderer.render().
 *
 * These run on the JVM (not on device) because USFMRenderer itself has no Android imports
 * beyond android.content.Context (used only by legacy constructors).
 *
 * USFM input uses pure backslash markers (\p, \v, \q, \s, \ms, \b, \cl, \qr, etc.).
 */
class USFMRendererTest {

    private fun testRender(input: String): List<RenderNode> {
        return renderer().render(input)
    }

    private fun renderer() = USFMRenderer()

    /** Recursively flatten a RenderNode tree into a single list of all nodes (depth-first). */
    private fun flatten(nodes: List<RenderNode>): List<RenderNode> {
        return nodes.flatMap { node ->
            when (node) {
                is RenderNode.Paragraph -> listOf(node) + flatten(node.children)
                is RenderNode.Section -> listOf(node) + flatten(node.children)
                is RenderNode.PoeticLine -> listOf(node) + flatten(node.children)
                else -> listOf(node)
            }
        }
    }

    @Test
    fun `plain text passes through as Text node`() {
        val nodes = testRender("Hello world")
        val text = flatten(nodes).filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertTrue(text.contains("Hello world"))
    }

    @Test
    fun `empty input returns empty list`() {
        val nodes = testRender("")
        val flat = flatten(nodes)
        assertTrue(
            flat.isEmpty() || flat.all { it is RenderNode.Text && it.content.isBlank() }
        )
    }

    @Test
    fun `whitespace-only input is trimmed to empty`() {
        val nodes = testRender("   \n   ")
        val flat = flatten(nodes)
        val hasNonBlankText = flat.any { it is RenderNode.Text && it.content.isNotBlank() }
        assertFalse(hasNonBlankText)
    }

    @Test
    fun `section heading produces Section node`() {
        val input = """\s The Beginning"""
        val nodes = testRender(input)
        val heading = flatten(nodes).filterIsInstance<RenderNode.Section>().firstOrNull()
        assertNotNull("Expected Section node", heading)
        assertEquals("The Beginning", heading!!.text)
        assertFalse(heading.isMajor)
    }

    @Test
    fun `major section heading produces isMajor=true Section`() {
        val input = """\ms CREATION"""
        val nodes = testRender(input)
        val heading = flatten(nodes).filterIsInstance<RenderNode.Section>().firstOrNull()
        assertNotNull(heading)
        assertTrue(heading!!.isMajor)
        assertEquals("CREATION", heading.text)
    }

    @Test
    fun `suppressed leading major section heading is excluded`() {
        val input = """\ms INTRO"""
        val r = renderer()
        r.setSuppressLeadingMajorSectionHeadings(true)
        val nodes = r.render(input)
        assertFalse(
            "Suppressed heading should not appear",
            flatten(nodes).any { it is RenderNode.Section && it.isMajor }
        )
    }

    @Test
    fun `non-leading major section heading is NOT suppressed`() {
        val input = """\v 1 text
\ms MID SECTION"""
        val r = renderer()
        r.setSuppressLeadingMajorSectionHeadings(true)
        val nodes = r.render(input)
        assertTrue(
            "Non-leading major heading should still appear",
            flatten(nodes).any { it is RenderNode.Section && it.isMajor }
        )
    }

    @Test
    fun `section heading is followed by LineBreak`() {
        val input = """\s Heading"""
        val nodes = testRender(input)
        val flat = flatten(nodes)
        val headingIndex = flat.indexOfFirst { it is RenderNode.Section }
        assertTrue(headingIndex >= 0)
        assertTrue(
            "Section should be followed by LineBreak",
            flat.getOrNull(headingIndex + 1) == RenderNode.LineBreak
        )
    }

    @Test
    fun `verse tag produces Verse node`() {
        val input = """\v 1 In the beginning"""
        val nodes = testRender(input)
        val verse = flatten(nodes).filterIsInstance<RenderNode.Verse>().firstOrNull()
        assertNotNull("Expected Verse", verse)
        assertEquals(1, verse!!.startVerse)
        assertEquals(0, verse.endVerse)
    }

    @Test
    fun `verse range produces Verse with endVerse`() {
        val input = """\v 3-5 joined verses"""
        val nodes = testRender(input)
        val verse = flatten(nodes).filterIsInstance<RenderNode.Verse>().firstOrNull()
        assertNotNull(verse)
        assertEquals(3, verse!!.startVerse)
        assertEquals(5, verse.endVerse)
    }

    @Test
    fun `duplicate verse is excluded`() {
        val input = """\v 1 text \v 1 dup"""
        val nodes = testRender(input)
        assertEquals(
            "Only one verse 1 expected",
            1,
            flatten(nodes).filterIsInstance<RenderNode.Verse>().count { it.startVerse == 1 }
        )
    }

    @Test
    fun `verses disabled produces no Verse nodes`() {
        val input = """\v 1 text"""
        val r = renderer()
        r.setVersesEnabled(false)
        val nodes = r.render(input)
        assertFalse(flatten(nodes).any { it is RenderNode.Verse })
    }

    @Test
    fun `expected verse range filters out-of-range verses`() {
        val input = """\v 3 text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1, 2)) // only verses 1-2 expected
        val nodes = r.render(input)
        assertFalse(
            "Verse 3 should be filtered out",
            flatten(nodes).filterIsInstance<RenderNode.Verse>().any { it.startVerse == 3 }
        )
    }

    @Test
    fun `missing expected verse is inserted`() {
        val input = """\v 2 text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1)) // expect verse 1, which is not present
        val nodes = r.render(input)
        assertTrue(
            "Missing verse 1 should be inserted",
            flatten(nodes).filterIsInstance<RenderNode.Verse>().any { it.startVerse == 1 }
        )
        assertTrue(r.isAddedMissingVerse)
    }

    @Test
    fun `missing verse is prepended before existing content`() {
        val input = """\v 2 text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1))
        val nodes = r.render(input)
        val firstVerse = flatten(nodes).filterIsInstance<RenderNode.Verse>().first()
        assertEquals("Missing verse should be first", 1, firstVerse.startVerse)
    }

    @Test
    fun `verse range missing all verses inserts them all`() {
        val input = "no verses here"
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1, 3))
        val nodes = r.render(input)
        val verses = flatten(nodes).filterIsInstance<RenderNode.Verse>().map { it.startVerse }.toSet()
        assertTrue(verses.containsAll(listOf(1, 2, 3)))
        assertTrue(r.isAddedMissingVerse)
    }

    @Test
    fun `blank line marker produces LineBreak node`() {
        val input = """text \b more"""
        val nodes = testRender(input)
        assertTrue(flatten(nodes).any { it is RenderNode.LineBreak })
    }

    @Test
    fun `paragraph marker produces Paragraph node`() {
        val input = """\p paragraph content"""
        val nodes = testRender(input)
        assertTrue("Expected Paragraph node", flatten(nodes).any { it is RenderNode.Paragraph })
    }

    @Test
    fun `paragraph content is preserved as Text node`() {
        val input = """\p paragraph content"""
        val nodes = testRender(input)
        val allText = flatten(nodes).filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertTrue("Paragraph content should be in Text nodes", allText.contains("paragraph content"))
    }

    @Test
    fun `paragraph node from p marker has indented=false`() {
        val input = """\p some text"""
        val nodes = testRender(input)
        val para = flatten(nodes).filterIsInstance<RenderNode.Paragraph>().firstOrNull()
        assertNotNull(para)
        assertFalse(para!!.indented)
    }

    @Test
    fun `poetic line produces PoeticLine node with correct indent`() {
        val input = """\q2 Praise the Lord"""
        val nodes = testRender(input)
        val poetic = flatten(nodes).filterIsInstance<RenderNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertEquals(2, poetic!!.indentLevel)
        assertFalse(poetic.rightAligned)
    }

    @Test
    fun `q1 poetic line has indentLevel 1`() {
        val input = """\q1 first indent"""
        val nodes = testRender(input)
        val poetic = flatten(nodes).filterIsInstance<RenderNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertEquals(1, poetic!!.indentLevel)
    }

    @Test
    fun `right-aligned poetic line produces rightAligned=true PoeticLine`() {
        val input = """\qr Selah"""
        val nodes = testRender(input)
        val poetic = flatten(nodes).filterIsInstance<RenderNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertTrue(poetic!!.rightAligned)
    }

    @Test
    fun `right-aligned poetic line has rightAligned flag`() {
        val input = """\qr Selah"""
        val nodes = testRender(input)
        val poetic = flatten(nodes).filterIsInstance<RenderNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertTrue("Expected rightAligned=true", poetic!!.rightAligned)
    }

    @Test
    fun `chapter label produces ChapterLabel node`() {
        val input = """\cl Chapter One"""
        val nodes = testRender(input)
        val label = flatten(nodes).filterIsInstance<RenderNode.ChapterLabel>().firstOrNull()
        assertNotNull(label)
        assertEquals("Chapter One", label!!.text)
    }

    @Test
    fun `search string produces SearchHighlight nodes`() {
        val input = """\v 1 In the beginning God created"""
        val r = renderer()
        r.setSearchString("beginning")
        val nodes = r.render(input)
        val highlights = flatten(nodes).filterIsInstance<RenderNode.Text>()
            .filter { it.attributes.searchHighlighted }
        assertTrue("Expected search-highlighted Text node", highlights.isNotEmpty())
        assertEquals("beginning", highlights.first().content)
    }

    @Test
    fun `search highlight preserves surrounding text`() {
        val input = "In the beginning God"
        val r = renderer()
        r.setSearchString("beginning")
        val nodes = r.render(input)
        val allText = flatten(nodes).filterIsInstance<RenderNode.Text>()
            .joinToString("") { it.content }
        assertEquals("In the beginning God", allText)
    }

    @Test
    fun `search is case-insensitive`() {
        val input = "In the Beginning God"
        val r = renderer()
        r.setSearchString("beginning")
        val nodes = r.render(input)
        assertTrue(flatten(nodes).filterIsInstance<RenderNode.Text>()
            .any { it.attributes.searchHighlighted })
    }

    @Test
    fun `search highlight content matches original case`() {
        val input = "In the Beginning God"
        val r = renderer()
        r.setSearchString("beginning")
        val nodes = r.render(input)
        val highlight = flatten(nodes).filterIsInstance<RenderNode.Text>()
            .firstOrNull { it.attributes.searchHighlighted }
        assertNotNull(highlight)
        assertEquals("Beginning", highlight!!.content)
    }

    @Test
    fun `empty search string disables highlighting`() {
        val input = "In the beginning"
        val r = renderer()
        r.setSearchString("")
        val nodes = r.render(input)
        assertFalse(flatten(nodes).filterIsInstance<RenderNode.Text>()
            .any { it.attributes.searchHighlighted })
    }

    @Test
    fun `note tag produces Note node`() {
        val input = """\f + \ft footnote text \f*"""
        val nodes = testRender(input)
        val note = flatten(nodes).filterIsInstance<RenderNode.Note>().firstOrNull()
        assertNotNull("Expected Note node", note)
        assertEquals("+", note!!.caller)
        assertEquals(NoteStyle.FOOTNOTE, note.noteStyle)
    }

    @Test
    fun `note marker highlighted when search matches note content`() {
        val input = """\f + \ft special footnote text \f*"""
        val r = renderer()
        r.setSearchString("special")
        val nodes = r.render(input)
        val note = flatten(nodes).filterIsInstance<RenderNode.Note>().firstOrNull()
        assertNotNull(note)
        assertTrue("Note should be highlighted when search matches", note!!.attributes.searchHighlighted)
    }

    @Test
    fun `note marker not highlighted when search does not match`() {
        val input = """\f + \ft footnote text \f*"""
        val r = renderer()
        r.setSearchString("xyz")
        val nodes = r.render(input)
        val note = flatten(nodes).filterIsInstance<RenderNode.Note>().firstOrNull()
        assertNotNull(note)
        assertFalse("Note should not be highlighted when search doesn't match", note!!.attributes.searchHighlighted)
    }

    @Test
    fun `chapter marker is stripped from output`() {
        val input = """\c 1 \v 1 In the beginning"""
        val nodes = testRender(input)
        // No chapter node should exist
        val allText = flatten(nodes).filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertFalse("Chapter marker should be stripped", allText.contains("\\c"))
    }

    @Test
    fun `chapter marker with multiple digits is stripped`() {
        val input = """\c 25 \v 1 Some verse"""
        val nodes = testRender(input)
        val allText = flatten(nodes).filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertFalse("Chapter marker \\c 25 should be stripped", allText.contains("\\c"))
        assertFalse("Digit should not appear as stray text", allText.trim().startsWith("25"))
    }

    @Test
    fun `chapter marker produces no ChapterLabel node`() {
        val input = """\c 1 text"""
        val nodes = testRender(input)
        assertFalse(
            "\\c marker should not produce a ChapterLabel",
            flatten(nodes).any { it is RenderNode.ChapterLabel }
        )
    }

    @Test
    fun `text after chapter marker is preserved`() {
        val input = """\c 1 \v 1 In the beginning"""
        val nodes = testRender(input)
        // The verse marker should still be present, and text after it
        assertTrue(flatten(nodes).any { it is RenderNode.Verse })
    }

    @Test
    fun `USFM paragraph marker produces Paragraph node with indented=false`() {
        // USFMParagraphSpan.PATTERN = "\\p\W?" — matches \p followed by optional non-word char
        val input = """\p some text"""
        val nodes = testRender(input)
        val para = flatten(nodes).filterIsInstance<RenderNode.Paragraph>().firstOrNull()
        assertNotNull("Expected Paragraph node from \\p marker", para)
        assertFalse("USFM \\p marker should produce indented=false", para!!.indented)
    }

    @Test
    fun `paragraph markers disabled suppresses USFM paragraph marker`() {
        val input = """\p some text"""
        val r = renderer()
        r.setParagraphsEnabled(false)
        val nodes = r.render(input)
        assertFalse(
            "\\p marker should not produce a Paragraph node when paragraphs disabled",
            flatten(nodes).any { it is RenderNode.Paragraph }
        )
    }

    @Test
    fun `USFM paragraph marker does not produce indented Paragraph`() {
        // Only <para style="p">...</para> tags produce indented=true
        // The standalone \p marker produces indented=false
        val input = """\p"""
        val nodes = testRender(input)
        val para = flatten(nodes).filterIsInstance<RenderNode.Paragraph>().firstOrNull()
        assertNotNull("Expected Paragraph node for standalone \\p marker", para)
        assertFalse("Standalone \\p should produce indented=false Paragraph", para!!.indented)
    }

    @Test
    fun `mixed content produces correct node sequence`() {
        val input = """\v 1 text \b more text"""
        val nodes = testRender(input)
        val flat = flatten(nodes)
        assertTrue(flat.any { it is RenderNode.Verse })
        assertTrue(flat.any { it is RenderNode.LineBreak })
        val textContent = flat.filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertTrue(textContent.contains("text"))
        assertTrue(textContent.contains("more text"))
    }

    @Test
    fun `no-arg constructor works without Context`() {
        val r = USFMRenderer()
        assertNotNull(r)
        // Calling render does not throw due to missing context
        val nodes = r.render("hello")
        assertFalse(nodes.isEmpty())
    }

    @Test
    fun `getLeadingMajorSectionHeading returns heading when leading`() {
        val input = """\ms GENESIS"""
        val heading = renderer().getLeadingMajorSectionHeading(input)
        assertEquals("GENESIS", heading.toString())
    }

    @Test
    fun `getLeadingMajorSectionHeading returns empty when not leading`() {
        val input = """some text \ms GENESIS"""
        val heading = renderer().getLeadingMajorSectionHeading(input)
        assertEquals("", heading.toString())
    }

    @Test
    fun `isAddedMissingVerse is false when no verse is missing`() {
        val input = """\v 1 text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1)) // verse 1 is present
        r.render(input)
        assertFalse("isAddedMissingVerse should be false when verse is present", r.isAddedMissingVerse)
    }

    @Test
    fun `carriage returns are stripped from input`() {
        val input = "line one\r\nline two"
        val nodes = testRender(input)
        val allText = flatten(nodes).filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertFalse("Carriage returns should be stripped", allText.contains("\r"))
    }

    @Test
    fun `note sub-markers are not leaked as stray text nodes`() {
        // USFMNoteSpan.PATTERN = "\\f\s(\S)\s([\s\S]+?)\f*"
        // A minimal valid note: \f + \fr 1:1 \ft footnote text\f*
        val input = """\f + \fr 1:1 \ft footnote text\f*"""
        val nodes = USFMRenderer().render(input)
        val flat = flatten(nodes)
        val noteMarkers = flat.filterIsInstance<RenderNode.Note>()
        assertEquals("Expected one Note", 1, noteMarkers.size)
        val textNodes = flat.filterIsInstance<RenderNode.Text>()
        assertFalse("No raw note sub-markers in text nodes",
            textNodes.any { it.content.contains("\\fr") || it.content.contains("\\ft") })
    }

    @Test
    fun `isAddedMissingVerse is false after render that does not insert missing verse`() {
        val input = """\v 1 text"""
        val r = USFMRenderer()
        r.setPopulateVerseMarkers(intArrayOf(1)) // verse 1 is present
        r.render(input)
        assertFalse("isAddedMissingVerse should be false when all verses are present", r.isAddedMissingVerse)
    }
}
