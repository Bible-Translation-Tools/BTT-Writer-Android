package com.door43.translationstudio.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.InfoDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import org.koin.androidx.compose.koinViewModel

private const val UPDATE_OPTIONS_HELP_URL =
    "http://help.door43.org/en/knowledgebase/9-translationstudio/docs/5-update-options"

@Composable
fun UpdateLibraryDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val viewModel: UpdateLibraryViewModel = koinViewModel()

    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val openIndexLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.onAction(UpdateAction.ImportIndex(it))
        }
    }

    var showIndexUpdatedDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                UpdateEvent.IndexUpdated -> showIndexUpdatedDialog = true
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 700.dp)
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.update_options),
                            fontSize = 24.sp
                        )
                        IconButton(
                            onClick = {
                                uriHandler.openUri(UPDATE_OPTIONS_HELP_URL)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info"
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.update_menu_requires_internet),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "internet",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    HorizontalDivider()

                    Column(modifier = Modifier.fillMaxWidth()) {
                        UpdateOptionItem(
                            stringResource(R.string.update_source)
                        ) {
                            viewModel.onAction(UpdateAction.UpdateSource)
                        }
                        UpdateOptionItem(
                            stringResource(R.string.import_index)
                        ) {
                            openIndexLauncher.launch("*/*")
                        }
                        UpdateOptionItem(
                            stringResource(R.string.download_index)
                        ) {
                            viewModel.onAction(UpdateAction.DownloadIndex)
                        }
                        UpdateOptionItem(
                            stringResource(R.string.download_sources)
                        ) {
                            viewModel.onAction(UpdateAction.DownloadSources)
                        }
                        UpdateOptionItem(
                            stringResource(R.string.update_languages)
                        ) {
                            viewModel.onAction(UpdateAction.UpdateLanguages)
                        }
                        UpdateOptionItem(
                            text = stringResource(R.string.check_app_update),
                            textColor = MaterialTheme.colorScheme.tertiary
                        ) {
                            viewModel.onAction(UpdateAction.CheckAppUpdate)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(R.string.menu_cancel))
                    }
                }
            }
        }
    }

    if (showIndexUpdatedDialog) {
        ConfirmDialog(
            title = stringResource(R.string.success),
            message = stringResource(R.string.download_index_success),
            onDismiss = App::restart,
            onConfirm = App::restart
        )
    }

    state.resultMessage?.let { (title, message) ->
        InfoDialog(
            title = title,
            message = message,
            onDismiss = { viewModel.onAction(UpdateAction.ClearResult) }
        ) { onInfoDismiss ->
            TextButton(
                onClick = onInfoDismiss
            ) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }

    state.latestRelease?.let { release ->
        ConfirmDialog(
            title = stringResource(R.string.apk_update_available),
            message = stringResource(R.string.download_latest_apk),
            onDismiss = {
                viewModel.onAction(UpdateAction.ClearLatestRelease)
            },
            onConfirm = {
                viewModel.onAction(UpdateAction.DownloadLatestRelease(release))
            }
        )
    }

    state.updateSourceResult?.let { result ->
        ConfirmDialog(
            title = stringResource(R.string.success),
            message = stringResource(
                R.string.update_sources_success,
                result.addedCount,
                result.updatedCount
            ),
            onDismiss = {
                viewModel.onAction(UpdateAction.ClearUpdateSourceResult)
            },
            onConfirm = {
                // Go to Download sources screen
                viewModel.onAction(UpdateAction.ClearUpdateSourceResult)
            }
        )
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value,
            details = it.details
        )
    }
}

@Composable
private fun UpdateOptionItem(
    text: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Text(
                text = text,
                color = textColor,
                modifier = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider()
    }
}