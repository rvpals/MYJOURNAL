/**
 * Database module using sql.js (SQLite WASM) for storage.
 * SQLite DB persisted as encrypted bytes in IndexedDB.
 * Supports multiple journals, each stored under its own key.
 */

const DB = (() => {
    const IDB_NAME = 'JournalDB';
    const STORE_NAME = 'encrypted_data';
    const JOURNALS_META_KEY = 'journal_list';
    const SCHEMA_VERSION = 1;

    let currentPassword = null;
    let currentJournalId = null;
    let sqlDB = null;       // sql.js Database instance
    let SQL = null;         // sql.js library reference

    function setPassword(pw) { currentPassword = pw; }
    function getPassword() { return currentPassword; }
    function setJournalId(id) { currentJournalId = id; }
    function getJournalId() { return currentJournalId; }

    // ========== Journal List (metadata, not encrypted) ==========

    function getJournalList() {
        const raw = Bootstrap.get(JOURNALS_META_KEY);
        return raw ? JSON.parse(raw) : [];
    }

    function saveJournalList(list) {
        const json = JSON.stringify(list);
        Bootstrap.set(JOURNALS_META_KEY, json);
        // Sync to native SharedPreferences if available
        if (window.AndroidBridge && typeof AndroidBridge.syncJournalList === 'function') {
            AndroidBridge.syncJournalList(json);
        }
    }

    function addJournalToList(id, name) {
        const list = getJournalList();
        if (!list.find(j => j.id === id)) {
            list.push({ id, name, createdAt: new Date().toISOString() });
            saveJournalList(list);
        }
    }

    function removeJournalFromList(id) {
        const list = getJournalList().filter(j => j.id !== id);
        saveJournalList(list);
    }

    function generateJournalId(name) {
        return name.toLowerCase().replace(/[^a-z0-9]/g, '_') + '_' + Date.now().toString(36);
    }

    // ========== sql.js Initialization ==========

    async function initSqlJs() {
        if (SQL) return;
        SQL = await initSqlJs.loader();
    }

    // Load sql.js WASM - use inline base64 for file:// protocol (CORS blocks XHR)
    initSqlJs.loader = () => {
        if (typeof SQL_WASM_BASE64 === 'string' && window.location.protocol === 'file:') {
            const binary = atob(SQL_WASM_BASE64);
            const bytes = new Uint8Array(binary.length);
            for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            return window.initSqlJs({ wasmBinary: bytes.buffer });
        }
        const wasmUrl = (typeof window !== 'undefined' && window.location)
            ? new URL('lib/sql-wasm.wasm', window.location.href.replace(/\/[^/]*$/, '/')).href
            : 'lib/sql-wasm.wasm';
        return window.initSqlJs({ locateFile: () => wasmUrl });
    };

    // ========== Schema ==========

    function createSchema(db) {
        db.run(`
            CREATE TABLE IF NOT EXISTS entries (
                id TEXT PRIMARY KEY,
                date TEXT,
                time TEXT,
                title TEXT,
                content TEXT,
                richContent TEXT,
                categories TEXT,
                tags TEXT,
                placeName TEXT,
                locations TEXT,
                weather TEXT,
                pinned INTEGER DEFAULT 0,
                dtCreated TEXT,
                dtUpdated TEXT,
                people TEXT
            )
        `);
        db.run(`CREATE INDEX IF NOT EXISTS idx_entries_date ON entries(date)`);
        db.run(`CREATE INDEX IF NOT EXISTS idx_entries_pinned ON entries(pinned)`);

        db.run(`
            CREATE TABLE IF NOT EXISTS images (
                id TEXT PRIMARY KEY,
                entryId TEXT NOT NULL,
                name TEXT,
                data TEXT,
                thumb TEXT,
                sortOrder INTEGER DEFAULT 0,
                FOREIGN KEY (entryId) REFERENCES entries(id) ON DELETE CASCADE
            )
        `);
        db.run(`CREATE INDEX IF NOT EXISTS idx_images_entry ON images(entryId)`);

        db.run(`CREATE TABLE IF NOT EXISTS categories (name TEXT PRIMARY KEY, description TEXT DEFAULT '')`);

        db.run(`CREATE TABLE IF NOT EXISTS tags (name TEXT PRIMARY KEY, description TEXT DEFAULT '')`);

        db.run(`CREATE TABLE IF NOT EXISTS icons (
            type TEXT NOT NULL,
            name TEXT NOT NULL,
            data TEXT,
            PRIMARY KEY (type, name)
        )`);

        db.run(`CREATE TABLE IF NOT EXISTS people (
            firstName TEXT NOT NULL,
            lastName TEXT NOT NULL,
            description TEXT DEFAULT '',
            PRIMARY KEY (firstName, lastName)
        )`);

        db.run(`CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)`);

        db.run(`CREATE TABLE IF NOT EXISTS widgets (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            description TEXT DEFAULT '',
            bgColor TEXT DEFAULT '',
            icon TEXT DEFAULT '',
            filters TEXT DEFAULT '[]',
            functions TEXT DEFAULT '[]',
            enabledInDashboard INTEGER DEFAULT 1,
            sortOrder INTEGER DEFAULT 0,
            dtCreated TEXT,
            dtUpdated TEXT
        )`);

        db.run(`CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY)`);
        db.run(`INSERT OR REPLACE INTO schema_version VALUES (?)`, [SCHEMA_VERSION]);
    }

    function upgradeSchema(db) {
        // Add tables introduced after initial schema
        db.run(`CREATE TABLE IF NOT EXISTS icons (
            type TEXT NOT NULL,
            name TEXT NOT NULL,
            data TEXT,
            PRIMARY KEY (type, name)
        )`);
        db.run(`CREATE TABLE IF NOT EXISTS people (
            firstName TEXT NOT NULL,
            lastName TEXT NOT NULL,
            description TEXT DEFAULT '',
            PRIMARY KEY (firstName, lastName)
        )`);
        try { db.run("ALTER TABLE entries ADD COLUMN people TEXT"); } catch(e) { /* column already exists */ }
        try { db.run("ALTER TABLE categories ADD COLUMN description TEXT DEFAULT ''"); } catch(e) { /* column already exists */ }
        db.run(`CREATE TABLE IF NOT EXISTS tags (name TEXT PRIMARY KEY, description TEXT DEFAULT '')`);
        db.run(`CREATE TABLE IF NOT EXISTS widgets (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            description TEXT DEFAULT '',
            bgColor TEXT DEFAULT '',
            icon TEXT DEFAULT '',
            filters TEXT DEFAULT '[]',
            functions TEXT DEFAULT '[]',
            enabledInDashboard INTEGER DEFAULT 1,
            sortOrder INTEGER DEFAULT 0,
            dtCreated TEXT,
            dtUpdated TEXT
        )`);
    }

    // ========== Row Conversion ==========

    function entryToParams(entry) {
        return [
            entry.id,
            entry.date || null,
            entry.time || null,
            entry.title || '',
            entry.content || '',
            entry.richContent || '',
            JSON.stringify(entry.categories || []),
            JSON.stringify(entry.tags || []),
            entry.placeName || '',
            JSON.stringify(entry.locations || []),
            entry.weather ? JSON.stringify(entry.weather) : null,
            entry.pinned ? 1 : 0,
            entry.dtCreated || null,
            entry.dtUpdated || null,
            JSON.stringify(entry.people || [])
        ];
    }

    function rowToEntry(cols, values) {
        const obj = {};
        cols.forEach((col, i) => { obj[col] = values[i]; });
        return {
            id: obj.id,
            date: obj.date || '',
            time: obj.time || '',
            title: obj.title || '',
            content: obj.content || '',
            richContent: obj.richContent || '',
            categories: safeJsonParse(obj.categories, []),
            tags: safeJsonParse(obj.tags, []),
            placeName: obj.placeName || '',
            locations: safeJsonParse(obj.locations, []),
            weather: safeJsonParse(obj.weather, null),
            pinned: obj.pinned === 1,
            dtCreated: obj.dtCreated || '',
            dtUpdated: obj.dtUpdated || '',
            people: safeJsonParse(obj.people, []),
            images: [] // populated separately
        };
    }

    function safeJsonParse(str, fallback) {
        if (str == null || str === '') return fallback;
        try { return JSON.parse(str); } catch { return fallback; }
    }

    // ========== IndexedDB Persistence ==========

    function openIDB() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(IDB_NAME, 2);
            request.onupgradeneeded = (e) => {
                const db = e.target.result;
                if (!db.objectStoreNames.contains(STORE_NAME)) {
                    db.createObjectStore(STORE_NAME);
                }
                if (!db.objectStoreNames.contains('bootstrap')) {
                    db.createObjectStore('bootstrap');
                }
            };
            request.onsuccess = (e) => resolve(e.target.result);
            request.onerror = (e) => reject(e.target.error);
        });
    }

    function dataKey(journalId) {
        return 'journal_data_' + (journalId || currentJournalId);
    }

    async function persistToIndexedDB() {
        if (!sqlDB || !currentPassword || !currentJournalId) return;
        const bytes = sqlDB.export();
        const encrypted = await Crypto.encryptBytes(new Uint8Array(bytes), currentPassword, currentJournalId);
        const idb = await openIDB();
        return new Promise((resolve, reject) => {
            const tx = idb.transaction(STORE_NAME, 'readwrite');
            const store = tx.objectStore(STORE_NAME);
            const request = store.put(encrypted, dataKey());
            request.onsuccess = () => resolve();
            request.onerror = (e) => reject(e.target.error);
        });
    }

    // ========== Default Data ==========

    function getDefaultData() {
        return {
            entries: [],
            categories: ['Personal', 'Work', 'Travel', 'Health', 'Finance', 'Ideas'],
            settings: { theme: 'light' }
        };
    }

    function createDefaultDB() {
        const db = new SQL.Database();
        db.run('PRAGMA foreign_keys = ON');
        createSchema(db);
        const defaults = getDefaultData();
        for (const cat of defaults.categories) {
            db.run('INSERT OR IGNORE INTO categories (name) VALUES (?)', [cat]);
        }
        for (const [key, val] of Object.entries(defaults.settings)) {
            db.run('INSERT OR REPLACE INTO settings VALUES (?, ?)', [key, JSON.stringify(val)]);
        }
        return db;
    }

    // ========== Migration from Old JSON Format ==========

    async function migrateFromJSON(encrypted, password, journalId) {
        const data = await Crypto.decrypt(encrypted, password, journalId);
        const db = new SQL.Database();
        db.run('PRAGMA foreign_keys = ON');
        createSchema(db);

        // Insert entries
        const entrySQL = `INSERT INTO entries VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`;
        const imgSQL = `INSERT INTO images VALUES (?,?,?,?,?,?)`;
        for (const entry of (data.entries || [])) {
            db.run(entrySQL, entryToParams(entry));
            if (entry.images && entry.images.length > 0) {
                entry.images.forEach((img, idx) => {
                    db.run(imgSQL, [
                        img.id || (entry.id + '_img_' + idx),
                        entry.id,
                        img.name || '',
                        img.data || '',
                        img.thumb || '',
                        idx
                    ]);
                });
            }
        }

        // Insert categories
        for (const cat of (data.categories || [])) {
            db.run('INSERT OR IGNORE INTO categories (name) VALUES (?)', [cat]);
        }

        // Insert settings
        for (const [key, val] of Object.entries(data.settings || {})) {
            db.run('INSERT OR REPLACE INTO settings VALUES (?, ?)', [key, JSON.stringify(val)]);
        }

        return db;
    }

    // ========== Core Load/Save ==========

    async function loadAll(password, journalId) {
        const pw = password || currentPassword;
        const jid = journalId || currentJournalId;
        await initSqlJs();

        const idb = await openIDB();
        const encrypted = await new Promise((resolve, reject) => {
            const tx = idb.transaction(STORE_NAME, 'readonly');
            const store = tx.objectStore(STORE_NAME);
            const request = store.get(dataKey(jid));
            request.onsuccess = (e) => resolve(e.target.result);
            request.onerror = (e) => reject(e.target.error);
        });

        if (!encrypted) {
            // New journal — create default DB
            sqlDB = createDefaultDB();
            await persistToIndexedDBWith(pw, jid);
            return getDefaultData();
        }

        if (encrypted.format === 'sqlite') {
            // New format: decrypt bytes, open with sql.js
            const bytes = await Crypto.decryptBytes(encrypted, pw, jid);
            sqlDB = new SQL.Database(bytes);
            sqlDB.run('PRAGMA foreign_keys = ON');
        } else {
            // Old JSON format: migrate
            sqlDB = await migrateFromJSON(encrypted, pw, jid);
            // Persist the migrated DB in new format
            await persistToIndexedDBWith(pw, jid);
        }

        // Ensure new tables exist on older databases
        upgradeSchema(sqlDB);

        // Return data in the old shape for compatibility with callers
        return getCachedCompat();
    }

    // Persist with explicit credentials (used during migration/initial setup)
    async function persistToIndexedDBWith(pw, jid) {
        if (!sqlDB) return;
        const bytes = sqlDB.export();
        const encrypted = await Crypto.encryptBytes(new Uint8Array(bytes), pw, jid);
        const idb = await openIDB();
        return new Promise((resolve, reject) => {
            const tx = idb.transaction(STORE_NAME, 'readwrite');
            const store = tx.objectStore(STORE_NAME);
            const request = store.put(encrypted, dataKey(jid));
            request.onsuccess = () => resolve();
            request.onerror = (e) => reject(e.target.error);
        });
    }

    async function saveAll(data, password, journalId) {
        const pw = password || currentPassword;
        const jid = journalId || currentJournalId;
        await initSqlJs();

        // Build a fresh DB from the data object
        sqlDB = new SQL.Database();
        sqlDB.run('PRAGMA foreign_keys = ON');
        createSchema(sqlDB);

        const entrySQL = `INSERT INTO entries VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`;
        const imgSQL = `INSERT INTO images VALUES (?,?,?,?,?,?)`;
        for (const entry of (data.entries || [])) {
            sqlDB.run(entrySQL, entryToParams(entry));
            if (entry.images && entry.images.length > 0) {
                entry.images.forEach((img, idx) => {
                    sqlDB.run(imgSQL, [
                        img.id || (entry.id + '_img_' + idx),
                        entry.id,
                        img.name || '',
                        img.data || '',
                        img.thumb || '',
                        idx
                    ]);
                });
            }
        }
        for (const cat of (data.categories || [])) {
            sqlDB.run('INSERT OR IGNORE INTO categories (name) VALUES (?)', [cat]);
        }
        for (const [key, val] of Object.entries(data.settings || {})) {
            sqlDB.run('INSERT OR REPLACE INTO settings VALUES (?, ?)', [key, JSON.stringify(val)]);
        }

        await persistToIndexedDBWith(pw, jid);
    }

    async function deleteJournalData(journalId) {
        const idb = await openIDB();
        return new Promise((resolve, reject) => {
            const tx = idb.transaction(STORE_NAME, 'readwrite');
            const store = tx.objectStore(STORE_NAME);
            const request = store.delete(dataKey(journalId));
            request.onsuccess = () => resolve();
            request.onerror = (e) => reject(e.target.error);
        });
    }

    async function save() {
        await persistToIndexedDB();
    }

    // ========== Compatibility: getCached returns old-shape object ==========

    function getCached() {
        return getCachedCompat();
    }

    function getCachedCompat() {
        if (!sqlDB) return null;
        return {
            entries: getEntries(),
            categories: getCategories(),
            settings: getSettings()
        };
    }

    // ========== Entry Operations ==========

    function getEntries() {
        if (!sqlDB) return [];
        const results = sqlDB.exec('SELECT * FROM entries ORDER BY date DESC, time DESC');
        if (!results.length) return [];
        const cols = results[0].columns;
        const entries = results[0].values.map(row => rowToEntry(cols, row));

        // Attach thumbnail stubs (no full image data)
        const thumbResults = sqlDB.exec('SELECT id, entryId, name, thumb FROM images ORDER BY sortOrder');
        if (thumbResults.length) {
            const thumbMap = {};
            const thumbCols = thumbResults[0].columns;
            for (const row of thumbResults[0].values) {
                const entryId = row[thumbCols.indexOf('entryId')];
                if (!thumbMap[entryId]) thumbMap[entryId] = [];
                thumbMap[entryId].push({
                    id: row[thumbCols.indexOf('id')],
                    name: row[thumbCols.indexOf('name')],
                    thumb: row[thumbCols.indexOf('thumb')]
                });
            }
            for (const entry of entries) {
                entry.images = thumbMap[entry.id] || [];
            }
        }

        return entries;
    }

    function getEntryById(id) {
        if (!sqlDB) return null;
        const results = sqlDB.exec('SELECT * FROM entries WHERE id = ?', [id]);
        if (!results.length || !results[0].values.length) return null;
        const entry = rowToEntry(results[0].columns, results[0].values[0]);
        // Load full images
        loadEntryImagesSync(entry);
        return entry;
    }

    function loadEntryImages(entry) {
        if (!sqlDB || !entry) return;
        const results = sqlDB.exec('SELECT * FROM images WHERE entryId = ? ORDER BY sortOrder', [entry.id]);
        if (results.length && results[0].values.length) {
            const cols = results[0].columns;
            entry.images = results[0].values.map(row => {
                const obj = {};
                cols.forEach((col, i) => { obj[col] = row[i]; });
                return { id: obj.id, name: obj.name, data: obj.data, thumb: obj.thumb };
            });
        } else {
            entry.images = [];
        }
    }

    // Alias for internal use
    function loadEntryImagesSync(entry) { loadEntryImages(entry); }

    function addEntry(entry) {
        if (!sqlDB) return;
        const sql = `INSERT INTO entries VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`;
        sqlDB.run(sql, entryToParams(entry));

        // Insert images
        if (entry.images && entry.images.length > 0) {
            const imgSQL = `INSERT INTO images VALUES (?,?,?,?,?,?)`;
            entry.images.forEach((img, idx) => {
                sqlDB.run(imgSQL, [
                    img.id || (entry.id + '_img_' + idx),
                    entry.id,
                    img.name || '',
                    img.data || '',
                    img.thumb || '',
                    idx
                ]);
            });
        }

        return save();
    }

    function updateEntry(id, updated) {
        if (!sqlDB) return;

        // Build SET clause dynamically from updated fields
        const fieldMap = {
            date: 'date', time: 'time', title: 'title', content: 'content',
            richContent: 'richContent', placeName: 'placeName',
            pinned: 'pinned', dtCreated: 'dtCreated', dtUpdated: 'dtUpdated'
        };
        const jsonFields = { categories: true, tags: true, locations: true, people: true };
        const objectFields = { weather: true };

        const setClauses = [];
        const params = [];

        for (const [key, val] of Object.entries(updated)) {
            if (key === 'images') continue; // handled separately
            if (key === 'id') continue;
            if (fieldMap[key]) {
                setClauses.push(`${fieldMap[key]} = ?`);
                params.push(key === 'pinned' ? (val ? 1 : 0) : (val ?? null));
            } else if (jsonFields[key]) {
                setClauses.push(`${key} = ?`);
                params.push(JSON.stringify(val || []));
            } else if (objectFields[key]) {
                setClauses.push(`${key} = ?`);
                params.push(val ? JSON.stringify(val) : null);
            }
        }

        if (setClauses.length > 0) {
            params.push(id);
            sqlDB.run(`UPDATE entries SET ${setClauses.join(', ')} WHERE id = ?`, params);
        }

        // Sync images if provided
        if (updated.images !== undefined) {
            syncEntryImages(id, updated.images);
        }

        return save();
    }

    function syncEntryImages(entryId, images) {
        // Delete existing images for this entry, re-insert
        sqlDB.run('DELETE FROM images WHERE entryId = ?', [entryId]);
        if (images && images.length > 0) {
            const imgSQL = `INSERT INTO images VALUES (?,?,?,?,?,?)`;
            images.forEach((img, idx) => {
                sqlDB.run(imgSQL, [
                    img.id || (entryId + '_img_' + idx),
                    entryId,
                    img.name || '',
                    img.data || '',
                    img.thumb || '',
                    idx
                ]);
            });
        }
    }

    function deleteEntryById(id) {
        if (!sqlDB) return;
        sqlDB.run('DELETE FROM images WHERE entryId = ?', [id]);
        sqlDB.run('DELETE FROM entries WHERE id = ?', [id]);
        return save();
    }

    function deleteEntriesByIds(ids) {
        if (!sqlDB || !ids || ids.length === 0) return;
        const placeholders = ids.map(() => '?').join(',');
        sqlDB.run(`DELETE FROM images WHERE entryId IN (${placeholders})`, ids);
        sqlDB.run(`DELETE FROM entries WHERE id IN (${placeholders})`, ids);
        return save();
    }

    // ========== Categories ==========

    function getCategories() {
        if (!sqlDB) return [];
        const results = sqlDB.exec('SELECT name FROM categories ORDER BY name');
        if (!results.length) return [];
        return results[0].values.map(row => row[0]);
    }

    function getCategoriesWithDesc() {
        if (!sqlDB) return [];
        const results = sqlDB.exec('SELECT name, description FROM categories ORDER BY name');
        if (!results.length) return [];
        return results[0].values.map(row => ({ name: row[0], description: row[1] || '' }));
    }

    function setCategories(cats) {
        if (!sqlDB) return;
        // Preserve existing descriptions when replacing category list
        const existing = {};
        const prev = sqlDB.exec('SELECT name, description FROM categories');
        if (prev.length) prev[0].values.forEach(row => { existing[row[0]] = row[1] || ''; });
        sqlDB.run('DELETE FROM categories');
        for (const cat of cats) {
            const desc = existing[cat] || '';
            sqlDB.run('INSERT OR IGNORE INTO categories (name, description) VALUES (?, ?)', [cat, desc]);
        }
        return save();
    }

    function setCategoryDescription(name, description) {
        if (!sqlDB) return;
        sqlDB.run('UPDATE categories SET description = ? WHERE name = ?', [description || '', name]);
        return save();
    }

    // ========== Icons ==========

    function getAllIcons() {
        if (!sqlDB) return [];
        const results = sqlDB.exec('SELECT type, name, data FROM icons ORDER BY type, name');
        if (!results.length) return [];
        return results[0].values.map(row => ({ type: row[0], name: row[1], data: row[2] }));
    }

    function getIcon(type, name) {
        if (!sqlDB) return null;
        const results = sqlDB.exec('SELECT data FROM icons WHERE type = ? AND name = ?', [type, name]);
        if (!results.length || !results[0].values.length) return null;
        return results[0].values[0][0];
    }

    function setIcon(type, name, data) {
        if (!sqlDB) return;
        sqlDB.run('INSERT OR REPLACE INTO icons VALUES (?, ?, ?)', [type, name, data]);
        return save();
    }

    function removeIcon(type, name) {
        if (!sqlDB) return;
        sqlDB.run('DELETE FROM icons WHERE type = ? AND name = ?', [type, name]);
        return save();
    }

    // ========== People ==========

    function getPeople() {
        if (!sqlDB) return [];
        const results = sqlDB.exec('SELECT firstName, lastName, description FROM people ORDER BY firstName, lastName');
        if (!results.length) return [];
        return results[0].values.map(row => ({ firstName: row[0], lastName: row[1], description: row[2] || '' }));
    }

    function addPerson(firstName, lastName, description) {
        if (!sqlDB) return;
        sqlDB.run('INSERT OR REPLACE INTO people VALUES (?, ?, ?)', [firstName, lastName, description || '']);
        return save();
    }

    function updatePerson(oldFirstName, oldLastName, newFirstName, newLastName, description) {
        if (!sqlDB) return;
        // If name changed, delete old record and insert new one
        if (oldFirstName !== newFirstName || oldLastName !== newLastName) {
            sqlDB.run('DELETE FROM people WHERE firstName = ? AND lastName = ?', [oldFirstName, oldLastName]);
            // Update references in entries
            const entries = sqlDB.exec('SELECT id, people FROM entries WHERE people IS NOT NULL AND people != "[]"');
            if (entries.length) {
                for (const row of entries[0].values) {
                    const people = safeJsonParse(row[1], []);
                    let changed = false;
                    for (let i = 0; i < people.length; i++) {
                        if (people[i].firstName === oldFirstName && people[i].lastName === oldLastName) {
                            people[i] = { firstName: newFirstName, lastName: newLastName };
                            changed = true;
                        }
                    }
                    if (changed) {
                        sqlDB.run('UPDATE entries SET people = ? WHERE id = ?', [JSON.stringify(people), row[0]]);
                    }
                }
            }
        }
        sqlDB.run('INSERT OR REPLACE INTO people VALUES (?, ?, ?)', [newFirstName, newLastName, description || '']);
        return save();
    }

    function deletePerson(firstName, lastName) {
        if (!sqlDB) return;
        sqlDB.run('DELETE FROM people WHERE firstName = ? AND lastName = ?', [firstName, lastName]);
        return save();
    }

    // ========== Settings ==========

    function getSettings() {
        if (!sqlDB) return {};
        const results = sqlDB.exec('SELECT key, value FROM settings');
        if (!results.length) return {};
        const settings = {};
        for (const row of results[0].values) {
            settings[row[0]] = safeJsonParse(row[1], row[1]);
        }
        return settings;
    }

    function setSettings(newSettings) {
        if (!sqlDB) return;
        for (const [key, val] of Object.entries(newSettings)) {
            sqlDB.run('INSERT OR REPLACE INTO settings VALUES (?, ?)', [key, JSON.stringify(val)]);
        }
        return save();
    }

    // ========== Tags & Places ==========

    function getAllTags() {
        if (!sqlDB) return [];
        const results = sqlDB.exec('SELECT tags FROM entries WHERE tags IS NOT NULL AND tags != "[]"');
        const tags = new Set();
        if (results.length) {
            for (const row of results[0].values) {
                const parsed = safeJsonParse(row[0], []);
                parsed.forEach(t => tags.add(t));
            }
        }
        return [...tags].sort();
    }

    function getAllPlaceNames() {
        if (!sqlDB) return [];
        const results = sqlDB.exec("SELECT DISTINCT placeName FROM entries WHERE placeName IS NOT NULL AND placeName != ''");
        if (!results.length) return [];
        return results[0].values.map(row => row[0]).sort();
    }

    // ========== Tag Descriptions ==========

    function getTagDescriptions() {
        if (!sqlDB) return {};
        const results = sqlDB.exec('SELECT name, description FROM tags');
        const map = {};
        if (results.length) {
            for (const row of results[0].values) {
                if (row[1]) map[row[0]] = row[1];
            }
        }
        return map;
    }

    function setTagDescription(name, description) {
        if (!sqlDB) return;
        if (description) {
            sqlDB.run('INSERT OR REPLACE INTO tags (name, description) VALUES (?, ?)', [name, description]);
        } else {
            sqlDB.run('DELETE FROM tags WHERE name = ?', [name]);
        }
        return save();
    }

    // ========== Export/Import (Encrypted SQLite) ==========

    async function exportJSON() {
        if (!sqlDB || !currentPassword || !currentJournalId) return null;
        const bytes = sqlDB.export();
        const encrypted = await Crypto.encryptBytes(new Uint8Array(bytes), currentPassword, currentJournalId);
        return JSON.stringify({ journalId: currentJournalId, encrypted }, null, 2);
    }

    async function importJSON(jsonStr) {
        await initSqlJs();
        const parsed = JSON.parse(jsonStr);
        const encrypted = parsed.encrypted || parsed;

        let db;
        if (encrypted.format === 'sqlite') {
            // New format: encrypted SQLite bytes
            const bytes = await Crypto.decryptBytes(encrypted, currentPassword, currentJournalId);
            db = new SQL.Database(bytes);
            db.run('PRAGMA foreign_keys = ON');
        } else {
            // Old JSON format: migrate
            db = await migrateFromJSON(encrypted, currentPassword, currentJournalId);
        }

        sqlDB = db;
        upgradeSchema(sqlDB);
        await save();
        return getCachedCompat();
    }

    // ========== Database Statistics ==========

    function getDBStats() {
        if (!sqlDB) return null;
        const stats = { tables: {} };

        // DB file size (export to get byte count)
        const bytes = sqlDB.export();
        stats.fileSize = bytes.length;
        stats.journalId = currentJournalId || '';

        // Entry count
        const entryResult = sqlDB.exec('SELECT COUNT(*) FROM entries');
        stats.tables.entries = { rows: entryResult.length ? entryResult[0].values[0][0] : 0 };

        // Image count and total data size
        const imgCountResult = sqlDB.exec('SELECT COUNT(*) FROM images');
        const imgCount = imgCountResult.length ? imgCountResult[0].values[0][0] : 0;
        const imgSizeResult = sqlDB.exec('SELECT COALESCE(SUM(LENGTH(data) + LENGTH(thumb)), 0) FROM images');
        const imgSize = imgSizeResult.length ? imgSizeResult[0].values[0][0] : 0;
        stats.tables.images = { rows: imgCount, dataSize: imgSize };

        // Category count
        const catResult = sqlDB.exec('SELECT COUNT(*) FROM categories');
        stats.tables.categories = { rows: catResult.length ? catResult[0].values[0][0] : 0 };

        // Settings count
        const setResult = sqlDB.exec('SELECT COUNT(*) FROM settings');
        stats.tables.settings = { rows: setResult.length ? setResult[0].values[0][0] : 0 };

        // Date range
        const dateRange = sqlDB.exec("SELECT MIN(date), MAX(date) FROM entries WHERE date IS NOT NULL AND date != ''");
        if (dateRange.length && dateRange[0].values[0][0]) {
            stats.dateRange = { earliest: dateRange[0].values[0][0], latest: dateRange[0].values[0][1] };
        }

        return stats;
    }

    // Export raw SQLite bytes (for future unencrypted export)
    function exportSQLiteBytes() {
        if (!sqlDB) return null;
        return sqlDB.export();
    }

    // Import raw SQLite bytes
    async function importSQLiteBytes(bytes) {
        await initSqlJs();
        const db = new SQL.Database(new Uint8Array(bytes));
        db.run('PRAGMA foreign_keys = ON');
        // Validate schema
        const tables = db.exec("SELECT name FROM sqlite_master WHERE type='table'");
        if (!tables.length) throw new Error('Invalid SQLite file: no tables found');
        const tableNames = tables[0].values.map(r => r[0]);
        if (!tableNames.includes('entries')) throw new Error('Invalid SQLite file: missing entries table');
        sqlDB = db;
        upgradeSchema(sqlDB);
        await save();
        return getCachedCompat();
    }

    // ========== Widgets ==========

    function getWidgets() {
        if (!sqlDB) return [];
        const results = sqlDB.exec('SELECT * FROM widgets ORDER BY sortOrder, name');
        if (!results.length) return [];
        return results[0].values.map(r => {
            const cols = results[0].columns;
            const obj = {};
            cols.forEach((c, i) => obj[c] = r[i]);
            obj.filters = safeJsonParse(obj.filters, []);
            obj.functions = safeJsonParse(obj.functions, []);
            obj.enabledInDashboard = !!obj.enabledInDashboard;
            return obj;
        });
    }

    function getWidgetById(id) {
        if (!sqlDB) return null;
        const results = sqlDB.exec('SELECT * FROM widgets WHERE id = ?', [id]);
        if (!results.length || !results[0].values.length) return null;
        const cols = results[0].columns;
        const r = results[0].values[0];
        const obj = {};
        cols.forEach((c, i) => obj[c] = r[i]);
        obj.filters = safeJsonParse(obj.filters, []);
        obj.functions = safeJsonParse(obj.functions, []);
        obj.enabledInDashboard = !!obj.enabledInDashboard;
        return obj;
    }

    function saveWidget(widget) {
        if (!sqlDB) return;
        const now = new Date().toISOString();
        sqlDB.run(`INSERT OR REPLACE INTO widgets (id, name, description, bgColor, icon, filters, functions, enabledInDashboard, sortOrder, dtCreated, dtUpdated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`, [
            widget.id,
            widget.name || '',
            widget.description || '',
            widget.bgColor || '',
            widget.icon || '',
            JSON.stringify(widget.filters || []),
            JSON.stringify(widget.functions || []),
            widget.enabledInDashboard ? 1 : 0,
            widget.sortOrder || 0,
            widget.dtCreated || now,
            now
        ]);
        return save();
    }

    function deleteWidget(id) {
        if (!sqlDB) return;
        sqlDB.run('DELETE FROM widgets WHERE id = ?', [id]);
        return save();
    }

    return {
        setPassword, getPassword, setJournalId, getJournalId,
        getJournalList, saveJournalList, addJournalToList, removeJournalFromList, generateJournalId,
        loadAll, saveAll, deleteJournalData, save, getCached, getDefaultData,
        getEntries, getEntryById, getCategories, getCategoriesWithDesc, getSettings,
        addEntry, updateEntry, deleteEntryById, deleteEntriesByIds,
        setCategories, setCategoryDescription, setSettings,
        getAllIcons, getIcon, setIcon, removeIcon,
        getPeople, addPerson, updatePerson, deletePerson,
        getWidgets, getWidgetById, saveWidget, deleteWidget,
        getAllTags, getAllPlaceNames, getTagDescriptions, setTagDescription,
        loadEntryImages,
        exportJSON, importJSON,
        exportSQLiteBytes, importSQLiteBytes,
        getDBStats,
        execRawSQL
    };

    function execRawSQL(sql, params) {
        if (!sqlDB) return [];
        return sqlDB.exec(sql, params || []);
    }
})();
