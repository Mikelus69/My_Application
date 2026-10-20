# TouchGrass – Entwurf

Sport-App für ein Berufsschulprojekt (zwei Azubis, 1–3 Monate). Android-App plus eigenes REST-Backend.

## 1. Ziel und Umfang

**MVP (fester Umfang)**

- Benutzerkonto: Registrieren, Login, Logout, Profil, Passwort ändern, Konto löschen
- Aktivität erfassen per Start/Stopp-Timer oder von Hand nachgetragen, mit geschätzten kcal
- Karte mit eigenen und öffentlichen Aktivitäten
- Verlauf mit Filtern
- Achievements
- Wochenziel und Streak
- Leaderboard (Punkte, Minuten)
- Statistiken mit Diagrammen

**Stretch Goals** (Architektur berücksichtigt sie, gebaut wird erst nach dem MVP, jeweils mit eigenem Entwurf)

- Freunde
- Fotos zu Aktivitäten
- GPS-Tracking während der Aktivität

**Nicht geplant:** Höhenmeter, Navigation/Routenplanung, Offline-Betrieb.

## 2. Architektur

```
 Android-App (app/)                     Backend (backend/)                Docker
┌──────────────────────┐   HTTP/JSON   ┌───────────────────────────┐    ┌────────────┐
│ Screen (Compose)     │   + JWT       │ Routes   (nur HTTP)       │    │ PostgreSQL │
│   ↕                  │ ────────────► │   ↓                       │    │            │
│ ViewModel (StateFlow)│               │ Services (Geschäftslogik) │    │            │
│   ↕                  │ ◄──────────── │   ↓                       │    │            │
│ ApiClient (Retrofit) │               │ Repositories (Exposed)  ──┼──► │            │
└──────────────────────┘               └───────────────────────────┘    └────────────┘
```

Grundsatz: Die App ist nur für Darstellung und Bedienung zuständig. Geschäftslogik und Daten liegen im Backend, das die einzige Wahrheit ist.

### Repository-Struktur

Ein Repo mit zwei unabhängigen Gradle-Projekten:

```
My_Application/
├── app/                 Android-App
├── backend/             Ktor-Backend, eigener Gradle-Build
├── docs/api.md          API-Vertrag zwischen App und Backend
├── docker-compose.yml   PostgreSQL + Backend
└── .env.example         Vorlage für Secrets (.env selbst ist in .gitignore)
```

Die JSON-Datenklassen existieren in App und Backend je einmal. Verbindlich ist `docs/api.md`.

### Android-App

| Thema | Entscheidung |
|---|---|
| Sprache/UI | Kotlin, Jetpack Compose, Material 3 |
| Paket / Name | `de.touchgrass`, App-Name „TouchGrass“ |
| Navigation | Navigation Compose, Bottom-Bar mit 4 Tabs |
| Zustand | Ein ViewModel je Screen, Zustand als `StateFlow` |
| Abhängigkeiten | Kein DI-Framework, ein `AppContainer` erzeugt API-Client, `TokenStore` und `TimerStore` einmal |
| HTTP | Retrofit + kotlinx.serialization, ein OkHttp-Interceptor setzt `Authorization: Bearer <JWT>` |
| Lokale Daten | DataStore nur für Token und Timer-Zustand, keine lokale Datenbank |
| Karte | MapLibre Compose mit OpenFreeMap-Style (OSM-Daten, kostenlos, kein API-Key) |
| Diagramme | Vico |
| Netzwerk | Klartext-HTTP nur im Debug-Build über eine Network Security Config erlaubt |

Hinweis: osmdroid ist seit November 2024 archiviert und wird deshalb nicht verwendet. MapLibre Compose ist eine 0.x-Version, die API kann sich ändern. Die Version wird fest gepinnt.

### Backend

