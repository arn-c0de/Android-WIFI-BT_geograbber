# 📁 Project Structure Documentation

This document provides a comprehensive overview of the Android_WIFI_BT_GEOGRABBER project structure.

---

## 📊 Project Overview

```
Android_WIFI_BT_GEOGRABBER/
├── 📱 WIFIGEOGRABBER/          # Android application (main project)
├── 🐍 Python/                  # Python tools for data analysis
├── 📚 docs/                    # Documentation
└── 📄 Configuration files      # Project metadata and configs
```

---

## 🏗️ Complete Directory Tree

```
Android_WIFI_BT_GEOGRABBER/
│
├── 📄 README.md                          # Main project documentation
├── 📄 CHANGELOG.md                       # Version history and changes
├── 📄 CONTRIBUTING.md                    # Contribution guidelines
├── 📄 CODE_OF_CONDUCT.md                 # Community guidelines
├── 📄 SECURITY.md                        # Security policy
├── 📄 PROJECT_STRUCTURE.md               # This file
├── 📄 .gitignore                         # Git ignore rules
│
├── 📚 docs/                               # Documentation directory
│   ├── 📄 README.md                      # Documentation index
│   └── 📁 quickstart/                    # Quick start guides
│       ├── database_combiner_quickstart.md
│       └── map_viewer_quickstart.md
│
├── 🐍 Python/                            # Python analysis tools
│   ├── 📄 start_combine_dbs.py          # Database merger script (78KB)
│   └── 📄 start_plot_gui.py             # Map visualization GUI (85KB)
│
└── 📱 WIFIGEOGRABBER/                    # Android Studio project root
    │
    ├── 📄 build.gradle.kts               # Project-level build configuration
    ├── 📄 settings.gradle.kts            # Gradle settings
    ├── 📄 gradle.properties              # Gradle properties
    ├── 📄 local.properties               # Local SDK paths (gitignored)
    ├── 📄 .gitignore                     # Android-specific git ignores
    ├── 🔧 gradlew                        # Gradle wrapper (Unix)
    ├── 🔧 gradlew.bat                    # Gradle wrapper (Windows)
    │
    ├── 📁 gradle/                        # Gradle wrapper configuration
    │   ├── 📄 libs.versions.toml        # Centralized dependency versions
    │   └── 📁 wrapper/
    │       ├── gradle-wrapper.jar
    │       └── gradle-wrapper.properties
    │
    ├── 📁 .idea/                         # Android Studio IDE settings
    │   ├── compiler.xml
    │   ├── gradle.xml
    │   ├── misc.xml
    │   ├── vcs.xml
    │   └── ...
    │
    ├── 📁 .gradle/                       # Gradle build cache (gitignored)
    │
    ├── 📁 build/                         # Project build output (gitignored)
    │
    └── 📁 app/                           # Main application module
        │
        ├── 📄 build.gradle.kts           # App-level build configuration
        ├── 📄 proguard-rules.pro         # ProGuard obfuscation rules
        │
        ├── 📁 build/                     # App build output (gitignored)
        │
        └── 📁 src/                       # Source code directory
            │
            ├── 📁 main/                  # Main source set
            │   │
            │   ├── 📄 AndroidManifest.xml  # App manifest (permissions, components)
            │   │
            │   ├── 📁 java/              # Java source code
            │   │   └── com/example/wifi_geograbber/
            │   │       ├── MainActivity.java      # Main UI activity (93KB)
            │   │       ├── MapActivity.java       # Map viewer activity (52KB)
            │   │       └── ScanService.java       # Background scanning service (24KB)
            │   │
            │   └── 📁 res/               # Android resources
            │       │
            │       ├── 📁 drawable/      # Vector graphics and icons
            │       │   ├── ic_launcher_background.xml
            │       │   └── ic_launcher_foreground.xml
            │       │
            │       ├── 📁 layout/        # UI layout XML files
            │       │   ├── activity_main.xml      # Main screen layout
            │       │   └── activity_map.xml       # Map screen layout
            │       │
            │       ├── 📁 mipmap-*/      # App launcher icons (multiple DPI)
            │       │   ├── mipmap-anydpi/
            │       │   ├── mipmap-hdpi/
            │       │   ├── mipmap-mdpi/
            │       │   ├── mipmap-xhdpi/
            │       │   ├── mipmap-xxhdpi/
            │       │   └── mipmap-xxxhdpi/
            │       │
            │       ├── 📁 values/        # Resource values
            │       │   ├── colors.xml    # Color definitions
            │       │   ├── strings.xml   # Text strings (localization)
            │       │   └── themes.xml    # App themes (light mode)
            │       │
            │       ├── 📁 values-night/  # Dark theme resources
            │       │   └── themes.xml    # Dark mode theme
            │       │
            │       └── 📁 xml/           # XML resources
            │           ├── backup_rules.xml
            │           └── data_extraction_rules.xml
            │
            ├── 📁 androidTest/           # Instrumentation tests (UI tests)
            │   └── java/com/example/wifi_geograbber/
            │       └── ExampleInstrumentedTest.java
            │
            └── 📁 test/                  # Unit tests (JVM tests)
                └── java/com/example/wifi_geograbber/
                    └── ExampleUnitTest.java
```

