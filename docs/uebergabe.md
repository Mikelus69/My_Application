# TouchGrass – Übergabe

Stand: 18.11.2026. Diese Datei fasst zusammen, was jemand braucht, um in einem neuen Chat oder als neue Person nahtlos weiterzumachen. Sie wird bei jedem größeren Schritt aktualisiert.

**Für einen neuen Chat:** „Wir arbeiten an TouchGrass weiter. Lies zuerst `CLAUDE.md` und `docs/uebergabe.md`, dann machen wir mit dem ersten Punkt unter ‚Nächste Schritte‘ weiter.“

## Ziel

TouchGrass ist das IT-Projekt von Nils Appel und Michael Lafutkin für die Berufsschule. Es besteht aus einer Android-App, mit der man Sport erfasst, und einem eigenen Backend in der Cloud:

- Aktivitäten per Timer oder von Hand nachtragen, mit geschätzten kcal und Punkten
- Karte mit eigenen, Freunde- und öffentlichen Aktivitäten
- Achievements, Wochenziel, Serie, Leaderboard und Statistiken mit Diagrammen
- Freunde per Benutzername, Sichtbarkeit privat, Freunde oder öffentlich

Abzugeben sind die laufende App, eine Projektdokumentation und eine Präsentation.

## Stand in Kürze

- **Fertig und auf zwei Handys getestet:** das MVP aus Konten, Aktivitäten, Timer und Karte, Erfolgen, Statistik und Betrieb in der Cloud, dazu die Erweiterung Freunde.
- **Danach dazugekommen:** Sportart-Auswahl mit Live-Suche, 25 Sportarten, Health-Check mit laufendem Commit, Tests der Wochengrenzen, App-Farben aus dem Logo.
- **Entworfen, noch nicht gebaut:** Fotos zu Aktivitäten.
- **Begonnen:** die Projektdokumentation für die Berufsschule.
- **Tests:** 120 im Backend und 111 in der App, alle grün.

## Erledigt

| Meilenstein | Datum | Inhalt | Plan |
|---|---|---|---|
| M0 Grundgerüst | 20.10. | Ktor-Backend mit eingebettetem PostgreSQL, Flyway, Health-Endpunkt, App mit Navigation und Retrofit | `plans/2026-10-20-m0-grundgeruest.md` |
| M1 Konten | 20.10. | Registrieren, Login, Logout, Profil, Passwort ändern, Konto löschen, Login per JWT | `plans/2026-10-20-m1-konten.md` |
| M2 Aktivitäten | 21.10. | Sportarten, Formular, kcal und Punkte, Wochenziel-Bonus, Verlauf mit Filtern, Details, Löschen | `plans/2026-10-21-m2-aktivitaeten.md` |
| M3 Timer und Karte | 21.10. | Timer mit Pausen, Ortswahl, Karten-Tab mit Markern | `plans/2026-10-21-m3-timer-karte.md` |
| M4 Erfolge | 26.10. | Achievements, Wochenziel, Serie, Leaderboard | `plans/2026-10-26-m4-erfolge.md` |
| M5 Statistik und Feinschliff | 27.10. | Diagramme, leere und Fehlerzustände, Doku, Testcheckliste | `plans/2026-10-27-m5-statistik-feinschliff.md` |
| M6 Ab in die Cloud | 27.10. bis 02.11. | Backend als Docker-Dienst bei Render, Datenbank bei Neon, Hinweis beim Aufwachen des Servers | `plans/2026-10-27-m6-cloud.md` |
| M7 Freunde | 02.11. bis 03.11. | Anfragen per Benutzername, Sichtbarkeit Freunde, Leaderboard unter Freunden | `plans/2026-11-02-m7-freunde.md` |
| Sportart-Auswahl | 03.11. | Dropdown mit Live-Suche, Sportarten als Migration V7 | – |
| Health-Check | 03.11. | `/api/health` nennt den laufenden Commit | – |
| Zeitgrenzen | 04.11. | Tests für Zeitumstellung im März und Neujahr | – |
| Farben | 18.11. | eigenes Farbschema aus dem Logo statt Vorlagen-Lila | – |
| Entwurf Fotos | 18.11. | Entwurf fertig und abgestimmt | `specs/2026-11-18-fotos-design.md` |

Die Pläne liegen unter `docs/superpowers/plans/`, die Entwürfe unter `docs/superpowers/specs/`.

## Architektur

