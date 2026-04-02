package com.door43.translationstudio.ui.home

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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.components.HomeSidebar
import com.door43.translationstudio.ui.components.rememberHomeMenuItems
import com.door43.translationstudio.ui.dialogs.FeedbackDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onLogout: () -> Unit,
    onAddTargetTranslation: () -> Unit,
    onSettings: () -> Unit
) {
    val profile: Profile = koinInject()
    var profileUser by remember { mutableStateOf(profile.currentUser) }

    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }

    val menuItems = rememberHomeMenuItems(
        onUpdateClick = {},
        onImport = {},
        onFeedback = { showFeedbackDialog = true },
        onShareApp = {},
        onLogout = {
            viewModel.onAction(HomeAction.Logout)
        },
        onSettings = onSettings
    )

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is HomeEvent.SnackbarMessage -> snackbarHostState.showSnackbar(event.message)
                HomeEvent.OnLogout -> onLogout()
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        profileUser = profile.currentUser
        onPauseOrDispose {}
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTargetTranslation,
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
            SnackbarHost(hostState = snackbarHostState) {
                Snackbar(
                    snackbarData = it,
                    actionColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                            onStartNewTranslation = onAddTargetTranslation
                        )
                    } else {
                        TranslationListScreen(
                            projects = state.translations,
                            onSortColumnChange = {},
                            onSortProjectChange = {}
                        )
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

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}