package com.door43.translationstudio.ui.devtools

/**
 * Tool items allow you to easily provide tools within a ListView
 */
data class ToolItem(
    /** Returns the tool name */
    val name: String,
    /** Returns the tool description */
    val description: String,
    /** Returns the image resource id */
    val icon: Int,
    /** Checks if the tool is enabled */
    val isEnabled: Boolean = true,
    /** Returns the notice to be shown when the tool is disabled */
    val disabledNotice: String = "",
    /** An action to be performed */
    val action: () -> Unit = {}
)
