package de.touchgrass.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.ui.UiState

private enum class AchievementsTab(val label: String) {
    OVERVIEW("Übersicht"),
    ACHIEVEMENTS("Achievements"),
    LEADERBOARD("Leaderboard"),
}

@Composable
fun AchievementsScreen(viewModel: AchievementsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(AchievementsTab.OVERVIEW) }
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            AchievementsTab.entries.forEach { entry ->
                Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(entry.label) })
            }
        }
        when (tab) {
            AchievementsTab.OVERVIEW -> Section(state.progress, viewModel::refresh) { OverviewTab(it) }
            AchievementsTab.ACHIEVEMENTS -> Section(state.achievements, viewModel::refresh) { AchievementGrid(it) }
            AchievementsTab.LEADERBOARD -> LeaderboardTab(state, viewModel::onPeriodChange, viewModel::onMetricChange, viewModel::refresh)
        }
    }
}

@Composable
fun <T> Section(state: UiState<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        UiState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Erneut versuchen") }
        }
        is UiState.Content -> content(state.data)
    }
}
