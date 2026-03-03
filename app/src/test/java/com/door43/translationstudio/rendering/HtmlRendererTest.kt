package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.ui.spannables.Span
import org.junit.Assert.*
import org.junit.Test

class HtmlRendererTest {

    private fun renderer(allowAll: Boolean = true) =
        HtmlRenderer(preprocessCallback = { allowAll })

    // --- Translation Academy address ---

    @Test
    fun `TA address pattern produces Article link node`() {
        val input = "[[en:ta:vol1:translate:figs_intro | Figures of Speech]]"
        val nodes = renderer().renderToNodes(input)
        val link = nodes.filterIsInstance<TextNode.Link>().firstOrNull()
        assertNotNull("Expected Link node", link)
        assertTrue(link!!.linkData is LinkData.Article)
    }

    @Test
    fun `TA address title is preserved in Article link`() {
        val input = "[[en:ta:vol1:translate:figs_intro | Figures of Speech]]"
        val nodes = renderer().renderToNodes(input)
        val link = nodes.filterIsInstance<TextNode.Link>().firstOrNull()
        assertNotNull(link)
        assertEquals("Figures of Speech", (link!!.linkData as LinkData.Article).title.trim())
    }

    @Test
    fun `TA address address is preserved in Article link`() {
        val input = "[[en:ta:vol1:translate:figs_intro | Figures of Speech]]"
        val nodes = renderer().renderToNodes(input)
        val link = nodes.filterIsInstance<TextNode.Link>().firstOrNull()
        assertNotNull(link)
        // ArticleLinkSpan.parse() replaces underscores with dashes in the slug,
        // so "figs_intro" becomes "figs-intro" in machineReadable.
        val address = (link!!.linkData as LinkData.Article).address
        assertTrue("Address should contain 'figs'", address.contains("figs"))
        assertTrue("Address should contain 'translate'", address.contains("translate"))
    }

    @Test
    fun `TA address rejected by preprocessor renders as plain text`() {
        val input = "[[en:ta:vol1:translate:figs_intro | Figures of Speech]]"
        val nodes = renderer(allowAll = false).renderToNodes(input)
        assertFalse("No Link when preprocessor rejects", nodes.any { it is TextNode.Link })
        val text = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertTrue("Rejected link shows as plain text", text.isNotEmpty())
    }

    // --- Markdown titled link ---

    @Test
    fun `markdown titled link produces Markdown link node`() {
        val input = "[My Title](http://example.com)"
        val nodes = renderer().renderToNodes(input)
        val link = nodes.filterIsInstance<TextNode.Link>().firstOrNull()
        assertNotNull("Expected Link node for markdown link", link)
        assertTrue(link!!.linkData is LinkData.Markdown)
    }

    @Test
    fun `markdown link title is preserved`() {
        val input = "[My Title](http://example.com)"
        val nodes = renderer().renderToNodes(input)
        val link = nodes.filterIsInstance<TextNode.Link>().first()
        assertEquals("My Title", (link.linkData as LinkData.Markdown).title)
    }

    @Test
    fun `markdown link address is preserved`() {
        val input = "[My Title](http://example.com)"
        val nodes = renderer().renderToNodes(input)
        val link = nodes.filterIsInstance<TextNode.Link>().first()
        assertEquals("http://example.com", (link.linkData as LinkData.Markdown).address)
    }

    // --- Plain text ---

    @Test
    fun `plain text with no links passes through as Text node`() {
        val input = "Simple text with no links."
        val nodes = renderer().renderToNodes(input)
        assertFalse(nodes.any { it is TextNode.Link })
        val text = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertTrue(text.contains("Simple text"))
    }

    @Test
    fun `empty input produces empty list`() {
        val nodes = renderer().renderToNodes("")
        assertTrue(nodes.isEmpty())
    }

    // --- Mixed content ---

    @Test
    fun `text before and after link is preserved`() {
        val input = "Before [[en:ta:vol1:translate:figs_intro | Title]] after"
        val nodes = renderer().renderToNodes(input)
        val texts = nodes.filterIsInstance<TextNode.Text>().map { it.content }
        assertTrue("Text before link preserved", texts.any { it.contains("Before") })
        assertTrue("Text after link preserved", texts.any { it.contains("after") })
        assertTrue("Link node present", nodes.any { it is TextNode.Link })
    }

    @Test
    fun `multiple different link types in same input`() {
        val input = "See [[en:ta:vol1:translate:figs_intro | TA Article]] and [Markdown](http://x.com)"
        val nodes = renderer().renderToNodes(input)
        val links = nodes.filterIsInstance<TextNode.Link>()
        assertEquals("Expected 2 links", 2, links.size)
        assertTrue(links.any { it.linkData is LinkData.Article })
        assertTrue(links.any { it.linkData is LinkData.Markdown })
    }

    // --- No-arg / context-free constructor ---

    @Test
    fun `renderer can be constructed without Context`() {
        val r = HtmlRenderer(preprocessCallback = { true })
        assertNotNull(r)
        val nodes = r.renderToNodes("hello")
        assertTrue(nodes.isNotEmpty())
    }

    @Test
    fun `preprocessor rejecting TW link falls back to plain text`() {
        val input = "[[en:obe:other:word]]"
        val nodes = HtmlRenderer(preprocessCallback = { false }).renderToNodes(input)
        // Should not produce a Link node; the word ID or raw text should appear
        assertTrue("Expected no Link node", nodes.none { it is TextNode.Link })
    }

    @Test
    fun `preprocessor rejecting TA address falls back to plain text`() {
        val input = "[[en:ta:vol1:translate:figs_intro | Figures of Speech]]"
        val nodes = HtmlRenderer(preprocessCallback = { false }).renderToNodes(input)
        assertTrue("Expected no Link node", nodes.none { it is TextNode.Link })
        val text = nodes.filterIsInstance<TextNode.Text>().joinToString("") { it.content }
        assertTrue("Expected title text in output", text.contains("Figures of Speech"))
    }
}
