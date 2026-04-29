---
tags: [testing, checklist, QA, verification, regression, manual-test, critical-path]
related_files: [ISSUES.md, TODO.md, REQUIREMENT.md]
summary: "Feature-by-feature test checklist for the fully native Android app (14 activities, no WebView)."
---

# TO TEST — MYJOURNAL

## Critical Path (must work)

- [x] **Login flow** — Create new journal with password, re-open existing journal, wrong password rejection (verified 2026-04-02)
- [x] **Biometric login** — Enable in settings, lock app, re-authenticate with fingerprint/face (fixed double-prompt bug 2026-04-01)
- [x] **Native LoginActivity** — Journal selector spinner, password entry, biometric button, auto-open last journal (fixed web login reappearing 2026-04-01)
- [ ] **Entry CRUD** — Create entry with all fields, edit existing, delete with confirmation
- [ ] **Encryption** — Lock app, re-login, verify entries are intact and decrypted correctly
- [ ] **Data persistence** — Close app completely, reopen, verify data survives

## Native Android Activities (all Kotlin — 14 activities, no WebView)

- [ ] **LoginActivity → DashboardActivity** — Login completes, ServiceProvider initialized, DB opened, DashboardDataBuilder computes data, DashboardActivity launches directly (no WebView)
- [ ] **DashboardActivity** — Stats grid (total, week, month, year), pinned entries, recent entries, ranked panels (tags, categories, places, people), widgets with colored backgrounds
- [ ] **AboutActivity** — App info display, clickable email/URL/Play Store links, changelog entries
- [ ] **CalendarActivity** — Monthly grid renders correctly, day selection shows entries, today/selected highlighting, prev/next month navigation, Today button, entry rows clickable
- [ ] **SearchActivity** — Search icon button on dashboard opens native search screen, text input with search/clear, whole-word checkbox, results show title/date/content snippet/metadata, back button returns to dashboard
- [ ] **EntryViewerActivity** — Font settings applied (reads ev_font_family/ev_font_size from BootstrapService), rich content via Html.fromHtml() + TextView, title bold at 1.3x
- [ ] **SettingsActivity** — All 6 tabs load and function: Preferences (toggles, font, date/time, theme, wallpaper, weather), Templates (views/entry/report), Metadata (categories/tags/people CRUD with icons/colors), Data (export/import, password change), Widgets (list/editor with filters/functions), Dashboard (component toggle/reorder)
- [ ] **Settings tab icons** — All 6 tabs show emoji icons (⚙️📝🏷️💾🧩📊) with 3D active/inactive styling
- [ ] **EntryListActivity** — Search across all fields, category/tag filter spinners, sort by configured field, pagination (10/20/50/100), card view with all entry metadata, select mode with batch delete, navigation to EntryViewerActivity
- [ ] **ExplorerActivity** — Table browser (click chip shows schema + sample), SQL textarea (raw SQL on any table), condition builder (add/remove rows, field/op/value spinners), field chips (toggle columns), Run Query, results table (clickable rows → record detail dialog), CSV export, iCalendar export, Clear button
- [ ] **EntryFormActivity** — New entry: date/time pickers, title, content with B/I/U/S formatting toolbar, image add (gallery + camera), save. Edit entry: loads existing data, updates correctly. Categories checkboxes + quick-add, tags autocomplete + chips, people checkboxes + quick-add, place name, location search (Photon/Nominatim) + GPS, weather import, delete button on existing entries
- [ ] **EntryFormActivity rich text** — Bold/italic/underline/strikethrough toggle, formatting persists after save and reload, Html.fromHtml()/toHtml() round-trip
- [ ] **EntryFormActivity images** — Gallery picker adds images with thumbnails, camera capture works, remove button on each image, images saved as base64 with thumb
- [ ] **EntryFormActivity tabs** — Main/Misc tab switching, all fields accessible, scroll position preserved
- [ ] **ReportsActivity** — HTML report generation with template, date/category/tag filters, CSV export, PDF export via native PdfDocument, template selection
- [ ] **WidgetEditorActivity** — Create widget: name/desc/icon/color, add filter rows, add function rows, preview, save. Edit existing widget: loads data, updates correctly. Color picker dialog opens on swatch/input tap with presets and hex input
- [ ] **CustomViewEditorActivity** — Create view: name, conditions (field/op/value), groupBy, orderBy, displayMode, pinToDashboard. Edit existing view, delete
- [ ] **CsvMappingActivity** — Column mapping dropdowns, date/time format config, duplicate detection, import executes correctly
- [ ] **Back button** — Proper navigation between all activities

