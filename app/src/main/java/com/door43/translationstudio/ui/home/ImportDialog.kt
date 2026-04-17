package com.door43.translationstudio.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.BaseDialog
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.OverlayDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog

private const val IMPORT_INFO_URL =
    "http://help.door43.org/en/knowledgebase/9-translationstudio/docs/3-import-options"

@Composable
fun ImportDialog(
    component: ImportComponent,
    onDismiss: () -> Unit
) {
    val state by component.state.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val uriHandler = LocalUriHandler.current

    val openUSFMContent = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(component::importUsfm)
    }

    val openProjectContent = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            component.importProject(it, false)
        }
    }

    val openDirectory = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            component.importSource(it, false)
        }
    }

    var showImportBackupDialog by rememberSaveable { mutableStateOf(false) }
    var showImportServerDialog by rememberSaveable { mutableStateOf(false) }
    var showAuthDialog by rememberSaveable { mutableStateOf(false) }
    var unsupportedRepoAccepted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(component) {
        component.event.collect { event ->
            when (event) {
                is ImportComponent.Event.AuthRequested -> showAuthDialog = true
            }
        }
    }

    OverlayDialog(
        snackbarHostState = snackbarHostState,
        onDismiss = onDismiss,
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_import_options),
                    fontSize = 24.sp
                )

                IconButton(
                    onClick = { uriHandler.openUri(IMPORT_INFO_URL) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                ImportButton(
                    text = stringResource(R.string.import_from_door43),
                    icon = Icons.Default.Wifi,
                    onClick = {
                        showImportServerDialog = true
                    }
                )

                ImportButton(
                    text = stringResource(R.string.import_project_file),
                    onClick = {
                        openProjectContent.launch("*/*")
                    }
                )

                ImportButton(
                    text = stringResource(R.string.import_usfm_file),
                    onClick = {
                        openUSFMContent.launch("*/*")
                    }
                )

                ImportButton(
                    text = stringResource(R.string.import_source_text),
                    onClick = {
                        openDirectory.launch(null)
                    }
                )

                ImportButton(
                    text = stringResource(R.string.import_from_backup),
                    onClick = {
                        showImportBackupDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(end = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.title_cancel))
                }
            }
        }
    }

    if (showImportServerDialog) {
        ImportFromServerDialog(
            repositories = state.repositories,
            onSearch = component::searchRepositories,
            onRepoSelected = { repo ->
                component.importRepo(repo, accepted = false, overwrite = false)
            },
            onDismiss = {
                showImportServerDialog = false
                component.clearResult()
            }
        )
    }

    if (showImportBackupDialog) {
        ImportBackupDialog(
            backups = state.backups,
            onBackupSelected = {
                showImportBackupDialog = false
                component.importBackup(it)
            },
            onDismiss = { showImportBackupDialog = false }
        )
    }

    if (showAuthDialog) {
        ConfirmDialog(
            title = stringResource(R.string.error),
            message = stringResource(R.string.auth_failure_retry),
            onDismiss = { showAuthDialog = false },
            onConfirm = {
                showAuthDialog = false
                component.registerKeys()
            }
        )
    }

    state.repoToImport?.let { repo ->
        if (!repo.isSupported && !unsupportedRepoAccepted) {
            ConfirmDialog(
                title = stringResource(R.string.import_from_door43),
                message = stringResource(
                    R.string.import_warning,
                    repo.projectNameAlt
                ),
                onConfirm = {
                    unsupportedRepoAccepted = true
                    component.importRepo(repo, accepted = true, overwrite = false)
                },
                onDismiss = component::clearImportRepo,
                confirmText = stringResource(R.string.label_import)
            )
        }
    }

    state.mergeConflict?.let { result ->
        val message = stringResource(
            if (result.hasMergeConflict) {
                R.string.import_merge_conflict_project_name
            } else {
                R.string.import_project_already_exists
            },
            result.translation.id
        )
        BaseDialog(
            title = stringResource(R.string.merge_conflict_title),
            message = message,
            onDismiss = {
                unsupportedRepoAccepted = false
                component.clearMergeConflict()
            },
        ) { onBaseDismiss ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = {
                        result.onResolve()
                        onBaseDismiss()
                    }
                ) {
                    Text(stringResource(R.string.merge_projects_label))
                }
                TextButton(
                    onClick = {
                        result.onOverwrite()
                        onBaseDismiss()
                    }
                ) {
                    Text(stringResource(R.string.overwrite_projects_label))
                }
                TextButton(
                    onClick = {
                        result.onCancel()
                        onBaseDismiss()
                    }
                ) {
                    Text(stringResource(R.string.title_cancel))
                }
            }
        }
    }

    state.sourceConflict?.let { result ->
        ConfirmDialog(
            title = stringResource(R.string.confirm),
            message = result.error ?: "Unknown error",
            onConfirm = {
                result.uri?.let { uri ->
                    component.importSource(uri, true)
                }
                component.clearSourceConflict()
            },
            onDismiss = component::clearSourceConflict
        )
    }

    state.resultMessage?.let { (title, message) ->
        BaseDialog(
            title = title,
            message = message,
            onDismiss = {
                component.clearResult()
                showImportServerDialog = false
            }
        ) { onBaseDismiss ->
            TextButton(
                onClick = onBaseDismiss
            ) {
                Text(stringResource(R.string.dismiss))
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

@Composable
private fun ImportButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null
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
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Spacer(modifier = Modifier.size(18.dp))
                }

                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )

                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider()
    }
}
