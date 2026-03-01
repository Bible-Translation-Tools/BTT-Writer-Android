package org.unfoldingword.door43client

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * A SQLite database helper
 */
internal class SQLiteHelper(context: Context, private val schema: String, name: String) :
    SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(false)
        schema.split(";").forEach { rawQuery ->
            val query = rawQuery.trim()
            if (query.isNotEmpty()) {
                try {
                    db.execSQL(query)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * TRICKY: this is only supported in API 16+
     */
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(false)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // TRICKY: if this is used to manage upgrades care must be taken to ensure the correct DATABASE_VERSION
        // is set in the db that is packaged with an android app. Otherwise the packaged db may get overwritten.
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }
}