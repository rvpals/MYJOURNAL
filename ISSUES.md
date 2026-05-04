---
tags: [bugs, issues, limitations, fixed-bugs, architecture-notes]
related_files: [TO_DO.md, CLAUDE.md, REQUIREMENT.md]
summary: "Known bugs, platform limitations, fixed issues, and architectural notes for the fully native Android app."
---

# ISSUES — MYJOURNAL

## Known Issues

- **Large journals may be slow** — entire SQLite DB operations are synchronous; no lazy loading for 1000+ entries
- **Image storage is inefficient** — full base64 images stored in SQLite; no server-side or file-system storage option

## Fixed Issues

### 2026-05-04

- **Pre-fill template not populating title field (v1.8.2)** — `applyTemplate()` called `showTab(activeTab)` which internally called `syncFormData()`, reading the old `titleInput.text` back into `titleValue` and overwriting what the template had just set. Replaced `showTab()` call with direct `contentContainer.removeAllViews()` + `buildMainTab()`/`buildMiscTab()` to skip the sync.
- **Pre-fill template not populating title field (v1.8.1)** — `buildMainTab()` read the old (empty) `titleInput` text back into `titleValue` before creating the new input, overwriting the value `applyTemplate()` had just set. Moved the title/content readback into `syncFormData()` which runs before template values are applied.

### 2026-05-03

- **Dashboard not refreshing after entry save/delete** — `EntryFormActivity` did not set `DashboardActivity.needsRefresh` in `saveEntry()` or `confirmDelete()`. New or edited entries required manual refresh to appear on dashboard. Added `needsRefresh = true` in both save and delete paths.

### 2026-05-02

- **SQL Explorer Library load crash** — Clicking "Load" on a saved query in the SQL Library dialog crashed the app. `findViewById<EditText>(R.id.explorer_sql_input)` inside the Load button's `setOnClickListener` resolved to `Button.findViewById` (via enclosing `apply` scope) instead of the Activity's, returning null → NPE. Fixed by qualifying with `this@ExplorerActivity.findViewById`.

### 2026-05-01

- **Dashboard not refreshing after category icon change** — `handleIconResult()` and icon remove button in SettingsActivity did not set `DashboardActivity.needsRefresh`. Icons assigned to categories wouldn't appear in the Top Categories dashboard panel until app restart. Added `needsRefresh = true` in both paths.
- **Dashboard not refreshing after dashboard component settings change** — Toggle, move up, and move down actions in Settings > Dashboard tab called `saveDashboardComponents()` but did not set `DashboardActivity.needsRefresh`. Changes to component visibility or order required app restart to take effect. Added `needsRefresh = true` after each `saveDashboardComponents()` call.

### 2026-04-30 (session 2)

- **Category icon editing missing** — Edit category dialog only had description field. Added Change Icon / Remove buttons with image picker, saves 64px + 128px icons to DB.
- **Wallpaper feature removed** — Removed wallpaper settings from Preferences tab, pickWallpaper/handleWallpaperResult methods, WALLPAPER_PICK constant, and loadBase64Image (unused after removal).
- **Daily Inspiration panel had no edit access** — Panel was hidden when no quotes existed, making it impossible to add quotes from dashboard. Now always visible with empty state prompt and ✏️ button that deep-links to Settings > Metadata tab.
- **Metadata tab hard to navigate** — Categories and Tags lists could get long. Made both sections collapsible with persisted state.

### 2026-04-30

