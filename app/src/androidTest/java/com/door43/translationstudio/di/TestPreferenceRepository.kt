package com.door43.translationstudio.di

import android.app.Application.MODE_PRIVATE
import android.content.Context
import android.content.SharedPreferences
import com.door43.repositories.PreferenceRepository
import dagger.hilt.android.qualifiers.ApplicationContext

class TestPreferenceRepository(
    @ApplicationContext private val context: Context
) : PreferenceRepository(context) {

    override val defaultPrefs: SharedPreferences
        get() = context.getSharedPreferences(DEFAULT_PREFERENCES_NAME, MODE_PRIVATE)

    override val privatePrefs: SharedPreferences
        get() = context.getSharedPreferences(PRIVATE_PREFERENCES_NAME, MODE_PRIVATE)

    private companion object {
        const val DEFAULT_PREFERENCES_NAME = "test_default_prefs"
        const val PRIVATE_PREFERENCES_NAME = "test_private_prefs"
    }
}