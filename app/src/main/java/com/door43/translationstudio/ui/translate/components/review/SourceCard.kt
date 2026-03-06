package com.door43.translationstudio.ui.translate.components.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R

@Composable
fun SourceCard(modifier: Modifier = Modifier) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val isLoading = false // Drive this from your state

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.weight(1f),
                    edgePadding = 0.dp
                ) {
                    Tab(selected = true, onClick = { /*TODO*/ }, text = { Text("Tab 1") })
                }
                
                IconButton(onClick = { /* Handle New Tab */ }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_tab_edit_secondary_24dp),
                        contentDescription = "New Tab"
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter))
                } else {
                    Text(
                        text = "this is the top ca\nsdf\ndf\nsf...",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}