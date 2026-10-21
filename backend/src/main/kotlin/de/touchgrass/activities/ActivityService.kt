package de.touchgrass.activities

import de.touchgrass.logic.BerlinTime
import de.touchgrass.logic.Calories
import de.touchgrass.logic.Points
import de.touchgrass.logic.WeekTotals
import de.touchgrass.logic.WeeklyGoal
import de.touchgrass.plugins.ApiException
import de.touchgrass.plugins.validationError
import de.touchgrass.sports.SportRepository
import de.touchgrass.users.UserRepository
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.OffsetDateTime

class ActivityService(
    private val activities: ActivityRepository,
    private val sports: SportRepository,
    private val users: UserRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(userId: Long, request: CreateActivityRequest): CreateActivityResponse {
        val user = users.findById(userId) ?: throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Bitte melde dich an.")
        val sport = sports.find(request.sportCode) ?: validationError("sportCode", "Unbekannte Sportart.")
        val startedAt = ActivityValidation.startedAt(request.startedAt, OffsetDateTime.now(clock))
        ActivityValidation.durationMin(request.durationMin)
        ActivityValidation.location(request.latitude, request.longitude)
        val description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        ActivityValidation.description(description)

        val kcal = Calories.estimate(sport.met, user.weightKg, request.durationMin)
        val week = activities.inRange(userId, BerlinTime.weekOf(startedAt))
        val before = WeekTotals(week.sumOf { it.durationMin }, week.size, week.sumOf { it.caloriesKcal })
        val goalBonus = WeeklyGoal.bonusEarned(
            user.goalType,
            user.goalTarget,
            before,
            before.plusActivity(request.durationMin, kcal),
            bonusAlreadyGiven = week.any { it.goalBonus },
        )
        val breakdown = Points.calculate(request.durationMin, !activities.hasSport(userId, sport.code), goalBonus)

        val id = activities.insert(
            NewActivity(
                userId = userId,
                sportCode = sport.code,
                startedAt = startedAt,
                durationMin = request.durationMin,
                caloriesKcal = kcal,
                points = breakdown.total,
                goalBonus = goalBonus,
                latitude = request.latitude,
                longitude = request.longitude,
                description = description,
                visibility = request.visibility,
            )
        )
        return CreateActivityResponse(activities.findById(id)!!.toDto(viewerId = userId), breakdown)
    }

    fun list(userId: Long, sportCode: String?, from: String?, to: String?): List<ActivityDto> {
        val fromDate = ActivityValidation.dateFilter(from, "from")
        val toDate = ActivityValidation.dateFilter(to, "to")
        return activities.list(
            userId,
            sportCode,
            fromDate?.let(BerlinTime::startOfDay),
            toDate?.let { BerlinTime.startOfDay(it.plusDays(1)) },
        ).map { it.toDto(viewerId = userId) }
    }

    fun map(userId: Long): List<ActivityDto> = activities.forMap(userId).map { it.toDto(viewerId = userId) }

    fun get(userId: Long, id: Long): ActivityDto {
        val activity = activities.findById(id)
        if (activity == null || (activity.userId != userId && activity.visibility == Visibility.PRIVATE)) throw notFound()
        return activity.toDto(viewerId = userId)
    }

    fun delete(userId: Long, id: Long) {
        val activity = activities.findById(id) ?: throw notFound()
        if (activity.userId != userId) {
            throw ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", "Das ist nicht deine Aktivität.")
        }
        activities.delete(id)
    }

    private fun notFound() = ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Aktivität nicht gefunden.")

    private fun ActivityRecord.toDto(viewerId: Long) = ActivityDto(
        id = id,
        username = username,
        mine = userId == viewerId,
        sportCode = sportCode,
        startedAt = BerlinTime.format(startedAt),
        durationMin = durationMin,
        caloriesKcal = caloriesKcal,
        points = points,
        latitude = latitude,
        longitude = longitude,
        description = description,
        visibility = visibility,
    )
}
