package com.door43.translationstudio.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.HomeSidebar
import com.door43.translationstudio.ui.components.SidebarAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    registerUrl: String,
    onLogin: () -> Unit,
    onRegisterOffline: () -> Unit,
    onSettings: () -> Unit,
    onCancel: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Row(modifier = Modifier.fillMaxSize()) {
        
        HomeSidebar(
            listOf(
                SidebarAction(
                    title = stringResource(R.string.action_settings),
                    icon = Icons.Default.Settings,
                    onClick = onSettings
                )
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.create_account_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
            )

            ProfileOptionCard(
                title = stringResource(R.string.login_doo43),
                subtitle = stringResource(R.string.requires_internet),
                onClick = onLogin
            )

            ProfileOptionCard(
                title = stringResource(R.string.register_door43),
                subtitle = stringResource(R.string.requires_internet),
                onClick = {
                    uriHandler.openUri(registerUrl)
                }
            )

            ProfileOptionCard(
                title = stringResource(R.string.create_offline_profile),
                subtitle = stringResource(R.string.still_possible_to_register_door43),
                onClick = onRegisterOffline
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp)
            ) {
                Text(stringResource(R.string.title_cancel))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileOptionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title, 
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle, 
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}