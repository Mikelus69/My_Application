package de.touchgrass.activities

import de.touchgrass.plugins.ApiException
import de.touchgrass.plugins.validationError
import io.ktor.http.HttpStatusCode
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

object PhotoRules {
    const val MAX_BYTES: Int = 204_800
    const val MAX_SIDE: Int = 512

    private val JPEG_START = listOf(0xFF, 0xD8, 0xFF).map { it.toByte() }
    private val JPEG_END = listOf(0xFF, 0xD9).map { it.toByte() }

    fun tooLarge(): Nothing =
        throw ApiException(HttpStatusCode.PayloadTooLarge, "PAYLOAD_TOO_LARGE", "Das Foto ist zu groß.", "photo")

    // Bricht das Hochladen mittendrin ab, kommt der Body bei Ktor wie ein vollständiger an. Ohne FF D9 am Ende fehlt ein Teil des Bildes.
    fun validate(jpeg: ByteArray) {
        if (jpeg.take(JPEG_START.size) != JPEG_START || jpeg.takeLast(JPEG_END.size) != JPEG_END) notJpeg()
        val (width, height) = headerSize(jpeg) ?: notJpeg()
        if (maxOf(width, height) > MAX_SIDE) validationError("photo", "Das Foto darf höchstens 512 Pixel lang sein.")
    }

    // Liest Breite und Höhe nur aus dem Kopf. ImageIO.read würde das Bild entpacken, und ein kleines JPEG mit riesigen Pixelangaben könnte den Speicher sprengen.
    // Jede Exception heißt „kein JPEG“: Bei manchen Farbprofilen (ICC) wirft der Reader statt einer IIOException eine CMMException.
    private fun headerSize(jpeg: ByteArray): Pair<Int, Int>? {
        val readers = ImageIO.getImageReadersByFormatName("jpeg")
        if (!readers.hasNext()) return null
        val reader = readers.next()
        return try {
            MemoryCacheImageInputStream(jpeg.inputStream()).use { input ->
                reader.setInput(input, true, true)
                reader.getWidth(0) to reader.getHeight(0)
            }
        } catch (e: Exception) {
            null
        } finally {
            reader.dispose()
        }
    }

    private fun notJpeg(): Nothing = validationError("photo", "Bitte ein Foto im JPEG-Format schicken.")
}
