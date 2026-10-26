package de.touchgrass.achievements

import de.touchgrass.activities.ActivityRepository
import de.touchgrass.logic.AchievementRules
import de.touchgrass.logic.BerlinTime
import de.touchgrass.logic.Streak
import de.touchgrass.logic.UserStats
import java.time.Clock
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class AchievementService(
    private val achievements: AchievementRepository,
    private val activities: ActivityRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    // Auch die Liste schaltet frei, sonst zählten Aktivitäten von vor der Einführung erst mit der nächsten neuen.
    fun list(userId: Long): List<AchievementDto> = evaluate(userId).all

    fun unlockNew(userId: Long): List<AchievementDto> = evaluate(userId).newlyUnlocked

    private class Evaluation(val all: List<AchievementDto>, val newlyUnlocked: List<AchievementDto>)

    private fun evaluate(userId: Long): Evaluation {
        val stats = statsFor(userId)
        val all = achievements.all()
        val unlocked = achievements.unlockedAt(userId)
        val now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
        val fresh = all.filter { it.code !in unlocked && AchievementRules.isReached(stats.valueFor(it.requirement, it.sportCode), it.threshold) }
        achievements.unlock(userId, fresh.map { it.code }, now)

        val unlockedNow = unlocked + fresh.associate { it.code to now }
        val dtos = all.map { it.toDto(stats, unlockedNow[it.code]) }
        val freshCodes = fresh.map { it.code }.toSet()
        return Evaluation(dtos, dtos.filter { it.code in freshCodes })
    }

    private fun statsFor(userId: Long): UserStats {
        val all = activities.list(userId)
        return UserStats(
            activities = all.size,
            minutes = all.sumOf { it.durationMin },
            kcal = all.sumOf { it.caloriesKcal },
            activitiesPerSport = all.groupingBy { it.sportCode }.eachCount(),
            longestStreak = Streak.longest(all.map { BerlinTime.date(it.startedAt) }.toSet()),
        )
    }

    private fun AchievementRecord.toDto(stats: UserStats, unlockedAt: OffsetDateTime?) = AchievementDto(
        code = code,
        name = name,
        description = description,
        emoji = emoji,
        threshold = threshold,
        progress = AchievementRules.progress(stats.valueFor(requirement, sportCode), threshold, unlocked = unlockedAt != null),
        unlockedAt = unlockedAt?.let(BerlinTime::format),
    )
}
