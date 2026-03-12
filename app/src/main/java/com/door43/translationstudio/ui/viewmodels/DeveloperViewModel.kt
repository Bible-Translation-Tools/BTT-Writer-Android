package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.devtools.ToolItem
import com.door43.translationstudio.ui.launchWithProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.LogEntry
import org.unfoldingword.tools.logger.Logger
import java.io.RandomAccessFile

data class DeveloperState(
    val versionName: String = "",
    val versionCode: String = "",
    val udid: String = "",
    val tools: List<ToolItem> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
    val keysRegenerated: Boolean? = null,
)

sealed class DeveloperEvent {
    object ReadLog : DeveloperEvent()
    object CheckSystemResources : DeveloperEvent()
    object DeleteLibrary : DeveloperEvent()
}

class DeveloperViewModel(
    private val directoryProvider: IDirectoryProvider,
    private val library: Door43Client
) : ViewModel(), KoinComponent, ProgressOwner {

    private val application: Application by inject()

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(DeveloperState())
    val state: StateFlow<DeveloperState> = _state.asStateFlow()

    private val _events = Channel<DeveloperEvent>()
    val events = _events.receiveAsFlow()

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    fun loadTools() {
        launchWithProgress(
            application.getString(R.string.please_wait)
        ) {
            val list = listOf(
                getGenerateSSHKeysItem(),
                readLogItem(),
                simulateCrashItem(),
                checkSystemResourcesItem(),
                deleteLibraryItem()
            )
            _state.update { it.copy(tools = list) }
        }
    }

    fun getTotalRam(): Long {
        var lastValue: Long = 0
        try {
            RandomAccessFile("/proc/meminfo", "r").use { reader ->
                val load = reader.readLine()

                val parts = load.trim().split("\\s+".toRegex())
                val value = parts[1]
                val units = parts[2]
                val unitsFirst = units.substring(0, 1)

                var totalRam = value.toDouble()

                if ("T".equals(unitsFirst, ignoreCase = true)) {
                    totalRam *= TB.toDouble()
                } else if ("G".equals(unitsFirst, ignoreCase = true)) {
                    totalRam *= GB.toDouble()
                } else if ("M".equals(unitsFirst, ignoreCase = true)) {
                    totalRam *= MB.toDouble()
                } else if ("K".equals(unitsFirst, ignoreCase = true)) {
                    totalRam *= KB.toDouble()
                }
                lastValue = totalRam.toLong()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        return lastValue
    }

    fun readErrorLog() {
        launchWithProgress(
            application.getString(R.string.reading_logs)
        ) {
            val logs = withContext(Dispatchers.IO) {
                Logger.getLogEntries()
            }
            _state.update { it.copy(logs = logs) }
        }
    }

    fun clearKeysRegenerated() {
        _state.update { it.copy(keysRegenerated = null) }
    }

    private fun getGenerateSSHKeysItem(): ToolItem {
        return ToolItem(
            application.getString(R.string.regenerate_ssh_keys),
            application.getString(R.string.regenerate_ssh_keys_hint),
            Icons.Outlined.Security,
            action = ::generateSSHKeys
        )
    }

    private fun readLogItem(): ToolItem {
        return ToolItem(
            application.getString(R.string.read_debug_log),
            application.getString(R.string.read_debug_log_hint),
            Icons.Outlined.Description
        ) {
            viewModelScope.launch {
                _events.send(DeveloperEvent.ReadLog)
            }
        }
    }

    private fun simulateCrashItem(): ToolItem {
        return ToolItem(
            application.getString(R.string.simulate_crash),
            "",
            Icons.Outlined.Warning
        ) {
            throw IllegalStateException(application.getString(R.string.simulating_crash))
        }
    }

    private fun checkSystemResourcesItem(): ToolItem {
        return ToolItem(
            application.getString(R.string.check_system_resources),
            application.getString(R.string.check_system_resources_hint),
            Icons.Outlined.Description
        ) {
            viewModelScope.launch {
                _events.send(DeveloperEvent.CheckSystemResources)
            }
        }
    }

    private fun deleteLibraryItem(): ToolItem {
        return ToolItem(
            application.getString(R.string.delete_library),
            application.getString(R.string.delete_library_hint),
            Icons.Outlined.Delete
        ) {
            deleteLibrary()
        }
    }

    private fun generateSSHKeys() {
        launchWithProgress(
            application.getString(R.string.recreate_keys)
        ) {
            val generated = withContext(Dispatchers.IO) {
                directoryProvider.generateSSHKeys()
                true
            }
            _state.update { it.copy(keysRegenerated = generated) }
        }
    }

    private fun deleteLibrary() {
        launchWithProgress(
            application.getString(R.string.deleting_library)
        ) {
            withContext(Dispatchers.IO) {
                try {
                    library.tearDown()
                    directoryProvider.deleteLibrary()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _events.send(DeveloperEvent.DeleteLibrary)
        }
    }

    companion object {
        const val KB: Long = 1024
        const val MB: Long = KB * KB
        const val GB: Long = MB * KB
        const val TB: Long = GB * KB
    }
}