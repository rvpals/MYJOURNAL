---
tags: [bugs, issues, limitations, fixed-bugs, encryption, known-problems]
related_files: [TO_TEST.md, TODO.md, CLAUDE.md]
summary: "Known bugs, platform limitations, fixed issues, and architectural notes for the fully native Android app."
---

# ISSUES — MYJOURNAL

## Known Issues

- **Large journals may be slow** — entire SQLite DB operations are synchronous; no lazy loading for 1000+ entries
- **Image storage is inefficient** — full base64 images stored in SQLite; no server-side or file-system storage option

## Fixed (2026-04-29, session 2)

- ~~Theme selection in Settings does nothing~~ **FIXED** — Colors were hardcoded as static XML resources (`R.color.login_*`) matching only the "dark" theme. Created `ThemeManager.kt` singleton with all 12 theme color maps, replaced 350 `ContextCompat.getColor()` calls with `ThemeManager.color(C.*)`, added `applyToActivity()` that walks the view tree to recolor XML-set backgrounds and text colors. Theme changes now apply immediately via `recreate()`.
- ~~CSV import fails with regex syntax error~~ **FIXED** — `parseDateWithFormat()` used chained `.replace()` where `replace("M", ...)` corrupted the `(?<M>` named group already inserted by `replace("MM", ...)`. Rewritten as single-pass `Regex("YYYY|YY|MM|DD|M|D").replace()` with lambda to avoid collision. Same fix applied to `parseTimeWithFormat()`.
- ~~"Use space to separate tags" option missing from Kotlin CSV mapping~~ **FIXED** — The web version had a `csv-tags-space-sep` checkbox but it was lost during Kotlin conversion. Added checkbox to CsvMappingActivity Import Options, saved as `csvTagsSpaceSep` in settings DB, used in import logic.

## Fixed (2026-04-29)

- ~~EntryFormActivity edit opens blank form~~ **FIXED** — `loadEntryData()` populated metadata fields (categories, tags, people, locations, images, weather, placeName) but never set `dateValue`, `timeValue`, `titleValue`, or `contentValue`. The `buildMainTab()` method reads these variables to fill inputs, so they were always empty when editing. Added four missing assignments at the top of `loadEntryData()`.

## Fixed (2026-04-28, session 3)

- ~~Widget icon not showing on dashboard badge~~ **FIXED** — `DashboardActivity.createWidgetCard()` never read the `icon` field from widget JSON. Added `ImageView` with `loadBase64Image()` to display 40x40 icon in widget card header, matching the preview layout in `WidgetEditorActivity`.
- ~~No color picker in widget editor~~ **FIXED** — Background color field only had a text input and static swatch with no picker. Added `showColorPickerDialog()` with 24 preset colors, hex input, and live preview. Swatch and hex input both open the picker on tap.

## Fixed (2026-04-28, session 2)

- ~~EntryViewer Edit button does nothing~~ **FIXED** — `editEntry()` passed null `bootstrapService` to EntryFormActivity (companion field was nulled in `onCreate`). EntryFormActivity received null and immediately called `finish()`. Fix: use `ServiceProvider.bootstrapService` instead.

## Fixed (2026-04-28)

- ~~Dashboard ranked panels (Top Tags/Categories/Places/People) don't filter entry list on Android~~ **FIXED** — `navigateToFilteredList()` called `navigateTo('entry-list')` which on Android intercepted to `AndroidBridge.openEntryList()` (no filters). Now constructs widget-format filter JSON and calls `openEntryListWithWidgetFilter()` directly.
- ~~Dashboard stat cards (This Week/Month/Year) don't filter entry list on Android~~ **FIXED** — `statNavigate()` had the same interception problem. Now constructs date `between` filter and calls `openEntryListWithWidgetFilter()`.
- ~~Native DashboardActivity ranked panel rows open entry list without filters~~ **FIXED** — `createRankedRow()` now constructs proper filter JSON per field type and launches EntryListActivity with filters via `openFilteredEntryList()`.
- ~~Native DashboardActivity not wired up~~ **FIXED** — `onDashboardReady()` bridge method was empty. Now launches DashboardActivity via ActivityResultLauncher with `pendingData` pattern.

