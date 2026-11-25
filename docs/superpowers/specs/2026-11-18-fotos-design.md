# TouchGrass – Entwurf „Fotos“

Zweites Stretch Goal nach dem MVP. Grundlage sind der Hauptentwurf `2026-10-20-touchgrass-design.md` und für die Sichtbarkeit der Entwurf `2026-11-02-freunde-design.md`.

## 1. Ziel und Umfang

- Ein Foto pro Aktivität, aus der Galerie oder direkt mit der Kamera
- Hinzufügen beim Anlegen (auch nach dem Timer) und später in den Details der eigenen Aktivität: hinzufügen, ändern, entfernen
- Anzeige groß in den Details, als kleines Vorschaubild im Verlauf und im Fenster eines angetippten Markers auf der Karte
- Das Foto sieht, wer auch die Aktivität sehen darf

**Nicht geplant:** mehrere Fotos, Videos, Zuschneiden oder Bearbeiten in der App, Profilbilder, Kommentare oder Likes, Prüfung der Bildinhalte.

## 2. Regeln

### Foto

- Die App bringt das Foto vor dem Hochladen auf höchstens 512 Pixel an der längsten Seite. Das Seitenverhältnis bleibt, kleinere Fotos werden nicht vergrößert.
- Sie dreht das Foto nach seiner EXIF-Angabe richtig, rechnet seine Farben nach sRGB um und speichert es als JPEG mit Qualität 85. Ist es dann größer als 200 KB, speichert sie es mit stärkerer Kompression erneut. Lässt es sich nicht als JPEG mit höchstens 200 KB speichern, gilt es als nicht lesbar.
- Beim Neuspeichern fallen alle Zusatzdaten des Fotos weg, auch der Aufnahmeort.
- Transparente Bilder bekommen einen weißen Hintergrund. Bilder, die das Handy nicht lesen kann (zum Beispiel HEIC unter Android 8), meldet die App mit „Dieses Bildformat wird nicht unterstützt.“
- Ein neues Foto ersetzt das alte. Löschen der Aktivität oder des Kontos löscht das Foto.

### Sichtbarkeit

- Das Foto folgt der Sichtbarkeit seiner Aktivität: 🔒 Privat nur die Person selbst, 👥 Freunde zusätzlich ihre Freunde, 🌎 Öffentlich alle.
- Hinzufügen, Ändern und Entfernen geht nur bei eigenen Aktivitäten.
- Das Foto ist der einzige Teil einer Aktivität, der sich nach dem Speichern noch ändern lässt. Alle anderen Felder bleiben, wie sie sind.

### Speicherplatz

- Die Fotos liegen in der Datenbank bei Neon. Ein Ordner auf dem Server scheidet aus, weil Render dessen Inhalt bei jedem Neustart und jedem Deploy löscht.
- Neon bietet im Gratis-Tarif 1 GB pro Projekt. Ein Foto hat meist um 60 KB, also passen etwa 15.000 Fotos hinein, bei der Obergrenze von 200 KB immer noch über 5.000.

## 3. Backend

### Datenmodell

Neue Migration `V8__fotos.sql`:

```sql
CREATE TABLE activity_photos (
    activity_id BIGINT PRIMARY KEY REFERENCES activities (id) ON DELETE CASCADE,
    image       BYTEA  NOT NULL
);

CREATE SEQUENCE photo_versions;

ALTER TABLE activities ADD COLUMN photo_version BIGINT;
```

- `image` enthält das JPEG, in Exposed als `binary("image")`.
- `activities.photo_version` ist leer, solange es kein Foto gibt. Jedes Hochladen setzt eine neue Nummer aus `photo_versions`, Entfernen setzt die Spalte wieder auf leer. Die Nummer wiederholt sich also nie, auch nicht nach Entfernen und erneutem Hochladen.
- Weil die Nummer in `activities` steht, bleiben die bestehenden Abfragen für Verlauf, Karte, Statistik und Erfolge unverändert und laden nie Bilddaten.
- Hochladen schreibt das Bild (Einfügen oder Ersetzen) und die neue Nummer in einer Transaktion.

