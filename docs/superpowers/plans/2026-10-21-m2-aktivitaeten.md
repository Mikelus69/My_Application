# M2 Aktivitäten – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktivitäten von Hand eintragen (mit geschätzten kcal und Punkten inklusive Boni), im Verlauf mit Filtern ansehen, Details öffnen und löschen.

**Architecture:** Das Backend bekommt reine Rechenfunktionen in `logic/` (kcal, Punkte, Wochenziel-Bonus, Berliner Wochengrenzen), die Tabelle `activities`, einen `ActivityService` mit allen Regeln und die Endpunkte `/sports` und `/activities`. Die App bekommt im ➕-Tab ein Formular und im Profil-Bereich Verlauf und Detailansicht. Timer und Ortswahl kommen erst in M3, Achievements in M4.

**Tech Stack:** Backend: Exposed 1.6.0 mit `exposed-java-time` (Spalten mit Zeitzone), Ktor 3.6.0. App: Material3 1.4 `DatePicker`/`TimePicker`, `java.time`, Navigation Compose 2.10.2 mit Routen-Parametern.

**Spec:** `docs/superpowers/specs/2026-10-20-touchgrass-design.md` (Abschnitte 3, 4, 5, 6) · **API-Vertrag:** `docs/api.md`

## Global Constraints

- Alle Commits direkt auf `master`, keine Branches. Commit-Nachrichten auf Deutsch mit Trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Alle Tages- und Wochenberechnungen in `Europe/Berlin`, Woche = Montag 00:00 bis Montag 00:00. Zeitpunkte gibt das Backend in Berliner Zeit mit Sekunden aus (`2026-10-05T09:30:00+02:00`).
- kcal = `round(MET × Gewicht_kg × Dauer_min / 60)`, ohne Gewicht 70 kg. kcal und Punkte werden beim Anlegen gespeichert.
- Punkte = Minuten + 10, +20 für die erste Aktivität dieser Sportart überhaupt, +50, wenn das Wochenziel (Woche von `startedAt`) mit dieser Aktivität erreicht wird und in der Woche noch keine Aktivität `goal_bonus = true` hat.
- Prüfregeln: Sportart muss existieren, Dauer 1–1440, Startzeit höchstens 5 Minuten in der Zukunft, Breite/Länge nur zusammen (−90…90 / −180…180), Beschreibung höchstens 500 Zeichen (vorher getrimmt, leer = keine).
- Sichtbarkeit `PRIVATE` (Standard) oder `PUBLIC`. Fremde private Aktivität → `404`, fremde löschen → `403`.
- `newAchievements` in der Antwort von `POST /activities` kommt erst mit M4.
- App: `minSdk` wird von 24 auf **26** angehoben, weil Datum und Uhrzeit mit `java.time` arbeiten (gibt es ab Android 8.0).
- App bleibt bei Kotlin 2.2.10 (`kotlinx-serialization-json` 1.9.0). Tests: Backend `kotlin.test`, App JUnit 4.
- Testnamen deutsch in Backticks, Code englisch, UI-Texte deutsch, wenig Kommentare.

## Ausführungshinweise

- Vor Gradle: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'`. Backend-Befehle im Ordner `backend`, App-Befehle im Wurzelordner.
- **Für Claude:** Gradle nur im Terminal-Panel (`run_in_terminal`). Testergebnisse in `**/build/test-results/**/*.xml` prüfen.
- **Nach dem Plan in Android Studio „Sync Project with Gradle Files“** (neue Abhängigkeiten, `minSdk`), sonst ist der neue Code rot.
- Die lokale Entwicklungsdatenbank migriert Flyway beim nächsten `gradlew run` automatisch auf Version 3.

## Dateiübersicht

```
backend/src/main/
├── kotlin/de/touchgrass/
│   ├── logic/BerlinTime.kt          TimeRange, weekOf, startOfDay, format
│   ├── logic/Calories.kt            kcal-Schätzung
│   ├── logic/Points.kt              PointsBreakdown + Berechnung
│   ├── logic/WeeklyGoal.kt          WeekTotals + Bonus-Regel
│   ├── sports/SportRepository.kt    SportTypesTable, SportDto
│   ├── activities/ActivityDtos.kt
│   ├── activities/ActivityValidation.kt
│   ├── activities/ActivityRepository.kt   ActivitiesTable, NewActivity, ActivityRecord
│   ├── activities/ActivityService.kt
│   ├── routes/SportRoutes.kt, routes/ActivityRoutes.kt
│   └── Application.kt               (verdrahtet alles)
└── resources/db/migration/V3__activities.sql

backend/src/test/kotlin/de/touchgrass/
├── logic/LogicTest.kt               Calories, Points, WeeklyGoal, BerlinTime
├── activities/ActivityValidationTest.kt
├── SportRoutesTest.kt, ActivityCreateTest.kt, ActivityAccessTest.kt
└── TestApp.kt                       + activity(), createActivity()

app/src/main/java/de/touchgrass/
├── data/dto/ActivityDtos.kt
├── data/SportApi.kt                 + sports, createActivity, activities, activity, deleteActivity
└── ui/
    ├── activities/ActivityFormat.kt, HistoryViewModel.kt, HistoryScreen.kt,
    │   ActivityDetailViewModel.kt, ActivityDetailScreen.kt
    ├── record/RecordViewModel.kt, record/RecordScreen.kt
    ├── profile/ProfileScreen.kt     + Eintrag „Verlauf“
    └── Navigation.kt                + Record-Tab, HistoryRoute, ActivityDetailRoute(id)

app/src/test/java/de/touchgrass/
├── testing/Fakes.kt                 + neue API-Methoden, testSports, testActivity()
├── data/SportApiTest.kt             + 3 Tests
└── ui/activities/…, ui/record/…     ViewModel- und Format-Tests
```

---

### Task 1: Backend – Rechenregeln

**Files:**
- Create: `backend/src/main/kotlin/de/touchgrass/logic/BerlinTime.kt`, `.../logic/Calories.kt`, `.../logic/Points.kt`, `.../logic/WeeklyGoal.kt`
- Test: `backend/src/test/kotlin/de/touchgrass/logic/LogicTest.kt`

**Interfaces:**
- Consumes: `de.touchgrass.users.GoalType` (M1)
- Produces: `data class TimeRange(from: OffsetDateTime, until: OffsetDateTime)` (until exklusiv); `object BerlinTime { val ZONE; fun startOfDay(LocalDate): OffsetDateTime; fun weekOf(OffsetDateTime): TimeRange; fun format(OffsetDateTime): String }`; `object Calories { const val DEFAULT_WEIGHT_KG = 70.0; fun estimate(met: Double, weightKg: Double?, durationMin: Int): Int }`; `@Serializable data class PointsBreakdown(minutes, completed, newSport, weeklyGoal, total)`; `object Points { fun calculate(durationMin: Int, firstOfSport: Boolean, weeklyGoalReached: Boolean): PointsBreakdown }`; `data class WeekTotals(minutes = 0, activities = 0, kcal = 0) { fun valueFor(GoalType): Int; fun plusActivity(durationMin, kcal): WeekTotals }`; `object WeeklyGoal { fun bonusEarned(type, target, before, after, bonusAlreadyGiven): Boolean }`.

- [ ] **Step 1: Failing Test schreiben**

`backend/src/test/kotlin/de/touchgrass/logic/LogicTest.kt`:
```kotlin
package de.touchgrass.logic

import de.touchgrass.users.GoalType
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaloriesTest {
    @Test
    fun `kcal ist MET mal Gewicht mal Stunden, gerundet`() {
        assertEquals(480, Calories.estimate(met = 8.0, weightKg = 80.0, durationMin = 45))
        assertEquals(169, Calories.estimate(met = 3.5, weightKg = 78.5, durationMin = 37))
    }

    @Test
    fun `ohne Gewicht wird mit 70 kg gerechnet`() {
        assertEquals(280, Calories.estimate(met = 8.0, weightKg = null, durationMin = 30))
    }
}

class PointsTest {
    @Test
    fun `Minuten plus 10 für die Aktivität`() {
        assertEquals(PointsBreakdown(48, 10, 0, 0, 58), Points.calculate(48, firstOfSport = false, weeklyGoalReached = false))
    }

    @Test
    fun `neue Sportart und Wochenziel geben Bonuspunkte`() {
        assertEquals(PointsBreakdown(48, 10, 20, 50, 128), Points.calculate(48, firstOfSport = true, weeklyGoalReached = true))
    }
}

class WeeklyGoalTest {
    private val before = WeekTotals(minutes = 120, activities = 2, kcal = 900)

    @Test
    fun `Bonus nur, wenn das Ziel genau mit dieser Aktivität erreicht wird`() {
        assertTrue(WeeklyGoal.bonusEarned(GoalType.MINUTES, 150, before, before.plusActivity(30, 200), bonusAlreadyGiven = false))
        assertFalse(WeeklyGoal.bonusEarned(GoalType.MINUTES, 150, before, before.plusActivity(20, 200), bonusAlreadyGiven = false))
        assertFalse(WeeklyGoal.bonusEarned(GoalType.MINUTES, 100, before, before.plusActivity(30, 200), bonusAlreadyGiven = false))
    }

    @Test
    fun `höchstens ein Bonus pro Woche`() {
        assertFalse(WeeklyGoal.bonusEarned(GoalType.MINUTES, 150, before, before.plusActivity(30, 200), bonusAlreadyGiven = true))
    }

    @Test
    fun `Zieltyp bestimmt den gezählten Wert`() {
        assertTrue(WeeklyGoal.bonusEarned(GoalType.ACTIVITIES, 3, before, before.plusActivity(5, 40), bonusAlreadyGiven = false))
        assertTrue(WeeklyGoal.bonusEarned(GoalType.KCAL, 1000, before, before.plusActivity(15, 100), bonusAlreadyGiven = false))
    }
}

class BerlinTimeTest {
    @Test
    fun `Woche geht von Montag 0 Uhr bis Montag 0 Uhr Berliner Zeit`() {
        val week = BerlinTime.weekOf(OffsetDateTime.parse("2026-10-08T23:30:00+02:00"))

        assertEquals(OffsetDateTime.parse("2026-10-05T00:00:00+02:00"), week.from)
        assertEquals(OffsetDateTime.parse("2026-10-12T00:00:00+02:00"), week.until)
    }

    @Test
    fun `Sonntagabend in UTC gehört schon zur nächsten Berliner Woche`() {
        val week = BerlinTime.weekOf(OffsetDateTime.parse("2026-10-11T22:30:00Z"))

        assertEquals(OffsetDateTime.parse("2026-10-12T00:00:00+02:00"), week.from)
    }

    @Test
    fun `Woche mit Zeitumstellung endet in Winterzeit`() {
        val week = BerlinTime.weekOf(OffsetDateTime.parse("2026-10-21T12:00:00+02:00"))

        assertEquals(OffsetDateTime.parse("2026-10-19T00:00:00+02:00"), week.from)
        assertEquals(OffsetDateTime.parse("2026-10-26T00:00:00+01:00"), week.until)
    }

    @Test
    fun `Zeitpunkte werden in Berliner Zeit mit Sekunden ausgegeben`() {
        assertEquals("2026-10-05T09:30:00+02:00", BerlinTime.format(OffsetDateTime.parse("2026-10-05T07:30Z")))
        assertEquals(OffsetDateTime.parse("2026-10-05T00:00:00+02:00"), BerlinTime.startOfDay(LocalDate.parse("2026-10-05")))
    }
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'Calories'`, `'Points'`, `'WeekTotals'`, `'BerlinTime'`.

