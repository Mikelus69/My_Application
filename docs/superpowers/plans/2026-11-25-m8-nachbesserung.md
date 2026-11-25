# M8 Nachbesserung – Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Befunde der Gesamtprüfung von M8 beheben, bevor Task 8 des M8-Plans die Doku schreibt. Die App behält ein vorbereitetes Foto bis zum erfolgreichen Hochladen und bietet in den Details „Foto erneut hochladen“ an. Sie räumt Fotoreste beim Abmelden und bei „Verwerfen“ auf und legt Fotos außerhalb der Datensicherung von Android ab. Sie stürzt beim Leeren der Bildspeicher nicht mehr ab, rechnet Fotos nach sRGB um und gibt Coil eine eigene Warteschlange. Backend und App bekommen die fehlenden Tests.

**Architecture:** Das Backend bleibt unverändert, dort kommen nur Tests dazu. In der App legt `AppContainer` die Ordner fest: neue Fotos in `noBackupFilesDir/photos/`, wartende in `noBackupFilesDir/photos/pending/<Aktivitäts-Id>.jpg`. Die neue Klasse `PendingPhotos` im Paket `data` verschiebt ein vorbereitetes Foto vor dem Hochladen dorthin, lädt es hoch und löscht es nach Erfolg oder endgültiger Ablehnung; nach Netz- und Serverfehlern bleibt es liegen. `RecordViewModel` und `ActivityDetailViewModel` laden nur noch über `PendingPhotos` hoch, die Details zeigen bei einer wartenden Datei „Foto erneut hochladen“. Beim Abmelden leert der Container die Caches von Coil, ohne bei einem Fehler abzustürzen, und löscht den Foto-Ordner und `cacheDir/camera`. Coil bekommt eine Kopie des OkHttp-Clients mit eigenem `Dispatcher`.

**Tech Stack:** wie M8: Kotlin 2.2.10, Jetpack Compose, Retrofit 3.0.0 mit OkHttp 4.12.0, Coil 3.3.0, androidx.exifinterface 1.4.2, core-ktx 1.19.0; Backend Ktor 3.6, Exposed 1.6, PostgreSQL (eingebettet im Test). Keine neue Bibliothek, keine neue Berechtigung.

**Spec:** `docs/superpowers/specs/2026-11-18-fotos-design.md` im Stand nach dem Commit „Entwurf Fotos: Foto bis zum Hochladen behalten und Fotoreste aufräumen“. Grundlage sind die Gesamtprüfung von M8 (Commits `4c3c692..9614452`) und die Entscheidungen des Users vom 25.11.2026.

## Global Constraints

- Alle Commits direkt auf `master`, keine Branches, nicht pushen. Gepusht wird nur vom User, erst nach Task 8 des M8-Plans. Commit-Nachrichten auf Deutsch mit Trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Jede Aufgabe ergänzt im selben Commit einen Eintrag im Projekttagebuch `docs/projektdoku/projekttagebuch.md` unter dem Abschnitt des Arbeitstags.
- Kein Endpunkt, keine Antwort und keine Meldung der API ändert sich: `docs/api.md`, die Migrationen und der Produktionscode des Backends bleiben, wie sie sind. Die Gegenprobe in Task 1 ändert Produktionscode nur vorübergehend und setzt ihn mit `git checkout --` zurück.
- Ordner der App: neue Fotos `noBackupFilesDir/photos/<UUID>.jpg`, wartende Fotos `noBackupFilesDir/photos/pending/<Aktivitäts-Id>.jpg` (höchstens eins je Aktivität), Kamera-Aufnahmen wie bisher `cacheDir/camera/<UUID>.jpg` (`res/xml/file_paths.xml` bleibt).
- Ein wartendes Foto bleibt nur nach einer `ApiException` mit `code` `NETWORK`, `INTERNAL_ERROR` oder `HTTP_5…` und nach einem Abbruch der Coroutine (Screen verlassen, App beendet). Gelöscht wird es nach erfolgreichem Hochladen, nach jedem anderen Fehler (unter anderem `PAYLOAD_TOO_LARGE`, `VALIDATION_ERROR`, `FORBIDDEN`, `NOT_FOUND`, `UNAUTHORIZED`, `BAD_RESPONSE`, `HTTP_4…`), nach dem Löschen der Aktivität in den Details und beim Abmelden.
- Abmelden heißt jeder Wechsel von `AuthSession.state` zu `AuthState.LoggedOut`: von Hand, „Konto löschen“, ein 401 und jeder Kaltstart ohne Token. Dann leert `AppContainer` Speicher- und Datei-Cache von Coil (ein Fehler dabei wird nur geloggt) und löscht `noBackupFilesDir/photos` und `cacheDir/camera` samt Inhalt.
- Verkleinern: Dekodieren in sRGB (`ImageDecoder.setTargetColorSpace`, `BitmapFactory.Options.inPreferredColorSpace`), Ergebnis immer `ARGB_8888` in sRGB ohne Transparenz, Weiß darunter. Größe und Qualitäten wie in M8: höchstens 512 Pixel an der längsten Seite, Qualität 85, 75, 65, 55, 45, höchstens 200 KB (`204_800` Byte). Scheitert `Bitmap.compress` oder bleibt das JPEG auch mit Qualität 45 größer, gilt das Foto als nicht lesbar: `PhotoException(UNSUPPORTED_PHOTO, Ursache)`.
- Logging nur in Klassen, die in keinem JVM-Test laufen (`AndroidPhotoPreparer`, `AppContainer`), mit `Log.w` und dem Tag `TouchGrass`. `android.util.Log` ist in lokalen Unit-Tests eine Attrappe, die wirft (`unitTests.isReturnDefaultValues` ist nicht gesetzt).
- App-Texte wörtlich. Neu ist nur „Foto erneut hochladen“. Unverändert bleiben unter anderem „Gespeichert, aber das Foto konnte nicht hochgeladen werden. Du kannst es in den Details erneut hinzufügen.“, „Dieses Bildformat wird nicht unterstützt.“, „Keine Kamera-App gefunden.“, „Foto hinzufügen“, „Foto ändern“, „Foto entfernen“ und „Server nicht erreichbar“ aus `apiCall`.
- Nicht gewünscht: Host-Prüfung im `AuthInterceptor`, andere Sperrreihenfolge in `savePhoto` und `deletePhoto`, Logging im `catch` von `PhotoRules.headerSize`, neue Berechtigungen, neue Bibliotheken.
- Die App bleibt bei Kotlin 2.2.10 und Coil 3.3.0, minSdk 26. Jede neue Android-API ist dagegen geprüft (Tabelle unter „Entscheidungen“).
- Testnamen deutsch in Backticks, Code englisch, UI-Texte deutsch, wenig Kommentare: eine Begründung an genau einer Stelle.

## Entscheidungen, die über den Entwurf hinausgehen

- **`PendingPhotos` im Paket `data`:** Die Klasse kennt nur `java.io.File`, `SportApi` und `apiCall` und läuft deshalb im JVM-Test mit `TemporaryFolder` und Fake-API. Eine Liste der wartenden Fotos gibt es nicht, der Dateiname ist die Aktivitäts-Id. Ein neues Foto ersetzt ein wartendes.
- **Verschieben vor dem ersten Versuch:** `PendingPhotos.upload` verschiebt das Foto vor dem Hochladen nach `pending/<Id>.jpg`. So bleibt es auch nach einem Abbruch oder dem Beenden der App während des Hochladens erhalten. `Files.move` mit `REPLACE_EXISTING` ersetzt eine vorhandene Datei auch unter Windows, wo die Unit-Tests laufen; `File.renameTo` scheitert dort an einer vorhandenen Datei.
- **Welche Fehler das Foto behalten:** `ApiException` kennt keinen HTTP-Status, nur `code`. Das Backend antwortet mit 5xx nur als `INTERNAL_ERROR` (`StatusPages`, `exception<Throwable>`), eine 5xx ohne JSON (etwa 502 oder 503 von Render) wird in `apiCall` zu `HTTP_5…`, ein Netzfehler oder Timeout zu `NETWORK`. `BAD_RESPONSE` gibt es nur bei einer 2xx-Antwort, das Foto ist dann angekommen.
- **Verschieben in `apiCall`:** Das Verschieben läuft im Block von `apiCall`. Scheitert es, etwa weil die Datei fehlt, wird daraus `ApiException("NETWORK", …)` wie bei einem Netzfehler. Die ViewModels fangen nur `ApiException`, ein Absturz ist so ausgeschlossen.
- **Dateioperationen ohne eigenen Dispatcher:** `PendingPhotos` verschiebt, prüft und löscht im Kontext des Aufrufers. Es sind einzelne Umbenennungen im selben Ordner, wie das schon vorhandene `file.delete()` in den ViewModels. Mit `Dispatchers.IO` liefen die ViewModel-Tests mit `UnconfinedTestDispatcher` nicht mehr synchron.
- **Details:** `photoPending` liest das ViewModel nach jedem Laden und nach jedem Fotowechsel neu von der Platte. `has` ist `false`, solange ein Hochladen dieser Aktivität läuft, auch eines aus dem Formular, das nach einem Tab-Wechsel im Hintergrund weiterläuft; sonst böte der Knopf ein Foto an, das gerade unterwegs ist. „Foto erneut hochladen“ steht als gefüllter Knopf über „Foto ändern“, nur bei eigenen Aktivitäten und nicht, solange `photoBusy` gilt. „Foto entfernen“ betrifft nur das Foto auf dem Server, ein wartendes bleibt. Das Löschen der Aktivität entfernt das wartende Foto nur, wenn es geklappt hat.
- **Formular ohne neuen Zustand:** `RecordViewModel` lädt über `PendingPhotos` hoch, Meldung und Ablauf bleiben. Nach „← Zurück“ bleibt ein gewähltes Foto bis zum Abmelden liegen; ein Aufräumen in `onCleared` würde das Foto eines gesicherten Formulars löschen (siehe M8-Plan). `SavedStateHandle` sichert wie bisher den Pfad des gewählten Fotos und während des Speicherns kein Formular.
- **Abmelden als Zeitpunkt zum Aufräumen:** Ohne Anmeldung zeigt `TouchGrassRoot` nur `AuthNavigation`, es gibt also kein Formular und keine Details, die ein Foto noch brauchen. Ein Kaltstart mit Token geht von `Loading` direkt zu `LoggedIn` und räumt nichts auf; ein nach dem Beenden der App gesichertes Formular behält sein Foto.
- **Reine Funktionen für Testbares:** `jpegWithinLimit` (Schleife über die Qualitäten mit geprüftem Rückgabewert von `compress`) in `ui/photo/PhotoSize.kt`, `imageClient` (Kopie des Clients mit eigenem `Dispatcher`) in `data/SportApi.kt` neben `httpClient`. `cameraDir(context)` in `PhotoFileProvider.kt` ist die eine Stelle für `cacheDir/camera`, die `PhotoPickers` und `AppContainer` nutzen.
- **Alter Ordner `filesDir/photos`:** Die App räumt ihn nicht auf. M8 ist nicht ausgeliefert, Reste kann es nur auf Entwicklerhandys geben.
- **Gegenproben:** Tests, die bestehendes Verhalten absichern, sind gegen den heutigen Code sofort grün. Ihr roter Schritt ist eine Gegenprobe: Produktionscode gezielt kaputt machen, der Lauf zeigt genau diese Tests rot, danach `git checkout --` und der grüne Lauf.

API-Prüfung gegen minSdk 26 und die festgelegten Versionen:

| API | verfügbar ab | Stelle |
|---|---|---|
| `ImageDecoder.setTargetColorSpace(ColorSpace)` | API 28 wie `ImageDecoder` selbst | nur in `decodeWithImageDecoder` mit `@RequiresApi(Build.VERSION_CODES.P)` |
| `BitmapFactory.Options.inPreferredColorSpace`, `Bitmap.colorSpace`, `ColorSpace.get(ColorSpace.Named.SRGB)` | API 26 | `AndroidPhotoPreparer` |
| `androidx.core.graphics.createBitmap(width, height, config, hasAlpha, colorSpace)` | core-ktx 1.19.0, `@RequiresApi(26)` | `toSrgbOnWhite` |
| `Context.noBackupFilesDir` | API 21 | `AppContainer` |
| `java.nio.file.Files.move`, `StandardCopyOption`, `File.toPath()` | API 26 | `PendingPhotos` |
| `okhttp3.Dispatcher()`, `OkHttpClient.newBuilder()` | OkHttp 4.12.0 | `imageClient` |
| `OkHttpNetworkFetcherFactory(callFactory = { … })` | Coil 3.3.0, wie bisher | `AppContainer` |
| `OutgoingContent.WriteChannelContent`, `ByteWriteChannel.writeFully(ByteArray)` | Ktor 3.6.0 (ktor-http, ktor-io) | `PhotoRoutesTest` |

## Ausführungshinweise

- **Für Claude:** Gradle nur im Terminal-Panel (`run_in_terminal`), in einem eigenen Tab, nie zwei Läufe gleichzeitig. Jeder Lauf ist eine einzige ASCII-Zeile ohne Pipes und ohne eigene Variablen. Backend: `$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'; Set-Location 'C:\Users\nils.appel\StudioProjects\My_Application\backend'; <Befehl>`, App: dieselbe Zeile mit `Set-Location 'C:\Users\nils.appel\StudioProjects\My_Application'`. In `--tests` stehen nur Klassennamen, nie Testnamen mit Umlauten.
- Backend: `.\gradlew.bat --no-daemon -q test --tests 'de.touchgrass.PhotoRoutesTest'`, alle Tests: `.\gradlew.bat --no-daemon -q test`.
- App: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.ui.record.RecordViewModelTest'`, alle Tests: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest`, Build und Lint: `.\gradlew.bat --no-daemon -q assembleDebug :app:lintDebug`.
- Mit `-q` heißt keine Ausgabe Erfolg. Die Zahlen stehen in den Testberichten `backend\build\test-results\test\TEST-<Klasse>.xml` und `app\build\test-results\testDebugUnitTest\TEST-<Klasse>.xml` (Attribute `tests`, `failures`, `errors`), Lint schreibt nach `app\build\reports\lint-results-debug.html`.
- „Rot“ heißt oft „kompiliert nicht“, weil der neue Test Klassen oder Funktionen nutzt, die es noch nicht gibt. Das zählt als roter Schritt. Bei Tests, die bestehendes Verhalten absichern, ist die Gegenprobe der rote Schritt.
- Git läuft in der Shell, nicht im Terminal-Panel. `git add` nennt jede Datei einzeln.
- Testzahlen: Start Backend 137 und App 132. Backend 140 nach Task 1. App 135 nach Task 2, 140 nach Task 3, 141 nach Task 4 und 150 nach Task 5.
- Nicht in JVM-Tests prüfbar sind `AndroidPhotoPreparer`, `AppContainer`, `PhotoPickers` und die Screens. Für sie gibt es Build und Lint und die Testcheckliste, die Task 8 des M8-Plans schreibt (Prüfpunkt „Foto erneut hochladen“, Abmelden löscht ein wartendes Foto).
- Reihenfolge: Task 1 bis 5 nacheinander, danach Task 8 von `docs/superpowers/plans/2026-11-18-m8-fotos.md` in der angepassten Fassung. Die Nachbesserung ändert keine der Dateien, die Task 8 bearbeitet, außer dem Projekttagebuch.
- **Für Claude, Fortschritt mit subagent-driven-development:** Das Ledger `.superpowers/sdd/progress.md` führt schon `Task 1: complete` bis `Task 7: complete` von M8, und `task-N-brief.md`, `task-N-report.md` und `plan-kopf.md` stammen von M8. Vor Task 1 im Ledger die Zeile `# Fortschritt Nachbesserung M8 (Plan docs/superpowers/plans/2026-11-25-m8-nachbesserung.md, Start <HEAD>)` anhängen und jede Aufgabe als `Nachbesserung Task N: complete (commits …, review clean)` eintragen, Task 8 danach als `M8 Task 8: complete (…)`. Zeilen `Task N: complete` ohne Vorsatz gehören zu M8. Briefs mit eigenem Namen erzeugen (`task-brief docs/superpowers/plans/2026-11-25-m8-nachbesserung.md N .superpowers/sdd/nachbesserung-task-N-brief.md`, Bericht `nachbesserung-task-N-report.md`). Den Kopf dieses Plans (alles vor `### Task 1`) als `.superpowers/sdd/nachbesserung-plan-kopf.md` ablegen, ihn bekommen Implementierer und Prüfer von Task 1 bis 5. `plan-kopf.md` vom 23.11. geht an keinen Subagenten: Seine Entscheidungen „Dateien“ und „Formular übersteht das Beenden“ widersprechen Task 4 und 5. Vor Task 8 dessen Brief mit `task-brief docs/superpowers/plans/2026-11-18-m8-fotos.md 8` neu erzeugen und `plan-kopf.md` aus dem geänderten M8-Plan neu schreiben (alles vor `### Task 1`).
- Projekttagebuch: Der Punkt einer Aufgabe kommt ans Ende des Abschnitts des Arbeitstags. Am 25.11.2026 ist das `## 25.11.2026 – Gesamtprüfung und Nachbesserung M8`. Gibt es für den Arbeitstag noch keinen Abschnitt, zuerst am Dateiende nach einer Leerzeile die Überschrift `## [Datum des Arbeitstags] – Nachbesserung M8` anlegen (Datum als `TT.MM.JJJJ`) und den Punkt darunter setzen.
- Dieser Plan liegt unter `docs/superpowers/plans/2026-11-25-m8-nachbesserung.md` im Commit „Plan für die Nachbesserung von M8“ zusammen mit der Anpassung von Task 8 im M8-Plan. Task 8 zählt ihn in Tab. 5 der Projektdoku mit und verlinkt ihn in der Übergabe.
- Stand beim Schreiben: Code, Anker und Testzahlen sind gegen `HEAD` `9614452` und die Testberichte vom 23.11.2026 abgeglichen (Backend 137, App 132). Den manipulierten JPEG-Kopf aus Task 1 hat ein eigenes Java-Programm unter JBR 25 geprüft: Der Kopf meldet 65.500 × 65.500 Pixel, `reader.read(0)` wirft `IIOException`. Dass der Test-Client von Ktor 3.6.0 `Content-Length` aus `OutgoingContent.contentLength` setzt und der Server den Body nicht gegen die Länge prüft, ist im Bytecode von `TestHttpClientEngine` und `BaseApplicationRequest` nachgesehen. Der Stand nach Task 5 ist außerhalb von Gradle mit dem Kotlin-Compiler aus Android Studio übersetzt: `PendingPhotosTest`, `RecordViewModelTest` und `ActivityDetailViewModelTest` laufen mit JUnit grün (43 Tests), `AppContainer` und `AndroidPhotoPreparer` kompilieren gegen `android.jar`. Ein Durchlauf des ganzen Plans in einer Kopie steht aus.

---

### Task 1: Backend – Tests für Fotos schärfen

