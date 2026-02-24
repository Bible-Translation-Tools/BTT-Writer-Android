package com.door43.translationstudio.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.door43.data.IDirectoryProvider
import org.koin.android.ext.android.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.foreground.Foreground
import org.unfoldingword.tools.logger.Logger

/**
 * This should be extended by all activities in the app so that we can perform verification on
 * activities such as recovery from crashes.
 *
 */
abstract class BaseActivity : AppCompatActivity(), Foreground.Listener {

    private var foreground: Foreground? = null

    val directoryProvider: IDirectoryProvider by inject()
    val library: Door43Client by inject()

    protected open val isBootActivity: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            foreground = Foreground.get()
            foreground?.addListener(this)
        } catch (e: IllegalStateException) {
            Logger.i(this.javaClass.name, "Foreground was not initialized")
        }
    }

    override fun onResume() {
        super.onResume()

        if (!isBootActivity) {
            val crashFiles = Logger.listStacktraces()

            if (crashFiles.isNotEmpty()) {
                val intent = Intent(this, SplashScreenActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onBecameForeground() {
        if (!isBootActivity && !library.isLibraryDeployed) {
            Logger.w(this.javaClass.name, "The library was not deployed.")

            val intent = Intent(this, SplashScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onBecameBackground() {
    }

    override fun onDestroy() {
        foreground?.removeListener(this)
        super.onDestroy()
    }
}
