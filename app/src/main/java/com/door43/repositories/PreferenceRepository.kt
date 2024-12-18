package com.door43.repositories

import android.app.Application.MODE_PRIVATE
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.door43.data.IPreferenceRepository
import com.door43.data.getPrivatePref
import com.door43.data.setPrivatePref
import com.door43.translationstudio.core.Migration
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import java.util.Locale

class PreferenceRepository (private val context: Context) : IPreferenceRepository {

    override var lastFocusTargetTranslation: String?
        get() = privatePrefs.getString(lastTranslation, null)
        set(targetTranslationId) {
            val editor = privatePrefs.edit()
            editor.putString(lastTranslation, targetTranslationId)
            editor.apply()
        }

    override var lastCheckedForUpdates: Long
        get() = privatePrefs.getLong(lastCheckedServerForUpdates, 0L)
        set(timeMillis) {
            val editor = privatePrefs.edit()
            editor.putLong(lastCheckedServerForUpdates, timeMillis)
            editor.apply()
        }

    /**
     * Returns an instance of the user preferences.
     * This is just the default shared preferences
     */
    private val defaultPrefs: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    private val privatePrefs: SharedPreferences
        get() = context.getSharedPreferences(privatePreferencesName, MODE_PRIVATE)

    override fun <T> getDefaultPref(key: String, type: Class<T>): T? {
        return getPref(key, type, defaultPrefs)
    }

    override fun <T> getDefaultPref(key: String, defaultValue: T, type: Class<T>): T {
        return getPref(key, defaultValue, type, defaultPrefs)
    }

    override fun <T> setDefaultPref(key: String, value: T?, type: Class<T>) {
        setPref(key, value, type, defaultPrefs)
    }

    override fun <T> getPrivatePref(key: String, type: Class<T>): T? {
        return getPref(key, type, privatePrefs)
    }

    override fun <T> getPrivatePref(key: String, defaultValue: T, type: Class<T>): T {
        return getPref(key, defaultValue, type, privatePrefs)
    }

