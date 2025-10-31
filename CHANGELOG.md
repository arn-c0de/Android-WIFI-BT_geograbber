# 📦 GeoGrabber Changelog

All notable changes to this project will be documented in this file.

---

## [1.0.2] – 2025-10-31 [LATEST]

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

### Added
- New security validation methods:
  - `validateExternalDatabase()` - Comprehensive database integrity checks
  - `validateTableSchema()` - Schema structure validation with table name whitelist
  - `sanitizeString()` - Input sanitization for string data

### Changed
- Database import functions now use read-only mode for external databases
- Import process now uses database transactions with automatic rollback on errors
- All string inputs from external databases are now sanitized before insertion
- Numeric values are validated against reasonable ranges before import

### Fixed
- Malicious database files with triggers or views are now rejected
- Invalid data (coordinates, timestamps, signal strength) is now filtered out during import
- Database corruption during import is prevented through transaction rollback


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
