package com.door43.translationstudio.ui.dialogs

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.ui.publish.TranslatorsSection

@Composable
fun ContributorsDialog(
    contributors: List<NativeSpeaker>,
    targetTranslation: TargetTranslation,
    onContributorsChanged: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.heightIn(max = 700.dp)
                .padding(32.dp)
        ) {
            TranslatorsSection(
                translators = contributors,
                targetTranslation = targetTranslation,
                onContributorsChanged = onContributorsChanged
            )
        }
    }
}