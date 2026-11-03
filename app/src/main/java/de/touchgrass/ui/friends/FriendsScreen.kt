package de.touchgrass.ui.friends

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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.data.dto.FriendsDto
import de.touchgrass.ui.UiState
import de.touchgrass.ui.components.FormField
import de.touchgrass.ui.components.LoadingIndicator

@Composable
fun FriendsScreen(viewModel: FriendsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        Text("👥 Freunde", style = MaterialTheme.typography.headlineMedium)
        FormField(state.username, viewModel::onUsernameChange, "Benutzername", state.usernameError)
        Button(onClick = viewModel::sendRequest, modifier = Modifier.fillMaxWidth()) { Text("Anfrage senden") }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (val s = state.friends) {
            UiState.Loading -> LoadingIndicator()
            is UiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::refresh) { Text("Erneut versuchen") }
            }
            is UiState.Content -> FriendLists(
                friends = s.data,
                onAccept = viewModel::accept,
                onDeleteRequest = viewModel::deleteRequest,
                onRemove = { confirmRemove = it },
            )
        }
    }

    confirmRemove?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Freundschaft mit $name beenden?") },
            text = { Text("Ihr seht dann eure „👥 Freunde“-Aktivitäten nicht mehr.") },
            confirmButton = { TextButton(onClick = { confirmRemove = null; viewModel.remove(name) }) { Text("Beenden") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun FriendLists(
    friends: FriendsDto,
    onAccept: (String) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (friends.friends.isEmpty() && friends.incoming.isEmpty() && friends.outgoing.isEmpty()) {
        Text("Noch keine Freunde. Schick jemandem eine Anfrage über seinen Benutzernamen.")
        return
    }
    if (friends.incoming.isNotEmpty()) {
        Text("Anfragen an dich", style = MaterialTheme.typography.titleMedium)
        friends.incoming.forEach { name ->
            ListItem(
                headlineContent = { Text(name) },
                trailingContent = {
                    Row {
                        TextButton(onClick = { onAccept(name) }) { Text("Annehmen") }
                        TextButton(onClick = { onDeleteRequest(name) }) { Text("Ablehnen") }
                    }
                },
            )
        }
    }
    if (friends.friends.isNotEmpty()) {
        Text("Deine Freunde", style = MaterialTheme.typography.titleMedium)
        friends.friends.forEach { name ->
            ListItem(
                headlineContent = { Text(name) },
                trailingContent = { TextButton(onClick = { onRemove(name) }) { Text("Entfernen") } },
            )
        }
    }
    if (friends.outgoing.isNotEmpty()) {
        Text("Gesendete Anfragen", style = MaterialTheme.typography.titleMedium)
        friends.outgoing.forEach { name ->
            ListItem(
                headlineContent = { Text(name) },
                trailingContent = { TextButton(onClick = { onDeleteRequest(name) }) { Text("Zurückziehen") } },
            )
        }
    }
}
