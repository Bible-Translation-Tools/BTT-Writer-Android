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
}