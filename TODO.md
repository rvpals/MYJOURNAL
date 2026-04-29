---
tags: [todo, backlog, changelog, version-history, completed-features, planned-features]
related_files: [REQUIREMENT.md, ISSUES.md, TO_TEST.md]
summary: "Version-by-version feature completion history (v0.9–v1.5) and remaining backlog items."
---

# TODO — MYJOURNAL

## Completed Features

### Core (v0.9.0 – v1.0.0)
- [x] Multi-journal support with per-journal passwords
- [x] AES-256-GCM encryption with PBKDF2 key derivation (100k iterations)
- [x] sql.js (SQLite WASM) database with IndexedDB persistence
- [x] Journal entries: create, edit, delete, view
- [x] Rich text editing (Quill.js) with image support
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

### Post-v1.3.1 (2026-03-31 session, in older JOURNAL repo)
- [x] People feature: first/last name, description, multi-select per entry
- [x] Custom icons for tags/categories
- [x] Dashboard search with live results
- [x] Settings tabs (Preferences, Templates, Edit Metadata, Data Management)
- [x] Android navbar redesign: icon-only, two-row layout
- [x] Separated CSS: style.css (web) + style-android.css (Android-only)
- [x] Category and tag colors: color picker swatches, "Use colors" toggles
- [x] Metadata export/import (JSON — categories, tags, people, icons, settings, templates, views)
- [x] SQL Explorer: CSV export, raw SQL for any table, record detail overlay, custom view loader
- [x] Delete button tooltip in Explorer results

### v1.4.0 (2026-04-03, UI overhaul + component refactor)
- [x] Native LoginActivity: journal selector, password, biometric login
- [x] Native DashboardActivity: stats grid, pinned/recent entries, ranked panels
- [x] Styled Android drawables: buttons (primary, secondary, accent, delete, biometric), inputs, cards, spinners, search/stat/entry/ranked backgrounds
- [x] Android layout XMLs: login screen, dashboard, spinner items
- [x] Web crypto.js: sync hooks for native SharedPreferences
- [x] Web dashboard.js: getDashboardDataJSON() with streak for native dashboard
- [x] Web db.js: journal list sync updates
- [x] Reusable components.js: ResultGrid (data table), RankedPanel (ranked list/card with view toggle + show all), RecordViewer (detail overlay with prev/next)
- [x] Dashboard: refactored ranked panels to use RankedPanel component, added whole-word search toggle, replaced inline search results with ResultGrid
- [x] Explorer: replaced inline results table with ResultGrid, replaced detail overlay with RecordViewer
- [x] Simplified index.html: removed hardcoded panel/table markup, replaced with component container divs
- [x] CollapsiblePanel component: reusable collapsible container with pin-to-expand, localStorage persistence
- [x] Entry list: search & filter controls wrapped in CollapsiblePanel, old filter-toggle removed
- [x] Entry list: table/list view replaced with ResultGrid component (search term highlighting, dynamic columns)
- [x] Entry list: "Clear All" button in filter criteria bar when filters are active
- [x] Entry form: quick-create buttons (+ New) for Category, Tags, and People with inline create panels
- [x] Removed old filter-toggle-header/filter-fields-body CSS from style.css and style-android.css
- [x] Dashboard 2x2 navigation grid: New Entry, Entry List, Report, SQL Explorer (3D styled buttons)
- [x] Removed theme cycle button from navbar
- [x] Moved Entry List, Report, SQL Explorer links from navbar to dashboard
- [x] Updated app_info.xml: name "My Journal", version 1.4.0, added v1.4.0 and v1.3.1 changelogs

### Bug Fixes (2026-04-01)
- [x] Fix: web login screen showing after native Android login — pass crypto keys via Intent extras, sync to localStorage in performAutoLogin()
- [x] Fix: biometric prompt appearing twice on fingerprint login — check hasNativeLogin() in onJournalSelect()
- [x] Fix: CSV import field mapping UI overflow on Android — reduce min-widths, add flex/overflow-x to container

### v1.4.0+ (2026-04-04, HD icons + documentation)
- [x] HD icon support: upload stores both 64x64 (chips/lists) and 128x128 (image buttons) when source >=96px
- [x] 3-tier icon fallback in RankedPanel card view: HD image button → icon+text → text abbreviation
- [x] `.ranked-btn-img` CSS: full-image 3D buttons with cover fit, depth shadow, hover lift, press inset
- [x] `getIconHD()` helper for HD icon retrieval via `type_hd` convention
- [x] `clearIcon()` now removes both standard and HD icon variants
- [x] Documentation: created `index.md` central navigation map with semantic summaries, context boundaries, quick-lookup table
- [x] Documentation: added YAML frontmatter (tags, related_files, summary) to all 7 .md files

