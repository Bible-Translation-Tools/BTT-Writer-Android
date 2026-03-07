package com.door43.translationstudio.ui.translate.read

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.ui.components.SourceTabRow
import com.door43.translationstudio.ui.viewmodels.SourceTabItem

@Composable
fun ReadSourceCard(
    title: String,
    text: AnnotatedString,
    sourceTabs: List<SourceTabItem>,
    selectedSourceId: String?,
    onSourceTabClick: (String) -> Unit,
    onAddNewSourceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize().heightIn(min = 120.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SourceTabRow(
                    sourceTabs = sourceTabs,
                    selectedTag = selectedSourceId,
                    onSourceTabClick = onSourceTabClick,
                    onAddClick = onAddNewSourceClick
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}