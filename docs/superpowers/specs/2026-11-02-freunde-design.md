# TouchGrass – Entwurf „Freunde“

Erstes Stretch Goal nach dem MVP. Grundlage ist der Hauptentwurf `2026-10-20-touchgrass-design.md`.

## 1. Ziel und Umfang

- Freundschaftsanfragen per Benutzername, Annehmen oder Ablehnen, Freundschaft beenden
- Dritte Sichtbarkeit „👥 Freunde“: Diese Aktivitäten sehen nur Freunde, auf der Karte und in den Details
- Leaderboard umschaltbar zwischen allen und den eigenen Freunden
- Hinweis auf offene Anfragen im Profil

**Nicht geplant:** Suche mit Vorschlägen, Hinzufügen aus Leaderboard oder Karte, Feed mit den Aktivitäten der Freunde, Push-Benachrichtigungen, Profile oder Statistiken anderer ansehen, Nutzer sperren.

## 2. Regeln

### Freundschaft

- Eine Anfrage geht an einen Benutzernamen, Groß- und Kleinschreibung egal, Leerzeichen am Rand zählen nicht.
- Die andere Person sieht sie unter „Anfragen an dich“ und nimmt sie an oder lehnt sie ab.
- Hat die andere Person selbst schon eine Anfrage geschickt, sind beide sofort befreundet.
- Ablehnen, Zurückziehen der eigenen offenen Anfrage und Beenden einer Freundschaft löschen die Verbindung. Danach ist eine neue Anfrage möglich.
- Konto löschen entfernt alle Freundschaften und Anfragen dieser Person.

### Sichtbarkeit

| Sichtbarkeit | Wer sieht die Aktivität auf der Karte und in den Details |
|---|---|
| 🔒 Privat (Standard) | nur die Person selbst |
| 👥 Freunde | die Person selbst und ihre Freunde |
| 🌎 Öffentlich | alle |

- Es zählen nur angenommene Freundschaften. Eine offene Anfrage gibt keine Sicht frei.
- Endet eine Freundschaft, sind die „👥 Freunde“-Aktivitäten für die andere Person sofort nicht mehr sichtbar.
- Die Sichtbarkeit wird beim Anlegen gewählt. Aktivitäten werden weiterhin nicht bearbeitet.

### Leaderboard

- Neuer Umschalter „Alle | Freunde“, Standard „Alle“.
- „Freunde“ wertet nur die eigene Person und ihre Freunde. Zeitraum, Wertung und Ränge folgen denselben Regeln wie bei „Alle“.
- Wie bei „Alle“ zählen alle Aktivitäten, auch private. Sichtbar sind nur Benutzername und Summe.

## 3. Backend

### Datenmodell

Neue Migration `V6__freunde.sql`:

```sql
CREATE TABLE friendships (
    requester_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (requester_id, addressee_id),
    CHECK (requester_id <> addressee_id)
);

CREATE UNIQUE INDEX friendships_pair ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));
CREATE INDEX friendships_addressee ON friendships (addressee_id);
```

- `status` ist `PENDING` oder `ACCEPTED`.
- Der eindeutige Index erlaubt je Paar nur eine Zeile, egal wer angefragt hat. Das gilt auch, wenn sich zwei gleichzeitig anfragen.
- `activities.visibility` bekommt den Wert `FRIENDS`. Die Spalte (`VARCHAR(10)`) bleibt unverändert.

### Schnittstelle

| Methode | Pfad | Zweck | Antwort |
|---|---|---|---|
| GET | `/friends` | eigene Freunde und Anfragen | 200 `FriendsDto` |
| POST | `/friends/requests` | `{ "username": "lisa_k" }` anfragen, bei Gegenanfrage sofort befreundet | 201 `FriendsDto` |
| POST | `/friends/requests/{username}/accept` | Anfrage von `username` annehmen | 200 `FriendsDto` |
| DELETE | `/friends/requests/{username}` | Anfrage von `username` ablehnen oder eigene an `username` zurückziehen | 200 `FriendsDto` |
| DELETE | `/friends/{username}` | Freundschaft beenden | 200 `FriendsDto` |
| GET | `/leaderboard?period=…&metric=…&scope=ALL\|FRIENDS` | neuer Parameter `scope`, ohne Angabe `ALL` | wie bisher |

