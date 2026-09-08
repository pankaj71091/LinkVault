<p align="center">
  <img src="logo.png" width="96" height="96" alt="LinkVault Logo">
</p>

<h1 align="center">LinkVault</h1>

<p align="center">
  <a href="https://github.com/pankaj71091/LinkVault/releases/latest">
    <img src="badge_github.png" height="40" alt="Download on GitHub">
  </a>
  <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22com.linkvault.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fpankaj71091%2FLinkVault%22%2C%22author%22%3A%22pankaj71091%22%2C%22name%22%3A%22LinkVault%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Atrue%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22about%5C%22%3A%5C%22Privacy-focused%2C%20local-only%20Android%20bookmark%20manager.%5C%22%7D%22%7D">
    <img src="badge_obtainium.png" height="40" alt="Get it on Obtainium">
  </a>
</p>

**LinkVault** is a privacy-focused, local-only Android bookmark manager. It isn't a browser; instead, it serves as a secure vault for links you share from other apps or browsers.

Designed for users who want to organize their digital life without cloud tracking, accounts, or data mining. **Your data never leaves your device.**

---

## ✨ Key Features

### 🚀 Seamless Capture
- **Universal Share Target**: Capture links instantly from any app using the standard Android Share sheet.
- **Quick-Save Bottom Sheet**: A powerful, Material 3 bottom-sheet that allows you to browse your folder hierarchy and create new folders without leaving your current app.
- **Smart Metadata**: Automatically fetches page titles and favicons in the background using WorkManager.
- **Duplicate Detection**: Alerts you if a URL is already in your vault to keep your library clean.
- **Folder Suggestions**: Suggests the most likely folder based on your saving habits for specific domains.

### 📁 Advanced Organization
- **Nested Folders**: Organize your bookmarks into deep hierarchies (up to 5 levels).
- **Many-to-Many Tags**: Apply multiple tags to a single link for flexible cross-referencing.
- **Pinned Bookmarks**: Keep your most important links at the top of your Home screen.
- **Notes & Annotations**: Add personal notes to any bookmark, which are also searchable.
- **Archive System**: Hide links you've finished with without deleting them permanently.
- **Full-Text Search**: Instant search across titles, URLs, and your personal notes.

### 🛠 Power User Tools
- **Transactional Imports**: Robust JSON and HTML imports wrapped in database transactions to ensure data integrity.
- **Browser Import**: Import your existing library from Chrome, Firefox, or Safari using standard Netscape HTML files.
- **Portable Backups**: Export and Import your entire vault via JSON for easy migration or safe-keeping.
- **Backup Reminders**: Automatic notifications to ensure your data is backed up regularly.

---

## 🛡 Privacy & Architecture
- **No Cloud**: No sync servers, no accounts, and no data collection.
- **Local Storage**: All data is stored in a private SQLite database on your device.
- **Modern Tech Stack**:
    - **Jetpack DataStore**: Asynchronous, non-blocking preference storage.
    - **Room Database**: Type-safe local database with transactional integrity.
    - **Jetpack Compose**: Modern, reactive UI built with Material 3.
- **Test-Driven Design**: Decoupled architecture using interfaces for easy unit testing.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio **Koala** or newer.
- Android device or emulator running **API 26 (Android 8.0)** or higher.

### Building
1. Clone the repository:
   ```bash
   git clone https://github.com/pankaj71091/LinkVault.git
   ```
2. Open the project in Android Studio.
3. Let Gradle sync complete.
4. Click **Run** to deploy to your device.

---

## 🗺 Roadmap
- [ ] **Read/Unread Status**: Track links you've already visited.
- [ ] **Dead-Link Checker**: Automatically flag 404s or expired links.
- [ ] **Database Encryption**: At-rest encryption using SQLCipher / Jetpack Security.
- [ ] **Encrypted Backups**: Password-protected export containers.

---

## 📄 License
This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---
*Built with ❤️ for privacy and organization.*
