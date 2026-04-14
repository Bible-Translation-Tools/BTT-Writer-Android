package com.door43.translationstudio.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Progress

@Composable
fun SplashLayout(
    progress: Progress?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.welcome),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.displaySmall
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (progress == null || progress.value < 0) {
            LinearProgressIndicator(
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(
                progress = { progress.value },
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = progress?.message ?: stringResource(R.string.loading),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}