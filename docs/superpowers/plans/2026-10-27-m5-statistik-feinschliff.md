# M5 Statistik und Feinschliff – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Statistik mit Diagrammen im Profil, die Zahlen dieser Woche im Profil-Kopf, „Erneut versuchen“ und Hinweise bei leeren Daten auf allen Screens und eine Doku, mit der man das Projekt versteht, verteidigt und vorführt.

**Architecture:** Das Backend bekommt eine reine Rechenfunktion `Statistics` in `logic/` und den Endpunkt `GET /stats`. Die App zeigt die Statistik in einem eigenen Screen unter 👤 Profil mit drei Säulendiagrammen (Vico). Die Wochenzahlen stehen im Profil-Kopf. Fehlende „Erneut versuchen“-Knöpfe und der Hinweis auf der leeren Karte kommen dazu. Die Doku ergänzt die README und legt eine Test-Checkliste an, die zugleich der Ablauf für die Präsentation ist.

**Tech Stack:** Backend: Ktor 3.6.0, Exposed 1.6.0. App: Vico 2.3.6 (`com.patrykandpatrick.vico:compose-m3`), Material3, Navigation Compose 2.10.2.

**Spec:** `docs/superpowers/specs/2026-10-20-touchgrass-design.md` (Abschnitte 4 Statistiken, 6 Profil und Gemeinsame Muster, 7 Datenschutz, 8 Tests) · **API-Vertrag:** `docs/api.md`

## Global Constraints

- Alle Commits direkt auf `master`, keine Branches, nicht pushen. Commit-Nachrichten auf Deutsch mit Trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Die App bleibt bei Kotlin 2.2.10. Vico wird fest auf **2.3.6** gepinnt: Vico 2.4 und neuer brauchen Kotlin 2.3, die neueste 3.3 sogar 2.4. Android Studio schlägt neuere Versionen vor – nicht übernehmen.
- Statistik (aus der Spec): Zusammenfassung Anzahl/Minuten/kcal der laufenden Woche, Minuten pro Woche (letzte 8 Wochen), Aktivitäten je Sportart (gesamt), kcal pro Monat (letzte 6 Monate). Alle Grenzen in `Europe/Berlin`, Woche Montag bis Sonntag.
- `minutesPerWeek` hat immer 8 Einträge, `kcalPerMonth` immer 6, jeweils älteste zuerst und auch mit 0.
- Jeder Screen-Zustand ist `Loading`, `Content` oder `Error` (Meldung + „Erneut versuchen“). Fehlertexte kommen aus der API, Netzwerkfehler heißen „Server nicht erreichbar“.
- Testnamen deutsch in Backticks, Code englisch, UI-Texte deutsch, wenig Kommentare.

## Entscheidungen, die über die Spec hinausgehen

- **„Vier Vico-Diagramme“:** Die Zusammenfassung der Woche sind drei Zahlen, ein Diagramm daraus wäre nichtssagend. Sie steht als Text im Profil-Kopf und oben im Statistik-Screen. Die anderen drei Kennzahlen sind Säulendiagramme.
- **Leere Diagramme:** Sind alle Werte 0, zeigt der Screen statt eines Diagramms einen Hinweis. So zeichnet Vico nie ein Diagramm ohne Höhe.
- **Achsen:** unten Kalenderwochen („KW 41“), Monatskürzel („Okt“) und die Sport-Emojis. Links runde Schritte (1, 2, 5, 10, 20, 50 …), damit keine Werte wie 0,5 erscheinen.
- **Einstellungen** sind seit M1 vollständig (Gewicht, Wochenziel, Passwort, Logout, Konto löschen). M5 ergänzt dort nur „Erneut versuchen“, wenn das Profil nicht lädt.
- **Die Cloud** (Neon + Render) und der Abschnitt „App auf andere Handys bringen“ kommen in M6.

## Ausführungshinweise

- Vor Gradle: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'`. Backend-Befehle im Ordner `backend`, App-Befehle im Wurzelordner.
- **Für Claude:** Gradle nur im Terminal-Panel (`run_in_terminal`). Testergebnisse in `**/build/test-results/**/*.xml` prüfen.
- **Nach Task 2 das Backend neu starten** (im Backend-Fenster Ctrl+C und `J`, dann `.\gradlew.bat run` im Ordner `backend`), sonst kennt es `/stats` nicht und das Profil zeigt einen Fehler.
- **Nach dem Plan in Android Studio „Sync Project with Gradle Files“** (neue Bibliothek Vico).
- M5 braucht keine Datenbank-Migration.

## Dateiübersicht

```
backend/src/main/kotlin/de/touchgrass/
├── logic/Statistics.kt                  ActivityFact, WeekValue, MonthValue, SportCount, Rechnung
├── stats/StatsDtos.kt, stats/StatsService.kt
├── routes/StatsRoutes.kt                GET /stats
└── Application.kt                       + StatsService, statsRoutes
backend/src/test/kotlin/de/touchgrass/
├── logic/StatisticsTest.kt
└── StatsRoutesTest.kt

app/src/main/java/de/touchgrass/
├── data/dto/StatsDtos.kt, data/SportApi.kt (+ stats)
├── ui/stats/StatsFormat.kt              Wochen-/Monatsnamen, Achsenschritt, Wochen-Text
├── ui/stats/StatsViewModel.kt, ui/stats/ColumnChart.kt, ui/stats/StatsScreen.kt
├── ui/profile/ProfileViewModel.kt, ProfileScreen.kt    Kopf mit Wochenzahlen, Eintrag „📊 Statistik“
├── ui/profile/SettingsViewModel.kt, SettingsScreen.kt  Erneut versuchen beim Laden
├── ui/record/TimerViewModel.kt, TimerScreen.kt, RecordViewModel.kt, RecordScreen.kt   Sportarten neu laden
├── ui/activities/ActivityDetailViewModel.kt, ActivityDetailScreen.kt                 Erneut versuchen
├── ui/map/MapScreen.kt                  Hinweis ohne Marker
└── ui/Navigation.kt                     StatsRoute
app/src/test/java/de/touchgrass/
├── testing/Fakes.kt                     + stats(), testStats
├── data/SportApiTest.kt (+1), ui/stats/StatsFormatTest.kt, ui/stats/StatsViewModelTest.kt
└── ProfileViewModelTest, TimerViewModelTest, RecordViewModelTest, ActivityDetailViewModelTest, SettingsViewModelTest

docs/api.md                              /stats: Reihenfolge, laufende Woche und laufender Monat
README.md                                Warum PostgreSQL, Datenschutz, Bewusste Grenzen
docs/testcheckliste.md                   Test-Checkliste und Ablauf der Präsentation
```

---

### Task 1: Backend – Statistik-Rechnung

**Files:**
- Create: `backend/src/main/kotlin/de/touchgrass/logic/Statistics.kt`
- Test: `backend/src/test/kotlin/de/touchgrass/logic/StatisticsTest.kt`

**Interfaces:**
- Consumes: `WeekTotals(minutes, activities, kcal)` aus `logic/WeeklyGoal.kt` (M2)
- Produces: `data class ActivityFact(date: LocalDate, durationMin: Int, kcal: Int, sportCode: String)`; `data class WeekValue(weekStart: LocalDate, value: Int)`; `data class MonthValue(month: YearMonth, value: Int)`; `data class SportCount(sportCode: String, count: Int)`; `object Statistics { const val WEEKS = 8; const val MONTHS = 6; fun thisWeek(facts, today): WeekTotals; fun minutesPerWeek(facts, today): List<WeekValue>; fun kcalPerMonth(facts, today): List<MonthValue>; fun activitiesPerSport(facts): List<SportCount> }`.

- [ ] **Step 1: Failing Test schreiben**

