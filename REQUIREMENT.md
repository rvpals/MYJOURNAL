---
tags: [requirements, features, authentication, biometric, entries, dashboard, views, explorer, reports, settings, weather, Android, components, downloads]
related_files: [CLAUDE.md, COMPONENTS.md, TODO.md]
summary: "Complete functional requirements for all 12 feature areas — authentication, storage, entries, dashboard, views, explorer, reports, settings, weather, Android platform, components, downloads."
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
- Passes credentials to MainActivity for WebView auto-login via JS injection

## 2. Data Storage

### Database (sql.js / SQLite WASM)
- SQLite compiled to WebAssembly, runs entirely in browser/WebView
- Base64 WASM fallback for file:// protocol (avoids CORS issues)
- Encrypted database bytes stored in IndexedDB (DB: 'JournalDB', Store: 'encrypted_data')
- Per-journal storage keys in IndexedDB
- Full database loaded into memory on login, saved back on every write operation

### Schema
- **entries**: id, date (YYYY-MM-DD), time (HH:MM), title, content (plain text), richContent (HTML), categories (JSON array), tags (JSON array), people (JSON array), placeName, locations (JSON array of {lat, lng, address}), weather (JSON {temp, unit, description, code}), pinned (0/1), dtCreated, dtUpdated
- **images**: id, entryId (FK), name, data (base64), thumb (base64), sortOrder
- **categories**: name (PK), description
- **tags**: name (PK), description
- **icons**: type + name (composite PK), data (SVG/emoji)
- **people**: firstName + lastName (composite PK), description
- **widgets**: id (PK), name, description, bgColor, icon, filters (JSON), functions (JSON), enabledInDashboard, sortOrder, dtCreated, dtUpdated
- **settings**: key (PK), value
- **schema_version**: version (INT)
- Indices: idx_entries_date, idx_entries_pinned, idx_images_entry

### Bootstrap Store (replaces localStorage)
- All client-side key-value storage uses the Bootstrap module (IndexedDB-backed with in-memory cache)
- `Bootstrap.init()` called once at startup; auto-migrates existing localStorage keys
- `Bootstrap.get(key)`, `Bootstrap.set(key, value)`, `Bootstrap.remove(key)` — synchronous cache reads, async IDB writes
- Keys migrated: crypto salts/verify tokens, journal list, UI preferences, column toggles, panel pins, view modes

### Sync (Android ↔ Web)
- Journal list synced between web Bootstrap store and native SharedPreferences
- Crypto salt and verify token synced via AndroidBridge.syncCryptoKey()
- Journal list synced via AndroidBridge.syncJournalList()
- Native auto-login injects Bootstrap.set() calls (not localStorage) for crypto key sync

## 3. Journal Entries

### Entry Form
- Date: text input, strict YYYY-MM-DD validation
- Time: text input, HH:MM format
- Title: text input
- Content: dual-mode — Quill.js rich text editor OR plain textarea toggle
- Categories: single-select dropdown from managed list, "+ New" quick-create panel to add inline; description hints shown below selector when category has a description
- Tags: multi-select with auto-complete from managed list, "+ New" quick-create panel to add inline; descriptions shown in autocomplete dropdown and as hints below tag list
- People: multi-select with auto-complete from managed list, "+ New" quick-create panel (first/last name + description) to add inline; descriptions shown as hints below selector
- Place name: text input with geocoding search
- Locations: array of {lat, lng, address} from geocoding results
- Weather: fetch current weather button (Open-Meteo API), displays temp + condition
- Images: file picker (gallery) or camera capture, base64 encoded, thumbnail generated
- Save validates required fields and persists to encrypted DB

### Entry List
- Card view: entry cards with date, title, category badge, tag chips, content preview
- List/table view: ResultGrid component with dynamic columns, search highlighting, click-to-view
- Search: debounced (300ms), searches title, content, tags, categories, highlights matches in list view
- Filters: date range picker, category dropdown, tag dropdown — wrapped in CollapsiblePanel with pin support; "Clear All" button in criteria bar
- Pagination: 10, 20, 50, 100, or all entries per page
- Sort: by date (asc/desc), title, created date, updated date
- Multi-select mode: checkbox per entry, batch delete with confirmation
- Entry numbering in both views

