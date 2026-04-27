package org.unfoldingword.door43client.models

/**
 * Represents the beginning of a chunk in a chapter
 */
data class ChunkMarker(
    /** the chapter this chunk exists in */
    val chapter: String,
    /** the verse at which this chunk starts */
    val verse: String
)
