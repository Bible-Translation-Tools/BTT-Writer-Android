package com.door43.translationstudio.ui.dialogs

import android.app.Activity
import android.content.Intent
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.ImportUsfmActivity
import com.door43.translationstudio.ui.ImportUsfmActivity.Companion.EXTRA_USFM_IMPORT_URI
import com.door43.translationstudio.ui.components.OverlayDialog
import com.door43.translationstudio.ui.viewmodels.ImportAction
import com.door43.translationstudio.ui.viewmodels.ImportEvent
import com.door43.translationstudio.ui.viewmodels.ImportViewModel
import org.koin.androidx.compose.koinViewModel

private const val IMPORT_INFO_URL =
    "http://help.door43.org/en/knowledgebase/9-translationstudio/docs/3-import-options"

@Composable
fun ImportDialog(
    viewModel: ImportViewModel = koinViewModel(),
    onDismiss: () -> Unit,
    onMergeConflict: (String) -> Unit,
    onProjectImported: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val openUSFMContent = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.onAction(ImportAction.ImportUsfm(it))
        }
    }

    val importUSFMContent = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_CANCELED) {
            onProjectImported()
        }
    }

    val openProjectContent = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.onAction(ImportAction.ImportProject(it, false))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is ImportEvent.ImportUsfm -> {
                    val intent = Intent(context, ImportUsfmActivity::class.java)
                    intent.putExtra(EXTRA_USFM_IMPORT_URI, event.uri.toString())
                    importUSFMContent.launch(intent)
                }
                is ImportEvent.ResolveMergeConflict -> {
                    onMergeConflict(event.translationId)
                }
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
                        // onImportDoor43
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
                        // onImportSource
                    }
                )

                ImportButton(
                    text = stringResource(R.string.import_from_backup),
                    onClick = {
                        // onImportBackup
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

    state.mergeConflict?.let { result ->
        val message = if (result.hasMergeConflict) {
            stringResource(
                R.string.import_merge_conflict_project_name,
                result.importedSlug ?: ""
            )
        } else {
            stringResource(
                R.string.import_project_already_exists,
                result.importedSlug ?: ""
            )
        }
        InfoDialog(
            title = stringResource(R.string.merge_conflict_title),
            message = message,
            onDismiss = { viewModel.onAction(ImportAction.ClearResult) },
        ) { onDismiss ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = {
                        onDismiss()
                        viewModel.onAction(ImportAction.ApplyMergeConflict)
                    }
                ) {
                    Text(stringResource(R.string.merge_projects_label))
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        viewModel.onAction(
                            ImportAction.ImportProject(result.filePath, true)
                        )
                    }
                ) {
                    Text(stringResource(R.string.overwrite_projects_label))
                }
                TextButton(
                    onClick = {
                        onDismiss()
                        result.importedSlug?.let {
                            viewModel.onAction(ImportAction.ResetToMaster(it))
                        }
                    }
                ) {
                    Text(stringResource(R.string.title_cancel))
                }
            }
        }
    }

    state.resultMessage?.let { message ->
        InfoDialog(
            title = stringResource(R.string.import_from_storage),
            message = message,
            onDismiss = { viewModel.onAction(ImportAction.ClearResult) }
        ) { onDismiss ->
            TextButton(onClick = onDismiss) {
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