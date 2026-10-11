@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hilbert0619.coursetable.ui.words

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.hilbert0619.coursetable.R
import io.github.hilbert0619.coursetable.ui.components.AppIcon
import io.github.hilbert0619.coursetable.ui.components.ChoiceRow
import io.github.hilbert0619.coursetable.ui.components.Section
import io.github.hilbert0619.coursetable.ui.components.WarningRow
import kotlinx.coroutines.launch

private enum class AuthMode { SIGN_IN, SIGN_UP }

/** Sign in or create an account (iOS `SignInView`); the emailed link finishes sign-up and password resets. */
@Composable
fun SignInScreen(modifier: Modifier = Modifier) {
    val controller = LocalWordsController.current
    val text = WordsText(LocalContext.current.resources)
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(AuthMode.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val confirmNotice = stringResource(R.string.check_inbox_confirm)
    val resetNotice = stringResource(R.string.reset_link_sent)
    val canSubmit = "@" in email && password.length >= (if (mode == AuthMode.SIGN_UP) 8 else 1) && !working

    fun perform(block: suspend () -> Unit) {
        working = true
        error = null
        notice = null
        scope.launch {
            try {
                block()
            } catch (failure: Exception) {
                error = text.error(failure)
            } finally {
                working = false
            }
        }
    }

    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_words)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon(R.drawable.ic_tab_words_filled, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.learn_kaoyan_words), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.learn_kaoyan_words_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section(footer = if (mode == AuthMode.SIGN_UP) stringResource(R.string.sign_up_footer) else null) {
                ChoiceRow(AuthMode.entries, mode, label = {
                    stringResource(if (it == AuthMode.SIGN_IN) R.string.sign_in else R.string.create_account)
                }) {
                    mode = it
                    error = null
                    notice = null
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                )
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        perform {
                            when (mode) {
                                AuthMode.SIGN_IN -> controller.vocab.signIn(email, password)
                                // The emailed link opens the app and signs the new account in.
                                AuthMode.SIGN_UP -> if (controller.vocab.signUp(email, password)) notice = confirmNotice
                            }
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    if (working) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(if (mode == AuthMode.SIGN_IN) R.string.sign_in else R.string.create_account))
                    }
                }
                if (mode == AuthMode.SIGN_IN) {
                    TextButton(
                        onClick = {
                            perform {
                                controller.vocab.sendPasswordReset(email)
                                notice = resetNotice
                            }
                        },
                        enabled = "@" in email && !working,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text(stringResource(R.string.forgot_password)) }
                }
            }

            error?.let { message ->
                Section { WarningRow(message, blocking = true) }
            }
            notice?.let { message ->
                Section {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(R.drawable.ic_mail, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Shown after a password-reset link opened the app (iOS `NewPasswordView`). */
@Composable
fun NewPasswordDialog() {
    val controller = LocalWordsController.current
    val text = WordsText(LocalContext.current.resources)
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { controller.vocab.dismissNewPassword() },
        title = { Text(stringResource(R.string.choose_new_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.new_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Text(stringResource(R.string.at_least_8), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    working = true
                    scope.launch {
                        try {
                            controller.vocab.setNewPassword(password)
                        } catch (failure: Exception) {
                            error = text.error(failure)
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = password.length >= 8 && !working,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = { controller.vocab.dismissNewPassword() }) { Text(stringResource(R.string.cancel)) } },
    )
}