| Thema | Entscheidung |
|---|---|
| Framework | Ktor (Netty) |
| JSON | ContentNegotiation + kotlinx.serialization |
| Auth | Ktor JWT-Plugin, Token 30 Tage gültig |
| Passwörter | BCrypt |
| Fehler | StatusPages, einheitliches Fehlerformat (siehe Abschnitt 5) |
| Datenbank | PostgreSQL, Zugriff über Exposed (DSL), Connection-Pool HikariCP |
| Schema | Flyway-Migrationen als SQL-Dateien |
| Schichten | `routes/` (HTTP) → `services/` (Regeln) → `repositories/` (DB) |
| Rechenlogik | Reine Funktionen ohne DB-Zugriff (`logic/`), damit sie einfach testbar sind |
| Zeitzone | Alle Tages- und Wochenberechnungen in `Europe/Berlin`, Woche = Montag 00:00 bis Sonntag 24:00 |

### Betrieb

- `docker compose up` startet PostgreSQL und das Backend auf Port 8080.
- Emulator: `http://10.0.2.2:8080`. Echtes Handy: LAN-IP des Laptops (beide im selben WLAN).
- JWT-Secret und DB-Passwort kommen aus `.env`.
- Im echten Betrieb wäre HTTPS Pflicht. Das gehört als Hinweis in die Doku.

## 3. Datenmodell

```
users
├─ id             BIGSERIAL PK
├─ username       VARCHAR(20) UNIQUE
├─ email          VARCHAR(254) UNIQUE
├─ password_hash  VARCHAR
├─ weight_kg      NUMERIC(5,1) NULL
├─ goal_type      VARCHAR  MINUTES | ACTIVITIES | KCAL   (Standard: MINUTES)
├─ goal_target    INT                                    (Standard: 150)
└─ created_at     TIMESTAMPTZ

sport_types                       (per Flyway befüllt)
├─ code    VARCHAR PK
├─ name    VARCHAR
├─ emoji   VARCHAR
└─ met     NUMERIC(4,1)

activities
├─ id             BIGSERIAL PK
├─ user_id        FK → users ON DELETE CASCADE
├─ sport_code     FK → sport_types
├─ started_at     TIMESTAMPTZ
├─ duration_min   INT  (1–1440)
├─ calories_kcal  INT  (beim Anlegen berechnet)
├─ points         INT  (beim Anlegen berechnet)
├─ goal_bonus     BOOLEAN  (hat diese Aktivität den Wochenziel-Bonus bekommen?)
├─ latitude       DOUBLE PRECISION NULL
├─ longitude      DOUBLE PRECISION NULL   (beide gesetzt oder beide NULL)
├─ description    VARCHAR(500) NULL
├─ visibility     VARCHAR  PRIVATE | PUBLIC   (Standard: PRIVATE)
└─ created_at     TIMESTAMPTZ

achievements                      (per Flyway befüllt)
├─ code         VARCHAR PK
├─ name         VARCHAR
├─ description  VARCHAR
├─ emoji        VARCHAR
├─ requirement  VARCHAR  TOTAL_ACTIVITIES | TOTAL_MINUTES | TOTAL_KCAL | SPORT_ACTIVITIES | STREAK_DAYS
├─ threshold    INT
└─ sport_code   FK → sport_types NULL  (nur bei SPORT_ACTIVITIES)

user_achievements
├─ user_id           FK → users ON DELETE CASCADE
├─ achievement_code  FK → achievements
├─ unlocked_at       TIMESTAMPTZ
└─ PK (user_id, achievement_code)
```

### Startdaten

| code | Name | Emoji | MET |
|---|---|---|---|
| WALKING | Gehen | 🚶 | 3,5 |
| RUNNING | Joggen | 🏃 | 8,0 |
| CYCLING | Radfahren | 🚴 | 7,0 |
| SWIMMING | Schwimmen | 🏊 | 6,0 |
| FOOTBALL | Fußball | ⚽ | 7,0 |
| BASKETBALL | Basketball | 🏀 | 6,5 |
| HIKING | Wandern | 🥾 | 5,0 |
| STRENGTH | Krafttraining | 🏋️ | 5,0 |

