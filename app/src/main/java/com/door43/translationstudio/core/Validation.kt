package com.door43.translationstudio.core

import org.bibletranslationtools.resourcecatalog.library.models.SourceLanguage
import org.bibletranslationtools.resourcecatalog.library.models.TargetLanguage


/**
 * A sealed hierarchy representing a validation set on a translation.
 */
sealed class Validation {
    abstract val title: String
    abstract val titleLanguage: SourceLanguage
    /** Checks if the validation item is over a range */
    abstract val isRange: Boolean

    data class ValidFrame(
        override val title: String,
        override val titleLanguage: SourceLanguage,
        override val isRange: Boolean
    ) : Validation()

    data class ValidGroup(
        override val title: String,
        override val titleLanguage: SourceLanguage,
        override val isRange: Boolean
    ) : Validation()

    data class InvalidFrame(
        override val title: String,
        override val titleLanguage: SourceLanguage,
        val body: String,
        val bodyLanguage: TargetLanguage,
        val bodyFormat: TranslationFormat,
        val targetTranslationId: String,
        val chapterId: String,
        val frameId: String
    ) : Validation() {
        override val isRange: Boolean = false
    }

    /** For our purposes a group can be either a chapter or a project */
    data class InvalidGroup(
        override val title: String,
        override val titleLanguage: SourceLanguage
    ) : Validation() {
        override val isRange: Boolean = false
    }
}