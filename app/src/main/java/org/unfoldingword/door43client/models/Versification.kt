package org.unfoldingword.door43client.models

/**
 * Represents a versification system.
 * This is what chunk markers are based on.
 */
data class Versification(
    var slug: String,
    var name: String
) {
    var rowId: Long = -1L
}