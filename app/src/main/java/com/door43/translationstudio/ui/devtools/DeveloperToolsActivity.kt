package com.door43.translationstudio.ui.devtools

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.door43.translationstudio.App
import com.door43.translationstudio.App.Companion.udid
import com.door43.translationstudio.BuildConfig
import com.door43.translationstudio.services.BackupService
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.util.RuntimeWrapper
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.unfoldingword.tools.logger.Logger
import java.text.DecimalFormat

class DeveloperToolsActivity : BaseActivity() {
    private val viewModel: DeveloperViewModel by viewModel()

    private var systemResourcesMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE

        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DeveloperToolsScreen(
                        versionName = versionName,
                        versionCode = versionCode,
                        udid = udid(),
                        systemResourcesMessage = systemResourcesMessage,
                        onDismissSystemResources = { systemResourcesMessage = null },
                        onDeleteLibrary = ::handleDeleteLibrary,
                        onCalculateSystemResources = ::calculateSystemResources
                    )
                }
            }
        }
    }

    private fun handleDeleteLibrary() {
        val backupIntent = Intent(this, BackupService::class.java)
        stopService(backupIntent)
        App.restart()
    }

    private fun calculateSystemResources() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        var message = "System Resources:\n"
        val numProcessors = RuntimeWrapper.availableProcessors
        message += "Number of processors: $numProcessors (${App.MINIMUM_NUMBER_OF_PROCESSORS} required)\n"
        val maxMem = RuntimeWrapper.maxMemory
        message += "JVM max memory: ${getFormattedSize(maxMem)} (${getFormattedSize(App.MINIMUM_REQUIRED_RAM)} required)\n"

        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        message += "Available memory on the system: ${getFormattedSize(info.availMem)}\n"
        message += "Total memory on the system (getMemoryInfo): ${getFormattedSize(info.totalMem)}\n"
        message += "Total memory on the system (/proc/meminfo): ${getFormattedSize(viewModel.getTotalRam())}\n"
        message += "Low memory threshold on the system: ${getFormattedSize(info.threshold)}\n"
        message += "Low memory state on the system: ${info.lowMemory}\n"

        message += "Manufacturer: ${Build.MANUFACTURER}\n"
        message += "Model: ${Build.MODEL}\n"
        message += "Version: ${Build.VERSION.SDK_INT}\n"
        message += "Version Release: ${Build.VERSION.RELEASE}\n"

        val displayMetrics = resources.displayMetrics
        message += "\nScreen size ${displayMetrics.heightPixels}H*${displayMetrics.widthPixels}W"
        message += ", density: ${displayMetrics.density}"
        message += ", dpi: ${displayMetrics.xdpi}X*${displayMetrics.ydpi}Y"

        Logger.i(TAG, "system resources check:\n$message")

        // Trigger the Compose Dialog
        systemResourcesMessage = message
    }

    private fun getFormattedSize(bytes: Long): String {
        val GB = DeveloperViewModel.GB
        val MB = DeveloperViewModel.MB
        val KB = DeveloperViewModel.KB

        if (bytes / GB > 0) return formatWithUnits(bytes.toDouble() / GB, "GB")
        if (bytes / MB > 0) return formatWithUnits(bytes.toDouble() / MB, "MB")
        if (bytes / KB > 0) return formatWithUnits(bytes.toDouble() / KB, "KB")
        return bytes.toString() + "B"
    }

    private fun formatWithUnits(size: Double, units: String): String {
        if (size >= 100) return (size + 0.5).toLong().toString() + units
        val decimalFormat = if (size >= 10) DecimalFormat("#.#") else DecimalFormat("#.##")
        return decimalFormat.format(size) + units
    }

    companion object {
        val TAG: String = DeveloperToolsActivity::class.java.simpleName
    }
}
