/**
 * Bootstrap module: IndexedDB-backed key-value store with synchronous in-memory cache.
 * Replaces all localStorage usage in the app.
 *
 * Usage:
 *   await Bootstrap.init();          // call once at startup (migrates from localStorage)
 *   Bootstrap.get('key');            // synchronous read from cache
 *   Bootstrap.set('key', 'value');   // synchronous cache update + async IndexedDB write
 *   Bootstrap.remove('key');         // synchronous remove + async IndexedDB delete
 */

const Bootstrap = (() => {
    const IDB_NAME = 'JournalDB';
    const STORE_NAME = 'bootstrap';
    const IDB_VERSION = 2; // bumped from 1 to add bootstrap store

    let _cache = {};       // in-memory cache: { key: value }
    let _ready = false;
    let _db = null;        // IDB database handle

    // ========== Known localStorage keys to migrate ==========

    const MIGRATE_KEYS = [
        // Crypto
        /^journal_salt_/,
        /^journal_verify_/,
        // Journal list
        'journal_list',
        // Login / session
        'auto_open_last_journal',
        'last_journal_id',
        'auto_biometric',
        // UI prefs
        'settingsTab',
        'entry_view_mode',
        'entries_page_size',
        'default_entry_sort_field',
        'default_entry_sort_dir',
        'warn_before_delete',
        'warn_unsaved_entry',
        'ev_misc_collapsed',
        'ev_font_family',
        'ev_font_size',
        'ev_date_format',
        'ev_time_format',
        'max_pinned_entries',
        'max_ranking_entries',
        'geocoding_provider',
        'geocoding_api_key',
        'journal_warn_delete',
        // Column toggles (col_*)
        /^col_/,
        // RankedPanel view modes (rp_view_*)
        /^rp_view_/,
        // CollapsiblePanel pins
        /^cp_pins_/,
    ];

    function _keyMatches(key) {
        for (const pattern of MIGRATE_KEYS) {
            if (pattern instanceof RegExp) {
                if (pattern.test(key)) return true;
            } else if (key === pattern) {
                return true;
            }
        }
        return false;
    }

    // ========== IndexedDB ==========

    function _openIDB() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(IDB_NAME, IDB_VERSION);
            request.onupgradeneeded = (e) => {
                const db = e.target.result;
                // Existing store from DB module
                if (!db.objectStoreNames.contains('encrypted_data')) {
                    db.createObjectStore('encrypted_data');
                }
                // New bootstrap store
                if (!db.objectStoreNames.contains(STORE_NAME)) {
                    db.createObjectStore(STORE_NAME);
                }
            };
            request.onsuccess = (e) => resolve(e.target.result);
            request.onerror = (e) => reject(e.target.error);
        });
    }

    function _idbPut(key, value) {
        if (!_db) return;
        try {
            const tx = _db.transaction(STORE_NAME, 'readwrite');
            tx.objectStore(STORE_NAME).put(value, key);
        } catch (e) {
            console.error('Bootstrap: IDB put failed for', key, e);
        }
    }

    function _idbDelete(key) {
        if (!_db) return;
        try {
            const tx = _db.transaction(STORE_NAME, 'readwrite');
            tx.objectStore(STORE_NAME).delete(key);
        } catch (e) {
            console.error('Bootstrap: IDB delete failed for', key, e);
        }
    }

    async function _idbGetAll() {
        if (!_db) return {};
        return new Promise((resolve, reject) => {
            const tx = _db.transaction(STORE_NAME, 'readonly');
            const store = tx.objectStore(STORE_NAME);
            const keys = store.getAllKeys();
            const values = store.getAll();
            tx.oncomplete = () => {
                const result = {};
                for (let i = 0; i < keys.result.length; i++) {
                    result[keys.result[i]] = values.result[i];
                }
                resolve(result);
            };
            tx.onerror = (e) => reject(e.target.error);
        });
    }

    // ========== Migration ==========

    function _migrateFromLocalStorage() {
        let migrated = 0;
        const len = localStorage.length;
        for (let i = 0; i < len; i++) {
            const key = localStorage.key(i);
            if (key && _keyMatches(key)) {
                const val = localStorage.getItem(key);
                if (val !== null && _cache[key] === undefined) {
                    _cache[key] = val;
                    _idbPut(key, val);
                    migrated++;
                }
            }
        }
        if (migrated > 0) {
            console.log(`Bootstrap: migrated ${migrated} keys from localStorage`);
            // Clear migrated keys from localStorage
            for (let i = localStorage.length - 1; i >= 0; i--) {
                const key = localStorage.key(i);
                if (key && _keyMatches(key)) {
                    localStorage.removeItem(key);
                }
            }
        }
    }

    // ========== Public API ==========

    async function init() {
        if (_ready) return;
        _db = await _openIDB();

        // Load all existing bootstrap data into cache
        _cache = await _idbGetAll();

        // Migrate any remaining localStorage data
        _migrateFromLocalStorage();

        _ready = true;
    }

    function get(key) {
        return _cache[key] !== undefined ? _cache[key] : null;
    }

    function set(key, value) {
        if (value === null || value === undefined) {
            return remove(key);
        }
        _cache[key] = value;
        _idbPut(key, value);
    }

    function remove(key) {
        delete _cache[key];
        _idbDelete(key);
    }

    function has(key) {
        return _cache[key] !== undefined;
    }

    // Convenience: get parsed int with default
    function getInt(key, defaultVal) {
        const v = _cache[key];
        if (v === undefined || v === null) return defaultVal;
        const n = parseInt(v);
        return isNaN(n) ? defaultVal : n;
    }

    function isReady() {
        return _ready;
    }

    return { init, get, set, remove, has, getInt, isReady };
})();
