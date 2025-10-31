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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    /**
     * =============================
     *   CODE VERSION MARKER
     *   APP_VERSION: 1.0.1
     * =============================
     * Use this variable to visually distinguish code versions.
     */
    public static final String APP_VERSION = "1.0.1";
    private WifiManager wifiManager;
    private LocationManager locationManager;
    private BluetoothAdapter bluetoothAdapter;
    private SQLiteDatabase database;
    private TextView statusText;
    private TextView infoSummary;
    private TextView logcatText;
    private ScrollView logcatScrollView;
    private ListView dataListView;
    private Button toggleScanButton, showButton, moreButton, bluetoothToggleButton, mapButton;
    private Handler handler;
    private Runnable scanRunnable;
    private boolean isScanning = false;
    private boolean isBluetoothScanningEnabled = false;
    private boolean isShowingStoredData = false;
    private boolean isUsingExternalDatabase = false;
    private String currentDatabasePath = null;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int EXPORT_DB_REQUEST_CODE = 101;
    private static final int IMPORT_DB_REQUEST_CODE = 102;
    private static final int IMPORT_ACTIVE_DB_REQUEST_CODE = 103;
    private static final long SCAN_INTERVAL = 5000; // 5 seconds - very frequently
    private static final long MIN_SCAN_INTERVAL = 3000; // Minimum 3 seconds between scans
    private long lastScanTime = 0;
    private StringBuilder logBuffer = new StringBuilder();


        

    // Hide system UI (navigation bar)
    private void hideSystemUI() {
        try {
            // For Android 11+ (API 30+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                if (getWindow().getInsetsController() != null) {
                    getWindow().getInsetsController().hide(android.view.WindowInsets.Type.navigationBars());
                    getWindow().getInsetsController().setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                }
            } else {
                // For older Android-Versionen
                View decorView = getWindow().getDecorView();
                int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
                decorView.setSystemUiVisibility(uiOptions);
            }
            
            // Always leave the screen on
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            Log.e("MainActivity", "Error hiding system UI: " + e.getMessage());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Activate full-screen mode - hide the navigation bar (after setContentView!)
        hideSystemUI();

        // initialization
        statusText = findViewById(R.id.status_text);
        infoSummary = findViewById(R.id.info_summary);
        logcatText = findViewById(R.id.logcat_text);
        logcatScrollView = findViewById(R.id.logcat_scrollview);
        dataListView = findViewById(R.id.data_list);
        toggleScanButton = findViewById(R.id.toggle_scan_button);
        bluetoothToggleButton = findViewById(R.id.bluetooth_toggle_button);
        showButton = findViewById(R.id.show_button);
        mapButton = findViewById(R.id.map_button);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        handler = new Handler();

        // Bluetooth initialization
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // New button for further actions
        moreButton = findViewById(R.id.more_button);
        moreButton.setOnClickListener(v -> showMoreDialog());

        // Toggle button for active scanning
        toggleScanButton.setOnClickListener(v -> {
            if (isScanning) {
                stopScanning();
            } else {
                startScanning();
            }
            updateToggleScanButton();
        });
        updateToggleScanButton();

        // Bluetooth Toggle Button
        bluetoothToggleButton.setOnClickListener(v -> {
            isBluetoothScanningEnabled = !isBluetoothScanningEnabled;
            updateBluetoothToggleButton();
            if (isBluetoothScanningEnabled) {
                Toast.makeText(this, "Bluetooth scanning enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth scanning disabled", Toast.LENGTH_SHORT).show();
            }
        });
        updateBluetoothToggleButton();

        // Check location services and display dialog if necessary.
        checkLocationServicesEnabled();

        // Initialize database
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        database = dbHelper.getWritableDatabase();

        // Check permissions
        checkPermissions();

        // Button-Listener
        showButton.setOnClickListener(v -> {
            if (isShowingStoredData) {
                // // Back to live view
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
            Intent mapIntent = new Intent(MainActivity.this, MapActivity.class);
            
            // We now always use the internal database for the map
            // since all external data has already been transferred to the internal database
            
            startActivity(mapIntent);
        });

        // WiFi Scan receiver
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);

        // Bluetooth-Scan Receiver
        IntentFilter bluetoothFilter = new IntentFilter();
        bluetoothFilter.addAction(BluetoothDevice.ACTION_FOUND);
        bluetoothFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); 
        registerReceiver(bluetoothScanReceiver, bluetoothFilter);

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
                    startWifiScan();
                    if (isBluetoothScanningEnabled) {
                        startBluetoothScan();
                    }
                    handler.postDelayed(this, SCAN_INTERVAL);
                }
            }
        };
    }

    // LogCat functions
    private void addLogMessage(String message) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        String logEntry = timestamp + ": " + message + "\n";

        // If the log is empty, insert version information and app name.
        if (logBuffer.length() == 0) {
            String appName = getString(getApplicationInfo().labelRes);
            logBuffer.append("==== " + appName + " v" + APP_VERSION + " ====" + "\n");
        }

        logBuffer.append(logEntry);

        // Limit to last 50 lines
        String[] lines = logBuffer.toString().split("\n");
        if (lines.length > 50) {
            logBuffer = new StringBuilder();
            for (int i = lines.length - 50; i < lines.length; i++) {
                if (i >= 0 && !lines[i].isEmpty()) {
                    logBuffer.append(lines[i]).append("\n");
                }
            }
        }

        // UI Update
        runOnUiThread(() -> {
            logcatText.setText(logBuffer.toString());
            // Auto-scroll to the end
            logcatScrollView.post(() -> logcatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
    
    // Auxiliary functions for advanced WiFi data extraction
    private String getWiFiStandard(ScanResult result) {
        // Determine WiFi standard based on frequency and capabilities
        String capabilities = result.capabilities;
        int frequency = result.frequency;
        
        if (capabilities.contains("HE")) return "802.11ax (WiFi 6)";
        if (capabilities.contains("VHT")) return "802.11ac (WiFi 5)";
        if (capabilities.contains("HT")) return "802.11n (WiFi 4)";
        
        if (frequency > 5000) return "802.11a";
        if (frequency < 3000 && capabilities.contains("ERP")) return "802.11g";
        if (frequency < 3000) return "802.11b";
        
        return "Unknown";
    }
    
    private int getChannelWidth(ScanResult result) {
        String capabilities = result.capabilities;
        if (capabilities.contains("HE")) {
            if (capabilities.contains("160")) return 160;
            if (capabilities.contains("80")) return 80;
            if (capabilities.contains("40")) return 40;
        }
        if (capabilities.contains("VHT")) {
            if (capabilities.contains("160")) return 160;
            if (capabilities.contains("80")) return 80;
            if (capabilities.contains("40")) return 40;
        }
        if (capabilities.contains("HT40")) return 40;
        if (capabilities.contains("HT")) return 40;
        return 20;
    }
    
    private String getVendorOUI(String bssid) {
        if (bssid != null && bssid.length() >= 8) {
            String oui = bssid.substring(0, 8).toUpperCase();
            return "OUI-" + oui.substring(0, 6); // Simplified OUI output for DB
        }
        return "Unknown";
    }
    
    private int getWiFiChannel(int frequency) {
        // 2.4 GHz Band
        if (frequency >= 2412 && frequency <= 2484) {
            if (frequency == 2484) return 14;
            return (frequency - 2412) / 5 + 1;
        }
        // 5 GHz Band
        if (frequency >= 5000 && frequency <= 6000) {
            return (frequency - 5000) / 5;
        }
        // 6 GHz Band (WiFi 6E)
        if (frequency >= 5945 && frequency <= 7125) {
            return (frequency - 5945) / 5;
        }
        return 0;
    }
    
    private int estimateMaxSpeed(ScanResult result) {
        String capabilities = result.capabilities;
        String standard = getWiFiStandard(result);
        int channelWidth = getChannelWidth(result);
        
        // Estimated maximum throughput based on standard and channel width
        if (standard.contains("802.11ax")) {
            if (channelWidth == 160) return 2400; // Mbps
            if (channelWidth == 80) return 1200;
            if (channelWidth == 40) return 600;
            return 300;
        }
        if (standard.contains("802.11ac")) {
            if (channelWidth == 160) return 1733;
            if (channelWidth == 80) return 867;
            if (channelWidth == 40) return 433;
            return 217;
        }
        if (standard.contains("802.11n")) {
            if (channelWidth == 40) return 300;
            return 150;
        }
        if (standard.contains("802.11g")) return 54;
        if (standard.contains("802.11a")) return 54;
        if (standard.contains("802.11b")) return 11;
        
        return 0;
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

    private void loadAndDisplayAvailableNetworks() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            List<ScanResult> availableNetworks = wifiManager.getScanResults();
            if (availableNetworks != null && !availableNetworks.isEmpty()) {
                displayResults(availableNetworks);
                statusText.setText("Auto-loaded " + availableNetworks.size() + " networks");
                
                //Save immediately
                Location location = getLocationForSaving();
                saveData(availableNetworks, location);
            } else {
                statusText.setText("No networks currently available");
                // Update information summary even if results are empty.
                updateInfoSummary(0, 0);
                // Try starting an initial scan.
                if (wifiManager.isWifiEnabled()) {
                    wifiManager.startScan();
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
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void checkLocationServicesEnabled() {
        boolean gpsEnabled = false;
        boolean networkEnabled = false;
        try {
            gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {}
        try {
            networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {}
        if (!gpsEnabled && !networkEnabled) {
            new android.app.AlertDialog.Builder(this)
                    .setMessage(R.string.location_services_disabled)
                    .setCancelable(false)
                    .setPositiveButton(R.string.settings, (dialog, which) -> {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
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

    private void startScanning() {
        // Check WiFi status
        if (!wifiManager.isWifiEnabled()) {
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
        
        // Database path passed on to service
        if (isUsingExternalDatabase && currentDatabasePath != null) {
            serviceIntent.putExtra("database_path", currentDatabasePath);
        }
        
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
        handler.removeCallbacks(scanRunnable);
    statusText.setText("WiFi scanning stopped");
    addLogMessage("Scanning stopped");

    // Stop background service
    Intent serviceIntent = new Intent(this, ScanService.class);
    stopService(serviceIntent);
    addLogMessage("Background service stopped");
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException e) {
                Log.e("Location", "SecurityException during removeUpdates: " + e.getMessage());
            }
        }
        updateToggleScanButton();
    }

    private void startWifiScan() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Always retrieve and display existing results first.
            List<ScanResult> cachedResults = wifiManager.getScanResults();
            if (cachedResults != null && !cachedResults.isEmpty()) {
                displayResults(cachedResults);
                if (!isShowingStoredData) {
                    statusText.setText(String.format(getString(R.string.current_networks_updating), cachedResults.size()));
                }
                
                // Also save these results
                Location location = getLocationForSaving();
                if (location != null) {
                    saveData(cachedResults, location);
                }
            }
            
            // Only attempt a new scan if enough time has passed.
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastScanTime >= MIN_SCAN_INTERVAL) {
                lastScanTime = currentTime;
                
                try {
                    boolean scanStarted = wifiManager.startScan();
                    if (scanStarted) {
                        if (!isShowingStoredData) {
                            statusText.setText(String.format(getString(R.string.active_scan_running), (cachedResults != null ? cachedResults.size() : 0)));
                        }
                        addLogMessage("WiFi scan started successfully");
                        Log.d("MainActivity", "WiFi scan started successfully");
                    } else {
                        if (!isShowingStoredData) {
                            statusText.setText("Scan start failed - Android limitation (" + (cachedResults != null ? cachedResults.size() : 0) + " in cache)");
                        }
                        addLogMessage("WiFi scan failed - Android limitation");
                    }
                } catch (SecurityException e) {
                    if (!isShowingStoredData) {
                        statusText.setText("Scan not allowed - using cache (" + (cachedResults != null ? cachedResults.size() : 0) + ")");
                    }
                    addLogMessage("WiFi scan blocked - Security Exception");
                }
            } else {
                long waitTime = (MIN_SCAN_INTERVAL - (currentTime - lastScanTime)) / 1000;
                if (!isShowingStoredData) {
                    statusText.setText("Scan cooldown active - waiting " + waitTime + "s (Cache: " + (cachedResults != null ? cachedResults.size() : 0) + ")");
                }
            }
        } else {
            statusText.setText("No location permission");
        }
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
                        Log.w("Bluetooth", "Failed to start Bluetooth discovery");
                    }
                } catch (SecurityException e) {
                    Log.e("Bluetooth", "SecurityException during Bluetooth scan: " + e.getMessage());
                    addLogMessage("Bluetooth permission missing: " + e.getMessage());
                }
            } else {
                Log.w("Bluetooth", "BLUETOOTH_SCAN permission not granted");
            }
        } else if (isBluetoothScanningEnabled) {
            Log.w("Bluetooth", "Bluetooth adapter not available or disabled");
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
                Log.e("Location", "SecurityException getting location: " + e.getMessage());
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
    
    private void displayResults(List<ScanResult> results) {
        // Only update when not in "Show Data" mode
        if (isShowingStoredData) {
            return;
        }
        
        List<String> dataList = new ArrayList<>();
        for (ScanResult result : results) {
            String ssid = result.SSID;
            if (ssid == null || ssid.isEmpty()) {
                ssid = "[Hidden Network]";
            }
            String capabilities = result.capabilities;
            String encrypted = "open";
            if (capabilities != null && !capabilities.isEmpty() && !capabilities.equals("[]")) {
                if (capabilities.contains("WEP") || capabilities.contains("WPA") || capabilities.contains("EAP")) {
                    encrypted = "encrypted";
                }
            }
            String data = "SSID: " + ssid +
                    ", Signal: " + result.level + " dBm" +
                    ", Freq: " + result.frequency + " MHz" +
                    ", Status: " + encrypted;
            dataList.add(data);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
        dataListView.setAdapter(adapter);
        
        // Update information summary
        updateInfoSummary(results.size(), 0); // WiFi active, BT active
    }

    // Update information summary
    private void updateInfoSummary(int activeWifi, int activeBluetooth) {
        // Retrieve total figures from the database
        int totalWifi = 0;
        int totalBluetooth = 0;
        
        // Count WiFi devices in DB
        Cursor wifiCursor = database.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'WIFI'", null);
        if (wifiCursor.moveToFirst()) {
            totalWifi = wifiCursor.getInt(0);
        }
        wifiCursor.close();
        
        // Count Bluetooth devices in DB
        Cursor bluetoothCursor = database.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'BLUETOOTH'", null);
        if (bluetoothCursor.moveToFirst()) {
            totalBluetooth = bluetoothCursor.getInt(0);
        }
        bluetoothCursor.close();
        
        // Count old WiFi data if available.
        Cursor oldWifiCursor = database.rawQuery("SELECT COUNT(*) FROM wifi_data", null);
        int oldWifiCount = 0;
        if (oldWifiCursor.moveToFirst()) {
            oldWifiCount = oldWifiCursor.getInt(0);
        }
        oldWifiCursor.close();
        
        String infoText = getString(R.string.active_label) + activeWifi + getString(R.string.wifi_label);
        if (isBluetoothScanningEnabled) {
            infoText += ", " + activeBluetooth + getString(R.string.bt_label);
        } else {
            infoText += getString(R.string.bt_disabled_label);
        }
        infoText += getString(R.string.total_label) + totalWifi + getString(R.string.wifi_label) + ", " + totalBluetooth + getString(R.string.bt_label);
        if (oldWifiCount > 0) {
            infoText += String.format(getString(R.string.old_count_label), oldWifiCount);
        }

        infoSummary.setText(infoText);
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
            } catch (SecurityException e) {
                Log.e("Location", "SecurityException during location updates: " + e.getMessage());
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
                            statusText.setText(String.format(getString(R.string.new_scan_found), results.size()));
                            displayResults(results);
                        }
                        addLogMessage(String.format(getString(R.string.wifi_scan_successful), results.size()));
                        
                        // Save the new results
                        Location location = getLocationForSaving();
                        saveData(results, location);
                    } else {
                        if (!isShowingStoredData) {
                            statusText.setText(R.string.new_scan_no_networks);
                        }
                        addLogMessage(getString(R.string.wifi_scan_no_networks));
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
                    displayResults(cachedResults);
                    if (!isShowingStoredData) {
                        statusText.setText(String.format(getString(R.string.scan_update_failed), cachedResults.size()));
                    }
                    
                    // Save cached results anyway if they are new
                    Location location = getLocationForSaving();
                    saveData(cachedResults, location);
                } else {
                    if (!isShowingStoredData) {
                        statusText.setText("Scan failed - starting a new attempt...");
                    }
                    // Try a new scan immediately
                    if (wifiManager.isWifiEnabled()) {
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
                                    Log.w("Bluetooth", "Cannot access device class: " + e.getMessage());
                                }
                                
                                saveBluetoothDevice(deviceName, deviceAddress, rssi, deviceClass, location);
                                // Update info summary
                                if (!isShowingStoredData) {
                                    List<ScanResult> currentWifi = wifiManager.getScanResults();
                                    updateInfoSummary(currentWifi != null ? currentWifi.size() : 0, 1);
                                }
                            }
                            
                            Log.d("Bluetooth", "Found device: " + deviceName + " (" + deviceAddress + ") RSSI: " + rssi);
                        } catch (SecurityException e) {
                            Log.e("Bluetooth", "SecurityException accessing Bluetooth device: " + e.getMessage());
                            addLogMessage("Bluetooth access denied: " + e.getMessage());
                        }
                    } else {
                        Log.w("Bluetooth", "BLUETOOTH_CONNECT permission not granted");
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d("Bluetooth", "Bluetooth discovery finished");
            }
        }
    };

    private void saveData(List<ScanResult> results, Location location) {
        List<String> dataList = new ArrayList<>();
        for (ScanResult result : results) {
            String capabilities = result.capabilities;
            String encryption = "open";
            if (capabilities != null && !capabilities.equals("") && !capabilities.equals("[]")) {
                if (capabilities.contains("WEP") || capabilities.contains("WPA") || capabilities.contains("EAP")) {
                    encryption = "encrypted";
                }
            }
            
            // Extract extended WiFi data
            int frequency = result.frequency;
            int channel = getWiFiChannel(frequency);
            String wifiStandard = getWiFiStandard(result);
            String vendorInfo = getVendorOUI(result.BSSID);
            int channelWidth = getChannelWidth(result);
            int centerFreq0 = 0;
            int centerFreq1 = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                centerFreq0 = result.centerFreq0;
                centerFreq1 = result.centerFreq1;
            }
            int maxSpeed = estimateMaxSpeed(result);
            
            // Only check the wifi_data table to see if the BSSID already exists.
            Cursor cursor = database.rawQuery("SELECT signal_strength FROM wifi_data WHERE bssid = ?", new String[]{result.BSSID});
            boolean update = false;
            if (cursor.moveToFirst()) {
                int oldSignal = cursor.getInt(0);
                if (result.level > oldSignal) {
                    update = true;
                }
            }
            cursor.close();
            
            // Store WiFi data only in the wifi_data table
            if (update) {
                database.execSQL("UPDATE wifi_data SET ssid=?, signal_strength=?, encryption=?, latitude=?, longitude=?, timestamp=?, frequency=?, channel=?, capabilities=?, wifi_standard=?, vendor_info=?, channel_width=?, center_freq0=?, center_freq1=?, max_connection_speed=? WHERE bssid=?",
                        new Object[]{result.SSID, result.level, encryption, location.getLatitude(), location.getLongitude(), System.currentTimeMillis(), frequency, channel, capabilities, wifiStandard, vendorInfo, channelWidth, centerFreq0, centerFreq1, maxSpeed, result.BSSID});
            } else if (!existsInDb(result.BSSID)) {
                database.execSQL("INSERT INTO wifi_data (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp, frequency, channel, capabilities, wifi_standard, vendor_info, channel_width, center_freq0, center_freq1, max_connection_speed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
        }
        statusText.setText(String.format(getString(R.string.data_saved), results.size()));
        addLogMessage(String.format(getString(R.string.data_saved), results.size()));
        // Display current networks directly - only when not in Show Data mode
        if (!isShowingStoredData) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
            dataListView.setAdapter(adapter);
        }

    }

    private void saveBluetoothDevice(String deviceName, String deviceAddress, int rssi, String deviceClass, Location location) {
        // Check if a Bluetooth device already exists and has a better signal strength.
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
            
            // Calculate movement distance if previous position exists
            Double movementDistance = null;
            if (exists && lastLat != 0 && lastLon != 0) {
                movementDistance = calculateDistance(lastLat, lastLon, currentLat, currentLon);
            }
            
            if (update) {
                // Update with movement data
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
                // New entry
                database.execSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        new Object[]{deviceName, deviceAddress, "BLUETOOTH", rssi, deviceClass, currentLat, currentLon, currentTimestamp});
            }
        }
    }
    
    // Save/update WiFi device for motion analysis in the device_data table
    private void saveWifiDeviceForMovementTracking(String ssid, String bssid, int signal, String encryption, Location location, 
                                                  int frequency, int channel, String standard, String vendor, int channelWidth, int maxSpeed) {
        // Check if the WiFi device already exists in device_data.
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
            
            // Calculate movement distance if previous position exists
            Double movementDistance = null;
            if (exists && lastLat != 0 && lastLon != 0) {
                movementDistance = calculateDistance(lastLat, lastLon, currentLat, currentLon);
            }
            
            if (update) {
                // Update with movement data
                if (movementDistance != null) {
                    database.execSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                            "frequency=?, channel=?, wifi_standard=?, vendor_info=?, channel_width=?, max_connection_speed=?, " +
                            "last_seen_latitude=latitude, last_seen_longitude=longitude, last_seen_timestamp=timestamp, " +
                            "latitude=?, longitude=?, timestamp=?, movement_distance=? " +
                            "WHERE device_address=? AND device_type='WIFI'",
                            new Object[]{ssid, signal, encryption, frequency, channel, standard, vendor, channelWidth, maxSpeed, 
                                       currentLat, currentLon, currentTimestamp, movementDistance, bssid});
                } else {
                    database.execSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                            "frequency=?, channel=?, wifi_standard=?, vendor_info=?, channel_width=?, max_connection_speed=?, " +
                            "latitude=?, longitude=?, timestamp=? " +
                            "WHERE device_address=? AND device_type='WIFI'",
                            new Object[]{ssid, signal, encryption, frequency, channel, standard, vendor, channelWidth, maxSpeed, 
                                       currentLat, currentLon, currentTimestamp, bssid});
                }
            } else {
                // New entry
                database.execSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, " +
                        "frequency, channel, wifi_standard, vendor_info, channel_width, max_connection_speed, " +
                        "latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        new Object[]{ssid, bssid, "WIFI", signal, encryption, frequency, channel, standard, vendor, channelWidth, maxSpeed, 
                                   currentLat, currentLon, currentTimestamp});
            }
        }
    }

    // Auxiliary function: Checks if BSSID is already in the database.
    private boolean existsInDb(String bssid) {
        Cursor cursor = database.rawQuery("SELECT 1 FROM wifi_data WHERE bssid = ? LIMIT 1", new String[]{bssid});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    private boolean existsInDeviceDb(String deviceAddress, String deviceType) {
        Cursor cursor = database.rawQuery("SELECT 1 FROM device_data WHERE device_address = ? AND device_type = ? LIMIT 1", new String[]{deviceAddress, deviceType});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    private void showData() {
        List<String> dataList = new ArrayList<>();

        // Show both old Wi-Fi data and new unified data
        // Show new device_data data first

        Cursor deviceCursor = database.rawQuery("SELECT * FROM device_data ORDER BY device_type, device_name", null);
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
        
        // If there are still old WiFi data files that have not been migrated
        Cursor wifiCursor = database.rawQuery("SELECT * FROM wifi_data WHERE bssid NOT IN (SELECT device_address FROM device_data WHERE device_type = 'WIFI')", null);
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
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
        dataListView.setAdapter(adapter);
        
        // Update information summary for Show Data mode
        updateInfoSummary(0, 0); // No active, only total
    }

    // Popup with further actions
    private void showMoreDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.more_actions);

        // Simplified list because all databases are transferred to an internal database.
        String[] items = {
            getString(R.string.save_db_as_file),
            getString(R.string.delete_database),
            getString(R.string.show_network_count),
            getString(R.string.import_external_db)
        };
        
        builder.setItems(items, (dialog, which) -> {
            switch(which) {
                case 0: // Save DB as a file
                    exportDatabase();
                    break;
                case 1: // Delete database
                    clearDatabase();
                    break;
                case 2: // Show number of networks
                    showNetworkCount();
                    break;
                case 3: // Import external DB
                    selectExternalDatabaseAsActive();
                    break;
            }
        });
        builder.show();
    }

    // No longer needed - all databases are transferred to the internal database.

    // Export database as file
    private void exportDatabase() {
        // Timestamp for unique file names
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = "wifiscannerexport_" + timestamp + ".db";
        
        // User selects storage location with unique file name
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, EXPORT_DB_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_DB_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                try {
                    String dbPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                    java.io.FileInputStream inStream = new java.io.FileInputStream(dbPath);
                    java.io.OutputStream outStream = getContentResolver().openOutputStream(uri);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inStream.read(buffer)) > 0) {
                        outStream.write(buffer, 0, length);
                    }
                    inStream.close();
                    outStream.close();
                    Toast.makeText(this, "DB exported!", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Export failed:" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == IMPORT_DB_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                loadExternalDatabaseAndShowMap(uri);
            }
        } else if (requestCode == IMPORT_ACTIVE_DB_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                loadExternalDatabaseAsActive(uri);
            }
        }
    }

    // Delete DB
    private void clearDatabase() {
        database.execSQL("DELETE FROM wifi_data");
        database.execSQL("DELETE FROM device_data");
        Toast.makeText(this, R.string.all_networks_deleted, Toast.LENGTH_SHORT).show();
        showData();
    }

    // Select external database
    private void selectExternalDatabase() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-sqlite3", "*/*"});
        startActivityForResult(intent, IMPORT_DB_REQUEST_CODE);
    }

    // Select external database as active DB
    private void selectExternalDatabaseAsActive() {
        if (isScanning) {
              Toast.makeText(this, "Please stop scanning first!", Toast.LENGTH_LONG).show();
            return;
        }
        
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-sqlite3", "*/*"});
        startActivityForResult(intent, IMPORT_ACTIVE_DB_REQUEST_CODE);
    }

    // Load external database and display map
    private void loadExternalDatabaseAndShowMap(android.net.Uri uri) {
        try {
            // Create temporary file
            java.io.File tempFile = new java.io.File(getCacheDir(), "temp_external.db");
            
            // Copy database
            java.io.InputStream inStream = getContentResolver().openInputStream(uri);
            java.io.FileOutputStream outStream = new java.io.FileOutputStream(tempFile);
            
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }
            inStream.close();
            outStream.close();
            
            // Check if it is a valid SQLite database.
            SQLiteDatabase testDb = null;
            try {
                testDb = SQLiteDatabase.openDatabase(tempFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
                
                // Check if relevant tables are available.
                boolean hasWifiData = hasTable(testDb, "wifi_data");
                boolean hasDeviceData = hasTable(testDb, "device_data");
                
                if (hasWifiData || hasDeviceData) {
                    // Count available data
                    int wifiCount = 0;
                    int bluetoothCount = 0;
                    
                    if (hasDeviceData) {
                        android.database.Cursor cursor = testDb.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type='WIFI' AND latitude != 0 AND longitude != 0", null);
                        if (cursor.moveToFirst()) wifiCount += cursor.getInt(0);
                        cursor.close();
                        
                        cursor = testDb.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type='BLUETOOTH' AND latitude != 0 AND longitude != 0", null);
                        if (cursor.moveToFirst()) bluetoothCount = cursor.getInt(0);
                        cursor.close();
                    }
                    
                    if (hasWifiData) {
                        android.database.Cursor cursor = testDb.rawQuery("SELECT COUNT(*) FROM wifi_data WHERE latitude != 0 AND longitude != 0", null);
                        if (cursor.moveToFirst()) wifiCount += cursor.getInt(0);
                        cursor.close();
                    }
                    
                    testDb.close();
                    
                    // Show confirmation
                    String message = String.format(getString(R.string.external_db_loaded),
                                                 wifiCount, bluetoothCount);

                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                    builder.setTitle(R.string.external_database)
                           .setMessage(message)
                           .setPositiveButton(R.string.open_map, (dialog, which) -> {
                               // Open map with external database
                               Intent mapIntent = new Intent(MainActivity.this, MapActivity.class);
                               mapIntent.putExtra("external_db_path", tempFile.getAbsolutePath());
                               startActivity(mapIntent);
                           })
                           .setNegativeButton(R.string.cancel, (dialog, which) -> {
                               // Delete temp file
                               if (tempFile.exists()) {
                                   tempFile.delete();
                               }
                           })
                           .show();
                           
                    addLogMessage("External DB loaded: " + wifiCount + " WiFi, " + bluetoothCount + " BT");
                    
                } else {
                        Toast.makeText(this, "No WiFi/Bluetooth data found in the database!", Toast.LENGTH_LONG).show();
                    tempFile.delete();
                }
                
            } catch (Exception e) {
                if (testDb != null) testDb.close();
                    Toast.makeText(this, "Invalid database file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                tempFile.delete();
            }
            
        } catch (Exception e) {
                Toast.makeText(this, "Error loading the database: " + e.getMessage(), Toast.LENGTH_LONG).show();
                addLogMessage("Error during DB import: " + e.getMessage());
        }
    }
    
    // Auxiliary method: Checks if table exists
    private boolean hasTable(SQLiteDatabase db, String tableName) {
        android.database.Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", new String[]{tableName});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    // Load external database as active database
    private void loadExternalDatabaseAsActive(android.net.Uri uri) {
        try {
            // Create a permanent file in the app directory
            java.io.File externalDbFile = new java.io.File(getFilesDir(), "external_active.db");
            
            // Delete old external databases if present.
            if (externalDbFile.exists()) {
                externalDbFile.delete();
            }
            
            // Copy database
            java.io.InputStream inStream = getContentResolver().openInputStream(uri);
            java.io.FileOutputStream outStream = new java.io.FileOutputStream(externalDbFile);
            
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inStream.read(buffer)) > 0) {
                outStream.write(buffer, 0, length);
            }
            inStream.close();
            outStream.close();
            
            // Check if it is a valid SQLite database.
            SQLiteDatabase testDb = null;
            try {
                testDb = SQLiteDatabase.openDatabase(externalDbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
                
                // Check and create missing tables
                boolean hasWifiData = hasTable(testDb, "wifi_data");
                boolean hasDeviceData = hasTable(testDb, "device_data");
                
                if (!hasWifiData) {
                    // Create wifi_data table
                    testDb.execSQL(DatabaseHelper.CREATE_WIFI_TABLE);
                    addLogMessage("wifi_data table created in external database");
                }
                
                if (!hasDeviceData) {
                    // Create device_data table
                    testDb.execSQL(DatabaseHelper.CREATE_DEVICE_TABLE);
                    addLogMessage("device_data table created in external database");
                }
                
                // Count available data
                int wifiCountTemp = 0;
                int bluetoothCountTemp = 0;
                
                android.database.Cursor cursor = testDb.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type='WIFI' AND latitude != 0 AND longitude != 0", null);
                if (cursor.moveToFirst()) wifiCountTemp += cursor.getInt(0);
                cursor.close();
                
                cursor = testDb.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type='BLUETOOTH' AND latitude != 0 AND longitude != 0", null);
                if (cursor.moveToFirst()) bluetoothCountTemp = cursor.getInt(0);
                cursor.close();
                
                cursor = testDb.rawQuery("SELECT COUNT(*) FROM wifi_data WHERE latitude != 0 AND longitude != 0", null);
                if (cursor.moveToFirst()) wifiCountTemp += cursor.getInt(0);
                cursor.close();
                
                testDb.close();
                
                //Final variables for Lambda
                final int wifiCount = wifiCountTemp;
                final int bluetoothCount = bluetoothCountTemp;
                
                // Show confirmation
                String message = String.format(getString(R.string.load_external_db),
                                             wifiCount, bluetoothCount);

                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                builder.setTitle(R.string.external_db_as_active)
                       .setMessage(message)
                       .setPositiveButton(R.string.yes_activate, (dialog, which) -> {
                           // Switch to the external database
                           switchToExternalDatabase(externalDbFile.getAbsolutePath(), wifiCount, bluetoothCount);
                       })
                       .setNegativeButton(R.string.cancel, (dialog, which) -> {
                           // Delete external file
                           if (externalDbFile.exists()) {
                               externalDbFile.delete();
                           }
                       })
                       .show();
                       
            } catch (Exception e) {
                if (testDb != null) testDb.close();
                    Toast.makeText(this, "Invalid database file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                externalDbFile.delete();
            }
            
        } catch (Exception e) {
                Toast.makeText(this, "Error loading the database: " + e.getMessage(), Toast.LENGTH_LONG).show();
                addLogMessage("Error during DB import: " + e.getMessage());
        }
    }

    // Switches to the external database as the active database
    private void switchToExternalDatabase(String dbPath, int wifiCount, int bluetoothCount) {
        try {
            // Open external database (read-only)
            SQLiteDatabase externalDb = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY);
            
            // Copy all data from the external database to the internal database
            copyDataFromExternalToInternal(externalDb);
            
            // Close external DB
            externalDb.close();
            
            // Reset status - we will continue to use the internal database
            isUsingExternalDatabase = false;
            currentDatabasePath = null;
            
            // Update UI
                        String statusMessage = "External data transferred to internal DB - " + wifiCount + " WiFi, " + bluetoothCount + " BT";
            statusText.setText(statusMessage);
            
                        // Reload and display data
            if (isShowingStoredData) {
                showData();
            } else {
                loadAndDisplayAvailableNetworks();
            }
            
                        // Update info summary
            updateInfoSummary(0, 0);
            
                        Toast.makeText(this, "External data has been transferred to the app database!\nData will persist after app restart.", Toast.LENGTH_LONG).show();
                        addLogMessage("External data transferred to internal DB: " + dbPath);
                        addLogMessage("Transferred data: " + wifiCount + " WiFi, " + bluetoothCount + " BT");
            
                        // Delete external DB file (no longer needed)
            java.io.File externalDbFile = new java.io.File(dbPath);
            if (externalDbFile.exists() && externalDbFile.getAbsolutePath().contains(getFilesDir().getAbsolutePath())) {
                externalDbFile.delete();
                            addLogMessage("Temporary external DB file deleted");
            }
            
        } catch (Exception e) {
                        Toast.makeText(this, "Error transferring external DB: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        addLogMessage("Error during DB transfer: " + e.getMessage());
        }
    }

    // Copies all data from the external database to the internal database.
    private void copyDataFromExternalToInternal(SQLiteDatabase externalDb) {
        int copiedWifi = 0;
        int copiedBluetooth = 0;
        int copiedLegacyWifi = 0;
        
        try {
            //1. Copy device_data (WiFi and Bluetooth)
            android.database.Cursor deviceCursor = externalDb.rawQuery("SELECT * FROM device_data", null);
            if (deviceCursor.moveToFirst()) {
                do {
                    String deviceName = deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("device_name"));
                    String deviceAddress = deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("device_address"));
                    String deviceType = deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("device_type"));
                    int signalStrength = deviceCursor.getInt(deviceCursor.getColumnIndexOrThrow("signal_strength"));
                    String encryptionInfo = deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("encryption_info"));
                    double latitude = deviceCursor.getDouble(deviceCursor.getColumnIndexOrThrow("latitude"));
                    double longitude = deviceCursor.getDouble(deviceCursor.getColumnIndexOrThrow("longitude"));
                    long timestamp = deviceCursor.getLong(deviceCursor.getColumnIndexOrThrow("timestamp"));

                    // Extended fields (if available)
                    int frequencyIdx = deviceCursor.getColumnIndex("frequency");
                    int channelIdx = deviceCursor.getColumnIndex("channel");
                    int channelWidthIdx = deviceCursor.getColumnIndex("channel_width");
                    int capabilitiesIdx = deviceCursor.getColumnIndex("capabilities");
                    int centerFreq0Idx = deviceCursor.getColumnIndex("center_freq0");
                    int centerFreq1Idx = deviceCursor.getColumnIndex("center_freq1");
                    int wifiStandardIdx = deviceCursor.getColumnIndex("wifi_standard");
                    int vendorInfoIdx = deviceCursor.getColumnIndex("vendor_info");
                    int maxSpeedIdx = deviceCursor.getColumnIndex("max_connection_speed");
                    
                    int frequency = frequencyIdx >= 0 ? deviceCursor.getInt(frequencyIdx) : 0;
                    int channel = channelIdx >= 0 ? deviceCursor.getInt(channelIdx) : 0;
                    String channelWidth = channelWidthIdx >= 0 ? deviceCursor.getString(channelWidthIdx) : null;
                    String capabilities = capabilitiesIdx >= 0 ? deviceCursor.getString(capabilitiesIdx) : null;
                    int centerFreq0 = centerFreq0Idx >= 0 ? deviceCursor.getInt(centerFreq0Idx) : 0;
                    int centerFreq1 = centerFreq1Idx >= 0 ? deviceCursor.getInt(centerFreq1Idx) : 0;
                    String wifiStandard = wifiStandardIdx >= 0 ? deviceCursor.getString(wifiStandardIdx) : null;
                    String vendorInfo = vendorInfoIdx >= 0 ? deviceCursor.getString(vendorInfoIdx) : null;
                    int maxSpeed = maxSpeedIdx >= 0 ? deviceCursor.getInt(maxSpeedIdx) : 0;
                    
                    // Check if the device already exists
                    if (!existsInDeviceDb(deviceAddress, deviceType)) {
                        // Add device to internal database
                        database.execSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, frequency, channel, channel_width, capabilities, center_freq0, center_freq1, wifi_standard, vendor_info, max_connection_speed, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                new Object[]{deviceName, deviceAddress, deviceType, signalStrength, encryptionInfo, frequency, channel, channelWidth, capabilities, centerFreq0, centerFreq1, wifiStandard, vendorInfo, maxSpeed, latitude, longitude, timestamp});
                        
                        if ("WIFI".equals(deviceType)) {
                            copiedWifi++;
                        } else if ("BLUETOOTH".equals(deviceType)) {
                            copiedBluetooth++;
                        }
                    }
                } while (deviceCursor.moveToNext());
            }
            deviceCursor.close();
            
            // 2. Copy legacy wifi_data (if it exists)
            if (hasTable(externalDb, "wifi_data")) {
                android.database.Cursor wifiCursor = externalDb.rawQuery("SELECT * FROM wifi_data", null);
                if (wifiCursor.moveToFirst()) {
                    do {
                        String ssid = wifiCursor.getString(wifiCursor.getColumnIndexOrThrow("ssid"));
                        String bssid = wifiCursor.getString(wifiCursor.getColumnIndexOrThrow("bssid"));
                        int signalStrength = wifiCursor.getInt(wifiCursor.getColumnIndexOrThrow("signal_strength"));
                        String encryption = wifiCursor.getString(wifiCursor.getColumnIndexOrThrow("encryption"));
                        double latitude = wifiCursor.getDouble(wifiCursor.getColumnIndexOrThrow("latitude"));
                        double longitude = wifiCursor.getDouble(wifiCursor.getColumnIndexOrThrow("longitude"));
                        long timestamp = wifiCursor.getLong(wifiCursor.getColumnIndexOrThrow("timestamp"));
                        
                        // Extended fields (if available)
                        int frequencyIdx = wifiCursor.getColumnIndex("frequency");
                        int channelIdx = wifiCursor.getColumnIndex("channel");
                        int capabilitiesIdx = wifiCursor.getColumnIndex("capabilities");
                        int wifiStandardIdx = wifiCursor.getColumnIndex("wifi_standard");
                        int vendorInfoIdx = wifiCursor.getColumnIndex("vendor_info");
                        int channelWidthIdx = wifiCursor.getColumnIndex("channel_width");
                        int centerFreq0Idx = wifiCursor.getColumnIndex("center_freq0");
                        int centerFreq1Idx = wifiCursor.getColumnIndex("center_freq1");
                        int maxSpeedIdx = wifiCursor.getColumnIndex("max_connection_speed");
                        
                        int frequency = frequencyIdx >= 0 ? wifiCursor.getInt(frequencyIdx) : 0;
                        int channel = channelIdx >= 0 ? wifiCursor.getInt(channelIdx) : 0;
                        String capabilities = capabilitiesIdx >= 0 ? wifiCursor.getString(capabilitiesIdx) : null;
                        String wifiStandard = wifiStandardIdx >= 0 ? wifiCursor.getString(wifiStandardIdx) : null;
                        String vendorInfo = vendorInfoIdx >= 0 ? wifiCursor.getString(vendorInfoIdx) : null;
                        String channelWidth = channelWidthIdx >= 0 ? wifiCursor.getString(channelWidthIdx) : null;
                        int centerFreq0 = centerFreq0Idx >= 0 ? wifiCursor.getInt(centerFreq0Idx) : 0;
                        int centerFreq1 = centerFreq1Idx >= 0 ? wifiCursor.getInt(centerFreq1Idx) : 0;
                        int maxSpeed = maxSpeedIdx >= 0 ? wifiCursor.getInt(maxSpeedIdx) : 0;
                        
                        // Check if the BSSID already exists (in wifi_data or device_data)
                        if (!existsInDb(bssid) && !existsInDeviceDb(bssid, "WIFI")) {
                            // Add to the wifi_data table
                            database.execSQL("INSERT INTO wifi_data (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp, frequency, channel, capabilities, wifi_standard, vendor_info, channel_width, center_freq0, center_freq1, max_connection_speed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    new Object[]{ssid, bssid, signalStrength, encryption, latitude, longitude, timestamp, frequency, channel, capabilities, wifiStandard, vendorInfo, channelWidth, centerFreq0, centerFreq1, maxSpeed});
                            copiedLegacyWifi++;
                        }
                    } while (wifiCursor.moveToNext());
                }
                wifiCursor.close();
            }
            
            addLogMessage("Data transfer completed:");
            addLogMessage("• " + copiedWifi + " WiFi devices (device_data)");
            addLogMessage("• " + copiedBluetooth + " Bluetooth devices");
            addLogMessage("• " + copiedLegacyWifi + " Legacy WiFi entries");
        } catch (Exception e) {
            addLogMessage("Error while copying data: " + e.getMessage());
            throw e;
        }

        }

    // Auxiliary methods for database transfer

    // Show number of networks
    private void showNetworkCount() {
        // Count devices from the new device_data table
        Cursor deviceCursor = database.rawQuery("SELECT COUNT(*) FROM device_data", null);
        int deviceCount = 0;
        if (deviceCursor.moveToFirst()) {
            deviceCount = deviceCursor.getInt(0);
        }
        deviceCursor.close();
        
        // Count WiFi devices
        Cursor wifiCursor = database.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'WIFI'", null);
        int wifiCount = 0;
        if (wifiCursor.moveToFirst()) {
            wifiCount = wifiCursor.getInt(0);
        }
        wifiCursor.close();
        
        // Count Bluetooth devices
        Cursor bluetoothCursor = database.rawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'BLUETOOTH'", null);
        int bluetoothCount = 0;
        if (bluetoothCursor.moveToFirst()) {
            bluetoothCount = bluetoothCursor.getInt(0);
        }
        bluetoothCursor.close();
        
        // Count old WiFi data
        Cursor oldWifiCursor = database.rawQuery("SELECT COUNT(*) FROM wifi_data", null);
        int oldWifiCount = 0;
        if (oldWifiCursor.moveToFirst()) {
            oldWifiCount = oldWifiCursor.getInt(0);
        }
        oldWifiCursor.close();
        
    String message = "Devices in DB: " + deviceCount + 
            " (WiFi: " + wifiCount + 
            ", Bluetooth: " + bluetoothCount + ")" +
            (oldWifiCount > 0 ? " + " + oldWifiCount + " old WiFi" : "");
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(wifiScanReceiver);
        unregisterReceiver(bluetoothScanReceiver);
        
        // Stop background service only if scanning was active.
        if (isScanning) {
            Intent serviceIntent = new Intent(this, ScanService.class);
            stopService(serviceIntent);
        }
        
        stopScanning();
        database.close();
    }

    // Update button text according to status
        private void updateToggleScanButton() {
            if (toggleScanButton != null) {
                if (isScanning) {
                    toggleScanButton.setText("Stop WiFi scanning");
                } else {
                    toggleScanButton.setText("Start WiFi scanning");
                }
            }
        }

    // Update Bluetooth button text according to status
    private void updateBluetoothToggleButton() {
        if (bluetoothToggleButton != null) {
            if (isBluetoothScanningEnabled) {
                    bluetoothToggleButton.setText("BT on");
                bluetoothToggleButton.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
            } else {
                    bluetoothToggleButton.setText("BT off");
                bluetoothToggleButton.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
            }
        }
    }

    // Database Helper Class
    public static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "wifi_scanner.db";
        private static final int DATABASE_VERSION = 6; // Increased for motion analysis fields
        private static final String CREATE_WIFI_TABLE = "CREATE TABLE wifi_data (" +
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
        
        private static final String CREATE_DEVICE_TABLE = "CREATE TABLE device_data (" +
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

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
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
}