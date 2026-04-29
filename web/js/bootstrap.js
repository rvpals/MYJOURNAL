/**
 * Bootstrap module: key-value store with synchronous in-memory cache.
 * On Android, delegates to native SharedPreferences via AndroidBridge.
 * In browser, uses IndexedDB as backing store.
 *
 * Usage:
 *   await Bootstrap.init();          // call once at startup
 *   Bootstrap.get('key');            // synchronous read from cache
 *   Bootstrap.set('key', 'value');   // synchronous cache update + persistent write
 *   Bootstrap.remove('key');         // synchronous remove + persistent delete
 */

const Bootstrap = (() => {
    const IDB_NAME = 'JournalDB';
    const STORE_NAME = 'bootstrap';
    const IDB_VERSION = 2;

    let _cache = {};
    let _ready = false;
    let _db = null;
    let _useNative = false;

    // ========== Known localStorage keys to migrate ==========

    const MIGRATE_KEYS = [
        /^journal_salt_/,
        /^journal_verify_/,
        'journal_list',
        'auto_open_last_journal',
        'last_journal_id',
        'auto_biometric',
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
        /^col_/,
        /^rp_view_/,
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

    // ========== Native Bridge ==========

    function _hasNativeBridge() {
        return typeof window !== 'undefined' &&
            window.AndroidBridge &&
            typeof AndroidBridge.bootstrapGet === 'function';
    }

    // ========== IndexedDB (browser fallback) ==========

    function _openIDB() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(IDB_NAME, IDB_VERSION);
            request.onupgradeneeded = (e) => {
                const db = e.target.result;
                if (!db.objectStoreNames.contains('encrypted_data')) {
                    db.createObjectStore('encrypted_data');
                }
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
                    if (_useNative) {
                        AndroidBridge.bootstrapSet(key, val);
                    } else {
                        _idbPut(key, val);
                    }
                    migrated++;
                }
            }
        }
        if (migrated > 0) {
            console.log(`Bootstrap: migrated ${migrated} keys from localStorage`);
            for (let i = localStorage.length - 1; i >= 0; i--) {
                const key = localStorage.key(i);
                if (key && _keyMatches(key)) {
                    localStorage.removeItem(key);
                }
            }
        }
    }

    async function _migrateIdbToNative() {
        if (!AndroidBridge.bootstrapIsEmpty()) return;
        try {
            const tmpDb = await _openIDB();
            const idbData = await new Promise((resolve, reject) => {
                const tx = tmpDb.transaction(STORE_NAME, 'readonly');
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
            tmpDb.close();

            if (Object.keys(idbData).length > 0) {
                AndroidBridge.bootstrapSetAll(JSON.stringify(idbData));
                console.log('Bootstrap: migrated ' + Object.keys(idbData).length + ' keys from IDB to native');
            }
        } catch (e) {
            console.error('Bootstrap: IDB-to-native migration failed', e);
        }
    }

    // ========== Public API ==========

    async function init() {
        if (_ready) return;

        _useNative = _hasNativeBridge();

        if (_useNative) {
            await _migrateIdbToNative();
            const allJson = AndroidBridge.bootstrapGetAll();
            _cache = JSON.parse(allJson || '{}');
        } else {
            _db = await _openIDB();
            _cache = await _idbGetAll();
        }

        _migrateFromLocalStorage();
        _ready = true;
    }

    function get(key) {
        if (_useNative) {
            const val = AndroidBridge.bootstrapGet(key);
            if (val !== null && val !== undefined) {
                _cache[key] = val;
            }
            return val !== undefined ? val : null;
        }
        return _cache[key] !== undefined ? _cache[key] : null;
    }

    function set(key, value) {
        if (value === null || value === undefined) {
            return remove(key);
        }
        _cache[key] = value;
        if (_useNative) {
            AndroidBridge.bootstrapSet(key, value);
        } else {
            _idbPut(key, value);
        }
    }

    function remove(key) {
        delete _cache[key];
        if (_useNative) {
            AndroidBridge.bootstrapRemove(key);
        } else {
            _idbDelete(key);
        }
    }

    function has(key) {
        if (_useNative) {
            return AndroidBridge.bootstrapHas(key);
        }
        return _cache[key] !== undefined;
    }

    function getInt(key, defaultVal) {
        if (_useNative) {
            return AndroidBridge.bootstrapGetInt(key, defaultVal);
        }
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
