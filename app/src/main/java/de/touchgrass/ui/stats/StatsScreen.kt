package de.touchgrass.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        Text("Statistik", style = MaterialTheme.typography.headlineMedium)
        when (val s = state) {
            UiState.Loading -> LoadingIndicator()
            is UiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::refresh) { Text("Erneut versuchen") }
            }
            is UiState.Content -> StatsCharts(s.data)
        }
    }
}

@Composable
private fun StatsCharts(content: StatsContent) {
    val stats = content.stats
    Text("Diese Woche: ${stats.thisWeek.label()}")
    ChartSection(
        title = "Minuten pro Woche",
        values = stats.minutesPerWeek.map { it.minutes },
        labels = stats.minutesPerWeek.map { weekLabel(it.weekStart) },
    )
    ChartSection(
        title = "Aktivitäten je Sportart",
        values = stats.activitiesPerSport.map { it.count },
        labels = stats.activitiesPerSport.map { content.sports.emojiFor(it.sportCode) },
    )
    ChartSection(
        title = "kcal pro Monat (geschätzt)",
        values = stats.kcalPerMonth.map { it.kcal },
        labels = stats.kcalPerMonth.map { monthLabel(it.month) },
    )
}

@Composable
private fun ChartSection(title: String, values: List<Int>, labels: List<String>) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (values.none { it > 0 }) {
        Text("Noch keine Daten. Trag eine Aktivität ein, dann erscheint hier ein Diagramm.")
    } else {
        ColumnChart(values, labels)
    }
}
