# Changelog

All notable changes to this project will be documented in this file.

## [0.2.0] - 2026-09-08

### Added
- **Transactional Integrity**: All bookmark and folder imports are now wrapped in a single database transaction. This prevents partial data corruption if an import is interrupted.
- **Improved Deep-Wipe**: The "Replace" import mode now correctly handles recursive folder deletion, ensuring a clean state before restoration.
- **Architectural Interfaces**: Introduced `BackupPreferences` and `BackupDatabase` interfaces to decouple business logic from Android-specific implementations, enabling easier unit testing.
- **Unit Test Infrastructure**: Added support for `kotlinx-coroutines-test` and `androidx.arch.core.testing` for reliable asynchronous testing.

### Changed
- **Storage Migration**: Migrated app-level preferences (like last backup time) from legacy `SharedPreferences` to **Jetpack DataStore (Preferences)** for asynchronous, non-blocking I/O.
- **Build System**: Updated Android Gradle Plugin (AGP) to 9.3.2 and optimized the dependency tree.

### Fixed
- **Nested Folder Remapping**: Fixed a bug where nested folders could have dangling parent IDs after a JSON import.

---

## [0.1.0] - 2026-08-14

### Added
- Initial public release.
- Universal Link Capture from any Android app.
- Hierarchical folder organization (up to 5 levels).
- Many-to-many Tagging system.
- Full-text search (Title, URL, and Notes).
- JSON/HTML import and export support.
- Minimalist, privacy-first UI with Material 3.
