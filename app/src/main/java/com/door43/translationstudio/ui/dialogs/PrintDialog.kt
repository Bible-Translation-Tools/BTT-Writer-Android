package com.door43.translationstudio.ui.dialogs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.LabeledCheckbox

@Composable
fun PrintDialog(
    projectTitle: String,
    isObs: Boolean,
    onDismiss: () -> Unit,
    onPrint: (
        includeImages: Boolean,
        includeIncomplete: Boolean
    ) -> Unit
) {
    var includeImages by remember { mutableStateOf(isObs) }
    var includeIncomplete by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.print),
                        fontSize = 24.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = projectTitle,
                        fontSize = 20.sp,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (isObs) {
                        LabeledCheckbox(
                            label = stringResource(id = R.string.include_images),
                            checked = includeImages,
                            onCheckedChange = { includeImages = it }
                        )
                    }

                    LabeledCheckbox(
                        label = stringResource(id = R.string.include_incomplete_frames),
                        checked = includeIncomplete,
                        onCheckedChange = { includeIncomplete = it }
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.secondary,
                            containerColor = Color.Transparent
                        )
                    ) {
                        Text(stringResource(id = R.string.menu_cancel).uppercase())
                    }

                    Button(
                        onClick = {
                            onPrint(
                                includeImages,
                                includeIncomplete
                            )
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.secondary,
                            containerColor = Color.Transparent
                        )
                    ) {
                        Text(stringResource(id = R.string.print).uppercase())
                    }
                }
            }
        }
    }
}