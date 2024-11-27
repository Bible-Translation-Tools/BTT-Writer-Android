package com.door43.translationstudio.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.ActivitySplashBinding
import com.door43.translationstudio.ui.viewmodels.SplashScreenViewModel
import com.door43.util.RuntimeWrapper
import org.unfoldingword.tools.logger.Logger

/**
 * This activity initializes the app
 */
class SplashScreenActivity : BaseActivity() {
    private var silentStart = true
    private var started = false

    private val viewModel: SplashScreenViewModel by viewModels()
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            loadingBar.max = 100
            loadingBar.isIndeterminate = true
        }

        setupObservers()

        // check minimum requirements
        val checkHardware = viewModel.checkHardware()
        if (checkHardware && !started) {
            val numProcessors = RuntimeWrapper.availableProcessors()
            val maxMem = RuntimeWrapper.maxMemory()

            if (numProcessors < App.MINIMUM_NUMBER_OF_PROCESSORS || maxMem < App.MINIMUM_REQUIRED_RAM) {
                silentStart = false
                AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                    .setTitle(R.string.slow_device)
                    .setMessage(R.string.min_hardware_req_not_met)
                    .setCancelable(false)
                    .setNegativeButton(R.string.do_not_show_again) { _, _ ->
                        viewModel.saveHardwareCheck(false)
                        start()
                    }
                    .setPositiveButton(R.string.label_continue) { _, _ -> start() }
                    .show()
            }
        }
    }

    private fun setupObservers() {
        viewModel.progress.observe(this) {
            if (it != null) {
                binding.loadingText.text = it.message
                binding.loadingBar.progress = it.progress
            }
        }
        viewModel.updateFinished.observe(this) {
            if (it == true) {
                openMainActivity()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (silentStart) {
            start()
        }
    }

    /**
     * Begins running tasks
     */
    private fun start() {
        started = true
        // check if we crashed
        val files = Logger.listStacktraces()
        if (files.isNotEmpty()) {
            val intent = Intent(this, CrashReporterActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        if (viewModel.progress.value == null) {
            viewModel.updateApp()
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_STARTED, started)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val STATE_STARTED = "started"
    }
}
