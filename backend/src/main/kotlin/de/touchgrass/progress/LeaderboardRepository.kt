package de.touchgrass.progress

import de.touchgrass.activities.ActivitiesTable
import de.touchgrass.logic.Score
import de.touchgrass.logic.TimeRange
import de.touchgrass.users.UsersTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class LeaderboardRepository(private val database: Database) {
    fun scores(range: TimeRange, metric: LeaderboardMetric): List<Score> = transaction(database) {
        val total = when (metric) {
            LeaderboardMetric.POINTS -> ActivitiesTable.points.sum()
            LeaderboardMetric.MINUTES -> ActivitiesTable.durationMin.sum()
        }
        ActivitiesTable.join(UsersTable, JoinType.INNER, ActivitiesTable.userId, UsersTable.id)
            .select(UsersTable.id, UsersTable.username, total)
            .where { (ActivitiesTable.startedAt greaterEq range.from) and (ActivitiesTable.startedAt less range.until) }
            .groupBy(UsersTable.id, UsersTable.username)
            .map { Score(it[UsersTable.id].value, it[UsersTable.username], it[total] ?: 0) }
    }
}
