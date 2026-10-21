package de.touchgrass.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.ui.UiState

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val s = state) {
            UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::refresh) { Text("Erneut versuchen") }
            }
            is UiState.Content -> {
                val user = s.data
                Text("👤 ${user.username}", style = MaterialTheme.typography.headlineMedium)
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
                Text("Gewicht: " + (user.weightKg?.let { "${formatWeight(it)} kg" } ?: "nicht angegeben – in den Einstellungen eintragen, dann wird die kcal-Schätzung genauer"))
                Text("Wochenziel: ${user.goalTarget} ${user.goalType.label()}")
            }
        }
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("📜 Verlauf") },
            modifier = Modifier.clickable(onClick = onOpenHistory),
        )
        ListItem(
            headlineContent = { Text("⚙️ Einstellungen") },
            modifier = Modifier.clickable(onClick = onOpenSettings),
        )
    }
}
