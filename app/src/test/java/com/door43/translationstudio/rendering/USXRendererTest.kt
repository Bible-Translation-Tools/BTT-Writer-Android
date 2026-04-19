package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.RenderNode
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for USXRenderer.render().
 *
 * These run on the JVM (not on device) because USXRenderer itself has no Android imports.
 * Note parsing (USXNoteSpan.parseNote) uses android.util.Xml internally and is therefore
 * exercised separately in instrumented tests.
 */
class USXRendererTest {

    private fun testRender(input: String): List<RenderNode> {
        return renderer().render(input)
    }

    private fun renderer() = USXRenderer()

    @Test
    fun `plain text passes through as Text node`() {
        val nodes = testRender("Hello world")
        val text = nodes.filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertTrue(text.contains("Hello world"))
    }

    @Test
    fun `empty input returns empty list`() {
        val nodes = testRender("")
        assertTrue(
            nodes.isEmpty() || nodes.all { it is RenderNode.Text && (it as RenderNode.Text).content.isBlank() }
        )
    }

    @Test
    fun `whitespace-only input is trimmed to empty`() {
        val nodes = testRender("   \n   ")
        val hasNonBlankText = nodes.any { it is RenderNode.Text && (it as RenderNode.Text).content.isNotBlank() }
        assertFalse(hasNonBlankText)
    }

    @Test
    fun `section heading produces Section node`() {
        val input = """<para style="s">The Beginning</para>"""
        val nodes = testRender(input)
        val heading = nodes.filterIsInstance<RenderNode.Section>().firstOrNull()
        assertNotNull("Expected Section node", heading)
        assertEquals("The Beginning", heading!!.text)
        assertFalse(heading.isMajor)
    }

    @Test
    fun `major section heading produces isMajor=true Section`() {
        val input = """<para style="ms">CREATION</para>"""
        val nodes = testRender(input)
        val heading = nodes.filterIsInstance<RenderNode.Section>().firstOrNull()
        assertNotNull(heading)
        assertTrue(heading!!.isMajor)
        assertEquals("CREATION", heading.text)
    }

    @Test
    fun `suppressed leading major section heading is excluded`() {
        val input = """<para style="ms">INTRO</para> rest of text"""
        val r = renderer()
        r.setSuppressLeadingMajorSectionHeadings(true)
        val nodes = r.render(input)
        assertFalse(
            "Suppressed heading should not appear",
            nodes.any { it is RenderNode.Section && (it as RenderNode.Section).isMajor }
        )
    }

    @Test
    fun `non-leading major section heading is NOT suppressed`() {
        val input = """<verse number="1" style="v" />text <para style="ms">MID SECTION</para>"""
        val r = renderer()
        r.setSuppressLeadingMajorSectionHeadings(true)
        val nodes = r.render(input)
        assertTrue(
            "Non-leading major heading should still appear",
            nodes.any { it is RenderNode.Section && (it as RenderNode.Section).isMajor }
        )
    }

    @Test
    fun `section heading is followed by LineBreak`() {
        val input = """<para style="s">Heading</para>"""
        val nodes = testRender(input)
        val headingIndex = nodes.indexOfFirst { it is RenderNode.Section }
        assertTrue(headingIndex >= 0)
        assertTrue(
            "Section should be followed by LineBreak",
            nodes.getOrNull(headingIndex + 1) == RenderNode.LineBreak
        )
    }

    @Test
    fun `verse tag produces Verse node`() {
        val input = """<verse number="1" style="v" />In the beginning"""
        val nodes = testRender(input)
        val verse = nodes.filterIsInstance<RenderNode.Verse>().firstOrNull()
        assertNotNull("Expected Verse", verse)
        assertEquals(1, verse!!.startVerse)
        assertEquals(0, verse.endVerse)
    }

