package com.door43.translationstudio.ui.publish

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.core.Validation
import com.door43.translationstudio.getComposeTextStyle
import com.door43.translationstudio.ui.AccentGreenLight

@Composable
fun ValidationCard(
    item: ValidationItem,
    typography: Typography,
    onReviewClick: (Validation.InvalidFrame) -> Unit
) {
    val isFrame = item.validation is Validation.ValidFrame
            || item.validation is Validation.InvalidFrame
    val horizontalPadding = if (isFrame) 48.dp else 0.dp

    val titleStyle = typography.getComposeTextStyle(
        translationType = TranslationType.TARGET,
        style = TextStyleType.SUB,
        languageCode = item.validation.titleLanguage.slug,
        direction = item.validation.titleLanguage.direction
    )

    val bodyStyle = typography.getComposeTextStyle(
        translationType = TranslationType.TARGET,
        style = TextStyleType.SUB,
        languageCode = (item.validation as? Validation.InvalidFrame)?.bodyLanguage?.slug,
        direction = (item.validation as? Validation.InvalidFrame)?.bodyLanguage?.direction
    )

    val inlineContentMap = mapOf(
        "note_icon" to InlineTextContent(
            Placeholder(
                width = bodyStyle.fontSize,
                height = bodyStyle.fontSize,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = "Note",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding)
    ) {
        if (item.validation.isRange) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 4.dp, end = 12.dp, bottom = 12.dp),
                elevation = CardDefaults.cardElevation(1.dp)
            ) { /* Shadow layer */ }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 4.dp),
            elevation = CardDefaults.cardElevation(3.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.validation.title,
                        style = titleStyle,
                        modifier = Modifier.weight(1f)
                    )

                    when (item.validation) {
                        is Validation.ValidFrame, is Validation.ValidGroup -> {
                            Icon(
                                imageVector = if (item.validation.isRange) {
                                    Icons.Default.DoneAll
                                } else Icons.Default.Done,
                                contentDescription = "Valid",
                                tint = AccentGreenLight
                            )
                        }
                        is Validation.InvalidGroup -> {
                            Icon(
                                imageVector = Icons.Default.Report,
                                contentDescription = "Invalid Group",
                                tint = MaterialTheme.colorScheme.errorContainer
                            )
                        }
                        is Validation.InvalidFrame -> {
                            Button(
                                onClick = { onReviewClick(item.validation) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(stringResource(R.string.review).uppercase())
                            }
                        }
                    }
                }

                if (item.validation is Validation.InvalidFrame) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.rendered,
                        style = bodyStyle,
                        inlineContent = inlineContentMap
                    )
                }
            }
        }
    }
}