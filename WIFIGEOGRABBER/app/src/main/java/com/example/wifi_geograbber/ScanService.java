package com.example.wifi_geograbber;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import java.util.List;

public class ScanService extends Service {
    private static final String CHANNEL_ID = "ScanServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final long SCAN_INTERVAL = 5000; // 5 seconds - very frequently
    private static final long MIN_SCAN_INTERVAL = 3000; // Minimum 3 seconds between scans
    
    private WifiManager wifiManager;
    private LocationManager locationManager;
    private BluetoothAdapter bluetoothAdapter;
    private Object database; // Can be android.database.sqlite.SQLiteDatabase or net.sqlcipher.database.SQLiteDatabase
    
    // Database encryption support
    private EncryptionManager encryptionManager;
    private DatabaseEncryptionHelper encryptedDbHelper;
    private boolean isUsingEncryptedDatabase = false; // Track actual database type in use
    
    private Handler handler;
    private Runnable scanRunnable;
    private long lastScanTime = 0;
    private boolean isRunning = false;
    private boolean isBluetoothEnabled = false;
    private int wifiCount = 0;
    private int bluetoothCount = 0;
    private String databasePath = null; // Path to the active database

    // Helper methods for database operations that work with both types
    private Cursor dbRawQuery(String sql, String[] selectionArgs) {
        try {
            if (database == null) {
                Log.w("ScanService", "Database is null in dbRawQuery");
                return null;
            }
            if (isUsingEncryptedDatabase) {
                net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                if (!db.isOpen() || db.isReadOnly()) {
                    Log.w("ScanService", "Encrypted database is not open or is readonly in dbRawQuery");
                    return null;
                }
                return db.rawQuery(sql, selectionArgs);
            } else {
                android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                if (!db.isOpen() || db.isReadOnly()) {
                    Log.w("ScanService", "Database is not open or is readonly in dbRawQuery");
                    return null;
                }
                return db.rawQuery(sql, selectionArgs);
            }
        } catch (Exception e) {
            Log.e("ScanService", "Error in dbRawQuery: " + e.getMessage(), e);
            return null;
        }
    }

    private void dbExecSQL(String sql) {
        try {
            if (database == null) {
                Log.w("ScanService", "Database is null in dbExecSQL");
                return;
            }
            if (isUsingEncryptedDatabase) {
                net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                if (!db.isOpen() || db.isReadOnly()) {
                    Log.w("ScanService", "Encrypted database is not open or is readonly in dbExecSQL");
                    return;
                }
                db.execSQL(sql);
            } else {
                android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                if (!db.isOpen() || db.isReadOnly()) {
                    Log.w("ScanService", "Database is not open or is readonly in dbExecSQL");
                    return;
                }
                db.execSQL(sql);
            }
        } catch (Exception e) {
            Log.e("ScanService", "Error in dbExecSQL: " + e.getMessage(), e);
        }
    }

    private void dbExecSQL(String sql, Object[] bindArgs) {
        try {
            if (database == null) {
                Log.w("ScanService", "Database is null in dbExecSQL with args");
                return;
            }
            if (isUsingEncryptedDatabase) {
                net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                if (!db.isOpen() || db.isReadOnly()) {
                    Log.w("ScanService", "Encrypted database is not open or is readonly in dbExecSQL with args");
                    return;
                }
                db.execSQL(sql, bindArgs);
            } else {
                android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                if (!db.isOpen() || db.isReadOnly()) {
                    Log.w("ScanService", "Database is not open or is readonly in dbExecSQL with args");
                    return;
                }
                db.execSQL(sql, bindArgs);
            }
        } catch (Exception e) {
            Log.e("ScanService", "Error in dbExecSQL with args: " + e.getMessage(), e);
        }
    }

    private void dbClose() {
        try {
            if (database == null) return;
            if (isUsingEncryptedDatabase) {
                ((net.sqlcipher.database.SQLiteDatabase) database).close();
            } else {
                ((android.database.sqlite.SQLiteDatabase) database).close();
            }
        } catch (Exception e) {
            Log.e("ScanService", "Error closing database: " + e.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        // Initialize encryption manager
        encryptionManager = new EncryptionManager(this);
        
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        handler = new Handler();
        
        // Initialize Bluetooth
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        
        // Initialize database
        initializeDatabase();
        
        // Register receiver
        IntentFilter wifiFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wifiScanReceiver, wifiFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(wifiScanReceiver, wifiFilter);
        }
        
        IntentFilter bluetoothFilter = new IntentFilter();
        bluetoothFilter.addAction(BluetoothDevice.ACTION_FOUND);
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothScanReceiver, bluetoothFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bluetoothScanReceiver, bluetoothFilter);
        }
        