### Schnittstelle

| Methode | Pfad | Zweck | Antwort |
|---|---|---|---|
| PUT | `/activities/{id}/photo` | Body ist das JPEG mit `Content-Type: image/jpeg`, ersetzt ein vorhandenes Foto | 200 `ActivityDto` |
| DELETE | `/activities/{id}/photo` | Foto entfernen | 204, auch wenn keins da war |
| GET | `/activities/{id}/photo?v={photoVersion}` | das Foto | 200 `image/jpeg` |

- `ActivityDto` bekommt das Feld `photoVersion`. Fehlt es, hat die Aktivität kein Foto.
- GET nutzt dieselbe Sichtbarkeitsbedingung wie `GET /activities/{id}`. Der Parameter `v` dient nur dem Zwischenspeicher der App, der Server liefert immer das aktuelle Foto mit `Cache-Control: private, max-age=31536000, immutable`.
- 401 bleibt fehlendem oder ungültigem Token vorbehalten, denn die App meldet bei 401 ab. Ein Foto, das man nicht sehen darf, gibt deshalb 404.

| Fall | Status | Code | `field` | Meldung |
|---|---|---|---|---|
| GET: Aktivität fehlt, ist nicht sichtbar oder hat kein Foto | 404 | `NOT_FOUND` | – | Foto nicht gefunden. |
| PUT oder DELETE: Aktivität fehlt | 404 | `NOT_FOUND` | – | Aktivität nicht gefunden. |
| PUT oder DELETE: fremde Aktivität | 403 | `FORBIDDEN` | – | Das ist nicht deine Aktivität. |
| größer als 200 KB | 413 | `PAYLOAD_TOO_LARGE` | `photo` | Das Foto ist zu groß. |
| kein JPEG | 400 | `VALIDATION_ERROR` | `photo` | Bitte ein Foto im JPEG-Format schicken. |
| länger als 512 Pixel | 400 | `VALIDATION_ERROR` | `photo` | Das Foto darf höchstens 512 Pixel lang sein. |

Der Server prüft die Größe vor dem Lesen anhand von `Content-Length` und beim Lesen (höchstens 200 KB und ein Byte). Danach prüft er die JPEG-Kennung am Anfang und liest Breite und Höhe aus dem Kopf des JPEGs, ohne das Bild zu entpacken. So kann ein kleines JPEG mit riesigen Pixelangaben den Speicher nicht sprengen.

### Änderungen am Bestehenden

- `ActivitiesTable`, `ActivityRecord` und `ActivityDto` bekommen `photoVersion`.
- Die Foto-Abfragen stehen im `ActivityRepository`, damit die Sichtbarkeitsbedingung dort die einzige bleibt.
- Die Besitzprüfung beim Hochladen und Entfernen folgt `ActivityService.delete()`. Die Routen kommen in `ActivityRoutes.kt` in den bestehenden Block für `/activities`.

## 4. App

### Foto auswählen und verkleinern

- Galerie über Androids Fotoauswahl (Photo Picker), Kamera über die Kamera-App (`TakePicture`) mit einer Datei im Cache der App über einen eigenen `FileProvider`. Dafür braucht die App keine Berechtigung, insbesondere keine Kamera-Berechtigung.
- Das Verkleinern läuft im Hintergrund. Ab Android 9 mit `ImageDecoder`, der die Drehung schon berücksichtigt, unter Android 9 mit `BitmapFactory`, `inSampleSize` und der neuen Bibliothek `androidx.exifinterface`. Beide Wege dekodieren in sRGB, das fertige Bild hat immer 8 Bit je Farbkanal. So ist das JPEG auch bei Fotos mit großem Farbraum oder HDR ein gewöhnliches sRGB-JPEG.
- Ab Android 14 entfernt die App den Ultra-HDR-Anteil, damit das JPEG klein und normal bleibt.
- Das fertige JPEG liegt bis zum erfolgreichen Hochladen als Datei in der App, in einem Ordner, der nicht in die Datensicherung von Android geht. Beendet Android die App, während die Kamera offen ist, bleiben Foto und Formularfelder erhalten.
- Vor dem Hochladen legt die App das Foto unter der Nummer seiner Aktivität ab, je Aktivität höchstens eins. Scheitert das Hochladen an Netz oder Server oder bricht es ab, weil man den Screen verlässt oder Android die App beendet, wartet das Foto dort auf einen neuen Versuch. Lehnt der Server es ab (zu groß, kein JPEG, fremde oder fehlende Aktivität), löscht die App es, denn ein neuer Versuch scheiterte genauso.
- Abmelden, egal ob von Hand, durch Konto löschen oder durch ein 401, löscht alle Fotos der App, auch wartende, und übrig gebliebene Kamera-Aufnahmen. Abgemeldet gibt es kein Formular und keine Details, die sie noch brauchen.