- **App:** Kotlin mit Jetpack Compose und Material 3, Paket `de.touchgrass`. Retrofit und OkHttp für die API, DataStore für Token und Timer, MapLibre Compose für die Karte, Vico für die Diagramme.
- **Backend:** Kotlin mit Ktor 3.6, Exposed 1.6 und Flyway auf PostgreSQL, Login per JWT, als Docker-Image gebaut.
- **Betrieb:** Render (Dienst `touchgrass-backend`, Gratis-Tarif) und Neon (PostgreSQL, Gratis-Tarif), beide in Frankfurt. Die App spricht `https://touchgrass-backend-pfz7.onrender.com/api/` an. Lokal läuft das Backend mit eingebettetem PostgreSQL.
- **Vertrag:** `docs/api.md` beschreibt jeden Aufruf zwischen App und Backend.

## Entscheidungen und Begründungen

| Entscheidung | Begründung |
|---|---|
| Das Backend rechnet kcal, Punkte, Achievements, Serie und Leaderboard. | Eine Wahrheit für alle Geräte, die App bleibt schlank. |
| kcal und Punkte werden beim Anlegen gespeichert. | Ändert jemand später sein Gewicht, bleiben alte Werte richtig. |
| Sportarten und Achievements stehen in der Datenbank. | Neue Sportarten kommen per Migration ohne App-Update. Bestehende Migrationen werden nie geändert, sonst startet das Backend wegen der Prüfsumme von Flyway nicht. |
| Alle Tages-, Wochen- und Monatsgrenzen gelten in Europe/Berlin, die Woche läuft von Montag bis Sonntag, eine Aktivität zählt an ihrem Starttag. | Schule und Nutzer sind in Deutschland. Zeitumstellung und Jahreswechsel sind getestet. |
| JWT gilt 30 Tage, ohne Refresh. 401 nur bei fehlendem oder ungültigem Token, alle anderen Fehler 400 mit Feldname. | Die App meldet bei 401 ab, ein Tippfehler darf das nicht auslösen. |
| Sichtbarkeit privat, Freunde oder öffentlich; die Regel steht nur an einer Stelle (`ActivityRepository`). | Karte, Details und künftig Fotos können nicht voneinander abweichen. |
| Eine Freundschaft ist eine Zeile pro Paar mit eindeutigem Index. | Doppelte oder gleichzeitige Anfragen sind ausgeschlossen. |
| Render und Neon im Gratis-Tarif, Region Frankfurt. | Kostenlos und in Deutschland. Der Server schläft nach 15 Minuten ein, deshalb wartet die App bis zu zwei Minuten und zeigt einen Hinweis. |
| Geheimnisse (`DB_URL`, `JWT_SECRET`) nur bei Render, nie im Git. | Das Repository ist für mehrere Personen zugänglich. |
| `/api/health` nennt den laufenden Commit. | Damit lässt sich prüfen, ob Render den gepushten Stand wirklich gebaut hat. |
| Die App bleibt bei Kotlin 2.2.10, Bibliotheken werden passend festgelegt (Vico 2.3.6, MapLibre Compose 0.12.1, künftig Coil 3.3.0). | Das Android Gradle Plugin 9.2.1 gibt Kotlin 2.2.10 vor, neuere Bibliotheksversionen brauchen ein neueres Kotlin. |
| Sportart-Auswahl als Dropdown mit Live-Suche, deutsche Sortierung, Umlaute und ß egal. | 25 Sportarten passen nicht mehr in eine einfache Liste. |
| Eigenes Material-3-Farbschema („Tonal Spot“) aus dem Limettengrün `#ADF224` des Logos, keine Farben vom Hintergrundbild. | Die App sieht auf jedem Handy gleich aus und passt zum Logo. Alle Texte haben mindestens 6:1 Kontrast. |
| Fotos (Entwurf): in der Datenbank statt im Ordner, höchstens 512 Pixel und 200 KB, ein Foto pro Aktivität. | Render löscht Dateien bei jedem Neustart. Neon hat 1 GB, das reicht für über 5.000 Fotos. |
| Arbeitsweise: Entwurf, Plan, Umsetzung mit Tests zuerst, ein Commit pro Aufgabe, alles direkt auf `master`. Gepusht wird von Hand. | Kleine, nachvollziehbare Schritte. Jeder Push löst einen Deploy aus, das soll bewusst geschehen. |

## Offene Punkte

