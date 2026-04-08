package com.door43.translationstudio.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun FeedbackDialog(
    viewModel: FeedbackViewModel = koinViewModel(),
    feedbackText: String = "",
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(feedbackText) }
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is FeedbackEvent.SnackbarMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    OverlayDialog(
        snackbarHostState = snackbarHostState,
        onDismiss = onDismiss
    ) { dismissWithKeyboard ->
        Text(
            text = stringResource(R.string.feedback),
            fontSize = 24.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "requires internet",
                modifier = Modifier.padding(end = 5.dp)
            )
            Text(
                text = stringResource(R.string.requires_internet),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = {
                Text(stringResource(R.string.bug_report))
            },
            modifier = Modifier.fillMaxWidth()
                .height(150.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { dismissWithKeyboard(onDismiss) }) {
                Text(
                    text = stringResource(R.string.title_cancel),
                    fontSize = 14.sp
                )
            }
            TextButton(
                onClick = {
                    viewModel.onAction(FeedbackAction.ReportBug(text))
                }
            ) {
                Text(
                    text = stringResource(R.string.confirm),
                    fontSize = 14.sp
                )
            }
        }
    }

    state.uploadError?.let { error ->
        InfoDialog(
            title = stringResource(R.string.upload_failed),
            message = error,
            onDismiss = { viewModel.onAction(FeedbackAction.ClearError) }
        ) { onDismiss ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.label_close))
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        viewModel.onAction(FeedbackAction.ReportBug(text))
                    }
                ) {
                    Text(stringResource(R.string.retry_label))
                }
            }
        }
    }

    state.release?.let { release ->
        InfoDialog(
            title = stringResource(R.string.apk_update_available),
            message = stringResource(R.string.upload_report_or_download_latest_apk),
            onDismiss = { viewModel.onAction(FeedbackAction.ClearRelease) }
        ) { onDismiss ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.title_cancel))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        onDismiss()
                        if (App.isStoreVersion) {
                            val appPackageName = context.packageName
                            try {
                                uriHandler.openUri("market://details?id=$appPackageName")
                            } catch (_: Exception) {
                                uriHandler.openUri("https://play.google.com/store/apps/details?id=$appPackageName")
                            }
                        } else {
                            uriHandler.openUri(release.downloadUrl)
                        }
                    }
                ) {
                    Text(stringResource(R.string.download_update))
                }

                TextButton(
                    onClick = {
                        onDismiss()
                        viewModel.onAction(FeedbackAction.UploadFeedback(text))
                    }
                ) {
                    Text(stringResource(R.string.label_continue))
                }
            }
        }
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}