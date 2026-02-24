package com.door43.translationstudio.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.ProgressDialog
import com.door43.translationstudio.ui.legal.LegalDocumentDialog
import com.door43.translationstudio.ui.viewmodels.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    appVersion: String,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDeveloperTools: () -> Unit
) {
    val model by viewModel.model.collectAsStateWithLifecycle()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showGogsApiDialog by remember { mutableStateOf(false) }
    var showTranslationFontDialog by remember { mutableStateOf(false) }
    var showTranslationFontSizeDialog by remember { mutableStateOf(false) }
    var showSourceFontDialog by remember { mutableStateOf(false) }
    var showSourceFontSizeDialog by remember { mutableStateOf(false) }

    var showContentServerDialog by remember { mutableStateOf(false) }
    var showGitPortDialog by remember { mutableStateOf(false) }
    var showMediaServerUrlDialog by remember { mutableStateOf(false) }
    var showReaderServerUrlDialog by remember { mutableStateOf(false) }
    var showAccountCreationUrlDialog by remember { mutableStateOf(false) }
    var showLanguageUrlDialog by remember { mutableStateOf(false) }
    var showIndexSqliteUrlDialog by remember { mutableStateOf(false) }
    var showTmLinksUrlDialog by remember { mutableStateOf(false) }

    var showBackupIntervalDialog by remember { mutableStateOf(false) }
    var showLoggingLevelDialog by remember { mutableStateOf(false) }

    var openLegalDocumentId by remember { mutableStateOf<Int?>(null) }

    val openDirectoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.migrateOldAppData(it) }
    }

    LaunchedEffect(model.loggedOut) {
        if (model.loggedOut) {
            onNavigateToProfile()
        }
    }

    Scaffold { paddingValues ->
        LazyColumn(contentPadding = paddingValues) {
            
            // --- GENERAL PREFERENCES ---
            item { PreferenceCategoryHeader(stringResource(R.string.pref_header_general)) }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_color_theme),
                    summary = model.currentThemeName,
                    onClick = { showThemeDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_translation_typeface),
                    summary = model.currentTranslationFontName,
                    onClick = { showTranslationFontDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_typeface_size),
                    summary = model.currentTranslationFontSizeName,
                    onClick = { showTranslationFontSizeDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_source_typeface),
                    summary = model.currentSourceFontName,
                    onClick = { showSourceFontDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_source_typeface_size),
                    summary = model.currentSourceFontSizeName,
                    onClick = { showSourceFontSizeDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                CheckboxPreference(
                    title = stringResource(R.string.pref_title_always_share),
                    summary = stringResource(R.string.pref_description_always_share),
                    checked = model.alwaysShareEnabled,
                    onCheckedChange = { viewModel.setAlwaysShare(it) }
                )
            }

            item { HorizontalDivider() }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.version)) },
                    supportingContent = { Text(appVersion) }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.check_for_updates),
                    summary = stringResource(R.string.check_for_app_updates),
                    onClick = { viewModel.checkForLatestRelease() }
                )
            }

            // --- SERVER PREFERENCES ---
            item { PreferenceCategoryHeader(stringResource(R.string.pref_header_synchronization)) }

            item {
                ClickablePreference(
                    title = stringResource(R.string.content_server),
                    summary = model.currentContentServerName,
                    onClick = { showContentServerDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_git_server_port),
                    summary = model.gitServerPort,
                    onClick = { showGitPortDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_gogs_api),
                    summary = model.currentGogsApiUrl,
                    onClick = { showGogsApiDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_media_server),
                    summary = model.mediaServerUrl,
                    onClick = { showMediaServerUrlDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_reader_server),
                    summary = model.readerServerUrl,
                    onClick = { showReaderServerUrlDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_create_account_url),
                    summary = model.accountCreationUrl,
                    onClick = { showAccountCreationUrlDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_language_url),
                    summary = model.languagesUrl,
                    onClick = { showLanguageUrlDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_index_sqlite_url),
                    summary = model.indexSqliteUrl,
                    onClick = { showIndexSqliteUrlDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_tm_url),
                    summary = model.tmLinksUrl,
                    onClick = { showTmLinksUrlDialog = true }
                )
            }

            // --- LEGAL PREFERENCES ---
            item { PreferenceCategoryHeader(stringResource(R.string.pref_header_legal)) }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_license_agreement),
                    onClick = { openLegalDocumentId = R.string.license_pdf }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_statement_of_faith),
                    onClick = { openLegalDocumentId = R.string.statement_of_faith }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_translation_guidelines),
                    onClick = { openLegalDocumentId = R.string.translation_guidlines }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_software_licenses),
                    onClick = { openLegalDocumentId = R.string.software_licenses }
                )
            }

            // --- ADVANCED PREFERENCES ---
            item { PreferenceCategoryHeader(stringResource(R.string.pref_header_advanced)) }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_migrate_old_app),
                    summary = stringResource(R.string.pref_description_migrate_old_app),
                    onClick = { openDirectoryLauncher.launch(null) }
                )
            }

            item { HorizontalDivider() }

            item {
                CheckboxPreference(
                    title = stringResource(R.string.pref_title_check_hardware_requirements),
                    summary = stringResource(R.string.pref_description_check_hardware_requirements),
                    checked = model.checkHardwareEnabled,
                    onCheckedChange = { viewModel.setCheckHardwareEnabled(it) }
                )
            }

            item { HorizontalDivider() }

            item {
                CheckboxPreference(
                    title = stringResource(R.string.pref_title_enable_tm_links),
                    summary = stringResource(R.string.pref_description_enable_tm_links),
                    checked = model.tmLinksEnabled,
                    onCheckedChange = { viewModel.setTmLinksEnabled(it) }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = "Backup Interval",
                    summary = model.currentBackupIntervalName,
                    onClick = { showBackupIntervalDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_logging_level),
                    summary = model.currentLoggingLevelName,
                    onClick = { showLoggingLevelDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_developer_tools),
                    onClick = onNavigateToDeveloperTools
                )
            }
        }
    }

    model.progress?.let { progressObj ->
        ProgressDialog(
            message = progressObj.message ?: "",
            progressValue = (progressObj.progress.coerceIn(0, 100).toFloat()) / 100f
        )
    }

    model.releaseResult?.let { resultObj ->
        if (resultObj.release != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateResultDialog() },
                title = { Text(stringResource(R.string.apk_update_available)) },
                text = { Text(stringResource(R.string.download_latest_apk)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.downloadLatestRelease(resultObj.release)
                            viewModel.dismissUpdateResultDialog()
                        }
                    ) {
                        Text(stringResource(R.string.label_ok))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissUpdateResultDialog() }
                    ) {
                        Text(stringResource(R.string.title_cancel))
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateResultDialog() },
                title = { Text(stringResource(R.string.check_for_updates)) },
                text = { Text(stringResource(R.string.have_latest_app_update)) },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.dismissUpdateResultDialog() }
                    ) {
                        Text(stringResource(R.string.label_ok))
                    }
                }
            )
        }
    }

    if (model.migrationFinished) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissMigrationFinishedDialog() },
            title = {},
            text = { Text(stringResource(R.string.migrating_complete)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissMigrationFinishedDialog() }
                ) {
                    Text(stringResource(R.string.label_ok))
                }
            }
        )
    }

    if (showThemeDialog) {
        ListPreferenceDialog(
            title = stringResource(R.string.pref_title_color_theme),
            entries = model.themeNames,
            entryValues = model.themeValues,
            selectedValue = model.currentThemeValue,
            onValueSelected = { newValue ->
                viewModel.updateColorTheme(newValue)
                showThemeDialog = false
            },
            onDismissRequest = { showThemeDialog = false }
        )
    }

    if (showTranslationFontDialog) {
        if (model.isFontsLoading) {
            // Show a simple loading dialog if they click it before IO finishes
            AlertDialog(
                onDismissRequest = { showTranslationFontDialog = false },
                text = { CircularProgressIndicator() },
                confirmButton = {}
            )
        } else {
            ListPreferenceDialog(
                title = stringResource(R.string.pref_title_translation_typeface),
                entries = model.availableFonts.map { it.displayName },
                entryValues = model.availableFonts.map { it.fileName },
                selectedValue = model.currentTranslationTypefaceValue,
                onValueSelected = { newFileName ->
                    viewModel.updateTranslationTypeface(newFileName)
                    showTranslationFontDialog = false
                },
                onDismissRequest = { showTranslationFontDialog = false }
            )
        }
    }

    if (showTranslationFontSizeDialog) {
        ListPreferenceDialog(
            title = stringResource(R.string.pref_title_typeface_size),
            entries = model.fontSizeNames,
            entryValues = model.fontSizeValues,
            selectedValue = model.currentTranslationFontSizeValue,
            onValueSelected = { newSizeValue ->
                viewModel.updateTranslationFontSize(newSizeValue)
                showTranslationFontSizeDialog = false
            },
            onDismissRequest = { showTranslationFontSizeDialog = false }
        )
    }

    if (showSourceFontDialog) {
        if (model.isFontsLoading) {
            // Show a simple loading dialog if they click it before IO finishes
            AlertDialog(
                onDismissRequest = { showSourceFontDialog = false },
                text = { CircularProgressIndicator() },
                confirmButton = {}
            )
        } else {
            ListPreferenceDialog(
                title = stringResource(R.string.pref_title_source_typeface),
                entries = model.availableFonts.map { it.displayName },
                entryValues = model.availableFonts.map { it.fileName },
                selectedValue = model.currentSourceTypefaceValue,
                onValueSelected = { newFileName ->
                    viewModel.updateSourceTypeface(newFileName)
                    showSourceFontDialog = false
                },
                onDismissRequest = { showSourceFontDialog = false }
            )
        }
    }

    if (showSourceFontSizeDialog) {
        ListPreferenceDialog(
            title = stringResource(R.string.pref_title_source_typeface_size),
            entries = model.fontSizeNames,
            entryValues = model.fontSizeValues,
            selectedValue = model.currentSourceFontSizeValue,
            onValueSelected = { newSizeValue ->
                viewModel.updateSourceFontSize(newSizeValue)
                showSourceFontSizeDialog = false
            },
            onDismissRequest = { showSourceFontSizeDialog = false }
        )
    }

    if (showContentServerDialog) {
        ListPreferenceDialog(
            title = stringResource(R.string.content_server),
            entries = model.contentServerNames,
            entryValues = model.contentServerValues,
            selectedValue = model.currentContentServerValue,
            onValueSelected = { newServerValue ->
                viewModel.onContentServerChanged(newServerValue)
                showContentServerDialog = false
            },
            onDismissRequest = { showContentServerDialog = false }
        )
    }

    if (showGitPortDialog) {
        EditTextPreferenceDialog(
            title = stringResource(R.string.pref_title_git_server_port),
            initialValue = model.gitServerPort,
            onValueSaved = { newValue ->
                viewModel.updateGitServerPort(newValue)
                showGitPortDialog = false
            },
            onDismissRequest = { showGitPortDialog = false }
        )
    }

    if (showGogsApiDialog) {
        EditTextPreferenceDialog(
            title = stringResource(R.string.pref_title_gogs_api),
            initialValue = model.currentGogsApiUrl,
            onValueSaved = { newValue ->
                viewModel.updateGogsApiUrl(newValue)
            },
            onDismissRequest = { showGogsApiDialog = false }
        )
    }

    if (showMediaServerUrlDialog) {
        EditTextPreferenceDialog(
            title = stringResource(R.string.pref_title_media_server),
            initialValue = model.mediaServerUrl,
            onValueSaved = { newValue ->
                viewModel.updateMediaServerUrl(newValue)
                showMediaServerUrlDialog = false
            },
            onDismissRequest = { showMediaServerUrlDialog = false }
        )
    }

    if (showReaderServerUrlDialog) {
        EditTextPreferenceDialog(
            title = stringResource(R.string.pref_title_reader_server),
            initialValue = model.readerServerUrl,
            onValueSaved = { newValue ->
                viewModel.updateReaderServerUrl(newValue)
                showReaderServerUrlDialog = false
            },
            onDismissRequest = { showReaderServerUrlDialog = false }
        )
    }

    if (showAccountCreationUrlDialog) {
        EditTextPreferenceDialog(
            title = stringResource(R.string.pref_title_create_account_url),
            initialValue = model.accountCreationUrl,
            onValueSaved = { newValue ->
                viewModel.updateAccountCreationUrl(newValue)
                showAccountCreationUrlDialog = false
            },
            onDismissRequest = { showAccountCreationUrlDialog = false }
        )
    }

    if (showLanguageUrlDialog) {
        EditTextPreferenceDialog(
            title = stringResource(R.string.pref_title_language_url),
            initialValue = model.languagesUrl,
            onValueSaved = { newValue ->
                viewModel.updateLanguageUrl(newValue)
                showLanguageUrlDialog = false
            },
            onDismissRequest = { showLanguageUrlDialog = false }
        )
    }

    if (showIndexSqliteUrlDialog) {
        EditTextPreferenceDialog(
            title = stringResource(R.string.pref_title_index_sqlite_url),
            initialValue = model.indexSqliteUrl,
            onValueSaved = { newValue ->
                viewModel.updateIndexSqliteUrl(newValue)
                showIndexSqliteUrlDialog = false
            },
            onDismissRequest = { showIndexSqliteUrlDialog = false }
        )
    }

    if (showTmLinksUrlDialog) {
        EditTextPreferenceDialog(
            title = stringResource(R.string.pref_title_tm_url),
            initialValue = model.tmLinksUrl,
            onValueSaved = { newValue ->
                viewModel.updateTmLinksUrl(newValue)
                showTmLinksUrlDialog = false
            },
            onDismissRequest = { showTmLinksUrlDialog = false }
        )
    }

    if (showBackupIntervalDialog) {
        ListPreferenceDialog(
            title = "Backup Interval",
            entries = model.backupIntervalNames,
            entryValues = model.backupIntervalValues,
            selectedValue = model.currentBackupIntervalValue,
            onValueSelected = { newIntervalValue ->
                viewModel.updateBackupInterval(newIntervalValue)
                showBackupIntervalDialog = false
            },
            onDismissRequest = { showBackupIntervalDialog = false }
        )
    }

    if (showLoggingLevelDialog) {
        ListPreferenceDialog(
            title = stringResource(R.string.pref_title_logging_level),
            entries = model.loggingLevelNames,
            entryValues = model.loggingLevelValues,
            selectedValue = model.currentLoggingLevelValue,
            onValueSelected = { newLevelValue ->
                viewModel.updateLoggingLevel(newLevelValue)
                showLoggingLevelDialog = false
            },
            onDismissRequest = { showLoggingLevelDialog = false }
        )
    }

    openLegalDocumentId?.let { resourceId ->
        LegalDocumentDialog(
            htmlResourceId = resourceId,
            onDismissRequest = { openLegalDocumentId = null }
        )
    }
}