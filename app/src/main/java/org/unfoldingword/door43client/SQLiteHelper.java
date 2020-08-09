package org.unfoldingword.door43client;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

import java.io.File;

/**
 * A SQLite database helper
 */
class SQLiteHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    private final String schema;

    /**
     *
     * @param context
     * @param schema The sqlite schema
     * @param name the db name
     */
    public SQLiteHelper(Context context, String schema, String name) {
        super(context, name, null, DATABASE_VERSION);
        this.schema = schema;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            db.execSQL("PRAGMA foreign_keys=OFF;");
        } else {
            db.setForeignKeyConstraintsEnabled(false);
        }
        String[] queries = schema.split(";");
        for (String query : queries) {
            query = query.trim();
            if(!query.isEmpty()) {
                try {
                    db.execSQL(query);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * TRICKY: this is only supported in API 16+
     * @param db
     */
    @Override
    public void onConfigure(SQLiteDatabase db) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            db.setForeignKeyConstraintsEnabled(false);
        } else {
            db.execSQL("PRAGMA foreign_keys=OFF;");
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TRICKY: if this is used to manage upgrades care must be taken to ensure the correct DATABASE_VERSION
        // is set in the db that is packaged with an android app. Otherwise the packaged db may get overwritten.
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            db.setForeignKeyConstraintsEnabled(true);
        } else {
            db.execSQL("PRAGMA foreign_keys=ON;");
        }
    }
}
