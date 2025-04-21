package com.door43.translationstudio.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.databinding.ActivityRegisterOfflineBinding
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RegisterOfflineActivity : AppCompatActivity() {
    @Inject lateinit var profile: Profile

    private lateinit var binding: ActivityRegisterOfflineBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterOfflineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            account.cancelButton.setOnClickListener { finish() }
            account.okButton.setOnClickListener {
                val fullName = account.fullName.text.toString().trim()
                if (fullName.isNotEmpty()) {
                    ProfileActivity.showPrivacyNotice(this@RegisterOfflineActivity) { _, _ ->
                        profile.login(fullName)
                        finish()
                    }
                } else {
                    // missing fields
                    val snack = Snackbar.make(
                        findViewById(android.R.id.content),
                        resources.getString(R.string.complete_required_fields),
                        Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                    snack.show()
                }
            }
            account.privacyNotice.setOnClickListener {
                ProfileActivity.showPrivacyNotice(
                    this@RegisterOfflineActivity, null
                )
            }
        }
    }
}
