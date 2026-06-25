package com.door43.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

object DateUtils {

    /**
     * Retrieves the current system date and time formatted as a string.
     *
     * @param locale The locale whose symbols should be used. Defaults to Locale.getDefault().
     * @return The formatted current date-time string.
     */
    fun getCurrentDateTime(locale: Locale = Locale.getDefault()): String {
        val formatter = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.MEDIUM,
            locale
        )
        return formatter.format(Date())
    }

    /**
     * Formats a [Date] into a localized date-time string using a [DateFormat.MEDIUM] style.
     *
     * @param date The [Date] object to format.
     * @param locale The locale whose formatting rules should be used. Defaults to Locale.getDefault().
     * @return A medium-styled date-time string.
     */
    fun dateToDateTime(
        date: Date,
        locale: Locale = Locale.getDefault()
    ): String {
        return DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.MEDIUM,
            locale
        ).format(date)
    }

    /**
     * Parses the given [dateString] into a [Date] object using the specified [format] and [locale].
     *
     * @param dateString A string containing the date or datetime to parse.
     * @param locale The locale whose symbols should be used. Defaults to [Locale.US].
     * @return The parsed [Date] object, or `null` if the [dateString] is invalid or doesn't match the format.
     */
    fun parseDateString(
        dateString: String,
        locale: Locale = Locale.getDefault()
    ): Date? {
        return try {
            DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.MEDIUM,
                locale
            ).parse(dateString)
        } catch (_: Exception) {
            null
        }
    }
}