| code | Name | Regel |
|---|---|---|
| FIRST_ACTIVITY | 🥉 Erste Schritte | TOTAL_ACTIVITIES ≥ 1 |
| KCAL_1000 | 🔥 Feuer und Flamme | TOTAL_KCAL ≥ 1000 |
| RUNNER_10 | 🏃 Läufer | SPORT_ACTIVITIES (RUNNING) ≥ 10 |
| MINUTES_500 | ⏱️ Ausdauer | TOTAL_MINUTES ≥ 500 |
| STREAK_7 | 🌟 Sportwoche | STREAK_DAYS ≥ 7 |
| MARATHON | 🏆 Marathon | TOTAL_MINUTES ≥ 2520 (42 h) |

### Entscheidungen

- Sportarten und Achievements liegen in der Datenbank. Eine neue Sportart oder ein neues Achievement mit einem der fünf Regeltypen ist eine SQL-Zeile und braucht weder Code-Änderung noch App-Update.
- kcal und Punkte werden beim Anlegen gespeichert. Ändert jemand später sein Gewicht, bleiben alte Aktivitäten unverändert.
- Der Standort ist optional (Datensparsamkeit). Aktivitäten ohne Standort erscheinen nicht auf der Karte.
- Ein freigeschaltetes Achievement bleibt, auch wenn die auslösende Aktivität gelöscht wird.
- Aktivitäten werden im MVP nicht bearbeitet, nur angelegt und gelöscht.

### Erweiterungen für die Stretch Goals

Jede Erweiterung ist eine neue Flyway-Migration. Bestehende Migrationen werden nie verändert.

- Freunde: Tabelle `friendships` (requester_id, addressee_id, status PENDING/ACCEPTED, created_at), neuer Sichtbarkeitswert `FRIENDS`
- Fotos: Spalte `activities.image_path`, die Dateien liegen in einem Docker-Volume
- GPS-Tracking: Tabelle `gps_points` (activity_id, seq, latitude, longitude, recorded_at)

## 4. Geschäftslogik (Backend)

Alle Regeln sind reine Funktionen in `logic/`.

### kcal-Schätzung

```
kcal = round(MET × Gewicht_kg × Dauer_min / 60)
```

- Ist kein Gewicht hinterlegt, wird mit 70 kg gerechnet. Die App weist dann darauf hin, dass ein eingetragenes Gewicht die Schätzung genauer macht.
- Die App zeigt den Wert immer als Schätzung an: „~480 kcal“.

### Punkte

Die Punkte werden beim Anlegen einer Aktivität berechnet:

| Regel | Punkte |
|---|---|
| je Minute | +1 |
| Aktivität abgeschlossen | +10 |
| erste Aktivität dieser Sportart für diese Person | +20 |
| Wochenziel wird mit dieser Aktivität erreicht | +50 |

Wochenziel-Bonus: Maßgeblich ist die Woche von `started_at`. Den Bonus gibt es, wenn der Fortschritt dieser Woche vor der Aktivität unter dem Ziel lag, danach mindestens beim Ziel liegt und in dieser Woche noch keine Aktivität `goal_bonus = true` hat. So bringt mehrfaches Hochsetzen des Ziels keinen zweiten Bonus.

Die Antwort auf `POST /activities` enthält die Aufschlüsselung (Minuten, Aktivität, neue Sportart, Wochenziel).

### Achievements

Nach jeder neuen Aktivität:

1. `UserStats` ermitteln: Anzahl Aktivitäten, Summe Minuten, Summe kcal, Anzahl je Sportart, längste Serie.
2. Jedes noch nicht freigeschaltete Achievement gegen seine Regel prüfen (`Wert ≥ threshold`).
3. Neu erfüllte in `user_achievements` eintragen und in der Antwort zurückgeben.

Für `GET /achievements` liefert dieselbe Auswertung auch den Fortschritt gesperrter Achievements, z. B. 7/10.

### Streak

