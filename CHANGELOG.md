# 📦 GeoGrabber Changelog

All notable changes to this project will be documented in this file.

---

## [1.0.3-rc1] – 2025-01-XX [RELEASE CANDIDATE]

### 🔐 Security - Major Feature Addition
- **NEW: SQLCipher Database Encryption Support**
  - AES-256 page-level database encryption using SQLCipher 4.5.4
  - Android Keystore integration for secure passphrase storage
  - Optional encryption - users can choose encrypted or standard database
  - PBKDF2-HMAC-SHA512 key derivation with 256k iterations
  - Hardware-backed secure key storage where available
  - Zero-knowledge design - passphrases never logged or transmitted

### Added
- **New Classes**
  - `EncryptionManager.java` (424 lines) - Passphrase management with Android Keystore
  - `DatabaseEncryptionHelper.java` (487 lines) - SQLCipher database operations
- **Encryption Features**
  - First-launch encryption prompt with enable/skip options
  - Passphrase setup with validation (minimum 6 characters)
  - Database unlock dialog on app launch for encrypted databases
  - Encryption settings menu (change passphrase, disable encryption)
  - Database migration: unencrypted → encrypted conversion
  - Change passphrase functionality with database re-encryption
  - Disable encryption: encrypted → unencrypted conversion
  - Visual status indicator showing encryption state (🔒 Encrypted / 🔓 Not Encrypted)
- **Import/Export Support**
  - Auto-detection of encrypted vs unencrypted databases
  - Import encrypted databases with passphrase prompt
  - Export shows encryption status in success message
  - Full data migration from encrypted sources
- **Background Service Support**
  - ScanService updated to work with encrypted databases
  - Automatic encrypted database initialization
  - Passphrase caching for background operations
- **Memory Management**
  - Passphrase cached in-memory during app session
  - Automatic clearing on app pause/close
  - Explicit memory zeroing for security
- **Documentation**
  - Complete Developer Guide (1,200 lines)
  - User Quickstart Guide (800 lines)
  - Implementation Status document (500 lines)
  - Troubleshooting and FAQ sections
- **UI Elements**
  - 90+ new string resources for encryption UI
  - 10+ new dialogs for encryption workflows
  - Encryption status TextView in main layout

### Changed
- **MainActivity.java** (~550 lines added)
  - Added encryption initialization in `onCreate()`
  - New `initializeDatabase()` method routes to encrypted/unencrypted DB
  - New `initializeEncryptedDatabase()` opens SQLCipher databases
  - 10 new dialog methods for encryption UI
  - 5 new AsyncTask classes for background operations
  - Updated import/export flows with encryption detection
- **ScanService.java** (~30 lines added)
  - Initialize EncryptionManager in `onCreate()`
  - Check encryption status before opening database
  - Support for encrypted database in background service
- **Build Configuration**
  - Added SQLCipher dependency: `net.zetetic:android-database-sqlcipher:4.5.4`
  - Added androidx.sqlite:sqlite:2.4.0 for compatibility

### Security Notes
- **Encryption is OPTIONAL** - users choose to enable or skip
- **No passphrase recovery** - lost passphrase = lost data (by design)
- **Hardware-backed security** - Android Keystore uses device security when available
- **Minimal performance impact** - ~10% overhead on database operations, 40-50ms on first open
- **Memory protection** - passphrases cleared on app close/background
- **Python tools** - do not yet support encrypted databases (requires separate update)

### Known Issues
- AsyncTask used for background operations (deprecated API 30+, but functional)
- Python tools cannot open encrypted databases without SQLCipher support
- No biometric unlock option (passphrase-only in this release)
- No failed attempt limiting (unlimited passphrase attempts allowed)

### Testing Status
- ✅ Implementation complete (all planned features)
- ⏳ Testing phase pending (needs device/emulator testing)
- 📋 See `docs/security/ENCRYPTION_IMPLEMENTATION_STATUS.md` for testing checklist

---

## [1.0.2] – 2025-10-31

### Security
- **CRITICAL: Fixed SQL injection vulnerability in database import functions** **(Issue #5)**
  - Added comprehensive validation for imported .db files
  - Implemented checks for malicious triggers, views, and schema structures
  - Added input sanitization for all string data during import
  - Enforced read-only mode when opening external databases
  - Added file size limits (100MB max) to prevent DoS attacks
  - Implemented transaction rollback on import errors
  - Added validation for numeric ranges (coordinates, timestamps, signal strength)
  - Limited query results (50,000 records max) to prevent resource exhaustion
  - Added table name whitelist validation to prevent SQL injection via PRAGMA commands
  - MainActivity.java:1265-1339 - Added two security warning dialogs and helper method

### Added
- New security validation methods:
  - `validateExternalDatabase()` - Comprehensive database integrity checks
  - `validateTableSchema()` - Schema structure validation with table name whitelist
  - `sanitizeString()` - Input sanitization for string data
- **SHA-256 Checksum Verification and Metadata Support in ALL Tools**
  - Python Map Viewer & Database Combiner: Full support for .sha256.json files
  - User can select checksum file manually or create new one
  - Cross-platform verification (Android ↔ Python)
  - Optional checksum creation after merge, clean, repair
  - Clear dialogs for verification, creation, and warnings
- **Database Structure Updates**
  - Support for new columns: capabilities, center_freq0, center_freq1, is_passpoint_network, operator_friendly_name, venue_name, movement tracking fields
  - Python tools now fully compatible with latest Android DB schema
- **Improved GUI Workflows**
  - Flexible checksum selection and creation dialogs
  - User can skip, select, or create checksum files as needed
  - All dialogs translated and internationalized

### Changed
- Database import functions now use read-only mode for external databases
- Import process now uses database transactions with automatic rollback on errors
- All string inputs from external databases are now sanitized before insertion
- Numeric values are validated against reasonable ranges before import
- Python tools: GUI workflow for checksum selection/creation improved
- Python tools: All database operations now offer checksum creation after completion
- Documentation: SHA256_CHECKSUM_VERIFICATION.md updated for all platforms

### Fixed
- Malicious database files with triggers or views are now rejected
- Invalid data (coordinates, timestamps, signal strength) is now filtered out during import
- Database corruption during import is prevented through transaction rollback
- Python tools: No more forced checksum creation, user always has choice
- Python tools: Checksum verification now supports manual file selection
- Python tools: All new DB columns handled correctly


## [1.0.1] – 2025-10-30
- **Internationalization**: Translated all German log messages, Toasts, button texts, and code comments to English for better accessibility and localization. Thanks to **@elsakarvouni** for the contribution. **(Issues #3, #14)**

- Remove clear-text logging of sensitive device data; redact address and coordinates from log output in location update process.
- Disabled automatic application backup by setting android:allowBackup="false" in the manifest to protect sensitive user data.
- Sanitized user input before logging in MapActivity to prevent log injection vulnerabilities.
- Set webSettings.setAllowFileAccess(false);


## [1.0.0] – 2025-06-01
### Added
- Android app: WiFi and Bluetooth scanning, GPS integration, live map view
- SQLite database storage and export/import
- Python tools: database combiner and map viewer
- Documentation: README, quickstart guides, contributing, code of conduct, security policy

### Changed
- Project structure and documentation improved for clarity

### Fixed
- Initial bug fixes and stability improvements

---

## [Unreleased]
### Added
- Planned: Advanced filtering in map viewer
- Planned: Export to CSV/JSON from Python tools
- Planned: More documentation and usage guides

---

**Older versions and changes will be added as the project evolves.**
