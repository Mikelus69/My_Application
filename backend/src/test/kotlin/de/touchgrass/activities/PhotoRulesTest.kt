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
