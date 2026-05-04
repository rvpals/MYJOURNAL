---
tags: [todo, testing, backlog, checklist, QA, version-history]
related_files: [CLAUDE.md, ISSUES.md, REQUIREMENT.md]
summary: "Remaining backlog, test checklist, version-by-version completion history, and Kotlin migration status."
---

# TO DO — MYJOURNAL

## Remaining TODO

- [ ] Re-test all features after WebView removal (all native screens)
- [ ] Entry image thumbnails could be optimized (currently full base64 stored)
- [ ] Consider lazy loading for large journal databases (1000+ entries)
- [ ] Password strength indicator on journal creation

- [ ] Accessibility audit (screen reader support, keyboard navigation)
- [ ] Localization / i18n support

## Test Checklist

### Critical Path (must work)

- [x] **Login flow** — Create new journal with password, re-open existing journal, wrong password rejection (verified 2026-04-02)
- [x] **Biometric login** — Enable in settings, lock app, re-authenticate with fingerprint/face (fixed double-prompt bug 2026-04-01)
- [x] **Native LoginActivity** — Journal selector spinner, password entry, biometric button, auto-open last journal (fixed web login reappearing 2026-04-01)
- [ ] **Entry CRUD** — Create entry with all fields, edit existing, delete with confirmation
- [ ] **Encryption** — Lock app, re-login, verify entries are intact and decrypted correctly
- [ ] **Data persistence** — Close app completely, reopen, verify data survives

### Native Android Activities (14 activities, no WebView)

- [ ] **LoginActivity → DashboardActivity** — Login completes, ServiceProvider initialized, DB opened, DashboardDataBuilder computes data, DashboardActivity launches directly (no WebView)
- [ ] **DashboardActivity** — Stats grid (total, week, month, year), pinned entries, recent entries, ranked panels (tags, categories, places), widgets with colored backgrounds
- [ ] **AboutActivity** — App info display, clickable email/URL/Play Store links, changelog entries
- [ ] **CalendarActivity** — Monthly grid renders correctly, day selection shows entries, today/selected highlighting, prev/next month navigation, Today button, entry rows clickable
- [ ] **SearchActivity** — Search icon button on dashboard opens native search screen, text input with search/clear, whole-word checkbox, results show title/date/content snippet/metadata, back button returns to dashboard
- [ ] **EntryViewerActivity** — Font settings applied (reads ev_font_family/ev_font_size from BootstrapService), title bold at 1.3x
- [ ] **SettingsActivity** — All 7 tabs load and function: Preferences (toggles, date/time, weather), Templates (views/entry/report), Metadata (categories/tags/inspiration CRUD with icons/colors), Data (export/import, password change), Widgets (list/editor with filters/functions), Dashboard (component toggle/reorder), Display (theme, font, alt row color)
- [ ] **Settings tab icons** — All 7 tabs show emoji icons with 3D active/inactive styling
- [ ] **EntryListActivity** — Search across all fields, category/tag filter spinners, sort by configured field, pagination (10/20/50/100), card view with all entry metadata, select mode with batch delete, navigation to EntryViewerActivity
- [ ] **ExplorerActivity** — Table browser (click chip shows schema + sample), SQL textarea (raw SQL on any table), condition builder (add/remove rows, field/op/value spinners), field chips (toggle columns), Run Query, results table (clickable rows → record detail dialog), CSV export, iCalendar export, Clear button, SQL Library (Save/Load/Edit/Delete queries)
- [ ] **EntryFormActivity** — New entry: date/time pickers, title, content, image add (gallery + camera), save. Edit entry: loads existing data, updates correctly. Categories checkboxes + quick-add, tags autocomplete + chips, place name, location search (Photon/Nominatim) + GPS, weather import, delete button on existing entries
- [ ] **EntryFormActivity images** — Gallery picker adds images with thumbnails, camera capture works, remove button on each image, images saved as base64 with thumb
- [ ] **EntryFormActivity tabs** — Main/Misc tab switching, all fields accessible, scroll position preserved
- [ ] **ReportsActivity** — HTML report generation with template, date/category/tag filters, CSV export, PDF export via native PdfDocument, template selection
- [ ] **WidgetEditorActivity** — Create widget: name/desc/icon/color, add filter rows, add function rows, preview, save. Edit existing widget: loads data, updates correctly. Color picker dialog opens on swatch/input tap with presets and hex input
- [ ] **CustomViewEditorActivity** — Create view: name, conditions (field/op/value), groupBy, orderBy, displayMode, pinToDashboard. Edit existing view, delete
- [ ] **CsvMappingActivity** — Column mapping dropdowns, date/time format config, "Use space to separate tags" checkbox, duplicate detection, import executes correctly
- [ ] **CsvMappingActivity Select CSV** — Select CSV file via file picker, verify headers auto-mapped to entry fields, toast shows filename/row count/column count
- [ ] **CsvMappingActivity Test** — Click Test after selecting CSV, verify 20 random rows shown in result grid with mapped column headers and parsed date/time values
- [ ] **CsvMappingActivity Test without file** — Click Test without selecting CSV, verify toast "Please select a CSV file first"
- [ ] **CSV date/time parsing** — Import with custom date formats (YYYY-MM-DD, MM/DD/YYYY, DD-MM-YYYY) and time formats (HH:mm, H:mm A) parses correctly without regex errors
- [ ] **Back button** — Proper navigation between all activities

