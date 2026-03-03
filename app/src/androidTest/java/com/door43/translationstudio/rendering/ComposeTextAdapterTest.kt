package com.door43.translationstudio.rendering

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.rendering.adapter.ComposeTextAdapter
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.NodeStyle
import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.TextNode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeTextAdapterTest {

    // --- Basic output string correctness ---

    @Test
    fun empty_list_produces_empty_annotated_string() {
        val result = ComposeTextAdapter.convert(emptyList())
        assertEquals("", result.text)
    }

    @Test
    fun plain_text_node_produces_matching_string() {
        val nodes = listOf(TextNode.Text("hello world"))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("hello world", result.text)
    }

    @Test
    fun multiple_nodes_are_concatenated_in_order() {
        val nodes = listOf(
            TextNode.Text("foo"),
            TextNode.Text("bar"),
            TextNode.Text("baz")
        )
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("foobarbaz", result.text)
    }

    @Test
    fun line_break_produces_newline() {
        val nodes = listOf(TextNode.Text("a"), TextNode.LineBreak, TextNode.Text("b"))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("a\nb", result.text)
    }

    @Test
    fun blank_line_produces_double_newline() {
        val nodes = listOf(TextNode.Text("a"), TextNode.BlankLine, TextNode.Text("b"))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("a\n\nb", result.text)
    }

    @Test
    fun paragraph_indented_produces_newline_plus_spaces() {
        val nodes = listOf(TextNode.Paragraph(indented = true))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("\n    ", result.text)
    }

    @Test
    fun paragraph_unindented_produces_newline() {
        val nodes = listOf(TextNode.Paragraph(indented = false))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("\n", result.text)
    }

    // --- VerseMarker ---

    @Test
    fun verse_marker_single_verse_produces_number_string() {
        val nodes = listOf(TextNode.VerseMarker(3, 0, pinned = false))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("3", result.text)
    }

    @Test
    fun verse_marker_range_produces_hyphenated_label() {
        val nodes = listOf(TextNode.VerseMarker(3, 5, pinned = false))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("3-5", result.text)
    }

    @Test
    fun verse_marker_adds_VERSE_string_annotation() {
        val nodes = listOf(TextNode.VerseMarker(7, 0, pinned = false))
        val result = ComposeTextAdapter.convert(nodes)
        val annotations = result.getStringAnnotations(tag = "VERSE", start = 0, end = result.text.length)
        assertTrue("Expected VERSE annotation", annotations.isNotEmpty())
        assertEquals("7", annotations[0].item)
    }

    @Test
    fun verse_marker_range_annotation_value_matches_label() {
        val nodes = listOf(TextNode.VerseMarker(3, 5, pinned = false))
        val result = ComposeTextAdapter.convert(nodes)
        val annotations = result.getStringAnnotations(tag = "VERSE", start = 0, end = result.text.length)
        assertTrue("Expected VERSE annotation for range", annotations.isNotEmpty())
        assertEquals("3-5", annotations[0].item)
    }

    // --- NoteMarker ---

    @Test
    fun note_marker_caller_text_appears_in_output() {
        val nodes = listOf(
            TextNode.NoteMarker(
                caller = "+",
                passage = "some passage",
                notes = "footnote body",
                noteStyle = NoteStyle.FOOTNOTE
            )
        )
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("+", result.text)
    }

    @Test
    fun note_marker_adds_NOTE_string_annotation_with_notes_content() {
        val nodes = listOf(
            TextNode.NoteMarker(
                caller = "+",
                passage = "some passage",
                notes = "footnote body",
                noteStyle = NoteStyle.FOOTNOTE
            )
        )
        val result = ComposeTextAdapter.convert(nodes)
        val annotations = result.getStringAnnotations(tag = "NOTE", start = 0, end = result.text.length)
        assertTrue("Expected NOTE annotation", annotations.isNotEmpty())
        assertEquals("footnote body", annotations[0].item)
    }

    // --- SectionHeading ---

    @Test
    fun section_heading_appends_newline_after_text() {
        val nodes = listOf(TextNode.SectionHeading("The Beginning", isMajor = false))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(result.text.startsWith("The Beginning"))
        assertTrue(result.text.endsWith("\n"))
    }

    @Test
    fun major_section_heading_text_is_uppercased() {
        val nodes = listOf(TextNode.SectionHeading("creation", isMajor = true))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(result.text.startsWith("CREATION"))
    }

    @Test
    fun section_heading_has_bold_span() {
        val nodes = listOf(TextNode.SectionHeading("The Beginning", isMajor = false))
        val result = ComposeTextAdapter.convert(nodes)
        val headingEnd = result.text.indexOf('\n')
        val spans = result.spanStyles.filter { it.start < headingEnd && it.end <= headingEnd }
        assertTrue(
            "Expected bold SpanStyle on heading",
            spans.any { it.item.fontWeight == FontWeight.Bold }
        )
    }

    // --- ChapterLabel ---

    @Test
    fun chapter_label_text_is_preserved() {
        val nodes = listOf(TextNode.ChapterLabel("Chapter 1"))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("Chapter 1", result.text)
    }

    @Test
    fun chapter_label_has_bold_span() {
        val nodes = listOf(TextNode.ChapterLabel("Chapter 1"))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(
            "Expected bold SpanStyle on chapter label",
            result.spanStyles.any { it.item.fontWeight == FontWeight.Bold }
        )
    }

    // --- Styled nodes ---

    @Test
    fun styled_bold_node_text_is_preserved() {
        val nodes = listOf(TextNode.Styled("bold text", NodeStyle.BOLD))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("bold text", result.text)
    }

    @Test
    fun styled_bold_node_has_bold_span() {
        val nodes = listOf(TextNode.Styled("bold text", NodeStyle.BOLD))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(
            "Expected bold SpanStyle",
            result.spanStyles.any { it.item.fontWeight == FontWeight.Bold }
        )
    }

    @Test
    fun styled_italic_node_has_italic_span() {
        val nodes = listOf(TextNode.Styled("italic text", NodeStyle.ITALIC))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(
            "Expected italic SpanStyle",
            result.spanStyles.any { it.item.fontStyle == FontStyle.Italic }
        )
    }

    // --- SearchHighlight ---

    @Test
    fun search_highlight_text_appears_in_output() {
        val nodes = listOf(
            TextNode.Text("In the "),
            TextNode.SearchHighlight("beginning"),
            TextNode.Text(" God")
        )
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("In the beginning God", result.text)
    }

    @Test
    fun search_highlight_applies_background_color_span() {
        val highlightColor = Color.Yellow
        val nodes = listOf(TextNode.SearchHighlight("hello"))
        val result = ComposeTextAdapter.convert(nodes, searchHighlightColor = highlightColor)
        assertTrue(
            "Expected background color span for search highlight",
            result.spanStyles.any { it.item.background == highlightColor }
        )
    }

    // --- PoeticLine ---

    @Test
    fun poetic_line_with_indent_level_2_has_correct_padding() {
        val nodes = listOf(TextNode.PoeticLine("Praise the Lord", indentLevel = 2))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(result.text.startsWith("    ".repeat(2)))
        assertTrue(result.text.contains("Praise the Lord"))
    }

    @Test
    fun right_aligned_poetic_line_has_italic_span() {
        val nodes = listOf(TextNode.PoeticLine("Selah", indentLevel = 0, rightAligned = true))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(
            "Expected italic SpanStyle on right-aligned poetic line",
            result.spanStyles.any { it.item.fontStyle == FontStyle.Italic }
        )
    }

    // --- Link ---

    @Test
    fun link_article_renders_title() {
        val nodes = listOf(TextNode.Link(LinkData.Article("en:ta:vol1", "Figures of Speech")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("Figures of Speech", result.text)
    }

    @Test
    fun link_article_adds_TA_string_annotation() {
        val nodes = listOf(TextNode.Link(LinkData.Article("en:ta:vol1", "Figures of Speech")))
        val result = ComposeTextAdapter.convert(nodes)
        val annotations = result.getStringAnnotations(tag = "TA", start = 0, end = result.text.length)
        assertTrue("Expected TA annotation", annotations.isNotEmpty())
        assertEquals("en:ta:vol1", annotations[0].item)
    }

    @Test
    fun link_translation_word_renders_id() {
        val nodes = listOf(TextNode.Link(LinkData.TranslationWord("grace")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("grace", result.text)
    }

    @Test
    fun link_translation_word_adds_TW_annotation() {
        val nodes = listOf(TextNode.Link(LinkData.TranslationWord("grace")))
        val result = ComposeTextAdapter.convert(nodes)
        val annotations = result.getStringAnnotations(tag = "TW", start = 0, end = result.text.length)
        assertTrue("Expected TW annotation", annotations.isNotEmpty())
        assertEquals("grace", annotations[0].item)
    }

    @Test
    fun link_passage_renders_title() {
        val nodes = listOf(TextNode.Link(LinkData.Passage("1:1", "Genesis 1:1")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("Genesis 1:1", result.text)
    }

    @Test
    fun link_passage_adds_PASSAGE_annotation() {
        val nodes = listOf(TextNode.Link(LinkData.Passage("1:1", "Genesis 1:1")))
        val result = ComposeTextAdapter.convert(nodes)
        val annotations = result.getStringAnnotations(tag = "PASSAGE", start = 0, end = result.text.length)
        assertTrue("Expected PASSAGE annotation", annotations.isNotEmpty())
        assertEquals("1:1", annotations[0].item)
    }

    @Test
    fun link_markdown_renders_title() {
        val nodes = listOf(TextNode.Link(LinkData.Markdown("http://example.com", "Example")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("Example", result.text)
    }

    @Test
    fun link_short_reference_renders_ref() {
        val nodes = listOf(TextNode.Link(LinkData.ShortReference("3:16")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("3:16", result.text)
    }

    @Test
    fun link_app_link_renders_title() {
        val nodes = listOf(TextNode.Link(LinkData.AppLink("/ta/figs", "ta", "Figures of Speech")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("Figures of Speech", result.text)
    }

    // --- Link color span ---

    @Test
    fun link_has_blue_color_span() {
        val nodes = listOf(TextNode.Link(LinkData.Article("en:ta", "Article")))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(
            "Expected blue SpanStyle on link",
            result.spanStyles.any { it.item.color == Color.Blue }
        )
    }
}
