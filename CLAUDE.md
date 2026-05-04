---
tags: [architecture, project-structure, tech-stack, database-schema, build, conventions, themes, encryption]
related_files: [REQUIREMENT.md, TO_DO.md, README.md]
summary: "Project architecture reference — directory structure, tech stack, database schema, ServiceProvider pattern, build instructions, and coding conventions."
---

## Response Style
- Keep responses minimal — 1-2 sentences max unless asked for detail.
- No summaries, no explanations of what was changed unless asked.
- Just make the change and confirm briefly.

# MYJOURNAL — Project Guide

## Overview

Fully native Android encrypted journal app. All 14 screens are native Kotlin activities. Services (crypto, database, bootstrap, weather) are managed by a `ServiceProvider` singleton. All data stored locally in AES-256-GCM encrypted SQLCipher database.

**App Name:** My Journal | **Version:** 1.8.0 | **Package:** com.journal.app | **Min SDK:** 24 | **Target SDK:** 34

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
│   │   │   ├── SearchActivity.kt        # Native full-text search screen with term highlighting
│   │   │   ├── EntryViewerActivity.kt   # Native entry viewer with font settings
│   │   │   ├── EntryListActivity.kt     # Native entry list (collapsible filters, custom view filter, order-by dropdowns, alternating rows, search, sort, paginate, batch delete)
│   │   │   ├── ExplorerActivity.kt      # Native SQL Explorer (table browser, query builder, raw SQL, CSV/iCal export, SQL Library save/load)
│   │   │   ├── SettingsActivity.kt      # Native settings (7 tabs in 3-row grid: Prefs, Templates, Metadata, Data, Widgets, Dashboard, Display incl. App Font)
│   │   │   ├── EntryFormActivity.kt     # Native entry form (content, images, categories, tags, locations, weather, pre-fill templates)
│   │   │   ├── ReportsActivity.kt       # Native reports (HTML export+browser open, PDF/CSV, filters, custom view filter, templates)
│   │   │   ├── WidgetEditorActivity.kt  # Native widget editor (header/filters/functions tabs, preview)
│   │   │   ├── CustomViewEditorActivity.kt # Native custom view editor (conditions, groupBy, orderBy, display)
│   │   │   ├── CsvMappingActivity.kt    # Native CSV import mapping screen (file picker, test preview, result grid)
│   │   │   ├── ServiceProvider.kt       # Singleton service holder (replaces old MainActivity.instance)
│   │   │   ├── DashboardDataBuilder.kt  # Computes dashboard JSON from DatabaseService (stats, streaks, ranked lists, widgets, today in history)
│   │   │   ├── BootstrapService.kt      # SharedPreferences wrapper
│   │   │   ├── CryptoService.kt         # AES-256-GCM + PBKDF2
│   │   │   ├── WeatherService.kt        # Open-Meteo HTTP client
│   │   │   └── DatabaseService.kt       # SQLCipher encrypted DB
│   │   │   ├── ThemeManager.kt          # Runtime theme system (12 themes, view tree recoloring, app font scale + typeface)
│   │   ├── res/
│   │   │   ├── drawable/           # Button ripples, input/card/search/stat backgrounds, 3D tab/search drawables (25+ XML files)
│   │   │   ├── layout/            # 14 activity layouts + 2 spinner item layouts
│   │   │   ├── values/            # colors.xml, strings.xml, styles.xml, app_info.xml
│   │   │   └── xml/file_paths.xml # FileProvider for camera
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
| Database | SQLCipher 4.5.6 | Encrypted SQLite via `net.zetetic:sqlcipher-android` |
| Encryption | AES-256-GCM | PBKDF2-SHA256, 100k iterations via javax.crypto |
| PDF (Android) | PdfDocument | Native Android PDF generation |
| Weather | Open-Meteo API | Free, no API key required |
| Geocoding | Photon / Nominatim / Google | Configurable in settings |
| Font | Quicksand | Google Fonts CDN |

## Database Schema

**entries** — id, date, time, title, content, categories (JSON), tags (JSON), placeName, locations (JSON of {lat, lng, address, name?}), weather (JSON), pinned, locked, dtCreated, dtUpdated
**images** — id, entryId, name, data (base64), thumb, sortOrder
**categories** — name (PK), description
**tags** — name (PK), description
**icons** — type + name (composite PK), data (PNG data URL). Types: category, tag (64x64), category_hd, tag_hd (128x128 for image buttons)
**widgets** — id (PK), name, description, bgColor, icon, filters (JSON), functions (JSON), enabledInDashboard, sortOrder, dtCreated, dtUpdated
**inspiration** — id (PK, autoincrement), quote (TEXT), source (TEXT)
**sql_library** — id (PK, autoincrement), name (TEXT), description (TEXT), sql_statement (TEXT), dtCreated, dtUpdated
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

