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
import android.text.Editable;
import android.text.Selection;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    /**
     * =============================
     *   CODE VERSION MARKER
     *   APP_VERSION: 1.0.3
     * =============================
     * Use this variable to visually distinguish code versions.
     */
    public static final String APP_VERSION = "1.0.3";
    private WifiManager wifiManager;
    private LocationManager locationManager;
    private BluetoothAdapter bluetoothAdapter;
    private Object database; // Can be android.database.sqlite.SQLiteDatabase or net.sqlcipher.database.SQLiteDatabase
    
    // Database encryption support
    private EncryptionManager encryptionManager;
    private DatabaseEncryptionHelper encryptedDbHelper;
    private boolean isDatabaseEncrypted = false;
    
    private TextView statusText;
    private TextView infoSummary;
    private TextView logcatText;
    private TextView encryptionStatusText;
    private ScrollView logcatScrollView;
    private ListView dataListView;
    private Button toggleScanButton, showButton, moreButton, bluetoothToggleButton, mapButton;
    private View securityOverlay;  // Black overlay to hide content during passphrase entry
    private boolean isNavigatingInternally = false;  // Flag to track internal navigation (e.g., to MapActivity)
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
    private static final int EXPORT_CHECKSUM_REQUEST_CODE = 104;
    private static final int IMPORT_CHECKSUM_REQUEST_CODE = 105;
    private static final long SCAN_INTERVAL = 5000; // 5 seconds - very frequently
    private static final long MIN_SCAN_INTERVAL = 3000; // Minimum 3 seconds between scans
    private long lastScanTime = 0;
    private StringBuilder logBuffer = new StringBuilder();

    // Store checksum data for export
    private String lastExportedDbChecksum = null;
    private String lastExportedDbFilename = null;

    // Store import data temporarily during checksum verification
    private android.net.Uri pendingImportUri = null;
    private java.io.File pendingImportFile = null;
    private byte[] pendingEncryptionSalt = null;  // Salt from metadata for encrypted DB import

    // Helper methods for database operations that work with both types
    private Cursor dbRawQuery(String sql, String[] selectionArgs) {
        try {
            if (database == null) return null;
            if (isDatabaseEncrypted) {
                return ((net.sqlcipher.database.SQLiteDatabase) database).rawQuery(sql, selectionArgs);
            } else {
                return ((android.database.sqlite.SQLiteDatabase) database).rawQuery(sql, selectionArgs);
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error in dbRawQuery: " + e.getMessage(), e);
            return null;
        }
    }

    private void dbExecSQL(String sql) throws Exception {
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
            android.util.Log.e("MainActivity", "Error in dbExecSQL: " + e.getMessage(), e);
            throw e; // Re-throw exception so caller can handle it
        }
    }

    private void dbExecSQL(String sql, Object[] bindArgs) throws Exception {
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
            android.util.Log.e("MainActivity", "Error in dbExecSQL with args: " + e.getMessage(), e);
            throw e; // Re-throw exception so caller can handle it
        }
    }

    private long dbInsert(String table, String nullColumnHack, android.content.ContentValues values) {
        try {
            if (database == null) {
                android.util.Log.w("MainActivity", "Database is null in dbInsert");
                return -1;
            }

            // Check if database is readonly before attempting write
            if (isDatabaseEncrypted) {
                net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                if (!db.isOpen() || db.isReadOnly()) {
                    android.util.Log.w("MainActivity", "Encrypted database is not open or readonly in dbInsert");
                    return -1;
                }
                return db.insert(table, nullColumnHack, values);
            } else {
                android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                if (!db.isOpen() || db.isReadOnly()) {
                    android.util.Log.w("MainActivity", "Database is not open or readonly in dbInsert");
                    return -1;
                }
                return db.insert(table, nullColumnHack, values);
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error in dbInsert: " + e.getMessage(), e);
            return -1;
        }
    }

    private void dbBeginTransaction() {
        if (database == null) return;
        if (isDatabaseEncrypted) {
            ((net.sqlcipher.database.SQLiteDatabase) database).beginTransaction();
        } else {
            ((android.database.sqlite.SQLiteDatabase) database).beginTransaction();
        }
    }

    private void dbSetTransactionSuccessful() {
        if (database == null) return;
        if (isDatabaseEncrypted) {
            ((net.sqlcipher.database.SQLiteDatabase) database).setTransactionSuccessful();
        } else {
            ((android.database.sqlite.SQLiteDatabase) database).setTransactionSuccessful();
        }
    }

    private void dbEndTransaction() {
        if (database == null) return;
        if (isDatabaseEncrypted) {
            ((net.sqlcipher.database.SQLiteDatabase) database).endTransaction();
        } else {
            ((android.database.sqlite.SQLiteDatabase) database).endTransaction();
        }
    }

    private boolean dbInTransaction() {
        if (database == null) return false;
        if (isDatabaseEncrypted) {
            return ((net.sqlcipher.database.SQLiteDatabase) database).inTransaction();
        } else {
            return ((android.database.sqlite.SQLiteDatabase) database).inTransaction();
        }
    }

    private void dbClose() {
        if (database == null) return;
        if (isDatabaseEncrypted) {
            ((net.sqlcipher.database.SQLiteDatabase) database).close();
        } else {
            ((android.database.sqlite.SQLiteDatabase) database).close();
        }
    }

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

        // Ensure ScanService is stopped on app start
        stopService(new Intent(this, ScanService.class));

        // Initialize encryption manager
        encryptionManager = new EncryptionManager(this);

        // initialization
        statusText = findViewById(R.id.status_text);
        infoSummary = findViewById(R.id.info_summary);
        encryptionStatusText = findViewById(R.id.encryption_status);
        logcatText = findViewById(R.id.logcat_text);
        logcatScrollView = findViewById(R.id.logcat_scrollview);
        dataListView = findViewById(R.id.data_list);
        toggleScanButton = findViewById(R.id.toggle_scan_button);
        bluetoothToggleButton = findViewById(R.id.bluetooth_toggle_button);
        showButton = findViewById(R.id.show_button);
        mapButton = findViewById(R.id.map_button);
        
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

        // New button for further actions
            // Double-click to select all in debug log
            logcatText.setOnClickListener(new View.OnClickListener() {
                private long lastClickTime = 0;
                @Override
                public void onClick(View v) {
                    long clickTime = System.currentTimeMillis();
                    if (clickTime - lastClickTime < 400) { // double-click detected (400ms)
                        Editable editable = Editable.Factory.getInstance().newEditable(logcatText.getText());
                        logcatText.setText(editable);
                        Selection.setSelection(editable, 0, editable.length());
                    }
                    lastClickTime = clickTime;
                }
            });
        moreButton = findViewById(R.id.more_button);
        moreButton.setOnClickListener(v -> showMoreDialog());

        // Export log button
        Button exportLogButton = findViewById(R.id.export_log_button);
        exportLogButton.setOnClickListener(v -> exportDebugLogToTxt());

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

        // If database is encrypted and needs unlock, show black overlay IMMEDIATELY
        if (encryptionManager.isEncryptionEnabled() && !encryptionManager.isPassphraseCached()) {
            if (securityOverlay != null) {
                securityOverlay.setVisibility(View.VISIBLE);
                securityOverlay.bringToFront();
            }
        }

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
                Log.i("MainActivity", "Database path: " + dbPath);
                addLogMessage("📂 DB Path: " + dbPath);
            } catch (Exception e) {
                Log.e("MainActivity", "Error getting DB path: " + e.getMessage());
            }
        } else {
            Log.w("MainActivity", "Database is null after initialization!");
            addLogMessage("⚠️ Database is NULL after initialization");
        }

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
            // Set flag to indicate internal navigation
            isNavigatingInternally = true;
            
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

    @Override
    protected void onResume() {
        super.onResume();
        
        // Reset internal navigation flag when returning to MainActivity
        isNavigatingInternally = false;
        
        // If database is encrypted and needs unlock, show black overlay
        if (encryptionManager != null && encryptionManager.isEncryptionEnabled() && 
            !encryptionManager.isPassphraseCached()) {
            if (securityOverlay != null) {
                securityOverlay.setVisibility(View.VISIBLE);
                securityOverlay.bringToFront();
            }
        } else {
            // Hide overlay if passphrase is cached or encryption is not enabled
            if (securityOverlay != null) {
                securityOverlay.setVisibility(View.GONE);
            }
        }
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
        
        // Clear passphrase from cache when scanning stops (if app is in background)
        if (!isInForeground() && encryptionManager != null && encryptionManager.isEncryptionEnabled()) {
            encryptionManager.clearPassphrase();
            if (encryptedDbHelper != null) {
                encryptedDbHelper.clearPassphrase();
            }
            Log.i("MainActivity", "Passphrase cleared after scan stopped");
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (SecurityException e) {
                Log.e("Location", "SecurityException during removeUpdates: " + e.getMessage());
            }
        }
        updateToggleScanButton();
    }
    
    /**
     * Check if app is currently in foreground
     */
    private boolean isInForeground() {
        android.app.ActivityManager.RunningAppProcessInfo appProcessInfo = new android.app.ActivityManager.RunningAppProcessInfo();
        android.app.ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                appProcessInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE);
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
        Cursor wifiCursor = dbRawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'WIFI'", null);
        if (wifiCursor != null) {
            if (wifiCursor.moveToFirst()) {
                totalWifi = wifiCursor.getInt(0);
            }
            wifiCursor.close();
        }
        
        // Count Bluetooth devices in DB
        Cursor bluetoothCursor = dbRawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'BLUETOOTH'", null);
        if (bluetoothCursor != null) {
            if (bluetoothCursor.moveToFirst()) {
                totalBluetooth = bluetoothCursor.getInt(0);
            }
            bluetoothCursor.close();
        }
        
        // Count old WiFi data if available.
        Cursor oldWifiCursor = dbRawQuery("SELECT COUNT(*) FROM wifi_data", null);
        int oldWifiCount = 0;
        if (oldWifiCursor != null) {
            if (oldWifiCursor.moveToFirst()) {
                oldWifiCount = oldWifiCursor.getInt(0);
            }
            oldWifiCursor.close();
        }
        
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

    /**
     * Update total networks count display (after import, etc.)
     */
    private void updateTotalNetworksCount() {
        updateInfoSummary(0, 0);
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
                Cursor cursor = dbRawQuery("SELECT signal_strength FROM wifi_data WHERE bssid = ?", new String[]{result.BSSID});
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
                    dbExecSQL("UPDATE wifi_data SET ssid=?, signal_strength=?, encryption=?, latitude=?, longitude=?, timestamp=?, frequency=?, channel=?, capabilities=?, wifi_standard=?, vendor_info=?, channel_width=?, center_freq0=?, center_freq1=?, max_connection_speed=? WHERE bssid=?",
                            new Object[]{result.SSID, result.level, encryption, location.getLatitude(), location.getLongitude(), System.currentTimeMillis(), frequency, channel, capabilities, wifiStandard, vendorInfo, channelWidth, centerFreq0, centerFreq1, maxSpeed, result.BSSID});
                } else if (!existsInDb(result.BSSID)) {
                    dbExecSQL("INSERT INTO wifi_data (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp, frequency, channel, capabilities, wifi_standard, vendor_info, channel_width, center_freq0, center_freq1, max_connection_speed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
            statusText.setText(String.format(getString(R.string.data_saved), results.size()));
            addLogMessage(String.format(getString(R.string.data_saved), results.size()));
        } else {
            statusText.setText(String.format(getString(R.string.data_saved), 0));
            addLogMessage(String.format(getString(R.string.data_saved), 0));
        }
        // Display current networks directly - only when not in Show Data mode
        if (!isShowingStoredData) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
            dataListView.setAdapter(adapter);
        }

    }

    private void saveBluetoothDevice(String deviceName, String deviceAddress, int rssi, String deviceClass, Location location) {
        try {
            // Check if a Bluetooth device already exists and has a better signal strength.
            Cursor cursor = dbRawQuery("SELECT signal_strength, latitude, longitude, timestamp FROM device_data WHERE device_address = ? AND device_type = 'BLUETOOTH'", new String[]{deviceAddress});
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
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error saving Bluetooth device: " + e.getMessage(), e);
        }
    }
    
    // Save/update WiFi device for motion analysis in the device_data table
    private void saveWifiDeviceForMovementTracking(String ssid, String bssid, int signal, String encryption, Location location,
                                                  int frequency, int channel, String standard, String vendor, int channelWidth, int maxSpeed) {
        try {
            // Check if the WiFi device already exists in device_data.
            Cursor cursor = dbRawQuery("SELECT signal_strength, latitude, longitude, timestamp FROM device_data WHERE device_address = ? AND device_type = 'WIFI'", new String[]{bssid});
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
                        dbExecSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                                "frequency=?, channel=?, wifi_standard=?, vendor_info=?, channel_width=?, max_connection_speed=?, " +
                                "last_seen_latitude=latitude, last_seen_longitude=longitude, last_seen_timestamp=timestamp, " +
                                "latitude=?, longitude=?, timestamp=?, movement_distance=? " +
                                "WHERE device_address=? AND device_type='WIFI'",
                                new Object[]{ssid, signal, encryption, frequency, channel, standard, vendor, channelWidth, maxSpeed,
                                           currentLat, currentLon, currentTimestamp, movementDistance, bssid});
                    } else {
                        dbExecSQL("UPDATE device_data SET device_name=?, signal_strength=?, encryption_info=?, " +
                                "frequency=?, channel=?, wifi_standard=?, vendor_info=?, channel_width=?, max_connection_speed=?, " +
                                "latitude=?, longitude=?, timestamp=? " +
                                "WHERE device_address=? AND device_type='WIFI'",
                                new Object[]{ssid, signal, encryption, frequency, channel, standard, vendor, channelWidth, maxSpeed,
                                           currentLat, currentLon, currentTimestamp, bssid});
                    }
                } else {
                    // New entry
                    dbExecSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, " +
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

    // Auxiliary function: Checks if BSSID is already in the database.
    private boolean existsInDb(String bssid) {
        Cursor cursor = dbRawQuery("SELECT 1 FROM wifi_data WHERE bssid = ? LIMIT 1", new String[]{bssid});
        if (cursor == null) return false;
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    private boolean existsInDeviceDb(String deviceAddress, String deviceType) {
        Cursor cursor = dbRawQuery("SELECT 1 FROM device_data WHERE device_address = ? AND device_type = ? LIMIT 1", new String[]{deviceAddress, deviceType});
        if (cursor == null) return false;
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    private void showData() {
        List<String> dataList = new ArrayList<>();

        // Show both old Wi-Fi data and new unified data
        // Show new device_data data first

        Cursor deviceCursor = dbRawQuery("SELECT * FROM device_data ORDER BY device_type, device_name", null);
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
        Cursor wifiCursor = dbRawQuery("SELECT * FROM wifi_data WHERE bssid NOT IN (SELECT device_address FROM device_data WHERE device_type = 'WIFI')", null);
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

    // Popup with further actions
    private void showMoreDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.more_actions);

        // Simplified list because all databases are transferred to an internal database.
        String[] items = {
            getString(R.string.save_db_as_file),
            getString(R.string.delete_database),
            getString(R.string.show_network_count),
            getString(R.string.import_external_db),
            getString(R.string.encryption_settings),
            "Close App"
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
                case 4: // Encryption settings
                    showEncryptionSettingsDialog();
                    break;
                case 5: // Close App
                    // Clear passphrase from cache before closing
                    if (encryptionManager != null) {
                        encryptionManager.clearPassphrase();
                    }
                    finishAffinity();
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
                    java.io.File dbFile = new java.io.File(dbPath);

                    // Calculate SHA-256 checksum before export
                    addLogMessage("Calculating SHA-256 checksum...");
                    String checksum = calculateSHA256(dbFile);

                    if (checksum != null) {
                        // Show checksum preview (first 8 + last 8 chars for verification)
                        String checksumPreview = checksum.length() > 16 ? 
                            checksum.substring(0, 8) + "..." + checksum.substring(checksum.length() - 8) : 
                            checksum;
                        addLogMessage("Checksum: " + checksumPreview);
                    }

                    // Export database file
                    java.io.FileInputStream inStream = new java.io.FileInputStream(dbPath);
                    java.io.OutputStream outStream = getContentResolver().openOutputStream(uri);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inStream.read(buffer)) > 0) {
                        outStream.write(buffer, 0, length);
                    }
                    inStream.close();
                    outStream.close();

                    // Extract filename from URI for metadata
                    String exportedFilename = "exported_database.db";
                    try {
                        android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                            if (nameIndex >= 0) {
                                exportedFilename = cursor.getString(nameIndex);
                            }
                            cursor.close();
                        }
                    } catch (Exception e) {
                        // Use default filename if extraction fails
                    }

                    // Store checksum data for metadata export
                    lastExportedDbChecksum = checksum;
                    lastExportedDbFilename = exportedFilename;

                    // Show success message with encryption status
                    String successMessage = "DB exported successfully!";
                    if (isDatabaseEncrypted) {
                        successMessage += "\n\n🔒 This database is encrypted.";
                    }
                    Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
                    addLogMessage("Database exported: " + exportedFilename + 
                                 (isDatabaseEncrypted ? " (encrypted)" : ""));

                    // Offer to export checksum metadata file
                    if (checksum != null) {
                        offerChecksumExport(exportedFilename, checksum, dbFile.length());
                    }

                } catch (Exception e) {
                    Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    addLogMessage("ERROR: Export failed: " + e.getMessage());
                }
            }
        } else if (requestCode == EXPORT_CHECKSUM_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null && lastExportedDbChecksum != null && lastExportedDbFilename != null) {
                try {
                    // Create checksum metadata JSON
                    String metadata = createChecksumMetadata(
                        lastExportedDbFilename,
                        lastExportedDbChecksum,
                        getDatabasePath("wifi_scanner.db").length()
                    );

                    if (metadata != null) {
                        // Write metadata to file
                        java.io.OutputStream outStream = getContentResolver().openOutputStream(uri);
                        outStream.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        outStream.close();

                        Toast.makeText(this, "Checksum metadata exported!", Toast.LENGTH_LONG).show();
                        addLogMessage("Checksum metadata exported successfully");
                        addLogMessage("SHA-256: " + lastExportedDbChecksum);
                    }

                    // Clear temporary data
                    lastExportedDbChecksum = null;
                    lastExportedDbFilename = null;

                } catch (Exception e) {
                    Toast.makeText(this, "Checksum export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    addLogMessage("ERROR: Checksum export failed: " + e.getMessage());
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
        } else if (requestCode == IMPORT_CHECKSUM_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null && pendingImportFile != null) {
                verifyAndImportWithChecksum(uri, pendingImportFile);
            }
        }
    }

    // Clear all data from database
    private void clearDatabase() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Clear Database");
        builder.setMessage("This will remove all WiFi and Bluetooth data from the database.\n\n" +
                "The database structure and encryption settings will be preserved.\n\n" +
                "Are you sure?");

        builder.setPositiveButton("Clear All Data", (dialog, which) -> {
            // Explicitly stop ScanService to release database
            stopService(new Intent(this, ScanService.class));
            if (isScanning) {
                stopScanning();
            }
            // Wait 500ms to ensure ScanService is fully stopped
            new android.os.Handler().postDelayed(() -> {
                try {
                    android.util.Log.d("MainActivity", "Starting database clear operation");

                    // Begin transaction for atomic operation
                    dbBeginTransaction();

                    try {
                        // Delete all data
                        dbExecSQL("DELETE FROM wifi_data");
                        android.util.Log.d("MainActivity", "Deleted wifi_data");

                        dbExecSQL("DELETE FROM device_data");
                        android.util.Log.d("MainActivity", "Deleted device_data");

                        // Mark transaction as successful
                        dbSetTransactionSuccessful();

                        android.util.Log.d("MainActivity", "Database cleared successfully - transaction committed");
                        Toast.makeText(this, R.string.all_networks_deleted, Toast.LENGTH_SHORT).show();

                        // Refresh the display to show empty data
                        showData();

                    } finally {
                        // End transaction (commits if successful, rolls back otherwise)
                        dbEndTransaction();
                    }

                } catch (Exception e) {
                    android.util.Log.e("MainActivity", "Error clearing database: " + e.getMessage(), e);
                    Toast.makeText(this, "Error clearing database: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }, 500);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // Offer to export checksum metadata file
    private void offerChecksumExport(String dbFilename, String checksum, long fileSize) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Export Checksum Metadata");

        String message = "Database exported successfully!\n\n" +
                "Would you like to export a checksum metadata file?\n\n" +
                "BENEFITS:\n" +
                "• Verify file integrity during import\n" +
                "• Detect tampering or corruption\n" +
                "• Establish trust for file sharing\n" +
                (isDatabaseEncrypted ? "• Store encryption salt for portable import\n" : "") +
                "\n" +
                "METADATA INCLUDES:\n" +
                "• SHA-256 checksum\n" +
                "• Original filename\n" +
                "• File size\n" +
                "• Export timestamp\n" +
                (isDatabaseEncrypted ? "• Encryption salt (for import)\n" : "") +
                "\n" +
                (isDatabaseEncrypted ? "🔐 IMPORTANT: For encrypted databases, the metadata file is REQUIRED for import on other devices!\n\n" : "") +
                "The metadata will be saved as a .json file that you can share alongside the database file.";

        builder.setMessage(message);

        builder.setPositiveButton("Yes, Export Checksum", (dialog, which) -> {
            // Create suggested filename for checksum metadata
            String metadataFilename = dbFilename.replace(".db", ".sha256.json");
            if (!metadataFilename.contains(".sha256.json")) {
                metadataFilename = dbFilename + ".sha256.json";
            }

            // Launch file picker for checksum metadata
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, metadataFilename);
            startActivityForResult(intent, EXPORT_CHECKSUM_REQUEST_CODE);

            addLogMessage("User chose to export checksum metadata");
        });

        // For encrypted databases, make metadata export mandatory
        if (isDatabaseEncrypted) {
            builder.setNegativeButton("Cancel Export", (dialog, which) -> {
                dialog.dismiss();
                addLogMessage("User cancelled metadata export for encrypted DB");
                Toast.makeText(this, "⚠️ Warning: Without metadata file, this database cannot be imported!", Toast.LENGTH_LONG).show();

                // Clear temporary data
                lastExportedDbChecksum = null;
                lastExportedDbFilename = null;
            });
        } else {
            // For unencrypted databases, skip is allowed
            builder.setNegativeButton("Skip", (dialog, which) -> {
                dialog.dismiss();
                addLogMessage("User skipped checksum metadata export");
                Toast.makeText(this, "Note: You can verify checksums manually if needed", Toast.LENGTH_SHORT).show();

                // Clear temporary data
                lastExportedDbChecksum = null;
                lastExportedDbFilename = null;
            });
        }

        builder.setCancelable(false);
        builder.show();
    }

    // Offer checksum verification during import
    private void offerChecksumVerification(android.net.Uri dbUri, java.io.File dbFile) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);

        // Check if database is encrypted
        boolean isEncrypted = DatabaseEncryptionHelper.isDatabaseEncrypted(dbFile);

        if (isEncrypted) {
            builder.setTitle("🔒 Metadata Required");
            String message = "This is an ENCRYPTED database!\n\n" +
                    "⚠️ MANDATORY: You MUST provide the metadata file (.sha256.json) that was exported with this database.\n\n" +
                    "The metadata contains:\n" +
                    "• Encryption salt (required for decryption)\n" +
                    "• Checksum for integrity verification\n\n" +
                    "WITHOUT the metadata file, import will fail even with the correct passphrase!";
            builder.setMessage(message);
        } else {
            builder.setTitle("Verify Checksum?");
            String message = "Do you have a checksum metadata file (.sha256.json) for this database?\n\n" +
                    "VERIFICATION BENEFITS:\n" +
                    "• Confirm file hasn't been tampered with\n" +
                    "• Verify file integrity\n" +
                    "• Ensure authentic source\n\n" +
                    "If you don't have a checksum file, you can skip this step. The database will still be validated using other security checks.";
            builder.setMessage(message);
        }

        String positiveButtonText = isEncrypted ? "Select Metadata File" : "Yes, Select Checksum File";
        builder.setPositiveButton(positiveButtonText, (dialog, which) -> {
            // Store pending import data
            pendingImportUri = dbUri;
            pendingImportFile = dbFile;

            // Open file picker for checksum metadata
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, IMPORT_CHECKSUM_REQUEST_CODE);

            addLogMessage(isEncrypted ? "User selected metadata file (required)" : "User chose to verify checksum");
        });

        // For encrypted databases, skip is not allowed
        if (isEncrypted) {
            builder.setNegativeButton("Cancel Import", (dialog, which) -> {
                dialog.dismiss();
                dbFile.delete();
                addLogMessage("User cancelled import - metadata file required for encrypted DB");
                Toast.makeText(this, "Import cancelled. Encrypted databases require metadata file.", Toast.LENGTH_LONG).show();
            });
        } else {
            // For unencrypted databases, skip is allowed
            builder.setNegativeButton("Skip Verification", (dialog, which) -> {
                dialog.dismiss();
                addLogMessage("User skipped checksum verification");
                Toast.makeText(this, "Proceeding without checksum verification", Toast.LENGTH_SHORT).show();

                // Continue with import without checksum verification
                continueImportWithoutChecksum(dbFile);
            });
        }

        builder.setCancelable(false);
        builder.show();
    }

    // Continue import without checksum verification
    private void continueImportWithoutChecksum(java.io.File externalDbFile) {
        // Continue with the normal validation flow
        proceedWithDatabaseImport(externalDbFile);
    }

    // Verify checksum and import database
    private void verifyAndImportWithChecksum(android.net.Uri checksumUri, java.io.File dbFile) {
        try {
            // Read checksum metadata file
            java.io.InputStream inStream = getContentResolver().openInputStream(checksumUri);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(inStream, java.nio.charset.StandardCharsets.UTF_8)
            );

            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            reader.close();
            inStream.close();

            // Parse JSON metadata
            JSONObject metadata = new JSONObject(jsonBuilder.toString());
            String expectedChecksum = metadata.getString("checksum");
            String algorithm = metadata.getString("algorithm");
            String originalFilename = metadata.optString("filename", "unknown");
            long originalFileSize = metadata.optLong("fileSize", -1);

            // Extract encryption metadata if present
            boolean isEncrypted = metadata.optBoolean("encrypted", false);
            String encryptionSaltBase64 = metadata.optString("encryptionSalt", null);

            addLogMessage("Checksum metadata loaded:");
            addLogMessage("  Algorithm: " + algorithm);
            addLogMessage("  Filename: " + originalFilename);
            // Show checksum preview (first 8 + last 8 chars)
            String checksumPreview = expectedChecksum.length() > 16 ? 
                expectedChecksum.substring(0, 8) + "..." + expectedChecksum.substring(expectedChecksum.length() - 8) : 
                expectedChecksum;
            addLogMessage("  Expected checksum: " + checksumPreview);
            if (isEncrypted) {
                addLogMessage("  Encryption: Enabled");
                if (encryptionSaltBase64 != null) {
                    // Store salt for later use during passphrase entry
                    pendingEncryptionSalt = android.util.Base64.decode(encryptionSaltBase64, android.util.Base64.NO_WRAP);
                    addLogMessage("  Encryption salt extracted from metadata");
                }
            }

            // Verify algorithm
            if (!"SHA-256".equals(algorithm)) {
                Toast.makeText(this, "Unsupported checksum algorithm: " + algorithm, Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Unsupported algorithm: " + algorithm);
                dbFile.delete();
                clearPendingImport();
                return;
            }

            // Verify file size if available
            if (originalFileSize > 0 && dbFile.length() != originalFileSize) {
                addLogMessage("WARNING: File size mismatch!");
                addLogMessage("  Expected: " + originalFileSize + " bytes");
                addLogMessage("  Actual: " + dbFile.length() + " bytes");

                // Show warning but allow user to decide
                showFileSizeMismatchWarning(dbFile, expectedChecksum, originalFileSize);
                return;
            }

            // Calculate actual checksum
            addLogMessage("Calculating file checksum...");
            String actualChecksum = calculateSHA256(dbFile);

            if (actualChecksum == null) {
                Toast.makeText(this, "Failed to calculate checksum", Toast.LENGTH_LONG).show();
                dbFile.delete();
                clearPendingImport();
                return;
            }

            // Verify checksum
            boolean checksumValid = verifyChecksum(actualChecksum, expectedChecksum, originalFilename);

            if (checksumValid) {
                // Checksum verified successfully
                Toast.makeText(this, "✓ Checksum verified successfully!", Toast.LENGTH_LONG).show();
                addLogMessage("Proceeding with verified import...");
                // DON'T clear pending import yet - we need the salt!
                proceedWithDatabaseImport(dbFile);
            } else {
                // Checksum mismatch - show error
                showChecksumMismatchError(dbFile);
            }

        } catch (Exception e) {
            Toast.makeText(this, "Checksum verification failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            addLogMessage("ERROR: Checksum verification failed: " + e.getMessage());
            dbFile.delete();
            clearPendingImport();
        }
    }

    // Clear pending import data
    private void clearPendingImport() {
        pendingImportUri = null;
        pendingImportFile = null;
        pendingEncryptionSalt = null;
    }

    // Show checksum mismatch error
    private void showChecksumMismatchError(java.io.File dbFile) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("⚠️ Checksum Verification Failed");

        String message = "The checksum verification FAILED!\n\n" +
                "POSSIBLE CAUSES:\n" +
                "• File has been tampered with\n" +
                "• File corruption during transfer\n" +
                "• Wrong checksum file selected\n" +
                "• Mismatched database and checksum files\n\n" +
                "RECOMMENDATION:\n" +
                "Do NOT import this database. It may be corrupted or compromised.\n\n" +
                "For security reasons, import has been blocked.";

        builder.setMessage(message);

        builder.setPositiveButton("OK", (dialog, which) -> {
            dbFile.delete();
            clearPendingImport();
            Toast.makeText(this, "Import cancelled for security", Toast.LENGTH_SHORT).show();
        });

        builder.setCancelable(false);
        builder.show();
    }

    // Show file size mismatch warning
    private void showFileSizeMismatchWarning(java.io.File dbFile, String expectedChecksum, long originalFileSize) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("⚠️ File Size Mismatch");

        String message = "The database file size doesn't match the metadata!\n\n" +
                "Expected size: " + originalFileSize + " bytes\n" +
                "Actual size: " + dbFile.length() + " bytes\n\n" +
                "This could indicate:\n" +
                "• File corruption\n" +
                "• Wrong file selected\n" +
                "• Metadata mismatch\n\n" +
                "Do you want to continue with checksum verification anyway?";

        builder.setMessage(message);

        builder.setPositiveButton("Continue Verification", (dialog, which) -> {
            // Calculate and verify checksum despite size mismatch
            addLogMessage("User chose to continue despite size mismatch");
            String actualChecksum = calculateSHA256(dbFile);

            if (actualChecksum != null) {
                boolean checksumValid = verifyChecksum(actualChecksum, expectedChecksum, dbFile.getName());

                if (checksumValid) {
                    Toast.makeText(this, "✓ Checksum verified!", Toast.LENGTH_SHORT).show();
                    clearPendingImport();
                    proceedWithDatabaseImport(dbFile);
                } else {
                    showChecksumMismatchError(dbFile);
                }
            } else {
                Toast.makeText(this, "Failed to calculate checksum", Toast.LENGTH_SHORT).show();
                dbFile.delete();
                clearPendingImport();
            }
        });

        builder.setNegativeButton("Cancel Import", (dialog, which) -> {
            dbFile.delete();
            clearPendingImport();
            Toast.makeText(this, "Import cancelled", Toast.LENGTH_SHORT).show();
        });

        builder.setCancelable(false);
        builder.show();
    }

    // Process validated database import
    private void proceedWithDatabaseImport(java.io.File externalDbFile) {
        try {
            // Check if database is encrypted
            if (DatabaseEncryptionHelper.isDatabaseEncrypted(externalDbFile)) {
                addLogMessage("Encrypted database detected");
                showEncryptedDatabaseImportDialog(externalDbFile);
                return;
            }
            
            // Security validation: Check database integrity and structure
            if (!validateExternalDatabase(externalDbFile)) {
                Toast.makeText(this, "Security error: Invalid or malicious database file!", Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Database validation failed - possible malicious content");
                externalDbFile.delete();
                return;
            }

            // Check if it is a valid SQLite database
            SQLiteDatabase testDb = null;
            try {
                // Open in read-only mode for security
                testDb = SQLiteDatabase.openDatabase(externalDbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

                // Check for required tables
                boolean hasWifiData = hasTable(testDb, "wifi_data");
                boolean hasDeviceData = hasTable(testDb, "device_data");

                if (!hasWifiData && !hasDeviceData) {
                    testDb.close();
                    Toast.makeText(this, "Invalid database: No WiFi or Bluetooth data tables found!", Toast.LENGTH_LONG).show();
                    externalDbFile.delete();
                    return;
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

                // Final variables for Lambda
                final int wifiCount = wifiCountTemp;
                final int bluetoothCount = bluetoothCountTemp;
                final String dbPath = externalDbFile.getAbsolutePath();

                // Confirmation dialog for activation
                android.app.AlertDialog.Builder confirmBuilder = new android.app.AlertDialog.Builder(this);
                confirmBuilder.setTitle(R.string.external_db_as_active_title);
                confirmBuilder.setMessage(
                    "Load external DB as active database?\n\n" +
                    "Existing devices:\n• " + wifiCount + " WiFi networks\n• " + bluetoothCount + " Bluetooth devices\n\n" +
                    "New scans will be added to this DB!"
                );
                confirmBuilder.setPositiveButton(R.string.yes_activate, (d2, w2) -> {
                    switchToExternalDatabase(dbPath, wifiCount, bluetoothCount);
                });
                confirmBuilder.setNegativeButton(R.string.cancel, (d2, w2) -> {
                    externalDbFile.delete();
                });
                confirmBuilder.show();

            } catch (Exception e) {
                if (testDb != null) testDb.close();
                Toast.makeText(this, "Error loading the database: " + e.getMessage(), Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Failed to load database: " + e.getMessage());
                externalDbFile.delete();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Invalid database file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            addLogMessage("ERROR: Invalid database file: " + e.getMessage());
            externalDbFile.delete();
        }
    }

    // Select external database
    private void selectExternalDatabase() {
        // Show lighter security warning for read-only preview
        showReadOnlyImportSecurityWarning();
    }

    // Show security warning dialog for read-only database preview
    private void showReadOnlyImportSecurityWarning() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("⚠️ Security Notice");

        String warningMessage = "You are about to preview an external database file.\n\n" +
                "SECURITY VALIDATIONS APPLIED:\n" +
                "✓ Read-only access (no modifications)\n" +
                "✓ Temporary cache storage\n" +
                "✓ File size and schema validation\n" +
                "✓ Malicious content detection\n\n" +
                "RECOMMENDATION:\n" +
                "Only open databases from trusted sources.\n\n" +
                "Continue with preview?";

        builder.setMessage(warningMessage);

        builder.setPositiveButton("Continue", (dialog, which) -> {
            // User acknowledged, proceed with file picker
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-sqlite3", "*/*"});
            startActivityForResult(intent, IMPORT_DB_REQUEST_CODE);
            addLogMessage("User acknowledged security notice for read-only preview");
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.setCancelable(true);
        builder.show();
    }

    // Select external database as active DB
    private void selectExternalDatabaseAsActive() {
        if (isScanning) {
              Toast.makeText(this, "Please stop scanning first!", Toast.LENGTH_LONG).show();
            return;
        }

        // Show security warning before allowing import
        showImportSecurityWarning();
    }

    // Show security warning dialog before database import
    private void showImportSecurityWarning() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("⚠️ Security Warning");

        String warningMessage = "Importing external database files may pose security risks:\n\n" +
                "POTENTIAL RISKS:\n" +
                "• Malicious or corrupted data\n" +
                "• Data loss or corruption\n" +
                "• Unexpected app behavior\n" +
                "• Privacy concerns from untrusted sources\n\n" +
                "SECURITY VALIDATIONS:\n" +
                "✓ File size limit (max 100MB)\n" +
                "✓ SQL injection detection\n" +
                "✓ Malicious trigger/view blocking\n" +
                "✓ Schema validation\n" +
                "✓ Data sanitization\n" +
                "✓ Range validation\n\n" +
                "RECOMMENDATIONS:\n" +
                "• Only import databases from trusted sources\n" +
                "• Verify file integrity before importing\n" +
                "• Backup your current data regularly\n\n" +
                "Do you understand the risks and wish to proceed?";

        builder.setMessage(warningMessage);

        builder.setPositiveButton("Yes, I Understand - Proceed", (dialog, which) -> {
            // User acknowledged the risks, proceed with file picker
            proceedWithDatabaseImport();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.dismiss();
            Toast.makeText(this, "Import cancelled", Toast.LENGTH_SHORT).show();
        });

        builder.setCancelable(false); // Require explicit choice

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Log security warning display
        addLogMessage("Security warning displayed for database import");
    }

    // Proceed with database import after security warning acknowledged
    private void proceedWithDatabaseImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/x-sqlite3", "*/*"});
        startActivityForResult(intent, IMPORT_ACTIVE_DB_REQUEST_CODE);

        addLogMessage("User acknowledged security risks, file picker opened");
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

            // Security validation: Check database integrity and structure
            if (!validateExternalDatabase(tempFile)) {
                Toast.makeText(this, "Security error: Invalid or malicious database file!", Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Database validation failed - possible malicious content");
                tempFile.delete();
                return;
            }

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

    // Security validation for external databases
    private boolean validateExternalDatabase(java.io.File dbFile) {
        SQLiteDatabase db = null;
        try {
            // Check file size (max 100MB to prevent DoS)
            long maxSize = 100 * 1024 * 1024; // 100MB
            if (dbFile.length() > maxSize) {
                addLogMessage("ERROR: Database file too large (" + (dbFile.length() / 1024 / 1024) + " MB)");
                return false;
            }

            // Open database in read-only mode
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);

            // Check for malicious triggers (SQL injection vectors)
            Cursor triggerCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='trigger'", null);
            if (triggerCursor.getCount() > 0) {
                addLogMessage("ERROR: Database contains triggers (potential security risk)");
                triggerCursor.close();
                return false;
            }
            triggerCursor.close();

            // Check for malicious views (SQL injection vectors)
            Cursor viewCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='view'", null);
            if (viewCursor.getCount() > 0) {
                addLogMessage("ERROR: Database contains views (potential security risk)");
                viewCursor.close();
                return false;
            }
            viewCursor.close();

            // Verify expected tables exist
            boolean hasWifiData = hasTable(db, "wifi_data");
            boolean hasDeviceData = hasTable(db, "device_data");

            if (!hasWifiData && !hasDeviceData) {
                addLogMessage("ERROR: No valid data tables found");
                return false;
            }

            // Check for excessive number of tables (potential abuse)
            Cursor tableCursor = db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table'", null);
            if (tableCursor.moveToFirst()) {
                int tableCount = tableCursor.getInt(0);
                if (tableCount > 10) {
                    addLogMessage("ERROR: Too many tables in database (" + tableCount + ")");
                    tableCursor.close();
                    return false;
                }
            }
            tableCursor.close();

            // Validate table schema for expected tables
            if (hasDeviceData) {
                if (!validateTableSchema(db, "device_data")) {
                    return false;
                }
            }
            if (hasWifiData) {
                if (!validateTableSchema(db, "wifi_data")) {
                    return false;
                }
            }

            addLogMessage("Database validation passed");
            return true;

        } catch (Exception e) {
            addLogMessage("ERROR: Database validation failed: " + e.getMessage());
            return false;
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    // Validate table schema to prevent malicious schema structures
    private boolean validateTableSchema(SQLiteDatabase db, String tableName) {
        try {
            // Whitelist table names to prevent SQL injection via table name
            if (!tableName.equals("wifi_data") && !tableName.equals("device_data")) {
                addLogMessage("ERROR: Invalid table name: " + tableName);
                return false;
            }

            // PRAGMA commands don't support parameterized queries, so we validate the input first
            Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
            int columnCount = cursor.getCount();
            cursor.close();

            // Check for reasonable number of columns
            if (columnCount > 50) {
                addLogMessage("ERROR: Table " + tableName + " has too many columns (" + columnCount + ")");
                return false;
            }

            if (columnCount == 0) {
                addLogMessage("ERROR: Table " + tableName + " has no columns");
                return false;
            }

            return true;
        } catch (Exception e) {
            addLogMessage("ERROR: Schema validation failed for " + tableName + ": " + e.getMessage());
            return false;
        }
    }

    // Sanitize string input to prevent SQL injection
    private String sanitizeString(String input) {
        if (input == null) {
            return null;
        }
        // Remove potentially dangerous characters and limit length
        if (input.length() > 1000) {
            input = input.substring(0, 1000);
        }
        // Remove null bytes and control characters that could cause issues
        return input.replaceAll("[\\x00\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }

    // Calculate SHA-256 checksum for a file
    private String calculateSHA256(java.io.File file) {
        try {
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            String checksum = calculateSHA256FromStream(fis);
            fis.close();
            return checksum;
        } catch (Exception e) {
            addLogMessage("ERROR: Failed to calculate checksum: " + e.getMessage());
            return null;
        }
    }

    // Calculate SHA-256 checksum from InputStream
    private String calculateSHA256FromStream(java.io.InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hashBytes = digest.digest();

            // Convert byte array to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException | java.io.IOException e) {
            addLogMessage("ERROR: Failed to calculate SHA-256: " + e.getMessage());
            return null;
        }
    }

    // Create checksum metadata JSON
    private String createChecksumMetadata(String filename, String checksum, long fileSize) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("version", "1.0");
            metadata.put("algorithm", "SHA-256");
            metadata.put("filename", filename);
            metadata.put("checksum", checksum);
            metadata.put("fileSize", fileSize);
            metadata.put("timestamp", System.currentTimeMillis());
            metadata.put("exportedBy", "WiFi GeoGrabber v" + APP_VERSION);

            // Include encryption metadata if database is encrypted
            metadata.put("encrypted", isDatabaseEncrypted);
            if (isDatabaseEncrypted && encryptionManager != null) {
                byte[] salt = encryptionManager.getCurrentSalt();
                if (salt != null) {
                    // Store salt as Base64 for portability
                    String saltBase64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP);
                    metadata.put("encryptionSalt", saltBase64);
                    addLogMessage("Including encryption salt in metadata for portable import");
                    
                    // DEBUG: Log derived key for troubleshooting
                    String cachedKey = encryptionManager.getCachedDatabaseKey();
                    if (cachedKey != null) {
                        // Log key preview only (first 2 + last 2 chars for security)
                        String keyPreview = cachedKey.length() > 4 ? 
                            cachedKey.substring(0, 2) + "..." + cachedKey.substring(cachedKey.length() - 2) : 
                            "**";
                        Log.i("EXPORT_DEBUG", "═══════════════════════════════════════");
                        Log.i("EXPORT_DEBUG", "DATABASE EXPORT - ENCRYPTION INFO");
                        Log.i("EXPORT_DEBUG", "═══════════════════════════════════════");
                        Log.i("EXPORT_DEBUG", "Salt (Base64): " + saltBase64);
                        Log.i("EXPORT_DEBUG", "Derived Key Preview: " + keyPreview);
                        Log.i("EXPORT_DEBUG", "Key Length: " + cachedKey.length() + " chars");
                        Log.i("EXPORT_DEBUG", "═══════════════════════════════════════");
                        addLogMessage("🔑 Export Key: " + keyPreview + " (length: " + cachedKey.length() + ")");
                    }
                }
            }

            return metadata.toString(2); // Pretty print with indent of 2
        } catch (Exception e) {
            addLogMessage("ERROR: Failed to create metadata: " + e.getMessage());
            return null;
        }
    }

    // Verify checksum from metadata
    private boolean verifyChecksum(String fileChecksum, String metadataChecksum, String filename) {
        if (fileChecksum == null || metadataChecksum == null) {
            return false;
        }

        boolean matches = fileChecksum.equalsIgnoreCase(metadataChecksum);

        if (matches) {
            addLogMessage("✓ Checksum verified successfully for " + filename);
        } else {
            addLogMessage("✗ Checksum verification FAILED for " + filename);
            // Show checksum previews (first 8 + last 8 chars)
            String expectedPreview = metadataChecksum.length() > 16 ? 
                metadataChecksum.substring(0, 8) + "..." + metadataChecksum.substring(metadataChecksum.length() - 8) : 
                metadataChecksum;
            String actualPreview = fileChecksum.length() > 16 ? 
                fileChecksum.substring(0, 8) + "..." + fileChecksum.substring(fileChecksum.length() - 8) : 
                fileChecksum;
            addLogMessage("  Expected: " + expectedPreview);
            addLogMessage("  Actual:   " + actualPreview);
        }

        return matches;
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

            addLogMessage("Database file copied, size: " + (externalDbFile.length() / 1024) + " KB");

            // Offer checksum verification before proceeding
            offerChecksumVerification(uri, externalDbFile);

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
    private void copyDataFromExternalToInternal(SQLiteDatabase externalDb) throws Exception {
        int copiedWifi = 0;
        int copiedBluetooth = 0;
        int copiedLegacyWifi = 0;

        try {
            // Use transaction for atomicity and better error handling
            dbBeginTransaction();

            //1. Copy device_data (WiFi and Bluetooth)
            android.database.Cursor deviceCursor = externalDb.rawQuery("SELECT * FROM device_data LIMIT 50000", null);
            if (deviceCursor.moveToFirst()) {
                do {
                    // Sanitize all string inputs
                    String deviceName = sanitizeString(deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("device_name")));
                    String deviceAddress = sanitizeString(deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("device_address")));
                    String deviceType = sanitizeString(deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("device_type")));
                    int signalStrength = deviceCursor.getInt(deviceCursor.getColumnIndexOrThrow("signal_strength"));
                    String encryptionInfo = sanitizeString(deviceCursor.getString(deviceCursor.getColumnIndexOrThrow("encryption_info")));
                    double latitude = deviceCursor.getDouble(deviceCursor.getColumnIndexOrThrow("latitude"));
                    double longitude = deviceCursor.getDouble(deviceCursor.getColumnIndexOrThrow("longitude"));
                    long timestamp = deviceCursor.getLong(deviceCursor.getColumnIndexOrThrow("timestamp"));

                    // Validate numeric ranges
                    if (signalStrength < -150 || signalStrength > 0) {
                        continue; // Skip invalid signal strength
                    }
                    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                        continue; // Skip invalid coordinates
                    }
                    if (timestamp < 0 || timestamp > System.currentTimeMillis() + 86400000) {
                        continue; // Skip invalid timestamps (future dates not allowed)
                    }

                    // Validate device type
                    if (deviceType != null && !deviceType.equals("WIFI") && !deviceType.equals("BLUETOOTH")) {
                        continue; // Skip invalid device types
                    }

                    // Extended fields (if available) - sanitize all strings
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
                    String channelWidth = channelWidthIdx >= 0 ? sanitizeString(deviceCursor.getString(channelWidthIdx)) : null;
                    String capabilities = capabilitiesIdx >= 0 ? sanitizeString(deviceCursor.getString(capabilitiesIdx)) : null;
                    int centerFreq0 = centerFreq0Idx >= 0 ? deviceCursor.getInt(centerFreq0Idx) : 0;
                    int centerFreq1 = centerFreq1Idx >= 0 ? deviceCursor.getInt(centerFreq1Idx) : 0;
                    String wifiStandard = wifiStandardIdx >= 0 ? sanitizeString(deviceCursor.getString(wifiStandardIdx)) : null;
                    String vendorInfo = vendorInfoIdx >= 0 ? sanitizeString(deviceCursor.getString(vendorInfoIdx)) : null;
                    int maxSpeed = maxSpeedIdx >= 0 ? deviceCursor.getInt(maxSpeedIdx) : 0;
                    
                    // Check if the device already exists
                    if (!existsInDeviceDb(deviceAddress, deviceType)) {
                        // Add device to internal database
                        dbExecSQL("INSERT INTO device_data (device_name, device_address, device_type, signal_strength, encryption_info, frequency, channel, channel_width, capabilities, center_freq0, center_freq1, wifi_standard, vendor_info, max_connection_speed, latitude, longitude, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
                android.database.Cursor wifiCursor = externalDb.rawQuery("SELECT * FROM wifi_data LIMIT 50000", null);
                if (wifiCursor.moveToFirst()) {
                    do {
                        // Sanitize all string inputs
                        String ssid = sanitizeString(wifiCursor.getString(wifiCursor.getColumnIndexOrThrow("ssid")));
                        String bssid = sanitizeString(wifiCursor.getString(wifiCursor.getColumnIndexOrThrow("bssid")));
                        int signalStrength = wifiCursor.getInt(wifiCursor.getColumnIndexOrThrow("signal_strength"));
                        String encryption = sanitizeString(wifiCursor.getString(wifiCursor.getColumnIndexOrThrow("encryption")));
                        double latitude = wifiCursor.getDouble(wifiCursor.getColumnIndexOrThrow("latitude"));
                        double longitude = wifiCursor.getDouble(wifiCursor.getColumnIndexOrThrow("longitude"));
                        long timestamp = wifiCursor.getLong(wifiCursor.getColumnIndexOrThrow("timestamp"));

                        // Validate numeric ranges
                        if (signalStrength < -150 || signalStrength > 0) {
                            continue; // Skip invalid signal strength
                        }
                        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                            continue; // Skip invalid coordinates
                        }
                        if (timestamp < 0 || timestamp > System.currentTimeMillis() + 86400000) {
                            continue; // Skip invalid timestamps
                        }

                        // Extended fields (if available) - sanitize all strings
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
                        String capabilities = capabilitiesIdx >= 0 ? sanitizeString(wifiCursor.getString(capabilitiesIdx)) : null;
                        String wifiStandard = wifiStandardIdx >= 0 ? sanitizeString(wifiCursor.getString(wifiStandardIdx)) : null;
                        String vendorInfo = vendorInfoIdx >= 0 ? sanitizeString(wifiCursor.getString(vendorInfoIdx)) : null;
                        String channelWidth = channelWidthIdx >= 0 ? sanitizeString(wifiCursor.getString(channelWidthIdx)) : null;
                        int centerFreq0 = centerFreq0Idx >= 0 ? wifiCursor.getInt(centerFreq0Idx) : 0;
                        int centerFreq1 = centerFreq1Idx >= 0 ? wifiCursor.getInt(centerFreq1Idx) : 0;
                        int maxSpeed = maxSpeedIdx >= 0 ? wifiCursor.getInt(maxSpeedIdx) : 0;
                        
                        // Check if the BSSID already exists (in wifi_data or device_data)
                        if (!existsInDb(bssid) && !existsInDeviceDb(bssid, "WIFI")) {
                            // Add to the wifi_data table
                            dbExecSQL("INSERT INTO wifi_data (ssid, bssid, signal_strength, encryption, latitude, longitude, timestamp, frequency, channel, capabilities, wifi_standard, vendor_info, channel_width, center_freq0, center_freq1, max_connection_speed) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                    new Object[]{ssid, bssid, signalStrength, encryption, latitude, longitude, timestamp, frequency, channel, capabilities, wifiStandard, vendorInfo, channelWidth, centerFreq0, centerFreq1, maxSpeed});
                            copiedLegacyWifi++;
                        }
                    } while (wifiCursor.moveToNext());
                }
                wifiCursor.close();
            }
            
            // Commit transaction if successful
            dbSetTransactionSuccessful();
            dbEndTransaction();

            addLogMessage("Data transfer completed:");
            addLogMessage("• " + copiedWifi + " WiFi devices (device_data)");
            addLogMessage("• " + copiedBluetooth + " Bluetooth devices");
            addLogMessage("• " + copiedLegacyWifi + " Legacy WiFi entries");
        } catch (Exception e) {
            // Rollback transaction on error
            if (dbInTransaction()) {
                dbEndTransaction();
            }
            addLogMessage("Error while copying data: " + e.getMessage());
            throw e;
        }

        }

    // Auxiliary methods for database transfer

    // Show number of networks
    private void showNetworkCount() {
        // Count devices from the new device_data table
        Cursor deviceCursor = dbRawQuery("SELECT COUNT(*) FROM device_data", null);
        int deviceCount = 0;
        if (deviceCursor != null) {
            if (deviceCursor.moveToFirst()) {
                deviceCount = deviceCursor.getInt(0);
            }
            deviceCursor.close();
        }
        
        // Count WiFi devices
        Cursor wifiCursor = dbRawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'WIFI'", null);
        int wifiCount = 0;
        if (wifiCursor != null) {
            if (wifiCursor.moveToFirst()) {
                wifiCount = wifiCursor.getInt(0);
            }
            wifiCursor.close();
        }
        
        // Count Bluetooth devices
        Cursor bluetoothCursor = dbRawQuery("SELECT COUNT(*) FROM device_data WHERE device_type = 'BLUETOOTH'", null);
        int bluetoothCount = 0;
        if (bluetoothCursor != null) {
            if (bluetoothCursor.moveToFirst()) {
                bluetoothCount = bluetoothCursor.getInt(0);
            }
            bluetoothCursor.close();
        }
        
        // Count old WiFi data
        Cursor oldWifiCursor = dbRawQuery("SELECT COUNT(*) FROM wifi_data", null);
        int oldWifiCount = 0;
        if (oldWifiCursor != null) {
            if (oldWifiCursor.moveToFirst()) {
                oldWifiCount = oldWifiCursor.getInt(0);
            }
            oldWifiCursor.close();
        }
        
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
        
        // Clear encryption passphrase from memory
        if (encryptionManager != null) {
            encryptionManager.clearPassphrase();
        }
        if (encryptedDbHelper != null) {
            encryptedDbHelper.clearPassphrase();
        }
        
        dbClose();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Do NOT clear passphrase here - onPause is called when navigating to MapActivity
        // Passphrase will be cleared in onStop() when app actually goes to background
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        // Clear passphrase when app goes to background for security
        // EXCEPT when scanning is active - then keep passphrase to allow background writes
        // EXCEPT when navigating internally to MapActivity - keep passphrase for internal navigation
        
        if (encryptionManager != null && encryptionManager.isEncryptionEnabled() && 
            !isScanning && !isNavigatingInternally) {
            encryptionManager.clearPassphrase();
            if (encryptedDbHelper != null) {
                encryptedDbHelper.clearPassphrase();
            }
            Log.i("MainActivity", "Passphrase cleared (app in background, not scanning)");
        } else if (isScanning) {
            Log.i("MainActivity", "Passphrase kept in cache (background scanning active)");
        } else if (isNavigatingInternally) {
            Log.i("MainActivity", "Passphrase kept in cache (internal navigation to MapActivity)");
        }
    }

    // Update button text according to status
        private void updateToggleScanButton() {
            if (toggleScanButton != null) {
                if (isScanning) {
                    toggleScanButton.setText("Stop WiFi_scan");
                } else {
                    toggleScanButton.setText("Start WiFi_scan");
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
    
    // ====================================================================
    //  DATABASE ENCRYPTION METHODS
    // ====================================================================
    
    /**
     * Delete database file and all associated files
     */
    private void deleteDatabaseFiles() {
        try {
            java.io.File dbFile = getDatabasePath("wifi_scanner.db");

            // Delete main database file
            if (dbFile.exists()) {
                boolean deleted = dbFile.delete();
                android.util.Log.d("MainActivity", "Deleted old database file: " + deleted);
            }

            // Delete associated files
            java.io.File journalFile = new java.io.File(dbFile.getAbsolutePath() + "-journal");
            if (journalFile.exists()) journalFile.delete();

            java.io.File walFile = new java.io.File(dbFile.getAbsolutePath() + "-wal");
            if (walFile.exists()) walFile.delete();

            java.io.File shmFile = new java.io.File(dbFile.getAbsolutePath() + "-shm");
            if (shmFile.exists()) shmFile.delete();

        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error deleting database files: " + e.getMessage());
        }
    }

    /**
     * Initialize database with encryption support
     */
    private void initializeDatabase() {
        // Check if encryption is enabled
        if (encryptionManager.isEncryptionEnabled()) {
            isDatabaseEncrypted = true;

            // Check if passphrase is cached
            if (!encryptionManager.isPassphraseCached()) {
                // Show black overlay immediately
                if (securityOverlay != null) {
                    securityOverlay.setVisibility(View.VISIBLE);
                    securityOverlay.bringToFront();
                }
                
                // Hide data view until unlocked
                if (dataListView != null) {
                    dataListView.setVisibility(View.GONE);
                }
                // Show unlock dialog
                showUnlockDialog();
            } else {
                // Open encrypted database
                initializeEncryptedDatabase();
            }
        } else {
            // Check if this is first launch
            if (!encryptionManager.isPassphraseSet()) {
                // Show first-launch encryption prompt
                showFirstLaunchEncryptionDialog();
            }

            // Use standard database
            DatabaseHelper dbHelper = new DatabaseHelper(this);
            database = dbHelper.getWritableDatabase();
        }

        // Update encryption status indicator
        updateEncryptionStatus();
    }
    
    /**
     * Update encryption status indicator
     */
    private void updateEncryptionStatus() {
        if (encryptionStatusText != null) {
            if (encryptionManager.isEncryptionEnabled()) {
                String status = encryptionManager.isPassphraseCached() ? 
                    getString(R.string.encryption_unlocked) : 
                    getString(R.string.encryption_locked);
                encryptionStatusText.setText("🔒 Database: " + status);
                encryptionStatusText.setVisibility(View.VISIBLE);
            } else {
                encryptionStatusText.setText(getString(R.string.encryption_disabled));
                encryptionStatusText.setVisibility(View.GONE); // Hide if not encrypted
            }
        }
    }
    
    /**
     * Initialize encrypted database
     * Automatically handles corrupted databases by recreating them
     */
    private void initializeEncryptedDatabase() {
        String dbKey = encryptionManager.getCachedDatabaseKey();
        if (dbKey != null) {
            encryptedDbHelper = new DatabaseEncryptionHelper(this, dbKey);
            database = encryptedDbHelper.openEncryptedDatabase();

            if (database == null) {
                // This should not happen anymore as openEncryptedDatabase() auto-recovers
                // But if it does, it means wrong passphrase
                android.util.Log.e("MainActivity", "Failed to open encrypted database even after auto-recovery");
                Toast.makeText(this, R.string.wrong_passphrase, Toast.LENGTH_LONG).show();
                finish();
            } else {
                // Verify database is writable
                net.sqlcipher.database.SQLiteDatabase encDb = (net.sqlcipher.database.SQLiteDatabase) database;
                if (!encDb.isOpen()) {
                    android.util.Log.e("MainActivity", "Encrypted database is not open");
                    Toast.makeText(this, "Database error - not open", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                if (encDb.isReadOnly()) {
                    android.util.Log.e("MainActivity", "Encrypted database is readonly");
                    Toast.makeText(this, "Database error - readonly. Please clear app data.", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                isDatabaseEncrypted = true;
                android.util.Log.d("MainActivity", "Encrypted database opened successfully and is writable");
            }
        } else {
            Toast.makeText(this, R.string.database_locked, Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    /**
     * Show first-launch encryption prompt
     */
    private void showFirstLaunchEncryptionDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.first_launch_encryption_title);
        builder.setMessage(R.string.first_launch_encryption_message);
        builder.setCancelable(false);
        
        builder.setPositiveButton(R.string.enable_now, (dialog, which) -> {
            showSetupEncryptionDialog();
        });
        
        builder.setNegativeButton(R.string.maybe_later, (dialog, which) -> {
            // Continue with unencrypted database
        });
        
        builder.show();
    }
    
    /**
     * Show encryption setup dialog
     */
    private void showSetupEncryptionDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.setup_encryption_title);
        builder.setMessage(R.string.setup_encryption_message);
        
        // Create custom layout for passphrase input
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final android.widget.EditText passphraseInput = new android.widget.EditText(this);
        passphraseInput.setHint(R.string.passphrase_hint);
        passphraseInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                                     android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(passphraseInput);
        
        final android.widget.EditText confirmInput = new android.widget.EditText(this);
        confirmInput.setHint(R.string.confirm_passphrase_hint);
        confirmInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                                   android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmInput);
        
        android.widget.CheckBox showPassphraseBox = new android.widget.CheckBox(this);
        showPassphraseBox.setText(R.string.show_passphrase);
        showPassphraseBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int inputType = isChecked ? 
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
            passphraseInput.setInputType(inputType);
            confirmInput.setInputType(inputType);
        });
        layout.addView(showPassphraseBox);
        
        builder.setView(layout);
        
        builder.setPositiveButton(R.string.set_passphrase, (dialog, which) -> {
            String passphrase = passphraseInput.getText().toString();
            String confirm = confirmInput.getText().toString();
            
            if (passphrase.length() < 6) {
                Toast.makeText(this, R.string.passphrase_too_short, Toast.LENGTH_LONG).show();
                showSetupEncryptionDialog(); // Show again
                return;
            }
            
            if (!passphrase.equals(confirm)) {
                Toast.makeText(this, R.string.passphrases_dont_match, Toast.LENGTH_LONG).show();
                showSetupEncryptionDialog(); // Show again
                return;
            }
            
            // Set up encryption
            setupEncryption(passphrase.toCharArray());
        });
        
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    
    /**
     * Set up encryption with passphrase
     */
    private void setupEncryption(final char[] passphrase) {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;
            
            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivity.this, 
                    getString(R.string.migration_title),
                    getString(R.string.migration_in_progress), 
                    true
                );
            }
            
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    // Setup encryption manager
                    if (!encryptionManager.setupEncryption(passphrase)) {
                        return false;
                    }
                    
                    // Get database paths
                    String unencryptedPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                    String encryptedPath = getDatabasePath("wifi_scanner_encrypted.db").getAbsolutePath();
                    
                    // Get encryption key
                    String dbKey = encryptionManager.getCachedDatabaseKey();
                    
                    if (dbKey == null) {
                        return false;
                    }
                    
                    // Close current database if open
                    if (database != null) {
                        dbClose();
                    }
                    
                    // Check if unencrypted database exists
                    java.io.File unencryptedFile = new java.io.File(unencryptedPath);
                    if (unencryptedFile.exists() && unencryptedFile.length() > 0) {
                        // Migrate database
                        boolean success = DatabaseEncryptionHelper.migrateToEncrypted(
                            MainActivity.this, unencryptedPath, encryptedPath, dbKey);
                        
                        if (success) {
                            // Delete unencrypted database
                            unencryptedFile.delete();
                            
                            // Rename encrypted database
                            new java.io.File(encryptedPath).renameTo(unencryptedFile);
                        }
                        
                        return success;
                    } else {
                        // No existing database, just create encrypted one
                        Log.d("MainActivity", "No existing database found, creating new encrypted database");
                        
                        // Make sure there's no corrupted database file
                        if (unencryptedFile.exists()) {
                            Log.d("MainActivity", "Deleting empty/corrupted database file");
                            unencryptedFile.delete();
                        }
                        
                        encryptedDbHelper = new DatabaseEncryptionHelper(MainActivity.this, dbKey);
                        net.sqlcipher.database.SQLiteDatabase db = encryptedDbHelper.openEncryptedDatabase();
                        
                        if (db != null) {
                            Log.d("MainActivity", "New encrypted database created successfully");
                            // Force onCreate to be called by accessing a table
                            try {
                                db.execSQL("SELECT COUNT(*) FROM wifi_data");
                            } catch (Exception e) {
                                Log.d("MainActivity", "onCreate will be called automatically");
                            }
                            db.close();
                            return true;
                        } else {
                            Log.e("MainActivity", "Failed to create new encrypted database");
                            return false;
                        }
                    }
                    
                } catch (Exception e) {
                    Log.e("MainActivity", "Encryption setup error", e);
                    return false;
                }
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();
                
                if (success) {
                    Toast.makeText(MainActivity.this, R.string.migration_success, 
                                  Toast.LENGTH_LONG).show();
                    isDatabaseEncrypted = true;
                    initializeEncryptedDatabase();
                    updateEncryptionStatus();
                } else {
                    Toast.makeText(MainActivity.this, R.string.migration_failed, 
                                  Toast.LENGTH_LONG).show();
                    encryptionManager.disableEncryption();
                    
                    // Fall back to standard database
                    DatabaseHelper dbHelper = new DatabaseHelper(MainActivity.this);
                    database = dbHelper.getWritableDatabase();
                    updateEncryptionStatus();
                }
            }
        }.execute();
    }
    
    /**
     * Show unlock dialog
     */
    private void showUnlockDialog() {
        showUnlockDialog(0); // Start with 0 attempts
    }
    
    /**
     * Show unlock dialog with retry capability
     * @param attemptCount Number of failed attempts
     */
    private void showUnlockDialog(final int attemptCount) {
        // Show security overlay to hide app content
        if (securityOverlay != null) {
            securityOverlay.setVisibility(View.VISIBLE);
            securityOverlay.bringToFront();
        }
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.unlock_database_title);
        
        // Show attempt count if not first attempt
        String message = getString(R.string.unlock_database_message);
        if (attemptCount > 0) {
            message += "\n\n❌ Wrong passphrase! Attempt " + (attemptCount + 1) + "/5";
        }
        builder.setMessage(message);
        builder.setCancelable(false);
        
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.passphrase_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                           android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(input);
        
        builder.setView(layout);
        
        builder.setPositiveButton(R.string.unlock, (dialog, which) -> {
            String passphrase = input.getText().toString();
            
            if (encryptionManager.unlockWithPassphrase(passphrase.toCharArray())) {
                // Successful unlock - reset failed attempts
                encryptionManager.resetFailedAttempts();
                
                // Hide security overlay - show app content
                if (securityOverlay != null) {
                    securityOverlay.setVisibility(View.GONE);
                }
                
                initializeEncryptedDatabase();
                // Show data view after successful unlock
                if (dataListView != null) {
                    dataListView.setVisibility(View.VISIBLE);
                }
                Toast.makeText(this, "✓ Database unlocked", Toast.LENGTH_SHORT).show();
                
                // Log database status after unlock
                if (database != null) {
                    try {
                        // Count data in database
                        Cursor wifiCursor = dbRawQuery("SELECT COUNT(*) FROM wifi_data", null);
                        int wifiCount = 0;
                        if (wifiCursor != null) {
                            if (wifiCursor.moveToFirst()) wifiCount = wifiCursor.getInt(0);
                            wifiCursor.close();
                        }
                        
                        Cursor btCursor = dbRawQuery("SELECT COUNT(*) FROM device_data WHERE device_type='BLUETOOTH'", null);
                        int btCount = 0;
                        if (btCursor != null) {
                            if (btCursor.moveToFirst()) btCount = btCursor.getInt(0);
                            btCursor.close();
                        }
                        
                        addLogMessage("📊 After unlock - DB contains: " + wifiCount + " WiFi + " + btCount + " BT");
                        Log.i("MainActivity", "After unlock - DB contains: " + wifiCount + " WiFi + " + btCount + " BT");
                        
                        // Update total count display
                        updateTotalNetworksCount();
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error counting data after unlock: " + e.getMessage());
                    }
                }
            } else {
                // Wrong passphrase - increment failed attempts
                encryptionManager.incrementFailedAttempts();
                
                // Check if database should be wiped
                if (encryptionManager.shouldWipeDatabase()) {
                    Toast.makeText(this, "⚠️ Too many failed attempts. Database will be wiped for security.", 
                                  Toast.LENGTH_LONG).show();
                    
                    // Keep overlay visible during wipe
                    // Wipe database after short delay
                    new android.os.Handler().postDelayed(() -> {
                        resetEncryption();
                        // Overlay will be removed after reset shows new setup dialog
                        if (securityOverlay != null) {
                            securityOverlay.setVisibility(View.GONE);
                        }
                    }, 2000);
                    return;
                }
                
                // Show remaining attempts
                int remaining = encryptionManager.getRemainingAttempts();
                int newAttemptCount = attemptCount + 1;
                
                Toast.makeText(this, "✗ Wrong passphrase. " + remaining + " attempts remaining.", 
                              Toast.LENGTH_LONG).show();
                
                // Show dialog again with retry
                showUnlockDialog(newAttemptCount);
            }
        });
        
        // Add cancel button after first attempt
        if (attemptCount > 0) {
            builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
                // Keep overlay visible and close app
                finish();
            });
        }
        
        // Don't allow dismissal without action (keep overlay visible)
        builder.setCancelable(false);
        
        builder.show();
    }
    
    /**
     * Show encryption settings dialog
     */
    private void showEncryptionSettingsDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.encryption_settings);

        String status = encryptionManager.isEncryptionEnabled() ?
            getString(R.string.encryption_enabled) : getString(R.string.encryption_disabled);

        String[] options = encryptionManager.isEncryptionEnabled() ?
            new String[]{getString(R.string.change_passphrase), getString(R.string.disable_encryption)} :
            new String[]{getString(R.string.enable_encryption)};

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 10);

        android.widget.TextView statusText = new android.widget.TextView(this);
        statusText.setText(getString(R.string.encryption_status) + ": " + status + "\n\nOptions:");
        layout.addView(statusText);

        android.widget.ListView optionsList = new android.widget.ListView(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options);
        optionsList.setAdapter(adapter);
        layout.addView(optionsList);

        if (encryptionManager.isEncryptionEnabled()) {
            android.widget.Button resetButton = new android.widget.Button(this);
            resetButton.setText("Encryption Reset");
            resetButton.setOnClickListener(v -> {
                showEncryptionResetDialog();
            });
            layout.addView(resetButton);
        }

        builder.setView(layout);
        builder.setNegativeButton(R.string.cancel, null);
        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        optionsList.setOnItemClickListener((parent, view, position, id) -> {
            if (encryptionManager.isEncryptionEnabled()) {
                if (position == 0) {
                    showChangePassphraseDialog();
                } else if (position == 1) {
                    showDisableEncryptionDialog();
                }
            } else {
                showSetupEncryptionDialog();
            }
            dialog.dismiss();
        });
    }
    
    /**
     * Show change passphrase dialog
     */
    private void showChangePassphraseDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.change_passphrase_title);
        builder.setMessage(R.string.change_passphrase_message);
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final android.widget.EditText currentInput = new android.widget.EditText(this);
        currentInput.setHint(R.string.current_passphrase_hint);
        currentInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                                  android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(currentInput);
        
        final android.widget.EditText newInput = new android.widget.EditText(this);
        newInput.setHint(R.string.new_passphrase_hint);
        newInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                              android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(newInput);
        
        final android.widget.EditText confirmInput = new android.widget.EditText(this);
        confirmInput.setHint(R.string.confirm_passphrase_hint);
        confirmInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                                   android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(confirmInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton(R.string.change_passphrase, (dialog, which) -> {
            String current = currentInput.getText().toString();
            String newPass = newInput.getText().toString();
            String confirm = confirmInput.getText().toString();
            
            if (newPass.length() < 6) {
                Toast.makeText(this, R.string.passphrase_too_short, Toast.LENGTH_LONG).show();
                return;
            }
            
            if (!newPass.equals(confirm)) {
                Toast.makeText(this, R.string.passphrases_dont_match, Toast.LENGTH_LONG).show();
                return;
            }
            
            changePassphrase(current.toCharArray(), newPass.toCharArray());
        });
        
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    
    /**
     * Change encryption passphrase
     */
    private void changePassphrase(final char[] oldPassphrase, final char[] newPassphrase) {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;
            
            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivity.this, 
                    getString(R.string.change_passphrase_title),
                    "Re-encrypting database...", 
                    true
                );
            }
            
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    // Change passphrase in encryption manager
                    if (!encryptionManager.changePassphrase(oldPassphrase, newPassphrase)) {
                        return false;
                    }
                    
                    // Get old and new keys
                    String newKey = encryptionManager.getCachedDatabaseKey();
                    
                    // Reconstruct old key for database re-encryption
                    EncryptionManager tempManager = new EncryptionManager(MainActivity.this);
                    if (!tempManager.unlockWithPassphrase(oldPassphrase)) {
                        return false;
                    }
                    String oldKey = tempManager.getCachedDatabaseKey();
                    tempManager.clearPassphrase();
                    
                    // Change database encryption key
                    String dbPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                    
                    // Close database
                    if (database != null) {
                        dbClose();
                    }
                    
                    boolean success = DatabaseEncryptionHelper.changeEncryptionKey(
                        dbPath, oldKey, newKey);
                    
                    return success;
                    
                } catch (Exception e) {
                    Log.e("MainActivity", "Error changing passphrase", e);
                    return false;
                }
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();
                
                if (success) {
                    Toast.makeText(MainActivity.this, R.string.encryption_key_changed, 
                                  Toast.LENGTH_LONG).show();
                    // Reinitialize database with new key
                    initializeEncryptedDatabase();
                    updateEncryptionStatus();
                } else {
                    Toast.makeText(MainActivity.this, R.string.encryption_key_change_failed, 
                                  Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }
    
    /**
     * Show encryption reset dialog
     */
    private void showEncryptionResetDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Encryption Reset");
        builder.setMessage("This will reset the encryption and allow you to set up a new passphrase.\n\n" +
                "⚠️ WARNING:\n" +
                "• Current database will be decrypted first\n" +
                "• Then re-encrypted with new passphrase\n" +
                "• All data will be preserved\n" +
                "• Background service will be stopped\n\n" +
                "Do you want to continue?");
        
        builder.setPositiveButton("Reset Encryption", (dialog, which) -> {
            resetEncryption();
        });
        
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    
    /**
     * Reset encryption (decrypt then show setup dialog)
     */
    private void resetEncryption() {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;
            
            @Override
            protected void onPreExecute() {
                // Stop all scanning activities
                if (isScanning) {
                    stopScanning();
                }
                stopService(new Intent(MainActivity.this, ScanService.class));
                
                progressDialog = android.app.ProgressDialog.show(
                    MainActivity.this, 
                    "Resetting Encryption",
                    "Deleting encrypted database...", 
                    true
                );
            }
            
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    // Close current database
                    if (database != null) {
                        dbClose();
                        database = null;
                    }
                    
                    // Wait for database to close
                    Thread.sleep(500);
                    
                    // Delete the encrypted database files
                    String dbPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                    java.io.File dbFile = new java.io.File(dbPath);
                    java.io.File dbJournal = new java.io.File(dbPath + "-journal");
                    java.io.File dbWal = new java.io.File(dbPath + "-wal");
                    java.io.File dbShm = new java.io.File(dbPath + "-shm");
                    
                    boolean success = true;
                    if (dbFile.exists()) {
                        success = dbFile.delete();
                        Log.d("MainActivity", "Deleted database file: " + success);
                    }
                    if (dbJournal.exists()) {
                        dbJournal.delete();
                    }
                    if (dbWal.exists()) {
                        dbWal.delete();
                    }
                    if (dbShm.exists()) {
                        dbShm.delete();
                    }
                    
                    if (!success) {
                        Log.e("MainActivity", "Failed to delete encrypted database");
                        return false;
                    }
                    
                    // Clear encryption manager
                    encryptionManager.clearPassphrase();
                    encryptionManager.disableEncryption();
                    
                    return true;
                    
                } catch (Exception e) {
                    Log.e("MainActivity", "Encryption reset error", e);
                    return false;
                }
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();
                
                if (success) {
                    Toast.makeText(MainActivity.this, "Encryption reset successful. Database deleted. You can now set up new encryption.", 
                                  Toast.LENGTH_LONG).show();
                    
                    // Reset encryption flags
                    isDatabaseEncrypted = false;
                    
                    // Re-initialize database as unencrypted
                    initializeDatabase();
                    
                    // Show setup dialog to offer encryption again
                    showSetupEncryptionDialog();
                } else {
                    Toast.makeText(MainActivity.this, "Encryption reset failed. Please try again.", 
                                  Toast.LENGTH_LONG).show();
                    
                    // Try to recover by opening whatever database we have
                    initializeDatabase();
                }
            }
        }.execute();
    }
    
    /**
     * Show disable encryption dialog
     */
    private void showDisableEncryptionDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.disable_encryption_title);
        builder.setMessage(R.string.disable_encryption_message);
        
        builder.setPositiveButton(R.string.yes_disable, (dialog, which) -> {
            disableEncryption();
        });
        
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    
    /**
     * Disable encryption (decrypt database)
     */
    private void disableEncryption() {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;
            
            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivity.this, 
                    getString(R.string.disable_encryption_title),
                    getString(R.string.decryption_in_progress), 
                    true
                );
            }
            
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    String encryptedPath = getDatabasePath("wifi_scanner.db").getAbsolutePath();
                    String unencryptedPath = getDatabasePath("wifi_scanner_unencrypted.db").getAbsolutePath();
                    
                    String dbKey = encryptionManager.getCachedDatabaseKey();
                    
                    // Close database
                    if (database != null) {
                        dbClose();
                    }
                    
                    // Decrypt database
                    boolean success = DatabaseEncryptionHelper.decryptDatabase(
                        MainActivity.this, encryptedPath, unencryptedPath, dbKey);
                    
                    if (success) {
                        // Delete encrypted database
                        new java.io.File(encryptedPath).delete();
                        
                        // Rename unencrypted database
                        new java.io.File(unencryptedPath).renameTo(new java.io.File(encryptedPath));
                        
                        // Disable encryption in manager
                        encryptionManager.disableEncryption();
                    }
                    
                    return success;
                    
                } catch (Exception e) {
                    Log.e("MainActivity", "Error disabling encryption", e);
                    return false;
                }
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();
                
                if (success) {
                    Toast.makeText(MainActivity.this, R.string.decryption_success, 
                                  Toast.LENGTH_LONG).show();
                    isDatabaseEncrypted = false;
                    
                    // Reinitialize with standard database
                    DatabaseHelper dbHelper = new DatabaseHelper(MainActivity.this);
                    database = dbHelper.getWritableDatabase();
                    updateEncryptionStatus();
                } else {
                    Toast.makeText(MainActivity.this, R.string.decryption_failed, 
                                  Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }
    
    /**
     * Show dialog for importing encrypted database
     */
    private void showEncryptedDatabaseImportDialog(final java.io.File encryptedDbFile) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.encrypted_db_detected);
        builder.setMessage(R.string.encrypted_db_detected_message);
        
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.passphrase_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                           android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(input);
        
        builder.setView(layout);
        
        builder.setPositiveButton("Import", (dialog, which) -> {
            String passphrase = input.getText().toString();
            importEncryptedDatabase(encryptedDbFile, passphrase);
        });
        
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> {
            encryptedDbFile.delete();
        });
        
        builder.show();
    }
    
    /**
     * Import encrypted database
     */
    private void importEncryptedDatabase(final java.io.File encryptedDbFile, final String passphrase) {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;
            int wifiCount = 0;
            int bluetoothCount = 0;
            String derivedKey = null;  // Store derived key for later use
            String originalPassphrase = passphrase;  // Store original passphrase for caching

            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivity.this,
                    "Importing Database",
                    "Verifying encrypted database...",
                    true
                );
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                net.sqlcipher.database.SQLiteDatabase testDb = null;
                try {
                    net.sqlcipher.database.SQLiteDatabase.loadLibs(MainActivity.this);

                    // If we have salt from metadata, derive the key properly
                    if (pendingEncryptionSalt != null) {
                        Log.i("MainActivity", "Using encryption salt from metadata to derive key");
                        
                        // DEBUG: Log import parameters
                        String saltBase64 = android.util.Base64.encodeToString(pendingEncryptionSalt, android.util.Base64.NO_WRAP);
                        Log.i("IMPORT_DEBUG", "═══════════════════════════════════════");
                        Log.i("IMPORT_DEBUG", "DATABASE IMPORT - ENCRYPTION INFO");
                        Log.i("IMPORT_DEBUG", "═══════════════════════════════════════");
                        Log.i("IMPORT_DEBUG", "Salt (Base64): " + saltBase64);
                        Log.i("IMPORT_DEBUG", "Passphrase Length: " + passphrase.length() + " chars");
                        
                        char[] passphraseChars = passphrase.toCharArray();
                        String hexKey = encryptionManager.deriveKeyWithCustomSalt(passphraseChars, pendingEncryptionSalt);
                        java.util.Arrays.fill(passphraseChars, '\0');  // Clear passphrase from memory
                        
                        // SQLCipher expects hex keys in the format: x'<hex-string>'
                        derivedKey = "x'" + hexKey + "'";
                        
                        // Log key preview only (first 2 + last 2 chars for security)
                        String keyPreview = hexKey.length() > 4 ? 
                            hexKey.substring(0, 2) + "..." + hexKey.substring(hexKey.length() - 2) : 
                            "**";
                        Log.i("IMPORT_DEBUG", "Derived Key Preview: " + keyPreview);
                        Log.i("IMPORT_DEBUG", "Key Length: " + hexKey.length() + " chars");
                        Log.i("IMPORT_DEBUG", "═══════════════════════════════════════");
                        
                        // Log to UI (must be done on UI thread)
                        final String keyPreviewForLog = keyPreview;
                        final int keyLength = hexKey.length();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                addLogMessage("🔑 Import Key: " + keyPreviewForLog + " (length: " + keyLength + ")");
                            }
                        });
                        
                        Log.i("MainActivity", "Derived hex key for SQLCipher (length: " + hexKey.length() + ")");
                        Log.i("MainActivity", "Attempting to open database with derived key...");

                        // Open database with derived key in hex format
                        testDb = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                            encryptedDbFile.getAbsolutePath(), derivedKey, null,
                            net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY);
                    } else {
                        // Fallback: Try raw passphrase (backwards compatibility)
                        Log.i("MainActivity", "No salt in metadata - trying raw passphrase");
                        Log.i("IMPORT_DEBUG", "═══════════════════════════════════════");
                        Log.i("IMPORT_DEBUG", "DATABASE IMPORT - NO SALT (LEGACY MODE)");
                        Log.i("IMPORT_DEBUG", "═══════════════════════════════════════");
                        Log.i("IMPORT_DEBUG", "Using raw passphrase (no PBKDF2 derivation)");
                        Log.i("IMPORT_DEBUG", "═══════════════════════════════════════");
                        derivedKey = passphrase;
                        testDb = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                            encryptedDbFile.getAbsolutePath(), derivedKey, null,
                            net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY);
                    }                    // Check for required tables
                    boolean hasWifiData = hasEncryptedTable(testDb, "wifi_data");
                    boolean hasDeviceData = hasEncryptedTable(testDb, "device_data");
                    
                    if (!hasWifiData && !hasDeviceData) {
                        return false;
                    }
                    
                    // Count data
                    android.database.Cursor cursor = testDb.rawQuery(
                        "SELECT COUNT(*) FROM device_data WHERE device_type='WIFI' AND latitude != 0 AND longitude != 0", null);
                    if (cursor.moveToFirst()) wifiCount += cursor.getInt(0);
                    cursor.close();

                    cursor = testDb.rawQuery(
                        "SELECT COUNT(*) FROM device_data WHERE device_type='BLUETOOTH' AND latitude != 0 AND longitude != 0", null);
                    if (cursor.moveToFirst()) bluetoothCount = cursor.getInt(0);
                    cursor.close();
                    
                    if (hasWifiData) {
                        cursor = testDb.rawQuery(
                            "SELECT COUNT(*) FROM wifi_data WHERE latitude != 0 AND longitude != 0", null);
                        if (cursor.moveToFirst()) wifiCount += cursor.getInt(0);
                        cursor.close();
                    }
                    
                    return true;
                    
                } catch (Exception e) {
                    Log.e("MainActivity", "Error opening encrypted database", e);
                    return false;
                } finally {
                    if (testDb != null) testDb.close();
                }
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();
                
                if (success) {
                    // Show confirmation dialog
                    android.app.AlertDialog.Builder confirmBuilder = new android.app.AlertDialog.Builder(MainActivity.this);
                    confirmBuilder.setTitle("Import Encrypted Database?");
                    confirmBuilder.setMessage(
                        "🔒 Encrypted database verified!\n\n" +
                        "Found data:\n" +
                        "• " + wifiCount + " WiFi networks\n" +
                        "• " + bluetoothCount + " Bluetooth devices\n\n" +
                        "Import this data into your database?\n\n" +
                        "📋 Encryption debug info logged to Logcat (tag: IMPORT_DEBUG)"
                    );
                    
                    confirmBuilder.setPositiveButton("Import", (d, w) -> {
                        performEncryptedDatabaseImport(encryptedDbFile, derivedKey, originalPassphrase, wifiCount, bluetoothCount);
                    });
                    
                    confirmBuilder.setNegativeButton(R.string.cancel, (d, w) -> {
                        encryptedDbFile.delete();
                    });
                    
                    confirmBuilder.show();
                    addLogMessage("✓ Database unlocked successfully - check Logcat for key comparison");
                } else {
                    Toast.makeText(MainActivity.this, R.string.wrong_passphrase, Toast.LENGTH_LONG).show();
                    addLogMessage("✗ Failed to unlock database - wrong passphrase or corrupted file");
                    addLogMessage("Check Logcat (tag: IMPORT_DEBUG) for detailed encryption info");
                    encryptedDbFile.delete();
                    pendingEncryptionSalt = null;  // Clear pending salt on failure
                }
            }
        }.execute();
    }
    
    /**
     * Perform actual encrypted database import (copy data)
     */
    private void performEncryptedDatabaseImport(final java.io.File encryptedDbFile, 
                                                final String derivedKey,
                                                final String originalPassphrase, 
                                                final int wifiCount, 
                                                final int bluetoothCount) {
        new android.os.AsyncTask<Void, Void, Boolean>() {
            android.app.ProgressDialog progressDialog;
            
            @Override
            protected void onPreExecute() {
                progressDialog = android.app.ProgressDialog.show(
                    MainActivity.this, 
                    "Importing Database",
                    "Copying encrypted database data...", 
                    true
                );
            }
            
            @Override
            protected Boolean doInBackground(Void... params) {
                net.sqlcipher.database.SQLiteDatabase externalDb = null;
                try {
                    // Ensure internal database is open before import
                    if (database == null) {
                        Log.e("Import", "Internal database is NULL before import! Opening now...");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Initialize database if not already open
                                if (encryptionManager.isEncryptionEnabled() && encryptionManager.isPassphraseCached()) {
                                    initializeEncryptedDatabase();
                                } else if (!encryptionManager.isEncryptionEnabled()) {
                                    DatabaseHelper dbHelper = new DatabaseHelper(MainActivity.this);
                                    database = dbHelper.getWritableDatabase();
                                }
                            }
                        });
                        
                        // Wait a bit for DB to open
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        
                        if (database == null) {
                            Log.e("Import", "Failed to open internal database!");
                            return false;
                        }
                    }
                    
                    // Open encrypted external database
                    net.sqlcipher.database.SQLiteDatabase.loadLibs(MainActivity.this);
                    externalDb = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                        encryptedDbFile.getAbsolutePath(), derivedKey, null, 
                        net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY);
                    
                    Log.i("Import", "Starting import from encrypted database...");
                    
                    // Copy data to internal database
                    copyDataFromEncryptedToInternal(externalDb);
                    
                    // Force database sync to ensure data is written to disk
                    if (database != null) {
                        if (isDatabaseEncrypted) {
                            net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                            if (db.isOpen() && !db.isReadOnly()) {
                                // SQLCipher databases are auto-synced on transaction commit
                                Log.i("Import", "Encrypted database transaction committed");
                            }
                        } else {
                            android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                            if (db.isOpen() && !db.isReadOnly()) {
                                // Standard SQLite databases are auto-synced
                                Log.i("Import", "Standard database transaction committed");
                            }
                        }
                    }
                    
                    return true;
                    
                } catch (Exception e) {
                    Log.e("MainActivity", "Error importing encrypted database", e);
                    return false;
                } finally {
                    if (externalDb != null) externalDb.close();
                    encryptedDbFile.delete();
                }
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                progressDialog.dismiss();

                // Clear pending encryption salt after import completes
                pendingEncryptionSalt = null;

                if (success) {
                    // Verify data was actually written to database
                    int totalWifi = 0;
                    int totalBT = 0;
                    
                    try {
                        Cursor wifiCursor = dbRawQuery("SELECT COUNT(*) FROM wifi_data", null);
                        if (wifiCursor != null) {
                            if (wifiCursor.moveToFirst()) totalWifi = wifiCursor.getInt(0);
                            wifiCursor.close();
                        }
                        
                        Cursor btCursor = dbRawQuery("SELECT COUNT(*) FROM device_data WHERE device_type='BLUETOOTH'", null);
                        if (btCursor != null) {
                            if (btCursor.moveToFirst()) totalBT = btCursor.getInt(0);
                            btCursor.close();
                        }
                    } catch (Exception e) {
                        Log.e("Import", "Error counting imported data: " + e.getMessage());
                    }
                    
                    // Hide security overlay after successful import
                    if (securityOverlay != null) {
                        securityOverlay.setVisibility(View.GONE);
                    }
                    
                    // Cache passphrase in encryptionManager so MapActivity can access the database
                    if (encryptionManager != null && encryptionManager.isEncryptionEnabled()) {
                        encryptionManager.unlockWithPassphrase(originalPassphrase.toCharArray());
                        Log.i("MainActivity", "Passphrase cached after import - MapActivity can now access DB");
                        addLogMessage("🔑 Passphrase cached - Map access enabled");
                    }
                    
                    Toast.makeText(MainActivity.this,
                        "✓ Imported " + wifiCount + " WiFi + " + bluetoothCount + " BT devices",
                        Toast.LENGTH_LONG).show();
                    addLogMessage("✓ Import completed: " + wifiCount + " WiFi + " + bluetoothCount + " BT");
                    addLogMessage("📊 Database now contains: " + totalWifi + " WiFi + " + totalBT + " BT");

                    // Update total networks count
                    updateTotalNetworksCount();

                    // Refresh display - always show data after import
                    isShowingStoredData = true;
                    showData();
                } else {
                    Toast.makeText(MainActivity.this, "Import failed", Toast.LENGTH_LONG).show();
                    addLogMessage("✗ Import failed");
                }
            }
        }.execute();
    }
    
    /**
     * Copy data from encrypted database to internal database
     */
    private void copyDataFromEncryptedToInternal(net.sqlcipher.database.SQLiteDatabase externalDb) {
        int wifiInserted = 0;
        int deviceInserted = 0;
        
        // Log database info for debugging
        Log.i("Import", "═══════════════════════════════════════");
        Log.i("Import", "IMPORT TARGET DATABASE INFO");
        Log.i("Import", "═══════════════════════════════════════");
        Log.i("Import", "Database is null: " + (database == null));
        Log.i("Import", "Database is encrypted: " + isDatabaseEncrypted);
        if (database != null) {
            try {
                String dbPath;
                if (isDatabaseEncrypted) {
                    net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                    dbPath = db.getPath();
                    Log.i("Import", "DB Path: " + dbPath);
                    Log.i("Import", "DB is open: " + db.isOpen());
                    Log.i("Import", "DB is readonly: " + db.isReadOnly());
                    
                    // Also log to UI
                    final String path = dbPath;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addLogMessage("📂 Import target DB: " + path);
                            addLogMessage("🔐 Target is encrypted: YES");
                        }
                    });
                } else {
                    android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                    dbPath = db.getPath();
                    Log.i("Import", "DB Path: " + dbPath);
                    Log.i("Import", "DB is open: " + db.isOpen());
                    Log.i("Import", "DB is readonly: " + db.isReadOnly());
                    
                    // Also log to UI
                    final String path = dbPath;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addLogMessage("📂 Import target DB: " + path);
                            addLogMessage("🔐 Target is encrypted: NO");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("Import", "Error getting DB info: " + e.getMessage());
            }
        }
        Log.i("Import", "═══════════════════════════════════════");
        
        try {
            // Start transaction for better performance and atomicity
            if (isDatabaseEncrypted) {
                ((net.sqlcipher.database.SQLiteDatabase) database).beginTransaction();
            } else {
                ((android.database.sqlite.SQLiteDatabase) database).beginTransaction();
            }
            
            // Copy wifi_data
            if (hasEncryptedTable(externalDb, "wifi_data")) {
                android.database.Cursor cursor = externalDb.rawQuery("SELECT * FROM wifi_data", null);
                while (cursor.moveToNext()) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    for (String columnName : cursor.getColumnNames()) {
                        int columnIndex = cursor.getColumnIndex(columnName);
                        if (!cursor.isNull(columnIndex)) {
                            int type = cursor.getType(columnIndex);
                            switch (type) {
                                case android.database.Cursor.FIELD_TYPE_INTEGER:
                                    values.put(columnName, cursor.getLong(columnIndex));
                                    break;
                                case android.database.Cursor.FIELD_TYPE_FLOAT:
                                    values.put(columnName, cursor.getDouble(columnIndex));
                                    break;
                                case android.database.Cursor.FIELD_TYPE_STRING:
                                    values.put(columnName, cursor.getString(columnIndex));
                                    break;
                            }
                        }
                    }
                    long result = dbInsert("wifi_data", null, values);
                    if (result != -1) wifiInserted++;
                }
                cursor.close();
                Log.i("Import", "Inserted " + wifiInserted + " wifi_data records");
            }
            
            // Copy device_data
            if (hasEncryptedTable(externalDb, "device_data")) {
                android.database.Cursor cursor = externalDb.rawQuery("SELECT * FROM device_data", null);
                while (cursor.moveToNext()) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    for (String columnName : cursor.getColumnNames()) {
                        int columnIndex = cursor.getColumnIndex(columnName);
                        if (!cursor.isNull(columnIndex)) {
                            int type = cursor.getType(columnIndex);
                            switch (type) {
                                case android.database.Cursor.FIELD_TYPE_INTEGER:
                                    values.put(columnName, cursor.getLong(columnIndex));
                                    break;
                                case android.database.Cursor.FIELD_TYPE_FLOAT:
                                    values.put(columnName, cursor.getDouble(columnIndex));
                                    break;
                                case android.database.Cursor.FIELD_TYPE_STRING:
                                    values.put(columnName, cursor.getString(columnIndex));
                                    break;
                            }
                        }
                    }
                    long result = dbInsert("device_data", null, values);
                    if (result != -1) deviceInserted++;
                }
                cursor.close();
                Log.i("Import", "Inserted " + deviceInserted + " device_data records");
            }
            
            // Commit transaction
            if (isDatabaseEncrypted) {
                ((net.sqlcipher.database.SQLiteDatabase) database).setTransactionSuccessful();
                ((net.sqlcipher.database.SQLiteDatabase) database).endTransaction();
                
                // Force sync to disk
                try {
                    net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                    // Execute a dummy query to ensure all changes are flushed
                    android.database.Cursor c = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null);
                    if (c != null) {
                        c.moveToFirst();
                        c.close();
                    }
                    Log.i("Import", "Forced WAL checkpoint (full sync to disk)");
                } catch (Exception e) {
                    Log.w("Import", "Could not force WAL checkpoint: " + e.getMessage());
                }
            } else {
                ((android.database.sqlite.SQLiteDatabase) database).setTransactionSuccessful();
                ((android.database.sqlite.SQLiteDatabase) database).endTransaction();
            }
            
            Log.i("Import", "✓ Import transaction committed: " + wifiInserted + " WiFi + " + deviceInserted + " device records");
            
            // Verify data was written by querying again
            try {
                int verifyWifi = 0;
                int verifyDevice = 0;
                
                android.database.Cursor wifiCursor = null;
                android.database.Cursor deviceCursor = null;
                
                if (isDatabaseEncrypted) {
                    net.sqlcipher.database.SQLiteDatabase db = (net.sqlcipher.database.SQLiteDatabase) database;
                    wifiCursor = db.rawQuery("SELECT COUNT(*) FROM wifi_data", null);
                    deviceCursor = db.rawQuery("SELECT COUNT(*) FROM device_data", null);
                } else {
                    android.database.sqlite.SQLiteDatabase db = (android.database.sqlite.SQLiteDatabase) database;
                    wifiCursor = db.rawQuery("SELECT COUNT(*) FROM wifi_data", null);
                    deviceCursor = db.rawQuery("SELECT COUNT(*) FROM device_data", null);
                }
                
                if (wifiCursor != null) {
                    if (wifiCursor.moveToFirst()) verifyWifi = wifiCursor.getInt(0);
                    wifiCursor.close();
                }
                if (deviceCursor != null) {
                    if (deviceCursor.moveToFirst()) verifyDevice = deviceCursor.getInt(0);
                    deviceCursor.close();
                }
                
                Log.i("Import", "✓ Verification after commit: " + verifyWifi + " wifi_data + " + verifyDevice + " device_data in DB");
            } catch (Exception e) {
                Log.e("Import", "Error verifying data after commit: " + e.getMessage());
            }
            
        } catch (Exception e) {
            Log.e("Import", "Error during import transaction: " + e.getMessage(), e);
            // Rollback transaction on error
            try {
                if (isDatabaseEncrypted) {
                    ((net.sqlcipher.database.SQLiteDatabase) database).endTransaction();
                } else {
                    ((android.database.sqlite.SQLiteDatabase) database).endTransaction();
                }
            } catch (Exception e2) {
                Log.e("Import", "Error rolling back transaction: " + e2.getMessage());
            }
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if encrypted table exists
     */
    private boolean hasEncryptedTable(net.sqlcipher.database.SQLiteDatabase db, String tableName) {
        android.database.Cursor cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?", 
            new String[]{tableName});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    // Database Helper Class
    public static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "wifi_scanner.db";
        private static final int DATABASE_VERSION = 6; // Motion analysis fields
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

    // Export debug log to txt file
    private void exportDebugLogToTxt() {
        String logText = logcatText.getText().toString();
        
        // Show dialog with options
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Export Debug Log");
        builder.setMessage("Choose export option:");
        
        // Copy to clipboard option
        builder.setPositiveButton("📋 Copy to Clipboard", (dialog, which) -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Debug Log", logText);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Debug log copied to clipboard!", Toast.LENGTH_SHORT).show();
            addLogMessage("Debug log copied to clipboard");
        });
        
        // Save to Downloads option
        builder.setNegativeButton("💾 Save to Downloads", (dialog, which) -> {
            try {
                // For Android 10+ (API 29+), use MediaStore to save to Downloads
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
                    String filename = "debug_log_" + timestamp + ".txt";
                    values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename);
                    values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                    values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
                    
                    android.net.Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        java.io.OutputStream outputStream = getContentResolver().openOutputStream(uri);
                        outputStream.write(logText.getBytes());
                        outputStream.close();
                        Toast.makeText(this, "Debug log saved to Downloads/" + filename, Toast.LENGTH_LONG).show();
                        addLogMessage("Debug log saved to Downloads/" + filename);
                    }
                } else {
                    // For older Android versions, save to public Downloads directory
                    java.io.File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                    String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
                    java.io.File exportFile = new java.io.File(downloadsDir, "debug_log_" + timestamp + ".txt");
                    java.io.FileWriter writer = new java.io.FileWriter(exportFile);
                    writer.write(logText);
                    writer.close();
                    Toast.makeText(this, "Debug log saved to Downloads/" + exportFile.getName(), Toast.LENGTH_LONG).show();
                    addLogMessage("Debug log saved to " + exportFile.getName());
                }
            } catch (Exception e) {
                Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                addLogMessage("ERROR: Failed to save log: " + e.getMessage());
            }
        });
        
        builder.setNeutralButton("Cancel", null);
        builder.show();
    }
}