`backend/src/test/kotlin/de/touchgrass/logic/StatisticsTest.kt`:
```kotlin
package de.touchgrass.logic

import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class StatisticsTest {
    private val today = LocalDate.parse("2026-10-08")

    private fun fact(date: String, minutes: Int = 30, kcal: Int = 200, sport: String = "RUNNING") =
        ActivityFact(LocalDate.parse(date), minutes, kcal, sport)

    @Test
    fun `diese Woche zählt Montag bis heute`() {
        val facts = listOf(fact("2026-10-04"), fact("2026-10-05", minutes = 40), fact("2026-10-08", minutes = 20, kcal = 150))

        assertEquals(WeekTotals(minutes = 60, activities = 2, kcal = 350), Statistics.thisWeek(facts, today))
    }

    @Test
    fun `acht Wochen, älteste zuerst, Wochen ohne Sport mit 0`() {
        val facts = listOf(fact("2026-08-17"), fact("2026-08-18", minutes = 15), fact("2026-09-30", minutes = 45), fact("2026-10-08", minutes = 20))

        val weeks = Statistics.minutesPerWeek(facts, today)

        assertEquals(8, weeks.size)
        assertEquals(LocalDate.parse("2026-08-17"), weeks.first().weekStart)
        assertEquals(LocalDate.parse("2026-10-05"), weeks.last().weekStart)
        assertEquals(listOf(45, 0, 0, 0, 0, 0, 45, 20), weeks.map { it.value })
    }

    @Test
    fun `sechs Monate über den Jahreswechsel`() {
        val facts = listOf(fact("2025-12-24", kcal = 300), fact("2026-02-01", kcal = 100), fact("2026-02-28", kcal = 50))

        val months = Statistics.kcalPerMonth(facts, LocalDate.parse("2026-03-10"))

        assertEquals((9..14).map { YearMonth.of(2025, 1).plusMonths(it.toLong()) }, months.map { it.month })
        assertEquals(listOf(0, 0, 300, 0, 150, 0), months.map { it.value })
    }

    @Test
    fun `Sportarten nach Anzahl, bei Gleichstand nach Code`() {
        val facts = listOf(fact("2026-10-01", sport = "WALKING"), fact("2026-10-02", sport = "CYCLING"), fact("2026-10-03", sport = "WALKING"), fact("2026-10-04", sport = "RUNNING"))

        assertEquals(
            listOf(SportCount("WALKING", 2), SportCount("CYCLING", 1), SportCount("RUNNING", 1)),
            Statistics.activitiesPerSport(facts),
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'ActivityFact'`, `'Statistics'`, `'SportCount'`.

- [ ] **Step 3: Rechnung schreiben**

`backend/src/main/kotlin/de/touchgrass/logic/Statistics.kt`:
```kotlin
package de.touchgrass.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class ActivityFact(val date: LocalDate, val durationMin: Int, val kcal: Int, val sportCode: String)

data class WeekValue(val weekStart: LocalDate, val value: Int)

data class MonthValue(val month: YearMonth, val value: Int)

data class SportCount(val sportCode: String, val count: Int)

object Statistics {
    const val WEEKS = 8
    const val MONTHS = 6

    fun thisWeek(facts: List<ActivityFact>, today: LocalDate): WeekTotals {
        val monday = mondayOf(today)
        val week = facts.filter { mondayOf(it.date) == monday }
        return WeekTotals(minutes = week.sumOf { it.durationMin }, activities = week.size, kcal = week.sumOf { it.kcal })
    }

    fun minutesPerWeek(facts: List<ActivityFact>, today: LocalDate): List<WeekValue> {
        val minutes = facts.groupBy { mondayOf(it.date) }.mapValues { (_, week) -> week.sumOf { it.durationMin } }
        val current = mondayOf(today)
        return (WEEKS - 1 downTo 0).map { back ->
            val monday = current.minusWeeks(back.toLong())
            WeekValue(monday, minutes[monday] ?: 0)
        }
    }

    fun kcalPerMonth(facts: List<ActivityFact>, today: LocalDate): List<MonthValue> {
        val kcal = facts.groupBy { YearMonth.from(it.date) }.mapValues { (_, month) -> month.sumOf { it.kcal } }
        val current = YearMonth.from(today)
        return (MONTHS - 1 downTo 0).map { back ->
            val month = current.minusMonths(back.toLong())
            MonthValue(month, kcal[month] ?: 0)
        }
    }

    fun activitiesPerSport(facts: List<ActivityFact>): List<SportCount> = facts
        .groupingBy { it.sportCode }
        .eachCount()
        .map { (code, count) -> SportCount(code, count) }
        .sortedWith(compareByDescending<SportCount> { it.count }.thenBy { it.sportCode })

    private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`. Insgesamt 91 Backend-Tests grün, davon neu: `StatisticsTest` 4.

- [ ] **Step 5: Commit**

```powershell
git add backend/src
git commit -m "Statistik-Rechnung für Woche, acht Wochen, Sportarten und sechs Monate" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Backend – Endpunkt GET /stats

**Files:**
- Create: `backend/src/main/kotlin/de/touchgrass/stats/StatsDtos.kt`, `.../stats/StatsService.kt`, `.../routes/StatsRoutes.kt`
- Modify: `backend/src/main/kotlin/de/touchgrass/Application.kt`, `docs/api.md`
- Test: `backend/src/test/kotlin/de/touchgrass/StatsRoutesTest.kt`

**Interfaces:**
- Consumes: `ActivityRepository.list(userId)` (M2), `BerlinTime.date` (M4), `Statistics` und `ActivityFact` (Task 1), `startApp(clock)` (M4)
- Produces: `WeekSummaryDto(activities, minutes, kcal)`, `WeekMinutesDto(weekStart: String, minutes)`, `SportCountDto(sportCode, count)`, `MonthKcalDto(month: String, kcal)`, `StatsDto(thisWeek, minutesPerWeek, activitiesPerSport, kcalPerMonth)`; `StatsService(activities, clock).stats(userId): StatsDto`; `Route.statsRoutes(service)` → `GET /api/stats`.

- [ ] **Step 1: Failing Test schreiben**

`backend/src/test/kotlin/de/touchgrass/StatsRoutesTest.kt`:
```kotlin
package de.touchgrass

import de.touchgrass.stats.MonthKcalDto
import de.touchgrass.stats.SportCountDto
import de.touchgrass.stats.StatsDto
import de.touchgrass.stats.WeekMinutesDto
import de.touchgrass.stats.WeekSummaryDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsRoutesTest {
    // Donnerstag, 8. Oktober 2026, 12 Uhr Berliner Zeit
    private val thursday = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC)

    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `Statistik fasst Woche, acht Wochen, Sportarten und sechs Monate zusammen`() = testApplication {
        val client = startApp(thursday)
        val token = client.register("max_m").token
        client.createActivity(token, activity(sportCode = "CYCLING", startedAt = "2026-09-01T08:00:00+02:00", durationMin = 60))
        client.createActivity(token, activity(startedAt = "2026-10-06T08:00:00+02:00", durationMin = 30))
        client.createActivity(token, activity(startedAt = "2026-10-08T08:00:00+02:00", durationMin = 15))

        val stats = client.get("/api/stats") { bearerAuth(token) }.body<StatsDto>()

        assertEquals(WeekSummaryDto(activities = 2, minutes = 45, kcal = 420), stats.thisWeek)
        assertEquals(
            listOf("2026-08-17", "2026-08-24", "2026-08-31", "2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28", "2026-10-05"),
            stats.minutesPerWeek.map { it.weekStart },
        )
        assertEquals(listOf(0, 0, 60, 0, 0, 0, 0, 45), stats.minutesPerWeek.map { it.minutes })
        assertEquals(listOf(SportCountDto("RUNNING", 2), SportCountDto("CYCLING", 1)), stats.activitiesPerSport)
        assertEquals(
            listOf(
                MonthKcalDto("2026-05", 0),
                MonthKcalDto("2026-06", 0),
                MonthKcalDto("2026-07", 0),
                MonthKcalDto("2026-08", 0),
                MonthKcalDto("2026-09", 490),
                MonthKcalDto("2026-10", 420),
            ),
            stats.kcalPerMonth,
        )
    }

    @Test
    fun `ohne Aktivitäten gibt es Nullen und keine Sportarten`() = testApplication {
        val client = startApp(thursday)
        val token = client.register("max_m").token

        val stats = client.get("/api/stats") { bearerAuth(token) }.body<StatsDto>()

        assertEquals(WeekSummaryDto(0, 0, 0), stats.thisWeek)
        assertEquals(List(8) { 0 }, stats.minutesPerWeek.map { it.minutes })
        assertEquals(WeekMinutesDto("2026-10-05", 0), stats.minutesPerWeek.last())
        assertEquals(emptyList(), stats.activitiesPerSport)
        assertEquals(List(6) { 0 }, stats.kcalPerMonth.map { it.kcal })
    }

    @Test
    fun `Statistik braucht eine Anmeldung`() = testApplication {
        val client = startApp(thursday)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/stats").status)
    }
}
```

Zu den kcal im ersten Test: ohne Gewicht gilt 70 kg. Joggen (MET 8): 30 min = 280, 15 min = 140, zusammen 420. Radfahren (MET 7): 60 min = 490.

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'StatsDto'`, `'WeekSummaryDto'`.

