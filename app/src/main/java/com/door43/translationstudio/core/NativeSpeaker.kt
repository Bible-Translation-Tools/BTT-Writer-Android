package com.door43.translationstudio.core

import kotlinx.serialization.Serializable

/**
 * Represents a single native speaker.
 * A native speaker understands at least one gateway language in addition to their native language
 *
 * note: this is parse for now, but keeping it in a class for potential future addition of properties
 */
@Serializable
data class NativeSpeaker(val name: String) {
    override fun toString() = name
}