### Entry Viewer
- Read-only display of all entry fields
- Rich content rendered as HTML
- Image gallery with lightbox (full-screen, prev/next navigation)
- Action buttons: Pin/Unpin, Edit, Print (PDF), Delete, Back — icon-only with title tooltips
- Prev/Next navigation between entries (swipe on Android)
- 3D styled buttons with gradient backgrounds, shadows, hover/press effects
- Date/Time displayed using user's configured format
- Viewer font customizable (family + size) in settings

## 4. Dashboard

### Navigation Grid
- 2-column button grid at top of dashboard: New Entry, Entry List, Report, SQL Explorer, Calendar
- 3D styled buttons with gradient, shadow, border-bottom, hover lift, press inset
- Entry List, Report, SQL Explorer, Calendar links removed from navbar — accessible only via dashboard

### Stats
- Total entries count
- Entries this week / this month / this year
- Clickable stats (navigate to filtered entry list)

### Ranked Panels
- Top tags, categories, places, people — ranked by entry count
- Show top 10 by default, "Show All" toggle
- Color-coded ranking badges
- Clickable items navigate to filtered entry list

### Pinned Entries
- List of pinned entries with date and title
- Max pinned entries configurable in settings
- Click to view entry

### Recent Entries
- Most recent entries sorted by date DESC
- Content preview (plain text, truncated)
- Click to view entry

### Dashboard Search
- Live search across all entries
- Results displayed inline with date, title, content preview

### Dashboard Widgets
- Configurable aggregate cards displayed on the dashboard
- Each widget has: name, description, optional icon (64x64), optional background color, enabled/disabled toggle
- Widget filters: entry criteria using field/operator/value triplets (AND logic)
  - Supported fields: date, time, title, content, categories, tags, people, placeName
  - Operators vary by type: date (after, before, equals, between), text (contains, equals, starts/ends with, is empty/not empty), array (includes, not includes, is empty/not empty)
- Widget functions: aggregate computations on filtered entries
  - Functions: Count, Sum, Max, Min, Average
  - Target fields: entries (row count), tags, categories, people, placeName, title
  - Each function has optional prefix label and postfix text
- Widget editor: separate page with 3 tabs (Header Info, Filter, Functions), live preview
- Widget storage: `widgets` DB table, included in metadata export/import
- Widget rendering: cards with auto-contrast text color, icon, results rows

### Quick Actions
- Custom views pinned to dashboard appear as action buttons

### Native Dashboard (Android)
- DashboardActivity receives JSON data from WebView via getDashboardDataJSON()
- 2x2 stats grid, pinned entries, recent entries, ranked panels
- Clickable rows return navigation intent to MainActivity
- Color-coded ranking badges

## 4b. Calendar View

- Accessible from "Calendar" button in dashboard navigation grid
- Two view modes: Monthly (default) and Weekly, toggled via buttons
- **Monthly view**: 7-column grid (Monday to Sunday) showing all days of the current month
  - Each day cell shows the day number and a color-coded entry count badge (green=1, blue=2-3, red=4+)
  - Empty days before the 1st are rendered as blank cells
  - Today is highlighted with accent border
- **Weekly view**: 7-column grid showing one week (Monday to Sunday)
  - Each day shows the day number, entry count badge, and a list of entry titles
  - Clicking an entry title navigates directly to the entry viewer
- **Navigation**: Previous/Next buttons to move by month or week, "Today" button to return to current date
- **Go to date**: Text input (YYYY-MM-DD format) with "Go" button to jump to a specific date; invalid input shows red border feedback
- **Day selection**: Clicking a day cell highlights it and renders a ResultGrid below the calendar with that day's entries
  - ResultGrid columns: Time, Title, Categories (with icon+color), Tags (with icon+color)
  - Clicking an entry row navigates to the entry viewer
  - Updates `viewEntryList` for prev/next navigation in viewer
- Entry data fetched via `DB.getEntries()` and filtered client-side by date range
- Full CSS theme support via variables; responsive design with mobile breakpoints

## 5. Custom Views