- [ ] **Step 3: DTOs, Service, Route und Verdrahtung**

`backend/src/main/kotlin/de/touchgrass/stats/StatsDtos.kt`:
```kotlin
package de.touchgrass.stats

import kotlinx.serialization.Serializable

@Serializable
data class WeekSummaryDto(val activities: Int, val minutes: Int, val kcal: Int)

@Serializable
data class WeekMinutesDto(val weekStart: String, val minutes: Int)

@Serializable
data class SportCountDto(val sportCode: String, val count: Int)

@Serializable
data class MonthKcalDto(val month: String, val kcal: Int)

@Serializable
data class StatsDto(
    val thisWeek: WeekSummaryDto,
    val minutesPerWeek: List<WeekMinutesDto>,
    val activitiesPerSport: List<SportCountDto>,
    val kcalPerMonth: List<MonthKcalDto>,
)
```

`backend/src/main/kotlin/de/touchgrass/stats/StatsService.kt`:
```kotlin
package de.touchgrass.stats

import de.touchgrass.activities.ActivityRepository
import de.touchgrass.logic.ActivityFact
import de.touchgrass.logic.BerlinTime
import de.touchgrass.logic.Statistics
import java.time.Clock
import java.time.OffsetDateTime

class StatsService(
    private val activities: ActivityRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun stats(userId: Long): StatsDto {
        val today = BerlinTime.date(OffsetDateTime.now(clock))
        val facts = activities.list(userId).map {
            ActivityFact(BerlinTime.date(it.startedAt), it.durationMin, it.caloriesKcal, it.sportCode)
        }
        val week = Statistics.thisWeek(facts, today)
        return StatsDto(
            thisWeek = WeekSummaryDto(activities = week.activities, minutes = week.minutes, kcal = week.kcal),
            minutesPerWeek = Statistics.minutesPerWeek(facts, today).map { WeekMinutesDto(it.weekStart.toString(), it.value) },
            activitiesPerSport = Statistics.activitiesPerSport(facts).map { SportCountDto(it.sportCode, it.count) },
            kcalPerMonth = Statistics.kcalPerMonth(facts, today).map { MonthKcalDto(it.month.toString(), it.value) },
        )
    }
}
```

`backend/src/main/kotlin/de/touchgrass/routes/StatsRoutes.kt`:
```kotlin
package de.touchgrass.routes

import de.touchgrass.auth.AUTH_JWT
import de.touchgrass.auth.userId
import de.touchgrass.stats.StatsService
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.statsRoutes(service: StatsService) {
    authenticate(AUTH_JWT) {
        get("/stats") {
            call.respond(service.stats(call.userId()))
        }
    }
}
```

In `backend/src/main/kotlin/de/touchgrass/Application.kt` die Imports ergänzen (alphabetisch einsortiert):
```kotlin
import de.touchgrass.routes.statsRoutes
import de.touchgrass.stats.StatsService
```
nach der Zeile `val leaderboardService = …` einfügen:
```kotlin
    val statsService = StatsService(activityRepository, clock)
```
und im `routing`-Block nach `progressRoutes(progressService, leaderboardService)` einfügen:
```kotlin
            statsRoutes(statsService)
```

In `docs/api.md` unter `### GET /stats` die Zeile
```
`minutesPerWeek` hat immer 8 Einträge (älteste zuerst, auch mit 0), `kcalPerMonth` immer 6.
```
ersetzen durch
```
`minutesPerWeek` hat immer 8 Einträge (älteste zuerst, auch mit 0, der letzte ist die laufende Woche), `kcalPerMonth` immer 6 (der letzte ist der laufende Monat). `activitiesPerSport` enthält nur Sportarten mit Aktivitäten, die häufigste zuerst, bei Gleichstand nach `sportCode`.
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`. Insgesamt 94 Backend-Tests grün, davon neu: `StatsRoutesTest` 3.

- [ ] **Step 5: Commit**

```powershell
git add backend/src docs/api.md
git commit -m "Statistik-Endpunkt GET /stats" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Danach das laufende Backend neu starten, damit es `/stats` kennt (siehe Ausführungshinweise).

---

### Task 3: App – Statistik abrufen

**Files:**
- Create: `app/src/main/java/de/touchgrass/data/dto/StatsDtos.kt`
- Modify: `app/src/main/java/de/touchgrass/data/SportApi.kt`, `app/src/test/java/de/touchgrass/testing/Fakes.kt`
- Test: `app/src/test/java/de/touchgrass/data/SportApiTest.kt` (+1)

**Interfaces:**
- Consumes: `GET /api/stats` (Task 2), `SportApi`, `FakeSportApi` (M0–M4), in `SportApiTest` die vorhandenen Helfer `enqueueJson`, `server` und `api`
- Produces: App-DTOs `WeekSummaryDto(activities, minutes, kcal)`, `WeekMinutesDto(weekStart: String, minutes)`, `SportCountDto(sportCode, count)`, `MonthKcalDto(month: String, kcal)`, `StatsDto(thisWeek, minutesPerWeek, activitiesPerSport, kcalPerMonth)` im Paket `de.touchgrass.data.dto` (gleiche Felder wie im Backend); `SportApi.stats(): StatsDto`; `FakeSportApi.stats()` meldet wie die anderen Methoden „wird in diesem Test nicht erwartet“.

- [ ] **Step 1: Failing Test schreiben**

In `app/src/test/java/de/touchgrass/data/SportApiTest.kt` am Ende der Klasse einfügen:
```kotlin

    @Test
    fun `stats liest Woche, Wochenminuten, Sportarten und Monats-kcal`() = runBlocking {
        enqueueJson(200, """{"thisWeek":{"activities":3,"minutes":156,"kcal":1420},"minutesPerWeek":[{"weekStart":"2026-10-05","minutes":156}],"activitiesPerSport":[{"sportCode":"RUNNING","count":12}],"kcalPerMonth":[{"month":"2026-10","kcal":1420}]}""")

        val stats = api.stats()

        assertEquals("/api/stats", server.takeRequest().path)
        assertEquals(156, stats.thisWeek.minutes)
        assertEquals("2026-10-05", stats.minutesPerWeek.single().weekStart)
        assertEquals(12, stats.activitiesPerSport.single().count)
        assertEquals("2026-10", stats.kcalPerMonth.single().month)
    }
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'stats'`.

- [ ] **Step 3: DTOs, API-Methode und Fake schreiben**

`app/src/main/java/de/touchgrass/data/dto/StatsDtos.kt`:
```kotlin
package de.touchgrass.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class WeekSummaryDto(val activities: Int, val minutes: Int, val kcal: Int)

@Serializable
data class WeekMinutesDto(val weekStart: String, val minutes: Int)

@Serializable
data class SportCountDto(val sportCode: String, val count: Int)

@Serializable
data class MonthKcalDto(val month: String, val kcal: Int)

@Serializable
data class StatsDto(
    val thisWeek: WeekSummaryDto,
    val minutesPerWeek: List<WeekMinutesDto>,
    val activitiesPerSport: List<SportCountDto>,
    val kcalPerMonth: List<MonthKcalDto>,
)
```

In `app/src/main/java/de/touchgrass/data/SportApi.kt` nach `import de.touchgrass.data.dto.SportDto` einfügen:
```kotlin
import de.touchgrass.data.dto.StatsDto
```
und im Interface `SportApi` nach der Methode `leaderboard(…)` einfügen:
```kotlin

    @GET("stats")
    suspend fun stats(): StatsDto
