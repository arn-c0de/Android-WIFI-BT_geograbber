package com.example.wifi_geograbber;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * DatabaseEncryptionHelper - Manages encrypted SQLite databases using SQLCipher
 * 
 * Features:
 * - Open/create encrypted databases with AES-256
 * - Migrate unencrypted databases to encrypted format
 * - Change database encryption keys
 * - Export/import encrypted databases
 * 
 * SQLCipher Integration:
 * - Transparent encryption/decryption at page level
 * - Compatible with standard SQLite operations
 * - FIPS 140-2 compliant encryption
 */
public class DatabaseEncryptionHelper extends SQLiteOpenHelper {
    private static final String TAG = "DBEncryptionHelper";
    private static final String DATABASE_NAME = "wifi_scanner.db";
    private static final String ENCRYPTED_DB_SUFFIX = "_encrypted";
    private static final int DATABASE_VERSION = 6;
    
    private final Context context;
    private String passphrase;
    
    // SQL table creation statements (same as MainActivity.DatabaseHelper)
    private static final String CREATE_WIFI_TABLE = "CREATE TABLE wifi_data (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "ssid TEXT, " +
            "bssid TEXT, " +
            "signal_strength INTEGER, " +
            "encryption TEXT, " +
            "frequency INTEGER, " +
            "channel INTEGER, " +
            "capabilities TEXT, " +
            "wifi_standard TEXT, " +
            "vendor_info TEXT, " +
            "channel_width TEXT, " +
            "center_freq0 INTEGER, " +
            "center_freq1 INTEGER, " +
            "max_connection_speed INTEGER, " +
            "latitude REAL, " +
            "longitude REAL, " +
            "timestamp INTEGER)";
    
    private static final String CREATE_DEVICE_TABLE = "CREATE TABLE device_data (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "device_name TEXT, " +
            "device_address TEXT, " +
            "device_type TEXT, " +
            "signal_strength INTEGER, " +
            "encryption_info TEXT, " +
            "frequency INTEGER, " +
            "channel INTEGER, " +
            "channel_width TEXT, " +
            "capabilities TEXT, " +
            "center_freq0 INTEGER, " +
            "center_freq1 INTEGER, " +
            "wifi_standard TEXT, " +
            "vendor_info TEXT, " +
            "is_passpoint_network INTEGER, " +
            "operator_friendly_name TEXT, " +
            "venue_name TEXT, " +
            "max_connection_speed INTEGER, " +
            "latitude REAL, " +
            "longitude REAL, " +
            "timestamp INTEGER, " +
            "last_seen_latitude REAL, " +
            "last_seen_longitude REAL, " +
            "last_seen_timestamp INTEGER, " +
            "movement_distance REAL)";
    
    public DatabaseEncryptionHelper(Context context, String passphrase) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
        this.passphrase = passphrase;
        
        // Initialize SQLCipher
        SQLiteDatabase.loadLibs(context);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating encrypted database tables");
        
        // Check if tables already exist (e.g., from migration)
        // This prevents "table already exists" errors
        db.execSQL("CREATE TABLE IF NOT EXISTS wifi_data (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "ssid TEXT, " +
                "bssid TEXT, " +
                "signal_strength INTEGER, " +
                "encryption TEXT, " +
                "frequency INTEGER, " +
                "channel INTEGER, " +
                "capabilities TEXT, " +
                "wifi_standard TEXT, " +
                "vendor_info TEXT, " +
                "channel_width TEXT, " +
                "center_freq0 INTEGER, " +
                "center_freq1 INTEGER, " +
                "max_connection_speed INTEGER, " +
                "latitude REAL, " +
                "longitude REAL, " +
                "timestamp INTEGER)");
        
        db.execSQL("CREATE TABLE IF NOT EXISTS device_data (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "device_name TEXT, " +
                "device_address TEXT, " +
                "device_type TEXT, " +
                "signal_strength INTEGER, " +
                "encryption_info TEXT, " +
                "frequency INTEGER, " +
                "channel INTEGER, " +
                "channel_width TEXT, " +
                "capabilities TEXT, " +
                "center_freq0 INTEGER, " +
                "center_freq1 INTEGER, " +
                "wifi_standard TEXT, " +
                "vendor_info TEXT, " +
                "is_passpoint_network INTEGER, " +
                "operator_friendly_name TEXT, " +
                "venue_name TEXT, " +
                "max_connection_speed INTEGER, " +
                "latitude REAL, " +
                "longitude REAL, " +
                "timestamp INTEGER, " +
                "last_seen_latitude REAL, " +
                "last_seen_longitude REAL, " +
                "last_seen_timestamp INTEGER, " +
                "movement_distance REAL)");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading encrypted database from version " + oldVersion + " to " + newVersion);
        
