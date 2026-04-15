package com.door43.translationstudio.ui.splash

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.ActionDialog

@Composable
fun SplashScreen(
    component: SplashComponent,
) {
    val state by component.state.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()

    val openDirToMigrateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        component.performMigrate(uri)
    }

    LaunchedEffect(component) {
        component.event.collect { event ->
            when (event) {
                is SplashComponent.Event.OpenDirToMigrate -> {
                    openDirToMigrateLauncher.launch(null)
                }
            }
        }
    }

    SplashLayout(progress = progress)

    if (state.showHardwareWarning) {
        ActionDialog(
            onDismiss = { /* Cannot cancel */ },
            title = stringResource(R.string.slow_device),
            message = stringResource(R.string.min_hardware_req_not_met),
        ) {
            TextButton(onClick = component::onHardwareWarningDismissedAndSaved) {
                Text(stringResource(R.string.do_not_show_again))
            }
            TextButton(onClick = component::onHardwareWarningContinued) {
                Text(stringResource(R.string.label_continue))
            }
        }
    }

    if (state.showMigrationDialog) {
        ActionDialog(
            onDismiss = { /* Cannot cancel */ },
            title = stringResource(R.string.migrate_from_old_app),
            message = stringResource(R.string.migrate_from_old_app_description)
        ) {
            TextButton(onClick = component::onMigrationDeclined) {
                Text(stringResource(R.string.no))
            }
            TextButton(onClick = component::onMigrationAccepted) {
                Text(stringResource(R.string.yes))
            }
        }
    }
}