```

In `app/src/test/java/de/touchgrass/testing/Fakes.kt` nach `import de.touchgrass.data.dto.SportDto` einfügen:
```kotlin
import de.touchgrass.data.dto.StatsDto
```
und in `FakeSportApi` nach `override suspend fun leaderboard(…)` einfügen:
```kotlin
    override suspend fun stats(): StatsDto = notExpected("stats")
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Insgesamt 80 App-Tests grün, davon neu: `SportApiTest` +1.

- [ ] **Step 5: Commit**

```powershell
git add app/src
git commit -m "App ruft die Statistik ab" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: App – Statistik-Screen mit drei Säulendiagrammen

**Files:**
- Create: `app/src/main/java/de/touchgrass/ui/stats/StatsFormat.kt`, `.../ui/stats/StatsViewModel.kt`, `.../ui/stats/ColumnChart.kt`, `.../ui/stats/StatsScreen.kt`
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: `app/src/main/java/de/touchgrass/ui/profile/ProfileScreen.kt`, `app/src/main/java/de/touchgrass/ui/Navigation.kt`
- Modify: `app/src/test/java/de/touchgrass/testing/Fakes.kt`
- Test: `app/src/test/java/de/touchgrass/ui/stats/StatsFormatTest.kt`, `app/src/test/java/de/touchgrass/ui/stats/StatsViewModelTest.kt`

**Interfaces:**
- Consumes: `SportApi.stats()` und die Stats-DTOs (Task 3), `SportApi.sports()`, `SportDto`, `UiState`, `apiCall`, `ApiException` (M0–M2), im Test `FakeSportApi`, `MainDispatcherRule`, `testSports`
- Produces:
  - `fun WeekSummaryDto.label(): String` („3 Aktivitäten · 156 min · ~1420 kcal“), `fun weekLabel(weekStart: String): String` („KW 34“), `fun monthLabel(month: String): String` („Mär“), `fun List<SportDto>.emojiFor(code: String): String`, `fun axisStep(max: Int): Double`
  - `data class StatsContent(stats: StatsDto, sports: List<SportDto>)`; `class StatsViewModel(api: SportApi)` mit `state: StateFlow<UiState<StatsContent>>` und `refresh()`
  - `@Composable fun ColumnChart(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier)`; `@Composable fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit)`
  - `ProfileScreen(viewModel, onOpenStats, onOpenHistory, onOpenSettings, extra)`; `@Serializable object StatsRoute`
  - im Test `val testStats: StatsDto` (diese Woche 3 Aktivitäten, 156 min, 1420 kcal; Wochen ab 2026-08-17; RUNNING 5, CYCLING 2; Monate 2026-05 bis 2026-10)

- [ ] **Step 1: Failing Tests schreiben**

In `app/src/test/java/de/touchgrass/testing/Fakes.kt` diese Imports alphabetisch einsortieren (`MonthKcalDto` nach `LoginRequest`, `SportCountDto` nach `RegisterRequest`, die beiden `Week…` nach `Visibility`):
```kotlin
import de.touchgrass.data.dto.MonthKcalDto
import de.touchgrass.data.dto.SportCountDto
import de.touchgrass.data.dto.WeekMinutesDto
import de.touchgrass.data.dto.WeekSummaryDto
```
nach `import retrofit2.Response` einfügen:
```kotlin
import java.time.LocalDate
import java.time.YearMonth
```
und am Ende der Datei einfügen:
```kotlin

val testStats = StatsDto(
    thisWeek = WeekSummaryDto(activities = 3, minutes = 156, kcal = 1420),
    minutesPerWeek = listOf(0, 30, 60, 0, 45, 90, 120, 156).mapIndexed { week, minutes ->
        WeekMinutesDto(LocalDate.parse("2026-08-17").plusWeeks(week.toLong()).toString(), minutes)
    },
    activitiesPerSport = listOf(SportCountDto("RUNNING", 5), SportCountDto("CYCLING", 2)),
    kcalPerMonth = listOf(0, 0, 400, 900, 1300, 1420).mapIndexed { month, kcal ->
        MonthKcalDto(YearMonth.of(2026, 5).plusMonths(month.toLong()).toString(), kcal)
    },
)
```

`app/src/test/java/de/touchgrass/ui/stats/StatsFormatTest.kt`:
```kotlin
package de.touchgrass.ui.stats

import de.touchgrass.data.dto.WeekSummaryDto
import de.touchgrass.testing.testSports
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsFormatTest {
    @Test
    fun `Wochenzusammenfassung in Einzahl und Mehrzahl`() {
        assertEquals("3 Aktivitäten · 156 min · ~1420 kcal", WeekSummaryDto(3, 156, 1420).label())
        assertEquals("1 Aktivität · 30 min · ~280 kcal", WeekSummaryDto(1, 30, 280).label())
    }

    @Test
    fun `Wochen als Kalenderwoche, Monate als Kurzname`() {
        assertEquals("KW 34", weekLabel("2026-08-17"))
        assertEquals("KW 41", weekLabel("2026-10-05"))
        assertEquals("Mär", monthLabel("2026-03"))
        assertEquals("Dez", monthLabel("2025-12"))
    }

    @Test
    fun `Sportart als Emoji, unbekannte als Code`() {
        assertEquals("🏃", testSports.emojiFor("RUNNING"))
        assertEquals("CURLING", testSports.emojiFor("CURLING"))
    }

    @Test
    fun `Achsenschritte sind runde Zahlen`() {
        assertEquals(1.0, axisStep(0), 0.0)
        assertEquals(1.0, axisStep(3), 0.0)
        assertEquals(5.0, axisStep(10), 0.0)
        assertEquals(10.0, axisStep(40), 0.0)
        assertEquals(50.0, axisStep(156), 0.0)
        assertEquals(500.0, axisStep(1420), 0.0)
    }
}
```

`app/src/test/java/de/touchgrass/ui/stats/StatsViewModelTest.kt`:
```kotlin
package de.touchgrass.ui.stats

