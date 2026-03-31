package com.door43.translationstudio.ui.legal

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfUseScreen(
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    var openLegalDocumentId by remember { mutableStateOf<Int?>(null) }

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
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.license_deny).uppercase())
                }
                
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.license_accept).uppercase())
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
                Text(stringResource(R.string.view_license_agreement).uppercase())
            }

            Button(
                onClick = { openLegalDocumentId = R.string.translation_guidlines },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Text(stringResource(R.string.view_translation_guidelines).uppercase())
            }

            Button(
                onClick = { openLegalDocumentId = R.string.statement_of_faith },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.view_statement_of_faith).uppercase())
            }
        }
    }

    openLegalDocumentId?.let { resourceId ->
        LegalDocumentDialog(
            htmlResourceId = resourceId,
            onDismissRequest = { openLegalDocumentId = null }
        )
    }
}