**Files:**
- Test: `backend/src/test/kotlin/de/touchgrass/activities/PhotoRulesTest.kt`, `backend/src/test/kotlin/de/touchgrass/PhotoRoutesTest.kt`
- Modify: `docs/projektdoku/projekttagebuch.md`
- Nur für die Gegenprobe, danach unverändert: `backend/src/main/kotlin/de/touchgrass/activities/ActivityRepository.kt`, `backend/src/main/kotlin/de/touchgrass/activities/ActivityService.kt`, `backend/src/main/kotlin/de/touchgrass/activities/PhotoRules.kt`, `backend/src/main/kotlin/de/touchgrass/routes/ActivityRoutes.kt`

**Interfaces:**
- Consumes: aus `TestApp.kt` `fun ApplicationTestBuilder.startApp(clock: Clock = Clock.systemUTC()): HttpClient`, `suspend fun HttpClient.register(username: String, …): AuthResponse`, `suspend fun HttpClient.createActivity(token: String, request: CreateActivityRequest = activity()): CreateActivityResponse`, `fun activity(…, visibility: Visibility = Visibility.PRIVATE, …)`, `suspend fun HttpClient.befriend(token: String, username: String, otherToken: String, otherUsername: String)`, `fun jpeg(width: Int = 4, height: Int = 3): ByteArray`, `suspend fun HttpClient.putPhoto(token: String, id: Long, bytes: ByteArray = jpeg()): HttpResponse`; `PhotoRules.MAX_BYTES`, `PhotoRules.validate(jpeg: ByteArray)`, `ActivityPhotosTable`, `ErrorResponse(error, message, field)`; in `PhotoRoutesTest` die privaten Helfer `details`, `deletePhoto`, `storedPhotos` und `photo`.
- Produces: keine Code-Schnittstellen. Neu sind `` `kleines JPEG mit riesigen Pixelangaben wird abgelehnt` `` in `PhotoRulesTest` (3 → 4 Tests) sowie `` `Entfernen löscht nur das Foto dieser Aktivität` `` und `` `angekündigte Länge über 200 KB liefert 413, ohne den Body zu lesen` `` in `PhotoRoutesTest` (14 → 16 Tests). `` `Foto einer fremden Aktivität ändern liefert 403` `` heißt jetzt `` `Foto einer fremden Aktivität ändern liefert 403 und lässt ihr Foto stehen` `` und arbeitet mit einem vorhandenen Foto. `` `Löschen der Aktivität oder des Kontos löscht das Foto` ``, `` `eigenes Foto kommt als JPEG mit Cache-Control und genau den hochgeladenen Bytes` ``, `` `fremdes Foto nur bei öffentlicher Aktivität oder als Freund` `` und `` `ohne Foto, nach dem Entfernen und ohne Aktivität liefert 404` `` prüfen jede Antwort von `putPhoto`. Backend 137 → 140.

- [ ] **Step 1: Test für ein JPEG mit riesigen Pixelangaben**

`backend/src/test/kotlin/de/touchgrass/activities/PhotoRulesTest.kt` komplett:
```kotlin
package de.touchgrass.activities

import de.touchgrass.jpeg
import de.touchgrass.plugins.ApiException
import java.awt.color.ColorSpace
import java.awt.color.ICC_Profile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PhotoRulesTest {
    private fun failure(bytes: ByteArray): List<String?> =
        assertFailsWith<ApiException> { PhotoRules.validate(bytes) }.let { listOf(it.field, it.message) }

    private fun png(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    // Mit dem PYCC-Profil der JDK im Kopf wirft der JPEG-Reader eine CMMException, weil LCMS das Profil nicht wieder als Bytes ausgeben kann.
    private fun jpegWithPyccProfile(): ByteArray {
        val chunks = ICC_Profile.getInstance(ColorSpace.CS_PYCC).data.toList().chunked(65_000)
        val segments = chunks.mapIndexed { index, chunk ->
            val length = 16 + chunk.size
            byteArrayOf(0xFF.toByte(), 0xE2.toByte(), (length shr 8).toByte(), length.toByte()) +
                "ICC_PROFILE".encodeToByteArray() + byteArrayOf(0, (index + 1).toByte(), chunks.size.toByte()) + chunk.toByteArray()
        }
        val image = jpeg()
        return segments.fold(image.copyOfRange(0, 2)) { start, segment -> start + segment } + image.copyOfRange(2, image.size)
    }

    // Hinter dem Marker FF C0 (SOF0) stehen Länge (2 Byte), Genauigkeit (1), Höhe (2) und Breite (2). 0xFFDC sind 65.500 Pixel.
    private fun jpegClaimingHugeSize(): ByteArray {
        val bytes = jpeg()
        val sof = (0 until bytes.size - 1).first { bytes[it] == 0xFF.toByte() && bytes[it + 1] == 0xC0.toByte() }
        for (index in sof + 5..sof + 8 step 2) {
            bytes[index] = 0xFF.toByte()
            bytes[index + 1] = 0xDC.toByte()
        }
        return bytes
    }

    @Test
    fun `JPEG bis 512 Pixel an der längsten Seite ist gültig`() {
        PhotoRules.validate(jpeg())
        PhotoRules.validate(jpeg(512, 384))
        PhotoRules.validate(jpeg(384, 512))
    }

    @Test
    fun `längste Seite über 512 Pixel wird abgelehnt`() {
        val tooLong = listOf("photo", "Das Foto darf höchstens 512 Pixel lang sein.")
        assertEquals(tooLong, failure(jpeg(513, 384)))
        assertEquals(tooLong, failure(jpeg(10, 600)))
    }

    @Test
    fun `andere Formate und kaputte JPEGs werden abgelehnt`() {
        val notJpeg = listOf("photo", "Bitte ein Foto im JPEG-Format schicken.")
        val brokenJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + "kein Bild".encodeToByteArray() + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        assertEquals(notJpeg, failure(ByteArray(0)))
        assertEquals(notJpeg, failure(png()))
        assertEquals(notJpeg, failure(brokenJpeg))
        assertEquals(notJpeg, failure(jpeg().let { it.copyOf(it.size - 2) }))
        val unusualProfile = runCatching { PhotoRules.validate(jpegWithPyccProfile()) }.exceptionOrNull()
        assertTrue(unusualProfile == null || unusualProfile is ApiException, "unerwartete Exception: $unusualProfile")
    }

    @Test
    fun `kleines JPEG mit riesigen Pixelangaben wird abgelehnt`() {
        assertEquals(listOf("photo", "Das Foto darf höchstens 512 Pixel lang sein."), failure(jpegClaimingHugeSize()))
    }
}
```
Zum Test: `jpeg()` (4 × 3 Pixel, 631 Byte) enthält den Marker `FF C0` genau einmal, an Stelle 158. Nach dem Ändern meldet der Kopf 65.500 × 65.500 Pixel, die Datei bleibt 631 Byte groß und endet weiter auf `FF D9`. `PhotoRules` liest nur den Kopf und lehnt das Foto sofort als zu lang ab. Würde jemand das Bild entpacken (`ImageIO.read` oder `reader.read(0)`), wirft der Reader unter JBR 25 `IIOException: Can not read image of the size 65500 by 65500`; der breite `catch` macht daraus „kein JPEG“, und der Test ist rot.

- [ ] **Step 2: API-Tests schärfen**

`backend/src/test/kotlin/de/touchgrass/PhotoRoutesTest.kt` komplett:
```kotlin
package de.touchgrass

import de.touchgrass.activities.ActivityDto
import de.touchgrass.activities.ActivityPhotosTable
import de.touchgrass.activities.PhotoRules
import de.touchgrass.activities.Visibility
import de.touchgrass.plugins.ErrorResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoRoutesTest {
    @BeforeTest
    fun setUp() = TestDatabase.reset()

    private suspend fun HttpClient.details(token: String, id: Long): ActivityDto = get("/api/activities/$id") { bearerAuth(token) }.body()

    private suspend fun HttpClient.deletePhoto(token: String, id: Long): HttpResponse =
        delete("/api/activities/$id/photo") { bearerAuth(token) }

    private fun storedPhotos(): Long = transaction(TestDatabase.database) { ActivityPhotosTable.selectAll().count() }

    private suspend fun HttpClient.photo(token: String, id: Long): HttpResponse = get("/api/activities/$id/photo") { bearerAuth(token) }

    @Test
    fun `Hochladen liefert die Aktivität mit photoVersion`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id

        val response = client.putPhoto(token, id)

        assertEquals(HttpStatusCode.OK, response.status)
        val dto = response.body<ActivityDto>()
        assertEquals(id, dto.id)
        assertNotNull(dto.photoVersion)
    }

    @Test
    fun `erneutes Hochladen vergibt eine größere photoVersion`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id

        val first = client.putPhoto(token, id).body<ActivityDto>().photoVersion!!
        val second = client.putPhoto(token, id, jpeg(3, 4)).body<ActivityDto>().photoVersion!!

        assertTrue(second > first)
        assertEquals(second, client.details(token, id).photoVersion)
    }

    @Test
    fun `Entfernen liefert 204 auch ohne Foto, die Nummer wiederholt sich danach nicht`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id
        val first = client.putPhoto(token, id).body<ActivityDto>().photoVersion!!

        val removed = client.deletePhoto(token, id)
        val removedAgain = client.deletePhoto(token, id)
        val afterRemoval = client.details(token, id).photoVersion
        val uploadedAgain = client.putPhoto(token, id).body<ActivityDto>().photoVersion!!

        assertEquals(HttpStatusCode.NoContent, removed.status)
        assertEquals(HttpStatusCode.NoContent, removedAgain.status)
        assertNull(afterRemoval)
        assertTrue(uploadedAgain > first)
    }

    @Test
    fun `Entfernen löscht nur das Foto dieser Aktivität`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val first = client.createActivity(token).activity.id
        val second = client.createActivity(token).activity.id
        val firstUpload = client.putPhoto(token, first).status
        val secondVersion = client.putPhoto(token, second).body<ActivityDto>().photoVersion

        val removed = client.deletePhoto(token, first)

        assertEquals(HttpStatusCode.OK, firstUpload)
        assertEquals(HttpStatusCode.NoContent, removed.status)
        assertEquals(1L, storedPhotos())
        assertNull(client.details(token, first).photoVersion)
        assertNotNull(secondVersion)
        assertEquals(secondVersion, client.details(token, second).photoVersion)
        assertEquals(HttpStatusCode.OK, client.photo(token, second).status)
    }

    @Test
    fun `photoVersion steht in Verlauf, Karte und Details`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token, activity(latitude = 48.1642, longitude = 11.6056)).activity.id
        val version = client.putPhoto(token, id).body<ActivityDto>().photoVersion

        val history = client.get("/api/activities") { bearerAuth(token) }.body<List<ActivityDto>>()
        val map = client.get("/api/map/activities") { bearerAuth(token) }.body<List<ActivityDto>>()

        assertNotNull(version)
        assertEquals(listOf(version), history.map { it.photoVersion })
        assertEquals(listOf(version), map.map { it.photoVersion })
        assertEquals(version, client.details(token, id).photoVersion)
    }

    @Test
    fun `Foto einer fremden Aktivität ändern liefert 403 und lässt ihr Foto stehen`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa").token
        val id = client.createActivity(lisa, activity(visibility = Visibility.PUBLIC)).activity.id
        val lisasPhoto = jpeg()
        val version = client.putPhoto(lisa, id, lisasPhoto).body<ActivityDto>().photoVersion

        val upload = client.putPhoto(max, id, jpeg(3, 4))
        val remove = client.deletePhoto(max, id)

        for (response in listOf(upload, remove)) {
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(ErrorResponse("FORBIDDEN", "Das ist nicht deine Aktivität."), response.body<ErrorResponse>())
        }
        assertNotNull(version)
        assertEquals(version, client.details(lisa, id).photoVersion)
        assertEquals(1L, storedPhotos())
        assertContentEquals(lisasPhoto, client.photo(lisa, id).bodyAsBytes())
    }

    @Test
    fun `fehlende Aktivität und Id abc liefern 404`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token

        val responses = listOf(
            client.putPhoto(token, 999999),
            client.put("/api/activities/abc/photo") { bearerAuth(token); contentType(ContentType.Image.JPEG); setBody(jpeg()) },
            client.deletePhoto(token, 999999),
            client.delete("/api/activities/abc/photo") { bearerAuth(token) },
        )

        for (response in responses) {
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(ErrorResponse("NOT_FOUND", "Aktivität nicht gefunden."), response.body<ErrorResponse>())
        }
    }

    @Test
    fun `Hochladen und Entfernen brauchen eine Anmeldung`() = testApplication {
        val client = startApp()

        val upload = client.put("/api/activities/1/photo") { contentType(ContentType.Image.JPEG); setBody(jpeg()) }
        val remove = client.delete("/api/activities/1/photo")

        assertEquals(HttpStatusCode.Unauthorized, upload.status)
        assertEquals(HttpStatusCode.Unauthorized, remove.status)
    }

    @Test
    fun `mehr als 200 KB liefert 413, auch ohne Content-Length`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id
        val tooLarge = ByteArray(PhotoRules.MAX_BYTES + 1)

        val withLength = client.putPhoto(token, id, tooLarge)
        val streamed = client.put("/api/activities/$id/photo") {
            bearerAuth(token)
            contentType(ContentType.Image.JPEG)
            setBody(ByteReadChannel(tooLarge))
        }
        val exactlyMax = client.putPhoto(token, id, ByteArray(PhotoRules.MAX_BYTES))

        for (response in listOf(withLength, streamed)) {
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertEquals(ErrorResponse("PAYLOAD_TOO_LARGE", "Das Foto ist zu groß.", "photo"), response.body<ErrorResponse>())
        }
        assertEquals(HttpStatusCode.BadRequest, exactlyMax.status)
    }

    @Test
    fun `angekündigte Länge über 200 KB liefert 413, ohne den Body zu lesen`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id
        val smallBodyWithLargeLength: OutgoingContent = object : OutgoingContent.WriteChannelContent() {
            override val contentType = ContentType.Image.JPEG
            override val contentLength = PhotoRules.MAX_BYTES + 1L
            override suspend fun writeTo(channel: ByteWriteChannel) = channel.writeFully(jpeg())
        }

        val response = client.put("/api/activities/$id/photo") { bearerAuth(token); setBody(smallBodyWithLargeLength) }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals(ErrorResponse("PAYLOAD_TOO_LARGE", "Das Foto ist zu groß.", "photo"), response.body<ErrorResponse>())
        assertNull(client.details(token, id).photoVersion)
    }

    @Test
    fun `kein JPEG oder länger als 512 Pixel liefert 400 am Feld photo`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id

        val notJpeg = client.putPhoto(token, id, "kein Foto".encodeToByteArray())
        val tooLong = client.putPhoto(token, id, jpeg(600, 10))

        assertEquals(HttpStatusCode.BadRequest, notJpeg.status)
        assertEquals(ErrorResponse("VALIDATION_ERROR", "Bitte ein Foto im JPEG-Format schicken.", "photo"), notJpeg.body<ErrorResponse>())
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
        assertEquals(ErrorResponse("VALIDATION_ERROR", "Das Foto darf höchstens 512 Pixel lang sein.", "photo"), tooLong.body<ErrorResponse>())
        assertNull(client.details(token, id).photoVersion)
    }

    @Test
    fun `Löschen der Aktivität oder des Kontos löscht das Foto`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val first = client.createActivity(token).activity.id
        val second = client.createActivity(token).activity.id
        val uploads = listOf(first, second).map { client.putPhoto(token, it).status }

        client.delete("/api/activities/$first") { bearerAuth(token) }
        val afterActivity = storedPhotos()
        client.delete("/api/me") { bearerAuth(token) }

        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK), uploads)
        assertEquals(1L, afterActivity)
        assertEquals(0L, storedPhotos())
    }

    @Test
    fun `eigenes Foto kommt als JPEG mit Cache-Control und genau den hochgeladenen Bytes`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id
        val firstUpload = client.putPhoto(token, id).status
        val replacement = jpeg(3, 4)
        val version = client.putPhoto(token, id, replacement).body<ActivityDto>().photoVersion

        val response = client.get("/api/activities/$id/photo?v=$version") { bearerAuth(token) }

        assertEquals(HttpStatusCode.OK, firstUpload)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("image/jpeg", response.headers[HttpHeaders.ContentType])
        assertEquals("private, max-age=31536000, immutable", response.headers[HttpHeaders.CacheControl])
        assertContentEquals(replacement, response.bodyAsBytes())
    }

    @Test
    fun `fremdes Foto nur bei öffentlicher Aktivität oder als Freund`() = testApplication {
        val client = startApp()
        val max = client.register("max_m").token
        val lisa = client.register("lisa_k").token
        val tom = client.register("tom_b").token
        client.befriend(max, "max_m", lisa, "lisa_k")
        val ids = listOf(Visibility.PRIVATE, Visibility.FRIENDS, Visibility.PUBLIC)
            .map { client.createActivity(max, activity(visibility = it)).activity.id }
        val uploads = ids.map { client.putPhoto(max, it).status }

        val forLisa = ids.map { client.photo(lisa, it).status }
        val forTom = ids.map { client.photo(tom, it).status }

        assertEquals(List(3) { HttpStatusCode.OK }, uploads)
        assertEquals(listOf(HttpStatusCode.NotFound, HttpStatusCode.OK, HttpStatusCode.OK), forLisa)
        assertEquals(listOf(HttpStatusCode.NotFound, HttpStatusCode.NotFound, HttpStatusCode.OK), forTom)
    }

    @Test
    fun `ohne Foto, nach dem Entfernen und ohne Aktivität liefert 404`() = testApplication {
        val client = startApp()
        val token = client.register("max_m").token
        val id = client.createActivity(token).activity.id

        val withoutPhoto = client.photo(token, id)
        val uploaded = client.putPhoto(token, id).status
        val beforeRemoval = client.photo(token, id).status
        val removed = client.deletePhoto(token, id).status
        val afterRemoval = client.photo(token, id)
        val missing = client.photo(token, 999999)
        val notANumber = client.get("/api/activities/abc/photo") { bearerAuth(token) }

        assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK, HttpStatusCode.NoContent), listOf(uploaded, beforeRemoval, removed))
        for (response in listOf(withoutPhoto, afterRemoval, missing, notANumber)) {
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(ErrorResponse("NOT_FOUND", "Foto nicht gefunden."), response.body<ErrorResponse>())
            assertNull(response.headers[HttpHeaders.CacheControl])
        }
    }

    @Test
    fun `Foto abrufen braucht eine Anmeldung`() = testApplication {
        val client = startApp()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/activities/1/photo").status)
    }
}
```
Zu den Tests:
- `Entfernen löscht nur das Foto dieser Aktivität` sichert `ActivityRepository.deletePhoto` ab. Dort ist `ActivityPhotosTable.activityId eq activityId` nur richtig, weil der Parameter `activityId` die gleichnamige Spalte verdeckt; ohne Parameter dieses Namens hieße derselbe Text `activity_id = activity_id` und träfe alle Fotos.
- Der 403-Test lädt zuerst ein Foto für lisa hoch. Dann zeigen gespeicherte Nummer, Zahl der Fotos und die Bytes beim Abrufen, dass ein verbotenes `PUT` oder `DELETE` nichts verändert hat.
- `smallBodyWithLargeLength` kündigt mit `contentLength` 204.801 Byte an, schickt aber nur `jpeg()` (631 Byte). Der Test-Client von Ktor setzt `Content-Length` aus `contentLength`, die Test-Engine reicht den kurzen Body ohne Längenprüfung durch. Mit der Prüfung vor dem Lesen kommt 413, ohne sie würde der Server das kleine JPEG speichern und 200 liefern. Der Typ `OutgoingContent` an der Variablen sorgt dafür, dass `setBody` den Body unverändert schickt.
- Alle Körper bleiben klein (höchstens `MAX_BYTES + 1` Byte) wegen KTOR-9968, wie in M8.

