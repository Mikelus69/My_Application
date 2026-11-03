package de.touchgrass

import de.touchgrass.friends.FriendsDto
import de.touchgrass.friends.SendFriendRequest
import de.touchgrass.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendRoutesTest {
    private val nobody = FriendsDto(friends = emptyList(), incoming = emptyList(), outgoing = emptyList())

    @BeforeTest
    fun setUp() = TestDatabase.reset()

    private suspend fun HttpClient.sendRequest(token: String, username: String): HttpResponse =
        post("/api/friends/requests") { bearerAuth(token); jsonBody(SendFriendRequest(username)) }

    private suspend fun HttpClient.friends(token: String): FriendsDto = get("/api/friends") { bearerAuth(token) }.body()

    @Test
    fun `Anfrage annehmen macht beide zu Freunden`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa_k").token

        val sent = client.sendRequest(max, " LISA_K ")
        val atLisa = client.friends(lisa)
        val accepted = client.post("/api/friends/requests/max_m/accept") { bearerAuth(lisa) }

        assertEquals(HttpStatusCode.Created, sent.status)
        assertEquals(nobody.copy(outgoing = listOf("lisa_k")), sent.body<FriendsDto>())
        assertEquals(nobody.copy(incoming = listOf("max_m")), atLisa)
        assertEquals(HttpStatusCode.OK, accepted.status)
        assertEquals(nobody.copy(friends = listOf("max_m")), accepted.body<FriendsDto>())
        assertEquals(nobody.copy(friends = listOf("lisa_k")), client.friends(max))
    }

    @Test
    fun `Gegenanfrage macht beide sofort zu Freunden`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa_k").token
        client.sendRequest(max, "lisa_k")

        val answer = client.sendRequest(lisa, "max_m")

        assertEquals(HttpStatusCode.Created, answer.status)
        assertEquals(nobody.copy(friends = listOf("max_m")), answer.body<FriendsDto>())
        assertEquals(nobody.copy(friends = listOf("lisa_k")), client.friends(max))
    }

    @Test
    fun `Ablehnen, Zurückziehen und Beenden löschen die Verbindung`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa_k").token

        client.sendRequest(max, "lisa_k")
        val declined = client.delete("/api/friends/requests/max_m") { bearerAuth(lisa) }.body<FriendsDto>()
        val maxAfterDecline = client.friends(max)
        client.sendRequest(max, "lisa_k")
        val withdrawn = client.delete("/api/friends/requests/lisa_k") { bearerAuth(max) }.body<FriendsDto>()
        val lisaAfterWithdraw = client.friends(lisa)
        client.befriend(max, "max_m", lisa, "lisa_k")
        val ended = client.delete("/api/friends/lisa_k") { bearerAuth(max) }.body<FriendsDto>()
        val lisaAfterEnd = client.friends(lisa)
        val again = client.sendRequest(max, "lisa_k")

        assertEquals(nobody, declined)
        assertEquals(nobody, maxAfterDecline)
        assertEquals(nobody, withdrawn)
        assertEquals(nobody, lisaAfterWithdraw)
        assertEquals(nobody, ended)
        assertEquals(nobody, lisaAfterEnd)
        assertEquals(HttpStatusCode.Created, again.status)
    }

    @Test
    fun `Listen sind alphabetisch ohne Rücksicht auf Groß- und Kleinschreibung`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        for (name in listOf("tom_b", "Anna", "bob")) {
            client.sendRequest(client.register(name).token, "max_m")
        }

        assertEquals(nobody.copy(incoming = listOf("Anna", "bob", "tom_b")), client.friends(max))
    }

    @Test
    fun `Fehler beim Anfragen stehen am Feld username`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa_k").token

        val empty = client.sendRequest(max, "  ")
        val self = client.sendRequest(max, "Max_M")
        val unknown = client.sendRequest(max, "niemand")
        client.sendRequest(max, "lisa_k")
        val twice = client.sendRequest(max, "lisa_k")
        client.post("/api/friends/requests/max_m/accept") { bearerAuth(lisa) }
        val alreadyFriends = client.sendRequest(max, "lisa_k")

        val errors = listOf(empty, self, unknown, twice, alreadyFriends).map { response ->
            val error = response.body<ErrorResponse>()
            listOf(response.status.value.toString(), error.error, error.message, error.field)
        }
        assertEquals(
            listOf(
                listOf("400", "VALIDATION_ERROR", "Bitte einen Benutzernamen eingeben.", "username"),
                listOf("400", "VALIDATION_ERROR", "Du kannst dir nicht selbst eine Anfrage schicken.", "username"),
                listOf("404", "USER_NOT_FOUND", "Diesen Benutzernamen gibt es nicht.", "username"),
                listOf("409", "REQUEST_PENDING", "Du hast schon eine Anfrage geschickt.", "username"),
                listOf("409", "ALREADY_FRIENDS", "Ihr seid schon befreundet.", "username"),
            ),
            errors,
        )
    }

    @Test
    fun `Annehmen, Löschen und Beenden ohne passende Verbindung liefern 404`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        client.register("lisa_k")
        client.sendRequest(max, "lisa_k")

        val ownRequest = client.post("/api/friends/requests/lisa_k/accept") { bearerAuth(max) }
        val unknown = client.delete("/api/friends/requests/niemand") { bearerAuth(max) }
        val notFriends = client.delete("/api/friends/lisa_k") { bearerAuth(max) }

        assertEquals(HttpStatusCode.NotFound, ownRequest.status)
        assertEquals("Anfrage nicht gefunden.", ownRequest.body<ErrorResponse>().message)
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals("Anfrage nicht gefunden.", unknown.body<ErrorResponse>().message)
        assertEquals(HttpStatusCode.NotFound, notFriends.status)
        assertEquals("Ihr seid nicht befreundet.", notFriends.body<ErrorResponse>().message)
        assertEquals(nobody.copy(outgoing = listOf("lisa_k")), client.friends(max))
    }

    @Test
    fun `Konto löschen entfernt seine Freundschaften und Anfragen`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa_k").token
        val tom = client.register("tom_b").token
        client.befriend(max, "max_m", lisa, "lisa_k")
        client.sendRequest(lisa, "tom_b")

        client.delete("/api/me") { bearerAuth(lisa) }

        assertEquals(nobody, client.friends(max))
        assertEquals(nobody, client.friends(tom))
    }

    @Test
    fun `Freunde brauchen eine Anmeldung`() = testApplication {
        val client = startApp()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/friends").status)
    }
}
