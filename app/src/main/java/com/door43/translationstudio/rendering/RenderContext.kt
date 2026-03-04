package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.RenderNode

/**
 * Context passed during adapter traversal to enable context-aware rendering.
 * Tracks the parent node so that verses, notes, etc. can apply different formatting
 * rules based on whether they're inside a poetic line, paragraph, or other container.
 */
data class RenderContext(
    val parentNode: RenderNode? = null
) {
    fun withParent(node: RenderNode) = RenderContext(parentNode = node)
}