- [ ] **Step 3: Implementierung schreiben**

`backend/src/main/kotlin/de/touchgrass/logic/BerlinTime.kt`:
```kotlin
package de.touchgrass.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class TimeRange(val from: OffsetDateTime, val until: OffsetDateTime)

object BerlinTime {
    val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

    fun startOfDay(date: LocalDate): OffsetDateTime = date.atStartOfDay(ZONE).toOffsetDateTime()

    fun weekOf(moment: OffsetDateTime): TimeRange {
        val monday = moment.atZoneSameInstant(ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return TimeRange(startOfDay(monday), startOfDay(monday.plusWeeks(1)))
    }

    fun format(moment: OffsetDateTime): String =
        moment.atZoneSameInstant(ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
```

`backend/src/main/kotlin/de/touchgrass/logic/Calories.kt`:
```kotlin
package de.touchgrass.logic

import kotlin.math.roundToInt

object Calories {
    const val DEFAULT_WEIGHT_KG = 70.0

    fun estimate(met: Double, weightKg: Double?, durationMin: Int): Int =
        (met * (weightKg ?: DEFAULT_WEIGHT_KG) * durationMin / 60.0).roundToInt()
}
```

`backend/src/main/kotlin/de/touchgrass/logic/Points.kt`:
```kotlin
package de.touchgrass.logic

import kotlinx.serialization.Serializable

@Serializable
data class PointsBreakdown(val minutes: Int, val completed: Int, val newSport: Int, val weeklyGoal: Int, val total: Int)

object Points {
    const val PER_ACTIVITY = 10
    const val NEW_SPORT = 20
    const val WEEKLY_GOAL = 50

    fun calculate(durationMin: Int, firstOfSport: Boolean, weeklyGoalReached: Boolean): PointsBreakdown {
        val newSport = if (firstOfSport) NEW_SPORT else 0
        val weeklyGoal = if (weeklyGoalReached) WEEKLY_GOAL else 0
        return PointsBreakdown(
            minutes = durationMin,
            completed = PER_ACTIVITY,
            newSport = newSport,
            weeklyGoal = weeklyGoal,
            total = durationMin + PER_ACTIVITY + newSport + weeklyGoal,
        )
    }
}
```

`backend/src/main/kotlin/de/touchgrass/logic/WeeklyGoal.kt`:
```kotlin
package de.touchgrass.logic

import de.touchgrass.users.GoalType

data class WeekTotals(val minutes: Int = 0, val activities: Int = 0, val kcal: Int = 0) {
    fun valueFor(type: GoalType): Int = when (type) {
        GoalType.MINUTES -> minutes
        GoalType.ACTIVITIES -> activities
        GoalType.KCAL -> kcal
    }

    fun plusActivity(durationMin: Int, kcal: Int) = WeekTotals(minutes + durationMin, activities + 1, this.kcal + kcal)
}

object WeeklyGoal {
    fun bonusEarned(type: GoalType, target: Int, before: WeekTotals, after: WeekTotals, bonusAlreadyGiven: Boolean): Boolean =
        !bonusAlreadyGiven && before.valueFor(type) < target && after.valueFor(type) >= target
}
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`; `CaloriesTest` 2, `PointsTest` 2, `WeeklyGoalTest` 3, `BerlinTimeTest` 4 – alle `PASSED`, alte Tests weiterhin grün.

- [ ] **Step 5: Commit**

```powershell
git add backend/src
git commit -m "Rechenregeln für kcal, Punkte, Wochenziel und Berliner Wochen" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Backend – Sportarten-Endpunkt

**Files:**
- Create: `backend/src/main/kotlin/de/touchgrass/sports/SportRepository.kt`, `.../routes/SportRoutes.kt`
- Modify: `backend/src/main/kotlin/de/touchgrass/Application.kt`
- Test: `backend/src/test/kotlin/de/touchgrass/SportRoutesTest.kt`

**Interfaces:**
- Consumes: Tabelle `sport_types` (M0), `AUTH_JWT` (M1)
- Produces: `object SportTypesTable`, `@Serializable data class SportDto(code, name, emoji, met: Double)`, `class SportRepository(database) { fun all(): List<SportDto>; fun find(code: String): SportDto? }`, `fun Route.sportRoutes(sports: SportRepository)` → `GET /api/sports` (mit Token, alphabetisch nach Name).

- [ ] **Step 1: Failing Test schreiben**

`backend/src/test/kotlin/de/touchgrass/SportRoutesTest.kt`:
```kotlin
package de.touchgrass

import de.touchgrass.sports.SportDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SportRoutesTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `sports liefert die acht Sportarten alphabetisch`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.get("/api/sports") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val sports = response.body<List<SportDto>>()
        assertEquals(8, sports.size)
        assertEquals("Basketball", sports.first().name)
        assertEquals(SportDto("RUNNING", "Joggen", "🏃", 8.0), sports.single { it.code == "RUNNING" })
    }

    @Test
    fun `sports braucht einen Token`() = testApplication {
        val client = startApp()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/sports").status)
    }
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'sports'` (Paket `de.touchgrass.sports` fehlt).

- [ ] **Step 3: Implementierung schreiben**

`backend/src/main/kotlin/de/touchgrass/sports/SportRepository.kt`:
```kotlin
package de.touchgrass.sports

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object SportTypesTable : Table("sport_types") {
    val code = varchar("code", 30)
    val name = varchar("name", 50)
    val emoji = varchar("emoji", 10)
    val met = decimal("met", 4, 1)
    override val primaryKey = PrimaryKey(code)
}

@Serializable
data class SportDto(val code: String, val name: String, val emoji: String, val met: Double)

class SportRepository(private val database: Database) {
    fun all(): List<SportDto> = transaction(database) {
        SportTypesTable.selectAll().orderBy(SportTypesTable.name).map { it.toDto() }
    }

    fun find(code: String): SportDto? = transaction(database) {
        SportTypesTable.selectAll().where { SportTypesTable.code eq code }.singleOrNull()?.toDto()
    }

    private fun ResultRow.toDto() = SportDto(
        code = this[SportTypesTable.code],
        name = this[SportTypesTable.name],
        emoji = this[SportTypesTable.emoji],
        met = this[SportTypesTable.met].toDouble(),
    )
}
```

`backend/src/main/kotlin/de/touchgrass/routes/SportRoutes.kt`:
```kotlin
package de.touchgrass.routes

import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.sports.SportRepository
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.sportRoutes(sports: SportRepository) {
    authenticate(AUTH_JWT) {
        get("/sports") {
            call.respond(sports.all())
        }
    }
}
```

`backend/src/main/kotlin/de/touchgrass/Application.kt` komplett ersetzen:
```kotlin
package de.touchgrass

import de.touchgrass.auth.JwtSecret
import de.touchgrass.auth.JwtService
import de.touchgrass.auth.configureSecurity
import de.touchgrass.config.AppConfig
import de.touchgrass.db.DatabaseFactory
import de.touchgrass.plugins.configureSerialization
import de.touchgrass.plugins.configureStatusPages
import de.touchgrass.routes.authRoutes
import de.touchgrass.routes.healthRoutes
import de.touchgrass.routes.meRoutes
import de.touchgrass.routes.sportRoutes
import de.touchgrass.sports.SportRepository
import de.touchgrass.users.UserRepository
import de.touchgrass.users.UserService
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path

fun main() {
    val config = AppConfig.fromEnvironment()
    val appDatabase = DatabaseFactory.start(config)
    Runtime.getRuntime().addShutdownHook(Thread { appDatabase.close() })
    val jwtService = JwtService(JwtSecret.load(config.jwtSecret, Path.of(config.dataDir)))
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(appDatabase.database, jwtService)
    }.start(wait = true)
}

