package com.door43.translationstudio.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.components.HomeSidebar
import com.door43.translationstudio.ui.components.LocalSnackbarHostState
import com.door43.translationstudio.ui.components.rememberHomeMenuItems
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.ExportDialog
import com.door43.translationstudio.ui.dialogs.FeedbackDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    component: HomeComponent
) {
    val profile: Profile = koinInject()
    var profileUser by remember { mutableStateOf(profile.currentUser) }

    val state by component.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val progress by component.progress.collectAsStateWithLifecycle()

    val dialogSlot by component.dialogSlot.subscribeAsState()

    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }

    val menuItems = rememberHomeMenuItems(
        onUpdateClick = { component.showUpdateLibraryDialog() },
        onImport = { component.showImportDialog() },
        onFeedback = { component.showFeedbackDialog() },
        onShareApp = component::shareApp,
        onLogout = component::logout,
        onSettings = component::openSettings
    )

    LaunchedEffect(component) {
        component.lastOpened?.let {
            component.openProject(it.id, false)
        }
    }

    LaunchedEffect(Unit) {
        component.event.collect { event ->
            when (event) {
                is HomeComponent.Event.SnackbarMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        profileUser = profile.currentUser
        component.lastFocusTargetTranslation?.let { translationId ->
            component.loadWithProgress(listOf(translationId))
            component.lastFocusTargetTranslation = null
        }

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
                    onClick = component::onNewTranslation,
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

                            TextButton(
                                onClick = component::logout,
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(4.dp),
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
                            if (progress == null) {
                                WelcomeScreen(
                                    onStartNewTranslation = component::onNewTranslation
                                )
                            }
                        } else {
                            TranslationListScreen(
                                component = component,
                                onProjectSelected = {
                                    component.openProject(it.translation.id, false)
                                },
                                onChangeLanguage = {
                                    component.onChangeTranslationLanguage(
                                        disabledLanguages = listOf(it.translation.targetLanguage.slug),
                                        translationId = it.translation.id
                                    )
                                },
                                onProjectPublish = component::publishProject
                            )
                        }
                    }
                }
            }
        }
    }

    dialogSlot.child?.instance?.let { child ->
        when (child) {
            is HomeComponent.DialogChild.Feedback -> FeedbackDialog(
                component = child.component,
                onDismiss = component::dismissDialog
            )
            is HomeComponent.DialogChild.Import -> ImportDialog(
                component = child.component,
                onDismiss = component::dismissDialog
            )
            is HomeComponent.DialogChild.UpdateLibrary -> UpdateLibraryDialog(
                component = child.component,
                onDismiss = component::dismissDialog
            )
            is HomeComponent.DialogChild.ImportUsfm -> ImportUsfmDialog(
                component = child.component,
                onDismiss = component::dismissDialog
            )
            is HomeComponent.DialogChild.DownloadSources -> DownloadSourcesDialog(
                component = child.component,
                onDismiss = component::dismissDialog
            )
            is HomeComponent.DialogChild.Export -> ExportDialog(
                component = child.component,
                onDismiss = component::dismissDialog
            )
        }
    }

    if (showExitConfirmation) {
        ConfirmDialog(
            title = stringResource(R.string.exit),
            message = stringResource(R.string.exit_confirmation),
            confirmText = stringResource(R.string.yes),
            dismissText = stringResource(R.string.no),
            onConfirm = component::exitApp,
            onDismiss = { showExitConfirmation = false }
        )
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}
