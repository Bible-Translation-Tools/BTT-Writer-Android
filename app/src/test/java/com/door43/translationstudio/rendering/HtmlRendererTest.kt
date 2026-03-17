package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.LinkData
import org.junit.Assert.*
import org.junit.Test

class HtmlRendererTest {

    private fun renderer(allowAll: Boolean = true) =
        HtmlRenderer(preprocessCallback = { allowAll })

    // --- Translation Academy address ---

    @Test
    fun `TA address pattern produces anchor tag`() {
        val input = "[[en:ta:vol1:translate:figs_intro | Figures of Speech]]"
        val html = renderer().toAnnotatedHtml(input)
        assertTrue("Expected <a> tag", html.contains("<a href=\"app://ta/"))
        assertTrue("Expected title in anchor", html.contains("Figures of Speech"))
    }

    @Test
    fun `TA address rejected by preprocessor renders as plain text`() {
        val input = "[[en:ta:vol1:translate:figs_intro | Figures of Speech]]"
        val html = renderer(false).toAnnotatedHtml(input)
        assertFalse("No <a> tag when preprocessor rejects", html.contains("<a "))
        assertTrue("Rejected link shows title as plain text", html.contains("Figures of Speech"))
    }

    // --- Markdown titled link ---

    @Test
    fun `markdown titled link produces anchor tag`() {
        val input = "[My Title](http://example.com)"
        val html = renderer().toAnnotatedHtml(input)
        assertTrue("Expected <a> tag", html.contains("<a href=\"app://md/"))
        assertTrue("Expected title", html.contains("My Title"))
    }

    @Test
    fun `markdown link rejected by preprocessor renders as plain text`() {
        val input = "[My Title](http://example.com)"
        val html = renderer(false).toAnnotatedHtml(input)
        assertFalse("No <a> tag", html.contains("<a "))
        assertTrue("Shows title as plain text", html.contains("My Title"))
    }

    // --- Passage link ---

    @Test
    fun `passage link produces anchor tag`() {
        val input = "[[en:bible:notes:gen:01:02|Genesis 1:2]]"
        val html = renderer().toAnnotatedHtml(input)
        assertTrue("Expected <a> tag", html.contains("<a href=\"app://passage/"))
    }

    // --- Translation Word link ---

    @Test
    fun `TW link produces anchor tag`() {
        val input = "[[:en:obe:other:assign]]"
        val html = renderer().toAnnotatedHtml(input)
        assertTrue("Expected <a> tag with tw scheme", html.contains("<a href=\"app://tw/"))
    }

    @Test
    fun `TW link rejected by preprocessor renders word id`() {
        val input = "[[:en:obe:other:assign]]"
        val html = renderer(false).toAnnotatedHtml(input)
        assertFalse("No <a> tag", html.contains("<a "))
        assertTrue("Shows word id", html.contains("assign"))
    }

    // --- Relative .md word link ---

    @Test
    fun `relative md word link uses tw scheme with extracted id`() {
        val input = "[altar of incense](../other/altarofincense.md)"
        val html = renderer().toAnnotatedHtml(input)
        assertTrue("Expected tw scheme", html.contains("<a href=\"app://tw/altarofincense\">"))
        assertTrue("Expected title", html.contains("altar of incense"))
    }

    @Test
    fun `parseLinkUrl parses tw from md word link`() {
        val data = HtmlRenderer.parseLinkUrl("app://tw/altarofincense")
        assertTrue(data is LinkData.TranslationWord)
        assertEquals("altarofincense", (data as LinkData.TranslationWord).id)
    }

    // --- RC link ---

    @Test
    fun `rc link uses rc scheme`() {
        val input = "[Genesis 8:20](rc://en/tn/help/gen/08/20)"
        val html = renderer().toAnnotatedHtml(input)
        assertTrue("Expected rc scheme", html.contains("<a href=\"app://rc/"))
        assertTrue("Expected title", html.contains("Genesis 8:20"))
    }

    @Test
    fun `parseLinkUrl parses rc link`() {
        val data = HtmlRenderer.parseLinkUrl("app://rc/rc://en/tn/help/gen/08/20")
        assertTrue(data is LinkData.RcLink)
    }

    // --- Plain text ---

