package com.door43.translationstudio.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.home.HomeActivity
import com.door43.translationstudio.ui.legal.TermsOfUseScreen
import com.door43.translationstudio.ui.legal.TermsOfUseViewModel
import com.door43.translationstudio.ui.viewmodels.SettingsViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class TermsOfUseActivity : BaseActivity() {

    override val isBootActivity: Boolean = true

    private val viewModel: TermsOfUseViewModel by viewModel {
        parametersOf(resources.getInteger(R.integer.terms_of_use_version))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (viewModel.initialState) {
            TermsOfUseViewModel.InitialState.GO_HOME -> {
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
                return
            }
            TermsOfUseViewModel.InitialState.FINISH -> {
                finish()
                return
            }
            TermsOfUseViewModel.InitialState.SHOW_TERMS -> {
            }
        }

        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val settingsModel by settingsViewModel.model.collectAsStateWithLifecycle()

            val lightValue = resources.getString(R.string.theme_value_light)
            val darkValue = resources.getString(R.string.theme_value_dark)
            val isDarkTheme = when (settingsModel.currentThemeValue) {
                lightValue -> false
                darkValue -> true
                else -> isSystemInDarkTheme()
            }

            LaunchedEffect(Unit) {
                viewModel.navigationEvent.collect { event ->
                    when (event) {
                        is TermsOfUseViewModel.NavigationEvent.NavigateToHome -> {
                            startActivity(
                                Intent(
                                    this@TermsOfUseActivity,
                                    HomeActivity::class.java
                                )
                            )
                            finish()
                        }
                        is TermsOfUseViewModel.NavigationEvent.NavigateToLogin -> {
                            startActivity(
                                Intent(
                                    this@TermsOfUseActivity,
                                    ProfileActivity::class.java
                                )
                            )
                            finish()
                        }
                    }
                }
            }

            AppTheme(darkTheme = isDarkTheme) {
                TermsOfUseScreen(
                    onAccept = { viewModel.acceptTerms() },
                    onReject = { viewModel.rejectTerms() }
                )
            }
        }
    }
}