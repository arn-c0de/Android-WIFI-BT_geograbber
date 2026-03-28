package com.example.wifi_geograbber;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;

import com.example.wifi_geograbber.activities.DatabaseUnlockActivity;
import com.example.wifi_geograbber.activities.MapActivity;
import com.example.wifi_geograbber.activities.main.MainActivityBase;
import com.example.wifi_geograbber.activities.main.database.DatabaseHelper;
import com.example.wifi_geograbber.activities.main.database.DatabaseManager;
import com.example.wifi_geograbber.activities.main.scanning.WiFiScanner;
import com.example.wifi_geograbber.activities.main.ui.UIManager;
import com.example.wifi_geograbber.services.ScanService;
import com.example.wifi_geograbber.utils.BiometricAuthManager;
import com.example.wifi_geograbber.utils.DatabaseEncryptionHelper;
import com.example.wifi_geograbber.utils.EncryptionManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends MainActivityBase {
    private DatabaseManager databaseManager;
    private WiFiScanner wiFiScanner;
    private UIManager uiManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize encryption manager first
        encryptionManager = new EncryptionManager(this);

        // Initialize biometric auth manager
        biometricAuthManager = new BiometricAuthManager(this, encryptionManager);

        // Check if database is encrypted and needs unlocking
        if (encryptionManager.isEncryptionEnabled() && !encryptionManager.isPassphraseCached()) {
            // Database is locked - redirect to unlock activity WITHOUT initializing database
            android.util.Log.d("MainActivity", "onCreate: DB locked, redirecting to unlock WITHOUT database init");
            Intent unlockIntent = new Intent(this, DatabaseUnlockActivity.class);
            unlockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(unlockIntent);
            finish();
            return;
        }
        
        setContentView(com.example.wifi_geograbber.R.layout.activity_main);
        
        // Activate full-screen mode - hide the navigation bar (after setContentView!)
        hideSystemUI();

        // Ensure ScanService is stopped on app start
        stopService(new Intent(this, ScanService.class));

        // Initialize UI components
        statusText = findViewById(com.example.wifi_geograbber.R.id.status_text);
        infoSummary = findViewById(com.example.wifi_geograbber.R.id.info_summary);
        encryptionStatusText = findViewById(com.example.wifi_geograbber.R.id.encryption_status);
        logcatText = findViewById(com.example.wifi_geograbber.R.id.logcat_text);
        logcatScrollView = findViewById(com.example.wifi_geograbber.R.id.logcat_scrollview);
        dataListView = findViewById(com.example.wifi_geograbber.R.id.data_list);
        toggleScanButton = findViewById(com.example.wifi_geograbber.R.id.toggle_scan_button);
        bluetoothToggleButton = findViewById(com.example.wifi_geograbber.R.id.bluetooth_toggle_button);
        showButton = findViewById(com.example.wifi_geograbber.R.id.show_button);
        mapButton = findViewById(com.example.wifi_geograbber.R.id.map_button);
        
        // Create security overlay (black screen to hide content)
        securityOverlay = new View(this);
        securityOverlay.setBackgroundColor(android.graphics.Color.BLACK);
        securityOverlay.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ));
        securityOverlay.setVisibility(View.GONE);
        securityOverlay.setElevation(1000); // Ensure it's on top
        // Add to this activity's content view only (not global DecorView)
        android.widget.FrameLayout contentView = findViewById(android.R.id.content);
        contentView.addView(securityOverlay);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        handler = new Handler();

        // Bluetooth initialization
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Initialize managers
        wiFiScanner = new WiFiScanner(wifiManager, this);
        uiManager = new UIManager(statusText, infoSummary, dataListView, this);

        // Initialize database with encryption support
        initializeDatabase();
        
        // Log database encryption status
        addLogMessage("📱 App started (v" + APP_VERSION + ")");
        addLogMessage("🔐 Database encryption: " + (isDatabaseEncrypted ? "ENABLED (AES-256)" : "DISABLED"));
        if (isDatabaseEncrypted) {
            addLogMessage("🔑 Using system-derived key (PBKDF2-HMAC-SHA256)");
        }
        
        // Log database path for debugging
        if (database != null) {
            try {
                String dbPath;
                if (isDatabaseEncrypted) {
                    dbPath = ((net.sqlcipher.database.SQLiteDatabase) database).getPath();
                } else {
                    dbPath = ((android.database.sqlite.SQLiteDatabase) database).getPath();
                }
                android.util.Log.i("MainActivity", "Database path: " + dbPath);
                addLogMessage("📂 DB Path: " + dbPath);
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Error getting DB path: " + e.getMessage());
            }
        } else {
            android.util.Log.w("MainActivity", "Database is null after initialization!");
            addLogMessage("⚠️ Database is NULL after initialization");
        }

        // Check permissions
        checkPermissions();

        // Button listeners
        showButton.setOnClickListener(v -> {
            if (isShowingStoredData) {
                // Back to live view
                isShowingStoredData = false;
                loadAndDisplayAvailableNetworks();
                showButton.setText("Show Data");
            } else {
                // Show saved data
                isShowingStoredData = true;
                showData();
                showButton.setText("Live View");
            }
        });

        // Map button
        mapButton.setOnClickListener(v -> {
            // Set flag to indicate internal navigation
            isNavigatingInternally = true;
            
            Intent mapIntent = new Intent(MainActivity.this, MapActivity.class);
            startActivity(mapIntent);
        });

        // WiFi Scan receiver
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);

        // Bluetooth Scan Receiver
        IntentFilter bluetoothFilter = new IntentFilter();
        bluetoothFilter.addAction(BluetoothDevice.ACTION_FOUND);
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); 
        registerReceiver(bluetoothScanReceiver, bluetoothFilter);
        
        // Mark receivers as registered
        receiversRegistered = true;

        // Load and display all available networks immediately upon startup.
        loadAndDisplayAvailableNetworks();

        // Initial information summary
        updateInfoSummary(0, 0);

        // Initialize LogCat
        String appName = getString(getApplicationInfo().labelRes);
        addLogMessage("==== " + appName + " v" + APP_VERSION + " ====");
        addLogMessage("App started - LogCat ready");
        addLogMessage("Fullscreen mode enabled - navigation bar hidden");

        // Periodic scan
        scanRunnable = new Runnable() {
            @Override
            public void run() {
                if (isScanning) {
                    wiFiScanner.startWifiScan();
                    if (isBluetoothScanningEnabled) {
                        startBluetoothScan();
                    }
                    handler.postDelayed(this, SCAN_INTERVAL);
                }
            }
        };
    }

    private void initializeDatabase() {
        try {
            DatabaseHelper dbHelper = new DatabaseHelper(this);
            if (encryptionManager.isEncryptionEnabled()) {
                // Use encrypted database
                isDatabaseEncrypted = true;
                String dbKey = encryptionManager.getCachedDatabaseKey();
                encryptedDbHelper = new DatabaseEncryptionHelper(this, dbKey);
                database = encryptedDbHelper.openEncryptedDatabase();
                databaseManager = new DatabaseManager(database, true);
            } else {
                // Use regular database
                isDatabaseEncrypted = false;
                database = dbHelper.getWritableDatabase();
                databaseManager = new DatabaseManager(database, false);
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error initializing database: " + e.getMessage(), e);
            addLogMessage("ERROR: Database initialization failed: " + e.getMessage());
        }
    }

    private void loadAndDisplayAvailableNetworks() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            List<ScanResult> availableNetworks = wiFiScanner.getScanResults();
            if (availableNetworks != null && !availableNetworks.isEmpty()) {
                uiManager.displayResults(availableNetworks);
                statusText.setText("Auto-loaded " + availableNetworks.size() + " networks");
                
                // Save immediately
                Location location = getLocationForSaving();
                saveData(availableNetworks, location);
            } else {
                statusText.setText("No networks currently available");
                // Update information summary even if results are empty.
                updateInfoSummary(0, 0);
                // Try starting an initial scan.
                if (wiFiScanner.isWifiEnabled()) {
                    wiFiScanner.startWifiScan();
                    statusText.setText("Searching for networks...");
                }
            }
        } else {
            statusText.setText("Location permission required for WiFi scanning");
        }
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
        };
        List<String> permissionsNeeded = new ArrayList<>();
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void startScanning() {
        // Check WiFi status
        if (!wiFiScanner.isWifiEnabled()) {
            statusText.setText("WiFi is disabled - please enable!");
            addLogMessage("ERROR: WiFi is disabled");
            Toast.makeText(this, "Please enable WiFi for scanning", Toast.LENGTH_LONG).show();
            return;
        }

        // Check permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            statusText.setText("Location permission required");
            addLogMessage("ERROR: Location permission missing");
            checkPermissions();
            return;
        }
        isScanning = true;

        String scanningInfo = "WiFi scanning active";
        if (isBluetoothScanningEnabled) {
            scanningInfo += " + Bluetooth";
        }
        scanningInfo += " - also in background...";
        statusText.setText(scanningInfo);
        addLogMessage("Scanning started: WiFi" + (isBluetoothScanningEnabled ? " + BT" : ""));
        
        loadAndDisplayAvailableNetworks();
        
        // Start Background Service for continuous scanning
        Intent serviceIntent = new Intent(this, ScanService.class);
        serviceIntent.putExtra("bluetooth_enabled", isBluetoothScanningEnabled);
        startForegroundService(serviceIntent);
        addLogMessage("Background service started");
        
        // Also start local scanning for UI updates
        handler.removeCallbacks(scanRunnable);
        handler.post(scanRunnable);
        startLocationUpdates();
        updateToggleScanButton();
    }

    private void stopScanning() {
        isScanning = false;
        if (handler != null && scanRunnable != null) {
            handler.removeCallbacks(scanRunnable);
        }
        if (statusText != null) {
            statusText.setText("WiFi scanning stopped");
        }
        addLogMessage("Scanning stopped");

        // Stop background service
        Intent serviceIntent = new Intent(this, ScanService.class);
        stopService(serviceIntent);
        addLogMessage("Background service stopped");
        
        // Clear passphrase from cache when scanning stops (if app is in background)
        if (!isInForeground() && encryptionManager != null && encryptionManager.isEncryptionEnabled()) {
            encryptionManager.clearPassphrase();
            if (encryptedDbHelper != null) {
                encryptedDbHelper.clearPassphrase();
            }
            android.util.Log.i("MainActivity", "Passphrase cleared after scan stopped");
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException e) {
                android.util.Log.e("Location", "SecurityException during removeUpdates: " + e.getMessage());
            }
        }
        updateToggleScanButton();
    }

    private void startBluetoothScan() {
        if (!isBluetoothScanningEnabled) {
            return; // Bluetooth scanning is disabled
        }
        
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                try {
                    // Stop previous discovery if running
                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                    // Start new discovery
                    boolean started = bluetoothAdapter.startDiscovery();
                    if (!started) {
                        android.util.Log.w("Bluetooth", "Failed to start Bluetooth discovery");
                    }
                } catch (SecurityException e) {
                    android.util.Log.e("Bluetooth", "SecurityException during Bluetooth scan: " + e.getMessage());
                    addLogMessage("Bluetooth permission missing: " + e.getMessage());
                }
            } else {
                android.util.Log.w("Bluetooth", "BLUETOOTH_SCAN permission not granted");
            }
        } else if (isBluetoothScanningEnabled) {
            android.util.Log.w("Bluetooth", "Bluetooth adapter not available or disabled");
        }
    }

    private Location getLocationForSaving() {
        Location location = null;
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location == null) {
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
            } catch (SecurityException e) {
                android.util.Log.e("Location", "SecurityException getting location: " + e.getMessage());
                addLogMessage("Location access denied: " + e.getMessage());
            }
        }
        
        if (location == null) {
            location = new Location("");
            location.setLatitude(0.0);
            location.setLongitude(0.0);
        }
        return location;
    }

    private void saveData(List<ScanResult> results, Location location) {
        List<String> dataList = new ArrayList<>();
        for (ScanResult result : results) {
            try {
                String capabilities = result.capabilities;
                String encryption = "open";
                if (capabilities != null && !capabilities.equals("") && !capabilities.equals("[]")) {
                    if (capabilities.contains("WEP") || capabilities.contains("WPA") || capabilities.contains("EAP")) {
                        encryption = "encrypted";
                    }
                }

                // Extract extended WiFi data
                int frequency = result.frequency;
                int channel = wiFiScanner.getWiFiChannel(frequency);
                String wifiStandard = wiFiScanner.getWiFiStandard(result);
                String vendorInfo = wiFiScanner.getVendorOUI(result.BSSID);
                int channelWidth = wiFiScanner.getChannelWidth(result);
                int centerFreq0 = 0;
                int centerFreq1 = 0;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    centerFreq0 = result.centerFreq0;
                    centerFreq1 = result.centerFreq1;
                }
                int maxSpeed = wiFiScanner.estimateMaxSpeed(result);

                // Only check the wifi_data table to see if the BSSID already exists.
                Cursor cursor = databaseManager.dbRawQuery("SELECT signal_strength FROM wifi_data WHERE bssid = ?", new String[]{result.BSSID});
                boolean update = false;
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int oldSignal = cursor.getInt(0);
                        if (result.level > oldSignal) {
                            update = true;
                        }
                    }
                    cursor.close();
                }

                // Store WiFi data only in the wifi_data table
                if (update) {
                    databaseManager.dbExecSQL("UPDATE wifi_data SET ssid=?, signal_strength=?, encryption=?, latitude=?, longitude=?, timestamp=?, frequency=?, channel=?, capabilities=?, wifi_standard=?, vendor_info=?, channel_width=?, center_freq0=?, center_freq1=?, max_connection_speed=? WHERE bssid=?",
                            new Object[]{result.SSID, result.level, encryption, location.getLatitude(), location.getLongitude(), System.currentTimeMillis(), frequency, channel, capabilities, wifiStandard, vendorInfo, channelWidth, centerFreq0, centerFreq1, maxSpeed, result.BSSID});
                } else if (!databaseManager.existsInDb(result.BSSID)) {
                    databaseManager.dbExecSQL("INSERT INTO wifi_data (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp, frequency, channel, capabilities, wifi_standard, vendor_info, channel_width, center_freq0, center_freq1, max_connection_speed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            new Object[]{result.SSID, result.BSSID, result.level, encryption, location.getLatitude(), location.getLongitude(), System.currentTimeMillis(), frequency, channel, capabilities, wifiStandard, vendorInfo, channelWidth, centerFreq0, centerFreq1, maxSpeed});
                }

                // Additionally, store the WiFi device information in device_data for motion analysis.
                saveWifiDeviceForMovementTracking(result.SSID, result.BSSID, result.level, encryption, location, frequency, channel, wifiStandard, vendorInfo, channelWidth, maxSpeed);

                String data = "SSID: " + result.SSID +
                        ", BSSID: " + result.BSSID +
                        ", Signal: " + result.level + " dBm" +
                        ", Freq: " + frequency + " MHz" +
                        ", Ch: " + channel +
                        ", Standard: " + wifiStandard +
                        ", " + encryption +
                        ", Speed: " + maxSpeed + " Mbps" +
                        ", Vendor: " + vendorInfo +
                        ", ChWidth: " + channelWidth + " MHz" +
                        ", Lat: " + String.format("%.4f", location.getLatitude()) +
                        ", Lon: " + String.format("%.4f", location.getLongitude());
                dataList.add(data);
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Error saving WiFi network: " + e.getMessage(), e);
            }
        }
        
        // Only show the count if the database is decrypted
        if (isDatabaseEncrypted && database != null) {
            statusText.setText(String.format(getString(com.example.wifi_geograbber.R.string.data_saved), results.size()));
            addLogMessage(String.format(getString(com.example.wifi_geograbber.R.string.data_saved), results.size()));
        } else {
            statusText.setText(String.format(getString(com.example.wifi_geograbber.R.string.data_saved), 0));
            addLogMessage(String.format(getString(com.example.wifi_geograbber.R.string.data_saved), 0));
        }
        // Display current networks directly - only when not in Show Data mode
        if (!isShowingStoredData) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
            dataListView.setAdapter(adapter);
        }
        
        // Broadcast data update for live map refresh
        Intent updateIntent = new Intent("com.example.wifi_geograbber.DATA_UPDATED");
        updateIntent.putExtra("data_type", "wifi");
        updateIntent.putExtra("count", results.size());
        sendBroadcast(updateIntent);
    }

    private void saveWifiDeviceForMovementTracking(String ssid, String bssid, int signal, String encryption, Location location,
                                                  int frequency, int channel, String standard, String vendor, int channelWidth, int maxSpeed) {
        try {
            // Check if the WiFi device already exists in device_data.
            Cursor cursor = databaseManager.dbRawQuery("SELECT signal_strength, latitude, longitude, timestamp FROM device_data WHERE device_address = ? AND device_type = 'WIFI'", new String[]{bssid});
            boolean update = false;
            boolean exists = false;
            double lastLat = 0, lastLon = 0;
            long lastTimestamp = 0;

            if (cursor != null) {
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
            }

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
                        databaseManager.dbExecSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                                "frequency=?, channel=?, wifi_standard=?, vendor_info=?, channel_width=?, max_connection_speed=?, " +
                                "last_seen_latitude=latitude, last_seen_longitude=longitude, last_seen_timestamp=timestamp, " +
                                "latitude=?, longitude=?, timestamp=?, movement_distance=? " +
                                "WHERE device_address=? AND device_type='WIFI'",
                                new Object[]{ssid, signal, encryption, frequency, channel, standard, vendor, channelWidth, maxSpeed,
                                           currentLat, currentLon, currentTimestamp, movementDistance, bssid});
                    } else {
                        databaseManager.dbExecSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                                "frequency=?, channel=?, wifi_standard=?, vendor_info=?, channel_width=?, max_connection_speed=?, " +
                                "latitude=?, longitude=?, timestamp=? " +
                                "WHERE device_address=? AND device_type='WIFI'",
                                new Object[]{ssid, signal, encryption, frequency, channel, standard, vendor, channelWidth, maxSpeed,
                                           currentLat, currentLon, currentTimestamp, bssid});
                    }
                } else {
                    // New entry
                    databaseManager.dbExecSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, " +
                            "frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed, " +
                            "latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            new Object[]{ssid, bssid, "WIFI", signal, encryption, frequency, channel, standard, vendor, channelWidth, maxSpeed,
                                       currentLat, currentLon, currentTimestamp});
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error saving WiFi device for movement tracking: " + e.getMessage(), e);
        }
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

    private void updateToggleScanButton() {
        if (toggleScanButton != null) {
            if (isScanning) {
                toggleScanButton.setText("Stop Scanning");
                toggleScanButton.setBackgroundColor(android.graphics.Color.RED);
            } else {
                toggleScanButton.setText("Start Scanning");
                toggleScanButton.setBackgroundColor(android.graphics.Color.GREEN);
            }
        }
    }

    private void updateBluetoothToggleButton() {
        if (bluetoothToggleButton != null) {
            if (isBluetoothScanningEnabled) {
                bluetoothToggleButton.setText("BT: ON");
                bluetoothToggleButton.setBackgroundColor(android.graphics.Color.BLUE);
            } else {
                bluetoothToggleButton.setText("BT: OFF");
                bluetoothToggleButton.setBackgroundColor(android.graphics.Color.GRAY);
            }
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
            } catch (SecurityException e) {
                android.util.Log.e("Location", "SecurityException during location updates: " + e.getMessage());
                addLogMessage("Location permission missing: " + e.getMessage());
            }
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Saves the current position in the database along with Wi-Fi data.
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
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    List<ScanResult> results = wifiManager.getScanResults();
                    if (results != null && !results.isEmpty()) {
                        if (!isShowingStoredData) {
                            statusText.setText(String.format(getString(com.example.wifi_geograbber.R.string.new_scan_found), results.size()));
                            uiManager.displayResults(results);
                        }
                        addLogMessage(String.format(getString(com.example.wifi_geograbber.R.string.wifi_scan_successful), results.size()));
                        
                        // Save the new results
                        Location location = getLocationForSaving();
                        saveData(results, location);
                    } else {
                        if (!isShowingStoredData) {
                            statusText.setText(com.example.wifi_geograbber.R.string.new_scan_no_networks);
                        }
                        addLogMessage(getString(com.example.wifi_geograbber.R.string.wifi_scan_no_networks));
                    }
                } else {
                    if (!isShowingStoredData) {
                        statusText.setText("Scan complete, but no authorization");
                    }
                }
            } else {
                // Scan failed - use cache anyway
                List<ScanResult> cachedResults = wifiManager.getScanResults();
                if (cachedResults != null && !cachedResults.isEmpty()) {
                    uiManager.displayResults(cachedResults);
                    if (!isShowingStoredData) {
                        statusText.setText(String.format(getString(com.example.wifi_geograbber.R.string.scan_update_failed), cachedResults.size()));
                    }
                    
                    // Save cached results anyway if they are new
                    Location location = getLocationForSaving();
                    saveData(cachedResults, location);
                } else {
                    if (!isShowingStoredData) {
                        statusText.setText("Scan failed - starting a new attempt...");
                    }
                    // Try a new scan immediately
                    if (wiFiScanner.isWifiEnabled()) {
                        try {
                            wifiManager.startScan();
                        } catch (Exception e) {
                            if (!isShowingStoredData) {
                                statusText.setText("WiFi scan blocked - Android limitation");
                            }
                        }
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
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            String deviceName = device.getName();
                            String deviceAddress = device.getAddress();
                            int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                            
                            if (deviceName == null) {
                                deviceName = "Unknown Device";
                            }
                            
                            addLogMessage("Bluetooth found:" + deviceName + " (" + deviceAddress + ") RSSI: " + rssi);
                            
                            // Save Bluetooth device to database
                            Location location = getLocationForSaving();
                            if (location != null) {
                                String deviceClass = "";
                                try {
                                    deviceClass = device.getBluetoothClass().toString();
                                } catch (SecurityException e) {
                                    deviceClass = "Unknown Class";
                                    android.util.Log.w("Bluetooth", "Cannot access device class: " + e.getMessage());
                                }
                                
                                saveBluetoothDevice(deviceName, deviceAddress, rssi, deviceClass, location);
                                // Update info summary
                                if (!isShowingStoredData) {
                                    List<ScanResult> currentWifi = wifiManager.getScanResults();
                                    updateInfoSummary(currentWifi != null ? currentWifi.size() : 0, 1);
                                }
                            }
                            
                            android.util.Log.d("Bluetooth", "Found device: " + deviceName + " (" + deviceAddress + ") RSSI: " + rssi);
                        } catch (SecurityException e) {
                            android.util.Log.e("Bluetooth", "SecurityException accessing Bluetooth device: " + e.getMessage());
                            addLogMessage("Bluetooth access denied: " + e.getMessage());
                        }
                    } else {
                        android.util.Log.w("Bluetooth", "BLUETOOTH_CONNECT permission not granted");
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                android.util.Log.d("Bluetooth", "Bluetooth discovery finished");
            }
        }
    };

    private void saveBluetoothDevice(String deviceName, String deviceAddress, int rssi, String deviceClass, Location location) {
        try {
            // Check if a Bluetooth device already exists and has a better signal strength.
            Cursor cursor = databaseManager.dbRawQuery("SELECT signal_strength, latitude, longitude, timestamp FROM device_data WHERE device_address = ? AND device_type = 'BLUETOOTH'", new String[]{deviceAddress});
            boolean update = false;
            boolean exists = false;
            double lastLat = 0, lastLon = 0;
            long lastTimestamp = 0;

            if (cursor != null) {
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
            }

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
                        databaseManager.dbExecSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                                "last_seen_latitude=latitude, last_seen_longitude=longitude, last_seen_timestamp=timestamp, " +
                                "latitude=?, longitude=?, timestamp=?, movement_distance=? " +
                                "WHERE device_address=? AND device_type='BLUETOOTH'",
                                new Object[]{deviceName, rssi, deviceClass, currentLat, currentLon, currentTimestamp, movementDistance, deviceAddress});
                    } else {
                        databaseManager.dbExecSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, latitude=?, longitude=?, timestamp=? WHERE device_address=? AND device_type='BLUETOOTH'",
                                new Object[]{deviceName, rssi, deviceClass, currentLat, currentLon, currentTimestamp, deviceAddress});
                    }
                } else {
                    // New entry
                    databaseManager.dbExecSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                            new Object[]{deviceName, deviceAddress, "BLUETOOTH", rssi, deviceClass, currentLat, currentLon, currentTimestamp});
                }
                
                // Broadcast data update for live map refresh
                Intent updateIntent = new Intent("com.example.wifi_geograbber.DATA_UPDATED");
                updateIntent.putExtra("data_type", "bluetooth");
                updateIntent.putExtra("count", 1);
                sendBroadcast(updateIntent);
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error saving Bluetooth device: " + e.getMessage(), e);
        }
    }

    private void showData() {
        List<String> dataList = new ArrayList<>();

        // Show both old Wi-Fi data and new unified data
        // Show new device_data data first

        Cursor deviceCursor = databaseManager.dbRawQuery("SELECT * FROM device_data ORDER BY device_type, device_name", null);
        if (deviceCursor != null) {
            if (deviceCursor.moveToFirst()) {
                int nameIdx = deviceCursor.getColumnIndexOrThrow("device_name");
                int addressIdx = deviceCursor.getColumnIndexOrThrow("device_address");
                int typeIdx = deviceCursor.getColumnIndexOrThrow("device_type");
                int signalIdx = deviceCursor.getColumnIndexOrThrow("signal_strength");
                int latIdx = deviceCursor.getColumnIndexOrThrow("latitude");
                int lonIdx = deviceCursor.getColumnIndexOrThrow("longitude");
                int timeIdx = deviceCursor.getColumnIndexOrThrow("timestamp");
                
                // Extended WiFi fields
                int freqIdx = deviceCursor.getColumnIndex("frequency");
                int channelIdx = deviceCursor.getColumnIndex("channel");
                int wifiStandardIdx = deviceCursor.getColumnIndex("wifi_standard");
                int vendorIdx = deviceCursor.getColumnIndex("vendor_info");
                int channelWidthIdx = deviceCursor.getColumnIndex("channel_width");
                int maxSpeedIdx = deviceCursor.getColumnIndex("max_connection_speed");
                
                do {
                    String type = deviceCursor.getString(typeIdx);
                    String data;
                    
                    if ("WIFI".equals(type) && freqIdx >= 0) {
                        // Advanced WiFi display
                        int frequency = freqIdx >= 0 ? deviceCursor.getInt(freqIdx) : 0;
                        int channel = channelIdx >= 0 ? deviceCursor.getInt(channelIdx) : 0;
                        String wifiStandard = wifiStandardIdx >= 0 ? deviceCursor.getString(wifiStandardIdx) : "N/A";
                        String vendor = vendorIdx >= 0 ? deviceCursor.getString(vendorIdx) : "Unknown";
                        int channelWidth = channelWidthIdx >= 0 ? deviceCursor.getInt(channelWidthIdx) : 0;
                        int maxSpeed = maxSpeedIdx >= 0 ? deviceCursor.getInt(maxSpeedIdx) : 0;
                        
                        data = "[WIFI] " + deviceCursor.getString(nameIdx) +
                                ", Addr: " + deviceCursor.getString(addressIdx) +
                                ", Signal: " + deviceCursor.getInt(signalIdx) + " dBm" +
                                ", Freq: " + frequency + " MHz" +
                                ", Ch: " + channel +
                                ", Standard: " + wifiStandard +
                                ", Speed: " + maxSpeed + " Mbps" +
                                ", Vendor: " + vendor +
                                ", ChWidth: " + channelWidth + " MHz" +
                                ", Lat: " + String.format("%.4f", deviceCursor.getDouble(latIdx)) +
                                ", Lon: " + String.format("%.4f", deviceCursor.getDouble(lonIdx));
                    } else {
                        // Standard display for Bluetooth and old WiFi data
                        data = "[" + type + "] " + deviceCursor.getString(nameIdx) +
                                ", Addr: " + deviceCursor.getString(addressIdx) +
                                ", Signal: " + deviceCursor.getInt(signalIdx) +
                                ", Lat: " + String.format("%.4f", deviceCursor.getDouble(latIdx)) +
                                ", Lon: " + String.format("%.4f", deviceCursor.getDouble(lonIdx)) +
                                ", Time: " + deviceCursor.getLong(timeIdx);
                    }
                    dataList.add(data);
                } while (deviceCursor.moveToNext());
            }
            deviceCursor.close();
        }
        
        // If there are still old WiFi data files that have not been migrated
        Cursor wifiCursor = databaseManager.dbRawQuery("SELECT * FROM wifi_data WHERE bssid NOT IN (SELECT device_address FROM device_data WHERE device_type = 'WIFI')", null);
        if (wifiCursor != null) {
            if (wifiCursor.moveToFirst()) {
                int ssidIdx = wifiCursor.getColumnIndexOrThrow("ssid");
                int bssidIdx = wifiCursor.getColumnIndexOrThrow("bssid");
                int signalIdx = wifiCursor.getColumnIndexOrThrow("signal_strength");
                int latIdx = wifiCursor.getColumnIndexOrThrow("latitude");
                int lonIdx = wifiCursor.getColumnIndexOrThrow("longitude");
                int timeIdx = wifiCursor.getColumnIndexOrThrow("timestamp");
                
                // Check if extended fields are available.
                int freqIdx = wifiCursor.getColumnIndex("frequency");
                int channelIdx = wifiCursor.getColumnIndex("channel");
                int wifiStandardIdx = wifiCursor.getColumnIndex("wifi_standard");
                int vendorIdx = wifiCursor.getColumnIndex("vendor_info");
                int channelWidthIdx = wifiCursor.getColumnIndex("channel_width");
                int maxSpeedIdx = wifiCursor.getColumnIndex("max_connection_speed");
                
                do {
                    String data;
                    if (freqIdx >= 0) {
                        // Enhanced WiFi display for migrated legacy data
                        int frequency = wifiCursor.getInt(freqIdx);
                        int channel = channelIdx >= 0 ? wifiCursor.getInt(channelIdx) : 0;
                        String wifiStandard = wifiStandardIdx >= 0 ? wifiCursor.getString(wifiStandardIdx) : "N/A";
                        String vendor = vendorIdx >= 0 ? wifiCursor.getString(vendorIdx) : "Unknown";
                        int channelWidth = channelWidthIdx >= 0 ? wifiCursor.getInt(channelWidthIdx) : 0;
                        int maxSpeed = maxSpeedIdx >= 0 ? wifiCursor.getInt(maxSpeedIdx) : 0;
                        
                        data = "[WIFI-LEGACY] " + wifiCursor.getString(ssidIdx) +
                            ", BSSID: " + wifiCursor.getString(bssidIdx) +
                            ", Signal: " + wifiCursor.getInt(signalIdx) + " dBm" +
                            ", Freq: " + frequency + " MHz" +
                            ", Ch: " + channel +
                            ", Standard: " + wifiStandard +
                            ", Speed: " + maxSpeed + " Mbps" +
                            ", Vendor: " + vendor +
                            ", ChWidth: " + channelWidth + " MHz" +
                            ", Lat: " + String.format("%.4f", wifiCursor.getDouble(latIdx)) +
                            ", Lon: " + String.format("%.4f", wifiCursor.getDouble(lonIdx));
                    } else {
                        // Old display for really old data without extended fields
                        data = "[WIFI-OLD] " + wifiCursor.getString(ssidIdx) +
                                ", BSSID: " + wifiCursor.getString(bssidIdx) +
                                ", Signal: " + wifiCursor.getInt(signalIdx) + " dBm" +
                                ", Lat: " + String.format("%.4f", wifiCursor.getDouble(latIdx)) +
                                ", Lon: " + String.format("%.4f", wifiCursor.getDouble(lonIdx)) +
                                ", Time: " + wifiCursor.getLong(timeIdx);
                    }
                    dataList.add(data);
                } while (wifiCursor.moveToNext());
            }
            wifiCursor.close();
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
        dataListView.setAdapter(adapter);
        
        // Update information summary for Show Data mode
        updateInfoSummary(0, 0); // No active, only total
    }

    private void updateInfoSummary(int activeWifi, int activeBluetooth) {
        // Retrieve total figures from the database
        int totalWifi = 0;
        int totalBluetooth = 0;
        
        // Count WiFi devices in DB
        Cursor wifiCursor = databaseManager.dbRawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'WIFI'", null);
        if (wifiCursor != null) {
            if (wifiCursor.moveToFirst()) {
                totalWifi = wifiCursor.getInt(0);
            }
            wifiCursor.close();
        }
        
        // Count Bluetooth devices in DB
        Cursor bluetoothCursor = databaseManager.dbRawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'BLUETOOTH'", null);
        if (bluetoothCursor != null) {
            if (bluetoothCursor.moveToFirst()) {
                totalBluetooth = bluetoothCursor.getInt(0);
            }
            bluetoothCursor.close();
        }
        
        // Count old WiFi data if available.
        Cursor oldWifiCursor = databaseManager.dbRawQuery("SELECT COUNT(*) FROM wifi_data", null);
        int oldWifiCount = 0;
        if (oldWifiCursor != null) {
            if (oldWifiCursor.moveToFirst()) {
                oldWifiCount = oldWifiCursor.getInt(0);
            }
            oldWifiCursor.close();
        }
        
        String infoText = getString(com.example.wifi_geograbber.R.string.active_label) + activeWifi + getString(com.example.wifi_geograbber.R.string.wifi_label);
        if (isBluetoothScanningEnabled) {
            infoText += ", " + activeBluetooth + getString(com.example.wifi_geograbber.R.string.bt_label);
        } else {
            infoText += getString(com.example.wifi_geograbber.R.string.bt_disabled_label);
        }
        infoText += getString(com.example.wifi_geograbber.R.string.total_label) + totalWifi + getString(com.example.wifi_geograbber.R.string.wifi_label) + ", " + totalBluetooth + getString(com.example.wifi_geograbber.R.string.bt_label);
        if (oldWifiCount > 0) {
            infoText += String.format(getString(com.example.wifi_geograbber.R.string.old_count_label), oldWifiCount);
        }

        infoSummary.setText(infoText);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Reset internal navigation flag when returning to MainActivity
        isNavigatingInternally = false;
        
        // CRITICAL FIX: If database is null (because onCreate skipped initialization when DB was locked),
        // and encryption key is now cached (after successful unlock), reinitialize the database
        if (database == null && encryptionManager != null && 
            encryptionManager.isEncryptionEnabled() && encryptionManager.isPassphraseCached()) {
            android.util.Log.d("MainActivity", "onResume: Database is null but key is cached - reinitializing database");
            initializeDatabase();
            
            // Reload data after database is initialized
            if (database != null) {
                loadAndDisplayAvailableNetworks();
                updateInfoSummary(0, 0);
                android.util.Log.d("MainActivity", "onResume: Database reinitialized successfully");
            } else {
                android.util.Log.e("MainActivity", "onResume: Database reinitialization FAILED");
            }
        }
        
        // Check if database needs to be unlocked
        if (encryptionManager != null && encryptionManager.isEncryptionEnabled() && 
            !encryptionManager.isPassphraseCached() && !isUnlockDialogShowing) {
            
            // Close all open dialogs before showing unlock dialog
            dismissAllDialogs();
            
            // Show overlay immediately
            if (securityOverlay != null) {
                securityOverlay.setVisibility(View.VISIBLE);
                securityOverlay.bringToFront();
            }
            
            // Defer showing unlock dialog to prevent ANR during Activity transition
            // This allows the Activity lifecycle to complete (focus events, window attachment)
            // before showing the blocking dialog
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    isUnlockDialogShowing = true;
                    showUnlockDialog();
                }
            }, 150); // 150ms delay allows Activity transition to complete
        } else if (encryptionManager != null && encryptionManager.isPassphraseCached()) {
            // Hide overlay if passphrase is cached
            if (securityOverlay != null) {
                securityOverlay.setVisibility(View.GONE);
            }
        }
    }

    private void showUnlockDialog() {
        // Implementation would go here - this is just a placeholder
        // to show where the unlock dialog would be shown
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                statusText.setText("Permissions granted - ready to scan");
                // Once authorized, immediately load networks.
                loadAndDisplayAvailableNetworks();
            } else {
                statusText.setText("Permissions denied - cannot scan");
                Toast.makeText(this, "Location permissions required for WiFi scanning", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Unregister receivers
        if (receiversRegistered) {
            try {
                unregisterReceiver(wifiScanReceiver);
                unregisterReceiver(bluetoothScanReceiver);
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Error unregistering receivers: " + e.getMessage());
            }
            receiversRegistered = false;
        }
        
        // Close database
        if (databaseManager != null) {
            databaseManager.dbClose();
        }
    }
}