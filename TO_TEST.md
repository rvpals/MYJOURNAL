# TO TEST — MYJOURNAL

## Critical Path (must work)

- [x] **Login flow** — Create new journal with password, re-open existing journal, wrong password rejection (verified 2026-04-02)
- [x] **Biometric login** — Enable in settings, lock app, re-authenticate with fingerprint/face (fixed double-prompt bug 2026-04-01)
- [x] **Native LoginActivity** — Journal selector spinner, password entry, biometric button, auto-open last journal (fixed web login reappearing 2026-04-01)
- [ ] **Entry CRUD** — Create entry with all fields, edit existing, delete with confirmation
- [ ] **Encryption** — Lock app, re-login, verify entries are intact and decrypted correctly
- [ ] **Data persistence** — Close app completely, reopen, verify data survives

## Native Android Activities

- [x] **LoginActivity → MainActivity** — Credential passing, auto-login via JS injection (crypto keys passed via Intent extras 2026-04-01)
- [x] **MainActivity → DashboardActivity** — Dashboard data JSON transfer, clickable items return navigation (converted to native 2026-04-01)
- [x] **DashboardActivity** — Stats grid (total, week, month, year), pinned entries, recent entries, ranked panels (tags, categories, places, people)
- [ ] **AboutActivity** — App info display, clickable email/URL/Play Store links
- [ ] **Back button** — Proper navigation between activities and WebView pages
- [ ] **AndroidBridge** — saveFile, biometric, credential storage, sync functions

## Entry Form

- [ ] **Rich text editor** — Bold, italic, lists, links, image embed via Quill.js
- [ ] **Image attachments** — Gallery picker, camera capture, image display in viewer, lightbox
- [x] **Categories** — Single-select dropdown, create new from form via quick-create panel (verified 2026-04-03)
- [x] **Tags** — Multi-select, auto-complete, create new from form via quick-create panel (verified 2026-04-03)
- [x] **People** — Multi-select, auto-complete, create new from form via quick-create panel (verified 2026-04-03)
- [ ] **Location** — Place name input, geocoding search (Photon/Nominatim/Google), GPS coordinates
- [ ] **Weather** — Fetch current weather, display in entry form and viewer
- [ ] **Date/Time** — Manual entry, validation (strict YYYY-MM-DD)
- [ ] **Pin entry** — Toggle pin, verify appears in dashboard pinned list

## Entry List & Viewer

- [x] **List view** — Card mode and list/table mode toggle, ResultGrid component for table view (verified 2026-04-03)
- [x] **Search** — Debounced search across title, content, tags, categories, highlight in ResultGrid (verified 2026-04-03)
- [x] **Filters** — Date range, category, tag filters in CollapsiblePanel, Clear All button (verified 2026-04-03)
- [ ] **Pagination** — 10/20/50/100/all entries per page
- [ ] **Multi-select** — Batch delete mode
- [ ] **Entry viewer** — Read-only view with all fields, prev/next navigation
- [ ] **Android swipe** — Swipe left/right to navigate entries on Android
- [ ] **Date/Time format** — Verify all 6 date formats and 12h/24h time display

## Dashboard

- [ ] **Stats cards** — Total entries, this week, this month, this year (verify counts)
- [ ] **Ranked panels** — Top tags, categories, places, people (ranked by count, top 10)
- [ ] **Pinned entries** — Display, click to view
- [ ] **Recent entries** — Display, click to view
- [ ] **Dashboard search** — Live search results, whole-word toggle, ResultGrid rendering
- [ ] **Quick actions** — Pinned custom views on dashboard
- [ ] **Ranked panels** — RankedPanel component: list/card view toggle, show all/top 10, click to filter

## Custom Views

- [ ] **Create view** — Name, conditions (AND/OR/NOT), group by, sort by, display mode
- [ ] **Condition types** — All operators: contains, equals, starts/ends with, before/after, between, includes, is empty
- [ ] **Preview** — Preview matching entries without saving
- [ ] **Load view** — Apply saved view to entry list
- [ ] **Pin to dashboard** — View appears as quick action
- [ ] **Default view** — Set a view as default on app open

## SQL Explorer

- [ ] **Visual builder** — Field/operator/value conditions, add/remove rows
- [ ] **Raw SQL** — Type raw SQL targeting any table (e.g., `SELECT * FROM icons`)
- [ ] **Results grid** — ResultGrid component: row numbers, clickable rows, truncated cell values
- [ ] **Record detail overlay** — RecordViewer component: click row to see full values, prev/next navigation
- [ ] **CSV export** — Export results to CSV (double-quoted values, date-stamped filename)
- [ ] **CSV export on Android** — Verify no crash (must use downloadFile, not blob URL)
- [ ] **Custom view loader** — Dropdown to load view conditions into builder
- [ ] **Table browser** — Click table chips to see schema and sample rows
- [ ] **Delete button** — Red x button with tooltip, confirmation dialog

## Reports

- [ ] **HTML report** — Generate with default and custom templates
- [ ] **PDF export** — jsPDF generation, download on both web and Android
- [ ] **CSV export** — Column headers, all fields
- [ ] **Template editor** — Create/edit/delete templates with field substitution tags
- [ ] **Filters** — Date range, category, tag filtering for reports

## Settings

- [ ] **Preferences tab** — Auto-open journal, confirm delete, biometric toggle, geocoding provider, viewer font, date/time format, max pinned
- [ ] **Theme picker** — All 12 themes apply correctly
- [ ] **Categories editor** — Add, rename, delete, color picker
- [ ] **Tags editor** — Add, rename, delete, color picker
- [ ] **People editor** — Add, edit, delete (first/last name, description)
- [ ] **Icons editor** — Custom icons for categories/tags
- [ ] **Pre-fill templates** — Create, edit, delete, apply to new entry
- [ ] **Report templates** — CRUD for HTML templates
- [ ] **Password change** — Old password verification, re-encrypt database
- [ ] **Data export** — Encrypted SQLite, plain SQLite, JSON, CSV
- [ ] **Data import** — SQLite, JSON, CSV with column mapping (CSV field mapping overflow fixed 2026-04-01)
- [ ] **Metadata export/import** — JSON with categories, tags, people, icons, settings, templates, views

## Cross-Platform

- [ ] **Web browser** — Full functionality in Chrome/Firefox (localhost or HTTPS)
- [ ] **Android WebView** — All features work within WebView container
- [ ] **File downloads** — PDF, CSV, SQLite, JSON exports work on both platforms
- [ ] **Theme consistency** — All 12 themes render correctly on both web and Android
- [ ] **Android CSS** — style-android.css overrides applied only on Android

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