import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.StatsDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.testSports
import de.touchgrass.testing.testStats
import de.touchgrass.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class StatsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `lädt Statistik und Sportarten`() {
        val viewModel = StatsViewModel(object : FakeSportApi() {
            override suspend fun stats(): StatsDto = testStats
            override suspend fun sports(): List<SportDto> = testSports
        })

        viewModel.refresh()

        assertEquals(UiState.Content(StatsContent(testStats, testSports)), viewModel.state.value)
    }

    @Test
    fun `nicht erreichbarer Server wird angezeigt`() {
        val viewModel = StatsViewModel(object : FakeSportApi() {
            override suspend fun stats(): StatsDto = throw IOException("offline")
        })

        viewModel.refresh()

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, sie müssen fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren, u. a. `Unresolved reference 'weekLabel'`, `'axisStep'`, `'StatsViewModel'`.

- [ ] **Step 3: Anzeige-Helfer und ViewModel schreiben**

`app/src/main/java/de/touchgrass/ui/stats/StatsFormat.kt`:
```kotlin
package de.touchgrass.ui.stats

import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.WeekSummaryDto
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val MONTHS = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")

fun WeekSummaryDto.label(): String =
    "${if (activities == 1) "1 Aktivität" else "$activities Aktivitäten"} · $minutes min · ~$kcal kcal"

fun weekLabel(weekStart: String): String = "KW ${LocalDate.parse(weekStart).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

fun monthLabel(month: String): String = MONTHS[YearMonth.parse(month).monthValue - 1]

fun List<SportDto>.emojiFor(code: String): String = firstOrNull { it.code == code }?.emoji ?: code

// Abstand der Achsenbeschriftung in runden Schritten (1, 2, 5, 10, 20, 50 …), damit keine Werte wie 0,5 erscheinen.
fun axisStep(max: Int): Double {
    val rough = max / 4.0
    if (rough <= 1) return 1.0
    val magnitude = 10.0.pow(floor(log10(rough)))
    return listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= rough } * magnitude
}
```

`app/src/main/java/de/touchgrass/ui/stats/StatsViewModel.kt`:
```kotlin
package de.touchgrass.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.StatsDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsContent(val stats: StatsDto, val sports: List<SportDto>)

class StatsViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow<UiState<StatsContent>>(UiState.Loading)
    val state: StateFlow<UiState<StatsContent>> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = try {
                UiState.Content(StatsContent(apiCall { api.stats() }, apiCall { api.sports() }))
            } catch (e: ApiException) {
                UiState.Error(e.message)
            }
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Insgesamt 86 App-Tests grün, davon neu: `StatsFormatTest` 4, `StatsViewModelTest` 2.

- [ ] **Step 5: Vico einbinden, Diagramm und Screen schreiben, im Profil verlinken**

In `gradle/libs.versions.toml` unter `[versions]` nach `spatialk = "0.6.1"` einfügen:
```toml
# Vico 2.4 und neuer brauchen Kotlin 2.3, die App hat 2.2.10.
vico = "2.3.6"
```
und unter `[libraries]` nach der Zeile `spatialk-geojson = …` einfügen:
```toml
vico-compose-m3 = { group = "com.patrykandpatrick.vico", name = "compose-m3", version.ref = "vico" }
```

In `app/build.gradle.kts` im Block `dependencies` nach `implementation(libs.spatialk.geojson)` einfügen:
```kotlin
    implementation(libs.vico.compose.m3)
```

`app/src/main/java/de/touchgrass/ui/stats/ColumnChart.kt`:
```kotlin
package de.touchgrass.ui.stats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.ColumnCartesianLayerModel

@Composable
fun ColumnChart(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier) {
    val model = remember(values) { CartesianChartModel(ColumnCartesianLayerModel.build { series(values) }) }
    val step = axisStep(values.maxOrNull() ?: 0)
    val itemPlacer = remember(step) { VerticalAxis.ItemPlacer.step({ step }) }
    val labelFormatter = remember(labels) { CartesianValueFormatter { _, x, _ -> labels.getOrElse(x.toInt()) { "" } } }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(itemPlacer = itemPlacer),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = labelFormatter),
            ),
            model = model,
            modifier = modifier.fillMaxWidth().height(200.dp),
        )
    }
}
```

`app/src/main/java/de/touchgrass/ui/stats/StatsScreen.kt`:
```kotlin
package de.touchgrass.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
fun StatsScreen(viewModel: StatsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        Text("Statistik", style = MaterialTheme.typography.headlineMedium)
        when (val s = state) {
            UiState.Loading -> CircularProgressIndicator()
            is UiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::refresh) { Text("Erneut versuchen") }
            }
            is UiState.Content -> StatsCharts(s.data)
        }
    }
}

@Composable
private fun StatsCharts(content: StatsContent) {
    val stats = content.stats
    Text("Diese Woche: ${stats.thisWeek.label()}")
    ChartSection(
        title = "Minuten pro Woche",
        values = stats.minutesPerWeek.map { it.minutes },
        labels = stats.minutesPerWeek.map { weekLabel(it.weekStart) },
    )
    ChartSection(
        title = "Aktivitäten je Sportart",
        values = stats.activitiesPerSport.map { it.count },
        labels = stats.activitiesPerSport.map { content.sports.emojiFor(it.sportCode) },
    )
    ChartSection(
        title = "kcal pro Monat (geschätzt)",
        values = stats.kcalPerMonth.map { it.kcal },
        labels = stats.kcalPerMonth.map { monthLabel(it.month) },
    )
}

@Composable
private fun ChartSection(title: String, values: List<Int>, labels: List<String>) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (values.none { it > 0 }) {
        Text("Noch keine Daten. Trag eine Aktivität ein, dann erscheint hier ein Diagramm.")
    } else {
        ColumnChart(values, labels)
    }
}
```

In `app/src/main/java/de/touchgrass/ui/profile/ProfileScreen.kt` in der Parameterliste von `ProfileScreen` nach `viewModel: ProfileViewModel,` einfügen:
```kotlin
    onOpenStats: () -> Unit,
```
und nach `HorizontalDivider()` vor dem Eintrag „📜 Verlauf“ einfügen:
```kotlin
        ListItem(
            headlineContent = { Text("📊 Statistik") },
            modifier = Modifier.clickable(onClick = onOpenStats),
        )
```

In `app/src/main/java/de/touchgrass/ui/Navigation.kt` nach `import de.touchgrass.ui.record.TimerViewModel` einfügen:
```kotlin
import de.touchgrass.ui.stats.StatsScreen
import de.touchgrass.ui.stats.StatsViewModel
```
nach `@Serializable object SettingsRoute` einfügen:
```kotlin
@Serializable object StatsRoute
```
im Aufruf von `ProfileScreen` nach `viewModel = viewModel { ProfileViewModel(container.api) },` einfügen:
```kotlin
                        onOpenStats = { navController.navigate(StatsRoute) },
```
und im Block `navigation<ProfileGraph>` vor `composable<SettingsRoute>` einfügen:
```kotlin
                composable<StatsRoute> {
                    StatsScreen(
                        viewModel = viewModel { StatsViewModel(container.api) },
                        onBack = { navController.popBackStack() },
                    )
                }
```

- [ ] **Step 6: Bauen und alle Tests laufen lassen**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 86 App-Tests grün.

- [ ] **Step 7: Auf dem Emulator prüfen**

Voraussetzungen: Der Emulator läuft, das Backend wurde nach Task 2 neu gestartet. Run (Wurzelordner): `.\gradlew.bat installDebug`. Dann die App öffnen und 👤 Profil → 📊 Statistik antippen.

Für Claude per adb (Tippen mit `& $adb shell input tap X Y`, die Koordinaten aus dem letzten Screenshot):
```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell am start -n de.touchgrass/.MainActivity
& $adb shell screencap -p /sdcard/stats.png
& $adb pull /sdcard/stats.png "$env:TEMP\stats.png"
& $adb logcat -b crash -d
```

Expected:
- Oben „← Zurück“, „Statistik“ und „Diese Woche: … Aktivitäten · … min · ~… kcal“.
- Drei Abschnitte „Minuten pro Woche“, „Aktivitäten je Sportart“ und „kcal pro Monat (geschätzt)“. Mit Aktivitäten zeigt jeder ein Säulendiagramm: unten KW-Nummern, Sport-Emojis oder Monatskürzel, links runde Zahlen. Ohne Aktivitäten steht dreimal „Noch keine Daten. Trag eine Aktivität ein, dann erscheint hier ein Diagramm.“
- Der Screen lässt sich scrollen, „← Zurück“ führt ins Profil.
- `logcat -b crash -d` enthält kein `FATAL EXCEPTION`.

- [ ] **Step 8: Commit**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src
git commit -m "Statistik-Screen mit drei Säulendiagrammen" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: App – Wochenzahlen im Profil-Kopf

**Files:**
- Modify: `app/src/main/java/de/touchgrass/ui/profile/ProfileViewModel.kt`, `app/src/main/java/de/touchgrass/ui/profile/ProfileScreen.kt`
- Test: `app/src/test/java/de/touchgrass/ui/profile/ProfileViewModelTest.kt`

**Interfaces:**
- Consumes: `SportApi.me()` (M1), `SportApi.stats()` und `WeekSummaryDto` (Task 3), `WeekSummaryDto.label()` (Task 4), im Test `testStats` (Task 4) und `testUser`
- Produces: `data class ProfileData(user: UserDto, thisWeek: WeekSummaryDto)`; `ProfileViewModel.state: StateFlow<UiState<ProfileData>>` (bisher `UiState<UserDto>`).

- [ ] **Step 1: Failing Test schreiben**

In `app/src/test/java/de/touchgrass/ui/profile/ProfileViewModelTest.kt` nach `import de.touchgrass.testing.MainDispatcherRule` einfügen:
```kotlin
import de.touchgrass.testing.testStats
```
und den Test `` `refresh lädt das Profil` `` ersetzen durch:
```kotlin
    @Test
    fun `refresh lädt das Profil und die Zahlen dieser Woche`() {
        val viewModel = ProfileViewModel(object : FakeSportApi() {
            override suspend fun me() = testUser
            override suspend fun stats() = testStats
        })

        viewModel.refresh()

        assertEquals(UiState.Content(ProfileData(testUser, testStats.thisWeek)), viewModel.state.value)
    }
