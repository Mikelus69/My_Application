package de.touchgrass.ui.profile

import de.touchgrass.data.dto.GoalType

fun formatWeight(kg: Double): String =
    if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString().replace('.', ',')

fun GoalType.label(): String = when (this) {
    GoalType.MINUTES -> "Minuten"
    GoalType.ACTIVITIES -> "Aktivitäten"
    GoalType.KCAL -> "kcal"
}
