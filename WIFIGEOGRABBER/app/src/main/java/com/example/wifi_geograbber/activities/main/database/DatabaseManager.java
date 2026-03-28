package com.example.wifi_geograbber.activities.main.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class DatabaseManager {
    private Object database;
    private boolean isDatabaseEncrypted = false;

    public DatabaseManager(Object database, boolean isDatabaseEncrypted) {
        this.database = database;
        this.isDatabaseEncrypted = isDatabaseEncrypted;
    }

    // Helper methods for database operations that work with both types
    public Cursor dbRawQuery(String sql, String[] selectionArgs) {
        try {
            if (database == null) return null;
            if (isDatabaseEncrypted) {
                return ((net.sqlcipher.database.SQLiteDatabase) database).rawQuery(sql, selectionArgs);
            } else {
                return ((android.database.sqlite.SQLiteDatabase) database).rawQuery(sql, selectionArgs);
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseManager", "Error in dbRawQuery: " + e.getMessage(), e);
            return null;
        }
    }

    public void dbExecSQL(String sql) throws Exception {
        if (database == null) {
            throw new Exception("Database is null");
        }

        try {
            if (isDatabaseEncrypted) {
                net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                if (!db.isOpen()) {
                    throw new Exception("Encrypted database is not open");
                }
                if (db.isReadOnly()) {
                    throw new Exception("Encrypted database is readonly");
                }
                db.execSQL(sql);
            } else {
                android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                if (!db.isOpen()) {
                    throw new Exception("Database is not open");
                }
                if (db.isReadOnly()) {
                    throw new Exception("Database is readonly");
                }
                db.execSQL(sql);
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseManager", "Error in dbExecSQL: " + e.getMessage(), e);
            throw e; // Re-throw exception so caller can handle it
        }
    }

    public void dbExecSQL(String sql, Object[] bindArgs) throws Exception {
        if (database == null) {
            throw new Exception("Database is null in dbExecSQL with args");
        }

        try {
            // Check if database is readonly before attempting write
            if (isDatabaseEncrypted) {
                net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                if (!db.isOpen()) {
                    throw new Exception("Encrypted database is not open in dbExecSQL with args");
                }
                if (db.isReadOnly()) {
                    throw new Exception("Encrypted database is readonly in dbExecSQL with args");
                }
                db.execSQL(sql, bindArgs);
            } else {
                android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                if (!db.isOpen()) {
                    throw new Exception("Database is not open in dbExecSQL with args");
                }
                if (db.isReadOnly()) {
                    throw new Exception("Database is readonly in dbExecSQL with args");
                }
                db.execSQL(sql, bindArgs);
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseManager", "Error in dbExecSQL with args: " + e.getMessage(), e);
            throw e; // Re-throw exception so caller can handle it
        }
    }

    public long dbInsert(String table, String nullColumnHack, android.content.ContentValues values) {
        try {
            if (database == null) {
                android.util.Log.w("DatabaseManager", "Database is null in dbInsert");
                return -1;
            }

            // Check if database is readonly before attempting write
            if (isDatabaseEncrypted) {
                net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                if (!db.isOpen() || db.isReadOnly()) {
                    android.util.Log.w("DatabaseManager", "Encrypted database is not open or readonly in dbInsert");
                    return -1;
                }
                return db.insert(table, nullColumnHack, values);
            } else {
                android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                if (!db.isOpen() || db.isReadOnly()) {
                    android.util.Log.w("DatabaseManager", "Database is not open or readonly in dbInsert");
                    return -1;
                }
                return db.insert(table, nullColumnHack, values);
            }
        } catch (Exception e) {
            android.util.Log.e("DatabaseManager", "Error in dbInsert: " + e.getMessage(), e);
            return -1;
        }
    }

    public void dbBeginTransaction() {
        if (database == null) return;
        if (isDatabaseEncrypted) {
            ((net.sqlcipher.database.SQLiteDatabase) database).beginTransaction();
        } else {
            ((android.database.sqlite.SQLiteDatabase) database).beginTransaction();
        }
    }

    public void dbSetTransactionSuccessful() {
        if (database == null) return;
        if (isDatabaseEncrypted) {
            ((net.sqlcipher.database.SQLiteDatabase) database).setTransactionSuccessful();
        } else {
            ((android.database.sqlite.SQLiteDatabase) database).setTransactionSuccessful();
        }
    }

    public void dbEndTransaction() {
        if (database == null) return;
        if (isDatabaseEncrypted) {
            ((net.sqlcipher.database.SQLiteDatabase) database).endTransaction();
        } else {
            ((android.database.sqlite.SQLiteDatabase) database).endTransaction();
        }
    }

    public boolean dbInTransaction() {
        if (database == null) return false;
        if (isDatabaseEncrypted) {
            return ((net.sqlcipher.database.SQLiteDatabase) database).inTransaction();
        } else {
            return ((android.database.sqlite.SQLiteDatabase) database).inTransaction();
        }
    }

    public void dbClose() {
        if (database == null) return;
        if (isDatabaseEncrypted) {
            ((net.sqlcipher.database.SQLiteDatabase) database).close();
        } else {
            ((android.database.sqlite.SQLiteDatabase) database).close();
        }
    }

    // Auxiliary function: Checks if BSSID is already in the database.
    public boolean existsInDb(String bssid) {
        Cursor cursor = dbRawQuery("SELECT 1 FROM wifi_data WHERE bssid = ? LIMIT 1", new String[]{bssid});
        if (cursor == null) return false;
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    public boolean existsInDeviceDb(String deviceAddress, String deviceType) {
        Cursor cursor = dbRawQuery("SELECT 1 FROM device_data WHERE device_address = ? AND device_type = ? LIMIT 1", new String[]{deviceAddress, deviceType});
        if (cursor == null) return false;
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }
}