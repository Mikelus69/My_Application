package de.touchgrass.logic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class TimeRange(val from: OffsetDateTime, val until: OffsetDateTime)

object BerlinTime {
    val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

    fun startOfDay(date: LocalDate): OffsetDateTime = date.atStartOfDay(ZONE).toOffsetDateTime()

    fun date(moment: OffsetDateTime): LocalDate = moment.atZoneSameInstant(ZONE).toLocalDate()

    fun weekOf(moment: OffsetDateTime): TimeRange {
        val monday = date(moment).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return TimeRange(startOfDay(monday), startOfDay(monday.plusWeeks(1)))
    }

    fun monthOf(moment: OffsetDateTime): TimeRange {
        val first = date(moment).withDayOfMonth(1)
        return TimeRange(startOfDay(first), startOfDay(first.plusMonths(1)))
    }

    fun format(moment: OffsetDateTime): String =
        moment.atZoneSameInstant(ZONE).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
