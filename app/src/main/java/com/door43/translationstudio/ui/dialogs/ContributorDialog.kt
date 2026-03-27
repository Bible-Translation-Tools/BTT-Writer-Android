package com.door43.translationstudio.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.ui.legal.LegalDocumentDialog
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
    val snackBarHostState = remember { SnackbarHostState() }

    val duplicateSpeakerMessage = stringResource(R.string.duplicate_native_speaker)

    Dialog(
        onDismissRequest = onDismiss
    ) {
        var name by remember { mutableStateOf(contributor.name) }
        var hasAgreed by remember { mutableStateOf(!isNew) }

        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.padding(vertical = 16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimensionResource(id = R.dimen.dialog_content_margin))
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(id = R.string.add_contributor),
                        fontSize = dimensionResource(id = R.dimen.headline).value.sp,
                        color = colorResource(id = R.color.dark_primary_text),
                        modifier = Modifier.padding(bottom = dimensionResource(id = R.dimen.dialog_content_margin))
                    )

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
                                fontSize = dimensionResource(id = R.dimen.body).value.sp,
                                color = colorResource(id = R.color.dark_primary_text),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        if (isNew) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = dimensionResource(id = R.dimen.card_margin)),
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

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dimensionResource(id = R.dimen.dialog_controls_margin)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isNew) {
                            TextButton(onClick = { showDeleteContributorDialog = true }) {
                                Text(
                                    text = stringResource(R.string.label_delete).uppercase(),
                                    color = Color.Red
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.title_cancel).uppercase())
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
                                                    snackBarHostState.showSnackbar(duplicateSpeakerMessage)
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = name.isNotBlank() && hasAgreed,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(stringResource(R.string.menu_save).uppercase())
                            }
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackBarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                ) { data ->
                    Snackbar(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = MaterialTheme.colorScheme.surface,
                        snackbarData = data
                    )
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
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.secondary,
            containerColor = Color.Transparent
        )
    ) {
        Text(text = text, fontSize = 12.sp)
    }
}