# M3 Timer und Karte – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktivitäten mit einem Start/Pause/Stopp-Timer messen, beim Speichern einen Ort auf einer kleinen Karte wählen und im Karten-Tab eigene und öffentliche Aktivitäten als Marker sehen.

**Architecture:** Das Backend bekommt `GET /api/map/activities` (eigene und öffentliche fremde Aktivitäten, nur mit Standort, neueste zuerst). Die App speichert den Timer als `TimerState` (Sportart, Start, Summe der Pausen, Beginn der aktuellen Pause) in DataStore. Die Dauer ist die reine Rechnung `jetzt − Start − Pausen`, darum übersteht der Timer einen Neustart ohne Foreground Service. Der ➕-Tab wird ein verschachtelter Graph aus Timer-Screen und Formular. Karte und Ortswahl nutzen MapLibre Compose mit dem OpenFreeMap-Style, den Standort liefert MapLibres `LocationManager`-Provider (ohne Google Play Services), gefragt wird erst beim Antippen.

**Tech Stack:** MapLibre Compose 0.12.1 (`org.maplibre.compose:maplibre-compose`, bringt MapLibre Android 12.0.1 und spatialk GeoJSON 0.6.0 mit), DataStore Preferences 1.2.1, Activity Result API (`RequestMultiplePermissions`), Material3 `ModalBottomSheet`, Navigation Compose 2.10.2 (verschachtelter Graph mit Routen-Parameter).

**Spec:** `docs/superpowers/specs/2026-10-20-touchgrass-design.md` (Abschnitte 2, 5, 6) · **API-Vertrag:** `docs/api.md`

## Global Constraints

- Alle Commits direkt auf `master`, keine Branches, nicht pushen. Commit-Nachrichten auf Deutsch mit Trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Die App bleibt bei Kotlin 2.2.10. MapLibre Compose wird fest auf **0.12.1** gepinnt: 0.13 und 0.14 brauchen Kotlin 2.3, ab 0.15 Kotlin 2.4. Android Studio schlägt neuere Versionen vor – nicht übernehmen.
- Kartenstil `https://tiles.openfreemap.org/styles/liberty` (OSM-Daten, kein API-Key). Ohne eigene Aktivität mit Ort startet die Karte über Deutschland (51,16° N, 10,45° O, Zoom 4,8).
- Der Standort ist freiwillig. `ACCESS_COARSE_LOCATION` und `ACCESS_FINE_LOCATION` stehen im Manifest und werden erst beim Antippen von „📍 Mein Standort“ bzw. „📍 Aktueller Standort“ angefragt. Keine Google Play Services.
- Timer ohne Foreground Service. `TimerStore` (DataStore `timer`) speichert Sportart, Startzeitpunkt, Summe der Pausen und ggf. den Beginn der aktuellen Pause. Angezeigte Dauer = `jetzt − Start − Pausen`. „⏹ Stopp“ pausiert und öffnet das Formular. Zurückgesetzt wird der Timer nur nach erfolgreichem Speichern oder nach „Verwerfen“ (mit Rückfrage).
- Dauer im Formular: auf ganze Minuten gerundet (ab 30 Sekunden aufrunden), mindestens 1.
- Karte: eigene Marker in der Akzentfarbe (`colorScheme.primary`), öffentliche fremde in Grau. Tippen öffnet ein Bottom Sheet mit Sportart, Benutzername, Datum, Dauer, kcal und Beschreibung.
- Testnamen deutsch in Backticks, Code englisch, UI-Texte deutsch, wenig Kommentare.

## Ausführungshinweise

- Vor Gradle: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'`. Backend-Befehle im Ordner `backend`, App-Befehle im Wurzelordner.
- **Für Claude:** Gradle nur im Terminal-Panel (`run_in_terminal`). Testergebnisse in `**/build/test-results/**/*.xml` prüfen.
- Der erste App-Build nach Task 3 lädt MapLibre mit nativen Bibliotheken und dauert länger.
- **Nach dem Plan in Android Studio „Sync Project with Gradle Files“** (neue Bibliothek), sonst ist der neue Code rot.
- M3 braucht keine Datenbank-Migration.

## Dateiübersicht

```
backend/src/main/kotlin/de/touchgrass/
├── activities/ActivityRepository.kt     + forMap(userId)
├── activities/ActivityService.kt        + map(userId)
└── routes/ActivityRoutes.kt             + GET /map/activities
backend/src/test/kotlin/de/touchgrass/
├── MapRoutesTest.kt
└── TestApp.kt                           activity() + latitude, longitude

app/src/main/
├── AndroidManifest.xml                  + Standort-Berechtigungen
└── java/de/touchgrass/
    ├── AppContainer.kt                  + timerStore
    ├── data/TimerState.kt               reine Timer-Rechnung
    ├── data/TimerStore.kt               DataStore „timer“
    ├── data/SportApi.kt                 + mapActivities()
    ├── ui/map/GeoPoint.kt               GeoPoint, Kartenstil, Start über Deutschland
    ├── ui/map/Markers.kt                GeoJSON-Marker, Tippen → Aktivität
    ├── ui/map/Location.kt               Berechtigung und Standort
    ├── ui/map/MapViewModel.kt, MapScreen.kt
    ├── ui/record/TimerDisplay.kt        formatElapsed, rememberNow, TimerTabLabel
    ├── ui/record/TimerViewModel.kt, TimerScreen.kt
    ├── ui/record/LocationPicker.kt      kleine Karte im Formular
    ├── ui/record/RecordViewModel.kt, RecordScreen.kt   + Timer-Werte, Ort, Verwerfen
    ├── ui/profile/ProfileScreen.kt      + extra-Slot (Server-Status zieht von der Karte hierher)
    └── ui/Navigation.kt                 Karte, ➕-Graph (Timer → Formular), Zeit im Tab

app/src/test/java/de/touchgrass/
├── testing/Fakes.kt                     + mapActivities, FakeTimerStore
├── data/TimerStateTest.kt, TimerStoreTest.kt, SportApiTest.kt (+1)
├── ui/map/MapDataTest.kt, MapViewModelTest.kt
└── ui/record/TimerDisplayTest.kt, TimerViewModelTest.kt, RecordViewModelTest.kt
```

---

### Task 1: Backend – Aktivitäten für die Karte

**Files:**
- Modify: `backend/src/test/kotlin/de/touchgrass/TestApp.kt` (`activity()` bekommt `latitude` und `longitude`)
- Create: `backend/src/test/kotlin/de/touchgrass/MapRoutesTest.kt`
- Modify: `backend/src/main/kotlin/de/touchgrass/activities/ActivityRepository.kt`, `.../activities/ActivityService.kt`, `.../routes/ActivityRoutes.kt`
- Modify: `docs/api.md`

**Interfaces:**
- Consumes: `ActivityRepository.withUser`, `ResultRow.toRecord()`, `ActivityService.toDto(viewerId)` (M2)
- Produces: `ActivityRepository.forMap(userId: Long): List<ActivityRecord>`; `ActivityService.map(userId: Long): List<ActivityDto>`; `GET /api/map/activities` → Liste von `ActivityDto`, nur mit Standort, eigene und öffentliche fremde, neueste zuerst. Test-Helfer `activity(..., latitude: Double? = null, longitude: Double? = null)`.

- [ ] **Step 1: Test-Helfer erweitern**

In `backend/src/test/kotlin/de/touchgrass/TestApp.kt` die Funktion `activity` ersetzen:
```kotlin
fun activity(
    sportCode: String = "RUNNING",
    startedAt: String = "2026-10-05T09:30:00+02:00",
    durationMin: Int = 48,
    visibility: Visibility = Visibility.PRIVATE,
    latitude: Double? = null,
    longitude: Double? = null,
) = CreateActivityRequest(
    sportCode = sportCode,
    startedAt = startedAt,
    durationMin = durationMin,
    latitude = latitude,
    longitude = longitude,
    visibility = visibility,
)
```

- [ ] **Step 2: Failing Test schreiben**

