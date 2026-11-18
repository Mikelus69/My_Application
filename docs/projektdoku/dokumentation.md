# Projektdokumentation TouchGrass

<!--
Gliederung nach dem Leitfaden der Berufsschule, siehe vorgaben.md.
Umfang: 5 bis 20 Seiten ohne Deckblatt, Verzeichnisse, Grafiken und Anhang. Ziel sind rund 16 Seiten,
die Seitenzahlen hinter den Kapiteln sind Richtwerte. Details wandern in den Anhang (höchstens 20 Seiten).
Für die Abgabe wird daraus ein Word- und PDF-Dokument mit der Formatierung aus vorgaben.md.
Abbildungen und Tabellen fortlaufend nummerieren, darunter die Quelle oder „Eigendarstellung“.
-->

## Deckblatt

- Staatliche Berufsschule Lauingen, Friedrich-Ebert-Straße 14, 89415 Lauingen a. d. Donau
- Thema: *offen, Wortlaut aus der Anmeldung*
- Betreuungslehrkraft: *offen*
- Abgabe: *offen, Termin aus Teams*
- Gruppe: Nils Appel, Michael Lafutkin

## Abkürzungsverzeichnis

<!-- Nur fachliche Abkürzungen, zum Beispiel API, DSGVO, HTTP, JSON, JWT, MET, MVP, REST, SQL, UI. -->

## 1 Einleitung

<!-- Höchstens eine Seite. Idee hinter „TouchGrass“ und warum das Thema gewählt wurde. Die Idee und den Kunden liefert das Team. -->

## 2 Ausgangssituation und Problemstellung

<!-- Rund eine Seite. Ist-Zustand, Problemstellung, Kunde, Grundlagen. -->

## 3 Leistungsbeschreibung

<!-- Rund vier Seiten. Quellen: Hauptentwurf Abschnitte 1, 3, 4 und 6, Entwurf Freunde, Entwurf Fotos. -->

### 3.1 Soll-Zustand und Zielbestimmung

<!-- Muss-Kriterien = MVP, Wunsch-Kriterien = Freunde, Fotos, GPS-Tracking, Abgrenzung = „Nicht geplant“. -->

### 3.2 Produkteinsatz

<!-- Anwendungsbereich, Zielgruppe, Betriebsbedingungen: Android ab 8.0, Internet, Server schläft nach 15 Minuten. -->

### 3.3 Produktumgebung

<!-- Hardware (Handys, Entwicklungsrechner, Server bei Render), Software (Android, Ktor, PostgreSQL), Orgware (GitHub, Arbeitsweise). -->

### 3.4 Datenstruktur

<!-- Datenmodell mit Abbildung: Benutzer, Sportarten, Aktivitäten, Achievements, Freundschaften. -->

### 3.5 Produktfunktionen

<!-- Benutzerfunktionen (Konto, Erfassen, Karte, Erfolge, Statistik, Freunde), Systemfunktionen (kcal, Punkte, Wochenziel, Serie, Leaderboard), Administratorfunktionen (Sportarten per Migration, Betrieb bei Render und Neon). -->

### 3.6 Qualitätsanforderungen

<!-- Datenschutz, Sicherheit, Zeitgrenzen in Europe/Berlin, Fehlermeldungen, Bedienbarkeit, Testabdeckung. -->

### 3.7 Benutzeroberfläche

<!-- Bedienkonzept mit vier Tabs und Screenshots, Schnittstellen-Bedienkonzept über die REST-API. -->

## 4 Projektplanung

<!-- Rund zwei Seiten. -->

### 4.1 Vorgehensmodell

<!-- Phasen nach DIN 69901 aus dem Leitfaden auf das Projekt übertragen: Entwurf, Meilensteine, je Meilenstein Plan und testgetriebene Umsetzung. -->

### 4.2 Zeitplanung

<!-- Meilensteine M0 bis M7 mit Datum, Abbildung als Zeitleiste. Quelle: Projekttagebuch und Git-Historie. -->

### 4.3 Kostenplanung

<!-- Laufende Kosten 0 € durch die Gratis-Tarife von Render und Neon, Grenzen der Tarife, vorhandene Hardware. -->

### 4.4 Aufgabenverteilung

<!-- Nils Appel: Umsetzung von Backend, App und Cloud-Betrieb mit Claude Code, Tests auf den Handys.
     Michael Lafutkin: Planung (Einzelheiten offen), Anlegen des Repositorys. -->

## 5 Projektdurchführung

<!-- Rund sechs Seiten. -->

### 5.1 Anforderungsanalyse

### 5.2 Entwurf

<!-- Architektur mit Abbildung, Programmablauf am Beispiel „Aktivität speichern bis Achievement-Dialog“, Datenbankdesign, Bedienkonzept. -->

### 5.3 Implementierung

<!-- Backend, App, Betrieb in der Cloud. Wichtige Entscheidungen mit Begründung und die größten Probleme mit Lösung. -->

### 5.4 Tests

<!-- Testumfeld (eingebettetes PostgreSQL, ViewModel-Tests, zwei Handys), Testläufe (Zahlen, Testcheckliste, Abnahme am 04.11.). -->

### 5.5 Inbetriebnahme

<!-- Auslieferung über Render und Neon, App auf andere Handys, Kontrolle über den Health-Check. -->

## 6 Verwendete Techniken und Hilfsmittel

<!-- Rund 1,5 Seiten. Technologien mit Begründung, Werkzeuge. -->

### 6.1 Einsatz von KI

<!-- Wofür Claude Code genutzt wurde (Entwurf, Code, Tests, Doku), was das Team selbst entschieden, geprüft und getestet hat,
     und wie die Ergebnisse geprüft wurden. Mit der Betreuungslehrkraft klären, wie der Einsatz anzugeben ist. -->

## 7 Schluss

<!-- Rund eine Seite. Bezug zur Problemstellung, Fazit, kritische Reflexion, Handlungsempfehlung an den Kunden, Ausblick (Fotos, GPS-Tracking). -->

## Literaturverzeichnis

<!-- Alphabetisch, Schema aus vorgaben.md. Kandidaten: Dokumentation von Android, Kotlin, Ktor, Exposed, PostgreSQL, Render, Neon, Material Design 3, MapLibre, Vico, Coil. -->

## Anhang

<!-- Höchstens 20 Seiten: Projekttagebuch, API-Übersicht, Testcheckliste, Datenmodell, Screenshots. -->

## Eidesstattliche Erklärung

<!-- Wortlaut bei der Betreuungslehrkraft erfragen. -->
