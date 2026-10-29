# TouchGrass

Sport-App für ein Berufsschulprojekt: Aktivitäten erfassen, auf der Karte sehen, Achievements sammeln.

- `app/` – Android-App (Kotlin, Jetpack Compose)
- `backend/` – REST-API (Kotlin, Ktor, PostgreSQL)
- `docs/api.md` – API-Vertrag zwischen App und Backend
- `docs/testcheckliste.md` – Test von Hand für alle Screens, zugleich Ablauf der Präsentation
- `render.yaml`, `backend/Dockerfile` – Betrieb des Backends in der Cloud bei Render
- `docs/superpowers/specs/` – Entwurf, `docs/superpowers/plans/` – Umsetzungspläne

## Voraussetzungen

Nur Android Studio. Es bringt das passende JDK mit. Gradle muss dieses JDK finden, deshalb einmalig in PowerShell:

```powershell
setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"
```

Danach ein neues Terminal öffnen.

## Backend in der Cloud

Die App spricht das Backend unter `https://touchgrass-backend.onrender.com/api/` an. Ob es läuft, zeigt <https://touchgrass-backend.onrender.com/api/health> mit `{"status":"ok","database":"ok"}`.

- **Render** baut das Backend aus `backend/` mit `backend/Dockerfile` und startet es. Die Einstellungen stehen in `render.yaml`. Nach jedem Push auf `master`, der etwas in `backend/` ändert, baut Render neu, das dauert einige Minuten. Den Stand zeigt das Render-Dashboard beim Dienst `touchgrass-backend` unter **Events**, Fehlermeldungen unter **Logs**.
- **Neon** stellt die Datenbank, ein PostgreSQL in Frankfurt. Flyway legt beim ersten Start alle Tabellen an. Konten in der Cloud sind unabhängig von denen im lokalen Backend.
- **Zugangsdaten** stehen nur bei Render, beim Dienst unter **Environment**:
  - `DB_URL`: die Zeile, die Neon im Projekt unter **Connect** zeigt, mit eingeblendetem Passwort und unverändert: `postgresql://<Benutzer>:<Passwort>@<Host>/<Datenbank>?…`. Das Backend liest Benutzer und Passwort daraus und verbindet sich direkt, auch wenn bei Neon „Connection pooling“ eingeschaltet ist.
  - `JWT_SECRET`: hat Render selbst erzeugt. Wer ihn ändert, meldet alle Nutzer ab.

  Nach einer Änderung bei Render speichern und neu deployen lassen. Steht in den Logs `password authentication failed`, die Zeile bei Neon neu kopieren und in `DB_URL` ersetzen.
- **Kaltstart:** Nach 15 Minuten ohne Anfrage schläft der Server ein. Die erste Anfrage danach dauert etwa eine Minute, die App zeigt so lange „Der Server wacht gerade auf, das kann bis zu einer Minute dauern.“ Vor einer Vorführung die App deshalb einmal öffnen.
- **Kostenlos mit Grenzen:** Render gibt 750 Stunden im Monat, das reicht für einen Dienst rund um die Uhr. Neon gibt 1 GB und 100 Rechenstunden im Monat. Den Server nicht mit regelmäßigen Anfragen wachhalten, sonst bleibt auch die Datenbank wach, und die Rechenstunden sind nach gut zwei Wochen verbraucht.

## Backend lokal starten

Nur nötig, um Backend-Änderungen vor dem Push in der App auszuprobieren. Die Tests brauchen kein laufendes Backend.

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
2. Emulator oder Handy wählen und ▶ Run. Die App spricht das Backend in der Cloud an.

**Gegen das lokale Backend:** In `local.properties` (nicht im Git) eintragen:

```
api.baseUrl=http://127.0.0.1:8080/api/
```

