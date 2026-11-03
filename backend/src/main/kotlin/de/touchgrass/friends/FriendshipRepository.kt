package de.touchgrass.friends

import de.touchgrass.users.UsersTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

enum class FriendshipStatus { PENDING, ACCEPTED }

object FriendshipsTable : Table("friendships") {
    val requesterId = long("requester_id")
    val addresseeId = long("addressee_id")
    val status = enumerationByName<FriendshipStatus>("status", 10).default(FriendshipStatus.PENDING)
    override val primaryKey = PrimaryKey(requesterId, addresseeId)
}

data class Friendship(val requesterId: Long, val addresseeId: Long, val status: FriendshipStatus)

/** Eine Verbindung aus Sicht einer Person: Name der anderen Seite, Status und ob die Person selbst angefragt hat. */
data class Connection(val username: String, val status: FriendshipStatus, val outgoing: Boolean)

class FriendshipRepository(private val database: Database) {
    fun between(a: Long, b: Long): Friendship? = transaction(database) {
        FriendshipsTable.selectAll().where { pair(a, b) }.singleOrNull()?.let {
            Friendship(it[FriendshipsTable.requesterId], it[FriendshipsTable.addresseeId], it[FriendshipsTable.status])
        }
    }

    fun request(requesterId: Long, addresseeId: Long) {
        transaction(database) {
            FriendshipsTable.insert {
                it[FriendshipsTable.requesterId] = requesterId
                it[FriendshipsTable.addresseeId] = addresseeId
            }
        }
    }

    fun accept(requesterId: Long, addresseeId: Long) {
        transaction(database) {
            FriendshipsTable.update({ (FriendshipsTable.requesterId eq requesterId) and (FriendshipsTable.addresseeId eq addresseeId) }) {
                it[FriendshipsTable.status] = FriendshipStatus.ACCEPTED
            }
        }
    }

    fun delete(a: Long, b: Long) {
        transaction(database) { FriendshipsTable.deleteWhere { pair(a, b) } }
    }

    fun friendIds(userId: Long): Set<Long> = transaction(database) {
        FriendshipsTable.selectAll()
            .where { involves(userId) and (FriendshipsTable.status eq FriendshipStatus.ACCEPTED) }
            .map { if (it[FriendshipsTable.requesterId] == userId) it[FriendshipsTable.addresseeId] else it[FriendshipsTable.requesterId] }
            .toSet()
    }

    fun connections(userId: Long): List<Connection> = transaction(database) {
        connections(userId, FriendshipsTable.requesterId, FriendshipsTable.addresseeId, outgoing = true) +
            connections(userId, FriendshipsTable.addresseeId, FriendshipsTable.requesterId, outgoing = false)
    }

    private fun connections(userId: Long, own: Column<Long>, other: Column<Long>, outgoing: Boolean): List<Connection> =
        FriendshipsTable.join(UsersTable, JoinType.INNER, other, UsersTable.id)
            .selectAll()
            .where { own eq userId }
            .map { Connection(it[UsersTable.username], it[FriendshipsTable.status], outgoing) }

    private fun involves(userId: Long): Op<Boolean> =
        (FriendshipsTable.requesterId eq userId) or (FriendshipsTable.addresseeId eq userId)

    private fun pair(a: Long, b: Long): Op<Boolean> =
        ((FriendshipsTable.requesterId eq a) and (FriendshipsTable.addresseeId eq b)) or
            ((FriendshipsTable.requesterId eq b) and (FriendshipsTable.addresseeId eq a))
}
