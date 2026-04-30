---
tags: [requirements, features, authentication, biometric, entries, dashboard, views, explorer, reports, settings, weather, Android, downloads]
related_files: [CLAUDE.md, TO_DO.md]
summary: "Complete functional requirements for all feature areas — authentication, storage, entries, dashboard, views, explorer, reports, settings, weather, Android platform, downloads."
---

# REQUIREMENTS — MYJOURNAL

## 1. Authentication & Security

### Password Authentication
- Each journal has its own independent password
- PBKDF2-SHA256 key derivation with 100,000 iterations
- AES-256-GCM encryption for all stored data
- 16-byte random salt per journal, 12-byte random IV per encryption operation
- Verification token: encrypt "JOURNAL_VERIFY_TOKEN" to validate password on login
- Password change: verify old password, re-encrypt entire database with new key

### Biometric Authentication (Android)
- Optional fingerprint/face unlock via AndroidX BiometricPrompt
- Enable/disable toggle in Settings > Preferences
- Stored credential: plaintext password, base64-encoded, in SharedPreferences
- Auto-biometric prompt when enabled and returning to locked app
- Credential cleared if password verification fails
- Credential updated when password is changed

### Native Login (Android)
- LoginActivity is the launcher/entry point
- Journal selector: spinner dropdown populated from SharedPreferences journal list
- New journal: password + confirmation fields, minimum length validation
- Existing journal: single password field
- Auto-open last journal option (skip login screen)
- Delete journal: removes DB data, crypto keys, biometric credential
- On login: initializes ServiceProvider, opens DB, computes dashboard data, launches DashboardActivity directly

## 2. Data Storage

### Database
- **SQLCipher encrypted SQLite** via `DatabaseService.kt` (`net.zetetic:sqlcipher-android:4.5.6`)
- Each journal stored as `journal_<id>.db` in app's database directory
- Auto-persists — no manual save needed
- ~35 CRUD methods for all tables

### Schema
- **entries**: id, date (YYYY-MM-DD), time (HH:MM), title, content (plain text), categories (JSON array), tags (JSON array), placeName, locations (JSON array of {lat, lng, address, name?}), weather (JSON {temp, unit, description, code}), pinned (0/1), locked (0/1), dtCreated, dtUpdated
- **images**: id, entryId (FK), name, data (base64), thumb (base64), sortOrder
- **categories**: name (PK), description
- **tags**: name (PK), description
- **icons**: type + name (composite PK), data (SVG/emoji)
- **widgets**: id (PK), name, description, bgColor, icon, filters (JSON), functions (JSON), enabledInDashboard, sortOrder, dtCreated, dtUpdated
- **settings**: key (PK), value
- **schema_version**: version (INT)
- Indices: idx_entries_date, idx_entries_pinned, idx_images_entry

### Bootstrap Store
- `BootstrapService.kt` wraps `SharedPreferences` (`bootstrap_prefs`)
- All operations synchronous
- `Bootstrap.get(key)`, `Bootstrap.set(key, value)`, `Bootstrap.remove(key)`
- Keys stored: crypto salts/verify tokens, journal list, UI preferences, column toggles, panel pins, view modes

### Crypto
- `CryptoService.kt` uses `javax.crypto` (AES/GCM/NoPadding, PBKDF2WithHmacSHA256)
- LoginActivity delegates to CryptoService

### Weather
- `WeatherService.kt` uses `java.net.HttpURLConnection` for Open-Meteo API calls

## 3. Journal Entries

### Entry Form (EntryFormActivity.kt)
- 2 tabs: Main and Misc
- Top navbar: Back (←), title, Save, Cancel, Delete (edit only) — no bottom action bar
- Date: `DatePickerDialog`; Time: `TimePickerDialog`
- Title: `EditText`
- Content: large plain text editor (minLines 10, maxLines 20)
- Categories: checkboxes from managed list + quick-add inline
- Tags: `AutoCompleteTextView` with chip display + quick-add inline
- Place name: `EditText`
- Locations: geocoding search (Photon/Nominatim) via background thread, GPS via `LocationManager.requestSingleUpdate()`, manual lat/lng entry; results shown in `AlertDialog` picker
- Weather: `WeatherService.fetchCurrent()` on background thread
- Images: `GetMultipleContents()` for gallery, `TakePicturePreview()` for camera; `resizeBitmap()` creates full (1920px) and thumb (150px) as JPEG base64
- Save: constructs `JSONObject` with all fields, calls `db.addEntry()` or `db.updateEntry()`

### Entry List (EntryListActivity.kt)
- Card view: entry cards with date, title, category badge, tag chips, content preview
- Alternating row colors: even rows use `CARD_BG`, odd rows use `INPUT_BG`, with `CARD_BORDER` stroke
- Collapsible "Filter Info" box: search bar, category/tag spinners, clear button, select mode, page size — collapsed by default, toggle via header tap
- Search: across title, content, tags, categories, place
- Filters: category and tag filter spinners
- Pagination: 10, 20, 50, 100 entries per page
- Sort: "Order by" dropdowns in Filter Info box — field selector (Date, Time, Title, Created, Updated, Categories, Tags) and direction (Desc/Asc), persisted to BootstrapService
- Multi-select mode: checkbox per entry, batch delete with confirmation
- Widget filter support: `matchesWidgetFilters()` with date/text/array operators

