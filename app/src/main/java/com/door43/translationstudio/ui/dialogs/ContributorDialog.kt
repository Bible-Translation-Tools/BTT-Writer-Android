package com.door43.translationstudio.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.core.TargetTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ContributorDialog(
    contributor: NativeSpeaker,
    targetTranslation: TargetTranslation,
    onDismiss: () -> Unit,
    onContributorsChanged: () -> Unit
) {
    val isNew by rememberUpdatedState(contributor.name.isEmpty())
    var openLegalDocumentId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showDeleteContributorDialog by rememberSaveable { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val duplicateSpeakerMessage = stringResource(R.string.duplicate_native_speaker)
    var name by remember { mutableStateOf(contributor.name) }
    var hasAgreed by remember { mutableStateOf(!isNew) }

    OverlayDialog(
        snackbarHostState = snackbarHostState,
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(id = R.string.add_contributor),
            fontSize = 24.sp,
            modifier = Modifier
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(id = R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isNew) hasAgreed = !hasAgreed
                    }
                    .padding(vertical = 8.dp)
            ) {
                Checkbox(
                    enabled = isNew,
                    checked = hasAgreed,
                    onCheckedChange = { hasAgreed = it }
                )
                Text(
                    text = stringResource(id = R.string.person_agrees_with_licenses),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (isNew) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TagButton(
                        text = stringResource(R.string.pref_title_license_agreement),
                        onClick = { openLegalDocumentId = R.string.license_pdf }
                    )
                    TagButton(
                        text = stringResource(R.string.pref_title_statement_of_faith),
                        onClick = { openLegalDocumentId = R.string.statement_of_faith }
                    )
                    TagButton(
                        text = stringResource(R.string.pref_title_translation_guidelines),
                        onClick = { openLegalDocumentId = R.string.translation_guidlines }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isNew) {
                TextButton(onClick = { showDeleteContributorDialog = true }) {
                    Text(
                        text = stringResource(R.string.label_delete),
                        color = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.title_cancel))
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                val duplicate = targetTranslation.getContributor(name)
                                when (duplicate) {
                                    null -> {
                                        targetTranslation.removeContributor(contributor)
                                        targetTranslation.addContributor(NativeSpeaker(name))
                                        onContributorsChanged()
                                    }
                                    contributor -> {
                                        onDismiss()
                                    }
                                    else -> {
                                        snackbarHostState.showSnackbar(duplicateSpeakerMessage)
                                    }
                                }
                            }
                        }
                    },
                    enabled = name.isNotBlank() && hasAgreed,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(stringResource(R.string.menu_save))
                }
            }
        }
    }

    openLegalDocumentId?.let { resourceId ->
        LegalDocumentDialog(
            htmlResourceId = resourceId,
            onDismissRequest = { openLegalDocumentId = null }
        )
    }

    if (showDeleteContributorDialog) {
        ConfirmDialog(
            title = stringResource(R.string.delete_translator_title),
            message = stringResource(R.string.confirm_delete_translator),
            onDismiss = { showDeleteContributorDialog = false },
            onConfirm = {
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        targetTranslation.removeContributor(contributor)
                    }
                    onContributorsChanged()
                }
            }
        )
    }
}

@Composable
fun TagButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text = text, fontSize = 14.sp)
    }
}