package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.TextNode
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for USXRenderer.renderToNodes().
 *
 * These run on the JVM (not on device) because USXRenderer itself has no Android imports.
 * Note parsing (USXNoteSpan.parseNote) uses android.util.Xml internally and is therefore
 * exercised separately in instrumented tests.
 */
class USXRendererTest {

    private fun testRender(input: String): List<TextNode> {
        val renderNodes = renderer().renderToNodes(input)
        return RenderNodeConverter.renderNodesToTextNodes(renderNodes)
    }

    private fun renderer() = USXRenderer()

    @Test
    fun `plain text passes through as Text node`() {
        val nodes = testRender("Hello world")
        val text = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertTrue(text.contains("Hello world"))
    }

    @Test
    fun `empty input returns empty list`() {
        val nodes = testRender("")
        assertTrue(
            nodes.isEmpty() || nodes.all { it is TextNode.Text && (it as TextNode.Text).content.isBlank() }
        )
    }

    @Test
    fun `whitespace-only input is trimmed to empty`() {
        val nodes = testRender("   \n   ")
        val hasNonBlankText = nodes.any { it is TextNode.Text && (it as TextNode.Text).content.isNotBlank() }
        assertFalse(hasNonBlankText)
    }

    @Test
    fun `section heading produces SectionHeading node`() {
        val input = """<para style="s">The Beginning</para>"""
        val nodes = testRender(input)
        val heading = nodes.filterIsInstance<TextNode.SectionHeading>().firstOrNull()
        assertNotNull("Expected SectionHeading node", heading)
        assertEquals("The Beginning", heading!!.text)
        assertFalse(heading.isMajor)
    }

    @Test
    fun `major section heading produces isMajor=true SectionHeading`() {
        val input = """<para style="ms">CREATION</para>"""
        val nodes = testRender(input)
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
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        assertFalse(
            "Suppressed heading should not appear",
            nodes.any { it is TextNode.SectionHeading && (it as TextNode.SectionHeading).isMajor }
        )
    }

    @Test
    fun `non-leading major section heading is NOT suppressed`() {
        val input = """<verse number="1" style="v" />text <para style="ms">MID SECTION</para>"""
        val r = renderer()
        r.setSuppressLeadingMajorSectionHeadings(true)
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        assertTrue(
            "Non-leading major heading should still appear",
            nodes.any { it is TextNode.SectionHeading && (it as TextNode.SectionHeading).isMajor }
        )
    }

    @Test
    fun `section heading is followed by LineBreak`() {
        val input = """<para style="s">Heading</para>"""
        val nodes = testRender(input)
        val headingIndex = nodes.indexOfFirst { it is TextNode.SectionHeading }
        assertTrue(headingIndex >= 0)
        assertTrue(
            "SectionHeading should be followed by LineBreak",
            nodes.getOrNull(headingIndex + 1) == TextNode.LineBreak
        )
    }

    @Test
    fun `verse tag produces VerseMarker node`() {
        val input = """<verse number="1" style="v" />In the beginning"""
        val nodes = testRender(input)
        val verse = nodes.filterIsInstance<TextNode.VerseMarker>().firstOrNull()
        assertNotNull("Expected VerseMarker", verse)
        assertEquals(1, verse!!.startVerse)
        assertEquals(0, verse.endVerse)
    }

    @Test
    fun `verse range produces VerseMarker with endVerse`() {
        val input = """<verse number="3-5" style="v" />joined verses"""
        val nodes = testRender(input)
        val verse = nodes.filterIsInstance<TextNode.VerseMarker>().firstOrNull()
        assertNotNull(verse)
        assertEquals(3, verse!!.startVerse)
        assertEquals(5, verse.endVerse)
    }

    @Test
    fun `duplicate verse is excluded`() {
        val input = """<verse number="1" style="v" />text <verse number="1" style="v" />dup"""
        val nodes = testRender(input)
        assertEquals(
            "Only one verse 1 expected",
            1,
            nodes.filterIsInstance<TextNode.VerseMarker>().count { it.startVerse == 1 }
        )
    }