- **Projektdokumentation:** Markdown unter `docs/projektdoku/`. Kapitel 1 bis 7 sind geschrieben, gegen die Quellen geprüft und auf rund 5.000 Wörter gekürzt; Projekttagebuch und Vorgaben der Berufsschule Lauingen liegen daneben. Offen sind die Platzhalter in eckigen Klammern (Lehrkraft, Termine, Screenshots, Kosten, Arbeitsstunden, Gründe für einige Technikentscheidungen, Testprotokoll), die Klärung mit der Lehrkraft, wie der KI-Einsatz anzugeben ist, und die Übertragung nach Word und PDF mit den Diagrammen als Bilder.
- **Fotos:** Entwurf fertig, der Umsetzungsplan fehlt noch.
- **Farben:** gebaut und gepusht, auf dem Handy noch nicht angesehen.
- **Wochenziel-Bonus:** Wird die Aktivität gelöscht, die den Bonus bekommen hat, ist der Bonus dieser Woche meist weg. Noch offen, ob das so bleiben soll.
- **GPS-Tracking:** zweites Stretch Goal, braucht einen eigenen Entwurf.
- **Präsentation:** noch nicht begonnen.

## Nächste Schritte

1. Form der Projektdokumentation festlegen und alles bisher Erledigte rückwirkend dokumentieren.
2. Umsetzungsplan für Fotos schreiben, dann Aufgabe für Aufgabe umsetzen.
3. Präsentation vorbereiten.
4. Danach GPS-Tracking entwerfen.

## Relevante Dateien

| Datei | Inhalt |
|---|---|
| `CLAUDE.md` | Arbeitsregeln für Claude in diesem Repository |
| `docs/superpowers/specs/2026-10-20-touchgrass-design.md` | Hauptentwurf, beschreibt immer den aktuellen Stand |
| `docs/superpowers/specs/2026-11-02-freunde-design.md` | Entwurf Freunde |
| `docs/superpowers/specs/2026-11-18-fotos-design.md` | Entwurf Fotos |
| `docs/superpowers/plans/` | Umsetzungspläne M0 bis M7 |
| `docs/api.md` | API-Vertrag zwischen App und Backend |
| `docs/testcheckliste.md` | Test von Hand mit zwei Handys |
| `docs/projektdoku/dokumentation.md` | Projektdokumentation für die Berufsschule, gegliedert nach dem Leitfaden |
| `docs/projektdoku/vorgaben.md` | Vorgaben der Schule: Termine, Umfang, Aufbau, Zitate, Formatierung, Sprache, Bewertung |
| `docs/projektdoku/projekttagebuch.md` | Projekttagebuch für die Berufsschul-Doku, ein Eintrag pro Arbeitstag |
| `README.md` | Starten, Cloud, App auf andere Handys bringen, Datenschutz, Bewusste Grenzen |
| `backend/src/main/kotlin/de/touchgrass/` | Backend: `Application.kt`, `routes/`, je Bereich ein Paket, Rechenregeln in `logic/` |
| `backend/src/main/resources/db/migration/` | Flyway-Migrationen V1 bis V7 |
| `backend/Dockerfile`, `render.yaml` | Bau und Betrieb bei Render |
| `app/src/main/java/de/touchgrass/` | App: `data/` (API, Login, Timer), `ui/` (Screens), `ui/theme/` (Farben) |
| `gradle/libs.versions.toml`, `backend/gradle/libs.versions.toml` | Versionen der Bibliotheken |
| `docs/logo.webp` | Logo |

## Abläufe

- **Tests:** im Ordner `backend` `.\gradlew.bat test`, im Wurzelordner `.\gradlew.bat testDebugUnitTest` für die App.
- **Backend lokal und App gegen das lokale Backend:** siehe README, Abschnitte „Backend lokal starten“ und „App starten“.
- **Auslieferung:** Push auf `master`. Ändert der Push etwas unter `backend/`, baut Render von selbst neu, das klappt seit dem 04.11. Danach `https://touchgrass-backend-pfz7.onrender.com/api/health` aufrufen: `commit` muss der gepushte sein, sonst bei Render „Manual Deploy → Deploy latest commit“.
- **App auf andere Handys:** README, Abschnitt „App auf andere Handys bringen“.
- **Auf Nils’ Rechner:** Gradle läuft dort nur im Terminal-Panel der Claude-App, der Emulator ist unzuverlässig, getestet wird auf einem Samsung Galaxy A34.
- **Wissensgraph:** `graphify-out/` ist ein lokaler Wissensgraph des Codes, nicht im Git. Neu erzeugen mit `/graphify --update`.