`backend/src/test/kotlin/de/touchgrass/MapRoutesTest.kt`:
```kotlin
package de.touchgrass

import de.touchgrass.activities.ActivityDto
import de.touchgrass.activities.Visibility
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MapRoutesTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    @Test
    fun `Karte zeigt eigene und öffentliche fremde Aktivitäten mit Standort, neueste zuerst`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa").token
        val ownPrivate = client.createActivity(
            max,
            activity(startedAt = "2026-10-05T09:00:00+02:00", latitude = 48.1642, longitude = 11.6056),
        ).activity.id
        client.createActivity(max, activity(startedAt = "2026-10-06T09:00:00+02:00"))
        val lisaPublic = client.createActivity(
            lisa,
            activity(startedAt = "2026-10-07T09:00:00+02:00", visibility = Visibility.PUBLIC, latitude = 52.52, longitude = 13.405),
        ).activity.id
        client.createActivity(lisa, activity(startedAt = "2026-10-08T09:00:00+02:00", latitude = 50.11, longitude = 8.68))
        client.createActivity(lisa, activity(startedAt = "2026-10-09T09:00:00+02:00", visibility = Visibility.PUBLIC))

        val list = client.get("/api/map/activities") { bearerAuth(max) }.body<List<ActivityDto>>()

        assertEquals(listOf(lisaPublic, ownPrivate), list.map { it.id })
        assertEquals(listOf(false, true), list.map { it.mine })
        assertEquals(listOf("lisa", "max_m"), list.map { it.username })
        assertEquals(52.52, list.first().latitude)
        assertEquals(13.405, list.first().longitude)
    }

    @Test
    fun `Karte braucht eine Anmeldung`() = testApplication {
        val client = startApp()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/map/activities").status)
    }
}
```

- [ ] **Step 3: Test laufen lassen, er muss fehlschlagen**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: FAIL. Beide `MapRoutesTest` scheitern, weil es den Pfad noch nicht gibt (`404` statt Liste bzw. statt `401`). Alle anderen Tests bleiben grün.

- [ ] **Step 4: Abfrage, Service und Route ergänzen**

In `backend/src/main/kotlin/de/touchgrass/activities/ActivityRepository.kt` die Imports ergänzen (alphabetisch einsortiert):
```kotlin
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.or
```
und direkt vor `fun inRange(` einfügen:
```kotlin
    fun forMap(userId: Long): List<ActivityRecord> = transaction(database) {
        withUser.selectAll()
            .where {
                ActivitiesTable.latitude.isNotNull() and
                    ((ActivitiesTable.userId eq userId) or (ActivitiesTable.visibility eq Visibility.PUBLIC))
            }
            .orderBy(ActivitiesTable.startedAt to SortOrder.DESC, ActivitiesTable.id to SortOrder.DESC)
            .map { it.toRecord() }
    }

```

In `backend/src/main/kotlin/de/touchgrass/activities/ActivityService.kt` direkt vor `fun get(userId: Long, id: Long)` einfügen:
```kotlin
    fun map(userId: Long): List<ActivityDto> = activities.forMap(userId).map { it.toDto(viewerId = userId) }

```

In `backend/src/main/kotlin/de/touchgrass/routes/ActivityRoutes.kt` innerhalb von `authenticate(AUTH_JWT) { … }` nach dem Block `route("/activities") { … }` einfügen, sodass das Ende so aussieht:
```kotlin
            delete("/{id}") {
                service.delete(call.userId(), call.activityId())
                call.respond(HttpStatusCode.NoContent)
            }
        }
        get("/map/activities") {
            call.respond(service.map(call.userId()))
        }
    }
}
```

- [ ] **Step 5: API-Vertrag ergänzen**

In `docs/api.md` unter `### GET /map/activities` die Zeile
```
Eigene Aktivitäten und öffentliche Aktivitäten anderer, jeweils nur mit Standort.
```
ersetzen durch
```
Eigene Aktivitäten und öffentliche Aktivitäten anderer, jeweils nur mit Standort, neueste zuerst.
```

- [ ] **Step 6: Tests laufen lassen, sie müssen grün sein**

Run (Ordner `backend`): `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`. Insgesamt 58 Backend-Tests grün, davon neu: `MapRoutesTest` 2.

- [ ] **Step 7: Commit**

```powershell
git add backend/src docs/api.md
git commit -m "Aktivitäten mit Standort für die Karte" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: App – Timer-Logik und TimerStore

**Files:**
- Create: `app/src/main/java/de/touchgrass/data/TimerState.kt`, `app/src/main/java/de/touchgrass/data/TimerStore.kt`
- Modify: `app/src/main/java/de/touchgrass/AppContainer.kt`, `app/src/test/java/de/touchgrass/testing/Fakes.kt`
- Test: `app/src/test/java/de/touchgrass/data/TimerStateTest.kt`, `app/src/test/java/de/touchgrass/data/TimerStoreTest.kt`

**Interfaces:**
- Consumes: DataStore Preferences (wie `TokenStore` aus M1)
- Produces: `data class TimerState(sportCode: String, startedAt: Instant, pausedTotal: Duration = Duration.ZERO, pausedSince: Instant? = null)` mit `isPaused: Boolean`, `elapsed(now: Instant): Duration`, `pause(now: Instant): TimerState`, `resume(now: Instant): TimerState`, `durationMinutes(now: Instant): Int` (alles `java.time`); `interface TimerStore { val state: Flow<TimerState?>; suspend fun save(state: TimerState); suspend fun clear() }`; `class DataStoreTimerStore(dataStore: DataStore<Preferences>) : TimerStore`; `val Context.timerDataStore`; `AppContainer.timerStore: TimerStore`; im Test `class FakeTimerStore(initial: TimerState? = null)` mit `val current: MutableStateFlow<TimerState?>`.

- [ ] **Step 1: Failing Tests schreiben**

`app/src/test/java/de/touchgrass/data/TimerStateTest.kt`:
```kotlin
package de.touchgrass.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class TimerStateTest {
    private val start = Instant.parse("2026-10-05T08:00:00Z")

    private fun at(minutes: Long, seconds: Long = 0): Instant = start.plusSeconds(minutes * 60 + seconds)

    @Test
    fun `laufender Timer zählt die Zeit seit dem Start`() {
        val timer = TimerState("RUNNING", start)

        assertFalse(timer.isPaused)
        assertEquals(Duration.ofMinutes(12), timer.elapsed(at(12)))
    }

    @Test
    fun `Pausen zählen nicht mit`() {
        val paused = TimerState("RUNNING", start).pause(at(10)).resume(at(15)).pause(at(20))

        assertTrue(paused.isPaused)
        assertEquals(Duration.ofMinutes(15), paused.elapsed(at(45)))
        assertEquals(Duration.ofMinutes(25), paused.resume(at(30)).elapsed(at(40)))
    }

    @Test
    fun `doppeltes Pausieren oder Weitermachen ändert nichts`() {
        val running = TimerState("RUNNING", start)
        val paused = running.pause(at(10))

        assertEquals(paused, paused.pause(at(12)))
        assertEquals(running, running.resume(at(12)))
    }

    @Test
    fun `zurückgestellte Uhr ergibt keine negative Dauer`() {
        assertEquals(Duration.ZERO, TimerState("RUNNING", start).elapsed(start.minusSeconds(60)))
    }

    @Test
    fun `Minuten für das Formular sind gerundet und mindestens 1`() {
        val timer = TimerState("RUNNING", start)

        assertEquals(1, timer.durationMinutes(at(0, 10)))
        assertEquals(44, timer.durationMinutes(at(44, 29)))
        assertEquals(45, timer.durationMinutes(at(44, 30)))
    }
}
```

`app/src/test/java/de/touchgrass/data/TimerStoreTest.kt`:
```kotlin
package de.touchgrass.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

// Eine echte DataStore-Datei lässt sich unter Windows im Test nicht zweimal ersetzen, darum liegen die Werte im Speicher.
private class InMemoryDataStore : DataStore<Preferences> {
    private val current = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = current
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(current.value).also { current.value = it }
}

