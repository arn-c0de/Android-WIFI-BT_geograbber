# WiFi/Bluetooth Map Viewer - Quickstart Guide

## Overview

The **Map Viewer** (`start_plot_gui.py`) is an interactive map visualization tool for WiFi and Bluetooth scan data from the WIFIGEOGRABBER project. It creates a browser-based, interactive map with extensive filtering and search capabilities.

## Features

### Main Functions
- **Interactive Map**: Visualize all WiFi and Bluetooth devices on OpenStreetMap
- **Advanced Search**: Search by SSID or MAC address
- **Smart Filters**: Filter by device type, encryption, signal strength
- **Bluetooth Proximity Search**: Automatic display of Bluetooth devices within 50m radius of WiFi APs
- **Movement Tracking**: Visualize device movements between scan positions
- **Drag & Drop**: Movable markers for GPS coordinate correction
- **Highlight Function**: Click-to-Highlight with Middle Mouse Button or Right Click

### Advanced Features
- Automatic duplicate detection
- Vendor/Manufacturer recognition via OUI (MAC prefix)
- Signal-based circle sizes
- Toggle function for highlights
- Export position changes as JSON

## Installation

### Prerequisites
```bash
pip install folium sqlite3 tkinter
```

### System Requirements
- Python 3.7+
- Modern web browser (Chrome, Firefox, Edge)
- Internet connection for map tiles (OpenStreetMap)

## Quick Start

### 1. Launch Program
```bash
python Python/start_plot_gui.py
```

### 2. Select Database
A file selection dialog opens:
1. Navigate to your `.db` files folder
2. Select a database
3. Click "Open"

### 3. Map Opens in Browser
The map opens automatically in your default browser with all scanned devices.

## User Interface

### Marker Types

| Icon | Color | Meaning |
|------|-------|---------|
| 📶 (WiFi) | 🔴 Red | Open WiFi network |
| 🔒 (Lock) | 🟢 Green | Encrypted WiFi network |
| 📱 (Bluetooth) | 🔵 Blue | Bluetooth device |
| 📶 (WiFi) | 🔴 Dark Red | Open WiFi with movement data |
| 🔒 (Lock) | 🟢 Dark Green | Encrypted WiFi with movement data |
| 📱 (Bluetooth) | 🔵 Dark Blue | Bluetooth with movement data |

### Signal-Based Circles
Each marker has a circle whose radius represents signal strength:
- **Small (10m)**: Very strong signal (≥ -50 dBm)
- **Medium (20m)**: Strong signal (-50 to -70 dBm)
- **Large (30m)**: Medium signal (-70 to -80 dBm)
- **Very Large (40m)**: Weak signal (< -80 dBm)

## Main Functions

### 1. Marker Control (Top Left)

#### Reset Markers
Resets all moved markers to their original positions (reloads page).

#### Save Positions
Exports all position changes as JSON file:
1. Click "Save Positions"
2. Confirm changes
3. The file `wifi_scanner_updates.json` will be downloaded
4. Place the file in the program folder
5. Updates will be automatically written to database on next startup

#### Debug Markers
Shows debug information about visible and hidden markers.

#### BT Proximity Mode
Toggle between three modes for Bluetooth devices when highlighting:
- **Known Only**: Shows only Bluetooth devices with names (default)
- **All**: Shows all Bluetooth devices including "Unknown Device"
- **None**: No Bluetooth proximity display

### 2. Filters (Top Left)

Multiple filters can be active simultaneously:

#### Device Type Filters
- **WiFi Open**: Only open WiFi networks
- **WiFi Encrypted**: Only encrypted WiFi networks
- **Bluetooth**: All Bluetooth devices
- **Bluetooth without Unknown**: Only Bluetooth devices with names

#### Special Filters
- **Devices with Movement**: Only devices with movement tracking
- **Vodafone Homespot/Hotspot**: Only Vodafone hotspots
- **WiFi Open without Vodafone**: Open WiFi excluding Vodafone

#### Signal Strength Filters
- **Signal Very Strong** (≥ -50 dBm)
- **Signal Strong** (-50 to -70 dBm)
- **Signal Medium** (-70 to -80 dBm)
- **Signal Weak** (< -80 dBm)

**Filter Buttons:**
- **All On**: Enables all filters
- **All Off**: Disables all filters (hides all markers)

### 3. Search Function (Top Center)

**Search by SSID or MAC Address:**
1. Enter at least 2 characters
2. Substring search works (e.g., "Eduard" or "db:7a")
3. With one result, it's automatically highlighted
4. With multiple results, a selection list appears

**Search Results:**
- Name (SSID)
- MAC Address (BSSID)
- Signal Strength
- Encryption

