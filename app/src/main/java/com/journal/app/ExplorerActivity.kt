package com.journal.app

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journal.app.ThemeManager.C
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class ExplorerActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic var databaseService: DatabaseService? = null
        @JvmStatic var bootstrapService: BootstrapService? = null
    }

    private lateinit var db: DatabaseService
    private lateinit var bs: BootstrapService

    private data class FieldDef(val key: String, val label: String, val type: String)
    private data class Condition(var field: String, var op: String, var value: String, var value2: String)

    private val fields = listOf(
        FieldDef("date", "Date", "date"),
        FieldDef("time", "Time", "text"),
        FieldDef("title", "Title", "text"),
        FieldDef("content", "Content", "text"),
        FieldDef("categories", "Categories", "array"),
        FieldDef("tags", "Tags", "array"),
        FieldDef("placeName", "Place Name", "text"),
        FieldDef("locations", "Locations", "locations"),
        FieldDef("weather", "Weather", "weather")
    )

    private val opsMap = mapOf(
        "text" to listOf("contains", "equals", "starts with", "ends with", "is empty", "is not empty"),
        "date" to listOf("equals", "before", "after", "between"),
        "array" to listOf("includes", "not includes", "is empty", "is not empty"),
        "locations" to listOf("contains", "is empty", "is not empty"),
        "weather" to listOf("has data", "no data")
    )

    private val conditions = mutableListOf<Condition>()
    private var selectedCols = mutableListOf("date", "title", "categories", "tags")
    private var activeTable: String? = null

    private var lastResults = mutableListOf<JSONObject>()
    private var lastRawCols: List<String>? = null
    private var lastIsEntryQuery = true

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(ThemeManager.fontScaledContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explorer)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        db = databaseService ?: run { finish(); return }
        bs = bootstrapService ?: run { finish(); return }
        databaseService = null
        bootstrapService = null

        setupNavbar()
        renderTableChips()
        renderFieldChips()
        if (conditions.isEmpty()) conditions.add(Condition("title", "contains", "", ""))
        renderConditions()
        setupActions()
    }

    private fun setupNavbar() {
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
    }

    // ========== Table Browser ==========

    private fun renderTableChips() {
        val container = findViewById<LinearLayout>(R.id.explorer_table_chips)
        container.removeAllViews()
        var tables = listOf("entries", "images", "categories", "icons", "settings", "schema_version")
        try {
            val result = db.execRawSQL("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", "")
            val arr = JSONArray(result)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                val values = obj.getJSONArray("values")
                val list = mutableListOf<String>()
                for (i in 0 until values.length()) {
                    list.add(values.getJSONArray(i).getString(0))
                }
                if (list.isNotEmpty()) tables = list
            }
        } catch (_: Exception) {}

        val flow = FlowLayout(this)
        for (t in tables) {
            flow.addView(makeChip(t, activeTable == t) { toggleTable(t) })
        }
        container.addView(flow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun toggleTable(name: String) {
        val detail = findViewById<LinearLayout>(R.id.explorer_table_detail)
        if (activeTable == name) {
            activeTable = null
            detail.visibility = View.GONE
            renderTableChips()
            return
        }
        activeTable = name
        renderTableChips()

        detail.removeAllViews()
        detail.visibility = View.VISIBLE

        try {
            val colResult = JSONArray(db.execRawSQL("PRAGMA table_info($name)", ""))
            val countResult = JSONArray(db.execRawSQL("SELECT COUNT(*) as cnt FROM $name", ""))
            val sampleResult = JSONArray(db.execRawSQL("SELECT * FROM $name LIMIT 5", ""))

            val rowCount = if (countResult.length() > 0) {
                val vals = countResult.getJSONObject(0).getJSONArray("values")
                if (vals.length() > 0) vals.getJSONArray(0).optInt(0, 0) else 0
            } else 0

            // Table info header
            detail.addView(TextView(this).apply {
                text = "$name — $rowCount row${if (rowCount != 1) "s" else ""}"
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(4))
            })

            // Column info
            if (colResult.length() > 0) {
                val cols = colResult.getJSONObject(0).getJSONArray("values")
                val headers = listOf("Column", "Type", "PK", "Not Null", "Default")
                detail.addView(buildTable(headers, List(cols.length()) { i ->
                    val r = cols.getJSONArray(i)
                    listOf(
                        r.optString(1, ""),
                        r.optString(2, ""),
                        if (r.optInt(5, 0) != 0) "Yes" else "",
                        if (r.optInt(3, 0) != 0) "Yes" else "",
                        if (r.isNull(4)) "" else r.optString(4, "")
                    )
                }))
            }

            // Sample data
            if (sampleResult.length() > 0) {
                val obj = sampleResult.getJSONObject(0)
                val sampleCols = obj.getJSONArray("columns")
                val sampleVals = obj.getJSONArray("values")
                if (sampleVals.length() > 0) {
                    detail.addView(TextView(this).apply {
                        text = "Sample data (${sampleVals.length()} row${if (sampleVals.length() != 1) "s" else ""})"
                        setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                        textSize = 12f
                        setPadding(0, dp(8), 0, dp(4))
                    })

                    val colNames = List(sampleCols.length()) { sampleCols.getString(it) }
                    val rows = List(sampleVals.length()) { i ->
                        val row = sampleVals.getJSONArray(i)
                        List(row.length()) { j ->
                            if (row.isNull(j)) "NULL" else truncCell(row.optString(j, ""))
                        }
                    }
                    val hsv = HorizontalScrollView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    hsv.addView(buildTable(colNames, rows))
                    detail.addView(hsv)
                }
            } else if (rowCount == 0) {
                detail.addView(TextView(this).apply {
                    text = "Table is empty"
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 12f
                    setPadding(0, dp(6), 0, 0)
                })
            }

            detail.setPadding(dp(8), dp(8), dp(8), dp(8))
            detail.background = ContextCompat.getDrawable(this, R.drawable.input_bg)
        } catch (e: Exception) {
            detail.addView(TextView(this).apply {
                text = "Error: ${e.message}"
                setTextColor(ThemeManager.color(C.ERROR))
                textSize = 12f
            })
        }
    }

    // ========== Field Chips ==========

    private fun renderFieldChips() {
        val container = findViewById<LinearLayout>(R.id.explorer_field_chips)
        container.removeAllViews()
        val flow = FlowLayout(this)
        for (f in fields) {
            flow.addView(makeChip(f.label, selectedCols.contains(f.key)) {
                toggleCol(f.key)
            })
        }
        container.addView(flow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun toggleCol(key: String) {
        if (selectedCols.contains(key)) {
            if (selectedCols.size <= 1) return
            selectedCols.remove(key)
        } else {
            selectedCols.add(key)
        }
        renderFieldChips()
    }

    // ========== Condition Builder ==========

    private fun renderConditions() {
        val container = findViewById<LinearLayout>(R.id.explorer_conditions)
        container.removeAllViews()

        for ((i, cond) in conditions.withIndex()) {
            container.addView(buildConditionRow(i, cond))
        }
    }

    private fun buildConditionRow(index: Int, cond: Condition): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }

        val fieldDef = fields.find { it.key == cond.field } ?: fields[0]
        val ops = opsMap[fieldDef.type] ?: opsMap["text"]!!

        // Field spinner
        val fieldSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
            background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.spinner_bg)
        }
        val fieldNames = fields.map { it.label }
        fieldSpinner.adapter = makeSpinnerAdapter(fieldNames)
        fieldSpinner.setSelection(fields.indexOfFirst { it.key == cond.field }.coerceAtLeast(0))
        var fieldInit = true
        fieldSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (fieldInit) { fieldInit = false; return }
                cond.field = fields[pos].key
                val newOps = opsMap[fields[pos].type] ?: opsMap["text"]!!
                cond.op = newOps[0]
                cond.value = ""
                cond.value2 = ""
                renderConditions()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        row.addView(fieldSpinner)

        // Op spinner
        val opSpinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
            background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.spinner_bg)
        }
        opSpinner.adapter = makeSpinnerAdapter(ops)
        opSpinner.setSelection(ops.indexOf(cond.op).coerceAtLeast(0))
        var opInit = true
        opSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (opInit) { opInit = false; return }
                cond.op = ops[pos]
                renderConditions()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        row.addView(opSpinner)

        // Value input(s)
        val noValue = cond.op in listOf("is empty", "is not empty", "has data", "no data")
        if (!noValue) {
            val inputType = if (fieldDef.type == "date")
                android.text.InputType.TYPE_CLASS_DATETIME
            else
                android.text.InputType.TYPE_CLASS_TEXT

            val valueInput = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
                hint = "Value..."
                setText(cond.value)
                textSize = 12f
                setTextColor(ThemeManager.color(C.TEXT))
                setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.input_bg)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                this.inputType = inputType
                setSingleLine()
            }
            valueInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) cond.value = valueInput.text.toString()
            }
            row.addView(valueInput)

            if (cond.op == "between") {
                row.addView(TextView(this).apply {
                    text = "and"
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 11f
                    setPadding(dp(2), 0, dp(4), 0)
                })
                val value2Input = EditText(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
                    hint = "Value..."
                    setText(cond.value2)
                    textSize = 12f
                    setTextColor(ThemeManager.color(C.TEXT))
                    setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.input_bg)
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    this.inputType = inputType
                    setSingleLine()
                }
                value2Input.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) cond.value2 = value2Input.text.toString()
                }
                row.addView(value2Input)
            }
        }

        // Remove button
        val removeBtn = Button(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(ThemeManager.color(C.ERROR))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setPadding(0, 0, 0, 0)
        }
        removeBtn.setOnClickListener {
            conditions.removeAt(index)
            if (conditions.isEmpty()) conditions.add(Condition("title", "contains", "", ""))
            renderConditions()
        }
        row.addView(removeBtn)

        return row
    }

    // ========== Actions ==========

    private fun setupActions() {
        findViewById<Button>(R.id.btn_add_condition).setOnClickListener {
            conditions.add(Condition("title", "contains", "", ""))
            renderConditions()
        }

        findViewById<Button>(R.id.btn_run_query).setOnClickListener { runQuery() }
        findViewById<Button>(R.id.btn_clear).setOnClickListener { clearQuery() }
        findViewById<Button>(R.id.btn_save_query).setOnClickListener { showSaveQueryDialog() }
        findViewById<Button>(R.id.btn_load_query).setOnClickListener { showSqlLibrary() }
        findViewById<Button>(R.id.btn_export_csv).setOnClickListener { exportCSV() }
        findViewById<Button>(R.id.btn_export_ical).setOnClickListener { exportICalendar() }
    }

    // ========== Query Execution ==========

    private fun commitConditionValues() {
        val condContainer = findViewById<LinearLayout>(R.id.explorer_conditions)
        for (i in 0 until condContainer.childCount) {
            val row = condContainer.getChildAt(i) as? LinearLayout ?: continue
            if (i >= conditions.size) break
            val cond = conditions[i]
            var valueIdx = 0
            for (j in 0 until row.childCount) {
                val child = row.getChildAt(j)
                if (child is EditText) {
                    when (valueIdx) {
                        0 -> cond.value = child.text.toString()
                        1 -> cond.value2 = child.text.toString()
                    }
                    valueIdx++
                }
            }
        }
    }

    private fun runQuery() {
        commitConditionValues()
        val sqlInput = findViewById<EditText>(R.id.explorer_sql_input)
        val sqlText = sqlInput.text.toString().trim()

        if (sqlText.isNotEmpty()) {
            val fromMatch = Regex("\\bFROM\\s+(\\w+)", RegexOption.IGNORE_CASE).find(sqlText)
            if (fromMatch != null && fromMatch.groupValues[1].lowercase() != "entries") {
                runRawSQL(sqlText)
                return
            }
            if (!sqlText.matches(Regex("^SELECT\\b.*", RegexOption.IGNORE_CASE)) &&
                !sqlText.matches(Regex("^\\w+\\s+(contains|equals|starts|ends|is\\s|includes|not\\s|before|after|between|has\\s|no\\s).*", RegexOption.IGNORE_CASE))) {
                runRawSQL(sqlText)
                return
            }

            val parsed = parseSQL(sqlText)
            if (parsed.error != null && parsed.conditions.isEmpty()) {
                findViewById<TextView>(R.id.explorer_result_count).text = "Error: ${parsed.error}"
                return
            }
            if (parsed.conditions.isNotEmpty()) {
                conditions.clear()
                conditions.addAll(parsed.conditions)
                renderConditions()
            }
            if (parsed.columns != null) {
                selectedCols.clear()
                selectedCols.addAll(parsed.columns)
                renderFieldChips()
            }
        }

        val entries = loadEntries()
        val filtered = entries.filter { e -> conditions.all { matchCondition(e, it) } }
            .sortedWith(compareByDescending<JSONObject> { it.optString("date", "") }
                .thenByDescending { it.optString("time", "") })

        lastResults.clear()
        lastResults.addAll(filtered)
        lastRawCols = null
        lastIsEntryQuery = true

        showResultCount(filtered.size)
        renderResults()
    }

    private fun runRawSQL(sql: String) {
        try {
            val resultStr = db.execRawSQL(sql, "")
            val results = JSONArray(resultStr)
            if (results.length() == 0 || !results.getJSONObject(0).has("values")) {
                showResultCount(0)
                lastResults.clear()
                renderResults()
                return
            }

            val obj = results.getJSONObject(0)
            val cols = obj.getJSONArray("columns")
            val vals = obj.getJSONArray("values")

            val colList = List(cols.length()) { cols.getString(it) }
            lastResults.clear()
            for (i in 0 until vals.length()) {
                val row = vals.getJSONArray(i)
                val rowObj = JSONObject()
                for (j in colList.indices) {
                    if (row.isNull(j)) rowObj.put(colList[j], JSONObject.NULL)
                    else rowObj.put(colList[j], row.getString(j))
                }
                lastResults.add(rowObj)
            }
            selectedCols.clear()
            selectedCols.addAll(colList)
            lastRawCols = colList
            lastIsEntryQuery = false

            showResultCount(lastResults.size)
            renderResults()
        } catch (e: Exception) {
            findViewById<TextView>(R.id.explorer_result_count).text = "Error: ${e.message}"
            findViewById<LinearLayout>(R.id.explorer_results).visibility = View.GONE
        }
    }

    private fun loadEntries(): List<JSONObject> {
        val json = db.getEntries()
        val arr = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
        val list = mutableListOf<JSONObject>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { list.add(it) }
        }
        return list
    }

    // ========== Condition Matching ==========

    private fun matchCondition(entry: JSONObject, cond: Condition): Boolean {
        val fieldDef = fields.find { it.key == cond.field } ?: return true
        val v = cond.value.lowercase()

        return when (fieldDef.type) {
            "text" -> {
                val ev = entry.optString(cond.field, "").lowercase()
                when (cond.op) {
                    "contains" -> ev.contains(v)
                    "equals" -> ev == v
                    "starts with" -> ev.startsWith(v)
                    "ends with" -> ev.endsWith(v)
                    "is empty" -> ev.isEmpty()
                    "is not empty" -> ev.isNotEmpty()
                    else -> true
                }
            }
            "date" -> {
                val d = entry.optString("date", "")
                when (cond.op) {
                    "equals" -> d == cond.value
                    "before" -> d < cond.value
                    "after" -> d > cond.value
                    "between" -> d >= cond.value && d <= cond.value2
                    else -> true
                }
            }
            "array" -> {
                val arr = entry.optJSONArray(cond.field) ?: JSONArray()
                val items = List(arr.length()) { arr.optString(it, "").lowercase() }
                when (cond.op) {
                    "includes" -> items.any { it.contains(v) }
                    "not includes" -> !items.any { it.contains(v) }
                    "is empty" -> items.isEmpty()
                    "is not empty" -> items.isNotEmpty()
                    else -> true
                }
            }
            "locations" -> {
                val locs = entry.optJSONArray("locations") ?: JSONArray()
                when (cond.op) {
                    "contains" -> {
                        var found = false
                        for (i in 0 until locs.length()) {
                            val l = locs.optJSONObject(i) ?: continue
                            if (l.optString("name", "").lowercase().contains(v) ||
                                l.optString("address", "").lowercase().contains(v)) {
                                found = true; break
                            }
                        }
                        found
                    }
                    "is empty" -> locs.length() == 0
                    "is not empty" -> locs.length() > 0
                    else -> true
                }
            }
            "weather" -> {
                when (cond.op) {
                    "has data" -> !entry.isNull("weather") && entry.optJSONObject("weather") != null
                    "no data" -> entry.isNull("weather") || entry.optJSONObject("weather") == null
                    else -> true
                }
            }
            else -> true
        }
    }

    // ========== SQL Parser ==========

    private data class ParseResult(
        val conditions: List<Condition>,
        val columns: List<String>?,
        val error: String?
    )

    private fun parseSQL(sql: String): ParseResult {
        val s = sql.trim().replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return ParseResult(emptyList(), null, "Empty query")

        val selectMatch = Regex("^SELECT\\s+(.+?)\\s+FROM\\s+entries(?:\\s+WHERE\\s+(.+))?$", RegexOption.IGNORE_CASE).find(s)
        if (selectMatch == null) {
            val whereOnly = s.replace(Regex("^WHERE\\s+", RegexOption.IGNORE_CASE), "")
            val conds = parseWhereClauses(whereOnly)
            return if (conds != null) ParseResult(conds, null, null)
            else ParseResult(emptyList(), null, "Could not parse query")
        }

        var columns: List<String>? = null
        val colPart = selectMatch.groupValues[1].trim()
        if (colPart != "*") {
            val cols = colPart.split(",").map { it.trim().lowercase() }
            val mapped = cols.mapNotNull { c -> resolveField(c) }
            if (mapped.isNotEmpty()) columns = mapped
        }

        val conds = if (selectMatch.groupValues.size > 2 && selectMatch.groupValues[2].isNotEmpty()) {
            parseWhereClauses(selectMatch.groupValues[2].trim())
        } else null

        return ParseResult(conds ?: emptyList(), columns, if (conds == null && selectMatch.groupValues.size > 2 && selectMatch.groupValues[2].isNotEmpty()) "Could not parse WHERE clause" else null)
    }

    private fun parseWhereClauses(whereStr: String): List<Condition>? {
        val parts = splitByAnd(whereStr)
        val conditions = mutableListOf<Condition>()
        for (part in parts) {
            val cond = parseSingleCondition(part.trim()) ?: return null
            conditions.add(cond)
        }
        return conditions
    }

    private fun splitByAnd(str: String): List<String> {
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '
        val upper = str.uppercase()
        var i = 0
        while (i < str.length) {
            val ch = str[i]
            if (inQuote) {
                current.append(ch)
                if (ch == quoteChar) inQuote = false
                i++; continue
            }
            if (ch == '\'' || ch == '"') {
                inQuote = true; quoteChar = ch
                current.append(ch); i++; continue
            }
            if (i + 4 < upper.length && upper.substring(i, i + 5) == " AND ") {
                parts.add(current.toString())
                current = StringBuilder()
                i += 5; continue
            }
            current.append(ch); i++
        }
        if (current.isNotEmpty()) parts.add(current.toString())
        return parts
    }

    private fun parseSingleCondition(str: String): Condition? {
        val s = str.trim()
        val ops = listOf(
            "NOT INCLUDES", "IS NOT EMPTY", "STARTS WITH", "ENDS WITH",
            "IS EMPTY", "HAS DATA", "NO DATA",
            "CONTAINS", "INCLUDES", "EQUALS", "BEFORE", "AFTER", "BETWEEN"
        )
        val upper = s.uppercase()
        val noValueOps = setOf("IS EMPTY", "IS NOT EMPTY", "HAS DATA", "NO DATA")

        for (op in ops) {
            if (op in noValueOps) {
                if (upper.endsWith(" $op")) {
                    val fieldStr = s.substring(0, upper.lastIndexOf(" $op")).trim()
                    val field = resolveField(fieldStr) ?: continue
                    return Condition(field, op.lowercase(), "", "")
                }
                continue
            }
            val opIdx = upper.indexOf(" $op ")
            if (opIdx < 0) continue

            val fieldStr = s.substring(0, opIdx).trim()
            val field = resolveField(fieldStr) ?: continue
            val valueStr = s.substring(opIdx + op.length + 2).trim()

            if (op == "BETWEEN") {
                val andIdx = valueStr.uppercase().indexOf(" AND ")
                if (andIdx < 0) continue
                return Condition(field, "between",
                    stripQuotes(valueStr.substring(0, andIdx).trim()),
                    stripQuotes(valueStr.substring(andIdx + 5).trim()))
            }

            return Condition(field, op.lowercase(), stripQuotes(valueStr), "")
        }
        return null
    }

    private fun resolveField(str: String): String? {
        val s = str.trim().lowercase().replace(Regex("[`\"'\\[\\]]"), "")
        fields.find { it.key.lowercase() == s }?.let { return it.key }
        fields.find { it.label.lowercase() == s }?.let { return it.key }
        val norm = s.replace(Regex("[\\s_-]"), "")
        fields.find { it.key.lowercase() == norm }?.let { return it.key }
        return null
    }

    private fun stripQuotes(s: String): String {
        val t = s.trim()
        if ((t.startsWith("'") && t.endsWith("'")) || (t.startsWith("\"") && t.endsWith("\"")))
            return t.substring(1, t.length - 1)
        return t
    }

    // ========== Results Rendering ==========

    private fun showResultCount(count: Int) {
        findViewById<TextView>(R.id.explorer_result_count).text =
            "$count result${if (count != 1) "s" else ""}"
    }

    private fun renderResults() {
        val container = findViewById<LinearLayout>(R.id.explorer_results)
        val tableContainer = findViewById<LinearLayout>(R.id.explorer_results_table)
        tableContainer.removeAllViews()

        if (lastResults.isEmpty()) {
            container.visibility = View.VISIBLE
            tableContainer.addView(TextView(this).apply {
                text = "No matching entries."
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(24), dp(16), dp(24))
            })
            return
        }

        container.visibility = View.VISIBLE

        val cols = if (lastRawCols != null) lastRawCols!! else selectedCols.filter { k -> fields.any { it.key == k } }
        val headers = if (lastRawCols != null) cols else cols.map { k -> fields.find { it.key == k }?.label ?: k }

        val rows = lastResults.mapIndexed { idx, entry ->
            val rowData = cols.map { k ->
                if (lastRawCols != null) {
                    val v = entry.opt(k)
                    if (v == null || v == JSONObject.NULL) "NULL" else truncCell(v.toString())
                } else {
                    getCellValue(entry, k)
                }
            }
            idx to rowData
        }

        val table = buildClickableTable(headers, rows) { idx ->
            showRecordDetail(idx)
        }
        tableContainer.addView(table)
    }

    private fun getCellValue(entry: JSONObject, key: String): String {
        return when (key) {
            "date" -> entry.optString("date", "")
            "time" -> entry.optString("time", "")
            "title" -> entry.optString("title", "")
            "content" -> entry.optString("content", "").take(80)
            "categories" -> jsonArrayToStr(entry.optJSONArray("categories"))
            "tags" -> jsonArrayToStr(entry.optJSONArray("tags"))
            "placeName" -> entry.optString("placeName", "")
            "locations" -> {
                val locs = entry.optJSONArray("locations") ?: JSONArray()
                val parts = mutableListOf<String>()
                for (i in 0 until locs.length()) {
                    val l = locs.optJSONObject(i) ?: continue
                    val p = listOf(l.optString("name", ""), l.optString("address", "")).filter { it.isNotEmpty() }
                    parts.add(p.joinToString(" — "))
                }
                parts.joinToString("; ")
            }
            "weather" -> {
                val w = entry.optJSONObject("weather") ?: return ""
                formatWeather(w)
            }
            else -> ""
        }
    }

    private fun getCellValueFull(entry: JSONObject, key: String): String {
        return when (key) {
            "content" -> entry.optString("content", "")
                .replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            "locations" -> {
                val locs = entry.optJSONArray("locations") ?: JSONArray()
                val parts = mutableListOf<String>()
                for (i in 0 until locs.length()) {
                    val l = locs.optJSONObject(i) ?: continue
                    val p = mutableListOf<String>()
                    l.optString("name", "").let { if (it.isNotEmpty()) p.add(it) }
                    l.optString("address", "").let { if (it.isNotEmpty()) p.add(it) }
                    val lat = l.optDouble("lat", Double.NaN)
                    val lng = l.optDouble("lng", Double.NaN)
                    if (!lat.isNaN() && !lng.isNaN()) p.add("($lat, $lng)")
                    parts.add(p.joinToString(" — "))
                }
                parts.joinToString("; ")
            }
            else -> getCellValue(entry, key)
        }
    }

    // ========== Record Detail ==========

    private fun showRecordDetail(index: Int) {
        if (index < 0 || index >= lastResults.size) return
        val entry = lastResults[index]

        val detailFields = if (lastRawCols != null) {
            lastRawCols!!.map { col ->
                val v = entry.opt(col)
                col to (if (v == null || v == JSONObject.NULL) "NULL" else v.toString())
            }
        } else {
            fields.map { f ->
                f.label to getCellValueFull(entry, f.key)
            }
        }

        val scrollView = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        // Nav label
        content.addView(TextView(this).apply {
            text = "Record ${index + 1} of ${lastResults.size}"
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 11f
            setPadding(0, 0, 0, dp(8))
        })

        for ((label, value) in detailFields) {
            content.addView(TextView(this).apply {
                text = label
                setTextColor(ThemeManager.color(C.ACCENT))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(6), 0, dp(2))
            })
            content.addView(TextView(this).apply {
                text = value.ifEmpty { "—" }
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 13f
                setPadding(0, 0, 0, dp(4))
            })
        }

        // Entry action buttons for entry-based results
        if (lastIsEntryQuery && lastRawCols == null) {
            val entryId = entry.optString("id", "")
            if (entryId.isNotEmpty()) {
                val btnRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    setPadding(0, dp(8), 0, 0)
                }
                btnRow.addView(Button(this).apply {
                    text = "View Entry"
                    textSize = 12f
                    isAllCaps = false
                    background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.btn_accent)
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    setOnClickListener {
                        openEntryViewer(entryId)
                    }
                })
                content.addView(btnRow)
            }
        }

        scrollView.addView(content)

        val dialog = AlertDialog.Builder(this)
            .setView(scrollView)
            .setPositiveButton("Close", null)
        if (index > 0) {
            dialog.setNeutralButton("← Prev") { _, _ -> showRecordDetail(index - 1) }
        }
        if (index < lastResults.size - 1) {
            dialog.setNegativeButton("Next →") { _, _ -> showRecordDetail(index + 1) }
        }
        dialog.show()
    }

    private fun openEntryViewer(entryId: String) {
        val ids = lastResults.mapNotNull { it.optString("id", "").ifEmpty { null } }
        EntryViewerActivity.pendingEntryId = entryId
        EntryViewerActivity.pendingEntryIds = ids
        EntryViewerActivity.databaseService = db
        EntryViewerActivity.bootstrapService = bs
        startActivity(Intent(this, EntryViewerActivity::class.java))
    }

    // ========== Export CSV ==========

    private fun exportCSV() {
        if (lastResults.isEmpty()) {
            Toast.makeText(this, "No results to export. Run a query first.", Toast.LENGTH_SHORT).show()
            return
        }

        val cols = if (lastRawCols != null) lastRawCols!! else selectedCols.filter { k -> fields.any { it.key == k } }
        val headers = if (lastRawCols != null) cols else cols.map { k -> fields.find { it.key == k }?.label ?: k }

        val csvEscape = { v: String -> "\"${v.replace("\"", "\"\"")}\"" }
        val lines = mutableListOf(headers.joinToString(",") { csvEscape(it) })
        for (entry in lastResults) {
            val row = cols.map { k ->
                val v = if (lastRawCols != null) {
                    val obj = entry.opt(k)
                    if (obj == null || obj == JSONObject.NULL) "" else obj.toString()
                } else getCellValue(entry, k)
                csvEscape(v)
            }
            lines.add(row.joinToString(","))
        }

        val csv = lines.joinToString("\n")
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())
        saveToDownloads("explorer_results_$date.csv", csv.toByteArray(), "text/csv")
    }

    // ========== Export iCalendar ==========

    private fun exportICalendar() {
        if (lastResults.isEmpty()) {
            Toast.makeText(this, "No results to export. Run a query first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!lastIsEntryQuery || lastRawCols != null) {
            Toast.makeText(this, "iCalendar export is only available for entry-based query results.", Toast.LENGTH_SHORT).show()
            return
        }

        val icalEscape = { s: String ->
            s.replace("\\", "\\\\").replace(";", "\\;")
                .replace(",", "\\,").replace("\n", "\\n")
        }

        val fold = { s: String ->
            val sb = StringBuilder()
            var remaining = s
            while (remaining.length > 75) {
                sb.append(remaining.substring(0, 75)).append("\r\n ")
                remaining = remaining.substring(75)
            }
            sb.append(remaining).toString()
        }

        val now = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).format(System.currentTimeMillis())
        val events = mutableListOf<String>()

        for (entry in lastResults) {
            val date = entry.optString("date", "").replace("-", "")
            if (date.isEmpty()) continue
            val time = entry.optString("time", "")
            val title = entry.optString("title", "Untitled")
            val content = entry.optString("content", "")
                .replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            val place = entry.optString("placeName", "")

            val lines = mutableListOf<String>()
            lines.add("BEGIN:VEVENT")
            lines.add("UID:${UUID.randomUUID()}")
            lines.add("DTSTAMP:$now")

            if (time.isNotEmpty()) {
                val t = time.replace(":", "").take(4) + "00"
                lines.add("DTSTART:${date}T$t")
                val h = time.substring(0, 2).toIntOrNull() ?: 0
                val m = time.substring(3, 5).toIntOrNull() ?: 0
                val endH = (h + 1).coerceAtMost(23).toString().padStart(2, '0')
                val endM = m.toString().padStart(2, '0')
                lines.add("DTEND:${date}T${endH}${endM}00")
            } else {
                lines.add("DTSTART;VALUE=DATE:$date")
                lines.add("DTEND;VALUE=DATE:$date")
            }

            lines.add(fold("SUMMARY:${icalEscape(title)}"))
            if (content.isNotEmpty()) lines.add(fold("DESCRIPTION:${icalEscape(content)}"))
            if (place.isNotEmpty()) lines.add(fold("LOCATION:${icalEscape(place)}"))
            lines.add("END:VEVENT")
            events.add(lines.joinToString("\r\n"))
        }

        if (events.isEmpty()) {
            Toast.makeText(this, "No entries with valid dates found.", Toast.LENGTH_SHORT).show()
            return
        }

        val ical = listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//MyJournal//Explorer Export//EN",
            "CALSCALE:GREGORIAN",
            events.joinToString("\r\n"),
            "END:VCALENDAR"
        ).joinToString("\r\n")

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())
        saveToDownloads("explorer_results_$date.ics", ical.toByteArray(), "text/calendar")
    }

    // ========== File Save ==========

    private fun saveToDownloads(filename: String, data: ByteArray, mimeType: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(data) }
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                FileOutputStream(File(dir, filename)).use { it.write(data) }
            }
            Toast.makeText(this, "Saved to Downloads: $filename", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== SQL Library ==========

    private fun getCurrentQueryText(): String {
        val sqlInput = findViewById<EditText>(R.id.explorer_sql_input)
        val sqlText = sqlInput.text.toString().trim()
        if (sqlText.isNotEmpty()) return sqlText
        commitConditionValues()
        if (conditions.isEmpty() || (conditions.size == 1 && conditions[0].value.isEmpty() && conditions[0].op !in listOf("is empty", "is not empty", "has data", "no data"))) return ""
        val colPart = if (selectedCols == fields.map { it.key }) "*" else selectedCols.joinToString(", ")
        val whereParts = conditions.map { cond ->
            val field = cond.field
            when (cond.op) {
                "between" -> "$field BETWEEN '${cond.value}' AND '${cond.value2}'"
                "is empty", "is not empty", "has data", "no data" -> "$field ${cond.op.uppercase()}"
                else -> "$field ${cond.op.uppercase()} '${cond.value}'"
            }
        }
        val where = if (whereParts.isNotEmpty()) " WHERE ${whereParts.joinToString(" AND ")}" else ""
        return "SELECT $colPart FROM entries$where"
    }

    private fun showSaveQueryDialog(editId: Int? = null, prefillName: String = "", prefillDesc: String = "", prefillSql: String? = null) {
        val sql = prefillSql ?: getCurrentQueryText()
        if (sql.isEmpty() && editId == null) {
            Toast.makeText(this, "No query to save. Enter SQL or build conditions first.", Toast.LENGTH_SHORT).show()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(4))
        }

        layout.addView(TextView(this).apply {
            text = "Name"
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
        })
        val nameInput = EditText(this).apply {
            hint = "Query name"
            setText(prefillName)
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.input_bg)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isSingleLine = true
        }
        layout.addView(nameInput)

        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
        })

        layout.addView(TextView(this).apply {
            text = "Description (optional)"
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
        })
        val descInput = EditText(this).apply {
            hint = "What does this query do?"
            setText(prefillDesc)
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.input_bg)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isSingleLine = true
        }
        layout.addView(descInput)

        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
        })

        layout.addView(TextView(this).apply {
            text = "SQL"
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
        })
        val sqlInput = EditText(this).apply {
            setText(sql)
            textSize = 12f
            setTextColor(ThemeManager.color(C.TEXT))
            background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.input_bg)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            minLines = 2
            maxLines = 5
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
        }
        layout.addView(sqlInput)

        AlertDialog.Builder(this)
            .setTitle(if (editId != null) "Edit Saved Query" else "Save Query")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                val statement = sqlInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Name is required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (statement.isEmpty()) {
                    Toast.makeText(this, "SQL statement is required.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (editId != null) {
                    db.updateSqlLibraryEntry(editId, name, desc, statement)
                    Toast.makeText(this, "Query updated.", Toast.LENGTH_SHORT).show()
                } else {
                    db.addSqlLibraryEntry(name, desc, statement)
                    Toast.makeText(this, "Query saved.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSqlLibrary() {
        val libraryJson = db.getSqlLibrary()
        val library = try { JSONArray(libraryJson) } catch (_: Exception) { JSONArray() }

        if (library.length() == 0) {
            Toast.makeText(this, "No saved queries. Use Save to store a query first.", Toast.LENGTH_SHORT).show()
            return
        }

        val scrollView = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        for (i in 0 until library.length()) {
            val item = library.getJSONObject(i)
            val id = item.getInt("id")
            val name = item.optString("name", "")
            val desc = item.optString("description", "")
            val sql = item.optString("sql_statement", "")

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.input_bg)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }

            card.addView(TextView(this).apply {
                text = name
                setTextColor(ThemeManager.color(C.ACCENT))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            })

            if (desc.isNotEmpty()) {
                card.addView(TextView(this).apply {
                    text = desc
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 12f
                    setPadding(0, dp(2), 0, 0)
                })
            }

            card.addView(TextView(this).apply {
                text = sql
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 11f
                typeface = Typeface.MONOSPACE
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, dp(6))
            })

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }

            btnRow.addView(Button(this).apply {
                text = "Load"
                textSize = 12f
                isAllCaps = false
                background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.btn_accent)
                setTextColor(ThemeManager.color(C.CARD_BG))
                setPadding(dp(12), dp(4), dp(12), dp(4))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)).apply { marginEnd = dp(6) }
                val dialogRef = arrayOfNulls<AlertDialog>(1)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    this@ExplorerActivity.findViewById<EditText>(R.id.explorer_sql_input).setText(sql)
                }
                tag = dialogRef
            })

            btnRow.addView(Button(this).apply {
                text = "Edit"
                textSize = 12f
                isAllCaps = false
                setTextColor(ThemeManager.color(C.TEXT))
                background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.btn_secondary)
                setPadding(dp(12), dp(4), dp(12), dp(4))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)).apply { marginEnd = dp(6) }
                val dialogRef = arrayOfNulls<AlertDialog>(1)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    showSaveQueryDialog(editId = id, prefillName = name, prefillDesc = desc, prefillSql = sql)
                }
                tag = dialogRef
            })

            btnRow.addView(Button(this).apply {
                text = "Delete"
                textSize = 12f
                isAllCaps = false
                setTextColor(ThemeManager.color(C.ERROR))
                background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.btn_secondary)
                setPadding(dp(12), dp(4), dp(12), dp(4))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
                val dialogRef = arrayOfNulls<AlertDialog>(1)
                setOnClickListener {
                    AlertDialog.Builder(this@ExplorerActivity)
                        .setMessage("Delete \"$name\"?")
                        .setPositiveButton("Delete") { _, _ ->
                            db.deleteSqlLibraryEntry(id)
                            dialogRef[0]?.dismiss()
                            showSqlLibrary()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                tag = dialogRef
            })

            card.addView(btnRow)
            content.addView(card)
        }

        scrollView.addView(content)

        val dialog = AlertDialog.Builder(this)
            .setTitle("SQL Library (${library.length()})")
            .setView(scrollView)
            .setNegativeButton("Close", null)
            .create()

        fun setDialogRefs(parent: LinearLayout) {
            for (i in 0 until parent.childCount) {
                val card = parent.getChildAt(i) as? LinearLayout ?: continue
                for (j in 0 until card.childCount) {
                    val row = card.getChildAt(j) as? LinearLayout ?: continue
                    for (k in 0 until row.childCount) {
                        val btn = row.getChildAt(k) as? Button ?: continue
                        val ref = btn.tag as? Array<*> ?: continue
                        @Suppress("UNCHECKED_CAST")
                        (ref as Array<AlertDialog?>)[0] = dialog
                    }
                }
            }
        }
        setDialogRefs(content)

        dialog.show()
    }

    // ========== Clear ==========

    private fun clearQuery() {
        conditions.clear()
        conditions.add(Condition("title", "contains", "", ""))
        renderConditions()
        findViewById<EditText>(R.id.explorer_sql_input).setText("")
        findViewById<LinearLayout>(R.id.explorer_results).visibility = View.GONE
        findViewById<TextView>(R.id.explorer_result_count).text = ""
        lastResults.clear()
        lastRawCols = null
    }

    // ========== UI Helpers ==========

    private fun makeChip(text: String, active: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(
                if (active) ThemeManager.color(C.CARD_BG)
                else ThemeManager.color(C.TEXT)
            )
            setBackgroundColor(
                if (active) ThemeManager.color(C.ACCENT)
                else Color.TRANSPARENT
            )
            background = if (active) {
                ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.btn_accent)
            } else {
                ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.btn_secondary)
            }
            setPadding(dp(10), dp(5), dp(10), dp(5))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(4); bottomMargin = dp(4) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun makeSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 11f
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    setBackgroundColor(ThemeManager.color(C.INPUT_BG))
                    textSize = 12f
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                }
            }
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun buildTable(headers: List<String>, rows: List<List<String>>): LinearLayout {
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.input_bg)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        // Header row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ThemeManager.color(C.CARD_BG))
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        for (h in headers) {
            headerRow.addView(TextView(this).apply {
                text = h
                setTextColor(ThemeManager.color(C.ACCENT))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                minWidth = dp(60)
                maxWidth = dp(160)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        table.addView(headerRow)

        // Data rows
        for (row in rows) {
            val dataRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            for (cell in row) {
                dataRow.addView(TextView(this).apply {
                    text = cell
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 11f
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    minWidth = dp(60)
                    maxWidth = dp(160)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            table.addView(dataRow)
        }

        return table
    }

    private fun buildClickableTable(headers: List<String>, rows: List<Pair<Int, List<String>>>, onRowClick: (Int) -> Unit): LinearLayout {
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.input_bg)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ThemeManager.color(C.CARD_BG))
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        for (h in headers) {
            headerRow.addView(TextView(this).apply {
                text = h
                setTextColor(ThemeManager.color(C.ACCENT))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                minWidth = dp(60)
                maxWidth = dp(160)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        table.addView(headerRow)

        for ((idx, rowData) in rows) {
            val dataRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
                isClickable = true
                isFocusable = true
                background = ContextCompat.getDrawable(this@ExplorerActivity, R.drawable.entry_row_bg)
                setOnClickListener { onRowClick(idx) }
            }
            for (cell in rowData) {
                dataRow.addView(TextView(this).apply {
                    text = cell
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 11f
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    minWidth = dp(60)
                    maxWidth = dp(160)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            table.addView(dataRow)
        }

        return table
    }

    private fun truncCell(str: String): String {
        return if (str.length > 100) str.substring(0, 100) + "..." else str
    }

    private fun formatWeather(weather: JSONObject): String {
        val temp = weather.optDouble("temp", Double.NaN)
        val unit = weather.optString("unit", "C")
        val desc = weather.optString("description", "")
        val parts = mutableListOf<String>()
        if (!temp.isNaN()) parts.add("${"%.1f".format(temp)}°$unit")
        if (desc.isNotEmpty()) parts.add(desc)
        return parts.joinToString("  ")
    }

    private fun jsonArrayToStr(arr: JSONArray?): String {
        arr ?: return ""
        val items = mutableListOf<String>()
        for (i in 0 until arr.length()) items.add(arr.optString(i, ""))
        return items.filter { it.isNotEmpty() }.joinToString(", ")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    class FlowLayout(context: android.content.Context) : ViewGroup(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
            var x = 0; var y = 0; var rowH = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
                val cw = child.measuredWidth; val ch = child.measuredHeight
                if (x + cw > maxWidth && x > 0) { x = 0; y += rowH; rowH = 0 }
                x += cw; rowH = maxOf(rowH, ch)
            }
            setMeasuredDimension(maxWidth, y + rowH)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val maxWidth = r - l
            var x = 0; var y = 0; var rowH = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val cw = child.measuredWidth; val ch = child.measuredHeight
                if (x + cw > maxWidth && x > 0) { x = 0; y += rowH; rowH = 0 }
                child.layout(x, y, x + cw, y + ch)
                x += cw; rowH = maxOf(rowH, ch)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (lastThemeVersion != ThemeManager.themeVersion) {
            finish()
            return
        }
    }

}
