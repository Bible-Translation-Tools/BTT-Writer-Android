package com.door43.translationstudio.ui.publish

import com.door43.translationstudio.core.TranslationFormat
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.TargetLanguage

/**
 * A sealed hierarchy representing a validation set on a translation.
 */
sealed class ValidationItem {
    /** Returns the title of the validation item */
    abstract val title: String
    abstract val titleLanguage: SourceLanguage
    /** Checks if the validation item is over a range */
    abstract val isRange: Boolean

    data class ValidFrame(
        override val title: String,
        override val titleLanguage: SourceLanguage,
        override val isRange: Boolean
    ) : ValidationItem()

    data class ValidGroup(
        override val title: String,
        override val titleLanguage: SourceLanguage,
        override val isRange: Boolean
    ) : ValidationItem()

    data class InvalidFrame(
        override val title: String,
        override val titleLanguage: SourceLanguage,
        val body: String,
        /** Returns the translation format of the body */
        val bodyLanguage: TargetLanguage,
        val bodyFormat: TranslationFormat,
        val targetTranslationId: String,
        val chapterId: String,
        val frameId: String
    ) : ValidationItem() {
        override val isRange: Boolean = false
    }

    /** For our purposes a group can be either a chapter or a project */
    data class InvalidGroup(
        override val title: String,
        override val titleLanguage: SourceLanguage
    ) : ValidationItem() {
        override val isRange: Boolean = false
    }
}