### Formular

Gilt für das Eintragen von Hand und nach dem Timer.

- Neuer Bereich „Foto“ mit den Knöpfen „🖼️ Galerie“ und „📷 Kamera“. Nach der Auswahl erscheint eine Vorschau mit „Entfernen“.
- Speichern legt zuerst die Aktivität an und lädt dann das Foto hoch. Klappt das Hochladen nicht, bleibt die Aktivität gespeichert, und die App meldet: „Gespeichert, aber das Foto konnte nicht hochgeladen werden. Du kannst es in den Details erneut hinzufügen.“ Nach Netz- oder Serverfehlern wartet das Foto in den Details auf „Foto erneut hochladen“, nach einer Ablehnung durch den Server lässt sich dort ein anderes hinzufügen.
- Der Timer wird wie bisher geleert, sobald die Aktivität gespeichert ist.
- „Verwerfen“ löscht ein schon gewähltes Foto sofort. Verlässt man das Formular mit „← Zurück“, bleibt das Foto bis zum Abmelden in der App.

### Details

- Das Foto steht groß über die ganze Breite.
- Bei eigenen Aktivitäten ohne Foto gibt es „Foto hinzufügen“, mit Foto „Foto ändern“ und „Foto entfernen“. Vor dem Entfernen fragt die App „Foto entfernen?“ mit „Entfernen“ und „Abbrechen“.
- Wartet zu einer eigenen Aktivität noch ein Foto, gibt es zusätzlich „Foto erneut hochladen“. Der Knopf lädt genau dieses Foto hoch und verschwindet, sobald es angekommen ist oder der Server es abgelehnt hat. Ein neues Foto aus Galerie oder Kamera ersetzt das wartende, „Foto entfernen“ betrifft nur das Foto auf dem Server. Löschen der Aktivität löscht auch das wartende Foto.
- Während ein Foto vorbereitet und hochgeladen wird, steht dort ein Ladekreis, ohne den Hinweis, dass der Server aufwacht.
- Bei Aktivitäten anderer wird das Foto nur angezeigt.

### Verlauf und Karte

- Im Verlauf steht bei Aktivitäten mit Foto links ein kleines quadratisches Vorschaubild.
- Auf der Karte zeigt das Fenster eines angetippten Markers das Foto.

### Laden und Zwischenspeicher

- Die Bibliothek Coil lädt die Bilder, festgelegt auf Version 3.3.0. Neuere Versionen brauchen ein neueres Kotlin als die 2.2.10 der App.
- Coil nutzt den OkHttp-Client der App, damit das Login mitgeht, aber mit eigener Warteschlange, damit viele Vorschaubilder die Aufrufe der API nicht aufhalten. Die Adresse ist `<API_BASE_URL>activities/{id}/photo?v={photoVersion}`. Die App fragt nur nach einem Foto, wenn `photoVersion` gesetzt ist.
- Abmelden leert den Bildzwischenspeicher, egal ob von Hand, durch Konto löschen oder durch ein 401. Scheitert das Leeren, schreibt die App den Fehler ins Log und läuft weiter.

### Code

