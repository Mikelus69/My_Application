# Projekttagebuch TouchGrass

Protokoll der Arbeit am Projekt mit Datum. Die Einträge bis zum 18.11.2026 sind rückwirkend aus der Git-Historie, den Entwürfen, den Plänen und den Rückmeldungen aus den Tests entstanden. Ab dem 18.11.2026 bekommt jede erledigte Aufgabe im selben Commit einen Eintrag. Die Kürzel in Klammern sind die Commits.

## 20.10.2026 – Projektstart, Entwurf, M0 Grundgerüst, M1 Konten

- Repository auf GitHub angelegt (`1c3518e`).
- Entwurf für TouchGrass mit Architektur, Datenmodell, REST-API und Meilensteinen geschrieben (`98b33b4`). Das Backend startet ein eingebettetes PostgreSQL statt eines Docker-Containers (`6ad116b`, `6419136`).
- **M0 Grundgerüst:**
  - Backend mit Health-Endpunkt, einheitlichem Fehlerformat, eingebettetem PostgreSQL, Flyway und Sportarten-Tabelle (`cf157dc`, `c800f01`, `c48a1c9`).
  - App in TouchGrass umbenannt (Paket `de.touchgrass`), Retrofit angebunden, Server-Status und Bottom-Navigation mit Platzhalter-Tabs (`0c60afb`, `9a2ce9d`, `a4b7810`, `5df0730`).
  - API-Vertrag `docs/api.md` und README (`fb965e9`).
- **M1 Konten:** Plan (`43d84b8`).
  - Backend: Prüfregeln mit einheitlichen Validierungsfehlern, JWT-Login mit Passwort-Hashing, Registrierung und Login, Profil, Passwort ändern und Konto löschen (`de5f95e`, `2a1fedf`, `70037f1`, `fe074fb`).
  - App: API-Schicht mit einheitlicher Fehlerbehandlung, Token in DataStore mit Auth-Interceptor, Login und Registrierung, Profil und Einstellungen (`607227c`, `00ae625`, `123c7e8`, `3a25247`).

## 21.10.2026 – M2 Aktivitäten, M3 Timer und Karte

- README: Hinweis auf „roten Code“ in Android Studio nach Gradle-Änderungen (`f1e5d81`).
- **M2 Aktivitäten:** Plan (`65ee4b1`).
  - Backend: Rechenregeln für kcal, Punkte, Wochenziel und Berliner Wochen, Endpunkt für Sportarten, Aktivität anlegen mit kcal, Punkten und Wochenziel-Bonus, Verlauf mit Filtern, Details und Löschen (`babe446`, `910af04`, `69109aa`, `7b13280`).
  - App: API für Sportarten und Aktivitäten mit minSdk 26, Eintragen im Plus-Tab, Verlauf mit Filtern und Details mit Löschen (`3a5fc68`, `a872c7f`, `c2d4d88`).
- **M3 Timer und Karte:** Plan (`1dbc4cb`).
  - Aktivitäten mit Standort, Timer mit Pausen in DataStore, Karten-Tab mit Markern und eigenem Standort, Timer-Screen mit Start, Pause und Stopp (`6dfb966`, `a627e7b`, `ed745ef`, `1517935`).
  - Das Formular übernimmt die Werte des Timers, dazu Ortswahl auf der Karte und Verwerfen (`d279473`).

## 26.10.2026 – M4 Erfolge

- **M4 Erfolge:** Plan (`8c96f6d`).
  - Backend: Rechenregeln für Serie, Achievements und Leaderboard-Plätze, Achievements freischalten und auflisten, Wochenziel, Serie und Leaderboard (`8b5a03d`, `5cc2deb`, `58b366e`).
  - App: API, Erfolge-Tab mit Wochenziel, Serie, Achievements und Leaderboard, Dialog bei neuen Achievements nach dem Speichern (`1087d12`, `f480e3a`, `c6d571d`).
- Entwurf: Das Backend startet aus dem Ordner `backend` (`b417148`).
- **Fehler behoben:** Die Karte stürzte ab, wenn es keine Marker gab. Lösung: spatialk 0.6.1 (`7ab9af9`).

## 27.10.2026 – M5 Statistik und Feinschliff, M6 Ab in die Cloud

- **Fehler behoben:** Ein zweiter Start des Backends beendete die Datenbank des laufenden. Jetzt bricht der zweite Start ab (`4d790df`).
- **M5 Statistik und Feinschliff:** Plan (`9eb6a45`).
  - Sportarten Badminton, Golf und Hockey als Migration V5 (`8316fc1`).
  - Statistik-Rechnung für die Woche, acht Wochen, Sportarten und sechs Monate, Endpunkt `GET /stats`, Abruf in der App, Statistik-Screen mit drei Säulendiagrammen (`b0d5782`, `c7edd69`, `a0ae20c`, `db3de56`, `f2e3a77`).
  - Profil zeigt die Zahlen dieser Woche, „Erneut versuchen“ und Hinweise bei fehlenden Daten (`2fb5134`, `7a2831c`).
  - Doku: Warum PostgreSQL, Datenschutz, Bewusste Grenzen, Testcheckliste (`4c42376`).