## Entry Form

- [ ] **Rich text editor** — Bold, italic, underline, strikethrough via Spannable toolbar buttons, formatting visible in EditText
- [ ] **Image attachments** — Gallery picker via GetMultipleContents, camera capture via TakePicturePreview, thumbnail grid, remove button per image
- [x] **Categories** — Checkboxes with quick-add (verified 2026-04-03)
- [x] **Tags** — Auto-complete, create new from form via quick-create panel (verified 2026-04-03)
- [x] **People** — Multi-select, create new from form via quick-create panel (verified 2026-04-03)
- [ ] **Location** — Place name input, geocoding search (Photon/Nominatim), GPS via requestSingleUpdate, manual lat/lng add
- [ ] **Weather** — Fetch current weather via WeatherService, display in form and viewer
- [ ] **Date/Time** — DatePickerDialog and TimePickerDialog
- [ ] **Pin entry** — Toggle pin, verify appears in dashboard pinned list
- [ ] **Edit from viewer** — EntryViewerActivity edit button launches EntryFormActivity with entry ID (bug fixed: was passing null bootstrapService)

## Entry List & Viewer

- [x] **List view** — Card mode and list/table mode toggle (verified 2026-04-03)
- [x] **Search** — Debounced search across title, content, tags, categories (verified 2026-04-03)
- [x] **Filters** — Date range, category, tag filters, Clear All button (verified 2026-04-03)
- [ ] **Pagination** — 10/20/50/100 entries per page
- [ ] **Multi-select** — Batch delete mode
- [ ] **Entry viewer** — Read-only view with all fields, prev/next navigation, icon-only action buttons
- [ ] **Date/Time format** — Verify all 6 date formats and 12h/24h time display

## Dashboard

- [x] **Stats cards** — Total entries, this week, this month, this year (verified 2026-04-06)
- [x] **Ranked panels** — Top tags, categories, places, people (verified 2026-04-06)
- [x] **Pinned entries** — Display, click to view (verified 2026-04-06)
- [x] **Recent entries** — Display, click to view (verified 2026-04-06)
- [x] **Dashboard weather** — Inset 3D styling, click to navigate to Settings > Weather Location (verified 2026-04-06)
- [x] **Quick actions** — Pinned custom views on dashboard (verified 2026-04-06)
- [ ] **Dashboard component settings** — Toggle components on/off in Settings > Dashboard, verify hidden components disappear from dashboard
- [ ] **Dashboard component reorder** — Reorder components via ▲/▼ in Settings > Dashboard, verify new order on dashboard
- [ ] **Dashboard ranked badge 3D** — Rank badges show gradient + shadow, count badges show dark gradient + shadow
- [ ] **Dashboard search button** — 3D icon button in navbar row (left of journal name) opens SearchActivity

## Dashboard Navigation (Native)

- [ ] **Dashboard → Entry List** — Tap "Entries" in bottom nav, native EntryListActivity opens, back returns to dashboard
- [ ] **Dashboard → Explorer** — Tap "Explorer" in bottom nav, native ExplorerActivity opens, back returns to dashboard
- [ ] **Dashboard → Settings** — Tap "Settings" in bottom nav, native SettingsActivity opens, back returns to dashboard
- [ ] **Dashboard → Entry Form** — Tap "New Entry" button, native EntryFormActivity launches
- [ ] **Dashboard → Reports** — Tap "Reports" in bottom nav, native ReportsActivity launches
- [ ] **Dashboard → Lock** — Tap "Lock" button, returns to LoginActivity, DB closed
- [ ] **Dashboard stat cards filter** — Tap "This Week" stat card, EntryListActivity opens with date range filter
- [ ] **Dashboard ranked panel filter** — Tap a tag/category/place/person, EntryListActivity opens with proper filter
- [ ] **Dashboard widget click** — Tap widget card, EntryListActivity opens with widget filters applied
- [ ] **Dashboard widget edit** — Tap pencil button on widget card, native WidgetEditorActivity opens
- [ ] **Dashboard pinned/recent entry click** — Tap entry row, EntryViewerActivity opens showing that entry
- [ ] **Dashboard search** — Tap search icon, native SearchActivity opens, back returns to dashboard

