package com.door43.translationstudio.di

import android.app.Application.MODE_PRIVATE
import android.content.Context
import android.content.SharedPreferences
import com.door43.repositories.PreferenceRepository
import dagger.hilt.android.qualifiers.ApplicationContext

class TestPreferenceRepository(
    @ApplicationContext private val context: Context
) : PreferenceRepository(context) {

    override val defaultPreferencesName = "test_default_prefs"
    override val privatePreferencesName = "test_private_prefs"

    override val githubBugReportRepoUrl = "/issues"
    override val githubRepoApiUrl = "/repo_api"
    override val questionnaireApiUrl = "/questionnaire/"
    override val rootCatalogApiUrl = "/catalog.json"

    override val defaultPrefs: SharedPreferences
        get() = context.getSharedPreferences(defaultPreferencesName, MODE_PRIVATE)

    override val privatePrefs: SharedPreferences
        get() = context.getSharedPreferences(privatePreferencesName, MODE_PRIVATE)
}