class TimerStoreTest {
    @Test
    fun `Timer wird gespeichert, wieder gelesen und gelöscht`() = runBlocking {
        val store = DataStoreTimerStore(InMemoryDataStore())
        val paused = TimerState(
            sportCode = "RUNNING",
            startedAt = Instant.parse("2026-10-05T08:00:00Z"),
            pausedTotal = Duration.ofMinutes(3),
            pausedSince = Instant.parse("2026-10-05T08:30:00Z"),
        )

        assertNull(store.state.first())

        store.save(paused)
        assertEquals(paused, store.state.first())

        val running = paused.resume(Instant.parse("2026-10-05T08:35:00Z"))
        store.save(running)
        assertEquals(running, store.state.first())

        store.clear()
        assertNull(store.state.first())
    }
}
```

- [ ] **Step 2: Tests laufen lassen, sie müssen fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'TimerState'` und `'DataStoreTimerStore'`.

- [ ] **Step 3: TimerState und TimerStore schreiben**

`app/src/main/java/de/touchgrass/data/TimerState.kt`:
```kotlin
package de.touchgrass.data

import java.time.Duration
import java.time.Instant

data class TimerState(
    val sportCode: String,
    val startedAt: Instant,
    val pausedTotal: Duration = Duration.ZERO,
    val pausedSince: Instant? = null,
) {
    val isPaused: Boolean get() = pausedSince != null

    fun elapsed(now: Instant): Duration {
        val elapsed = Duration.between(startedAt, pausedSince ?: now) - pausedTotal
        return if (elapsed.isNegative) Duration.ZERO else elapsed
    }

    fun pause(now: Instant): TimerState = if (isPaused) this else copy(pausedSince = now)

    fun resume(now: Instant): TimerState {
        val since = pausedSince ?: return this
        return copy(pausedTotal = pausedTotal + Duration.between(since, now), pausedSince = null)
    }

    fun durationMinutes(now: Instant): Int = maxOf(1, ((elapsed(now).seconds + 30) / 60).toInt())
}
```

`app/src/main/java/de/touchgrass/data/TimerStore.kt`:
```kotlin
package de.touchgrass.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

val Context.timerDataStore: DataStore<Preferences> by preferencesDataStore(name = "timer")

interface TimerStore {
    val state: Flow<TimerState?>
    suspend fun save(state: TimerState)
    suspend fun clear()
}

class DataStoreTimerStore(private val dataStore: DataStore<Preferences>) : TimerStore {
    override val state: Flow<TimerState?> = dataStore.data.map { prefs ->
        val sportCode = prefs[SPORT_CODE] ?: return@map null
        val startedAt = prefs[STARTED_AT] ?: return@map null
        TimerState(
            sportCode = sportCode,
            startedAt = Instant.ofEpochMilli(startedAt),
            pausedTotal = Duration.ofMillis(prefs[PAUSED_TOTAL] ?: 0),
            pausedSince = prefs[PAUSED_SINCE]?.let(Instant::ofEpochMilli),
        )
    }

    override suspend fun save(state: TimerState) {
        dataStore.edit { prefs ->
            prefs[SPORT_CODE] = state.sportCode
            prefs[STARTED_AT] = state.startedAt.toEpochMilli()
            prefs[PAUSED_TOTAL] = state.pausedTotal.toMillis()
            val pausedSince = state.pausedSince
            if (pausedSince != null) prefs[PAUSED_SINCE] = pausedSince.toEpochMilli() else prefs.remove(PAUSED_SINCE)
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val SPORT_CODE = stringPreferencesKey("sport_code")
        val STARTED_AT = longPreferencesKey("started_at")
        val PAUSED_TOTAL = longPreferencesKey("paused_total")
        val PAUSED_SINCE = longPreferencesKey("paused_since")
    }
}
```

- [ ] **Step 4: TimerStore im AppContainer und als Fake bereitstellen**

`app/src/main/java/de/touchgrass/AppContainer.kt` komplett:
```kotlin
package de.touchgrass

import android.content.Context
import de.touchgrass.data.AuthInterceptor
import de.touchgrass.data.AuthSession
import de.touchgrass.data.DataStoreTimerStore
import de.touchgrass.data.DataStoreTokenStore
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerStore
import de.touchgrass.data.authDataStore
import de.touchgrass.data.createSportApi
import de.touchgrass.data.timerDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class AppContainer(context: Context, baseUrl: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val session = AuthSession(DataStoreTokenStore(context.authDataStore), scope)

    val timerStore: TimerStore = DataStoreTimerStore(context.timerDataStore)

    val api: SportApi = createSportApi(
        baseUrl,
        OkHttpClient.Builder().addInterceptor(AuthInterceptor(session)).build(),
    )
}
```

In `app/src/test/java/de/touchgrass/testing/Fakes.kt` die Imports ergänzen:
```kotlin
import de.touchgrass.data.TimerState
import de.touchgrass.data.TimerStore
```
und direkt nach der Klasse `FakeTokenStore` einfügen:
```kotlin
class FakeTimerStore(initial: TimerState? = null) : TimerStore {
    val current = MutableStateFlow(initial)
    override val state: Flow<TimerState?> = current
    override suspend fun save(state: TimerState) { current.value = state }
    override suspend fun clear() { current.value = null }
}
```

- [ ] **Step 5: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Insgesamt 50 App-Tests grün, davon neu: `TimerStateTest` 5, `TimerStoreTest` 1.

- [ ] **Step 6: Commit**

```powershell
git add app/src
git commit -m "Timer-Zustand mit Pausen, gespeichert in DataStore" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: App – Karten-Tab

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts` (MapLibre Compose 0.12.1)
- Modify: `app/src/main/AndroidManifest.xml` (Standort-Berechtigungen)
- Modify: `app/src/main/java/de/touchgrass/data/SportApi.kt` (`mapActivities`)
- Create: `app/src/main/java/de/touchgrass/ui/map/GeoPoint.kt`, `.../ui/map/Markers.kt`, `.../ui/map/Location.kt`, `.../ui/map/MapViewModel.kt`, `.../ui/map/MapScreen.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/profile/ProfileScreen.kt` (`extra`-Slot), `app/src/main/java/de/touchgrass/ui/Navigation.kt` (Karte statt Platzhalter, Server-Status ins Profil)
- Modify: `README.md`
- Test: `app/src/test/java/de/touchgrass/data/SportApiTest.kt` (+1), `app/src/test/java/de/touchgrass/testing/Fakes.kt`, `app/src/test/java/de/touchgrass/ui/map/MapDataTest.kt`, `app/src/test/java/de/touchgrass/ui/map/MapViewModelTest.kt`

**Interfaces:**
- Consumes: `SportApi`, `ActivityDto`, `SportDto`, `UiState`, `apiCall`, `labelFor`, `startedAtLabel` (M2), `ServerStatusCard` (M0)
- Produces: `SportApi.mapActivities(): List<ActivityDto>`; `const val MAP_STYLE_URL`; `val GERMANY: CameraPosition`; `data class GeoPoint(latitude: Double, longitude: Double)`; `Position.toGeoPoint(): GeoPoint` (rechnet Längengrade außerhalb ±180 zurück); `GeoPoint.toPosition(): Position`; `ActivityDto.geoPoint(): GeoPoint?`; `List<ActivityDto>.toFeatureCollection(): FeatureCollection<Point, JsonObject?>` (Eigenschaft `"id"` als Text); `@Composable @MaplibreComposable fun ActivityMarkers(id: String, activities: List<ActivityDto>, color: Color, onClick: (ActivityDto) -> Unit)`; `@Composable @MaplibreComposable fun PointMarker(id: String, point: GeoPoint, color: Color)`; `const val LOCATION_DENIED`; `@Composable fun rememberLocationRequest(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit`; `@Composable fun rememberUserLocation(): UserLocationState` (nur nach erteilter Berechtigung aufrufen); `data class MapContent(activities, sports)`; `MapViewModel(api)` mit `state: StateFlow<UiState<MapContent>>` und `refresh()`; `startPosition(activities): CameraPosition`; `@Composable fun MapScreen(viewModel: MapViewModel)`; `ProfileScreen(..., extra: @Composable () -> Unit = {})`.

- [ ] **Step 1: MapLibre Compose eintragen**

In `gradle/libs.versions.toml` unter `[versions]` nach `datastore = "1.2.1"`:
```toml
maplibreCompose = "0.12.1"
```
und unter `[libraries]` als erste Zeile:
```toml
maplibre-compose = { group = "org.maplibre.compose", name = "maplibre-compose", version.ref = "maplibreCompose" }
```

