# 📦 GeoGrabber Changelog

All notable changes to this project will be documented in this file.

---
## [1.0.3] – 2025-11-01

### 🔐 Security & Reliability
- Security Overlay (black screen): App content is fully hidden during passphrase entry until the database is unlocked
- SQLCipher 4.5.4 AES-256 page-level database encryption  
- PBKDF2-HMAC-SHA512 with 256k iterations and per-installation salt  
- Android Keystore integration with hardware-backed key storage  
- Passphrase stored only in RAM, securely wiped on app close  
- No sensitive data logged; only SHA-256 previews (8+8 chars)  
- Database always encrypted, never stored in plaintext  
- Import/Export remains encrypted, no unencrypted copies created  
- Transaction commit and WAL checkpoint ensure persistent data  
- Zero-knowledge design: passphrases never logged or transmitted  

### 🛠️ Bug Fixes & Improvements
- Import fix: data retained after restart  
- Improved debug log (export, copy, select-all)  
- Status display for encryption and database path  
- Code cleanup: no unsafe defaults or backdoors  

### 📦 New/Updated Classes
- `EncryptionManager.java` – passphrase and key management with Keystore integration  
- `DatabaseEncryptionHelper.java` – SQLCipher database handling  

### 🔄 Migration
- Existing unencrypted databases can be securely converted to encrypted form  

### ⚙️ Notes
- Encryption is optional; users can enable or skip  
- Lost passphrase = lost data (by design)  
- Hardware-backed security where supported  
- ~10% performance overhead on DB operations  
- Passphrase cleared on app close or background  
- Python tools currently lack encrypted DB support  


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
