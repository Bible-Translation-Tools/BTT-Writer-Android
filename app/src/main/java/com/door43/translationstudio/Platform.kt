package com.door43.translationstudio

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Context.CONNECTIVITY_SERVICE
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import androidx.core.content.FileProvider
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.Platform.Companion.GB
import com.door43.translationstudio.Platform.Companion.KB
import com.door43.translationstudio.Platform.Companion.MB
import com.door43.translationstudio.Platform.Companion.TB
import com.door43.translationstudio.services.BackupService
import com.door43.util.FileUtilities
import com.door43.util.RuntimeWrapper
import org.bibletranslationtools.logger.LogLevel
import org.bibletranslationtools.logger.Logger
import java.io.File
import java.io.RandomAccessFile
import java.text.DecimalFormat
import java.util.Locale

data class AppInfo(
    val versionName: String,
    val versionCode: Int,
    val model: String,
    val device: String,
    val manufacturer: String
)

interface Platform {
    val info: AppInfo
    val udid: String
        get() = info.model.lowercase().replace(" ", "_")
    val isStoreVersion: Boolean
    val deviceLanguageCode: String
        get() {
            val code = Locale.getDefault().language
            return code.replace("[_-]$".toRegex(), "")
        }
    val isNetworkAvailable: Boolean

    fun restart()
    fun exit()
    fun configureLogger(minLogLevel: Int)

    suspend fun shareApp()
    fun shareProject(file: File)

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

        const val MIN_CHECKING_LEVEL: Int = 3
        // 96 MB, Minimum RAM needed for reliable operation
        const val MINIMUM_REQUIRED_RAM: Long = (96 * 1024 * 1024).toLong()
        // Minimum number of processors needed for reliable operations
        const val MINIMUM_NUMBER_OF_PROCESSORS: Long = 2
    }
}

class AndroidPlatform(
    private val context: Context,
    private val directoryProvider: IDirectoryProvider
) : Platform {

    override val info: AppInfo
        get() = AppInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            model = Build.MODEL,
            device = Build.DEVICE,
            manufacturer = Build.MANUFACTURER
        )

    override val isNetworkAvailable: Boolean
        get() {
            val cm = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val actNet = cm.getNetworkCapabilities(net) ?: return false
            return when {
                actNet.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNet.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        }

    override val isStoreVersion: Boolean
        get() {
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            return !installer.isNullOrEmpty()
        }

    override fun restart() {
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

    override fun exit() {
        (context as? Activity)?.finishAffinity()
    }

    override fun configureLogger(minLogLevel: Int) {
        Logger.configure(
            directoryProvider.logFile,
            LogLevel.getLevel(minLogLevel)
        )
    }

    override suspend fun shareApp() {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.applicationInfo?.let { info ->
            val apkFile = File(info.publicSourceDir)
            val exportFile = File(
                directoryProvider.sharingDir, info.loadLabel(
                    context.packageManager
                ).toString() + "_" + pInfo.versionName + ".apk"
            )
            FileUtilities.copyFile(apkFile, exportFile)
            shareArchive(exportFile)
        }
    }

    override fun shareProject(file: File) {
        shareArchive(file)
    }

    override fun calculateSystemResources(): String {
        val am = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        var message = "System Resources:\n"
        val numProcessors = RuntimeWrapper.availableProcessors
        message += "Number of processors: $numProcessors " +
                "(${Platform.MINIMUM_NUMBER_OF_PROCESSORS} required)\n"
        val maxMem = RuntimeWrapper.maxMemory
        message += "JVM max memory: ${getFormattedSize(maxMem)} " +
                "(${getFormattedSize(Platform.MINIMUM_REQUIRED_RAM)} required)\n"

        val memoryInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memoryInfo)
        message += "Available memory on the system: " +
                "${getFormattedSize(memoryInfo.availMem)}\n"
        message += "Total memory on the system (getMemoryInfo): " +
                "${getFormattedSize(memoryInfo.totalMem)}\n"
        message += "Total memory on the system (/proc/meminfo): " +
                "${getFormattedSize(getTotalRam())}\n"
        message += "Low memory threshold on the system: " +
                "${getFormattedSize(memoryInfo.threshold)}\n"
        message += "Low memory state on the system: ${memoryInfo.lowMemory}\n"

        message += "Manufacturer: ${Build.MANUFACTURER}\n"
        message += "Model: ${info.model}\n"
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

    private fun shareArchive(file: File) {
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            setDataAndType(uri, "application/zip")

            putExtra(Intent.EXTRA_STREAM, uri)

            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.send_to)),
        )
    }
}