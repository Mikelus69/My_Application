package de.touchgrass.activities

import de.touchgrass.logic.TimeRange
import de.touchgrass.users.UsersTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime

object ActivitiesTable : LongIdTable("activities") {
    val userId = long("user_id")
    val sportCode = varchar("sport_code", 30)
    val startedAt = timestampWithTimeZone("started_at")
    val durationMin = integer("duration_min")
    val caloriesKcal = integer("calories_kcal")
    val points = integer("points")
    val goalBonus = bool("goal_bonus").default(false)
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val description = varchar("description", 500).nullable()
    val visibility = enumerationByName<Visibility>("visibility", 10).default(Visibility.PRIVATE)
}

data class NewActivity(
    val userId: Long,
    val sportCode: String,
    val startedAt: OffsetDateTime,
    val durationMin: Int,
    val caloriesKcal: Int,
    val points: Int,
    val goalBonus: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val description: String?,
    val visibility: Visibility,
)

data class ActivityRecord(
    val id: Long,
    val userId: Long,
    val username: String,
    val sportCode: String,
    val startedAt: OffsetDateTime,
    val durationMin: Int,
    val caloriesKcal: Int,
    val points: Int,
    val goalBonus: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val description: String?,
    val visibility: Visibility,
)

class ActivityRepository(private val database: Database) {
    private val withUser = ActivitiesTable.join(UsersTable, JoinType.INNER, ActivitiesTable.userId, UsersTable.id)

    fun insert(activity: NewActivity): Long = transaction(database) {
        ActivitiesTable.insertAndGetId {
            it[userId] = activity.userId
            it[sportCode] = activity.sportCode
            it[startedAt] = activity.startedAt
            it[durationMin] = activity.durationMin
            it[caloriesKcal] = activity.caloriesKcal
            it[points] = activity.points
            it[goalBonus] = activity.goalBonus
            it[latitude] = activity.latitude
            it[longitude] = activity.longitude
            it[description] = activity.description
            it[visibility] = activity.visibility
        }.value
    }

    fun findById(id: Long): ActivityRecord? = transaction(database) {
        withUser.selectAll().where { ActivitiesTable.id eq id }.singleOrNull()?.toRecord()
    }

    fun list(userId: Long, sportCode: String? = null, from: OffsetDateTime? = null, until: OffsetDateTime? = null): List<ActivityRecord> =
        transaction(database) {
            var condition: Op<Boolean> = ActivitiesTable.userId eq userId
            sportCode?.let { condition = condition and (ActivitiesTable.sportCode eq it) }
            from?.let { condition = condition and (ActivitiesTable.startedAt greaterEq it) }
            until?.let { condition = condition and (ActivitiesTable.startedAt less it) }
            withUser.selectAll()
                .where { condition }
                .orderBy(ActivitiesTable.startedAt to SortOrder.DESC, ActivitiesTable.id to SortOrder.DESC)
                .map { it.toRecord() }
        }

    fun forMap(userId: Long): List<ActivityRecord> = transaction(database) {
        withUser.selectAll()
            .where {
                ActivitiesTable.latitude.isNotNull() and
                    ((ActivitiesTable.userId eq userId) or (ActivitiesTable.visibility eq Visibility.PUBLIC))
            }
            .orderBy(ActivitiesTable.startedAt to SortOrder.DESC, ActivitiesTable.id to SortOrder.DESC)
            .map { it.toRecord() }
    }

    fun inRange(userId: Long, range: TimeRange): List<ActivityRecord> = list(userId, from = range.from, until = range.until)

    fun hasSport(userId: Long, sportCode: String): Boolean = transaction(database) {
        ActivitiesTable.selectAll()
            .where { (ActivitiesTable.userId eq userId) and (ActivitiesTable.sportCode eq sportCode) }
            .count() > 0
    }

    fun delete(id: Long) = transaction(database) {
        ActivitiesTable.deleteWhere { ActivitiesTable.id eq id }
    }

    private fun ResultRow.toRecord() = ActivityRecord(
        id = this[ActivitiesTable.id].value,
        userId = this[ActivitiesTable.userId],
        username = this[UsersTable.username],
        sportCode = this[ActivitiesTable.sportCode],
        startedAt = this[ActivitiesTable.startedAt],
        durationMin = this[ActivitiesTable.durationMin],
        caloriesKcal = this[ActivitiesTable.caloriesKcal],
        points = this[ActivitiesTable.points],
        goalBonus = this[ActivitiesTable.goalBonus],
        latitude = this[ActivitiesTable.latitude],
        longitude = this[ActivitiesTable.longitude],
        description = this[ActivitiesTable.description],
        visibility = this[ActivitiesTable.visibility],
    )
}