In `app/build.gradle.kts` im Block `dependencies` nach `implementation(libs.kotlinx.serialization.json)`:
```kotlin
    implementation(libs.maplibre.compose)
```

- [ ] **Step 2: Failing Tests schreiben**

In `app/src/test/java/de/touchgrass/testing/Fakes.kt` in `FakeSportApi` nach `deleteActivity` einfügen:
```kotlin
    override suspend fun mapActivities(): List<ActivityDto> = notExpected("mapActivities")
```

In `app/src/test/java/de/touchgrass/data/SportApiTest.kt` als letzten Test einfügen:
```kotlin
    @Test
    fun `mapActivities liest Aktivitäten mit Standort`() = runBlocking {
        enqueueJson(200, """[{"id":7,"username":"lisa","mine":false,"sportCode":"CYCLING","startedAt":"2026-10-05T09:30:00+02:00","durationMin":60,"caloriesKcal":560,"points":90,"latitude":52.52,"longitude":13.405,"visibility":"PUBLIC"}]""")

        val activities = api.mapActivities()

        assertEquals("/api/map/activities", server.takeRequest().path)
        assertEquals(52.52, activities.single().latitude)
        assertEquals(13.405, activities.single().longitude)
    }
```

`app/src/test/java/de/touchgrass/ui/map/MapDataTest.kt`:
```kotlin
package de.touchgrass.ui.map

import de.touchgrass.testing.testActivity
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.spatialk.geojson.Position

class MapDataTest {
    private val own = testActivity(id = 1).copy(latitude = 48.1642, longitude = 11.6056)
    private val other = testActivity(id = 2).copy(mine = false, username = "lisa", latitude = 52.52, longitude = 13.405)

    @Test
    fun `Längengrad aus einer Kopie der Welt wird zurückgerechnet`() {
        assertEquals(GeoPoint(48.1642, 11.6056), Position(longitude = 11.6056, latitude = 48.1642).toGeoPoint())
        assertEquals(10.0, Position(longitude = 370.0, latitude = 0.0).toGeoPoint().longitude, 1e-9)
        assertEquals(170.0, Position(longitude = -190.0, latitude = 0.0).toGeoPoint().longitude, 1e-9)
    }

    @Test
    fun `nur Aktivitäten mit Standort werden zu Markern mit ihrer ID`() {
        val features = listOf(own, testActivity(id = 3)).toFeatureCollection().features

        assertEquals(1, features.size)
        assertEquals(Position(longitude = 11.6056, latitude = 48.1642), features.single().geometry.coordinates)
        assertEquals("1", features.single().properties?.get("id")?.jsonPrimitive?.content)
    }

    @Test
    fun `Karte startet über der letzten eigenen Aktivität, sonst über Deutschland`() {
        assertEquals(Position(longitude = 11.6056, latitude = 48.1642), startPosition(listOf(other, own)).target)
        assertEquals(GERMANY, startPosition(listOf(other)))
        assertEquals(GERMANY, startPosition(emptyList()))
    }
}
```

`app/src/test/java/de/touchgrass/ui/map/MapViewModelTest.kt`:
```kotlin
package de.touchgrass.ui.map

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

class MapViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `lädt Aktivitäten für die Karte und die Sportarten`() {
        val activities = listOf(testActivity(id = 1).copy(latitude = 48.1642, longitude = 11.6056))
        val api = object : FakeSportApi() {
            override suspend fun mapActivities(): List<ActivityDto> = activities
            override suspend fun sports(): List<SportDto> = testSports
        }
        val viewModel = MapViewModel(api)
        assertEquals(UiState.Loading, viewModel.state.value)

        viewModel.refresh()

        assertEquals(UiState.Content(MapContent(activities, testSports)), viewModel.state.value)
    }

    @Test
    fun `nicht erreichbarer Server wird angezeigt`() {
        val api = object : FakeSportApi() {
            override suspend fun mapActivities(): List<ActivityDto> = throw IOException("kein Netz")
        }
        val viewModel = MapViewModel(api)

        viewModel.refresh()

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value)
    }
}
```

- [ ] **Step 3: Tests laufen lassen, sie müssen fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren, u. a. `'mapActivities' overrides nothing`, `Unresolved reference 'toGeoPoint'`, `'GeoPoint'`, `'MapViewModel'`.

- [ ] **Step 4: API, Kartendaten und ViewModel schreiben**

In `app/src/main/java/de/touchgrass/data/SportApi.kt` im Interface `SportApi` nach `deleteActivity` einfügen:
```kotlin

    @GET("map/activities")
    suspend fun mapActivities(): List<ActivityDto>
```

`app/src/main/java/de/touchgrass/ui/map/GeoPoint.kt`:
```kotlin
package de.touchgrass.ui.map

import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

val GERMANY = CameraPosition(target = Position(longitude = 10.45, latitude = 51.16), zoom = 4.8)

data class GeoPoint(val latitude: Double, val longitude: Double)

// Tippt man auf eine Kopie der Welt neben der eigentlichen Karte, liefert MapLibre Längengrade außerhalb von ±180.
fun Position.toGeoPoint(): GeoPoint =
    GeoPoint(latitude, if (longitude in -180.0..180.0) longitude else (longitude + 180).mod(360.0) - 180)

fun GeoPoint.toPosition(): Position = Position(longitude = longitude, latitude = latitude)
```

`app/src/main/java/de/touchgrass/ui/map/Markers.kt`:
```kotlin
package de.touchgrass.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.touchgrass.data.dto.ActivityDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point

fun ActivityDto.geoPoint(): GeoPoint? =
    if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null

// Die ID steht als Text in den Eigenschaften, weil MapLibre Zahlen beim Antippen als Kommazahl zurückgibt.
fun List<ActivityDto>.toFeatureCollection(): FeatureCollection<Point, JsonObject?> =
    FeatureCollection(
        mapNotNull { activity ->
            activity.geoPoint()?.let { point ->
                Feature(geometry = Point(point.toPosition()), properties = buildJsonObject { put("id", activity.id.toString()) })
            }
        }
    )

@Composable
@MaplibreComposable
fun ActivityMarkers(id: String, activities: List<ActivityDto>, color: Color, onClick: (ActivityDto) -> Unit) {
    val data = remember(activities) { GeoJsonData.Features(activities.toFeatureCollection()) }
    val source = rememberGeoJsonSource(data)
    CircleLayer(
        id = id,
        source = source,
        color = const(color),
        radius = const(8.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
        onClick = { features ->
            val clickedId = features.firstOrNull()?.properties?.get("id")?.jsonPrimitive?.content
            val activity = activities.firstOrNull { it.id.toString() == clickedId }
            if (activity != null) {
                onClick(activity)
                ClickResult.Consume
            } else {
                ClickResult.Pass
            }
        },
    )
}

@Composable
@MaplibreComposable
fun PointMarker(id: String, point: GeoPoint, color: Color) {
    val data = remember(point) { GeoJsonData.Features(Point(point.toPosition())) }
    val source = rememberGeoJsonSource(data)
    CircleLayer(
        id = id,
        source = source,
        color = const(color),
        radius = const(8.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp),
    )
}
```

`app/src/main/java/de/touchgrass/ui/map/MapViewModel.kt`:
```kotlin
package de.touchgrass.ui.map

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
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition

data class MapContent(val activities: List<ActivityDto>, val sports: List<SportDto>)

class MapViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow<UiState<MapContent>>(UiState.Loading)
    val state: StateFlow<UiState<MapContent>> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = try {
                UiState.Content(MapContent(apiCall { api.mapActivities() }, apiCall { api.sports() }))
            } catch (e: ApiException) {
                UiState.Error(e.message)
            }
        }
    }
}

/** Die Liste kommt neueste zuerst, also steht die letzte eigene Aktivität vorne. */
fun startPosition(activities: List<ActivityDto>): CameraPosition =
    activities.firstNotNullOfOrNull { if (it.mine) it.geoPoint() else null }
        ?.let { CameraPosition(target = it.toPosition(), zoom = 13.0) }
        ?: GERMANY
```

- [ ] **Step 5: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Insgesamt 56 App-Tests grün, davon neu: `SportApiTest` +1, `MapDataTest` 3, `MapViewModelTest` 2.