Navigation between activities uses standard Android `startActivity()`.

## Themes (12)

Light, Dark, Ocean, Midnight, Forest, Amethyst, Aurora, Lavender, Frost, Navy, Sunflower, Meadow — theme selection stored in settings DB.

`ThemeManager.kt` singleton provides runtime theme colors, app font scale, and typeface. All activities call `ThemeManager.applyToActivity(this)` in `onCreate` to recolor XML-set backgrounds/text and schedule typeface application. All 13 post-login activities override `attachBaseContext` with `ThemeManager.fontScaledContext()` for font size scaling. Activities detect theme/font changes via `themeVersion` counter in `onResume`.

## Key Conventions

- **Fully native** — all 14 screens are Kotlin activities
- **ServiceProvider singleton** — all activities access services via `ServiceProvider.xxxService`
- **ThemeManager singleton** — runtime theme colors via `ThemeManager.color(C.*)`, app font scale via `fontScaledContext()`, typeface via `applyTypefaceToViewTree()`
- **DashboardDataBuilder** — computes dashboard JSON natively from DatabaseService
- **Encryption everywhere** — SQLCipher auto-encrypts DB files
- **Bootstrap store** — all key-value storage uses BootstrapService (SharedPreferences wrapper)
- **Large data to activities** — SearchActivity, CalendarActivity use static `companion object` holders for entry data (avoids TransactionTooLargeException)
- **Dashboard component settings** — Settings > Dashboard tab allows toggling/reordering 11 components; stored in BootstrapService as `dashboard_components` JSON
- **Dashboard auto-refresh** — `DashboardActivity.needsRefresh` static flag; set after erase all entries, CSV import completion, widget save/delete, category icon change, or entry save/delete; checked in `onResume()` to rebuild dashboard data
- **DashboardActivity navigation** — hamburger menu (☰) in top navbar with PopupMenu (Entries, Calendar, Reports, Explorer, Settings, About); no bottom nav bar
- **File exports** — `ServiceProvider.saveFileToDownloads()` via MediaStore scoped storage (API 29+)
- **Auto GPS & weather** — Optional setting (`auto_gps_weather` in BootstrapService) to auto-populate GPS location and weather when creating a new entry; silently skips if location permission not granted or GPS disabled
- **Entry form layout** — Plain text content input; all action buttons (Save/Cancel/Delete) in top navbar, no bottom bar; 📋 template button for new entries when pre-fill templates exist
- **Entry list** — Collapsible "Filter Info" box with custom view selector, search/filter controls and Order by dropdowns (field + asc/desc); alternating row colors (`CARD_BG`/custom `alt_row_bg_color`) with `CARD_BORDER` stroke
- **Custom view integration** — Custom views selectable as filter presets in EntryListActivity and ReportsActivity; full condition evaluation engine (AND/OR logic, negation, date relative ranges, text/array/boolean operators); entry list also applies multi-level orderBy sorting from views
- **Settings tabs** — 3-row grid layout (3 tabs per row), equal-width buttons, no horizontal scrolling; 7 tabs: Prefs, Templates, Metadata, Data, Widgets, Dashboard, Display
- **HTML reports** — Exported to Downloads and opened in browser via `ACTION_VIEW` intent
- **CSV mapping** — CsvMappingActivity has Select CSV (file picker + auto-map headers), Test (20 random rows with mapping applied), and Save Mapping buttons; result grid with HorizontalScrollView and alternating row colors
- **Settings deep-link** — `SettingsActivity.initialTab` static field allows opening Settings to a specific tab (e.g. "cattags" for Metadata)
- **Collapsible metadata sections** — Categories and Tags lists in Metadata tab are collapsible; state persisted in BootstrapService
- **Collapsible template sections** — Custom Views, Pre-fill Templates, and Report Templates in Templates tab are collapsible; state persisted in BootstrapService
- **Collapsible template items** — Individual items within each template section (Custom Views, Pre-fill Templates, Report Templates) are collapsible panels with ▶/▼ toggle; header shows name + summary, body shows details and Edit/Delete buttons
- **Pre-fill templates in entry form** — 📋 button in navbar (new entries only) opens picker dialog; applies template's auto-date, auto-time, title, content, tags, categories to the form
- **Search term highlighting** — SearchActivity highlights matching terms in title and content snippet using semi-transparent accent background spans
- **Collapsible dashboard panels** — Recent Entries, Top Tags, Top Categories, Top Places, Daily Inspiration panels have ▶/▼ toggle headers; collapse state persisted in BootstrapService (`dash_*_collapsed` keys)
- **Daily Inspiration decorative panel** — Double accent border with layered insets, 3D drop shadow via LayerDrawable + elevation
