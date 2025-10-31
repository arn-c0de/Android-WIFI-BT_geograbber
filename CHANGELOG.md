# 📦 GeoGrabber Changelog

All notable changes to this project will be documented in this file.

---

## [1.0.1] – 2025-10-30 [LATEST]
- **Internationalization**: Translated all German log messages, Toasts, button texts, and code comments to English for better accessibility and localization. Thanks to **@elsakarvouni** for the contribution. **(Issues #3, #14)**

- Remove clear-text logging of sensitive device data; redact address and coordinates from log output in location update process.
- Disabled automatic application backup by setting android:allowBackup="false" in the manifest to protect sensitive user data.
- Sanitized user input before logging in MapActivity to prevent log injection vulnerabilities.
-set webSettings.setAllowFileAccess(false);


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