- Grundlage ist die Menge der Kalendertage (Europe/Berlin), an denen die Person mindestens eine Aktivität hat.
- **Aktuelle Serie:** aufeinanderfolgende Tage bis heute. War heute noch keine Aktivität, wird ab gestern gezählt, damit die Serie erst nach Mitternacht reißt.
- **Längste Serie:** längste Folge aufeinanderfolgender Tage überhaupt. Nachgetragene Aktivitäten zählen mit. Sie ist der Wert für `STREAK_DAYS`.

### Wochenziel

Der aktuelle Wert ist die Summe der Minuten, die Anzahl der Aktivitäten oder die Summe der kcal in der laufenden Woche, je nach `goal_type`.

### Leaderboard

- Zeitraum: laufende Woche oder laufender Monat
- Wertung: Summe `points` oder Summe `duration_min`. kcal wird bewusst nicht gewertet, weil sonst das Körpergewicht entscheidet.
- Es erscheinen alle Personen mit mindestens einer Aktivität im Zeitraum.
- Ausgegeben werden die Top 50 und der eigene Platz. Gleicher Wert bedeutet gleichen Rang (1, 2, 2, 4).

### Statistiken

| Kennzahl | Zeitraum |
|---|---|
| Zusammenfassung: Anzahl, Minuten, kcal | laufende Woche |
| Minuten pro Woche | letzte 8 Wochen |
| Aktivitäten je Sportart | gesamt |
| kcal pro Monat | letzte 6 Monate |

### Bewusste Grenzen (für die Doku)

- Eingaben werden nicht auf Plausibilität geprüft. Wer unrealistische Zeiten einträgt, steigt im Leaderboard.
- JWTs lassen sich nicht vorzeitig sperren. Logout löscht nur den Token in der App.

## 5. REST-API

Basis-Pfad `/api`, JSON. Außer `/auth/*` brauchen alle Aufrufe `Authorization: Bearer <JWT>`. Die Details mit Beispiel-JSON stehen in `docs/api.md`, das in M0 entsteht.

### Konto

| Methode | Pfad | Zweck |
|---|---|---|
| POST | `/auth/register` | Konto anlegen (`username`, `email`, `password`), liefert `token` + `user` |
| POST | `/auth/login` | `login` (Benutzername oder E-Mail) + `password`, liefert `token` + `user` |
| GET | `/me` | Eigenes Profil inkl. Gewicht und Wochenziel |
| PATCH | `/me` | `weightKg`, `goalType`, `goalTarget` ändern |
| PUT | `/me/password` | `oldPassword`, `newPassword` |
| DELETE | `/me` | Konto mit allen Aktivitäten löschen |

### Aktivitäten

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/sports` | Sportarten (`code`, `name`, `emoji`, `met`) |
| POST | `/activities` | Anlegen, liefert `activity`, `pointsBreakdown`, `newAchievements` |
| GET | `/activities?sport=&from=&to=` | Eigener Verlauf, neueste zuerst |
| GET | `/activities/{id}` | Details (eigene oder öffentliche) |
| DELETE | `/activities/{id}` | Nur eigene |
| GET | `/map/activities` | Eigene + öffentliche anderer, nur mit Standort, inkl. `username` |

### Erfolge und Auswertung

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/achievements` | Alle Achievements mit `unlockedAt` bzw. `progress`/`threshold` |
| GET | `/progress` | Wochenziel (`type`, `target`, `current`), Streak (`current`, `longest`), `weekDays` (7 × bool, Mo–So) |
| GET | `/leaderboard?period=WEEK\|MONTH&metric=POINTS\|MINUTES` | `entries` (`rank`, `username`, `value`) + `me` |
| GET | `/stats` | Diagrammdaten aus Abschnitt 4 |

### Fehlerformat

```json
{ "error": "EMAIL_TAKEN", "message": "Diese E-Mail ist bereits registriert.", "field": "email" }
```

`field` gibt es nur bei Eingabefehlern.

| Status | Wann | Beispiel-Codes |
|---|---|---|
| 400 | Eingabe ungültig | `VALIDATION_ERROR` |
| 401 | Token fehlt/abgelaufen, Login falsch | `UNAUTHORIZED`, `INVALID_CREDENTIALS` |
| 403 | Fremde Aktivität | `FORBIDDEN` |
| 404 | Nicht gefunden | `NOT_FOUND` |
| 409 | Konflikt | `USERNAME_TAKEN`, `EMAIL_TAKEN` |

