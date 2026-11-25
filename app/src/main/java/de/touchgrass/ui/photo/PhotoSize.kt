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
