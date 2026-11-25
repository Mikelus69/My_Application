# TouchGrass

IT-Projekt für die Berufsschule: Android-App (Kotlin, Jetpack Compose) und Ktor-Backend mit PostgreSQL, betrieben bei Render und Neon. Stand, Entscheidungen und nächste Schritte stehen in `docs/uebergabe.md`.

## Arbeitsweise

- Direkt auf `master` committen, keine Branches. Gepusht wird von Hand durch die Entwickler.
- Neue Funktionen: Entwurf in `docs/superpowers/specs/`, Plan in `docs/superpowers/plans/`, Umsetzung mit Tests zuerst, ein Commit pro Aufgabe, Commit-Nachrichten auf Deutsch.
- Der Hauptentwurf `docs/superpowers/specs/2026-10-20-touchgrass-design.md` beschreibt immer den aktuellen Stand, ohne Status oder Historie.
- `docs/api.md` ist der Vertrag zwischen App und Backend und wird vor jeder Änderung an der API angepasst.
- Jede erledigte Aufgabe bekommt im selben Commit einen Eintrag in `docs/projektdoku/projekttagebuch.md`. Die Projektdokumentation für die Berufsschule entsteht in `docs/projektdoku/dokumentation.md`, die Regeln der Schule für Aufbau, Sprache, Zitate und Umfang stehen in `docs/projektdoku/vorgaben.md`. `docs/uebergabe.md` wird bei größeren Schritten aktualisiert.
- Wenig Kommentare im Code: eine Begründung an genau einer Stelle.

## Technik

- Die App bleibt bei Kotlin 2.2.10, das Android Gradle Plugin 9.2.1 gibt es vor. Bibliotheken nur in Versionen, die damit laufen (Vico 2.3.6, MapLibre Compose 0.12.1, Coil 3.3.0).
- Bestehende Flyway-Migrationen nie ändern, sonst startet das Backend nicht mehr. Neue Sportarten und Achievements kommen per neuer Migration.
- Keine Geheimnisse ins Git: `DB_URL` und `JWT_SECRET` liegen nur bei Render.
- 401 nur bei fehlendem oder ungültigem Token, alle anderen Fehler 400 mit Feldname, nur ein zu großes Foto 413 mit Feld `photo`: Die App meldet bei 401 ab.
- Alle Tages-, Wochen- und Monatsgrenzen in Europe/Berlin über `BerlinTime`.
- Nach jedem Push mit Änderungen unter `backend/`: `https://touchgrass-backend-pfz7.onrender.com/api/health` aufrufen. Steht unter `commit` nicht der gepushte Commit, bei Render „Manual Deploy → Deploy latest commit“.
