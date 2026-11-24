// Kotlin port of track-e-reddit/engine/src/dates.ts. Dates are 'YYYY-MM-DD' strings in UTC.
// java.time is available on Android API 26+.
package songbranch.engine

import java.time.LocalDate
import java.time.format.DateTimeParseException

private val ISO_DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

fun isIsoDate(value: String): Boolean {
    if (!ISO_DATE.matches(value)) return false
    return try {
        LocalDate.parse(value).toString() == value
    } catch (e: DateTimeParseException) {
        false
    }
}

fun addDays(date: String, days: Int): String = LocalDate.parse(date).plusDays(days.toLong()).toString()

/** Whole days from a to b (positive when b is later). */
fun daysBetween(a: String, b: String): Int =
    (LocalDate.parse(b).toEpochDay() - LocalDate.parse(a).toEpochDay()).toInt()

/** 0 = Sunday ... 6 = Saturday, like Date.getUTCDay(). */
fun weekday(date: String): Int = LocalDate.parse(date).dayOfWeek.value % 7
