package de.touchgrass.progress

import de.touchgrass.friends.FriendshipRepository
import de.touchgrass.logic.BerlinTime
import de.touchgrass.logic.Leaderboard
import de.touchgrass.logic.RankedScore
import de.touchgrass.plugins.validationError
import java.time.Clock
import java.time.OffsetDateTime

class LeaderboardService(
    private val leaderboard: LeaderboardRepository,
    private val friendships: FriendshipRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun leaderboard(userId: Long, period: String?, metric: String?, scope: String?): LeaderboardDto {
        val chosenPeriod = choice(period, LeaderboardPeriod.WEEK, "period", "Zeitraum bitte als WEEK oder MONTH angeben.")
        val chosenMetric = choice(metric, LeaderboardMetric.POINTS, "metric", "Wertung bitte als POINTS oder MINUTES angeben.")
        val chosenScope = choice(scope, LeaderboardScope.ALL, "scope", "Auswahl bitte als ALL oder FRIENDS angeben.")
        val now = OffsetDateTime.now(clock)
        val range = when (chosenPeriod) {
            LeaderboardPeriod.WEEK -> BerlinTime.weekOf(now)
            LeaderboardPeriod.MONTH -> BerlinTime.monthOf(now)
        }
        val onlyUsers = if (chosenScope == LeaderboardScope.FRIENDS) friendships.friendIds(userId) + userId else null
        val ranked = Leaderboard.rank(leaderboard.scores(range, chosenMetric, onlyUsers))
        return LeaderboardDto(
            period = chosenPeriod,
            metric = chosenMetric,
            entries = ranked.take(Leaderboard.TOP).map { it.toDto() },
            me = ranked.firstOrNull { it.score.userId == userId }?.toDto(),
        )
    }

    private inline fun <reified T : Enum<T>> choice(value: String?, default: T, field: String, message: String): T =
        if (value == null) default else enumValues<T>().firstOrNull { it.name == value } ?: validationError(field, message)

    private fun RankedScore.toDto() = LeaderboardEntryDto(rank, score.username, score.value)
}