fun Application.module(database: Database, jwtService: JwtService) {
    val userRepository = UserRepository(database)
    val sportRepository = SportRepository(database)
    val userService = UserService(userRepository, jwtService)

    configureSerialization()
    configureStatusPages()
    configureSecurity(jwtService)
    routing {
        route("/api") {
            healthRoutes(database)
            authRoutes(userService)
            meRoutes(userService)
            sportRoutes(sportRepository)
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, `SportRoutesTest` 2 × `PASSED`.

- [ ] **Step 5: Commit**

```powershell
git add backend/src
git commit -m "Endpunkt für Sportarten" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Backend – Aktivität anlegen

**Files:**
- Modify: `backend/gradle/libs.versions.toml`, `backend/build.gradle.kts`
- Create: `backend/src/main/resources/db/migration/V3__activities.sql`
- Create: `backend/src/main/kotlin/de/touchgrass/activities/ActivityDtos.kt`, `.../ActivityValidation.kt`, `.../ActivityRepository.kt`, `.../ActivityService.kt`
- Create: `backend/src/main/kotlin/de/touchgrass/routes/ActivityRoutes.kt`
- Modify: `backend/src/main/kotlin/de/touchgrass/Application.kt`, `backend/src/test/kotlin/de/touchgrass/TestApp.kt`
- Test: `backend/src/test/kotlin/de/touchgrass/activities/ActivityValidationTest.kt`, `backend/src/test/kotlin/de/touchgrass/ActivityCreateTest.kt`
- Modify: `docs/api.md`

**Interfaces:**
- Consumes: alles aus Task 1 und 2, `UserRepository.findById` (M1), `validationError`, `ApiException`
- Produces: Tabelle `activities`; `enum class Visibility { PRIVATE, PUBLIC }`; `CreateActivityRequest(sportCode, startedAt: String, durationMin, latitude?, longitude?, description?, visibility = PRIVATE)`; `ActivityDto(id, username, mine, sportCode, startedAt, durationMin, caloriesKcal, points, latitude?, longitude?, description?, visibility)`; `CreateActivityResponse(activity, pointsBreakdown)`; `object ActivityValidation { startedAt(String, now): OffsetDateTime; durationMin(Int); location(Double?, Double?); description(String?); dateFilter(String?, field): LocalDate? }`; `object ActivitiesTable`; `NewActivity`, `ActivityRecord`; `class ActivityRepository(database) { insert; findById; list(userId, sportCode?, from?, until?); inRange(userId, TimeRange); hasSport; delete }`; `class ActivityService(activities, sports, users, clock = Clock.systemUTC()) { fun create(userId, request): CreateActivityResponse }` (Task 4 ergänzt `list`, `get`, `delete`); Test-Hilfen `fun activity(sportCode = "RUNNING", startedAt = "2026-10-05T09:30:00+02:00", durationMin = 48, visibility = PRIVATE): CreateActivityRequest`, `suspend fun HttpClient.createActivity(token, request = activity()): CreateActivityResponse`.

- [ ] **Step 1: Abhängigkeit `exposed-java-time` eintragen**

`backend/gradle/libs.versions.toml`, nach der Zeile `exposed-jdbc = …` einfügen:
```toml
exposed-java-time = { module = "org.jetbrains.exposed:exposed-java-time", version.ref = "exposed" }
```

`backend/build.gradle.kts`, nach `implementation(libs.exposed.jdbc)` einfügen:
```kotlin
    implementation(libs.exposed.java.time)
```

- [ ] **Step 2: Failing Tests schreiben**

`backend/src/test/kotlin/de/touchgrass/TestApp.kt` komplett ersetzen:
```kotlin
package de.touchgrass

import de.touchgrass.activities.CreateActivityRequest
import de.touchgrass.activities.CreateActivityResponse
import de.touchgrass.activities.Visibility
import de.touchgrass.auth.JwtService
import de.touchgrass.users.AuthResponse
import de.touchgrass.users.RegisterRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder

val testJwtService = JwtService("test-secret")

fun ApplicationTestBuilder.startApp(): HttpClient {
    application { module(TestDatabase.database, testJwtService) }
    return createClient { install(ContentNegotiation) { json() } }
}

inline fun <reified T> HttpRequestBuilder.jsonBody(body: T) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

suspend fun HttpClient.register(
    username: String,
    email: String = "$username@example.com",
    password: String = "geheim123",
): AuthResponse = post("/api/auth/register") { jsonBody(RegisterRequest(username, email, password)) }.body()

fun activity(
    sportCode: String = "RUNNING",
    startedAt: String = "2026-10-05T09:30:00+02:00",
    durationMin: Int = 48,
    visibility: Visibility = Visibility.PRIVATE,
) = CreateActivityRequest(sportCode = sportCode, startedAt = startedAt, durationMin = durationMin, visibility = visibility)

suspend fun HttpClient.createActivity(token: String, request: CreateActivityRequest = activity()): CreateActivityResponse =
    post("/api/activities") { bearerAuth(token); jsonBody(request) }.body()
```

`backend/src/test/kotlin/de/touchgrass/activities/ActivityValidationTest.kt`:
```kotlin
package de.touchgrass.activities

import de.touchgrass.plugins.ApiException
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ActivityValidationTest {
    private val now = OffsetDateTime.parse("2026-10-05T12:00:00+02:00")

    private fun failingField(block: () -> Unit): String? = assertFailsWith<ApiException> { block() }.field

    @Test
    fun `Startzeit darf höchstens 5 Minuten in der Zukunft liegen`() {
        assertEquals(now.plusMinutes(5), ActivityValidation.startedAt("2026-10-05T12:05:00+02:00", now))
        assertEquals("startedAt", failingField { ActivityValidation.startedAt("2026-10-05T12:06:00+02:00", now) })
        assertEquals("startedAt", failingField { ActivityValidation.startedAt("gestern", now) })
    }

    @Test
    fun `Dauer zwischen 1 und 1440 Minuten`() {
        ActivityValidation.durationMin(1)
        ActivityValidation.durationMin(1440)
        assertEquals("durationMin", failingField { ActivityValidation.durationMin(0) })
        assertEquals("durationMin", failingField { ActivityValidation.durationMin(1441) })
    }

    @Test
    fun `Koordinaten nur zusammen und im gültigen Bereich`() {
        ActivityValidation.location(null, null)
        ActivityValidation.location(48.16, 11.6)
        assertEquals("latitude", failingField { ActivityValidation.location(48.16, null) })
        assertEquals("latitude", failingField { ActivityValidation.location(91.0, 11.6) })
        assertEquals("longitude", failingField { ActivityValidation.location(48.16, 181.0) })
    }

    @Test
    fun `Beschreibung höchstens 500 Zeichen`() {
        ActivityValidation.description("x".repeat(500))
        assertEquals("description", failingField { ActivityValidation.description("x".repeat(501)) })
    }

    @Test
    fun `Datumsfilter im Format JJJJ-MM-TT`() {
        assertEquals(LocalDate.parse("2026-10-01"), ActivityValidation.dateFilter("2026-10-01", "from"))
        assertNull(ActivityValidation.dateFilter(null, "from"))
        assertEquals("to", failingField { ActivityValidation.dateFilter("01.10.2026", "to") })
    }
}
```

`backend/src/test/kotlin/de/touchgrass/ActivityCreateTest.kt`:
```kotlin
package de.touchgrass

import de.touchgrass.activities.CreateActivityResponse
import de.touchgrass.activities.Visibility
import de.touchgrass.logic.PointsBreakdown
import de.touchgrass.plugins.ErrorResponse
import de.touchgrass.users.GoalType
import de.touchgrass.users.UpdateMeRequest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityCreateTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `erste Aktivität bekommt kcal, Punkte und den Bonus für die neue Sportart`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        client.patch("/api/me") { bearerAuth(token); jsonBody(UpdateMeRequest(weightKg = 80.0)) }

        val response = client.post("/api/activities") {
            bearerAuth(token)
            jsonBody(activity(startedAt = "2026-10-05T07:30:00Z", durationMin = 45).copy(description = "  Schöne Runde  "))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<CreateActivityResponse>()
        assertTrue(body.activity.mine)
        assertEquals("max_m", body.activity.username)
        assertEquals("2026-10-05T09:30:00+02:00", body.activity.startedAt)
        assertEquals(480, body.activity.caloriesKcal)
        assertEquals("Schöne Runde", body.activity.description)
        assertEquals(Visibility.PRIVATE, body.activity.visibility)
        assertNull(body.activity.latitude)
        assertEquals(PointsBreakdown(45, 10, 20, 0, 75), body.pointsBreakdown)
        assertEquals(75, body.activity.points)
    }

    @Test
    fun `gleiche Sportart ein zweites Mal gibt keinen Sportart-Bonus`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        client.createActivity(token)

        val second = client.createActivity(token, activity(startedAt = "2026-10-06T18:00:00+02:00", durationMin = 30))

        assertEquals(PointsBreakdown(30, 10, 0, 0, 40), second.pointsBreakdown)
    }

    @Test
    fun `Wochenziel-Bonus genau einmal, wenn das Ziel erreicht wird`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        client.patch("/api/me") { bearerAuth(token); jsonBody(UpdateMeRequest(goalType = GoalType.MINUTES, goalTarget = 60)) }

        val first = client.createActivity(token, activity(startedAt = "2026-10-05T09:00:00+02:00", durationMin = 40))
        val second = client.createActivity(token, activity(sportCode = "CYCLING", startedAt = "2026-10-07T09:00:00+02:00", durationMin = 30))
        val third = client.createActivity(token, activity(sportCode = "HIKING", startedAt = "2026-10-08T09:00:00+02:00", durationMin = 30))
        val nextWeek = client.createActivity(token, activity(startedAt = "2026-10-12T09:00:00+02:00", durationMin = 60))

        assertEquals(0, first.pointsBreakdown.weeklyGoal)
        assertEquals(50, second.pointsBreakdown.weeklyGoal)
        assertEquals(0, third.pointsBreakdown.weeklyGoal)
        assertEquals(50, nextWeek.pointsBreakdown.weeklyGoal)
    }

    @Test
    fun `unbekannte Sportart liefert 400 am Feld sportCode`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.post("/api/activities") { bearerAuth(token); jsonBody(activity(sportCode = "CURLING")) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorResponse("VALIDATION_ERROR", "Unbekannte Sportart.", "sportCode"), response.body<ErrorResponse>())
    }

    @Test
    fun `Startzeit in der Zukunft liefert 400 am Feld startedAt`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.post("/api/activities") { bearerAuth(token); jsonBody(activity(startedAt = "2099-01-01T10:00:00+01:00")) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("startedAt", response.body<ErrorResponse>().field)
    }

    @Test
    fun `Anlegen braucht einen Token`() = testApplication {
        val client = startApp()

        assertEquals(HttpStatusCode.Unauthorized, client.post("/api/activities") { jsonBody(activity()) }.status)
    }
}
```

- [ ] **Step 3: Tests laufen lassen, sie müssen fehlschlagen**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'activities'` (Paket `de.touchgrass.activities` fehlt).

- [ ] **Step 4: Implementierung schreiben**

`backend/src/main/resources/db/migration/V3__activities.sql`:
```sql
CREATE TABLE activities (
    id            BIGSERIAL        PRIMARY KEY,
    user_id       BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sport_code    VARCHAR(30)      NOT NULL REFERENCES sport_types (code),
    started_at    TIMESTAMPTZ      NOT NULL,
    duration_min  INT              NOT NULL,
    calories_kcal INT              NOT NULL,
    points        INT              NOT NULL,
    goal_bonus    BOOLEAN          NOT NULL DEFAULT FALSE,
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    description   VARCHAR(500),
    visibility    VARCHAR(10)      NOT NULL DEFAULT 'PRIVATE',
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX activities_user_started ON activities (user_id, started_at DESC);
```

`backend/src/main/kotlin/de/touchgrass/activities/ActivityDtos.kt`:
```kotlin
package de.touchgrass.activities

import de.touchgrass.logic.PointsBreakdown
import kotlinx.serialization.Serializable

@Serializable
enum class Visibility { PRIVATE, PUBLIC }

@Serializable
data class CreateActivityRequest(
    val sportCode: String,
    val startedAt: String,
    val durationMin: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val visibility: Visibility = Visibility.PRIVATE,
)

@Serializable
data class ActivityDto(
    val id: Long,
    val username: String,
    val mine: Boolean,
    val sportCode: String,
    val startedAt: String,
    val durationMin: Int,
    val caloriesKcal: Int,
    val points: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val visibility: Visibility,
)

@Serializable
data class CreateActivityResponse(val activity: ActivityDto, val pointsBreakdown: PointsBreakdown)
```

