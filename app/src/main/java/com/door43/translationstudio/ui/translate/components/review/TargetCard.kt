package com.door43.translationstudio.ui.translate.components.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R

@Composable
fun TargetCard(modifier: Modifier = Modifier) {
    var isDone by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) } // Toggles between LinedEditText and Text

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // (Hidden Undo/Redo/Add Note buttons would go here wrapped in if(isEditing))

                Text(
                    text = "John 1:1-3 - Afaraf",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondary
                )

                IconButton(onClick = { isEditing = !isEditing }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mode_edit_secondary_24dp), // Replace
                        contentDescription = "Edit Translation"
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = "this is the editable body...",
                        onValueChange = {},
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SelectionContainer {
                        Text(text = "this is the keyboardless body...")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(checked = isDone, onCheckedChange = { isDone = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mark Done", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}