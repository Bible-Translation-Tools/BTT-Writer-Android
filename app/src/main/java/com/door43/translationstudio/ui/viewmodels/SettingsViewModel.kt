package com.door43.translationstudio.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.BackupController
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.ResourceProvider
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_ALWAYS_SHARE
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_BACKUP_INTERVAL
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_CHECK_HARDWARE
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_COLOR_THEME
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_CONTENT_SERVER
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_CREATE_ACCOUNT_URL
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_ENABLE_TM_LINKS
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_GIT_SERVER_PORT
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_GOGS_API
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_INDEX_SQLITE_URL
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_LANGUAGES_URL
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_LOGGING_LEVEL
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_MEDIA_SERVER
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_READER_SERVER
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_SOURCE_TYPEFACE
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_SOURCE_TYPEFACE_SIZE
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_TM_URL
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_TRANSLATION_TYPEFACE
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_TRANSLATION_TYPEFACE_SIZE
import com.door43.translationstudio.ui.launchWithProgress
import com.door43.usecases.CheckForLatestRelease
import com.door43.usecases.DownloadLatestRelease
import com.door43.usecases.GogsLogout
import com.door43.usecases.MigrateTranslations
import com.door43.util.TTFAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import java.io.IOException

data class TypefaceOption(
    val displayName: String,
    val fileName: String
)

data class SettingsState(
    // General Prefs
    val themeNames: List<String> = emptyList(),
    val themeValues: List<String> = emptyList(),
    val currentThemeValue: String = "",
    val currentThemeName: String = "",
    val alwaysShareEnabled: Boolean = false,

    // Font Data
    val isFontsLoading: Boolean = true,
    val availableFonts: List<TypefaceOption> = emptyList(),
    val fontSizeNames: List<String> = emptyList(),
    val fontSizeValues: List<String> = emptyList(),
    val currentTranslationTypefaceValue: String = "",
    val currentTranslationFontName: String = "",
    val currentTranslationFontSizeValue: String = "",
    val currentTranslationFontSizeName: String = "",
    val currentSourceTypefaceValue: String = "",
    val currentSourceFontName: String = "",
    val currentSourceFontSizeValue: String = "",
    val currentSourceFontSizeName: String = "",

    // Server Prefs
    val contentServerNames: List<String> = emptyList(),
    val contentServerValues: List<String> = emptyList(),
    val currentGogsApiUrl: String = "",
    val currentContentServerValue: String = "",
    val currentContentServerName: String = "",
    val gitServerPort: String = "",
    val mediaServerUrl: String = "",
    val readerServerUrl: String = "",
    val accountCreationUrl: String = "",
    val languagesUrl: String = "",
    val indexSqliteUrl: String = "",
    val tmLinksUrl: String = "",

    // Advanced Prefs
    val checkHardwareEnabled: Boolean = false,
    val tmLinksEnabled: Boolean = false,
    val backupIntervalNames: List<String> = emptyList(),
    val backupIntervalValues: List<String> = emptyList(),
    val currentBackupIntervalValue: String = "",
    val currentBackupIntervalName: String = "",
    val loggingLevelNames: List<String> = emptyList(),
    val loggingLevelValues: List<String> = emptyList(),
    val currentLoggingLevelValue: String = "",
    val currentLoggingLevelName: String = "",

    val releaseResult: CheckForLatestRelease.Result? = null,
    val migrationFinished: Boolean = false,
    val loggedOut: Boolean = false
)

