# SHA-256 Checksum Verification in WiFi GeoGrabber

## Overview
This document explains the SHA-256 checksum generation and verification features implemented across the WiFi GeoGrabber ecosystem (Android App, Python Database Combiner, and Python Map Viewer) to enhance database security and integrity.

## Features

### 1. SHA-256 Checksum Generation
- **Android App**: When exporting a database, the app automatically calculates a SHA-256 checksum for the exported file.
- **Python Tools**: After merge, clean, or repair operations, users are prompted to create/update a checksum metadata file.
- The metadata file is named `<filename>.sha256.json` and includes:
  - version (1.0)
  - algorithm (always SHA-256)
  - filename
  - checksum (64-character hex string)
  - fileSize (in bytes)
  - timestamp (Unix timestamp in milliseconds)
  - exportedBy (source application identifier)
- Users can skip saving the metadata if not needed.

### 2. SHA-256 Checksum Verification
- **Android App**: When importing a database, users are offered the option to verify the file using a checksum metadata file.
- **Python Database Combiner**: When selecting Main DB or adding Source DBs, automatic verification if .sha256.json file exists.
- **Python Map Viewer**: When opening a database, automatic detection and verification of checksum files.
- Verification steps:
  1. Confirm the algorithm is SHA-256.
  2. Check that the file size matches the metadata.
  3. Calculate the actual checksum and compare it to the metadata value.
- If verification passes, the operation continues. If it fails, the user is shown a clear error message.
- Users can choose to proceed anyway despite failed verification (not recommended).

## User Workflows

### Android App

#### Export with Checksum
1. User exports the database from the Android app.
2. App calculates SHA-256 checksum.
3. User is prompted to save the checksum metadata file.
4. Metadata file is saved alongside the database file.

#### Import with Verification
1. User imports a database file.
2. App offers checksum verification if .sha256.json file exists.
3. User confirms verification.
4. App verifies algorithm, file size, and checksum.
5. If all checks pass, import proceeds. Otherwise, import is blocked and the user is notified.

### Python Database Combiner

#### Selecting Databases with Verification
1. User selects Main Database or adds Source Databases.
2. If a `.sha256.json` file exists, user is asked to verify.
3. Verification is performed automatically.
4. If verification fails, user can choose to continue or cancel.

#### After Database Operations
1. After merge, clean, or repair operations complete successfully.
2. User is prompted to create/update a SHA-256 checksum.
3. If accepted, checksum is calculated and metadata file is saved.
4. Metadata file is saved as `<database_name>.db.sha256.json`.

### Python Map Viewer

#### Opening Database with Verification
1. User selects a database file to view.
2. If a `.sha256.json` file exists, user is asked to verify.
3. Verification is performed automatically.
4. If verification passes, database is loaded normally.
5. If verification fails, user is warned and can choose to continue or cancel.

#### Creating Checksum for Existing Database
1. User opens a database without existing checksum file.
2. User is asked if they want to create a checksum file.
3. If accepted, SHA-256 checksum is calculated and saved.
4. Future openings will use this checksum for verification.

## Security Benefits
- **Tamper Detection**: Detects file tampering and corruption across all platforms.
- **Integrity Assurance**: Ensures files have not been modified since checksum creation.
- **Trust & Provenance**: Establishes trust and provenance for shared databases.
- **Cross-Platform Compatibility**: Checksums created on Android can be verified on Python tools and vice versa.
- **Clear Feedback**: Provides clear error messages and warnings for tampered files.
- **User Control**: Users can choose to verify or skip verification based on their needs.
- **Non-Intrusive**: Optional system that doesn't disrupt standard workflows.

## Example Metadata File
```json
{
  "version": "1.0",
  "algorithm": "SHA-256",
  "filename": "wifiscannerexport_123456.db",
  "checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "fileSize": 1234567,
  "timestamp": 1730476800000,
  "exportedBy": "WiFi GeoGrabber v1.0.2"
}
```

## Implementation Details

### Android App
- Implemented in `MainActivity.java`
- Helper methods: `calculateSHA256()`, `createChecksumMetadata()`, `saveChecksumMetadata()`, `verifyChecksumFromMetadata()`
- Integrated into export and import workflows

### Python Database Combiner (`start_combine_dbs.py`)
- Methods: `calculate_sha256_checksum()`, `create_checksum_metadata()`, `save_checksum_metadata()`, `verify_checksum_from_metadata()`
- Integrated into database selection (`select_main_db()`, `add_source_db()`)
- Optional checksum creation after merge, clean, and repair operations

### Python Map Viewer (`start_plot_gui.py`)
- Functions: `calculate_sha256_checksum()`, `create_checksum_metadata()`, `save_checksum_metadata()`, `verify_checksum_from_metadata()`
- Integrated into `select_database_file()` function
- Automatic detection and verification of checksum files
- Optional checksum creation for databases without existing checksums

## Technical Specifications
- **Algorithm**: SHA-256 (Secure Hash Algorithm 256-bit)
- **Checksum Format**: 64-character hexadecimal string
- **File Format**: JSON with UTF-8 encoding
- **File Extension**: `.sha256.json`
- **Naming Convention**: `<original_filename>.sha256.json`
- **File Size Verification**: Exact byte-level comparison
- **Timestamp Format**: Unix timestamp in milliseconds

## Best Practices
1. **Always create checksums** for databases that will be shared or transferred.
2. **Keep checksum files** together with database files when moving or sharing.
3. **Verify checksums** before merging databases from untrusted sources.
4. **Update checksums** after any database modification operations.
5. **Don't ignore failed verifications** - investigate the cause before proceeding.

## Compatibility
- ✅ Cross-platform: Android ↔ Python tools
- ✅ Backward compatible: Works with databases created by older versions
- ✅ Forward compatible: Metadata format designed for future extensibility
- ✅ Platform-independent: Uses standard SHA-256 algorithm and JSON format

## Next Steps
- Consider implementing automatic checksum creation on database export
- Add batch verification for multiple databases
- Implement checksum verification in automated workflows
- Consider SQLite file header validation for additional security
