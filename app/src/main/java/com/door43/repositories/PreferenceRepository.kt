package com.door43.repositories

import android.app.Application.MODE_PRIVATE
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.door43.data.IPreferenceRepository
import com.door43.data.setPrivatePref
import com.door43.translationstudio.core.Migration
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import java.util.Locale

open class PreferenceRepository (private val context: Context) : IPreferenceRepository {

    private companion object {
        const val PREFERENCES_NAME = "com.door43.translationstudio.general"
        const val LAST_VIEW_MODE = "last_view_mode_"
        const val LAST_FOCUS_CHAPTER = "last_focus_chapter_"
        const val LAST_FOCUS_FRAME = "last_focus_frame_"
        const val OPEN_SOURCE_TRANSLATIONS = "open_source_translations_"
        const val SELECTED_SOURCE_TRANSLATION = "selected_source_translation_"
        const val LAST_CHECKED_SERVER_FOR_UPDATES = "last_checked_server_for_updates"
        const val LAST_TRANSLATION = "last_translation"
    }

    override var lastFocusTargetTranslation: String?
        get() = privatePrefs.getString(LAST_TRANSLATION, null)
        set(targetTranslationId) {
            val editor = privatePrefs.edit()
            editor.putString(LAST_TRANSLATION, targetTranslationId)
            editor.apply()
        }

    override var lastCheckedForUpdates: Long
        get() = privatePrefs.getLong(LAST_CHECKED_SERVER_FOR_UPDATES, 0L)
        set(timeMillis) {
            val editor = privatePrefs.edit()
            editor.putLong(LAST_CHECKED_SERVER_FOR_UPDATES, timeMillis)
            editor.apply()
        }

    /**
     * Returns an instance of the user preferences.
     * This is just the default shared preferences
     */
    protected open val defaultPrefs: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    protected open val privatePrefs: SharedPreferences
        get() = context.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)

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
        editor.remove(SELECTED_SOURCE_TRANSLATION + targetTranslationId)
        editor.remove(OPEN_SOURCE_TRANSLATIONS + targetTranslationId)
        editor.remove(LAST_FOCUS_FRAME + targetTranslationId)
        editor.remove(LAST_FOCUS_CHAPTER + targetTranslationId)
        editor.remove(LAST_VIEW_MODE + targetTranslationId)
        editor.apply()
    }

    override fun getLastViewMode(targetTranslationId: String): TranslationViewMode {
        try {
            val modeName = privatePrefs.getString(
                LAST_VIEW_MODE + targetTranslationId,
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
            LAST_VIEW_MODE + targetTranslationId,
            viewMode.name.uppercase(Locale.getDefault())
        )
        editor.apply()
    }

    override fun setLastFocus(targetTranslationId: String, chapterId: String?, frameId: String?) {
        val editor = privatePrefs.edit()
        editor.putString(LAST_FOCUS_CHAPTER + targetTranslationId, chapterId)
        editor.putString(LAST_FOCUS_FRAME + targetTranslationId, frameId)
        editor.apply()
        lastFocusTargetTranslation = targetTranslationId
    }

    override fun getLastFocusChapterId(targetTranslationId: String): String? {
        return privatePrefs.getString(LAST_FOCUS_CHAPTER + targetTranslationId, null)
    }

    override fun getLastFocusFrameId(targetTranslationId: String): String? {
        return privatePrefs.getString(LAST_FOCUS_FRAME + targetTranslationId, null)
    }

    override fun getOpenSourceTranslations(targetTranslationId: String): Array<String> {
        val idSet = privatePrefs.getString(
            OPEN_SOURCE_TRANSLATIONS + targetTranslationId,
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
        editor.putString(OPEN_SOURCE_TRANSLATIONS + targetTranslationId, newIdSet)
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
            SELECTED_SOURCE_TRANSLATION + targetTranslationId,
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
                SELECTED_SOURCE_TRANSLATION + targetTranslationId,
                Migration.migrateSourceTranslationSlug(sourceTranslationId)
            )
        } else {
            editor.remove(SELECTED_SOURCE_TRANSLATION + targetTranslationId)
        }
        editor.apply()
    }
}