### Entry Viewer (EntryViewerActivity.kt)
- Read-only display of all entry fields
- Image thumbnails displayed
- Action buttons: Lock/Unlock, Pin/Unpin, Edit, Delete, Back
- Lock/Unlock toggle with confirmation prompt; locked entries disable Edit button
- Prev/Next navigation between entries
- Font settings: reads `ev_font_family` and `ev_font_size` from BootstrapService

## 4. Dashboard (DashboardActivity.kt)

### Data
- `DashboardDataBuilder.build(db, bs)` computes all dashboard data natively:
  - Total entries, this week/month/year counts
  - Streak (consecutive days with entries)
  - Top tags, categories, places (ranked by count, top 20)
  - Pinned entries (up to 20), recent entries (5)
  - Widget results (filtered + aggregated)
  - Journal name, theme

### Stats
- Total entries count
- Entries this week / this month / this year
- Clickable stats open EntryListActivity with date range filter
- Stats grid wrapped in `stats_container` for reordering as a unit

### Ranked Panels
- Top tags, categories, places — ranked by entry count
- Clickable items open EntryListActivity with field-specific filter
- 3D rank badges: vertical gradient (cyan→accent→dark teal), rounded corners (8dp), elevation shadow
- 3D count badges: dark gradient, rounded corners (6dp), elevation shadow
- Alternating row backgrounds for readability
- Center-aligned panel captions (Top Tags, Top Categories)

### Dashboard Search
- 3D icon button (🔍) in navbar row, left of journal name
- Opens SearchActivity on tap

### Today in History
- Matches entries where month/day equals today's date from past years (excludes current year)
- `DashboardDataBuilder.buildTodayInHistory()` computes matches, sorted by most recent year first
- Each entry shows: title, 20-character content preview, "N year(s) ago" accent badge
- Entries clickable to open in EntryViewerActivity
- Panel hidden when no matching entries exist

### Pinned & Recent Entries
- Pinned entries and recent entries displayed as rows
- Click opens EntryViewerActivity directly

### Dashboard Widgets
- Configurable aggregate cards with filters and functions
- Widget icon displayed as 40x40 ImageView in card header (base64 data URL decoded via loadBase64Image)
- Widget click opens EntryListActivity with widget filters
- Pencil edit button opens WidgetEditorActivity

### Navigation
- Top navbar: Search (🔍), journal name, New Entry (✏️), Lock (🔒), Menu (☰)
- Hamburger menu (☰) opens `PopupMenu` with: Entries, Calendar, Reports, Explorer, Settings, About
- No bottom navigation bar — all navigation via top navbar and hamburger menu
- DashboardActivity stays in back stack for proper back navigation
- Lock returns to LoginActivity (closes DB via ServiceProvider.clear())

## 4b. Calendar View (CalendarActivity.kt)

- Monthly grid view: 7-column (Mon-Sun) with day numbers, entry count dots
- Today highlighted with accent border, selected day with accent background
- Month navigation: prev/next buttons, "Today" button
- Day selection: tapping a day shows entries for that date in results panel
- Entry rows: title, time, content preview — tapping opens EntryViewerActivity

## 5. Custom Views (CustomViewEditorActivity.kt)

- Saved filter/sort/group combinations with a name
- Condition builder: field, operator, value triplets
- Logic: AND, OR, NOT combinations
- Supported fields: date, time, title, content, categories, tags, placeName, locations, weather
- Group by, sort by, display mode override
- Pin to dashboard as quick action button

## 6. SQL Explorer (ExplorerActivity.kt)

- Two input modes: raw SQL textarea or visual condition builder
- Table browser: clickable table chips show column schema + sample data
- Condition builder: Spinner dropdowns for field/op selection, EditText for values
- SQL parser: Kotlin reimplementation — SELECT/FROM/WHERE parsing, quote-aware AND splitting
- Results: HorizontalScrollView table, clickable rows → AlertDialog record detail with prev/next + "View Entry"
- CSV export: saved to Downloads via MediaStore scoped storage
- iCalendar export: .ics VEVENT per entry (entry queries only)

## 7. Reports (ReportsActivity.kt)

### Formats
- **HTML**: exported to Downloads as `.html` file and opened in device browser via `ACTION_VIEW` intent; also rendered inline in scrollable output area (600dp)
- **PDF**: native `PdfDocument` API, page breaks between entries, saved to Downloads
- **CSV**: all fields as columns, double-quoted values

### Templates
- HTML templates with field substitution tags: `<%TITLE%>`, `<%DATE%>`, `<%TIME%>`, `<%CONTENT%>`, etc.
- Template CRUD in Settings > Templates tab

### Filters
- Date range (from/to), category, tag — applied before report generation

## 8. Settings (SettingsActivity.kt, ~3000+ lines)

### Tabs (6 total, in 2-row grid with emoji icons + 3D styling)
- Row 1: ⚙️ Prefs | 📝 Templates | 🏷️ Metadata
- Row 2: 💾 Data | 🧩 Widgets | 📊 Dashboard