### Entry Form

- [ ] **Image attachments** — Gallery picker via GetMultipleContents, camera capture via TakePicturePreview, thumbnail grid, remove button per image
- [x] **Categories** — Checkboxes with quick-add (verified 2026-04-03)
- [x] **Tags** — Auto-complete, create new from form via quick-create panel (verified 2026-04-03)
- [ ] **Location** — Place name input, geocoding search (Photon/Nominatim), GPS via requestSingleUpdate, manual lat/lng add
- [ ] **Weather** — Fetch current weather via WeatherService, display in form and viewer
- [ ] **Date/Time** — DatePickerDialog and TimePickerDialog
- [ ] **Pin entry** — Toggle pin, verify appears in dashboard pinned list
- [ ] **Edit from viewer** — EntryViewerActivity edit button launches EntryFormActivity with entry ID, all existing fields pre-populated (bug fixed: was not loading date/time/title/content)

### Entry List & Viewer

- [x] **List view** — Card mode and list/table mode toggle (verified 2026-04-03)
- [x] **Search** — Debounced search across title, content, tags, categories (verified 2026-04-03)
- [x] **Filters** — Date range, category, tag filters, Clear All button (verified 2026-04-03)
- [ ] **Pagination** — 10/20/50/100 entries per page
- [ ] **Multi-select** — Batch delete mode
- [ ] **Entry viewer** — Read-only view with all fields, prev/next navigation, icon-only action buttons
- [ ] **Date/Time format** — Verify all 6 date formats (MMMM d, yyyy / MMM d, yyyy / yyyy-MM-dd / MM/dd/yyyy / dd/MM/yyyy / EEE, MMM d, yyyy) and 2 time formats (h:mm a / HH:mm) display correctly across EntryList, EntryViewer, Dashboard, Search, Reports

### Dashboard

- [x] **Stats cards** — Total entries, this week, this month, this year (verified 2026-04-06)
- [x] **Ranked panels** — Top tags, categories, places (verified 2026-04-06)
- [x] **Pinned entries** — Display, click to view (verified 2026-04-06)
- [x] **Recent entries** — Display, click to view (verified 2026-04-06)
- [x] **Dashboard weather** — Inset 3D styling, click to navigate to Settings > Weather Location (verified 2026-04-06)
- [x] **Quick actions** — Pinned custom views on dashboard (verified 2026-04-06)
- [ ] **Today in History** — Verify panel shows entries from same month/day in past years, displays title + 20-char content preview + "N year(s) ago" badge, hidden when no matches, entries clickable to viewer
- [ ] **Dashboard component settings** — Toggle components on/off in Settings > Dashboard (11 components including Today in History, Daily Inspiration), verify hidden components disappear from dashboard
- [ ] **Dashboard component reorder** — Reorder components via arrows in Settings > Dashboard, verify new order on dashboard
- [ ] **Daily Inspiration panel** — Shows random quote from inspiration table, refresh button loads new quote, ✏️ edit button opens Settings > Metadata, empty state shows prompt
- [ ] **Top Categories card view** — Toggle ☰/▦ icon switches between list and card view, card view shows category icons in 3-column grid
- [ ] **Dashboard ranked badge 3D** — Rank badges show gradient + shadow, count badges show dark gradient + shadow
- [ ] **Dashboard search button** — 3D icon button in navbar row (left of journal name) opens SearchActivity

### Dashboard Navigation (Native — via ☰ hamburger menu)

