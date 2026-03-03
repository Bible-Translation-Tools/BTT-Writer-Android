package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.NodeStyle
import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.TextNode
import org.junit.Assert.*
import org.junit.Test

class TextNodeTest {

    @Test
    fun `Text node holds content`() {
        val node = TextNode.Text("hello")
        assertEquals("hello", node.content)
    }

    @Test
    fun `VerseMarker holds verse range`() {
        val node = TextNode.VerseMarker(startVerse = 3, endVerse = 5, pinned = false)
        assertEquals(3, node.startVerse)
        assertEquals(5, node.endVerse)
        assertFalse(node.pinned)
    }

    @Test
    fun `VerseMarker single verse has endVerse zero`() {
        val node = TextNode.VerseMarker(startVerse = 1, endVerse = 0, pinned = false)
        assertEquals(1, node.startVerse)
        assertEquals(0, node.endVerse)
    }

    @Test
    fun `NoteMarker holds note data`() {
        val node = TextNode.NoteMarker(
            caller = "+",
            passage = "In the beginning",
            notes = "footnote text",
            noteStyle = NoteStyle.FOOTNOTE
        )
        assertEquals("+", node.caller)
        assertEquals("footnote text", node.notes)
        assertEquals(NoteStyle.FOOTNOTE, node.noteStyle)
        assertFalse(node.highlighted)
    }

    @Test
    fun `SectionHeading isMajor flag works`() {
        val minor = TextNode.SectionHeading("The Beginning", isMajor = false)
        val major = TextNode.SectionHeading("CREATION", isMajor = true)
        assertFalse(minor.isMajor)
        assertTrue(major.isMajor)
    }

    @Test
    fun `Link holds LinkData_Article`() {
        val data = LinkData.Article(address = "en:ta:vol1:translate", title = "Figures of Speech")
        val node = TextNode.Link(data)
        assertTrue(node.linkData is LinkData.Article)
        assertEquals("en:ta:vol1:translate", (node.linkData as LinkData.Article).address)
    }

    @Test
    fun `Link holds LinkData_TranslationWord`() {
        val data = LinkData.TranslationWord(id = "grace")
        val node = TextNode.Link(data)
        assertTrue(node.linkData is LinkData.TranslationWord)
        assertEquals("grace", (node.linkData as LinkData.TranslationWord).id)
    }

    @Test
    fun `SearchHighlight holds content`() {
        val node = TextNode.SearchHighlight("beginning")
        assertEquals("beginning", node.content)
    }

    @Test
    fun `Styled node holds style`() {
        val node = TextNode.Styled("bold text", NodeStyle.BOLD)
        assertEquals(NodeStyle.BOLD, node.style)
        assertEquals("bold text", node.content)
    }

    @Test
    fun `PoeticLine holds indent level and alignment`() {
        val line = TextNode.PoeticLine("Praise the Lord", indentLevel = 2, rightAligned = false)
        assertEquals(2, line.indentLevel)
        assertFalse(line.rightAligned)
    }

    @Test
    fun `Paragraph indented flag`() {
        val plain = TextNode.Paragraph(indented = false)
        val indented = TextNode.Paragraph(indented = true)
        assertFalse(plain.indented)
        assertTrue(indented.indented)
    }

    @Test
    fun `node list can be filtered by type`() {
        val nodes: List<TextNode> = listOf(
            TextNode.Text("In the beginning "),
            TextNode.VerseMarker(1, 0, false),
            TextNode.Text("God created"),
            TextNode.VerseMarker(2, 0, false),
            TextNode.SearchHighlight("created")
        )
        val verses = nodes.filterIsInstance<TextNode.VerseMarker>()
        val highlights = nodes.filterIsInstance<TextNode.SearchHighlight>()
        assertEquals(2, verses.size)
        assertEquals(1, verses[0].startVerse)
        assertEquals(2, verses[1].startVerse)
        assertEquals(1, highlights.size)
        assertEquals("created", highlights[0].content)
    }

    @Test
    fun `BlankLine and LineBreak are singletons`() {
        assertSame(TextNode.BlankLine, TextNode.BlankLine)
        assertSame(TextNode.LineBreak, TextNode.LineBreak)
    }

    @Test
    fun `Link holds LinkData_Passage`() {
        val data = LinkData.Passage(address = "1:1", title = "Genesis 1:1")
        val node = TextNode.Link(data)
        assertTrue(node.linkData is LinkData.Passage)
        assertEquals("1:1", (node.linkData as LinkData.Passage).address)
        assertEquals("Genesis 1:1", (node.linkData as LinkData.Passage).title)
    }

    @Test
    fun `Link holds LinkData_Markdown`() {
        val data = LinkData.Markdown(address = "http://example.com", title = "Example")
        val node = TextNode.Link(data)
        assertTrue(node.linkData is LinkData.Markdown)
        assertEquals("Example", (node.linkData as LinkData.Markdown).title)
    }

    @Test
    fun `Link holds LinkData_ShortReference`() {
        val data = LinkData.ShortReference(ref = "3:16")
        val node = TextNode.Link(data)
        assertTrue(node.linkData is LinkData.ShortReference)
        assertEquals("3:16", (node.linkData as LinkData.ShortReference).ref)
    }

    @Test
    fun `Link holds LinkData_AppLink`() {
        val data = LinkData.AppLink(href = "/ta/figs", linkType = "ta", title = "Figures of Speech")
        val node = TextNode.Link(data)
        assertTrue(node.linkData is LinkData.AppLink)
        assertEquals("ta", (node.linkData as LinkData.AppLink).linkType)
    }

    @Test
    fun `sealed class exhaustive when expression compiles`() {
        val node: TextNode = TextNode.Text("test")
        val result = when (node) {
            is TextNode.Text -> "text"
            is TextNode.Styled -> "styled"
            is TextNode.VerseMarker -> "verse"
            is TextNode.NoteMarker -> "note"
            is TextNode.Paragraph -> "paragraph"
            TextNode.BlankLine -> "blank"
            is TextNode.SectionHeading -> "heading"
            is TextNode.PoeticLine -> "poetic"
            is TextNode.ChapterLabel -> "chapter"
            is TextNode.Link -> "link"
            is TextNode.SearchHighlight -> "highlight"
            TextNode.LineBreak -> "linebreak"
        }
        assertEquals("text", result)
    }
}