**Automatic Highlighting:**
- Golden ring around device
- Pulsing outer ring
- Zoom to position
- For WiFi: 50m search radius with nearby Bluetooth devices

### 4. Legend (Top Right)

Shows statistics:
- Count WiFi Open
- Count WiFi Encrypted
- Count Bluetooth devices
- Count Bluetooth without "Unknown"
- Count Vodafone Homespots
- Count WiFi Open without Vodafone
- Total device count
- Database name

**Highlight Button:**
When you click on a marker, it's displayed in the legend with a "Highlight" button for re-highlighting.

## Interactive Features

### Move Markers (Drag & Drop)

**Step by Step:**
1. Click on a marker and hold the mouse button
2. Drag the marker to the desired position
3. Release the mouse button
4. A confirmation dialog appears with new coordinates
5. Confirm with "OK"
6. The change is queued

**Save Changes:**
- Click "Save Positions" to export all changes as JSON
- The file will be automatically processed on next program start

**Discard Changes:**
- Click "Reset Markers" to discard all changes

### Highlight Markers (Middle Mouse Button / Right Click)

**Method 1: Middle Mouse Button**
1. Click with middle mouse button (scroll wheel) on a marker
2. The device is highlighted
3. The SSID is automatically entered in the search bar
4. Another click on the same marker removes the highlight (Toggle)

**Method 2: Right Click**
1. Right-click on a marker
2. The context menu is blocked
3. The device is highlighted like with middle mouse button

**Highlighting Effects:**
- Golden ring (80m radius)
- Pulsing outer ring (120m radius)
- For WiFi: 50m search radius (orange dashed)
- For WiFi: Automatic display of nearby Bluetooth devices
- Zoom to position (Zoom level 18)

### Display Movement Tracking

**When clicking on a marker with movement data:**
1. A gray marker appears at the last known position
2. A dashed black line connects both positions
3. The distance is shown in the popup
4. Timestamps of both positions are displayed

**Movement Data in Popup:**
- **Last Position**: GPS coordinates of previous sighting
- **Last Sighting**: Timestamp
- **Movement**: Distance in meters

### Bluetooth Proximity Search (only for WiFi highlight)

**Automatic:**
When you highlight a WiFi network, all Bluetooth devices within 50m radius are automatically displayed:
- Violet markers for Bluetooth devices
- Name label above each Bluetooth marker
- Small violet circle around each Bluetooth device
- Popup with Bluetooth information

**Modes:**
- **Known Only** (default): Shows only Bluetooth devices with names
- **All**: Shows all Bluetooth devices including "Unknown Device"
- **None**: No Bluetooth display

**Toggle:**
Click the "BT Proximity" button at top left to switch between modes.

## Use Cases

### Use Case 1: Find Open WiFi Networks

1. Enable the "WiFi Open" filter
2. The map shows only open networks (red markers)
3. Optional: Enable "WiFi Open without Vodafone" to exclude Vodafone hotspots

**Result:** Quick overview of all open WiFi networks in your area.

### Use Case 2: Find Strong Signals in Specific Area

1. Enable "Signal Very Strong" or "Signal Strong"
2. Zoom into the desired area
3. Click on markers to see details

**Result:** Identification of the best WiFi hotspots in a specific region.

### Use Case 3: Bluetooth Devices Near a WiFi AP

1. Search for a WiFi network (e.g., "Starbucks")
2. The network is highlighted
3. All Bluetooth devices within 50m radius are automatically displayed
4. Use the "BT Proximity" button to switch between "Known Only", "All", or "None"

**Result:** Analysis of device density and possible device owners near an AP.

### Use Case 4: Track Device Movements

1. Enable the "Devices with Movement" filter
2. Click on a marker
3. The movement line and previous position are displayed
4. Popup shows distance and timestamps of both positions

**Result:** Tracking of mobile devices (e.g., smartphones, tablets).

### Use Case 5: Correct GPS Coordinates

1. Drag a marker to the correct position
2. Confirm the new position
3. Repeat for all incorrectly positioned markers
4. Click "Save Positions"
5. The JSON file is downloaded
6. Place the file in the program folder
7. Restart the program

**Result:** Corrected GPS data in the database for future visualizations.

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `Escape` | Clear search bar + remove highlights |
| `Enter` | In search bar: Highlight first result |
| `Ctrl + F` | Focus search bar (browser default) |

## Tips and Best Practices

### Performance
1. **Many Markers**: With > 1000 devices, the map can become slow
   - Use filters to show only relevant markers
   - Disable filters you don't need

2. **Bluetooth Proximity Search**: With many Bluetooth devices (> 100), display may be delayed
   - Use "Known Only" mode to filter out "Unknown Devices"