Dann das Backend lokal starten und nach jedem Anstecken des Handys oder Start des Emulators einmal ausführen:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8080 tcp:8080
```

Sind Handy und Emulator gleichzeitig verbunden, den Befehl je Gerät mit `-s <Geräte-ID>` ausführen, `adb devices` zeigt die IDs. Zurück zur Cloud: die Zeile in `local.properties` löschen und ▶ Run.

**Karte und Standort:** Die Kartenbilder kommen von OpenFreeMap aus dem Internet, ohne Netz bleibt die Karte leer. Nach dem Standort fragt die App erst, wenn man „📍 Mein Standort“ oder im Formular „📍 Aktueller Standort“ antippt. Im Emulator stellt man den Standort unter **⋮ (Extended Controls) → Location** ein.

Unit-Tests der App: `.\gradlew.bat testDebugUnitTest` (im Wurzelordner).

## App auf andere Handys bringen

1. In `local.properties` darf keine Zeile `api.baseUrl=…` aktiv sein, sonst spricht die APK das lokale Backend an.
2. Im Wurzelordner `.\gradlew.bat assembleDebug` ausführen. Die APK liegt danach in `app\build\outputs\apk\debug\app-debug.apk` (etwa 60 MB).
3. Die Datei aufs Handy bringen, z. B. per Mail, Cloud-Speicher, Messenger oder USB-Kabel.
4. Auf dem Handy die Datei öffnen. Android fragt, ob die App, mit der man sie öffnet, Apps installieren darf: erlauben. Warnt Google Play Protect, trotzdem installieren.
5. TouchGrass öffnen und registrieren. Alle Konten liegen in der Cloud, alle sehen dieselbe Karte und dasselbe Leaderboard.

Eine neue Version installiert man genauso über die alte. Kommt sie von einem anderen Rechner, ist sie mit einem anderen Debug-Schlüssel signiert. Dann muss die alte App vorher deinstalliert werden. Die Anmeldung auf dem Handy ist danach weg, die Daten in der Cloud bleiben.

## Roter Code in Android Studio, obwohl der Build klappt

- Android Studio arbeitet mit einem gespeicherten Abbild der Gradle-Dateien. Nach `git pull` oder neuen Bibliotheken ist das veraltet, und Imports werden rot. Abhilfe: **File → Sync Project with Gradle Files** (Elefanten-Symbol oben rechts).
- `backend/` ist ein eigenes Gradle-Projekt und gehört nicht zum Android-Projekt. Zum Bearbeiten den Ordner `backend` in einem zweiten Fenster öffnen (**File → Open → `backend`**), sonst ist dort alles rot.

## Warum PostgreSQL

Die Daten hängen eng zusammen: Aktivitäten gehören zu einer Person und einer Sportart, freigeschaltete Achievements zu einer Person. Wochenziel, Leaderboard und Statistik sind Summen über Wochen und Monate. Dafür ist eine relationale Datenbank mit SQL gemacht. PostgreSQL ist kostenlos und weit verbreitet. Lokal braucht es keine Installation, weil das Backend es mitbringt, und die Tests laufen gegen dasselbe echte PostgreSQL. Für ein PostgreSQL auf einem Server reicht `DB_URL`, der Code bleibt gleich. In der Cloud ist das Neon.

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
- Das Backend schreibt keine Anfragen ins Log, Passwörter tauchen dort also nicht auf. Der Schlüssel für die Login-Tokens und die Zugangsdaten der Datenbank liegen nicht im Git (siehe „Backend in der Cloud“).
- Backend und Datenbank laufen in Frankfurt bei Render und Neon. Die Verbindungen sind verschlüsselt: App ↔ Render über HTTPS, Render ↔ Neon über SSL.

## Bewusste Grenzen

- **kcal sind geschätzt:** MET-Wert der Sportart × Gewicht in kg × Dauer in Stunden. Ohne Gewicht rechnet das Backend mit 70 kg. Die App zeigt deshalb immer „~“ vor den kcal.
- **Keine Plausibilitätsprüfung:** Wer unrealistische Zeiten einträgt, steigt im Leaderboard.
- **Logout gilt nur für dieses Handy:** Die App löscht ihren Login-Token. Der Token selbst bleibt bis zu 30 Tage gültig und lässt sich nicht vorzeitig sperren.
- **Kaltstart:** Der kostenlose Server bei Render schläft nach 15 Minuten ohne Anfrage ein. Die erste Anfrage danach dauert etwa eine Minute.
- **HTTP nur lokal:** In der Cloud läuft alles über HTTPS. Klartext-HTTP erlaubt nur der Debug-Build, für das lokale Backend.
- **Ohne Server geht fast nichts:** Auf dem Handy liegen nur Login-Token und Timer. Der Timer läuft auch ohne Netz, alles andere braucht das Backend.
