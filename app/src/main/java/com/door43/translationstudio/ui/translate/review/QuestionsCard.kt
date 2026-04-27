package com.door43.translationstudio.ui.translate.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.getComposeTextStyle
import org.bibletranslationtools.resourcecontainer.Language

@Composable
fun QuestionsCard(
    title: String,
    body: AnnotatedString,
    sourceLanguage: Language?,
    typography: Typography,
    onClose: () -> Unit
) {
    val titleStyle = typography.getComposeTextStyle(
        translationType = TranslationType.SOURCE,
        style = TextStyleType.TITLE,
        languageCode = sourceLanguage?.slug,
        direction = sourceLanguage?.direction
    )

    val bodyStyle = typography.getComposeTextStyle(
        translationType = TranslationType.SOURCE,
        style = TextStyleType.SUB,
        languageCode = sourceLanguage?.slug,
        direction = sourceLanguage?.direction
    )

    val scrollState = rememberScrollState()

    LaunchedEffect(body) {
        scrollState.scrollTo(0)
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(
            topStart = 16.dp,
            bottomStart = 16.dp,
            topEnd = 0.dp,
            bottomEnd = 0.dp
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.dismiss))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = stringResource(R.string.question),
                    style = titleStyle,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = title,
                    style = bodyStyle,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.answer),
                    style = titleStyle,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = body,
                    style = bodyStyle,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}