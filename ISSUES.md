---
tags: [bugs, issues, limitations, fixed-bugs, WebView-crash, blob-URL, WASM, encryption, known-problems]
related_files: [TO_TEST.md, TODO.md, CLAUDE.md]
summary: "Known bugs, platform limitations, fixed issues with dates, and architectural constraints."
---

# ISSUES — MYJOURNAL

## Known Issues

- **Web Crypto API requires HTTPS or localhost** — plain HTTP won't work; file:// protocol uses base64 WASM fallback which is slower
- **Blob URL downloads crash Android WebView** — all file downloads must use `downloadFile()` which routes through `AndroidBridge.saveFile()` on Android
- **Large journals may be slow** — entire SQLite DB is loaded into memory via sql.js; no lazy loading for 1000+ entries
- **Image storage is inefficient** — full base64 images stored in SQLite; no server-side or file-system storage option
- **Bootstrap IDB version bump** — IndexedDB version bumped from 1 to 2 (adds `bootstrap` object store); existing users will trigger onupgradeneeded on first load after update

## Fixed (2026-04-04)

- ~~Metadata import shows blank screen after "app will refresh" message~~ **FIXED** — `window.location.reload()` fails in WebView file:// protocol; replaced with in-place `refreshSettings()` + theme reapply + `navigateTo('settings')`

## Fixed (2026-04-01)

- ~~Web login screen showing after native Android login~~ **FIXED** — crypto keys (salt/verify) passed via Intent extras and synced to localStorage in performAutoLogin()
- ~~Biometric prompt appearing twice on fingerprint login~~ **FIXED** — web onJournalSelect() checked hasNativeLogin() to skip redundant biometric trigger
- ~~CSV import field mapping UI overflows on Android~~ **FIXED** — reduced min-widths, added flex properties and overflow-x: auto on container

## Fixed (2026-03-31)

- ~~Settings Tags card cut off with no scrollbar~~ **FIXED** — increased `.settings-section-body` max-height from 2000px to 5000px
- ~~Android viewer pin button on third row~~ **FIXED** — restructured header into row1 (date, time, pin) and title below
- ~~SQL Explorer CSV export crashes Android app~~ **FIXED** — replaced blob URL download with `downloadFile()` which uses AndroidBridge on Android

## Fixed (2026-03-27)

- ~~formatDate() returns "Invalid Date" for bad values~~ **FIXED** — returns empty string; added `isValidDate()` helper for strict validation
- ~~Web app WASM loading fails on file:// protocol (CORS blocks XHR)~~ **FIXED** — added inline base64 WASM fallback for file:// protocol
- ~~New journal creation silently fails on web~~ **FIXED** — was caused by WASM CORS issue; added try-catch with error display and loading indicator

## Notes

- Dashboard ranked panels, Explorer results, and Entry list table view now use shared components (ResultGrid, RankedPanel, RecordViewer, CollapsiblePanel) from components.js — any rendering bugs in these areas should be checked against the component code, not the old inline implementations (which have been removed).
- Old filter-toggle-header / filter-fields-body CSS removed from both style.css and style-android.css — entry list filters now use CollapsiblePanel.
- Navbar no longer has Entry List, Report, or SQL Explorer links — navigation moved to dashboard 2x2 button grid.
- Theme cycle button removed from navbar — theme selection is in Settings > Display & Appearance > Theme.
- HD icons use `type_hd` suffix in the same `icons` table (e.g., `tag_hd`). No schema change. Upload generates both 64x64 and 128x128 if source image >=96px. RankedPanel card view checks HD first, then standard icon, then text fallback.
- Data Management now uses dropdown selects instead of individual buttons. Export/Import Metadata moved from Edit Metadata tab to Data Management dropdowns.
- `cleanupDatelessEntries()` function removed — Cleanup Dateless button no longer exists.
- Entry viewer buttons (Edit, Print, Delete, Back) are icon-only; text labels removed.
- All localStorage usage has been replaced by Bootstrap module (IndexedDB-backed with in-memory cache). Bootstrap auto-migrates existing localStorage keys on first init.
- `gdrive.js` removed from assets — Google Drive placeholder no longer included.
- Dashboard widgets are stored in the `widgets` DB table with filters/functions as JSON. Widget editor is a separate page (`page-widget-editor`).
- Categories table now has a `description` column. A new `tags` table stores tag descriptions (separate from entry tag arrays).
- SQL Explorer now supports iCalendar (.ics) export alongside CSV.
- Android backup folder uses SAF (Storage Access Framework) with persistent URI permissions; folder URI stored in SharedPreferences (`backup_prefs`).

## Limitations

- Web Crypto API requires HTTPS or localhost (no plain HTTP)
- No cloud sync — all data is local-only
- Android WebView cannot handle blob: URLs for downloads
- sql.js loads entire database into memory (not suitable for very large datasets)
- Quill.js editor is v1.3.7 (legacy; v2 has breaking changes)
- No undo/redo in entry form beyond browser default
- Camera capture stores full-resolution images (no automatic compression setting)
