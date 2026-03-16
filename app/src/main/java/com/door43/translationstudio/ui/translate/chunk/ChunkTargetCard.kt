package com.door43.translationstudio.ui.translate.chunk

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.getComposeTextStyle
import com.door43.translationstudio.ui.translate.components.RichEditText


@Composable
fun ChunkTargetCard(
    title: String,
    rawText: String,
    displayText: AnnotatedString,
    targetTranslation: TargetTranslation,
    typography: Typography,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val titleStyle = typography.getComposeTextStyle(
        translationType = TranslationType.SOURCE,
        style = TextStyleType.SUB,
        languageCode = targetTranslation.targetLanguage.slug,
        direction = targetTranslation.targetLanguage.direction
    )

    val bodyStyle = typography.getComposeTextStyle(
        translationType = TranslationType.TARGET,
        style = TextStyleType.NORMAL,
        languageCode = targetTranslation.targetLanguage.slug,
        direction = targetTranslation.targetLanguage.direction
    )

    Card(
        modifier = modifier.fillMaxSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 16.dp, end = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            RichEditText(
                rawText = rawText,
                displayText = displayText.text,
                onRawTextChange = {
                    onTextChange(it)
                },
                textStyle = bodyStyle,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
