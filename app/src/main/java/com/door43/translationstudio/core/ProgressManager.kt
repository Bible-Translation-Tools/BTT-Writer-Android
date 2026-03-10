package com.door43.translationstudio.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class Progress(
    val value: Float = -1f,
    val message: String? = null
) {
    val isIndeterminate: Boolean get() = value < 0f
}

class TaskHandle(
    val id: String,
    val initialMessage: String?,
    private val onUpdate: (Progress) -> Unit
) {
    fun update(value: Float, message: String? = null) {
        val safeValue = if (value < 0f) -1f else value.coerceIn(0f, 1f)
        onUpdate(Progress(safeValue, message ?: initialMessage))
    }
}

interface ProgressOwner {
    val progress: StateFlow<Progress?>

    suspend fun runTask(message: String? = null, block: suspend (TaskHandle) -> Unit)
}

/**
 * Coroutine context element that marks "we're inside a tracked task."
 * Allows nested runTask calls to detect the parent and run inline.
 */
private class TaskContext(
    val handle: TaskHandle
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TaskContext>
}

class ProgressManager(private val scope: CoroutineScope) : ProgressOwner {
    private val activeTasks = MutableStateFlow<Map<String, Progress>>(emptyMap())

    override val progress: StateFlow<Progress?> = activeTasks
        .map { tasks -> combineProgress(tasks.values) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        val parentTask = currentCoroutineContext()[TaskContext]

        if (parentTask != null) {
            // Already inside a task — update message if provided, run inline
            if (message != null) {
                updateTaskProgress(parentTask.handle.id) { it.copy(message = message) }
            }
            block(parentTask.handle)
        } else {
            // Top-level task — register and track
            val taskId = Uuid.random().toString()
            val handle = createHandle(taskId, message)
            activeTasks.update { it + (taskId to Progress(-1f, message)) }

            try {
                // Add the TaskContext to the coroutine tree
                withContext(TaskContext(handle)) {
                    block(handle)
                }
            } finally {
                activeTasks.update { it - taskId }
            }
        }
    }

    private fun createHandle(taskId: String, initialMessage: String?): TaskHandle {
        return TaskHandle(taskId, initialMessage) { progress ->
            updateTaskProgress(taskId) {
                progress.copy(message = progress.message ?: it.message)
            }
        }
    }

    private fun updateTaskProgress(taskId: String, transform: (Progress) -> Progress) {
        activeTasks.update { current ->
            val existing = current[taskId] ?: return@update current
            current + (taskId to transform(existing))
        }
    }

    private fun combineProgress(tasks: Collection<Progress>): Progress? {
        if (tasks.isEmpty()) return null

        val message = tasks.lastOrNull { it.message != null }?.message
        val isAnyIndeterminate = tasks.any { it.isIndeterminate }
        val value = if (isAnyIndeterminate) -1f else tasks.map { it.value }.average().toFloat()

        return Progress(value, message)
    }
}