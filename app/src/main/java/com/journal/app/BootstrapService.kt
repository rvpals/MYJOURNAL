package com.journal.app

import android.content.Context
import org.json.JSONObject

class BootstrapService(context: Context) {

    companion object {
        private const val PREFS_NAME = "bootstrap_prefs"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(key: String): String? = prefs.getString(key, null)

    fun set(key: String, value: String?) {
        if (value == null) {
            remove(key)
            return
        }
        prefs.edit().putString(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun has(key: String): Boolean = prefs.contains(key)

    fun getInt(key: String, defaultVal: Int): Int {
        val v = prefs.getString(key, null) ?: return defaultVal
        return v.toIntOrNull() ?: defaultVal
    }

    fun getAll(): String {
        val obj = JSONObject()
        for ((key, value) in prefs.all) {
            if (value is String) {
                obj.put(key, value)
            }
        }
        return obj.toString()
    }

    fun setAll(json: String) {
        val obj = JSONObject(json)
        val editor = prefs.edit()
        val iter = obj.keys()
        while (iter.hasNext()) {
            val key = iter.next()
            editor.putString(key, obj.getString(key))
        }
        editor.apply()
    }

    fun isEmpty(): Boolean = prefs.all.isEmpty()
}
