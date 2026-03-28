package com.example.wifi_geograbber.activities.main.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.wifi_geograbber.utils.DatabaseEncryptionHelper;
import com.example.wifi_geograbber.utils.EncryptionManager;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "wifi_scanner.db";
    private static final int DATABASE_VERSION = 6; // Motion analysis fields
    
    private static final String CREATE_WIFI_TABLE = "CREATE TABLE IF NOT EXISTS wifi_data (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "ssid TEXT, " +
            "bssid TEXT, " +
            "signal_strength INTEGER, " +
            "encryption TEXT, " +
            "frequency INTEGER, " +     // Frequency in MHz
            "channel INTEGER, " +       // WiFi channel
            "capabilities TEXT, " +     // Full Capabilities
            "wifi_standard TEXT, " +    // 802.11a/b/g/n/ac/ax
            "vendor_info TEXT, " +      // Manufacturer information
            "channel_width TEXT, " +    // Channel width
            "center_freq0 INTEGER, " +  // Center Frequency 0
            "center_freq1 INTEGER, " +  // Center Frequency 1
            "max_connection_speed INTEGER, " + // Max connection speed
            "latitude REAL, " +
            "longitude REAL, " +
            "timestamp INTEGER)";

    private static final String CREATE_DEVICE_TABLE = "CREATE TABLE IF NOT EXISTS device_data (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "device_name TEXT, " +
            "device_address TEXT, " +
            "device_type TEXT, " +  // 'WIFI' or 'BLUETOOTH'
            "signal_strength INTEGER, " +
            "encryption_info TEXT, " +  // WiFi encryption or Bluetooth device class
            "frequency INTEGER, " +     // WiFi: Frequency in MHz
            "channel INTEGER, " +       // WiFi: Channel
            "channel_width TEXT, " +    // WiFi: Channel width (20MHz, 40MHz, 80MHz, 160MHz)
            "capabilities TEXT, " +     // WiFi: Full Capabilities
            "center_freq0 INTEGER, " +  // WiFi: Center Frequency 0
            "center_freq1 INTEGER, " +  // WiFi: Center Frequency 1
            "wifi_standard TEXT, " +    // WiFi: Standard (802.11a/b/g/n/ac/ax)
            "vendor_info TEXT, " +       // WiFi: Vendor OUI (first 3 bytes of MAC)
            "is_passpoint_network INTEGER, " + // WiFi: Hotspot 2.0 Support
            "operator_friendly_name TEXT, " +  // WiFi: Operator Name
            "venue_name TEXT, " +       // WiFi: Venue Name
            "max_connection_speed INTEGER, " +  // WiFi: Max Speed
            "latitude REAL, " +
            "longitude REAL, " +
            "timestamp INTEGER, " +
            "last_seen_latitude REAL, " +      // Motion analysis: Last known position
            "last_seen_longitude REAL, " +     // Motion analysis: Last known position
            "last_seen_timestamp INTEGER, " +  // Motion analysis: Timestamp of the last sighting
            "movement_distance REAL)";          // Motion analysis: Distance between first and last position

    public DatabaseHelper(Context context) {
        super(context, DatabaseEncryptionHelper.getDbFile(context, DATABASE_NAME).getAbsolutePath(), null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_WIFI_TABLE);
        db.execSQL(CREATE_DEVICE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            // Migrate data from wifi_data to device_data
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

            // Copy existing WiFi data to device_data table
            db.execSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, latitude, longitude, timestamp) " +
                    "SELECT ssid, bssid, 'WIFI', signal_strength, encryption, latitude, longitude, timestamp FROM wifi_data");
        }
        
        if (oldVersion < 4) {
            // Expand tables to include new WiFi fields.
            try {
                // Extend wifi_data table
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN frequency INTEGER");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN channel INTEGER");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN capabilities TEXT");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN wifi_standard TEXT");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN vendor_info TEXT");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN channel_width TEXT");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN center_freq0 INTEGER");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN center_freq1 INTEGER");
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN max_connection_speed INTEGER");
                
                // Extend device_data table
                db.execSQL("ALTER TABLE device_data ADD COLUMN frequency INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN channel INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN channel_width TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN capabilities TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN center_freq0 INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN center_freq1 INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN wifi_standard TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN vendor_info TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN is_passpoint_network INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN operator_friendly_name TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN venue_name TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN max_connection_speed INTEGER");
            } catch (Exception e) {
                // Fields already exist or other errors
            }
        }
        
        if (oldVersion < 5) {
            // Fix vendor_info column schema consistency
            try {
                // Ensure vendor_info exists in both tables
                db.execSQL("ALTER TABLE wifi_data ADD COLUMN vendor_info TEXT");
                db.execSQL("ALTER TABLE device_data ADD COLUMN vendor_info TEXT");
            } catch (Exception e) {
                // Columns already exist
            }
        }
        
        if (oldVersion < 6) {
            // Add motion analysis fields
            try {
                db.execSQL("ALTER TABLE device_data ADD COLUMN last_seen_latitude REAL");
                db.execSQL("ALTER TABLE device_data ADD COLUMN last_seen_longitude REAL");
                db.execSQL("ALTER TABLE device_data ADD COLUMN last_seen_timestamp INTEGER");
                db.execSQL("ALTER TABLE device_data ADD COLUMN movement_distance REAL");
            } catch (Exception e) {
                // Columns already exist or other error
            }
        }
    }
}