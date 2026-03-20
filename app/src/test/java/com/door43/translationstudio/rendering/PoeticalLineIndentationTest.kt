package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.RenderNode
import org.junit.Test
import org.junit.Assert.*

class PoeticalLineIndentationTest {

    private fun testRender(input: String): List<RenderNode> {
        val renderer = USXRenderer()
        return renderer.render(input)
    }

    @Test
    fun `poetic lines q1_q2_q3 have progressively increasing indentation`() {
        val input = """
            <para style="q1">First level</para>
            <para style="q2">Second level</para>
            <para style="q3">Third level</para>
        """.trimIndent()

        val nodes = testRender(input)

        // Verify PoeticLine nodes exist with correct indentLevels and children
        val poeticLines = nodes.filterIsInstance<RenderNode.PoeticLine>()
        assertEquals("Should have 3 poetic lines", 3, poeticLines.size)
        assertEquals("First line should be level 1", 1, poeticLines[0].indentLevel)
        assertEquals("Second line should be level 2", 2, poeticLines[1].indentLevel)
        assertEquals("Third line should be level 3", 3, poeticLines[2].indentLevel)

        // Verify text content is inside children
        fun childText(line: RenderNode.PoeticLine): String =
            line.children.filterIsInstance<RenderNode.Text>().joinToString("") { it.content }
        assertTrue("First level text", childText(poeticLines[0]).contains("First level"))
        assertTrue("Second level text", childText(poeticLines[1]).contains("Second level"))
        assertTrue("Third level text", childText(poeticLines[2]).contains("Third level"))
    }
}
