package de.touchgrass.ui.activities

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.PointsBreakdownDto
import de.touchgrass.data.dto.SportDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

fun formatDate(date: LocalDate): String = date.format(dateFormat)

fun formatTime(time: LocalTime): String = time.format(timeFormat)

fun ActivityDto.startedAtLabel(): String {
    val startedAt = OffsetDateTime.parse(startedAt)
    return "${formatDate(startedAt.toLocalDate())}, ${formatTime(startedAt.toLocalTime())} Uhr"
}

fun ActivityDto.summary(): String = "$durationMin min · ~$caloriesKcal kcal · $points Punkte"

fun PointsBreakdownDto.describe(): String = buildList {
    add("$minutes min")
    add("+$completed")
    if (newSport > 0) add("+$newSport neue Sportart")
    if (weeklyGoal > 0) add("+$weeklyGoal Wochenziel")
}.joinToString(" · ")

fun List<SportDto>.labelFor(code: String): String =
    firstOrNull { it.code == code }?.let { "${it.emoji} ${it.name}" } ?: code
