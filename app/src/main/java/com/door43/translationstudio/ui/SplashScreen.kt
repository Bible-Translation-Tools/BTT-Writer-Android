package com.door43.translationstudio.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.viewmodels.SplashEvent
import com.door43.translationstudio.ui.viewmodels.SplashScreenViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun SplashScreen(
    viewModel: SplashScreenViewModel = koinViewModel(),
    onNavigateToProfile: () -> Unit,
    onNavigateToCrashReporter: () -> Unit
) {
    val model by viewModel.model.collectAsStateWithLifecycle()

    val openDirectoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        viewModel.onDirectoryPicked(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SplashEvent.NavigateToProfile -> onNavigateToProfile()
                is SplashEvent.NavigateToCrashReporter -> onNavigateToCrashReporter()
                is SplashEvent.LaunchDirectoryPicker -> openDirectoryLauncher.launch(null)
            }
        }
    }

    SplashLayout(
        progressMessage = model.progress?.message ?: "",
        progressValue = model.progress?.progress
    )

    if (model.showHardwareWarning) {
        AlertDialog(
            onDismissRequest = { /* Cannot cancel */ },
            title = { Text(stringResource(R.string.slow_device)) },
            text = { Text(stringResource(R.string.min_hardware_req_not_met)) },
            confirmButton = {
                TextButton(onClick = viewModel::onHardwareWarningContinued) {
                    Text(stringResource(R.string.label_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onHardwareWarningDismissedAndSaved) {
                    Text(stringResource(R.string.do_not_show_again))
                }
            }
        )
    }

    if (model.showMigrationDialog) {
        AlertDialog(
            onDismissRequest = { /* Cannot cancel */ },
            title = { Text(stringResource(R.string.migrate_from_old_app)) },
            text = { Text(stringResource(R.string.migrate_from_old_app_description)) },
            confirmButton = {
                TextButton(onClick = viewModel::onMigrationAccepted) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onMigrationDeclined) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}