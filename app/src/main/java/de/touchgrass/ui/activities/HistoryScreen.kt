package de.touchgrass.ui.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
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
import de.touchgrass.ui.components.LoadingIndicator

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        Text("Verlauf", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.sportFilter == null, onClick = { viewModel.onSportFilterChange(null) }, label = { Text("Alle Sportarten") })
            state.sports.forEach { sport ->
                FilterChip(
                    selected = state.sportFilter == sport.code,
                    onClick = { viewModel.onSportFilterChange(sport.code) },
                    label = { Text("${sport.emoji} ${sport.name}") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { period ->
                FilterChip(selected = state.period == period, onClick = { viewModel.onPeriodChange(period) }, label = { Text(period.label) })
            }
        }
        HorizontalDivider()
        when (val activities = state.activities) {
            UiState.Loading -> LoadingIndicator()
            is UiState.Error -> {
                Text(activities.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::refresh) { Text("Erneut versuchen") }
            }
            is UiState.Content -> if (activities.data.isEmpty()) {
                Text("Noch keine Aktivitäten in diesem Zeitraum.")
            } else {
                LazyColumn {
                    items(activities.data, key = { it.id }) { activity ->
                        ListItem(
                            overlineContent = { Text(activity.startedAtLabel()) },
                            headlineContent = { Text(state.sports.labelFor(activity.sportCode)) },
                            supportingContent = { Text(activity.summary()) },
                            modifier = Modifier.clickable { onOpen(activity.id) },
                        )
                    }
                }
            }
        }
    }
}
