package com.grocerylist.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import com.grocerylist.app.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.ui.AppIcons
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(viewModel: SignInViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var resetEmail by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(painterResource(R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(56.dp))
            Text("Groceries", style = MaterialTheme.typography.displaySmall)
        }
        Text(
            if (state.creatingAccount) "Create your account" else "Sign in to your household's lists",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    try {
                        viewModel.signInWithGoogle(requestGoogleIdToken(context))
                    } catch (_: GetCredentialCancellationException) {
                        // User closed the account picker.
                    } catch (e: Exception) {
                        viewModel.showError(e)
                    }
                }
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(painterResource(R.drawable.ic_google_g), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Continue with Google")
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        if (state.creatingAccount) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::setEmail,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) AppIcons.Visibility else AppIcons.VisibilityOff,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
            isError = state.passwordTooShort,
            supportingText = if (state.creatingAccount) {
                {
                    Text(
                        if (state.passwordTooShort) {
                            "Too short. Use at least ${SignInState.MIN_PASSWORD_LENGTH} characters."
                        } else {
                            "At least ${SignInState.MIN_PASSWORD_LENGTH} characters"
                        },
                    )
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (state.creatingAccount) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.creatingAccount) {
            // Uses the same show/hide toggle as the password field above.
            OutlinedTextField(
                value = state.confirmPassword,
                onValueChange = viewModel::setConfirmPassword,
                label = { Text("Confirm password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                isError = state.showMismatch,
                supportingText = if (state.showMismatch) {
                    { Text("Passwords don't match. Type the same password in both fields.") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(
                onClick = { resetEmail = state.email.trim() },
                enabled = !state.busy,
                modifier = Modifier.align(Alignment.End),
            ) { Text("Forgot password?") }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(onClick = viewModel::submit, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.creatingAccount) "Create account" else "Sign in")
        }
        TextButton(onClick = viewModel::toggleMode, enabled = !state.busy) {
            Text(if (state.creatingAccount) "I already have an account" else "New here? Create an account")
        }
    }

    resetEmail?.let { email ->
        ResetPasswordDialog(
            initialEmail = email,
            state = state,
            onSend = viewModel::sendPasswordReset,
            onDismiss = {
                resetEmail = null
                viewModel.closePasswordReset()
            },
        )
    }
}

/** Asks for the email, then says the link is on its way in the same dialog. */
@Composable
private fun ResetPasswordDialog(
    initialEmail: String,
    state: SignInState,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    val sentTo = state.resetSentTo
    if (sentTo != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Check your email") },
            text = { Text("If there's an account for $sentTo, we've sent it a link to reset your password.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        )
        return
    }
    val canSend = email.isNotBlank() && !state.resetSending
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset your password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("We'll email you a link to choose a new password.")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    isError = state.resetError != null,
                    supportingText = state.resetError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend(email) }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(email) }, enabled = canSend) {
                Text(if (state.resetSending) "Sending…" else "Send link")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
