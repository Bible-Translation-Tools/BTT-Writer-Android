package com.door43.translationstudio.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.ProgressDialog
import com.door43.translationstudio.ui.viewmodels.LoginViewModel
import org.koin.androidx.compose.koinViewModel
import org.unfoldingword.gogsclient.User

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = koinViewModel(),
    profileFullName: String?,
    isNetworkAvailable: Boolean,
    onLoginSuccess: (user: User) -> Unit,
    onCancel: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    
    var errorMessageId by rememberSaveable { mutableStateOf<Int?>(null) }

    val model by viewModel.model.collectAsStateWithLifecycle()

    LaunchedEffect(model.result) {
        model.result?.let { result ->
            if (result.user != null) {
                onLoginSuccess(result.user)
            } else {
                errorMessageId = if (isNetworkAvailable) {
                    R.string.double_check_credentials
                } else {
                    R.string.internet_not_available
                }
            }
        }
    }

    val submitLogin = {
        keyboardController?.hide()
        viewModel.login(username.trim(), password, profileFullName)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
    ) {
        Text(
            text = stringResource(R.string.server_account),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next
            ),
            visualTransformation = VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submitLogin() }),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, contentDescription = "Toggle Password")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.title_cancel),
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Button(
                onClick = submitLogin,
                enabled = username.isNotBlank() && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text(stringResource(R.string.label_continue))
            }
        }
    }

    model.progress?.let { progress ->
        ProgressDialog(
            message = progress.message ?: stringResource(R.string.loading),
            progressValue = progress.progress.toFloat()
        )
    }

    errorMessageId?.let {
        AlertDialog(
            onDismissRequest = { errorMessageId = null },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(stringResource(it)) },
            confirmButton = {
                TextButton(onClick = { errorMessageId = null }) {
                    Text(stringResource(R.string.label_ok))
                }
            }
        )
    }
}