- [ ] **Step 3: Gegenprobe vorbereiten – vier Stellen im Produktionscode vorübergehend kaputt machen**

Die neuen Tests sichern Verhalten ab, das es schon gibt. Damit sie zeigen, dass sie einen Fehler finden, kommen vier Fehler in den Produktionscode, jeder mit dem Edit-Werkzeug. Die Zeilen in den Blöcken stehen mit ihrer echten Einrückung da.

**Fehler 1: Entfernen trifft alle Fotos.** In `backend/src/main/kotlin/de/touchgrass/activities/ActivityRepository.kt` ersetzen:
```kotlin
            ActivityPhotosTable.deleteWhere { ActivityPhotosTable.activityId eq activityId }
```
durch:
```kotlin
            ActivityPhotosTable.deleteWhere { ActivityPhotosTable.activityId eq ActivityPhotosTable.activityId }
```

**Fehler 2: Entfernen vor der Besitzprüfung.** In `backend/src/main/kotlin/de/touchgrass/activities/ActivityService.kt` ersetzen:
```kotlin
    fun deletePhoto(userId: Long, id: Long) {
        ownActivity(userId, id)
        activities.deletePhoto(id)
    }
```
durch:
```kotlin
    fun deletePhoto(userId: Long, id: Long) {
        activities.deletePhoto(id)
        ownActivity(userId, id)
    }
```

**Fehler 3: keine Prüfung vor dem Lesen.** In `backend/src/main/kotlin/de/touchgrass/routes/ActivityRoutes.kt` diese Zeile löschen (der dann ungenutzte Import von `contentLength` ergibt nur eine Warnung):
```kotlin
    if ((request.contentLength() ?: 0L) > PhotoRules.MAX_BYTES) PhotoRules.tooLarge()
```

**Fehler 4: Bild entpacken statt Kopf lesen.** In `backend/src/main/kotlin/de/touchgrass/activities/PhotoRules.kt` ersetzen:
```kotlin
                reader.getWidth(0) to reader.getHeight(0)
```
durch:
```kotlin
                reader.read(0).let { it.width to it.height }
```

- [ ] **Step 4: Gegenprobe ausführen, die neuen Tests müssen scheitern**

Run: `.\gradlew.bat --no-daemon -q test --tests 'de.touchgrass.PhotoRoutesTest' --tests 'de.touchgrass.activities.PhotoRulesTest'`
Expected: BUILD FAILED. `backend\build\test-results\test\TEST-de.touchgrass.PhotoRoutesTest.xml` meldet `tests="16" skipped="0" failures="3" errors="0"`, `TEST-de.touchgrass.activities.PhotoRulesTest.xml` meldet `tests="4" skipped="0" failures="1" errors="0"`. Rot sind genau:
- `Entfernen löscht nur das Foto dieser Aktivität` (`storedPhotos()` ist 0 statt 1),
- `Foto einer fremden Aktivität ändern liefert 403 und lässt ihr Foto stehen` (die 403 kommen, aber `photoVersion` ist danach `null`),
- `angekündigte Länge über 200 KB liefert 413, ohne den Body zu lesen` (200 statt 413),
- `kleines JPEG mit riesigen Pixelangaben wird abgelehnt` („Bitte ein Foto im JPEG-Format schicken.“ statt „Das Foto darf höchstens 512 Pixel lang sein.“).

Alle anderen Tests der beiden Klassen bleiben grün. Scheitert ein anderer Test oder einer der vier mit einer anderen Meldung, anhalten und klären.

- [ ] **Step 5: Produktionscode zurücksetzen**

```bash
git checkout -- backend/src/main/kotlin/de/touchgrass/activities/ActivityRepository.kt backend/src/main/kotlin/de/touchgrass/activities/ActivityService.kt backend/src/main/kotlin/de/touchgrass/activities/PhotoRules.kt backend/src/main/kotlin/de/touchgrass/routes/ActivityRoutes.kt
git status --short
```
Expected: `git status` zeigt nur noch ` M backend/src/test/kotlin/de/touchgrass/PhotoRoutesTest.kt` und ` M backend/src/test/kotlin/de/touchgrass/activities/PhotoRulesTest.kt`.

- [ ] **Step 6: Tests ausführen**

Run: `.\gradlew.bat --no-daemon -q test --tests 'de.touchgrass.PhotoRoutesTest' --tests 'de.touchgrass.activities.PhotoRulesTest'`
Expected: keine Ausgabe. `TEST-de.touchgrass.PhotoRoutesTest.xml` meldet `tests="16" skipped="0" failures="0" errors="0"`, `TEST-de.touchgrass.activities.PhotoRulesTest.xml` meldet `tests="4" skipped="0" failures="0" errors="0"`.

- [ ] **Step 7: Alle Backend-Tests**

Run: `.\gradlew.bat --no-daemon -q test`
Expected: 140 Tests grün (137 bisherige, 1 neuer in `PhotoRulesTest`, 2 neue in `PhotoRoutesTest`), in keiner `TEST-*.xml` stehen `failures` oder `errors` über 0. Die ERROR-Zeile aus `HealthRoutesTest` ist alt und gewollt.

- [ ] **Step 8: Projekttagebuch**

In `docs/projektdoku/projekttagebuch.md` diesen Punkt ans Ende des Abschnitts des Arbeitstags setzen (Regel unter „Ausführungshinweise“):
```markdown
- **Nachbesserung M8, Backend-Tests:** Drei Tests sind neu: Entfernen trifft nur das Foto dieser Aktivität, schon ein angekündigter `Content-Length` über 200 KB ergibt 413, und ein kleines JPEG mit riesigen Pixelangaben wird abgelehnt, ohne dass der Server es entpackt. Der Test zu fremden Aktivitäten arbeitet jetzt mit einem vorhandenen Foto, das unverändert bleibt, und jede Antwort beim Hochladen wird geprüft. Eine Gegenprobe mit vier eingebauten Fehlern machte genau diese Tests rot. Backend 140 Tests.
```

- [ ] **Step 9: Commit**

```bash
git add backend/src/test/kotlin/de/touchgrass/PhotoRoutesTest.kt backend/src/test/kotlin/de/touchgrass/activities/PhotoRulesTest.kt docs/projektdoku/projekttagebuch.md
git commit -m "Backend: Tests für Fotos geschärft" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: App – Robustheit beim Verkleinern, Bildladen und Abmelden

**Files:**
- Modify: `app/src/main/java/de/touchgrass/ui/photo/PhotoSize.kt`, `app/src/main/java/de/touchgrass/ui/photo/PhotoPreparer.kt`, `app/src/main/java/de/touchgrass/ui/photo/AndroidPhotoPreparer.kt`
- Modify: `app/src/main/java/de/touchgrass/data/SportApi.kt`, `app/src/main/java/de/touchgrass/AppContainer.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/activities/ActivityDetailScreen.kt`
- Test: `app/src/test/java/de/touchgrass/ui/photo/PhotoSizeTest.kt`, `app/src/test/java/de/touchgrass/data/SportApiTest.kt`
- Modify: `docs/projektdoku/projekttagebuch.md`

**Interfaces:**
- Consumes: `const val MAX_PHOTO_BYTES = 204_800`, `val JPEG_QUALITIES = listOf(85, 75, 65, 55, 45)`, `fitWithin(width, height, maxSide = 512): PhotoSize`, `sampleSizeFor(width, height, target = 512): Int`, `PHOTO_AUTHORITY`, `UNSUPPORTED_PHOTO` aus `ui/photo`; `fun httpClient(vararg interceptors: Interceptor): OkHttpClient` aus `data/SportApi.kt`; `AuthSession.state`, `AuthState.LoggedOut`; `LoadingIndicator()` aus `ui/components`.
- Produces:
  - `class PhotoException(message: String, cause: Throwable? = null) : Exception(message, cause)`; bestehende Aufrufe `PhotoException(UNSUPPORTED_PHOTO)` bleiben gültig.
  - In `ui/photo/PhotoSize.kt`: `fun jpegWithinLimit(compress: (quality: Int) -> ByteArray?): ByteArray?` probiert die Qualitäten aus `JPEG_QUALITIES` der Reihe nach und liefert das erste JPEG mit höchstens `MAX_PHOTO_BYTES` Byte, `null`, sobald `compress` `null` liefert oder auch Qualität 45 zu groß bleibt.
  - In `data/SportApi.kt`: `fun imageClient(apiClient: OkHttpClient): OkHttpClient`, eine Kopie mit eigenem `okhttp3.Dispatcher`, gleichen Interceptoren, gleichem Verbindungspool und gleichen Timeouts.
  - `AndroidPhotoPreparer(context: Context)` (Signatur unverändert): dekodiert in sRGB, gibt immer `ARGB_8888` in sRGB ohne Transparenz an `compress`, wirft bei jedem Fehler `PhotoException(UNSUPPORTED_PHOTO, e)` und loggt `e` mit `Log.w("TouchGrass", …)`.
  - `AppContainer`: Coil lädt über `imageClient(okHttpClient)`; beim Abmelden wird ein Fehler aus `imageLoader.diskCache?.clear()` geloggt statt die App zu beenden.
  - `ActivityDetailScreen`: Während `photoBusy` steht `CircularProgressIndicator()` statt `LoadingIndicator()`.
  - Tests: `PhotoSizeTest` 6 → 8, `SportApiTest` 19 → 20, App 132 → 135.

- [ ] **Step 1: Tests für die Wahl der JPEG-Qualität**

`app/src/test/java/de/touchgrass/ui/photo/PhotoSizeTest.kt` komplett:
```kotlin
package de.touchgrass.ui.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoSizeTest {
    @Test
    fun `Querformat wird auf 512 Pixel Breite verkleinert`() {
        assertEquals(PhotoSize(512, 384), fitWithin(4000, 3000))
    }

    @Test
    fun `Hochformat wird auf 512 Pixel Höhe verkleinert`() {
        assertEquals(PhotoSize(384, 512), fitWithin(3000, 4000))
    }

    @Test
    fun `kleinere Fotos werden nicht vergrößert`() {
        assertEquals(PhotoSize(300, 200), fitWithin(300, 200))
    }

    @Test
    fun `genau 512 Pixel bleiben unverändert`() {
        assertEquals(PhotoSize(512, 384), fitWithin(512, 384))
    }

    @Test
    fun `sehr schmale Fotos behalten mindestens ein Pixel`() {
        assertEquals(PhotoSize(512, 1), fitWithin(10_000, 5))
    }

    @Test
    fun `Abtastrate ist die größte Zweierpotenz, bei der die längste Seite mindestens 512 bleibt`() {
        assertEquals(4, sampleSizeFor(4000, 3000))
        assertEquals(1, sampleSizeFor(1000, 800))
        assertEquals(2, sampleSizeFor(1024, 768))
    }

    @Test
    fun `die Qualität sinkt, bis das JPEG höchstens 200 KB hat`() {
        val tried = mutableListOf<Int>()

        val jpeg = jpegWithinLimit { quality ->
            tried += quality
            ByteArray(if (quality > 65) MAX_PHOTO_BYTES + 1 else MAX_PHOTO_BYTES)
        }

        assertEquals(listOf(85, 75, 65), tried)
        assertEquals(MAX_PHOTO_BYTES, jpeg?.size)
    }

    @Test
    fun `scheitert das Speichern oder bleibt das JPEG zu groß, gibt es kein Ergebnis`() {
        val tried = mutableListOf<Int>()

        val failed = jpegWithinLimit { null }
        val tooLarge = jpegWithinLimit { quality ->
            tried += quality
            ByteArray(MAX_PHOTO_BYTES + 1)
        }

        assertNull(failed)
        assertNull(tooLarge)
        assertEquals(listOf(85, 75, 65, 55, 45), tried)
    }
}
```

- [ ] **Step 2: Test für die eigene Warteschlange der Bilder**

In `app/src/test/java/de/touchgrass/data/SportApiTest.kt` zwei Änderungen. Die Blöcke stehen mit der echten Einrückung der Datei da.

**Importe.** Ersetzen:
```kotlin
import org.junit.Assert.assertEquals
```
durch:
```kotlin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
```

**Neuer Test nach `` `httpClient übernimmt die Interceptoren und wartet bis zu zwei Minuten auf den Server` ``.** Ersetzen:
```kotlin
        assertEquals(listOf(interceptor), client.interceptors)
        assertEquals(120_000, client.readTimeoutMillis)
    }
```
durch:
```kotlin
        assertEquals(listOf(interceptor), client.interceptors)
        assertEquals(120_000, client.readTimeoutMillis)
    }

    @Test
    fun `Bilder haben eine eigene Warteschlange, aber dasselbe Login und dieselben Verbindungen`() {
        val interceptor = Interceptor { chain -> chain.proceed(chain.request()) }
        val apiClient = httpClient(interceptor)

        val images = imageClient(apiClient)

        assertNotSame(apiClient.dispatcher, images.dispatcher)
        assertSame(apiClient.connectionPool, images.connectionPool)
        assertEquals(listOf(interceptor), images.interceptors)
        assertEquals(120_000, images.readTimeoutMillis)
    }
```

- [ ] **Step 3: Tests ausführen, sie müssen scheitern**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.ui.photo.PhotoSizeTest' --tests 'de.touchgrass.data.SportApiTest'`
Expected: BUILD FAILED, Kompilierfehler `Unresolved reference 'jpegWithinLimit'` in `PhotoSizeTest.kt` und `Unresolved reference 'imageClient'` in `SportApiTest.kt`.

- [ ] **Step 4: Wahl der JPEG-Qualität als reine Funktion**

`app/src/main/java/de/touchgrass/ui/photo/PhotoSize.kt` komplett:
```kotlin
package de.touchgrass.ui.photo

import kotlin.math.roundToInt

const val MAX_PHOTO_BYTES = 204_800

val JPEG_QUALITIES = listOf(85, 75, 65, 55, 45)

data class PhotoSize(val width: Int, val height: Int)

fun fitWithin(width: Int, height: Int, maxSide: Int = 512): PhotoSize {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return PhotoSize(width, height)
    val scale = maxSide.toDouble() / longest
    return PhotoSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
}

fun sampleSizeFor(width: Int, height: Int, target: Int = 512): Int {
    val longest = maxOf(width, height)
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= target) sampleSize *= 2
    return sampleSize
}

fun jpegWithinLimit(compress: (quality: Int) -> ByteArray?): ByteArray? {
    for (quality in JPEG_QUALITIES) {
        val jpeg = compress(quality) ?: return null
        if (jpeg.size <= MAX_PHOTO_BYTES) return jpeg
    }
    return null
}
```

- [ ] **Step 5: Kopie des Clients für Coil**

In `app/src/main/java/de/touchgrass/data/SportApi.kt` zwei Änderungen.

**Import.** Ersetzen:
```kotlin
import okhttp3.Interceptor
```
durch:
```kotlin
import okhttp3.Dispatcher
import okhttp3.Interceptor
```

**Neue Funktion am Dateiende.** Ersetzen:
```kotlin
    .apply { interceptors.forEach { addInterceptor(it) } }
    .build()
```
durch:
```kotlin
    .apply { interceptors.forEach { addInterceptor(it) } }
    .build()

// Bilder bekommen eine eigene Warteschlange, damit viele Vorschaubilder die Aufrufe der API nicht aufhalten. Login und Verbindungen bleiben gemeinsam.
fun imageClient(apiClient: OkHttpClient): OkHttpClient = apiClient.newBuilder().dispatcher(Dispatcher()).build()
```

`newBuilder()` übernimmt Interceptoren (also den `AuthInterceptor` mit Token und Abmelden bei 401), Verbindungspool und Timeouts. Nur der `Dispatcher` ist neu: Seine Grenze von fünf gleichzeitigen Anfragen je Host gilt damit für Bilder und für die API getrennt. Coil 3.3.0 schickt Bildanfragen über `Call.enqueue`, also über genau diesen `Dispatcher`.

- [ ] **Step 6: Tests ausführen**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.ui.photo.PhotoSizeTest' --tests 'de.touchgrass.data.SportApiTest'`
Expected: keine Ausgabe. `app\build\test-results\testDebugUnitTest\TEST-de.touchgrass.ui.photo.PhotoSizeTest.xml` meldet `tests="8" skipped="0" failures="0" errors="0"`, `TEST-de.touchgrass.data.SportApiTest.xml` meldet `tests="20" skipped="0" failures="0" errors="0"`.

- [ ] **Step 7: `PhotoException` mit Ursache**

`app/src/main/java/de/touchgrass/ui/photo/PhotoPreparer.kt` komplett:
```kotlin
package de.touchgrass.ui.photo

import java.io.File

const val UNSUPPORTED_PHOTO = "Dieses Bildformat wird nicht unterstützt."

class PhotoException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun interface PhotoPreparer {
    suspend fun prepare(source: String): File
}
```

- [ ] **Step 8: Verkleinern in sRGB, geprüftes JPEG, Ursache im Log**

