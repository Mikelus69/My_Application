package de.touchgrass.progress

import de.touchgrass.activities.ActivitiesTable
import de.touchgrass.logic.Score
import de.touchgrass.logic.TimeRange
import de.touchgrass.users.UsersTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class LeaderboardRepository(private val database: Database) {
    fun scores(range: TimeRange, metric: LeaderboardMetric, onlyUsers: Set<Long>? = null): List<Score> = transaction(database) {
        val total = when (metric) {
            LeaderboardMetric.POINTS -> ActivitiesTable.points.sum()
            LeaderboardMetric.MINUTES -> ActivitiesTable.durationMin.sum()
        }
        var condition: Op<Boolean> = (ActivitiesTable.startedAt greaterEq range.from) and (ActivitiesTable.startedAt less range.until)
        onlyUsers?.let { condition = condition and (ActivitiesTable.userId inList it) }
        ActivitiesTable.join(UsersTable, JoinType.INNER, ActivitiesTable.userId, UsersTable.id)
            .select(UsersTable.id, UsersTable.username, total)
            .where { condition }
            .groupBy(UsersTable.id, UsersTable.username)
            .map { Score(it[UsersTable.id].value, it[UsersTable.username], it[total] ?: 0) }
    }
}
