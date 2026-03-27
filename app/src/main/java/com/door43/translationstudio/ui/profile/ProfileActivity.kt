package com.door43.translationstudio.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.settings.SettingsActivity
import org.koin.android.ext.android.inject

class ProfileActivity : BaseActivity() {
    val profile: Profile by inject()
    val preRepository: IPreferenceRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (profile.loggedIn) {
            startActivity(Intent(this, TermsOfUseActivity::class.java))
            finish()
            return
        }

        val registerUrl = preRepository.getDefaultPref(
            SettingsActivity.Companion.KEY_PREF_CREATE_ACCOUNT_URL,
            getString(R.string.pref_default_create_account_url)
        )

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ProfileScreen(
                        registerUrl = registerUrl,
                        onLogin = {
                            startActivity(
                                Intent(
                                    this@ProfileActivity,
                                    LoginDoor43Activity::class.java
                                )
                            )
                        },
                        onRegisterOffline = {
                            startActivity(
                                Intent(
                                    this@ProfileActivity,
                                    RegisterOfflineActivity::class.java
                                )
                            )
                        },
                        onSettingsClick = {
                            startActivity(
                                Intent(
                                    this@ProfileActivity,
                                    SettingsActivity::class.java
                                )
                            )
                        },
                        onCancel = {
                            finishAffinity()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (profile.loggedIn) {
            startActivity(Intent(this, TermsOfUseActivity::class.java))
            finish()
            return
        }
    }
}