`app/src/main/java/de/touchgrass/ui/photo/AndroidPhotoPreparer.kt` komplett:
```kotlin
package de.touchgrass.ui.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.UUID

private val SRGB: ColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)

class AndroidPhotoPreparer(private val context: Context) : PhotoPreparer {
    override suspend fun prepare(source: String): File = withContext(Dispatchers.IO) {
        val uri = source.toUri()
        try {
            val jpeg = decode(uri).withoutGainmap().toSrgbOnWhite().toJpeg()
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            File(dir, "${UUID.randomUUID()}.jpg").apply { writeBytes(jpeg) }
        } catch (e: Exception) {
            Log.w("TouchGrass", "Foto nicht lesbar", e)
            throw PhotoException(UNSUPPORTED_PHOTO, e)
        } finally {
            if (uri.authority == PHOTO_AUTHORITY) context.contentResolver.delete(uri, null, null)
        }
    }

    // ImageDecoder dreht schon nach der EXIF-Angabe, BitmapFactory nicht. Nur der Weg unter Android 9 dreht deshalb selbst.
    private fun decode(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(uri) else decodeWithBitmapFactory(uri)

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val size = fitWithin(info.size.width, info.size.height)
            decoder.setTargetSize(size.width, size.height)
            decoder.setTargetColorSpace(SRGB)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun decodeWithBitmapFactory(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredColorSpace = SRGB
        }
        val sampled = open(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: error("BitmapFactory kann das Bild nicht lesen")
        val exif = open(uri).use { ExifInterface(it) }
        val target = fitWithin(sampled.width, sampled.height)
        val matrix = Matrix().apply {
            postScale(target.width.toFloat() / sampled.width, target.height.toFloat() / sampled.height)
            if (exif.isFlipped) postScale(-1f, 1f)
            postRotate(exif.rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(sampled, 0, 0, sampled.width, sampled.height, matrix, true)
    }

    private fun open(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())

    private fun Bitmap.withoutGainmap(): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasGainmap()) gainmap = null
        return this
    }

    // Bei einem anderen Farbraum schreibt compress dessen Farbprofil ins JPEG, und HDR-Fotos kommen als RGBA_F16 an.
    // Deshalb geht immer ARGB_8888 in sRGB hinaus, Transparenz auf Weiß.
    private fun Bitmap.toSrgbOnWhite(): Bitmap {
        if (config == Bitmap.Config.ARGB_8888 && colorSpace == SRGB && !hasAlpha()) return this
        val opaque = createBitmap(width, height, Bitmap.Config.ARGB_8888, hasAlpha = false, colorSpace = SRGB)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@toSrgbOnWhite, 0f, 0f, null)
        }
        return opaque
    }

    private fun Bitmap.toJpeg(): ByteArray =
        jpegWithinLimit { quality ->
            val out = ByteArrayOutputStream()
            if (compress(Bitmap.CompressFormat.JPEG, quality, out)) out.toByteArray() else null
        } ?: error("JPEG nicht gespeichert oder auch mit Qualität ${JPEG_QUALITIES.last()} größer als $MAX_PHOTO_BYTES Byte")
}
```
Zum Code:
- Beide Wege dekodieren gleich in sRGB: `ImageDecoder` über `setTargetColorSpace` (API 28 wie der ganze Zweig), `BitmapFactory` über `inPreferredColorSpace` (API 26). Ein 16-Bit-Bild dekodiert `ImageDecoder` trotzdem als `RGBA_F16` in erweitertem sRGB; deshalb zeichnet `toSrgbOnWhite` jedes Bild, das nicht schon `ARGB_8888` in sRGB ohne Transparenz ist, auf eine neue weiße `ARGB_8888`-Fläche in sRGB. Beim Zeichnen rechnet Android die Farben um. Die Funktion ersetzt `onWhite` aus M8.
- `toJpeg` nutzt `jpegWithinLimit`. Liefert `compress` `false` oder bleibt das JPEG zu groß, wirft `error(…)` eine `IllegalStateException` mit dem Grund, und `prepare` macht daraus wie bisher „Dieses Bildformat wird nicht unterstützt.“, jetzt mit Ursache. Der Weg unter Android 9 meldet ein unlesbares Bild ebenso mit `error(…)` statt mit einer zweiten `PhotoException`.
- `Log.w("TouchGrass", "Foto nicht lesbar", e)` schreibt Ursache und Stacktrace ins Logcat. Im `try` steht weiter kein Suspend-Aufruf, `catch (e: Exception)` fängt also keinen Abbruch der Coroutine.
- Der Ordner bleibt in dieser Aufgabe `filesDir/photos`, Task 4 verlegt ihn.

- [ ] **Step 9: Container mit eigener Warteschlange für Coil und ohne Absturz beim Abmelden**

`app/src/main/java/de/touchgrass/AppContainer.kt` komplett:
```kotlin
package de.touchgrass

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import de.touchgrass.data.AuthInterceptor
import de.touchgrass.data.AuthSession
import de.touchgrass.data.AuthState
import de.touchgrass.data.DataStoreTimerStore
import de.touchgrass.data.DataStoreTokenStore
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerStore
import de.touchgrass.data.authDataStore
import de.touchgrass.data.createSportApi
import de.touchgrass.data.httpClient
import de.touchgrass.data.imageClient
import de.touchgrass.data.timerDataStore
import de.touchgrass.ui.photo.AndroidPhotoPreparer
import de.touchgrass.ui.photo.PhotoPreparer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppContainer(context: Context, baseUrl: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val session = AuthSession(DataStoreTokenStore(context.authDataStore), scope)

    val timerStore: TimerStore = DataStoreTimerStore(context.timerDataStore)

    val photoPreparer: PhotoPreparer = AndroidPhotoPreparer(context)

    private val okHttpClient = httpClient(AuthInterceptor(session))

    val api: SportApi = createSportApi(baseUrl, okHttpClient)

    private val imageHttpClient = imageClient(okHttpClient)

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient })) }
        .build()

    init {
        // Coil zeigt Bilder aus dem Zwischenspeicher ohne Anfrage an den Server, also ohne Login. Deshalb leert jedes Abmelden ihn.
        // Das läuft auch bei jedem Start ohne Anmeldung, ein Fehler dabei darf die App also nicht beenden.
        scope.launch {
            session.state.filter { it == AuthState.LoggedOut }.collect {
                imageLoader.memoryCache?.clear()
                withContext(Dispatchers.IO) {
                    try {
                        imageLoader.diskCache?.clear()
                    } catch (e: Exception) {
                        Log.w("TouchGrass", "Bildzwischenspeicher nicht geleert", e)
                    }
                }
            }
        }
    }
}
```
Zum Code: `diskCache.clear()` ruft in Coil 3.3.0 `DiskLruCache.evictAll()` auf, das erst `initialize()` ausführt; dort können `writeJournal()` und das Löschen einer kaputten Journaldatei eine `IOException` werfen, etwa bei vollem Speicher. Ohne `catch` ginge sie an den Scope ohne `CoroutineExceptionHandler` und beendete die App, bei jedem Start ohne Anmeldung von Neuem. `catch (e: Exception)` fängt auch eine `IllegalStateException` eines geschlossenen Caches; `clear()` ist keine Suspend-Funktion, ein Abbruch wird also nicht verschluckt. Der Collector läuft nach einem Fehler weiter. `imageHttpClient` entsteht einmal beim Start des Containers.

- [ ] **Step 10: Ladekreis ohne Aufwach-Hinweis in den Details**

In `app/src/main/java/de/touchgrass/ui/activities/ActivityDetailScreen.kt` zwei Änderungen.

**Import.** Ersetzen:
```kotlin
import androidx.compose.material3.ButtonDefaults
```
durch:
```kotlin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
```

**Ladekreis beim Fotowechsel.** Ersetzen:
```kotlin
                    if (state.photoBusy) {
                        LoadingIndicator()
                    } else {
```
durch:
```kotlin
                    if (state.photoBusy) {
                        CircularProgressIndicator()
                    } else {
```

`LoadingIndicator` bleibt für den Ladezustand der Aktivität (`UiState.Loading`) und zeigt dort weiter nach fünf Sekunden „Der Server wacht gerade auf, das kann bis zu einer Minute dauern.“; beim Fotowechsel ist der Server schon wach, lange dauern hier Verkleinern und Hochladen.

- [ ] **Step 11: Build und Lint**

Run: `.\gradlew.bat --no-daemon -q assembleDebug :app:lintDebug`
Expected: keine Ausgabe, Build erfolgreich, Lint ohne Fehler (`app\build\reports\lint-results-debug.html` meldet keine Fehler). Kommt `NewApi`, steht eine API außerhalb ihres Zweigs; siehe die Tabelle unter „Entscheidungen“.

- [ ] **Step 12: Alle App-Tests**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest`
Expected: 135 Tests grün (132 bisherige, 2 neue in `PhotoSizeTest`, 1 neuer in `SportApiTest`), in keiner `TEST-*.xml` stehen `failures` oder `errors` über 0.

- [ ] **Step 13: Projekttagebuch**

In `docs/projektdoku/projekttagebuch.md` diesen Punkt ans Ende des Abschnitts des Arbeitstags setzen:
```markdown
- **Nachbesserung M8, App – Robustheit:** Scheitert beim Abmelden das Leeren des Bildzwischenspeichers, schreibt die App den Fehler ins Log, statt abzustürzen; das Abmelden läuft auch bei jedem Start ohne Anmeldung. Fotos werden beim Verkleinern nach sRGB umgerechnet und immer mit 8 Bit je Farbkanal als JPEG gespeichert. Scheitert das Speichern oder bleibt das JPEG über 200 KB, meldet die App „Dieses Bildformat wird nicht unterstützt.“ und loggt die Ursache. Bilder laden über eine eigene Warteschlange, Login und Verbindungen teilen sie weiter mit der API. Beim Fotowechsel in den Details erscheint kein Hinweis mehr, dass der Server aufwacht. App 135 Tests.
```

- [ ] **Step 14: Commit**

```bash
git add app/src/main/java/de/touchgrass/ui/photo/PhotoSize.kt app/src/main/java/de/touchgrass/ui/photo/PhotoPreparer.kt app/src/main/java/de/touchgrass/ui/photo/AndroidPhotoPreparer.kt app/src/main/java/de/touchgrass/data/SportApi.kt app/src/main/java/de/touchgrass/AppContainer.kt app/src/main/java/de/touchgrass/ui/activities/ActivityDetailScreen.kt app/src/test/java/de/touchgrass/ui/photo/PhotoSizeTest.kt app/src/test/java/de/touchgrass/data/SportApiTest.kt docs/projektdoku/projekttagebuch.md
git commit -m "App: Fotos in sRGB verkleinern, eigene Warteschlange für Bilder, kein Absturz beim Abmelden" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: App – fehlende Tests zu Fotos in Formular und Details

**Files:**
- Modify: `app/src/test/java/de/touchgrass/testing/Fakes.kt`
- Test: `app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt`, `app/src/test/java/de/touchgrass/ui/activities/ActivityDetailViewModelTest.kt`
- Modify: `docs/projektdoku/projekttagebuch.md`
- Nur für die Gegenprobe, danach unverändert: `app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt`

**Interfaces:**
- Consumes: `RecordViewModel(api: SportApi, timerStore: TimerStore, fromTimer: Boolean, photos: PhotoPreparer, savedState: SavedStateHandle, clock: Clock = Clock.systemDefaultZone())` mit `onPhotoPicked(source: String)`, `onPhotoError(message: String)`, `save()` und `state.value.photoPath`, `photoBusy`, `error`, `fieldErrors`; `ActivityDetailViewModel(api: SportApi, id: Long, photos: PhotoPreparer)` mit `onPhotoPicked`, `onPhotoError`, `removePhoto()` und `state.value.activity`, `photoBusy`, `error`; aus `testing/Fakes.kt` `FakeSportApi`, `httpError(code, json)`, `testActivity(id = …)`, `MainDispatcherRule`.
- Produces: `class FakePhotoPreparer(private val block: suspend (String) -> File) : PhotoPreparer` in `testing/Fakes.kt`; bestehende Aufrufe wie `FakePhotoPreparer { photo }` bleiben gültig. Neue Tests: `` `scheitert schon das Anlegen, bleibt das Foto im Formular` ``, `` `während das Foto vorbereitet wird, schickt Speichern nichts` ``, `` `fehlende Kamera-App erscheint am Foto` `` (`RecordViewModelTest` 20 → 23); `` `scheitert das Entfernen, bleibt das Foto` ``, `` `fehlende Kamera-App erscheint als Fehler` `` (`ActivityDetailViewModelTest` 8 → 10). `` `Fehler beim Hochladen landen in error` `` heißt jetzt `` `Fehler beim Hochladen landen in error, das bisherige Foto bleibt` `` und startet mit `photoVersion = 5`, `` `nicht lesbares Bild meldet das Bildformat` `` prüft zusätzlich `photoBusy`. App 135 → 140.

- [ ] **Step 1: Tests im Formular**

In `app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt` am Ende der Klasse drei Tests anfügen. Ersetzen (die letzten Zeilen der Datei):
```kotlin
        assertNull(handle.get<String>("sportCode"))
    }
}
```
durch:
```kotlin
        assertNull(handle.get<String>("sportCode"))
    }

    @Test
    fun `scheitert schon das Anlegen, bleibt das Foto im Formular`() {
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse =
                throw httpError(500, """{"error":"INTERNAL_ERROR","message":"Unerwarteter Fehler."}""")
        }
        val photo = photoFile()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, FakePhotoPreparer { photo }, SavedStateHandle(), clock)

        viewModel.onPhotoPicked("content://media/picker/0/42")
        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        val state = viewModel.state.value
        assertEquals("Unerwarteter Fehler.", state.error)
        assertEquals(photo.path, state.photoPath)
        assertTrue(photo.exists())
        assertEquals(emptyList<Pair<Long, String>>(), api.uploads)
    }

    @Test
    fun `während das Foto vorbereitet wird, schickt Speichern nichts`() {
        val api = Api()
        val photo = photoFile()
        val prepared = CompletableDeferred<File>()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, FakePhotoPreparer { prepared.await() }, SavedStateHandle(), clock)
        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")

        viewModel.onPhotoPicked("content://media/picker/0/42")
        val busy = viewModel.state.value.photoBusy
        viewModel.save()
        val sentWhileBusy = api.sent
        prepared.complete(photo)

        val state = viewModel.state.value
        assertTrue(busy)
        assertNull(sentWhileBusy)
        assertEquals(photo.path, state.photoPath)
        assertFalse(state.photoBusy)
    }

    @Test
    fun `fehlende Kamera-App erscheint am Foto`() {
        val viewModel = RecordViewModel(Api(), FakeTimerStore(), fromTimer = false, noPhoto, SavedStateHandle(), clock)

        viewModel.onPhotoError("Keine Kamera-App gefunden.")

        assertEquals("Keine Kamera-App gefunden.", viewModel.state.value.fieldErrors["photo"])
    }
}
```
Alle genutzten Importe (`CompletableDeferred`, `File`, `httpError`, `CreateActivityRequest`, `CreateActivityResponse`) stehen schon in der Datei. Der erste Test sichert die Entscheidung aus dem M8-Plan ab: Scheitert schon das Anlegen, bleibt das Foto im Formular. Der zweite prüft die Sperre `if (current.saving || current.photoBusy) return` in `save()`; dafür muss der Fake warten können, deshalb bekommt `FakePhotoPreparer` in Step 4 eine Suspend-Funktion.

- [ ] **Step 2: Tests in den Details**

`app/src/test/java/de/touchgrass/ui/activities/ActivityDetailViewModelTest.kt` komplett:
```kotlin
package de.touchgrass.ui.activities

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.testing.FakePhotoPreparer
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.UiState
import de.touchgrass.ui.photo.PhotoException
import de.touchgrass.ui.photo.UNSUPPORTED_PHOTO
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ActivityDetailViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val noPhoto = FakePhotoPreparer { error("prepare() wird in diesem Test nicht erwartet") }

    private open inner class Api(private val photoVersion: Long? = null) : FakeSportApi() {
        var deletedId: Long? = null
        val uploads = mutableListOf<Pair<Long, String>>()
        val deletedPhotos = mutableListOf<Long>()
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun activity(id: Long): ActivityDto = testActivity(id = id).copy(photoVersion = photoVersion)
        override suspend fun deleteActivity(id: Long) {
            deletedId = id
        }
        override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto {
            uploads += id to Buffer().also { photo.writeTo(it) }.readUtf8()
            return testActivity(id = id).copy(photoVersion = 8)
        }
        override suspend fun deletePhoto(id: Long) {
            deletedPhotos += id
        }
    }

    private fun photoFile(): File = folder.newFile().apply { writeText("jpeg") }

    @Test
    fun `lädt die Aktivität mit der übergebenen ID`() {
        val viewModel = ActivityDetailViewModel(Api(), id = 7, noPhoto)

        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
    }

    @Test
    fun `Löschen ruft die API auf und meldet deleted`() {
        val api = Api()
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto)

        viewModel.delete()

        assertEquals(7L, api.deletedId)
        assertTrue(viewModel.state.value.deleted)
    }

    @Test
    fun `Fehler beim Löschen wird angezeigt`() {
        val api = object : Api() {
            override suspend fun deleteActivity(id: Long): Unit =
                throw httpError(403, """{"error":"FORBIDDEN","message":"Das ist nicht deine Aktivität."}""")
        }
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto)

        viewModel.delete()

        assertFalse(viewModel.state.value.deleted)
        assertEquals("Das ist nicht deine Aktivität.", viewModel.state.value.error)
    }

    @Test
    fun `nach einem Ladefehler lädt Erneut versuchen die Aktivität`() {
        var calls = 0
        val failingOnce = object : Api() {
            override suspend fun activity(id: Long): ActivityDto = if (calls++ == 0) throw IOException("offline") else testActivity(id = id)
        }
        val viewModel = ActivityDetailViewModel(failingOnce, id = 7, noPhoto)
        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value.activity)

        viewModel.load()

        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
    }

    @Test
    fun `neues Foto wird hochgeladen und die Antwort ersetzt die Aktivität`() {
        val api = Api(photoVersion = 5)
        val photo = photoFile()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { photo })

        viewModel.onPhotoPicked("content://media/picker/0/42")

        val state = viewModel.state.value
        assertEquals(listOf(7L to "jpeg"), api.uploads)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 8)), state.activity)
        assertFalse(state.photoBusy)
        assertFalse(photo.exists())
    }

    @Test
    fun `Foto entfernen ruft deletePhoto auf und setzt photoVersion auf null`() {
        val api = Api(photoVersion = 5)
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto)

        viewModel.removePhoto()

        assertEquals(listOf(7L), api.deletedPhotos)
        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
        assertFalse(viewModel.state.value.photoBusy)
    }

    @Test
    fun `Fehler beim Hochladen landen in error, das bisherige Foto bleibt`() {
        val api = object : Api(photoVersion = 5) {
            override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto =
                throw httpError(413, """{"error":"PAYLOAD_TOO_LARGE","message":"Das Foto ist zu groß.","field":"photo"}""")
        }
        val photo = photoFile()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { photo })

        viewModel.onPhotoPicked("content://media/picker/0/42")

        val state = viewModel.state.value
        assertEquals("Das Foto ist zu groß.", state.error)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 5)), state.activity)
        assertFalse(state.photoBusy)
        assertFalse(photo.exists())
    }

    @Test
    fun `nicht lesbares Bild meldet das Bildformat`() {
        val api = Api()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { throw PhotoException(UNSUPPORTED_PHOTO) })

        viewModel.onPhotoPicked("content://media/picker/0/42")

        assertEquals("Dieses Bildformat wird nicht unterstützt.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.photoBusy)
        assertEquals(emptyList<Pair<Long, String>>(), api.uploads)
    }

    @Test
    fun `scheitert das Entfernen, bleibt das Foto`() {
        val api = object : Api(photoVersion = 5) {
            override suspend fun deletePhoto(id: Long): Unit =
                throw httpError(500, """{"error":"INTERNAL_ERROR","message":"Unerwarteter Fehler."}""")
        }
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto)

        viewModel.removePhoto()

        val state = viewModel.state.value
        assertEquals("Unerwarteter Fehler.", state.error)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 5)), state.activity)
        assertFalse(state.photoBusy)
    }

    @Test
    fun `fehlende Kamera-App erscheint als Fehler`() {
        val viewModel = ActivityDetailViewModel(Api(), id = 7, noPhoto)

        viewModel.onPhotoError("Keine Kamera-App gefunden.")

        assertEquals("Keine Kamera-App gefunden.", viewModel.state.value.error)
    }
}
```
Gegenüber dem Stand nach M8: `` `Fehler beim Hochladen landen in error, das bisherige Foto bleibt` `` startet mit einem Foto (`photoVersion = 5`) und erwartet es nach dem gescheiterten Hochladen unverändert, `` `nicht lesbares Bild meldet das Bildformat` `` prüft `photoBusy`, und die letzten zwei Tests sind neu.

