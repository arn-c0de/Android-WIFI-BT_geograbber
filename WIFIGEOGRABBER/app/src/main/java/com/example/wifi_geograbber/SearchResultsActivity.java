package com.example.wifi_geograbber;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class SearchResultsActivity extends AppCompatActivity {

    private ListView resultsListView;
    private TextView resultsCountText;
    private EditText searchQueryText;
    private Button backButton;
    private Button showAllOnMapButton;
    
    private List<MapActivity.DeviceData> searchResults;
    private String searchQuery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        // Initialize views
        resultsListView = findViewById(R.id.results_list);
        resultsCountText = findViewById(R.id.results_count_text);
        searchQueryText = findViewById(R.id.search_query_text);
        backButton = findViewById(R.id.back_button);
        showAllOnMapButton = findViewById(R.id.show_all_on_map_button);

        // Get search results from intent
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("search_results") && intent.hasExtra("search_query")) {
            searchResults = intent.getParcelableArrayListExtra("search_results");
            searchQuery = intent.getStringExtra("search_query");
        }

        if (searchResults == null) {
            searchResults = new ArrayList<>();
        }

        if (searchQuery == null) {
            searchQuery = "";
        }

        // Set up UI
        searchQueryText.setText(searchQuery);
        resultsCountText.setText(searchResults.size() + " results found for: " + searchQuery);

        // Create and set adapter
        List<String> resultStrings = new ArrayList<>();
        for (MapActivity.DeviceData device : searchResults) {
            String name = device.name != null && !device.name.isEmpty() ? device.name : "[Hidden/Unknown]";
            String type = device.type != null && device.type.equals("WIFI") ? "📶" : "🔵";
            String signal = device.signal + " dBm";
            String location = String.format("%.6f, %.6f", device.lat, device.lon);
            String vendor = device.vendor != null && !device.vendor.isEmpty() ? device.vendor : "Unknown";
            
            resultStrings.add(String.format("%s %s\n%s | %s\n%s | %s", 
                type, name, device.address, signal, vendor, location));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_list_item_1, resultStrings);
        resultsListView.setAdapter(adapter);

        // Set up click listeners
        backButton.setOnClickListener(v -> finish());

        showAllOnMapButton.setOnClickListener(v -> {
            // Return all results to MapActivity to show on map
            Intent resultIntent = new Intent();
            resultIntent.putExtra("show_all_results", true);
            resultIntent.putExtra("search_results", new ArrayList<>(searchResults));
            setResult(RESULT_OK, resultIntent);
            finish();
        });

        // Set item click listener to zoom to specific device
        resultsListView.setOnItemClickListener((parent, view, position, id) -> {
            MapActivity.DeviceData selectedDevice = searchResults.get(position);
            Intent resultIntent = new Intent();
            resultIntent.putExtra("zoom_to_device", true);
            resultIntent.putExtra("device_address", selectedDevice.address);
            resultIntent.putExtra("device_lat", selectedDevice.lat);
            resultIntent.putExtra("device_lon", selectedDevice.lon);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}