`backend/src/main/kotlin/de/touchgrass/activities/ActivityValidation.kt`:
```kotlin
package de.touchgrass.activities

import de.touchgrass.plugins.validationError
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

object ActivityValidation {
    fun startedAt(value: String, now: OffsetDateTime): OffsetDateTime {
        val parsed = try {
            OffsetDateTime.parse(value)
        } catch (e: DateTimeParseException) {
            validationError("startedAt", "Bitte Datum und Uhrzeit angeben.")
        }
        if (parsed.isAfter(now.plusMinutes(5))) {
            validationError("startedAt", "Die Aktivität darf nicht in der Zukunft liegen.")
        }
        return parsed
    }

    fun durationMin(value: Int) {
        if (value !in 1..1440) {
            validationError("durationMin", "Die Dauer muss zwischen 1 und 1440 Minuten liegen.")
        }
    }

    fun location(latitude: Double?, longitude: Double?) {
        if ((latitude == null) != (longitude == null)) {
            validationError("latitude", "Breite und Länge nur zusammen angeben.")
        }
        if (latitude != null && latitude !in -90.0..90.0) {
            validationError("latitude", "Die Breite muss zwischen -90 und 90 liegen.")
        }
        if (longitude != null && longitude !in -180.0..180.0) {
            validationError("longitude", "Die Länge muss zwischen -180 und 180 liegen.")
        }
    }

    fun description(value: String?) {
        if (value != null && value.length > 500) {
            validationError("description", "Die Beschreibung darf höchstens 500 Zeichen haben.")
        }
    }

    fun dateFilter(value: String?, field: String): LocalDate? = value?.let {
        try {
            LocalDate.parse(it)
        } catch (e: DateTimeParseException) {
            validationError(field, "Datum bitte im Format JJJJ-MM-TT angeben.")
        }
    }
}
```

`backend/src/main/kotlin/de/touchgrass/activities/ActivityRepository.kt`:
```kotlin
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
import org.jetbrains.exposed.v1.core.less
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
```

`backend/src/main/kotlin/de/touchgrass/activities/ActivityService.kt` (Stand nach Task 3; Task 4 ergänzt `list`, `get`, `delete`):
```kotlin
package de.touchgrass.activities

import de.touchgrass.logic.BerlinTime
import de.touchgrass.logic.Calories
import de.touchgrass.logic.Points
import de.touchgrass.logic.WeekTotals
import de.touchgrass.logic.WeeklyGoal
import de.touchgrass.plugins.ApiException
import de.touchgrass.plugins.validationError
import de.touchgrass.sports.SportRepository
import de.touchgrass.users.UserRepository
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.OffsetDateTime

class ActivityService(
    private val activities: ActivityRepository,
    private val sports: SportRepository,
    private val users: UserRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(userId: Long, request: CreateActivityRequest): CreateActivityResponse {
        val user = users.findById(userId) ?: throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Bitte melde dich an.")
        val sport = sports.find(request.sportCode) ?: validationError("sportCode", "Unbekannte Sportart.")
        val startedAt = ActivityValidation.startedAt(request.startedAt, OffsetDateTime.now(clock))
        ActivityValidation.durationMin(request.durationMin)
        ActivityValidation.location(request.latitude, request.longitude)
        val description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        ActivityValidation.description(description)

        val kcal = Calories.estimate(sport.met, user.weightKg, request.durationMin)
        val week = activities.inRange(userId, BerlinTime.weekOf(startedAt))
        val before = WeekTotals(week.sumOf { it.durationMin }, week.size, week.sumOf { it.caloriesKcal })
        val goalBonus = WeeklyGoal.bonusEarned(
            user.goalType,
            user.goalTarget,
            before,
            before.plusActivity(request.durationMin, kcal),
            bonusAlreadyGiven = week.any { it.goalBonus },
        )
        val breakdown = Points.calculate(request.durationMin, !activities.hasSport(userId, sport.code), goalBonus)

        val id = activities.insert(
            NewActivity(
                userId = userId,
                sportCode = sport.code,
                startedAt = startedAt,
                durationMin = request.durationMin,
                caloriesKcal = kcal,
                points = breakdown.total,
                goalBonus = goalBonus,
                latitude = request.latitude,
                longitude = request.longitude,
                description = description,
                visibility = request.visibility,
            )
        )
        return CreateActivityResponse(activities.findById(id)!!.toDto(viewerId = userId), breakdown)
    }

    private fun ActivityRecord.toDto(viewerId: Long) = ActivityDto(
        id = id,
        username = username,
        mine = userId == viewerId,
        sportCode = sportCode,
        startedAt = BerlinTime.format(startedAt),
        durationMin = durationMin,
        caloriesKcal = caloriesKcal,
        points = points,
        latitude = latitude,
        longitude = longitude,
        description = description,
        visibility = visibility,
    )
}
```

`backend/src/main/kotlin/de/touchgrass/routes/ActivityRoutes.kt` (Stand nach Task 3; Task 4 ergänzt Lesen und Löschen):
```kotlin
package de.touchgrass.routes

import de.touchgrass.activities.ActivityService
import de.touchgrass.activities.CreateActivityRequest
import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.activityRoutes(service: ActivityService) {
    authenticate(AUTH_JWT) {
        route("/activities") {
            post {
                call.respond(HttpStatusCode.Created, service.create(call.userId(), call.receive<CreateActivityRequest>()))
            }
        }
    }
}
```

`backend/src/main/kotlin/de/touchgrass/Application.kt` komplett ersetzen:
```kotlin
package de.touchgrass

import de.touchgrass.activities.ActivityRepository
import de.touchgrass.activities.ActivityService
import de.touchgrass.auth.JwtSecret
import de.touchgrass.auth.JwtService
import de.touchgrass.auth.configureSecurity
import de.touchgrass.config.AppConfig
import de.touchgrass.db.DatabaseFactory
import de.touchgrass.plugins.configureSerialization
import de.touchgrass.plugins.configureStatusPages
import de.touchgrass.routes.activityRoutes
import de.touchgrass.routes.authRoutes
import de.touchgrass.routes.healthRoutes
import de.touchgrass.routes.meRoutes
import de.touchgrass.routes.sportRoutes
import de.touchgrass.sports.SportRepository
import de.touchgrass.users.UserRepository
import de.touchgrass.users.UserService
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import java.nio.file.Path

fun main() {
    val config = AppConfig.fromEnvironment()
    val appDatabase = DatabaseFactory.start(config)
    Runtime.getRuntime().addShutdownHook(Thread { appDatabase.close() })
    val jwtService = JwtService(JwtSecret.load(config.jwtSecret, Path.of(config.dataDir)))
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(appDatabase.database, jwtService)
    }.start(wait = true)
}

fun Application.module(database: Database, jwtService: JwtService) {
    val userRepository = UserRepository(database)
    val sportRepository = SportRepository(database)
    val userService = UserService(userRepository, jwtService)
    val activityService = ActivityService(ActivityRepository(database), sportRepository, userRepository)

    configureSerialization()
    configureStatusPages()
    configureSecurity(jwtService)
    routing {
        route("/api") {
            healthRoutes(database)
            authRoutes(userService)
            meRoutes(userService)
            sportRoutes(sportRepository)
            activityRoutes(activityService)
        }
    }
}
```

`docs/api.md`, im Abschnitt `### POST /activities` direkt unter dem `201`-Beispiel (vor „Fehler: …“) einfügen:
```markdown
`newAchievements` kommt mit M4 dazu; bis dahin fehlt das Feld in der Antwort.
```

- [ ] **Step 5: Tests laufen lassen, sie müssen grün sein**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, `ActivityValidationTest` 5, `ActivityCreateTest` 6 – alle `PASSED`.

- [ ] **Step 6: Commit**

```powershell
git add backend docs/api.md
git commit -m "Aktivität anlegen mit kcal, Punkten und Wochenziel-Bonus" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Backend – Verlauf, Details, Löschen

**Files:**
- Modify: `backend/src/main/kotlin/de/touchgrass/activities/ActivityService.kt`, `.../routes/ActivityRoutes.kt`
- Test: `backend/src/test/kotlin/de/touchgrass/ActivityAccessTest.kt`

**Interfaces:**
- Consumes: alles aus Task 3
- Produces: `ActivityService.list(userId, sportCode: String?, from: String?, to: String?): List<ActivityDto>` (neueste zuerst, `from`/`to` einschließlich, Berliner Tage), `get(userId, id): ActivityDto` (fremd und privat → 404), `delete(userId, id)` (fremd → 403, fehlt → 404); Routen `GET /activities`, `GET /activities/{id}`, `DELETE /activities/{id}` (nicht-numerische ID → 404).

- [ ] **Step 1: Failing Test schreiben**

`backend/src/test/kotlin/de/touchgrass/ActivityAccessTest.kt`:
```kotlin
package de.touchgrass

import de.touchgrass.activities.ActivityDto
import de.touchgrass.activities.Visibility
import de.touchgrass.plugins.ErrorResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ActivityAccessTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `Verlauf zeigt nur eigene Aktivitäten, neueste zuerst`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa").token
        client.createActivity(max, activity(startedAt = "2026-10-05T09:00:00+02:00"))
        client.createActivity(max, activity(startedAt = "2026-10-07T09:00:00+02:00"))
        client.createActivity(lisa, activity(startedAt = "2026-10-06T09:00:00+02:00"))

        val list = client.get("/api/activities") { bearerAuth(max) }.body<List<ActivityDto>>()

        assertEquals(listOf("2026-10-07T09:00:00+02:00", "2026-10-05T09:00:00+02:00"), list.map { it.startedAt })
    }

    @Test
    fun `Verlauf filtert nach Sportart und Zeitraum`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        client.createActivity(token, activity(sportCode = "RUNNING", startedAt = "2026-09-30T23:30:00+02:00"))
        client.createActivity(token, activity(sportCode = "RUNNING", startedAt = "2026-10-01T00:15:00+02:00"))
        client.createActivity(token, activity(sportCode = "CYCLING", startedAt = "2026-10-03T10:00:00+02:00"))
        client.createActivity(token, activity(sportCode = "RUNNING", startedAt = "2026-10-04T23:59:00+02:00"))
        client.createActivity(token, activity(sportCode = "RUNNING", startedAt = "2026-10-05T00:00:00+02:00"))

        val list = client.get("/api/activities?sport=RUNNING&from=2026-10-01&to=2026-10-04") { bearerAuth(token) }
            .body<List<ActivityDto>>()

        assertEquals(listOf("2026-10-04T23:59:00+02:00", "2026-10-01T00:15:00+02:00"), list.map { it.startedAt })
    }

    @Test
    fun `falsches Datumsformat im Filter liefert 400`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val response = client.get("/api/activities?from=01.10.2026") { bearerAuth(token) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("from", response.body<ErrorResponse>().field)
    }

    @Test
    fun `fremde private Aktivität ist nicht sichtbar, öffentliche schon`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa").token
        val private = client.createActivity(lisa).activity.id
        val public = client.createActivity(lisa, activity(visibility = Visibility.PUBLIC)).activity.id

        val privateResponse = client.get("/api/activities/$private") { bearerAuth(max) }
        val publicResponse = client.get("/api/activities/$public") { bearerAuth(max) }

        assertEquals(HttpStatusCode.NotFound, privateResponse.status)
        assertEquals(HttpStatusCode.OK, publicResponse.status)
        val dto = publicResponse.body<ActivityDto>()
        assertFalse(dto.mine)
        assertEquals("lisa", dto.username)
    }

    @Test
    fun `eigene Aktivität löschen, fremde nicht`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa").token
        val own = client.createActivity(max).activity.id
        val foreign = client.createActivity(lisa, activity(visibility = Visibility.PUBLIC)).activity.id

        val deleteOwn = client.delete("/api/activities/$own") { bearerAuth(max) }
        val getOwn = client.get("/api/activities/$own") { bearerAuth(max) }
        val deleteForeign = client.delete("/api/activities/$foreign") { bearerAuth(max) }
        val deleteMissing = client.delete("/api/activities/999999") { bearerAuth(max) }
        val deleteGarbage = client.delete("/api/activities/abc") { bearerAuth(max) }

        assertEquals(HttpStatusCode.NoContent, deleteOwn.status)
        assertEquals(HttpStatusCode.NotFound, getOwn.status)
        assertEquals(HttpStatusCode.Forbidden, deleteForeign.status)
        assertEquals("FORBIDDEN", deleteForeign.body<ErrorResponse>().error)
        assertEquals(HttpStatusCode.NotFound, deleteMissing.status)
        assertEquals(HttpStatusCode.NotFound, deleteGarbage.status)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, sie müssen fehlschlagen**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: Die `ActivityAccessTest` scheitern, weil `GET` und `DELETE` noch nicht existieren, z. B. `expected: <204 No Content> but was: <404 Not Found>` bzw. `405 Method Not Allowed`.