- **M6 Ab in die Cloud:**
  - `render.yaml` über GitHub angelegt (`e9d4034`, `d0c5319`, zusammengeführt in `a6c0694`).
  - Entwurf für den Betrieb mit Render und Neon, Plan (`c422063`, `f05bc50`, `6afb247`).
  - Backend als Docker-Dienst für Render, App spricht das Backend bei Render an und wartet bis zu zwei Minuten, Hinweis beim Aufwachen des Servers (`0a87f77`, `f9c59f2`, `152977a`).
  - Doku: Backend in der Cloud, App auf andere Handys bringen (`f3e2bd5`).

## 29.10.2026 – Datenbank bei Neon anbinden

- **Problem:** Die Verbindung zu Neon schlug fehl. `DB_USER` und `DB_PASSWORD` wurden mit der Anmeldung bei Neon verwechselt. Lösung: Die Doku stellt das klar, und das Backend nimmt jetzt die Verbindungszeile von Neon so, wie Neon sie anzeigt (`ac001bd`, `faed274`).

## 02.11.2026 – Feste Cloud-Adresse, Entwurf Freunde

- Die App spricht das Backend unter `touchgrass-backend-pfz7.onrender.com` an (`9e6624d`).
- App-Icon aus dem TouchGrass-Logo (`728f1b4`).
- Entwurf und Plan für das Stretch Goal Freunde (`838a53f`, `bd65d0e`).

## 03.11.2026 – M7 Freunde, Sportart-Auswahl, Health-Check

- **M7 Freunde:**
  - Backend: Freundschaften anfragen, annehmen, ablehnen und beenden, Sichtbarkeit „Freunde“ auf der Karte und in den Details, Leaderboard unter Freunden (`bc5641b`, `faf7598`, `37f2244`).
  - App: Freunde in der Schnittstelle, Freunde-Screen im Profil, Hinweis auf offene Anfragen, Freunde im Formular, in den Details und auf der Karte, Leaderboard-Umschalter (`df1a486`, `8f3bb34`, `0f8eeb7`, `307165f`, `d15f719`).
  - Doku: README, Testcheckliste und Entwurf (`cdea0ef`).
- **Sportart-Auswahl:** Dropdown mit Live-Suche, deutsch sortiert, Umlaute und ß egal (`8ab00ea`). Die inzwischen 25 Sportarten aus der Neon-Datenbank als Migration V7 (`b0e9797`).
- **Problem:** Im Test zeigte das Freunde-Leaderboard alle Nutzer. Ursache: Render hatte den neuen Stand nicht gebaut. Nach einem Manual Deploy funktionierte es.
- Der Health-Check nennt jetzt den laufenden Commit, damit sich das nach jedem Push prüfen lässt (`3613544`).

## 04.11.2026 – Abnahme M7, Code-Analyse, Zeitgrenzen

- Test aller Funktionen mit zwei Handys und zwei Konten: alles funktioniert.
- **Problem gelöst:** Render baute nach einem Push nicht von selbst, weil das Repository dem Kollegen gehört und die Render-App bei GitHub nur im Konto von Nils installiert war. Nach dem Eintragen der Zugangsdaten des Repo-Besitzers bei Render baute Render den nächsten Push von selbst (geprüft mit `d334c0f`).
- Wissensgraph des Codes mit graphify erstellt und ausgewertet: Weg des Login-Tokens, Weg der Fehlermeldungen, Ablauf vom Speichern bis zum Achievement-Dialog. Der Graph bleibt lokal (`6f944dd`).
- Zeitgrenzen geprüft (Mitternacht, Zeitumstellung, Jahreswechsel): alles korrekt. Zwei Tests für die Zeitumstellung im März und den Jahreswechsel ergänzt (`d334c0f`).
- Einstellungen für JSON-Schemas in Android Studio (`3d28cd3`), Git-Hooks von graphify wieder entfernt (`7615b51`).

## 18.11.2026 – Farben, Entwurf Fotos, Beginn der Projektdokumentation

- **App-Farben:** drei Farbschemata aus den Logo-Farben berechnet und verglichen, Variante A gewählt. Die App nutzt keine Farben vom Hintergrundbild mehr (`33053c6`).
- **Entwurf Fotos:**
  - Speicherort geklärt: Render löscht Dateien bei jedem Neustart, Neon bietet 1 GB, deshalb kommen die Fotos in die Datenbank.
  - Recherche zu Coil, Ktor, Exposed sowie Fotoauswahl und Kamera unter Android. Entwurf abgestimmt (`b4f00a6`).