- [ ] **Step 3: Tests ausführen, sie müssen scheitern**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.ui.record.RecordViewModelTest' --tests 'de.touchgrass.ui.activities.ActivityDetailViewModelTest'`
Expected: BUILD FAILED, Kompilierfehler in `RecordViewModelTest.kt` bei `prepared.await()`: Eine Suspend-Funktion darf nur aus einer Coroutine oder einer anderen Suspend-Funktion aufgerufen werden (Kotlin 2.2 meldet sinngemäß „Suspend function … should be called only from a coroutine or another suspend function“). `FakePhotoPreparer` nimmt noch eine normale Funktion `(String) -> File`.

- [ ] **Step 4: Fake mit Suspend-Funktion**

In `app/src/test/java/de/touchgrass/testing/Fakes.kt` ersetzen:
```kotlin
class FakePhotoPreparer(private val prepare: (String) -> File) : PhotoPreparer {
    override suspend fun prepare(source: String): File = prepare.invoke(source)
}
```
durch:
```kotlin
class FakePhotoPreparer(private val block: suspend (String) -> File) : PhotoPreparer {
    override suspend fun prepare(source: String): File = block(source)
}
```
Mit dem eigenen Namen `block` braucht es kein `prepare.invoke` mehr; vorher hätte `prepare(source)` die Methode selbst aufgerufen.

- [ ] **Step 5: Tests ausführen**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.ui.record.RecordViewModelTest' --tests 'de.touchgrass.ui.activities.ActivityDetailViewModelTest'`
Expected: keine Ausgabe. `TEST-de.touchgrass.ui.record.RecordViewModelTest.xml` meldet `tests="23" skipped="0" failures="0" errors="0"`, `TEST-de.touchgrass.ui.activities.ActivityDetailViewModelTest.xml` meldet `tests="10" skipped="0" failures="0" errors="0"`. Die neuen Tests sichern bestehendes Verhalten ab; rot war in Step 3 nur der Kompilierfehler. Deshalb zeigt die Gegenprobe in Step 6, dass der wichtigste von ihnen einen Fehler auch findet.

- [ ] **Step 6: Gegenprobe für die Sperre beim Speichern**

In `app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt` vorübergehend ersetzen:
```kotlin
        if (current.saving || current.photoBusy) return
```
durch:
```kotlin
        if (current.saving) return
```
Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.ui.record.RecordViewModelTest'`
Expected: BUILD FAILED, `TEST-de.touchgrass.ui.record.RecordViewModelTest.xml` meldet `tests="23" skipped="0" failures="1" errors="0"`. Rot ist nur `während das Foto vorbereitet wird, schickt Speichern nichts`: `sentWhileBusy` ist nicht `null`, weil `createActivity` schon lief.

- [ ] **Step 7: Produktionscode zurücksetzen**

```bash
git checkout -- app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt
git status --short
```
Expected: `git status` zeigt nur ` M app/src/test/java/de/touchgrass/testing/Fakes.kt`, ` M app/src/test/java/de/touchgrass/ui/activities/ActivityDetailViewModelTest.kt` und ` M app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt`.

- [ ] **Step 8: Alle App-Tests**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest`
Expected: 140 Tests grün (135 nach Task 2, dazu 3 in `RecordViewModelTest` und 2 in `ActivityDetailViewModelTest`), in keiner `TEST-*.xml` stehen `failures` oder `errors` über 0.

- [ ] **Step 9: Projekttagebuch**

In `docs/projektdoku/projekttagebuch.md` diesen Punkt ans Ende des Abschnitts des Arbeitstags setzen:
```markdown
- **Nachbesserung M8, App – fehlende Tests:** Fünf ViewModel-Tests sind neu: Scheitert schon das Anlegen der Aktivität, bleibt das Foto im Formular; solange ein Foto vorbereitet wird, schickt „Speichern“ nichts; „Keine Kamera-App gefunden.“ erscheint im Formular und in den Details; scheitert das Entfernen, bleibt das Foto. Dazu prüfen die Details, dass ein gescheitertes Hochladen ein vorhandenes Foto stehen lässt und dass nach einem nicht lesbaren Bild kein Ladekreis stehen bleibt. Der Test-Fake für das Vorbereiten von Fotos kann jetzt warten. App 140 Tests.
```

- [ ] **Step 10: Commit**

```bash
git add app/src/test/java/de/touchgrass/testing/Fakes.kt app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt app/src/test/java/de/touchgrass/ui/activities/ActivityDetailViewModelTest.kt docs/projektdoku/projekttagebuch.md
git commit -m "App: fehlende Tests zu Fotos in Formular und Details" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: App – Fotoreste aufräumen

**Files:**
- Modify: `app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt`
- Test: `app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/photo/PhotoFileProvider.kt`, `app/src/main/java/de/touchgrass/ui/photo/PhotoPickers.kt`, `app/src/main/java/de/touchgrass/ui/photo/AndroidPhotoPreparer.kt`
- Modify: `app/src/main/java/de/touchgrass/AppContainer.kt`
- Modify: `docs/projektdoku/projekttagebuch.md`

**Interfaces:**
- Consumes: `RecordViewModel.discard()`, `state.value.photoPath`, `state.value.discarded`; `AndroidPhotoPreparer(context: Context)` und `AppContainer` im Stand nach Task 2 (`imageClient`, `try`/`catch` beim Leeren); `FakePhotoPreparer { … }` aus Task 3.
- Produces:
  - `fun cameraDir(context: Context): File` in `ui/photo/PhotoFileProvider.kt`, liefert `File(context.cacheDir, "camera")`; `PhotoPickers` und `AppContainer` nutzen nur noch diese Funktion.
  - `class AndroidPhotoPreparer(private val context: Context, private val dir: File) : PhotoPreparer`, schreibt neue Fotos als `<UUID>.jpg` in `dir`.
  - `AppContainer`: `private val photoDir = File(context.noBackupFilesDir, "photos")`, `photoPreparer = AndroidPhotoPreparer(context, photoDir)`; jeder Wechsel zu `AuthState.LoggedOut` löscht nach dem Leeren der Coil-Caches `photoDir` und `cameraDir(context)` samt Inhalt.
  - `RecordViewModel.discard()` löscht ein gewähltes Foto sofort und setzt `photoPath` auf `null`.
  - Test: `` `Verwerfen löscht das gewählte Foto` `` (`RecordViewModelTest` 23 → 24), App 140 → 141.

- [ ] **Step 1: Test für „Verwerfen“**

In `app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt` nach dem Test `` `Verwerfen setzt den Timer zurück` `` einen Test einfügen. Ersetzen:
```kotlin
        assertNull(store.current.value)
        assertTrue(viewModel.state.value.discarded)
    }
```
durch:
```kotlin
        assertNull(store.current.value)
        assertTrue(viewModel.state.value.discarded)
    }

    @Test
    fun `Verwerfen löscht das gewählte Foto`() {
        val photo = photoFile()
        val viewModel = RecordViewModel(Api(), FakeTimerStore(pausedTimer), fromTimer = true, FakePhotoPreparer { photo }, SavedStateHandle(), clock)
        viewModel.onPhotoPicked("content://media/picker/0/42")

        viewModel.discard()

        val state = viewModel.state.value
        assertTrue(state.discarded)
        assertNull(state.photoPath)
        assertFalse(photo.exists())
    }
```

- [ ] **Step 2: Test ausführen, er muss scheitern**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.ui.record.RecordViewModelTest'`
Expected: BUILD FAILED, `TEST-de.touchgrass.ui.record.RecordViewModelTest.xml` meldet `tests="24" skipped="0" failures="1" errors="0"`. Rot ist `Verwerfen löscht das gewählte Foto`: `photoPath` ist noch gesetzt.

- [ ] **Step 3: „Verwerfen“ löscht das Foto**

In `app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt` ersetzen:
```kotlin
    fun discard() {
        viewModelScope.launch {
            timerStore.clear()
            _state.update { it.copy(discarded = true) }
        }
    }
```
durch:
```kotlin
    fun discard() {
        _state.value.photoPath?.let { File(it).delete() }
        viewModelScope.launch {
            timerStore.clear()
            _state.update { it.copy(photoPath = null, discarded = true) }
        }
    }
```
Das Löschen steht vor dem Zurücksetzen des Timers, so ist das Foto auch weg, wenn der Screen danach sofort schließt. `photoPath = null` landet über `keepForm` auch im `SavedStateHandle`.

- [ ] **Step 4: Test ausführen**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.ui.record.RecordViewModelTest'`
Expected: keine Ausgabe, `TEST-de.touchgrass.ui.record.RecordViewModelTest.xml` meldet `tests="24" skipped="0" failures="0" errors="0"`.

- [ ] **Step 5: Eine Stelle für den Kamera-Ordner**

`app/src/main/java/de/touchgrass/ui/photo/PhotoFileProvider.kt` komplett:
```kotlin
package de.touchgrass.ui.photo

import android.content.Context
import androidx.core.content.FileProvider
import de.touchgrass.BuildConfig
import java.io.File

const val PHOTO_AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"

// Muss zu <cache-path name="camera" path="camera/" /> in res/xml/file_paths.xml passen.
fun cameraDir(context: Context): File = File(context.cacheDir, "camera")

class PhotoFileProvider : FileProvider()
```

In `app/src/main/java/de/touchgrass/ui/photo/PhotoPickers.kt` ersetzen:
```kotlin
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
```
durch:
```kotlin
            val dir = cameraDir(context).apply { mkdirs() }
```
Der Import `java.io.File` bleibt, die nächste Zeile braucht ihn für den Dateinamen.

- [ ] **Step 6: Ordner für neue Fotos von außen**

In `app/src/main/java/de/touchgrass/ui/photo/AndroidPhotoPreparer.kt` (Stand nach Task 2) zwei Änderungen.

**Konstruktor.** Ersetzen:
```kotlin
class AndroidPhotoPreparer(private val context: Context) : PhotoPreparer {
```
durch:
```kotlin
class AndroidPhotoPreparer(private val context: Context, private val dir: File) : PhotoPreparer {
```

**Schreiben in diesen Ordner.** Ersetzen:
```kotlin
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            File(dir, "${UUID.randomUUID()}.jpg").apply { writeBytes(jpeg) }
```
durch:
```kotlin
            dir.mkdirs()
            File(dir, "${UUID.randomUUID()}.jpg").apply { writeBytes(jpeg) }
```

- [ ] **Step 7: Fotos außerhalb der Sicherung, Aufräumen beim Abmelden**

`app/src/main/java/de/touchgrass/AppContainer.kt` komplett:
```kotlin
package de.touchgrass

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import de.touchgrass.data.AuthInterceptor
import de.touchgrass.data.AuthSession
import de.touchgrass.data.AuthState
import de.touchgrass.data.DataStoreTimerStore
import de.touchgrass.data.DataStoreTokenStore
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerStore
import de.touchgrass.data.authDataStore
import de.touchgrass.data.createSportApi
import de.touchgrass.data.httpClient
import de.touchgrass.data.imageClient
import de.touchgrass.data.timerDataStore
import de.touchgrass.ui.photo.AndroidPhotoPreparer
import de.touchgrass.ui.photo.PhotoPreparer
import de.touchgrass.ui.photo.cameraDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppContainer(context: Context, baseUrl: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Nicht in filesDir: Fotos auf dem Handy sollen nicht in die Datensicherung von Android.
    private val photoDir = File(context.noBackupFilesDir, "photos")

    val session = AuthSession(DataStoreTokenStore(context.authDataStore), scope)

    val timerStore: TimerStore = DataStoreTimerStore(context.timerDataStore)

    val photoPreparer: PhotoPreparer = AndroidPhotoPreparer(context, photoDir)

    private val okHttpClient = httpClient(AuthInterceptor(session))

    val api: SportApi = createSportApi(baseUrl, okHttpClient)

    private val imageHttpClient = imageClient(okHttpClient)

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient })) }
        .build()

    init {
        // Coil zeigt Bilder aus dem Zwischenspeicher ohne Anfrage an den Server, also ohne Login. Deshalb leert jedes Abmelden ihn
        // und löscht die Fotos auf dem Handy; abgemeldet gibt es kein Formular und keine Details, die sie noch brauchen.
        // Das läuft auch bei jedem Start ohne Anmeldung, ein Fehler dabei darf die App also nicht beenden.
        scope.launch {
            session.state.filter { it == AuthState.LoggedOut }.collect {
                imageLoader.memoryCache?.clear()
                withContext(Dispatchers.IO) {
                    try {
                        imageLoader.diskCache?.clear()
                    } catch (e: Exception) {
                        Log.w("TouchGrass", "Bildzwischenspeicher nicht geleert", e)
                    }
                    photoDir.deleteRecursively()
                    cameraDir(context).deleteRecursively()
                }
            }
        }
    }
}
```
Zum Code:
- Android nimmt `noBackupFilesDir` von der automatischen Sicherung aus, eigene Regeln in `backup_rules.xml` oder `data_extraction_rules.xml` braucht es nicht. Der Cache von Coil und `cacheDir/camera` liegen ohnehin im Cache, der nie gesichert wird.
- Das Aufräumen ist sicher: Bei `LoggedOut` zeigt `TouchGrassRoot` nur `AuthNavigation`, Formular und Details samt ihrer ViewModels sind weg. Ein Kaltstart mit Token geht von `Loading` direkt zu `LoggedIn` und löscht nichts, ein nach dem Beenden der App gesichertes Formular behält sein Foto. Bei jedem Kaltstart ohne Token läuft das Aufräumen auch; dann ist der Ordner meist schon leer.
- `deleteRecursively()` wirft nicht, es liefert nur `false`, wenn etwas nicht gelöscht werden konnte. Der `try`-Block bleibt deshalb beim Coil-Cache.
- `context` ist der Konstruktorparameter, also der Kontext der App (`TouchGrassApp`). Der Lambda-Ausdruck im `init` hält ihn so lange wie der Container selbst, ein Leck entsteht dadurch nicht.
- Den alten Ordner `filesDir/photos` aus M8 räumt die App nicht auf. M8 ist noch nicht ausgeliefert; auf Entwicklerhandys hilft einmal „App-Daten löschen“.

- [ ] **Step 8: Build und Lint**

Run: `.\gradlew.bat --no-daemon -q assembleDebug :app:lintDebug`
Expected: keine Ausgabe, Build erfolgreich, Lint ohne Fehler.

- [ ] **Step 9: Alle App-Tests**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest`
Expected: 141 Tests grün (140 nach Task 3, dazu 1 in `RecordViewModelTest`), in keiner `TEST-*.xml` stehen `failures` oder `errors` über 0.

- [ ] **Step 10: Projekttagebuch**

In `docs/projektdoku/projekttagebuch.md` diesen Punkt ans Ende des Abschnitts des Arbeitstags setzen:
```markdown
- **Nachbesserung M8, App – Fotoreste:** Die Fotos der App liegen jetzt in einem Ordner, der nicht in die Datensicherung von Android geht. Jedes Abmelden, auch durch „Konto löschen“ oder ein 401, löscht diese Fotos und übrig gebliebene Kamera-Aufnahmen. „Verwerfen“ im Formular löscht ein gewähltes Foto sofort. App 141 Tests.
```

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt app/src/main/java/de/touchgrass/ui/photo/PhotoFileProvider.kt app/src/main/java/de/touchgrass/ui/photo/PhotoPickers.kt app/src/main/java/de/touchgrass/ui/photo/AndroidPhotoPreparer.kt app/src/main/java/de/touchgrass/AppContainer.kt docs/projektdoku/projekttagebuch.md
git commit -m "App: Fotoreste beim Abmelden und Verwerfen aufräumen, Fotos nicht in der Datensicherung" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: App – Foto bis zum Hochladen behalten, „Foto erneut hochladen“

**Files:**
- Create: `app/src/main/java/de/touchgrass/data/PendingPhotos.kt`
- Test: `app/src/test/java/de/touchgrass/data/PendingPhotosTest.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/activities/ActivityDetailViewModel.kt`, `app/src/main/java/de/touchgrass/ui/activities/ActivityDetailScreen.kt`
- Modify: `app/src/main/java/de/touchgrass/ui/Navigation.kt`, `app/src/main/java/de/touchgrass/AppContainer.kt`
- Test: `app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt`, `app/src/test/java/de/touchgrass/ui/activities/ActivityDetailViewModelTest.kt`
- Modify: `docs/projektdoku/projekttagebuch.md`

**Interfaces:**
- Consumes: `suspend fun <T> apiCall(block: suspend () -> T): T` und `class ApiException(val code: String, override val message: String, val field: String? = null)` aus `data/ApiCall.kt`; `suspend fun SportApi.uploadPhotoFile(id: Long, file: File): ActivityDto` aus `data/Photos.kt`; `FakeSportApi`, `httpError(code, json)`, `testActivity(id = …)`, `FakePhotoPreparer(block: suspend (String) -> File)` aus den Tests; `AppContainer` mit `photoDir` aus Task 4; `ActivityDetailScreen` mit `CircularProgressIndicator()` aus Task 2; `RecordViewModel.discard()` aus Task 4.
- Produces:
  - `class PendingPhotos(private val dir: File)` in `data/PendingPhotos.kt` mit `fun file(activityId: Long): File` (`dir/<activityId>.jpg`), `fun has(activityId: Long): Boolean` (`false`, solange ein Hochladen dieser Aktivität läuft), `fun remove(activityId: Long)` und `suspend fun upload(api: SportApi, activityId: Long, photo: File): ActivityDto`. `upload` verschiebt `photo` vor dem Hochladen nach `file(activityId)` (ist `photo` schon diese Datei, bleibt sie, wo sie ist), löscht die Datei nach Erfolg und nach jeder `ApiException`, deren `code` nicht `NETWORK`, `INTERNAL_ERROR` oder `HTTP_5…` ist, und wirft die `ApiException` weiter.
  - `AppContainer.pendingPhotos: PendingPhotos` mit `File(photoDir, "pending")`.
  - `RecordViewModel(api: SportApi, timerStore: TimerStore, fromTimer: Boolean, photos: PhotoPreparer, pending: PendingPhotos, savedState: SavedStateHandle, clock: Clock = Clock.systemDefaultZone())`; Hochladen nach dem Anlegen über `pending.upload`.
  - `ActivityDetailViewModel(api: SportApi, id: Long, photos: PhotoPreparer, pending: PendingPhotos)`; `ActivityDetailUiState.photoPending: Boolean = false`; neu `fun retryPhotoUpload()`; `delete()` entfernt nach Erfolg auch das wartende Foto.
  - `ActivityDetailScreen`: Knopf „Foto erneut hochladen“ über „Foto ändern“ oder „Foto hinzufügen“, wenn `photoPending` gilt.
  - Tests: `PendingPhotosTest` neu mit 5 Tests, `RecordViewModelTest` 24 → 25, `ActivityDetailViewModelTest` 10 → 13, App 141 → 150.

- [ ] **Step 1: Tests für wartende Fotos**

`app/src/test/java/de/touchgrass/data/PendingPhotosTest.kt`:
```kotlin
package de.touchgrass.data

import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testActivity
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException

class PendingPhotosTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val pending by lazy { PendingPhotos(File(folder.root, "pending")) }

    private inner class Api(private val failure: Exception? = null) : FakeSportApi() {
        val uploads = mutableListOf<Pair<Long, String>>()
        val offeredWhileUploading = mutableListOf<Boolean>()
        override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto {
            uploads += id to Buffer().also { photo.writeTo(it) }.readUtf8()
            offeredWhileUploading += pending.has(id)
            if (failure != null) throw failure
            return testActivity(id = id).copy(photoVersion = 3)
        }
    }

    private fun photoFile(content: String = "jpeg"): File = folder.newFile().apply { writeText(content) }

    private suspend fun failedUpload(failure: Exception, activityId: Long, photo: File): ApiException? =
        runCatching { pending.upload(Api(failure), activityId, photo) }.exceptionOrNull() as? ApiException

    @Test
    fun `während des Hochladens wird nichts angeboten, danach wartet nichts mehr`() = runBlocking {
        val api = Api()
        val photo = photoFile()

        val activity = pending.upload(api, 42, photo)

        assertEquals(3L, activity.photoVersion)
        assertEquals(listOf(42L to "jpeg"), api.uploads)
        assertEquals(listOf(false), api.offeredWhileUploading)
        assertFalse(photo.exists())
        assertFalse(pending.has(42))
    }

    @Test
    fun `bei Netz- und Serverfehlern wartet das Foto unter der Aktivität`() = runBlocking {
        val failures = listOf(
            IOException("Verbindung abgebrochen"),
            httpError(500, """{"error":"INTERNAL_ERROR","message":"Interner Serverfehler"}"""),
            HttpException(Response.error<Any>(503, "<html>Service Unavailable</html>".toResponseBody("text/html".toMediaType()))),
        )

        val codes = failures.mapIndexed { index, failure -> failedUpload(failure, 40L + index, photoFile("jpeg $index"))?.code }

        assertEquals(listOf("NETWORK", "INTERNAL_ERROR", "HTTP_503"), codes)
        assertEquals(listOf("jpeg 0", "jpeg 1", "jpeg 2"), (40L..42L).map { pending.file(it).readText() })
    }

    @Test
    fun `lehnt der Server das Foto ab, wird es gelöscht`() = runBlocking {
        val failures = listOf(
            httpError(413, """{"error":"PAYLOAD_TOO_LARGE","message":"Das Foto ist zu groß.","field":"photo"}"""),
            httpError(400, """{"error":"VALIDATION_ERROR","message":"Bitte ein Foto im JPEG-Format schicken.","field":"photo"}"""),
            httpError(403, """{"error":"FORBIDDEN","message":"Das ist nicht deine Aktivität."}"""),
            httpError(404, """{"error":"NOT_FOUND","message":"Aktivität nicht gefunden."}"""),
        )
        val photos = List(failures.size) { photoFile() }
        failedUpload(IOException("Verbindung abgebrochen"), 44, photoFile())

        val codes = failures.mapIndexed { index, failure -> failedUpload(failure, 40L + index, photos[index])?.code }
        val retried = failedUpload(httpError(413, """{"error":"PAYLOAD_TOO_LARGE","message":"Das Foto ist zu groß.","field":"photo"}"""), 44, pending.file(44))?.code

        assertEquals(listOf("PAYLOAD_TOO_LARGE", "VALIDATION_ERROR", "FORBIDDEN", "NOT_FOUND"), codes)
        assertEquals("PAYLOAD_TOO_LARGE", retried)
        assertEquals(List(5) { false }, (40L..44L).map { pending.has(it) })
        assertTrue(photos.none { it.exists() })
    }

    @Test
    fun `ein wartendes Foto lässt sich erneut hochladen`() = runBlocking {
        failedUpload(IOException("Verbindung abgebrochen"), 42, photoFile())
        val api = Api()

        pending.upload(api, 42, pending.file(42))

        assertEquals(listOf(42L to "jpeg"), api.uploads)
        assertFalse(pending.has(42))
    }

    @Test
    fun `ein neues Foto ersetzt das wartende, remove trifft nur die eigene Aktivität`() = runBlocking {
        failedUpload(IOException("Verbindung abgebrochen"), 42, photoFile("alt"))
        failedUpload(IOException("Verbindung abgebrochen"), 42, photoFile("neu"))
        failedUpload(IOException("Verbindung abgebrochen"), 7, photoFile("sieben"))

        pending.remove(7)

        assertEquals("neu", pending.file(42).readText())
        assertFalse(pending.has(7))
    }
}
```
Zu den Tests: Der Fake liest den Body wie Retrofit aus der Datei und wirft danach den eingestellten Fehler. Als innere Klasse hält er in `offeredWhileUploading` fest, was `has` während des Hochladens meldet. `httpError` baut eine Fehlerantwort mit JSON wie vom Backend, die 503 kommt mit HTML wie von Render und wird in `apiCall` zu `HTTP_503`. Im Test zur Ablehnung wartet das Foto der Aktivität 44 schon nach einem Netzfehler und wird beim zweiten Versuch abgelehnt, also der Weg von „Foto erneut hochladen“. Dafür braucht es ein neues `httpError`: `apiCall` liest den Fehler-Body, und ein schon gelesener Body ergäbe `HTTP_413`. Jeder Test endet mit einer Prüfung, damit `runBlocking` `Unit` liefert.

- [ ] **Step 2: Tests ausführen, sie müssen scheitern**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.data.PendingPhotosTest'`
Expected: BUILD FAILED, Kompilierfehler `Unresolved reference 'PendingPhotos'`.

- [ ] **Step 3: Wartende Fotos**

`app/src/main/java/de/touchgrass/data/PendingPhotos.kt`:
```kotlin
package de.touchgrass.data

import de.touchgrass.data.dto.ActivityDto
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class PendingPhotos(private val dir: File) {
    private val uploading = mutableSetOf<Long>()

    fun file(activityId: Long): File = File(dir, "$activityId.jpg")

    fun has(activityId: Long): Boolean = activityId !in uploading && file(activityId).isFile

    fun remove(activityId: Long) {
        file(activityId).delete()
    }

    // Das Foto liegt schon vor dem ersten Versuch unter seiner Aktivität und übersteht so auch einen Abbruch oder das Beenden der App.
    // Es bleibt nur nach Netz- und Serverfehlern. Bei jedem anderen Fehler hat der Server das Foto schon angenommen (BAD_RESPONSE) oder lehnt es auch beim nächsten Versuch ab.
    suspend fun upload(api: SportApi, activityId: Long, photo: File): ActivityDto {
        val waiting = file(activityId)
        uploading += activityId
        try {
            val activity = apiCall {
                if (photo != waiting) {
                    dir.mkdirs()
                    Files.move(photo.toPath(), waiting.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                api.uploadPhotoFile(activityId, waiting)
            }
            waiting.delete()
            return activity
        } catch (e: ApiException) {
            if (!e.temporary) waiting.delete()
            throw e
        } finally {
            uploading -= activityId
        }
    }

    private val ApiException.temporary: Boolean
        get() = code == "NETWORK" || code == "INTERNAL_ERROR" || code.startsWith("HTTP_5")
}
```
Zum Code:
- Das Verschieben steht im Block von `apiCall`: Eine `IOException` dabei, etwa weil die Datei fehlt, wird `ApiException("NETWORK", "Server nicht erreichbar")`. Die Datei ist dann nicht verschoben, und nichts geht verloren. Die ViewModels fangen nur `ApiException`.
- Ein Abbruch der Coroutine (Screen verlassen) kommt als `CancellationException`; `apiCall` und dieser `catch` fangen sie nicht, das Foto bleibt also liegen.
- `photo != waiting` vergleicht die Pfade. Beim erneuten Versuch übergeben die Details `file(activityId)` selbst, dann wird nichts verschoben.
- `uploading` merkt sich, welche Aktivitäten gerade hochladen; `has` meldet für sie nichts, auch wenn die Datei schon unter `pending/` liegt. `AppContainer` hat nur ein `PendingPhotos`, das gilt also über alle ViewModels: Wechselt man während „Speichern …“ den Tab, lädt das Formular im Hintergrund weiter, und die Details der neuen Aktivität zeigen „Foto erneut hochladen“ erst, wenn man sie nach einem gescheiterten Hochladen wieder öffnet. Das `finally` räumt auch nach einem Abbruch auf, die Datei bleibt dann liegen und wird angeboten. Die Menge braucht keine Synchronisierung, weil alle Aufrufer im `viewModelScope` auf dem Main-Thread laufen und `apiCall` den Dispatcher nicht wechselt.
- Kein Wechsel auf `Dispatchers.IO`, siehe „Entscheidungen“.

- [ ] **Step 4: Tests ausführen**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.data.PendingPhotosTest'`
Expected: keine Ausgabe, `TEST-de.touchgrass.data.PendingPhotosTest.xml` meldet `tests="5" skipped="0" failures="0" errors="0"`.

- [ ] **Step 5: Formular-Tests mit wartenden Fotos**

`app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt` komplett. Jeder Aufruf von `RecordViewModel` bekommt `pending` an fünfter Stelle. `` `Foto wird nach dem Anlegen hochgeladen und danach gelöscht` `` prüft zusätzlich, dass nichts wartet. Der Test zum Fehler 413 heißt jetzt `` `lehnt der Server das Foto ab, bleibt die Aktivität gespeichert und das Foto wird gelöscht` ``, neu ist `` `scheitert das Hochladen am Netz, wartet das Foto auf einen neuen Versuch in den Details` ``:
```kotlin
package de.touchgrass.ui.record

import androidx.lifecycle.SavedStateHandle
import de.touchgrass.data.PendingPhotos
import de.touchgrass.data.TimerState
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.CreateActivityResponse
import de.touchgrass.data.dto.PointsBreakdownDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.Visibility
import de.touchgrass.testing.FakePhotoPreparer
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.FakeTimerStore
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testAchievement
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.map.GeoPoint
import de.touchgrass.ui.photo.PhotoException
import de.touchgrass.ui.photo.UNSUPPORTED_PHOTO
import kotlinx.coroutines.CompletableDeferred
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecordViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-10-05T08:15:30Z"), ZoneId.of("Europe/Berlin"))

    private val pausedTimer = TimerState(
        sportCode = "CYCLING",
        startedAt = Instant.parse("2026-10-05T07:30:20Z"),
        pausedTotal = Duration.ofMinutes(5),
        pausedSince = Instant.parse("2026-10-05T08:15:30Z"),
    )

    private val noPhoto = FakePhotoPreparer { error("prepare() wird in diesem Test nicht erwartet") }

    private val pending by lazy { PendingPhotos(File(folder.root, "pending")) }

    private open inner class Api : FakeSportApi() {
        var sent: CreateActivityRequest? = null
        val uploads = mutableListOf<Pair<Long, String>>()
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse {
            sent = body
            return CreateActivityResponse(testActivity(), PointsBreakdownDto(45, 10, 20, 0, 75))
        }
        override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto {
            uploads += id to Buffer().also { photo.writeTo(it) }.readUtf8()
            return testActivity(id = id).copy(photoVersion = 1)
        }
    }

    private fun photoFile(): File = folder.newFile().apply { writeText("jpeg") }

    @Test
    fun `Formular startet mit Sportarten und der aktuellen Zeit auf die Minute`() {
        val viewModel = RecordViewModel(Api(), FakeTimerStore(), fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)

        val state = viewModel.state.value
        assertEquals(testSports, state.sports)
        assertEquals(LocalDate.parse("2026-10-05"), state.date)
        assertEquals(LocalTime.parse("10:15"), state.time)
        assertFalse(state.fromTimer)
    }

    @Test
    fun `ohne Sportart oder mit ungültiger Dauer wird nichts gesendet`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.onDurationChange("45")
        viewModel.save()
        assertEquals("Bitte eine Sportart wählen.", viewModel.state.value.fieldErrors["sportCode"])

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("dreiviertel Stunde")
        viewModel.save()
        assertEquals("Bitte die Dauer in ganzen Minuten angeben.", viewModel.state.value.fieldErrors["durationMin"])
        assertNull(api.sent)
    }

    @Test
    fun `Speichern schickt die Aktivität mit Berliner Zeit und leert das Formular`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDateChange(LocalDate.parse("2026-10-04"))
        viewModel.onTimeChange(LocalTime.parse("09:30"))
        viewModel.onDurationChange(" 45 ")
        viewModel.onDescriptionChange("  ")
        viewModel.onVisibilityChange(Visibility.PUBLIC)
        viewModel.save()

        assertEquals(
            CreateActivityRequest("RUNNING", "2026-10-04T09:30:00+02:00", 45, visibility = Visibility.PUBLIC),
            api.sent,
        )
        val state = viewModel.state.value
        assertEquals("Gespeichert! ~480 kcal · +75 Punkte (45 min · +10 · +20 neue Sportart)", state.savedMessage)
        assertNull(state.sportCode)
        assertEquals("", state.duration)
        assertEquals(testSports, state.sports)
    }

    @Test
    fun `gewählter Ort wird mitgeschickt und lässt sich wieder entfernen`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.onLocationChange(GeoPoint(48.1642, 11.6056))
        viewModel.save()
        assertEquals(48.1642, api.sent?.latitude)
        assertEquals(11.6056, api.sent?.longitude)
        assertNull(viewModel.state.value.location)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.onLocationChange(GeoPoint(48.1642, 11.6056))
        viewModel.onLocationChange(null)
        viewModel.save()
        assertNull(api.sent?.latitude)
        assertNull(api.sent?.longitude)
    }

    @Test
    fun `Feldfehler vom Server erscheint am Feld`() {
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse = throw httpError(
                400,
                """{"error":"VALIDATION_ERROR","message":"Die Aktivität darf nicht in der Zukunft liegen.","field":"startedAt"}""",
            )
        }
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        assertEquals("Die Aktivität darf nicht in der Zukunft liegen.", viewModel.state.value.fieldErrors["startedAt"])
        assertEquals(false, viewModel.state.value.saving)
    }

    @Test
    fun `vom Timer kommen Sportart, Startzeit und Dauer ohne Pausen`() {
        val viewModel = RecordViewModel(Api(), FakeTimerStore(pausedTimer), fromTimer = true, noPhoto, pending, SavedStateHandle(), clock)

        val state = viewModel.state.value
        assertEquals("CYCLING", state.sportCode)
        assertEquals(LocalDate.parse("2026-10-05"), state.date)
        assertEquals(LocalTime.parse("09:30"), state.time)
        assertEquals("40", state.duration)
        assertTrue(state.fromTimer)
    }

    @Test
    fun `nach dem Speichern aus dem Timer ist der Timer zurückgesetzt`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = true, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.save()

        assertNull(store.current.value)
        assertFalse(viewModel.state.value.fromTimer)
    }

    @Test
    fun `Nachtragen ohne Timer lässt einen laufenden Timer in Ruhe`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        assertEquals(pausedTimer, store.current.value)
    }

    @Test
    fun `fehlgeschlagenes Speichern behält den Timer`() {
        val store = FakeTimerStore(pausedTimer)
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse =
                throw httpError(500, """{"error":"INTERNAL_ERROR","message":"Unerwarteter Fehler."}""")
        }
        val viewModel = RecordViewModel(api, store, fromTimer = true, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.save()

        assertEquals(pausedTimer, store.current.value)
        assertEquals("Unerwarteter Fehler.", viewModel.state.value.error)
    }

    @Test
    fun `Verwerfen setzt den Timer zurück`() {
        val store = FakeTimerStore(pausedTimer)
        val viewModel = RecordViewModel(Api(), store, fromTimer = true, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.discard()

        assertNull(store.current.value)
        assertTrue(viewModel.state.value.discarded)
    }

    @Test
    fun `Verwerfen löscht das gewählte Foto`() {
        val photo = photoFile()
        val viewModel = RecordViewModel(Api(), FakeTimerStore(pausedTimer), fromTimer = true, FakePhotoPreparer { photo }, pending, SavedStateHandle(), clock)
        viewModel.onPhotoPicked("content://media/picker/0/42")

        viewModel.discard()

        val state = viewModel.state.value
        assertTrue(state.discarded)
        assertNull(state.photoPath)
        assertFalse(photo.exists())
    }

    @Test
    fun `neue Achievements erscheinen nach dem Speichern, bis sie gesehen wurden`() {
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse =
                CreateActivityResponse(testActivity(), PointsBreakdownDto(45, 10, 20, 0, 75), listOf(testAchievement()))
        }
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()
        assertEquals(listOf("FIRST_ACTIVITY"), viewModel.state.value.newAchievements.map { it.code })

        viewModel.onAchievementsSeen()
        assertEquals(emptyList<String>(), viewModel.state.value.newAchievements.map { it.code })
    }

    @Test
    fun `Sportarten lassen sich nach einem Fehler erneut laden`() {
        var calls = 0
        val failingOnce = object : FakeSportApi() {
            override suspend fun sports(): List<SportDto> = if (calls++ == 0) throw IOException("offline") else testSports
        }
        val viewModel = RecordViewModel(failingOnce, FakeTimerStore(), fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)
        assertEquals("Server nicht erreichbar", viewModel.state.value.sportsError)

        viewModel.loadSports()

        assertEquals(testSports, viewModel.state.value.sports)
        assertNull(viewModel.state.value.sportsError)
    }

    @Test
    fun `Sichtbarkeit Freunde wird mitgeschickt`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.onVisibilityChange(Visibility.FRIENDS)
        viewModel.save()

        assertEquals(Visibility.FRIENDS, api.sent?.visibility)
    }

    @Test
    fun `Foto wird nach dem Anlegen hochgeladen und danach gelöscht`() {
        val api = Api()
        val photo = photoFile()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, FakePhotoPreparer { photo }, pending, SavedStateHandle(), clock)

        viewModel.onPhotoPicked("content://media/picker/0/42")
        val picked = viewModel.state.value.photoPath
        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        val state = viewModel.state.value
        assertEquals(photo.path, picked)
        assertEquals(listOf(42L to "jpeg"), api.uploads)
        assertFalse(photo.exists())
        assertFalse(pending.has(42))
        assertEquals("Gespeichert! ~480 kcal · +75 Punkte (45 min · +10 · +20 neue Sportart)", state.savedMessage)
        assertNull(state.photoPath)
    }

    @Test
    fun `ohne Foto wird nichts hochgeladen`() {
        val api = Api()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        assertEquals(emptyList<Pair<Long, String>>(), api.uploads)
        assertEquals("Gespeichert! ~480 kcal · +75 Punkte (45 min · +10 · +20 neue Sportart)", viewModel.state.value.savedMessage)
    }

    @Test
    fun `lehnt der Server das Foto ab, bleibt die Aktivität gespeichert und das Foto wird gelöscht`() {
        val store = FakeTimerStore(pausedTimer)
        val api = object : Api() {
            override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto =
                throw httpError(413, """{"error":"PAYLOAD_TOO_LARGE","message":"Das Foto ist zu groß.","field":"photo"}""")
        }
        val photo = photoFile()
        val viewModel = RecordViewModel(api, store, fromTimer = true, FakePhotoPreparer { photo }, pending, SavedStateHandle(), clock)

        viewModel.onPhotoPicked("content://media/picker/0/42")
        viewModel.save()

        val state = viewModel.state.value
        assertEquals("CYCLING", api.sent?.sportCode)
        assertEquals(
            "Gespeichert, aber das Foto konnte nicht hochgeladen werden. Du kannst es in den Details erneut hinzufügen.",
            state.savedMessage,
        )
        assertNull(state.sportCode)
        assertNull(state.photoPath)
        assertTrue(state.fieldErrors.isEmpty())
        assertNull(store.current.value)
        assertFalse(photo.exists())
        assertFalse(pending.has(42))
    }

    @Test
    fun `scheitert das Hochladen am Netz, wartet das Foto auf einen neuen Versuch in den Details`() {
        val store = FakeTimerStore(pausedTimer)
        val api = object : Api() {
            override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto = throw IOException("Verbindung abgebrochen")
        }
        val photo = photoFile()
        val viewModel = RecordViewModel(api, store, fromTimer = true, FakePhotoPreparer { photo }, pending, SavedStateHandle(), clock)

        viewModel.onPhotoPicked("content://media/picker/0/42")
        viewModel.save()

        val state = viewModel.state.value
        assertEquals(
            "Gespeichert, aber das Foto konnte nicht hochgeladen werden. Du kannst es in den Details erneut hinzufügen.",
            state.savedMessage,
        )
        assertNull(state.photoPath)
        assertNull(store.current.value)
        assertFalse(photo.exists())
        assertEquals("jpeg", pending.file(42).readText())
    }

    @Test
    fun `nicht lesbares Bild meldet das Formular am Foto`() {
        val viewModel = RecordViewModel(
            Api(),
            FakeTimerStore(),
            fromTimer = false,
            FakePhotoPreparer { throw PhotoException(UNSUPPORTED_PHOTO) },
            pending,
            SavedStateHandle(),
            clock,
        )

        viewModel.onPhotoPicked("content://media/picker/0/42")

        val state = viewModel.state.value
        assertEquals("Dieses Bildformat wird nicht unterstützt.", state.fieldErrors["photo"])
        assertNull(state.photoPath)
        assertFalse(state.photoBusy)
    }

    @Test
    fun `Entfernen leert das Foto und löscht die Datei`() {
        val photo = photoFile()
        val viewModel = RecordViewModel(Api(), FakeTimerStore(), fromTimer = false, FakePhotoPreparer { photo }, pending, SavedStateHandle(), clock)
        viewModel.onPhotoPicked("content://media/picker/0/42")

        viewModel.removePhoto()

        assertNull(viewModel.state.value.photoPath)
        assertFalse(photo.exists())
    }

    @Test
    fun `nach dem Beenden der App stellt das SavedStateHandle Formular und Foto wieder her`() {
        val handleWithRouteArgs = SavedStateHandle(mapOf("fromTimer" to true))
        val photo = photoFile()
        val before = RecordViewModel(
            Api(),
            FakeTimerStore(pausedTimer),
            fromTimer = true,
            FakePhotoPreparer { photo },
            pending,
            handleWithRouteArgs,
            clock,
        )
        before.onDurationChange("35")
        before.onLocationChange(GeoPoint(48.1642, 11.6056))
        before.onDescriptionChange("Runde an der Isar")
        before.onVisibilityChange(Visibility.FRIENDS)
        before.onPhotoPicked("content://media/picker/0/42")

        val after = RecordViewModel(Api(), FakeTimerStore(pausedTimer), fromTimer = true, noPhoto, pending, handleWithRouteArgs, clock)

        assertEquals("CYCLING", before.state.value.sportCode)
        assertEquals(before.state.value, after.state.value)
        assertEquals("35", after.state.value.duration)
        assertEquals(photo.path, after.state.value.photoPath)
    }

    @Test
    fun `während des Speicherns sichert das Handle kein Formular`() {
        val handle = SavedStateHandle()
        val created = CompletableDeferred<CreateActivityResponse>()
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse = created.await()
        }
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, noPhoto, pending, handle, clock)
        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")

        viewModel.save()
        val dateWhileSaving = handle.get<String>("date")
        created.complete(CreateActivityResponse(testActivity(), PointsBreakdownDto(45, 10, 20, 0, 75)))

        assertNull(dateWhileSaving)
        assertEquals("2026-10-05", handle.get<String>("date"))
        assertNull(handle.get<String>("sportCode"))
    }

    @Test
    fun `scheitert schon das Anlegen, bleibt das Foto im Formular`() {
        val api = object : Api() {
            override suspend fun createActivity(body: CreateActivityRequest): CreateActivityResponse =
                throw httpError(500, """{"error":"INTERNAL_ERROR","message":"Unerwarteter Fehler."}""")
        }
        val photo = photoFile()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, FakePhotoPreparer { photo }, pending, SavedStateHandle(), clock)

        viewModel.onPhotoPicked("content://media/picker/0/42")
        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")
        viewModel.save()

        val state = viewModel.state.value
        assertEquals("Unerwarteter Fehler.", state.error)
        assertEquals(photo.path, state.photoPath)
        assertTrue(photo.exists())
        assertEquals(emptyList<Pair<Long, String>>(), api.uploads)
    }

    @Test
    fun `während das Foto vorbereitet wird, schickt Speichern nichts`() {
        val api = Api()
        val photo = photoFile()
        val prepared = CompletableDeferred<File>()
        val viewModel = RecordViewModel(api, FakeTimerStore(), fromTimer = false, FakePhotoPreparer { prepared.await() }, pending, SavedStateHandle(), clock)
        viewModel.onSportChange("RUNNING")
        viewModel.onDurationChange("45")

        viewModel.onPhotoPicked("content://media/picker/0/42")
        val busy = viewModel.state.value.photoBusy
        viewModel.save()
        val sentWhileBusy = api.sent
        prepared.complete(photo)

        val state = viewModel.state.value
        assertTrue(busy)
        assertNull(sentWhileBusy)
        assertEquals(photo.path, state.photoPath)
        assertFalse(state.photoBusy)
    }

    @Test
    fun `fehlende Kamera-App erscheint am Foto`() {
        val viewModel = RecordViewModel(Api(), FakeTimerStore(), fromTimer = false, noPhoto, pending, SavedStateHandle(), clock)

        viewModel.onPhotoError("Keine Kamera-App gefunden.")

        assertEquals("Keine Kamera-App gefunden.", viewModel.state.value.fieldErrors["photo"])
    }
}
```
`pending` ist `by lazy`, weil `TemporaryFolder` seinen Ordner erst anlegt, wenn der Test läuft. Der Fake wirft die `IOException` vor dem Lesen des Bodys; die Datei ist dann schon nach `pending/42.jpg` verschoben.

