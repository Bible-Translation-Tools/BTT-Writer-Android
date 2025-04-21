package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.devtools.ToolItem
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.LogEntry
import org.unfoldingword.tools.logger.Logger
import java.io.RandomAccessFile
import javax.inject.Inject

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _tools = MutableLiveData<List<ToolItem>>()
    val tools: LiveData<List<ToolItem>> = _tools

    private val _keysRegenerated = MutableLiveData<Boolean?>()
    val keysRegenerated: LiveData<Boolean?> = _keysRegenerated

    private val _logs = MutableLiveData<List<LogEntry>>()
    val logs: LiveData<List<LogEntry>> = _logs

    private var listener: ToolsListener? = null

    fun loadTools() {
        _progress.value = ProgressHelper.Progress(application.getString(R.string.please_wait))
        viewModelScope.launch {
            val list = listOf(
                getGenerateSSHKeysItem(),
                readLogItem(),
                simulateCrashItem(),
                checkSystemResourcesItem(),
                deleteLibraryItem()
            )
            _tools.value = list
        }
        _progress.value = null
    }

    fun setListener(listener: ToolsListener) {
        this.listener = listener
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
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.reading_logs))
            _logs.value = Logger.getLogEntries()
            _progress.value = null
        }
    }

    private fun getGenerateSSHKeysItem(): ToolItem {
        return ToolItem(
            application.getString(R.string.regenerate_ssh_keys),
            application.getString(R.string.regenerate_ssh_keys_hint),
            R.drawable.ic_security_secondary_24dp,
            action = ::generateSSHKeys
        )
    }

    private fun readLogItem(): ToolItem {
        return ToolItem(
            application.getString(R.string.read_debug_log),
            application.getString(R.string.read_debug_log_hint),
            R.drawable.ic_description_secondary_24dp
        ) {
            listener?.onReadLog()
        }
    }

    private fun simulateCrashItem(): ToolItem {
        return ToolItem(
            application.getString(R.string.simulate_crash),
            "",
            R.drawable.ic_warning_secondary_24dp
        ) {
            throw IllegalStateException(application.getString(R.string.simulating_crash))
        }
    }

    private fun checkSystemResourcesItem(): ToolItem {
        return ToolItem(
            application.getString(R.string.check_system_resources),
            application.getString(R.string.check_system_resources_hint),
            R.drawable.ic_description_secondary_24dp
        ) {
            listener?.onCheckSystemResources()
        }
    }

    private fun deleteLibraryItem(): ToolItem {
        return ToolItem(
            application.getString(R.string.delete_library),
            application.getString(R.string.delete_library_hint),
            R.drawable.ic_delete_secondary_24dp
        ) {
            deleteLibrary()
        }
    }

    private fun generateSSHKeys() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.recreate_keys))
            directoryProvider.generateSSHKeys()
            _keysRegenerated.value = true
            _progress.value = null
        }
    }

    private fun deleteLibrary() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.deleting_library))
            withContext(Dispatchers.IO) {
                try {
                    library.tearDown()
                    directoryProvider.deleteLibrary()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            listener?.onDeleteLibrary()
            _progress.value = null
        }
    }

    interface ToolsListener {
        fun onReadLog()
        fun onCheckSystemResources()
        fun onDeleteLibrary()
    }

    companion object {
        const val KB: Long = 1024
        const val MB: Long = KB * KB
        const val GB: Long = MB * KB
        const val TB: Long = GB * KB
    }
}