## Custom Views

- [ ] **Create view** — Name, conditions (AND/OR/NOT), group by, sort by, display mode
- [ ] **Condition types** — All operators: contains, equals, starts/ends with, before/after, between, includes, is empty
- [ ] **Load view** — Apply saved view to entry list
- [ ] **Pin to dashboard** — View appears as quick action

## SQL Explorer

- [ ] **Visual builder** — Field/operator/value conditions with Spinner dropdowns, add/remove rows
- [ ] **Raw SQL** — Type raw SQL targeting any table
- [ ] **Results table** — Horizontally scrollable, clickable rows
- [ ] **Record detail** — Click row opens AlertDialog with full field values, prev/next, "View Entry" button
- [ ] **CSV export** — Export results to CSV, saved to Downloads via MediaStore
- [ ] **iCalendar export** — Export entry results as .ics
- [ ] **Table browser** — Click table chips to see column schema and sample rows
- [ ] **Clear button** — Resets conditions, SQL input, results

## Reports

- [ ] **HTML report** — Generate with templates, display in output area
- [ ] **PDF export** — Native PdfDocument API generation, saved to Downloads
- [ ] **CSV export** — Column headers, all fields
- [ ] **Template editor** — Create/edit/delete templates
- [ ] **Filters** — Date range, category, tag filtering

## Settings

- [ ] **Preferences tab** — Auto-open journal, confirm delete, biometric toggle, geocoding provider, viewer font with live preview, date/time format, max pinned, sort order
- [ ] **Theme picker** — All 12 themes apply correctly
- [ ] **Wallpaper** — Browse image, preview thumbnail, clear wallpaper
- [ ] **Weather city search** — Search city, select from results, save location
- [ ] **Categories editor** — Add, rename, delete, color picker, icon upload
- [ ] **Tags editor** — Add, rename, delete, color picker, description
- [ ] **People editor** — Add, edit, delete (first/last name, description)
- [ ] **Custom Views** — Create/edit/delete views
- [ ] **Pre-fill templates** — Create, edit, delete entry templates
- [ ] **Report templates** — CRUD for HTML templates
- [ ] **Password change** — Old password verification, re-encrypt database
- [ ] **Data export** — Export encrypted, CSV, metadata JSON
- [ ] **Widgets tab** — Widget list, create/edit/delete with editor, live preview

## Dashboard Widgets

- [x] **Create widget** — Open widget editor, set name, description, icon, background color (verified 2026-04-05)
- [x] **Widget filters** — Add filter rows, verify entries filtered correctly (verified 2026-04-05)
- [x] **Widget functions** — Add aggregate functions (Count, Sum, Max, Min, Average) (verified 2026-04-05)
- [x] **Widget preview** — Preview shows live widget card with computed results (verified 2026-04-05)
- [x] **Widget on dashboard** — Enabled widgets render as cards with correct values (verified 2026-04-05)
- [ ] **Widget icon on dashboard** — Widget with icon shows 40x40 image in card header (was missing before fix)
- [ ] **Widget color picker** — Tap swatch or hex input in editor, color picker dialog opens with 24 presets, hex input, live preview
- [x] **Widget edit/delete** — Edit existing widget, delete with confirmation (verified 2026-04-05)
- [x] **Widget persistence** — Widgets survive app restart (verified 2026-04-05)

## Widget Click-to-Filter

