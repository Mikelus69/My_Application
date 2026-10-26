package de.touchgrass.achievements

import de.touchgrass.logic.Requirement
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime

object AchievementsTable : Table("achievements") {
    val code = varchar("code", 30)
    val name = varchar("name", 50)
    val description = varchar("description", 200)
    val emoji = varchar("emoji", 10)
    val requirement = enumerationByName<Requirement>("requirement", 30)
    val threshold = integer("threshold")
    val sportCode = varchar("sport_code", 30).nullable()
    override val primaryKey = PrimaryKey(code)
}

object UserAchievementsTable : Table("user_achievements") {
    val userId = long("user_id")
    val achievementCode = varchar("achievement_code", 30)
    val unlockedAt = timestampWithTimeZone("unlocked_at")
    override val primaryKey = PrimaryKey(userId, achievementCode)
}

data class AchievementRecord(
    val code: String,
    val name: String,
    val description: String,
    val emoji: String,
    val requirement: Requirement,
    val threshold: Int,
    val sportCode: String?,
)

class AchievementRepository(private val database: Database) {
    fun all(): List<AchievementRecord> = transaction(database) {
        AchievementsTable.selectAll()
            .orderBy(AchievementsTable.threshold to SortOrder.ASC, AchievementsTable.code to SortOrder.ASC)
            .map { it.toRecord() }
    }

    fun unlockedAt(userId: Long): Map<String, OffsetDateTime> = transaction(database) {
        UserAchievementsTable.selectAll()
            .where { UserAchievementsTable.userId eq userId }
            .associate { it[UserAchievementsTable.achievementCode] to it[UserAchievementsTable.unlockedAt] }
    }

    fun unlock(userId: Long, codes: List<String>, at: OffsetDateTime) {
        if (codes.isEmpty()) return
        transaction(database) {
            codes.forEach { code ->
                UserAchievementsTable.insertIgnore {
                    it[UserAchievementsTable.userId] = userId
                    it[achievementCode] = code
                    it[unlockedAt] = at
                }
            }
        }
    }

    private fun ResultRow.toRecord() = AchievementRecord(
        code = this[AchievementsTable.code],
        name = this[AchievementsTable.name],
        description = this[AchievementsTable.description],
        emoji = this[AchievementsTable.emoji],
        requirement = this[AchievementsTable.requirement],
        threshold = this[AchievementsTable.threshold],
        sportCode = this[AchievementsTable.sportCode],
    )
}