```
Die Tests `` `ohne Server erscheint eine Fehlermeldung` `` und `` `Gewicht wird mit Komma und ohne unnötige Nachkommastelle angezeigt` `` bleiben unverändert.

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'ProfileData'`.

- [ ] **Step 3: ViewModel und Profil-Kopf**

`app/src/main/java/de/touchgrass/ui/profile/ProfileViewModel.kt` ganz ersetzen:
```kotlin
package de.touchgrass.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.UserDto
import de.touchgrass.data.dto.WeekSummaryDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileData(val user: UserDto, val thisWeek: WeekSummaryDto)

class ProfileViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ProfileData>>(UiState.Loading)
    val state: StateFlow<UiState<ProfileData>> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = try {
                UiState.Content(ProfileData(apiCall { api.me() }, apiCall { api.stats() }.thisWeek))
            } catch (e: ApiException) {
                UiState.Error(e.message)
            }
        }
    }
}
```

In `app/src/main/java/de/touchgrass/ui/profile/ProfileScreen.kt` nach `import de.touchgrass.ui.UiState` einfügen:
```kotlin
import de.touchgrass.ui.stats.label
```
und im Zweig `is UiState.Content` die ersten drei Zeilen
```kotlin
                val user = s.data
                Text("👤 ${user.username}", style = MaterialTheme.typography.headlineMedium)
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
```
ersetzen durch:
```kotlin
                val user = s.data.user
                Text("👤 ${user.username}", style = MaterialTheme.typography.headlineMedium)
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
                Text("Diese Woche: ${s.data.thisWeek.label()}", style = MaterialTheme.typography.titleMedium)
```

- [ ] **Step 4: Bauen und alle Tests laufen lassen**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Insgesamt 86 App-Tests grün. Neu ist keiner, der erste Test in `ProfileViewModelTest` heißt jetzt `` `refresh lädt das Profil und die Zahlen dieser Woche` ``.

- [ ] **Step 5: Commit**

```powershell
git add app/src
git commit -m "Profil zeigt die Zahlen dieser Woche" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: App – Fehler- und Leerzustände

**Files:**
- Modify: `app/src/main/java/de/touchgrass/ui/record/TimerViewModel.kt`, `.../ui/record/TimerScreen.kt`, `.../ui/record/RecordViewModel.kt`, `.../ui/record/RecordScreen.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/activities/ActivityDetailViewModel.kt`, `.../ui/activities/ActivityDetailScreen.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/profile/SettingsViewModel.kt`, `.../ui/profile/SettingsScreen.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/map/MapScreen.kt`
- Test: `app/src/test/java/de/touchgrass/ui/record/TimerViewModelTest.kt`, `.../ui/record/RecordViewModelTest.kt`, `.../ui/activities/ActivityDetailViewModelTest.kt`, `.../ui/profile/SettingsViewModelTest.kt` (je +1)

**Interfaces:**
- Consumes: `apiCall` mit „Server nicht erreichbar“ bei `IOException` (M0), `SettingsViewModel` (M1), `RecordViewModel`, `ActivityDetailViewModel` (M2), `TimerViewModel` (M3), im Test `FakeSportApi`, `FakeTimerStore`, `testSports`, `testActivity`, `testUser`
- Produces: `TimerUiState.sportsError: String?` und `RecordUiState.sportsError: String?` (`loadSports()` setzt und löscht nur noch dieses Feld, `error` bleibt für Start und Speichern); `ActivityDetailViewModel.load()`; `SettingsUiState.loadError: String?` und `SettingsViewModel.load()`.

- [ ] **Step 1: Failing Tests schreiben**

In allen vier Testdateien den Import ergänzen, in `TimerViewModelTest` und `RecordViewModelTest` vor `import java.time.Clock`, in `ActivityDetailViewModelTest` und `SettingsViewModelTest` nach `import org.junit.Test`:
```kotlin
import java.io.IOException
```

In `app/src/test/java/de/touchgrass/ui/record/TimerViewModelTest.kt` am Ende der Klasse einfügen:
```kotlin

    @Test
    fun `Sportarten lassen sich nach einem Fehler erneut laden`() {
        var calls = 0
        val failingOnce = object : FakeSportApi() {
            override suspend fun sports(): List<SportDto> = if (calls++ == 0) throw IOException("offline") else testSports
        }
        val viewModel = TimerViewModel(failingOnce, FakeTimerStore(), clock)
        assertEquals("Server nicht erreichbar", viewModel.state.value.sportsError)

        viewModel.loadSports()

        assertEquals(testSports, viewModel.state.value.sports)
        assertNull(viewModel.state.value.sportsError)
    }
```

In `app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt` am Ende der Klasse einfügen:
```kotlin

    @Test
    fun `Sportarten lassen sich nach einem Fehler erneut laden`() {
        var calls = 0
        val failingOnce = object : FakeSportApi() {
            override suspend fun sports(): List<SportDto> = if (calls++ == 0) throw IOException("offline") else testSports
        }
        val viewModel = RecordViewModel(failingOnce, FakeTimerStore(), fromTimer = false, clock)
        assertEquals("Server nicht erreichbar", viewModel.state.value.sportsError)

        viewModel.loadSports()

        assertEquals(testSports, viewModel.state.value.sports)
        assertNull(viewModel.state.value.sportsError)
    }
```

In `app/src/test/java/de/touchgrass/ui/activities/ActivityDetailViewModelTest.kt` am Ende der Klasse einfügen:
```kotlin

    @Test
    fun `nach einem Ladefehler lädt Erneut versuchen die Aktivität`() {
        var calls = 0
        val failingOnce = object : Api() {
            override suspend fun activity(id: Long): ActivityDto = if (calls++ == 0) throw IOException("offline") else testActivity(id = id)
        }
        val viewModel = ActivityDetailViewModel(failingOnce, id = 7)
        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value.activity)

        viewModel.load()

        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
    }
```

In `app/src/test/java/de/touchgrass/ui/profile/SettingsViewModelTest.kt` am Ende der Klasse einfügen:
```kotlin

    @Test
    fun `nach einem Ladefehler zeigt Erneut versuchen das Profil`() {
        var calls = 0
        val failingOnce = object : Api() {
            override suspend fun me(): UserDto = if (calls++ == 0) throw IOException("offline") else testUser.copy(goalTarget = 200)
        }
        val viewModel = SettingsViewModel(failingOnce, session)
        assertEquals("Server nicht erreichbar", viewModel.state.value.loadError)

        viewModel.load()

        assertNull(viewModel.state.value.loadError)
        assertEquals("200", viewModel.state.value.goalTarget)
    }
```

- [ ] **Step 2: Tests laufen lassen, sie müssen fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren, u. a. `Unresolved reference 'sportsError'`, `'loadError'`, `'load'`.

- [ ] **Step 3: ViewModels anpassen**

In `app/src/main/java/de/touchgrass/ui/record/TimerViewModel.kt` und in `app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt` jeweils im UI-Zustand (`TimerUiState` bzw. `RecordUiState`) nach `val sports: List<SportDto> = emptyList(),` einfügen:
```kotlin
    val sportsError: String? = null,