---

## 📱 Android Application Structure

### Core Components

#### Activities
| File | Size | Lines | Description |
|------|------|-------|-------------|
| `MainActivity.java` | 93 KB | ~1,700 | Main scanning interface, WiFi/BT management, database operations |
| `MapActivity.java` | 52 KB | ~1,000 | Interactive map viewer with Leaflet.js WebView |

#### Services
| File | Size | Lines | Description |
|------|------|-------|-------------|
| `ScanService.java` | 24 KB | ~350 | Foreground service for background WiFi/BT scanning |

### Resources

#### Layouts (`res/layout/`)
- **activity_main.xml** – Main screen UI (buttons, lists, debug log)
- **activity_map.xml** – Map screen UI (WebView, navigation buttons)

#### Values (`res/values/`)
- **strings.xml** – 70+ localized UI strings
- **colors.xml** – App color palette
- **themes.xml** – Material Design theme definitions

#### Graphics
- **drawable/** – Vector XML icons
- **mipmap-*/** – Launcher icons for all screen densities (hdpi, xhdpi, xxhdpi, xxxhdpi)

---

## 🐍 Python Tools

### Scripts

| Script | Size | Description | Main Functions |
|--------|------|-------------|----------------|
| `start_combine_dbs.py` | 78 KB | Database merger utility | Combines multiple .db files, removes duplicates, GUI interface |
| `start_plot_gui.py` | 85 KB | Interactive map viewer | Plots scan data on map, filtering, export to CSV/JSON |

### Dependencies
- **tkinter** – GUI framework
- **sqlite3** – Database operations
- **folium** / **matplotlib** – Map visualization
- **pandas** – Data analysis

---

## 📚 Documentation Structure

```
docs/
├── README.md                               # Documentation index
└── quickstart/
    ├── database_combiner_quickstart.md    # How to merge databases
    └── map_viewer_quickstart.md           # How to use map viewer
```

### Additional Documentation Files (Root)
- **README.md** – Main project readme
- **CHANGELOG.md** – Version history
- **CONTRIBUTING.md** – Contribution guidelines
- **CODE_OF_CONDUCT.md** – Community standards
- **SECURITY.md** – Security policies
- **PROJECT_STRUCTURE.md** – This file

---

## 🛠️ Build System (Gradle)

### Configuration Files

| File | Purpose |
|------|---------|
| `build.gradle.kts` (project) | Project-level Gradle configuration |
| `build.gradle.kts` (app) | App module dependencies and settings |
| `settings.gradle.kts` | Project structure definition |
| `gradle.properties` | Gradle JVM settings and properties |
| `gradle/libs.versions.toml` | Centralized dependency version catalog |

### Key Dependencies (Example)
```toml
[versions]
androidGradlePlugin = "8.x.x"
kotlin = "1.9.x"
compileSdk = "34"
minSdk = "23"
targetSdk = "34"

[libraries]
androidx-core-ktx = { ... }
google-play-services-location = { ... }
leaflet-js = "1.7.1"  # Embedded in WebView
```

---

## 🗄️ Database Schema

The app uses SQLite with two main tables:

### wifi_data Table
```sql
CREATE TABLE wifi_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bssid TEXT NOT NULL,              -- MAC address (unique identifier)
    ssid TEXT,                        -- Network name
    signal_strength INTEGER,          -- RSSI in dBm
    frequency INTEGER,                -- MHz (2.4GHz/5GHz)
    channel INTEGER,                  -- WiFi channel (1-165)
    channel_width INTEGER,            -- 20/40/80/160 MHz
    security_type TEXT,               -- WPA2/WPA3/Open/etc.
    timestamp TEXT,                   -- ISO 8601 format
    latitude REAL,                    -- GPS latitude
    longitude REAL                    -- GPS longitude
);
```

### device_data Table
```sql
CREATE TABLE device_data (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id TEXT,                   -- Bluetooth device ID
    device_name TEXT,                 -- Friendly name
    device_type TEXT,                 -- WIFI/BLE/CLASSIC
    mac_address TEXT NOT NULL,        -- Hardware address
    signal_strength INTEGER,          -- RSSI value
    timestamp TEXT,                   -- ISO 8601 format
    latitude REAL,                    -- GPS latitude
    longitude REAL                    -- GPS longitude
);
```

