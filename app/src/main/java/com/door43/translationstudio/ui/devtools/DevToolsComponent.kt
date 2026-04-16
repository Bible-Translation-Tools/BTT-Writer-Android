package com.door43.translationstudio.ui.devtools

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import com.arkivanov.decompose.ComponentContext
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App
import com.door43.translationstudio.BuildConfig
import com.door43.translationstudio.Platform
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.translationstudio.ui.navigation.ComponentScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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

interface DevToolsComponent {

    val versionName: String
    val versionCode: Int
    val udid: String

    val state: StateFlow<State>
    val event: Flow<Event>
    val progress: StateFlow<Progress?>

    fun loadTools()
    fun readErrorLog()
    fun clearKeysRegenerated()
    fun calculateSystemResources(): String

    fun navigateBack()

    data class State(
        val versionName: String = "",
        val versionCode: String = "",
        val udid: String = "",
        val tools: List<ToolItem> = emptyList(),
        val logs: List<LogEntry> = emptyList(),
        val keysRegenerated: Boolean? = null,
    )

    sealed class Event {
        data object ReadLog : Event()
        data object CheckSystemResources : Event()
    }

    sealed interface Result {
        data object NavigateBack : Result
    }
}

class DefaultDevToolsComponent(
    componentContext: ComponentContext,
    private val platform: Platform,
    private val onResult: (DevToolsComponent.Result) -> Unit
) : DevToolsComponent,
    ComponentContext by componentContext,
    KoinComponent, ProgressOwner, ComponentScope {

    private val application: Application by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val library: Door43Client by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(DevToolsComponent.State())
    override val state: StateFlow<DevToolsComponent.State> = _state.asStateFlow()

    private val _event = Channel<DevToolsComponent.Event>()
    override val event = _event.receiveAsFlow()

    override val versionName = BuildConfig.VERSION_NAME
    override val versionCode = BuildConfig.VERSION_CODE
    override val udid: String get() = App.udid()

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    override fun loadTools() {
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

    override fun readErrorLog() {
        launchWithProgress(
            application.getString(R.string.reading_logs)
        ) {
            val logs = withContext(Dispatchers.IO) {
                Logger.getLogEntries()
            }
            _state.update { it.copy(logs = logs) }
        }
    }

    override fun clearKeysRegenerated() {
        _state.update { it.copy(keysRegenerated = null) }
    }

    override fun calculateSystemResources(): String {
        return platform.calculateSystemResources()
    }

    override fun navigateBack() {
        onResult(DevToolsComponent.Result.NavigateBack)
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
            coroutineScope.launch {
                _event.send(DevToolsComponent.Event.ReadLog)
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
            coroutineScope.launch {
                _event.send(DevToolsComponent.Event.CheckSystemResources)
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
            platform.restartApp()
        }
    }
}