## Fixed (2026-04-26)

- ~~SearchActivity crashes on launch (TransactionTooLargeException)~~ **FIXED** — `openSearch()` passed full entries JSON via `Intent.putExtra()`, exceeding Android's ~1MB Binder transaction limit. Replaced with static `companion object` data holder.

## Fixed (2026-04-16)

- ~~CSV import mapping UI unusable — columns cut off with no scrollbar~~ **FIXED** — moved CSV mapping to full-screen modal overlay
- ~~build.gradle versionName stuck at "1.0a"~~ **FIXED** — updated to "1.5.0", versionCode to 6

## Fixed (2026-04-06)

- ~~New journal creation fails due to categories INSERT missing column name~~ **FIXED** — `INSERT INTO categories VALUES (?)` fails when table has `name` + `description` columns; changed to `INSERT INTO categories (name) VALUES (?)`
- ~~Null reference when `.nav-links` element doesn't exist~~ **FIXED** — added null check before calling `classList.remove('open')` in `navigateTo()`

## Fixed (2026-04-04)

- ~~Metadata import shows blank screen after "app will refresh" message~~ **FIXED** — `window.location.reload()` fails in WebView file:// protocol; replaced with in-place `refreshSettings()` + theme reapply + `navigateTo('settings')`

## Fixed (2026-04-01)

- ~~Web login screen showing after native Android login~~ **FIXED** — crypto keys (salt/verify) passed via Intent extras and synced to localStorage in performAutoLogin()
- ~~Biometric prompt appearing twice on fingerprint login~~ **FIXED** — web onJournalSelect() checked hasNativeLogin() to skip redundant biometric trigger
- ~~CSV import field mapping UI overflows on Android~~ **FIXED** — reduced min-widths, added flex properties and overflow-x: auto on container

## Fixed (2026-03-31)

- ~~Settings Tags card cut off with no scrollbar~~ **FIXED**
- ~~Android viewer pin button on third row~~ **FIXED**
- ~~SQL Explorer CSV export crashes Android app~~ **FIXED** — replaced blob URL download with `downloadFile()` which uses AndroidBridge on Android

## Fixed (2026-03-27)

- ~~formatDate() returns "Invalid Date" for bad values~~ **FIXED** — returns empty string
- ~~Web app WASM loading fails on file:// protocol (CORS blocks XHR)~~ **FIXED** — added inline base64 WASM fallback
- ~~New journal creation silently fails on web~~ **FIXED** — WASM CORS issue

## Notes

### Dashboard Component Settings (2026-04-29)

- Dashboard components (weather/streak, stats, quick actions, widgets, pinned, recent, today_history, tags, categories, places, people) are configurable via Settings > Dashboard tab.
- 11 components total (added `today_history` — "Today in History").
- Visibility and order stored in BootstrapService as `dashboard_components` JSON array of `{id, enabled}` objects.
- `DashboardActivity.applyDashboardComponentSettings()` removes all component views from `content_container` and re-adds only enabled ones in saved order.
- Stats grid wrapped in `stats_container` (id: `R.id.stats_container`) so both stat rows move as one unit.

### Today in History (2026-04-29)

- `DashboardDataBuilder.buildTodayInHistory()` matches entries where `date.substring(5)` equals today's MM-dd, excluding current year.
- Returns JSON array of `{id, title, date, contentPreview (20 chars + "..."), yearsAgo}`, sorted by most recent year first.
- `DashboardActivity.populateTodayInHistory()` renders each entry as a row with title, content preview, and accent-colored "N year(s) ago" badge.
- Panel hidden when no matching entries exist. Entries are clickable (opens EntryViewerActivity).