- [ ] **Dashboard → Entry List** — Tap ☰ > Entries, native EntryListActivity opens, back returns to dashboard
- [ ] **Dashboard → Calendar** — Tap ☰ > Calendar, native CalendarActivity opens, back returns to dashboard
- [ ] **Dashboard → Reports** — Tap ☰ > Reports, native ReportsActivity launches
- [ ] **Dashboard → Explorer** — Tap ☰ > Explorer, native ExplorerActivity opens, back returns to dashboard
- [ ] **Dashboard → Settings** — Tap ☰ > Settings, native SettingsActivity opens, back returns to dashboard
- [ ] **Dashboard → About** — Tap ☰ > About, native AboutActivity opens
- [ ] **Dashboard → Entry Form** — Tap "New Entry" (✏️) button, native EntryFormActivity launches
- [ ] **Dashboard → Lock** — Tap "Lock" (🔒) button, returns to LoginActivity, DB closed
- [ ] **Dashboard stat cards filter** — Tap "This Week" stat card, EntryListActivity opens with date range filter
- [ ] **Dashboard ranked panel filter** — Tap a tag/category/place, EntryListActivity opens with proper filter
- [ ] **Dashboard widget click** — Tap widget card, EntryListActivity opens with widget filters applied
- [ ] **Dashboard widget edit** — Tap pencil button on widget card, native WidgetEditorActivity opens
- [ ] **Dashboard pinned/recent entry click** — Tap entry row, EntryViewerActivity opens showing that entry
- [ ] **Dashboard search** — Tap search icon, native SearchActivity opens, back returns to dashboard

### Custom Views

- [ ] **Create view** — Name, conditions (AND/OR/NOT), group by, sort by, display mode
- [ ] **Condition types** — All operators: contains, equals, starts/ends with, before/after, between, includes, is empty
- [ ] **Load view** — Apply saved view to entry list
- [ ] **Pin to dashboard** — View appears as quick action

### SQL Explorer

- [ ] **Visual builder** — Field/operator/value conditions with Spinner dropdowns, add/remove rows
- [ ] **Raw SQL** — Type raw SQL targeting any table
- [ ] **Results table** — Horizontally scrollable, clickable rows
- [ ] **Record detail** — Click row opens AlertDialog with full field values, prev/next, "View Entry" button
- [ ] **CSV export** — Export results to CSV, saved to Downloads via MediaStore
- [ ] **iCalendar export** — Export entry results as .ics
- [ ] **Table browser** — Click table chips to see column schema and sample rows
- [ ] **Clear button** — Resets conditions, SQL input, results

### Reports

- [ ] **HTML report** — Generate with templates, display in output area
- [ ] **PDF export** — Native PdfDocument API generation, saved to Downloads
- [ ] **CSV export** — Column headers, all fields
- [ ] **Template editor** — Create/edit/delete templates
- [ ] **Filters** — Date range, category, tag filtering

### Settings

- [ ] **Preferences tab** — Auto-open journal, confirm delete, biometric toggle, auto GPS & weather on new entry, geocoding provider, date/time format, max pinned, sort order
- [ ] **Display tab** — Theme picker, app font (family + size with preview and Apply), entry viewer font with live preview, alternate row background color picker
- [ ] **Theme picker** — All 12 themes apply correctly (ThemeManager recolors backgrounds, text, status bar)
- [ ] **Theme persistence** — Selected theme survives app restart (saved in DB settings, loaded via ThemeManager.init())
- [ ] **Theme propagation** — After theme change in Settings, Dashboard and other activities update colors on return
- [ ] **Weather city search** — Search city, select from results, save location
- [ ] **Categories editor** — Add, rename, delete, color picker, icon upload (Change Icon / Remove in edit dialog)
- [ ] **Tags editor** — Add, rename, delete, color picker, description
- [ ] **Inspiration quotes** — Add, edit, delete quotes with source attribution in Metadata tab
- [ ] **Custom Views** — Create/edit/delete views
- [ ] **Pre-fill templates** — Create, edit, delete entry templates
- [ ] **Report templates** — CRUD for HTML templates
- [ ] **Password change** — Old password verification, re-encrypt database
- [ ] **Data export** — Export encrypted, CSV, metadata JSON
- [ ] **Widgets tab** — Widget list, create/edit/delete with editor, live preview

### Dashboard Widgets

- [x] **Create widget** — Open widget editor, set name, description, icon, background color (verified 2026-04-05)
- [x] **Widget filters** — Add filter rows, verify entries filtered correctly (verified 2026-04-05)
- [x] **Widget functions** — Add aggregate functions (Count, Sum, Max, Min, Average) (verified 2026-04-05)
- [x] **Widget preview** — Preview shows live widget card with computed results (verified 2026-04-05)
- [x] **Widget on dashboard** — Enabled widgets render as cards with correct values (verified 2026-04-05)
- [ ] **Widget icon on dashboard** — Widget with icon shows 40x40 image in card header (was missing before fix)
- [ ] **Widget color picker** — Tap swatch or hex input in editor, color picker dialog opens with 24 presets, hex input, live preview
- [x] **Widget edit/delete** — Edit existing widget, delete with confirmation (verified 2026-04-05)
- [x] **Widget persistence** — Widgets survive app restart (verified 2026-04-05)

