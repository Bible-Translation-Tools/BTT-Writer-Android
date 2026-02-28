package com.door43.translationstudio.core

data class ProjectTranslation @JvmOverloads constructor(
    val title: String,
    val isTitleFinished: Boolean,
    val description: String = ""
)