- **Date/time format used key-based system** — Settings stored keys like `short`/`long`/`12h`/`24h` that each activity had to interpret via `when` blocks. Replaced with actual `SimpleDateFormat` pattern strings (e.g., `MMMM d, yyyy`, `h:mm a`) stored directly in BootstrapService. Applied formatting consistently across 6 activities: SettingsActivity, EntryListActivity, ReportsActivity, EntryViewerActivity, DashboardActivity, SearchActivity.
- **Dashboard not refreshing after widget save/delete** — WidgetEditorActivity did not set `DashboardActivity.needsRefresh` flag on save or delete. Added `needsRefresh = true` before `finish()` in both `saveWidget()` and `confirmDelete()`.
- **Dashboard not refreshing after data changes** — `onResume()` only checked for theme changes. Added `DashboardActivity.needsRefresh` static flag, set after erase all entries and CSV import completion. CSV import now shows AlertDialog with count; OK closes Settings and triggers dashboard rebuild.
- **People feature removed** — Deleted people table, people column from entries, all people CRUD functions, people UI in form/viewer/settings/dashboard/CSV/widgets. Simplifies schema and reduces form complexity.
- **Rich content / Quill.js editor removed** — Deleted RichEditorActivity, rich_editor.html asset, activity_rich_editor.xml layout, richContent column from entries, and all related code across EntryForm, EntryViewer, Search, Reports, CsvMapping, Settings. App goes from 15 to 14 activities, 22 to 21 Kotlin files. Content editing is now plain text only.
- **Web SPA directory removed** — Deleted entire `/web/` directory (24 files: HTML, CSS, JS, WASM, templates). The browser-only fallback SPA is no longer part of the project. All references removed from documentation.

### 2026-04-29

- **Theme selection did nothing** — Colors were hardcoded as static XML resources. Created `ThemeManager.kt` with all 12 theme color maps, replaced 350 `ContextCompat.getColor()` calls with `ThemeManager.color(C.*)`.
- **CSV import regex crash** — `parseDateWithFormat()` used chained `.replace()` that corrupted named groups. Rewritten as single-pass `Regex.replace()` with lambda.
- **"Use space to separate tags" missing** — Lost during Kotlin conversion. Restored checkbox in CsvMappingActivity.
- **EntryFormActivity edit opened blank form** — `loadEntryData()` never set `dateValue`, `timeValue`, `titleValue`, `contentValue`. Added four missing assignments.
- **CSV mapping had no preview** — Users couldn't validate mapping against actual CSV data before import. Added Select CSV file picker, Test button (20 random rows), and horizontally scrollable result grid with mapped/parsed values.
### 2026-04-28

- **Widget icon not showing on dashboard** — `createWidgetCard()` never read the `icon` field. Added `ImageView` with `loadBase64Image()`.
- **No color picker in widget editor** — Added `showColorPickerDialog()` with 24 presets, hex input, and live preview.
- **EntryViewer Edit button did nothing** — Passed null `bootstrapService`. Fixed to use `ServiceProvider.bootstrapService`.
- **Dashboard ranked panels / stat cards didn't filter** — Android intercepted navigation. Fixed to construct filter JSON directly.

### 2026-04-26

- **SearchActivity crash (TransactionTooLargeException)** — Passed full entries JSON via Intent. Replaced with static companion object data holder.

### 2026-04-16

- **CSV mapping UI unusable** — Moved to full-screen modal overlay.
- **build.gradle stuck at "1.0a"** — Updated to "1.5.0", versionCode 6.

### 2026-04-06

- **New journal creation fails** — Categories INSERT missing column name. Fixed SQL.
- **Null reference on nav-links** — Added null check.

### 2026-04-04

- **Metadata import blank screen** — `window.location.reload()` fails in WebView file:// protocol. Replaced with in-place refresh.

### 2026-04-01

- **Web login showing after native login** — Crypto keys now passed via Intent extras.
- **Biometric prompt appearing twice** — Added `hasNativeLogin()` check.
- **CSV import UI overflow** — Reduced min-widths, added flex/overflow.

### 2026-03-31 and earlier

- Settings Tags card cut off, Android viewer pin button layout, SQL Explorer CSV export crash, formatDate() "Invalid Date", WASM loading on file:// protocol, new journal creation on web — all fixed.

## Architecture Notes

### Fully Native Architecture (2026-04-28+)