- Übergabe-Dokument `docs/uebergabe.md` und Arbeitsregeln `CLAUDE.md` für neue Chats (`31ea7bd`).
- Projektdokumentation begonnen: Markdown unter `docs/projektdoku/`, mit diesem Projekttagebuch.
- Leitfaden der Berufsschule ausgewertet: Die Vorgaben stehen in `vorgaben.md`, die Gliederung der Dokumentation danach in `dokumentation.md`.
- Projektdokumentation rückwirkend geschrieben:
  - Fakten zu allen Meilensteinen aus Git-Historie, Plänen und Entwürfen gesammelt und gegen die Quellen geprüft.
  - Kapitel 1 bis 7 mit Abkürzungs- und Literaturverzeichnis sowie Anhang verfasst. Einleitung, Ausgangslage und Schluss nach den Angaben des Teams zu Thema, Idee und Arbeitsteilung.
  - Text aus vier Blickwinkeln geprüft (Fakten, Sprache, Struktur und Umfang, Bewertung durch die Lehrkraft) und auf rund 5.000 Wörter gekürzt, damit er mit Tabellen und Abbildungen in 20 Seiten passt.
- **Plan M8 Fotos:**
  - Umsetzungsplan mit acht Aufgaben geschrieben: Backend zum Hochladen, Abrufen und Entfernen, in der App Bildladen, Verkleinern, Formular, Details, Verlauf und Karte, zum Schluss die Doku.
  - Jede Aufgabe vorab in einer Kopie des Repositorys Schritt für Schritt durchgespielt.
  - Plan aus sechs Blickwinkeln geprüft (Abgleich mit der Probe, Entwurf und Vorgaben, Android zur Laufzeit, Sicherheit im Backend, Ausführbarkeit, Doku), jeder Befund von zwei weiteren Prüfungen gegengeprüft. Die bestätigten Befunde sind eingearbeitet, etwa: Das Backend erkennt abgeschnittene Fotos, und ein im Hintergrund gespeichertes Formular kommt nach dem Beenden der App nicht wieder. Danach waren in der Kopie 137 Tests im Backend und 132 in der App grün, Lint meldete keine Fehler.
  - Übergabe auf den Stand mit Plan gebracht.

## 23.11.2026 – M8 Fotos

- **M8 Fotos, Backend:** Fotos hochladen und entfernen (`PUT` und `DELETE /api/activities/{id}/photo`), die API-Doku vorher angepasst. Migration V8 mit der Tabelle `activity_photos`, der Sequenz `photo_versions` und der Spalte `activities.photo_version`. Der Server lehnt Fotos über 200 KB mit 413 ab und prüft JPEG-Kennung und Seitenlänge (höchstens 512 Pixel), ohne das Bild zu entpacken.
- **M8 Fotos, Backend:** Foto abrufen (`GET /api/activities/{id}/photo`) mit derselben Sichtbarkeitsregel wie die Details, als `image/jpeg` mit `Cache-Control: private, max-age=31536000, immutable`. Ohne Foto oder ohne Sicht antwortet der Server mit 404, nie mit 401.
- **M8 Fotos, App – Schnittstelle und Bildladen:** Die App kennt `photoVersion` und kann Fotos hochladen und entfernen. Coil 3.3.0 lädt Bilder über denselben OkHttp-Client wie die API, damit das Login mitgeht. Jedes Abmelden leert den Bildzwischenspeicher, auch nach „Konto löschen“ und nach einem 401.
- **M8 Fotos, App – Foto auswählen und verkleinern:** Galerie über Androids Fotoauswahl, Kamera über die Kamera-App mit eigenem FileProvider, beides ohne neue Berechtigung. Die App verkleinert das Foto im Hintergrund auf höchstens 512 Pixel, dreht es nach der EXIF-Angabe und speichert es ohne Zusatzdaten und Ultra-HDR-Anteil als JPEG mit höchstens 200 KB. Neue Bibliothek androidx.exifinterface 1.4.2.
- **M8 Fotos, App – Foto im Formular:** Foto beim Eintragen aus der Galerie oder mit der Kamera, mit Vorschau und „Entfernen“. Die App lädt das Foto nach dem Speichern der Aktivität hoch. Klappt das nicht, bleibt die Aktivität gespeichert, und die App sagt Bescheid. Formular und Foto bleiben erhalten, wenn Android die App beendet, während die Kamera offen ist.
- **M8 Fotos, App – Foto in den Details:** Foto groß in den Details. Bei eigenen Aktivitäten lässt es sich hinzufügen, ändern und nach einer Rückfrage entfernen.
- **M8 Fotos, App – Verlauf und Karte:** Vorschaubild im Verlauf und Foto im Fenster eines angetippten Markers auf der Karte.