class SettingsViewModel(
    private val checkForLatestRelease: CheckForLatestRelease,
    private val downloadLatestRelease: DownloadLatestRelease,
    private val library: Door43Client,
    private val profile: Profile,
    private val logout: GogsLogout,
    private val migrateTranslations: MigrateTranslations,
    private val prefRepository: IPreferenceRepository,
    private val directoryProvider: IDirectoryProvider,
    private val assetsProvider: AssetsProvider,
    private val resourceProvider: ResourceProvider,
    private val backupController: BackupController
) : ViewModel(), ProgressOwner {

    private val progressManager = ProgressManager(viewModelScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadInitialPreferences()
        loadTypefaces()
    }

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    private fun loadInitialPreferences() {
        launchWithProgress {
            val themeNames = resourceProvider.getStringArray(R.array.pref_color_theme_titles)
            val themeValues = resourceProvider.getStringArray(R.array.pref_color_theme_values)
            val themeValue = prefRepository.getDefaultPref(
                KEY_PREF_COLOR_THEME,
                resourceProvider.getString(R.string.pref_default_color_theme)
            )
            val themeIndex = themeValues.indexOf(themeValue).takeIf { it >= 0 } ?: 1
            val themeName = themeNames.getOrNull(themeIndex) ?: themeValue

            // Fonts

            val targetFontValue = prefRepository.getDefaultPref(
                KEY_PREF_TRANSLATION_TYPEFACE,
                resourceProvider.getString(R.string.pref_default_translation_typeface)
            )
            val sourceFontValue = prefRepository.getDefaultPref(
                KEY_PREF_SOURCE_TYPEFACE,
                resourceProvider.getString(R.string.pref_default_translation_typeface)
            )

            val sizeNames = resourceProvider.getStringArray(R.array.pref_typeface_size_titles)
            val sizeValues = resourceProvider.getStringArray(R.array.pref_typeface_size_values)

            val translationSizeValue = prefRepository.getDefaultPref(
                KEY_PREF_TRANSLATION_TYPEFACE_SIZE,
                resourceProvider.getString(R.string.pref_default_typeface_size)
            )
            val sourceSizeValue = prefRepository.getDefaultPref(
                KEY_PREF_SOURCE_TYPEFACE_SIZE,
                resourceProvider.getString(R.string.pref_default_typeface_size)
            )

            val translationSizeIndex = sizeValues.indexOf(translationSizeValue).takeIf { it >= 0 } ?: 1
            val translationSizeName = sizeNames.getOrNull(translationSizeIndex) ?: translationSizeValue
            val sourceSizeIndex = sizeValues.indexOf(sourceSizeValue).takeIf { it >= 0 } ?: 1
            val sourceSizeName = sizeNames.getOrNull(sourceSizeIndex) ?: sourceSizeValue

            val alwaysShare = prefRepository.getDefaultPref(
                KEY_PREF_ALWAYS_SHARE,
                resourceProvider.getString(R.string.pref_default_always_share).toBoolean()
            )

            // Server

            val serverNames = resourceProvider.getStringArray(R.array.content_server_names_array)
            val serverValues = resourceProvider.getStringArray(R.array.content_server_values_array)

            val savedServerValue = prefRepository.getDefaultPref(
                KEY_PREF_CONTENT_SERVER,
                serverValues.firstOrNull() ?: "wacs_value"
            )
            val savedIndex = serverValues.indexOf(savedServerValue).takeIf { it >= 0 } ?: 0
            val savedServerName = serverNames.getOrNull(savedIndex) ?: ""
            val gitPort = prefRepository.getDefaultPref(
                KEY_PREF_GIT_SERVER_PORT,
                resourceProvider.getString(R.string.pref_default_git_server_port)
            )
            val gogsApiUrl = prefRepository.getDefaultPref(
                KEY_PREF_GOGS_API,
                resourceProvider.getString(R.string.pref_default_gogs_api)
            )
            val mediaServerUrl = prefRepository.getDefaultPref(
                KEY_PREF_MEDIA_SERVER,
                resourceProvider.getString(R.string.pref_default_media_server)
            )
            val readerServerUrl = prefRepository.getDefaultPref(
                KEY_PREF_READER_SERVER,
                resourceProvider.getString(R.string.pref_default_reader_server)
            )
            val accountCreationUrl = prefRepository.getDefaultPref(
                KEY_PREF_CREATE_ACCOUNT_URL,
                resourceProvider.getString(R.string.pref_default_create_account_url)
            )
            val languagesUrl = prefRepository.getDefaultPref(
                KEY_PREF_LANGUAGES_URL,
                resourceProvider.getString(R.string.pref_default_language_url)
            )
            val indexSqliteUrl = prefRepository.getDefaultPref(
                KEY_PREF_INDEX_SQLITE_URL,
                resourceProvider.getString(R.string.pref_default_index_sqlite_url)
            )
            val tmLinksUrl = prefRepository.getDefaultPref(
                KEY_PREF_TM_URL,
                resourceProvider.getString(R.string.pref_default_tm_url)
            )

            // Advanced

            val checkHardwareEnabled = prefRepository.getDefaultPref(
                KEY_PREF_CHECK_HARDWARE,
                true
            )
            val tmLinksEnabled = prefRepository.getDefaultPref(
                KEY_PREF_ENABLE_TM_LINKS,
                false
            )

            val intervalNames = resourceProvider.getStringArray(R.array.pref_backup_interval_titles)
            val intervalValues = resourceProvider.getStringArray(R.array.pref_backup_interval_values)
            val savedIntervalValue = prefRepository.getDefaultPref(
                KEY_PREF_BACKUP_INTERVAL,
                resourceProvider.getString(R.string.pref_default_backup_interval)
            )
            val intervalIndex = intervalValues.indexOf(savedIntervalValue).takeIf { it >= 0 } ?: 1
            val savedIntervalName = intervalNames.getOrNull(intervalIndex) ?: savedIntervalValue

            val loggingNames = resourceProvider.getStringArray(R.array.pref_logging_level_titles)
            val loggingValues = resourceProvider.getStringArray(R.array.pref_logging_level_values)
            val savedLoggingValue = prefRepository.getDefaultPref(
                KEY_PREF_LOGGING_LEVEL,
                resourceProvider.getString(R.string.pref_default_logging_level)
            )
            val loggingIndex = loggingValues.indexOf(savedLoggingValue).takeIf { it >= 0 } ?: 2
            val savedLoggingName = loggingNames.getOrNull(loggingIndex) ?: savedLoggingValue

            _state.update { state ->
                state.copy(
                    themeNames = themeNames,
                    themeValues = themeValues,
                    currentThemeValue = themeValue,
                    currentThemeName = themeName,
                    fontSizeNames = sizeNames,
                    fontSizeValues = sizeValues,
                    currentTranslationTypefaceValue = targetFontValue,
                    currentTranslationFontSizeValue = translationSizeValue,
                    currentTranslationFontSizeName = translationSizeName,
                    currentSourceTypefaceValue = sourceFontValue,
                    currentSourceFontSizeValue = sourceSizeValue,
                    currentSourceFontSizeName = sourceSizeName,
                    contentServerNames = serverNames,
                    contentServerValues = serverValues,
                    currentContentServerValue = savedServerValue,
                    currentContentServerName = savedServerName,
                    gitServerPort = gitPort,
                    currentGogsApiUrl = gogsApiUrl,
                    mediaServerUrl = mediaServerUrl,
                    readerServerUrl = readerServerUrl,
                    accountCreationUrl = accountCreationUrl,
                    languagesUrl = languagesUrl,
                    indexSqliteUrl = indexSqliteUrl,
                    tmLinksUrl = tmLinksUrl,
                    alwaysShareEnabled = alwaysShare,
                    checkHardwareEnabled = checkHardwareEnabled,
                    tmLinksEnabled = tmLinksEnabled,
                    backupIntervalNames = intervalNames,
                    backupIntervalValues = intervalValues,
                    currentBackupIntervalValue = savedIntervalValue,
                    currentBackupIntervalName = savedIntervalName,
                    loggingLevelNames = loggingNames,
                    loggingLevelValues = loggingValues,
                    currentLoggingLevelValue = savedLoggingValue,
                    currentLoggingLevelName = savedLoggingName
                )
            }
        }
    }

    private fun loadTypefaces() {
        launchWithProgress {
            val loadedFonts = mutableListOf<TypefaceOption>()

            try {
                val fileList = assetsProvider.list("fonts")?.asList()?.sortedBy { it.lowercase() }

                fileList?.forEach { fileName ->
                    val typefaceFile = directoryProvider.getAssetAsFile("fonts/$fileName")
                    if (typefaceFile != null) {
                        val analyzer = TTFAnalyzer()
                        var fontName = analyzer.getTtfFontName(typefaceFile.absolutePath)

                        if (fontName == null) {
                            fontName = typefaceFile.name.substringBeforeLast(".")
                        }
                        loadedFonts.add(TypefaceOption(displayName = fontName, fileName = fileName))
                    }
                }
            } catch (e: IOException) {
                Logger.e(this.javaClass.name, "failed to load font assets", e)
            }

            _state.update { state ->
                val translationFontName = loadedFonts.find {
                    it.fileName == state.currentTranslationTypefaceValue
                }?.displayName ?: "Default"
                val sourceFontName = loadedFonts.find {
                    it.fileName == state.currentSourceTypefaceValue
                }?.displayName ?: "Default"

                state.copy(
                    isFontsLoading = false,
                    availableFonts = loadedFonts,
                    currentTranslationFontName = translationFontName,
                    currentSourceFontName = sourceFontName
                )
            }
        }
    }

    fun updateColorTheme(newValue: String) {
        prefRepository.setDefaultPref(KEY_PREF_COLOR_THEME, newValue)

        val index = _state.value.themeValues.indexOf(newValue)
        val newName = _state.value.themeNames.getOrNull(index) ?: newValue

        _state.update {
            it.copy(
                currentThemeValue = newValue,
                currentThemeName = newName
            )
        }
    }

    fun updateTranslationTypeface(newFileName: String) {
        prefRepository.setDefaultPref(KEY_PREF_TRANSLATION_TYPEFACE, newFileName)

        val newName = _state.value.availableFonts.find {
            it.fileName == newFileName
        }?.displayName ?: "Default"

        _state.update {
            it.copy(
                currentTranslationTypefaceValue = newFileName,
                currentTranslationFontName = newName
            )
        }
    }

    fun updateTranslationFontSize(newValue: String) {
        prefRepository.setDefaultPref(KEY_PREF_TRANSLATION_TYPEFACE_SIZE, newValue)

        val index = _state.value.fontSizeValues.indexOf(newValue)
        val newName = _state.value.fontSizeNames.getOrNull(index) ?: newValue

        _state.update {
            it.copy(
                currentTranslationFontSizeValue = newValue,
                currentTranslationFontSizeName = newName
            )
        }
    }

    fun updateSourceFontSize(newValue: String) {
        prefRepository.setDefaultPref(KEY_PREF_SOURCE_TYPEFACE_SIZE, newValue)

        val index = _state.value.fontSizeValues.indexOf(newValue)
        val newName = _state.value.fontSizeNames.getOrNull(index) ?: newValue

        _state.update {
            it.copy(
                currentSourceFontSizeValue = newValue,
                currentSourceFontSizeName = newName
            )
        }
    }

    fun updateSourceTypeface(newFileName: String) {
        prefRepository.setDefaultPref(KEY_PREF_SOURCE_TYPEFACE, newFileName)

        val newName = _state.value.availableFonts.find {
            it.fileName == newFileName
        }?.displayName ?: "Default"

        _state.update {
            it.copy(
                currentSourceTypefaceValue = newFileName,
                currentSourceFontName = newName
            )
        }
    }

    fun setAlwaysShare(enabled: Boolean) {
        prefRepository.setDefaultPref(KEY_PREF_ALWAYS_SHARE, enabled)
        _state.update { it.copy(alwaysShareEnabled = enabled) }
    }

    fun checkForLatestRelease() {
        launchWithProgress(
            resourceProvider.getString(R.string.checking_for_updates)
        ) {
            val result = withContext(Dispatchers.IO) {
                checkForLatestRelease.execute()
            }
            _state.update {
                it.copy(releaseResult = result)
            }
        }
    }

    fun dismissUpdateResultDialog() {
        _state.update { it.copy(releaseResult = null) }
    }

    fun migrateOldAppData(appDataFolder: Uri) {
        launchWithProgress(
            resourceProvider.getString(R.string.migrating_translations)
        ) { handle ->
            withContext(Dispatchers.IO) {
                migrateTranslations.execute(appDataFolder) { progress, message ->
                    handle.update(progress, message)
                }
            }
            _state.update {
                it.copy(migrationFinished = true)
            }
        }
    }

    fun dismissMigrationFinishedDialog() {
        _state.update { it.copy(migrationFinished = false) }
    }

    fun updateGitServerPort(newPort: String) {
        prefRepository.setDefaultPref(KEY_PREF_GIT_SERVER_PORT, newPort)
        _state.update { it.copy(gitServerPort = newPort) }
    }

    fun updateGogsApiUrl(newUrl: String) {
        prefRepository.setDefaultPref(KEY_PREF_GOGS_API, newUrl)
        _state.update { it.copy(currentGogsApiUrl = newUrl) }
    }

    fun updateMediaServerUrl(newUrl: String) {
        prefRepository.setDefaultPref(KEY_PREF_MEDIA_SERVER, newUrl)
        _state.update { it.copy(mediaServerUrl = newUrl) }
    }

    fun updateReaderServerUrl(newUrl: String) {
        prefRepository.setDefaultPref(KEY_PREF_READER_SERVER, newUrl)
        _state.update { it.copy(readerServerUrl = newUrl) }
    }

    fun updateAccountCreationUrl(newUrl: String) {
        prefRepository.setDefaultPref(KEY_PREF_CREATE_ACCOUNT_URL, newUrl)
        _state.update { it.copy(accountCreationUrl = newUrl) }
    }

    fun updateLanguageUrl(newUrl: String) {
        prefRepository.setDefaultPref(KEY_PREF_LANGUAGES_URL, newUrl)
        _state.update { it.copy(languagesUrl = newUrl) }
        library.updateLanguageUrl(newUrl)
    }

    fun updateIndexSqliteUrl(newUrl: String) {
        prefRepository.setDefaultPref(KEY_PREF_INDEX_SQLITE_URL, newUrl)
        _state.update { it.copy(indexSqliteUrl = newUrl) }
    }

    fun updateTmLinksUrl(newUrl: String) {
        prefRepository.setDefaultPref(KEY_PREF_TM_URL, newUrl)
        _state.update { it.copy(tmLinksUrl = newUrl) }
    }

    fun setCheckHardwareEnabled(enabled: Boolean) {
        prefRepository.setDefaultPref(KEY_PREF_CHECK_HARDWARE, enabled)
        _state.update { it.copy(checkHardwareEnabled = enabled) }
    }

    fun setTmLinksEnabled(enabled: Boolean) {
        prefRepository.setDefaultPref(KEY_PREF_ENABLE_TM_LINKS, enabled)
        _state.update { it.copy(tmLinksEnabled = enabled) }
    }

    fun updateBackupInterval(newValue: String) {
        prefRepository.setDefaultPref(KEY_PREF_BACKUP_INTERVAL, newValue)

        val index = _state.value.backupIntervalValues.indexOf(newValue)
        val newName = _state.value.backupIntervalNames.getOrNull(index) ?: newValue

        _state.update {
            it.copy(
                currentBackupIntervalValue = newValue,
                currentBackupIntervalName = newName
            )
        }

        backupController.restartServiceIfRunning()
    }

    fun updateLoggingLevel(newValue: String) {
        prefRepository.setDefaultPref(KEY_PREF_LOGGING_LEVEL, newValue)

        val index = _state.value.loggingLevelValues.indexOf(newValue)
        val newName = _state.value.loggingLevelNames.getOrNull(index) ?: newValue

        _state.update {
            it.copy(
                currentLoggingLevelValue = newValue,
                currentLoggingLevelName = newName
            )
        }

        App.configureLogger(newValue.toInt())
    }

    fun downloadLatestRelease(release: CheckForLatestRelease.Release) {
        downloadLatestRelease.execute(release)
    }

    fun onContentServerChanged(newValue: String) {
        val serverValues = resourceProvider.getStringArray(R.array.content_server_values_array)
        val serverNames = resourceProvider.getStringArray(R.array.content_server_names_array)
        val gitPorts = resourceProvider.getStringArray(R.array.content_server_git_server_port_values_array)
        val gitApiUrls = resourceProvider.getStringArray(R.array.content_server_git_server_api_values_array)
        val mediaUrls = resourceProvider.getStringArray(R.array.content_server_media_server_values_array)
        val readerUrls = resourceProvider.getStringArray(R.array.content_server_reader_server_values_array)
        val createAccountUrls = resourceProvider.getStringArray(R.array.content_server_account_create_urls_array)
        val langNameUrls = resourceProvider.getStringArray(R.array.content_server_lang_names_url_array)
        val indexSqliteUrls = resourceProvider.getStringArray(R.array.content_server_index_sqlite_url_array)

        val index = serverValues.indexOf(newValue)
        if (index == -1) return

        prefRepository.setDefaultPref(KEY_PREF_CONTENT_SERVER, newValue)
        prefRepository.setDefaultPref(KEY_PREF_GIT_SERVER_PORT, gitPorts[index])
        prefRepository.setDefaultPref(KEY_PREF_GOGS_API, gitApiUrls[index])
        prefRepository.setDefaultPref(KEY_PREF_MEDIA_SERVER, mediaUrls[index])
        prefRepository.setDefaultPref(KEY_PREF_READER_SERVER, readerUrls[index])
        prefRepository.setDefaultPref(KEY_PREF_CREATE_ACCOUNT_URL, createAccountUrls[index])
        prefRepository.setDefaultPref(KEY_PREF_LANGUAGES_URL, langNameUrls[index])
        prefRepository.setDefaultPref(KEY_PREF_INDEX_SQLITE_URL, indexSqliteUrls[index])

        _state.update { state ->
            state.copy(
                currentContentServerValue = newValue,
                currentContentServerName = serverNames[index],
                gitServerPort = gitPorts[index],
                mediaServerUrl = mediaUrls[index],
                currentGogsApiUrl = gitApiUrls[index],
                readerServerUrl = readerUrls[index],
                accountCreationUrl = createAccountUrls[index],
                languagesUrl = langNameUrls[index],
                indexSqliteUrl = indexSqliteUrls[index]
            )
        }

        logout()
    }

    fun logout() {
        if (profile.gogsUser != null) {
            launchWithProgress(
                resourceProvider.getString(R.string.log_out)
            ) {
                withContext(Dispatchers.IO) {
                    logout.execute()
                    profile.logout()
                }
                _state.update { it.copy(loggedOut = true) }
            }
        }
    }
}