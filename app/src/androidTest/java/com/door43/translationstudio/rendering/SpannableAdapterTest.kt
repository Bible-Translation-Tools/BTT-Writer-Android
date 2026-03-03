package com.door43.translationstudio.rendering

import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.rendering.adapter.SpannableAdapter
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.NodeStyle
import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.TextNode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpannableAdapterTest {

    @Test
    fun plain_text_node_produces_matching_string() {
        val nodes = listOf(TextNode.Text("hello world"))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("hello world", result.toString())
    }

    @Test
    fun multiple_text_nodes_are_concatenated() {
        val nodes = listOf(
            TextNode.Text("foo"),
            TextNode.Text("bar")
        )
        val result = SpannableAdapter.convert(nodes)
        assertEquals("foobar", result.toString())
    }

    @Test
    fun verse_marker_single_verse_produces_number() {
        val nodes = listOf(TextNode.VerseMarker(3, 0, pinned = false))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("3", result.toString())
    }

    @Test
    fun verse_marker_range_produces_hyphenated_label() {
        val nodes = listOf(TextNode.VerseMarker(3, 5, pinned = false))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("3-5", result.toString())
    }

    @Test
    fun line_break_node_produces_newline() {
        val nodes = listOf(TextNode.Text("a"), TextNode.LineBreak, TextNode.Text("b"))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("a\nb", result.toString())
    }

    @Test
    fun blank_line_produces_double_newline() {
        val nodes = listOf(TextNode.Text("a"), TextNode.BlankLine, TextNode.Text("b"))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("a\n\nb", result.toString())
    }

    @Test
    fun paragraph_indented_produces_newline_plus_spaces() {
        val nodes = listOf(TextNode.Paragraph(indented = true))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("\n    ", result.toString())
    }

    @Test
    fun paragraph_unindented_produces_newline() {
        val nodes = listOf(TextNode.Paragraph(indented = false))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("\n", result.toString())
    }

    @Test
    fun section_heading_appends_newline_after_text() {
        val nodes = listOf(TextNode.SectionHeading("The Beginning", isMajor = false))
        val result = SpannableAdapter.convert(nodes)
        assertTrue(result.toString().startsWith("The Beginning"))
        assertTrue(result.toString().endsWith("\n"))
    }

    @Test
    fun major_section_heading_text_is_uppercased() {
        val nodes = listOf(TextNode.SectionHeading("creation", isMajor = true))
        val result = SpannableAdapter.convert(nodes)
        assertTrue(result.toString().startsWith("CREATION"))
    }

    @Test
    fun search_highlight_node_produces_correct_text() {
        val nodes = listOf(
            TextNode.Text("In the "),
            TextNode.SearchHighlight("beginning"),
            TextNode.Text(" God")
        )
        val result = SpannableAdapter.convert(nodes, searchHighlightColor = 0xFF0000FF.toInt())
        assertEquals("In the beginning God", result.toString())
    }

    @Test
    fun search_highlight_applies_background_color_span() {
        val color = 0xFF0000FF.toInt()
        val nodes = listOf(TextNode.SearchHighlight("hello"))
        val result = SpannableAdapter.convert(nodes, searchHighlightColor = color)
        val spans = result.getSpans(0, result.length, BackgroundColorSpan::class.java)
        assertTrue("Expected BackgroundColorSpan", spans.isNotEmpty())
        assertEquals(color, spans[0].backgroundColor)
    }

    @Test
    fun no_highlight_when_color_is_zero() {
        val nodes = listOf(TextNode.SearchHighlight("hello"))
        val result = SpannableAdapter.convert(nodes, searchHighlightColor = 0)
        val spans = result.getSpans(0, result.length, BackgroundColorSpan::class.java)
        assertTrue("Expected no BackgroundColorSpan when color=0", spans.isEmpty())
    }

    @Test
    fun styled_bold_node_gets_bold_style_span_over_full_range() {
        val nodes = listOf(TextNode.Styled("bold text", NodeStyle.BOLD))
        val result = SpannableAdapter.convert(nodes)
        val spans = result.getSpans(0, result.length, StyleSpan::class.java)
        assertTrue(spans.isNotEmpty())
        assertEquals(android.graphics.Typeface.BOLD, spans[0].style)
        assertEquals(0, result.getSpanStart(spans[0]))
        assertEquals(result.length, result.getSpanEnd(spans[0]))
    }

    @Test
    fun chapter_label_text_is_preserved() {
        val nodes = listOf(TextNode.ChapterLabel("Chapter 1"))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("Chapter 1", result.toString())
    }

    @Test
    fun link_article_renders_title() {
        val nodes = listOf(TextNode.Link(LinkData.Article("en:ta:vol1", "Figures of Speech")))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("Figures of Speech", result.toString())
    }

    @Test
    fun link_translation_word_renders_id() {
        val nodes = listOf(TextNode.Link(LinkData.TranslationWord("grace")))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("grace", result.toString())
    }

    @Test
    fun note_marker_renders_passage_when_present() {
        val nodes = listOf(
            TextNode.NoteMarker(
                caller = "+",
                passage = "In the beginning",
                notes = "footnote",
                noteStyle = NoteStyle.FOOTNOTE
            )
        )
        val result = SpannableAdapter.convert(nodes)
        assertEquals("In the beginning", result.toString())
    }

    @Test
    fun note_marker_renders_caller_when_passage_empty() {
        val nodes = listOf(
            TextNode.NoteMarker(
                caller = "+",
                passage = "",
                notes = "footnote",
                noteStyle = NoteStyle.FOOTNOTE
            )
        )
        val result = SpannableAdapter.convert(nodes)
        assertEquals("+", result.toString())
    }

    @Test
    fun poetic_line_with_indent_level_2_has_correct_padding() {
        val nodes = listOf(TextNode.PoeticLine("Praise the Lord", indentLevel = 2))
        val result = SpannableAdapter.convert(nodes)
        assertTrue(result.toString().startsWith("    ".repeat(2)))
        assertTrue(result.toString().contains("Praise the Lord"))
    }

    @Test
    fun empty_node_list_produces_empty_spannable() {
        val result = SpannableAdapter.convert(emptyList())
        assertEquals(0, result.length)
    }

    // Fix 7: highlighted NoteMarker tests

    @Test
    fun note_marker_highlighted_applies_search_color_span() {
        val color = 0xFF00FF00.toInt() // green
        val nodes = listOf(
            TextNode.NoteMarker(
                caller = "+",
                passage = "passage text",
                notes = "note body",
                noteStyle = NoteStyle.FOOTNOTE,
                highlighted = true
            )
        )
        val result = SpannableAdapter.convert(nodes, searchHighlightColor = color)
        val spans = result.getSpans(0, result.length, BackgroundColorSpan::class.java)
        val hasHighlight = spans.any { it.backgroundColor == color }
        assertTrue("Expected search highlight BackgroundColorSpan", hasHighlight)
    }

    @Test
    fun note_marker_not_highlighted_does_not_apply_search_color_span() {
        val color = 0xFF00FF00.toInt()
        val nodes = listOf(
            TextNode.NoteMarker(
                caller = "+",
                passage = "passage text",
                notes = "note body",
                noteStyle = NoteStyle.FOOTNOTE,
                highlighted = false
            )
        )
        val result = SpannableAdapter.convert(nodes, searchHighlightColor = color)
        val spans = result.getSpans(0, result.length, BackgroundColorSpan::class.java)
        val hasHighlight = spans.any { it.backgroundColor == color }
        assertFalse("Unhighlighted note should not have search color span", hasHighlight)
    }

    // Fix 8: span tests for SectionHeading, ChapterLabel, and right-aligned PoeticLine

    @Test
    fun section_heading_has_bold_and_center_alignment_spans() {
        val nodes = listOf(TextNode.SectionHeading("The Beginning", isMajor = false))
        val result = SpannableAdapter.convert(nodes)
        val headingEnd = result.toString().indexOf('\n')
        val boldSpans = result.getSpans(0, headingEnd, StyleSpan::class.java)
        val alignSpans = result.getSpans(0, headingEnd, AlignmentSpan::class.java)
        assertTrue("Expected bold StyleSpan on heading", boldSpans.any { it.style == android.graphics.Typeface.BOLD })
        assertTrue("Expected center AlignmentSpan on heading", alignSpans.isNotEmpty())
    }

    @Test
    fun chapter_label_has_bold_span() {
        val nodes = listOf(TextNode.ChapterLabel("Chapter 1"))
        val result = SpannableAdapter.convert(nodes)
        val spans = result.getSpans(0, result.length, StyleSpan::class.java)
        assertTrue("Expected bold StyleSpan on chapter label", spans.any { it.style == android.graphics.Typeface.BOLD })
    }

    @Test
    fun right_aligned_poetic_line_has_italic_and_alignment_spans() {
        val nodes = listOf(TextNode.PoeticLine("Selah", indentLevel = 0, rightAligned = true))
        val result = SpannableAdapter.convert(nodes)
        val styleSpans = result.getSpans(0, result.length, StyleSpan::class.java)
        val alignSpans = result.getSpans(0, result.length, AlignmentSpan::class.java)
        assertTrue("Expected italic StyleSpan on right-aligned line", styleSpans.any { it.style == android.graphics.Typeface.ITALIC })
        assertTrue("Expected AlignmentSpan on right-aligned line", alignSpans.isNotEmpty())
    }

    // Fix 9: remaining LinkData subtype tests

    @Test
    fun link_passage_renders_title() {
        val nodes = listOf(TextNode.Link(LinkData.Passage("1:1", "Genesis 1:1")))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("Genesis 1:1", result.toString())
    }

    @Test
    fun link_markdown_renders_title() {
        val nodes = listOf(TextNode.Link(LinkData.Markdown("http://example.com", "Example")))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("Example", result.toString())
    }

    @Test
    fun link_short_reference_renders_ref() {
        val nodes = listOf(TextNode.Link(LinkData.ShortReference("3:16")))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("3:16", result.toString())
    }

    @Test
    fun link_app_link_renders_title() {
        val nodes = listOf(TextNode.Link(LinkData.AppLink("/ta/figs", "ta", "Figures of Speech")))
        val result = SpannableAdapter.convert(nodes)
        assertEquals("Figures of Speech", result.toString())
    }
}
