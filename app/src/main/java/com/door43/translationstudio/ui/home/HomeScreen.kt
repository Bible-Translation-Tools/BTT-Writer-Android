package com.door43.translationstudio.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.components.HomeSidebar
import com.door43.translationstudio.ui.components.LocalSnackbarHostState
import com.door43.translationstudio.ui.components.rememberHomeMenuItems
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.FeedbackDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationActivity
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onSettings: () -> Unit,
    onOpenProject: (TranslationItem) -> Unit,
    onShareApp: (File) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onProjectPublish: (String) -> Unit,
    onMergeConflict: (String) -> Unit,
    onAppExit: () -> Unit
) {
    val profile: Profile = koinInject()
    var profileUser by remember { mutableStateOf(profile.currentUser) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val progress by viewModel.progress.collectAsStateWithLifecycle()

    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showUpdateLibraryDialog by rememberSaveable { mutableStateOf(false) }

    var projectToImport by remember { mutableStateOf<Uri?>(null) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }

    val errorString = stringResource(R.string.error)

    val menuItems = rememberHomeMenuItems(
        onUpdateClick = { showUpdateLibraryDialog = true },
        onImport = { showImportDialog = true },
        onFeedback = { showFeedbackDialog = true },
        onShareApp = {
            viewModel.onAction(HomeAction.ShareApp)
        },
        onLogout = {
            viewModel.onAction(HomeAction.Logout)
        },
        onSettings = onSettings
    )

    val newTranslationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onAction(HomeAction.LoadProjects)

        when(result.resultCode) {
            NewTargetTranslationActivity.RESULT_DUPLICATE -> {
                result.data?.getStringExtra(
                    NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID
                )?.let {
                    viewModel.onAction(HomeAction.ShowProjectExists(it))
                }
            }
            NewTargetTranslationActivity.RESULT_MERGE_CONFLICT -> {
                result.data?.getStringExtra(
                    NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID
                )?.let {
                    onMergeConflict(it)
                }
            }
            NewTargetTranslationActivity.RESULT_ERROR -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(errorString)
                }
            }
        }
    }

    val launchNewTranslation: () -> Unit = {
        val intent = Intent(
            context,
            NewTargetTranslationActivity::class.java
        )
        newTranslationLauncher.launch(intent)
    }

    val launchChangeLanguage: (TranslationItem) -> Unit = {
        val intent = Intent(
            context,
            NewTargetTranslationActivity::class.java
        )
        intent.putExtra(
            NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID,
            it.translation.id
        )
        intent.putExtra(
            NewTargetTranslationActivity.EXTRA_DISABLED_LANGUAGES, arrayOf(
                it.translation.targetLanguage.slug
            )
        )
        intent.putExtra(
            NewTargetTranslationActivity.EXTRA_CHANGE_TARGET_LANGUAGE_ONLY,
            true
        )
        newTranslationLauncher.launch(intent)
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is HomeEvent.SnackbarMessage -> snackbarHostState.showSnackbar(event.message)
                is HomeEvent.ShareApp -> onShareApp(event.file)
                is HomeEvent.ImportProject -> {
                    showImportDialog = true
                    projectToImport = event.uri
                }
                HomeEvent.OnLogout -> onLogout()
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        profileUser = profile.currentUser
        onPauseOrDispose {}
    }

    BackHandler(enabled = true) {
        showExitConfirmation = true
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = launchNewTranslation,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add"
                    )
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { paddingValues ->
            Row(modifier = Modifier.fillMaxSize()) {
                HomeSidebar(actions = menuItems)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 48.dp)
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.title_activity_target_translations),
                                fontSize = 20.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                            )

                            Text(
                                text = stringResource(R.string.current_user, profileUser),
                                fontSize = 18.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )

                            ElevatedButton(
                                onClick = {
                                    viewModel.onAction(HomeAction.Logout)
                                },
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.log_out),
                                    fontSize = 18.sp
                                )
                            }
                        }

                        HorizontalDivider()
                    }

                    Box(
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (state.translations.isEmpty()) {
                            WelcomeScreen(
                                onStartNewTranslation = launchNewTranslation
                            )
                        } else {
                            TranslationListScreen(
                                onProjectSelected = onOpenProject,
                                onChangeLanguage = launchChangeLanguage,
                                onMergeConflict = onMergeConflict,
                                onProjectPublish = onProjectPublish,
                                onLogin = onLogin,
                                onLogout = onLogout
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            onDismiss = { showFeedbackDialog = false }
        )
    }

    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onMergeConflict = {
                showImportDialog = false
                onMergeConflict(it)
            },
            onProjectImported = {
                viewModel.onAction(HomeAction.LoadProjects)
            },
            projectImportUri = projectToImport,
            onProjectUriConsumed = { projectToImport = null }
        )
    }

    if (showUpdateLibraryDialog) {
        UpdateLibraryDialog(
            onDismiss = { showUpdateLibraryDialog = false }
        )
    }

    if (showExitConfirmation) {
        ConfirmDialog(
            title = stringResource(R.string.exit),
            message = stringResource(R.string.exit_confirmation),
            onConfirm = onAppExit,
            onDismiss = {
                showExitConfirmation = false
            }
        )
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}