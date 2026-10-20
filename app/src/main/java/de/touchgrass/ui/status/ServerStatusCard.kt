package de.touchgrass.ui.status

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.touchgrass.data.SportApi
import de.touchgrass.ui.UiState

@Composable
fun ServerStatusCard(api: SportApi, modifier: Modifier = Modifier) {
    val viewModel = viewModel { ServerStatusViewModel(api) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val text = when (val s = state) {
                UiState.Loading -> "⏳ Verbinde mit Server …"
                is UiState.Content -> "✅ Server erreichbar (Datenbank: ${s.data.database})"
                is UiState.Error -> "❌ ${s.message}"
            }
            Text(text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = viewModel::refresh) { Text("Erneut") }
        }
    }
}