### Architecture (2026-04-29, themes + WebView removal)

- **Fully native Android app**: All 14 screens are Kotlin activities. No WebView, no AndroidBridge, no web assets in APK.
- **ServiceProvider singleton**: Replaces old `MainActivity.instance` pattern. Holds CryptoService, BootstrapService, WeatherService, DatabaseService. Initialized in LoginActivity, accessed by all activities via `ServiceProvider.xxxService`.
- **ThemeManager singleton**: Runtime theme system with all 12 theme color maps. `ThemeManager.color(C.*)` replaces `ContextCompat.getColor(ctx, R.color.login_*)`. `applyToActivity()` recolors XML-set backgrounds/text via view tree walk. Theme changes detected via `themeVersion` counter in `onResume`.
- **DashboardDataBuilder**: Computes dashboard JSON natively from DatabaseService — stats, streaks, ranked lists, widgets, pinned/recent entries. Replaces the old JS `getDashboardDataJSON()`.
- **Login flow**: LoginActivity → ServiceProvider.init() → DatabaseService.open() → ThemeManager.init() → DashboardDataBuilder.build() → DashboardActivity (no WebView intermediary).
- **Rich content rendering**: `Html.fromHtml()` + `TextView` replaces WebView for rich content in EntryViewerActivity and ReportsActivity.
- **SearchActivity**: Queries DatabaseService directly via ServiceProvider (no longer receives all entries as JSON from WebView bridge).
- **File exports**: `ServiceProvider.saveFileToDownloads()` handles MediaStore scoped storage downloads.
- **21 Kotlin source files**: 14 activities + 4 services + ServiceProvider + DashboardDataBuilder + ThemeManager.
- **Web SPA preserved**: `/web/` directory still exists as standalone browser-only fallback but is NOT bundled in the Android APK.

### Widgets (2026-04-27)

- Widget click behavior: clicking a widget card shows filtered entries (was: opened widget editor). Pencil (✎) edit button provides editor access.
- `_widgetFilterActive` state in browser: set in `showWidgetEntries()`, consumed in entries.js `filterEntries()`, cleared on navigation away.
- Native widget filter engine: `EntryListActivity.matchesWidgetFilters()` replicates full JS widget filter logic including people JSONObject support.
- `getDashboardDataJSON()` (browser) includes pre-computed widget results for native dashboard.
- DashboardActivity widget rendering uses `GradientDrawable` for colored backgrounds with `getContrastColor()` luminance formula.

### Entry Locking (2026-04-13)

- `locked` column added to entries table. Schema version bumped from 1 to 2.
- Locked entries can still be viewed, pinned, and deleted — only editing is prevented.
- Lock/Unlock button in entry viewer bottom bar with confirmation prompt.

### Service Layer (2026-04-25)

- **SQLCipher dependency**: `net.zetetic:sqlcipher-android:4.5.6`. Import: `net.zetetic.database.sqlcipher.SQLiteDatabase`.
- **SQLCipher API**: `System.loadLibrary("sqlcipher")`. `openOrCreateDatabase` takes 5 params, `openDatabase` takes 6 params.
- **Location name field**: Locations store optional `name` field alongside `lat`, `lng`, `address`. Backwards-compatible.

### Browser-Only Notes

- Web Crypto API requires HTTPS or localhost (file:// uses base64 WASM fallback which is slower)
- Bootstrap IDB version bump from 1 to 2 (adds `bootstrap` object store)
- Dashboard nav grid and hamburger menu used for browser navigation
- Quill.js 1.3.7 used for rich text editing in browser
- jsPDF 2.5.1 used for PDF export in browser

## Limitations

- No cloud sync — all data is local-only
- sql.js (browser) loads entire database into memory
- Camera capture stores full-resolution images (no automatic compression setting)
- No undo/redo in native entry form beyond OS default
