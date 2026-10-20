package de.touchgrass.users

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal

object UsersTable : LongIdTable("users") {
    val username = varchar("username", 20)
    val email = varchar("email", 254)
    val passwordHash = varchar("password_hash", 100)
    val weightKg = decimal("weight_kg", 5, 1).nullable()
    val goalType = enumerationByName<GoalType>("goal_type", 20).default(GoalType.MINUTES)
    val goalTarget = integer("goal_target").default(150)
}

data class UserRecord(
    val id: Long,
    val username: String,
    val email: String,
    val passwordHash: String,
    val weightKg: Double?,
    val goalType: GoalType,
    val goalTarget: Int,
)

class UserRepository(private val database: Database) {
    fun findById(id: Long): UserRecord? = transaction(database) {
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toRecord()
    }

    fun findByLogin(login: String): UserRecord? = transaction(database) {
        val normalized = login.trim().lowercase()
        UsersTable.selectAll()
            .where { (UsersTable.username.lowerCase() eq normalized) or (UsersTable.email eq normalized) }
            .singleOrNull()?.toRecord()
    }

    fun usernameTaken(username: String): Boolean = transaction(database) {
        UsersTable.selectAll().where { UsersTable.username.lowerCase() eq username.lowercase() }.count() > 0
    }

    fun emailTaken(email: String): Boolean = transaction(database) {
        UsersTable.selectAll().where { UsersTable.email eq email }.count() > 0
    }

    fun create(username: String, email: String, passwordHash: String): UserRecord = transaction(database) {
        val id = UsersTable.insertAndGetId {
            it[UsersTable.username] = username
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = passwordHash
        }.value
        UsersTable.selectAll().where { UsersTable.id eq id }.single().toRecord()
    }

    fun updateProfile(id: Long, weightKg: Double?, goalType: GoalType?, goalTarget: Int?) {
        if (weightKg == null && goalType == null && goalTarget == null) return
        transaction(database) {
            UsersTable.update({ UsersTable.id eq id }) { row ->
                weightKg?.let { row[UsersTable.weightKg] = BigDecimal.valueOf(it) }
                goalType?.let { row[UsersTable.goalType] = it }
                goalTarget?.let { row[UsersTable.goalTarget] = it }
            }
        }
    }

    fun updatePasswordHash(id: Long, passwordHash: String) = transaction(database) {
        UsersTable.update({ UsersTable.id eq id }) { it[UsersTable.passwordHash] = passwordHash }
    }

    fun delete(id: Long) = transaction(database) {
        UsersTable.deleteWhere { UsersTable.id eq id }
    }

    private fun ResultRow.toRecord() = UserRecord(
        id = this[UsersTable.id].value,
        username = this[UsersTable.username],
        email = this[UsersTable.email],
        passwordHash = this[UsersTable.passwordHash],
        weightKg = this[UsersTable.weightKg]?.toDouble(),
        goalType = this[UsersTable.goalType],
        goalTarget = this[UsersTable.goalTarget],
    )
}