- [ ] **Step 3: Implementierung schreiben**

`backend/src/main/kotlin/de/touchgrass/activities/ActivityService.kt` komplett ersetzen:
```kotlin
package de.touchgrass.activities

import de.touchgrass.logic.BerlinTime
import de.touchgrass.logic.Calories
import de.touchgrass.logic.Points
import de.touchgrass.logic.WeekTotals
import de.touchgrass.logic.WeeklyGoal
import de.touchgrass.plugins.ApiException
import de.touchgrass.plugins.validationError
import de.touchgrass.sports.SportRepository
import de.touchgrass.users.UserRepository
import io.ktor.http.HttpStatusCode
import java.time.Clock
import java.time.OffsetDateTime

class ActivityService(
    private val activities: ActivityRepository,
    private val sports: SportRepository,
    private val users: UserRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(userId: Long, request: CreateActivityRequest): CreateActivityResponse {
        val user = users.findById(userId) ?: throw ApiException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Bitte melde dich an.")
        val sport = sports.find(request.sportCode) ?: validationError("sportCode", "Unbekannte Sportart.")
        val startedAt = ActivityValidation.startedAt(request.startedAt, OffsetDateTime.now(clock))
        ActivityValidation.durationMin(request.durationMin)
        ActivityValidation.location(request.latitude, request.longitude)
        val description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        ActivityValidation.description(description)

        val kcal = Calories.estimate(sport.met, user.weightKg, request.durationMin)
        val week = activities.inRange(userId, BerlinTime.weekOf(startedAt))
        val before = WeekTotals(week.sumOf { it.durationMin }, week.size, week.sumOf { it.caloriesKcal })
        val goalBonus = WeeklyGoal.bonusEarned(
            user.goalType,
            user.goalTarget,
            before,
            before.plusActivity(request.durationMin, kcal),
            bonusAlreadyGiven = week.any { it.goalBonus },
        )
        val breakdown = Points.calculate(request.durationMin, !activities.hasSport(userId, sport.code), goalBonus)

        val id = activities.insert(
            NewActivity(
                userId = userId,
                sportCode = sport.code,
                startedAt = startedAt,
                durationMin = request.durationMin,
                caloriesKcal = kcal,
                points = breakdown.total,
                goalBonus = goalBonus,
                latitude = request.latitude,
                longitude = request.longitude,
                description = description,
                visibility = request.visibility,
            )
        )
        return CreateActivityResponse(activities.findById(id)!!.toDto(viewerId = userId), breakdown)
    }

    fun list(userId: Long, sportCode: String?, from: String?, to: String?): List<ActivityDto> {
        val fromDate = ActivityValidation.dateFilter(from, "from")
        val toDate = ActivityValidation.dateFilter(to, "to")
        return activities.list(
            userId,
            sportCode,
            fromDate?.let(BerlinTime::startOfDay),
            toDate?.let { BerlinTime.startOfDay(it.plusDays(1)) },
        ).map { it.toDto(viewerId = userId) }
    }

    fun get(userId: Long, id: Long): ActivityDto {
        val activity = activities.findById(id)
        if (activity == null || (activity.userId != userId && activity.visibility == Visibility.PRIVATE)) throw notFound()
        return activity.toDto(viewerId = userId)
    }

    fun delete(userId: Long, id: Long) {
        val activity = activities.findById(id) ?: throw notFound()
        if (activity.userId != userId) {
            throw ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", "Das ist nicht deine Aktivität.")
        }
        activities.delete(id)
    }

    private fun notFound() = ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Aktivität nicht gefunden.")

    private fun ActivityRecord.toDto(viewerId: Long) = ActivityDto(
        id = id,
        username = username,
        mine = userId == viewerId,
        sportCode = sportCode,
        startedAt = BerlinTime.format(startedAt),
        durationMin = durationMin,
        caloriesKcal = caloriesKcal,
        points = points,
        latitude = latitude,
        longitude = longitude,
        description = description,
        visibility = visibility,
    )
}
```

`backend/src/main/kotlin/de/touchgrass/routes/ActivityRoutes.kt` komplett ersetzen:
```kotlin
package de.touchgrass.routes

import de.touchgrass.activities.ActivityService
import de.touchgrass.activities.CreateActivityRequest
import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import de.touchgrass.plugins.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.activityRoutes(service: ActivityService) {
    authenticate(AUTH_JWT) {
        route("/activities") {
            post {
                call.respond(HttpStatusCode.Created, service.create(call.userId(), call.receive<CreateActivityRequest>()))
            }
            get {
                val query = call.request.queryParameters
                call.respond(service.list(call.userId(), query["sport"], query["from"], query["to"]))
            }
            get("/{id}") {
                call.respond(service.get(call.userId(), call.activityId()))
            }
            delete("/{id}") {
                service.delete(call.userId(), call.activityId())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ApplicationCall.activityId(): Long =
    parameters["id"]?.toLongOrNull() ?: throw ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "Aktivität nicht gefunden.")
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`. Insgesamt 56 Backend-Tests grün, davon neu: `LogicTest`-Klassen 11, `SportRoutesTest` 2, `ActivityValidationTest` 5, `ActivityCreateTest` 6, `ActivityAccessTest` 5.

- [ ] **Step 5: Commit**

```powershell
git add backend/src
git commit -m "Verlauf mit Filtern, Details und Löschen von Aktivitäten" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: App – API für Sportarten und Aktivitäten

**Files:**
- Modify: `app/build.gradle.kts:24` (`minSdk`)
- Create: `app/src/main/java/de/touchgrass/data/dto/ActivityDtos.kt`
- Modify: `app/src/main/java/de/touchgrass/data/SportApi.kt`
- Modify: `app/src/test/java/de/touchgrass/testing/Fakes.kt`, `app/src/test/java/de/touchgrass/data/SportApiTest.kt`

**Interfaces:**
- Produces: DTOs `SportDto`, `Visibility`, `CreateActivityRequest`, `ActivityDto`, `PointsBreakdownDto`, `CreateActivityResponse` (wie im Backend); `SportApi.sports()`, `createActivity(body)`, `activities(sport: String? = null, from: String? = null, to: String? = null)`, `activity(id: Long)`, `deleteActivity(id: Long)`; Test-Hilfen `val testSports: List<SportDto>` (Radfahren, Joggen), `fun testActivity(id = 42, sportCode = "RUNNING", startedAt = "2026-10-05T09:30:00+02:00"): ActivityDto` (45 min, 480 kcal, 75 Punkte, privat).

- [ ] **Step 1: `minSdk` anheben**

`app/build.gradle.kts`: `minSdk = 24` → `minSdk = 26`.

- [ ] **Step 2: Failing Tests schreiben**

`app/src/test/java/de/touchgrass/testing/Fakes.kt` komplett ersetzen:
```kotlin
package de.touchgrass.testing

import de.touchgrass.data.SportApi
import de.touchgrass.data.TokenStore
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.AuthResponse
import de.touchgrass.data.dto.ChangePasswordRequest
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.CreateActivityResponse
import de.touchgrass.data.dto.GoalType
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.data.dto.LoginRequest
import de.touchgrass.data.dto.RegisterRequest
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.UpdateMeRequest
import de.touchgrass.data.dto.UserDto
import de.touchgrass.data.dto.Visibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import retrofit2.HttpException
import retrofit2.Response

open class FakeSportApi : SportApi {
    override suspend fun health(): HealthDto = notExpected("health")
    override suspend fun register(body: RegisterRequest): AuthResponse = notExpected("register")
    override suspend fun login(body: LoginRequest): AuthResponse = notExpected("login")
    override suspend fun me(): UserDto = notExpected("me")
    override suspend fun updateMe(body: UpdateMeRequest): UserDto = notExpected("updateMe")
    override suspend fun changePassword(body: ChangePasswordRequest): Unit = notExpected("changePassword")
    override suspend fun deleteMe(): Unit = notExpected("deleteMe")
    override suspend fun sports(): List<SportDto> = notExpected("sports")
    override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse = notExpected("createActivity")
    override suspend fun activities(sport: String?, from: String?, to: String?): List<ActivityDto> = notExpected("activities")
    override suspend fun activity(id: Long): ActivityDto = notExpected("activity")
    override suspend fun deleteActivity(id: Long): Unit = notExpected("deleteActivity")

    private fun notExpected(name: String): Nothing = error("$name() wird in diesem Test nicht erwartet")
}

