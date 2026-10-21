package de.touchgrass.ui.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Duration

@Composable
fun TimerScreen(viewModel: TimerViewModel, onOpenForm: () -> Unit, onRecordManually: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timer = state.timer
    val now = rememberNow(ticking = timer?.isPaused == false)
    LaunchedEffect(state.openForm) {
        if (state.openForm) {
            viewModel.onFormOpened()
            onOpenForm()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Aktivität", style = MaterialTheme.typography.headlineMedium)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.sports.forEach { sport ->
                FilterChip(
                    selected = state.sportCode == sport.code,
                    onClick = { viewModel.onSportChange(sport.code) },
                    enabled = timer == null,
                    label = { Text("${sport.emoji} ${sport.name}") },
                )
            }
        }

        Text(formatElapsed(timer?.elapsed(now) ?: Duration.ZERO), style = MaterialTheme.typography.displayLarge)

        if (timer == null) {
            Button(onClick = viewModel::start) { Text("▶ Start") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (timer.isPaused) {
                    Button(onClick = viewModel::resume) { Text("▶ Weiter") }
                } else {
                    OutlinedButton(onClick = viewModel::pause) { Text("⏸ Pause") }
                }
                Button(onClick = viewModel::stop) { Text("⏹ Stopp") }
            }
        }

        TextButton(onClick = onRecordManually) { Text("Ohne Timer nachtragen") }
    }
}
