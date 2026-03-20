package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.TextNode
import org.junit.Test
import org.junit.Assert.*

class PoeticalLineIndentationTest {

    private fun testRender(input: String): List<TextNode> {
        val renderer = USXRenderer()
        val renderNodes = renderer.render(input)
        return RenderNodeConverter.renderNodesToTextNodes(renderNodes)
    }

    @Test
    fun `poetic lines q1_q2_q3 have progressively increasing indentation`() {
        val input = """
            <para style="q1">First level</para>
            <para style="q2">Second level</para>
            <para style="q3">Third level</para>
        """.trimIndent()

        val nodes = testRender(input)

        // Verify marker nodes exist with correct indentLevels
        val markers = nodes.filterIsInstance<TextNode.PoeticLine>().filter { it.content.isEmpty() }
        assertEquals("Should have 3 markers", 3, markers.size)
        assertEquals("First marker should be level 1", 1, markers[0].indentLevel)
        assertEquals("Second marker should be level 2", 2, markers[1].indentLevel)
        assertEquals("Third marker should be level 3", 3, markers[2].indentLevel)

        // Verify text nodes exist with correct content
        val textNodes = nodes.filterIsInstance<TextNode.Text>()
        assertTrue("Should have text nodes", textNodes.any { it.content.contains("First level") })
        assertTrue("Should have text nodes", textNodes.any { it.content.contains("Second level") })
        assertTrue("Should have text nodes", textNodes.any { it.content.contains("Third level") })
    }
}
