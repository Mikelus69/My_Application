package de.touchgrass.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.touchgrass.data.dto.ProgressDto

@Composable
fun OverviewTab(progress: ProgressDto) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Wochenziel", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { progress.weeklyGoal.fraction() }, modifier = Modifier.fillMaxWidth())
        Text(progress.weeklyGoal.label())

        HorizontalDivider()
        Text("Serie", style = MaterialTheme.typography.titleMedium)
        Text("🔥 ${daysLabel(progress.streak.current)} in Folge", style = MaterialTheme.typography.headlineSmall)
        Text("Längste Serie: ${daysLabel(progress.streak.longest)}")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WEEKDAYS.zip(progress.weekDays).forEach { (day, active) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day, style = MaterialTheme.typography.labelMedium)
                    Text(if (active) "✅" else "⬜")
                }
            }
        }
    }
}
