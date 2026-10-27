# TouchGrass

Sport-App für ein Berufsschulprojekt: Aktivitäten erfassen, auf der Karte sehen, Achievements sammeln.

- `app/` – Android-App (Kotlin, Jetpack Compose)
- `backend/` – REST-API (Kotlin, Ktor, PostgreSQL)
- `docs/api.md` – API-Vertrag zwischen App und Backend
- `docs/testcheckliste.md` – Test von Hand für alle Screens, zugleich Ablauf der Präsentation
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
- Beim ersten Start entpackt das Backend ein eingebautes PostgreSQL (ca. 30 s). Die Daten liegen in `backend/data/` und bleiben erhalten.
- Beenden mit Ctrl+C und `J`. Das eingebaute PostgreSQL läuft danach im Hintergrund weiter, weil Gradle den Server hart beendet. Der nächste Start räumt es automatisch weg. Ganz beenden: `Stop-Process -Name postgres`.
- Das Backend läuft nur einmal. Ist es schon gestartet (anderes Fenster oder Terminal-Tab), bricht ein zweiter Start mit „Das Backend ist schon gestartet …“ ab und lässt das laufende in Ruhe.
- Datenbank zurücksetzen: Backend beenden, `Stop-Process -Name postgres`, dann `backend/data/` löschen.
- Datenbank ansehen (z. B. in IntelliJ oder DBeaver): `localhost:5433`, Datenbank `postgres`, Benutzer `postgres`, kein Passwort.
- Eigenes PostgreSQL statt des eingebauten: Umgebungsvariablen `DB_URL` (z. B. `jdbc:postgresql://localhost:5432/touchgrass`), `DB_USER`, `DB_PASSWORD` setzen.
- Login-Tokens werden mit einem geheimen Schlüssel signiert. Ohne Umgebungsvariable `JWT_SECRET` erzeugt das Backend beim ersten Start einen zufälligen Schlüssel in `backend/data/jwt-secret`. Wer diese Datei löscht, meldet alle Nutzer ab.
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

**Karte und Standort:** Die Kartenbilder kommen von OpenFreeMap aus dem Internet, ohne Netz bleibt die Karte leer. Nach dem Standort fragt die App erst, wenn man „📍 Mein Standort“ oder im Formular „📍 Aktueller Standort“ antippt. Im Emulator stellt man den Standort unter **⋮ (Extended Controls) → Location** ein.

Unit-Tests der App: `.\gradlew.bat testDebugUnitTest` (im Wurzelordner).

## Roter Code in Android Studio, obwohl der Build klappt

- Android Studio arbeitet mit einem gespeicherten Abbild der Gradle-Dateien. Nach `git pull` oder neuen Bibliotheken ist das veraltet, und Imports werden rot. Abhilfe: **File → Sync Project with Gradle Files** (Elefanten-Symbol oben rechts).
- `backend/` ist ein eigenes Gradle-Projekt und gehört nicht zum Android-Projekt. Zum Bearbeiten den Ordner `backend` in einem zweiten Fenster öffnen (**File → Open → `backend`**), sonst ist dort alles rot.

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