### Accuracy
1. **GPS Accuracy**: Marker accuracy depends on GPS quality of scan device
   - Use Drag & Drop function to correct inaccurate positions

2. **Signal Circles**: Circle sizes are only estimates
   - Real-world signal range depends on many factors (walls, interference, etc.)

### Workflow
1. **Filter First, Then Search**: Enable relevant filters before searching
2. **Highlight Toggle**: Use toggle function (click again) to quickly remove highlights
3. **Batch Corrections**: Correct multiple positions at once and save at the end

## Data Format

### Input: SQLite Database (.db)
The program reads from the following tables:
- `wifi_data`: WiFi networks
- `device_data`: Bluetooth devices

### Output: HTML + JSON
- **HTML Map**: Interactive Leaflet map (temporary in browser)
- **JSON Export**: Position changes for database update

### Position Update JSON Format
```json
[
  {
    "address": "AA:BB:CC:DD:EE:FF",
    "type": "WIFI",
    "latitude": 51.123456,
    "longitude": 7.654321,
    "timestamp": "2025-10-30T12:34:56.789Z"
  }
]
```

## Vendor Recognition (OUI)

The program recognizes manufacturers based on the first 6 characters of the MAC address (OUI):
- Apple, Samsung, Google
- Intel, Cisco, NETGEAR
- VMware, VirtualBox (virtual machines)
- Linksys, D-Link, etc.

**Note**: The OUI database is embedded in the code and can be extended.

## Troubleshooting

### Problem: Map shows no markers
**Solution**:
1. Check if filters are active (deactivate all filters with "All Off" and then activate individually)
2. Check if database has entries with valid GPS coordinates (`latitude != 0, longitude != 0`)

### Problem: "No valid GPS coordinates found"
**Solution**: The database only contains entries with `latitude = 0` and `longitude = 0`. Ensure the Android app records GPS data.

### Problem: Map doesn't load in browser
**Solution**:
1. Check your internet connection (for OpenStreetMap tiles)
2. Check browser console for JavaScript errors (F12 → Console)
3. Try a different browser

### Problem: Drag & Drop doesn't work
**Solution**:
1. Wait until map is fully loaded (approx. 1-2 seconds after opening)
2. Ensure JavaScript is enabled in browser
3. Check browser console for errors

### Problem: Bluetooth Proximity Search shows no devices
**Solution**:
1. Check BT Proximity mode (should not be set to "None")
2. Ensure Bluetooth devices exist in database
3. Check if Bluetooth devices exist within 50m radius of WiFi AP

### Problem: Middle Mouse Button / Right Click doesn't work
**Solution**:
1. Wait until drag handlers are loaded (approx. 2 seconds after opening)
2. Check browser console for JavaScript errors
3. Try using search function instead

## Advanced: localStorage and JSON Export

### localStorage (Browser)
The program uses localStorage to store position changes:
- Key: `pendingLocationUpdates`
- Format: JSON array with position updates

**Manual Reading:**
```javascript
// Browser Console (F12)
let updates = JSON.parse(localStorage.getItem('pendingLocationUpdates'));
console.log(updates);
```

### Manually Process JSON File

If you want to manually write the JSON file to the database:

```python
import json
import sqlite3

# Load JSON
with open('wifi_scanner_updates.json', 'r') as f:
    updates = json.load(f)

# Open database
conn = sqlite3.connect('your_database.db')
cursor = conn.cursor()

# Process updates
for update in updates:
    if update['type'] == 'WIFI':
        cursor.execute("""
            UPDATE wifi_data
            SET latitude = ?, longitude = ?
            WHERE bssid = ?
        """, (update['latitude'], update['longitude'], update['address']))
    elif update['type'] == 'BLUETOOTH':
        cursor.execute("""
            UPDATE device_data
            SET latitude = ?, longitude = ?
            WHERE device_address = ? AND device_type = 'BLUETOOTH'
        """, (update['latitude'], update['longitude'], update['address']))

conn.commit()
conn.close()
```

## Browser Compatibility

Tested with:
- ✅ Google Chrome 90+
- ✅ Mozilla Firefox 88+
- ✅ Microsoft Edge 90+
- ✅ Safari 14+ (macOS)

**Limitations:**
- Internet Explorer is NOT supported
- Mobile browsers have limited Drag & Drop support

## Support

For issues or questions:
1. Check browser console (F12 → Console) for errors
2. Check terminal output of Python script
3. Verify database structure
4. Create an issue in the GitHub repository

## Further Information

- Folium Documentation: https://python-visualization.github.io/folium/
- Leaflet.js (underlying JavaScript library): https://leafletjs.com/
- OpenStreetMap: https://www.openstreetmap.org/
- SQLite Documentation: https://www.sqlite.org/docs.html
