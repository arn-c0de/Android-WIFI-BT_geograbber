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
    private static final long SCAN_INTERVAL = 5000; // 5 Sekunden - sehr häufig
    private static final long MIN_SCAN_INTERVAL = 3000; // Minimum 3 Sekunden zwischen Scans
    
    private WifiManager wifiManager;
    private LocationManager locationManager;
    private BluetoothAdapter bluetoothAdapter;
    private SQLiteDatabase database;
    private Handler handler;
    private Runnable scanRunnable;
    private long lastScanTime = 0;
    private boolean isRunning = false;
    private boolean isBluetoothEnabled = false;
    private int wifiCount = 0;
    private int bluetoothCount = 0;
    private String databasePath = null; // Pfad zur aktiven Datenbank

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        handler = new Handler();
        
        // Bluetooth initialisieren
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        
        // Datenbank initialisieren
        initializeDatabase();
        
        // Receiver registrieren
        IntentFilter wifiFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, wifiFilter);
        
        IntentFilter bluetoothFilter = new IntentFilter();
        bluetoothFilter.addAction(BluetoothDevice.ACTION_FOUND);
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetoothScanReceiver, bluetoothFilter);
        
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
        
        // Bluetooth-Status aus Intent auslesen
        if (intent != null) {
            isBluetoothEnabled = intent.getBooleanExtra("bluetooth_enabled", false);
            // Datenbankpfad aus Intent auslesen
            databasePath = intent.getStringExtra("database_path");
        }
        
        // Datenbank neu initialisieren falls Pfad geändert wurde
        initializeDatabase();
        
        startForeground(NOTIFICATION_ID, createNotification());
        
        if (!isRunning) {
            isRunning = true;
            startLocationUpdates();
            handler.post(scanRunnable);
            Log.d("ScanService", "Background scanning started - Bluetooth: " + isBluetoothEnabled);
        }
        
        return START_STICKY; // Service wird automatisch neu gestartet wenn beendet
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
            database.close();
        }
        
        Log.d("ScanService", "Background scanning stopped");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Wir verwenden einen unbound service
    }

    // Datenbank initialisieren basierend auf Pfad
    private void initializeDatabase() {
        try {
            // Alte Verbindung schließen
            if (database != null) {
                database.close();
            }
            
            if (databasePath != null && !databasePath.isEmpty()) {
                // Externe Datenbank verwenden
                database = SQLiteDatabase.openDatabase(databasePath, null, SQLiteDatabase.OPEN_READWRITE);
                Log.d("ScanService", "Using external database: " + databasePath);
            } else {
                // Interne Datenbank verwenden
                MainActivity.DatabaseHelper dbHelper = new MainActivity.DatabaseHelper(this);
                database = dbHelper.getWritableDatabase();
                Log.d("ScanService", "Using internal database");
            }
        } catch (Exception e) {
            Log.e("ScanService", "Error initializing database: " + e.getMessage());
            // Fallback zur internen Datenbank
            MainActivity.DatabaseHelper dbHelper = new MainActivity.DatabaseHelper(this);
            database = dbHelper.getWritableDatabase();
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
            // Verwende Cache-Ergebnisse
            List<ScanResult> cachedResults = wifiManager.getScanResults();
            if (cachedResults != null && !cachedResults.isEmpty()) {
                Location location = getLocationForSaving();
                if (location != null) {
                    saveWifiData(cachedResults, location);
                }
            }
            
            // Versuche neuen Scan nur wenn genug Zeit vergangen ist
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

    // Bluetooth-Scan-Steuerung: Starte neuen Scan erst nach Abschluss des vorherigen und mit Pause
    private boolean isBtScanRunning = false;
    private long lastBtScanTime = 0;
    private static final long BT_SCAN_PAUSE = 7000; // 7 Sekunden Pause zwischen BT-Scans

    private void performBluetoothScan() {
        if (!isBluetoothEnabled) {
            return; // Bluetooth-Scanning ist deaktiviert
        }
        if (isBtScanRunning) {
            // Noch ein Scan läuft, nicht erneut starten
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBtScanTime < BT_SCAN_PAUSE) {
            // Noch Pause, nicht erneut starten
            return;
        }
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                isBtScanRunning = true;
                lastBtScanTime = now;
                // Vorherige Discovery stoppen falls läuft
                if (bluetoothAdapter.isDiscovering()) {
                    bluetoothAdapter.cancelDiscovery();
                }
                // Neue Discovery starten
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
    
    // Berechnet die Distanz zwischen zwei GPS-Koordinaten in Metern (Haversine-Formel)
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Erdradius in Kilometern
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = R * c; // Distanz in Kilometern
        
        return distance * 1000; // Rückgabe in Metern
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Location wird automatisch beim Speichern verwendet
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
                // Scan ist fertig, Pause einhalten
                isBtScanRunning = false;
            }
        }
    };

    private void saveWifiData(List<ScanResult> results, Location location) {
        int newCount = 0;
        for (ScanResult result : results) {
            String capabilities = result.capabilities;
            String verschluesselung = "offen";
            if (capabilities != null && !capabilities.equals("") && !capabilities.equals("[]")) {
                if (capabilities.contains("WEP") || capabilities.contains("WPA") || capabilities.contains("EAP")) {
                    verschluesselung = "verschlüsselt";
                }
            }
            
            // WiFi nur in wifi_data Tabelle speichern
            Cursor wifiCursor = database.rawQuery("SELECT signal_strength FROM wifi_data WHERE bssid = ?", new String[]{result.BSSID});
            boolean wifiUpdate = false;
            boolean wifiExists = false;
            if (wifiCursor.moveToFirst()) {
                wifiExists = true;
                int oldSignal = wifiCursor.getInt(0);
                if (result.level > oldSignal) {
                    wifiUpdate = true;
                }
            }
            wifiCursor.close();
            
            if (wifiUpdate) {
                database.execSQL("UPDATE wifi_data SET ssid=?, signal_strength=?, verschluesselung=?, latitude=?, longitude=?, timestamp=? WHERE bssid=?",
                        new Object[]{result.SSID, result.level, verschluesselung, location.getLatitude(), location.getLongitude(), System.currentTimeMillis(), result.BSSID});
            } else if (!wifiExists) {
                database.execSQL("INSERT INTO wifi_data (ssid, bssid, signal_strength, verschluesselung, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        new Object[]{result.SSID, result.BSSID, result.level, verschluesselung, location.getLatitude(), location.getLongitude(), System.currentTimeMillis()});
                newCount++;
            }
            
            // Zusätzlich WiFi-Gerät auch in device_data speichern für Bewegungsanalyse
            saveWifiDeviceForMovementTracking(result.SSID, result.BSSID, result.level, verschluesselung, location);
        }
        
        if (newCount > 0) {
            wifiCount += newCount;
            Log.d("ScanService", "Saved " + newCount + " new WiFi networks to wifi_data");
        }
    }
    
    // WiFi-Gerät für Bewegungsanalyse in device_data Tabelle speichern/aktualisieren
    private void saveWifiDeviceForMovementTracking(String ssid, String bssid, int signal, String encryption, Location location) {
        // Prüfe, ob WiFi-Gerät in device_data schon existiert
        Cursor cursor = database.rawQuery("SELECT signal_strength, latitude, longitude, timestamp FROM device_data WHERE device_address = ? AND device_type = 'WIFI'", new String[]{bssid});
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
            
            // Berechne Bewegungsdistanz falls vorherige Position vorhanden
            Double movementDistance = null;
            if (exists && lastLat != 0 && lastLon != 0) {
                movementDistance = calculateDistance(lastLat, lastLon, currentLat, currentLon);
            }
            
            if (update) {
                // Update mit Bewegungsdaten
                if (movementDistance != null) {
                    database.execSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                            "last_seen_latitude=latitude, last_seen_longitude=longitude, last_seen_timestamp=timestamp, " +
                            "latitude=?, longitude=?, timestamp=?, movement_distance=? " +
                            "WHERE device_address=? AND device_type='WIFI'",
                            new Object[]{ssid, signal, encryption, currentLat, currentLon, currentTimestamp, movementDistance, bssid});
                } else {
                    database.execSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, latitude=?, longitude=?, timestamp=? WHERE device_address=? AND device_type='WIFI'",
                            new Object[]{ssid, signal, encryption, currentLat, currentLon, currentTimestamp, bssid});
                }
            } else {
                // Neuer Eintrag
                database.execSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        new Object[]{ssid, bssid, "WIFI", signal, encryption, currentLat, currentLon, currentTimestamp});
            }
        }
    }

    private void saveBluetoothDevice(String deviceName, String deviceAddress, int rssi, String deviceClass, Location location) {
        Cursor cursor = database.rawQuery("SELECT signal_strength, latitude, longitude, timestamp FROM device_data WHERE device_address = ? AND device_type = 'BLUETOOTH'", new String[]{deviceAddress});
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
            
            // Berechne Bewegungsdistanz falls vorherige Position vorhanden
            Double movementDistance = null;
            if (exists && lastLat != 0 && lastLon != 0) {
                movementDistance = calculateDistance(lastLat, lastLon, currentLat, currentLon);
            }
            
            if (update) {
                // Update mit Bewegungsdaten
                if (movementDistance != null) {
                    database.execSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                            "last_seen_latitude=latitude, last_seen_longitude=longitude, last_seen_timestamp=timestamp, " +
                            "latitude=?, longitude=?, timestamp=?, movement_distance=? " +
                            "WHERE device_address=? AND device_type='BLUETOOTH'",
                            new Object[]{deviceName, rssi, deviceClass, currentLat, currentLon, currentTimestamp, movementDistance, deviceAddress});
                } else {
                    database.execSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, latitude=?, longitude=?, timestamp=? WHERE device_address=? AND device_type='BLUETOOTH'",
                            new Object[]{deviceName, rssi, deviceClass, currentLat, currentLon, currentTimestamp, deviceAddress});
                }
            } else {
                // Neuer Eintrag
                database.execSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        new Object[]{deviceName, deviceAddress, "BLUETOOTH", rssi, deviceClass, currentLat, currentLon, currentTimestamp});
                bluetoothCount++;
                Log.d("ScanService", "Saved new Bluetooth device: " + deviceName);
            }
        }
    }
}
