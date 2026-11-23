package de.touchgrass.activities

import de.touchgrass.logic.TimeRange
import de.touchgrass.users.UsersTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.nextLongVal
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.jetbrains.exposed.v1.jdbc.upsert
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
    val photoVersion = long("photo_version").nullable()
}

object ActivityPhotosTable : Table("activity_photos") {
    val activityId = long("activity_id")
    val image = binary("image")
    override val primaryKey = PrimaryKey(activityId)
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
    val photoVersion: Long?,
)

class ActivityRepository(private val database: Database) {
    private val withUser = ActivitiesTable.join(UsersTable, JoinType.INNER, ActivitiesTable.userId, UsersTable.id)
    private val photoVersions = Sequence("photo_versions")

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

    fun findVisible(id: Long, viewerId: Long, friendIds: Set<Long>): ActivityRecord? = transaction(database) {
        withUser.selectAll().where { (ActivitiesTable.id eq id) and visibleTo(viewerId, friendIds) }.singleOrNull()?.toRecord()
    }

    fun forMap(viewerId: Long, friendIds: Set<Long>): List<ActivityRecord> = transaction(database) {
        withUser.selectAll()
            .where { ActivitiesTable.latitude.isNotNull() and visibleTo(viewerId, friendIds) }
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

    fun savePhoto(activityId: Long, jpeg: ByteArray): Long = transaction(database) {
        ActivityPhotosTable.upsert {
            it[ActivityPhotosTable.activityId] = activityId
            it[ActivityPhotosTable.image] = jpeg
        }
        ActivitiesTable.updateReturning(listOf(ActivitiesTable.photoVersion), where = { ActivitiesTable.id eq activityId }) {
            it[photoVersion] = photoVersions.nextLongVal()
        }.single()[ActivitiesTable.photoVersion]!!
    }

    fun deletePhoto(activityId: Long) {
        transaction(database) {
            ActivityPhotosTable.deleteWhere { ActivityPhotosTable.activityId eq activityId }
            ActivitiesTable.update({ ActivitiesTable.id eq activityId }) { it[photoVersion] = null }
        }
    }

    fun findVisiblePhoto(id: Long, viewerId: Long, friendIds: Set<Long>): ByteArray? = transaction(database) {
        ActivitiesTable.join(ActivityPhotosTable, JoinType.INNER, ActivitiesTable.id, ActivityPhotosTable.activityId)
            .select(ActivityPhotosTable.image)
            .where { (ActivitiesTable.id eq id) and visibleTo(viewerId, friendIds) }
            .singleOrNull()
            ?.let { it[ActivityPhotosTable.image] }
    }

    // Die einzige Stelle, die festlegt, wer welche Aktivität sieht. Karte, Details und Fotos nutzen diese Bedingung.
    private fun visibleTo(viewerId: Long, friendIds: Set<Long>): Op<Boolean> =
        (ActivitiesTable.userId eq viewerId) or
            (ActivitiesTable.visibility eq Visibility.PUBLIC) or
            ((ActivitiesTable.visibility eq Visibility.FRIENDS) and (ActivitiesTable.userId inList friendIds))

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
        photoVersion = this[ActivitiesTable.photoVersion],
    )
}
