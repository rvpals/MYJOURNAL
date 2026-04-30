package com.journal.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
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

class CsvMappingActivity : AppCompatActivity() {

    private var lastThemeVersion = 0
    private var csvUri: Uri? = null
    private var csvFileName: String = ""

    companion object {
        @JvmStatic var databaseService: DatabaseService? = null
        private const val CSV_PICK = 3001

        val ENTRY_FIELDS = listOf(
            "" to "-- Skip --",
            "date" to "Date",
            "time" to "Time",
            "title" to "Title",
            "content" to "Content",
            "richContent" to "Rich Content (HTML)",
            "categories" to "Categories",
            "tags" to "Tags",
            "people" to "People",
            "placeName" to "Place Name",
            "placeAddress" to "Place Address",
            "placeCoords" to "Place Coords (lat, lng)"
        )
    }

    private lateinit var db: DatabaseService
    private lateinit var contentContainer: LinearLayout

    private data class MappingRow(
        val csvField: String,
        val fieldSpinner: Spinner,
        val miscInput: EditText
    )

    private val mappingRows = mutableListOf<MappingRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_csv_mapping)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        val dbRef = databaseService
        if (dbRef == null) { finish(); return }
        db = dbRef

        contentContainer = findViewById(R.id.mapping_content)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_save_mapping).setOnClickListener { saveMapping() }
        findViewById<Button>(R.id.btn_select_csv).setOnClickListener { pickCsvFile() }
        findViewById<Button>(R.id.btn_test_import).setOnClickListener { testImport() }

        loadOrCreateMapping()
    }

    private fun loadOrCreateMapping(): Boolean {
        val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
        val existing = settings.optJSONArray("csvMapping")

        if (existing != null && existing.length() > 0) {
            buildMappingUI(existing)
            return true
        }

        buildDefaultMappingUI()
        return false
    }

    private fun buildDefaultMappingUI() {
        val defaultFields = listOf(
            "Date", "Time", "Title", "Content", "Rich Content",
            "Categories", "Tags", "People", "Place Name"
        )
        val arr = JSONArray()
        for (field in defaultFields) {
            arr.put(JSONObject().apply {
                put("csvField", field)
                put("entryField", autoDetect(field))
                put("misc", defaultMisc(autoDetect(field)))
            })
        }
        buildMappingUI(arr)
    }

    private fun autoDetect(header: String): String {
        val h = header.lowercase().replace(Regex("[^a-z0-9]"), "")
        return when {
            h.matches(Regex("^(date|entrydate)$")) -> "date"
            h.matches(Regex("^(time|entrytime)$")) -> "time"
            h.matches(Regex("^(title|subject|heading)$")) -> "title"
            h.matches(Regex("^(content|body|text|description|note)$")) -> "content"
            h.matches(Regex("^(richcontent|html|htmlcontent)$")) -> "richContent"
            h.startsWith("categor") -> "categories"
            h.startsWith("tag") -> "tags"
            h.matches(Regex("^(people|person|persons)$")) -> "people"
            h.matches(Regex("^(place|placename|location|locationname)$")) -> "placeName"
            h.matches(Regex("^(address|placeaddress)$")) -> "placeAddress"
            h.matches(Regex("^(coord|gps|latl|placecoord).*")) -> "placeCoords"
            else -> ""
        }
    }

    private fun defaultMisc(entryField: String): String {
        return when (entryField) {
            "date" -> "YYYY-MM-DD"
            "time" -> "HH:mm"
            else -> ""
        }
    }

    private fun buildMappingUI(mappings: JSONArray) {
        contentContainer.removeAllViews()
        mappingRows.clear()

        // Description
        contentContainer.addView(TextView(this).apply {
            text = "Define how CSV columns map to journal entry fields.\nUse \"Misc\" for date/time formats."
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
            setPadding(dp(4), dp(4), dp(4), dp(12))
        })

        // Column headers
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        for ((label, weight) in listOf("CSV Field" to 1.2f, "Entry Field" to 1.5f, "Misc" to 1f)) {
            headerRow.addView(TextView(this).apply {
                text = label
                setTextColor(ThemeManager.color(C.ACCENT))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
                    marginEnd = dp(4)
                }
            })
        }
        contentContainer.addView(headerRow)

        // Divider
        contentContainer.addView(View(this).apply {
            setBackgroundColor(ThemeManager.color(C.TEXT_SECONDARY))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { bottomMargin = dp(6) }
        })

        // Mapping rows
        for (i in 0 until mappings.length()) {
            val m = mappings.optJSONObject(i) ?: continue
            addMappingRow(
                m.optString("csvField", ""),
                m.optString("entryField", ""),
                m.optString("misc", "")
            )
        }

        // Add Row button
        contentContainer.addView(Button(this).apply {
            text = "+ Add Row"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.ACCENT))
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.btn_secondary)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)
            ).apply { topMargin = dp(8) }
            setOnClickListener { addMappingRowBeforeButton(this) }
        })

        // Separator options section
        contentContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(16)
            )
        })
        contentContainer.addView(TextView(this).apply {
            text = "Import Options"
            setTextColor(ThemeManager.color(C.ACCENT))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(4), dp(4), dp(8))
        })

        // Multi-value separator
        val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
        val sepRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        sepRow.addView(TextView(this).apply {
            text = "Separator (categories, tags)"
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val sepInput = EditText(this).apply {
            tag = "csv_separator"
            setText(settings.optString("csvSeparator", ","))
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.input_bg)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        sepRow.addView(sepInput)
        contentContainer.addView(sepRow)

        // Tags space separator checkbox
        val tagsSpaceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }
        val tagsSpaceCb = CheckBox(this).apply {
            tag = "csv_tags_space_sep"
            isChecked = settings.optBoolean("csvTagsSpaceSep", false)
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
        }
        tagsSpaceRow.addView(tagsSpaceCb)
        tagsSpaceRow.addView(TextView(this).apply {
            text = "Use space to separate tags"
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
            setOnClickListener { tagsSpaceCb.isChecked = !tagsSpaceCb.isChecked }
        })
        contentContainer.addView(tagsSpaceRow)
    }

    private fun addMappingRow(csvField: String, entryField: String, misc: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        // CSV field name (editable)
        val csvInput = EditText(this).apply {
            setText(csvField)
            textSize = 13f
            setTextColor(ThemeManager.color(C.TEXT))
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.input_bg)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginEnd = dp(4)
            }
        }
        row.addView(csvInput)

        // Entry field dropdown
        val spinner = Spinner(this).apply {
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.spinner_bg)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f).apply {
                marginEnd = dp(4)
            }
        }
        val labels = ENTRY_FIELDS.map { it.second }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 12f
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    setBackgroundColor(ThemeManager.color(C.INPUT_BG))
                    textSize = 13f
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val selectedIdx = ENTRY_FIELDS.indexOfFirst { it.first == entryField }.coerceAtLeast(0)
        spinner.setSelection(selectedIdx)

        row.addView(spinner)

        // Misc input (date/time format)
        val miscInput = EditText(this).apply {
            setText(misc)
            hint = "format"
            textSize = 12f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.input_bg)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
        }
        row.addView(miscInput)

        // Delete button
        row.addView(Button(this).apply {
            text = "✕"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.btn_secondary)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setOnClickListener {
                val idx = mappingRows.indexOfFirst { it.fieldSpinner === spinner }
                if (idx >= 0) {
                    mappingRows.removeAt(idx)
                    contentContainer.removeView(row)
                }
            }
        })

        contentContainer.addView(row)
        mappingRows.add(MappingRow(csvField, spinner, miscInput))
    }

    private fun addMappingRowBeforeButton(addButton: View) {
        val idx = contentContainer.indexOfChild(addButton)
        addMappingRowAt(idx, "", "", "")
    }

    private fun addMappingRowAt(insertIdx: Int, csvField: String, entryField: String, misc: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        val csvInput = EditText(this).apply {
            setText(csvField)
            hint = "Column name"
            textSize = 13f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.input_bg)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f).apply {
                marginEnd = dp(4)
            }
        }
        row.addView(csvInput)

        val spinner = Spinner(this).apply {
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.spinner_bg)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f).apply {
                marginEnd = dp(4)
            }
        }
        val labels = ENTRY_FIELDS.map { it.second }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 12f
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    setBackgroundColor(ThemeManager.color(C.INPUT_BG))
                    textSize = 13f
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val selectedIdx = ENTRY_FIELDS.indexOfFirst { it.first == entryField }.coerceAtLeast(0)
        spinner.setSelection(selectedIdx)
        row.addView(spinner)

        val miscInput = EditText(this).apply {
            setText(misc)
            hint = "format"
            textSize = 12f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.input_bg)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
        }
        row.addView(miscInput)

        row.addView(Button(this).apply {
            text = "✕"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.btn_secondary)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setOnClickListener {
                val rmIdx = mappingRows.indexOfFirst { it.fieldSpinner === spinner }
                if (rmIdx >= 0) {
                    mappingRows.removeAt(rmIdx)
                    contentContainer.removeView(row)
                }
            }
        })

        contentContainer.addView(row, insertIdx)
        mappingRows.add(MappingRow(csvField, spinner, miscInput))
    }

    private fun saveMapping() {
        val arr = JSONArray()
        for (row in mappingRows) {
            val parent = row.fieldSpinner.parent as? LinearLayout ?: continue
            val csvInput = parent.getChildAt(0) as? EditText ?: continue
            val csvFieldName = csvInput.text.toString().trim()
            if (csvFieldName.isEmpty()) continue

            val selectedIdx = row.fieldSpinner.selectedItemPosition
            val entryField = ENTRY_FIELDS.getOrNull(selectedIdx)?.first ?: ""
            val misc = row.miscInput.text.toString().trim()

            arr.put(JSONObject().apply {
                put("csvField", csvFieldName)
                put("entryField", entryField)
                put("misc", misc)
            })
        }

        // Also save separator
        val sepInput = contentContainer.findViewWithTag<EditText>("csv_separator")
        val separator = sepInput?.text?.toString()?.trim() ?: ","

        val tagsSpaceCb = contentContainer.findViewWithTag<CheckBox>("csv_tags_space_sep")
        val tagsSpaceSep = tagsSpaceCb?.isChecked ?: false

        val settingsUpdate = JSONObject().apply {
            put("csvMapping", arr)
            put("csvSeparator", separator)
            put("csvTagsSpaceSep", tagsSpaceSep)
        }
        db.setSettings(settingsUpdate.toString())

        Toast.makeText(this, "CSV mapping saved (${arr.length()} fields)", Toast.LENGTH_SHORT).show()
        finish()
    }

    // ========== CSV File Selection ==========

    @Suppress("DEPRECATION")
    private fun pickCsvFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "text/comma-separated-values", "application/csv"))
        }
        startActivityForResult(intent, CSV_PICK)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        if (requestCode == CSV_PICK) {
            csvUri = data.data
            csvFileName = ""
            try {
                val cursor = contentResolver.query(data.data!!, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) csvFileName = it.getString(nameIdx) ?: ""
                    }
                }
            } catch (_: Exception) {}
            if (csvFileName.isEmpty()) csvFileName = "selected file"

            val text = contentResolver.openInputStream(data.data!!)?.bufferedReader()?.readText() ?: ""
            if (text.isBlank()) {
                Toast.makeText(this, "CSV file is empty", Toast.LENGTH_SHORT).show()
                csvUri = null
                csvFileName = ""
                return
            }

            val allRows = parseCSV(text)
            if (allRows.size < 2) {
                Toast.makeText(this, "CSV needs a header row and at least one data row", Toast.LENGTH_SHORT).show()
                csvUri = null
                csvFileName = ""
                return
            }

            val csvHeaders = allRows[0]
            autoMapFromHeaders(csvHeaders)
            Toast.makeText(this, "Loaded: $csvFileName (${allRows.size - 1} rows, ${csvHeaders.size} columns)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun autoMapFromHeaders(csvHeaders: List<String>) {
        contentContainer.removeAllViews()
        mappingRows.clear()

        val arr = JSONArray()
        for (header in csvHeaders) {
            arr.put(JSONObject().apply {
                put("csvField", header)
                put("entryField", autoDetect(header))
                put("misc", defaultMisc(autoDetect(header)))
            })
        }

        val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
        val existing = settings.optJSONArray("csvMapping")
        if (existing != null && existing.length() > 0) {
            val savedMap = mutableMapOf<String, Pair<String, String>>()
            for (i in 0 until existing.length()) {
                val m = existing.optJSONObject(i) ?: continue
                savedMap[m.optString("csvField", "").lowercase()] =
                    m.optString("entryField", "") to m.optString("misc", "")
            }
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val saved = savedMap[obj.getString("csvField").lowercase()]
                if (saved != null) {
                    obj.put("entryField", saved.first)
                    obj.put("misc", saved.second)
                }
            }
        }
        buildMappingUI(arr)
    }

    // ========== Test Import ==========

    private fun testImport() {
        val uri = csvUri
        if (uri == null) {
            Toast.makeText(this, "Please select a CSV file first", Toast.LENGTH_SHORT).show()
            return
        }

        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
        } catch (_: Exception) { "" }
        if (text.isBlank()) {
            Toast.makeText(this, "Could not read CSV file", Toast.LENGTH_SHORT).show()
            return
        }

        val allRows = parseCSV(text)
        if (allRows.size < 2) {
            Toast.makeText(this, "CSV needs a header and at least one data row", Toast.LENGTH_SHORT).show()
            return
        }

        val csvHeaders = allRows[0]
        val dataRows = allRows.subList(1, allRows.size)

        val currentMapping = getCurrentMapping()
        if (currentMapping.length() == 0) {
            Toast.makeText(this, "No mapping fields defined", Toast.LENGTH_SHORT).show()
            return
        }

        val sepInput = contentContainer.findViewWithTag<EditText>("csv_separator")
        val separator = sepInput?.text?.toString()?.trim() ?: ","
        val tagsSpaceCb = contentContainer.findViewWithTag<CheckBox>("csv_tags_space_sep")
        val tagsSpaceSep = tagsSpaceCb?.isChecked ?: false

        val colMap = mutableListOf<Triple<Int, String, String>>()
        for (i in 0 until currentMapping.length()) {
            val m = currentMapping.optJSONObject(i) ?: continue
            val csvField = m.optString("csvField", "")
            val entryField = m.optString("entryField", "")
            if (csvField.isEmpty() || entryField.isEmpty()) continue
            val colIdx = csvHeaders.indexOfFirst { it.equals(csvField, ignoreCase = true) }
            if (colIdx < 0) continue
            colMap.add(Triple(colIdx, entryField, m.optString("misc", "")))
        }

        if (colMap.isEmpty()) {
            Toast.makeText(this, "No CSV columns matched the mapping", Toast.LENGTH_SHORT).show()
            return
        }

        val sampleRows = if (dataRows.size <= 20) dataRows
        else dataRows.shuffled().take(20)

        val mappedHeaders = colMap.map { (_, field, _) ->
            ENTRY_FIELDS.firstOrNull { it.first == field }?.second ?: field
        }
        val mappedRows = sampleRows.map { row ->
            colMap.map { (colIdx, entryField, misc) ->
                val value = row.getOrElse(colIdx) { "" }.trim()
                formatPreviewValue(value, entryField, misc, separator, tagsSpaceSep)
            }
        }

        showResultGrid(mappedHeaders, mappedRows, sampleRows.size, dataRows.size)
    }

    private fun formatPreviewValue(value: String, entryField: String, misc: String, separator: String, tagsSpaceSep: Boolean): String {
        if (value.isEmpty()) return ""
        return when (entryField) {
            "date" -> parseDateWithFormat(value, misc)
            "time" -> parseTimeWithFormat(value, misc)
            "categories" -> value.split(separator).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            "tags" -> {
                val tagSep = if (tagsSpaceSep) Regex("\\s+") else Regex(Regex.escape(separator))
                value.split(tagSep).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            }
            "people" -> value.split(separator).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
            else -> value
        }
    }

    private fun getCurrentMapping(): JSONArray {
        val arr = JSONArray()
        for (row in mappingRows) {
            val parent = row.fieldSpinner.parent as? LinearLayout ?: continue
            val csvInput = parent.getChildAt(0) as? EditText ?: continue
            val csvFieldName = csvInput.text.toString().trim()
            if (csvFieldName.isEmpty()) continue
            val selectedIdx = row.fieldSpinner.selectedItemPosition
            val entryField = ENTRY_FIELDS.getOrNull(selectedIdx)?.first ?: ""
            val misc = row.miscInput.text.toString().trim()
            arr.put(JSONObject().apply {
                put("csvField", csvFieldName)
                put("entryField", entryField)
                put("misc", misc)
            })
        }
        return arr
    }

    // ========== Result Grid ==========

    private fun showResultGrid(headers: List<String>, rows: List<List<String>>, shown: Int, total: Int) {
        val existing = contentContainer.findViewWithTag<View>("test_result_section")
        if (existing != null) contentContainer.removeView(existing)

        val section = LinearLayout(this).apply {
            tag = "test_result_section"
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        section.addView(View(this).apply {
            setBackgroundColor(ThemeManager.color(C.TEXT_SECONDARY))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { bottomMargin = dp(8) }
        })

        section.addView(TextView(this).apply {
            text = "Test Preview — $shown of $total rows (mapped)"
            setTextColor(ThemeManager.color(C.ACCENT))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(4), dp(4), dp(8))
        })

        val hsv = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        hsv.addView(buildResultTable(headers, rows))
        section.addView(hsv)

        contentContainer.addView(section)
    }

    private fun buildResultTable(headers: List<String>, rows: List<List<String>>): LinearLayout {
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@CsvMappingActivity, R.drawable.input_bg)
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
                minWidth = dp(80)
                maxWidth = dp(180)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        table.addView(headerRow)

        for ((idx, row) in rows.withIndex()) {
            val dataRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(if (idx % 2 == 0) ThemeManager.color(C.CARD_BG) else ThemeManager.color(C.INPUT_BG))
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            for (cell in row) {
                dataRow.addView(TextView(this).apply {
                    text = cell
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 11f
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    minWidth = dp(80)
                    maxWidth = dp(180)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            table.addView(dataRow)
        }

        return table
    }

    // ========== CSV Parsing (same as SettingsActivity) ==========

    private fun parseCSV(text: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var current = StringBuilder()
        var inQuotes = false
        var row = mutableListOf<String>()

        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val next = text.getOrNull(i + 1)

            if (inQuotes) {
                if (ch == '"' && next == '"') {
                    current.append('"')
                    i++
                } else if (ch == '"') {
                    inQuotes = false
                } else {
                    current.append(ch)
                }
            } else {
                when {
                    ch == '"' -> inQuotes = true
                    ch == ',' -> { row.add(current.toString().trim()); current = StringBuilder() }
                    ch == '\r' && next == '\n' -> {
                        row.add(current.toString().trim()); current = StringBuilder()
                        rows.add(row); row = mutableListOf(); i++
                    }
                    ch == '\n' -> {
                        row.add(current.toString().trim()); current = StringBuilder()
                        rows.add(row); row = mutableListOf()
                    }
                    else -> current.append(ch)
                }
            }
            i++
        }
        if (current.isNotEmpty() || row.isNotEmpty()) {
            row.add(current.toString().trim())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotEmpty() } }
    }

    private fun parseDateWithFormat(value: String, fmt: String): String {
        if (value.isEmpty()) return ""
        if (fmt.isEmpty()) return normalizeDate(value)

        val escaped = Regex("""[.*+?^${'$'}{}()\[\]\\|]""").replace(fmt) { "\\${it.value}" }
        val pattern = Regex("YYYY|YY|MM|DD|M|D").replace(escaped) { mr ->
            when (mr.value) {
                "YYYY" -> """(?<Y>\d{4})"""
                "YY" -> """(?<Y>\d{2})"""
                "MM", "M" -> """(?<M>\d{1,2})"""
                "DD", "D" -> """(?<D>\d{1,2})"""
                else -> mr.value
            }
        }

        val match = Regex("^$pattern$").find(value)
        if (match != null) {
            val y = match.groups["Y"]?.value ?: return normalizeDate(value)
            val m = match.groups["M"]?.value ?: return normalizeDate(value)
            val d = match.groups["D"]?.value ?: return normalizeDate(value)
            val year = if (y.length == 2) (if (y.toInt() > 50) "19$y" else "20$y") else y
            return "$year-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
        }
        return normalizeDate(value)
    }

    private fun parseTimeWithFormat(value: String, fmt: String): String {
        if (value.isEmpty()) return ""
        if (fmt.isEmpty()) return normalizeTime(value)

        val escaped = Regex("""[.*+?^${'$'}{}()\[\]\\|]""").replace(fmt) { "\\${it.value}" }
        val pattern = Regex("HH|H|mm|A|a").replace(escaped) { mr ->
            when (mr.value) {
                "HH", "H" -> """(?<H>\d{1,2})"""
                "mm" -> """(?<m>\d{2})"""
                "A", "a" -> "(?<A>[AaPp][Mm])"
                else -> mr.value
            }
        }

        val match = Regex("^$pattern$").find(value.trim())
        if (match != null) {
            var h = match.groups["H"]?.value?.toIntOrNull() ?: return normalizeTime(value)
            val min = match.groups["m"]?.value ?: "00"
            val ampm = match.groups["A"]?.value
            if (ampm != null) {
                val pm = ampm.lowercase() == "pm"
                if (pm && h < 12) h += 12
                if (!pm && h == 12) h = 0
            }
            return "${h.toString().padStart(2, '0')}:$min"
        }
        return normalizeTime(value)
    }

    private fun normalizeDate(value: String): String {
        if (value.isEmpty()) return ""
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value)) return value
        val m = Regex("^(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})$").find(value)
        if (m != null) return "${m.groupValues[3]}-${m.groupValues[1].padStart(2, '0')}-${m.groupValues[2].padStart(2, '0')}"
        return try {
            val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            d.format(java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.US).parse(value)!!)
        } catch (_: Exception) { value }
    }

    private fun normalizeTime(value: String): String {
        if (value.isEmpty()) return ""
        if (Regex("^\\d{2}:\\d{2}$").matches(value)) return value
        val m = Regex("^(\\d{1,2}):(\\d{2})\\s*(am|pm)?$", RegexOption.IGNORE_CASE).find(value)
        if (m != null) {
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2]
            val ampm = m.groupValues[3]
            if (ampm.isNotEmpty()) {
                val pm = ampm.lowercase() == "pm"
                if (pm && h < 12) h += 12
                if (!pm && h == 12) h = 0
            }
            return "${h.toString().padStart(2, '0')}:$min"
        }
        return value
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onResume() {
        super.onResume()
        if (lastThemeVersion != ThemeManager.themeVersion) {
            recreate()
            return
        }
    }

}
