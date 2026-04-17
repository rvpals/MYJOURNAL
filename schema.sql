-- My Journal v1.5.0 — Database Schema (schema_version 2)
-- SQLite database structure for sharing/reference.
-- All JSON columns (categories, tags, people, locations, weather, filters, functions)
-- store JSON-encoded arrays/objects as TEXT.

CREATE TABLE IF NOT EXISTS entries (
    id TEXT PRIMARY KEY,
    date TEXT,
    time TEXT,
    title TEXT,
    content TEXT,
    richContent TEXT,
    categories TEXT,          -- JSON array of category names
    tags TEXT,                -- JSON array of tag names
    placeName TEXT,
    locations TEXT,           -- JSON array of location objects
    weather TEXT,             -- JSON weather object
    pinned INTEGER DEFAULT 0,
    locked INTEGER DEFAULT 0,
    dtCreated TEXT,
    dtUpdated TEXT,
    people TEXT               -- JSON array of person names
);

CREATE INDEX IF NOT EXISTS idx_entries_date ON entries(date);
CREATE INDEX IF NOT EXISTS idx_entries_pinned ON entries(pinned);

CREATE TABLE IF NOT EXISTS images (
    id TEXT PRIMARY KEY,
    entryId TEXT NOT NULL,
    name TEXT,
    data TEXT,                -- Base64-encoded image data
    thumb TEXT,               -- Base64-encoded thumbnail
    sortOrder INTEGER DEFAULT 0,
    FOREIGN KEY (entryId) REFERENCES entries(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_images_entry ON images(entryId);

CREATE TABLE IF NOT EXISTS categories (
    name TEXT PRIMARY KEY,
    description TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS tags (
    name TEXT PRIMARY KEY,
    description TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS icons (
    type TEXT NOT NULL,       -- category, tag, person, category_hd, tag_hd, person_hd
    name TEXT NOT NULL,
    data TEXT,                -- PNG data URL
    PRIMARY KEY (type, name)
);

CREATE TABLE IF NOT EXISTS people (
    firstName TEXT NOT NULL,
    lastName TEXT NOT NULL,
    description TEXT DEFAULT '',
    PRIMARY KEY (firstName, lastName)
);

CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT
);

CREATE TABLE IF NOT EXISTS widgets (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    bgColor TEXT DEFAULT '',
    icon TEXT DEFAULT '',
    filters TEXT DEFAULT '[]',    -- JSON array of filter objects
    functions TEXT DEFAULT '[]',  -- JSON array of function objects
    enabledInDashboard INTEGER DEFAULT 1,
    sortOrder INTEGER DEFAULT 0,
    dtCreated TEXT,
    dtUpdated TEXT
);

CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY
);

INSERT OR REPLACE INTO schema_version VALUES (2);
