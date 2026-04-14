package com.door43.translationstudio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.door43.translationstudio.ui.dialogs.OverlayDialog

@Composable
fun ListPreferenceDialog(
    title: String,
    entries: List<String>,
    entryValues: List<String>,
    selectedValue: String,
    onValueSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    OverlayDialog(
        onDismiss = onDismissRequest
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )

            LazyColumn(
                modifier = Modifier
                    .selectableGroup()
                    .padding(vertical = 8.dp)
            ) {
                itemsIndexed(entries) { index, entryDisplayName ->
                    val entryValue = entryValues.getOrNull(index) ?: return@itemsIndexed
                    val isSelected = entryValue == selectedValue

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onValueSelected(entryValue) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = entryDisplayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}