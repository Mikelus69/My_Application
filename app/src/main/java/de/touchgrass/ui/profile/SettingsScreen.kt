package de.touchgrass.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.data.dto.GoalType
import de.touchgrass.ui.components.FormField

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        Text("Einstellungen", style = MaterialTheme.typography.headlineMedium)
        if (state.loading) {
            CircularProgressIndicator()
            return@Column
        }
        state.loadError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::load) { Text("Erneut versuchen") }
            return@Column
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text("Profil", style = MaterialTheme.typography.titleMedium)
        FormField(state.weight, viewModel::onWeightChange, "Körpergewicht in kg (freiwillig)", state.fieldErrors["weightKg"], KeyboardType.Decimal)
        Text("Wochenziel")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalType.entries.forEach { type ->
                FilterChip(
                    selected = state.goalType == type,
                    onClick = { viewModel.onGoalTypeChange(type) },
                    label = { Text(type.label()) },
                )
            }
        }
        FormField(state.goalTarget, viewModel::onGoalTargetChange, "${state.goalType.label()} pro Woche", state.fieldErrors["goalTarget"], KeyboardType.Number)
        Button(onClick = viewModel::saveProfile, modifier = Modifier.fillMaxWidth()) { Text("Speichern") }

        HorizontalDivider()
        Text("Passwort ändern", style = MaterialTheme.typography.titleMedium)
        FormField(state.oldPassword, viewModel::onOldPasswordChange, "Bisheriges Passwort", state.fieldErrors["oldPassword"], isPassword = true)
        FormField(state.newPassword, viewModel::onNewPasswordChange, "Neues Passwort (mind. 8 Zeichen)", state.fieldErrors["newPassword"], isPassword = true)
        Button(onClick = viewModel::changePassword, modifier = Modifier.fillMaxWidth()) { Text("Passwort ändern") }

        HorizontalDivider()
        OutlinedButton(onClick = viewModel::logOut, modifier = Modifier.fillMaxWidth()) { Text("Abmelden") }
        TextButton(
            onClick = { confirmDelete = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Konto löschen") }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Konto löschen?") },
            text = { Text("Dein Konto und alle deine Daten werden endgültig gelöscht.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteAccount() }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            },
        )
    }
}
