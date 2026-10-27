package de.touchgrass.ui.stats

import de.touchgrass.data.dto.SportDto
import de.touchgrass.data.dto.WeekSummaryDto
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val MONTHS = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")

fun WeekSummaryDto.label(): String =
    "${if (activities == 1) "1 Aktivität" else "$activities Aktivitäten"} · $minutes min · ~$kcal kcal"

fun weekLabel(weekStart: String): String = "KW ${LocalDate.parse(weekStart).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"

fun monthLabel(month: String): String = MONTHS[YearMonth.parse(month).monthValue - 1]

fun List<SportDto>.emojiFor(code: String): String = firstOrNull { it.code == code }?.emoji ?: code

// Abstand der Achsenbeschriftung in runden Schritten (1, 2, 5, 10, 20, 50 …), damit keine Werte wie 0,5 erscheinen.
fun axisStep(max: Int): Double {
    val rough = max / 4.0
    if (rough <= 1) return 1.0
    val magnitude = 10.0.pow(floor(log10(rough)))
    return listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= rough } * magnitude
}