- All 14 screens are Kotlin activities. No WebView usage.
- **ServiceProvider singleton**: Holds CryptoService, BootstrapService, WeatherService, DatabaseService. Initialized in LoginActivity, accessed by all activities.
- **ThemeManager singleton**: Runtime theme system with 12 color maps. `ThemeManager.color(C.*)` replaces static XML resources. `applyToActivity()` recolors view tree and schedules typeface application. `fontScaledContext()` provides scaled context for `attachBaseContext`. Theme/font changes detected via `themeVersion` counter.
- **DashboardDataBuilder**: Computes dashboard JSON natively from DatabaseService.
- **Login flow**: LoginActivity -> ServiceProvider.init() -> DB open -> ThemeManager.init() -> DashboardDataBuilder.build() -> DashboardActivity.
- **21 Kotlin source files**: 14 activities + 4 services + ServiceProvider + DashboardDataBuilder + ThemeManager.
- **Database tables**: entries, images, categories, tags, icons, widgets, inspiration, sql_library, settings, schema_version.

### UI Patterns (2026-04-29)

- **DashboardActivity**: No bottom nav bar. Top navbar has Search, journal name, New Entry, Lock, and ☰ hamburger menu (`PopupMenu` with Entries, Calendar, Reports, Explorer, Settings, About).
- **EntryFormActivity**: Plain text content input. Action buttons (Save/Cancel/Delete) in top navbar, no bottom bar.
- **ReportsActivity**: HTML button exports to Downloads and opens in browser via ACTION_VIEW intent. Output area 600dp.
- **EntryListActivity**: Collapsible "Filter Info" box with custom view selector, search, category/tag filters, "Order by" dropdowns (field + direction), select mode, page size. Alternating row colors (CARD_BG/INPUT_BG) with CARD_BORDER stroke. Custom view filter applies full condition evaluation engine with multi-level orderBy sorting.
- **ReportsActivity**: Custom view selector pre-populates date/category/tag filters and applies full condition evaluation during report generation.
- **EntryFormActivity**: 📋 template button (new entries only) opens pre-fill template picker; applies saved field defaults.
- **SettingsActivity**: 7 tabs in 3-row grid (3 per row, equal-width) instead of horizontal scroll. Display tab for theme, app font (family + size), entry viewer font, and alternate row color.

### Dashboard Components (11 total)

Weather/streak, Stats, Quick actions, Widgets, Pinned entries, Recent entries, Today in History, Top Tags, Top Categories, Top Places, Daily Inspiration. Configurable via Settings > Dashboard tab. Order/visibility stored in BootstrapService as `dashboard_components` JSON.

### Dashboard Auto-Refresh

`DashboardActivity.needsRefresh` static flag triggers dashboard rebuild in `onResume()`. Set after: erase all entries (SettingsActivity), CSV import completion (SettingsActivity — shows AlertDialog, OK finishes back to dashboard), widget save/delete (WidgetEditorActivity), category icon change (SettingsActivity — icon upload or remove), dashboard component settings change (SettingsActivity — toggle/reorder in Dashboard tab), entry save or delete (EntryFormActivity).

### Widgets

- Widget click shows filtered entries (not editor). Pencil button for editor access.
- Native filter engine: `EntryListActivity.matchesWidgetFilters()` with date/text/array operators.
- DashboardActivity uses `GradientDrawable` for colored backgrounds with `getContrastColor()`.

### Entry Locking

- `locked` column (INTEGER DEFAULT 0), schema version 2.
- Locked entries viewable but not editable. Lock/Unlock with confirmation.

### Service Layer

- SQLCipher: `net.zetetic:sqlcipher-android:4.5.6`, `System.loadLibrary("sqlcipher")`.
- Location name field: `{lat, lng, address, name?}` — backwards-compatible.

## Limitations

- No cloud sync — all data is local-only
- Camera capture stores full-resolution images (no automatic compression setting)
- No undo/redo in native entry form beyond OS default