### Widget Click-to-Filter

- [ ] **Widget card click shows entries** — Click widget card, verify navigates to entry list with filtered entries
- [ ] **Widget edit button** — Tap pencil button, verify opens widget editor
- [ ] **Widget filter criteria chip** — "Widget: <name>" chip in filter criteria box
- [ ] **Widget name in title** — Entry list title shows widget name when filter active
- [ ] **Clear widget filter** — Clear All removes widget filter
- [ ] **Widget date/text/array filters** — All filter types work correctly

### Backup Folder (Android)

- [ ] **Select folder** — SAF folder picker opens, selected folder name displays
- [ ] **Backup all data** — Creates JSON backup file in selected folder
- [ ] **Restore all data** — Lists backup files, restores selected backup
- [ ] **Clear folder** — Clears folder selection, releases URI permissions
- [ ] **Persist across restarts** — Folder URI persisted in SharedPreferences

### iCalendar Export

- [ ] **Export from Explorer** — iCalendar export button in results bar
- [ ] **Entry-based only** — Shows alert for non-entry raw SQL results
- [ ] **Valid .ics** — Valid iCalendar with VEVENT per entry

### Calendar View

- [x] **Monthly view** — Calendar grid, day numbers, entry count badges (verified 2026-04-06)
- [x] **Navigation** — Prev/next, Today button (verified 2026-04-06)
- [x] **Day selection** — Click day to show entries (verified 2026-04-06)
- [x] **Today highlight** — Current day accent border (verified 2026-04-06)
- [x] **Theme support** — Renders correctly across all 12 themes (verified 2026-04-06)

### Entry Locking

- [x] **Lock entry** — Lock button, confirm, lock icon changes (verified 2026-04-13)
- [x] **Edit disabled when locked** — Edit button grayed out (verified 2026-04-13)
- [x] **Unlock entry** — Unlock, confirm, Edit re-enables (verified 2026-04-13)
- [x] **Lock persists** — Navigate away and return, still locked (verified 2026-04-13)
- [x] **Schema migration** — Existing journals load with `locked` defaulting to false (verified 2026-04-13)

### Location Name Field

- [ ] **Add location with name** — GPS/search location, type custom name, stored as `{lat, lng, address, name}`
- [ ] **Display in viewer** — "Name — Address" for named locations, just address without
- [ ] **Search locations** — Entry list search matches location name text
- [ ] **Backwards compat** — Existing entries without name field display correctly

### Native Service Layer

- [ ] **BootstrapService** — SharedPreferences stores/retrieves values correctly
- [ ] **CryptoService** — Encrypt/decrypt strings and bytes
- [ ] **CryptoService setup** — `setupPassword`/`verifyPassword` work correctly
- [ ] **WeatherService** — City search returns results, `fetchCurrent` returns weather
- [ ] **DatabaseService** — Open/close SQLCipher DB, CRUD on all tables
- [ ] **DB entries** — getEntries, addEntry, updateEntry, deleteEntryById all work
- [ ] **DB categories/tags/icons/widgets** — All CRUD operations
- [ ] **DB settings** — getSettings/setSettings round-trip correctly
- [ ] **DB export/stats/raw SQL** — exportJSON, getDBStats, execRawSQL

### Prior Sessions (pre-UI overhaul, tested OK)

- [x] Core CRUD operations
- [x] Encryption/decryption cycle
- [x] Multi-journal management
- [x] Image attachments and lightbox
- [x] All 12 themes
- [x] Export/import (SQLite, JSON)
- [x] Reports (HTML, PDF, CSV)
- [x] Weather tracking
- [x] Location/geocoding
- [x] Metadata export/import

## Completed Features

### Core (v0.9.0 – v1.0.0)
- [x] Multi-journal support with per-journal passwords
- [x] AES-256-GCM encryption with PBKDF2 key derivation (100k iterations)
- [x] Journal entries: create, edit, delete, view
- [x] Image attachments: gallery picker, camera capture, base64 storage, lightbox viewer
- [x] Categories (single-select) and tags (multi-select) per entry
- [x] Location/place name with geocoding (Photon/Nominatim/Google)
- [x] Weather tracking via Open-Meteo API (GPS-based)
- [x] Dashboard with stats (total, week, month, year), pinned entries, recent entries
- [x] Entry list with search, date/category/tag filtering
- [x] 8 themes (Light, Dark, Ocean, Midnight, Forest, Amethyst, Aurora, Lavender)
- [x] Data export: encrypted SQLite, plain SQLite, JSON, CSV
- [x] Data import: SQLite, JSON
- [x] Settings: preferences, theme picker, category/tag editor
- [x] Reports: HTML with custom templates, PDF (jsPDF), CSV

