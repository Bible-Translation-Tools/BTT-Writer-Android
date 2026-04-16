package com.door43.translationstudio

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Process
import com.door43.translationstudio.Platform.Companion.GB
import com.door43.translationstudio.Platform.Companion.KB
import com.door43.translationstudio.Platform.Companion.MB
import com.door43.translationstudio.Platform.Companion.TB
import com.door43.translationstudio.services.BackupService
import com.door43.util.RuntimeWrapper
import org.unfoldingword.tools.logger.Logger
import java.io.RandomAccessFile
import java.text.DecimalFormat

interface Platform {
    fun restartApp()

    fun calculateSystemResources(): String
    fun getTotalRam(): Long
    fun getFormattedSize(bytes: Long): String {
        if (bytes / GB > 0) return formatWithUnits(bytes.toDouble() / GB, "GB")
        if (bytes / MB > 0) return formatWithUnits(bytes.toDouble() / MB, "MB")
        if (bytes / KB > 0) return formatWithUnits(bytes.toDouble() / KB, "KB")
        return bytes.toString() + "B"
    }

    fun formatWithUnits(size: Double, units: String): String {
        if (size >= 100) return (size + 0.5).toLong().toString() + units
        val decimalFormat = if (size >= 10) DecimalFormat("#.#") else DecimalFormat("#.##")
        return decimalFormat.format(size) + units
    }

    companion object {
        const val KB: Long = 1024
        const val MB: Long = KB * KB
        const val GB: Long = MB * KB
        const val TB: Long = GB * KB
    }
}

class AndroidPlatform(private val context: Context) : Platform {

    override fun restartApp() {
        val backupIntent = Intent(context, BackupService::class.java)
        context.stopService(backupIntent)

        val packageName = context.packageName
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(Intent.makeRestartActivityTask(intent.component))
            Process.killProcess(Process.myPid())
            RuntimeWrapper.exit(0)
        }
    }

    override fun calculateSystemResources(): String {
        val am = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        var message = "System Resources:\n"
        val numProcessors = RuntimeWrapper.availableProcessors
        message += "Number of processors: $numProcessors (${App.MINIMUM_NUMBER_OF_PROCESSORS} required)\n"
        val maxMem = RuntimeWrapper.maxMemory
        message += "JVM max memory: ${getFormattedSize(maxMem)} (${getFormattedSize(App.MINIMUM_REQUIRED_RAM)} required)\n"

        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        message += "Available memory on the system: ${getFormattedSize(info.availMem)}\n"
        message += "Total memory on the system (getMemoryInfo): ${getFormattedSize(info.totalMem)}\n"
        message += "Total memory on the system (/proc/meminfo): ${getFormattedSize(getTotalRam())}\n"
        message += "Low memory threshold on the system: ${getFormattedSize(info.threshold)}\n"
        message += "Low memory state on the system: ${info.lowMemory}\n"

        message += "Manufacturer: ${Build.MANUFACTURER}\n"
        message += "Model: ${Build.MODEL}\n"
        message += "Version: ${Build.VERSION.SDK_INT}\n"
        message += "Version Release: ${Build.VERSION.RELEASE}\n"

        val displayMetrics = context.resources.displayMetrics
        message += "\nScreen size ${displayMetrics.heightPixels}H*${displayMetrics.widthPixels}W"
        message += ", density: ${displayMetrics.density}"
        message += ", dpi: ${displayMetrics.xdpi}X*${displayMetrics.ydpi}Y"

        Logger.i(this.javaClass.simpleName, "system resources check:\n$message")

        return message
    }

    override fun getTotalRam(): Long {
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
}