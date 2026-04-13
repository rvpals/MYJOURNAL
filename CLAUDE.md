---
tags: [architecture, project-structure, tech-stack, database-schema, AndroidBridge, build, conventions, themes, encryption]
related_files: [REQUIREMENT.md, COMPONENTS.md, README.md]
summary: "Project architecture reference — directory structure, tech stack, database schema, AndroidBridge API, build instructions, and coding conventions."
---

# MYJOURNAL — Project Guide

## Overview

Android WebView-based encrypted journal app. Native Java shell handles authentication, biometrics, file I/O, and a native dashboard. The web layer (HTML/CSS/JS SPA) handles all core journal functionality. All data stored locally in AES-256-GCM encrypted SQLite (sql.js WASM) via IndexedDB.

**App Name:** My Journal | **Version:** 1.5.0 | **Package:** com.journal.app | **Min SDK:** 24 | **Target SDK:** 34

## Project Structure

```
MYJOURNAL/
├── app/
│   ├── build.gradle                # Android build config (compileSdk 34, minSdk 24)
│   ├── src/main/
│   │   ├── AndroidManifest.xml     # 4 activities, permissions (internet, location, camera, biometric)
│   │   ├── java/com/journal/app/
│   │   │   ├── LoginActivity.java      # Entry point: journal select, password, biometric login
│   │   │   ├── MainActivity.java       # WebView container + AndroidBridge JS interface
│   │   │   ├── DashboardActivity.java  # Native dashboard (stats, ranked lists, pinned/recent)
│   │   │   └── AboutActivity.java      # App info screen
│   │   ├── res/
│   │   │   ├── drawable/           # Button ripples, input/card/search/stat backgrounds (16 XML files)
│   │   │   ├── layout/            # activity_main, activity_login, activity_dashboard, activity_about
│   │   │   ├── values/            # colors.xml, strings.xml, styles.xml, app_info.xml
│   │   │   └── xml/file_paths.xml # FileProvider for camera
│   │   └── assets/web/            # ← copied from /web at build time (copyWebAssets task)
├── web/                            # Shared web SPA (loaded by WebView)
│   ├── index.html                  # Single-page app, all views as hidden divs (~1,200 lines)
│   ├── app_info.xml                # App metadata & changelog (single source of truth)
│   ├── css/
│   │   ├── style.css               # 12 themes via CSS variables, all component styles
│   │   └── style-android.css       # Android-only overrides (.android prefix rules)
│   ├── js/
│   │   ├── app.js          # Main controller: login, navigation, theme, Quill editor
│   │   ├── app_info.js     # Parses app_info.xml → APP_INFO + APP_CHANGELOG objects
│   │   ├── bootstrap.js    # IndexedDB-backed key-value store replacing localStorage
│   │   ├── components.js   # Reusable UI: ResultGrid, RankedPanel, RecordViewer, CollapsiblePanel
│   │   ├── crypto.js       # PBKDF2 + AES-256-GCM via Web Crypto API
│   │   ├── db.js           # sql.js SQLite abstraction, IndexedDB persistence, CRUD
│   │   ├── entries.js      # Entry form, list view, search, filter, pagination
│   │   ├── dashboard.js    # Stats aggregation, ranked lists, widgets, quick actions
│   │   ├── views.js        # Custom saved views: AND/OR/NOT filter, group, sort
│   │   ├── explorer.js     # SQL Explorer: query builder, raw SQL, CSV/iCalendar export
│   │   ├── calendar.js     # Calendar View: monthly/weekly grid, day selection, entry results
│   │   ├── reports.js      # HTML/PDF/CSV report generation with templates
│   │   ├── settings.js     # All settings: preferences, themes, metadata, widgets, import/export
│   │   ├── weather.js      # Open-Meteo API, city search, GPS location
│   │   └── widgets.js      # Dashboard widgets: filters, aggregate functions, editor
│   ├── lib/
│   │   ├── sql-wasm.js         # sql.js library
│   │   └── sql-wasm-base64.js  # Base64-encoded WASM fallback for file:// protocol
│   └── templates/              # Sample report templates (HTML)
├── build.gradle                # Root Gradle config (AGP 8.3.0)
├── settings.gradle
├── gradle.properties
└── README.md
```

