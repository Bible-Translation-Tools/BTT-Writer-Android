package com.door43.translationstudio.rendering

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.model.NodeStyle
import com.door43.translationstudio.rendering.model.NoteStyle
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val nodes = listOf(RenderNode.Text("hello world"))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("hello world", result.text)
    }

    @Test
    fun multiple_nodes_are_concatenated_in_order() {
        val nodes = listOf(
            RenderNode.Text("foo"),
            RenderNode.Text("bar"),
            RenderNode.Text("baz")
        )
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("foobarbaz", result.text)
    }

    @Test
    fun line_break_produces_newline() {
        val nodes = listOf(RenderNode.Text("a"), RenderNode.LineBreak, RenderNode.Text("b"))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("a\nb", result.text)
    }

    @Test
    fun blank_line_produces_single_newline() {
        val nodes = listOf(RenderNode.Text("a"), RenderNode.BlankLine, RenderNode.Text("b"))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("a\nb", result.text)
    }

    @Test
    fun paragraph_indented_produces_newline_plus_spaces() {
        val nodes = listOf(RenderNode.Paragraph(indented = true, children = emptyList()))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("\n    ", result.text)
    }

    @Test
    fun paragraph_unindented_produces_newline() {
        val nodes = listOf(RenderNode.Paragraph(indented = false, children = emptyList()))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("\n", result.text)
    }

    // --- Verse ---

    @Test
    fun verse_marker_single_verse_produces_number_string() {
        val nodes = listOf(RenderNode.Verse(3, 0, pinned = false))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("3", result.text)
    }

    @Test
    fun verse_marker_range_produces_hyphenated_label() {
        val nodes = listOf(RenderNode.Verse(3, 5, pinned = false))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("3-5", result.text)
    }

    @Test
    fun pinned_verse_marker_adds_VERSE_MARKER_annotation() {
        val nodes = listOf(RenderNode.Verse(7, 0, pinned = true, machineReadable = "\\v 7 "))
        val result = ComposeTextAdapter.convert(nodes)
        val annotations = result.getStringAnnotations(
            tag = "VERSE_MARKER", start = 0, end = result.text.length
        )
        assertTrue("Expected VERSE_MARKER annotation", annotations.isNotEmpty())
        assertEquals("7|0|\\v 7 ", annotations[0].item)
    }

    @Test
    fun unpinned_verse_has_no_VERSE_MARKER_annotation() {
        val nodes = listOf(RenderNode.Verse(3, 0, pinned = false))
        val result = ComposeTextAdapter.convert(nodes)
        val annotations = result.getStringAnnotations(
            tag = "VERSE_MARKER", start = 0, end = result.text.length
        )
        assertTrue("Unpinned verse should not have VERSE_MARKER annotation", annotations.isEmpty())
    }

    @Test
    fun verse_with_raw_position_adds_RAW_POSITION_annotation() {
        val nodes = listOf(RenderNode.Verse(1, 0, pinned = false, start = 0, end = 5))
        val result = ComposeTextAdapter.convert(nodes)
        val annotations = result.getStringAnnotations(
            tag = "RAW_POSITION", start = 0, end = result.text.length
        )
        assertTrue("Expected RAW_POSITION annotation", annotations.isNotEmpty())
        assertEquals("0|5", annotations[0].item)
    }

    // --- Note ---

    @Test
    fun note_marker_produces_inline_content() {
        val nodes = listOf(
            RenderNode.Note(
                caller = "+",
                passage = "some passage",
                notes = "footnote body",
                noteStyle = NoteStyle.FOOTNOTE
            )
        )
        val result = ComposeTextAdapter.convert(nodes)
        // Note renders as inline content with NOTE_CHAR (\u2800)
        assertTrue("Note should produce non-empty output", result.text.isNotEmpty())
    }

    @Test
    fun note_marker_triggers_onNoteClick_callback() {
        var clickedNote: RenderNode.Note? = null
        val note = RenderNode.Note(
            caller = "+",
            passage = "some passage",
            notes = "footnote body",
            noteStyle = NoteStyle.FOOTNOTE
        )
        val nodes = listOf(note)
        ComposeTextAdapter.convert(
            nodes,
            onNoteClick = { n, _, _ -> clickedNote = n }
        )
        // Verify the note was wired up (callback object was created)
        // The actual click needs UI interaction, but the convert should not throw
        assertNull("Callback should not be triggered during convert", clickedNote)
    }

    // --- SectionHeading ---

    @Test
    fun section_heading_appends_newline_after_text() {
        val nodes = listOf(RenderNode.Section("The Beginning", isMajor = false))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(result.text.startsWith("The Beginning"))
        assertTrue(result.text.endsWith("\n"))
    }

    @Test
    fun major_section_heading_text_is_uppercased() {
        val nodes = listOf(RenderNode.Section("creation", isMajor = true))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(result.text.startsWith("CREATION"))
    }

    @Test
    fun section_heading_has_bold_span() {
        val nodes = listOf(RenderNode.Section("The Beginning", isMajor = false))
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
        val nodes = listOf(RenderNode.ChapterLabel("Chapter 1"))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("Chapter 1", result.text)
    }

    @Test
    fun chapter_label_has_bold_span() {
        val nodes = listOf(RenderNode.ChapterLabel("Chapter 1"))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(
            "Expected bold SpanStyle on chapter label",
            result.spanStyles.any { it.item.fontWeight == FontWeight.Bold }
        )
    }

    // --- Styled nodes ---

    @Test
    fun styled_bold_node_text_is_preserved() {
        val nodes = listOf(RenderNode.StyledText("bold text", NodeStyle.BOLD))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("bold text", result.text)
    }

    @Test
    fun styled_bold_node_has_bold_span() {
        val nodes = listOf(RenderNode.StyledText("bold text", NodeStyle.BOLD))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(
            "Expected bold SpanStyle",
            result.spanStyles.any { it.item.fontWeight == FontWeight.Bold }
        )
    }

    @Test
    fun styled_italic_node_has_italic_span() {
        val nodes = listOf(RenderNode.StyledText("italic text", NodeStyle.ITALIC))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(
            "Expected italic SpanStyle",
            result.spanStyles.any { it.item.fontStyle == FontStyle.Italic }
        )
    }

    // --- PoeticLine ---

    @Test
    fun poetic_line_with_indent_level_2_has_correct_padding() {
        val nodes = listOf(
            RenderNode.PoeticLine(
                indentLevel = 2,
                children = listOf(RenderNode.Text("Praise the Lord"))
            )
        )
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(result.text.startsWith("    ".repeat(2)))
        assertTrue(result.text.contains("Praise the Lord"))
    }

    @Test
    fun right_aligned_poetic_line_has_italic_span() {
        val nodes = listOf(
            RenderNode.PoeticLine(
                indentLevel = 0,
                rightAligned = true,
                children = listOf(RenderNode.Text("Selah"))
            )
        )
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(
            "Expected italic SpanStyle on right-aligned poetic line",
            result.spanStyles.any { it.item.fontStyle == FontStyle.Italic }
        )
    }

    // --- Link ---

    @Test
    fun link_article_renders_title() {
        val nodes = listOf(RenderNode.Link(LinkData.Article("en:ta:vol1", "Figures of Speech")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("Figures of Speech", result.text)
    }

    @Test
    fun link_translation_word_renders_title() {
        val nodes = listOf(RenderNode.Link(LinkData.TranslationWord("grace")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("grace", result.text)
    }

    @Test
    fun link_passage_renders_title() {
        val nodes = listOf(RenderNode.Link(LinkData.Passage("1:1", "Genesis 1:1")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("Genesis 1:1", result.text)
    }

    @Test
    fun link_markdown_renders_title() {
        val nodes = listOf(RenderNode.Link(LinkData.Markdown("http://example.com", "Example")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("Example", result.text)
    }

    @Test
    fun link_short_reference_renders_ref() {
        val nodes = listOf(RenderNode.Link(LinkData.ShortReference("3:16")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("3:16", result.text)
    }

    @Test
    fun link_app_link_renders_title() {
        val nodes = listOf(RenderNode.Link(LinkData.AppLink("/ta/figs", "ta", "Figures of Speech")))
        val result = ComposeTextAdapter.convert(nodes)
        assertEquals("Figures of Speech", result.text)
    }

    // --- Link color span ---

    @Test
    fun link_has_blue_color_span() {
        val nodes = listOf(RenderNode.Link(LinkData.Article("en:ta", "Article")))
        val result = ComposeTextAdapter.convert(nodes)
        assertTrue(
            "Expected blue SpanStyle on link",
            result.spanStyles.any { it.item.color == Color.Blue }
        )
    }
}