- Saved filter/sort/group combinations with a name
- Condition builder: field, operator, value triplets
- Logic: AND, OR, NOT combinations
- Supported fields: date, time, title, content, categories, tags, placeName, locations, weather, people
- Operators vary by field type: contains, equals, starts with, ends with, before, after, between, includes, not includes, is empty, is not empty
- Group by: date, category, tag, place, weather condition
- Sort by: multiple fields with asc/desc
- Display mode override: card or list view
- Pin to dashboard: view appears as quick action button
- Default view: optionally set one view as default on app open
- Preview: show matching entries without saving the view
- Load into SQL Explorer: populate explorer conditions from saved view

## 6. SQL Explorer

- Accessible from "SQL Explorer" nav link
- Two input modes: raw SQL textarea or visual condition builder
- Visual builder: field chips, operator dropdown, value input, add/remove rows
- Raw SQL: queries targeting non-entries tables (e.g., `SELECT * FROM icons`) run directly via sql.js
- Entry queries: parsed from SQL or built from conditions, executed as in-memory filter
- Supported fields (condition builder): date, time, title, content, categories, tags, placeName, locations, weather
- Type-specific operators: contains, equals, starts/ends with, before/after, between, includes, is empty, has/no data
- Toggleable field chips to select which columns appear in results
- Results table: row numbers, clickable rows to open record detail overlay, inline delete button (with "Delete journal entry" tooltip)
- Record detail overlay: full-screen modal showing all field values (untruncated), prev/next navigation between records, click outside or x to close
- Load Custom View dropdown: load conditions from saved custom views into the builder and auto-run
- CSV export button: exports current results grid to CSV file with all values double-quoted, date-stamped filename (e.g., `explorer_results_2026-04-01.csv`)
- iCalendar export button: exports entry-based results as .ics file with VEVENT per entry (date, time, title, location, description); only available for entry queries (not raw SQL on non-entry tables)
- Table browser: clickable table name chips (dynamically loaded from SQLite schema) show column schema (name, type, PK, not null, default) and up to 5 sample rows
- Clear button resets both SQL and builder

## 7. Reports

### Formats
- **HTML**: rendered in-page with template substitution, printable
- **PDF**: generated via jsPDF, page breaks between entries
- **CSV**: all fields as columns, double-quoted values

### Templates
- HTML templates with field substitution tags:
  - `<%TITLE%>`, `<%DATE%>`, `<%TIME%>`, `<%CONTENT%>`, `<%RICH_CONTENT%>`
  - `<%CATEGORIES%>`, `<%TAGS%>`, `<%PLACES%>`, `<%WEATHER%>`
  - `<%DT_CREATED%>`, `<%DT_UPDATED%>`, `<%ID%>`
- Inline template editor in Reports page
- Template CRUD in Settings > Templates tab
- Sample templates provided in /web/templates/

### Filters
- Date range (from/to)
- Category filter
- Tag filter
- Applied before report generation

## 8. Settings

### Preferences
- Auto-open last journal (skip login)
- Confirm before delete (entries)
- Biometric authentication toggle
- Geocoding provider: Photon, Nominatim, or Google
- Viewer font: family + size with live preview
- Date display format: Short, Long, ISO, US, EU, Weekday
- Time display format: 12-hour, 24-hour
- Max pinned entries count
- Default sort field and direction

### Themes
- 12 themes: Light, Dark, Ocean, Midnight, Forest, Amethyst, Aurora, Lavender, Frost, Navy, Sunflower, Meadow
- All implemented via CSS `[data-theme]` custom properties
- Theme selection persisted in settings DB

### Metadata Editing
- Categories: add, rename, delete, assign color (color picker with swatches), editable description (shown as hint in entry form)
- Tags: add, rename, delete, assign color (color picker with swatches), editable description (stored in tags table, shown as hint in entry form and autocomplete dropdown)
- "Use category/tag colors" toggles — colors applied in entry list and dashboard
- People: add, edit, delete (first name, last name, description)
- Icons: custom image icons for categories, tags, and people
  - Standard: 64x64 PNG data URL for chips and list views
  - HD: 128x128 PNG data URL for full-image 3D buttons (stored as `type_hd` in icons table)
  - Upload auto-generates both sizes if source image >=96px; small sources get standard only
  - 3-tier fallback in RankedPanel card view: HD image button → standard icon + text → 2-letter text abbreviation
