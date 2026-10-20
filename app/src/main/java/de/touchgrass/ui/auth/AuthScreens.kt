package de.touchgrass.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.ui.components.FormField

@Composable
fun LoginScreen(viewModel: LoginViewModel, onRegisterClick: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AuthForm(title = "Anmelden") {
        FormField(state.login, viewModel::onLoginChange, "Benutzername oder E-Mail")
        FormField(state.password, viewModel::onPasswordChange, "Passwort", isPassword = true)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::submit, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.loading) "Anmelden …" else "Anmelden")
        }
        TextButton(onClick = onRegisterClick) { Text("Noch kein Konto? Registrieren") }
    }
}

@Composable
fun RegisterScreen(viewModel: RegisterViewModel, onLoginClick: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AuthForm(title = "Konto anlegen") {
        FormField(state.username, viewModel::onUsernameChange, "Benutzername", state.fieldErrors["username"])
        FormField(state.email, viewModel::onEmailChange, "E-Mail", state.fieldErrors["email"], KeyboardType.Email)
        FormField(state.password, viewModel::onPasswordChange, "Passwort (mind. 8 Zeichen)", state.fieldErrors["password"], isPassword = true)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = viewModel::submit, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.loading) "Konto wird angelegt …" else "Konto anlegen")
        }
        TextButton(onClick = onLoginClick) { Text("Schon ein Konto? Anmelden") }
    }
}

@Composable
private fun AuthForm(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("TouchGrass 🌱", style = MaterialTheme.typography.headlineLarge)
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
