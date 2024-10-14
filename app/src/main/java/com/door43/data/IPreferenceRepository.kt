package com.door43.data

import androidx.annotation.StringRes
import com.door43.translationstudio.core.TranslationViewMode
import kotlin.reflect.KClass

interface IPreferenceRepository {
    /**
     * Returns and sets the last focused target translation
     * @return
     */
    var lastFocusTargetTranslation: String?

    /**
     * Returns and sets the last time we checked the server for updates
     * @return
     */
    var lastCheckedForUpdates: Long

    /**
     * Returns the value of a user preference
     * @param key
     * @return String
     */
    fun <T>getDefaultPref(key: String, type: Class<T>): T?

    /**
     * Returns the value of a user preference or the default value
     */
    fun <T>getDefaultPref(key: String, defaultValue: T, type: Class<T>): T

    /**
     * Sets the value of a default preference.
     * @param key
     * @param value if null the string will be removed
     */
    fun <T>setDefaultPref(key: String, value: T?, type: Class<T>)

    /**
     * Looks up a string preference in private storage
     * @param key
     * @return String
     */
    fun <T>getPrivatePref(key: String, type: Class<T>): T?

    /**
     * Returns the value of a private preference or the default value
     */
    fun <T>getPrivatePref(key: String, defaultValue: T, type: Class<T>): T

    /**
     * Sets the value of a private preference.
     * @param key
     * @param value if null the string will be removed
     */
    fun <T>setPrivatePref(key: String, value: T?, type: Class<T>)

    /**
     * Removes all settings for a target translation
     * @param targetTranslationId
     */
    fun clearTargetTranslationSettings(targetTranslationId: String)

    /**
     * Returns the last view mode of the target translation.
     * The default view mode will be returned if there is no recorded last view mode
     *
     * @param targetTranslationId
     * @return
     */
    fun getLastViewMode(targetTranslationId: String): TranslationViewMode

    /**
    * Sets the last opened view mode for a target translation
    * @param targetTranslationId
    * @param viewMode
    */
    fun setLastViewMode(targetTranslationId: String, viewMode: TranslationViewMode)

    /**
     * Sets the last focused chapter and frame for a target translation
     * @param targetTranslationId
     * @param chapterId
     * @param frameId
     */
    fun setLastFocus(targetTranslationId: String, chapterId: String?, frameId: String?)

    /**
     * Returns the id of the chapter that was last in focus for this target translation
     * @param targetTranslationId
     * @return
     */
    fun getLastFocusChapterId(targetTranslationId: String): String?

    /**
     * Returns the id of the frame that was last in focus for this target translation
     * @param targetTranslationId
     * @return
     */
    fun getLastFocusFrameId(targetTranslationId: String): String?

    /**
     * Returns an array of open source translation tabs on a target translation
     * @param targetTranslationId
     * @return
     */
    fun getOpenSourceTranslations(targetTranslationId: String): Array<String>

    /**
     * Adds a source translation to the list of open tabs on a target translation
     * @param targetTranslationId
     * @param sourceTranslationId
     */
    fun addOpenSourceTranslation(targetTranslationId: String, sourceTranslationId: String)

    /**
     * Removes a source translation from the list of open tabs on a target translation
     * @param targetTranslationId
     * @param sourceTranslationId
     */
    fun removeOpenSourceTranslation(targetTranslationId: String, sourceTranslationId: String?)

    /**
     * Returns the selected open source translation tab on the target translation
     * If there is no selection the first open tab will be set as the selected tab
     * @param targetTranslationId
     * @return
     */
    fun getSelectedSourceTranslationId(targetTranslationId: String): String?

    /**
     * Sets or removes the selected open source translation tab on a target translation
     * @param targetTranslationId
     * @param sourceTranslationId if null the selection will be unset
     */
    fun setSelectedSourceTranslation(targetTranslationId: String, sourceTranslationId: String?)
}

inline fun <reified T> IPreferenceRepository.getDefaultPref(key: String) =
    getDefaultPref(key, T::class.java)

inline fun <reified T> IPreferenceRepository.getDefaultPref(key: String, defaultValue: T) =
    getDefaultPref(key, defaultValue, T::class.java)

inline fun <reified T> IPreferenceRepository.setDefaultPref(key: String, value: T?) =
    setDefaultPref(key, value, T::class.java)

inline fun <reified T> IPreferenceRepository.getPrivatePref(key: String) =
    getPrivatePref(key, T::class.java)

inline fun <reified T> IPreferenceRepository.getPrivatePref(key: String, defaultValue: T) =
    getPrivatePref(key, defaultValue, T::class.java)

inline fun <reified T> IPreferenceRepository.setPrivatePref(key: String, value: T?) =
    setPrivatePref(key, value, T::class.java)
