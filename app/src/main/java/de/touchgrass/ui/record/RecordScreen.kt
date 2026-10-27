package de.touchgrass.ui.record

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.data.dto.Visibility
import de.touchgrass.ui.activities.formatDate
import de.touchgrass.ui.activities.formatTime
import de.touchgrass.ui.components.FormField
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun RecordScreen(viewModel: RecordViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(state.discarded) { if (state.discarded) onBack() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        Text("Aktivität eintragen", style = MaterialTheme.typography.headlineMedium)
        state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text("Sportart")
        state.sportsError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::loadSports) { Text("Erneut versuchen") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.sports.forEach { sport ->
                FilterChip(
                    selected = state.sportCode == sport.code,
                    onClick = { viewModel.onSportChange(sport.code) },
                    label = { Text("${sport.emoji} ${sport.name}") },
                )
            }
        }
        state.fieldErrors["sportCode"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate = true }) { Text("📅 ${formatDate(state.date)}") }
            OutlinedButton(onClick = { pickTime = true }) { Text("🕘 ${formatTime(state.time)} Uhr") }
        }
        state.fieldErrors["startedAt"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        FormField(state.duration, viewModel::onDurationChange, "Dauer in Minuten", state.fieldErrors["durationMin"], KeyboardType.Number)
        LocationPicker(
            location = state.location,
            error = state.fieldErrors["latitude"] ?: state.fieldErrors["longitude"],
            onLocationChange = viewModel::onLocationChange,
        )
        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text("Beschreibung (freiwillig)") },
            isError = state.fieldErrors["description"] != null,
            supportingText = state.fieldErrors["description"]?.let { { Text(it) } },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.visibility == Visibility.PRIVATE,
                onClick = { viewModel.onVisibilityChange(Visibility.PRIVATE) },
                label = { Text("🔒 Privat") },
            )
            FilterChip(
                selected = state.visibility == Visibility.PUBLIC,
                onClick = { viewModel.onVisibilityChange(Visibility.PUBLIC) },
                label = { Text("🌎 Öffentlich") },
            )
        }

        Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.saving) "Speichern …" else "Speichern")
        }
        if (state.fromTimer) {
            TextButton(
                onClick = { confirmDiscard = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Verwerfen") }
        }
    }

    if (pickDate) {
        DateDialog(state.date, onDismiss = { pickDate = false }, onPicked = { pickDate = false; viewModel.onDateChange(it) })
    }
    if (pickTime) {
        TimeDialog(state.time, onDismiss = { pickTime = false }, onPicked = { pickTime = false; viewModel.onTimeChange(it) })
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Aktivität verwerfen?") },
            text = { Text("Der Timer wird zurückgesetzt, die gemessene Zeit geht verloren.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; viewModel.discard() }) { Text("Verwerfen") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Abbrechen") } },
        )
    }
    if (state.newAchievements.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::onAchievementsSeen,
            title = { Text(if (state.newAchievements.size == 1) "Neues Achievement!" else "Neue Achievements!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.newAchievements.forEach { Text("${it.emoji} ${it.name} – ${it.description}") }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::onAchievementsSeen) { Text("Super!") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPicked(LocalTime.of(pickerState.hour, pickerState.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = { TimePicker(state = pickerState) },
    )
}
