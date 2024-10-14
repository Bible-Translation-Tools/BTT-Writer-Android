package com.door43.translationstudio.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.door43.data.IDirectoryProvider
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.foreground.Foreground
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

/**
 * This should be extended by all activities in the app so that we can perform verification on
 * activities such as recovery from crashes.
 *
 */
@AndroidEntryPoint
abstract class BaseActivity : AppCompatActivity(), Foreground.Listener {
    private var foreground: Foreground? = null

    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            foreground = Foreground.get()
            foreground?.addListener(this)
        } catch (e: IllegalStateException) {
            Logger.i(this.javaClass.name, "Foreground was not initialized")
        }
    }

    public override fun onResume() {
        super.onResume()

        if (!isBootActivity) {
            val crashFiles = Logger.listStacktraces()

            if (crashFiles.isNotEmpty()) {
                // restart
                val intent = Intent(this, SplashScreenActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
    }

    private val isBootActivity: Boolean
        get() = (this is TermsOfUseActivity
                || this is SplashScreenActivity
                || this is CrashReporterActivity)

    public override fun onDestroy() {
        if (this.foreground != null) {
            foreground!!.removeListener(this)
        }
        super.onDestroy()
    }

    override fun onBecameForeground() {
        // check if the index had been loaded
        if (!isBootActivity && !library.isLibraryDeployed) {
            Logger.w(this.javaClass.name, "The library was not deployed.")
            // restart
            val intent = Intent(this, SplashScreenActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onBecameBackground() {
    }
}
