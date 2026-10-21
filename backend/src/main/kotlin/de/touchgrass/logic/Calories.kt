package de.touchgrass.logic

import kotlin.math.roundToInt

object Calories {
    const val DEFAULT_WEIGHT_KG = 70.0

    fun estimate(met: Double, weightKg: Double?, durationMin: Int): Int =
        (met * (weightKg ?: DEFAULT_WEIGHT_KG) * durationMin / 60.0).roundToInt()
}
