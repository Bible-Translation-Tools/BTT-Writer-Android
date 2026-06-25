package com.door43.util

import java.text.DateFormat
import java.text.DateFormat.MEDIUM
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd_HH.mm.ss"

object DateUtils {

    /**
     * Retrieves the current system date and time formatted as a string.
     *
     * @param format The date pattern string. Defaults to [DEFAULT_DATETIME_FORMAT].
     * @param locale The locale whose symbols should be used. Defaults to [Locale.US].
     * @return The formatted current date-time string.
     */
    fun getCurrentDateTime(
        format: String = DEFAULT_DATETIME_FORMAT,
        locale: Locale = Locale.US
    ): String {
        val sdf = SimpleDateFormat(format, locale)
        return sdf.format(Date())
    }

    /**
     * Formats a [Date] into a localized date-time string using a [DateFormat.MEDIUM] style.
     * * *Example output ([Locale.US]):* `Jan 12, 2025, 3:30:32 PM`
     *
     * @param date The [Date] object to format.
     * @param locale The locale whose formatting rules should be used. Defaults to [Locale.US].
     * @return A medium-styled date-time string.
     */
    fun dateToDateTime(date: Date, locale: Locale = Locale.US): String {
        return DateFormat.getDateTimeInstance(
            MEDIUM,
            MEDIUM,
            locale
        ).format(date)
    }

    /**
     * Parses the given [dateString] into a [Date] object using the specified [format] and [locale].
     *
     * @param dateString A string containing the date or datetime to parse.
     * @param format The date pattern string. Defaults to [DEFAULT_DATETIME_FORMAT].
     * @param locale The locale whose symbols should be used. Defaults to [Locale.US].
     * @return The parsed [Date] object, or `null` if the [dateString] is invalid or doesn't match the format.
     */
    fun parseDateString(
        dateString: String,
        format: String = DEFAULT_DATETIME_FORMAT,
        locale: Locale = Locale.US
    ): Date? {
        return try {
            SimpleDateFormat(format, locale).parse(dateString)
        } catch (_: Exception) {
            null
        }
    }
}