```
und in beiden Dateien `loadSports()` ersetzen durch:
```kotlin
    fun loadSports() {
        viewModelScope.launch {
            try {
                val sports = apiCall { api.sports() }
                _state.update { it.copy(sports = sports, sportsError = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(sportsError = e.message) }
            }
        }
    }
```

In `app/src/main/java/de/touchgrass/ui/activities/ActivityDetailViewModel.kt` den `init`-Block ersetzen durch:
```kotlin
    init {
        load()
    }

    fun load() {
        _state.update { it.copy(activity = UiState.Loading) }
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
```

In `app/src/main/java/de/touchgrass/ui/profile/SettingsViewModel.kt` in `SettingsUiState` nach `val loading: Boolean = true,` einfügen:
```kotlin
    val loadError: String? = null,
```
und den `init`-Block ersetzen durch:
```kotlin
    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            try {
                showProfile(apiCall { api.me() })
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, loadError = e.message) }
            }
        }
    }
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Insgesamt 90 App-Tests grün, davon neu: je 1 in `TimerViewModelTest`, `RecordViewModelTest`, `ActivityDetailViewModelTest` und `SettingsViewModelTest`.

- [ ] **Step 5: Screens anpassen**

In `app/src/main/java/de/touchgrass/ui/record/TimerScreen.kt` nach `Text("Aktivität", style = MaterialTheme.typography.headlineMedium)` und in `app/src/main/java/de/touchgrass/ui/record/RecordScreen.kt` nach `Text("Sportart")` jeweils einfügen:
```kotlin
        state.sportsError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::loadSports) { Text("Erneut versuchen") }
        }
```

In `app/src/main/java/de/touchgrass/ui/activities/ActivityDetailScreen.kt` die Zeile
```kotlin
            is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
```
ersetzen durch:
```kotlin
            is UiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::load) { Text("Erneut versuchen") }
            }
```

In `app/src/main/java/de/touchgrass/ui/profile/SettingsScreen.kt` direkt nach dem Block `if (state.loading) { … return@Column }` einfügen:
```kotlin
        state.loadError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::load) { Text("Erneut versuchen") }
            return@Column
        }
```

In `app/src/main/java/de/touchgrass/ui/map/MapScreen.kt` vor der Funktion `ActivityMap` (also vor deren Zeile `@Composable`) einfügen:
```kotlin
private const val NO_MARKERS_HINT = "Noch keine Aktivitäten mit Ort. Wähl beim Speichern einen Ort, dann erscheint hier ein Marker."

```
und in `ActivityMap` die Zeile
```kotlin
        hint?.let {
```
ersetzen durch:
```kotlin
        (hint ?: NO_MARKERS_HINT.takeIf { content.activities.isEmpty() })?.let {
```

- [ ] **Step 6: Bauen und alle Tests laufen lassen**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 90 App-Tests grün.

- [ ] **Step 7: Commit**

