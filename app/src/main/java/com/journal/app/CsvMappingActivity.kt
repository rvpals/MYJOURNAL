package com.journal.app

import android.graphics.Typeface
import android.os.Bundle
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

    companion object {
        @JvmStatic var databaseService: DatabaseService? = null

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
