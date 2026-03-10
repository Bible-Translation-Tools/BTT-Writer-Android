package com.door43.translationstudio.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Launches a coroutine in the viewModelScope and immediately starts a tracked task.
 * This extension is only available to classes that are BOTH a ViewModel and a ProgressOwner.
 */
fun <T> T.launchWithProgress(
    message: String? = null,
    block: suspend (TaskHandle) -> Unit
): Job where T : ViewModel, T : ProgressOwner {
    return viewModelScope.launch {
        runTask(message, block)
    }
}