    override fun <T> setPrivatePref(key: String, value: T?, type: Class<T>) {
        setPref(key, value, type, privatePrefs)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T>getPref(
        key: String,
        type: Class<T>,
        sharedPreferences: SharedPreferences
    ): T {
        return when (type) {
            java.lang.String::class.java -> sharedPreferences.getString(key, null)
            java.lang.Integer::class.java -> sharedPreferences.getInt(key, -1)
            java.lang.Long::class.java -> sharedPreferences.getLong(key, -1L)
            java.lang.Float::class.java -> sharedPreferences.getFloat(key, -1F)
            java.lang.Boolean::class.java -> sharedPreferences.getBoolean(key, false)
            else -> null
        } as T
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T>getPref(
        key: String,
        value: T,
        type: Class<T>,
        sharedPreferences: SharedPreferences
    ): T {
        return when (type) {
            java.lang.String::class.java -> sharedPreferences.getString(key, value as String)
            java.lang.Integer::class.java -> sharedPreferences.getInt(key, value as Int)
            java.lang.Long::class.java -> sharedPreferences.getLong(key, value as Long)
            java.lang.Float::class.java -> sharedPreferences.getFloat(key, value as Float)
            java.lang.Boolean::class.java -> sharedPreferences.getBoolean(key, value as Boolean)
            else -> value as String
        } as T
    }

    private fun <T>setPref(
        key: String,
        value: T?,
        type: Class<T>,
        sharedPreferences: SharedPreferences
    ) {
        val editor = sharedPreferences.edit()
        if (value != null) {
            when (type) {
                java.lang.String::class.java -> editor.putString(key, value as String?)
                java.lang.Integer::class.java -> editor.putInt(key, value as Int)
                java.lang.Long::class.java -> editor.putLong(key, value as Long)
                java.lang.Float::class.java -> editor.putFloat(key, value as Float)
                java.lang.Boolean::class.java -> editor.putBoolean(key, value as Boolean)
                else -> editor.apply()
            }
        } else {
            editor.remove(key)
        }
        editor.apply()
    }

    override fun clearTargetTranslationSettings(targetTranslationId: String) {
        val editor = privatePrefs.edit()
        editor.remove(selectedSourceTranslation + targetTranslationId)
        editor.remove(openSourceTranslations + targetTranslationId)
        editor.remove(lastFocusFrame + targetTranslationId)
        editor.remove(lastFocusChapter + targetTranslationId)
        editor.remove(lastViewMode + targetTranslationId)
        editor.apply()
    }

    override fun getLastViewMode(targetTranslationId: String): TranslationViewMode {
        try {
            val modeName = privatePrefs.getString(
                lastViewMode + targetTranslationId,
                TranslationViewMode.READ.name
            )
            return TranslationViewMode.valueOf(modeName!!.uppercase(Locale.getDefault()))
        } catch (_: Exception) {
        }
        return TranslationViewMode.READ
    }

    override fun setLastViewMode(targetTranslationId: String, viewMode: TranslationViewMode) {
        val editor = privatePrefs.edit()
        editor.putString(
            lastViewMode + targetTranslationId,
            viewMode.name.uppercase(Locale.getDefault())
        )
        editor.apply()
    }

    override fun setLastFocus(targetTranslationId: String, chapterId: String?, frameId: String?) {
        val editor = privatePrefs.edit()
        editor.putString(lastFocusChapter + targetTranslationId, chapterId)
        editor.putString(lastFocusFrame + targetTranslationId, frameId)
        editor.apply()
        lastFocusTargetTranslation = targetTranslationId
    }

    override fun getLastFocusChapterId(targetTranslationId: String): String? {
        return privatePrefs.getString(lastFocusChapter + targetTranslationId, null)
    }

    override fun getLastFocusFrameId(targetTranslationId: String): String? {
        return privatePrefs.getString(lastFocusFrame + targetTranslationId, null)
    }

    override fun getOpenSourceTranslations(targetTranslationId: String): Array<String> {
        val idSet = privatePrefs.getString(
            openSourceTranslations + targetTranslationId,
            ""
        )?.trim()

        if (idSet.isNullOrEmpty()) {
            return arrayOf()
        } else {
            val ids = idSet.split("\\|".toRegex()).toTypedArray()
            for (i in ids.indices) {
                Migration.migrateSourceTranslationSlug(ids[i])?.let {
                    ids[i] = it
                }
            }
            return ids
        }
    }

    override fun addOpenSourceTranslation(
        targetTranslationId: String,
        sourceTranslationId: String
    ) {
        val editor = privatePrefs.edit()
        val sourceTranslationIds = getOpenSourceTranslations(targetTranslationId)
        var newIdSet: String? = ""
        for (id in sourceTranslationIds) {
            if (id != sourceTranslationId) {
                newIdSet += "$id|"
            }
        }
        newIdSet += sourceTranslationId
        editor.putString(openSourceTranslations + targetTranslationId, newIdSet)
        editor.apply()
    }

    override fun removeOpenSourceTranslation(
        targetTranslationId: String,
        sourceTranslationId: String?
    ) {
        if (!sourceTranslationId.isNullOrEmpty()) {
            val sourceTranslationIds = getOpenSourceTranslations(targetTranslationId)
            var newIdSet: String? = ""
            for (id in sourceTranslationIds) {
                if (id != sourceTranslationId) {
                    if (newIdSet!!.isEmpty()) {
                        newIdSet = id
                    } else {
                        newIdSet += "|$id"
                    }
                } else if (id == getSelectedSourceTranslationId(targetTranslationId)) {
                    // unset the selected tab if it is removed
                    setSelectedSourceTranslation(targetTranslationId, null)
                }
            }
            setPrivatePref(Translator.OPEN_SOURCE_TRANSLATIONS + targetTranslationId, newIdSet)
        }
    }

    override fun getSelectedSourceTranslationId(targetTranslationId: String): String? {
        var selectedSourceTranslationId = privatePrefs.getString(
            selectedSourceTranslation + targetTranslationId,
            null
        )

        if (selectedSourceTranslationId.isNullOrEmpty()) {
            // default to first tab
            val openSourceTranslationIds = getOpenSourceTranslations(targetTranslationId)
            if (openSourceTranslationIds.isNotEmpty()) {
                selectedSourceTranslationId = openSourceTranslationIds[0]
                setSelectedSourceTranslation(targetTranslationId, selectedSourceTranslationId)
            }
        }
        return Migration.migrateSourceTranslationSlug(selectedSourceTranslationId)
    }

    override fun setSelectedSourceTranslation(
        targetTranslationId: String,
        sourceTranslationId: String?
    ) {
        val editor = privatePrefs.edit()
        if (!sourceTranslationId.isNullOrEmpty()) {
            editor.putString(
                selectedSourceTranslation + targetTranslationId,
                Migration.migrateSourceTranslationSlug(sourceTranslationId)
            )
        } else {
            editor.remove(selectedSourceTranslation + targetTranslationId)
        }
        editor.apply()
    }

    override fun getGithubBugReportRepo(): String {
        return getPrivatePref("github_bug_report_repo", githubBugReportRepoUrl)
    }

    override fun getGithubRepoApi(): String {
        return getPrivatePref("github_repo_api", githubRepoApiUrl)
    }

    override fun getQuestionnaireApi(): String {
        return getPrivatePref("questionnaire_api", questionnaireApiUrl)
    }

    override fun getRootCatalogApi(): String {
        return getPrivatePref("root_catalog_api", rootCatalogApiUrl)
    }
}