```json
{ "friends": ["lisa_k", "max"], "incoming": ["tom_b"], "outgoing": ["anna"] }
```

Die Listen sind alphabetisch sortiert, ohne Rücksicht auf Groß- und Kleinschreibung. Jeder schreibende Aufruf liefert den neuen Stand, die App muss danach nichts nachladen.

| Fall | Status | Code | `field` | Meldung |
|---|---|---|---|---|
| Name leer | 400 | `VALIDATION_ERROR` | `username` | Bitte einen Benutzernamen eingeben. |
| Anfrage an sich selbst | 400 | `VALIDATION_ERROR` | `username` | Du kannst dir nicht selbst eine Anfrage schicken. |
| Name unbekannt | 404 | `USER_NOT_FOUND` | `username` | Diesen Benutzernamen gibt es nicht. |
| schon befreundet | 409 | `ALREADY_FRIENDS` | `username` | Ihr seid schon befreundet. |
| eigene Anfrage schon offen | 409 | `REQUEST_PENDING` | `username` | Du hast schon eine Anfrage geschickt. |
| Annehmen oder Löschen ohne passende Anfrage | 404 | `NOT_FOUND` | – | Anfrage nicht gefunden. |
| Beenden ohne Freundschaft | 404 | `NOT_FOUND` | – | Ihr seid nicht befreundet. |
| `scope` ungültig | 400 | `VALIDATION_ERROR` | `scope` | Auswahl bitte als ALL oder FRIENDS angeben. |

Bei den Aufrufen mit `{username}` im Pfad zählt Groß- und Kleinschreibung ebenfalls nicht, und ein unbekannter Name gilt wie eine fehlende Anfrage oder Freundschaft.

### Änderungen am Bestehenden

- Die Sichtbarkeitsregel steht an genau einer Stelle: als Abfrage-Bedingung im `ActivityRepository` (eigene, `PUBLIC`, oder `FRIENDS` von einem Freund). Karte und Details nutzen beide diese Bedingung.
- `ActivityDto` bekommt neben `mine` das Feld `friend`: `true`, wenn die Aktivität von einem Freund stammt.
- `POST /activities` nimmt `visibility: "FRIENDS"` an.
- Das Leaderboard schränkt bei `FRIENDS` die Personen auf die eigene und die Freunde ein. Sonst rechnet es wie bisher.
- Neues Paket `friends/` mit `FriendshipRepository`, `FriendService` (Regeln aus Abschnitt 2) und `FriendDtos`, dazu `routes/FriendRoutes.kt`, nach demselben Muster wie die anderen Bereiche.

## 4. App

### Freunde-Screen

Erreichbar aus dem Profil, mit „← Zurück“ wie bei der Statistik.

- Oben das Feld „Benutzername“ und der Knopf „Anfrage senden“. Ein leeres Feld meldet die App selbst, alle anderen Fehler stehen mit der Meldung der API am Feld.
- Nach dem Senden steht „Anfrage an lisa_k gesendet.“ oder, bei Gegenanfrage, „Du und lisa_k seid jetzt Freunde.“ Nach dem Annehmen steht ebenfalls „Du und lisa_k seid jetzt Freunde.“
- „Anfragen an dich“, nur wenn es welche gibt: Name mit „Annehmen“ und „Ablehnen“.
- „Deine Freunde“: Name mit „Entfernen“. Vorher fragt die App „Freundschaft mit lisa_k beenden?“ mit „Beenden“ und „Abbrechen“.
- „Gesendete Anfragen“, nur wenn es welche gibt: Name mit „Zurückziehen“.
- Ohne Freunde und Anfragen: „Noch keine Freunde. Schick jemandem eine Anfrage über seinen Benutzernamen.“
- Laden, Fehler mit „Erneut versuchen“ und der Hinweis beim Aufwachen des Servers wie auf den anderen Screens.

