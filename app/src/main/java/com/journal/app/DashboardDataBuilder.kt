package com.journal.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DashboardDataBuilder {

    fun build(db: DatabaseService, bs: BootstrapService): String {
        val entriesJson = db.getEntries()
        val entries = try { JSONArray(entriesJson) } catch (_: Exception) { JSONArray() }

        val week = getDateRange("week")
        val month = getDateRange("month")
        val year = getDateRange("year")

        val entryList = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }

        val tags = countArrayField(entryList, "tags")
        val categories = countArrayField(entryList, "categories")
        val places = countPlaces(entryList)
        val streak = calculateStreak(entryList)

        val pinned = entryList.filter { it.optBoolean("pinned", false) }
            .sortedByDescending { it.optString("date", "") }
            .take(20)
            .map { summarizeEntry(it) }

        val recent = entryList
            .sortedWith(compareByDescending<JSONObject> { it.optString("date", "") }
                .thenByDescending { it.optString("time", "") })
            .take(5)
            .map { summarizeEntry(it) }

        val widgets = computeWidgets(db, entryList)

        val journalList = bs.get("journal_list") ?: "[]"
        val journalId = bs.get("last_journal_id") ?: ""
        val journalName = try {
            val arr = JSONArray(journalList)
            var name = ""
            for (i in 0 until arr.length()) {
                val j = arr.optJSONObject(i) ?: continue
                if (j.optString("id") == journalId) { name = j.optString("name", ""); break }
            }
            name
        } catch (_: Exception) { "" }

        val settingsJson = db.getSettings()
        val settings = try { JSONObject(settingsJson) } catch (_: Exception) { JSONObject() }
        val theme = settings.optString("theme", "dark").trim('"')

        val result = JSONObject()
        result.put("totalEntries", entryList.size)
        result.put("thisWeek", entryList.count { it.optString("date", "") >= week.first && it.optString("date", "") <= week.second })
        result.put("thisMonth", entryList.count { it.optString("date", "") >= month.first && it.optString("date", "") <= month.second })
        result.put("thisYear", entryList.count { it.optString("date", "") >= year.first && it.optString("date", "") <= year.second })
        result.put("streak", streak)
        result.put("topTags", toRankedArray(tags, 20))
        result.put("topCategories", toRankedArray(categories, 20))
        result.put("topPlaces", toRankedArray(places, 20))
        result.put("pinnedEntries", JSONArray().apply { pinned.forEach { put(it) } })
        result.put("recentEntries", JSONArray().apply { recent.forEach { put(it) } })
        result.put("pinnedViews", JSONArray())
        result.put("widgets", widgets)
        result.put("journalName", journalName)
        result.put("theme", theme)
        result.put("todayInHistory", buildTodayInHistory(entryList))

        return result.toString()
    }

    private fun getDateRange(period: String): Pair<String, String> {
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return when (period) {
            "week" -> {
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val diff = if (dow == Calendar.SUNDAY) -6 else Calendar.MONDAY - dow
                cal.add(Calendar.DAY_OF_MONTH, diff)
                val from = fmt.format(cal.time)
                cal.add(Calendar.DAY_OF_MONTH, 6)
                Pair(from, fmt.format(cal.time))
            }
            "month" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val from = fmt.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                Pair(from, fmt.format(cal.time))
            }
            "year" -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val from = fmt.format(cal.time)
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                Pair(from, fmt.format(cal.time))
            }
            else -> Pair("", "")
        }
    }

    private fun calculateStreak(entries: List<JSONObject>): Int {
        val dates = entries.mapNotNull { it.optString("date", "").takeIf { d -> d.isNotEmpty() } }
            .toSortedSet().sortedDescending()
        if (dates.isEmpty()) return 0

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = fmt.format(Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val yesterday = fmt.format(cal.time)

        if (dates[0] != today && dates[0] != yesterday) return 0

        var streak = 1
        for (i in 0 until dates.size - 1) {
            val current = fmt.parse(dates[i]) ?: break
            val prev = fmt.parse(dates[i + 1]) ?: break
            val diff = (current.time - prev.time) / 86400000
            if (diff == 1L) streak++ else break
        }
        return streak
    }

    private fun countArrayField(entries: List<JSONObject>, field: String): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        for (e in entries) {
            val arr = e.optJSONArray(field) ?: continue
            for (i in 0 until arr.length()) {
                val v = arr.optString(i, "")
                if (v.isNotEmpty()) counts[v] = (counts[v] ?: 0) + 1
            }
        }
        return counts.entries.sortedByDescending { it.value }.map { Pair(it.key, it.value) }
    }

    private fun countPlaces(entries: List<JSONObject>): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        for (e in entries) {
            val name = e.optString("placeName", "")
            if (name.isNotEmpty()) counts[name] = (counts[name] ?: 0) + 1
        }
        return counts.entries.sortedByDescending { it.value }.map { Pair(it.key, it.value) }
    }

    private fun toRankedArray(items: List<Pair<String, Int>>, limit: Int): JSONArray {
        val arr = JSONArray()
        for (item in items.take(limit)) {
            arr.put(JSONObject().apply {
                put("name", item.first)
                put("count", item.second)
            })
        }
        return arr
    }

    private fun summarizeEntry(e: JSONObject): JSONObject {
        val s = JSONObject()
        s.put("id", e.optString("id", ""))
        s.put("date", e.optString("date", ""))
        s.put("time", e.optString("time", ""))
        s.put("title", e.optString("title", ""))
        val content = e.optString("content", "")
        s.put("contentPreview", if (content.length > 100) content.substring(0, 100) else content)
        return s
    }

    private fun buildTodayInHistory(entries: List<JSONObject>): JSONArray {
        val today = SimpleDateFormat("MM-dd", Locale.US).format(Date())
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val result = JSONArray()

        val matches = entries.filter { e ->
            val date = e.optString("date", "")
            date.length >= 10 && date.substring(5) == today && date.substring(0, 4).toIntOrNull() != currentYear
        }.sortedByDescending { it.optString("date", "") }

        for (e in matches) {
            val year = e.optString("date", "").substring(0, 4).toIntOrNull() ?: continue
            val yearsAgo = currentYear - year
            val content = e.optString("content", "")
            result.put(JSONObject().apply {
                put("id", e.optString("id", ""))
                put("title", e.optString("title", ""))
                put("date", e.optString("date", ""))
                put("contentPreview", if (content.length > 20) content.substring(0, 20) + "..." else content)
                put("yearsAgo", yearsAgo)
            })
        }
        return result
    }

    private fun computeWidgets(db: DatabaseService, entryList: List<JSONObject>): JSONArray {
        val widgetsJson = db.getWidgets()
        val widgets = try { JSONArray(widgetsJson) } catch (_: Exception) { JSONArray() }
        val result = JSONArray()

        for (i in 0 until widgets.length()) {
            val w = widgets.optJSONObject(i) ?: continue
            val enabled = w.optString("enabledInDashboard", "1") == "1" || w.optBoolean("enabledInDashboard", true)
            if (!enabled) continue

            val filters = w.optJSONArray("filters") ?: JSONArray()
            val functions = w.optJSONArray("functions") ?: JSONArray()
            val filtered = applyWidgetFilters(entryList, filters)

            val results = JSONArray()
            for (j in 0 until functions.length()) {
                val fn = functions.optJSONObject(j) ?: continue
                val value = computeFunction(fn, filtered)
                results.put(JSONObject().apply {
                    put("prefix", fn.optString("prefix", ""))
                    put("postfix", fn.optString("postfix", ""))
                    put("result", value.toString())
                })
            }

            result.put(JSONObject().apply {
                put("id", w.optString("id", ""))
                put("name", w.optString("name", ""))
                put("description", w.optString("description", ""))
                put("bgColor", w.optString("bgColor", ""))
                put("icon", w.optString("icon", ""))
                put("filters", filters)
                put("results", results)
            })
        }
        return result
    }

    private fun applyWidgetFilters(entries: List<JSONObject>, filters: JSONArray): List<JSONObject> {
        if (filters.length() == 0) return entries
        return entries.filter { entry ->
            (0 until filters.length()).all { i ->
                val f = filters.optJSONObject(i) ?: return@all true
                val field = f.optString("field", "")
                val op = f.optString("op", "")
                val value = f.optString("value", "")
                val value2 = f.optString("value2", "")
                matchesFilter(entry, field, op, value, value2)
            }
        }
    }

    private fun matchesFilter(entry: JSONObject, field: String, op: String, value: String, value2: String): Boolean {
        val fieldType = when (field) {
            "date" -> "date"
            "categories", "tags" -> "array"
            else -> "text"
        }

        return when (fieldType) {
            "date" -> {
                val d = entry.optString(field, "")
                when (op) {
                    "after" -> d > value
                    "before" -> d < value
                    "equals" -> d == value
                    "between" -> d >= value && d <= value2.ifEmpty { value }
                    else -> true
                }
            }
            "array" -> {
                val arr = entry.optJSONArray(field) ?: JSONArray()
                val items = (0 until arr.length()).map { arr.optString(it, "").lowercase() }
                val lower = value.lowercase()
                when (op) {
                    "includes" -> items.any { it == lower }
                    "not includes" -> items.none { it == lower }
                    "is empty" -> items.isEmpty()
                    "is not empty" -> items.isNotEmpty()
                    else -> true
                }
            }
            else -> {
                val s = entry.optString(field, "").lowercase()
                val v = value.lowercase()
                when (op) {
                    "contains" -> s.contains(v)
                    "equals" -> s == v
                    "starts with" -> s.startsWith(v)
                    "ends with" -> s.endsWith(v)
                    "is empty" -> s.isEmpty()
                    "is not empty" -> s.isNotEmpty()
                    else -> true
                }
            }
        }
    }

    private fun computeFunction(fn: JSONObject, filtered: List<JSONObject>): Any {
        val field = fn.optString("field", "")
        val func = fn.optString("func", "Count")

        if (field == "entries") return filtered.size

        val isArray = field in setOf("tags", "categories")

        if (func == "Count") {
            val matchVal = fn.optString("value", "").lowercase()
            return if (isArray) {
                if (matchVal.isNotEmpty()) {
                    filtered.count { e ->
                        val arr = e.optJSONArray(field) ?: JSONArray()
                        (0 until arr.length()).any { arr.optString(it, "").lowercase() == matchVal }
                    }
                } else {
                    val set = mutableSetOf<String>()
                    filtered.forEach { e ->
                        val arr = e.optJSONArray(field) ?: JSONArray()
                        (0 until arr.length()).forEach { set.add(arr.optString(it, "")) }
                    }
                    set.size
                }
            } else {
                val mv = matchVal
                if (mv.isNotEmpty()) {
                    filtered.count { it.optString(field, "").lowercase() == mv }
                } else {
                    filtered.count { it.optString(field, "").isNotEmpty() }
                }
            }
        }

        val values = mutableListOf<Double>()
        for (e in filtered) {
            val raw = e.opt(field)
            if (raw is JSONArray) {
                for (i in 0 until raw.length()) {
                    raw.optString(i, "").toDoubleOrNull()?.let { values.add(it) }
                }
            } else {
                raw?.toString()?.toDoubleOrNull()?.let { values.add(it) }
            }
        }

        if (values.isEmpty()) return 0

        return when (func) {
            "Sum" -> values.sum()
            "Max" -> values.max()
            "Min" -> values.min()
            "Average" -> Math.round(values.average() * 100) / 100.0
            else -> 0
        }
    }
}