class FakeTokenStore(initial: String? = null) : TokenStore {
    private val current = MutableStateFlow(initial)
    override val token: Flow<String?> = current
    override suspend fun save(token: String) { current.value = token }
    override suspend fun clear() { current.value = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun httpError(code: Int, json: String): HttpException =
    HttpException(Response.error<Any>(code, json.toResponseBody("application/json".toMediaType())))

val testUser = UserDto(
    id = 1,
    username = "max_m",
    email = "max@example.com",
    weightKg = null,
    goalType = GoalType.MINUTES,
    goalTarget = 150,
)

val testSports = listOf(
    SportDto("CYCLING", "Radfahren", "🚴", 7.0),
    SportDto("RUNNING", "Joggen", "🏃", 8.0),
)

fun testActivity(id: Long = 42, sportCode: String = "RUNNING", startedAt: String = "2026-10-05T09:30:00+02:00") = ActivityDto(
    id = id,
    username = "max_m",
    mine = true,
    sportCode = sportCode,
    startedAt = startedAt,
    durationMin = 45,
    caloriesKcal = 480,
    points = 75,
    visibility = Visibility.PRIVATE,
)
```

`app/src/test/java/de/touchgrass/data/SportApiTest.kt`: Import `import de.touchgrass.data.dto.CreateActivityRequest` ergänzen und vor der letzten schließenden `}` der Klasse diese drei Tests einfügen:
```kotlin
    @Test
    fun `createActivity schickt Pflichtfelder und lässt Standardwerte weg`() = runBlocking {
        enqueueJson(201, """{"activity":{"id":1,"username":"max_m","mine":true,"sportCode":"RUNNING","startedAt":"2026-10-05T09:30:00+02:00","durationMin":45,"caloriesKcal":480,"points":75,"visibility":"PRIVATE"},"pointsBreakdown":{"minutes":45,"completed":10,"newSport":20,"weeklyGoal":0,"total":75}}""")

        val response = api.createActivity(CreateActivityRequest("RUNNING", "2026-10-05T09:30:00+02:00", 45))

        val request = server.takeRequest()
        assertEquals("/api/activities", request.path)
        assertEquals("""{"sportCode":"RUNNING","startedAt":"2026-10-05T09:30:00+02:00","durationMin":45}""", request.body.readUtf8())
        assertEquals(75, response.pointsBreakdown.total)
    }

    @Test
    fun `activities lässt leere Filter weg`() = runBlocking {
        enqueueJson(200, "[]")
        enqueueJson(200, "[]")

        api.activities()
        api.activities(sport = "RUNNING", from = "2026-10-01")

        assertEquals("/api/activities", server.takeRequest().path)
        assertEquals("/api/activities?sport=RUNNING&from=2026-10-01", server.takeRequest().path)
    }

    @Test
    fun `deleteActivity setzt die ID in den Pfad`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        api.deleteActivity(42)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/activities/42", request.path)
    }
```

- [ ] **Step 3: Tests laufen lassen, sie müssen fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren, u. a. `Unresolved reference 'ActivityDto'`, `'CreateActivityRequest'`, `'sports' overrides nothing`.

- [ ] **Step 4: Implementierung schreiben**

`app/src/main/java/de/touchgrass/data/dto/ActivityDtos.kt`:
```kotlin
package de.touchgrass.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SportDto(val code: String, val name: String, val emoji: String, val met: Double)

@Serializable
enum class Visibility { PRIVATE, PUBLIC }

@Serializable
data class CreateActivityRequest(
    val sportCode: String,
    val startedAt: String,
    val durationMin: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val visibility: Visibility = Visibility.PRIVATE,
)

@Serializable
data class ActivityDto(
    val id: Long,
    val username: String,
    val mine: Boolean,
    val sportCode: String,
    val startedAt: String,
    val durationMin: Int,
    val caloriesKcal: Int,
    val points: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val visibility: Visibility,
)

@Serializable
data class PointsBreakdownDto(val minutes: Int, val completed: Int, val newSport: Int, val weeklyGoal: Int, val total: Int)

