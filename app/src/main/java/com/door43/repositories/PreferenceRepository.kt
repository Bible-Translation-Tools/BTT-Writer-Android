package com.door43.repositories

import android.app.Application.MODE_PRIVATE
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.core.Migration
import com.door43.translationstudio.core.TranslationViewMode
import java.util.Locale
import javax.inject.Inject

class PreferenceRepository @Inject constructor(
    private val context: Context
) : IPreferenceRepository {

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
    private val defaultPrefs: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    private val privatePrefs
        get() = context.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)

    override fun getDefaultPref(key: String, defaultValue: String?): String? {
        return defaultPrefs.getString(key, defaultValue)
    }

    override fun getDefaultPref(key: String, defaultResource: Int): String? {
        return getDefaultPref(key, context.resources.getString(defaultResource))
    }

    override fun setDefaultPref(key: String, value: String?) {
        val editor = defaultPrefs.edit()
        if (value == null) {
            editor.remove(key)
        } else {
            editor.putString(key, value)
        }
        editor.apply()
    }

    override fun getPrivatePref(key: String, defaultValue: String?): String? {
        return privatePrefs.getString(key, defaultValue)
    }

    override fun setPrivatePref(key: String, value: String?) {
        val editor = privatePrefs.edit()
        if (value == null) {
            editor.remove(key)
        } else {
            editor.putString(key, value)
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
                ids[i] = Migration.migrateSourceTranslationSlug(ids[i])
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

    override fun getSelectedSourceTranslationId(targetTranslationId: String): String {
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