    @Test
    fun `verse range produces Verse with endVerse`() {
        val input = """<verse number="3-5" style="v" />joined verses"""
        val nodes = testRender(input)
        val verse = nodes.filterIsInstance<RenderNode.Verse>().firstOrNull()
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
            nodes.filterIsInstance<RenderNode.Verse>().count { it.startVerse == 1 }
        )
    }

    @Test
    fun `verses disabled produces no Verse nodes`() {
        val input = """<verse number="1" style="v" />text"""
        val r = renderer()
        r.setVersesEnabled(false)
        val nodes = r.render(input)
        assertFalse(nodes.any { it is RenderNode.Verse })
    }

    @Test
    fun `expected verse range filters out-of-range verses`() {
        val input = """<verse number="3" style="v" />text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1, 2)) // only verses 1-2 expected
        val nodes = r.render(input)
        assertFalse(
            "Verse 3 should be filtered out",
            nodes.filterIsInstance<RenderNode.Verse>().any { it.startVerse == 3 }
        )
    }

    @Test
    fun `missing expected verse is inserted`() {
        val input = """<verse number="2" style="v" />text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1)) // expect verse 1, which is not present
        val nodes = r.render(input)
        assertTrue(
            "Missing verse 1 should be inserted",
            nodes.filterIsInstance<RenderNode.Verse>().any { it.startVerse == 1 }
        )
        assertTrue(r.isAddedMissingVerse)
    }

    @Test
    fun `missing verse is prepended before existing content`() {
        val input = """<verse number="2" style="v" />text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1))
        val nodes = r.render(input)
        val firstVerse = nodes.filterIsInstance<RenderNode.Verse>().first()
        assertEquals("Missing verse should be first", 1, firstVerse.startVerse)
    }

    @Test
    fun `verse range missing all verses inserts them all`() {
        val input = "no verses here"
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1, 3))
        val nodes = r.render(input)
        val verses = nodes.filterIsInstance<RenderNode.Verse>().map { it.startVerse }.toSet()
        assertTrue(verses.containsAll(listOf(1, 2, 3)))
        assertTrue(r.isAddedMissingVerse)
    }

    @Test
    fun `blank line tag produces LineBreak node`() {
        val input = """text<para style="b"/>more"""
        val nodes = testRender(input)
        assertTrue(nodes.any { it is RenderNode.LineBreak })
    }

    @Test
    fun `paragraph tag produces Paragraph node`() {
        val input = """<para style="p">paragraph content</para>"""
        val nodes = testRender(input)
        assertTrue("Expected Paragraph node", nodes.any { it is RenderNode.Paragraph })
    }

    @Test
    fun `paragraph content is preserved as Text node in children`() {
        val input = """<para style="p">paragraph content</para>"""
        val nodes = testRender(input)
        val para = nodes.filterIsInstance<RenderNode.Paragraph>().firstOrNull()
        assertNotNull("Expected Paragraph node", para)
        val childText = para!!.children.filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertTrue("Paragraph children should contain text", childText.contains("paragraph content"))
    }

    @Test
    fun `paragraph node has indented=false`() {
        val input = """<para style="p">some text</para>"""
        val nodes = testRender(input)
        val para = nodes.filterIsInstance<RenderNode.Paragraph>().firstOrNull()
        assertNotNull(para)
        assertFalse(para!!.indented)
    }

    @Test
    fun `poetic line produces PoeticLine node with correct indent`() {
        val input = """<para style="q2">Praise the Lord</para>"""
        val nodes = testRender(input)
        val poetic = nodes.filterIsInstance<RenderNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertEquals(2, poetic!!.indentLevel)
        assertFalse(poetic.rightAligned)
    }

    @Test
    fun `q1 poetic line has indentLevel 1`() {
        val input = """<para style="q1">first indent</para>"""
        val nodes = testRender(input)
        val poetic = nodes.filterIsInstance<RenderNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertEquals(1, poetic!!.indentLevel)
    }

    @Test
    fun `right-aligned poetic line produces rightAligned=true PoeticLine`() {
        val input = """<para style="qr">Selah</para>"""
        val nodes = testRender(input)
        val poetic = nodes.filterIsInstance<RenderNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertTrue(poetic!!.rightAligned)
    }

    @Test
    fun `right-aligned poetic line has rightAligned flag`() {
        val input = """<para style="qr">Selah</para>"""
        val nodes = testRender(input)
        val poetic = nodes.filterIsInstance<RenderNode.PoeticLine>().firstOrNull()
        assertNotNull(poetic)
        assertTrue("Expected rightAligned=true", poetic!!.rightAligned)
    }

    @Test
    fun `Selah char tag inside paragraph is rendered`() {
        val input = """<para style="q1">Test text <char style="qs">Selah</char></para>"""
        val nodes = testRender(input)
        // After rendering, we should have a LineBreak followed by a right-aligned PoeticLine with "Selah"
        val poeticLines = nodes.filterIsInstance<RenderNode.PoeticLine>()
        assertTrue("Expected at least one PoeticLine node for Selah", poeticLines.isNotEmpty())
        val selahLine = poeticLines.lastOrNull()
        assertNotNull(selahLine)
        assertTrue("Selah line should be right-aligned", selahLine!!.rightAligned)
        val selahText = selahLine.children.filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertEquals("Selah", selahText)
    }

    @Test
    fun `chapter label produces ChapterLabel node`() {
        val input = """<para style="cl">Chapter One</para>"""
        val nodes = testRender(input)
        val label = nodes.filterIsInstance<RenderNode.ChapterLabel>().firstOrNull()
        assertNotNull(label)
        assertEquals("Chapter One", label!!.text)
    }

    @Test
    fun `mixed content produces correct node sequence`() {
        // verse + text + blank line
        val input = """<verse number="1" style="v" />text<para style="b"/>more text"""
        val nodes = testRender(input)
        assertTrue(nodes.any { it is RenderNode.Verse })
        assertTrue(nodes.any { it is RenderNode.LineBreak })
        val textContent = nodes.filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertTrue(textContent.contains("text"))
        assertTrue(textContent.contains("more text"))
    }

    @Test
    fun `no-arg constructor works without Context`() {
        val r = USXRenderer()
        assertNotNull(r)
        // Calling render does not throw due to missing context
        val nodes = r.render("hello")
        assertFalse(nodes.isEmpty())
    }

    @Test
    fun `getLeadingMajorSectionHeading returns heading when leading`() {
        val input = """<para style="ms">GENESIS</para> rest"""
        val heading = renderer().getLeadingMajorSectionHeading(input)
        assertEquals("GENESIS", heading)
    }

    @Test
    fun `getLeadingMajorSectionHeading returns empty when not leading`() {
        val input = """some text <para style="ms">GENESIS</para>"""
        val heading = renderer().getLeadingMajorSectionHeading(input)
        assertEquals("", heading)
    }

    @Test
    fun `note tag produces Note node`() {
        val input = """<note style="f" caller="+"><char style="fr">1:1 </char><char style="ft">footnote text</char></note>"""
        val nodes = testRender(input)
        val note = nodes.filterIsInstance<RenderNode.Note>().firstOrNull()
        assertNotNull("Expected Note node", note)
        assertEquals("+", note!!.caller)
        assertEquals(NoteStyle.FOOTNOTE, note.noteStyle)
    }

    @Test
    fun `char tag inside note span is not emitted as separate node`() {
        // The <char style="ft"> inside a <note> should be consumed by the note parser,
        // not emitted as a separate Text node by stripRemainingMarkers.
        val input = """<note style="f" caller="+"><char style="ft">note text</char></note>"""
        val nodes = testRender(input)
        // Should have exactly one Note, no stray text from char tag markup
        val noteNodes = nodes.filterIsInstance<RenderNode.Note>()
        assertEquals(1, noteNodes.size)
        // No text node should contain raw "<char" markup
        val textNodes = nodes.filterIsInstance<RenderNode.Text>()
        assertFalse("No stray <char> markup in text nodes",
            textNodes.any { it.content.contains("<char") })
    }

    @Test
    fun `isAddedMissingVerse is false when no verse is missing`() {
        val input = """<verse number="1" style="v" />text"""
        val r = renderer()
        r.setPopulateVerseMarkers(intArrayOf(1)) // verse 1 is present
        r.render(input)
        assertFalse("isAddedMissingVerse should be false when verse is present", r.isAddedMissingVerse)
    }

    @Test
    fun `verse markers are pinned when verseDisplay is PIN`() {
        val renderer = USXRenderer(verseDisplay = VerseDisplay.PIN)
        val nodes = renderer.render("<verse number=\"1\" style=\"v\" />In the beginning.")
        val verse = nodes.filterIsInstance<RenderNode.Verse>().firstOrNull()
        assertNotNull("Expected a Verse node", verse)
        assertTrue("Expected pinned=true when verseDisplay=PIN", verse!!.pinned)
    }

    @Test
    fun `verse markers are not pinned when verseDisplay is NUMBER`() {
        val renderer = USXRenderer(verseDisplay = VerseDisplay.NUMBER)
        val nodes = renderer.render("<verse number=\"1\" style=\"v\" />In the beginning.")
        val verse = nodes.filterIsInstance<RenderNode.Verse>().firstOrNull()
        assertNotNull(verse)
        assertFalse("Expected pinned=false when verseDisplay=NUMBER", verse!!.pinned)
    }

    @Test
    fun `bare verse markers between poetry blocks are wrapped in implicit Paragraphs`() {
        // Actual USX format: verse markers appear OUTSIDE <para style="q"> tags.
        // The </para> close token creates a block boundary, so orphan verses after
        // a closing tag are wrapped in implicit Paragraphs.
        val input = """<verse number="9" style="v" />
Therefore pray like this:
<para style="b"/>
<para style="q1">
'Our Father in heaven,
</para>
<para style="q2">
may your name be honored as holy.
</para>
<verse number="10" style="v" />
May your kingdom come.
<para style="q1">
May your will be done
</para>
<para style="q2">
on earth as it is in heaven.
</para>
<verse number="11" style="v" />
Give us today our daily bread.
<verse number="12" style="v" />
Forgive us our debts,
<para style="q2">
as we also have forgiven our debtors.
</para>
<verse number="13" style="v" />
Do not bring us into temptation,
<para style="q2">
but deliver us from the evil one.'
</para>"""

        val nodes = testRender(input)

        // Verse 9 is an orphan at top level (before any block node)
        assertTrue("Verse 9 should be at top level",
            nodes.any { it is RenderNode.Verse && it.startVerse == 9 })

        // Helper to find verses inside block node children
        fun findVerseInBlockChildren(verseNum: Int): Boolean =
            nodes.any { block ->
                val children = when (block) {
                    is RenderNode.Paragraph -> block.children
                    is RenderNode.PoeticLine -> block.children
                    else -> emptyList()
                }
                children.any { it is RenderNode.Verse && it.startVerse == verseNum }
            }

        // Verses 10, 11, 12, 13 should be inside block node children
        // (wrapped in implicit Paragraphs from </para> close tokens)
        for (verseNum in listOf(10, 11, 12, 13)) {
            assertTrue("Verse $verseNum should be inside a block node's children",
                findVerseInBlockChildren(verseNum))
        }
    }

    @Test
    fun `tree structure groups verses into correct block nodes`() {
        // Tests that buildTree + </para> tokenization correctly groups verses.
        // Verse 2, 3 are orphans (before any block). Verse 4 is wrapped in an
        // implicit Paragraph (from </para> after q2). Verse 11 is wrapped in a
        // Paragraph (from </para> after <para style="p">).
        val input = """<verse number="2" style="v" />
He opened his mouth and taught them, saying,
<para style="b"/>
<verse number="3" style="v" />
"Blessed are the poor in spirit,
<para style="q2">
for theirs is the kingdom of heaven.
</para>
<verse number="4" style="v" />
Blessed are those who mourn,
<para style="q2">
for they will be comforted.
</para>
<para style="p">
</para>
<verse number="11" style="v" />
"Blessed are you when people insult you"""

        val nodes = testRender(input)

        // Verse 2 is at top level (orphan before any block)
        assertTrue("Verse 2 should be at top level",
            nodes.any { it is RenderNode.Verse && it.startVerse == 2 })

        // Verse 3 is at top level (orphan before first block)
        assertTrue("Verse 3 should be at top level",
            nodes.any { it is RenderNode.Verse && it.startVerse == 3 })

        // Verse 4 is inside a Paragraph (implicit, from </para> close token)
        val verse4InParagraph = nodes.filterIsInstance<RenderNode.Paragraph>().any { p ->
            p.children.any { it is RenderNode.Verse && it.startVerse == 4 }
        }
        assertTrue("Verse 4 should be inside a Paragraph's children", verse4InParagraph)

        // Verse 11 is inside a Paragraph (from </para> after <para style="p">)
        val verse11InParagraph = nodes.filterIsInstance<RenderNode.Paragraph>().any { p ->
            p.children.any { it is RenderNode.Verse && it.startVerse == 11 }
        }
        assertTrue("Verse 11 should be inside a Paragraph's children", verse11InParagraph)
    }

    @Test
    fun `missing verse marker is pinned when verseDisplay is PIN`() {
        // USX with verse 2 present but verse 1 missing; expected range includes verse 1
        val renderer = USXRenderer(verseDisplay = VerseDisplay.PIN)
        // Set expected verse range to include verse 1 even though only verse 2 is in the text
        renderer.setPopulateVerseMarkers(intArrayOf(1, 2))
        val nodes = renderer.render("<para style=\"p\"><verse number=\"2\" style=\"v\" />Second verse.</para>")
        val missing = nodes.filterIsInstance<RenderNode.Verse>().firstOrNull { it.startVerse == 1 }
        assertNotNull("Expected verse 1 to be inserted as missing", missing)
        assertTrue("Expected missing verse marker to be pinned when verseDisplay=PIN", missing!!.pinned)
    }

}