### Indexes (for performance)
```sql
CREATE INDEX idx_wifi_bssid ON wifi_data(bssid);
CREATE INDEX idx_wifi_coords ON wifi_data(latitude, longitude);
CREATE INDEX idx_device_mac ON device_data(mac_address);
CREATE INDEX idx_device_coords ON device_data(latitude, longitude);
```

---

## 📦 APK Build Output

When building the app:

```
WIFIGEOGRABBER/app/build/outputs/apk/
├── debug/
│   └── app-debug.apk              # Debug build (unoptimized)
└── release/
    └── app-release-unsigned.apk   # Release build (requires signing)
```

---

## 🔐 Security & Privacy Files

### ProGuard Rules (`proguard-rules.pro`)
Defines code obfuscation rules for release builds:
- Keep database model classes
- Preserve Leaflet.js JavaScript interface
- Optimize APK size

### Backup Rules (`res/xml/backup_rules.xml`)
Android Auto Backup configuration:
- Excludes sensitive data
- Includes database for cloud backup (optional)

---

## 📋 File Size Summary

| Category | Count | Total Size |
|----------|-------|------------|
| **Java Source** | 3 files | ~170 KB |
| **XML Layouts** | 2 files | ~10 KB |
| **XML Resources** | ~15 files | ~50 KB |
| **Python Scripts** | 2 files | ~163 KB |
| **Documentation** | 7+ files | ~100 KB |
| **Build System** | 5 files | ~15 KB |
| **Icons/Graphics** | 20+ files | ~500 KB |

**Total Repository Size:** ~1-2 MB (excluding build artifacts)

---

## 🔄 Build Artifacts (Excluded from Git)

These directories are generated during build and excluded via `.gitignore`:

```
.gradle/              # Gradle cache
.idea/                # IDE settings (some tracked, most ignored)
build/                # Build outputs
*.apk                 # Compiled APKs
*.aab                 # Android App Bundles
local.properties      # Local SDK paths
```

---

## 🚀 Development Workflow

### Initial Setup
```bash
1. Clone repository
2. Open WIFIGEOGRABBER/ in Android Studio
3. Sync Gradle (automatic)
4. Connect device or start emulator
5. Run app (Shift+F10)
```

### File Modification Impact

| File Changed | Action Required |
|--------------|-----------------|
| `*.java` | Rebuild app |
| `*.xml` (layout) | Hot swap possible |
| `strings.xml` | Rebuild required |
| `build.gradle.kts` | Gradle sync required |
| `AndroidManifest.xml` | Clean rebuild required |

---

## 📍 Key File Locations

### Frequently Modified Files
```
MainActivity.java             (Line 1681)
MapActivity.java              (Line 991)
ScanService.java              (Line 324)
activity_main.xml             (Line 146)
activity_map.xml              (Line 79)
strings.xml                   (Line 76)
```

### Configuration Files
```
AndroidManifest.xml           # App permissions and components
build.gradle.kts (app)        # Dependencies and SDK versions
proguard-rules.pro            # Release optimization rules
```

---

## 🧪 Testing Structure

### Unit Tests (`src/test/`)
- **ExampleUnitTest.java** – JVM unit tests (fast, no emulator)
- Location: `app/src/test/java/com/example/wifi_geograbber/`

### Instrumentation Tests (`src/androidTest/`)
- **ExampleInstrumentedTest.java** – Device/emulator tests (slow, requires device)
- Location: `app/src/androidTest/java/com/example/wifi_geograbber/`

### Running Tests
```bash
# Unit tests (fast)
./gradlew test

# Instrumentation tests (requires device)
./gradlew connectedAndroidTest
```

---

## 🔍 Important Notes

### Code Organization
- All Java code follows package: `com.example.wifi_geograbber`
- No subdirectories in Java package (flat structure)
- Resources organized by type (layout/, values/, drawable/)

### Resource Naming Conventions
- Activities: `activity_*.xml`
- Strings: snake_case (e.g., `wifi_scan_successful`)
- IDs: snake_case with prefix (e.g., `button_scan_start`)
- Colors: Material Design naming (e.g., `color_primary`)

### Database Location
- Internal storage: `/data/data/com.example.wifi_geograbber/databases/`
- Exported files: `/storage/emulated/0/Download/` or user-selected location

---

## 📞 Related Documentation

- **[Main README](README.md)** – Project overview and features
- **[Database Combiner Guide](docs/quickstart/database_combiner_quickstart.md)** – Merge multiple databases
- **[Map Viewer Guide](docs/quickstart/map_viewer_quickstart.md)** – Visualize scan data
- **[Contributing Guide](CONTRIBUTING.md)** – How to contribute
- **[Security Policy](SECURITY.md)** – Report vulnerabilities

---

<div align="center">
  <p><i>Last Updated: October 30, 2025</i></p>
  <p>📁 Structure documented with precision</p>
</div>
