package com.door43.translationstudio.core

/**
 * Created by joel on 9/16/2015.
 */
data class ChapterTranslation(
    val title: String,
    val reference: String,
    val id: String,
    /** Checks if the chapter title is finished being translated */
    val titleFinished: Boolean,
    /** Checks if the chapter reference is finished being translated */
    val referenceFinished: Boolean,
    /** Returns the translation format for the chapter title and reference */
    val translationFormat: TranslationFormat
)