- [ ] **Step 6: Detail-Tests mit wartenden Fotos**

`app/src/test/java/de/touchgrass/ui/activities/ActivityDetailViewModelTest.kt` komplett. Jeder Aufruf von `ActivityDetailViewModel` bekommt `pending` an vierter Stelle, die zwei Tests zum Hochladen prüfen zusätzlich, dass nichts wartet, und drei Tests sind neu:
```kotlin
package de.touchgrass.ui.activities

import de.touchgrass.data.PendingPhotos
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.testing.FakePhotoPreparer
import de.touchgrass.testing.FakeSportApi
import de.touchgrass.testing.MainDispatcherRule
import de.touchgrass.testing.httpError
import de.touchgrass.testing.testActivity
import de.touchgrass.testing.testSports
import de.touchgrass.ui.UiState
import de.touchgrass.ui.photo.PhotoException
import de.touchgrass.ui.photo.UNSUPPORTED_PHOTO
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ActivityDetailViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val noPhoto = FakePhotoPreparer { error("prepare() wird in diesem Test nicht erwartet") }

    private val pending by lazy { PendingPhotos(File(folder.root, "pending")) }

    private open inner class Api(private val photoVersion: Long? = null) : FakeSportApi() {
        var deletedId: Long? = null
        val uploads = mutableListOf<Pair<Long, String>>()
        val deletedPhotos = mutableListOf<Long>()
        override suspend fun sports(): List<SportDto> = testSports
        override suspend fun activity(id: Long): ActivityDto = testActivity(id = id).copy(photoVersion = photoVersion)
        override suspend fun deleteActivity(id: Long) {
            deletedId = id
        }
        override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto {
            uploads += id to Buffer().also { photo.writeTo(it) }.readUtf8()
            return testActivity(id = id).copy(photoVersion = 8)
        }
        override suspend fun deletePhoto(id: Long) {
            deletedPhotos += id
        }
    }

    private fun photoFile(): File = folder.newFile().apply { writeText("jpeg") }

    private fun waitingPhoto(activityId: Long): File =
        pending.file(activityId).apply {
            parentFile?.mkdirs()
            writeText("wartet")
        }

    @Test
    fun `lädt die Aktivität mit der übergebenen ID`() {
        val viewModel = ActivityDetailViewModel(Api(), id = 7, noPhoto, pending)

        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
    }

    @Test
    fun `Löschen ruft die API auf und meldet deleted`() {
        val api = Api()
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto, pending)

        viewModel.delete()

        assertEquals(7L, api.deletedId)
        assertTrue(viewModel.state.value.deleted)
    }

    @Test
    fun `Fehler beim Löschen wird angezeigt`() {
        val api = object : Api() {
            override suspend fun deleteActivity(id: Long): Unit =
                throw httpError(403, """{"error":"FORBIDDEN","message":"Das ist nicht deine Aktivität."}""")
        }
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto, pending)

        viewModel.delete()

        assertFalse(viewModel.state.value.deleted)
        assertEquals("Das ist nicht deine Aktivität.", viewModel.state.value.error)
    }

    @Test
    fun `nach einem Ladefehler lädt Erneut versuchen die Aktivität`() {
        var calls = 0
        val failingOnce = object : Api() {
            override suspend fun activity(id: Long): ActivityDto = if (calls++ == 0) throw IOException("offline") else testActivity(id = id)
        }
        val viewModel = ActivityDetailViewModel(failingOnce, id = 7, noPhoto, pending)
        assertEquals(UiState.Error("Server nicht erreichbar"), viewModel.state.value.activity)

        viewModel.load()

        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
    }

    @Test
    fun `neues Foto wird hochgeladen und die Antwort ersetzt die Aktivität`() {
        val api = Api(photoVersion = 5)
        val photo = photoFile()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { photo }, pending)

        viewModel.onPhotoPicked("content://media/picker/0/42")

        val state = viewModel.state.value
        assertEquals(listOf(7L to "jpeg"), api.uploads)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 8)), state.activity)
        assertFalse(state.photoBusy)
        assertFalse(state.photoPending)
        assertFalse(photo.exists())
        assertFalse(pending.has(7))
    }

    @Test
    fun `Foto entfernen ruft deletePhoto auf und setzt photoVersion auf null`() {
        val api = Api(photoVersion = 5)
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto, pending)

        viewModel.removePhoto()

        assertEquals(listOf(7L), api.deletedPhotos)
        assertEquals(UiState.Content(testActivity(id = 7)), viewModel.state.value.activity)
        assertFalse(viewModel.state.value.photoBusy)
    }

    @Test
    fun `Fehler beim Hochladen landen in error, das bisherige Foto bleibt`() {
        val api = object : Api(photoVersion = 5) {
            override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto =
                throw httpError(413, """{"error":"PAYLOAD_TOO_LARGE","message":"Das Foto ist zu groß.","field":"photo"}""")
        }
        val photo = photoFile()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { photo }, pending)

        viewModel.onPhotoPicked("content://media/picker/0/42")

        val state = viewModel.state.value
        assertEquals("Das Foto ist zu groß.", state.error)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 5)), state.activity)
        assertFalse(state.photoBusy)
        assertFalse(state.photoPending)
        assertFalse(photo.exists())
        assertFalse(pending.has(7))
    }

    @Test
    fun `nicht lesbares Bild meldet das Bildformat`() {
        val api = Api()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { throw PhotoException(UNSUPPORTED_PHOTO) }, pending)

        viewModel.onPhotoPicked("content://media/picker/0/42")

        assertEquals("Dieses Bildformat wird nicht unterstützt.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.photoBusy)
        assertEquals(emptyList<Pair<Long, String>>(), api.uploads)
    }

    @Test
    fun `scheitert das Entfernen, bleibt das Foto`() {
        val api = object : Api(photoVersion = 5) {
            override suspend fun deletePhoto(id: Long): Unit =
                throw httpError(500, """{"error":"INTERNAL_ERROR","message":"Unerwarteter Fehler."}""")
        }
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto, pending)

        viewModel.removePhoto()

        val state = viewModel.state.value
        assertEquals("Unerwarteter Fehler.", state.error)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 5)), state.activity)
        assertFalse(state.photoBusy)
    }

    @Test
    fun `fehlende Kamera-App erscheint als Fehler`() {
        val viewModel = ActivityDetailViewModel(Api(), id = 7, noPhoto, pending)

        viewModel.onPhotoError("Keine Kamera-App gefunden.")

        assertEquals("Keine Kamera-App gefunden.", viewModel.state.value.error)
    }

    @Test
    fun `scheitert das Hochladen am Netz, wartet das Foto auf einen neuen Versuch`() {
        val api = object : Api(photoVersion = 5) {
            override suspend fun uploadPhoto(id: Long, photo: RequestBody): ActivityDto = throw IOException("Verbindung abgebrochen")
        }
        val photo = photoFile()
        val viewModel = ActivityDetailViewModel(api, id = 7, FakePhotoPreparer { photo }, pending)

        viewModel.onPhotoPicked("content://media/picker/0/42")

        val state = viewModel.state.value
        assertEquals("Server nicht erreichbar", state.error)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 5)), state.activity)
        assertFalse(state.photoBusy)
        assertTrue(state.photoPending)
        assertEquals("jpeg", pending.file(7).readText())
    }

    @Test
    fun `ein wartendes Foto wird angeboten und lässt sich erneut hochladen`() {
        val api = Api(photoVersion = 5)
        waitingPhoto(7)
        val viewModel = ActivityDetailViewModel(api, id = 7, noPhoto, pending)
        val offered = viewModel.state.value.photoPending
        val offeredElsewhere = ActivityDetailViewModel(api, id = 8, noPhoto, pending).state.value.photoPending

        viewModel.retryPhotoUpload()

        val state = viewModel.state.value
        assertTrue(offered)
        assertFalse(offeredElsewhere)
        assertEquals(listOf(7L to "wartet"), api.uploads)
        assertEquals(UiState.Content(testActivity(id = 7).copy(photoVersion = 8)), state.activity)
        assertFalse(state.photoPending)
        assertFalse(pending.has(7))
    }

    @Test
    fun `Löschen der Aktivität löscht auch das wartende Foto`() {
        waitingPhoto(7)
        val viewModel = ActivityDetailViewModel(Api(), id = 7, noPhoto, pending)

        viewModel.delete()

        assertTrue(viewModel.state.value.deleted)
        assertFalse(pending.has(7))
    }
}
```

- [ ] **Step 7: Tests ausführen, sie müssen scheitern**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.ui.record.RecordViewModelTest' --tests 'de.touchgrass.ui.activities.ActivityDetailViewModelTest'`
Expected: BUILD FAILED, Kompilierfehler in beiden Testdateien: `RecordViewModel` und `ActivityDetailViewModel` nehmen noch kein `PendingPhotos` (zu viele Argumente, `PendingPhotos` statt `SavedStateHandle` an fünfter Stelle), `photoPending` und `retryPhotoUpload` sind unbekannt.

- [ ] **Step 8: Formular lädt über `PendingPhotos`**

