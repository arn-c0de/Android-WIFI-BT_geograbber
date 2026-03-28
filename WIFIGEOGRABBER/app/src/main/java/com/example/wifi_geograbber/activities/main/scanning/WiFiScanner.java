package com.example.wifi_geograbber.activities.main.scanning;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import androidx.core.app.ActivityCompat;

import java.util.List;

public class WiFiScanner {
    private WifiManager wifiManager;
    private android.content.Context context;
    private long lastScanTime = 0;
    private static final long MIN_SCAN_INTERVAL = 3000; // Minimum 3 seconds between scans

    public WiFiScanner(WifiManager wifiManager, android.content.Context context) {
        this.wifiManager = wifiManager;
        this.context = context;
    }

    public void startWifiScan() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Always retrieve and display existing results first.
            List<ScanResult> cachedResults = wifiManager.getScanResults();
            
            // Only attempt a new scan if enough time has passed.
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastScanTime >= MIN_SCAN_INTERVAL) {
                lastScanTime = currentTime;
                
                try {
                    boolean scanStarted = wifiManager.startScan();
                    if (scanStarted) {
                        android.util.Log.d("WiFiScanner", "WiFi scan started successfully");
                    } else {
                        android.util.Log.d("WiFiScanner", "WiFi scan failed - Android limitation");
                    }
                } catch (SecurityException e) {
                    android.util.Log.e("WiFiScanner", "WiFi scan blocked - Security Exception");
                }
            }
        }
    }

    public List<ScanResult> getScanResults() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return wifiManager.getScanResults();
        }
        return null;
    }

    public boolean isWifiEnabled() {
        return wifiManager.isWifiEnabled();
    }

    // Auxiliary functions for advanced WiFi data extraction
    public String getWiFiStandard(ScanResult result) {
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
    
    public int getChannelWidth(ScanResult result) {
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
    
    public String getVendorOUI(String bssid) {
        if (bssid != null && bssid.length() >= 8) {
            String oui = bssid.substring(0, 8).toUpperCase();
            return "OUI-" + oui.substring(0, 6); // Simplified OUI output for DB
        }
        return "Unknown";
    }
    
    public int getWiFiChannel(int frequency) {
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
    
    public int estimateMaxSpeed(ScanResult result) {
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
}