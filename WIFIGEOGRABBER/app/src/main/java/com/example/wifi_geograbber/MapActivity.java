package com.example.wifi_geograbber;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.List;
import java.util.ArrayList;

public class MapActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "MapPrefs";

    /**
     * Sanitize user input for safe logging by removing all line breaking/control characters.
     */
    private static String sanitizeForLogging(String input) {
        if (input == null) return "";
        // Remove CR, LF, Vertical Tab, Form Feed, Unicode Next Line, Unicode Line Separator, Unicode Paragraph Separator
        return input.replaceAll("[\\r\\n\\u000B\\u000C\\u0085\\u2028\\u2029]", "");
    }
    private static final String PREF_FILTERS = "activeFilters";
    private static final String PREF_CENTER_LAT = "centerLat";
    private static final String PREF_CENTER_LON = "centerLon";
    private static final String PREF_ZOOM = "zoomLevel";
    private static final String PREF_MAP_STYLE = "mapStyle";
    private WebView mapWebView;
    private Object database; // Can be either SQLiteDatabase or net.sqlcipher.database.SQLiteDatabase
    private boolean isDatabaseEncrypted = false;
    private EncryptionManager encryptionManager;
    private Button backButton, refreshButton, locationButton;
    private Button searchToggleButton, searchButton, clearSearchButton;
    private android.widget.EditText searchInput;
    private android.widget.LinearLayout searchBarLayout;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private android.widget.TextView dbStatusText;
    private List<DeviceData> deviceList;
    private boolean isMapInitialized = false;
    
    // Flag to track if we're navigating within the app (no auth needed)
    private boolean isInternalNavigation = false;
    
    // BroadcastReceiver for screen off event
    private BroadcastReceiver screenOffReceiver;
    
    // Bounding box for performance optimization (only load visible markers)
    private double bboxMinLat = -90, bboxMinLon = -180, bboxMaxLat = 90, bboxMaxLon = 180;
    private static final int MAX_MARKERS_PER_LOAD = 500; // Maximum number of markers per load

    // Fullscreen flags for System UI
    private final int FULLSCREEN_FLAGS = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    
    // Data class for devices
    public static class DeviceData {
        public String name;
        public String address;
        public String type;
        public int signal;
        public String encryption;
        public double lat;
        public double lon;
        public long timestamp;
        public String vendor;
        public int frequency;
        public int channel;
        public String standard;
        public int channelWidth;
        public int maxSpeed;
        // Movement analysis fields
        public double lastSeenLat;
        public double lastSeenLon;
        public long lastSeenTimestamp;
        public double movementDistance;
        
        public DeviceData() {}
        
        public JSONObject toJSON() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("name", name != null ? name : "");
            json.put("address", address != null ? address : "");
            json.put("type", type != null ? type : "");
            json.put("signal", signal);
            json.put("encryption", encryption != null ? encryption : "");
            json.put("lat", lat);
            json.put("lon", lon);
            json.put("timestamp", timestamp);
            json.put("vendor", vendor != null ? vendor : "");
            json.put("frequency", frequency);
            json.put("channel", channel);
            json.put("standard", standard != null ? standard : "");
            json.put("channel_width", channelWidth);
            json.put("max_speed", maxSpeed);
            // Movement analysis fields
            json.put("last_seen_lat", lastSeenLat);
            json.put("last_seen_lon", lastSeenLon);
            json.put("last_seen_timestamp", lastSeenTimestamp);
            json.put("movement_distance", movementDistance);
            return json;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Hide System UI for fullscreen (status bar, navigation, home button)
        hideSystemUI();

        // Initialize views
        mapWebView = findViewById(R.id.map_webview);
        backButton = findViewById(R.id.back_button);
        refreshButton = findViewById(R.id.refresh_button);
        locationButton = findViewById(R.id.location_button);
        searchToggleButton = findViewById(R.id.search_toggle_button);
        searchButton = findViewById(R.id.search_button);
        clearSearchButton = findViewById(R.id.clear_search_button);
        searchInput = findViewById(R.id.search_input);
        searchBarLayout = findViewById(R.id.search_bar_layout);
        dbStatusText = findViewById(R.id.db_status_text);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize encryption manager
        encryptionManager = new EncryptionManager(this);

        // Register screen off receiver to clear encryption key
        registerScreenOffReceiver();

        // Initialize database - check if encryption is enabled
        initializeDatabase();

        if (database == null) {
            Toast.makeText(this, "Failed to open database", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Toast.makeText(this, R.string.internal_database_loaded, Toast.LENGTH_SHORT).show();
        dbStatusText.setText(R.string.internal_app_database);
        dbStatusText.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));

        // Button listeners
        backButton.setOnClickListener(v -> {
            // Mark as internal navigation so onPause doesn't lock the app
            isInternalNavigation = true;
            finish();
        });
        refreshButton.setOnClickListener(v -> refreshMap());
        locationButton.setOnClickListener(v -> requestLocationAndCenterMap());
        searchToggleButton.setOnClickListener(v -> toggleSearchBar());
        searchButton.setOnClickListener(v -> performSearch());
        clearSearchButton.setOnClickListener(v -> clearSearch());
        
        // Search on enter key
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        // Configure WebView
        setupWebView();

        // Load data and show map
        loadDataAndShowMap();
    }

    /**
     * Initialize database (encrypted or unencrypted)
     */
    private void initializeDatabase() {
        // Check if encryption is enabled
        if (encryptionManager.isEncryptionEnabled()) {
            // Get cached passphrase
            String dbKey = encryptionManager.getCachedDatabaseKey();
            if (dbKey != null) {
                try {
                    // Open encrypted database in readonly mode
                    DatabaseEncryptionHelper encryptedDbHelper = new DatabaseEncryptionHelper(this, dbKey);
                    database = encryptedDbHelper.openEncryptedDatabaseReadonly();
                    isDatabaseEncrypted = true;
                    Log.d("MapActivity", "Opened encrypted database in readonly mode");
                } catch (Exception e) {
                    Log.e("MapActivity", "Failed to open encrypted database: " + e.getMessage());
                    database = null;
                }
            } else {
                Log.e("MapActivity", "Encryption enabled but passphrase not cached");
                database = null;
            }
        } else {
            // Open unencrypted database
            try {
                MainActivity.DatabaseHelper dbHelper = new MainActivity.DatabaseHelper(this);
                database = dbHelper.getReadableDatabase();
                isDatabaseEncrypted = false;
                Log.d("MapActivity", "Opened unencrypted database in readonly mode");
            } catch (Exception e) {
                Log.e("MapActivity", "Failed to open unencrypted database: " + e.getMessage());
                database = null;
            }
        }
    }

    /**
     * Execute raw query on database (works with both encrypted and unencrypted)
     */
    private Cursor dbRawQuery(String sql, String[] selectionArgs) {
        try {
            if (database == null) return null;
            if (isDatabaseEncrypted) {
                return ((net.sqlcipher.database.SQLiteDatabase) database).rawQuery(sql, selectionArgs);
            } else {
                return ((android.database.sqlite.SQLiteDatabase) database).rawQuery(sql, selectionArgs);
            }
        } catch (Exception e) {
            Log.e("MapActivity", "Error in dbRawQuery: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Close database
     */
    private void dbClose() {
        try {
            if (database == null) return;
            if (isDatabaseEncrypted) {
                ((net.sqlcipher.database.SQLiteDatabase) database).close();
            } else {
                ((android.database.sqlite.SQLiteDatabase) database).close();
            }
        } catch (Exception e) {
            Log.e("MapActivity", "Error closing database: " + e.getMessage(), e);
        }
    }

    // Method to hide System UI (fullscreen, home button, navigation)
    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(FULLSCREEN_FLAGS);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }
    
    private void setupWebView() {
        WebSettings webSettings = mapWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(false);
        // SECURITY: Prevent access to content:// URLs
        webSettings.setAllowContentAccess(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // SECURITY: Add JavaScript interface
        // This is safe because:
        // 1. WebView ONLY loads locally-generated HTML content via loadDataWithBaseURL()
        // 2. No user-controlled or external URLs are loaded
        // 3. External resources (Leaflet, OSM tiles) are from trusted CDNs
        // 4. All methods exposed via @JavascriptInterface sanitize user input
        // 5. No navigation to untrusted content is allowed (see WebViewClient below)
        mapWebView.addJavascriptInterface(new WebAppInterface(), "Android");
        
        mapWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Pass data to JavaScript when page is loaded (even with empty list)
                if (deviceList != null) {
                    injectDeviceData();
                }
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // SECURITY: Block all navigation attempts to prevent loading untrusted content
                // Only allow the initial locally-generated content
                Log.w("MapActivity", "Blocked navigation attempt to: " + sanitizeForLogging(url));
                return true; // Block the navigation
            }
        });
    }
    
    private void loadDataAndShowMap() {
        // Initially load with default bounding box (will be updated later by map movement)
        deviceList = getDevicesInBoundingBox(bboxMinLat, bboxMinLon, bboxMaxLat, bboxMaxLon);
        
        Log.d("MapActivity", "Initial loaded " + deviceList.size() + " devices in viewport");

        // Load HTML map only the first time
        if (!isMapInitialized) {
            loadMapHTML();
        } else {
            // On subsequent calls only update data (even with empty list)
            if (deviceList != null) {
                injectDeviceData();
            }
        }
    }
    
    // Loads only devices in the visible area of the map (performance optimization)
    private List<DeviceData> getDevicesInBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {
        List<DeviceData> filtered = new ArrayList<>();
        try {
            // SQL query without LIMIT, load all devices in area
            String sql = "SELECT device_name, device_address, device_type, signal_strength, " +
                "encryption_info, latitude, longitude, timestamp, frequency, channel, " +
                "wifi_standard, vendor_info, channel_width, max_connection_speed, " +
                "COALESCE(last_seen_latitude, 0) as last_seen_latitude, " +
                "COALESCE(last_seen_longitude, 0) as last_seen_longitude, " +
                "COALESCE(last_seen_timestamp, 0) as last_seen_timestamp, " +
                "COALESCE(movement_distance, 0) as movement_distance " +
                "FROM device_data WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? " +
                "AND latitude != 0 AND longitude != 0 " +
                "ORDER BY signal_strength DESC";

            Cursor deviceCursor = dbRawQuery(sql, new String[]{
                String.valueOf(minLat), String.valueOf(maxLat),
                String.valueOf(minLon), String.valueOf(maxLon)
            });

            while (deviceCursor.moveToNext()) {
                DeviceData device = new DeviceData();
                device.name = deviceCursor.getString(0);
                device.address = deviceCursor.getString(1);
                device.type = deviceCursor.getString(2);
                device.signal = deviceCursor.getInt(3);
                device.encryption = deviceCursor.getString(4);
                device.lat = deviceCursor.getDouble(5);
                device.lon = deviceCursor.getDouble(6);
                device.timestamp = deviceCursor.getLong(7);
                device.frequency = deviceCursor.getInt(8);
                device.channel = deviceCursor.getInt(9);
                device.standard = deviceCursor.getString(10);
                device.vendor = deviceCursor.getString(11);
                device.channelWidth = deviceCursor.getInt(12);
                device.maxSpeed = deviceCursor.getInt(13);
                device.lastSeenLat = deviceCursor.getDouble(14);
                device.lastSeenLon = deviceCursor.getDouble(15);
                device.lastSeenTimestamp = deviceCursor.getLong(16);
                device.movementDistance = deviceCursor.getDouble(17);

                // Derive vendor from OUI if not present
                if (device.vendor == null || device.vendor.equals("Unknown")) {
                    device.vendor = getVendorFromOUI(device.address);
                }

                filtered.add(device);
            }
            deviceCursor.close();

            // Additionally load WiFi data from old table (only in visible area, without LIMIT)
            Cursor wifiCursor = dbRawQuery(
                "SELECT ssid, bssid, signal_strength, encryption, " +
                "latitude, longitude, timestamp " +
                "FROM wifi_data WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? " +
                "AND latitude != 0 AND longitude != 0 " +
                "AND bssid NOT IN (SELECT device_address FROM device_data WHERE device_type = 'WIFI') " +
                "ORDER BY signal_strength DESC",
                new String[]{
                    String.valueOf(minLat), String.valueOf(maxLat),
                    String.valueOf(minLon), String.valueOf(maxLon)
                });

            while (wifiCursor.moveToNext()) {
                DeviceData device = new DeviceData();
                device.name = wifiCursor.getString(0);
                device.address = wifiCursor.getString(1);
                device.type = "WIFI";
                device.signal = wifiCursor.getInt(2);
                device.encryption = wifiCursor.getString(3);
                device.lat = wifiCursor.getDouble(4);
                device.lon = wifiCursor.getDouble(5);
                device.timestamp = wifiCursor.getLong(6);
                device.vendor = getVendorFromOUI(device.address);

                filtered.add(device);
            }
            wifiCursor.close();

        } catch (Exception e) {
            Log.e("MapActivity", "Error loading devices in bounding box: " + e.getMessage());
        }
        return filtered;
    }
    
    private String getVendorFromOUI(String bssid) {
        if (bssid == null || bssid.length() < 8) {
            return "Unknown";
        }
        
        String oui = bssid.substring(0, 8).toUpperCase().replace(":", "").replace("-", "");
        if (oui.length() < 6) {
            return "Unknown";
        }

        // Known vendor OUIs (selection)
        String ouiKey = oui.substring(0, 6);
        switch (ouiKey) {
            case "F01898":
            case "ACBC32":
            case "00236C":
            case "8C8590":
            case "40CBC0":
                return "Apple";
            case "001DD8":
            case "A00460":
            case "204E7F":
            case "C40415":
                return "NETGEAR";
            case "00223F":
            case "C05627":
            case "48F8B3":
            case "0014BF":
                return "Linksys";
            case "302303":
            case "0015E9":
            case "00265A":
            case "BCF685":
                return "D-Link";
            case "000B6B":
            case "001302":
            case "001500":
            case "0016EA":
            case "0019D1":
            case "001B77":
            case "00216A":
            case "0024D7":
            case "A434D9":
                return "Intel";
            case "0024E9":
            case "002637":
            case "A8F274":
            case "F47B09":
            case "183A2D":
            case "E8508B":
                return "Samsung";
            case "188796":
            case "000A41":
            case "00D0BC":
            case "00D058":
            case "B8BEBF":
                return "Cisco";
            default:
                return "Unknown (" + bssid.substring(0, 8) + ")";
        }
    }
    
    private void loadMapHTML() {
        // Load saved filters and map position
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedFilters = prefs.getString(PREF_FILTERS, null);
        double centerLat = Double.longBitsToDouble(prefs.getLong(PREF_CENTER_LAT, Double.doubleToLongBits(0)));
        double centerLon = Double.longBitsToDouble(prefs.getLong(PREF_CENTER_LON, Double.doubleToLongBits(0)));
        int zoomLevel = prefs.getInt(PREF_ZOOM, 15);
        String mapStyle = prefs.getString(PREF_MAP_STYLE, "light"); // Default: light

        String htmlContent = generateMapHTML(savedFilters, centerLat, centerLon, zoomLevel, mapStyle);
        // SECURITY: Only locally-generated, trusted HTML content is loaded
        // No external or user-controlled URLs are ever loaded into this WebView
        mapWebView.loadDataWithBaseURL("https://localhost/", htmlContent, "text/html", "UTF-8", null);
    }
    
    private void injectDeviceData() {
        try {
            JSONArray deviceArray = new JSONArray();
            for (DeviceData device : deviceList) {
                deviceArray.put(device.toJSON());
            }
            
            String jsCode;
            if (isMapInitialized) {
                // If map is already initialized, only update data without changing centering
                jsCode = "updateMapData(" + deviceArray.toString() + ");";
            } else {
                // First initialization
                jsCode = "initializeMap(" + deviceArray.toString() + ");";
                isMapInitialized = true;
            }
            mapWebView.evaluateJavascript(jsCode, null);
            
        } catch (JSONException e) {
            Log.e("MapActivity", "Error creating JSON: " + e.getMessage());
        }
    }
    
    private void refreshMap() {
        Toast.makeText(this, R.string.map_updating, Toast.LENGTH_SHORT).show();
        
        // Only reload data in current viewport
        deviceList = getDevicesInBoundingBox(bboxMinLat, bboxMinLon, bboxMaxLat, bboxMaxLon);
        
        Log.d("MapActivity", "Refreshed " + deviceList.size() + " devices in current viewport");

        // Only inject data, don't reload HTML
        if (deviceList != null && !deviceList.isEmpty()) {
            injectDeviceData();
        }
    }
    
    // Overloaded method for MapHTML with filter and center
    private String generateMapHTML(String savedFilters, double centerLat, double centerLon, int zoomLevel, String mapStyle) {
        // Check if external DB is used
        String externalDbPath = getIntent().getStringExtra("external_db_path");
        boolean isExternalDb = (externalDbPath != null);
        String dbInfo = isExternalDb ? "External DB" : "Internal DB";
        // Filter and map status as JSON for JS
        String filterJson = savedFilters != null ? savedFilters : "[]";
        String centerJson = "{" +
            "\"lat\":" + centerLat + ",\"lon\":" + centerLon + ",\"zoom\":" + zoomLevel + "}";

        // Determine tile layer URL based on style
        String tileLayerUrl;
        String tileLayerAttribution;
        if ("dark".equals(mapStyle)) {
            // CartoDB Dark Matter (free, no API key needed)
            tileLayerUrl = "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png";
            tileLayerAttribution = "© OpenStreetMap contributors © CARTO";
        } else {
            // Default: OpenStreetMap
            tileLayerUrl = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
            tileLayerAttribution = "© OpenStreetMap contributors";
        }

        return "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset='utf-8'>\n" +
            "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
            "    <title>" + getString(R.string.wifi_bluetooth_map) + "</title>\n" +
            "    <link rel='stylesheet' href='https://unpkg.com/leaflet@1.7.1/dist/leaflet.css' />\n" +
            "    <style>\n" +
            "        body { margin: 0; padding: 0; }\n" +
            "        #map { height: 100vh; width: 100vw; }\n" +
            "        .custom-control {\n" +
            "            position: absolute;\n" +
            "            top: 10px;\n" +
            "            left: 10px;\n" +
            "            background: #222;\n" +
            "            color: #fff;\n" +
            "            border: 2px solid rgba(255,255,255,0.2);\n" +
            "            border-radius: 5px;\n" +
            "            padding: 10px;\n" +
            "            z-index: 10000;\n" +
            "            max-width: 250px;\n" +
            "            max-height: 400px;\n" +
            "            overflow-y: auto;\n" +
            "            box-shadow: 0 1px 7px rgba(0,0,0,0.4);\n" +
            "        }\n" +
            "        .filter-checkbox {\n" +
            "            display: block;\n" +
            "            margin: 5px 0;\n" +
            "            font-size: 12px;\n" +
            "            cursor: pointer;\n" +
            "        }\n" +
            "        .info-panel {\n" +
            "            position: absolute;\n" +
            "            bottom: 10px;\n" +
            "            right: 10px;\n" +
            "            background: rgba(0, 0, 0, 0.8);\n" +
            "            color: white;\n" +
            "            padding: 8px 12px;\n" +
            "            border-radius: 5px;\n" +
            "            font-size: 12px;\n" +
            "            z-index: 10000;\n" +
            "        }\n" +
            "        .db-info {\n" +
            "            position: absolute;\n" +
            "            top: 10px;\n" +
            "            right: 10px;\n" +
                "        background: " + (isExternalDb ? "rgba(255, 140, 0, 0.9)" : "#0A0A2A") + ";\n" +
            "            color: white;\n" +
            "            padding: 6px 10px;\n" +
            "            border-radius: 5px;\n" +
            "            font-size: 11px;\n" +
            "            z-index: 10000;\n" +
            "            font-weight: bold;\n" +
            "        }\n" +
            "        .user-location-icon {\n" +
            "            font-size: 20px;\n" +
            "            text-align: center;\n" +
            "            line-height: 20px;\n" +
            "        }\n" +
            "        .filter-toggle-btn {\n" +
            "            display: block;\n" +
            "            width: 100%;\n" +
            "            background: #333;\n" +
            "            color: #fff;\n" +
            "            border: none;\n" +
            "            border-radius: 4px;\n" +
            "            padding: 6px 0;\n" +
            "            margin-bottom: 8px;\n" +
            "            font-size: 13px;\n" +
            "            cursor: pointer;\n" +
            "        }\n" +
            "        .filter-action-btn {\n" +
            "            background: #333;\n" +
            "            color: #fff;\n" +
            "            border: none;\n" +
            "            border-radius: 4px;\n" +
            "            padding: 6px 0;\n" +
            "            margin: 2px 0;\n" +
            "            font-size: 13px;\n" +
            "            width: 100%;\n" +
            "            cursor: pointer;\n" +
            "        }\n" +
            "        .filter-content-collapsed { display: none; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div id='map'></div>\n" +
            "    \n" +
            "    <!-- DB Info -->\n" +
            "    <div class='db-info'>\n" +
            "        📁 " + dbInfo + "\n" +
            "    </div>\n" +
            "    \n" +
            "    <div class='custom-control'>\n" +
            "        <button class='filter-toggle-btn' id='filterToggleBtn' onclick='toggleFilterContainer()'>Filter ▼</button>\n" +
            "        <div id='filterContent'>\n" +
            "        <h4 style='margin: 0 0 10px 0; font-size: 14px;'>Filter</h4>\n" +
            "        <label class='filter-checkbox'>\n" +
            "            <input type='checkbox' id='wifi_open' onchange='toggleFilter(\"wifi_open\")'>\n" +
            "            WiFi Open\n" +
            "        </label>\n" +
            "        <label class='filter-checkbox'>\n" +
            "            <input type='checkbox' id='wifi_open_no_vodafone' onchange='toggleFilter(\"wifi_open_no_vodafone\")'>\n" +
            "            WiFi Open without Vodafone\n" +
            "        </label>\n" +
            "        <label class='filter-checkbox'>\n" +
            "            <input type='checkbox' id='wifi_encrypted' onchange='toggleFilter(\"wifi_encrypted\")'>\n" +
            "            WiFi Encrypted\n" +
            "        </label>\n" +
            "        <label class='filter-checkbox'>\n" +
            "            <input type='checkbox' id='bluetooth' onchange='toggleFilter(\"bluetooth\")'>\n" +
            "            Bluetooth\n" +
            "        </label>\n" +
            "        <label class='filter-checkbox'>\n" +
            "            <input type='checkbox' id='bluetooth_known' onchange='toggleFilter(\"bluetooth_known\")'>\n" +
            "            Bluetooth without Unknown\n" +
            "        </label>\n" +
            "        <label class='filter-checkbox'>\n" +
            "            <input type='checkbox' id='signal_strong' onchange='toggleFilter(\"signal_strong\")'>\n" +
            "            Signal Strong (≥-70 dBm)\n" +
            "        </label>\n" +
            "        <label class='filter-checkbox'>\n" +
            "            <input type='checkbox' id='signal_weak' onchange='toggleFilter(\"signal_weak\")'>\n" +
            "            Signal Weak (<-70 dBm)\n" +
            "        </label>\n" +
            "        <hr style='margin: 10px 0;'>\n" +
            "        <button class='filter-action-btn' onclick='toggleAllFilters(true)'>All On</button>\n" +
            "        <button class='filter-action-btn' onclick='toggleAllFilters(false)'>All Off</button>\n" +
            "        <hr style='margin: 10px 0;'>\n" +
            "        <button class='filter-action-btn' onclick='requestCenterOnUser()'>Live Location</button>\n" +
            "        <button class='filter-action-btn' id='mapStyleBtn' onclick='toggleMapStyle()'>Map Style: " + (mapStyle.equals("dark") ? "Dark" : "Light") + "</button>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "    \n" +
            "    <div class='info-panel' id='info-panel'>\n" +
            "        Loading...\n" +
            "    </div>\n" +
            "    \n" +
            "    <script src='https://unpkg.com/leaflet@1.7.1/dist/leaflet.js'></script>\n" +
            "    <script>\n" +
            "    function toggleFilterContainer() {\n" +
            "        var content = document.getElementById('filterContent');\n" +
            "        var btn = document.getElementById('filterToggleBtn');\n" +
            "        if (content.classList.contains('filter-content-collapsed')) {\n" +
            "            content.classList.remove('filter-content-collapsed');\n" +
            "            btn.innerHTML = 'Filter ▼';\n" +
            "        } else {\n" +
            "            content.classList.add('filter-content-collapsed');\n" +
            "            btn.innerHTML = 'Filter ▲';\n" +
            "        }\n" +
            "    }\n" +
            "    window.onload = function() {\n" +
            "        try {\n" +
            "            if (Array.isArray(savedFilters)) {\n" +
            "                savedFilters.forEach(f => {\n" +
            "                    const cb = document.getElementById(f);\n" +
            "                    if (cb) { cb.checked = true; }\n" +
            "                });\n" +
            "            }\n" +
            "        } catch (e) { console.log('Filter restore error', e); }\n" +
            "    };\n" +
            "\n" +
            "    let map;\n" +
            "    let deviceData = [];\n" +
            "    let allMarkers = [];\n" +
            "    let allCircles = [];\n" +
            "    let activeFilters = new Set();\n" +
            "    let deviceFilterMap = new Map();\n" +
            "    let highlightedMarkerIndices = new Set(); // Indices of markers that should stay visible\n" +
            "    let userMarker = null;\n" +
            "    let currentCenter = null;\n" +
            "    let currentZoom = 15;\n" +
            "    const savedFilters = " + filterJson + ";\n" +
            "    const savedCenter = " + centerJson + ";\n" +
            "        \n" +
            "        function initializeMap(devices) {\n" +
            "            deviceData = devices;\n" +
            "            console.log('Initializing map with', devices.length, 'devices');\n" +
            "            \n" +
            "            // Always initialize map, even if no devices are present\n" +
            "            if (savedCenter && savedCenter.lat !== 0 && savedCenter.lon !== 0) {\n" +
            "                currentCenter = [savedCenter.lat, savedCenter.lon];\n" +
            "                currentZoom = savedCenter.zoom;\n" +
            "            } else if (devices.length > 0) {\n" +
            "                let avgLat = devices.reduce((sum, d) => sum + d.lat, 0) / devices.length;\n" +
            "                let avgLon = devices.reduce((sum, d) => sum + d.lon, 0) / devices.length;\n" +
            "                currentCenter = [avgLat, avgLon];\n" +
            "            } else {\n" +
            "                // Default position if no devices and no saved position\n" +
            "                currentCenter = [51.1657, 10.4515]; // Germany center\n" +
            "            }\n" +
            "            map = L.map('map').setView(currentCenter, currentZoom);\n" +
            "            \n" +
            "            map.on('moveend', function() {\n" +
            "                currentCenter = map.getCenter();\n" +
            "                // Performance optimization: Send bounding box to Android\n" +
            "                setTimeout(notifyAndroidOfViewportChange, 200); // Debounce\n" +
            "            });\n" +
            "            \n" +
            "            map.on('zoomend', function() {\n" +
            "                currentZoom = map.getZoom();\n" +
            "                // Performance optimization: Send bounding box to Android\n" +
            "                setTimeout(notifyAndroidOfViewportChange, 200); // Debounce\n" +
            "            });\n" +
            "            \n" +
            "            L.tileLayer('" + tileLayerUrl + "', {\n" +
            "                attribution: '" + tileLayerAttribution + "'\n" +
            "            }).addTo(map);\n" +
            "            \n" +
            "            // Only add markers if devices are present\n" +
            "            if (devices.length > 0) {\n" +
            "                addMarkers();\n" +
            "            }\n" +
            "            \n" +
            "            if (Array.isArray(savedFilters)) {\n" +
            "                savedFilters.forEach(f => {\n" +
            "                    const cb = document.getElementById(f);\n" +
            "                    if (cb) { cb.checked = true; activeFilters.add(f); }\n" +
            "                });\n" +
            "                updateMarkerVisibility();\n" +
            "            }\n" +
            "            updateInfoPanel();\n" +
            "            \n" +
            "            // Send initial viewport to Android\n" +
            "            setTimeout(notifyAndroidOfViewportChange, 1000);\n" +
            "        }\n" +
            "        \n" +
            "        // NEW FUNCTION: Sends current viewport to Android for performance optimization\n" +
            "        function notifyAndroidOfViewportChange() {\n" +
            "            if (map && typeof Android !== 'undefined' && Android.onMapViewportChanged) {\n" +
            "                const bounds = map.getBounds();\n" +
            "                const sw = bounds.getSouthWest();\n" +
            "                const ne = bounds.getNorthEast();\n" +
            "                Android.onMapViewportChanged(sw.lat, sw.lng, ne.lat, ne.lng);\n" +
            "                console.log('Viewport sent to Android:', sw.lat, sw.lng, 'to', ne.lat, ne.lng);\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        function updateMapData(devices) {\n" +
            "            deviceData = devices;\n" +
            "            console.log('UpdateMapData called with', devices.length, 'devices. Highlighted indices:', Array.from(highlightedMarkerIndices));\n" +
            "            \n" +
            "            // If there are highlighted markers, DON'T update the map at all\n" +
            "            if (highlightedMarkerIndices.size > 0) {\n" +
            "                console.log('Highlighted markers present - skipping map update to preserve them');\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            if (devices.length === 0) {\n" +
            "                document.getElementById('info-panel').textContent = 'No devices in visible area';\n" +
            "                // Remove all existing markers except highlighted ones\n" +
            "                allMarkers.forEach((marker, index) => {\n" +
            "                    if (!highlightedMarkerIndices.has(index)) {\n" +
            "                        map.removeLayer(marker);\n" +
            "                    }\n" +
            "                });\n" +
            "                allCircles.forEach((circle, index) => {\n" +
            "                    if (!highlightedMarkerIndices.has(index)) {\n" +
            "                        map.removeLayer(circle);\n" +
            "                    }\n" +
            "                });\n" +
            "                allMarkers = [];\n" +
            "                allCircles = [];\n" +
            "                deviceFilterMap.clear();\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            // Store highlighted device addresses before clearing\n" +
            "            const highlightedAddresses = new Set();\n" +
            "            highlightedMarkerIndices.forEach(index => {\n" +
            "                if (deviceData[index]) {\n" +
            "                    highlightedAddresses.add(deviceData[index].address);\n" +
            "                }\n" +
            "            });\n" +
            "            \n" +
            "            allMarkers.forEach(marker => map.removeLayer(marker));\n" +
            "            allCircles.forEach(circle => map.removeLayer(circle));\n" +
            "            allMarkers = [];\n" +
            "            allCircles = [];\n" +
            "            deviceFilterMap.clear();\n" +
            "            highlightedMarkerIndices.clear();\n" +
            "            \n" +
            "            addMarkers();\n" +
            "            \n" +
            "            // Restore highlighted markers by address\n" +
            "            highlightedAddresses.forEach(address => {\n" +
            "                const index = deviceData.findIndex(d => d.address === address);\n" +
            "                if (index !== -1) {\n" +
            "                    highlightedMarkerIndices.add(index);\n" +
            "                }\n" +
            "            });\n" +
            "            \n" +
            "            updateMarkerVisibility();\n" +
            "            updateInfoPanel();\n" +
            "        }\n" +
            "        \n" +
            "        function addMarkers() {\n" +
            "            deviceData.forEach((device, index) => {\n" +
            "                // If filter wifi_open_no_vodafone is active and device.name === '[Hidden/Unknown]', then skip\n" +
            "                if (activeFilters.has('wifi_open_no_vodafone') && device.type === 'WIFI' && device.name === '[Hidden/Unknown]') {\n" +
            "                    return;\n" +
            "                }\n" +
            "                let filterClasses = [];\n" +
            "                // Determine filter classes\n" +
            "                if (device.type === 'WIFI') {\n" +
            "                    if (device.encryption === 'open') {\n" +
            "                        filterClasses.push('wifi_open');\n" +
            "                        // WiFi Open without Vodafone: SSID must not contain 'Vodafone Homespot' or 'Vodafone Hotspot'\n" +
            "                        if (typeof device.name === 'string' && device.name.toLowerCase().indexOf('vodafone') === -1 && device.name !== '[Hidden/Unknown]') {\n" +
            "                            filterClasses.push('wifi_open_no_vodafone');\n" +
            "                        }\n" +
            "                    } else {\n" +
            "                        filterClasses.push('wifi_encrypted');\n" +
            "                    }\n" +
            "                } else {\n" +
            "                    filterClasses.push('bluetooth');\n" +
            "                    // Bluetooth without Unknown: Device name must not be 'Unknown device'\n" +
            "                    if (device.name && device.name.trim() !== '' && \n" +
            "                        !device.name.toLowerCase().includes('unknown device') &&\n" +
            "                        device.name !== '[Hidden/Unknown]') {\n" +
            "                        filterClasses.push('bluetooth_known');\n" +
            "                    }\n" +
            "                }\n" +
            "                if (device.signal >= -70) {\n" +
            "                    filterClasses.push('signal_strong');\n" +
            "                } else {\n" +
            "                    filterClasses.push('signal_weak');\n" +
            "                }\n" +
            "                deviceFilterMap.set(device.address, filterClasses);\n" +
            "                \n" +
            "                // Icon and color\n" +
            "                let iconColor, iconName;\n" +
            "                if (device.type === 'WIFI') {\n" +
            "                    if (device.encryption === 'open') {\n" +
            "                        iconColor = 'red';\n" +
            "                    } else {\n" +
            "                        iconColor = 'green';\n" +
            "                    }\n" +
            "                } else {\n" +
            "                    iconColor = 'blue';\n" +
            "                }\n" +
            "                \n" +
            "                // Create marker (don't add to map yet - updateMarkerVisibility will do it)\n" +
            "                let marker = L.marker([device.lat, device.lon]);\n" +
            "                \n" +
            "                // Popup content\n" +
            "                let popupContent = `\n" +
            "                    <b>${device.type === 'WIFI' ? 'SSID' : 'Device'}:</b> ${device.name || '[Hidden/Unknown]'}<br>\n" +
            "                    <b>${device.type === 'WIFI' ? 'BSSID' : 'MAC'}:</b> ${device.address}<br>\n" +
            "                    <b>Signal:</b> ${device.signal} dBm<br>\n" +
            "                    <b>${device.type === 'WIFI' ? 'Encryption' : 'Class'}:</b> ${device.encryption}<br>\n" +
            "                    <b>Manufacturer:</b> ${device.vendor}<br>\n" +
            "                    <b>Coordinates:</b> ${device.lat.toFixed(6)}, ${device.lon.toFixed(6)}\n" +
            "                `;\n" +
            "                \n" +
            "                if (device.type === 'WIFI' && device.frequency) {\n" +
            "                    popupContent += `<br><b>Frequency:</b> ${device.frequency} MHz`;\n" +
            "                }\n" +
            "                if (device.channel) {\n" +
            "                    popupContent += `<br><b>Channel:</b> ${device.channel}`;\n" +
            "                }\n" +
            "                \n" +
            "                marker.bindPopup(popupContent);\n" +
            "                \n" +
            "                // Circle around marker (don't add to map yet)\n" +
            "                let radius = device.signal >= -50 ? 10 : device.signal >= -70 ? 20 : 30;\n" +
            "                let circle = L.circle([device.lat, device.lon], {\n" +
            "                    color: iconColor,\n" +
            "                    fillColor: iconColor,\n" +
            "                    fillOpacity: 0.2,\n" +
            "                    radius: radius,\n" +
            "                    weight: 2\n" +
            "                });\n" +
            "                \n" +
            "                allMarkers.push(marker);\n" +
            "                allCircles.push(circle);\n" +
            "            });\n" +
            "            \n" +
            "            // Initially hide all\n" +
            "            updateMarkerVisibility();\n" +
            "        }\n" +
            "        \n" +
            "        function toggleFilter(filterKey) {\n" +
            "            const checkbox = document.getElementById(filterKey);\n" +
            "            if (checkbox.checked) {\n" +
            "                activeFilters.add(filterKey);\n" +
            "            } else {\n" +
            "                activeFilters.delete(filterKey);\n" +
            "            }\n" +
            "            updateMarkerVisibility();\n" +
            "        }\n" +
            "        \n" +
            "        function toggleAllFilters(enable) {\n" +
            "            const checkboxes = document.querySelectorAll('.custom-control input[type=\"checkbox\"]');\n" +
            "            activeFilters.clear();\n" +
            "            \n" +
            "            checkboxes.forEach(checkbox => {\n" +
            "                checkbox.checked = enable;\n" +
            "                if (enable) {\n" +
            "                    activeFilters.add(checkbox.id);\n" +
            "                }\n" +
            "            });\n" +
            "            \n" +
            "            updateMarkerVisibility();\n" +
            "        }\n" +
            "        \n" +
            "        function updateMarkerVisibility() {\n" +
            "            console.log('updateMarkerVisibility called. Active filters:', activeFilters.size, 'Highlighted:', highlightedMarkerIndices.size);\n" +
            "            \n" +
            "            if (activeFilters.size === 0) {\n" +
            "                // Alle verstecken, außer die hervorgehobenen\n" +
            "                console.log('No filters active - showing only highlighted markers');\n" +
            "                allMarkers.forEach((marker, index) => {\n" +
            "                    if (highlightedMarkerIndices.has(index)) {\n" +
            "                        if (!map.hasLayer(marker)) {\n" +
            "                            map.addLayer(marker);\n" +
            "                            console.log('Added highlighted marker', index);\n" +
            "                        }\n" +
            "                    } else {\n" +
            "                        if (map.hasLayer(marker)) {\n" +
            "                            map.removeLayer(marker);\n" +
            "                        }\n" +
            "                    }\n" +
            "                });\n" +
            "                allCircles.forEach((circle, index) => {\n" +
            "                    if (highlightedMarkerIndices.has(index)) {\n" +
            "                        if (!map.hasLayer(circle)) {\n" +
            "                            map.addLayer(circle);\n" +
            "                            console.log('Added highlighted circle', index);\n" +
            "                        }\n" +
            "                    } else {\n" +
            "                        if (map.hasLayer(circle)) {\n" +
            "                            map.removeLayer(circle);\n" +
            "                        }\n" +
            "                    }\n" +
            "                });\n" +
            "                updateInfoPanel();\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            console.log('Filters active - showing filter matches + highlighted markers');\n" +
            "            deviceData.forEach((device, index) => {\n" +
            "                const deviceFilters = deviceFilterMap.get(device.address) || [];\n" +
            "                const matchesFilter = deviceFilters.some(cls => activeFilters.has(cls));\n" +
            "                const isHighlighted = highlightedMarkerIndices.has(index);\n" +
            "                const shouldShow = matchesFilter || isHighlighted;\n" +
            "                \n" +
            "                const marker = allMarkers[index];\n" +
            "                const circle = allCircles[index];\n" +
            "                \n" +
            "                if (shouldShow) {\n" +
            "                    if (!map.hasLayer(marker)) map.addLayer(marker);\n" +
            "                    if (!map.hasLayer(circle)) map.addLayer(circle);\n" +
            "                } else {\n" +
            "                    if (map.hasLayer(marker)) map.removeLayer(marker);\n" +
            "                    if (map.hasLayer(circle)) map.removeLayer(circle);\n" +
            "                }\n" +
            "            });\n" +
            "            \n" +
            "            updateInfoPanel();\n" +
            "        }\n" +
            "        \n" +
            "        function updateInfoPanel() {\n" +
            "            let wifiCount = 0;\n" +
            "            let bluetoothCount = 0;\n" +
            "            let bluetoothKnownCount = 0;\n" +
            "            let visibleCount = 0;\n" +
            "            \n" +
            "            deviceData.forEach((device, index) => {\n" +
            "                if (device.type === 'WIFI') {\n" +
            "                    wifiCount++;\n" +
            "                } else {\n" +
            "                    bluetoothCount++;\n" +
            "                    // Count Bluetooth without Unknown\n" +
            "                    if (device.name && device.name.trim() !== '' && \n" +
            "                        !device.name.toLowerCase().includes('unknown device') &&\n" +
            "                        device.name !== '[Hidden/Unknown]') {\n" +
            "                        bluetoothKnownCount++;\n" +
            "                    }\n" +
            "                }\n" +
            "                \n" +
            "                const marker = allMarkers[index];\n" +
            "                if (marker && map.hasLayer(marker)) {\n" +
            "                    visibleCount++;\n" +
            "                }\n" +
            "            });\n" +
            "            \n" +
            "            // Retrieve total statistics from Android\n" +
            "            let totalStats = '';\n" +
            "            if (typeof Android !== 'undefined' && Android.getTotalDeviceCount) {\n" +
            "                totalStats = Android.getTotalDeviceCount();\n" +
            "            }\n" +
            "            \n" +
            "            document.getElementById('info-panel').innerHTML = \n" +
            "                `Visible: ${visibleCount} | WiFi: ${wifiCount} | BT: ${bluetoothCount} (${bluetoothKnownCount} known)<br><small>Only in current area | ${totalStats}</small>`;\n" +
            "        }\n" +
            "        \n" +
            "        function requestCenterOnUser() {\n" +
            "            if (typeof Android !== 'undefined' && Android.requestDeviceLocation) {\n" +
            "                Android.requestDeviceLocation();\n" +
            "            } else {\n" +
            "                alert('Location feature not available.');\n" +
            "            }\n" +
            "        }\n" +
            "\n" +
            "        function toggleMapStyle() {\n" +
            "            if (typeof Android !== 'undefined' && Android.toggleMapStyle) {\n" +
            "                Android.toggleMapStyle();\n" +
            "            } else {\n" +
            "                alert('Map style toggle not available.');\n" +
            "            }\n" +
            "        }\n" +
            "\n" +
            "        function centerOnUserLocation(lat, lon) {\n" +
            "            if (!map) return;\n" +
            "            const userLatLng = [lat, lon];\n" +
            "            if (userMarker) {\n" +
            "                userMarker.setLatLng(userLatLng);\n" +
            "            } else {\n" +
            "                const userIcon = L.divIcon({\n" +
            "                    html: '&#128512;',\n" +
            "                    className: 'user-location-icon',\n" +
            "                    iconSize: [20, 20]\n" +
            "                });\n" +
            "                userMarker = L.marker(userLatLng, { icon: userIcon, zIndexOffset: 1000 }).addTo(map);\n" +
            "                userMarker.bindPopup('<b>Your Location</b>');\n" +
            "            }\n" +
            "            map.setView(userLatLng, 15);\n" +
            "        }\n" +
            "\n" +
            "        let searchResults = [];\n" +
            "        let currentSearchIndex = -1;\n" +
            "\n" +
            "        function searchMarkers(query) {\n" +
            "            if (!query || query.trim() === '') {\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            const lowerQuery = query.toLowerCase();\n" +
            "            searchResults = [];\n" +
            "            currentSearchIndex = -1;\n" +
            "            \n" +
            "            // Search through all devices\n" +
            "            deviceData.forEach((device, index) => {\n" +
            "                const name = (device.name || '').toLowerCase();\n" +
            "                const address = (device.address || '').toLowerCase();\n" +
            "                const vendor = (device.vendor || '').toLowerCase();\n" +
            "                \n" +
            "                if (name.includes(lowerQuery) || address.includes(lowerQuery) || vendor.includes(lowerQuery)) {\n" +
            "                    searchResults.push({ device: device, index: index });\n" +
            "                }\n" +
            "            });\n" +
            "            \n" +
            "            if (searchResults.length === 0) {\n" +
            "                if (typeof Android !== 'undefined' && Android.showToast) {\n" +
            "                    Android.showToast('No results found for: ' + query);\n" +
            "                }\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            // Show first result\n" +
            "            currentSearchIndex = 0;\n" +
            "            showSearchResult();\n" +
            "            \n" +
            "            if (typeof Android !== 'undefined' && Android.showToast) {\n" +
            "                Android.showToast('Found ' + searchResults.length + ' results');\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        function showSearchResult() {\n" +
            "            if (searchResults.length === 0 || currentSearchIndex < 0) return;\n" +
            "            \n" +
            "            const result = searchResults[currentSearchIndex];\n" +
            "            const marker = allMarkers[result.index];\n" +
            "            \n" +
            "            if (marker) {\n" +
            "                // Center on marker\n" +
            "                map.setView([result.device.lat, result.device.lon], 18);\n" +
            "                \n" +
            "                // Open popup\n" +
            "                marker.openPopup();\n" +
            "                \n" +
            "                // Highlight marker temporarily\n" +
            "                const circle = allCircles[result.index];\n" +
            "                if (circle) {\n" +
            "                    const originalColor = circle.options.color;\n" +
            "                    circle.setStyle({ color: 'yellow', fillColor: 'yellow' });\n" +
            "                    setTimeout(() => {\n" +
            "                        circle.setStyle({ color: originalColor, fillColor: originalColor });\n" +
            "                    }, 2000);\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        function nextSearchResult() {\n" +
            "            if (searchResults.length === 0) return;\n" +
            "            currentSearchIndex = (currentSearchIndex + 1) % searchResults.length;\n" +
            "            showSearchResult();\n" +
            "        }\n" +
            "        \n" +
            "        function previousSearchResult() {\n" +
            "            if (searchResults.length === 0) return;\n" +
            "            currentSearchIndex = (currentSearchIndex - 1 + searchResults.length) % searchResults.length;\n" +
            "            showSearchResult();\n" +
            "        }\n" +
            "        \n" +
            "        function clearSearch() {\n" +
            "            searchResults = [];\n" +
            "            currentSearchIndex = -1;\n" +
            "            \n" +
            "            // Clear highlighted markers\n" +
            "            highlightedMarkerIndices.clear();\n" +
            "            updateMarkerVisibility();\n" +
            "            \n" +
            "            if (typeof Android !== 'undefined' && Android.showToast) {\n" +
            "                Android.showToast('Search cleared');\n" +
            "            }\n" +
            "        }\n" +
            "        \n" +
            "        function zoomToDevice(deviceAddress, lat, lon) {\n" +
            "            if (!map) return;\n" +
            "            \n" +
            "            console.log('Zooming to device:', deviceAddress, 'at', lat, lon);\n" +
            "            \n" +
            "            // Clear previous highlights\n" +
            "            highlightedMarkerIndices.clear();\n" +
            "            \n" +
            "            // Find the device by address\n" +
            "            let deviceIndex = -1;\n" +
            "            for (let i = 0; i < deviceData.length; i++) {\n" +
            "                if (deviceData[i].address === deviceAddress) {\n" +
            "                    deviceIndex = i;\n" +
            "                    console.log('Found device at index', i, 'with coords', deviceData[i].lat, deviceData[i].lon);\n" +
            "                    break;\n" +
            "                }\n" +
            "            }\n" +
            "            \n" +
            "            if (deviceIndex === -1) {\n" +
            "                console.log('Device not found:', deviceAddress);\n" +
            "                // Fallback: use provided coordinates\n" +
            "                map.setView([lat, lon], 18);\n" +
            "                if (typeof Android !== 'undefined' && Android.showToast) {\n" +
            "                    Android.showToast('Device marker not in current view');\n" +
            "                }\n" +
            "                return;\n" +
            "            }\n" +
            "            \n" +
            "            // Use the actual device coordinates from deviceData\n" +
            "            const actualLat = deviceData[deviceIndex].lat;\n" +
            "            const actualLon = deviceData[deviceIndex].lon;\n" +
            "            const device = deviceData[deviceIndex];\n" +
            "            \n" +
            "            // Add to highlighted markers\n" +
            "            highlightedMarkerIndices.add(deviceIndex);\n" +
            "            \n" +
            "            console.log('Highlighted marker indices:', Array.from(highlightedMarkerIndices));\n" +
            "            \n" +
            "            // Ensure marker is visible by showing it directly\n" +
            "            const marker = allMarkers[deviceIndex];\n" +
            "            const circle = allCircles[deviceIndex];\n" +
            "            \n" +
            "            if (marker) {\n" +
            "                if (!map.hasLayer(marker)) {\n" +
            "                    map.addLayer(marker);\n" +
            "                    console.log('Added main marker to map at index', deviceIndex);\n" +
            "                }\n" +
            "            }\n" +
            "            if (circle) {\n" +
            "                if (!map.hasLayer(circle)) {\n" +
            "                    map.addLayer(circle);\n" +
            "                    console.log('Added main circle to map at index', deviceIndex);\n" +
            "                }\n" +
            "            }\n" +
            "            \n" +
            "            // Zoom to location\n" +
            "            map.setView([actualLat, actualLon], 18);\n" +
            "            \n" +
            "            // Find and show nearby Bluetooth devices (only known ones, within 50m)\n" +
            "            if (device.type === 'WIFI') {\n" +
            "                const nearbyBluetooth = [];\n" +
            "                for (let i = 0; i < deviceData.length; i++) {\n" +
            "                    const btDevice = deviceData[i];\n" +
            "                    if (btDevice.type === 'BLUETOOTH') {\n" +
            "                        // Check if device is known (not unknown/hidden)\n" +
            "                        const isUnknown = !btDevice.name || btDevice.name === '' || \n" +
            "                                        btDevice.name === '[Hidden/Unknown]' || \n" +
            "                                        btDevice.name === '[Unknown Device]' || \n" +
            "                                        btDevice.name.toLowerCase().includes('unknown');\n" +
            "                        \n" +
            "                        if (isUnknown) continue;\n" +
            "                        \n" +
            "                        // Calculate distance\n" +
            "                        const R = 6371e3; // Earth radius in meters\n" +
            "                        const φ1 = actualLat * Math.PI / 180;\n" +
            "                        const φ2 = btDevice.lat * Math.PI / 180;\n" +
            "                        const Δφ = (btDevice.lat - actualLat) * Math.PI / 180;\n" +
            "                        const Δλ = (btDevice.lon - actualLon) * Math.PI / 180;\n" +
            "                        \n" +
            "                        const a = Math.sin(Δφ/2) * Math.sin(Δφ/2) +\n" +
            "                                  Math.cos(φ1) * Math.cos(φ2) *\n" +
            "                                  Math.sin(Δλ/2) * Math.sin(Δλ/2);\n" +
            "                        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));\n" +
            "                        const distance = R * c;\n" +
            "                        \n" +
            "                        if (distance <= 50) {\n" +
            "                            nearbyBluetooth.push({index: i, device: btDevice, distance: distance});\n" +
            "                        }\n" +
            "                    }\n" +
            "                }\n" +
            "                \n" +
            "                // Show nearby Bluetooth devices and add to highlighted set\n" +
            "                console.log('Found', nearbyBluetooth.length, 'known Bluetooth devices within 50m');\n" +
            "                nearbyBluetooth.forEach(item => {\n" +
            "                    highlightedMarkerIndices.add(item.index);\n" +
            "                    const btMarker = allMarkers[item.index];\n" +
            "                    const btCircle = allCircles[item.index];\n" +
            "                    if (btMarker) {\n" +
            "                        if (!map.hasLayer(btMarker)) {\n" +
            "                            map.addLayer(btMarker);\n" +
            "                            console.log('Added BT marker to map at index', item.index);\n" +
            "                        }\n" +
            "                    }\n" +
            "                    if (btCircle) {\n" +
            "                        if (!map.hasLayer(btCircle)) {\n" +
            "                            map.addLayer(btCircle);\n" +
            "                            console.log('Added BT circle to map at index', item.index);\n" +
            "                        }\n" +
            "                    }\n" +
            "                });\n" +
            "                \n" +
            "                // Show 50m radius circle\n" +
            "                if (typeof L !== 'undefined') {\n" +
            "                    const radiusCircle = L.circle([actualLat, actualLon], {\n" +
            "                        color: 'orange',\n" +
            "                        fillColor: 'orange',\n" +
            "                        fillOpacity: 0.1,\n" +
            "                        radius: 50,\n" +
            "                        weight: 2,\n" +
            "                        dashArray: '5, 5'\n" +
            "                    }).addTo(map);\n" +
            "                    \n" +
            "                    // Remove radius circle after 5 seconds\n" +
            "                    setTimeout(() => {\n" +
            "                        map.removeLayer(radiusCircle);\n" +
            "                    }, 5000);\n" +
            "                }\n" +
            "            }\n" +
            "            \n" +
            "            // Open popup\n" +
            "            if (marker) {\n" +
            "                marker.openPopup();\n" +
            "                \n" +
            "                // Highlight marker with yellow circle\n" +
            "                if (circle) {\n" +
            "                    const originalColor = circle.options.color;\n" +
            "                    circle.setStyle({ color: 'yellow', fillColor: 'yellow', weight: 4 });\n" +
            "                    setTimeout(() => {\n" +
            "                        circle.setStyle({ color: originalColor, fillColor: originalColor, weight: 2 });\n" +
            "                    }, 3000);\n" +
            "                }\n" +
            "            } else {\n" +
            "                console.log('Marker not found at index', deviceIndex);\n" +
            "            }\n" +
            "            \n" +
            "            // Hide all other markers that are not highlighted\n" +
            "            console.log('Hiding non-highlighted markers...');\n" +
            "            allMarkers.forEach((m, idx) => {\n" +
            "                if (!highlightedMarkerIndices.has(idx)) {\n" +
            "                    if (map.hasLayer(m)) {\n" +
            "                        map.removeLayer(m);\n" +
            "                    }\n" +
            "                }\n" +
            "            });\n" +
            "            allCircles.forEach((c, idx) => {\n" +
            "                if (!highlightedMarkerIndices.has(idx)) {\n" +
            "                    if (map.hasLayer(c)) {\n" +
            "                        map.removeLayer(c);\n" +
            "                    }\n" +
            "                }\n" +
            "            });\n" +
            "            console.log('Zoom to device complete. Highlighted markers:', Array.from(highlightedMarkerIndices));\n" +
            "        }\n" +
            "\n" +
            "        function updateLocationInDB(deviceAddress, deviceType, newLat, newLon) {\n" +
            "            if (typeof Android !== 'undefined') {\n" +
            "                Android.updateDeviceLocation(deviceAddress, deviceType, newLat, newLon);\n" +
            "            }\n" +
            "        }\n" +
            "        function saveMapState() {\n" +
            "            let filters = Array.from(activeFilters);\n" +
            "            let center = map.getCenter();\n" +
            "            let zoom = map.getZoom();\n" +
            "            if (typeof Android !== 'undefined' && Android.saveMapState) {\n" +
            "                Android.saveMapState(JSON.stringify(filters), center.lat, center.lng, zoom);\n" +
            "            }\n" +
            "        }\n" +
            "        map.on('moveend', saveMapState);\n" +
            "        map.on('zoomend', saveMapState);\n" +
            "        document.querySelectorAll('.custom-control input[type=\"checkbox\"]').forEach(cb => {\n" +
            "            cb.addEventListener('change', saveMapState);\n" +
            "        });\n" +
            "    </script>\n" +
            "</body>\n" +
            "</html>";
    }
    // For compatibility, in case old method is called
    private String generateMapHTML() {
        return generateMapHTML(null, 0, 0, 15, "light");
    }
    
    // JavaScript interface for communication between WebView and Android
    public class WebAppInterface {
        @JavascriptInterface
        public void updateDeviceLocation(String deviceAddress, String deviceType, double newLat, double newLon) {
            runOnUiThread(() -> {
                // Sanitize user input to prevent log injection
                String safeAddress = (deviceAddress == null) ? "" : deviceAddress.replaceAll("[^A-Za-z0-9]", "");
                String safeType = (deviceType == null) ? "" : deviceType.replaceAll("[^A-Za-z0-9]", "");
                Toast.makeText(MapActivity.this,
                    "Position updated for " + safeAddress + " -> " +
                    String.format("%.6f, %.6f", newLat, newLon),
                    Toast.LENGTH_SHORT).show();
                Log.d("MapActivity", String.format("Device %s (type: %s) moved to: %.6f, %.6f", 
                    safeAddress, safeType, newLat, newLon));
            });
        }

        @JavascriptInterface
        public void showToast(String message) {
            // Sanitize message to prevent issues with special characters or injection attempts
            String safeMessage = sanitizeForLogging(message);
            runOnUiThread(() -> Toast.makeText(MapActivity.this, safeMessage, Toast.LENGTH_SHORT).show());
        }

        // Save filters and map status
        @JavascriptInterface
        public void saveMapState(String filters, double lat, double lon, int zoom) {
            // Validate filters is valid JSON array format to prevent injection
            String safeFilters = filters;
            if (filters != null) {
                try {
                    new JSONArray(filters); // Validate JSON format
                } catch (JSONException e) {
                    Log.w("MapActivity", "Invalid filter JSON, ignoring: " + sanitizeForLogging(filters));
                    safeFilters = "[]"; // Default to empty array
                }
            }
            
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(PREF_FILTERS, safeFilters)
                .putLong(PREF_CENTER_LAT, Double.doubleToLongBits(lat))
                .putLong(PREF_CENTER_LON, Double.doubleToLongBits(lon))
                .putInt(PREF_ZOOM, zoom)
                .apply();
        }

        @JavascriptInterface
        public void requestDeviceLocation() {
            runOnUiThread(() -> requestLocationAndCenterMap());
        }

        // NEW METHOD: Bounding box update for performance optimization
        @JavascriptInterface
        public void onMapViewportChanged(double minLat, double minLon, double maxLat, double maxLon) {
            runOnUiThread(() -> {
                Log.d("MapActivity", String.format("Viewport changed: %.6f,%.6f to %.6f,%.6f",
                    minLat, minLon, maxLat, maxLon));

                // Only reload if viewport has changed significantly
                double latDiff = Math.abs(bboxMinLat - minLat) + Math.abs(bboxMaxLat - maxLat);
                double lonDiff = Math.abs(bboxMinLon - minLon) + Math.abs(bboxMaxLon - maxLon);

                if (latDiff > 0.001 || lonDiff > 0.001) { // Threshold for update
                    bboxMinLat = minLat;
                    bboxMinLon = minLon;
                    bboxMaxLat = maxLat;
                    bboxMaxLon = maxLon;

                    // Load new markers for the visible area
                    List<DeviceData> newDevices = getDevicesInBoundingBox(minLat, minLon, maxLat, maxLon);
                    deviceList = newDevices;

                    Log.d("MapActivity", "Loaded " + newDevices.size() + " devices for new viewport");

                    // Send data to WebView
                    if (!newDevices.isEmpty()) {
                        injectDeviceData();
                    } else {
                        // Also send empty list so UI gets updated
                        injectDeviceData();
                    }
                }
            });
        }

        // NEW METHOD: Query total number of devices in DB
        @JavascriptInterface
        public String getTotalDeviceCount() {
            try {
                Cursor cursor = dbRawQuery(
                    "SELECT COUNT(*) as total_devices, " +
                    "SUM(CASE WHEN device_type = 'WIFI' THEN 1 ELSE 0 END) as wifi_count, " +
                    "SUM(CASE WHEN device_type != 'WIFI' THEN 1 ELSE 0 END) as bt_count " +
                    "FROM device_data WHERE latitude != 0 AND longitude != 0", null);
                
                if (cursor.moveToFirst()) {
                    int total = cursor.getInt(0);
                    int wifi = cursor.getInt(1);
                    int bt = cursor.getInt(2);
                    cursor.close();
                    return String.format(getString(R.string.total_stats), total, wifi, bt);
                }
                cursor.close();
            } catch (Exception e) {
                Log.e("MapActivity", "Error getting total device count: " + e.getMessage());
            }
            return getString(R.string.total_unknown);
        }

        @JavascriptInterface
        public void toggleMapStyle() {
            runOnUiThread(() -> {
                android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String currentStyle = prefs.getString(PREF_MAP_STYLE, "light");
                String newStyle = currentStyle.equals("dark") ? "light" : "dark";
                
                prefs.edit().putString(PREF_MAP_STYLE, newStyle).apply();
                
                // Change tile layer via JavaScript without reloading the entire page
                String tileLayerUrl;
                String tileLayerAttribution;
                if ("dark".equals(newStyle)) {
                    tileLayerUrl = "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png";
                    tileLayerAttribution = "© OpenStreetMap contributors © CARTO";
                } else {
                    tileLayerUrl = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";
                    tileLayerAttribution = "© OpenStreetMap contributors";
                }
                
                String jsCode = String.format(
                    "if (typeof map !== 'undefined' && map) {" +
                    "  map.eachLayer(function(layer) {" +
                    "    if (layer instanceof L.TileLayer) {" +
                    "      map.removeLayer(layer);" +
                    "    }" +
                    "  });" +
                    "  L.tileLayer('%s', { attribution: '%s' }).addTo(map);" +
                    "  document.getElementById('mapStyleBtn').innerHTML = 'Map Style: %s';" +
                    "}",
                    tileLayerUrl, tileLayerAttribution, newStyle.equals("dark") ? "Dark" : "Light"
                );
                
                mapWebView.evaluateJavascript(jsCode, null);
                
                Toast.makeText(MapActivity.this, 
                    "Map Style: " + (newStyle.equals("dark") ? "Dark" : "Light"), 
                    Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private void toggleSearchBar() {
        if (searchBarLayout.getVisibility() == View.VISIBLE) {
            searchBarLayout.setVisibility(View.GONE);
        } else {
            searchBarLayout.setVisibility(View.VISIBLE);
        }
    }
    
    private void performSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, R.string.no_search_results, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Hide keyboard
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        
        // First try database search
        java.util.List<DeviceData> searchResults = searchInDatabase(query);
        
        // Fallback: if database search returns nothing, search all loaded devices
        if (searchResults.isEmpty() && deviceList != null && !deviceList.isEmpty()) {
            Log.d("MapActivity", "Database search found nothing, trying deviceList fallback");
            String lowerQuery = query.toLowerCase();
            for (DeviceData device : deviceList) {
                String name = device.name != null ? device.name.toLowerCase() : "";
                String address = device.address != null ? device.address.toLowerCase() : "";
                String vendor = device.vendor != null ? device.vendor.toLowerCase() : "";
                
                if (name.contains(lowerQuery) || address.contains(lowerQuery) || vendor.contains(lowerQuery)) {
                    searchResults.add(device);
                }
            }
            Log.d("MapActivity", "Fallback search in deviceList found " + searchResults.size() + " results");
        }
        
        if (searchResults.isEmpty()) {
            Toast.makeText(this, "No results found for: " + query, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show results in a dialog
        showSearchResultsDialog(searchResults, query);
    }
    
    private java.util.List<DeviceData> searchInDatabase(String query) {
        java.util.List<DeviceData> results = new java.util.ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        // Sanitize query for logging to prevent log injection
        String safeQuery = sanitizeForLogging(query);
        Log.d("MapActivity", "Starting database search for: '" + safeQuery + "', encrypted=" + isDatabaseEncrypted);
        
        if (database == null) {
            Log.e("MapActivity", "Database is null!");
            return results;
        }
        
        // Try to get all tables first
        try {
            android.database.Cursor tablesCursor = null;
            if (isDatabaseEncrypted) {
                net.sqlcipher.database.SQLiteDatabase sqlCipherDb = (net.sqlcipher.database.SQLiteDatabase) database;
                tablesCursor = sqlCipherDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
            } else {
                android.database.sqlite.SQLiteDatabase stdDb = (android.database.sqlite.SQLiteDatabase) database;
                tablesCursor = stdDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
            }
            
            if (tablesCursor != null) {
                Log.d("MapActivity", "Available tables:");
                while (tablesCursor.moveToNext()) {
                    Log.d("MapActivity", "  - " + tablesCursor.getString(0));
                }
                tablesCursor.close();
            }
        } catch (Exception e) {
            Log.e("MapActivity", "Error listing tables: " + e.getMessage());
        }
        
        // Try device_data table
        try {
            android.database.Cursor cursor = null;
            String sql = "SELECT name, address, type, signal, encryption, latitude, longitude, timestamp, " +
                        "COALESCE(vendor, ''), COALESCE(frequency, 0), COALESCE(channel, 0), COALESCE(standard, ''), " +
                        "COALESCE(channel_width, 0), COALESCE(max_speed, 0) " +
                        "FROM device_data WHERE LOWER(name) LIKE ? OR LOWER(address) LIKE ? OR LOWER(vendor) LIKE ?";
            
            Log.d("MapActivity", "Executing query: " + sql);
            
            if (isDatabaseEncrypted) {
                net.sqlcipher.database.SQLiteDatabase sqlCipherDb = (net.sqlcipher.database.SQLiteDatabase) database;
                cursor = sqlCipherDb.rawQuery(sql, new String[]{"%" + lowerQuery + "%", "%" + lowerQuery + "%", "%" + lowerQuery + "%"});
            } else {
                android.database.sqlite.SQLiteDatabase stdDb = (android.database.sqlite.SQLiteDatabase) database;
                cursor = stdDb.rawQuery(sql, new String[]{"%" + lowerQuery + "%", "%" + lowerQuery + "%", "%" + lowerQuery + "%"});
            }
            
            if (cursor != null) {
                int count = cursor.getCount();
                Log.d("MapActivity", "device_data query returned " + count + " rows");
                
                while (cursor.moveToNext()) {
                    DeviceData device = new DeviceData();
                    device.name = cursor.getString(0);
                    device.address = cursor.getString(1);
                    device.type = cursor.getString(2);
                    device.signal = cursor.getInt(3);
                    device.encryption = cursor.getString(4);
                    device.lat = cursor.getDouble(5);
                    device.lon = cursor.getDouble(6);
                    device.timestamp = cursor.getLong(7);
                    device.vendor = cursor.getString(8);
                    device.frequency = cursor.getInt(9);
                    device.channel = cursor.getInt(10);
                    device.standard = cursor.getString(11);
                    device.channelWidth = cursor.getInt(12);
                    device.maxSpeed = cursor.getInt(13);
                    results.add(device);
                    Log.d("MapActivity", "Found: " + device.name + " (" + device.address + ")");
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e("MapActivity", "Error querying device_data: " + e.getMessage(), e);
            e.printStackTrace();
        }
        
        // Try wifi_data table
        try {
            android.database.Cursor cursor = null;
            String sql = "SELECT ssid, bssid, 'WIFI' as type, signal_strength, encryption, latitude, longitude, timestamp, " +
                        "COALESCE(vendor, ''), COALESCE(frequency, 0), COALESCE(channel, 0), COALESCE(standard, ''), " +
                        "COALESCE(channel_width, 0), COALESCE(max_speed, 0) " +
                        "FROM wifi_data WHERE LOWER(ssid) LIKE ? OR LOWER(bssid) LIKE ? OR LOWER(vendor) LIKE ?";
            
            if (isDatabaseEncrypted) {
                net.sqlcipher.database.SQLiteDatabase sqlCipherDb = (net.sqlcipher.database.SQLiteDatabase) database;
                cursor = sqlCipherDb.rawQuery(sql, new String[]{"%" + lowerQuery + "%", "%" + lowerQuery + "%", "%" + lowerQuery + "%"});
            } else {
                android.database.sqlite.SQLiteDatabase stdDb = (android.database.sqlite.SQLiteDatabase) database;
                cursor = stdDb.rawQuery(sql, new String[]{"%" + lowerQuery + "%", "%" + lowerQuery + "%", "%" + lowerQuery + "%"});
            }
            
            if (cursor != null) {
                Log.d("MapActivity", "wifi_data query returned " + cursor.getCount() + " rows");
                while (cursor.moveToNext()) {
                    DeviceData device = new DeviceData();
                    device.name = cursor.getString(0);
                    device.address = cursor.getString(1);
                    device.type = cursor.getString(2);
                    device.signal = cursor.getInt(3);
                    device.encryption = cursor.getString(4);
                    device.lat = cursor.getDouble(5);
                    device.lon = cursor.getDouble(6);
                    device.timestamp = cursor.getLong(7);
                    device.vendor = cursor.getString(8);
                    device.frequency = cursor.getInt(9);
                    device.channel = cursor.getInt(10);
                    device.standard = cursor.getString(11);
                    device.channelWidth = cursor.getInt(12);
                    device.maxSpeed = cursor.getInt(13);
                    
                    // Check for duplicates
                    boolean isDuplicate = false;
                    for (DeviceData existing : results) {
                        if (existing.address.equals(device.address)) {
                            isDuplicate = true;
                            break;
                        }
                    }
                    if (!isDuplicate) {
                        results.add(device);
                        Log.d("MapActivity", "Found: " + device.name + " (" + device.address + ")");
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.w("MapActivity", "wifi_data not available: " + e.getMessage());
        }
        
        Log.d("MapActivity", "Total search results: " + results.size());
        return results;
    }
    
    private void showSearchResultsDialog(java.util.List<DeviceData> results, String query) {
        // Create dialog items
        String[] items = new String[results.size()];
        for (int i = 0; i < results.size(); i++) {
            DeviceData device = results.get(i);
            String name = device.name != null && !device.name.isEmpty() ? device.name : "[Hidden/Unknown]";
            String type = device.type.equals("WIFI") ? "📶" : "🔵";
            String signal = device.signal + " dBm";
            items[i] = String.format("%s %s\n%s | %s", type, name, device.address, signal);
        }
        
        // Create and show dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        String safeQuery = sanitizeForLogging(query);
        builder.setTitle(String.format(getString(R.string.search_results), results.size()) + " for: " + safeQuery);
        builder.setItems(items, (dialog, which) -> {
            // User selected a result - zoom to it on map
            DeviceData selectedDevice = results.get(which);
            String jsCode = String.format("javascript:zoomToDevice('%s', %f, %f);", 
                selectedDevice.address.replace("'", "\\'"),
                selectedDevice.lat,
                selectedDevice.lon);
            mapWebView.evaluateJavascript(jsCode, null);
        });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.show();
    }
    
    private void clearSearch() {
        searchInput.setText("");
        
        // Hide keyboard
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        
        // Clear search in JavaScript
        mapWebView.evaluateJavascript("javascript:clearSearch();", null);
    }
    
    private void requestLocationAndCenterMap() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        mapWebView.evaluateJavascript("javascript:centerOnUserLocation(" + location.getLatitude() + ", " + location.getLongitude() + ");", null);
                        Toast.makeText(MapActivity.this, R.string.centering_on_location, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MapActivity.this, R.string.location_unavailable, Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestLocationAndCenterMap();
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Unregister screen off receiver
        unregisterScreenOffReceiver();
        
        if (database != null) {
            dbClose();
        }

        // Delete temporary external DB
        String extDbPath = getIntent().getStringExtra("external_db_path");
        if (extDbPath != null) {
            java.io.File tempFile = new java.io.File(extDbPath);
            if (tempFile.exists() && tempFile.getAbsolutePath().contains("cache")) {
                tempFile.delete();
                Log.d("MapActivity", "Temporary external database file deleted");
            }
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Only clear encryption key if this is NOT an internal navigation
        // (i.e., user pressed Home button or switched apps, not Back button)
        if (!isInternalNavigation && isDatabaseEncrypted && encryptionManager != null) {
            String cachedKey = encryptionManager.getCachedDatabaseKey();
            if (cachedKey != null) {
                clearEncryptionKey();
                Log.d("MapActivity", "App paused (external) - encryption key cleared");
            }
        } else if (isInternalNavigation) {
            Log.d("MapActivity", "App paused (internal navigation) - key NOT cleared");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If database is encrypted and key was cleared, require unlock
        if (isDatabaseEncrypted && encryptionManager != null) {
            String cachedKey = encryptionManager.getCachedDatabaseKey();
            if (cachedKey == null) {
                Log.d("MapActivity", "App resumed with encrypted DB but no key - showing unlock screen");
                Intent unlockIntent = new Intent(this, DatabaseUnlockActivity.class);
                startActivityForResult(unlockIntent, 9999);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9999) {
            if (resultCode == RESULT_OK) {
                Log.d("MapActivity", "Database unlocked successfully - reloading data");
                // Reinitialize database connection and reload data
                initializeDatabase();
                if (database != null) {
                    loadDataAndShowMap();
                } else {
                    Toast.makeText(this, "Failed to open database", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                Log.w("MapActivity", "Database unlock failed or cancelled - closing activity");
                Toast.makeText(this, "Database unlock required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
    
    private void registerScreenOffReceiver() {
        screenOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    Log.d("MapActivity", "Screen turned off - clearing encryption key");
                    clearEncryptionKey();
                }
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenOffReceiver, filter);
        Log.d("MapActivity", "Screen off receiver registered");
    }

    private void unregisterScreenOffReceiver() {
        if (screenOffReceiver != null) {
            try {
                unregisterReceiver(screenOffReceiver);
                Log.d("MapActivity", "Screen off receiver unregistered");
            } catch (IllegalArgumentException e) {
                // Receiver was not registered
            }
        }
    }

    private void clearEncryptionKey() {
        if (encryptionManager != null) {
            encryptionManager.clearKey();
            Log.d("MapActivity", "Encryption key cleared from RAM");
            
            // Close database if it's encrypted
            if (isDatabaseEncrypted && database != null) {
                dbClose();
                Log.d("MapActivity", "Encrypted database closed");
            }
        }
    }
}