- [ ] **Widget card click shows entries** — Click widget card, verify navigates to entry list with filtered entries
- [ ] **Widget edit button** — Tap pencil button, verify opens widget editor
- [ ] **Widget filter criteria chip** — "Widget: <name>" chip in filter criteria box
- [ ] **Widget name in title** — Entry list title shows widget name when filter active
- [ ] **Clear widget filter** — Clear All removes widget filter
- [ ] **Widget date/text/array filters** — All filter types work correctly

## Backup Folder (Android)

- [ ] **Select folder** — SAF folder picker opens, selected folder name displays
- [ ] **Backup all data** — Creates JSON backup file in selected folder
- [ ] **Restore all data** — Lists backup files, restores selected backup
- [ ] **Clear folder** — Clears folder selection, releases URI permissions
- [ ] **Persist across restarts** — Folder URI persisted in SharedPreferences

## iCalendar Export

- [ ] **Export from Explorer** — iCalendar export button in results bar
- [ ] **Entry-based only** — Shows alert for non-entry raw SQL results
- [ ] **Valid .ics** — Valid iCalendar with VEVENT per entry

## Wallpaper

- [ ] **Browse image** — Select image in Settings > Preferences
- [ ] **Dashboard background** — Wallpaper appears on dashboard
- [ ] **Preview** — Thumbnail in settings after selection
- [ ] **Clear wallpaper** — Restores theme defaults
- [ ] **Persistence** — Survives app restart

## Calendar View

- [x] **Monthly view** — Calendar grid, day numbers, entry count badges (verified 2026-04-06)
- [x] **Navigation** — Prev/next, Today button (verified 2026-04-06)
- [x] **Day selection** — Click day to show entries (verified 2026-04-06)
- [x] **Today highlight** — Current day accent border (verified 2026-04-06)
- [x] **Theme support** — Renders correctly across all 12 themes (verified 2026-04-06)

## Entry Locking

- [x] **Lock entry** — Lock button, confirm, lock icon changes (verified 2026-04-13)
- [x] **Edit disabled when locked** — Edit button grayed out (verified 2026-04-13)
- [x] **Unlock entry** — Unlock, confirm, Edit re-enables (verified 2026-04-13)
- [x] **Lock persists** — Navigate away and return, still locked (verified 2026-04-13)
- [x] **Schema migration** — Existing journals load with `locked` defaulting to false (verified 2026-04-13)

## Location Name Field

- [ ] **Add location with name** — GPS/search location, type custom name, stored as `{lat, lng, address, name}`
- [ ] **Display in viewer** — "Name — Address" for named locations, just address without
- [ ] **Search locations** — Entry list search matches location name text
- [ ] **Backwards compat** — Existing entries without name field display correctly

## Native Service Layer

- [ ] **BootstrapService** — SharedPreferences stores/retrieves values correctly
- [ ] **CryptoService** — Encrypt/decrypt strings and bytes
- [ ] **CryptoService setup** — `setupPassword`/`verifyPassword` work correctly
- [ ] **WeatherService** — City search returns results, `fetchCurrent` returns weather
- [ ] **DatabaseService** — Open/close SQLCipher DB, CRUD on all tables
- [ ] **DB entries** — getEntries, addEntry, updateEntry, deleteEntryById all work
- [ ] **DB categories/tags/icons/people/widgets** — All CRUD operations
- [ ] **DB settings** — getSettings/setSettings round-trip correctly
- [ ] **DB export/stats/raw SQL** — exportJSON, getDBStats, execRawSQL

## Browser-Only Fallback (web/ directory)

- [ ] **Web browser** — Full functionality in Chrome/Firefox (localhost or HTTPS)
- [ ] **File downloads** — PDF, CSV, SQLite, JSON, iCalendar exports
- [ ] **Theme consistency** — All 12 themes render correctly

## Prior Sessions (pre-UI overhaul, tested OK)

- [x] Core CRUD operations
- [x] Encryption/decryption cycle
- [x] Multi-journal management
- [x] Rich text editor
- [x] Image attachments and lightbox
- [x] All 12 themes
- [x] Export/import (SQLite, JSON)
- [x] Reports (HTML, PDF, CSV)
- [x] Weather tracking
- [x] Location/geocoding
- [x] Metadata export/import
