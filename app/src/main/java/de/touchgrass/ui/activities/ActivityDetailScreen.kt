package de.touchgrass.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.photoUrl
import de.touchgrass.ui.UiState
import de.touchgrass.ui.components.LoadingIndicator
import de.touchgrass.ui.photo.PhotoPickers
import de.touchgrass.ui.photo.rememberPhotoPickers

@Composable
fun ActivityDetailScreen(viewModel: ActivityDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickers = rememberPhotoPickers(onPicked = viewModel::onPhotoPicked, onError = viewModel::onPhotoError)
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemovePhoto by remember { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        when (val s = state.activity) {
            UiState.Loading -> LoadingIndicator()
            is UiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::load) { Text("Erneut versuchen") }
            }
            is UiState.Content -> {
                val activity = s.data
                ActivityPhoto(activity)
                Text(state.sports.labelFor(activity.sportCode), style = MaterialTheme.typography.headlineMedium)
                Text(activity.startedAtLabel())
                Text("${activity.durationMin} Minuten")
                Text("~${activity.caloriesKcal} kcal (geschätzt)")
                Text("${activity.points} Punkte")
                Text(activity.visibility.label())
                activity.description?.let { Text("„$it“") }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (activity.mine) {
                    if (state.photoBusy) {
                        LoadingIndicator()
                    } else {
                        PhotoActions(
                            hasPhoto = activity.photoVersion != null,
                            pickers = pickers,
                            onRemove = { confirmRemovePhoto = true },
                        )
                    }
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Aktivität löschen") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Aktivität löschen?") },
            text = { Text("Die Punkte dieser Aktivität verschwinden ebenfalls.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
    if (confirmRemovePhoto) {
        AlertDialog(
            onDismissRequest = { confirmRemovePhoto = false },
            title = { Text("Foto entfernen?") },
            confirmButton = { TextButton(onClick = { confirmRemovePhoto = false; viewModel.removePhoto() }) { Text("Entfernen") } },
            dismissButton = { TextButton(onClick = { confirmRemovePhoto = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
fun ActivityPhoto(activity: ActivityDto, modifier: Modifier = Modifier) {
    val url = activity.photoUrl() ?: return
    AsyncImage(
        model = url,
        contentDescription = "Foto der Aktivität",
        modifier = modifier.fillMaxWidth().aspectRatio(4f / 3f),
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun PhotoActions(hasPhoto: Boolean, pickers: PhotoPickers, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            OutlinedButton(onClick = { menuOpen = true }) { Text(if (hasPhoto) "Foto ändern" else "Foto hinzufügen") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("🖼️ Galerie") }, onClick = { menuOpen = false; pickers.openGallery() })
                DropdownMenuItem(text = { Text("📷 Kamera") }, onClick = { menuOpen = false; pickers.openCamera() })
            }
        }
        if (hasPhoto) {
            TextButton(onClick = onRemove) { Text("Foto entfernen") }
        }
    }
}
