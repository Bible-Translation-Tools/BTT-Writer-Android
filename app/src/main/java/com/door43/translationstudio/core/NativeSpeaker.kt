package com.door43.translationstudio.core

/**
 * Represents a single native speaker.
 * A native speaker understands at least one gateway language in addition to their native language
 *
 * note: this is parse for now, but keeping it in a class for potential future addition of properties
 */
class NativeSpeaker(val name: String?) {
    override fun toString(): String {
        return name ?: "Unknown Native Speaker"
    }

    override fun equals(other: Any?): Boolean {
        if (other != null && other is NativeSpeaker) {
            return other.name == this.name
        }
        return false
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}
