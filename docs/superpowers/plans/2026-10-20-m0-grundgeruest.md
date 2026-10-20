# M0 Grundgerüst – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein lauffähiges Grundgerüst: Ktor-Backend mit eingebettetem PostgreSQL und `GET /api/health`, eine in TouchGrass umbenannte App mit Bottom-Navigation, die über Retrofit den Server-Status anzeigt, sowie `docs/api.md` und `README.md`.

**Architecture:** Zwei unabhängige Gradle-Projekte in einem Repo: `app/` (Android, Jetpack Compose) und `backend/` (Ktor, eigener Gradle-Build). Das Backend startet beim Hochfahren selbst ein PostgreSQL (zonky embedded-postgres), migriert es mit Flyway und spricht es über Exposed an. Die App ruft das Backend über Retrofit auf. Ein `AppContainer` ersetzt ein DI-Framework.

**Tech Stack:** Backend: Kotlin 2.4.21, Ktor 3.6.0, Exposed 1.6.0, Flyway 13.10.3, HikariCP 7.1.0, zonky embedded-postgres 2.2.2 (PostgreSQL 18.6), Logback 1.6.5. App: AGP 9.2.1, Kotlin 2.2.10, Compose BOM 2026.02.01, Navigation Compose 2.10.2, Retrofit 3.0.0, kotlinx-serialization-json 1.9.0.

**Spec:** `docs/superpowers/specs/2026-10-20-touchgrass-design.md`

## Global Constraints

- Alle Commits direkt auf `master`, keine Branches.
- Commit-Nachrichten auf Deutsch. Committet Claude, kommt als zweiter Absatz `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` dazu (in den Befehlen unten schon enthalten).
- Paket in App und Backend: `de.touchgrass`. App-Name: `TouchGrass`.
- API-Basispfad: `/api`. Fehlerformat: `{ "error": "CODE", "message": "…", "field": "…" }`, `field` nur bei Eingabefehlern. JSON-Felder mit Wert `null` werden weggelassen.
- Exposed 1.x nutzt die Pakete `org.jetbrains.exposed.v1.*` (nicht `org.jetbrains.exposed.sql.*`).
- Die App bleibt bei Kotlin 2.2.10. Deshalb `kotlinx-serialization-json` **1.9.0** (neuere Versionen brauchen einen neueren Kotlin-Compiler) und `mockwebserver` **4.12.0** (passend zu OkHttp 4.12.0 aus Retrofit 3.0.0).
- Wenig Kommentare im Code: eine Begründung an genau einer Stelle, kein Aufsatz pro Datei.
- Testnamen auf Deutsch in Backticks, Code-Bezeichner auf Englisch, UI-Texte auf Deutsch.

## Ausführungshinweise

