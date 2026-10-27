# Test-Checkliste

Von oben nach unten durchgehen, vor jeder Abgabe und vor der Präsentation. Die Abschnitte 1 bis 7 sind zugleich der Ablauf der Vorführung, Abschnitt 8 gehört nicht dazu.

## Vorbereitung

- [ ] Die App ist installiert (▶ Run oder APK, siehe README). Etwa eine Minute vor der Vorführung die App einmal öffnen, damit der Server bei Render wach ist.
- [ ] Ein zweites Konto `lisa_k` hat zwei, drei öffentliche Aktivitäten an verschiedenen Orten, damit Karte und Leaderboard nicht leer sind. Danach abmelden.
- [ ] Im Emulator ist ein Standort gesetzt: **⋮ (Extended Controls) → Location**.

## 1. Konto anlegen

- [ ] Die App startet mit „Anmelden“. Als `lisa_k` mit falschem Passwort anmelden → „Benutzername/E-Mail oder Passwort ist falsch.“
- [ ] „Noch kein Konto? Registrieren“: Benutzername `lisa_k` mit sonst gültigen Angaben → „Dieser Benutzername ist schon vergeben.“ am Feld. Eigener Benutzername, aber Passwort mit weniger als 8 Zeichen → Fehlermeldung am Passwortfeld.
- [ ] Mit gültigen Angaben „Konto anlegen“ → die App öffnet die 🗺️ Karte.
- [ ] 👤 Profil: Benutzername, E-Mail, „Diese Woche: 0 Aktivitäten · 0 min · ~0 kcal“, „Gewicht: nicht angegeben – …“, „Wochenziel: 150 Minuten“ und „✅ Server erreichbar …“.
- [ ] ⚙️ Einstellungen: Gewicht `78,5`, Wochenziel `60` Minuten, „Speichern“ → „Gespeichert.“ Zurück im Profil stehen „Gewicht: 78,5 kg“ und „Wochenziel: 60 Minuten“.

## 2. Aktivität mit Timer

- [ ] ➕ Aktivität: „▶ Start“ ohne Sportart → „Bitte eine Sportart wählen.“
- [ ] 🏃 Joggen wählen, „▶ Start“ → die Zeit läuft, und der ➕-Tab zeigt sie statt „Aktivität“. Mindestens eine Minute laufen lassen.
- [ ] „⏸ Pause“ → die Zeit steht. „▶ Weiter“ → sie läuft weiter.
- [ ] App ganz schließen (aus den letzten Apps wischen) und neu öffnen → der Timer läuft mit der richtigen Zeit weiter.
- [ ] „⏹ Stopp“ → Formular mit Sportart, Datum, Uhrzeit und Dauer vom Timer.
- [ ] Ort per „📍 Aktueller Standort“ oder Tippen auf die kleine Karte, „🌎 Öffentlich“, eine Beschreibung, „Speichern“ → „Gespeichert! ~… kcal · +… Punkte (… min · +10 · +20 neue Sportart)“ und der Dialog „Neues Achievement!“ mit „🥉 Erste Schritte – Erste Aktivität eingetragen“. „Super!“ schließt ihn.
- [ ] Mit „← Zurück“ zum Timer, noch einmal „▶ Start“ und „⏹ Stopp“, dann „Verwerfen“ → Rückfrage „Aktivität verwerfen?“ → „Verwerfen“ → der ➕-Tab zeigt wieder „Aktivität“.

## 3. Nachtragen

- [ ] Im Timer „Ohne Timer nachtragen“, 🚴 Radfahren wählen, Dauer `abc`, „Speichern“ → „Bitte die Dauer in ganzen Minuten angeben.“
- [ ] Uhrzeit eine Stunde früher, Dauer `60`, „Speichern“ → die Meldung enthält „+20 neue Sportart“ und „+50 Wochenziel“, weil das Wochenziel von 60 Minuten damit erreicht ist.

