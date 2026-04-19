package com.door43.translationstudio.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.dialogs.LegalDocumentDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfUseScreen(component: TermsOfUseComponent) {
    var openLegalDocumentId by rememberSaveable { mutableStateOf<Int?>(null) }

    val progress by component.progress.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.terms_title),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimary
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = component::rejectTerms,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.license_deny))
                }
                
                Button(
                    onClick = component::acceptTerms,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.license_accept))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.terms),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Button(
                onClick = { openLegalDocumentId = R.string.license_pdf },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.view_license_agreement))
            }

            Button(
                onClick = { openLegalDocumentId = R.string.translation_guidlines },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.view_translation_guidelines))
            }

            Button(
                onClick = { openLegalDocumentId = R.string.statement_of_faith },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.view_statement_of_faith))
            }
        }
    }

    openLegalDocumentId?.let { resourceId ->
        LegalDocumentDialog(
            htmlResourceId = resourceId,
            onDismissRequest = { openLegalDocumentId = null }
        )
    }

    progress?.let { progress ->
        ProgressDialog(
            message = progress.message,
            progress = progress.value
        )
    }
}