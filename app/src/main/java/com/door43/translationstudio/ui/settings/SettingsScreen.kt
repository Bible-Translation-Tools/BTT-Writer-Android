package com.door43.translationstudio.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.ActionDialog
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.legal.LegalDocumentDialog
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    appVersion: String,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDeveloperTools: () -> Unit,
    onMigrationFinished: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showGogsApiDialog by rememberSaveable { mutableStateOf(false) }
    var showTranslationFontDialog by rememberSaveable { mutableStateOf(false) }
    var showTranslationFontSizeDialog by rememberSaveable { mutableStateOf(false) }
    var showSourceFontDialog by rememberSaveable { mutableStateOf(false) }
    var showSourceFontSizeDialog by rememberSaveable { mutableStateOf(false) }

    var showContentServerDialog by rememberSaveable { mutableStateOf(false) }
    var showGitPortDialog by rememberSaveable { mutableStateOf(false) }
    var showMediaServerUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showReaderServerUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showAccountCreationUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showIndexSqliteUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showTmLinksUrlDialog by rememberSaveable { mutableStateOf(false) }

    var showBackupIntervalDialog by rememberSaveable { mutableStateOf(false) }
    var showLoggingLevelDialog by rememberSaveable { mutableStateOf(false) }

    var openLegalDocumentId by rememberSaveable { mutableStateOf<Int?>(null) }

    val openDirectoryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.migrateOldAppData(it) }
    }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            onNavigateToProfile()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.menu_settings))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "back",
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(contentPadding = paddingValues) {
            
            // --- GENERAL PREFERENCES ---
            item { PreferenceCategoryHeader(stringResource(R.string.pref_header_general)) }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_color_theme),
                    summary = state.currentThemeName,
                    onClick = { showThemeDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_translation_typeface),
                    summary = state.currentTranslationFontName,
                    onClick = { showTranslationFontDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_typeface_size),
                    summary = state.currentTranslationFontSizeName,
                    onClick = { showTranslationFontSizeDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_source_typeface),
                    summary = state.currentSourceFontName,
                    onClick = { showSourceFontDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_source_typeface_size),
                    summary = state.currentSourceFontSizeName,
                    onClick = { showSourceFontSizeDialog = true }
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
                    summary = state.currentContentServerName,
                    onClick = { showContentServerDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_git_server_port),
                    summary = state.gitServerPort,
                    onClick = { showGitPortDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_gogs_api),
                    summary = state.currentGogsApiUrl,
                    onClick = { showGogsApiDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_media_server),
                    summary = state.mediaServerUrl,
                    onClick = { showMediaServerUrlDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_reader_server),
                    summary = state.readerServerUrl,
                    onClick = { showReaderServerUrlDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_create_account_url),
                    summary = state.accountCreationUrl,
                    onClick = { showAccountCreationUrlDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_language_url),
                    summary = state.languagesUrl,
                    onClick = { showLanguageUrlDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_index_sqlite_url),
                    summary = state.indexSqliteUrl,
                    onClick = { showIndexSqliteUrlDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_tm_url),
                    summary = state.tmLinksUrl,
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
                    checked = state.checkHardwareEnabled,
                    onCheckedChange = { viewModel.setCheckHardwareEnabled(it) }
                )
            }

            item { HorizontalDivider() }

            item {
                CheckboxPreference(
                    title = stringResource(R.string.pref_title_enable_tm_links),
                    summary = stringResource(R.string.pref_description_enable_tm_links),
                    checked = state.tmLinksEnabled,
                    onCheckedChange = { viewModel.setTmLinksEnabled(it) }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = "Backup Interval",
                    summary = state.currentBackupIntervalName,
                    onClick = { showBackupIntervalDialog = true }
                )
            }

            item { HorizontalDivider() }

            item {
                ClickablePreference(
                    title = stringResource(R.string.pref_title_logging_level),
                    summary = state.currentLoggingLevelName,
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

    state.releaseResult?.let { resultObj ->
        if (resultObj.release != null) {
            ConfirmDialog(
                title = stringResource(R.string.apk_update_available),
                message = stringResource(R.string.download_latest_apk),
                onConfirm = {
                    viewModel.downloadLatestRelease(resultObj.release)
                    viewModel.dismissUpdateResultDialog()
                },
                onDismiss = { viewModel.dismissUpdateResultDialog() },
                confirmText = stringResource(R.string.label_ok)
            )
        } else {
            ActionDialog(
                onDismiss = { viewModel.dismissUpdateResultDialog() },
                title = stringResource(R.string.check_for_updates),
                message = stringResource(R.string.have_latest_app_update)
            ) {
                TextButton(
                    onClick = { viewModel.dismissUpdateResultDialog() }
                ) {
                    Text(stringResource(R.string.label_ok))
                }
            }
        }
    }

    if (state.migrationFinished) {
        ActionDialog(
            onDismiss = onMigrationFinished,
            title = "",
            message = stringResource(R.string.migrating_complete)
        ) {
            TextButton(onClick = onMigrationFinished) {
                Text(stringResource(R.string.label_ok))
            }
        }
    }

    if (showThemeDialog) {
        ListPreferenceDialog(
            title = stringResource(R.string.pref_title_color_theme),
            entries = state.themeNames,
            entryValues = state.themeValues,
            selectedValue = state.currentThemeValue,
            onValueSelected = { newValue ->
                showThemeDialog = false
                viewModel.updateColorTheme(newValue)
            },
            onDismissRequest = { showThemeDialog = false }
        )
    }

    if (showTranslationFontDialog) {
        if (state.isFontsLoading) {
            // Show a simple loading dialog if they click it before IO finishes
            ActionDialog(
                onDismiss = { showTranslationFontDialog = false },
                message = stringResource(R.string.loading)
            ){}
        } else {
            ListPreferenceDialog(
                title = stringResource(R.string.pref_title_translation_typeface),
                entries = state.availableFonts.map { it.displayName },
                entryValues = state.availableFonts.map { it.fileName },
                selectedValue = state.currentTranslationTypefaceValue,
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
            entries = state.fontSizeNames,
            entryValues = state.fontSizeValues,
            selectedValue = state.currentTranslationFontSizeValue,
            onValueSelected = { newSizeValue ->
                viewModel.updateTranslationFontSize(newSizeValue)
                showTranslationFontSizeDialog = false
            },
            onDismissRequest = { showTranslationFontSizeDialog = false }
        )
    }

    if (showSourceFontDialog) {
        if (state.isFontsLoading) {
            // Show a simple loading dialog if they click it before IO finishes
            ActionDialog(
                onDismiss = { showSourceFontDialog = false },
                message = stringResource(R.string.loading)
            ){}
        } else {
            ListPreferenceDialog(
                title = stringResource(R.string.pref_title_source_typeface),
                entries = state.availableFonts.map { it.displayName },
                entryValues = state.availableFonts.map { it.fileName },
                selectedValue = state.currentSourceTypefaceValue,
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
            entries = state.fontSizeNames,
            entryValues = state.fontSizeValues,
            selectedValue = state.currentSourceFontSizeValue,
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
            entries = state.contentServerNames,
            entryValues = state.contentServerValues,
            selectedValue = state.currentContentServerValue,
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
            initialValue = state.gitServerPort,
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
            initialValue = state.currentGogsApiUrl,
            onValueSaved = { newValue ->
                viewModel.updateGogsApiUrl(newValue)
            },
            onDismissRequest = { showGogsApiDialog = false }
        )
    }

    if (showMediaServerUrlDialog) {
        EditTextPreferenceDialog(
            title = stringResource(R.string.pref_title_media_server),
            initialValue = state.mediaServerUrl,
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
            initialValue = state.readerServerUrl,
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
            initialValue = state.accountCreationUrl,
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
            initialValue = state.languagesUrl,
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
            initialValue = state.indexSqliteUrl,
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
            initialValue = state.tmLinksUrl,
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
            entries = state.backupIntervalNames,
            entryValues = state.backupIntervalValues,
            selectedValue = state.currentBackupIntervalValue,
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
            entries = state.loggingLevelNames,
            entryValues = state.loggingLevelValues,
            selectedValue = state.currentLoggingLevelValue,
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

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}