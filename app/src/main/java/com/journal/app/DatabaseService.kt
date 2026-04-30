package com.journal.app

import android.content.ContentValues
import android.content.Context
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DatabaseService(private val context: Context) {

    companion object {
        private const val TAG = "DatabaseService"
        private const val SCHEMA_VERSION = 2
        private const val DB_VERSION = 1
    }

    private var db: SQLiteDatabase? = null
    private var currentPassword: String? = null
    private var currentJournalId: String? = null

    // ========== Open / Close ==========

    fun open(password: String, journalId: String): Boolean {
        close()
        currentPassword = password
        currentJournalId = journalId
        System.loadLibrary("sqlcipher")
        val dbFile = dbPath(journalId)
        dbFile.parentFile?.mkdirs()
        val isNew = !dbFile.exists()
        db = SQLiteDatabase.openOrCreateDatabase(dbFile.absolutePath, password, null, null, null)
        db?.execSQL("PRAGMA foreign_keys = ON")
        if (isNew) {
            createSchema()
            insertDefaults()
        } else {
            upgradeSchema()
        }
        return true
    }

    fun close() {
        try { db?.close() } catch (_: Exception) {}
        db = null
        currentPassword = null
        currentJournalId = null
    }

    fun isOpen(): Boolean = db != null

    fun hasNativeDB(journalId: String): Boolean = dbPath(journalId).exists()

    private fun dbPath(journalId: String): File =
        context.getDatabasePath("journal_$journalId.db")

    fun deleteNativeDB(journalId: String) {
        val f = dbPath(journalId)
        if (f.exists()) f.delete()
        val journal = f.path + "-journal"
        if (File(journal).exists()) File(journal).delete()
        val wal = f.path + "-wal"
        if (File(wal).exists()) File(wal).delete()
        val shm = f.path + "-shm"
        if (File(shm).exists()) File(shm).delete()
    }

    // ========== Schema ==========

    private fun createSchema() {
        val d = db ?: return
        d.execSQL("""
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
                locked INTEGER DEFAULT 0,
                dtCreated TEXT,
                dtUpdated TEXT
            )
        """)
        d.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_date ON entries(date)")
        d.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_pinned ON entries(pinned)")

        d.execSQL("""
            CREATE TABLE IF NOT EXISTS images (
                id TEXT PRIMARY KEY,
                entryId TEXT NOT NULL,
                name TEXT,
                data TEXT,
                thumb TEXT,
                sortOrder INTEGER DEFAULT 0,
                FOREIGN KEY (entryId) REFERENCES entries(id) ON DELETE CASCADE
            )
        """)
        d.execSQL("CREATE INDEX IF NOT EXISTS idx_images_entry ON images(entryId)")

        d.execSQL("CREATE TABLE IF NOT EXISTS categories (name TEXT PRIMARY KEY, description TEXT DEFAULT '')")
        d.execSQL("CREATE TABLE IF NOT EXISTS tags (name TEXT PRIMARY KEY, description TEXT DEFAULT '')")
        d.execSQL("""
            CREATE TABLE IF NOT EXISTS icons (
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                data TEXT,
                PRIMARY KEY (type, name)
            )
        """)
        d.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)")
        d.execSQL("""
            CREATE TABLE IF NOT EXISTS widgets (
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
            )
        """)
        d.execSQL("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY)")
        d.execSQL("INSERT OR REPLACE INTO schema_version VALUES ($SCHEMA_VERSION)")
    }

    private fun upgradeSchema() {
        val d = db ?: return
        d.execSQL("CREATE TABLE IF NOT EXISTS icons (type TEXT NOT NULL, name TEXT NOT NULL, data TEXT, PRIMARY KEY (type, name))")
        try { d.execSQL("ALTER TABLE entries ADD COLUMN locked INTEGER DEFAULT 0") } catch (_: Exception) {}
        try { d.execSQL("ALTER TABLE categories ADD COLUMN description TEXT DEFAULT ''") } catch (_: Exception) {}
        d.execSQL("CREATE TABLE IF NOT EXISTS tags (name TEXT PRIMARY KEY, description TEXT DEFAULT '')")
        d.execSQL("""
            CREATE TABLE IF NOT EXISTS widgets (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT DEFAULT '',
                bgColor TEXT DEFAULT '', icon TEXT DEFAULT '',
                filters TEXT DEFAULT '[]', functions TEXT DEFAULT '[]',
                enabledInDashboard INTEGER DEFAULT 1, sortOrder INTEGER DEFAULT 0,
                dtCreated TEXT, dtUpdated TEXT
            )
        """)
    }

    private fun insertDefaults() {
        val d = db ?: return
        val defaults = listOf("Personal", "Work", "Travel", "Health", "Finance", "Ideas")
        for (cat in defaults) {
            d.execSQL("INSERT OR IGNORE INTO categories (name) VALUES (?)", arrayOf(cat))
        }
        d.execSQL("INSERT OR REPLACE INTO settings VALUES (?, ?)", arrayOf("theme", "\"light\""))
    }

    // ========== Migration from JS ==========

    fun migrateFromBytes(base64SqliteBytes: String, password: String, journalId: String): Boolean {
        try {
            val rawBytes = android.util.Base64.decode(base64SqliteBytes, android.util.Base64.NO_WRAP)
            val tempFile = File(context.cacheDir, "migrate_temp_$journalId.db")
            tempFile.writeBytes(rawBytes)

            val targetFile = dbPath(journalId)
            targetFile.parentFile?.mkdirs()

            System.loadLibrary("sqlcipher")
            val sourceDb = SQLiteDatabase.openDatabase(
                tempFile.absolutePath, "", null, SQLiteDatabase.OPEN_READONLY, null, null
            )

            if (targetFile.exists()) targetFile.delete()
            val targetDb = SQLiteDatabase.openOrCreateDatabase(targetFile.absolutePath, password, null, null, null)
            targetDb.execSQL("PRAGMA foreign_keys = ON")

            createSchemaOn(targetDb)
            copyAllData(sourceDb, targetDb)

            sourceDb.close()
            targetDb.close()
            tempFile.delete()

            Log.d(TAG, "Migration complete for journal $journalId")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed for journal $journalId", e)
            return false
        }
    }

    private fun createSchemaOn(d: SQLiteDatabase) {
        d.execSQL("""
            CREATE TABLE IF NOT EXISTS entries (
                id TEXT PRIMARY KEY, date TEXT, time TEXT, title TEXT,
                content TEXT, richContent TEXT, categories TEXT, tags TEXT,
                placeName TEXT, locations TEXT, weather TEXT,
                pinned INTEGER DEFAULT 0, locked INTEGER DEFAULT 0,
                dtCreated TEXT, dtUpdated TEXT
            )
        """)
        d.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_date ON entries(date)")
        d.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_pinned ON entries(pinned)")
        d.execSQL("""
            CREATE TABLE IF NOT EXISTS images (
                id TEXT PRIMARY KEY, entryId TEXT NOT NULL, name TEXT,
                data TEXT, thumb TEXT, sortOrder INTEGER DEFAULT 0,
                FOREIGN KEY (entryId) REFERENCES entries(id) ON DELETE CASCADE
            )
        """)
        d.execSQL("CREATE INDEX IF NOT EXISTS idx_images_entry ON images(entryId)")
        d.execSQL("CREATE TABLE IF NOT EXISTS categories (name TEXT PRIMARY KEY, description TEXT DEFAULT '')")
        d.execSQL("CREATE TABLE IF NOT EXISTS tags (name TEXT PRIMARY KEY, description TEXT DEFAULT '')")
        d.execSQL("CREATE TABLE IF NOT EXISTS icons (type TEXT NOT NULL, name TEXT NOT NULL, data TEXT, PRIMARY KEY (type, name))")
        d.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)")
        d.execSQL("""
            CREATE TABLE IF NOT EXISTS widgets (
                id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT DEFAULT '',
                bgColor TEXT DEFAULT '', icon TEXT DEFAULT '',
                filters TEXT DEFAULT '[]', functions TEXT DEFAULT '[]',
                enabledInDashboard INTEGER DEFAULT 1, sortOrder INTEGER DEFAULT 0,
                dtCreated TEXT, dtUpdated TEXT
            )
        """)
        d.execSQL("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY)")
        d.execSQL("INSERT OR REPLACE INTO schema_version VALUES ($SCHEMA_VERSION)")
    }

    private fun copyAllData(src: SQLiteDatabase, dst: SQLiteDatabase) {
        val tables = listOf("entries", "images", "categories", "tags", "icons", "settings", "widgets")
        for (table in tables) {
            try {
                val cursor = src.rawQuery("SELECT * FROM $table", null)
                while (cursor.moveToNext()) {
                    val cv = ContentValues()
                    for (i in 0 until cursor.columnCount) {
                        val name = cursor.getColumnName(i)
                        if (cursor.isNull(i)) {
                            cv.putNull(name)
                        } else {
                            cv.put(name, cursor.getString(i))
                        }
                    }
                    dst.insertWithOnConflict(table, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                cursor.close()
            } catch (e: Exception) {
                Log.w(TAG, "Could not copy table $table: ${e.message}")
            }
        }
    }

    // ========== Entry Operations ==========

    fun getEntries(): String {
        val d = db ?: return "[]"
        val arr = JSONArray()
        val cursor = d.rawQuery("SELECT * FROM entries ORDER BY date DESC, time DESC", null)
        val thumbMap = buildThumbMap()
        while (cursor.moveToNext()) {
            val entry = cursorToEntryJson(cursor)
            val id = entry.getString("id")
            entry.put("images", thumbMap[id] ?: JSONArray())
            arr.put(entry)
        }
        cursor.close()
        return arr.toString()
    }

    fun getEntryById(id: String): String {
        val d = db ?: return "{}"
        val cursor = d.rawQuery("SELECT * FROM entries WHERE id = ?", arrayOf(id))
        if (!cursor.moveToFirst()) { cursor.close(); return "{}" }
        val entry = cursorToEntryJson(cursor)
        cursor.close()
        val images = loadImagesForEntry(id)
        entry.put("images", images)
        return entry.toString()
    }

    fun loadEntryImages(entryId: String): String {
        return loadImagesForEntry(entryId).toString()
    }

    private fun loadImagesForEntry(entryId: String): JSONArray {
        val d = db ?: return JSONArray()
        val arr = JSONArray()
        val cursor = d.rawQuery("SELECT * FROM images WHERE entryId = ? ORDER BY sortOrder", arrayOf(entryId))
        while (cursor.moveToNext()) {
            val obj = JSONObject()
            obj.put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            obj.put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: "")
            obj.put("data", cursor.getString(cursor.getColumnIndexOrThrow("data")) ?: "")
            obj.put("thumb", cursor.getString(cursor.getColumnIndexOrThrow("thumb")) ?: "")
            arr.put(obj)
        }
        cursor.close()
        return arr
    }

    private fun buildThumbMap(): Map<String, JSONArray> {
        val d = db ?: return emptyMap()
        val map = mutableMapOf<String, JSONArray>()
        val cursor = d.rawQuery("SELECT id, entryId, name, thumb FROM images ORDER BY sortOrder", null)
        while (cursor.moveToNext()) {
            val entryId = cursor.getString(cursor.getColumnIndexOrThrow("entryId"))
            val arr = map.getOrPut(entryId) { JSONArray() }
            val obj = JSONObject()
            obj.put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            obj.put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")) ?: "")
            obj.put("thumb", cursor.getString(cursor.getColumnIndexOrThrow("thumb")) ?: "")
            arr.put(obj)
        }
        cursor.close()
        return map
    }

    private fun cursorToEntryJson(cursor: android.database.Cursor): JSONObject {
        val obj = JSONObject()
        obj.put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")) ?: "")
        obj.put("date", cursor.getString(cursor.getColumnIndexOrThrow("date")) ?: "")
        obj.put("time", cursor.getString(cursor.getColumnIndexOrThrow("time")) ?: "")
        obj.put("title", cursor.getString(cursor.getColumnIndexOrThrow("title")) ?: "")
        obj.put("content", cursor.getString(cursor.getColumnIndexOrThrow("content")) ?: "")
        obj.put("richContent", cursor.getString(cursor.getColumnIndexOrThrow("richContent")) ?: "")
        obj.put("categories", safeParseArray(cursor.getString(cursor.getColumnIndexOrThrow("categories"))))
        obj.put("tags", safeParseArray(cursor.getString(cursor.getColumnIndexOrThrow("tags"))))
        obj.put("placeName", cursor.getString(cursor.getColumnIndexOrThrow("placeName")) ?: "")
        obj.put("locations", safeParseArray(cursor.getString(cursor.getColumnIndexOrThrow("locations"))))
        val weatherStr = cursor.getString(cursor.getColumnIndexOrThrow("weather"))
        obj.put("weather", if (weatherStr != null) safeParseObject(weatherStr) else JSONObject.NULL)
        obj.put("pinned", cursor.getInt(cursor.getColumnIndexOrThrow("pinned")) == 1)
        obj.put("locked", cursor.getInt(cursor.getColumnIndexOrThrow("locked")) == 1)
        obj.put("dtCreated", cursor.getString(cursor.getColumnIndexOrThrow("dtCreated")) ?: "")
        obj.put("dtUpdated", cursor.getString(cursor.getColumnIndexOrThrow("dtUpdated")) ?: "")
        obj.put("images", JSONArray())
        return obj
    }

    fun addEntry(entryJson: String) {
        val d = db ?: return
        val entry = JSONObject(entryJson)
        val cv = entryToContentValues(entry)
        d.insertWithOnConflict("entries", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        val images = entry.optJSONArray("images")
        if (images != null && images.length() > 0) {
            insertImages(entry.getString("id"), images)
        }
    }

    fun updateEntry(id: String, fieldsJson: String) {
        val d = db ?: return
        val fields = JSONObject(fieldsJson)
        val cv = ContentValues()
        val fieldMap = mapOf(
            "date" to "date", "time" to "time", "title" to "title",
            "content" to "content", "richContent" to "richContent",
            "placeName" to "placeName", "dtCreated" to "dtCreated", "dtUpdated" to "dtUpdated"
        )
        val jsonFields = setOf("categories", "tags", "locations")
        val objectFields = setOf("weather")
        val boolFields = setOf("pinned", "locked")

        val iter = fields.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            if (key == "images" || key == "id") continue
            when {
                fieldMap.containsKey(key) -> cv.put(fieldMap[key], fields.optString(key, ""))
                boolFields.contains(key) -> cv.put(key, if (fields.optBoolean(key)) 1 else 0)
                jsonFields.contains(key) -> cv.put(key, fields.optJSONArray(key)?.toString() ?: "[]")
                objectFields.contains(key) -> {
                    val v = fields.opt(key)
                    cv.put(key, if (v == null || v == JSONObject.NULL) null else v.toString())
                }
            }
        }

        if (cv.size() > 0) {
            d.update("entries", cv, "id = ?", arrayOf(id))
        }

        if (fields.has("images")) {
            syncEntryImages(id, fields.optJSONArray("images"))
        }
    }

    fun deleteEntryById(id: String) {
        val d = db ?: return
        d.delete("images", "entryId = ?", arrayOf(id))
        d.delete("entries", "id = ?", arrayOf(id))
    }

    fun eraseAllEntries() {
        val d = db ?: return
        d.execSQL("DELETE FROM images")
        d.execSQL("DELETE FROM entries")
    }

    fun deleteEntriesByIds(idsJson: String) {
        val d = db ?: return
        val arr = JSONArray(idsJson)
        if (arr.length() == 0) return
        val ids = (0 until arr.length()).map { arr.getString(it) }
        val placeholders = ids.joinToString(",") { "?" }
        val args = ids.toTypedArray()
        d.execSQL("DELETE FROM images WHERE entryId IN ($placeholders)", args)
        d.execSQL("DELETE FROM entries WHERE id IN ($placeholders)", args)
    }

    private fun insertImages(entryId: String, images: JSONArray) {
        val d = db ?: return
        for (i in 0 until images.length()) {
            val img = images.getJSONObject(i)
            val cv = ContentValues()
            cv.put("id", img.optString("id", "${entryId}_img_$i"))
            cv.put("entryId", entryId)
            cv.put("name", img.optString("name", ""))
            cv.put("data", img.optString("data", ""))
            cv.put("thumb", img.optString("thumb", ""))
            cv.put("sortOrder", i)
            d.insertWithOnConflict("images", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun syncEntryImages(entryId: String, images: JSONArray?) {
        val d = db ?: return
        d.delete("images", "entryId = ?", arrayOf(entryId))
        if (images != null && images.length() > 0) {
            insertImages(entryId, images)
        }
    }

    private fun entryToContentValues(entry: JSONObject): ContentValues {
        val cv = ContentValues()
        cv.put("id", entry.getString("id"))
        cv.put("date", entry.optString("date", ""))
        cv.put("time", entry.optString("time", ""))
        cv.put("title", entry.optString("title", ""))
        cv.put("content", entry.optString("content", ""))
        cv.put("richContent", entry.optString("richContent", ""))
        cv.put("categories", entry.optJSONArray("categories")?.toString() ?: "[]")
        cv.put("tags", entry.optJSONArray("tags")?.toString() ?: "[]")
        cv.put("placeName", entry.optString("placeName", ""))
        cv.put("locations", entry.optJSONArray("locations")?.toString() ?: "[]")
        val weather = entry.opt("weather")
        cv.put("weather", if (weather == null || weather == JSONObject.NULL) null else weather.toString())
        cv.put("pinned", if (entry.optBoolean("pinned")) 1 else 0)
        cv.put("locked", if (entry.optBoolean("locked")) 1 else 0)
        cv.put("dtCreated", entry.optString("dtCreated", ""))
        cv.put("dtUpdated", entry.optString("dtUpdated", ""))
        return cv
    }

    // ========== Categories ==========

    fun getCategories(): String {
        val d = db ?: return "[]"
        val arr = JSONArray()
        val cursor = d.rawQuery("SELECT name FROM categories ORDER BY name", null)
        while (cursor.moveToNext()) { arr.put(cursor.getString(0)) }
        cursor.close()
        return arr.toString()
    }

    fun getCategoriesWithDesc(): String {
        val d = db ?: return "[]"
        val arr = JSONArray()
        val cursor = d.rawQuery("SELECT name, description FROM categories ORDER BY name", null)
        while (cursor.moveToNext()) {
            val obj = JSONObject()
            obj.put("name", cursor.getString(0))
            obj.put("description", cursor.getString(1) ?: "")
            arr.put(obj)
        }
        cursor.close()
        return arr.toString()
    }

    fun setCategories(catsJson: String) {
        val d = db ?: return
        val cats = JSONArray(catsJson)
        val existing = mutableMapOf<String, String>()
        val cursor = d.rawQuery("SELECT name, description FROM categories", null)
        while (cursor.moveToNext()) { existing[cursor.getString(0)] = cursor.getString(1) ?: "" }
        cursor.close()
        d.delete("categories", null, null)
        for (i in 0 until cats.length()) {
            val name = cats.getString(i)
            val desc = existing[name] ?: ""
            d.execSQL("INSERT OR IGNORE INTO categories (name, description) VALUES (?, ?)", arrayOf(name, desc))
        }
    }

    fun setCategoryDescription(name: String, desc: String) {
        val d = db ?: return
        val cv = ContentValues()
        cv.put("description", desc)
        d.update("categories", cv, "name = ?", arrayOf(name))
    }

    // ========== Icons ==========

    fun getAllIcons(): String {
        val d = db ?: return "[]"
        val arr = JSONArray()
        val cursor = d.rawQuery("SELECT type, name, data FROM icons ORDER BY type, name", null)
        while (cursor.moveToNext()) {
            val obj = JSONObject()
            obj.put("type", cursor.getString(0))
            obj.put("name", cursor.getString(1))
            obj.put("data", cursor.getString(2) ?: "")
            arr.put(obj)
        }
        cursor.close()
        return arr.toString()
    }

    fun getIcon(type: String, name: String): String {
        val d = db ?: return ""
        val cursor = d.rawQuery("SELECT data FROM icons WHERE type = ? AND name = ?", arrayOf(type, name))
        val result = if (cursor.moveToFirst()) cursor.getString(0) ?: "" else ""
        cursor.close()
        return result
    }

    fun setIcon(type: String, name: String, data: String) {
        val d = db ?: return
        d.execSQL("INSERT OR REPLACE INTO icons VALUES (?, ?, ?)", arrayOf(type, name, data))
    }

    fun removeIcon(type: String, name: String) {
        val d = db ?: return
        d.delete("icons", "type = ? AND name = ?", arrayOf(type, name))
    }

    // ========== Settings ==========

    fun getSettings(): String {
        val d = db ?: return "{}"
        val obj = JSONObject()
        val cursor = d.rawQuery("SELECT key, value FROM settings", null)
        while (cursor.moveToNext()) {
            val key = cursor.getString(0)
            val value = cursor.getString(1)
            obj.put(key, safeParseValue(value))
        }
        cursor.close()
        return obj.toString()
    }

    fun setSettings(settingsJson: String) {
        val d = db ?: return
        val settings = JSONObject(settingsJson)
        val iter = settings.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            val value = settings.get(key)
            d.execSQL("INSERT OR REPLACE INTO settings VALUES (?, ?)",
                arrayOf(key, if (value == JSONObject.NULL) null else JSONObject.wrap(value)?.toString() ?: value.toString()))
        }
    }

    // ========== Tags & Places ==========

    fun getAllTags(): String {
        val d = db ?: return "[]"
        val tags = mutableSetOf<String>()
        val cursor = d.rawQuery("SELECT tags FROM entries WHERE tags IS NOT NULL AND tags != '[]'", null)
        while (cursor.moveToNext()) {
            val arr = safeParseArray(cursor.getString(0))
            for (i in 0 until arr.length()) {
                tags.add(arr.getString(i))
            }
        }
        cursor.close()
        val result = JSONArray()
        tags.sorted().forEach { result.put(it) }
        return result.toString()
    }

    fun getAllPlaceNames(): String {
        val d = db ?: return "[]"
        val arr = JSONArray()
        val cursor = d.rawQuery("SELECT DISTINCT placeName FROM entries WHERE placeName IS NOT NULL AND placeName != ''", null)
        val names = mutableListOf<String>()
        while (cursor.moveToNext()) { names.add(cursor.getString(0)) }
        cursor.close()
        names.sorted().forEach { arr.put(it) }
        return arr.toString()
    }

    fun getTagDescriptions(): String {
        val d = db ?: return "{}"
        val obj = JSONObject()
        val cursor = d.rawQuery("SELECT name, description FROM tags", null)
        while (cursor.moveToNext()) {
            val desc = cursor.getString(1)
            if (!desc.isNullOrEmpty()) {
                obj.put(cursor.getString(0), desc)
            }
        }
        cursor.close()
        return obj.toString()
    }

    fun setTagDescription(name: String, description: String) {
        val d = db ?: return
        if (description.isNotEmpty()) {
            d.execSQL("INSERT OR REPLACE INTO tags (name, description) VALUES (?, ?)", arrayOf(name, description))
        } else {
            d.delete("tags", "name = ?", arrayOf(name))
        }
    }

    // ========== Widgets ==========

    fun getWidgets(): String {
        val d = db ?: return "[]"
        val arr = JSONArray()
        val cursor = d.rawQuery("SELECT * FROM widgets ORDER BY sortOrder, name", null)
        while (cursor.moveToNext()) {
            arr.put(cursorToWidgetJson(cursor))
        }
        cursor.close()
        return arr.toString()
    }

    fun getWidgetById(id: String): String {
        val d = db ?: return "{}"
        val cursor = d.rawQuery("SELECT * FROM widgets WHERE id = ?", arrayOf(id))
        val result = if (cursor.moveToFirst()) cursorToWidgetJson(cursor).toString() else "{}"
        cursor.close()
        return result
    }

    fun saveWidget(widgetJson: String) {
        val d = db ?: return
        val w = JSONObject(widgetJson)
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .format(java.util.Date())
        d.execSQL("""
            INSERT OR REPLACE INTO widgets (id, name, description, bgColor, icon, filters, functions, enabledInDashboard, sortOrder, dtCreated, dtUpdated)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, arrayOf(
            w.getString("id"),
            w.optString("name", ""),
            w.optString("description", ""),
            w.optString("bgColor", ""),
            w.optString("icon", ""),
            (w.optJSONArray("filters") ?: JSONArray()).toString(),
            (w.optJSONArray("functions") ?: JSONArray()).toString(),
            if (w.optBoolean("enabledInDashboard", true)) 1 else 0,
            w.optInt("sortOrder", 0),
            w.optString("dtCreated", now),
            now
        ))
    }

    fun deleteWidget(id: String) {
        val d = db ?: return
        d.delete("widgets", "id = ?", arrayOf(id))
    }

    private fun cursorToWidgetJson(cursor: android.database.Cursor): JSONObject {
        val obj = JSONObject()
        for (i in 0 until cursor.columnCount) {
            val name = cursor.getColumnName(i)
            if (cursor.isNull(i)) {
                obj.put(name, JSONObject.NULL)
            } else {
                obj.put(name, cursor.getString(i))
            }
        }
        obj.put("filters", safeParseArray(obj.optString("filters", "[]")))
        obj.put("functions", safeParseArray(obj.optString("functions", "[]")))
        obj.put("enabledInDashboard", obj.optString("enabledInDashboard", "1") == "1")
        return obj
    }

    // ========== Export / Import ==========

    fun exportJSON(): String {
        val d = db ?: return "{}"
        val result = JSONObject()
        result.put("journalId", currentJournalId ?: "")
        result.put("entries", JSONArray(getEntries()))
        result.put("categories", JSONArray(getCategories()))
        result.put("settings", JSONObject(getSettings()))
        result.put("icons", JSONArray(getAllIcons()))
        result.put("tags", JSONObject(getTagDescriptions()))
        result.put("widgets", JSONArray(getWidgets()))
        return result.toString(2)
    }

    fun getDBStats(): String {
        val d = db ?: return "{}"
        val stats = JSONObject()
        val tables = JSONObject()

        val entryCount = scalarInt(d, "SELECT COUNT(*) FROM entries")
        tables.put("entries", JSONObject().put("rows", entryCount))

        val imgCount = scalarInt(d, "SELECT COUNT(*) FROM images")
        val imgSize = scalarLong(d, "SELECT COALESCE(SUM(LENGTH(data) + LENGTH(thumb)), 0) FROM images")
        tables.put("images", JSONObject().put("rows", imgCount).put("dataSize", imgSize))

        tables.put("categories", JSONObject().put("rows", scalarInt(d, "SELECT COUNT(*) FROM categories")))
        tables.put("settings", JSONObject().put("rows", scalarInt(d, "SELECT COUNT(*) FROM settings")))

        stats.put("tables", tables)
        stats.put("journalId", currentJournalId ?: "")

        val dbFile = currentJournalId?.let { dbPath(it) }
        stats.put("fileSize", dbFile?.length() ?: 0)

        val dateRange = d.rawQuery("SELECT MIN(date), MAX(date) FROM entries WHERE date IS NOT NULL AND date != ''", null)
        if (dateRange.moveToFirst() && !dateRange.isNull(0)) {
            val dr = JSONObject()
            dr.put("earliest", dateRange.getString(0))
            dr.put("latest", dateRange.getString(1))
            stats.put("dateRange", dr)
        }
        dateRange.close()

        return stats.toString()
    }

    fun execRawSQL(sql: String, paramsJson: String): String {
        val d = db ?: return "[]"
        val params = if (paramsJson.isNotEmpty()) {
            val arr = JSONArray(paramsJson)
            Array(arr.length()) { arr.getString(it) }
        } else {
            emptyArray()
        }
        val cursor = d.rawQuery(sql, params)
        val result = JSONArray()
        val cols = cursor.columnNames
        val colArr = JSONArray()
        cols.forEach { colArr.put(it) }

        val values = JSONArray()
        while (cursor.moveToNext()) {
            val row = JSONArray()
            for (i in cols.indices) {
                if (cursor.isNull(i)) row.put(JSONObject.NULL)
                else row.put(cursor.getString(i))
            }
            values.put(row)
        }
        cursor.close()

        if (cols.isNotEmpty()) {
            val obj = JSONObject()
            obj.put("columns", colArr)
            obj.put("values", values)
            result.put(obj)
        }
        return result.toString()
    }

    // ========== Helpers ==========

    private fun scalarInt(d: SQLiteDatabase, sql: String): Int {
        val cursor = d.rawQuery(sql, null)
        val v = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return v
    }

    private fun scalarLong(d: SQLiteDatabase, sql: String): Long {
        val cursor = d.rawQuery(sql, null)
        val v = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        cursor.close()
        return v
    }

    private fun safeParseArray(str: String?): JSONArray {
        if (str.isNullOrEmpty()) return JSONArray()
        return try { JSONArray(str) } catch (_: Exception) { JSONArray() }
    }

    private fun safeParseObject(str: String?): Any {
        if (str.isNullOrEmpty()) return JSONObject.NULL
        return try { JSONObject(str) } catch (_: Exception) { JSONObject.NULL }
    }

    private fun safeParseValue(str: String?): Any {
        if (str == null) return JSONObject.NULL
        return try { JSONObject(str) } catch (_: Exception) {
            try { JSONArray(str) } catch (_: Exception) {
                try {
                    if (str == "true") true
                    else if (str == "false") false
                    else if (str == "null") JSONObject.NULL
                    else if (str.startsWith("\"") && str.endsWith("\"")) str.substring(1, str.length - 1)
                    else str.toDoubleOrNull() ?: str
                } catch (_: Exception) { str }
            }
        }
    }
}