## Building

```bash
set JAVA_HOME=E:\Prog\Java\jdk-17
set ANDROID_SDK_ROOT=C:\Android\android-sdk
gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

The `copyWebAssets` Gradle task copies `/web` → `app/src/main/assets/web/` before each build.

## Tech Stack

| Layer | Technology | Notes |
|-------|-----------|-------|
| Android | Java, SDK 34 | WebView + AndroidX BiometricPrompt |
| Web | Plain HTML/CSS/JS | No frameworks, SPA with div toggling |
| Database | sql.js (SQLite→WASM) | Encrypted bytes in IndexedDB |
| Encryption | AES-256-GCM | PBKDF2-SHA256, 100k iterations, Web Crypto API |
| Editor | Quill.js 1.3.7 | Rich text with image support |
| PDF | jsPDF 2.5.1 | Report/entry PDF export |
| Weather | Open-Meteo API | Free, no API key required |
| Geocoding | Photon / Nominatim / Google | Configurable in settings |
| Font | Quicksand | Google Fonts CDN |

## Database Schema

**entries** — id, date, time, title, content, richContent, categories (JSON), tags (JSON), people (JSON), placeName, locations (JSON), weather (JSON), pinned, locked, dtCreated, dtUpdated
**images** — id, entryId, name, data (base64), thumb, sortOrder
**categories** — name (PK), description
**tags** — name (PK), description
**icons** — type + name (composite PK), data (PNG data URL). Types: category, tag, person (64x64), category_hd, tag_hd, person_hd (128x128 for image buttons)
**people** — firstName + lastName (composite PK), description
**widgets** — id (PK), name, description, bgColor, icon, filters (JSON), functions (JSON), enabledInDashboard, sortOrder, dtCreated, dtUpdated
**settings** — key (PK), value
**schema_version** — version (INT)

## AndroidBridge Interface

JavaScript calls from WebView to native Android:
- `saveFile(filename, base64Data, mimeType)` — Scoped storage file download
- `isBiometricAvailable()` / `authenticate(callback)` — Biometric prompt
- `saveCredential()` / `getCredential()` / `hasCredential()` / `removeCredential()` — Password storage
- `onDashboardReady(json)` / `requestDashboardRefresh()` — Native dashboard data
- `onAutoLoginComplete(json)` / `onAutoLoginFailed(error)` — Auto-login result callbacks
- `returnToLogin()` — Back to LoginActivity
- `syncCryptoKey()` / `syncJournalList()` — Sync Bootstrap store ↔ SharedPreferences
- `isAndroid()` / `hasNativeLogin()` — Platform detection
- `selectBackupFolder()` / `hasBackupFolder()` / `getBackupFolderName()` / `clearBackupFolder()` — SAF backup folder management
- `saveFileToBackupFolder()` / `listBackupFolderFiles()` / `readBackupFolderFile()` — Backup file I/O

## Themes (12)

Light, Dark, Ocean, Midnight, Forest, Amethyst, Aurora, Lavender, Frost, Navy, Sunflower, Meadow — all via CSS `[data-theme]` variables.

## Key Conventions

- **No frameworks** — vanilla JS, no React/Vue/Angular
- **Single-page app** — pages are div sections toggled via `navigateTo(page)`
- **Encryption everywhere** — all DB writes go through Crypto.encrypt → IndexedDB
- **Bootstrap store** — all client-side key-value storage uses `Bootstrap.get/set/remove()` (IndexedDB-backed), NOT localStorage
- **Dual auth** — web crypto.js and native LoginActivity both implement PBKDF2+AES-GCM (must stay in sync)
- **AndroidBridge** — file downloads MUST use `downloadFile()` (not blob URLs) to avoid WebView crashes
- **CSS themes** — all colors via CSS variables; never hardcode colors
- **copyWebAssets** — changes to `/web` are automatically copied to assets at build time
