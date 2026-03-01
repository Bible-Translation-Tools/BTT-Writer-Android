package com.door43.util

/**
 * Created by joel on 5/20/16.
 */
class NumericStringComparator : Comparator<String> {

    override fun compare(lhs: String, rhs: String): Int {
        val num1 = coerceInt(lhs)
        val num2 = coerceInt(rhs)
        return num1 - num2
    }

    private fun coerceInt(value: String): Int {
        // toIntOrNull() safely attempts the parse and returns null if it fails (NumberFormatException).
        // The Elvis operator (?:) then catches the null and defaults it to 0.
        return value.toIntOrNull() ?: 0
    }
}