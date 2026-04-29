---
tags: [architecture, project-structure, tech-stack, database-schema, build, conventions, themes, encryption]
related_files: [REQUIREMENT.md, TO_DO.md, README.md]
summary: "Project architecture reference — directory structure, tech stack, database schema, ServiceProvider pattern, build instructions, and coding conventions."
---

# MYJOURNAL — Project Guide

## Overview

Fully native Android encrypted journal app. All 14 screens are native Kotlin activities. Services (crypto, database, bootstrap, weather) are managed by a `ServiceProvider` singleton. The web layer (`/web/` directory) is a standalone browser-only fallback SPA — it is NOT bundled in the Android APK. All data stored locally in AES-256-GCM encrypted SQLCipher database.

**App Name:** My Journal | **Version:** 1.5.0 | **Package:** com.journal.app | **Min SDK:** 24 | **Target SDK:** 34

## Project Structure

```
MYJOURNAL/
├── app/
│   ├── build.gradle                # Android build config (compileSdk 34, minSdk 24)
│   ├── src/main/
│   │   ├── AndroidManifest.xml     # 14 activities, permissions (internet, location, camera, biometric)
│   │   ├── java/com/journal/app/    # All Kotlin sources (21 files)
│   │   │   ├── LoginActivity.kt         # Entry point: journal select, password, biometric login
│   │   │   ├── DashboardActivity.kt     # Native dashboard (stats, ranked lists, pinned/recent, widgets, hamburger menu)
│   │   │   ├── CalendarActivity.kt      # Native calendar view
│   │   │   ├── AboutActivity.kt         # App info screen
│   │   │   ├── SearchActivity.kt        # Native full-text search screen
│   │   │   ├── EntryViewerActivity.kt   # Native entry viewer with font settings
│   │   │   ├── EntryListActivity.kt     # Native entry list (collapsible filters, order-by dropdowns, alternating rows, search, sort, paginate, batch delete)
│   │   │   ├── ExplorerActivity.kt      # Native SQL Explorer (table browser, query builder, raw SQL, CSV/iCal export)
│   │   │   ├── SettingsActivity.kt      # Native settings (6 tabs in 2-row grid: Prefs, Templates, Metadata, Data, Widgets, Dashboard)
│   │   │   ├── EntryFormActivity.kt     # Native entry form (Content group box with sub-tabs, rich text, images, categories, tags, people, locations, weather)
│   │   │   ├── ReportsActivity.kt       # Native reports (HTML export+browser open, PDF/CSV, filters, templates)
│   │   │   ├── WidgetEditorActivity.kt  # Native widget editor (header/filters/functions tabs, preview)
│   │   │   ├── CustomViewEditorActivity.kt # Native custom view editor (conditions, groupBy, orderBy, display)
│   │   │   ├── CsvMappingActivity.kt    # Native CSV import mapping screen
│   │   │   ├── ServiceProvider.kt       # Singleton service holder (replaces old MainActivity.instance)
│   │   │   ├── DashboardDataBuilder.kt  # Computes dashboard JSON from DatabaseService (stats, streaks, ranked lists, widgets, today in history)
│   │   │   ├── BootstrapService.kt      # SharedPreferences wrapper
│   │   │   ├── CryptoService.kt         # AES-256-GCM + PBKDF2
│   │   │   ├── WeatherService.kt        # Open-Meteo HTTP client
│   │   │   └── DatabaseService.kt       # SQLCipher encrypted DB
│   │   │   ├── ThemeManager.kt          # Runtime theme system (12 themes, view tree recoloring)
│   │   ├── res/
│   │   │   ├── drawable/           # Button ripples, input/card/search/stat backgrounds, 3D tab/search drawables (25+ XML files)
│   │   │   ├── layout/            # 14 activity layouts + 2 spinner item layouts
│   │   │   ├── values/            # colors.xml, strings.xml, styles.xml, app_info.xml
│   │   │   └── xml/file_paths.xml # FileProvider for camera
├── web/                            # Browser-only SPA fallback (NOT bundled in APK)
│   ├── index.html                  # Single-page app, all views as hidden divs
│   ├── app_info.xml                # App metadata & changelog
│   ├── css/
│   │   ├── style.css               # 12 themes via CSS variables
│   │   └── style-android.css       # Android-only overrides (unused since WebView removed)
│   ├── js/
│   │   ├── app.js          # Main controller: login, navigation, theme, Quill editor
│   │   ├── app_info.js     # Parses app_info.xml
│   │   ├── bootstrap.js    # IndexedDB-backed key-value store
│   │   ├── components.js   # Reusable UI: ResultGrid, RankedPanel, RecordViewer, CollapsiblePanel
│   │   ├── crypto.js       # PBKDF2 + AES-256-GCM via Web Crypto API
│   │   ├── db.js           # sql.js SQLite abstraction, IndexedDB persistence
│   │   ├── entries.js      # Entry form, list view, search, filter, pagination
│   │   ├── dashboard.js    # Stats aggregation, ranked lists, widgets
│   │   ├── views.js        # Custom saved views
│   │   ├── explorer.js     # SQL Explorer
│   │   ├── calendar.js     # Calendar View
│   │   ├── reports.js      # Report generation
│   │   ├── settings.js     # Settings UI
│   │   ├── weather.js      # Open-Meteo API
│   │   └── widgets.js      # Dashboard widgets
│   ├── lib/
│   │   ├── sql-wasm.js         # sql.js library
│   │   └── sql-wasm-base64.js  # Base64-encoded WASM fallback for file:// protocol
│   └── templates/              # Sample report templates (HTML)
├── build.gradle                # Root Gradle config (AGP 8.3.0, Kotlin 1.9.22)
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

## Tech Stack

| Layer | Technology | Notes |
|-------|-----------|-------|
| Android | Kotlin, SDK 34 | Fully native activities + AndroidX BiometricPrompt |
| Web (browser only) | Plain HTML/CSS/JS | Standalone SPA, not bundled in APK |
| Database | SQLCipher 4.5.6 | Encrypted SQLite via `net.zetetic:sqlcipher-android` |
| Encryption | AES-256-GCM | PBKDF2-SHA256, 100k iterations via javax.crypto |
| Rich text (Android) | Spannable | Bold/Italic/Underline/Strikethrough via Html.fromHtml()/toHtml() |
| PDF (Android) | PdfDocument | Native Android PDF generation |
| Weather | Open-Meteo API | Free, no API key required |
| Geocoding | Photon / Nominatim / Google | Configurable in settings |
| Font | Quicksand | Google Fonts CDN |

## Database Schema

**entries** — id, date, time, title, content, richContent, categories (JSON), tags (JSON), people (JSON), placeName, locations (JSON of {lat, lng, address, name?}), weather (JSON), pinned, locked, dtCreated, dtUpdated
**images** — id, entryId, name, data (base64), thumb, sortOrder
**categories** — name (PK), description
**tags** — name (PK), description
**icons** — type + name (composite PK), data (PNG data URL). Types: category, tag, person (64x64), category_hd, tag_hd, person_hd (128x128 for image buttons)
**people** — firstName + lastName (composite PK), description
**widgets** — id (PK), name, description, bgColor, icon, filters (JSON), functions (JSON), enabledInDashboard, sortOrder, dtCreated, dtUpdated
**settings** — key (PK), value
**schema_version** — version (INT)

## ServiceProvider Pattern

`ServiceProvider` is a Kotlin singleton that holds all 4 services:
- `cryptoService: CryptoService` — AES-256-GCM encryption/decryption
- `bootstrapService: BootstrapService` — SharedPreferences key-value store
- `weatherService: WeatherService` — Open-Meteo HTTP client
- `databaseService: DatabaseService` — SQLCipher encrypted DB

`ServiceProvider.init(context)` creates all services. `ServiceProvider.clear()` closes DB and nulls references. All activities access services via `ServiceProvider.xxxService`.

`ServiceProvider.saveFileToDownloads(filename, base64Data, mimeType)` handles scoped storage file exports.

## App Flow

1. **LoginActivity** — User selects/creates journal, enters password, optional biometric
2. `ServiceProvider.init(context)` — Creates all services
3. `DatabaseService.open(password, journalId)` — Opens/creates encrypted SQLCipher DB
4. `ThemeManager.init()` — Loads active theme from DB settings
5. `DashboardDataBuilder.build(db, bs)` — Computes dashboard JSON (stats, streaks, ranked lists, widgets, today in history, pinned/recent entries)
6. **DashboardActivity** — Displays dashboard, launches other activities directly

No WebView intermediary. All navigation between activities uses standard Android `startActivity()`.

## Themes (12)

Light, Dark, Ocean, Midnight, Forest, Amethyst, Aurora, Lavender, Frost, Navy, Sunflower, Meadow — theme selection stored in settings DB.

`ThemeManager.kt` singleton provides runtime theme colors. All activities call `ThemeManager.applyToActivity(this)` in `onCreate` to recolor XML-set backgrounds/text. Activities detect theme changes via `themeVersion` counter in `onResume`.

## Key Conventions

- **Fully native** — all 14 screens are Kotlin activities, no WebView
- **ServiceProvider singleton** — all activities access services via `ServiceProvider.xxxService`
- **ThemeManager singleton** — runtime theme colors via `ThemeManager.color(C.*)`, replaces static `R.color.login_*` resources
- **DashboardDataBuilder** — computes dashboard JSON natively from DatabaseService
- **Encryption everywhere** — SQLCipher auto-encrypts DB files
- **Bootstrap store** — all key-value storage uses BootstrapService (SharedPreferences wrapper)
- **Large data to activities** — SearchActivity, CalendarActivity use static `companion object` holders for entry data (avoids TransactionTooLargeException)
- **Dashboard component settings** — Settings > Dashboard tab allows toggling/reordering 11 components; stored in BootstrapService as `dashboard_components` JSON
- **DashboardActivity navigation** — hamburger menu (☰) in top navbar with PopupMenu (Entries, Calendar, Reports, Explorer, Settings, About); no bottom nav bar
- **Rich content rendering** — `Html.fromHtml()` + `TextView` (no WebView)
- **File exports** — `ServiceProvider.saveFileToDownloads()` via MediaStore scoped storage (API 29+)
- **Entry form layout** — Content/Rich Content in a tabbed group box; all action buttons (Save/Cancel/Delete) in top navbar, no bottom bar
- **Entry list** — Collapsible "Filter Info" box with search/filter controls and Order by dropdowns (field + asc/desc); alternating row colors (`CARD_BG`/`INPUT_BG`) with `CARD_BORDER` stroke
- **Settings tabs** — 2-row grid layout (3 tabs per row), equal-width buttons, no horizontal scrolling
- **HTML reports** — Exported to Downloads and opened in browser via `ACTION_VIEW` intent
