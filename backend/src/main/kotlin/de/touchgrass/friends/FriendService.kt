package de.touchgrass.friends

import de.touchgrass.plugins.ApiException
import de.touchgrass.plugins.validationError
import de.touchgrass.users.UserRecord
import de.touchgrass.users.UserRepository
import io.ktor.http.HttpStatusCode

class FriendService(private val friendships: FriendshipRepository, private val users: UserRepository) {
    fun overview(userId: Long): FriendsDto {
        val connections = friendships.connections(userId)
        fun names(include: (Connection) -> Boolean) = connections.filter(include).map { it.username }.sortedBy { it.lowercase() }
        return FriendsDto(
            friends = names { it.status == FriendshipStatus.ACCEPTED },
            incoming = names { it.status == FriendshipStatus.PENDING && !it.outgoing },
            outgoing = names { it.status == FriendshipStatus.PENDING && it.outgoing },
        )
    }

    fun request(userId: Long, username: String): FriendsDto {
        val name = username.trim()
        if (name.isEmpty()) validationError("username", "Bitte einen Benutzernamen eingeben.")
        val other = users.findByUsername(name)
            ?: throw ApiException(HttpStatusCode.NotFound, "USER_NOT_FOUND", "Diesen Benutzernamen gibt es nicht.", "username")
        if (other.id == userId) validationError("username", "Du kannst dir nicht selbst eine Anfrage schicken.")
        val existing = friendships.between(userId, other.id)
        when {
            existing == null -> friendships.request(userId, other.id)
            existing.status == FriendshipStatus.ACCEPTED ->
                throw ApiException(HttpStatusCode.Conflict, "ALREADY_FRIENDS", "Ihr seid schon befreundet.", "username")
            existing.requesterId == userId ->
                throw ApiException(HttpStatusCode.Conflict, "REQUEST_PENDING", "Du hast schon eine Anfrage geschickt.", "username")
            else -> friendships.accept(existing.requesterId, userId)
        }
        return overview(userId)
    }

    fun accept(userId: Long, username: String): FriendsDto {
        val (_, existing) = connectionWith(userId, username) ?: throw requestNotFound()
        if (existing.status != FriendshipStatus.PENDING || existing.addresseeId != userId) throw requestNotFound()
        friendships.accept(existing.requesterId, userId)
        return overview(userId)
    }

    fun deleteRequest(userId: Long, username: String): FriendsDto {
        val (other, existing) = connectionWith(userId, username) ?: throw requestNotFound()
        if (existing.status != FriendshipStatus.PENDING) throw requestNotFound()
        friendships.delete(userId, other.id)
        return overview(userId)
    }

    fun remove(userId: Long, username: String): FriendsDto {
        val (other, existing) = connectionWith(userId, username) ?: throw notFriends()
        if (existing.status != FriendshipStatus.ACCEPTED) throw notFriends()
        friendships.delete(userId, other.id)
        return overview(userId)
    }

    private fun connectionWith(userId: Long, username: String): Pair<UserRecord, Friendship>? {
        val other = users.findByUsername(username) ?: return null
        return friendships.between(userId, other.id)?.let { other to it }
    }

    private fun requestNotFound() = ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Anfrage nicht gefunden.")

    private fun notFriends() = ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Ihr seid nicht befreundet.")
}