- `SportApi`: `uploadPhoto()` und `deletePhoto()`
- `data/dto/`: `ActivityDto.photoVersion`
- Neues Paket `ui/photo/` für Auswahl, Verkleinern und die Größenberechnung
- `AppContainer`: ein gemeinsamer OkHttp-Client für Retrofit und Coil, für Coil mit eigener Warteschlange. Die App-Klasse stellt Coil den `ImageLoader` bereit. Der Container legt die Ordner für Fotos fest und räumt beim Abmelden auf.
- `data/PendingPhotos`: wartende Fotos je Aktivität, genutzt von Formular und Details
- Manifest: `FileProvider` mit `res/xml/file_paths.xml`
- `RecordViewModel` sichert die Formularfelder und den Pfad des Fotos über `SavedStateHandle`.

## 5. Datenschutz

- Ein Foto sieht nur, wer auch die Aktivität sehen darf.
- Beim Verkleinern verschwinden alle Zusatzdaten des Fotos, auch der Aufnahmeort.
- Fotos liegen wie alle anderen Daten bei Neon in Frankfurt und verschwinden mit der Aktivität oder dem Konto.
- Abmelden leert den Bildzwischenspeicher und löscht alle Fotos, die die App auf dem Handy gespeichert hat. Diese Fotos gehen nicht in die Datensicherung von Android.
- Die Datenschutz-Tabelle bekommt die Zeile „Fotos zu Aktivitäten | Anzeige in Details, Verlauf und Karte | wer die Aktivität sehen darf“.

## 6. Tests

**Backend** (API-Tests gegen das eingebettete PostgreSQL)

- Hochladen, Ersetzen mit neuer `photoVersion`, Abrufen mit den richtigen Headern, Entfernen
- Sichtbarkeit beim Abrufen: eigene Aktivität ja, private fremde nein, „👥 Freunde“ eines Freundes ja, öffentliche ja
- alle Fehlerfälle aus der Tabelle in Abschnitt 3, dazu 401 ohne Token
- Entfernen trifft nur das Foto dieser Aktivität, ein verbotenes Ändern lässt ein fremdes Foto stehen, schon ein angekündigtes `Content-Length` über 200 KB ergibt 413, ein kleines JPEG mit riesigen Pixelangaben wird abgelehnt, ohne dass der Server es entpackt
- Löschen der Aktivität entfernt das Foto, `photoVersion` steht in Verlauf, Karte und Details

**App** (ViewModel-Tests mit `FakeSportApi`)

- Formular: Hochladen nach dem Speichern, Meldung bei Fehler, kein Hochladen ohne Foto, Foto wartet nach einem Netzfehler, „Verwerfen“ löscht das Foto
- Details: hinzufügen, ändern, entfernen mit Rückfrage, erneut hochladen, Löschen der Aktivität löscht ein wartendes Foto
- Größenberechnung und Wahl der JPEG-Qualität als reine Funktionen, wartende Fotos mit Dateien in einem Testordner

**Von Hand:** Die Testcheckliste bekommt den Abschnitt „Fotos“: Galerie und Kamera, Hoch- und Querformat richtig gedreht, nicht lesbares Format, App während der Kamera beendet, Anzeige auf dem zweiten Handy je nach Sichtbarkeit, „Foto erneut hochladen“ nach einem Fehler im Flugmodus, Abmelden leert die Bilder und löscht ein wartendes Foto.

## 7. Doku und Auslieferung

- `docs/api.md` zuerst: neue Aufrufe, Feld `photoVersion`, Status 413 und die neuen Meldungen
- README: Datenschutz-Tabelle, unter „Bewusste Grenzen“: Fotos werden nicht inhaltlich geprüft.
- Der Hauptentwurf wird auf den neuen Stand gebracht.
- Auslieferung: pushen, Render baut neu, Flyway legt bei Neon `activity_photos` an. `commit` in `/api/health` prüfen, dann die App auf die Handys bringen. Alte App-Versionen laufen weiter, zeigen aber keine Fotos.
