package com.door43.translationstudio.ui.devtools

import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.AdapterView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.door43.translationstudio.App
import com.door43.translationstudio.App.Companion.udid
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.ActivityDeveloperToolsBinding
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.dialogs.ErrorLogDialog
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.viewmodels.DeveloperViewModel
import com.door43.translationstudio.ui.viewmodels.DeveloperViewModel.Companion.GB
import com.door43.translationstudio.ui.viewmodels.DeveloperViewModel.Companion.KB
import com.door43.translationstudio.ui.viewmodels.DeveloperViewModel.Companion.MB
import com.door43.util.StringUtilities
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.tools.logger.Logger
import java.text.DecimalFormat

@AndroidEntryPoint
class DeveloperToolsActivity : BaseActivity(), DeveloperViewModel.ToolsListener {
    private val adapter by lazy { ToolAdapter() }
    private var versionName: String? = null
    private var versionCode: String? = null
    private var progressDialog: ProgressHelper.ProgressDialog? = null

    private lateinit var binding: ActivityDeveloperToolsBinding
    private val viewModel: DeveloperViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeveloperToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel.setListener(this)

        with(binding) {
            // display app version
            try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)

                versionName = pInfo.versionName
                appVersionText.text = String.format(resources.getString(R.string.app_version_name), pInfo.versionName)
                versionCode = pInfo.versionCode.toString()
                appBuildNumberText.text = String.format(resources.getString(R.string.app_version_code), pInfo.versionCode)
            } catch (e: PackageManager.NameNotFoundException) {
                Logger.e(this.javaClass.name, "failed to get package name", e)
            }

            // display device id
            deviceUDIDText.text = String.format(resources.getString(R.string.app_udid), udid())

            // set up copy handlers
            appVersionText.setOnClickListener {
                StringUtilities.copyToClipboard(this@DeveloperToolsActivity, versionName)
                val snack = Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.copied_to_clipboard,
                    Snackbar.LENGTH_LONG
                )
                ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                snack.show()
            }

            appBuildNumberText.setOnClickListener {
                StringUtilities.copyToClipboard(this@DeveloperToolsActivity, versionCode)
                val snack = Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.copied_to_clipboard,
                    Snackbar.LENGTH_LONG
                )
                ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                snack.show()
            }

            deviceUDIDText.setOnClickListener {
                StringUtilities.copyToClipboard(this@DeveloperToolsActivity, udid())
                val snack = Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.copied_to_clipboard,
                    Snackbar.LENGTH_LONG
                )
                ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                snack.show()
            }

            // set up developer tools
            developerToolsListView.adapter = adapter
            developerToolsListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
                val tool = adapter.getItem(i)
                if (tool.isEnabled) {
                    tool.action()
                }
            }
        }

        setupObservers()

        viewModel.loadTools()
    }

    override fun onStart() {
        super.onStart()
        progressDialog = ProgressHelper.newInstance(
            supportFragmentManager,
            R.string.pref_title_developer_tools,
            false
        )
    }

    private fun setupObservers() {
        viewModel.progress.observe(this) {
            if (it != null) {
                progressDialog?.show()
                progressDialog?.setProgress(it.progress)
                progressDialog?.setMessage(it.message)
                progressDialog?.setMax(it.max)
            } else {
                progressDialog?.dismiss()
            }
        }
        viewModel.tools.observe(this) {
            it?.let { tools ->
                adapter.setTools(tools)
            }
        }
        viewModel.keysRegenerated.observe(this) {
            it?.let { regenerated ->
                if (regenerated) {
                    AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.success)
                        .setMessage("The SSH keys have been regenerated")
                        .setNeutralButton(R.string.dismiss, null)
                }
            }
        }
    }

    override fun onReadLog() {
        val dialog = ErrorLogDialog()
        val ft = supportFragmentManager.beginTransaction()
        val prev = supportFragmentManager.findFragmentByTag("dialog")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)
        dialog.show(ft, "dialog")
    }

    override fun onCheckSystemResources() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        var message = "System Resources:\n"
        val numProcessors = Runtime.getRuntime().availableProcessors()
        message += "Number of processors: $numProcessors (${App.MINIMUM_NUMBER_OF_PROCESSORS} required)\n"
        val maxMem = Runtime.getRuntime().maxMemory()
        val maxMemStr = getFormattedSize(maxMem)
        val minReqRamStr = getFormattedSize(App.MINIMUM_REQUIRED_RAM)
        message += "JVM max memory: $maxMemStr ($minReqRamStr required)\n"
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val availMemStr = getFormattedSize(info.availMem)
        message += "Available memory on the system: $availMemStr\n"
        var totalMemStr = "NA"
        totalMemStr = getFormattedSize(info.totalMem)
        message += "Total memory on the system (getMemoryInfo): $totalMemStr\n"
        val getTotalRamStr = getFormattedSize(viewModel.getTotalRam())
        message += "Total memory on the system (/proc/meminfo): $getTotalRamStr\n"

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

        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle("System Resources Check")
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton(R.string.label_close, null)
            .show()
    }

    override fun onDeleteLibrary() {
        val snack = Snackbar.make(
            findViewById(android.R.id.content),
            "The library content was deleted",
            Snackbar.LENGTH_LONG
        )
        ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
        snack.show()
    }

    /**
     * convert memory size in bytes to value with units (e.g. "128MB")
     * @param bytes
     * @return
     */
    private fun getFormattedSize(bytes: Long): String {
        if (bytes / GB > 0) {
            return formatWithUnits(bytes.toDouble() / GB, "GB")
        }
        if (bytes / MB > 0) {
            return formatWithUnits(bytes.toDouble() / MB, "MB")
        }
        if (bytes / KB > 0) {
            return formatWithUnits(bytes.toDouble() / KB, "KB")
        }
        return bytes.toString() + "B"
    }

    private fun formatWithUnits(size: Double, units: String): String {
        if (size >= 100) {
            return (size + 0.5).toLong().toString() + units
        }
        var decimalFormat = DecimalFormat("#.##")
        if (size >= 10) {
            decimalFormat = DecimalFormat("#.#")
        }
        return decimalFormat.format(size) + units
    }

    companion object {
        const val TASK_INDEX_CHUNK_MARKERS: String = "index_chunk_markers"
        val TAG: String = DeveloperToolsActivity::class.java.simpleName
        private const val TASK_INDEX_TA = "index-ta-task"
        private const val TASK_REGENERATE_KEYS = "regenerate-keys-task"
    }
}
