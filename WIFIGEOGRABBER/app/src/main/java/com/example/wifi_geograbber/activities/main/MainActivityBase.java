package com.example.wifi_geograbber.activities.main;

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

import com.example.wifi_geograbber.activities.DatabaseUnlockActivity;
import com.example.wifi_geograbber.activities.MapActivity;
import com.example.wifi_geograbber.services.ScanService;
import com.example.wifi_geograbber.utils.BiometricAuthManager;
import com.example.wifi_geograbber.utils.DatabaseEncryptionHelper;
import com.example.wifi_geograbber.utils.EncryptionManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.json.JSONObject;

public class MainActivityBase extends AppCompatActivity {
    /**
     * =============================
     *   CODE VERSION MARKER
     *   APP_VERSION: 1.0.6
     * =============================
     * Use this variable to visually distinguish code versions.
     */
    public static final String APP_VERSION = "1.0.6";

    /**
     * Redirect all database files to external app-specific storage so they
     * survive Studio reinstalls (adb install -r).
     */
    @Override
    public File getDatabasePath(String name) {
        return DatabaseEncryptionHelper.getDbFile(this, name);
    }
    protected WifiManager wifiManager;
    protected LocationManager locationManager;
    protected BluetoothAdapter bluetoothAdapter;
    protected Object database; // Can be android.database.sqlite.SQLiteDatabase or net.sqlcipher.database.SQLiteDatabase

    // Database encryption support
    protected EncryptionManager encryptionManager;
    protected BiometricAuthManager biometricAuthManager;
    protected DatabaseEncryptionHelper encryptedDbHelper;
    protected boolean isDatabaseEncrypted = false;

    protected TextView statusText;
    protected TextView infoSummary;
    protected TextView logcatText;
    protected TextView encryptionStatusText;
    protected ScrollView logcatScrollView;
    protected ListView dataListView;
    protected Button toggleScanButton, showButton, moreButton, bluetoothToggleButton, mapButton;
    protected View securityOverlay;  // Black overlay to hide content during passphrase entry
    protected boolean isNavigatingInternally = false;  // Flag to track internal navigation (e.g., to MapActivity)
    protected boolean isUnlockDialogShowing = false;  // Flag to prevent multiple unlock dialogs
    protected android.app.AlertDialog currentDialog = null;  // Track currently open dialog to dismiss it
    protected Handler handler;
    protected Runnable scanRunnable;
    protected boolean isScanning = false;
    protected boolean isBluetoothScanningEnabled = false;
    protected boolean isShowingStoredData = false;
    protected boolean receiversRegistered = false;
    protected boolean isUsingExternalDatabase = false;
    protected String currentDatabasePath = null;
    protected static final int PERMISSION_REQUEST_CODE = 100;
    protected static final int EXPORT_DB_REQUEST_CODE = 101;
    protected static final int IMPORT_DB_REQUEST_CODE = 102;
    protected static final int IMPORT_ACTIVE_DB_REQUEST_CODE = 103;
    protected static final int EXPORT_CHECKSUM_REQUEST_CODE = 104;
    protected static final int IMPORT_CHECKSUM_REQUEST_CODE = 105;
    protected static final long SCAN_INTERVAL = 5000; // 5 seconds - very frequently
    protected static final long MIN_SCAN_INTERVAL = 3000; // Minimum 3 seconds between scans
    protected long lastScanTime = 0;
    protected StringBuilder logBuffer = new StringBuilder();

    // Store checksum data for export
    protected String lastExportedDbChecksum = null;
    protected String lastExportedDbFilename = null;

    // Store import data temporarily during checksum verification
    protected android.net.Uri pendingImportUri = null;
    protected java.io.File pendingImportFile = null;
    protected byte[] pendingEncryptionSalt = null;  // Salt from metadata for encrypted DB import

    // Hide system UI (navigation bar)
    protected void hideSystemUI() {
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

    // LogCat functions
    protected void addLogMessage(String message) {
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

    /**
     * Check if app is currently in foreground
     */
    protected boolean isInForeground() {
        android.app.ActivityManager.RunningAppProcessInfo appProcessInfo = new android.app.ActivityManager.RunningAppProcessInfo();
        android.app.ActivityManager.getMyMemoryState(appProcessInfo);
        return (appProcessInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                appProcessInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE);
    }

    protected void dismissAllDialogs() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }
    }
}