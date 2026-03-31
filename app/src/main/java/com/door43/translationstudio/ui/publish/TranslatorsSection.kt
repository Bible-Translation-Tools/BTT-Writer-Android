package com.door43.translationstudio.ui.publish

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.ui.dialogs.ContributorDialog

@Composable
fun TranslatorsSection(
    translators: List<NativeSpeaker>,
    targetTranslation: TargetTranslation,
    modifier: Modifier = Modifier,
    onNextClick: () -> Unit,
    onContributorsChanged: () -> Unit
) {
    var showPrivacyNoticeDialog by rememberSaveable { mutableStateOf(false) }
    var selectedContributorName by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { showPrivacyNoticeDialog = true }),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.names_will_be_public),
                fontSize = 20.sp
            )

            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "notice",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(translators, key = { it.name }) { translator ->
                TranslatorCard(
                    translator = translator,
                    onEditClick = { selectedContributorName = translator.name }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ElevatedButton(
                onClick = { selectedContributorName = "" },
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.add_contributor).uppercase()
                )
            }
            Button(
                onClick = onNextClick,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.next).uppercase(),
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showPrivacyNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyNoticeDialog = false },
            title = { Text(stringResource(R.string.privacy_notice)) },
            text = { Text(stringResource(R.string.publishing_privacy_notice)) },
            confirmButton = {
                TextButton(onClick = { showPrivacyNoticeDialog = false }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    selectedContributorName?.let { name ->
        ContributorDialog(
            contributor = NativeSpeaker(name),
            targetTranslation = targetTranslation,
            onDismiss = {
                selectedContributorName = null
                onContributorsChanged()
            },
            onContributorsChanged = {
                selectedContributorName = null
                onContributorsChanged()
            }
        )
    }
}