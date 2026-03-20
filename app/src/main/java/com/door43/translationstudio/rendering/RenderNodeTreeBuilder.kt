package com.door43.translationstudio.rendering

import com.door43.translationstudio.rendering.model.RenderNode

/**
 * Converts a flat list of RenderNodes (where block nodes like Paragraph and PoeticLine
 * are markers with empty children) into a proper tree where block nodes contain their
 * content as children.
 *
 * Rule: block markers consume following inline nodes as children until the next block
 * marker or end of list. Nodes before the first block marker stay as top-level siblings.
 */
fun buildTree(flatNodes: List<RenderNode>): List<RenderNode> {
    val result = mutableListOf<RenderNode>()
    var i = 0

    while (i < flatNodes.size) {
        val node = flatNodes[i]
        when {
            isBlockNode(node) -> {
                val children = mutableListOf<RenderNode>()
                i++
                while (i < flatNodes.size && !isBlockNode(flatNodes[i])) {
                    children.add(flatNodes[i])
                    i++
                }
                result.add(withChildren(node, children))
            }
            else -> {
                result.add(node)
                i++
            }
        }
    }

    return result
}

private fun isBlockNode(node: RenderNode): Boolean = when (node) {
    is RenderNode.Paragraph -> true
    is RenderNode.PoeticLine -> true
    else -> false
}

private fun withChildren(block: RenderNode, gathered: List<RenderNode>): RenderNode = when (block) {
    is RenderNode.Paragraph -> block.copy(children = block.children + gathered)
    is RenderNode.PoeticLine -> block.copy(children = block.children + gathered)
    else -> block
}
