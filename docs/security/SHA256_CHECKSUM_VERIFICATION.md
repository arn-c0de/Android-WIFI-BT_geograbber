# SHA-256 Checksum Verification in WiFi GeoGrabber

## Overview
This document explains the SHA-256 checksum generation and verification features implemented in the WiFi GeoGrabber app to enhance database security and integrity.

## Features

### 1. SHA-256 Checksum Generation on Export
- When exporting a database, the app automatically calculates a SHA-256 checksum for the exported file.
- After export, users are prompted to save a metadata file containing the checksum and related information.
- The metadata file is named `<filename>.sha256.json` and includes:
  - version
  - algorithm (always SHA-256)
  - filename
  - checksum
  - file size
  - timestamp
  - exportedBy (app version)
- Users can skip saving the metadata if not needed.

### 2. SHA-256 Checksum Verification on Import
- When importing a database, users are offered the option to verify the file using a checksum metadata file.
- Verification steps:
  1. Confirm the algorithm is SHA-256.
  2. Check that the file size matches the metadata.
  3. Calculate the actual checksum and compare it to the metadata value.
- If verification passes, the import continues. If it fails, the import is blocked and the user is shown a clear error message.
- Users can skip verification and proceed with standard validation if desired.

## User Workflow

### Export with Checksum
1. User exports the database.
2. App calculates SHA-256 checksum.
3. User is prompted to save the checksum metadata file.
4. Metadata file is saved alongside the database file.

### Import with Verification
1. User imports a database file.
2. App offers checksum verification.
3. User selects the checksum metadata file.
4. App verifies algorithm, file size, and checksum.
5. If all checks pass, import proceeds. Otherwise, import is blocked and the user is notified.

### Import without Verification
- Users can skip verification and proceed with standard import.

## Security Benefits
- Detects file tampering and corruption.
- Ensures files have not been modified since export.
- Establishes trust and provenance for shared databases.
- Provides clear error messages and automatic rejection of tampered files.

## Example Metadata File
```json
{
  "version": "1.0",
  "algorithm": "SHA-256",
  "filename": "wifiscannerexport_123456.db",
  "checksum": "a1b2c3d4...",
  "fileSize": 1234567,
  "timestamp": 1234567890123,
  "exportedBy": "WiFi GeoGrabber v1.0.2"
}
```

## Implementation Details
- All logic is implemented in `MainActivity.java`.
- Helper methods handle checksum calculation, metadata creation, and verification dialogs.
- The system is optional and does not disrupt standard workflows.

## Next Steps
- Consider enabling automatic backup before import and SQLite file header validation for further security.