`app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt` komplett. Gegenüber Task 4 neu: der Parameter `pending`, `tryUploadPhoto` ohne `finally`, der Import von `PendingPhotos` statt `uploadPhotoFile` und der Kommentar an `keepForm`:
```kotlin
package de.touchgrass.ui.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.PendingPhotos
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerState
import de.touchgrass.data.TimerStore
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.AchievementDto
import de.touchgrass.data.dto.CreateActivityRequest
import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.Visibility
import de.touchgrass.ui.activities.describe
import de.touchgrass.ui.map.GeoPoint
import de.touchgrass.ui.photo.PhotoException
import de.touchgrass.ui.photo.PhotoPreparer
import de.touchgrass.ui.photo.UNSUPPORTED_PHOTO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private const val PHOTO_UPLOAD_FAILED =
    "Gespeichert, aber das Foto konnte nicht hochgeladen werden. Du kannst es in den Details erneut hinzufügen."

private const val KEY_SPORT = "sportCode"
private const val KEY_DATE = "date"
private const val KEY_TIME = "time"
private const val KEY_DURATION = "duration"
private const val KEY_LATITUDE = "latitude"
private const val KEY_LONGITUDE = "longitude"
private const val KEY_DESCRIPTION = "description"
private const val KEY_VISIBILITY = "visibility"
private const val KEY_PHOTO = "photoPath"
private const val KEY_FROM_TIMER = "formFromTimer"

data class RecordUiState(
    val sports: List<SportDto> = emptyList(),
    val sportsError: String? = null,
    val sportCode: String? = null,
    val date: LocalDate,
    val time: LocalTime,
    val duration: String = "",
    val location: GeoPoint? = null,
    val description: String = "",
    val visibility: Visibility = Visibility.PRIVATE,
    val photoPath: String? = null,
    val photoBusy: Boolean = false,
    val fromTimer: Boolean = false,
    val saving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val savedMessage: String? = null,
    val newAchievements: List<AchievementDto> = emptyList(),
    val discarded: Boolean = false,
)

class RecordViewModel(
    private val api: SportApi,
    private val timerStore: TimerStore,
    fromTimer: Boolean,
    private val photos: PhotoPreparer,
    private val pending: PendingPhotos,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _state = MutableStateFlow(emptyForm())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    init {
        val saved = savedForm()
        if (saved != null) {
            _state.value = saved
        } else if (fromTimer) {
            viewModelScope.launch { timerStore.state.first()?.let(::fillFromTimer) }
        }
        loadSports()
        viewModelScope.launch { _state.collect { keepForm(it) } }
    }

    fun loadSports() {
        viewModelScope.launch {
            try {
                val sports = apiCall { api.sports() }
                _state.update { it.copy(sports = sports, sportsError = null) }
            } catch (e: ApiException) {
                _state.update { it.copy(sportsError = e.message) }
            }
        }
    }

    fun onSportChange(code: String) = edit("sportCode") { copy(sportCode = code) }

    fun onDateChange(date: LocalDate) = edit("startedAt") { copy(date = date) }

    fun onTimeChange(time: LocalTime) = edit("startedAt") { copy(time = time) }

    fun onDurationChange(value: String) = edit("durationMin") { copy(duration = value) }

    fun onLocationChange(location: GeoPoint?) = edit("latitude", "longitude") { copy(location = location) }

    fun onDescriptionChange(value: String) = edit("description") { copy(description = value) }

    fun onVisibilityChange(value: Visibility) = edit("visibility") { copy(visibility = value) }

    fun onPhotoPicked(source: String) {
        edit("photo") { copy(photoBusy = true) }
        viewModelScope.launch {
            try {
                val file = photos.prepare(source)
                _state.update { it.copy(photoPath = file.path, photoBusy = false) }
            } catch (e: PhotoException) {
                _state.update { it.copy(photoBusy = false) }
                showFieldError("photo", e.message ?: UNSUPPORTED_PHOTO)
            }
        }
    }

    fun onPhotoError(message: String) = showFieldError("photo", message)

    fun removePhoto() {
        _state.value.photoPath?.let { File(it).delete() }
        edit("photo") { copy(photoPath = null) }
    }

    fun save() {
        val current = _state.value
        if (current.saving || current.photoBusy) return
        val sportCode = current.sportCode ?: return showFieldError("sportCode", "Bitte eine Sportart wählen.")
        val duration = current.duration.trim().toIntOrNull()
            ?: return showFieldError("durationMin", "Bitte die Dauer in ganzen Minuten angeben.")
        val request = CreateActivityRequest(
            sportCode = sportCode,
            startedAt = ZonedDateTime.of(current.date, current.time, clock.zone)
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            durationMin = duration,
            latitude = current.location?.latitude,
            longitude = current.location?.longitude,
            description = current.description.trim().ifEmpty { null },
            visibility = current.visibility,
        )
        _state.update { it.copy(saving = true, fieldErrors = emptyMap(), error = null, savedMessage = null) }
        viewModelScope.launch {
            try {
                val response = apiCall { api.createActivity(request) }
                if (current.fromTimer) timerStore.clear()
                val photoUploaded = current.photoPath == null || tryUploadPhoto(response.activity.id, File(current.photoPath))
                val points = response.pointsBreakdown
                _state.value = emptyForm().copy(
                    sports = current.sports,
                    savedMessage = if (photoUploaded) {
                        "Gespeichert! ~${response.activity.caloriesKcal} kcal · +${points.total} Punkte (${points.describe()})"
                    } else {
                        PHOTO_UPLOAD_FAILED
                    },
                    newAchievements = response.newAchievements,
                )
            } catch (e: ApiException) {
                _state.update { it.copy(saving = false) }
                if (e.field != null) showFieldError(e.field, e.message) else _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun onAchievementsSeen() = _state.update { it.copy(newAchievements = emptyList()) }

    fun discard() {
        _state.value.photoPath?.let { File(it).delete() }
        viewModelScope.launch {
            timerStore.clear()
            _state.update { it.copy(photoPath = null, discarded = true) }
        }
    }

    private suspend fun tryUploadPhoto(activityId: Long, file: File): Boolean =
        try {
            pending.upload(api, activityId, file)
            true
        } catch (e: ApiException) {
            false
        }

    private fun fillFromTimer(timer: TimerState) {
        val start = LocalDateTime.ofInstant(timer.startedAt, clock.zone).truncatedTo(ChronoUnit.MINUTES)
        _state.update {
            it.copy(
                sportCode = timer.sportCode,
                date = start.toLocalDate(),
                time = start.toLocalTime(),
                duration = timer.durationMinutes(clock.instant()).toString(),
                fromTimer = true,
            )
        }
    }

    private fun emptyForm(): RecordUiState {
        val now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES)
        return RecordUiState(date = now.toLocalDate(), time = now.toLocalTime())
    }

    // Während des Speicherns steht kein Formular im Handle. Endet das Speichern im Hintergrund und beendet Android danach die App, käme sonst das schon gespeicherte Formular zurück, obwohl sein Foto nicht mehr am alten Ort liegt.
    private fun keepForm(form: RecordUiState) {
        if (form.saving) {
            savedState.remove<String>(KEY_DATE)
            return
        }
        savedState[KEY_SPORT] = form.sportCode
        savedState[KEY_DATE] = form.date.toString()
        savedState[KEY_TIME] = form.time.toString()
        savedState[KEY_DURATION] = form.duration
        savedState[KEY_LATITUDE] = form.location?.latitude
        savedState[KEY_LONGITUDE] = form.location?.longitude
        savedState[KEY_DESCRIPTION] = form.description
        savedState[KEY_VISIBILITY] = form.visibility.name
        savedState[KEY_PHOTO] = form.photoPath
        savedState[KEY_FROM_TIMER] = form.fromTimer
    }

    // Im Handle stehen auch die Argumente der Route. Ein gesichertes Formular erkennt man deshalb erst am Datum.
    private fun savedForm(): RecordUiState? {
        val date = savedState.get<String>(KEY_DATE) ?: return null
        val time = savedState.get<String>(KEY_TIME) ?: return null
        val latitude = savedState.get<Double>(KEY_LATITUDE)
        val longitude = savedState.get<Double>(KEY_LONGITUDE)
        return RecordUiState(
            sportCode = savedState.get<String>(KEY_SPORT),
            date = LocalDate.parse(date),
            time = LocalTime.parse(time),
            duration = savedState.get<String>(KEY_DURATION).orEmpty(),
            location = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null,
            description = savedState.get<String>(KEY_DESCRIPTION).orEmpty(),
            visibility = savedState.get<String>(KEY_VISIBILITY)?.let(Visibility::valueOf) ?: Visibility.PRIVATE,
            photoPath = savedState.get<String>(KEY_PHOTO),
            fromTimer = savedState.get<Boolean>(KEY_FROM_TIMER) == true,
        )
    }

    private fun edit(vararg fields: String, change: RecordUiState.() -> RecordUiState) =
        _state.update { it.change().copy(fieldErrors = it.fieldErrors - fields.toSet(), error = null, savedMessage = null) }

    private fun showFieldError(field: String, message: String) =
        _state.update { it.copy(fieldErrors = it.fieldErrors + (field to message)) }
}
```
Die Meldung bei gescheitertem Hochladen bleibt `PHOTO_UPLOAD_FAILED`, wörtlich wie im Entwurf. Sie stimmt jetzt für jedes Foto: Nach einem Netz- oder Serverfehler wartet es in den Details auf „Foto erneut hochladen“, nach einer Ablehnung durch den Server lässt sich dort ein anderes hinzufügen.

- [ ] **Step 9: Details mit wartendem Foto**

`app/src/main/java/de/touchgrass/ui/activities/ActivityDetailViewModel.kt` komplett:
```kotlin
package de.touchgrass.ui.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.touchgrass.data.ApiException
import de.touchgrass.data.PendingPhotos
import de.touchgrass.data.SportApi
import de.touchgrass.data.apiCall
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.dto.SportDto
import de.touchgrass.ui.UiState
import de.touchgrass.ui.photo.PhotoException
import de.touchgrass.ui.photo.PhotoPreparer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityDetailUiState(
    val sports: List<SportDto> = emptyList(),
    val activity: UiState<ActivityDto> = UiState.Loading,
    val photoBusy: Boolean = false,
    val photoPending: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
)

class ActivityDetailViewModel(
    private val api: SportApi,
    private val id: Long,
    private val photos: PhotoPreparer,
    private val pending: PendingPhotos,
) : ViewModel() {
    private val _state = MutableStateFlow(ActivityDetailUiState())
    val state: StateFlow<ActivityDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(activity = UiState.Loading) }
        viewModelScope.launch {
            try {
                val sports = apiCall { api.sports() }
                val activity = apiCall { api.activity(id) }
                _state.update { it.copy(sports = sports, activity = UiState.Content(activity), photoPending = pending.has(id)) }
            } catch (e: ApiException) {
                _state.update { it.copy(activity = UiState.Error(e.message)) }
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                apiCall { api.deleteActivity(id) }
                pending.remove(id)
                _state.update { it.copy(deleted = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun onPhotoPicked(source: String) = changePhoto { pending.upload(api, id, photos.prepare(source)) }

    fun retryPhotoUpload() = changePhoto { pending.upload(api, id, pending.file(id)) }

    fun onPhotoError(message: String) = _state.update { it.copy(error = message) }

    fun removePhoto() {
        val current = _state.value.activity
        if (current !is UiState.Content) return
        changePhoto {
            apiCall { api.deletePhoto(id) }
            current.data.copy(photoVersion = null)
        }
    }

    private fun changePhoto(change: suspend () -> ActivityDto) {
        _state.update { it.copy(photoBusy = true, error = null) }
        viewModelScope.launch {
            try {
                val activity = change()
                _state.update { it.copy(activity = UiState.Content(activity), photoBusy = false, photoPending = pending.has(id)) }
            } catch (e: PhotoException) {
                _state.update { it.copy(photoBusy = false, error = e.message) }
            } catch (e: ApiException) {
                _state.update { it.copy(photoBusy = false, photoPending = pending.has(id), error = e.message) }
            }
        }
    }
}
```
Zum Code:
- `onPhotoPicked` und `retryPhotoUpload` gehen denselben Weg über `PendingPhotos.upload`; ein neues Foto ersetzt dabei ein wartendes. Das `finally` mit `file.delete()` aus M8 entfällt, gelöscht wird nur noch in `PendingPhotos`.
- Nach einem Netz- oder Serverfehler steht in `error` „Server nicht erreichbar“ oder die Meldung des Servers, `photoPending` wird `true`, und das bisherige Foto bleibt angezeigt.
- `delete()` löscht das wartende Foto erst nach erfolgreichem Löschen der Aktivität. Scheitert das Löschen, bleibt es für einen neuen Versuch.

- [ ] **Step 10: Navigation und Container**

In `app/src/main/java/de/touchgrass/ui/Navigation.kt` zwei Änderungen.

**Formular.** Ersetzen:
```kotlin
                            RecordViewModel(container.api, container.timerStore, fromTimer, container.photoPreparer, createSavedStateHandle())
```
durch:
```kotlin
                            RecordViewModel(
                                container.api,
                                container.timerStore,
                                fromTimer,
                                container.photoPreparer,
                                container.pendingPhotos,
                                createSavedStateHandle(),
                            )
```

**Details.** Ersetzen:
```kotlin
                        viewModel = viewModel { ActivityDetailViewModel(container.api, id, container.photoPreparer) },
```
durch:
```kotlin
                        viewModel = viewModel { ActivityDetailViewModel(container.api, id, container.photoPreparer, container.pendingPhotos) },
```

`app/src/main/java/de/touchgrass/AppContainer.kt` komplett (gegenüber Task 4 neu: `pendingPhotos` und der Import von `PendingPhotos`):
```kotlin
package de.touchgrass

import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import de.touchgrass.data.AuthInterceptor
import de.touchgrass.data.AuthSession
import de.touchgrass.data.AuthState
import de.touchgrass.data.DataStoreTimerStore
import de.touchgrass.data.DataStoreTokenStore
import de.touchgrass.data.PendingPhotos
import de.touchgrass.data.SportApi
import de.touchgrass.data.TimerStore
import de.touchgrass.data.authDataStore
import de.touchgrass.data.createSportApi
import de.touchgrass.data.httpClient
import de.touchgrass.data.imageClient
import de.touchgrass.data.timerDataStore
import de.touchgrass.ui.photo.AndroidPhotoPreparer
import de.touchgrass.ui.photo.PhotoPreparer
import de.touchgrass.ui.photo.cameraDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppContainer(context: Context, baseUrl: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Nicht in filesDir: Fotos auf dem Handy sollen nicht in die Datensicherung von Android.
    private val photoDir = File(context.noBackupFilesDir, "photos")

    val session = AuthSession(DataStoreTokenStore(context.authDataStore), scope)

    val timerStore: TimerStore = DataStoreTimerStore(context.timerDataStore)

    val photoPreparer: PhotoPreparer = AndroidPhotoPreparer(context, photoDir)

    val pendingPhotos = PendingPhotos(File(photoDir, "pending"))

    private val okHttpClient = httpClient(AuthInterceptor(session))

    val api: SportApi = createSportApi(baseUrl, okHttpClient)

    private val imageHttpClient = imageClient(okHttpClient)

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient })) }
        .build()

    init {
        // Coil zeigt Bilder aus dem Zwischenspeicher ohne Anfrage an den Server, also ohne Login. Deshalb leert jedes Abmelden ihn
        // und löscht die Fotos auf dem Handy; abgemeldet gibt es kein Formular und keine Details, die sie noch brauchen.
        // Das läuft auch bei jedem Start ohne Anmeldung, ein Fehler dabei darf die App also nicht beenden.
        scope.launch {
            session.state.filter { it == AuthState.LoggedOut }.collect {
                imageLoader.memoryCache?.clear()
                withContext(Dispatchers.IO) {
                    try {
                        imageLoader.diskCache?.clear()
                    } catch (e: Exception) {
                        Log.w("TouchGrass", "Bildzwischenspeicher nicht geleert", e)
                    }
                    photoDir.deleteRecursively()
                    cameraDir(context).deleteRecursively()
                }
            }
        }
    }
}
```
Wartende Fotos liegen in einem Unterordner von `photoDir`. Das Aufräumen aus Task 4 löscht sie beim Abmelden mit, ohne eigene Zeile.

- [ ] **Step 11: Tests ausführen**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest --tests 'de.touchgrass.ui.record.RecordViewModelTest' --tests 'de.touchgrass.ui.activities.ActivityDetailViewModelTest'`
Expected: keine Ausgabe. `TEST-de.touchgrass.ui.record.RecordViewModelTest.xml` meldet `tests="25" skipped="0" failures="0" errors="0"`, `TEST-de.touchgrass.ui.activities.ActivityDetailViewModelTest.xml` meldet `tests="13" skipped="0" failures="0" errors="0"`.

- [ ] **Step 12: Knopf „Foto erneut hochladen“**

`app/src/main/java/de/touchgrass/ui/activities/ActivityDetailScreen.kt` komplett (gegenüber Task 2 neu: `PhotoActions` mit `pending` und `onRetry` in einer `Column` und der Import von `Button`):
```kotlin
package de.touchgrass.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import de.touchgrass.data.dto.ActivityDto
import de.touchgrass.data.photoUrl
import de.touchgrass.ui.UiState
import de.touchgrass.ui.components.LoadingIndicator
import de.touchgrass.ui.photo.PhotoPickers
import de.touchgrass.ui.photo.rememberPhotoPickers

@Composable
fun ActivityDetailScreen(viewModel: ActivityDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickers = rememberPhotoPickers(onPicked = viewModel::onPhotoPicked, onError = viewModel::onPhotoError)
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemovePhoto by remember { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        when (val s = state.activity) {
            UiState.Loading -> LoadingIndicator()
            is UiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::load) { Text("Erneut versuchen") }
            }
            is UiState.Content -> {
                val activity = s.data
                ActivityPhoto(activity)
                Text(state.sports.labelFor(activity.sportCode), style = MaterialTheme.typography.headlineMedium)
                Text(activity.startedAtLabel())
                Text("${activity.durationMin} Minuten")
                Text("~${activity.caloriesKcal} kcal (geschätzt)")
                Text("${activity.points} Punkte")
                Text(activity.visibility.label())
                activity.description?.let { Text("„$it“") }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (activity.mine) {
                    if (state.photoBusy) {
                        CircularProgressIndicator()
                    } else {
                        PhotoActions(
                            hasPhoto = activity.photoVersion != null,
                            pending = state.photoPending,
                            pickers = pickers,
                            onRetry = viewModel::retryPhotoUpload,
                            onRemove = { confirmRemovePhoto = true },
                        )
                    }
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Aktivität löschen") }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Aktivität löschen?") },
            text = { Text("Die Punkte dieser Aktivität verschwinden ebenfalls.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
    if (confirmRemovePhoto) {
        AlertDialog(
            onDismissRequest = { confirmRemovePhoto = false },
            title = { Text("Foto entfernen?") },
            confirmButton = { TextButton(onClick = { confirmRemovePhoto = false; viewModel.removePhoto() }) { Text("Entfernen") } },
            dismissButton = { TextButton(onClick = { confirmRemovePhoto = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
fun ActivityPhoto(activity: ActivityDto, modifier: Modifier = Modifier) {
    val url = activity.photoUrl() ?: return
    AsyncImage(
        model = url,
        contentDescription = "Foto der Aktivität",
        modifier = modifier.fillMaxWidth().aspectRatio(4f / 3f),
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun PhotoActions(hasPhoto: Boolean, pending: Boolean, pickers: PhotoPickers, onRetry: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pending) {
            Button(onClick = onRetry) { Text("Foto erneut hochladen") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { menuOpen = true }) { Text(if (hasPhoto) "Foto ändern" else "Foto hinzufügen") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("🖼️ Galerie") }, onClick = { menuOpen = false; pickers.openGallery() })
                    DropdownMenuItem(text = { Text("📷 Kamera") }, onClick = { menuOpen = false; pickers.openCamera() })
                }
            }
            if (hasPhoto) {
                TextButton(onClick = onRemove) { Text("Foto entfernen") }
            }
        }
    }
}
```
Der Knopf steht in einer eigenen Zeile, weil „Foto erneut hochladen“, „Foto ändern“ und „Foto entfernen“ nebeneinander auf einem schmalen Handy nicht in eine Zeile passen. Er erscheint nur bei eigenen Aktivitäten und nicht, solange der Ladekreis läuft.

- [ ] **Step 13: Build und Lint**

Run: `.\gradlew.bat --no-daemon -q assembleDebug :app:lintDebug`
Expected: keine Ausgabe, Build erfolgreich, Lint ohne Fehler.

- [ ] **Step 14: Alle App-Tests**

Run: `.\gradlew.bat --no-daemon -q :app:testDebugUnitTest`
Expected: 150 Tests grün (141 nach Task 4, dazu 5 in `PendingPhotosTest`, 1 in `RecordViewModelTest` und 3 in `ActivityDetailViewModelTest`), in keiner `TEST-*.xml` stehen `failures` oder `errors` über 0. Je Kategorie aus Task 8 Step 8 des M8-Plans: `A-Daten` 35, `A-Regeln` 37, `A-VM` 78.

- [ ] **Step 15: Projekttagebuch**

In `docs/projektdoku/projekttagebuch.md` diesen Punkt ans Ende des Abschnitts des Arbeitstags setzen:
```markdown
- **Nachbesserung M8, App – Foto bis zum Hochladen:** Scheitert das Hochladen eines Fotos an Netz oder Server, behält die App es unter der Nummer der Aktivität, auch wenn man den Screen verlässt oder Android die App beendet. Die Details der eigenen Aktivität bieten dann „Foto erneut hochladen“ an. Klappt das Hochladen, lehnt der Server das Foto ab oder wird die Aktivität gelöscht, löscht die App die Datei. So geht auch ein Foto aus der Kamera nicht mehr verloren, und die Meldung „Du kannst es in den Details erneut hinzufügen.“ stimmt. Neue Klasse `PendingPhotos` mit eigenen Tests. App 150 Tests.
```

- [ ] **Step 16: Commit**

```bash
git add app/src/main/java/de/touchgrass/data/PendingPhotos.kt app/src/test/java/de/touchgrass/data/PendingPhotosTest.kt app/src/main/java/de/touchgrass/ui/record/RecordViewModel.kt app/src/main/java/de/touchgrass/ui/activities/ActivityDetailViewModel.kt app/src/main/java/de/touchgrass/ui/activities/ActivityDetailScreen.kt app/src/main/java/de/touchgrass/ui/Navigation.kt app/src/main/java/de/touchgrass/AppContainer.kt app/src/test/java/de/touchgrass/ui/record/RecordViewModelTest.kt app/src/test/java/de/touchgrass/ui/activities/ActivityDetailViewModelTest.kt docs/projektdoku/projekttagebuch.md
git commit -m "App: Foto bis zum erfolgreichen Hochladen behalten und in den Details erneut hochladen" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 17: Weiter mit Task 8 des M8-Plans**

Die Nachbesserung ist fertig. Gepusht wird noch nicht: Als Nächstes läuft Task 8 von `docs/superpowers/plans/2026-11-18-m8-fotos.md`. Er vergleicht in Step 8 die Testzahlen mit Backend 140 und App 150.