- [ ] **Step 6: Karten-Screen, Standort und Navigation**

In `app/src/main/AndroidManifest.xml` nach der `INTERNET`-Berechtigung:
```xml
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

`app/src/main/java/de/touchgrass/ui/map/Location.kt`:
```kotlin
package de.touchgrass.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.maplibre.compose.location.UserLocationState
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberUserLocationState

const val LOCATION_DENIED = "Ohne Standort-Berechtigung geht das nicht. Du kannst sie in den App-Einstellungen erlauben."

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private fun hasLocationPermission(context: Context): Boolean =
    LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

@Composable
fun rememberLocationRequest(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val granted by rememberUpdatedState(onGranted)
    val denied by rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) granted() else denied()
    }
    return { if (hasLocationPermission(context)) granted() else launcher.launch(LOCATION_PERMISSIONS) }
}

// Nur aufrufen, nachdem rememberLocationRequest die Berechtigung bestätigt hat.
@SuppressLint("MissingPermission")
@Composable
fun rememberUserLocation(): UserLocationState = rememberUserLocationState(rememberDefaultLocationProvider())
```

`app/src/main/java/de/touchgrass/ui/map/MapScreen.kt`:
```kotlin
package de.touchgrass.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.ui.UiState
import de.touchgrass.ui.activities.labelFor
import de.touchgrass.ui.activities.startedAtLabel
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle

@Composable
fun MapScreen(viewModel: MapViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    when (val s = state) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::refresh) { Text("Erneut versuchen") }
        }
        is UiState.Content -> ActivityMap(s.data)
    }
}

@Composable
private fun ActivityMap(content: MapContent) {
    val cameraState = rememberCameraState(firstPosition = startPosition(content.activities))
    var selected by remember { mutableStateOf<ActivityDto?>(null) }
    var showMyLocation by remember { mutableStateOf(false) }
    var centerRequests by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf<String?>(null) }
    val requestLocation = rememberLocationRequest(
        onGranted = { hint = null; showMyLocation = true; centerRequests++ },
        onDenied = { hint = LOCATION_DENIED },
    )
    val ownColor = MaterialTheme.colorScheme.primary
    val (own, others) = content.activities.partition { it.mine }

    Box(Modifier.fillMaxSize()) {
        val userLocation = if (showMyLocation) rememberUserLocation() else null
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
            cameraState = cameraState,
        ) {
            ActivityMarkers("others", others, Color.Gray) { selected = it }
            ActivityMarkers("own", own, ownColor) { selected = it }
            userLocation?.let { LocationPuck("me", it, cameraState) }
        }

        val myPosition = userLocation?.location?.position
        if (myPosition != null) {
            LaunchedEffect(centerRequests) { cameraState.animateTo(CameraPosition(target = myPosition, zoom = 14.0)) }
        }

        hint?.let {
            Card(Modifier.align(Alignment.TopCenter).padding(16.dp)) { Text(it, Modifier.padding(16.dp)) }
        }
        ExtendedFloatingActionButton(
            onClick = requestLocation,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text("📍 Mein Standort") }
    }

    selected?.let { ActivitySheet(it, content.sports, onDismiss = { selected = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivitySheet(activity: ActivityDto, sports: List<SportDto>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(sports.labelFor(activity.sportCode), style = MaterialTheme.typography.headlineSmall)
            Text(if (activity.mine) "von dir" else "von ${activity.username}")
            Text(activity.startedAtLabel())
            Text("${activity.durationMin} Minuten · ~${activity.caloriesKcal} kcal")
            activity.description?.let { Text("„$it“") }
        }
    }
}
```

In `app/src/main/java/de/touchgrass/ui/profile/ProfileScreen.kt` die Signatur ersetzen:
```kotlin
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
```
und nach dem letzten `ListItem` („⚙️ Einstellungen“), noch innerhalb der `Column`, `extra()` aufrufen:
```kotlin
        ListItem(
            headlineContent = { Text("⚙️ Einstellungen") },
            modifier = Modifier.clickable(onClick = onOpenSettings),
        )
        extra()
    }
}
```

In `app/src/main/java/de/touchgrass/ui/Navigation.kt` die Imports ergänzen:
```kotlin
import de.touchgrass.ui.map.MapScreen
import de.touchgrass.ui.map.MapViewModel
```
den Platzhalter der Karte
```kotlin
            composable<MapRoute> {
                PlaceholderScreen("Karte", "Die Karte kommt in M3.") {
                    ServerStatusCard(container.api)
                }
            }
```
ersetzen durch
```kotlin
            composable<MapRoute> {
                MapScreen(viewModel = viewModel { MapViewModel(container.api) })
            }
