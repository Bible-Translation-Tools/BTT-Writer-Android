package com.door43.translationstudio.ui.settings

import android.net.Uri
import com.door43.translationstudio.core.Progress
import com.door43.usecases.CheckForLatestRelease
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class TypefaceOption(
    val displayName: String,
    val fileName: String
)

interface SettingsComponent {

    val state: StateFlow<State>
    val progress: StateFlow<Progress?>
    val event: Flow<Event>

    val appVersion: String

    fun migrateOldAppData(uri: Uri)
    fun checkForLatestRelease()
    fun setCheckHardwareEnabled(enabled: Boolean)
    fun setTmLinksEnabled(enabled: Boolean)
    fun downloadLatestRelease(release: CheckForLatestRelease.Release)
    fun dismissUpdateResultDialog()
    fun updateColorTheme(newValue: String)
    fun updateTranslationTypeface(newFileName: String)
    fun updateTranslationFontSize(newValue: String)
    fun updateSourceTypeface(newValue: String)
    fun updateSourceFontSize(newValue: String)
    fun onContentServerChanged(newValue: String)
    fun updateGitServerPort(newValue: String)
    fun updateGogsApiUrl(newValue: String)
    fun updateMediaServerUrl(newValue: String)
    fun updateReaderServerUrl(newValue: String)
    fun updateAccountCreationUrl(newValue: String)
    fun updateLanguageUrl(newValue: String)
    fun updateIndexSqliteUrl(newValue: String)
    fun updateTmLinksUrl(newValue: String)
    fun updateBackupInterval(newValue: String)
    fun updateLoggingLevel(newValue: String)

    fun onNavigateBack()
    fun openDeveloperTools()
    fun onMigrationFinished()
    fun onLogout()

    data class State(
        // General Prefs
        val themeNames: List<String> = emptyList(),
        val themeValues: List<String> = emptyList(),
        val currentThemeValue: String = "",
        val currentThemeName: String = "",

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
        val migrationFinished: Boolean = false
    )

    sealed interface Event {
        data object OnLogout : Event
    }

    sealed interface Result {
        data object NavigateBack : Result
        data object OpenDeveloperTools : Result
        data object MigrationFinished : Result
        data object Logout : Result
        data class ThemeUpdated(val theme: String) : Result
    }
}