### Preferences
- Auto-open last journal, confirm before delete, biometric toggle
- Geocoding provider: Photon, Nominatim, or Google
- Viewer font: family + size with live preview
- Date/time format settings
- Max pinned entries, default sort

### Wallpaper
- Browse and select background image, max 1920px, JPEG 85%
- Stored as base64 in encrypted DB settings table
- Preview thumbnail, clear button

### Themes (12)
- Light, Dark, Ocean, Midnight, Forest, Amethyst, Aurora, Lavender, Frost, Navy, Sunflower, Meadow
- Theme selection persisted in settings DB
- Runtime theme system via `ThemeManager.kt` singleton: all activities use `ThemeManager.color(C.*)` for colors
- `ThemeManager.applyToActivity()` recolors XML-set backgrounds (`ColorDrawable`/`GradientDrawable`) and text via view tree walk
- Theme change: `ThemeManager.setTheme()` increments `themeVersion`; activities detect in `onResume` and recreate/finish
- Light/dark status bar icons set automatically based on theme background luminance

### Metadata Editing
- Categories: add, rename, delete, color picker, description, icons
- Tags: add, rename, delete, color picker, description
- Icons: custom 64x64 (standard) and 128x128 (HD) PNG data URLs

### Data Management
- Export: encrypted DB, CSV, metadata JSON
- Import: CSV with column mapping (CsvMappingActivity — file picker, auto-map headers, test preview of 20 random rows, result grid), metadata JSON
- Backup folder: SAF folder picker, backup/restore JSON files
- Password change: verify old, re-encrypt database

### Pre-fill Templates
- Saved templates that auto-fill entry form fields (date, time, title, content, categories, tags)

### Widgets (WidgetEditorActivity.kt)
- Widget list, create/edit/delete
- Header Info (name, desc, icon, color), Filters (field/op/value), Functions (Count/Sum/Max/Min/Average)
- Background color picker dialog: 24 preset colors, hex input, live preview swatch — opens on swatch or input tap
- Live preview in editor

### Dashboard Components
- Toggle 10 dashboard components on/off: Weather & Streak, Stats Grid, Quick Actions, Widgets, Pinned Entries, Recent Entries, Today in History, Top Tags, Top Categories, Top Places
- Reorder components via ▲/▼ arrow buttons
- Config stored in BootstrapService as `dashboard_components` JSON array of `{id, enabled}` objects
- DashboardActivity reads config and reorders/hides panels dynamically

## 9. Weather Integration

- Provider: Open-Meteo API (free, no API key) via WeatherService.kt
- City search by name → select location → save as default
- Fetch current weather: temperature (C/F toggle), condition code, description
- Weather data stored per-entry as JSON
- Weather location persisted in settings

## 10. Android Platform

### Activities (all Kotlin — 14 total)
1. **LoginActivity** (launcher) — Journal selection, password, biometric
2. **DashboardActivity** — Native stats/entries/rankings/widgets
3. **CalendarActivity** — Native monthly calendar with entry dots, day selection
4. **AboutActivity** — App info, version, links
5. **SearchActivity** — Native full-text search across all entries
6. **EntryViewerActivity** — Native entry viewer with font customization
7. **SettingsActivity** — Full native settings (6 tabs: Prefs, Templates, Metadata, Data, Widgets, Dashboard)
8. **EntryListActivity** — Native entry list with search, filter, sort, pagination, batch delete
9. **ExplorerActivity** — Native SQL Explorer with table browser, query builder, results, exports
10. **EntryFormActivity** — Native entry form with images, categories, tags, locations, weather
11. **ReportsActivity** — Native reports with HTML/PDF/CSV generation
12. **WidgetEditorActivity** — Native widget editor with tabs, live preview
13. **CustomViewEditorActivity** — Native custom view editor
14. **CsvMappingActivity** — Native CSV import column mapping with file picker, test preview (20 random rows), result grid

### Services (via ServiceProvider singleton)
- **ServiceProvider.kt** — Singleton holding all 4 services, initialized in LoginActivity
- **BootstrapService.kt** — SharedPreferences wrapper
- **CryptoService.kt** — AES-256-GCM + PBKDF2
- **WeatherService.kt** — Open-Meteo HTTP client
- **DatabaseService.kt** — SQLCipher encrypted DB (~35 CRUD methods)

### Utilities
- **DashboardDataBuilder.kt** — Computes dashboard JSON from DatabaseService
- **ThemeManager.kt** — Runtime theme singleton with 12 theme color maps, view tree recoloring, light/dark status bar

### Permissions
- INTERNET — API access (weather, geocoding)
- ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION — GPS for weather/places
- USE_BIOMETRIC — Fingerprint/face unlock
- CAMERA — Photo capture for journal entries

## 11. File Downloads

- `ServiceProvider.saveFileToDownloads(filename, base64Data, mimeType)` — scoped storage via MediaStore (API 29+)
- Supported formats: PDF, CSV, SQLite (.db), JSON, HTML, iCalendar (.ics)