    @Test
    fun `verses disabled produces no VerseMarker nodes`() {
        val input = """<verse number="1" style="v" />text"""
        val r = renderer()
        r.setVersesEnabled(false)
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        assertFalse(nodes.any { it is TextNode.VerseMarker })
    }

    @Test
    fun `expected verse range filters out-of-range verses`() {
        val input = """<verse number="3" style="v" />text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1, 2)) // only verses 1-2 expected
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        assertFalse(
            "Verse 3 should be filtered out",
            nodes.filterIsInstance<TextNode.VerseMarker>().any { it.startVerse == 3 }
        )
    }

    @Test
    fun `missing expected verse is inserted`() {
        val input = """<verse number="2" style="v" />text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1)) // expect verse 1, which is not present
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        assertTrue(
            "Missing verse 1 should be inserted",
            nodes.filterIsInstance<TextNode.VerseMarker>().any { it.startVerse == 1 }
        )
        assertTrue(r.isAddedMissingVerse)
    }

    @Test
    fun `missing verse is prepended before existing content`() {
        val input = """<verse number="2" style="v" />text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1))
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        val firstVerse = nodes.filterIsInstance<TextNode.VerseMarker>().first()
        assertEquals("Missing verse should be first", 1, firstVerse.startVerse)
    }

    @Test
    fun `verse range missing all verses inserts them all`() {
        val input = "no verses here"
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1, 3))
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        val verses = nodes.filterIsInstance<TextNode.VerseMarker>().map { it.startVerse }.toSet()
        assertTrue(verses.containsAll(listOf(1, 2, 3)))
        assertTrue(r.isAddedMissingVerse)
    }

    @Test
    fun `blank line tag produces BlankLine node`() {
        val input = """text<para style="b"/>more"""
        val nodes = testRender(input)
        assertTrue(nodes.any { it is TextNode.BlankLine })
    }

    @Test
    fun `paragraph tag produces Paragraph node`() {
        val input = """<para style="p">paragraph content</para>"""
        val nodes = testRender(input)
        assertTrue("Expected Paragraph node", nodes.any { it is TextNode.Paragraph })
    }

    @Test
    fun `paragraph content is preserved as Text node`() {
        val input = """<para style="p">paragraph content</para>"""
        val nodes = testRender(input)
        val allText = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertTrue("Paragraph content should be in Text nodes", allText.contains("paragraph content"))
    }

    @Test
    fun `paragraph node has indented=true`() {
        val input = """<para style="p">some text</para>"""
        val nodes = testRender(input)
        val para = nodes.filterIsInstance<TextNode.Paragraph>().firstOrNull()
        assertNotNull(para)
        assertTrue(para!!.indented)
    }

    @Test
    fun `poetic line produces PoeticLine node with correct indent`() {
        val input = """<para style="q2">Praise the Lord</para>"""
        val nodes = testRender(input)
        val poetic = nodes.filterIsInstance<TextNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertEquals(2, poetic!!.indentLevel)
        assertFalse(poetic.rightAligned)
    }

    @Test
    fun `q1 poetic line has indentLevel 1`() {
        val input = """<para style="q1">first indent</para>"""
        val nodes = testRender(input)
        val poetic = nodes.filterIsInstance<TextNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertEquals(1, poetic!!.indentLevel)
    }

    @Test
    fun `right-aligned poetic line produces rightAligned=true PoeticLine`() {
        val input = """<para style="qr">Selah</para>"""
        val nodes = testRender(input)
        val poetic = nodes.filterIsInstance<TextNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertTrue(poetic!!.rightAligned)
    }

    @Test
    fun `right-aligned poetic line is preceded by LineBreak`() {
        val input = """<para style="qr">Selah</para>"""
        val nodes = testRender(input)
        val poeticIdx = nodes.indexOfFirst { it is TextNode.PoeticLine }
        assertTrue(poeticIdx > 0)
        assertEquals(TextNode.LineBreak, nodes[poeticIdx - 1])
    }

    @Test
    fun `Selah char tag inside paragraph is rendered`() {
        val input = """<para style="q1">Test text <char style="qs">Selah</char></para>"""
        val nodes = testRender(input)
        // After rendering, we should have a LineBreak followed by a right-aligned PoeticLine with "Selah"
        val poeticLines = nodes.filterIsInstance<TextNode.PoeticLine>()
        assertTrue("Expected at least one PoeticLine node for Selah", poeticLines.isNotEmpty())
        val selahLine = poeticLines.lastOrNull()
        assertNotNull(selahLine)
        assertTrue("Selah line should be right-aligned", selahLine!!.rightAligned)
        assertEquals("Selah", selahLine.content)
    }

    @Test
    fun `chapter label produces ChapterLabel node`() {
        val input = """<para style="cl">Chapter One</para>"""
        val nodes = testRender(input)
        val label = nodes.filterIsInstance<TextNode.ChapterLabel>().firstOrNull()
        assertNotNull(label)
        assertEquals("Chapter One", label!!.text)
    }

    @Test
    fun `search string produces SearchHighlight nodes`() {
        val input = """<verse number="1" style="v" />In the beginning God created"""
        val r = renderer()
        r.setSearchString("beginning", 0xFF0000)
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        val highlights = nodes.filterIsInstance<TextNode.SearchHighlight>()
        assertTrue("Expected SearchHighlight node", highlights.isNotEmpty())
        assertEquals("beginning", highlights.first().content)
    }

    @Test
    fun `search highlight preserves surrounding text`() {
        val input = "In the beginning God"
        val r = renderer()
        r.setSearchString("beginning", 0xFF0000)
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
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
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        assertTrue(nodes.any { it is TextNode.SearchHighlight })
    }

    @Test
    fun `search highlight content matches original case`() {
        val input = "In the Beginning God"
        val r = renderer()
        r.setSearchString("beginning", 0xFF0000)
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        val highlight = nodes.filterIsInstance<TextNode.SearchHighlight>().firstOrNull()
        assertNotNull(highlight)
        // The highlighted content preserves the original case from the input
        assertEquals("Beginning", highlight!!.content)
    }

    @Test
    fun `empty search string disables highlighting`() {
        val input = "In the beginning"
        val r = renderer()
        r.setSearchString("", 0xFF0000)
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        assertFalse(nodes.any { it is TextNode.SearchHighlight })
    }

    @Test
    fun `mixed content produces correct node sequence`() {
        // verse + text + blank line
        val input = """<verse number="1" style="v" />text<para style="b"/>more text"""
        val nodes = testRender(input)
        assertTrue(nodes.any { it is TextNode.VerseMarker })
        assertTrue(nodes.any { it is TextNode.BlankLine })
        val textContent = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertTrue(textContent.contains("text"))
        assertTrue(textContent.contains("more text"))
    }

    @Test
    fun `no-arg constructor works without Context`() {
        val r = USXRenderer()
        assertNotNull(r)
        // Calling renderToNodes does not throw due to missing context
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes("hello"))
        assertFalse(nodes.isEmpty())
    }

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

    @Test
    fun `note tag produces NoteMarker node`() {
        val input = """<note style="f" caller="+"><char style="fr">1:1 </char><char style="ft">footnote text</char></note>"""
        val nodes = testRender(input)
        val note = nodes.filterIsInstance<TextNode.NoteMarker>().firstOrNull()
        assertNotNull("Expected NoteMarker node", note)
        assertEquals("+", note!!.caller)
        assertEquals(NoteStyle.FOOTNOTE, note.noteStyle)
    }

    @Test
    fun `note marker highlighted when search matches note content`() {
        val input = """<note style="f" caller="+"><char style="ft">special footnote text</char></note>"""
        val r = renderer()
        r.setSearchString("special", 0xFF0000)
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        val note = nodes.filterIsInstance<TextNode.NoteMarker>().firstOrNull()
        assertNotNull(note)
        assertTrue("Note should be highlighted when search matches", note!!.highlighted)
    }

    @Test
    fun `note marker not highlighted when search does not match`() {
        val input = """<note style="f" caller="+"><char style="ft">footnote text</char></note>"""
        val r = renderer()
        r.setSearchString("xyz", 0xFF0000)
        val nodes = RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        val note = nodes.filterIsInstance<TextNode.NoteMarker>().firstOrNull()
        assertNotNull(note)
        assertFalse("Note should not be highlighted when search doesn't match", note!!.highlighted)
    }

    @Test
    fun `char tag inside note span is not emitted as separate node`() {
        // The <char style="ft"> inside a <note> should be consumed by the note parser,
        // not emitted as a separate Text node by stripRemainingMarkers.
        val input = """<note style="f" caller="+"><char style="ft">note text</char></note>"""
        val nodes = testRender(input)
        // Should have exactly one NoteMarker, no stray text from char tag markup
        val noteMarkers = nodes.filterIsInstance<TextNode.NoteMarker>()
        assertEquals(1, noteMarkers.size)
        // No text node should contain raw "<char" markup
        val textNodes = nodes.filterIsInstance<TextNode.Text>()
        assertFalse("No stray <char> markup in text nodes",
            textNodes.any { it.content.contains("<char") })
    }

    @Test
    fun `isAddedMissingVerse is false when no verse is missing`() {
        val input = """<verse number="1" style="v" />text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1)) // verse 1 is present
        RenderNodeConverter.renderNodesToTextNodes(r.renderToNodes(input))
        assertFalse("isAddedMissingVerse should be false when verse is present", r.isAddedMissingVerse)
    }

    @Test
    fun `verse markers are pinned when verseDisplay is PIN`() {
        val renderer = USXRenderer(verseDisplay = VerseDisplay.PIN)
        val nodes = RenderNodeConverter.renderNodesToTextNodes(renderer.renderToNodes("<verse number=\"1\" style=\"v\" />In the beginning."))
        val verse = nodes.filterIsInstance<TextNode.VerseMarker>().firstOrNull()
        assertNotNull("Expected a VerseMarker node", verse)
        assertTrue("Expected pinned=true when verseDisplay=PIN", verse!!.pinned)
    }

    @Test
    fun `verse markers are not pinned when verseDisplay is NUMBER`() {
        val renderer = USXRenderer(verseDisplay = VerseDisplay.NUMBER)
        val nodes = RenderNodeConverter.renderNodesToTextNodes(renderer.renderToNodes("<verse number=\"1\" style=\"v\" />In the beginning."))
        val verse = nodes.filterIsInstance<TextNode.VerseMarker>().firstOrNull()
        assertNotNull(verse)
        assertFalse("Expected pinned=false when verseDisplay=NUMBER", verse!!.pinned)
    }

    @Test
    fun `missing verse marker is pinned when verseDisplay is PIN`() {
        // USX with verse 2 present but verse 1 missing; expected range includes verse 1
        val renderer = USXRenderer(verseDisplay = VerseDisplay.PIN)
        // Set expected verse range to include verse 1 even though only verse 2 is in the text
        renderer.setPopulateVerseMarkers(intArrayOf(1, 2))
        val nodes = RenderNodeConverter.renderNodesToTextNodes(renderer.renderToNodes("<para style=\"p\"><verse number=\"2\" style=\"v\" />Second verse.</para>"))
        val missing = nodes.filterIsInstance<TextNode.VerseMarker>().firstOrNull { it.startVerse == 1 }
        assertNotNull("Expected verse 1 to be inserted as missing", missing)
        assertTrue("Expected missing verse marker to be pinned when verseDisplay=PIN", missing!!.pinned)
    }
}