### Änderungen an bestehenden Screens

| Screen | Änderung |
|---|---|
| 👤 Profil | Neuer erster Eintrag „👥 Freunde“ über Statistik, Verlauf und Einstellungen. Bei offenen Anfragen steht darunter „1 offene Anfrage“ bzw. „2 offene Anfragen“. |
| ➕ Formular | Drei Chips „🔒 Privat“, „👥 Freunde“, „🌎 Öffentlich“, Standard Privat |
| Details | zeigt auch „👥 Freunde“ |
| 🗺️ Karte | Eigene Marker in `primary`, die von Freunden in `tertiary` aus dem Farbschema, alle anderen grau. Im Bottom Sheet steht „👥 Freund“ beim Benutzernamen. |
| 🏆 Leaderboard | Dritte Chip-Reihe „Alle“ und „Freunde“. Leer bei „Freunde“: „Du und deine Freunde habt in diesem Zeitraum noch keinen Sport eingetragen.“ |

### Code

- `SportApi`: `friends()`, `sendFriendRequest()`, `acceptFriendRequest()`, `deleteFriendRequest()`, `removeFriend()`, `leaderboard()` mit `scope`
- `data/dto/`: `FriendsDto`, `SendFriendRequest`, `Visibility.FRIENDS`, `ActivityDto.friend`, `LeaderboardScope`
- `ui/friends/`: `FriendsScreen`, `FriendsViewModel`, Route `FriendsRoute` im Profil-Graphen
- Profil lädt zusätzlich `GET /friends` für die Zahl der offenen Anfragen.

## 5. Datenschutz

- Freundschaften und Anfragen sehen nur die beiden Beteiligten. Die App zeigt nirgends die Freunde anderer.
- „👥 Freunde“-Aktivitäten sehen nur angenommene Freunde.
- Eine Anfrage verrät, ob es einen Benutzernamen gibt. Das verrät schon die Registrierung, und die Namen stehen ohnehin im Leaderboard.
- Die Datenschutz-Tabelle bekommt die Zeile „Freundschaften und Anfragen | Freunde-Funktion | die beiden Beteiligten“. Bei den Aktivitäten kommt „bei ‚👥 Freunde‘ die Freunde“ dazu.

## 6. Tests

**Backend** (API-Tests gegen das eingebettete PostgreSQL)

- Anfrage, Annehmen, Ablehnen, Zurückziehen, Beenden, Gegenanfrage
- alle Fehlerfälle aus der Tabelle in Abschnitt 3
- Sichtbarkeit einer „👥 Freunde“-Aktivität auf der Karte und in den Details: für den Freund ja, für Fremde nein, bei offener Anfrage nein, nach dem Beenden nein
- Feld `friend` in den Antworten
- Leaderboard mit `scope=FRIENDS` enthält nur die eigene Person und die Freunde
- Konto löschen entfernt die Freundschaften

**App** (ViewModel-Tests mit `FakeSportApi`)

- Freunde-Screen: Laden, Senden mit Erfolg, mit Fehler am Feld und mit Gegenanfrage, Annehmen, Ablehnen, Zurückziehen, Entfernen
- Profil: Zahl der offenen Anfragen
- Leaderboard: Umschalten auf „Freunde“ fragt `scope=FRIENDS` ab
- Formular: „👥 Freunde“ wird mitgeschickt

**Von Hand:** Die Testcheckliste bekommt den Abschnitt „Freunde“ für zwei Handys, darin auch die Markerfarben.

## 7. Doku und Auslieferung

- `docs/api.md`: neue Aufrufe, Feld `friend`, Parameter `scope`, neue Fehlercodes
- README: Datenschutz-Tabelle und unter „Bewusste Grenzen“: Anfragen lösen keine Benachrichtigung aus, man sieht sie im Profil.
- Der Hauptentwurf wird auf den neuen Stand gebracht.
- Auslieferung: erst pushen, Render baut neu und Flyway legt `friendships` bei Neon an. Danach beide Handys aktualisieren. Alte App-Versionen laufen weiter, kennen aber keine Freunde.