@Serializable
data class CreateActivityResponse(val activity: ActivityDto, val pointsBreakdown: PointsBreakdownDto)
```

`app/src/main/java/de/touchgrass/data/SportApi.kt` komplett ersetzen:
```kotlin
package de.touchgrass.data

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.AuthResponse
import de.touchgrass.data.dto.ChangePasswordRequest
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.CreateActivityResponse
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.data.dto.LoginRequest
import de.touchgrass.data.dto.RegisterRequest
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.UpdateMeRequest
import de.touchgrass.data.dto.UserDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface SportApi {
    @GET("health")
    suspend fun health(): HealthDto

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("me")
    suspend fun me(): UserDto

    @PATCH("me")
    suspend fun updateMe(@Body body: UpdateMeRequest): UserDto

    @PUT("me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest)

    @DELETE("me")
    suspend fun deleteMe()

    @GET("sports")
    suspend fun sports(): List<SportDto>

    @POST("activities")
    suspend fun createActivity(@Body body: CreateActivityRequest): CreateActivityResponse

    @GET("activities")
    suspend fun activities(
        @Query("sport") sport: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<ActivityDto>

    @GET("activities/{id}")
    suspend fun activity(@Path("id") id: Long): ActivityDto

    @DELETE("activities/{id}")
    suspend fun deleteActivity(@Path("id") id: Long)
}

val apiJson = Json { ignoreUnknownKeys = true }

fun createSportApi(baseUrl: String, client: OkHttpClient = OkHttpClient()): SportApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(apiJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(SportApi::class.java)
```

- [ ] **Step 5: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, `SportApiTest` `tests="9"`, alle App-Tests ohne Fehler.

- [ ] **Step 6: Commit**

```powershell
git add app
git commit -m "App-API für Sportarten und Aktivitäten, minSdk 26" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: App – Aktivität eintragen (➕-Tab)

**Files:**
- Create: `app/src/main/java/de/touchgrass/ui/activities/ActivityFormat.kt`
- Create: `app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt`, `.../ui/record/RecordScreen.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/Navigation.kt` (Record-Tab)
- Test: `app/src/test/java/de/touchgrass/ui/activities/ActivityFormatTest.kt`, `app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt`

**Interfaces:**
- Consumes: `SportApi`, DTOs (Task 5), `apiCall`, `ApiException`, `FormField` (M1)
- Produces: `formatDate(LocalDate)` („05.10.2026“), `formatTime(LocalTime)` („09:30“), `ActivityDto.startedAtLabel()` („05.10.2026, 09:30 Uhr“), `ActivityDto.summary()` („45 min · ~480 kcal · 75 Punkte“), `PointsBreakdownDto.describe()` („45 min · +10 · +20 neue Sportart“), `List<SportDto>.labelFor(code)` („🏃 Joggen“); `RecordViewModel(api, clock = Clock.systemDefaultZone())` mit `state: StateFlow<RecordUiState>`, `loadSports`, `onSportChange`, `onDateChange`, `onTimeChange`, `onDurationChange`, `onDescriptionChange`, `onVisibilityChange`, `save`; `@Composable fun RecordScreen(viewModel)`.

- [ ] **Step 1: Failing Tests schreiben**

`app/src/test/java/de/touchgrass/ui/activities/ActivityFormatTest.kt`:
```kotlin
package de.touchgrass.ui.activities

import de.touchgrass.data.dto.PointsBreakdownDto
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityFormatTest {
    @Test
    fun `Punkte-Aufschlüsselung nennt nur vergebene Boni`() {
        assertEquals("45 min · +10 · +20 neue Sportart", PointsBreakdownDto(45, 10, 20, 0, 75).describe())
        assertEquals("30 min · +10 · +50 Wochenziel", PointsBreakdownDto(30, 10, 0, 50, 90).describe())
    }

    @Test
    fun `Aktivität wird mit Datum, Uhrzeit und Zusammenfassung angezeigt`() {
        val activity = testActivity(startedAt = "2026-10-05T09:30:00+02:00")

        assertEquals("05.10.2026, 09:30 Uhr", activity.startedAtLabel())
        assertEquals("45 min · ~480 kcal · 75 Punkte", activity.summary())
    }

    @Test
    fun `Sportart wird mit Emoji angezeigt, unbekannte mit Code`() {
        assertEquals("🏃 Joggen", testSports.labelFor("RUNNING"))
        assertEquals("CURLING", testSports.labelFor("CURLING"))
    }
}
```

`app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt`:
```kotlin
package de.touchgrass.ui.record

import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.CreateActivityResponse
import de.touchgrass.data.dto.PointsBreakdownDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.Visibility
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecordViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T08:15:30Z"), ZoneId.of("Europe/Berlin"))

    private open inner class Api : FakeSportApi() {
        var sent: CreateActivityRequest? = null
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse {
            sent = body
            return CreateActivityResponse(testActivity(), PointsBreakdownDto(45, 10, 20, 0, 75))
        }
    }

    @Test
    fun `Formular startet mit Sportarten und der aktuellen Zeit auf die Minute`() {
        val viewModel = RecordViewModel(Api(), clock)

        val state = viewModel.state.value
        assertEquals(testSports, state.sports)
        assertEquals(LocalDate.parse("2026-10-05"), state.date)
        assertEquals(LocalTime.parse("10:15"), state.time)
    }

    @Test
    fun `ohne Sportart oder mit ungültiger Dauer wird nichts gesendet`() {
        val api = Api()
        val viewModel = RecordViewModel(api, clock)

        viewModel.onDurationChange("45")
        viewModel.save()
        assertEquals("Bitte eine Sportart wählen.", viewModel.state.value.fieldErrors["sportCode"])

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("dreiviertel Stunde")
        viewModel.save()
        assertEquals("Bitte die Dauer in ganzen Minuten angeben.", viewModel.state.value.fieldErrors["durationMin"])
        assertNull(api.sent)
    }

    @Test
    fun `Speichern schickt die Aktivität mit Berliner Zeit und leert das Formular`() {
        val api = Api()
        val viewModel = RecordViewModel(api, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDateChange(LocalDate.parse("2026-10-04"))
        viewModel.onTimeChange(LocalTime.parse("09:30"))
        viewModel.onDurationChange(" 45 ")
        viewModel.onDescriptionChange("  ")
        viewModel.onVisibilityChange(Visibility.PUBLIC)
        viewModel.save()

        assertEquals(
            CreateActivityRequest("RUNNING", "2026-10-04T09:30:00+02:00", 45, visibility = Visibility.PUBLIC),
            api.sent,
        )
        val state = viewModel.state.value
        assertEquals("Gespeichert! ~480 kcal · +75 Punkte (45 min · +10 · +20 neue Sportart)", state.savedMessage)
        assertNull(state.sportCode)
        assertEquals("", state.duration)
        assertEquals(testSports, state.sports)
    }

    @Test
    fun `Feldfehler vom Server erscheint am Feld`() {
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse = throw httpError(
                400,
                """{"error":"VALIDATION_ERROR","message":"Die Aktivität darf nicht in der Zukunft liegen.","field":"startedAt"}""",
            )
        }
        val viewModel = RecordViewModel(api, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        assertEquals("Die Aktivität darf nicht in der Zukunft liegen.", viewModel.state.value.fieldErrors["startedAt"])
        assertEquals(false, viewModel.state.value.saving)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, sie müssen fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'describe'`, `'startedAtLabel'`, `'RecordViewModel'`.

- [ ] **Step 3: Format-Helfer und ViewModel schreiben**

`app/src/main/java/de/touchgrass/ui/activities/ActivityFormat.kt`:
```kotlin
package de.touchgrass.ui.activities

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.PointsBreakdownDto
import de.touchgrass.data.dto.SportDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

fun formatDate(date: LocalDate): String = date.format(dateFormat)

fun formatTime(time: LocalTime): String = time.format(timeFormat)

fun ActivityDto.startedAtLabel(): String {
    val startedAt = OffsetDateTime.parse(startedAt)
    return "${formatDate(startedAt.toLocalDate())}, ${formatTime(startedAt.toLocalTime())} Uhr"
}

fun ActivityDto.summary(): String = "$durationMin min · ~$caloriesKcal kcal · $points Punkte"

fun PointsBreakdownDto.describe(): String = buildList {
    add("$minutes min")
    add("+$completed")
    if (newSport > 0) add("+$newSport neue Sportart")
    if (weeklyGoal > 0) add("+$weeklyGoal Wochenziel")
}.joinToString(" · ")

fun List<SportDto>.labelFor(code: String): String =
    firstOrNull { it.code == code }?.let { "${it.emoji} ${it.name}" } ?: code
```

`app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt`:
```kotlin
package de.touchgrass.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.Visibility
import de.touchgrass.ui.activities.describe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class RecordUiState(
    val sports: List<SportDto> = emptyList(),
    val sportCode: String? = null,
    val date: LocalDate,
    val time: LocalTime,
    val duration: String = "",
    val description: String = "",
    val visibility: Visibility = Visibility.PRIVATE,
    val saving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val savedMessage: String? = null,
)

class RecordViewModel(private val api: SportApi, private val clock: Clock = Clock.systemDefaultZone()) : ViewModel() {
    private val _state = MutableStateFlow(emptyForm())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    init {
        loadSports()
    }

    fun loadSports() {
        viewModelScope.launch {
            try {
                val sports = apiCall { api.sports() }
                _state.update { it.copy(sports = sports, error = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun onSportChange(code: String) = edit("sportCode") { copy(sportCode = code) }

    fun onDateChange(date: LocalDate) = edit("startedAt") { copy(date = date) }

    fun onTimeChange(time: LocalTime) = edit("startedAt") { copy(time = time) }

    fun onDurationChange(value: String) = edit("durationMin") { copy(duration = value) }

    fun onDescriptionChange(value: String) = edit("description") { copy(description = value) }

    fun onVisibilityChange(value: Visibility) = edit("visibility") { copy(visibility = value) }

    fun save() {
        val current = _state.value
        if (current.saving) return
        val sportCode = current.sportCode ?: return showFieldError("sportCode", "Bitte eine Sportart wählen.")
        val duration = current.duration.trim().toIntOrNull()
            ?: return showFieldError("durationMin", "Bitte die Dauer in ganzen Minuten angeben.")
        val request = CreateActivityRequest(
            sportCode = sportCode,
            startedAt = ZonedDateTime.of(current.date, current.time, clock.zone)
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            durationMin = duration,
            description = current.description.trim().ifEmpty { null },
            visibility = current.visibility,
        )
        _state.update { it.copy(saving = true, fieldErrors = emptyMap(), error = null, savedMessage = null) }
        viewModelScope.launch {
            try {
                val response = apiCall { api.createActivity(request) }
                val points = response.pointsBreakdown
                _state.value = emptyForm().copy(
                    sports = current.sports,
                    savedMessage = "Gespeichert! ~${response.activity.caloriesKcal} kcal · +${points.total} Punkte (${points.describe()})",
                )
            } catch (e: ApiException) {
                _state.update { it.copy(saving = false) }
                if (e.field != null) showFieldError(e.field, e.message) else _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun emptyForm(): RecordUiState {
        val now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        return RecordUiState(date = now.toLocalDate(), time = now.toLocalTime())
    }

    private fun edit(field: String, change: RecordUiState.() -> RecordUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - field, error = null, savedMessage = null) }

    private fun showFieldError(field: String, message: String) =
        _state.update { it.copy(fieldErrors = it.fieldErrors + (field to message)) }
}
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, `ActivityFormatTest` `tests="3"`, `RecordViewModelTest` `tests="4"`, ohne Fehler.

- [ ] **Step 5: Screen schreiben und im ➕-Tab einhängen**

`app/src/main/java/de/touchgrass/ui/record/RecordScreen.kt`:
```kotlin
package de.touchgrass.ui.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.data.dto.Visibility
import de.touchgrass.ui.activities.formatDate
import de.touchgrass.ui.activities.formatTime
import de.touchgrass.ui.components.FormField
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun RecordScreen(viewModel: RecordViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Aktivität eintragen", style = MaterialTheme.typography.headlineMedium)
        state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text("Sportart")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.sports.forEach { sport ->
                FilterChip(
                    selected = state.sportCode == sport.code,
                    onClick = { viewModel.onSportChange(sport.code) },
                    label = { Text("${sport.emoji} ${sport.name}") },
                )
            }
        }
        state.fieldErrors["sportCode"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate = true }) { Text("📅 ${formatDate(state.date)}") }
            OutlinedButton(onClick = { pickTime = true }) { Text("🕘 ${formatTime(state.time)} Uhr") }
        }
        state.fieldErrors["startedAt"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        FormField(state.duration, viewModel::onDurationChange, "Dauer in Minuten", state.fieldErrors["durationMin"], KeyboardType.Number)
        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text("Beschreibung (freiwillig)") },
            isError = state.fieldErrors["description"] != null,
            supportingText = state.fieldErrors["description"]?.let { { Text(it) } },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.visibility == Visibility.PRIVATE,
                onClick = { viewModel.onVisibilityChange(Visibility.PRIVATE) },
                label = { Text("🔒 Privat") },
            )
            FilterChip(
                selected = state.visibility == Visibility.PUBLIC,
                onClick = { viewModel.onVisibilityChange(Visibility.PUBLIC) },
                label = { Text("🌎 Öffentlich") },
            )
        }

        Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.saving) "Speichern …" else "Speichern")
        }
    }

    if (pickDate) {
        DateDialog(state.date, onDismiss = { pickDate = false }, onPicked = { pickDate = false; viewModel.onDateChange(it) })
    }
    if (pickTime) {
        TimeDialog(state.time, onDismiss = { pickTime = false }, onPicked = { pickTime = false; viewModel.onTimeChange(it) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPicked(LocalTime.of(pickerState.hour, pickerState.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        text = { TimePicker(state = pickerState) },
    )
}
```

`app/src/main/java/de/touchgrass/ui/Navigation.kt`:
1. Imports ergänzen:
```kotlin
import de.touchgrass.ui.record.RecordScreen
import de.touchgrass.ui.record.RecordViewModel
```
2. Die Zeile
```kotlin
            composable<RecordRoute> { PlaceholderScreen("Aktivität", "Formular kommt in M2, Timer in M3.") }
```
ersetzen durch:
```kotlin
            composable<RecordRoute> {
                RecordScreen(viewModel = viewModel { RecordViewModel(container.api) })
            }
```

- [ ] **Step 6: Bauen und alle Tests laufen lassen**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, alle App-Tests ohne Fehler.

- [ ] **Step 7: Commit**

```powershell
git add app/src
git commit -m "Aktivität eintragen im Plus-Tab" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: App – Verlauf und Detailansicht

**Files:**
- Create: `app/src/main/java/de/touchgrass/ui/activities/HistoryViewModel.kt`, `.../HistoryScreen.kt`, `.../ActivityDetailViewModel.kt`, `.../ActivityDetailScreen.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/profile/ProfileScreen.kt`, `app/src/main/java/de/touchgrass/ui/Navigation.kt`
- Test: `app/src/test/java/de/touchgrass/ui/activities/HistoryViewModelTest.kt`, `.../ActivityDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `SportApi`, `UiState`, Format-Helfer (Task 6)
- Produces: `enum class Period(label, days: Long?) { ALL, WEEK(7), MONTH(30) }`; `HistoryViewModel(api, clock = Clock.systemDefaultZone())` mit `state: StateFlow<HistoryUiState>` (`sports`, `sportFilter`, `period`, `activities: UiState<List<ActivityDto>>`), `refresh`, `onSportFilterChange(String?)`, `onPeriodChange(Period)` (Zeitraum wird als `from = heute − (Tage − 1)` geschickt); `ActivityDetailViewModel(api, id)` mit `state` (`sports`, `activity: UiState<ActivityDto>`, `deleted`, `error`), `delete`; Routen `HistoryRoute`, `@Serializable data class ActivityDetailRoute(val id: Long)`; `ProfileScreen(viewModel, onOpenHistory, onOpenSettings)`.

- [ ] **Step 1: Failing Tests schreiben**

`app/src/test/java/de/touchgrass/ui/activities/HistoryViewModelTest.kt`:
```kotlin
package de.touchgrass.ui.activities

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class HistoryViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-21T10:00:00Z"), ZoneId.of("Europe/Berlin"))

    private inner class Api : FakeSportApi() {
        val queries = mutableListOf<Pair<String?, String?>>()
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun activities(sport: String?, from: String?, to: String?): List<ActivityDto> {
            queries += sport to from
            return listOf(testActivity())
        }
    }

    @Test
    fun `refresh lädt Sportarten und Aktivitäten ohne Filter`() {
        val api = Api()
        val viewModel = HistoryViewModel(api, clock)

        viewModel.refresh()

        assertEquals(testSports, viewModel.state.value.sports)
        assertEquals(UiState.Content(listOf(testActivity())), viewModel.state.value.activities)
        assertEquals(listOf<Pair<String?, String?>>(null to null), api.queries)
    }

    @Test
    fun `Filter werden als Sportart und Startdatum geschickt`() {
        val api = Api()
        val viewModel = HistoryViewModel(api, clock)

        viewModel.onSportFilterChange("RUNNING")
        viewModel.onPeriodChange(Period.WEEK)

        assertEquals(listOf<Pair<String?, String?>>("RUNNING" to null, "RUNNING" to "2026-10-15"), api.queries)
    }

    @Test
    fun `ohne Server erscheint eine Fehlermeldung`() {
        val viewModel = HistoryViewModel(object : FakeSportApi() {
            override suspend fun sports(): List<SportDto> = throw IOException("offline")
        }, clock)

        viewModel.refresh()

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value.activities)
    }
}
```

`app/src/test/java/de/touchgrass/ui/activities/ActivityDetailViewModelTest.kt`:
```kotlin
package de.touchgrass.ui.activities

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActivityDetailViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private open inner class Api : FakeSportApi() {
        var deletedId: Long? = null
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun activity(id: Long): ActivityDto = testActivity(id = id)
        override suspend fun deleteActivity(id: Long) {
            deletedId = id
        }
    }

    @Test
    fun `lädt die Aktivität mit der übergebenen ID`() {
        val viewModel = ActivityDetailViewModel(Api(), id = 7)

        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
    }

    @Test
    fun `Löschen ruft die API auf und meldet deleted`() {
        val api = Api()
        val viewModel = ActivityDetailViewModel(api, id = 7)

        viewModel.delete()

        assertEquals(7L, api.deletedId)
        assertTrue(viewModel.state.value.deleted)
    }

    @Test
    fun `Fehler beim Löschen wird angezeigt`() {
        val api = object : Api() {
            override suspend fun deleteActivity(id: Long): Unit =
                throw httpError(403, """{"error":"FORBIDDEN","message":"Das ist nicht deine Aktivität."}""")
        }
        val viewModel = ActivityDetailViewModel(api, id = 7)

        viewModel.delete()

        assertFalse(viewModel.state.value.deleted)
        assertEquals("Das ist nicht deine Aktivität.", viewModel.state.value.error)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, sie müssen fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'HistoryViewModel'`, `'Period'`, `'ActivityDetailViewModel'`.

- [ ] **Step 3: ViewModels schreiben**

`app/src/main/java/de/touchgrass/ui/activities/HistoryViewModel.kt`:
```kotlin
package de.touchgrass.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class Period(val label: String, val days: Long?) {
    ALL("Alle", null),
    WEEK("7 Tage", 7),
    MONTH("30 Tage", 30),
}

data class HistoryUiState(
    val sports: List<SportDto> = emptyList(),
    val sportFilter: String? = null,
    val period: Period = Period.ALL,
    val activities: UiState<List<ActivityDto>> = UiState.Loading,
)

class HistoryViewModel(private val api: SportApi, private val clock: Clock = Clock.systemDefaultZone()) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun onSportFilterChange(code: String?) {
        _state.update { it.copy(sportFilter = code) }
        refresh()
    }

    fun onPeriodChange(period: Period) {
        _state.update { it.copy(period = period) }
        refresh()
    }

    fun refresh() {
        val current = _state.value
        val from = current.period.days?.let { LocalDate.now(clock).minusDays(it - 1).toString() }
        viewModelScope.launch {
            try {
                val sports = current.sports.ifEmpty { apiCall { api.sports() } }
                val activities = apiCall { api.activities(sport = current.sportFilter, from = from) }
                _state.update { it.copy(sports = sports, activities = UiState.Content(activities)) }
            } catch (e: ApiException) {
                _state.update { it.copy(activities = UiState.Error(e.message)) }
            }
        }
    }
}
```

`app/src/main/java/de/touchgrass/ui/activities/ActivityDetailViewModel.kt`:
```kotlin
package de.touchgrass.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityDetailUiState(
    val sports: List<SportDto> = emptyList(),
    val activity: UiState<ActivityDto> = UiState.Loading,
    val deleted: Boolean = false,
    val error: String? = null,
)

class ActivityDetailViewModel(private val api: SportApi, private val id: Long) : ViewModel() {
    private val _state = MutableStateFlow(ActivityDetailUiState())
    val state: StateFlow<ActivityDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val sports = apiCall { api.sports() }
                val activity = apiCall { api.activity(id) }
                _state.update { it.copy(sports = sports, activity = UiState.Content(activity)) }
            } catch (e: ApiException) {
                _state.update { it.copy(activity = UiState.Error(e.message)) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                apiCall { api.deleteActivity(id) }
                _state.update { it.copy(deleted = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, `HistoryViewModelTest` `tests="3"`, `ActivityDetailViewModelTest` `tests="3"`.

- [ ] **Step 5: Screens, Profil-Eintrag und Navigation**

`app/src/main/java/de/touchgrass/ui/activities/HistoryScreen.kt`:
```kotlin
package de.touchgrass.ui.activities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.ui.UiState

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        Text("Verlauf", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.sportFilter == null, onClick = { viewModel.onSportFilterChange(null) }, label = { Text("Alle Sportarten") })
            state.sports.forEach { sport ->
                FilterChip(
                    selected = state.sportFilter == sport.code,
                    onClick = { viewModel.onSportFilterChange(sport.code) },
                    label = { Text("${sport.emoji} ${sport.name}") },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { period ->
                FilterChip(selected = state.period == period, onClick = { viewModel.onPeriodChange(period) }, label = { Text(period.label) })
            }
        }
        HorizontalDivider()
        when (val activities = state.activities) {
            UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> {
                Text(activities.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::refresh) { Text("Erneut versuchen") }
            }
            is UiState.Content -> if (activities.data.isEmpty()) {
                Text("Noch keine Aktivitäten in diesem Zeitraum.")
            } else {
                LazyColumn {
                    items(activities.data, key = { it.id }) { activity ->
                        ListItem(
                            overlineContent = { Text(activity.startedAtLabel()) },
                            headlineContent = { Text(state.sports.labelFor(activity.sportCode)) },
                            supportingContent = { Text(activity.summary()) },
                            modifier = Modifier.clickable { onOpen(activity.id) },
                        )
                    }
                }
            }
        }
    }
}
```

`app/src/main/java/de/touchgrass/ui/activities/ActivityDetailScreen.kt`:
```kotlin
package de.touchgrass.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.data.dto.Visibility
import de.touchgrass.ui.UiState

@Composable
fun ActivityDetailScreen(viewModel: ActivityDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        when (val s = state.activity) {
            UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is UiState.Content -> {
                val activity = s.data
                Text(state.sports.labelFor(activity.sportCode), style = MaterialTheme.typography.headlineMedium)
                Text(activity.startedAtLabel())
                Text("${activity.durationMin} Minuten")
                Text("~${activity.caloriesKcal} kcal (geschätzt)")
                Text("${activity.points} Punkte")
                Text(if (activity.visibility == Visibility.PUBLIC) "🌎 Öffentlich" else "🔒 Privat")
                activity.description?.let { Text("„$it“") }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (activity.mine) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Aktivität löschen") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Aktivität löschen?") },
            text = { Text("Die Punkte dieser Aktivität verschwinden ebenfalls.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}
```

`app/src/main/java/de/touchgrass/ui/profile/ProfileScreen.kt`:
1. Signatur ändern:
```kotlin
fun ProfileScreen(viewModel: ProfileViewModel, onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
```
2. Direkt nach `HorizontalDivider()` (vor dem Eintrag „⚙️ Einstellungen“) einfügen:
```kotlin
        ListItem(
            headlineContent = { Text("📜 Verlauf") },
            modifier = Modifier.clickable(onClick = onOpenHistory),
        )
```
3. Hinweis bei fehlendem Gewicht (Spec, Abschnitt 4): die Zeile
```kotlin
                Text("Gewicht: " + (user.weightKg?.let { "${formatWeight(it)} kg" } ?: "nicht angegeben"))
```
ersetzen durch:
```kotlin
                Text("Gewicht: " + (user.weightKg?.let { "${formatWeight(it)} kg" } ?: "nicht angegeben – in den Einstellungen eintragen, dann wird die kcal-Schätzung genauer"))
```

`app/src/main/java/de/touchgrass/ui/Navigation.kt` komplett ersetzen:
```kotlin
package de.touchgrass.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.touchgrass.AppContainer
import de.touchgrass.ui.activities.ActivityDetailScreen
import de.touchgrass.ui.activities.ActivityDetailViewModel
import de.touchgrass.ui.activities.HistoryScreen
import de.touchgrass.ui.activities.HistoryViewModel
import de.touchgrass.ui.profile.ProfileScreen
import de.touchgrass.ui.profile.ProfileViewModel
import de.touchgrass.ui.profile.SettingsScreen
import de.touchgrass.ui.profile.SettingsViewModel
import de.touchgrass.ui.record.RecordScreen
import de.touchgrass.ui.record.RecordViewModel
import de.touchgrass.ui.status.ServerStatusCard
import kotlinx.serialization.Serializable

@Serializable object MapRoute
@Serializable object RecordRoute
@Serializable object AchievementsRoute
@Serializable object ProfileGraph
@Serializable object ProfileRoute
@Serializable object SettingsRoute
@Serializable object HistoryRoute
@Serializable data class ActivityDetailRoute(val id: Long)

private data class Tab<T : Any>(val route: T, val emoji: String, val label: String)

private val tabs = listOf(
    Tab(MapRoute, "🗺️", "Karte"),
    Tab(RecordRoute, "➕", "Aktivität"),
    Tab(AchievementsRoute, "🏆", "Erfolge"),
    Tab(ProfileGraph, "👤", "Profil"),
)

@Composable
fun TouchGrassNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(tab.emoji) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MapRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<MapRoute> {
                PlaceholderScreen("Karte", "Die Karte kommt in M3.") {
                    ServerStatusCard(container.api)
                }
            }
            composable<RecordRoute> {
                RecordScreen(viewModel = viewModel { RecordViewModel(container.api) })
            }
            composable<AchievementsRoute> { PlaceholderScreen("Erfolge", "Achievements, Wochenziel und Leaderboard kommen in M4.") }
            navigation<ProfileGraph>(startDestination = ProfileRoute) {
                composable<ProfileRoute> {
                    ProfileScreen(
                        viewModel = viewModel { ProfileViewModel(container.api) },
                        onOpenHistory = { navController.navigate(HistoryRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        viewModel = viewModel { SettingsViewModel(container.api, container.session) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<HistoryRoute> {
                    HistoryScreen(
                        viewModel = viewModel { HistoryViewModel(container.api) },
                        onOpen = { id -> navController.navigate(ActivityDetailRoute(id)) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<ActivityDetailRoute> { entry ->
                    val id = entry.toRoute<ActivityDetailRoute>().id
                    ActivityDetailScreen(
                        viewModel = viewModel { ActivityDetailViewModel(container.api, id) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 6: Bauen und alle Tests laufen lassen**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. 44 App-Tests grün, davon neu: `SportApiTest` +3, `ActivityFormatTest` 3, `RecordViewModelTest` 4, `HistoryViewModelTest` 3, `ActivityDetailViewModelTest` 3.

- [ ] **Step 7: Commit**

```powershell
git add app/src
git commit -m "Verlauf mit Filtern und Detailansicht mit Löschen" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Abschluss von M2

- [ ] Backend: `.\gradlew.bat test --rerun` im Ordner `backend` → 56 Tests grün.
- [ ] App: `.\gradlew.bat assembleDebug :app:testDebugUnitTest --rerun` im Wurzelordner → 44 Tests grün.
- [ ] Android Studio: **Sync Project with Gradle Files**.
- [ ] Von Hand auf Gerät oder Emulator (Backend läuft, angemeldet):
  1. ➕-Tab: Sportarten erscheinen als Chips. Ohne Auswahl „Speichern“ → „Bitte eine Sportart wählen.“
  2. Joggen, Datum/Uhrzeit über die Dialoge ändern, 45 Minuten, „Speichern“ → „Gespeichert! ~… kcal · +75 Punkte (45 min · +10 · +20 neue Sportart)“, Formular ist wieder leer.
  3. Uhrzeit auf eine Stunde in der Zukunft stellen → Fehler „Die Aktivität darf nicht in der Zukunft liegen.“
  4. Profil → „📜 Verlauf“: Die Aktivität steht oben. Filter „🏃 Joggen“ und „7 Tage“ ändern die Liste.
  5. Eintrag antippen → Details. „Aktivität löschen“ → Rückfrage → „Löschen“ → zurück im Verlauf, Eintrag ist weg.
- [ ] `git status --short` zeigt keine unbeabsichtigten Dateien.