Bei 401 auf einem geschützten Aufruf löscht die App den Token und springt zum Login.

### Prüfregeln

| Feld | Regel |
|---|---|
| username | 3–20 Zeichen, `[a-zA-Z0-9_]` |
| email | gültiges Format |
| password | mindestens 8 Zeichen |
| durationMin | 1–1440 |
| startedAt | nicht in der Zukunft (5 Minuten Toleranz) |
| latitude / longitude | −90…90 / −180…180, nur zusammen |
| description | höchstens 500 Zeichen |
| weightKg | 30–300 |
| goalTarget | größer als 0 |

Keine Pagination: Der Verlauf liefert alle passenden Einträge.

## 6. App

### Start

Liegt ein Token im `TokenStore`, öffnet die App die Karte, sonst Login (mit Wechsel zu Registrieren).

### Navigation

Bottom-Bar: 🗺️ Karte · ➕ Aktivität · 🏆 Erfolge · 👤 Profil

### 🗺️ Karte

- Eigene Marker in Akzentfarbe, öffentliche anderer in Grau.
- Tippen öffnet ein Bottom Sheet mit Sportart, Dauer, kcal, Datum, Beschreibung und Benutzername.
- Die Karte startet über der letzten eigenen Aktivität, sonst über Deutschland.
- Der Knopf „📍 Mein Standort“ fragt erst beim Antippen nach der Standortberechtigung.

### ➕ Aktivität

**Timer-Screen**

- Sportart als Chips, große Zeitanzeige, ▶ Start / ⏸ Pause / ▶ Weiter / ⏹ Stopp.
- Link „Ohne Timer nachtragen“ öffnet das Formular leer.
- Läuft der Timer, zeigt der ➕-Tab die laufende Zeit an.

**Timer-Logik (nur App)**

- `TimerStore` speichert in DataStore: Sportart, Startzeitpunkt, Summe der Pausen und ggf. Beginn der aktuellen Pause.
- Die angezeigte Dauer ist `jetzt − Start − Pausen`. Der Timer übersteht dadurch App-Schließen und Neustart ohne Foreground Service.
- Die Rechnung ist eine reine Funktion (testbar).

**Speichern-Formular**

| Feld | Inhalt |
|---|---|
| Sportart | Auswahl |
| Datum/Uhrzeit | vom Timer vorausgefüllt |
| Dauer | vom Timer vorausgefüllt, änderbar |
| Ort | auf kleiner Karte tippen, „📍 Aktueller Standort“ oder „Ohne Standort“ |
| Beschreibung | optional |
| Sichtbarkeit | 🔒 Privat (Standard) / 🌎 Öffentlich |

Nach dem Speichern erscheint eine Meldung mit kcal und Punkte-Aufschlüsselung, neue Achievements kommen als Dialog. Der Timer wird erst nach erfolgreichem Speichern zurückgesetzt, oder wenn man im Formular „Verwerfen“ wählt (mit Rückfrage).

### 🏆 Erfolge

Drei Tabs:

- **Übersicht:** Wochenziel-Fortschrittsbalken, Streak mit Mo–So-Häkchen, aktuelle und längste Serie
- **Achievements:** Raster, gesperrte ausgegraut mit Fortschritt
- **Leaderboard:** Umschalter Woche/Monat und Punkte/Minuten, eigener Platz hervorgehoben

### 👤 Profil

Kopf mit Benutzername und den Zahlen dieser Woche, darunter drei Einträge:

- **Statistik:** vier Vico-Diagramme
- **Verlauf:** Liste mit Filtern (Sportart, Zeitraum). Tippen öffnet die Details, dort ist Löschen mit Rückfrage möglich.
- **Einstellungen:** Gewicht, Wochenziel, Passwort ändern, Logout, Konto löschen (mit Rückfrage)