### v1.1.0 (2026-03-20)
- [x] SQL Explorer: visual query builder + raw SQL text input
- [x] Print PDF from entry viewer
- [x] 4 new themes: Amethyst, Aurora, Lavender, Frost
- [x] Navbar icon buttons
- [x] Entry numbering in lists
- [x] Column layout toggle in entry list

### v1.2.0 (2026-03-25)
- [x] Custom Views: saved filter combinations with AND/OR/NOT logic, grouping, sorting
- [x] 3 new themes: Navy, Sunflower, Meadow (total: 12)
- [x] Pagination in entry list (10/20/50/100/all)
- [x] Card and list view toggle
- [x] Pre-fill templates (auto-fill date, time, title, content, categories, tags)
- [x] Batch deletion (multi-select mode)
- [x] CSV import with column mapping and duplicate detection
- [x] Pin to dashboard for custom views

### v1.3.1 (2026-03-27)
- [x] Pin/unpin entries, max pinned entries setting
- [x] Default entry view option
- [x] Viewer font customization (family + size)
- [x] Android swipe navigation in entry viewer
- [x] Collapsible filter fields
- [x] Date & time display format settings (6 date formats, 12h/24h time)
- [x] Entry viewer 3D icon buttons (gradient, shadow, hover/press effects)
- [x] Cleanup tool: find dateless entries + duplicate detection
- [x] Invalid date bug fix (returns empty string instead of "Invalid Date")
- [x] Biometric authentication (AndroidX BiometricPrompt)

### Post-v1.3.1 (2026-03-31)
- [x] Custom icons for tags/categories
- [x] Dashboard search with live results
- [x] Settings tabs (Preferences, Templates, Edit Metadata, Data Management)
- [x] Android navbar redesign: icon-only, two-row layout
- [x] Category and tag colors: color picker swatches, "Use colors" toggles
- [x] Metadata export/import (JSON — categories, tags, icons, settings, templates, views)
- [x] SQL Explorer: CSV export, raw SQL for any table, record detail overlay, custom view loader
- [x] Delete button tooltip in Explorer results

### v1.4.0 (2026-04-03, UI overhaul + component refactor)
- [x] Native LoginActivity: journal selector, password, biometric login
- [x] Native DashboardActivity: stats grid, pinned/recent entries, ranked panels
- [x] Styled Android drawables: buttons (primary, secondary, accent, delete, biometric), inputs, cards, spinners, search/stat/entry/ranked backgrounds
- [x] Android layout XMLs: login screen, dashboard, spinner items
- [x] Dashboard: refactored ranked panels, whole-word search toggle, ResultGrid
- [x] Explorer: replaced inline results with ResultGrid, RecordViewer
- [x] Simplified index.html: component container divs
- [x] Entry list: CollapsiblePanel for filters, ResultGrid, "Clear All" button
- [x] Entry form: quick-create buttons for Category, Tags
- [x] Dashboard 2x2 navigation grid
- [x] Removed theme cycle button from navbar
- [x] Updated app_info.xml: version 1.4.0

### Bug Fixes (2026-04-01)
- [x] Fix: web login screen showing after native Android login
- [x] Fix: biometric prompt appearing twice on fingerprint login
- [x] Fix: CSV import field mapping UI overflow on Android

### v1.4.0+ (2026-04-04, HD icons + UI consolidation)
- [x] HD icon support: 64x64 (chips/lists) and 128x128 (image buttons)
- [x] 3-tier icon fallback in RankedPanel card view
- [x] Data Management: consolidated export/import into dropdown+button rows
- [x] Fix: metadata import blank screen
- [x] Dashboard: weather info inset 3D styling, click navigates to Settings
- [x] Entry viewer: icon-only action buttons

### v1.4.0+ (2026-04-05, widgets + Bootstrap + descriptions)
- [x] Dashboard Widgets: configurable aggregate cards with filters and functions
- [x] Widget editor: tabbed UI with live preview, icon upload, color picker
- [x] Widget storage: new `widgets` DB table
- [x] Settings > Widgets tab
- [x] Bootstrap module: IndexedDB-backed key-value store replacing localStorage
- [x] Category and tag descriptions
- [x] Entity hints in entry form
- [x] iCalendar export from SQL Explorer
- [x] Backup folder (Android): SAF folder picker, backup/restore
- [x] All localStorage usage migrated to Bootstrap store

