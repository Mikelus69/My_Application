package de.touchgrass.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.touchgrass.data.dto.AchievementDto

@Composable
fun AchievementGrid(achievements: List<AchievementDto>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(achievements, key = { it.code }) { AchievementCard(it) }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementDto) {
    val unlocked = achievement.unlockedAt != null
    Card(Modifier.alpha(if (unlocked) 1f else 0.5f)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(achievement.emoji, style = MaterialTheme.typography.displaySmall)
            Text(achievement.name, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            Text(achievement.description, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            if (!unlocked) {
                LinearProgressIndicator(
                    progress = { achievement.progress.toFloat() / achievement.threshold },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(achievement.progressLabel(), style = MaterialTheme.typography.labelSmall)
        }
    }
}