## 4. Karte

- [ ] 🗺️ Karte: der eigene Marker in Akzentfarbe, die Marker von `lisa_k` grau.
- [ ] Einen Marker antippen → Sportart, Dauer, kcal, Datum, Beschreibung und Benutzername.
- [ ] „📍 Mein Standort“ → die App fragt nach der Standort-Berechtigung, danach springt die Karte zum Standort.

## 5. Erfolge

- [ ] 🏆 Übersicht: Wochenziel-Balken voll mit „… / 60 Minuten – geschafft! 🎉“, „🔥 1 Tag in Folge“, unter dem heutigen Wochentag ✅.
- [ ] Achievements: sechs Karten. „🥉 Erste Schritte“ ist freigeschaltet mit Datum, die anderen sind blass und zeigen „x / y“.
- [ ] Leaderboard: der eigene Platz ist farbig hinterlegt. „Monat“ und „Minuten“ umschalten → Liste und Einheit ändern sich.

## 6. Profil, Statistik und Verlauf

- [ ] 👤 Profil: „Diese Woche: 2 Aktivitäten · … min · ~… kcal“.
- [ ] 📊 Statistik: „Minuten pro Woche“ mit KW-Nummern, „Aktivitäten je Sportart“ mit 🏃 und 🚴, „kcal pro Monat (geschätzt)“ mit Monatskürzeln. „← Zurück“ führt ins Profil.
- [ ] 📜 Verlauf: beide Aktivitäten. Filter „🚴 Radfahren“ → nur noch eine. „7 Tage“, „30 Tage“ und „Alle“ lassen sich umschalten.
- [ ] Eine Aktivität antippen → Details mit Dauer, kcal und Punkten. „Aktivität löschen“ → Rückfrage „Aktivität löschen?“ → „Löschen“ → sie fehlt im Verlauf, und die Statistik zählt sie nicht mehr.

## 7. Passwort, Abmelden, Konto löschen

- [ ] ⚙️ Einstellungen: „Passwort ändern“ mit falschem bisherigen Passwort → „Das bisherige Passwort ist falsch.“ am Feld. Mit dem richtigen → „Passwort geändert.“
- [ ] „Abmelden“ → „Anmelden“. Mit dem neuen Passwort anmelden → klappt.
- [ ] „Konto löschen“ → Rückfrage „Konto löschen?“ → „Löschen“ → zurück bei „Anmelden“. Anmelden mit den alten Daten → „Benutzername/E-Mail oder Passwort ist falsch.“

## 8. Leere Zustände und Fehler (nicht vorführen)

- [ ] Frisch registriertes Konto: Die Statistik zeigt dreimal „Noch keine Daten. Trag eine Aktivität ein, dann erscheint hier ein Diagramm.“, der Verlauf „Noch keine Aktivitäten in diesem Zeitraum.“
- [ ] Mit dem lokalen Backend, nach dem Zurücksetzen der Datenbank (siehe README) und mit einem neuen Konto ohne Aktivitäten: Die Karte zeigt „Noch keine Aktivitäten mit Ort. Wähl beim Speichern einen Ort, dann erscheint hier ein Marker.“, das Leaderboard „In diesem Zeitraum hat noch niemand Sport eingetragen.“
- [ ] Flugmodus einschalten, App ganz schließen und neu öffnen: Karte, Timer, „Ohne Timer nachtragen“, alle drei Erfolge-Reiter, Profil, Statistik, Verlauf und Einstellungen zeigen „Server nicht erreichbar“ mit „Erneut versuchen“.
- [ ] Flugmodus aus und überall „Erneut versuchen“ antippen → die Inhalte erscheinen.
- [ ] Mindestens 15 Minuten nichts tun, dann die App öffnen: Nach 5 Sekunden steht unter dem Ladekreis „Der Server wacht gerade auf, das kann bis zu einer Minute dauern.“, danach erscheint die Karte.
