package com.door43.translationstudio.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.door43.translationstudio.App.Companion.closeKeyboard
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.databinding.ActivityLoginDoor43Binding
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.viewmodels.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LoginDoor43Activity : AppCompatActivity() {
    @Inject
    lateinit var profile: Profile

    private lateinit var binding: ActivityLoginDoor43Binding
    private var progressDialog: ProgressHelper.ProgressDialog? = null

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginDoor43Binding.inflate(layoutInflater)
        setContentView(binding.root)

        progressDialog = ProgressHelper.newInstance(
            supportFragmentManager,
            R.string.logging_in,
            false
        )

        with(binding) {
            account.cancelButton.setOnClickListener { finish() }
            account.okButton.setOnClickListener {
                closeKeyboard(this@LoginDoor43Activity)
                val username = account.username.text.toString().trim()
                val password = account.password.text.toString()
                val fullName = profile.fullName

                viewModel.login(username, password, fullName)
            }
        }

        setupObservers()
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
        viewModel.loginResult.observe(this) {
            it?.let { result ->
                if (result.user != null) {
                    // save gogs user to profile
                    if (result.user.fullName.isNullOrEmpty()) {
                        // TODO: 4/15/16 if the fullname has not been set we need to ask for it
                        // this is our quick fix to get the full name for now
                        result.user.fullName = result.user.username
                    }
                    profile.login(result.user.fullName, result.user)
                    finish()
                } else {
                    val networkAvailable = isNetworkAvailable
                    // login failed
                    val hand = Handler(Looper.getMainLooper())
                    hand.post {
                        val messageId = if (networkAvailable) {
                            R.string.double_check_credentials
                        } else {
                            R.string.internet_not_available
                        }
                        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                            .setTitle(R.string.error)
                            .setMessage(messageId)
                            .setPositiveButton(R.string.label_ok, null)
                            .show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog = null
    }
}