```
und beim Profil den Server-Status anhängen:
```kotlin
                composable<ProfileRoute> {
                    ProfileScreen(
                        viewModel = viewModel { ProfileViewModel(container.api) },
                        onOpenHistory = { navController.navigate(HistoryRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        extra = { ServerStatusCard(container.api) },
                    )
                }
```

- [ ] **Step 7: Bauen und alle Tests laufen lassen**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 56 App-Tests grün.

- [ ] **Step 8: README ergänzen**

In `README.md` im Abschnitt „App starten“ direkt vor der Zeile „Unit-Tests der App: …“ einfügen:
```markdown
**Karte und Standort:** Die Kartenbilder kommen von OpenFreeMap aus dem Internet, ohne Netz bleibt die Karte leer. Nach dem Standort fragt die App erst, wenn man „📍 Mein Standort“ oder im Formular „📍 Aktueller Standort“ antippt. Im Emulator stellt man den Standort unter **⋮ (Extended Controls) → Location** ein.

```

- [ ] **Step 9: Commit**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src README.md
git commit -m "Karten-Tab mit Markern, Details und eigenem Standort" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: App – Timer-Screen im ➕-Tab

**Files:**
- Create: `app/src/main/java/de/touchgrass/ui/record/TimerDisplay.kt`, `.../ui/record/TimerViewModel.kt`, `.../ui/record/TimerScreen.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/Navigation.kt` (➕-Graph, Zeit im Tab)
- Test: `app/src/test/java/de/touchgrass/ui/record/TimerDisplayTest.kt`, `app/src/test/java/de/touchgrass/ui/record/TimerViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerState`, `TimerStore`, `FakeTimerStore`, `AppContainer.timerStore` (Task 2); `SportApi.sports()`, `apiCall`, `RecordScreen`, `RecordViewModel(api)` (M2)
- Produces: `formatElapsed(duration: Duration): String` („00:12:05“); `@Composable fun rememberNow(ticking: Boolean): Instant` (tickt alle 500 ms, solange `ticking`); `@Composable fun TimerTabLabel(timerStore: TimerStore, label: String)`; `data class TimerUiState(sports, sportCode, timer: TimerState?, error, openForm)`; `TimerViewModel(api, timerStore, clock = Clock.systemDefaultZone())` mit `state`, `loadSports()`, `onSportChange(code)`, `start()`, `pause()`, `resume()`, `stop()`, `onFormOpened()`; `@Composable fun TimerScreen(viewModel, onOpenForm: () -> Unit, onRecordManually: () -> Unit)`; Routen `RecordGraph`, `TimerRoute`, `RecordFormRoute(fromTimer: Boolean)` (ersetzen `RecordRoute`).

Nach diesem Task öffnet „⏹ Stopp“ schon das Formular, die Timer-Werte übernimmt es aber erst mit Task 5.

- [ ] **Step 1: Failing Tests schreiben**

`app/src/test/java/de/touchgrass/ui/record/TimerDisplayTest.kt`:
```kotlin
package de.touchgrass.ui.record

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class TimerDisplayTest {
    @Test
    fun `Zeit wird als Stunden, Minuten und Sekunden angezeigt`() {
        assertEquals("00:00:00", formatElapsed(Duration.ZERO))
        assertEquals("00:12:05", formatElapsed(Duration.ofSeconds(12 * 60 + 5)))
        assertEquals("01:30:00", formatElapsed(Duration.ofMinutes(90)))
        assertEquals("26:00:59", formatElapsed(Duration.ofHours(26).plusSeconds(59)))
    }
}
```

`app/src/test/java/de/touchgrass/ui/record/TimerViewModelTest.kt`:
```kotlin
package de.touchgrass.ui.record

import de.touchgrass.data.TimerState
import de.touchgrass.data.dto.SportDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.FakeTimerStore
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.testSports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class TimerViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val now = Instant.parse("2026-10-05T08:15:30Z")
    private val clock = Clock.fixed(now, ZoneId.of("Europe/Berlin"))

    private val api = object : FakeSportApi() {
        override suspend fun sports(): List<SportDto> = testSports
    }

    @Test
    fun `Start ohne Sportart zeigt einen Hinweis`() {
        val store = FakeTimerStore()
        val viewModel = TimerViewModel(api, store, clock)

        viewModel.start()

        assertEquals("Bitte eine Sportart wählen.", viewModel.state.value.error)
        assertNull(store.current.value)
    }

    @Test
    fun `Start und Pause landen im TimerStore`() {
        val store = FakeTimerStore()
        val viewModel = TimerViewModel(api, store, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.start()
        assertEquals(TimerState("RUNNING", now), store.current.value)
        assertEquals(TimerState("RUNNING", now), viewModel.state.value.timer)

        viewModel.pause()
        assertEquals(now, store.current.value?.pausedSince)

        viewModel.resume()
        assertEquals(false, store.current.value?.isPaused)
    }

    @Test
    fun `gespeicherter Timer erscheint nach einem Neustart`() {
        val running = TimerState("CYCLING", now.minus(Duration.ofMinutes(10)))

        val viewModel = TimerViewModel(api, FakeTimerStore(running), clock)

        assertEquals(running, viewModel.state.value.timer)
        assertEquals("CYCLING", viewModel.state.value.sportCode)
        assertEquals(testSports, viewModel.state.value.sports)
    }

    @Test
    fun `Stopp pausiert den Timer und öffnet das Formular`() {
        val store = FakeTimerStore(TimerState("RUNNING", now.minus(Duration.ofMinutes(10))))
        val viewModel = TimerViewModel(api, store, clock)

        viewModel.stop()

        assertEquals(now, store.current.value?.pausedSince)
        assertTrue(viewModel.state.value.openForm)
        viewModel.onFormOpened()
        assertFalse(viewModel.state.value.openForm)
    }
}
```

- [ ] **Step 2: Tests laufen lassen, sie müssen fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'formatElapsed'` und `'TimerViewModel'`.

- [ ] **Step 3: Zeitanzeige und TimerViewModel schreiben**

`app/src/main/java/de/touchgrass/ui/record/TimerDisplay.kt`:
```kotlin
package de.touchgrass.ui.record

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.touchgrass.data.TimerStore
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.Locale

fun formatElapsed(duration: Duration): String {
    val seconds = duration.seconds
    return "%02d:%02d:%02d".format(Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@Composable
fun rememberNow(ticking: Boolean): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(ticking) {
        now = Instant.now()
        while (ticking) {
            delay(500)
            now = Instant.now()
        }
    }
    return now
}

@Composable
fun TimerTabLabel(timerStore: TimerStore, label: String) {
    val timer by timerStore.state.collectAsStateWithLifecycle(initialValue = null)
    val now = rememberNow(ticking = timer?.isPaused == false)
    Text(timer?.let { formatElapsed(it.elapsed(now)) } ?: label)
}
```

`app/src/main/java/de/touchgrass/ui/record/TimerViewModel.kt`:
```kotlin
package de.touchgrass.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerState
import de.touchgrass.data.TimerStore
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.SportDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

data class TimerUiState(
    val sports: List<SportDto> = emptyList(),
    val sportCode: String? = null,
    val timer: TimerState? = null,
    val error: String? = null,
    val openForm: Boolean = false,
)

class TimerViewModel(
    private val api: SportApi,
    private val timerStore: TimerStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()

    init {
        loadSports()
        viewModelScope.launch {
            timerStore.state.collect { timer ->
                _state.update { it.copy(timer = timer, sportCode = timer?.sportCode ?: it.sportCode) }
            }
        }
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

    fun onSportChange(code: String) = _state.update { it.copy(sportCode = code, error = null) }

    fun start() {
        val sportCode = _state.value.sportCode ?: return _state.update { it.copy(error = "Bitte eine Sportart wählen.") }
        save(TimerState(sportCode, clock.instant()))
    }

    fun pause() {
        _state.value.timer?.let { save(it.pause(clock.instant())) }
    }

    fun resume() {
        _state.value.timer?.let { save(it.resume(clock.instant())) }
    }

    fun stop() {
        val timer = _state.value.timer ?: return
        viewModelScope.launch {
            timerStore.save(timer.pause(clock.instant()))
            _state.update { it.copy(openForm = true) }
        }
    }

    fun onFormOpened() = _state.update { it.copy(openForm = false) }

    private fun save(timer: TimerState) {
        viewModelScope.launch { timerStore.save(timer) }
    }
}
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Insgesamt 61 App-Tests grün, davon neu: `TimerDisplayTest` 1, `TimerViewModelTest` 4.

- [ ] **Step 5: Timer-Screen und ➕-Graph**

`app/src/main/java/de/touchgrass/ui/record/TimerScreen.kt`:
```kotlin
package de.touchgrass.ui.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Duration

@Composable
fun TimerScreen(viewModel: TimerViewModel, onOpenForm: () -> Unit, onRecordManually: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timer = state.timer
    val now = rememberNow(ticking = timer?.isPaused == false)
    LaunchedEffect(state.openForm) {
        if (state.openForm) {
            viewModel.onFormOpened()
            onOpenForm()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Aktivität", style = MaterialTheme.typography.headlineMedium)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.sports.forEach { sport ->
                FilterChip(
                    selected = state.sportCode == sport.code,
                    onClick = { viewModel.onSportChange(sport.code) },
                    enabled = timer == null,
                    label = { Text("${sport.emoji} ${sport.name}") },
                )
            }
        }

        Text(formatElapsed(timer?.elapsed(now) ?: Duration.ZERO), style = MaterialTheme.typography.displayLarge)

        if (timer == null) {
            Button(onClick = viewModel::start) { Text("▶ Start") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (timer.isPaused) {
                    Button(onClick = viewModel::resume) { Text("▶ Weiter") }
                } else {
                    OutlinedButton(onClick = viewModel::pause) { Text("⏸ Pause") }
                }
                Button(onClick = viewModel::stop) { Text("⏹ Stopp") }
            }
        }

        TextButton(onClick = onRecordManually) { Text("Ohne Timer nachtragen") }
    }
}
```

In `app/src/main/java/de/touchgrass/ui/Navigation.kt`:

Imports ergänzen:
```kotlin
import de.touchgrass.ui.record.TimerScreen
import de.touchgrass.ui.record.TimerTabLabel
import de.touchgrass.ui.record.TimerViewModel
```

Die Route `@Serializable object RecordRoute` ersetzen durch:
```kotlin
@Serializable object RecordGraph
@Serializable object TimerRoute
@Serializable data class RecordFormRoute(val fromTimer: Boolean)
```

In `tabs` die Zeile `Tab(RecordRoute, "➕", "Aktivität"),` ersetzen durch:
```kotlin
    Tab(RecordGraph, "➕", "Aktivität"),
```

Im `NavigationBarItem` die Zeile `label = { Text(tab.label) },` ersetzen durch:
```kotlin
                        label = {
                            if (tab.route == RecordGraph) TimerTabLabel(container.timerStore, tab.label) else Text(tab.label)
                        },
```

Den Eintrag
```kotlin
            composable<RecordRoute> {
                RecordScreen(viewModel = viewModel { RecordViewModel(container.api) })
            }
```
ersetzen durch:
```kotlin
            navigation<RecordGraph>(startDestination = TimerRoute) {
                composable<TimerRoute> {
                    TimerScreen(
                        viewModel = viewModel { TimerViewModel(container.api, container.timerStore) },
                        onOpenForm = { navController.navigate(RecordFormRoute(fromTimer = true)) },
                        onRecordManually = { navController.navigate(RecordFormRoute(fromTimer = false)) },
                    )
                }
                composable<RecordFormRoute> {
                    RecordScreen(viewModel = viewModel { RecordViewModel(container.api) })
                }
            }
```

- [ ] **Step 6: Bauen und alle Tests laufen lassen**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 61 App-Tests grün.

- [ ] **Step 7: Commit**

```powershell
git add app/src
git commit -m "Timer mit Start, Pause und Stopp im Plus-Tab" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: App – Formular mit Timer-Werten, Ort und „Verwerfen“

**Files:**
- Modify: `app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt` (komplett), `.../ui/record/RecordScreen.kt` (komplett)
- Create: `app/src/main/java/de/touchgrass/ui/record/LocationPicker.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/Navigation.kt` (Formular-Route)
- Test: `app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt` (komplett)

**Interfaces:**
- Consumes: `TimerState`, `TimerStore`, `FakeTimerStore` (Task 2); `GeoPoint`, `toGeoPoint`, `toPosition`, `PointMarker`, `MAP_STYLE_URL`, `GERMANY`, `LOCATION_DENIED`, `rememberLocationRequest`, `rememberUserLocation` (Task 3); `RecordFormRoute(fromTimer)` (Task 4)
- Produces: `RecordUiState` + `location: GeoPoint?`, `fromTimer: Boolean`, `discarded: Boolean`; `RecordViewModel(api, timerStore, fromTimer: Boolean, clock = Clock.systemDefaultZone())` + `onLocationChange(location: GeoPoint?)`, `discard()`; `@Composable fun LocationPicker(location: GeoPoint?, error: String?, onLocationChange: (GeoPoint?) -> Unit)`; `@Composable fun RecordScreen(viewModel: RecordViewModel, onBack: () -> Unit)`.

- [ ] **Step 1: Failing Tests schreiben**

`app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt` komplett ersetzen:
```kotlin
package de.touchgrass.ui.record

import de.touchgrass.data.TimerState
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.CreateActivityResponse
import de.touchgrass.data.dto.PointsBreakdownDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.Visibility
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.FakeTimerStore
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.map.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecordViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T08:15:30Z"), ZoneId.of("Europe/Berlin"))

    private val pausedTimer = TimerState(
        sportCode = "CYCLING",
        startedAt = Instant.parse("2026-10-05T07:30:20Z"),
        pausedTotal = Duration.ofMinutes(5),
        pausedSince = Instant.parse("2026-10-05T08:15:30Z"),
    )

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
        val viewModel = RecordViewModel(Api(), FakeTimerStore(), fromTimer = false, clock)

        val state = viewModel.state.value
        assertEquals(testSports, state.sports)
        assertEquals(LocalDate.parse("2026-10-05"), state.date)
        assertEquals(LocalTime.parse("10:15"), state.time)
        assertFalse(state.fromTimer)
    }

    @Test
    fun `ohne Sportart oder mit ungültiger Dauer wird nichts gesendet`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, clock)

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
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, clock)

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
    fun `gewählter Ort wird mitgeschickt und lässt sich wieder entfernen`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.onLocationChange(GeoPoint(48.1642, 11.6056))
        viewModel.save()
        assertEquals(48.1642, api.sent?.latitude)
        assertEquals(11.6056, api.sent?.longitude)
        assertNull(viewModel.state.value.location)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.onLocationChange(GeoPoint(48.1642, 11.6056))
        viewModel.onLocationChange(null)
        viewModel.save()
        assertNull(api.sent?.latitude)
        assertNull(api.sent?.longitude)
    }

    @Test
    fun `Feldfehler vom Server erscheint am Feld`() {
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse = throw httpError(
                400,
                """{"error":"VALIDATION_ERROR","message":"Die Aktivität darf nicht in der Zukunft liegen.","field":"startedAt"}""",
            )
        }
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        assertEquals("Die Aktivität darf nicht in der Zukunft liegen.", viewModel.state.value.fieldErrors["startedAt"])
        assertEquals(false, viewModel.state.value.saving)
    }

    @Test
    fun `vom Timer kommen Sportart, Startzeit und Dauer ohne Pausen`() {
        val viewModel = RecordViewModel(Api(), FakeTimerStore(pausedTimer), fromTimer = true, clock)

        val state = viewModel.state.value
        assertEquals("CYCLING", state.sportCode)
        assertEquals(LocalDate.parse("2026-10-05"), state.date)
        assertEquals(LocalTime.parse("09:30"), state.time)
        assertEquals("40", state.duration)
        assertTrue(state.fromTimer)
    }

    @Test
    fun `nach dem Speichern aus dem Timer ist der Timer zurückgesetzt`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = true, clock)

        viewModel.save()

        assertNull(store.current.value)
        assertFalse(viewModel.state.value.fromTimer)
    }

    @Test
    fun `Nachtragen ohne Timer lässt einen laufenden Timer in Ruhe`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = false, clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        assertEquals(pausedTimer, store.current.value)
    }

    @Test
    fun `fehlgeschlagenes Speichern behält den Timer`() {
        val store = FakeTimerStore(pausedTimer)
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse =
                throw httpError(500, """{"error":"INTERNAL_ERROR","message":"Unerwarteter Fehler."}""")
        }
        val viewModel = RecordViewModel(api, store, fromTimer = true, clock)

        viewModel.save()

        assertEquals(pausedTimer, store.current.value)
        assertEquals("Unerwarteter Fehler.", viewModel.state.value.error)
    }

    @Test
    fun `Verwerfen setzt den Timer zurück`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = true, clock)

        viewModel.discard()

        assertNull(store.current.value)
        assertTrue(viewModel.state.value.discarded)
    }
}
```

Zur Rechnung im Timer-Test: Start 09:30:20 Berliner Zeit, Pause seit 10:15:30, davor 5 Minuten Pause → 45:10 − 5:00 = 40:10 → 40 Minuten. Die Startzeit im Formular wird auf die Minute abgeschnitten (09:30).

- [ ] **Step 2: Tests laufen lassen, sie müssen fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren, u. a. `No parameter with name 'fromTimer'`, `Unresolved reference 'onLocationChange'`, `'discard'`, `'location'`.

- [ ] **Step 3: RecordViewModel erweitern und in der Navigation aufrufen**

`app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt` komplett ersetzen:
```kotlin
package de.touchgrass.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerState
import de.touchgrass.data.TimerStore
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.Visibility
import de.touchgrass.ui.activities.describe
import de.touchgrass.ui.map.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val location: GeoPoint? = null,
    val description: String = "",
    val visibility: Visibility = Visibility.PRIVATE,
    val fromTimer: Boolean = false,
    val saving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val savedMessage: String? = null,
    val discarded: Boolean = false,
)

class RecordViewModel(
    private val api: SportApi,
    private val timerStore: TimerStore,
    fromTimer: Boolean,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _state = MutableStateFlow(emptyForm())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    init {
        loadSports()
        if (fromTimer) {
            viewModelScope.launch { timerStore.state.first()?.let(::fillFromTimer) }
        }
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

    fun onLocationChange(location: GeoPoint?) = edit("latitude", "longitude") { copy(location = location) }

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
            latitude = current.location?.latitude,
            longitude = current.location?.longitude,
            description = current.description.trim().ifEmpty { null },
            visibility = current.visibility,
        )
        _state.update { it.copy(saving = true, fieldErrors = emptyMap(), error = null, savedMessage = null) }
        viewModelScope.launch {
            try {
                val response = apiCall { api.createActivity(request) }
                if (current.fromTimer) timerStore.clear()
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

    fun discard() {
        viewModelScope.launch {
            timerStore.clear()
            _state.update { it.copy(discarded = true) }
        }
    }

    private fun fillFromTimer(timer: TimerState) {
        val start = LocalDateTime.ofInstant(timer.startedAt, clock.zone).truncatedTo(ChronoUnit.MINUTES)
        _state.update {
            it.copy(
                sportCode = timer.sportCode,
                date = start.toLocalDate(),
                time = start.toLocalTime(),
                duration = timer.durationMinutes(clock.instant()).toString(),
                fromTimer = true,
            )
        }
    }

    private fun emptyForm(): RecordUiState {
        val now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        return RecordUiState(date = now.toLocalDate(), time = now.toLocalTime())
    }

    private fun edit(vararg fields: String, change: RecordUiState.() -> RecordUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - fields.toSet(), error = null, savedMessage = null) }

    private fun showFieldError(field: String, message: String) =
        _state.update { it.copy(fieldErrors = it.fieldErrors + (field to message)) }
}
```

In `app/src/main/java/de/touchgrass/ui/Navigation.kt` den Eintrag
```kotlin
                composable<RecordFormRoute> {
                    RecordScreen(viewModel = viewModel { RecordViewModel(container.api) })
                }
```
ersetzen durch:
```kotlin
                composable<RecordFormRoute> { entry ->
                    val fromTimer = entry.toRoute<RecordFormRoute>().fromTimer
                    RecordScreen(viewModel = viewModel { RecordViewModel(container.api, container.timerStore, fromTimer) })
                }
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Insgesamt 67 App-Tests grün, `RecordViewModelTest` hat jetzt 10 Tests (neu: Ort, Timer-Werte, Timer nach dem Speichern zurückgesetzt, Nachtragen lässt Timer in Ruhe, Fehler behält Timer, Verwerfen).

- [ ] **Step 5: Ortswahl, Formular und Zurück-Knopf**

`app/src/main/java/de/touchgrass/ui/record/LocationPicker.kt`:
```kotlin
package de.touchgrass.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.touchgrass.ui.map.GERMANY
import de.touchgrass.ui.map.GeoPoint
import de.touchgrass.ui.map.LOCATION_DENIED
import de.touchgrass.ui.map.MAP_STYLE_URL
import de.touchgrass.ui.map.PointMarker
import de.touchgrass.ui.map.rememberLocationRequest
import de.touchgrass.ui.map.rememberUserLocation
import de.touchgrass.ui.map.toGeoPoint
import de.touchgrass.ui.map.toPosition
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.RenderOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import java.util.Locale

@Composable
fun LocationPicker(location: GeoPoint?, error: String?, onLocationChange: (GeoPoint?) -> Unit) {
    val scope = rememberCoroutineScope()
    val cameraState = rememberCameraState(
        firstPosition = location?.let { CameraPosition(target = it.toPosition(), zoom = 14.0) } ?: GERMANY,
    )
    var locating by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val requestLocation = rememberLocationRequest(
        onGranted = { hint = null; locating = true },
        onDenied = { hint = LOCATION_DENIED },
    )
    val markerColor = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ort (freiwillig) – auf die Karte tippen")
        MaplibreMap(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
            cameraState = cameraState,
            // TextureView statt SurfaceView, damit die Karte beim Scrollen des Formulars mitwandert.
            options = MapOptions(renderOptions = RenderOptions(renderMode = RenderOptions.RenderMode.TextureView)),
            onMapClick = { position, _ ->
                onLocationChange(position.toGeoPoint())
                ClickResult.Consume
            },
        ) {
            location?.let { PointMarker("selected-location", it, markerColor) }
        }
        Text(
            location?.let { "📍 %.5f, %.5f".format(Locale.ROOT, it.latitude, it.longitude) } ?: "Kein Ort gewählt",
            style = MaterialTheme.typography.bodySmall,
        )
        hint?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = requestLocation, enabled = !locating) {
                Text(if (locating) "Suche Standort …" else "📍 Aktueller Standort")
            }
            TextButton(onClick = { onLocationChange(null) }, enabled = location != null) { Text("Ohne Standort") }
        }
    }

    if (locating) {
        val position = rememberUserLocation().location?.position
        LaunchedEffect(position != null) {
            if (position != null) {
                onLocationChange(position.toGeoPoint())
                scope.launch { cameraState.animateTo(CameraPosition(target = position, zoom = 15.0)) }
                locating = false
            }
        }
    }
}
```

`app/src/main/java/de/touchgrass/ui/record/RecordScreen.kt` komplett ersetzen:
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
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
fun RecordScreen(viewModel: RecordViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(state.discarded) { if (state.discarded) onBack() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Zurück") }
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
        LocationPicker(
            location = state.location,
            error = state.fieldErrors["latitude"] ?: state.fieldErrors["longitude"],
            onLocationChange = viewModel::onLocationChange,
        )
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
        if (state.fromTimer) {
            TextButton(
                onClick = { confirmDiscard = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Verwerfen") }
        }
    }

    if (pickDate) {
        DateDialog(state.date, onDismiss = { pickDate = false }, onPicked = { pickDate = false; viewModel.onDateChange(it) })
    }
    if (pickTime) {
        TimeDialog(state.time, onDismiss = { pickTime = false }, onPicked = { pickTime = false; viewModel.onTimeChange(it) })
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Aktivität verwerfen?") },
            text = { Text("Der Timer wird zurückgesetzt, die gemessene Zeit geht verloren.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; viewModel.discard() }) { Text("Verwerfen") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Abbrechen") } },
        )
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

In `app/src/main/java/de/touchgrass/ui/Navigation.kt` im Eintrag `composable<RecordFormRoute>` die Zeile
```kotlin
                    RecordScreen(viewModel = viewModel { RecordViewModel(container.api, container.timerStore, fromTimer) })
