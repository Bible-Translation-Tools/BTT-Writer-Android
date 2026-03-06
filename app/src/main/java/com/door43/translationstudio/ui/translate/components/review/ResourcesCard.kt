package com.door43.translationstudio.ui.translate.components.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ResourcesCard(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedResourceTab by remember { mutableIntStateOf(0) }

    Card(
        modifier = modifier.clickable { onToggle() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        if (isExpanded) {
            Column(modifier = Modifier.fillMaxSize()) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedResourceTab,
                    edgePadding = 0.dp
                ) {
                    Tab(selected = true, onClick = { /*TODO*/ }, text = { Text("Resource 1") })
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("Resource content loads here...")
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Subject,
                    contentDescription = "Expand Resources",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}