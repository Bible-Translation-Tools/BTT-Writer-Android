package com.door43.translationstudio.core

data class ProjectTranslation(
    val title: String,
    val isTitleFinished: Boolean,
    val description: String = ""
)