---
tags: [components, ResultGrid, RankedPanel, RecordViewer, CollapsiblePanel, API, CSS-classes, reusable-UI]
related_files: [REQUIREMENT.md, CLAUDE.md]
summary: "API documentation for reusable UI components — constructor options, methods, CSS classes, usage locations, and planned components."
---

# COMPONENTS — MYJOURNAL

Reusable UI components in `web/js/components.js` with styles in `web/css/style.css`.

## Implemented

### ResultGrid
Scrollable data table with configurable columns and horizontal scroll for overflow.

**Usage:** `ResultGrid.render(opts)`

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `data` | Array\<Object\> | `[]` | Row data objects |
| `columns` | Array\<Object\> | `[]` | Column defs: `{ key, label, width?, formatter?(value, row, idx), noHighlight? }` |
| `containerId` | string | — | Target element id (innerHTML replaced) |
| `showRowNum` | boolean | `true` | Show # column |
| `rowNumOffset` | number | `0` | Starting row number offset (for pagination) |
| `onRowClick` | Function | — | Callback `(row, index)` on row click |
| `emptyMessage` | string | `'No data.'` | Message when data is empty |
| `highlightTerm` | string | — | Term to highlight with `<mark>` |
| `wholeWord` | boolean | `false` | Whole-word highlight matching |
| `maxHeight` | number | `400` | Max container height in px (0 = unlimited) |

**Returns:** HTML string. Also injects into `containerId` if provided.

**CSS classes:** `rg-container`, `rg-table`, `rg-row`, `rg-clickable`, `rg-num`, `rg-empty`

**Used in:** Dashboard search, SQL Explorer results (entry queries + raw SQL)

---

### RankedPanel
Self-contained ranked list/card panel with built-in view toggle and show-all.

**Usage:** `RankedPanel.create(opts)` returns an instance; call `.setData(array).render()`.

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `id` | string | — | Unique panel id (localStorage key + lookup) |
| `containerId` | string | — | Wrapper element id (innerHTML replaced) |
| `title` | string | — | Panel heading text |
| `icon` | string | `''` | Emoji/icon before title |
| `filterType` | string | — | Type key for icons/colors (e.g. 'category', 'tag') |
| `onItemClick` | Function | — | Callback `(name, count)` on item click |
| `getIcon` | Function | — | `(name) => iconHTML`; defaults to `getIconHtml` |
| `getColor` | Function | — | `(name) => style attr`; defaults to `getItemColorStyle` |
| `topN` | number | `10` | Items shown before "Show All" |

**Static methods:** `RankedPanel.get(id)`, `RankedPanel.toggleView(id)`, `RankedPanel.toggleShowAll(id)`

**CSS classes:** `rp-panel`, `rp-body`, `panel-header`, `panel-header-actions`, `ranked-list`, `ranked-button-grid`, `ranked-item`, `ranked-btn`, `ranked-btn-img`, `ranked-btn-img-face`, `ranked-btn-img-count`

**Card view 3-tier fallback:**
1. HD image (`type_hd` icon exists) → `.ranked-btn-img` — image fills entire 3D button, count badge in corner, no text label
2. Standard icon (64x64 icon exists) → `.ranked-btn` with `.ranked-btn-icon` img + text label
3. No icon → `.ranked-btn` with `.ranked-btn-text-icon` 2-letter abbreviation + text label

**Used in:** Dashboard ranked panels (tags, categories, places, people)

---

### RecordViewer
Full-screen overlay showing all fields of a single record with prev/next navigation.

**Usage:** `RecordViewer.show(opts)` / `RecordViewer.close()`

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `data` | Array\<Object\> | `[]` | Full data array (for prev/next nav) |
| `index` | number | `0` | Current record index |
| `fields` | Array\<Object\> | auto | Field defs: `{ key, label, formatter?(value, row) }`. If omitted, shows all keys. |
| `highlightTerm` | string | — | Term to highlight with `<mark>` |
| `wholeWord` | boolean | `false` | Whole-word highlight matching |
| `onNavigate` | Function | — | Callback `(row, index)` on prev/next |

**CSS classes:** `rv-overlay`, `rv-panel`, `rv-header`, `rv-nav`, `rv-body`, `rv-table`, `rv-label`, `rv-value`

**Used in:** Dashboard search (row click), SQL Explorer (record detail)

---

### CollapsiblePanel
Reusable collapsible container with header toggle and pin-to-expand. Collapsed by default; pinned panels stay expanded (persisted to localStorage).

**Usage:** `CollapsiblePanel.render(opts)`

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `id` | string | — | Unique panel id (pin state key) |
| `containerId` | string | — | Wrapper element id (innerHTML replaced) |
| `title` | string | — | Panel heading text |
| `icon` | string | `''` | Emoji/icon before title |
| `bodyHTML` | string | `''` | Inner HTML content |
| `collapsed` | boolean | auto | Override initial state (default: true unless pinned) |

**Static methods:** `CollapsiblePanel.updateBody(id, html)`, `.expand(id)`, `.collapse(id)`, `.isPinned(id)`

**CSS classes:** `cp-section`, `cp-header`, `cp-icon`, `cp-body`, `cp-pin`

**Used in:** Entry list search & filter controls

---

## Bootstrap (bootstrap.js)

IndexedDB-backed key-value store with synchronous in-memory cache. Replaces all `localStorage` usage.

**Usage:**
```js
await Bootstrap.init();        // call once at startup (migrates from localStorage)
Bootstrap.get('key');           // synchronous read from cache
Bootstrap.set('key', 'value'); // sync cache update + async IDB write
Bootstrap.remove('key');       // sync remove + async IDB delete
Bootstrap.has('key');           // check existence
Bootstrap.getInt('key', 0);    // parsed int with default
Bootstrap.isReady();            // true after init()
```

**IDB details:** DB name `JournalDB`, store `bootstrap`, version 2. Auto-migrates known localStorage keys on first init (crypto salts/verify, journal list, UI prefs, column toggles, panel pins, view modes).

---

## Planned

### SearchInput
Reusable search bar with debounce, clear button, and options row (e.g., whole word toggle).
**Candidates:** Dashboard search, entry list search, explorer search, location search.

### Modal
Generic modal/overlay with open/close lifecycle, backdrop click to dismiss, and customizable content.
**Candidates:** Print modal, about modal, image lightbox, explorer detail, confirmation dialogs.

### EntryCard
Standard entry card renderer with configurable field visibility, thumbnails, tag chips, and click handlers.
**Candidates:** Entry list (card view), dashboard pinned/recent, custom view preview.

### TagChip
Consistent tag/category/people chip rendering with icon, color, and click-to-filter support.
**Candidates:** Entry list, entry cards, dashboard ranked panels, explorer results, views.

### Pagination
Page controls with page size selector, numbered buttons, prev/next, and ellipsis for large ranges.
**Candidates:** Entry list, explorer results, reports.

### ConfirmDialog
Standard confirmation prompt with customizable title, message, and confirm/cancel actions.
**Candidates:** Entry delete, batch delete, journal delete, password change, data import overwrite.
