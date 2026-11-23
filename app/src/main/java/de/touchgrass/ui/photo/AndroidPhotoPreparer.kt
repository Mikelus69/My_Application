package de.touchgrass.ui.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
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

class AndroidPhotoPreparer(private val context: Context) : PhotoPreparer {
    override suspend fun prepare(source: String): File = withContext(Dispatchers.IO) {
        val uri = source.toUri()
        try {
            val jpeg = decode(uri).withoutGainmap().onWhite().toJpeg()
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            File(dir, "${UUID.randomUUID()}.jpg").apply { writeBytes(jpeg) }
        } catch (e: Exception) {
            throw PhotoException(UNSUPPORTED_PHOTO)
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
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    private fun decodeWithBitmapFactory(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        val sampled = open(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: throw PhotoException(UNSUPPORTED_PHOTO)
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

    private fun Bitmap.onWhite(): Bitmap {
        if (!hasAlpha()) return this
        val opaque = createBitmap(width, height)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@onWhite, 0f, 0f, null)
        }
        return opaque
    }

    private fun Bitmap.toJpeg(): ByteArray {
        var jpeg = ByteArray(0)
        for (quality in JPEG_QUALITIES) {
            jpeg = ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            if (jpeg.size <= MAX_PHOTO_BYTES) break
        }
        return jpeg
    }
}
