---
tags: [todo, backlog, changelog, version-history, completed-features, planned-features]
related_files: [REQUIREMENT.md, ISSUES.md, TO_TEST.md]
summary: "Version-by-version feature completion history (v0.9–v1.4) and remaining backlog items."
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
- [x] MainActivity expansion: AndroidBridge, file chooser, auto-login, metadata sync
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

## Remaining / TODO

- [ ] Re-test all features after UI overhaul (web + Android)
- [ ] Google Drive backup/sync (gdrive.js is placeholder)
- [ ] Web app: HTTPS requirement for Web Crypto API (currently file:// only works with base64 WASM fallback)
- [ ] Entry image thumbnails could be optimized (currently full base64 stored)
- [ ] Consider lazy loading for large journal databases (1000+ entries)
- [ ] Password strength indicator on journal creation
- [ ] Entry search: full-text search across rich content (currently plain text only)
- [ ] Accessibility audit (screen reader support, keyboard navigation)
- [ ] Localization / i18n support
