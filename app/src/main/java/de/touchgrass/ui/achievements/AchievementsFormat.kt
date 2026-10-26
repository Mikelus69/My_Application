package de.touchgrass.ui.achievements

import de.touchgrass.data.dto.AchievementDto
import de.touchgrass.data.dto.LeaderboardDto
import de.touchgrass.data.dto.LeaderboardEntryDto
import de.touchgrass.data.dto.LeaderboardMetric
import de.touchgrass.data.dto.WeeklyGoalDto
import de.touchgrass.ui.activities.formatDate
import de.touchgrass.ui.profile.label
import java.time.OffsetDateTime

val WEEKDAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

fun WeeklyGoalDto.fraction(): Float = if (target <= 0) 0f else (current.toFloat() / target).coerceIn(0f, 1f)

fun WeeklyGoalDto.label(): String =
    "$current / $target ${type.label()}" + if (current >= target) " – geschafft! 🎉" else ""

fun daysLabel(days: Int): String = if (days == 1) "1 Tag" else "$days Tage"

fun AchievementDto.progressLabel(): String =
    unlockedAt?.let { "Freigeschaltet am ${formatDate(OffsetDateTime.parse(it).toLocalDate())}" } ?: "$progress / $threshold"

fun LeaderboardEntryDto.valueLabel(metric: LeaderboardMetric): String = when (metric) {
    LeaderboardMetric.POINTS -> "$value Punkte"
    LeaderboardMetric.MINUTES -> "$value min"
}

fun LeaderboardDto.ownEntryBelowList(): LeaderboardEntryDto? = me?.takeIf { own -> entries.none { it.username == own.username } }