        // Scan Runnable
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    performWifiScan();
                    if (isBluetoothEnabled) {
                        performBluetoothScan();
                    }
                    updateNotification();
                    handler.postDelayed(this, SCAN_INTERVAL);
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        // Read Bluetooth status from intent
        if (intent != null) {
            isBluetoothEnabled = intent.getBooleanExtra("bluetooth_enabled", false);
            // Extract database path from intent
            databasePath = intent.getStringExtra("database_path");
        }

        // CRITICAL: startForeground() MUST be called before initializeDatabase()
        // because initializeDatabase() may call stopSelf() if passphrase is not cached
        startForeground(NOTIFICATION_ID, createNotification());

        // Reinitialize database if path has changed
        initializeDatabase();

        // Check if database initialization succeeded
        if (database == null) {
            Log.w("ScanService", "Database not initialized - service will stop");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!isRunning) {
            isRunning = true;
            startLocationUpdates();
            handler.post(scanRunnable);
            Log.d("ScanService", "Background scanning started - Bluetooth: " + isBluetoothEnabled);
        }

    return START_NOT_STICKY; // The service will NOT restart automatically when stopped.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacks(scanRunnable);
        
        try {
            unregisterReceiver(wifiScanReceiver);
            unregisterReceiver(bluetoothScanReceiver);
        } catch (Exception e) {
            Log.e("ScanService", "Error unregistering receivers: " + e.getMessage());
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.removeUpdates(locationListener);
        }
        
        if (database != null) {
            dbClose();
        }
        