        // Same upgrade logic as MainActivity.DatabaseHelper
        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS device_data (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "device_name TEXT, " +
                    "device_address TEXT, " +
                    "device_type TEXT, " +
                    "signal_strength INTEGER, " +
                    "encryption_info TEXT, " +
                    "latitude REAL, " +
                    "longitude REAL, " +
                    "timestamp INTEGER)");
            
            db.execSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, latitude, longitude, timestamp) " +
                    "SELECT ssid, bssid, 'WIFI', signal_strength, encryption, latitude, longitude, timestamp FROM wifi_data");
        }
        
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN frequency INTEGER");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN channel INTEGER");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN capabilities TEXT");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN wifi_standard TEXT");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN vendor_info TEXT");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN channel_width TEXT");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN center_freq0 INTEGER");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN center_freq1 INTEGER");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN max_connection_speed INTEGER");
                
                db.execSQL("ALTER TABLE device_data ADD COLUMN frequency INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN channel INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN channel_width TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN capabilities TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN center_freq0 INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN center_freq1 INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN wifi_standard TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN is_passpoint_network INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN operator_friendly_name TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN venue_name TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN max_connection_speed INTEGER");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 4", e);
            }
        }
        
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN vendor_info TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN vendor_info TEXT");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 5", e);
            }
        }
        
        if (oldVersion < 6) {
            try {
                db.execSQL("ALTER TABLE device_data ADD COLUMN last_seen_latitude REAL");
                db.execSQL("ALTER TABLE device_data ADD COLUMN last_seen_longitude REAL");
                db.execSQL("ALTER TABLE device_data ADD COLUMN last_seen_timestamp INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN movement_distance REAL");
            } catch (Exception e) {
                Log.e(TAG, "Error upgrading to version 6", e);
            }
        }
    }
    
    /**
     * Open encrypted database with passphrase
     * Automatically creates a new database if file doesn't exist or is corrupted
     */
    public SQLiteDatabase openEncryptedDatabase() {
        File dbFile = context.getDatabasePath(DATABASE_NAME);
        
        try {
            boolean dbExists = dbFile.exists() && dbFile.length() > 0;
            
            Log.d(TAG, "Opening encrypted database at: " + dbFile.getAbsolutePath());
            Log.d(TAG, "Database file exists: " + dbExists + 
                     (dbExists ? ", size: " + dbFile.length() + " bytes" : ""));
            
            SQLiteDatabase db = getWritableDatabase(passphrase);
            
            if (db != null) {
                // Verify the database is accessible
                try {
                    Cursor cursor = db.rawQuery("SELECT SQLITE_VERSION()", null);
                    if (cursor != null) {
                        cursor.moveToFirst();
                        String version = cursor.getString(0);
                        Log.d(TAG, "Successfully opened encrypted database, SQLite version: " + version);
                        cursor.close();
                    }
                } catch (Exception verifyError) {
                    Log.e(TAG, "Error verifying database after open", verifyError);
                }
            }
            
            return db;
        } catch (Exception e) {
            Log.e(TAG, "Error opening encrypted database: " + e.getMessage(), e);
            
            // If database is corrupted or doesn't exist, delete and create new one
            if (e.getMessage() != null && e.getMessage().contains("file is not a database")) {
                Log.w(TAG, "Database file corrupted or incompatible. Creating new database...");
                
                try {
                    // Delete corrupted database file and associated files
                    if (dbFile.exists()) {
                        Log.d(TAG, "Deleting corrupted database file");
                        dbFile.delete();
                    }
                    
                    // Delete journal files
                    File journalFile = new File(dbFile.getAbsolutePath() + "-journal");
                    if (journalFile.exists()) {
                        journalFile.delete();
                    }
                    
                    File walFile = new File(dbFile.getAbsolutePath() + "-wal");
                    if (walFile.exists()) {
                        walFile.delete();
                    }
                    
                    File shmFile = new File(dbFile.getAbsolutePath() + "-shm");
                    if (shmFile.exists()) {
                        shmFile.delete();
                    }
                    
                    Log.d(TAG, "Creating new encrypted database...");
                    // Try to create new database
                    SQLiteDatabase newDb = getWritableDatabase(passphrase);
                    
                    if (newDb != null) {
                        Log.d(TAG, "New encrypted database created successfully");
                        return newDb;
                    }
                    
                } catch (Exception createError) {
                    Log.e(TAG, "Failed to create new database after corruption", createError);
                }
            }
            
            return null;
        }
    }
    
    /**
     * Check if database is encrypted (SQLCipher database)
     */
    public static boolean isDatabaseEncrypted(File dbFile) {
        if (!dbFile.exists() || dbFile.length() < 16) {
            return false;
        }
        
        try {
            // Read first 16 bytes - SQLCipher databases have encrypted header
            FileInputStream fis = new FileInputStream(dbFile);
            byte[] header = new byte[16];
            int read = fis.read(header);
            fis.close();
            
            if (read != 16) {
                return false;
            }
            
            // Standard SQLite header is "SQLite format 3\0"
            String headerStr = new String(header, "UTF-8");
            boolean isStandardSQLite = headerStr.startsWith("SQLite format 3");
            
            // If it's not standard SQLite, it's probably encrypted
            return !isStandardSQLite;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking database encryption", e);
            return false;
        }
    }
    
    /**
     * Migrate unencrypted database to encrypted format
     * @param unencryptedDbPath Path to unencrypted database
     * @param encryptedDbPath Path for new encrypted database
     * @param passphrase Encryption passphrase
     * @return true if migration successful
     */
    public static boolean migrateToEncrypted(Context context, String unencryptedDbPath, 
                                            String encryptedDbPath, String passphrase) {
        android.database.sqlite.SQLiteDatabase unencryptedDb = null;
        SQLiteDatabase encryptedDb = null;
        
        try {
            Log.i(TAG, "Starting database migration to encrypted format");
            
            // Initialize SQLCipher
            SQLiteDatabase.loadLibs(context);
            
            // Open unencrypted database (standard Android SQLite)
            unencryptedDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                unencryptedDbPath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            
            // Create new encrypted database
            File encryptedFile = new File(encryptedDbPath);
            if (encryptedFile.exists()) {
                encryptedFile.delete();
            }
            
            encryptedDb = SQLiteDatabase.openOrCreateDatabase(
                encryptedDbPath, passphrase, null, null);
            
            // Create tables in encrypted database
            encryptedDb.execSQL(CREATE_WIFI_TABLE);
            encryptedDb.execSQL(CREATE_DEVICE_TABLE);
            
            // Copy wifi_data table
            if (hasTable(unencryptedDb, "wifi_data")) {
                Cursor cursor = unencryptedDb.rawQuery("SELECT * FROM wifi_data", null);
                int wifiCount = copyTableData(cursor, encryptedDb, "wifi_data");
                cursor.close();
                Log.i(TAG, "Migrated " + wifiCount + " WiFi records");
            }
            
            // Copy device_data table
            if (hasTable(unencryptedDb, "device_data")) {
                Cursor cursor = unencryptedDb.rawQuery("SELECT * FROM device_data", null);
                int deviceCount = copyTableData(cursor, encryptedDb, "device_data");
                cursor.close();
                Log.i(TAG, "Migrated " + deviceCount + " device records");
            }
            
            Log.i(TAG, "Database migration completed successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error migrating database", e);
            return false;
        } finally {
            if (unencryptedDb != null) unencryptedDb.close();
            if (encryptedDb != null) encryptedDb.close();
        }
    }
    
    /**
     * Change database encryption passphrase
     * @param dbPath Database path
     * @param oldPassphrase Current passphrase
     * @param newPassphrase New passphrase
     * @return true if successful
     */
    public static boolean changeEncryptionKey(String dbPath, String oldPassphrase, String newPassphrase) {
        SQLiteDatabase db = null;
        try {
            Log.i(TAG, "Changing database encryption key");
            
            // Open with old passphrase
            db = SQLiteDatabase.openDatabase(dbPath, oldPassphrase, null, SQLiteDatabase.OPEN_READWRITE);
            
            // Change key using SQLCipher PRAGMA
            db.rawExecSQL("PRAGMA rekey = '" + newPassphrase.replace("'", "''") + "'");
            
            Log.i(TAG, "Database encryption key changed successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error changing encryption key", e);
            return false;
        } finally {
            if (db != null) db.close();
        }
    }
    
    /**
     * Decrypt database (convert encrypted to unencrypted)
     * WARNING: This removes encryption protection!
     */
    public static boolean decryptDatabase(Context context, String encryptedDbPath, 
                                         String unencryptedDbPath, String passphrase) {
        SQLiteDatabase encryptedDb = null;
        android.database.sqlite.SQLiteDatabase unencryptedDb = null;
        
        try {
            Log.i(TAG, "Decrypting database");
            
            SQLiteDatabase.loadLibs(context);
            
            // Open encrypted database
            encryptedDb = SQLiteDatabase.openDatabase(
                encryptedDbPath, passphrase, null, SQLiteDatabase.OPEN_READONLY);
            
            // Create unencrypted database
            File unencryptedFile = new File(unencryptedDbPath);
            if (unencryptedFile.exists()) {
                unencryptedFile.delete();
            }
            
            unencryptedDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
                unencryptedFile, null);
            
            // Create tables
            unencryptedDb.execSQL(CREATE_WIFI_TABLE);
            unencryptedDb.execSQL(CREATE_DEVICE_TABLE);
            
            // Copy data
            if (hasTable(encryptedDb, "wifi_data")) {
                Cursor cursor = encryptedDb.rawQuery("SELECT * FROM wifi_data", null);
                copyTableDataToStandardSQLite(cursor, unencryptedDb, "wifi_data");
                cursor.close();
            }
            
            if (hasTable(encryptedDb, "device_data")) {
                Cursor cursor = encryptedDb.rawQuery("SELECT * FROM device_data", null);
                copyTableDataToStandardSQLite(cursor, unencryptedDb, "device_data");
                cursor.close();
            }
            
            Log.i(TAG, "Database decrypted successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting database", e);
            return false;
        } finally {
            if (encryptedDb != null) encryptedDb.close();
            if (unencryptedDb != null) unencryptedDb.close();
        }
    }
    
    /**
     * Check if table exists in database
     */
    private static boolean hasTable(SQLiteDatabase db, String tableName) {
        Cursor cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?", 
            new String[]{tableName});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }
    
    private static boolean hasTable(android.database.sqlite.SQLiteDatabase db, String tableName) {
        Cursor cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?", 
            new String[]{tableName});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }
    
    /**
     * Copy table data from cursor to encrypted database
     */
    private static int copyTableData(Cursor cursor, SQLiteDatabase targetDb, String tableName) {
        int count = 0;
        
        if (cursor.moveToFirst()) {
            String[] columnNames = cursor.getColumnNames();
            
            do {
                android.content.ContentValues values = new android.content.ContentValues();
                
                for (String columnName : columnNames) {
                    int columnIndex = cursor.getColumnIndex(columnName);
                    
                    if (cursor.isNull(columnIndex)) {
                        values.putNull(columnName);
                    } else {
                        int type = cursor.getType(columnIndex);
                        switch (type) {
                            case Cursor.FIELD_TYPE_INTEGER:
                                values.put(columnName, cursor.getLong(columnIndex));
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                values.put(columnName, cursor.getDouble(columnIndex));
                                break;
                            case Cursor.FIELD_TYPE_STRING:
                                values.put(columnName, cursor.getString(columnIndex));
                                break;
                            case Cursor.FIELD_TYPE_BLOB:
                                values.put(columnName, cursor.getBlob(columnIndex));
                                break;
                        }
                    }
                }
                
                targetDb.insert(tableName, null, values);
                count++;
                
            } while (cursor.moveToNext());
        }
        
        return count;
    }
    
    /**
     * Copy table data from SQLCipher cursor to standard SQLite database
     */
    private static int copyTableDataToStandardSQLite(Cursor cursor, 
                                                     android.database.sqlite.SQLiteDatabase targetDb, 
                                                     String tableName) {
        int count = 0;
        
        if (cursor.moveToFirst()) {
            String[] columnNames = cursor.getColumnNames();
            
            do {
                android.content.ContentValues values = new android.content.ContentValues();
                
                for (String columnName : columnNames) {
                    int columnIndex = cursor.getColumnIndex(columnName);
                    
                    if (cursor.isNull(columnIndex)) {
                        values.putNull(columnName);
                    } else {
                        int type = cursor.getType(columnIndex);
                        switch (type) {
                            case Cursor.FIELD_TYPE_INTEGER:
                                values.put(columnName, cursor.getLong(columnIndex));
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                values.put(columnName, cursor.getDouble(columnIndex));
                                break;
                            case Cursor.FIELD_TYPE_STRING:
                                values.put(columnName, cursor.getString(columnIndex));
                                break;
                            case Cursor.FIELD_TYPE_BLOB:
                                values.put(columnName, cursor.getBlob(columnIndex));
                                break;
                        }
                    }
                }
                
                targetDb.insert(tableName, null, values);
                count++;
                
            } while (cursor.moveToNext());
        }
        
        return count;
    }
    
    /**
     * Get database file path
     */
    public String getDatabasePath() {
        return context.getDatabasePath(DATABASE_NAME).getAbsolutePath();
    }
    
    /**
     * Clear passphrase from memory
     */
    public void clearPassphrase() {
        if (passphrase != null) {
            passphrase = null;
        }
    }
}
