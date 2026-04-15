package com.door43.translationstudio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.navigation.ComponentScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Launches a coroutine in the viewModelScope and immediately starts a tracked task.
 * This extension is only available to classes that are BOTH a ViewModel and a ProgressOwner.
 */
@Deprecated("Remove after migration to Decompose")
fun <T> T.launchWithProgress(
    message: String? = null,
    block: suspend (TaskHandle) -> Unit
): Job where T : ViewModel, T : ProgressOwner {
    return viewModelScope.launch {
        runTask(message, block)
    }
}

/**
 * Launches a coroutine in the custom scope and immediately starts a tracked task.
 * This extension is only available to classes that are BOTH a ComponentContext and a ProgressOwner.
 */
fun <T> T.launchWithProgress(
    message: String? = null,
    block: suspend (TaskHandle) -> Unit
): Job where T : ComponentScope, T : ProgressOwner {
    return coroutineScope.launch {
        runTask(message, block)
    }
}