### v1.4.0+ (2026-04-05, Calendar View + Wallpaper)
- [x] Calendar View: monthly/weekly grid, navigation, day selection, badges, theme support
- [x] Wallpaper: browse, apply, preview, clear, persistence

### v1.5.0 (2026-04-06, hamburger menu + calendar redesign + rename)
- [x] Hamburger menu: navigation links moved to dropdown
- [x] New Entry button in navbar
- [x] Dashboard cleaned up
- [x] About menu item in hamburger menu
- [x] App renamed from "JOURNAL" to "My Journal"
- [x] Calendar View redesigned: Google Calendar-inspired
- [x] Fixed: new journal creation, nav-menu active state, null reference

### v1.5.0+ (2026-04-13, entry locking)
- [x] Entry locking: `locked` column, schema version 2
- [x] Lock/Unlock toggle with confirmation
- [x] Locked entries: Edit button disabled
- [x] Database migration: `upgradeSchema()`

### v1.5.0+ (2026-04-16, CSV mapping UI redesign)
- [x] CSV import mapping: full-screen modal overlay
- [x] Fixed `build.gradle` versionName/versionCode

### v1.5.0+ (2026-04-17–18, Kotlin migration)
- [x] Kotlin build support: `kotlin-gradle-plugin:1.9.22`, `kotlin-android` plugin
- [x] All 5 original activities converted from Java to Kotlin
- [x] Native CalendarActivity created in Kotlin
- [x] Zero Java source files remain

### v1.5.0+ (2026-04-25, location name + service layer)
- [x] Location name field: `{lat, lng, address, name}`
- [x] BootstrapService.kt — SharedPreferences wrapper
- [x] CryptoService.kt — AES-256-GCM + PBKDF2
- [x] WeatherService.kt — Open-Meteo HTTP client
- [x] DatabaseService.kt — SQLCipher encrypted DB (~35 CRUD methods)

### v1.5.0+ (2026-04-26, native activities)
- [x] SearchActivity.kt — Native search screen
- [x] SettingsActivity.kt (~2865 lines) — 5 tabs (now 7 tabs)
- [x] EntryListActivity.kt (~850 lines) — Full native entry list
- [x] EntryViewerActivity.kt — Font settings

### v1.5.0+ (2026-04-27, native Explorer + dashboard wiring)
- [x] ExplorerActivity.kt — Full native SQL Explorer
- [x] Widget card click-to-filter
- [x] Dashboard navigation fully wired

### v1.5.0+ (2026-04-28, all features native + WebView removal)
- [x] WidgetEditorActivity.kt — Widget editor with 3 tabs, live preview
- [x] CustomViewEditorActivity.kt — Custom view editor
- [x] ReportsActivity.kt — Native reports (HTML/PDF/CSV)
- [x] EntryFormActivity.kt (~1400 lines) — Native entry form
- [x] CsvMappingActivity.kt — CSV import mapping
- [x] **Removed MainActivity.kt** (1,267 lines) — WebView eliminated
- [x] **Created ServiceProvider.kt** — Singleton replacing `MainActivity.instance`
- [x] **Created DashboardDataBuilder.kt** — Native dashboard data computation
- [x] 14 activities, 4 services, ServiceProvider, DashboardDataBuilder — fully native

### v1.5.0+ (2026-04-28, UI polish + dashboard settings)
- [x] Fix: EntryViewer edit button not working
- [x] Dashboard ranked badges uniform
- [x] Dashboard search button redesign
- [x] Dashboard component settings (toggle/reorder 11 components)
- [x] Settings tab 3D icons
- [x] Widget icon fix + color picker dialog

### v1.5.0+ (2026-04-29, Today in History + themes + CSV fixes)
- [x] Fix: EntryFormActivity edit not loading existing data
- [x] Today in History dashboard component
- [x] Runtime theme system (ThemeManager.kt, 12 themes)
- [x] 350 ContextCompat.getColor() calls replaced with ThemeManager.color()
- [x] Fix: CSV import date/time parsing regex
- [x] Restored "Use space to separate tags" option

