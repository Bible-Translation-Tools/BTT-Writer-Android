package com.door43.translationstudio.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ProgressDialog(
    progress: Float = -1f,
    message: String? = null,
    details: String? = null
) {
    Dialog(
        onDismissRequest = { /* only close automatically */ }
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                message?.let {
                    Text(text = it)
                }

                if (progress == -1f) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                details?.let {
                    Text(
                        text = it,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}