```
ersetzen durch:
```kotlin
                    RecordScreen(
                        viewModel = viewModel { RecordViewModel(container.api, container.timerStore, fromTimer) },
                        onBack = { navController.popBackStack() },
                    )
```

- [ ] **Step 6: Bauen und alle Tests laufen lassen**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, 67 App-Tests grün.

- [ ] **Step 7: Commit**

```powershell
git add app/src
git commit -m "Formular übernimmt Timer-Werte, Ortswahl auf der Karte und Verwerfen" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Abschluss von M3

- [ ] Backend: `.\gradlew.bat test --rerun` im Ordner `backend` → 58 Tests grün.
- [ ] App: `.\gradlew.bat assembleDebug :app:testDebugUnitTest --rerun` im Wurzelordner → 67 Tests grün.
- [ ] Android Studio: **Sync Project with Gradle Files**.
- [ ] Von Hand auf Gerät oder Emulator (Backend läuft, angemeldet, Internet für die Kartenbilder):
  1. 🗺️-Tab: Die Karte erscheint. Ohne eigene Aktivität mit Ort zeigt sie Deutschland.
  2. ➕-Tab: Sportart wählen, „▶ Start“ → die Zeit läuft, der ➕-Tab zeigt sie statt „Aktivität“. „⏸ Pause“ → die Zeit steht, „▶ Weiter“ → sie läuft weiter.
  3. App ganz schließen (aus „Zuletzt verwendet“ wischen) und neu öffnen → der Timer zeigt die richtige Zeit und läuft weiter.
  4. „⏹ Stopp“ → Formular mit Sportart, Startzeit und Dauer ohne Pausen. „← Zurück“ → Timer-Screen, Timer steht auf Pause.
  5. Wieder „⏹ Stopp“, auf die kleine Karte tippen → Marker und Koordinaten. „📍 Aktueller Standort“ → Berechtigungsdialog → nach „Zulassen“ springt der Marker zum Standort. „Ohne Standort“ entfernt ihn. Ort setzen, 🌎 Öffentlich, „Speichern“ → Meldung mit kcal und Punkten, der ➕-Tab zeigt wieder „Aktivität“.
  6. Neuer Timer, „⏹ Stopp“, „Verwerfen“ → Rückfrage → „Verwerfen“ → zurück auf dem Timer-Screen, der Timer ist weg.
  7. „Ohne Timer nachtragen“ → leeres Formular ohne „Verwerfen“, ein laufender Timer läuft weiter.
  8. 🗺️-Tab: Die gespeicherte Aktivität ist ein farbiger Marker, die Karte startet dort. Marker antippen → Bottom Sheet mit Sportart, „von dir“, Datum, Dauer, kcal und Beschreibung.
  9. Zweites Konto: öffentliche Aktivität mit Ort und private Aktivität mit Ort anlegen → im ersten Konto ein grauer Marker „von <name>“, die private fehlt.
  10. „📍 Mein Standort“ → nach der Berechtigung erscheint der Standortpunkt und die Karte springt hin. Berechtigung ablehnen → Hinweis oben auf der Karte.
  11. 👤-Tab: Die Server-Status-Karte steht jetzt unten im Profil.
- [ ] `git status --short` zeigt keine unbeabsichtigten Dateien.
