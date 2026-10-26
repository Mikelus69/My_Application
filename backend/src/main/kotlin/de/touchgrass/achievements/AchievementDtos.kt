package de.touchgrass.achievements

import kotlinx.serialization.Serializable

@Serializable
data class AchievementDto(
    val code: String,
    val name: String,
    val description: String,
    val emoji: String,
    val threshold: Int,
    val progress: Int,
    val unlockedAt: String? = null,
)