        Log.d("ScanService", "Background scanning stopped");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; //We use an unbound service
    }

    /**
     * Delete database file and all associated files
     */
    private void deleteDatabaseFiles() {
        try {
            java.io.File dbFile = getDatabasePath("wifi_scanner.db");

            // Securely delete database and associated files
            boolean deleted = SecureFileDelete.secureDatabaseDelete(dbFile);
            Log.d("ScanService", "Securely deleted database files: " + deleted);

        } catch (Exception e) {
            Log.e("ScanService", "Error deleting database files: " + e.getMessage());
        }
    }

    // Initialize database based on path
    private void initializeDatabase() {
        try {
            // Close old connection
            if (database != null) {
                dbClose();
            }

            // Reset the flag
            isUsingEncryptedDatabase = false;
            database = null; // Reset to null

            // Check if encryption is enabled
            if (encryptionManager.isEncryptionEnabled()) {
                // Use encrypted database
                String dbKey = encryptionManager.getCachedDatabaseKey();
                if (dbKey != null) {
                    encryptedDbHelper = new DatabaseEncryptionHelper(this, dbKey);
                    database = encryptedDbHelper.openEncryptedDatabase();

                    if (database != null) {
                        net.sqlcipher.database.SQLiteDatabase encDb = (net.sqlcipher.database.SQLiteDatabase) database;
                        if (encDb.isOpen() && !encDb.isReadOnly()) {
                            isUsingEncryptedDatabase = true;
                            Log.d("ScanService", "Using encrypted database (writable)");
                        } else {
                            Log.e("ScanService", "Encrypted database opened but is readonly - cannot write data");
                            encDb.close();
                            database = null;
                            // DO NOT disable encryption! User needs to unlock it in MainActivity
                            // Service will be stopped in onStartCommand() when it checks database == null
                            return;
                        }
                    } else {
                        Log.e("ScanService", "Failed to open encrypted database - wrong passphrase or corrupted");
                        database = null;
                        // DO NOT disable encryption! User needs to provide correct passphrase
                        // Service will be stopped in onStartCommand() when it checks database == null
                        return;
                    }
                } else {
                    Log.w("ScanService", "Encryption enabled but passphrase not cached - waiting for user to unlock");
                    // DO NOT disable encryption! User needs to unlock the database first in MainActivity
                    database = null;
                    // Service will be stopped in onStartCommand() when it checks database == null
                    return;
                }
            } else if (databasePath != null && !databasePath.isEmpty()) {
                // Use external database
                database = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE);
                isUsingEncryptedDatabase = false;
                Log.d("ScanService", "Using external database: " + databasePath);
            } else {
                // Use internal database
                MainActivity.DatabaseHelper dbHelper = new MainActivity.DatabaseHelper(this);
                database = dbHelper.getWritableDatabase();
                isUsingEncryptedDatabase = false;
                Log.d("ScanService", "Using internal database");
            }

            // Verify database is valid and writable
            if (database == null) {
                Log.e("ScanService", "Database is null after initialization");
                return;
            }

            // Additional validation: check if database is writable
            boolean isWritable = false;
            if (isUsingEncryptedDatabase) {
                net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                isWritable = db.isOpen() && !db.isReadOnly();
            } else {
                android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                isWritable = db.isOpen() && !db.isReadOnly();
            }

            if (!isWritable) {
                Log.e("ScanService", "Database is readonly - cannot write data");
                database = null;
                return;
            }

            Log.d("ScanService", "Database initialized successfully and is writable");

        } catch (Exception e) {
            Log.e("ScanService", "Error initializing database: " + e.getMessage());
            e.printStackTrace();

            // DO NOT automatically disable encryption or delete database!
            // Set database to null so service will stop in onStartCommand()
            database = null;
        }
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "WiFi & Bluetooth Scanner Service",
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription(getString(R.string.scan_service_description));
        
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        String contentText = "WiFi: " + wifiCount;
        if (isBluetoothEnabled) {
            contentText += ", BT: " + bluetoothCount;
        } else {
            contentText += " (BT aus)";
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WiFi Scanner läuft")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
    
    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification());
    }

    private void performWifiScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Use cached results
            List<ScanResult> cachedResults = wifiManager.getScanResults();
            if (cachedResults != null && !cachedResults.isEmpty()) {
                Location location = getLocationForSaving();
                if (location != null) {
                    saveWifiData(cachedResults, location);
                }
            }
            
            // Only attempt a new scan if enough time has passed.
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastScanTime >= MIN_SCAN_INTERVAL) {
                lastScanTime = currentTime;
                
                if (wifiManager.isWifiEnabled()) {
                    try {
                        wifiManager.startScan();
                        Log.d("ScanService", "WiFi scan started");
                    } catch (SecurityException e) {
                        Log.w("ScanService", "WiFi scan not allowed: " + e.getMessage());
                    }
                }
            }
        }
    }

    // Bluetooth scan control: Start a new scan only after the previous one is complete and after a pause.
    private boolean isBtScanRunning = false;
    private long lastBtScanTime = 0;
    private static final long BT_SCAN_PAUSE = 7000; // 7-second pause between BT scans

    private void performBluetoothScan() {
        if (!isBluetoothEnabled) {
            return; // Bluetooth scanning is disabled.
        }
        if (isBtScanRunning) {
            // Another scan is running, do not restart.
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBtScanTime < BT_SCAN_PAUSE) {
            // Pause, do not restart.
            return;
        }
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                isBtScanRunning = true;
                lastBtScanTime = now;
                // Stop previous discovery if it's running
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
                // Start new discovery
                boolean started = bluetoothAdapter.startDiscovery();
                if (started) {
                    Log.d("ScanService", "Bluetooth discovery started");
                } else {
                    Log.w("ScanService", "Failed to start Bluetooth discovery");
                    isBtScanRunning = false;
                }
            }
        }
    }

    private Location getLocationForSaving() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        
        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (location == null) {
            location = new Location("");
            location.setLatitude(0.0);
            location.setLongitude(0.0);
        }
        return location;
    }
    
    // Calculates the distance between two GPS coordinates in meters (Haversine formula)
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth's radius in kilometers
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c; // Distance in kilometers
        
        return distance * 1000; // Return in meters
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // The location is automatically used when saving.
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override
        public void onProviderEnabled(String provider) {}
        @Override
        public void onProviderDisabled(String provider) {}
    };

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                if (ActivityCompat.checkSelfPermission(ScanService.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    List<ScanResult> results = wifiManager.getScanResults();
                    if (results != null && !results.isEmpty()) {
                        Location location = getLocationForSaving();
                        if (location != null) {
                            saveWifiData(results, location);
                        }
                        Log.d("ScanService", "WiFi scan completed: " + results.size() + " networks");
                    }
                }
            }
        }
    };

    private final BroadcastReceiver bluetoothScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    if (ActivityCompat.checkSelfPermission(ScanService.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        String deviceName = device.getName();
                        String deviceAddress = device.getAddress();
                        int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                        if (deviceName == null) {
                            deviceName = "Unknown Device";
                        }
                        Location location = getLocationForSaving();
                        if (location != null) {
                            saveBluetoothDevice(deviceName, deviceAddress, rssi, device.getBluetoothClass().toString(), location);
                        }
                        Log.d("ScanService", "Bluetooth device found: " + deviceName);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // Scan is complete, please pause.
                isBtScanRunning = false;
            }
        }
    };

    private void saveWifiData(List<ScanResult> results, Location location) {
        // Check if database is available
        if (database == null) {
            Log.e("ScanService", "Cannot save WiFi data: database is null");
            return;
        }
        
        int newCount = 0;
        int updateCount = 0;
        for (ScanResult result : results) {
            try {
                String capabilities = result.capabilities;
                String  encryption = "open";
                if (capabilities != null && !capabilities.equals("") && !capabilities.equals("[]")) {
                    if (capabilities.contains("WEP") || capabilities.contains("WPA") || capabilities.contains("EAP")) {
                        encryption = "encryption";
                    }
                }
                
                // Store WiFi data only in the wifi_data table
                Cursor wifiCursor = dbRawQuery("SELECT signal_strength FROM wifi_data WHERE bssid = ?", new String[]{result.BSSID});
                if (wifiCursor == null) {
                    Log.e("ScanService", "Database query returned null cursor");
                    continue;
                }
                
                boolean wifiExists = false;
                if (wifiCursor.moveToFirst()) {
                    wifiExists = true;
                }
                wifiCursor.close();
                
                if (wifiExists) {
                    // Update existing WiFi network (always update to refresh timestamp and location)
                    dbExecSQL("UPDATE wifi_data SET ssid=?, signal_strength=?, encryption=?, latitude=?, longitude=?, timestamp=? WHERE bssid=?",
                            new Object[]{result.SSID, result.level, encryption, location.getLatitude(), location.getLongitude(), System.currentTimeMillis(), result.BSSID});
                    updateCount++;
                } else {
                    // Insert new WiFi network
                    dbExecSQL("INSERT INTO wifi_data (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            new Object[]{result.SSID, result.BSSID, result.level, encryption, location.getLatitude(), location.getLongitude(), System.currentTimeMillis()});
                    newCount++;
                }
                
                // Additionally, store the WiFi device information in device_data for motion analysis.
                saveWifiDeviceForMovementTracking(result.SSID, result.BSSID, result.level, encryption, location);
            } catch (Exception e) {
                Log.e("ScanService", "Error saving WiFi result: " + e.getMessage(), e);
            }
        }
        
        // Broadcast data update for live map refresh when new or updated networks are detected
        if (newCount > 0 || updateCount > 0) {
            wifiCount += newCount;
            if (newCount > 0) {
                Log.d("ScanService", "Saved " + newCount + " new WiFi networks to wifi_data");
            }
            if (updateCount > 0) {
                Log.d("ScanService", "Updated " + updateCount + " existing WiFi networks in wifi_data");
            }
            
            // Broadcast data update for live map refresh (explicit broadcast for Android 8+)
            Intent updateIntent = new Intent("com.example.wifi_geograbber.DATA_UPDATED");
            updateIntent.setPackage(getPackageName()); // Make explicit for same app
            updateIntent.putExtra("data_type", "wifi");
            updateIntent.putExtra("count", newCount + updateCount);
            sendBroadcast(updateIntent);
            Log.d("ScanService", "Sent broadcast for WiFi data update (total: " + (newCount + updateCount) + ")");
        }
    }
    
    // Save/update WiFi device for motion analysis in device data table
    private void saveWifiDeviceForMovementTracking(String ssid, String bssid, int signal, String encryption, Location location) {
        // Check if database is available
        if (database == null) {
            return;
        }
        
        try {
            // Check if the WiFi device already exists in device_data.
            Cursor cursor = dbRawQuery("SELECT signal_strength, latitude, longitude, timestamp FROM device_data WHERE device_address = ? AND device_type = 'WIFI'", new String[]{bssid});
            if (cursor == null) {
                return;
            }
            
            boolean update = false;
            boolean exists = false;
            double lastLat = 0, lastLon = 0;
            long lastTimestamp = 0;
            
            if (cursor.moveToFirst()) {
                exists = true;
                int oldSignal = cursor.getInt(0);
                lastLat = cursor.getDouble(1);
                lastLon = cursor.getDouble(2);
                lastTimestamp = cursor.getLong(3);
                
                if (signal > oldSignal) {
                    update = true;
                }
            }
            cursor.close();
            
            if (update || !exists) {
                double currentLat = location.getLatitude();
                double currentLon = location.getLongitude();
                long currentTimestamp = System.currentTimeMillis();
                
                // Calculate movement distance if previous position exists
                Double movementDistance = null;
                if (exists && lastLat != 0 && lastLon != 0) {
                    movementDistance = calculateDistance(lastLat, lastLon, currentLat, currentLon);
                }
                
                if (update) {
                    // Update with movement data
                    if (movementDistance != null) {
                        dbExecSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                                "last_seen_latitude=latitude, last_seen_longitude=longitude, last_seen_timestamp=timestamp, " +
                                "latitude=?, longitude=?, timestamp=?, movement_distance=? " +
                                "WHERE device_address=? AND device_type='WIFI'",
                                new Object[]{ssid, signal, encryption, currentLat, currentLon, currentTimestamp, movementDistance, bssid});
                    } else {
                        dbExecSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, latitude=?, longitude=?, timestamp=? WHERE device_address=? AND device_type='WIFI'",
                                new Object[]{ssid, signal, encryption, currentLat, currentLon, currentTimestamp, bssid});
                    }
                } else {
                    // New entry
                    dbExecSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            new Object[]{ssid, bssid, "WIFI", signal, encryption, currentLat, currentLon, currentTimestamp});
                }
            }
        } catch (Exception e) {
            Log.e("ScanService", "Error in saveWifiDeviceForMovementTracking: " + e.getMessage(), e);
        }
    }

    private void saveBluetoothDevice(String deviceName, String deviceAddress, int rssi, String deviceClass, Location location) {
        // Check if database is available
        if (database == null) {
            Log.e("ScanService", "Cannot save Bluetooth device: database is null");
            return;
        }
        
        try {
            Cursor cursor = dbRawQuery("SELECT signal_strength, latitude, longitude, timestamp FROM device_data WHERE device_address = ? AND device_type = 'BLUETOOTH'", new String[]{deviceAddress});
            if (cursor == null) {
                return;
            }
            
            boolean update = false;
            boolean exists = false;
            double lastLat = 0, lastLon = 0;
            long lastTimestamp = 0;
            
            if (cursor.moveToFirst()) {
                exists = true;
                int oldSignal = cursor.getInt(0);
                lastLat = cursor.getDouble(1);
                lastLon = cursor.getDouble(2);
                lastTimestamp = cursor.getLong(3);
                
                if (rssi > oldSignal) {
                    update = true;
                }
            }
            cursor.close();
            
            if (update || !exists) {
                double currentLat = location.getLatitude();
                double currentLon = location.getLongitude();
                long currentTimestamp = System.currentTimeMillis();
                
                // Calculate movement distance if previous position exists
                Double movementDistance = null;
                if (exists && lastLat != 0 && lastLon != 0) {
                    movementDistance = calculateDistance(lastLat, lastLon, currentLat, currentLon);
                }
                
                if (update) {
                    // update with movement data
                    if (movementDistance != null) {
                        dbExecSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                                "last_seen_latitude=latitude, last_seen_longitude=longitude, last_seen_timestamp=timestamp, " +
                                "latitude=?, longitude=?, timestamp=?, movement_distance=? " +
                                "WHERE device_address=? AND device_type='BLUETOOTH'",
                                new Object[]{deviceName, rssi, deviceClass, currentLat, currentLon, currentTimestamp, movementDistance, deviceAddress});
                    } else {
                        dbExecSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, latitude=?, longitude=?, timestamp=? WHERE device_address=? AND device_type='BLUETOOTH'",
                                new Object[]{deviceName, rssi, deviceClass, currentLat, currentLon, currentTimestamp, deviceAddress});
                    }
                } else {
                    // New entry
                    dbExecSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            new Object[]{deviceName, deviceAddress, "BLUETOOTH", rssi, deviceClass, currentLat, currentLon, currentTimestamp});
                    bluetoothCount++;
                    Log.d("ScanService", "Saved new Bluetooth device: " + deviceName);
                    
                    // Broadcast data update for live map refresh (explicit broadcast for Android 8+)
                    Intent updateIntent = new Intent("com.example.wifi_geograbber.DATA_UPDATED");
                    updateIntent.setPackage(getPackageName()); // Make explicit for same app
                    updateIntent.putExtra("data_type", "bluetooth");
                    updateIntent.putExtra("count", 1);
                    sendBroadcast(updateIntent);
                    Log.d("ScanService", "Sent broadcast for Bluetooth data update");
                }
            }
        } catch (Exception e) {
            Log.e("ScanService", "Error in saveBluetoothDevice: " + e.getMessage(), e);
        }
    }
}
