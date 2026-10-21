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
- Beim ersten Start entpackt das Backend ein eingebautes PostgreSQL (ca. 30 s). Die Daten liegen in `backend/data/` und bleiben erhalten.
- Beenden mit Ctrl+C und `J`. Das eingebaute PostgreSQL läuft danach im Hintergrund weiter, weil Gradle den Server hart beendet. Der nächste Start räumt es automatisch weg. Ganz beenden: `Stop-Process -Name postgres`.
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

Unit-Tests der App: `.\gradlew.bat testDebugUnitTest` (im Wurzelordner).

## Roter Code in Android Studio, obwohl der Build klappt

- Android Studio arbeitet mit einem gespeicherten Abbild der Gradle-Dateien. Nach `git pull` oder neuen Bibliotheken ist das veraltet, und Imports werden rot. Abhilfe: **File → Sync Project with Gradle Files** (Elefanten-Symbol oben rechts).
- `backend/` ist ein eigenes Gradle-Projekt und gehört nicht zum Android-Projekt. Zum Bearbeiten den Ordner `backend` in einem zweiten Fenster öffnen (**File → Open → `backend`**), sonst ist dort alles rot.