### v1.5.0+ (2026-04-29, UI improvements)
- [x] EntryFormActivity: Content input enlarged (minLines 10, maxLines 20)
- [x] EntryFormActivity: All action buttons (Save, Cancel, Delete) moved to top navbar row alongside Back button
- [x] EntryFormActivity: Bottom action bar removed — more vertical space for content
- [x] ReportsActivity: HTML output area enlarged (400dp → 600dp)
- [x] ReportsActivity: HTML button now exports file to Downloads and opens in device browser via ACTION_VIEW intent
- [x] EntryListActivity: Collapsible "Filter Info" box wrapping search bar, category/tag filter spinners, clear button, select mode, page size spinner
- [x] EntryListActivity: Alternating row colors (CARD_BG / INPUT_BG) with CARD_BORDER stroke for entry cards
- [x] EntryListActivity: "Order by" dropdowns (field selector + asc/desc direction) in Filter Info box, persisted to BootstrapService
- [x] DashboardActivity: Bottom navigation bar removed, replaced with ☰ hamburger PopupMenu in top navbar
- [x] DashboardActivity: Menu items: Entries, Calendar, Reports, Explorer, Settings, About
- [x] SettingsActivity: Tab bar changed from horizontal scroll to 2-row grid (3 tabs per row, equal-width)
- [x] DashboardDataBuilder: Recent entries sorted by date descending with time tiebreaker
- [x] File cleanup: COMPONENTS.md deleted; KT_TODO.md + TODO.md + TO_TEST.md combined into TO_DO.md

### v1.5.0+ (2026-04-29, CSV mapping test preview)
- [x] CsvMappingActivity: "Select CSV" button — file picker to load CSV, auto-maps headers to entry fields (preserves saved mapping)
- [x] CsvMappingActivity: "Test" button — imports 20 random rows from selected CSV, applies current mapping and date/time parsing
- [x] CsvMappingActivity: Result grid — horizontally scrollable table with mapped column headers, alternating row colors, "Test Preview — N of M rows" label
- [x] CsvMappingActivity: Bottom bar redesigned — 3 buttons (Select CSV | Test | Save Mapping)

### v1.5.0+ (2026-04-30, cleanup: people, rich content, web SPA removal)
- [x] Removed people feature entirely: people table, people column from entries, people CRUD in DatabaseService, people section in EntryFormActivity/SettingsActivity/EntryViewerActivity, people ranked panel in Dashboard, people from CSV import/export, people from widget/view filter fields
- [x] Dashboard auto-refresh: `DashboardActivity.needsRefresh` flag checked in `onResume()`
- [x] Erase all entries sets refresh flag so dashboard updates on return
- [x] CSV import completion now shows AlertDialog with import count; OK closes Settings and refreshes dashboard
- [x] Removed rich content / Quill.js editor: RichEditorActivity, rich_editor.html, richContent column, all related code (15→14 activities, 22→21 Kotlin files)
- [x] Removed entire `/web/` directory (24 files) — browser-only SPA fallback no longer part of project

### v1.5.0+ (2026-04-30, date/time format + widget refresh)
- [x] Date/time format settings: replaced key-based system (short/long/12h/24h) with SimpleDateFormat pattern strings
- [x] 6 date formats: MMMM d, yyyy | MMM d, yyyy | yyyy-MM-dd | MM/dd/yyyy | dd/MM/yyyy | EEE, MMM d, yyyy
- [x] 2 time formats: h:mm a (12h) | HH:mm (24h)
- [x] Format applied in 6 activities: SettingsActivity, EntryListActivity, ReportsActivity, EntryViewerActivity, DashboardActivity, SearchActivity
- [x] Dashboard refreshes after widget save or delete (WidgetEditorActivity sets needsRefresh flag)

### v1.5.0+ (2026-04-30, Display Preferences + Inspiration + UI)
- [x] New "Display Preferences" settings tab: theme picker, entry viewer font (family + size with preview), alternate row background color picker
- [x] Theme and font settings moved from Preferences tab to Display tab
- [x] Wallpaper feature removed (browse/preview/clear)
- [x] Alternate row background color: configurable via color picker with 28 presets + hex input, applied in EntryListActivity
- [x] Category icon editing restored: Change Icon / Remove buttons in edit category dialog, saves 64px + 128px icons
- [x] Top Categories card/list view toggle: ☰/▦ icon in panel header, card view shows 3-column grid with category icons
- [x] Inspiration table: new DB table (id, quote, source) with CRUD methods + getRandomInspiration()
- [x] Daily Inspiration dashboard panel: random quote in serif italic, source attribution, refresh button
- [x] Inspiration quotes management: Settings > Metadata > Inspiration Quotes (add/edit/delete)
- [x] Dashboard components increased from 10 to 11 (added Daily Inspiration)
- [x] Settings tabs increased from 6 to 7 (added Display), grid now 3 rows
- [x] Daily Inspiration panel: always visible (empty state shows "No quotes yet" prompt), ✏️ edit button deep-links to Settings > Metadata
- [x] Settings deep-link: `SettingsActivity.initialTab` allows opening to a specific tab
- [x] Collapsible Categories and Tags lists in Metadata tab, state persisted in BootstrapService