```powershell
git add app/src
git commit -m "Erneut versuchen und Hinweise bei fehlenden Daten" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Doku – Warum PostgreSQL, Datenschutz, Grenzen und Test-Checkliste

**Files:**
- Modify: `README.md`
- Create: `docs/testcheckliste.md`

**Interfaces:**
- Consumes: die UI-Texte aus M1–M4 und Task 4–6, Spec Abschnitt 4 (Bewusste Grenzen), 7 (Datenschutz) und 8 (Manuelle Test-Checkliste)
- Produces: README-Abschnitte „Warum PostgreSQL“, „Datenschutz“, „Bewusste Grenzen“; `docs/testcheckliste.md` als Test von Hand und Ablauf der Präsentation.

- [ ] **Step 1: README ergänzen**

In `README.md` in der Liste oben nach der Zeile ``- `docs/api.md` – API-Vertrag zwischen App und Backend`` einfügen:
```markdown
- `docs/testcheckliste.md` – Test von Hand für alle Screens, zugleich Ablauf der Präsentation
```
und am Ende der Datei anhängen:
````markdown

## Warum PostgreSQL

Die Daten hängen eng zusammen: Aktivitäten gehören zu einer Person und einer Sportart, freigeschaltete Achievements zu einer Person. Wochenziel, Leaderboard und Statistik sind Summen über Wochen und Monate. Dafür ist eine relationale Datenbank mit SQL gemacht. PostgreSQL ist kostenlos und weit verbreitet. Lokal braucht es keine Installation, weil das Backend es mitbringt, und die Tests laufen gegen dasselbe echte PostgreSQL. Für ein PostgreSQL auf einem Server reicht `DB_URL`, der Code bleibt gleich.

| Alternative | Warum nicht |
|---|---|
| Datenbank in der App (SQLite/Room) | Jedes Handy hätte nur seine eigenen Daten. Karte, Leaderboard und Anmeldung brauchen eine gemeinsame Datenbank. Spräche die App eine Datenbank im Internet direkt an, stünde deren Passwort in der APK. |
| Firebase (Firestore) | Dokumentdatenbank ohne SQL und ohne Joins. Punkte, Wochenziel-Bonus und Leaderboard müssten in der App oder in Cloud Functions laufen, unser Backend fiele weg. Genau das wollen wir selbst bauen und testen. |
| Supabase | Ist selbst PostgreSQL, bringt aber eine fertige API und Anmeldung mit. Auch das ersetzt den Teil, den wir selbst programmieren. |
| TiDB | Spricht MySQL, nicht PostgreSQL. Die Migrationen (`BIGSERIAL`, `TIMESTAMPTZ`) müssten umgeschrieben werden, und die Tests mit dem eingebauten PostgreSQL passten nicht mehr dazu. Die 5 GiB Gratisspeicher brauchen wir nicht: Tausend Aktivitäten belegen weniger als 1 MB. |

## Datenschutz

| Daten | Wozu | Wer sieht sie |
|---|---|---|
| Benutzername | Anmeldung, Leaderboard, öffentliche Marker | alle Nutzer |
| E-Mail | Anmeldung | nur das Backend |
| Passwort | Anmeldung, gespeichert nur als BCrypt-Hash | nur das Backend |
| Gewicht | kcal-Schätzung | nur die Person selbst |
| Aktivitäten mit Ort | Kernfunktion | die Person selbst, bei „🌎 Öffentlich“ alle |

- Neue Aktivitäten sind privat. Auf der Karte sehen andere nur öffentliche Aktivitäten.
- Im Leaderboard zählen alle Aktivitäten mit, auch private. Andere sehen dort nur Benutzername und Summe.
- Ort und Gewicht sind freiwillig.
- „Konto löschen“ entfernt das Konto mit allen Aktivitäten und Achievements (Recht auf Löschung). Die Datenbank löscht sie über `ON DELETE CASCADE` mit.
- Das Backend schreibt keine Anfragen ins Log, Passwörter tauchen dort also nicht auf. Der Schlüssel für die Login-Tokens liegt nicht im Git (siehe „Backend starten“).

## Bewusste Grenzen

- **kcal sind geschätzt:** MET-Wert der Sportart × Gewicht in kg × Dauer in Stunden. Ohne Gewicht rechnet das Backend mit 70 kg. Die App zeigt deshalb immer „~“ vor den kcal.
- **Keine Plausibilitätsprüfung:** Wer unrealistische Zeiten einträgt, steigt im Leaderboard.
- **Logout gilt nur für dieses Handy:** Die App löscht ihren Login-Token. Der Token selbst bleibt bis zu 30 Tage gültig und lässt sich nicht vorzeitig sperren.
- **HTTP statt HTTPS:** Lokal läuft alles über HTTP, das erlaubt nur der Debug-Build. Im echten Betrieb wäre HTTPS Pflicht, sonst gehen Passwort und Token lesbar durchs Netz.
- **Ohne Server geht fast nichts:** Auf dem Handy liegen nur Login-Token und Timer. Der Timer läuft auch ohne Netz, alles andere braucht das Backend.
````

- [ ] **Step 2: Test-Checkliste anlegen**

`docs/testcheckliste.md`:
````markdown
# Test-Checkliste

Von oben nach unten durchgehen, vor jeder Abgabe und vor der Präsentation. Die Abschnitte 1 bis 7 sind zugleich der Ablauf der Vorführung, Abschnitt 8 gehört nicht dazu.

## Vorbereitung

- [ ] Backend läuft (`.\gradlew.bat run` im Ordner `backend`), die App ist mit ▶ Run installiert.
- [ ] Ein zweites Konto `lisa_k` hat zwei, drei öffentliche Aktivitäten an verschiedenen Orten, damit Karte und Leaderboard nicht leer sind. Danach abmelden.
- [ ] Im Emulator ist ein Standort gesetzt: **⋮ (Extended Controls) → Location**.

## 1. Konto anlegen

- [ ] Die App startet mit „Anmelden“. Als `lisa_k` mit falschem Passwort anmelden → „Benutzername/E-Mail oder Passwort ist falsch.“
- [ ] „Noch kein Konto? Registrieren“: Benutzername `lisa_k` mit sonst gültigen Angaben → „Dieser Benutzername ist schon vergeben.“ am Feld. Eigener Benutzername, aber Passwort mit weniger als 8 Zeichen → Fehlermeldung am Passwortfeld.
- [ ] Mit gültigen Angaben „Konto anlegen“ → die App öffnet die 🗺️ Karte.
- [ ] 👤 Profil: Benutzername, E-Mail, „Diese Woche: 0 Aktivitäten · 0 min · ~0 kcal“, „Gewicht: nicht angegeben – …“, „Wochenziel: 150 Minuten“ und „✅ Server erreichbar …“.
- [ ] ⚙️ Einstellungen: Gewicht `78,5`, Wochenziel `60` Minuten, „Speichern“ → „Gespeichert.“ Zurück im Profil stehen „Gewicht: 78,5 kg“ und „Wochenziel: 60 Minuten“.

## 2. Aktivität mit Timer

- [ ] ➕ Aktivität: „▶ Start“ ohne Sportart → „Bitte eine Sportart wählen.“
- [ ] 🏃 Joggen wählen, „▶ Start“ → die Zeit läuft, und der ➕-Tab zeigt sie statt „Aktivität“. Mindestens eine Minute laufen lassen.
- [ ] „⏸ Pause“ → die Zeit steht. „▶ Weiter“ → sie läuft weiter.
- [ ] App ganz schließen (aus den letzten Apps wischen) und neu öffnen → der Timer läuft mit der richtigen Zeit weiter.
- [ ] „⏹ Stopp“ → Formular mit Sportart, Datum, Uhrzeit und Dauer vom Timer.
- [ ] Ort per „📍 Aktueller Standort“ oder Tippen auf die kleine Karte, „🌎 Öffentlich“, eine Beschreibung, „Speichern“ → „Gespeichert! ~… kcal · +… Punkte (… min · +10 · +20 neue Sportart)“ und der Dialog „Neues Achievement!“ mit „🥉 Erste Schritte – Erste Aktivität eingetragen“. „Super!“ schließt ihn.
- [ ] Mit „← Zurück“ zum Timer, noch einmal „▶ Start“ und „⏹ Stopp“, dann „Verwerfen“ → Rückfrage „Aktivität verwerfen?“ → „Verwerfen“ → der ➕-Tab zeigt wieder „Aktivität“.

## 3. Nachtragen

- [ ] Im Timer „Ohne Timer nachtragen“, 🚴 Radfahren wählen, Dauer `abc`, „Speichern“ → „Bitte die Dauer in ganzen Minuten angeben.“
- [ ] Uhrzeit eine Stunde früher, Dauer `60`, „Speichern“ → die Meldung enthält „+20 neue Sportart“ und „+50 Wochenziel“, weil das Wochenziel von 60 Minuten damit erreicht ist.

## 4. Karte

- [ ] 🗺️ Karte: der eigene Marker in Akzentfarbe, die Marker von `lisa_k` grau.
- [ ] Einen Marker antippen → Sportart, Dauer, kcal, Datum, Beschreibung und Benutzername.
- [ ] „📍 Mein Standort“ → die App fragt nach der Standort-Berechtigung, danach springt die Karte zum Standort.

## 5. Erfolge

- [ ] 🏆 Übersicht: Wochenziel-Balken voll mit „… / 60 Minuten – geschafft! 🎉“, „🔥 1 Tag in Folge“, unter dem heutigen Wochentag ✅.
- [ ] Achievements: sechs Karten. „🥉 Erste Schritte“ ist freigeschaltet mit Datum, die anderen sind blass und zeigen „x / y“.
- [ ] Leaderboard: der eigene Platz ist farbig hinterlegt. „Monat“ und „Minuten“ umschalten → Liste und Einheit ändern sich.

## 6. Profil, Statistik und Verlauf

- [ ] 👤 Profil: „Diese Woche: 2 Aktivitäten · … min · ~… kcal“.
- [ ] 📊 Statistik: „Minuten pro Woche“ mit KW-Nummern, „Aktivitäten je Sportart“ mit 🏃 und 🚴, „kcal pro Monat (geschätzt)“ mit Monatskürzeln. „← Zurück“ führt ins Profil.
- [ ] 📜 Verlauf: beide Aktivitäten. Filter „🚴 Radfahren“ → nur noch eine. „7 Tage“, „30 Tage“ und „Alle“ lassen sich umschalten.
- [ ] Eine Aktivität antippen → Details mit Dauer, kcal und Punkten. „Aktivität löschen“ → Rückfrage „Aktivität löschen?“ → „Löschen“ → sie fehlt im Verlauf, und die Statistik zählt sie nicht mehr.

## 7. Passwort, Abmelden, Konto löschen

- [ ] ⚙️ Einstellungen: „Passwort ändern“ mit falschem bisherigen Passwort → „Das bisherige Passwort ist falsch.“ am Feld. Mit dem richtigen → „Passwort geändert.“
- [ ] „Abmelden“ → „Anmelden“. Mit dem neuen Passwort anmelden → klappt.
- [ ] „Konto löschen“ → Rückfrage „Konto löschen?“ → „Löschen“ → zurück bei „Anmelden“. Anmelden mit den alten Daten → „Benutzername/E-Mail oder Passwort ist falsch.“

## 8. Leere Zustände und Fehler (nicht vorführen)

- [ ] Frisch registriertes Konto: Die Statistik zeigt dreimal „Noch keine Daten. Trag eine Aktivität ein, dann erscheint hier ein Diagramm.“, der Verlauf „Noch keine Aktivitäten in diesem Zeitraum.“
- [ ] Nach dem Zurücksetzen der Datenbank (siehe README) mit einem neuen Konto ohne Aktivitäten: Die Karte zeigt „Noch keine Aktivitäten mit Ort. Wähl beim Speichern einen Ort, dann erscheint hier ein Marker.“, das Leaderboard „In diesem Zeitraum hat noch niemand Sport eingetragen.“
- [ ] Backend beenden, App ganz schließen und neu öffnen: Karte, Timer, „Ohne Timer nachtragen“, alle drei Erfolge-Reiter, Profil, Statistik, Verlauf und Einstellungen zeigen „Server nicht erreichbar“ mit „Erneut versuchen“.
- [ ] Backend wieder starten und überall „Erneut versuchen“ antippen → die Inhalte erscheinen.
````

- [ ] **Step 3: Texte gegen den Code prüfen**

Jeden ausgeschriebenen Text der Checkliste im Code suchen: in Android Studio **Edit → Find → Find in Files** über `app/src/main` und `backend/src/main`, für Claude mit dem Grep-Tool. Ein „…“ steht für Zahlen, Namen oder den Rest eines langen Textes.
Expected: Jeder Text kommt im Code vor. Weicht einer ab, die Checkliste an den Code anpassen.

- [ ] **Step 4: Commit**

```powershell
git add README.md docs/testcheckliste.md
git commit -m "Doku: Warum PostgreSQL, Datenschutz, Grenzen und Test-Checkliste" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Abschluss von M5

- [ ] Backend: `.\gradlew.bat test --rerun` im Ordner `backend` → 94 Tests grün.
- [ ] App: `.\gradlew.bat assembleDebug :app:testDebugUnitTest --rerun` im Wurzelordner → 90 Tests grün.
- [ ] Android Studio: **Sync Project with Gradle Files** (neue Bibliothek Vico).
- [ ] Backend neu gestartet, damit es `/stats` kennt.
- [ ] `docs/testcheckliste.md` einmal komplett auf dem Emulator durchgehen (von Hand).
- [ ] `git status --short` zeigt keine unbeabsichtigten Dateien.
