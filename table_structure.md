# Database Table Structure

**Engine:** SQLCipher 4.5.6 (encrypted SQLite via `net.zetetic:sqlcipher-android`) | **Schema Version:** 2

---

## Tables

### entries

Primary table storing all journal entries.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | TEXT | PRIMARY KEY | Unique entry identifier (generated via `generateId()`) |
| date | TEXT | | Entry date (YYYY-MM-DD) |
| time | TEXT | | Entry time (HH:MM) |
| title | TEXT | | Entry title |
| content | TEXT | | Plain text content |
| categories | TEXT | | JSON array of category names, e.g. `["Work","Travel"]` |
| tags | TEXT | | JSON array of tag names, e.g. `["daily","personal"]` |
| placeName | TEXT | | Free-text place name, e.g. "NYC Trip" |
| locations | TEXT | | JSON array of `{lat, lng, address, name?}` objects |
| weather | TEXT | | JSON object `{temp, unit, description, code}` or null |
| pinned | INTEGER | DEFAULT 0 | 1 = pinned to dashboard, 0 = not pinned |
| locked | INTEGER | DEFAULT 0 | 1 = locked (editing disabled), 0 = unlocked |
| dtCreated | TEXT | | ISO 8601 creation timestamp |
| dtUpdated | TEXT | | ISO 8601 last-updated timestamp |

**Indexes:**
- `idx_entries_date` on `date`
- `idx_entries_pinned` on `pinned`

---

### images

Stores image attachments for journal entries.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | TEXT | PRIMARY KEY | Unique image identifier |
| entryId | TEXT | NOT NULL, FK -> entries(id) ON DELETE CASCADE | Parent entry |
| name | TEXT | | Original filename |
| data | TEXT | | Full image as base64 data URL |
| thumb | TEXT | | Thumbnail as base64 data URL |
| sortOrder | INTEGER | DEFAULT 0 | Display order within entry |

**Indexes:**
- `idx_images_entry` on `entryId`

---

### categories

Managed list of available categories.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| name | TEXT | PRIMARY KEY | Category name |
| description | TEXT | DEFAULT '' | Optional description (shown as hint in entry form) |

---

### tags

Managed list of tag descriptions. Tags can also be created inline in entries.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| name | TEXT | PRIMARY KEY | Tag name |
| description | TEXT | DEFAULT '' | Optional description (shown in autocomplete and hints) |

---

### icons

Custom icons for categories and tags. Supports standard (64x64) and HD (128x128) variants.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| type | TEXT | NOT NULL, PK (composite) | Icon type: `category`, `tag`, `category_hd`, `tag_hd` |
| name | TEXT | NOT NULL, PK (composite) | Name of the category/tag/person |
| data | TEXT | | PNG image as data URL |

---

### widgets

Dashboard widgets with configurable filters and aggregate functions.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | TEXT | PRIMARY KEY | Unique widget identifier |
| name | TEXT | NOT NULL | Widget display name |
| description | TEXT | DEFAULT '' | Optional description |
| bgColor | TEXT | DEFAULT '' | Background color hex, e.g. `#4a90d9` |
| icon | TEXT | DEFAULT '' | Icon as base64 data URL (64x64) |
| filters | TEXT | DEFAULT '[]' | JSON array of filter conditions `{field, operator, value}` |
| functions | TEXT | DEFAULT '[]' | JSON array of aggregate functions `{func, target, prefix, postfix}` |
| enabledInDashboard | INTEGER | DEFAULT 1 | 1 = shown on dashboard, 0 = hidden |
| sortOrder | INTEGER | DEFAULT 0 | Display order on dashboard |
| dtCreated | TEXT | | ISO 8601 creation timestamp |
| dtUpdated | TEXT | | ISO 8601 last-updated timestamp |

---

### inspiration

Stores inspirational quotes for the Daily Inspiration dashboard panel.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | Unique quote identifier |
| quote | TEXT | | Quote text |
| source | TEXT | | Attribution / source |

---

### settings

Key-value store for all application settings.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| key | TEXT | PRIMARY KEY | Setting key name |
| value | TEXT | | JSON-encoded value |

**Common keys:** `theme`, `reportTemplates`, `entryTemplates`, `customViews`, `entryListFields`

---

### schema_version

Tracks the database schema version for migrations.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| version | INTEGER | PRIMARY KEY | Current schema version (currently 2) |

---

## Relationship Diagram

```
entries (1) ------< images (many)
   |                   FK: images.entryId -> entries.id (CASCADE DELETE)
   |
   |-- .categories[]  ...> categories.name     (logical, JSON array)
   |-- .tags[]        ...> tags.name           (logical, JSON array)
   |
icons(type, name) ...> categories.name         (logical, type='category'/'category_hd')
                  ...> tags.name               (logical, type='tag'/'tag_hd')

widgets                (standalone, references entries via filter criteria at runtime)
inspiration            (standalone, quotes for Daily Inspiration panel)
settings               (standalone key-value store)
schema_version         (standalone, single row)
```

**Legend:** `------<` = foreign key (enforced) | `...>` = logical reference (JSON field, not FK-enforced)
