package de.touchgrass.friends

import kotlinx.serialization.Serializable

@Serializable
data class FriendsDto(val friends: List<String>, val incoming: List<String>, val outgoing: List<String>)

@Serializable
data class SendFriendRequest(val username: String)
