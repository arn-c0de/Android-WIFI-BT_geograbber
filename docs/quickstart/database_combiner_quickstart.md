# Quickstart: Setup & Usage (Windows & Linux)

## 1. Environment Setup

### Windows
1. Open a command prompt and navigate to the `Python` folder:
   ```bat
   cd Python
   ```
2. Run the setup script:
   ```bat
   setup.bat
   ```
   - This will create a virtual environment (`venv`) and install all required dependencies from `requirements.txt`.

### Linux
1. Open a terminal and navigate to the `Python` folder:
   ```bash
   cd Python
   ```
2. Make the setup and run scripts executable (only needed once):
   ```bash
   chmod +x setup.sh run.sh
   ```
3. Run the setup script:
   ```bash
   ./setup.sh
   ```
   - This will create a virtual environment (`venv`) and install all required dependencies from `requirements.txt`.

## 2. Starting the Tools

### Windows
1. In the same command prompt, run:
   ```bat
   run.bat
   ```
2. Select the tool you want to use:
   - [1] Map Viewer (Plot GUI)
   - [2] Database Combiner
   - [3] Exit

### Linux
1. In the same terminal, run:
   ```bash
   ./run.sh
   ```
2. Select the tool you want to use:
   - [1] Map Viewer (Plot GUI)
   - [2] Database Combiner
   - [3] Exit

---

# WiFi/Bluetooth Database Combiner - Quickstart Guide

## Overview

The **Database Combiner** (`start_combine_dbs.py`) is a GUI tool for merging, cleaning, and repairing WiFi and Bluetooth databases from the WIFIGEOGRABBER project.

## Features

### Main Functions
- **Merge Databases**: Combine multiple SQLite databases into a single main database
- **Clean Database**: Remove duplicates and sort data into correct tables
- **Repair Database**: Modernize table structures and add missing columns
- **Analyze Database**: Display statistics about database contents

### Advanced Features
- Automatic backup creation before changes
- Device movement tracking (GPS coordinates)
- Signal strength prioritization for duplicates
- Intelligent duplicate detection (< 100m distance)
- Performance indexes for faster queries

## Installation

### Prerequisites
```bash
pip install tkinter sqlite3
```

**Note**: `tkinter` is usually included in standard Python installations.

## Quick Start

### 1. Launch Program
```bash
python Python/start_combine_dbs.py
```

### 2. Main Window

The program opens a GUI with the following sections:

#### Main Database (Target)
- Select the database that will receive all merged data
- This database serves as the main database

#### Source Databases
- Add one or more databases to be imported into the main database
- Devices from these databases will be combined with the main database

#### Options
- **Create Backup**: Automatically creates a backup before changes
- **Prefer Better Signal Strength**: Only updates entries with better signal
- **Update Coordinates**: Updates GPS coordinates when signal is better

## Usage

### Scenario 1: Merge Multiple Databases

1. Click **"Select"** under "Main Database" and choose your main database
2. Click **"Add DB"** under "Source Databases" and select one or more databases
3. Click **"🔍 Start Analysis"** to preview the data
4. Click **"🚀 Merge"** to start the process

**Result**:
- New devices are added
- Existing devices are updated if signal is better
- Duplicates are automatically removed
- A backup is created

### Scenario 2: Clean a Single Database

1. Select the database to clean as main database
2. Click **"🧹 Clean DB"** without adding source databases
3. Confirm the cleaning operation

**Result**:
- WiFi data is moved to `wifi_data` table
- Bluetooth data is moved to `device_data` table
- Duplicates are removed based on BSSID/MAC address
- A backup is created

### Scenario 3: Repair Database Structure

1. Select the database to repair as main database
2. Click **"🔧 Repair DB"**
3. Confirm the repair operation

**Result**:
- Missing tables are created
- Missing columns are added (e.g., movement tracking)
- Performance indexes are created
- A backup is created

## Database Structure

### wifi_data Table
Stores all WiFi networks with the following fields:
- `ssid`: Network name
- `bssid`: MAC address (unique identifier)
- `signal_strength`: Signal strength in dBm
- `verschluesselung`: Encryption type (open, WPA2, etc.)
- `latitude`, `longitude`: GPS coordinates
- `timestamp`: Scan timestamp
- `frequency`, `channel`, `wifi_standard`: WiFi details
- `vendor_info`: Manufacturer information
- `last_seen_latitude`, `last_seen_longitude`: Last known position
- `movement_distance`: Distance between current and last position

### device_data Table
Stores all Bluetooth devices with the following fields:
- `device_name`: Device name
- `device_address`: MAC address (unique identifier)
- `device_type`: "BLUETOOTH"
- `signal_strength`: Signal strength in dBm
- `encryption_info`: Device class
- `latitude`, `longitude`: GPS coordinates
- `timestamp`: Scan timestamp
- `vendor_info`: Manufacturer information
- `last_seen_latitude`, `last_seen_longitude`: Last known position
- `movement_distance`: Distance between current and last position

## Intelligent Duplicate Detection

The tool detects duplicates based on:
- **BSSID/MAC Address**: Unique hardware address
- **GPS Distance**: Devices < 100m apart are considered duplicates
- **Signal Strength**: For duplicates, the entry with better signal is preferred
- **Timestamp**: With equal signal, the newer entry is preferred

## Movement Tracking

The tool tracks device movements:
- When a device is scanned at a new position (> 100m), the old position is saved as "Last Seen"
- The distance between positions is calculated and stored
- The main position always shows the location with the best signal

## Tips and Best Practices

1. **Always Use Backups**: Keep the backup option enabled
2. **Analyze Before Merging**: Use the analysis function to get an overview
3. **Large Datasets**: With many databases, the process may take several minutes
4. **Regular Cleaning**: Perform regular cleaning to remove duplicates
5. **Structure Updates**: Use the repair function after program updates

## Troubleshooting

### Problem: "No valid GPS coordinates found"
**Solution**: The database only contains entries with `latitude = 0` and `longitude = 0`. Ensure the Android app records GPS data.

### Problem: "Backup failed"
**Solution**: Check write permissions in the database folder or choose a different location.

### Problem: Merge takes very long
**Solution**: This is normal with large databases (> 10,000 entries). The log shows progress.

## Understanding Log Output

In the log area, you'll see detailed information:
- `Added`: Completely new devices
- `Updated (better signal)`: Existing entries updated with better signal
- `Movement detected - Main position updated`: Device moved, new position has better signal
- `Movement detected - Last Seen updated`: Device moved, old position had better signal
- `Skipped (too close < 100m)`: Duplicate in immediate vicinity ignored
- `Skipped (worse signal)`: Entry has worse signal than existing one

## File Format

The tool works with SQLite3 databases (`.db` files). These can be opened with any SQLite browser.

## Support

For issues or questions:
1. Check the log output in the program
2. Verify database structure with an SQLite browser
3. Create an issue in the GitHub repository

## Further Information

- SQLite Documentation: https://www.sqlite.org/docs.html
- Haversine Formula (GPS distance): https://en.wikipedia.org/wiki/Haversine_formula