### v1.5.0+ (2026-05-01, App Font customization + icon refresh fix)
- [x] App Font settings in Display tab: font family (9 options: System, Sans Serif, Serif, Monospace, etc.) and font size scale (Small/Medium Small/Default/Large/X-Large/XX-Large)
- [x] Font scale uses Android `Configuration.fontScale` via `attachBaseContext` — all SP-based text sizes scale automatically across all 13 post-login activities
- [x] Font family applied via `ThemeManager.applyTypefaceToViewTree()` — walks view tree to set typeface on all TextViews, including dynamically created views
- [x] Live preview in Display tab shows selected font family and size
- [x] Apply button recreates activity to apply font scale changes
- [x] Settings stored in BootstrapService: `ui_font_family`, `ui_font_scale`
- [x] `ThemeManager.loadFontSettings()` reads font preferences, called during `init()` and on Display tab changes
- [x] Fix: Dashboard not refreshing after category icon change — `DashboardActivity.needsRefresh` now set in `handleIconResult()` and icon remove handler

### v1.5.0+ (2026-05-02, SQL Library + auto GPS & weather)
- [x] SQL Library: new `sql_library` table (id, name, description, sql_statement, dtCreated, dtUpdated)
- [x] DatabaseService CRUD: getSqlLibrary, addSqlLibraryEntry, updateSqlLibraryEntry, deleteSqlLibraryEntry
- [x] SQL Explorer Save button: prompts for name/description, captures current SQL (from input or builder conditions)
- [x] SQL Explorer Library button: dialog showing all saved queries as cards with Load/Edit/Delete actions
- [x] Table included in schema creation, upgrade, and data migration

### v1.5.0+ (2026-05-02, auto GPS & weather on new entry)
- [x] Auto populate GPS & weather setting: toggle in Settings > Preferences (`auto_gps_weather` in BootstrapService)
- [x] EntryFormActivity auto-populates GPS location and weather on new entry when setting enabled
- [x] Silently skips if location permission not granted or GPS provider disabled (no prompts)

### v1.5.0+ (2026-05-01, dashboard category icons + settings refresh)
- [x] Category icons in dashboard ranked panel: Top Categories rows now show 24x24 category icons inline (base64 PNG decoded from icons table)
- [x] Fix: Dashboard not refreshing after dashboard component settings change — toggle, move up, move down actions in Settings > Dashboard tab now set `DashboardActivity.needsRefresh = true`

### v1.6.0 (2026-05-02, search highlights + UI polish)
- [x] Fix: SQL Explorer Library load crash — `findViewById` resolved to Button scope instead of Activity, causing NPE on Load
- [x] Search term highlighting: matching terms highlighted with semi-transparent accent background in title and content snippet
- [x] Collapsible Templates tab: Custom Views, Pre-fill Templates, and Report Templates sections are now collapsible with persisted state in BootstrapService
- [x] Daily Inspiration decorative panel: double accent border with layered insets, 3D drop shadow via LayerDrawable + Android elevation

### v1.7.0 (2026-05-03, collapsible dashboard panels + entry refresh fix)
- [x] Collapsible dashboard panels: Recent Entries, Top Tags, Top Categories, Top Places, Daily Inspiration — ▶/▼ toggle headers with collapse state persisted in BootstrapService (`dash_*_collapsed` keys)
- [x] Shared `setupCollapsibleHeader()` helper in DashboardActivity for consistent collapsible behavior
- [x] Fix: Dashboard not refreshing after entry save/delete — `EntryFormActivity` now sets `DashboardActivity.needsRefresh = true` in both `saveEntry()` and `confirmDelete()`

## Kotlin Migration (complete)

All original Java activities were converted to Kotlin (2026-04-17/18). MainActivity was later removed entirely when WebView was eliminated (2026-04-28). The app is now 100% Kotlin with 21 source files: 14 activities + 4 services + ServiceProvider + DashboardDataBuilder + ThemeManager.

### Service Layer

| Service | File | Purpose |
|---------|------|---------|
| BootstrapService | BootstrapService.kt | SharedPreferences wrapper |
| CryptoService | CryptoService.kt | AES-256-GCM + PBKDF2 via javax.crypto |
| WeatherService | WeatherService.kt | Open-Meteo HTTP client |
| DatabaseService | DatabaseService.kt | SQLCipher encrypted DB (~35 CRUD methods) |

### Key Dependencies
- `net.zetetic:sqlcipher-android:4.5.6` + `androidx.sqlite:sqlite:2.4.0`
- All sources in `java/com/journal/app/` (no separate `kotlin/` source set)
- Release APK ~25MB (SQLCipher native libraries for multiple ABIs)