### Gemeinsame Muster

- Jeder Screen-Zustand ist `Loading`, `Content` oder `Error` (Meldung + „Erneut versuchen“).
- Fehlertexte kommen aus dem `message` der API, Netzwerkfehler bekommen einen eigenen Text („Server nicht erreichbar“).

### Paket-Struktur

```
de.touchgrass
├── data/           SportApi, dto/, TokenStore, TimerStore
├── ui/auth/
├── ui/map/
├── ui/record/      Timer + Formular
├── ui/achievements/
├── ui/profile/     Statistik, Verlauf, Einstellungen
├── AppContainer.kt
├── MainActivity.kt
└── Navigation.kt
```

### Berechtigungen

- `INTERNET`
- `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, erst bei Bedarf zur Laufzeit angefragt

## 7. Datenschutz und Sicherheit

- Passwörter nur als BCrypt-Hash, nie im Log.
- Secrets in `.env`, nicht im Git.
- Aktivitäten standardmäßig privat. Andere sehen nur öffentliche Aktivitäten auf der Karte und im Leaderboard nur Benutzername und Wert.
- Standort und Gewicht sind freiwillig.
- Konto löschen entfernt alle personenbezogenen Daten (Recht auf Löschung).
- Die Doku enthält eine Tabelle: welche Daten, wozu, wer sie sieht.

| Datum | Zweck | Sichtbar für |
|---|---|---|
| Benutzername | Anmeldung, Leaderboard, öffentliche Marker | alle Nutzer |
| E-Mail | Anmeldung | nur Backend |
| Passwort-Hash | Anmeldung | nur Backend |
| Gewicht | kcal-Schätzung | nur die Person selbst |
| Aktivitäten inkl. Standort | Kernfunktion | die Person selbst, bei „öffentlich“ alle |

## 8. Tests

### Backend

- **Unit-Tests der reinen Funktionen:** kcal, Punkte inkl. Wochenziel-Bonus (auch zweimaliges Hochsetzen des Ziels), Achievement-Prüfung, aktuelle und längste Serie, Wochengrenzen inkl. Zeitumstellung.
- **API-Tests** mit Ktor `testApplication` gegen PostgreSQL per Testcontainers:
  - Registrieren und Login, doppelte E-Mail ergibt 409
  - Aktivität anlegen, Punkte und neue Achievements in der Antwort
  - Fremde Aktivität löschen ergibt 403
  - Ungültige Eingaben ergeben 400 mit `field`
  - Ohne Token ergibt 401

### App

- Unit-Tests der Timer-Rechnung (Start, Pausen, Fortsetzen nach Neustart).
- Manuelle Test-Checkliste für alle Screens, gleichzeitig Demo-Ablauf für die Präsentation.

## 9. Meilensteine

Jeder Meilenstein endet mit etwas Vorführbarem und bekommt einen eigenen Umsetzungsplan. Ab M1 hat jeder Meilenstein eine Backend- und eine App-Hälfte, verbunden über `docs/api.md`.

| # | Meilenstein | Ergebnis |
|---|---|---|
| M0 | Grundgerüst | `backend/` mit Ktor, Docker Compose, Flyway, `GET /health`; App umbenannt in TouchGrass (`de.touchgrass`), Bottom-Navigation mit leeren Tabs, Retrofit angebunden; `docs/api.md` |
| M1 | Konten | Registrieren, Login, Logout, Profil, Passwort ändern, Konto löschen |
| M2 | Aktivitäten | Sportarten, Formular (manuell), kcal und Punkte, Verlauf mit Filtern, Details, Löschen |
| M3 | Timer und Karte | Timer-Screen, Ortswahl auf der Karte, Karten-Tab mit Markern |
| M4 | Erfolge | Achievements, Wochenziel, Streak, Leaderboard |
| M5 | Statistik und Feinschliff | Diagramme, Einstellungen, leere und Fehler-Zustände, Doku |
| danach | Stretch Goals | Freunde → Fotos → GPS-Tracking, jeweils mit eigenem Entwurf |