- Entry field visibility toggles (show/hide fields in entry form)

### Data Management
- **Consolidated UI**: Export and Import each have a dropdown selector + action button (replaces individual buttons)
- **Export dropdown**: Export Encrypted, Export Unencrypted, Export CSV File, Export MetaData — with 📤 icon button
- **Import dropdown**: Import Data, Import CSV File, Import MetaData — with 📥 icon button
- **CSV Import**: Column mapping UI, date/time format configuration, duplicate detection
- **Metadata Export**: JSON containing categories, tags, people, icons, settings, report templates, pre-fill templates, custom views (excludes entries/images)
- **Metadata Import**: Overwrite warning, full replace, in-place UI refresh (no page reload); supports both old (string[]) and new ({name,description}[]) category format
- **Metadata Export/Import fields**: categories (with descriptions), icons, people, tag descriptions, settings, widgets
- **Backup Folder** (Android only): SAF folder picker for persistent backup directory, backup all data to JSON, restore from backup files, list available backups
- **Password Change**: Verify old password, re-encrypt entire database

### Pre-fill Templates
- Saved templates that auto-fill entry form fields
- Fields: date pattern, time, title, content, categories, tags
- CRUD in Settings > Templates tab
- Apply from entry form dropdown

## 9. Weather Integration

- Provider: Open-Meteo API (free, no API key)
- City search by name → select location → save as default
- Fetch current weather: temperature (C/F toggle), condition code, description
- Weather data stored per-entry as JSON
- Weather location persisted in settings
- Display: weather badge in entry cards, full details in viewer
- Dashboard weather: inset 3D styled display, clickable to navigate to Settings > Weather Location

## 10. Android Platform

### Activities
1. **LoginActivity** (launcher) — Journal selection, password, biometric
2. **MainActivity** — WebView container with AndroidBridge
3. **DashboardActivity** — Native stats/entries/rankings
4. **AboutActivity** — App info, version, links

### Permissions
- INTERNET — API access (weather, geocoding, CDN)
- ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION — GPS for weather/places
- USE_BIOMETRIC — Fingerprint/face unlock
- CAMERA — Photo capture for journal entries

### AndroidBridge (JS ↔ Native)
- File downloads via scoped storage (API 29+)
- Biometric prompting
- Credential storage in SharedPreferences
- Dashboard data exchange
- Platform detection
- Crypto key and journal list sync (via Bootstrap store, not localStorage)
- Backup folder: selectBackupFolder, hasBackupFolder, getBackupFolderName, clearBackupFolder, saveFileToBackupFolder, listBackupFolderFiles, readBackupFolderFile

### CSS
- style-android.css loaded only on Android (`.android` body class)
- Compact navbar with icon-only buttons
- Touch-optimized button sizes (2.2rem minimum)
- 3D button styling with gradients and shadows
- Active/press states with inset shadow + transform

## 11. Reusable UI Components (components.js)

- **ResultGrid** — scrollable data table with configurable columns, row numbers, click handlers, highlight term support, and max-height scroll. Used in: dashboard search, SQL explorer results, entry list (table view).
- **RankedPanel** — self-contained ranked list/card panel with built-in list/card view toggle, show all/top N, localStorage-persisted view mode, click-to-filter. Used in: dashboard ranked panels (tags, categories, places, people).
- **RecordViewer** — full-screen detail overlay for a single record with prev/next navigation, field formatters, highlight support. Used in: dashboard search row click, SQL explorer row click.
- **CollapsiblePanel** — reusable collapsible container with header toggle and pin-to-expand button. Pin state persisted to localStorage. Used in: entry list search & filter controls.

## 12. File Downloads

- **Web browser**: standard blob URL download or anchor click
- **Android WebView**: MUST use `downloadFile()` → `AndroidBridge.saveFile()` (blob URLs crash WebView)
- Supported formats: PDF, CSV, SQLite (.db), JSON, HTML
- Scoped storage on Android API 29+ (Downloads directory)