- **JDK:** Auf diesem Rechner liegt Java 8 im `PATH`, Gradle braucht aber ≥ 17. Vor jedem Gradle-Aufruf in einer neuen Shell:
  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
  ```
- **Für Claude:** Gradle-Befehle im Terminal-Panel ausführen (`run_in_terminal`). Über die Bash- oder PowerShell-Tools bricht Gradle mit `Unable to establish loopback connection` ab, auch ohne Sandbox. Git, Dateioperationen und `curl` gehen über Bash.
- Alle Pfade relativ zum Repo-Wurzelordner `C:\Users\nils.appel\StudioProjects\My_Application`.
- Der Erststart des eingebetteten PostgreSQL entpackt ca. 30 s lang Binärdateien, danach geht es schnell.
- Die Warnung `We recommend using a newer Android Gradle plugin to use compile SDK version 37.1` gab es schon vor M0. Sie ist harmlos und wird ignoriert.

## Dateiübersicht

```
backend/
├── .gitignore
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew, gradlew.bat, gradle/wrapper/*           (Kopie aus dem Wurzelordner)
├── gradle/libs.versions.toml
└── src/
    ├── main/kotlin/de/touchgrass/
    │   ├── Application.kt          main() und Application.module(database)
    │   ├── config/AppConfig.kt     Konfiguration aus Umgebungsvariablen
    │   ├── db/DatabaseFactory.kt   eingebettetes/externes PostgreSQL, Flyway, Exposed
    │   ├── plugins/Serialization.kt
    │   ├── plugins/StatusPages.kt  einheitliches Fehlerformat
    │   └── routes/HealthRoutes.kt  GET /api/health
    ├── main/resources/
    │   ├── logback.xml
    │   └── db/migration/V1__sport_types.sql
    └── test/kotlin/de/touchgrass/
        ├── TestDatabase.kt
        └── HealthRoutesTest.kt

app/src/
├── main/java/de/touchgrass/
│   ├── MainActivity.kt
│   ├── TouchGrassApp.kt            Application-Klasse, hält den AppContainer
│   ├── AppContainer.kt             erzeugt SportApi einmal
│   ├── data/SportApi.kt            Retrofit-Interface + createSportApi()
│   ├── data/dto/HealthDto.kt
│   ├── ui/UiState.kt               Loading / Content / Error
│   ├── ui/PlaceholderScreen.kt
│   ├── ui/Navigation.kt            Bottom-Bar + NavHost
│   ├── ui/status/ServerStatusViewModel.kt
│   ├── ui/status/ServerStatusCard.kt
│   └── ui/theme/                   (umbenannt, sonst unverändert)
├── main/res/xml/network_security_config.xml     Klartext verboten
├── debug/res/xml/network_security_config.xml    Klartext erlaubt (nur Debug)
└── test/java/de/touchgrass/
    ├── data/SportApiTest.kt
    └── ui/status/ServerStatusViewModelTest.kt

docs/api.md
README.md
```

---

### Task 1: Backend-Grundgerüst mit Fehlerformat

**Files:**
- Create: `backend/.gitignore`, `backend/settings.gradle.kts`, `backend/gradle/libs.versions.toml`, `backend/build.gradle.kts`
- Create (Kopie): `backend/gradlew`, `backend/gradlew.bat`, `backend/gradle/wrapper/gradle-wrapper.jar`, `backend/gradle/wrapper/gradle-wrapper.properties`
- Create: `backend/src/main/resources/logback.xml`
- Create: `backend/src/main/kotlin/de/touchgrass/plugins/Serialization.kt`, `.../plugins/StatusPages.kt`, `.../routes/HealthRoutes.kt`, `.../Application.kt`
- Test: `backend/src/test/kotlin/de/touchgrass/HealthRoutesTest.kt`

**Interfaces:**
- Produces: `fun Application.module()` (Task 2 ändert die Signatur zu `module(database: Database)`), `fun Application.configureSerialization()`, `fun Application.configureStatusPages()`, `@Serializable data class ErrorResponse(val error: String, val message: String, val field: String? = null)`, `fun Route.healthRoutes()`, `@Serializable data class HealthResponse(val status: String)`

- [ ] **Step 1: Gradle-Wrapper kopieren und Build-Dateien anlegen**

```powershell
New-Item -ItemType Directory -Force backend/gradle | Out-Null
Copy-Item gradlew, gradlew.bat backend/
Copy-Item -Recurse gradle/wrapper backend/gradle/
```

`backend/.gitignore`:
```
/build
/data
/.gradle
/.kotlin
```

`backend/settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "touchgrass-backend"
```

`backend/gradle/libs.versions.toml` (enthält schon die Datenbank-Bibliotheken für Task 2):
```toml
[versions]
kotlin = "2.4.21"
ktor = "3.6.0"
exposed = "1.6.0"
flyway = "13.10.3"
postgresql = "42.7.14"
hikari = "7.1.0"
embeddedPostgres = "2.2.2"
embeddedPostgresBinaries = "18.6.0"
logback = "1.6.5"

[libraries]
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-server-status-pages = { module = "io.ktor:ktor-server-status-pages", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-server-test-host = { module = "io.ktor:ktor-server-test-host", version.ref = "ktor" }
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-postgresql = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
hikari = { module = "com.zaxxer:HikariCP", version.ref = "hikari" }
embedded-postgres = { module = "io.zonky.test:embedded-postgres", version.ref = "embeddedPostgres" }
embedded-postgres-binaries-bom = { module = "io.zonky.test.postgres:embedded-postgres-binaries-bom", version.ref = "embeddedPostgresBinaries" }
logback = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`backend/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass = "de.touchgrass.ApplicationKt"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed") }
}
```

`backend/src/main/resources/logback.xml`:
```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
    <logger name="io.netty" level="WARN"/>
    <logger name="com.zaxxer.hikari" level="WARN"/>
    <logger name="io.zonky" level="WARN"/>
</configuration>
```

- [ ] **Step 2: Failing Test schreiben**

`backend/src/test/kotlin/de/touchgrass/HealthRoutesTest.kt`:
```kotlin
package de.touchgrass

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRoutesTest {
    @Test
    fun `health meldet ok`() = testApplication {
        application { module() }

        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
    }

    @Test
    fun `unbekannter Pfad liefert einheitliches Fehlerformat`() = testApplication {
        application { module() }

        val response = client.get("/api/gibt-es-nicht")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("""{"error":"NOT_FOUND","message":"Nicht gefunden"}""", response.bodyAsText())
    }

    @Test
    fun `unerwarteter Fehler liefert 500 im einheitlichen Fehlerformat`() = testApplication {
        application {
            module()
            routing { get("/api/kaputt") { error("absichtlich kaputt") } }
        }

        val response = client.get("/api/kaputt")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("""{"error":"INTERNAL_ERROR","message":"Interner Serverfehler"}""", response.bodyAsText())
    }
}
```

- [ ] **Step 3: Test laufen lassen, er muss fehlschlagen**

Run (im Ordner `backend`): `.\gradlew.bat test`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'module'`.

- [ ] **Step 4: Implementierung schreiben**

`backend/src/main/kotlin/de/touchgrass/plugins/Serialization.kt`:
```kotlin
package de.touchgrass.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            encodeDefaults = true
            explicitNulls = false
        })
    }
}
```

`backend/src/main/kotlin/de/touchgrass/plugins/StatusPages.kt`:
```kotlin
package de.touchgrass.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String, val message: String, val field: String? = null)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unerwarteter Fehler", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("INTERNAL_ERROR", "Interner Serverfehler"))
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorResponse("NOT_FOUND", "Nicht gefunden"))
        }
    }
}
```

`backend/src/main/kotlin/de/touchgrass/routes/HealthRoutes.kt`:
```kotlin
package de.touchgrass.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HealthResponse(status = "ok"))
    }
}
```

`backend/src/main/kotlin/de/touchgrass/Application.kt`:
```kotlin
package de.touchgrass

import de.touchgrass.plugins.configureSerialization
import de.touchgrass.plugins.configureStatusPages
import de.touchgrass.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    configureSerialization()
    configureStatusPages()
    routing {
        route("/api") {
            healthRoutes()
        }
    }
}
```

- [ ] **Step 5: Tests laufen lassen, sie müssen grün sein**

Run (im Ordner `backend`): `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, drei Zeilen `HealthRoutesTest > … PASSED`.

- [ ] **Step 6: Commit**

```powershell
git add backend
git commit -m "Backend-Grundgerüst mit Health-Endpunkt und Fehlerformat" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Vorher mit `git status --short` prüfen, dass weder `backend/build` noch `backend/.gradle` dabei sind.

---

### Task 2: Eingebettetes PostgreSQL, Flyway und Exposed

**Files:**
- Modify: `backend/build.gradle.kts` (Datenbank-Abhängigkeiten)
- Create: `backend/src/main/kotlin/de/touchgrass/config/AppConfig.kt`, `.../db/DatabaseFactory.kt`
- Create: `backend/src/main/resources/db/migration/V1__sport_types.sql`
- Modify: `backend/src/main/kotlin/de/touchgrass/routes/HealthRoutes.kt`, `.../Application.kt`
- Create: `backend/src/test/kotlin/de/touchgrass/TestDatabase.kt`
- Modify: `backend/src/test/kotlin/de/touchgrass/HealthRoutesTest.kt`

**Interfaces:**
- Consumes: `configureSerialization()`, `configureStatusPages()` aus Task 1
- Produces: `fun Application.module(database: org.jetbrains.exposed.v1.jdbc.Database)`, `data class AppConfig(port: Int, dataDir: String, embeddedDbPort: Int, dbUrl: String?, dbUser: String?, dbPassword: String?)` mit `AppConfig.fromEnvironment(env: Map<String, String> = System.getenv())`, `object DatabaseFactory { fun start(config: AppConfig): AppDatabase; fun startForTests(): AppDatabase }`, `class AppDatabase(val database: Database, …) : Closeable`, `object TestDatabase { val database: Database }` (für alle späteren Backend-Tests), `fun Route.healthRoutes(database: Database)`, `@Serializable data class HealthResponse(val status: String, val database: String)`, Tabelle `sport_types(code, name, emoji, met)` mit 8 Zeilen.

- [ ] **Step 1: Abhängigkeiten ergänzen**

`backend/build.gradle.kts`, Block `dependencies` komplett ersetzen:
```kotlin
dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.embedded.postgres)
    implementation(platform(libs.embedded.postgres.binaries.bom))
    implementation(libs.logback)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}
```

- [ ] **Step 2: Failing Tests schreiben**

`backend/src/test/kotlin/de/touchgrass/TestDatabase.kt`:
```kotlin
package de.touchgrass

import de.touchgrass.db.DatabaseFactory

object TestDatabase {
    private val appDatabase by lazy { DatabaseFactory.startForTests() }
    val database get() = appDatabase.database
}
```

`backend/src/test/kotlin/de/touchgrass/HealthRoutesTest.kt` komplett ersetzen:
```kotlin
package de.touchgrass

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRoutesTest {
    @Test
    fun `health meldet ok, wenn die Datenbank erreichbar ist`() = testApplication {
        application { module(TestDatabase.database) }

        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok","database":"ok"}""", response.bodyAsText())
    }

    @Test
    fun `unbekannter Pfad liefert einheitliches Fehlerformat`() = testApplication {
        application { module(TestDatabase.database) }

        val response = client.get("/api/gibt-es-nicht")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("""{"error":"NOT_FOUND","message":"Nicht gefunden"}""", response.bodyAsText())
    }

    @Test
    fun `unerwarteter Fehler liefert 500 im einheitlichen Fehlerformat`() = testApplication {
        application {
            module(TestDatabase.database)
            routing { get("/api/kaputt") { error("absichtlich kaputt") } }
        }

        val response = client.get("/api/kaputt")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("""{"error":"INTERNAL_ERROR","message":"Interner Serverfehler"}""", response.bodyAsText())
    }

    @Test
    fun `Flyway legt die acht Sportarten an`() {
        val count = transaction(TestDatabase.database) {
            exec("SELECT COUNT(*) FROM sport_types") { rs -> rs.next(); rs.getInt(1) }
        }
        assertEquals(8, count)
    }
}
```

- [ ] **Step 3: Tests laufen lassen, sie müssen fehlschlagen**

Run (im Ordner `backend`): `.\gradlew.bat test`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'db'` bzw. `Too many arguments for 'fun Application.module()'`.

- [ ] **Step 4: Implementierung schreiben**

`backend/src/main/kotlin/de/touchgrass/config/AppConfig.kt`:
```kotlin
package de.touchgrass.config

data class AppConfig(
    val port: Int,
    val dataDir: String,
    val embeddedDbPort: Int,
    val dbUrl: String?,
    val dbUser: String?,
    val dbPassword: String?,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()) = AppConfig(
            port = env["PORT"]?.toInt() ?: 8080,
            dataDir = env["DATA_DIR"] ?: "data",
            embeddedDbPort = env["EMBEDDED_DB_PORT"]?.toInt() ?: 5433,
            dbUrl = env["DB_URL"],
            dbUser = env["DB_USER"],
            dbPassword = env["DB_PASSWORD"],
        )
    }
}
```

`backend/src/main/kotlin/de/touchgrass/db/DatabaseFactory.kt`:
```kotlin
package de.touchgrass.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.touchgrass.config.AppConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import java.io.Closeable
import java.nio.file.Path

class AppDatabase(val database: Database, private val resources: List<Closeable>) : Closeable {
    override fun close() = resources.reversed().forEach { it.close() }
}

object DatabaseFactory {
    fun start(config: AppConfig): AppDatabase {
        if (config.dbUrl != null) {
            val dataSource = pooled(config.dbUrl, config.dbUser ?: "", config.dbPassword ?: "")
            return connect(dataSource, listOf(dataSource))
        }
        val embedded = EmbeddedPostgres.builder()
            .setDataDirectory(Path.of(config.dataDir, "postgres"))
            .setCleanDataDirectory(false)
            .setPort(config.embeddedDbPort)
            .start()
        val dataSource = pooled(embedded.getJdbcUrl("postgres", "postgres"), "postgres", "postgres")
        return connect(dataSource, listOf(embedded, dataSource))
    }

    fun startForTests(): AppDatabase {
        val embedded = EmbeddedPostgres.start()
        val dataSource = pooled(embedded.getJdbcUrl("postgres", "postgres"), "postgres", "postgres")
        return connect(dataSource, listOf(embedded, dataSource))
    }

    private fun pooled(url: String, user: String, password: String) = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 5
        }
    )

    private fun connect(dataSource: HikariDataSource, resources: List<Closeable>): AppDatabase {
        Flyway.configure().dataSource(dataSource).load().migrate()
        return AppDatabase(Database.connect(dataSource), resources)
    }
}
```

`backend/src/main/resources/db/migration/V1__sport_types.sql` (Datei in UTF-8 speichern):
```sql
CREATE TABLE sport_types (
    code  VARCHAR(30)  PRIMARY KEY,
    name  VARCHAR(50)  NOT NULL,
    emoji VARCHAR(10)  NOT NULL,
    met   NUMERIC(4,1) NOT NULL
);

INSERT INTO sport_types (code, name, emoji, met) VALUES
    ('WALKING',    'Gehen',         '🚶', 3.5),
    ('RUNNING',    'Joggen',        '🏃', 8.0),
    ('CYCLING',    'Radfahren',     '🚴', 7.0),
    ('SWIMMING',   'Schwimmen',     '🏊', 6.0),
    ('FOOTBALL',   'Fußball',       '⚽', 7.0),
    ('BASKETBALL', 'Basketball',    '🏀', 6.5),
    ('HIKING',     'Wandern',       '🥾', 5.0),
    ('STRENGTH',   'Krafttraining', '🏋️', 5.0);
```

`backend/src/main/kotlin/de/touchgrass/routes/HealthRoutes.kt` komplett ersetzen:
```kotlin
package de.touchgrass.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Serializable
data class HealthResponse(val status: String, val database: String)

fun Route.healthRoutes(database: Database) {
    get("/health") {
        transaction(database) { exec("SELECT 1") }
        call.respond(HealthResponse(status = "ok", database = "ok"))
    }
}
```

`backend/src/main/kotlin/de/touchgrass/Application.kt` komplett ersetzen:
```kotlin
package de.touchgrass

import de.touchgrass.config.AppConfig
import de.touchgrass.db.DatabaseFactory
import de.touchgrass.plugins.configureSerialization
import de.touchgrass.plugins.configureStatusPages
import de.touchgrass.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database

fun main() {
    val config = AppConfig.fromEnvironment()
    val appDatabase = DatabaseFactory.start(config)
    Runtime.getRuntime().addShutdownHook(Thread { appDatabase.close() })
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(appDatabase.database)
    }.start(wait = true)
}

fun Application.module(database: Database) {
    configureSerialization()
    configureStatusPages()
    routing {
        route("/api") {
            healthRoutes(database)
        }
    }
}
```

- [ ] **Step 5: Tests laufen lassen, sie müssen grün sein**

Run (im Ordner `backend`): `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, vier Zeilen `HealthRoutesTest > … PASSED`. Beim allerersten Lauf dauert der erste Test ca. 15 s (PostgreSQL wird entpackt).

- [ ] **Step 6: Server von Hand starten und prüfen**

Run (im Ordner `backend`, eigenes Terminal, läuft weiter): `.\gradlew.bat run`
Expected im Log: `Successfully applied 1 migration to schema "public", now at version v1` und `Responding at http://127.0.0.1:8080`.

Run (zweites Terminal): `curl http://localhost:8080/api/health`
Expected: `{"status":"ok","database":"ok"}`

Server mit Ctrl+C beenden, die Frage `Batchvorgang abbrechen (J/N)?` mit `J` beantworten.

- [ ] **Step 7: Commit**

```powershell
git add backend
git commit -m "Eingebettetes PostgreSQL mit Flyway und Sportarten-Tabelle" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

`git status --short` darf danach `backend/data` nicht zeigen (steht in `backend/.gitignore`).

---

### Task 3: Neustart nach hartem Abbruch absichern

Wird das Backend hart beendet (Terminal geschlossen, Stopp-Knopf der IDE), läuft PostgreSQL weiter, hält Port 5433 und die Datenbankdateien. Der nächste Start bleibt dann hängen. Das Backend beendet beim Start deshalb ein übrig gebliebenes PostgreSQL. Der Prozess-Code lässt sich nicht sinnvoll automatisch testen, darum gibt es hier eine Prüfung von Hand.

**Files:**
- Modify: `backend/src/main/kotlin/de/touchgrass/db/DatabaseFactory.kt`

**Interfaces:**
- Consumes: `DatabaseFactory.start(config)` aus Task 2 (Signatur bleibt gleich)

- [ ] **Step 1: Fehler nachstellen**

1. Im Ordner `backend`: `.\gradlew.bat run`, warten auf `Responding at http://127.0.0.1:8080`.
2. Das Terminal-Fenster bzw. den Terminal-Tab **schließen** (nicht Ctrl+C).
3. Prüfen, dass PostgreSQL noch läuft:
   ```powershell
   Get-Process postgres
   ```
   Expected: mehrere `postgres`-Prozesse.
4. In neuem Terminal (JAVA_HOME setzen): `.\gradlew.bat run`
   Expected: Es erscheint **kein** `Responding at …`, der Start hängt oder bricht mit einem PostgreSQL-Fehler ab. Mit Ctrl+C beenden. Die PostgreSQL-Reste bleiben für Step 3 stehen.

- [ ] **Step 2: Aufräumen beim Start einbauen**

`backend/src/main/kotlin/de/touchgrass/db/DatabaseFactory.kt` komplett ersetzen:
```kotlin
package de.touchgrass.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.touchgrass.config.AppConfig
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class AppDatabase(val database: Database, private val resources: List<Closeable>) : Closeable {
    override fun close() = resources.reversed().forEach { it.close() }
}

object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun start(config: AppConfig): AppDatabase {
        if (config.dbUrl != null) {
            val dataSource = pooled(config.dbUrl, config.dbUser ?: "", config.dbPassword ?: "")
            return connect(dataSource, listOf(dataSource))
        }
        val dataDir = Path.of(config.dataDir, "postgres")
        stopLeftoverPostgres(dataDir)
        val embedded = EmbeddedPostgres.builder()
            .setDataDirectory(dataDir)
            .setCleanDataDirectory(false)
            .setPort(config.embeddedDbPort)
            .start()
        val dataSource = pooled(embedded.getJdbcUrl("postgres", "postgres"), "postgres", "postgres")
        return connect(dataSource, listOf(embedded, dataSource))
    }

    fun startForTests(): AppDatabase {
        val embedded = EmbeddedPostgres.start()
        val dataSource = pooled(embedded.getJdbcUrl("postgres", "postgres"), "postgres", "postgres")
        return connect(dataSource, listOf(embedded, dataSource))
    }

    // Wird der Server hart beendet (Terminal zu, Stopp in der IDE), läuft PostgreSQL weiter und blockiert den nächsten Start.
    // Erst der Hauptprozess, dann die Kinder – sonst startet er in der Lücke neue Kindprozesse.
    private fun stopLeftoverPostgres(dataDir: Path) {
        val pidFile = dataDir.resolve("postmaster.pid")
        if (!Files.exists(pidFile)) return
        val pid = Files.readAllLines(pidFile).firstOrNull()?.trim()?.toLongOrNull() ?: return
        val postmaster = ProcessHandle.of(pid).orElse(null) ?: return
        val command = postmaster.info().command().orElse("")
        if (!command.endsWith("postgres.exe") && !command.endsWith("/postgres")) return

        log.warn("Stoppe altes PostgreSQL vom letzten Start (PID {})", pid)
        val children = postmaster.descendants().toList()
        postmaster.destroy()
        children.forEach { it.destroy() }
        (children + postmaster).forEach { it.onExit().get(10, TimeUnit.SECONDS) }
    }

    private fun pooled(url: String, user: String, password: String) = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 5
        }
    )

    private fun connect(dataSource: HikariDataSource, resources: List<Closeable>): AppDatabase {
        Flyway.configure().dataSource(dataSource).load().migrate()
        return AppDatabase(Database.connect(dataSource), resources)
    }
}
```

- [ ] **Step 3: Prüfen, dass der Neustart jetzt klappt**

1. Im Ordner `backend`: `.\gradlew.bat run` (die PostgreSQL-Reste aus Step 1 laufen noch).
   Expected: zuerst `WARN … Stoppe altes PostgreSQL vom letzten Start (PID …)`, dann `Schema "public" is up to date. No migration necessary.` und `Responding at http://127.0.0.1:8080`.
2. `curl http://localhost:8080/api/health` → `{"status":"ok","database":"ok"}`
3. Terminal wieder **schließen**, neues Terminal, `.\gradlew.bat run` → wieder `Stoppe altes PostgreSQL …` und `Responding at …`.
4. Diesmal mit Ctrl+C und `J` beenden.

- [ ] **Step 4: Tests laufen lassen**

Run (im Ordner `backend`): `.\gradlew.bat test`
Expected: `BUILD SUCCESSFUL`, vier Tests `PASSED`.

- [ ] **Step 5: Commit**

```powershell
git add backend/src/main/kotlin/de/touchgrass/db/DatabaseFactory.kt
git commit -m "Altes PostgreSQL nach hartem Abbruch beim Start beenden" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: App in TouchGrass (`de.touchgrass`) umbenennen

**Files:**
- Move: `app/src/main/java/com/example/myapplication/` → `app/src/main/java/de/touchgrass/`
- Delete: `app/src/test/java/com/example/myapplication/ExampleUnitTest.kt`, `app/src/androidTest/` (Vorlagen-Tests ohne Inhalt)
- Modify: `settings.gradle.kts:25`, `app/build.gradle.kts:7,15`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/de/touchgrass/ui/theme/Color.kt:1`, `.../Type.kt:1`, `.../Theme.kt:1,37`, `app/src/main/java/de/touchgrass/MainActivity.kt`

**Interfaces:**
- Produces: `@Composable fun TouchGrassTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)` im Paket `de.touchgrass.ui.theme`, Style `Theme.TouchGrass`.

- [ ] **Step 1: Dateien verschieben und Vorlagen-Tests löschen**

```powershell
New-Item -ItemType Directory -Force app/src/main/java/de | Out-Null
git mv app/src/main/java/com/example/myapplication app/src/main/java/de/touchgrass
Remove-Item -Recurse app/src/main/java/com
git rm -r app/src/test/java/com app/src/androidTest
```

- [ ] **Step 2: Paketnamen und Theme anpassen**

`app/src/main/java/de/touchgrass/ui/theme/Color.kt` und `.../Type.kt`, jeweils Zeile 1:
```kotlin
package de.touchgrass.ui.theme
```

`app/src/main/java/de/touchgrass/ui/theme/Theme.kt`: Zeile 1 wie oben, und `fun MyApplicationTheme(` wird zu:
```kotlin
fun TouchGrassTheme(
```

`app/src/main/java/de/touchgrass/MainActivity.kt` komplett ersetzen:
```kotlin
package de.touchgrass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import de.touchgrass.ui.theme.TouchGrassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TouchGrassTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Text("TouchGrass", modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
```

`settings.gradle.kts`, Zeile 25:
```kotlin
rootProject.name = "TouchGrass"
```

`app/build.gradle.kts`: `namespace = "com.example.myapplication"` → `namespace = "de.touchgrass"` und `applicationId = "com.example.myapplication"` → `applicationId = "de.touchgrass"`.

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">TouchGrass</string>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style name="Theme.TouchGrass" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`app/src/main/AndroidManifest.xml`: beide Vorkommen von `@style/Theme.MyApplication` → `@style/Theme.TouchGrass`.

- [ ] **Step 3: Prüfen, dass kein alter Name übrig ist**

Run (Bash): `grep -rniE "myapplication|MyApplication" app/src settings.gradle.kts app/build.gradle.kts`
Expected: keine Ausgabe.

- [ ] **Step 4: Bauen**

Run (Wurzelordner): `.\gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add -A app settings.gradle.kts
git commit -m "App in TouchGrass umbenennen (Paket de.touchgrass)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

`.idea/` nicht mit committen.

---

### Task 5: Netzwerk-Schicht der App (Retrofit, BuildConfig, AppContainer)

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/network_security_config.xml`, `app/src/debug/res/xml/network_security_config.xml`
- Create: `app/src/main/java/de/touchgrass/data/dto/HealthDto.kt`, `.../data/SportApi.kt`, `.../AppContainer.kt`, `.../TouchGrassApp.kt`
- Test: `app/src/test/java/de/touchgrass/data/SportApiTest.kt`

**Interfaces:**
- Produces: `interface SportApi { suspend fun health(): HealthDto }`, `fun createSportApi(baseUrl: String): SportApi` (Json mit `ignoreUnknownKeys = true`), `@Serializable data class HealthDto(val status: String, val database: String)`, `class AppContainer(baseUrl: String) { val api: SportApi }`, `class TouchGrassApp : Application() { val container: AppContainer }`, `BuildConfig.API_BASE_URL` (Standard `http://10.0.2.2:8080/api/`, überschreibbar mit `api.baseUrl` in `local.properties`).

- [ ] **Step 1: Abhängigkeiten eintragen**

`gradle/libs.versions.toml` komplett ersetzen:
```toml
[versions]
agp = "9.2.1"
coreKtx = "1.19.0"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
lifecycleRuntimeKtx = "2.11.0"
activityCompose = "1.13.0"
kotlin = "2.2.10"
composeBom = "2026.02.01"
navigationCompose = "2.10.2"
retrofit = "3.0.0"
kotlinxSerializationJson = "1.9.0"
mockwebserver = "4.12.0"
kotlinxCoroutinesTest = "1.10.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-converter-kotlinx-serialization = { group = "com.squareup.retrofit2", name = "converter-kotlinx-serialization", version.ref = "retrofit" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`build.gradle.kts` (Wurzel) komplett ersetzen:
```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`app/build.gradle.kts` komplett ersetzen:
```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "de.touchgrass"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "de.touchgrass"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val apiBaseUrl = localProperties.getProperty("api.baseUrl") ?: "http://10.0.2.2:8080/api/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

- [ ] **Step 2: Failing Test schreiben**

`app/src/test/java/de/touchgrass/data/SportApiTest.kt`:
```kotlin
package de.touchgrass.data

import de.touchgrass.data.dto.HealthDto
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SportApiTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `health ruft api-health auf und ignoriert unbekannte Felder`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","database":"ok","version":"spaeter"}""")
        )
        val api = createSportApi(server.url("/api/").toString())

        val health = api.health()

        assertEquals(HealthDto(status = "ok", database = "ok"), health)
        assertEquals("/api/health", server.takeRequest().path)
    }
}
```

- [ ] **Step 3: Test laufen lassen, er muss fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'dto'` bzw. `Unresolved reference 'createSportApi'`.

- [ ] **Step 4: Implementierung schreiben**

`app/src/main/java/de/touchgrass/data/dto/HealthDto.kt`:
```kotlin
package de.touchgrass.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(val status: String, val database: String)
```

`app/src/main/java/de/touchgrass/data/SportApi.kt`:
```kotlin
package de.touchgrass.data

import de.touchgrass.data.dto.HealthDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

interface SportApi {
    @GET("health")
    suspend fun health(): HealthDto
}

private val json = Json { ignoreUnknownKeys = true }

fun createSportApi(baseUrl: String): SportApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(SportApi::class.java)
```

`app/src/main/java/de/touchgrass/AppContainer.kt`:
```kotlin
package de.touchgrass

import de.touchgrass.data.SportApi
import de.touchgrass.data.createSportApi

class AppContainer(baseUrl: String) {
    val api: SportApi = createSportApi(baseUrl)
}
```

`app/src/main/java/de/touchgrass/TouchGrassApp.kt`:
```kotlin
package de.touchgrass

import android.app.Application

class TouchGrassApp : Application() {
    val container by lazy { AppContainer(BuildConfig.API_BASE_URL) }
}
```

`app/src/main/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

`app/src/debug/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

`app/src/main/AndroidManifest.xml` komplett ersetzen:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".TouchGrassApp"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.TouchGrass">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.TouchGrass"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 5: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Bericht `app/build/test-results/testDebugUnitTest/TEST-de.touchgrass.data.SportApiTest.xml` enthält `tests="1" … failures="0" errors="0"`.

- [ ] **Step 6: Commit**

```powershell
git add gradle/libs.versions.toml build.gradle.kts app
git commit -m "Retrofit-Anbindung mit Health-Aufruf und AppContainer" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Server-Status mit ViewModel und Lade-/Fehlerzustand

**Files:**
- Create: `app/src/main/java/de/touchgrass/ui/UiState.kt`, `.../ui/status/ServerStatusViewModel.kt`, `.../ui/status/ServerStatusCard.kt`
- Test: `app/src/test/java/de/touchgrass/ui/status/ServerStatusViewModelTest.kt`

**Interfaces:**
- Consumes: `SportApi`, `HealthDto` aus Task 5
- Produces: `sealed interface UiState<out T> { data object Loading; data class Content<T>(val data: T); data class Error(val message: String) }` (Muster für alle späteren Screens), `class ServerStatusViewModel(api: SportApi) : ViewModel { val state: StateFlow<UiState<HealthDto>>; fun refresh() }`, `@Composable fun ServerStatusCard(api: SportApi, modifier: Modifier = Modifier)`

- [ ] **Step 1: Failing Test schreiben**

`app/src/test/java/de/touchgrass/ui/status/ServerStatusViewModelTest.kt`:
```kotlin
package de.touchgrass.ui.status

import de.touchgrass.data.SportApi
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ServerStatusViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `erreichbarer Server ergibt Content`() {
        val viewModel = ServerStatusViewModel(FakeApi { HealthDto("ok", "ok") })

        assertEquals(UiState.Content(HealthDto("ok", "ok")), viewModel.state.value)
    }

    @Test
    fun `Netzwerkfehler ergibt Error`() {
        val viewModel = ServerStatusViewModel(FakeApi { throw IOException("offline") })

        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value)
    }
}

private class FakeApi(private val answer: () -> HealthDto) : SportApi {
    override suspend fun health(): HealthDto = answer()
}
```

- [ ] **Step 2: Test laufen lassen, er muss fehlschlagen**

Run (Wurzelordner): `.\gradlew.bat testDebugUnitTest`
Expected: FAIL beim Kompilieren mit `Unresolved reference 'UiState'` bzw. `Unresolved reference 'ServerStatusViewModel'`.

- [ ] **Step 3: Implementierung schreiben**

`app/src/main/java/de/touchgrass/ui/UiState.kt`:
```kotlin
package de.touchgrass.ui

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
```

`app/src/main/java/de/touchgrass/ui/status/ServerStatusViewModel.kt`:
```kotlin
package de.touchgrass.ui.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.SportApi
import de.touchgrass.data.dto.HealthDto
import de.touchgrass.ui.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class ServerStatusViewModel(private val api: SportApi) : ViewModel() {
    private val _state = MutableStateFlow<UiState<HealthDto>>(UiState.Loading)
    val state: StateFlow<UiState<HealthDto>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                UiState.Content(api.health())
            } catch (e: IOException) {
                UiState.Error("Server nicht erreichbar")
            } catch (e: HttpException) {
                UiState.Error("Serverfehler ${e.code()}")
            }
        }
    }
}
```

`app/src/main/java/de/touchgrass/ui/status/ServerStatusCard.kt`:
```kotlin
package de.touchgrass.ui.status

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.touchgrass.data.SportApi
import de.touchgrass.ui.UiState

@Composable
fun ServerStatusCard(api: SportApi, modifier: Modifier = Modifier) {
    val viewModel = viewModel { ServerStatusViewModel(api) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val text = when (val s = state) {
                UiState.Loading -> "⏳ Verbinde mit Server …"
                is UiState.Content -> "✅ Server erreichbar (Datenbank: ${s.data.database})"
                is UiState.Error -> "❌ ${s.message}"
            }
            Text(text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = viewModel::refresh) { Text("Erneut") }
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen, sie müssen grün sein**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. `app/build/test-results/testDebugUnitTest/TEST-de.touchgrass.ui.status.ServerStatusViewModelTest.xml` enthält `tests="2" … failures="0" errors="0"`.

- [ ] **Step 5: Commit**

```powershell
git add app/src
git commit -m "Server-Status mit ViewModel und UiState" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Bottom-Navigation mit Platzhalter-Tabs

**Files:**
- Create: `app/src/main/java/de/touchgrass/ui/PlaceholderScreen.kt`, `.../ui/Navigation.kt`
- Modify: `app/src/main/java/de/touchgrass/MainActivity.kt`

**Interfaces:**
- Consumes: `AppContainer`, `TouchGrassApp.container` (Task 5), `ServerStatusCard` (Task 6), `TouchGrassTheme` (Task 4)
- Produces: Routen `MapRoute`, `RecordRoute`, `AchievementsRoute`, `ProfileRoute` (`@Serializable object`), `@Composable fun TouchGrassNavigation(container: AppContainer)`, `@Composable fun PlaceholderScreen(title: String, hint: String, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {})`. Spätere Meilensteine ersetzen die Platzhalter in `Navigation.kt` durch echte Screens.

- [ ] **Step 1: Platzhalter-Screen und Navigation schreiben**

`app/src/main/java/de/touchgrass/ui/PlaceholderScreen.kt`:
```kotlin
package de.touchgrass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderScreen(
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(hint, style = MaterialTheme.typography.bodyMedium)
        extra()
    }
}
```

`app/src/main/java/de/touchgrass/ui/Navigation.kt`:
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
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.touchgrass.AppContainer
import de.touchgrass.ui.status.ServerStatusCard
import kotlinx.serialization.Serializable

@Serializable object MapRoute
@Serializable object RecordRoute
@Serializable object AchievementsRoute
@Serializable object ProfileRoute

private data class Tab<T : Any>(val route: T, val emoji: String, val label: String)

private val tabs = listOf(
    Tab(MapRoute, "🗺️", "Karte"),
    Tab(RecordRoute, "➕", "Aktivität"),
    Tab(AchievementsRoute, "🏆", "Erfolge"),
    Tab(ProfileRoute, "👤", "Profil"),
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
            composable<RecordRoute> { PlaceholderScreen("Aktivität", "Formular kommt in M2, Timer in M3.") }
            composable<AchievementsRoute> { PlaceholderScreen("Erfolge", "Achievements, Wochenziel und Leaderboard kommen in M4.") }
            composable<ProfileRoute> { PlaceholderScreen("Profil", "Profil kommt in M1, Statistik in M5.") }
        }
    }
}
```

`app/src/main/java/de/touchgrass/MainActivity.kt` komplett ersetzen:
```kotlin
package de.touchgrass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.touchgrass.ui.TouchGrassNavigation
import de.touchgrass.ui.theme.TouchGrassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TouchGrassApp).container
        setContent {
            TouchGrassTheme {
                TouchGrassNavigation(container)
            }
        }
    }
}
```

- [ ] **Step 2: Bauen und Unit-Tests laufen lassen**

Run (Wurzelordner): `.\gradlew.bat assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, weiterhin 3 grüne Unit-Tests.

- [ ] **Step 3: Im Emulator von Hand prüfen**

1. Backend starten (Ordner `backend`): `.\gradlew.bat run`, warten auf `Responding at …`.
2. App in Android Studio auf einem Emulator starten (Run ▶).
3. Expected: Unten vier Tabs 🗺️ Karte · ➕ Aktivität · 🏆 Erfolge · 👤 Profil. Der Karten-Tab zeigt „✅ Server erreichbar (Datenbank: ok)“.
4. Jeden Tab antippen: Der jeweilige Platzhaltertext erscheint, der angetippte Tab ist markiert.
5. Backend mit Ctrl+C beenden, in der App „Erneut“ antippen → „❌ Server nicht erreichbar“.
6. Backend wieder starten, „Erneut“ → „✅ Server erreichbar …“.

- [ ] **Step 4: Commit**

```powershell
git add app/src
git commit -m "Bottom-Navigation mit Platzhalter-Tabs und Server-Status" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: API-Vertrag und README

**Files:**
- Create: `docs/api.md`, `README.md`

**Interfaces:**
- Produces: `docs/api.md` als verbindlicher Vertrag für M1–M5 (Objekte `User`, `Sport`, `Activity`, `Achievement`, alle Endpunkte mit Beispiel-JSON).

- [ ] **Step 1: `docs/api.md` anlegen**

````markdown
# TouchGrass REST-API

Verbindliche Absprache zwischen App und Backend. Ändert sich etwas an der API, wird zuerst diese Datei angepasst.

## Grundlagen

- Basis-URL: `http://<host>:8080/api`. Aus dem Emulator: `http://10.0.2.2:8080/api`.
- Format: JSON in UTF-8. Felder mit Wert `null` werden weggelassen.
- Zeitpunkte: ISO-8601 mit Offset, z. B. `2026-10-04T09:30:00+02:00`.
- Datumswerte: `YYYY-MM-DD`. Tage und Wochen rechnet das Backend in `Europe/Berlin`, eine Woche geht von Montag bis Sonntag.
- Anmeldung: Außer `/health`, `/auth/register` und `/auth/login` braucht jeder Aufruf den Header `Authorization: Bearer <token>`. Ein Token ist 30 Tage gültig.

## Fehler

Alle Fehler haben dieselbe Form. `field` gibt es nur bei Eingabefehlern.

```json
{ "error": "VALIDATION_ERROR", "message": "Die Dauer muss zwischen 1 und 1440 Minuten liegen.", "field": "durationMin" }
```

| Status | `error` | Wann |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Eingabe ungültig |
| 401 | `UNAUTHORIZED` | Token fehlt, ist ungültig oder abgelaufen |
| 401 | `INVALID_CREDENTIALS` | Login oder altes Passwort falsch |
| 403 | `FORBIDDEN` | Fremde Aktivität löschen |
| 404 | `NOT_FOUND` | Pfad oder Objekt existiert nicht (auch: private Aktivität anderer) |
| 409 | `USERNAME_TAKEN`, `EMAIL_TAKEN` | Benutzername oder E-Mail schon vergeben |
| 500 | `INTERNAL_ERROR` | Unerwarteter Serverfehler |

## Objekte

**User**
```json
{ "id": 1, "username": "max_m", "email": "max@example.com", "weightKg": 80.0, "goalType": "MINUTES", "goalTarget": 150 }
```
`weightKg` fehlt, solange kein Gewicht eingetragen ist. `goalType`: `MINUTES` | `ACTIVITIES` | `KCAL`.

**Sport**
```json
{ "code": "RUNNING", "name": "Joggen", "emoji": "🏃", "met": 8.0 }
```

**Activity**
```json
{
  "id": 42,
  "username": "max_m",
  "mine": true,
  "sportCode": "RUNNING",
  "startedAt": "2026-10-04T09:30:00+02:00",
  "durationMin": 48,
  "caloriesKcal": 512,
  "points": 78,
  "latitude": 48.1642,
  "longitude": 11.6056,
  "description": "Schöne Runde",
  "visibility": "PRIVATE"
}
```
`latitude`, `longitude` und `description` fehlen, wenn nicht gesetzt. `mine` ist `true`, wenn die Aktivität dem angemeldeten Benutzer gehört. `visibility`: `PRIVATE` | `PUBLIC`.

**Achievement**
```json
{ "code": "RUNNER_10", "name": "Läufer", "description": "10 Laufaktivitäten", "emoji": "🏃", "threshold": 10, "progress": 7 }
```
Freigeschaltete haben zusätzlich `"unlockedAt": "2026-10-04T10:18:00+02:00"`.

## Endpunkte

| Methode | Pfad | Meilenstein |
|---|---|---|
| GET | `/health` | M0 |
| POST | `/auth/register`, `/auth/login` | M1 |
| GET, PATCH, DELETE | `/me` | M1 |
| PUT | `/me/password` | M1 |
| GET | `/sports` | M2 |
| POST, GET | `/activities` | M2 |
| GET, DELETE | `/activities/{id}` | M2 |
| GET | `/map/activities` | M3 |
| GET | `/achievements`, `/progress`, `/leaderboard` | M4 |
| GET | `/stats` | M5 |

### GET /health

Prüft Server und Datenbank. Keine Anmeldung nötig.

`200`
```json
{ "status": "ok", "database": "ok" }
```

### POST /auth/register

```json
{ "username": "max_m", "email": "max@example.com", "password": "geheim123" }
```
`201`
```json
{ "token": "eyJhbGciOi…", "user": { "id": 1, "username": "max_m", "email": "max@example.com", "goalType": "MINUTES", "goalTarget": 150 } }
```
Fehler: `400 VALIDATION_ERROR` (`field`: `username` 3–20 Zeichen aus `a-z A-Z 0-9 _`, `email`, `password` mindestens 8 Zeichen), `409 USERNAME_TAKEN`, `409 EMAIL_TAKEN`.

### POST /auth/login

```json
{ "login": "max_m", "password": "geheim123" }
```
`login` ist Benutzername oder E-Mail.
`200`: wie bei `/auth/register`. Fehler: `401 INVALID_CREDENTIALS`.

### GET /me

`200`: `User`

### PATCH /me

Alle Felder optional, nur mitgeschickte werden geändert.
```json
{ "weightKg": 78.5, "goalType": "ACTIVITIES", "goalTarget": 4 }
```
`200`: geänderter `User`. Fehler: `400 VALIDATION_ERROR` (`weightKg` 30–300, `goalTarget` > 0).

### PUT /me/password

```json
{ "oldPassword": "geheim123", "newPassword": "nochgeheimer456" }
```
`204` ohne Inhalt. Fehler: `401 INVALID_CREDENTIALS` (altes Passwort falsch), `400 VALIDATION_ERROR` (`newPassword` zu kurz).

### DELETE /me

Löscht das Konto mit allen Aktivitäten und Achievements. `204` ohne Inhalt.

### GET /sports

`200`: Liste von `Sport`.

### POST /activities

```json
{
  "sportCode": "RUNNING",
  "startedAt": "2026-10-04T09:30:00+02:00",
  "durationMin": 48,
  "latitude": 48.1642,
  "longitude": 11.6056,
  "description": "Schöne Runde",
  "visibility": "PRIVATE"
}
```
`latitude`, `longitude`, `description` optional. `visibility` optional, Standard `PRIVATE`.

`201`
```json
{
  "activity": { "id": 42, "username": "max_m", "mine": true, "sportCode": "RUNNING", "startedAt": "2026-10-04T09:30:00+02:00", "durationMin": 48, "caloriesKcal": 512, "points": 78, "latitude": 48.1642, "longitude": 11.6056, "description": "Schöne Runde", "visibility": "PRIVATE" },
  "pointsBreakdown": { "minutes": 48, "completed": 10, "newSport": 20, "weeklyGoal": 0, "total": 78 },
  "newAchievements": [
    { "code": "FIRST_ACTIVITY", "name": "Erste Schritte", "description": "Erste Aktivität eingetragen", "emoji": "🥉", "threshold": 1, "progress": 1, "unlockedAt": "2026-10-04T10:18:00+02:00" }
  ]
}
```
Fehler: `400 VALIDATION_ERROR` mit `field`:

| Feld | Regel |
|---|---|
| `sportCode` | unbekannte Sportart |
| `durationMin` | nicht 1–1440 |
| `startedAt` | mehr als 5 Minuten in der Zukunft |
| `latitude`, `longitude` | außerhalb des gültigen Bereichs oder nur eins von beiden gesetzt |
| `description` | länger als 500 Zeichen |

### GET /activities

Eigener Verlauf, neueste zuerst. Alle Parameter optional:

- `sport=RUNNING`
- `from=2026-10-01`, `to=2026-10-31` (beide einschließlich)

`200`: Liste von `Activity`.

### GET /activities/{id}

`200`: `Activity`. Fehler: `404 NOT_FOUND`, wenn es die Aktivität nicht gibt oder sie privat und fremd ist.

### DELETE /activities/{id}

`204` ohne Inhalt. Fehler: `403 FORBIDDEN` (fremde Aktivität), `404 NOT_FOUND`.

### GET /map/activities

Eigene Aktivitäten und öffentliche Aktivitäten anderer, jeweils nur mit Standort.
`200`: Liste von `Activity`.

### GET /achievements

`200`: Liste aller `Achievement`, freigeschaltete mit `unlockedAt`.

### GET /progress

`200`
```json
{
  "weeklyGoal": { "type": "MINUTES", "target": 150, "current": 96 },
  "streak": { "current": 3, "longest": 7 },
  "weekDays": [true, true, false, true, false, false, false]
}
```
`weekDays` steht für Montag bis Sonntag der laufenden Woche: Gab es an dem Tag mindestens eine Aktivität?

### GET /leaderboard

Parameter: `period=WEEK|MONTH`, `metric=POINTS|MINUTES` (Standard: `WEEK`, `POINTS`).

`200`
```json
{
  "period": "WEEK",
  "metric": "POINTS",
  "entries": [
    { "rank": 1, "username": "max_m", "value": 184 },
    { "rank": 2, "username": "lisa", "value": 162 },
    { "rank": 2, "username": "tom", "value": 162 },
    { "rank": 4, "username": "ali", "value": 148 }
  ],
  "me": { "rank": 2, "username": "lisa", "value": 162 }
}
```
Höchstens 50 Einträge. `me` fehlt, wenn man im Zeitraum keine Aktivität hat.

### GET /stats

`200`
```json
{
  "thisWeek": { "activities": 3, "minutes": 156, "kcal": 1420 },
  "minutesPerWeek": [ { "weekStart": "2026-08-31", "minutes": 120 } ],
  "activitiesPerSport": [ { "sportCode": "RUNNING", "count": 12 } ],
  "kcalPerMonth": [ { "month": "2026-05", "kcal": 3400 } ]
}
```
`minutesPerWeek` hat immer 8 Einträge (älteste zuerst, auch mit 0), `kcalPerMonth` immer 6.
````

- [ ] **Step 2: `README.md` anlegen**

````markdown
# TouchGrass

Sport-App für ein Berufsschulprojekt: Aktivitäten erfassen, auf der Karte sehen, Achievements sammeln.

- `app/` – Android-App (Kotlin, Jetpack Compose)
- `backend/` – REST-API (Kotlin, Ktor, PostgreSQL)
- `docs/api.md` – API-Vertrag zwischen App und Backend
- `docs/superpowers/specs/` – Entwurf, `docs/superpowers/plans/` – Umsetzungspläne

## Voraussetzungen

Nur Android Studio. Es bringt das passende JDK mit. Gradle muss dieses JDK finden, deshalb einmalig in PowerShell:

```powershell
setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"
```

Danach ein neues Terminal öffnen.

## Backend starten

```powershell
cd backend
.\gradlew.bat run
```

- Läuft, sobald `Responding at http://127.0.0.1:8080` erscheint. Test im Browser: <http://localhost:8080/api/health>
- Beim ersten Start entpackt das Backend ein eingebautes PostgreSQL (ca. 30 s). Die Daten liegen in `backend/data/` und bleiben erhalten. Zum Zurücksetzen das Backend stoppen und `backend/data/` löschen.
- Beenden mit Ctrl+C und `J`.
- Datenbank ansehen (z. B. in IntelliJ oder DBeaver): `localhost:5433`, Datenbank `postgres`, Benutzer `postgres`, kein Passwort.
- Eigenes PostgreSQL statt des eingebauten: Umgebungsvariablen `DB_URL` (z. B. `jdbc:postgresql://localhost:5432/touchgrass`), `DB_USER`, `DB_PASSWORD` setzen.
- Tests: `.\gradlew.bat test`

## App starten

1. Ordner `My_Application` in Android Studio öffnen.
2. Backend starten (siehe oben).
3. Emulator wählen und ▶ Run. Die App erreicht das Backend unter `http://10.0.2.2:8080/api/`, das ist aus Sicht des Emulators der eigene Rechner.

**Echtes Handy:** Handy und Laptop im selben WLAN. In `local.properties` (nicht im Git) eintragen:

```
api.baseUrl=http://<IP-des-Laptops>:8080/api/
```

Beim ersten Backend-Start fragt Windows eventuell nach der Firewall: Zugriff im privaten Netzwerk erlauben.

Unit-Tests der App: `.\gradlew.bat testDebugUnitTest` (im Wurzelordner).
````

- [ ] **Step 3: Commit**

```powershell
git add docs/api.md README.md
git commit -m "API-Vertrag und README" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Abschluss von M0

- [ ] Backend: `.\gradlew.bat test` im Ordner `backend` → 4 Tests grün.
- [ ] App: `.\gradlew.bat assembleDebug testDebugUnitTest` im Wurzelordner → 3 Tests grün.
- [ ] Emulator-Prüfung aus Task 7, Step 3 einmal komplett durchgespielt.
- [ ] `git status --short` zeigt nur noch die Änderung an `.idea/misc.xml`, die schon vor M0 da war.