    @Test
    fun `plain text with no links passes through unchanged`() {
        val input = "Simple text with no links."
        val html = renderer().toAnnotatedHtml(input)
        assertEquals(input, html)
    }

    @Test
    fun `empty input returns empty string`() {
        val html = renderer().toAnnotatedHtml("")
        assertEquals("", html)
    }

    // --- Mixed content ---

    @Test
    fun `text before and after link is preserved`() {
        val input = "Before [[en:ta:vol1:translate:figs_intro | Title]] after"
        val html = renderer().toAnnotatedHtml(input)
        assertTrue("Text before link preserved", html.contains("Before"))
        assertTrue("Text after link preserved", html.contains("after"))
        assertTrue("Link present", html.contains("<a "))
    }

    @Test
    fun `multiple different link types in same input`() {
        val input = "See [[en:ta:vol1:translate:figs_intro | TA Article]] and [Markdown](http://x.com)"
        val html = renderer().toAnnotatedHtml(input)
        assertTrue("TA link present", html.contains("app://ta/"))
        assertTrue("Markdown link present", html.contains("app://md/"))
    }

    // --- HTML preservation ---

    @Test
    fun `existing HTML tags are preserved`() {
        val input = "<p>See <b>this</b> [[en:ta:vol1:translate:figs_intro | link]]</p>"
        val html = renderer().toAnnotatedHtml(input)
        assertTrue("p tag preserved", html.contains("<p>"))
        assertTrue("b tag preserved", html.contains("<b>"))
        assertTrue("Link converted", html.contains("<a href=\"app://ta/"))
    }

    @Test
    fun `HTML entities in link titles are escaped`() {
        val input = "[[en:ta:vol1:translate:figs_intro | A & B < C]]"
        val html = renderer().toAnnotatedHtml(input)
        assertTrue("Ampersand escaped", html.contains("&amp;"))
        assertTrue("Less-than escaped", html.contains("&lt;"))
    }

    // --- parseLinkUrl ---

    @Test
    fun `parseLinkUrl parses ta link`() {
        val data = HtmlRenderer.parseLinkUrl("app://ta/translate/figs-intro")
        assertTrue(data is LinkData.Article)
        assertEquals("translate/figs-intro", (data as LinkData.Article).address)
    }

    @Test
    fun `parseLinkUrl parses tw link`() {
        val data = HtmlRenderer.parseLinkUrl("app://tw/assign")
        assertTrue(data is LinkData.TranslationWord)
        assertEquals("assign", (data as LinkData.TranslationWord).id)
    }

    @Test
    fun `parseLinkUrl parses passage link`() {
        val data = HtmlRenderer.parseLinkUrl("app://passage/gen/01/02")
        assertTrue(data is LinkData.Passage)
        assertEquals("gen/01/02", (data as LinkData.Passage).address)
    }

    @Test
    fun `parseLinkUrl parses md link`() {
        val data = HtmlRenderer.parseLinkUrl("app://md/http://example.com")
        assertTrue(data is LinkData.Markdown)
    }

    @Test
    fun `parseLinkUrl parses ref link`() {
        val data = HtmlRenderer.parseLinkUrl("app://ref/1:2")
        assertTrue(data is LinkData.ShortReference)
        assertEquals("1:2", (data as LinkData.ShortReference).ref)
    }

    @Test
    fun `parseLinkUrl parses rc link`() {
        val data = HtmlRenderer.parseLinkUrl("app://rc/rc://en/tn/help/gen/08/20")
        assertTrue(data is LinkData.RcLink)
        assertEquals("rc://en/tn/help/gen/08/20", (data as LinkData.RcLink).address)
    }

    @Test
    fun `parseLinkUrl returns null for non-app urls`() {
        assertNull(HtmlRenderer.parseLinkUrl("http://example.com"))
    }

    @Test
    fun `parseLinkUrl returns null for unknown type`() {
        assertNull(HtmlRenderer.parseLinkUrl("app://unknown/data"))
    }

    @Test
    fun `parseLinkUrl decodes percent-encoded characters`() {
        val data = HtmlRenderer.parseLinkUrl("app://ta/translate%20intro")
        assertTrue(data is LinkData.Article)
        assertEquals("translate intro", (data as LinkData.Article).address)
    }
}
