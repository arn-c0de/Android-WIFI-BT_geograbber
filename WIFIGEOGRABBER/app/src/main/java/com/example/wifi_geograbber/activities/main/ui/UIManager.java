package com.example.wifi_geograbber.activities.main.ui;

import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.net.wifi.ScanResult;
import java.util.ArrayList;
import java.util.List;

public class UIManager {
    private TextView statusText;
    private TextView infoSummary;
    private ListView dataListView;
    private android.content.Context context;

    public UIManager(TextView statusText, TextView infoSummary, ListView dataListView, android.content.Context context) {
        this.statusText = statusText;
        this.infoSummary = infoSummary;
        this.dataListView = dataListView;
        this.context = context;
    }

    public void updateStatus(String message) {
        if (statusText != null) {
            statusText.setText(message);
        }
    }

    public void updateInfoSummary(String message) {
        if (infoSummary != null) {
            infoSummary.setText(message);
        }
    }

    public void displayResults(List<ScanResult> results) {
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, dataList);
        dataListView.setAdapter(adapter);
    }

    public void updateInfoSummary(int activeWifi, int activeBluetooth, int totalWifi, int totalBluetooth, int oldWifiCount) {
        String infoText = context.getString(com.example.wifi_geograbber.R.string.active_label) + activeWifi + context.getString(com.example.wifi_geograbber.R.string.wifi_label);
        if (activeBluetooth > 0) {
            infoText += ", " + activeBluetooth + context.getString(com.example.wifi_geograbber.R.string.bt_label);
        } else {
            infoText += context.getString(com.example.wifi_geograbber.R.string.bt_disabled_label);
        }
        infoText += context.getString(com.example.wifi_geograbber.R.string.total_label) + totalWifi + context.getString(com.example.wifi_geograbber.R.string.wifi_label) + ", " + totalBluetooth + context.getString(com.example.wifi_geograbber.R.string.bt_label);
        if (oldWifiCount > 0) {
            infoText += String.format(context.getString(com.example.wifi_geograbber.R.string.old_count_label), oldWifiCount);
        }

        infoSummary.setText(infoText);
    }
}