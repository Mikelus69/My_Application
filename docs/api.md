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
| 401 | `INVALID_CREDENTIALS` | Login falsch (nur `POST /auth/login`) |
| 403 | `FORBIDDEN` | Fremde Aktivität löschen |
| 404 | `NOT_FOUND` | Pfad oder Objekt existiert nicht (auch: private Aktivität anderer) |
| 409 | `USERNAME_TAKEN`, `EMAIL_TAKEN` | Benutzername oder E-Mail schon vergeben |
| 500 | `INTERNAL_ERROR` | Unerwarteter Serverfehler |

Eine `401` bedeutet außer beim Login immer: Token fehlt oder ist ungültig. Die App meldet sich dann ab. Alle anderen Fehleingaben sind `400`.

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
Freigeschaltete haben zusätzlich `"unlockedAt": "2026-10-04T10:18:00+02:00"`. `progress` geht höchstens bis `threshold`, freigeschaltete haben immer `progress = threshold` (auch wenn Aktivitäten später gelöscht wurden).

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
Fehler: `400 VALIDATION_ERROR` (`field`: `username` 3–20 Zeichen aus `a-z A-Z 0-9 _`, `email`, `password` 8–64 Zeichen), `409 USERNAME_TAKEN`, `409 EMAIL_TAKEN`. Benutzernamen sind ohne Beachtung von Groß- und Kleinschreibung eindeutig, E-Mails werden kleingeschrieben gespeichert.

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
`204` ohne Inhalt. Fehler: `400 VALIDATION_ERROR` mit `field` `oldPassword` (bisheriges Passwort falsch) oder `newPassword` (nicht 8–64 Zeichen).

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
`newAchievements` enthält die Achievements, die diese Aktivität freigeschaltet hat, sonst `[]`.

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

Eigene Aktivitäten und öffentliche Aktivitäten anderer, jeweils nur mit Standort, neueste zuerst.
`200`: Liste von `Activity`.

### GET /achievements

`200`: Liste aller `Achievement`, freigeschaltete mit `unlockedAt`, sortiert nach `threshold`. Der Aufruf schaltet auch Achievements frei, die schon erfüllt, aber noch nicht eingetragen sind (z. B. durch Aktivitäten aus der Zeit vor den Achievements).

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
`streak.current` zählt die Tage in Folge bis heute, ohne Aktivität heute ab gestern. `streak.longest` ist die längste Folge überhaupt.

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
Höchstens 50 Einträge, bei gleichem Wert alphabetisch. `me` fehlt, wenn man im Zeitraum keine Aktivität hat. Private Aktivitäten zählen mit, zu sehen sind nur Benutzername und Wert. Fehler: `400 VALIDATION_ERROR` mit `field` `period` oder `metric` bei unbekanntem Wert.

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
