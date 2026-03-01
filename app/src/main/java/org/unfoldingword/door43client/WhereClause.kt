package org.unfoldingword.door43client

import android.content.ContentValues

/**
 * This is a utility class for preparing a where clause
 */
internal class WhereClause private constructor(
    val statement: String,
    val arguments: Array<String>
) {

    // Required to automatically generate correct equals/hashCode for arrays
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WhereClause

        if (statement != other.statement) return false
        if (!arguments.contentEquals(other.arguments)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = statement.hashCode()
        result = 31 * result + arguments.contentHashCode()
        return result
    }

    companion object {
        /**
         * Performs a bunch of magical operations to convert a set of values and the specified unique columns
         * into a valid where clause with supporting values.
         *
         * @param values
         * @param uniqueColumns
         * @return
         */
        fun prepare(values: ContentValues, uniqueColumns: Array<String>): WhereClause {
            // Split columns into sets by type
            val (stringColumns, numberColumns) = uniqueColumns.partition { key ->
                values.get(key) is String
            }

            // Build the statement parts
            val stringStmt = if (stringColumns.isNotEmpty()) {
                stringColumns.joinToString(separator = "=? and ", postfix = "=?")
            } else ""

            val numberStmt = if (numberColumns.isNotEmpty()) {
                numberColumns.joinToString(separator = " and ") { key ->
                    "$key=${values.get(key)}"
                }
            } else ""

            // Combine the statements
            val whereStmt = when {
                stringStmt.isNotEmpty() && numberStmt.isNotEmpty() -> "$stringStmt and $numberStmt"
                stringStmt.isNotEmpty() -> stringStmt
                else -> numberStmt
            }

            // Build the values array
            val uniqueValues = stringColumns.map { key ->
                values.get(key).toString()
            }.toTypedArray()

            return WhereClause(whereStmt, uniqueValues)
        }
    }
}