package com.door43.translationstudio.rendering

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.door43.translationstudio.rendering.adapter.SpannableAdapter
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.translationstudio.rendering.model.TextNode
import org.junit.Test
import org.junit.Assert.*

class DebugSpannableAdapterTest {

    @Test
    fun `debug q1_q2_q3 spannable output`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Create the exact node structure that would come from renderer
        val nodes = listOf(
            TextNode.PoeticLine("", indentLevel = 1, rightAligned = false),
            TextNode.Text("Q1 content"),
            TextNode.LineBreak,
            TextNode.PoeticLine("", indentLevel = 2, rightAligned = false),
            TextNode.Text("Q2 content"),
            TextNode.LineBreak,
            TextNode.PoeticLine("", indentLevel = 3, rightAligned = false),
            TextNode.Text("Q3 content"),
            TextNode.LineBreak
        )

        val spannable = SpannableAdapter.convert(nodes, context)
        val output = spannable.toString()

        // Show character-by-character representation
        val report = StringBuilder()
        report.append("\n=== SPANNABLE OUTPUT (raw string) ===\n")
        report.append(output).append("\n")

        report.append("\n=== SPANNABLE OUTPUT (with visible spaces) ===\n")
        output.forEach { c ->
            when (c) {
                ' ' -> report.append("·")
                '\n' -> report.append("↵\n")
                else -> report.append(c)
            }
        }
        report.append("\n")

        report.append("\n=== SPANNABLE ANALYSIS ===\n")
        report.append("Total length: ${output.length}\n")

        // Split by lines and analyze
        val lines = output.split("\n")
        report.append("Lines: ${lines.size}\n")
        lines.forEachIndexed { idx, line ->
            val leadingSpaces = line.takeWhile { it == ' ' }.length
            report.append("Line $idx: [$leadingSpaces spaces] '$line'\n")
        }

        System.err.println(report.toString())

        // Assertions to verify indentation is correct
        val lines_list = output.split("\n").filter { it.isNotEmpty() }

        assertTrue("Should have content lines", lines_list.size >= 3)

        // Find lines with Q1, Q2, Q3 content
        val q1Line = lines_list.find { it.contains("Q1") }
        val q2Line = lines_list.find { it.contains("Q2") }
        val q3Line = lines_list.find { it.contains("Q3") }

        assertNotNull("Q1 line should exist", q1Line)
        assertNotNull("Q2 line should exist", q2Line)
        assertNotNull("Q3 line should exist", q3Line)

        val q1Spaces = q1Line?.takeWhile { it == ' ' }?.length ?: 0
        val q2Spaces = q2Line?.takeWhile { it == ' ' }?.length ?: 0
        val q3Spaces = q3Line?.takeWhile { it == ' ' }?.length ?: 0

        report.append("\n=== INDENTATION COMPARISON ===\n")
        report.append("Q1 leading spaces: $q1Spaces\n")
        report.append("Q2 leading spaces: $q2Spaces\n")
        report.append("Q3 leading spaces: $q3Spaces\n")

        // Q1 should have 4 spaces (1 level), Q2 should have 8 (2 levels), Q3 should have 12 (3 levels)
        System.err.println(report.toString())

        assertEquals("Q1 should have 4 leading spaces", 4, q1Spaces)
        assertEquals("Q2 should have 8 leading spaces", 8, q2Spaces)
        assertEquals("Q3 should have 12 leading spaces", 12, q3Spaces)
    }
}
