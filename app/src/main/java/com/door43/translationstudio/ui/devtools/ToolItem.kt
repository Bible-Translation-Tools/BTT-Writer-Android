package com.door43.translationstudio.ui.devtools

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Tool items allow you to easily provide tools within a ListView
 */
data class ToolItem(
    val name: String,
    val description: String,
    val icon: ImageVector? = null,
    val isEnabled: Boolean = true,
    val disabledNotice: String = "",
    val action: () -> Unit = {}
)