### v1.4.0+ (2026-04-04, UI consolidation + fixes)
- [x] Data Management: consolidated export/import buttons into dropdown+button rows (Export dropdown + Import dropdown)
- [x] Data Management: moved Export/Import Metadata from Edit Metadata tab into Data Management dropdowns
- [x] Data Management: removed Cleanup Dateless button and `cleanupDatelessEntries()` function
- [x] Data Management: added icons to Export (📤) and Import (📥) buttons
- [x] Fix: metadata import blank screen — replaced `window.location.reload()` with in-place `refreshSettings()` (WebView file:// reload crashes)
- [x] Dashboard: weather info styled with inset 3D look (inset box-shadow, bg-secondary, border)
- [x] Dashboard: clicking weather info navigates to Settings > Preferences > Weather Location
- [x] Entry viewer: removed text labels from Edit/Print/Delete/Back buttons, icon-only with title tooltips

### v1.4.0+ (2026-04-05, widgets + Bootstrap + descriptions)
- [x] Dashboard Widgets: configurable aggregate cards with filters (entry criteria) and functions (Count, Sum, Max, Min, Average)
- [x] Widget editor: tabbed UI (Header Info, Filter, Functions) with live preview, custom icon upload, background color picker
- [x] Widget storage: new `widgets` DB table with filters/functions as JSON, enabledInDashboard toggle, sort order
- [x] Settings > Widgets tab: widget list, create/edit/delete widgets
- [x] Bootstrap module (`bootstrap.js`): IndexedDB-backed key-value store replacing all localStorage usage
- [x] Bootstrap: synchronous in-memory cache with async IDB persistence, auto-migration from localStorage
- [x] Category descriptions: added `description` column to categories table, editable in Settings > Edit Metadata
- [x] Tag descriptions: new `tags` table (name PK, description), editable in Settings > Edit Metadata
- [x] Entity hints: category, tag, and people descriptions shown as hints below selectors in entry form
- [x] Tag dropdown: descriptions shown inline in autocomplete suggestions
- [x] iCalendar export: SQL Explorer results exportable as .ics files (VEVENT per entry with date/time/title/location)
- [x] Backup folder (Android): SAF folder picker for selecting persistent backup/restore directory
- [x] Backup/Restore: full data backup to JSON file in selected folder, restore from backup files
- [x] All localStorage usage migrated to Bootstrap store (crypto.js, app.js, db.js, settings.js, components.js)
- [x] Metadata export/import updated: includes category descriptions, tag descriptions, and widgets

### v1.4.0+ (2026-04-05, Calendar View + Wallpaper)
- [x] Calendar View: monthly calendar grid (Mon-Sun) with entry count badges per day
- [x] Calendar View: weekly view with 7-column grid showing entry titles per day
- [x] Calendar View: month/week navigation (prev/next buttons), "Today" button
- [x] Calendar View: go-to-date text input (YYYY-MM-DD) for jumping to specific dates
- [x] Calendar View: day selection shows entries below calendar via ResultGrid component
- [x] Calendar View: color-coded badges (green=1, blue=2-3, red=4+ entries)
- [x] Calendar View: today highlighting with accent border, selected day highlight
- [x] Calendar View: "CALENDAR" button added to dashboard navigation grid
- [x] Calendar View: responsive layout with mobile breakpoints
- [x] Calendar View: full theme support via CSS variables
- [x] Wallpaper: browse and select background image in Settings > Preferences
- [x] Wallpaper: applied as background on dashboard and entry viewer pages
- [x] Wallpaper: semi-transparent overlay (55% opacity) for text readability
- [x] Wallpaper: image resized to max 1920px, JPEG compressed (85%), stored encrypted in DB settings
- [x] Wallpaper: preview thumbnail and clear button in settings UI

### v1.5.0 (2026-04-06, hamburger menu + calendar redesign + rename)
- [x] Hamburger menu: navigation links moved from navbar to dropdown menu (Entry List, Report, SQL Explorer, Calendar, Settings)
- [x] New Entry button (📝) added to navbar for quick access
- [x] Dashboard cleaned up: navigation buttons removed (now accessible via hamburger menu)
- [x] About menu item added to hamburger menu (between Settings and Lock)
- [x] About button removed from Settings page header
- [x] Journal button (navbar brand) fills available screen width up to new entry/hamburger buttons with text ellipsis for long names
- [x] App renamed from "JOURNAL" to "My Journal" across all surfaces (Android app name, login screen, dashboard header, navbar, page title)
- [x] Calendar View redesigned: Google Calendar-inspired modern look with table gridlines
- [x] Calendar month view: entry title chips in day cells, today circle indicator
- [x] Calendar week view: horizontal day rows with time and entry title chips
- [x] Calendar toolbar: single-line layout with nav arrows, Today, Goto, and Month/Week toggle
- [x] Calendar Goto: collapsible panel with date picker, text input (mm/dd/yyyy), and Prev/Next Event jump buttons
- [x] Backup/Restore buttons styled consistently on Data Management tab
- [x] Fixed: new journal creation failing due to categories INSERT missing column name
- [x] Fixed: nav-menu active state highlighting for current page
- [x] Fixed: null reference when `.nav-links` element doesn't exist

### v1.5.0+ (2026-04-13, entry locking)
- [x] Entry locking: `locked` column (INTEGER DEFAULT 0) added to entries table, schema version bumped to 2
- [x] Lock/Unlock toggle button in entry viewer bottom bar with confirmation prompt ("Are you sure you want to lock/unlock this entry?")
- [x] Locked entries: Edit button disabled (grayed out, non-clickable) to prevent accidental edits
- [x] Lock icon reflects state: locked (closed lock) / unlocked (open lock) with appropriate tooltip
- [x] Database migration: `upgradeSchema()` adds `locked` column to existing databases

### v1.5.0+ (2026-04-16, CSV mapping UI redesign)
- [x] CSV import mapping moved to full-screen modal overlay (`position: fixed`, `z-index: 200`) outside settings container hierarchy
- [x] Modal follows existing print/about modal pattern: centered, scrollable (`max-height: 90vh`), themed background
- [x] Each CSV column rendered as `form-group` (label + full-width select dropdown), all fully visible and selectable
- [x] Removed all custom CSV mapping CSS (`.csv-mapping-row`, `.csv-arrow`, `.csv-import-section` inline styles)
- [x] HTML moved from inline settings fieldset to `<body>`-level `#csv-import-modal` div
- [x] Fixed `build.gradle` versionName "1.0a" → "1.5.0", versionCode 1 → 6

### v1.5.0+ (2026-04-17, Kotlin migration + Copy Content + native calendar)
- [x] Kotlin build support: added `kotlin-gradle-plugin:1.9.22`, `kotlin-android` plugin, `kotlinOptions`, `kotlin-stdlib` dependency
- [x] AboutActivity converted from Java to Kotlin (idiomatic `apply` scoping, string templates, property access)
- [x] Native CalendarActivity (Kotlin): monthly grid view with day cells, entry count dots, today/selected highlighting, day selection with entry listing
- [x] CalendarActivity layout: navbar (back/today), month navigation (prev/next), weekday header, calendar grid, results panel with entry rows
- [x] Calendar drawables: `cal_day_bg.xml`, `cal_today_bg.xml`, `cal_selected_bg.xml`
- [x] Entry form: "Copy" button next to Rich Content label copies plain text Content into Quill rich text editor
- [x] CSS: `.btn-copy-content` styled as small inline button matching theme variables
- [x] DashboardActivity converted from Java to Kotlin (idiomatic `apply` scoping, named params, `lateinit`, string templates)

### v1.5.0+ (2026-04-18, full Kotlin migration)
- [x] LoginActivity converted from Java to Kotlin: `lateinit var` bindings, `data class JournalInfo`, `mutableListOf`, string templates, lambda listeners, idiomatic null handling
- [x] MainActivity converted from Java to Kotlin: `inner class AndroidBridge` with all 20+ `@JavascriptInterface` methods, `buildString` for JS injection, raw string literals for JS blocks, `when` expressions, `.use {}` for stream auto-close
- [x] All 5 activities now 100% Kotlin — zero Java source files remain
- [x] Old `.java` files removed from repository

### v1.5.0+ (2026-04-25, location name + service layer migration)
- [x] Location name field: locations now store `{lat, lng, address, name}` — user can type a custom name for each location
- [x] Location name displayed in entry viewer as "Name — Address", search includes location names
- [x] Location name included in Explorer display/filter, Reports exports, CSV import
- [x] Backwards-compatible: existing locations without `name` field continue to work via `migrateLocations()`
- [x] **BootstrapService.kt** — SharedPreferences wrapper replacing IndexedDB bootstrap store on Android
- [x] **CryptoService.kt** — Standalone AES-256-GCM + PBKDF2 service (extracted from LoginActivity)
- [x] **WeatherService.kt** — HTTP calls to Open-Meteo API via `java.net.HttpURLConnection`
- [x] **DatabaseService.kt** — Full SQLCipher encrypted SQLite database service (~35 CRUD methods)

### v1.5.0+ (2026-04-26, native SearchActivity + Settings + Entry List + Entry Viewer)
- [x] Dashboard search moved from inline fieldset to separate screen: search icon button opens search
- [x] **SearchActivity.kt** — Native Kotlin search screen with text input, whole-word checkbox, search/clear buttons, scrollable results
- [x] **SettingsActivity.kt** (~2865 lines) — Full native settings with 5 tabs
- [x] **EntryListActivity.kt** (~850 lines) — Full native entry list with search, filter, sort, paginate, batch delete
- [x] **EntryViewerActivity.kt** — Font settings applied, rich content via Html.fromHtml() + TextView

### v1.5.0+ (2026-04-27, native Explorer + widgets + dashboard wiring)
- [x] **ExplorerActivity.kt** — Full native SQL Explorer (table browser, query builder, raw SQL, results, CSV/iCal export)
- [x] Widget card click shows filtered entries (not widget editor); pencil edit button for editor access
- [x] Native DashboardActivity wired up with all navigation (stats, ranked panels, widgets, entries)
- [x] Dashboard stat cards and ranked panel clicks open EntryListActivity with proper filters

### v1.5.0+ (2026-04-28, all features native + widget/view editors + reports + entry form)
- [x] **WidgetEditorActivity.kt** — Full native widget editor with 3 tabs, live preview
- [x] **CustomViewEditorActivity.kt** — Full native custom view editor with conditions, groupBy, orderBy
- [x] **ReportsActivity.kt** — Native reports with HTML/PDF/CSV, filters, templates
- [x] **EntryFormActivity.kt** (~1400 lines) — Native entry form with rich text (Spannable), images, categories, tags, people, locations, weather
- [x] **CsvMappingActivity.kt** — Native CSV import column mapping screen
- [x] All 15 activities fully native Kotlin (at this point, still with WebView/MainActivity)

### v1.5.0+ (2026-04-28, WebView removal — fully native architecture)
- [x] **Removed MainActivity.kt** (1,267 lines) — WebView container + AndroidBridge JS interface eliminated
- [x] **Removed app/src/main/assets/web/** — Web SPA assets no longer bundled in APK
- [x] **Removed activity_main.xml** — WebView layout deleted
- [x] **Created ServiceProvider.kt** — Singleton service holder replacing `MainActivity.instance`
- [x] **Created DashboardDataBuilder.kt** — Computes dashboard JSON natively from DatabaseService (stats, streaks, ranked lists, widgets, pinned/recent)
- [x] **LoginActivity.kt** — Now initializes ServiceProvider, opens DB, computes dashboard data, launches DashboardActivity directly (no WebView intermediary)
- [x] **DashboardActivity.kt** — All `MainActivity.instance?.xxxService` replaced with `ServiceProvider.xxxService`; lock returns to LoginActivity directly
- [x] **SearchActivity.kt** — Queries DatabaseService directly via ServiceProvider (no more pendingData from WebView)
- [x] **EntryViewerActivity.kt** — Rich content via Html.fromHtml() + TextView (no WebView)
- [x] **ReportsActivity.kt** — HTML output via HorizontalScrollView + TextView (no WebView)
- [x] **build.gradle** — Removed `copyWebAssets` task and `androidx.webkit:webkit:1.9.0` dependency
- [x] **AndroidManifest.xml** — 14 activities (removed MainActivity entry)
- [x] 20 Kotlin source files: 14 activities + 4 services + ServiceProvider + DashboardDataBuilder

### v1.5.0+ (2026-04-28, UI polish + dashboard settings)
- [x] **Fix: EntryViewer edit button not working** — `bootstrapService` was null when passed to EntryFormActivity (nulled in `onCreate`); now uses `ServiceProvider.bootstrapService`
- [x] **Dashboard ranked badges uniform** — all ranks now get accent background (not just top 3), alternating row backgrounds for readability
- [x] **Dashboard search button redesign** — moved from standalone full-width bar below navbar to compact 3D icon button (🔍) in the navbar row, left of journal name
- [x] **Dashboard Top Tags/Categories captions** — center-aligned text
- [x] **Dashboard component settings** — new "Dashboard" tab in Settings to toggle components on/off and reorder via ▲/▼ arrows; config stored in BootstrapService as `dashboard_components` JSON
- [x] **Dashboard respects component settings** — `applyDashboardComponentSettings()` reads saved order/visibility, removes and re-adds panels accordingly
- [x] **Stats grid wrapper** — wrapped 2x2 stats rows in `stats_container` LinearLayout for reordering as a unit
- [x] **Settings tab 3D icons** — emoji icons (⚙️📝🏷️💾🧩📊) stacked above labels, 3D active/inactive drawables with raised look and shadow
- [x] **Dashboard ranked badges 3D** — rank badge uses vertical gradient (cyan→accent→dark teal) with rounded corners + elevation shadow; count badge uses dark gradient with rounded corners + shadow
- [x] **New drawables** — `btn_search_3d.xml`, `tab_active_3d.xml`, `tab_inactive_3d.xml`

### v1.5.0+ (2026-04-28, widget icon fix + color picker)
- [x] **Fix: Widget icon not showing on dashboard** — `DashboardActivity.createWidgetCard()` never read the widget `icon` field; added `ImageView` with `loadBase64Image()` to render 40x40 icon in widget card header
- [x] **Widget editor color picker dialog** — Tapping the color swatch or hex input in widget editor now opens a color picker dialog with 24 preset color grid, hex input field, and live preview swatch

### v1.5.0+ (2026-04-29, Today in History + edit fix)
- [x] **Fix: EntryFormActivity edit not loading existing data** — `loadEntryData()` loaded metadata (categories, tags, people, etc.) but never set `dateValue`, `timeValue`, `titleValue`, `contentValue`. Editing an entry opened a blank form. Added four missing field assignments.
- [x] **Today in History dashboard component** — New dashboard panel showing entries from the same month/day in past years. Displays entry title, 20-character content preview, and "N year(s) ago" badge. Entries are clickable to open in EntryViewerActivity.
- [x] **DashboardDataBuilder.buildTodayInHistory()** — Matches entries by MM-dd against today's date, excludes current year, sorted by most recent year first.
- [x] **Dashboard component settings** — `today_history` added to Settings > Dashboard tab for toggle/reorder (11 components total).

### v1.5.0+ (2026-04-29, runtime themes + CSV fixes)
- [x] **Runtime theme system** — New `ThemeManager.kt` singleton with all 12 theme color maps (transcribed from web CSS). `ThemeManager.applyToActivity()` sets status/nav bar colors, walks the view tree to recolor XML-set backgrounds (`ColorDrawable` and `GradientDrawable`) and text colors. Light/dark status bar icons set automatically based on theme luminance.
- [x] **Theme switching works** — Selecting a theme in Settings > Preferences now immediately applies: `ThemeManager.setTheme()` + `recreate()`. All other activities detect theme changes via `themeVersion` counter in `onResume` and rebuild/finish accordingly.
- [x] **350 ContextCompat.getColor() calls replaced** — All 12 activity files migrated from static `R.color.login_*` XML resources to dynamic `ThemeManager.color(C.*)` calls.
- [x] **Fix: CSV import date/time parsing regex syntax error** — `parseDateWithFormat()` and `parseTimeWithFormat()` used chained `.replace()` where single-char tokens (`M`, `D`, `H`) corrupted named groups already inserted by double-char tokens (`MM`, `DD`, `HH`). Rewritten to use single-pass `Regex("YYYY|YY|MM|DD|M|D").replace()` with lambda.
- [x] **Restored "Use space to separate tags" option** — Checkbox added to CsvMappingActivity Import Options, saved as `csvTagsSpaceSep` in settings DB, used in `handleCsvImport()` to split tags by whitespace when enabled (matching web version behavior).

## Remaining / TODO

- [ ] Re-test all features after WebView removal (all native screens)
- [ ] Web app: HTTPS requirement for Web Crypto API (browser-only fallback)
- [ ] Entry image thumbnails could be optimized (currently full base64 stored)
- [ ] Consider lazy loading for large journal databases (1000+ entries)
- [ ] Password strength indicator on journal creation
- [ ] Entry search: full-text search across rich content (currently plain text only)
- [ ] Accessibility audit (screen reader support, keyboard navigation)
- [ ] Localization / i18n support
