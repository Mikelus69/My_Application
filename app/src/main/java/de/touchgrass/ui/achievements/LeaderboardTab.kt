package de.touchgrass.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.touchgrass.data.dto.LeaderboardDto
import de.touchgrass.data.dto.LeaderboardEntryDto
import de.touchgrass.data.dto.LeaderboardMetric
import de.touchgrass.data.dto.LeaderboardPeriod
import de.touchgrass.data.dto.LeaderboardScope

@Composable
fun LeaderboardTab(
    state: AchievementsUiState,
    onPeriodChange: (LeaderboardPeriod) -> Unit,
    onMetricChange: (LeaderboardMetric) -> Unit,
    onScopeChange: (LeaderboardScope) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.period == LeaderboardPeriod.WEEK,
                onClick = { onPeriodChange(LeaderboardPeriod.WEEK) },
                label = { Text("Woche") },
            )
            FilterChip(
                selected = state.period == LeaderboardPeriod.MONTH,
                onClick = { onPeriodChange(LeaderboardPeriod.MONTH) },
                label = { Text("Monat") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.metric == LeaderboardMetric.POINTS,
                onClick = { onMetricChange(LeaderboardMetric.POINTS) },
                label = { Text("Punkte") },
            )
            FilterChip(
                selected = state.metric == LeaderboardMetric.MINUTES,
                onClick = { onMetricChange(LeaderboardMetric.MINUTES) },
                label = { Text("Minuten") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.scope == LeaderboardScope.ALL,
                onClick = { onScopeChange(LeaderboardScope.ALL) },
                label = { Text("Alle") },
            )
            FilterChip(
                selected = state.scope == LeaderboardScope.FRIENDS,
                onClick = { onScopeChange(LeaderboardScope.FRIENDS) },
                label = { Text("Freunde") },
            )
        }
        Section(state.leaderboard, onRetry) { LeaderboardList(it, state.scope) }
    }
}

@Composable
private fun LeaderboardList(board: LeaderboardDto, scope: LeaderboardScope) {
    if (board.entries.isEmpty()) {
        Text(emptyLeaderboardText(scope))
        return
    }
    val ownName = board.me?.username
    val ownBelow = board.ownEntryBelowList()
    LazyColumn {
        items(board.entries, key = { it.username }) { entry ->
            LeaderboardRow(entry, board.metric, highlighted = entry.username == ownName)
        }
        if (ownBelow != null) {
            item { HorizontalDivider() }
            item { LeaderboardRow(ownBelow, board.metric, highlighted = true) }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntryDto, metric: LeaderboardMetric, highlighted: Boolean) {
    ListItem(
        leadingContent = { Text("${entry.rank}.", style = MaterialTheme.typography.titleMedium) },
        headlineContent = { Text(entry.username) },
        trailingContent = { Text(entry.valueLabel(metric)) },
        colors = if (highlighted) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            ListItemDefaults.colors()
        },
    )
}
