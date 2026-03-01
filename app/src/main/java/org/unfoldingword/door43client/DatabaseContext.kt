package org.unfoldingword.door43client

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Custom wrapper to provide a custom database path
 * http://stackoverflow.com/questions/5332328/sqliteopenhelper-problem-with-fully-qualified-db-path-name
 */
internal class DatabaseContext(
    base: Context,
    private val dir: File,
    dbExt: String?
) : ContextWrapper(base) {

    private val dbExt: String = if (dbExt.isNullOrEmpty()) "db" else dbExt

    override fun getDatabasePath(name: String): File {
        val dbName = if (!name.endsWith(".${this.dbExt}")) {
            "$name.${this.dbExt}"
        } else {
            name
        }

        val result = File(dir, dbName)

        result.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        return result
    }

    /**
     * for devices greater than or equal to api v11
     */
    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?
    ): SQLiteDatabase {
        return openOrCreateDatabase(name, mode, factory)
    }

    /**
     * For devices less than api v11
     */
    override fun openOrCreateDatabase(
        name: String,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?
    ): SQLiteDatabase {
        return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), null)
    }
}