package com.journal.app

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journal.app.ThemeManager.C
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WidgetEditorActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic var databaseService: DatabaseService? = null
        @JvmStatic var bootstrapService: BootstrapService? = null
        @JvmStatic var pendingWidgetId: String? = null
    }

    private lateinit var db: DatabaseService
    private lateinit var bs: BootstrapService

    private lateinit var contentContainer: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var deleteBtn: Button

    private var widget = JSONObject()
    private var isNew = true
    private var filters = JSONArray()
    private var functions = JSONArray()
    private var activeTab = "header"

    private var iconDataUrl = ""

    private val filterFields = listOf(
        "date" to "Date", "time" to "Time", "title" to "Title",
        "content" to "Content", "categories" to "Categories",
        "tags" to "Tags", "placeName" to "Place Name"
    )

    private val dateOps = listOf(
        "after" to "after", "before" to "before",
        "equals" to "equals", "between" to "between"
    )
    private val textOps = listOf(
        "contains" to "contains", "equals" to "equals",
        "starts with" to "starts with", "ends with" to "ends with",
        "is empty" to "is empty", "is not empty" to "is not empty"
    )
    private val arrayOps = listOf(
        "includes" to "includes", "not includes" to "not includes",
        "is empty" to "is empty", "is not empty" to "is not empty"
    )

    private val aggFuncs = listOf("Count", "Sum", "Max", "Min", "Average")
    private val aggFields = listOf(
        "entries" to "Entries", "tags" to "Tags", "categories" to "Categories",
        "placeName" to "Place Name", "title" to "Title"
    )

    // Header form references
    private lateinit var nameInput: EditText
    private lateinit var descInput: EditText
    private lateinit var colorInput: EditText
    private lateinit var colorEnabledCb: CheckBox
    private lateinit var enabledDashboardCb: CheckBox

    private val iconPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val stream = contentResolver.openInputStream(uri) ?: return@registerForActivityResult
            val original = BitmapFactory.decodeStream(stream)
            stream.close()
            if (original != null) {
                val scaled = android.graphics.Bitmap.createScaledBitmap(original, 64, 64, true)
                val baos = java.io.ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                iconDataUrl = "data:image/png;base64,$b64"
                if (activeTab == "header") showTab("header")
            }
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_editor)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        db = databaseService ?: run { finish(); return }
        bs = bootstrapService ?: run { finish(); return }
        databaseService = null
        bootstrapService = null

        contentContainer = findViewById(R.id.content_container)
        titleView = findViewById(R.id.editor_title)
        deleteBtn = findViewById(R.id.btn_delete)

        val widgetId = pendingWidgetId
        pendingWidgetId = null

        if (widgetId != null) {
            val json = db.getWidgetById(widgetId)
            val loaded = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
            if (loaded.has("id")) {
                widget = loaded
                isNew = false
            }
        }

        if (isNew) {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            widget = JSONObject().apply {
                put("id", "w_${generateId()}")
                put("name", "")
                put("description", "")
                put("bgColor", "")
                put("icon", "")
                put("enabledInDashboard", true)
                put("sortOrder", 0)
                put("dtCreated", now)
                put("dtUpdated", now)
                put("filters", JSONArray())
                put("functions", JSONArray())
            }
        }

        filters = widget.optJSONArray("filters") ?: JSONArray()
        functions = widget.optJSONArray("functions") ?: JSONArray()
        iconDataUrl = widget.optString("icon", "")

        titleView.text = if (isNew) "New Widget" else "Edit Widget"

        if (!isNew) {
            deleteBtn.visibility = View.VISIBLE
            deleteBtn.setOnClickListener { confirmDelete() }
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveWidget() }
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_preview).setOnClickListener { showPreview() }

        setupTabs()
        showTab("header")
    }

    // ========== Tabs ==========

    private fun setupTabs() {
        findViewById<Button>(R.id.tab_header).setOnClickListener { showTab("header") }
        findViewById<Button>(R.id.tab_filters).setOnClickListener { showTab("filters") }
        findViewById<Button>(R.id.tab_functions).setOnClickListener { showTab("functions") }
    }

    private fun showTab(tab: String) {
        syncFormToWidget()
        activeTab = tab

        val tabs = mapOf(
            "header" to R.id.tab_header,
            "filters" to R.id.tab_filters,
            "functions" to R.id.tab_functions
        )
        for ((key, id) in tabs) {
            val btn = findViewById<Button>(id)
            if (key == tab) {
                btn.background = ContextCompat.getDrawable(this, R.drawable.btn_accent)
                btn.setTextColor(ThemeManager.color(C.CARD_BG))
            } else {
                btn.background = ContextCompat.getDrawable(this, R.drawable.btn_secondary)
                btn.setTextColor(ThemeManager.color(C.TEXT))
            }
        }

        contentContainer.removeAllViews()
        when (tab) {
            "header" -> buildHeaderTab()
            "filters" -> buildFiltersTab()
            "functions" -> buildFunctionsTab()
        }
    }

    private fun syncFormToWidget() {
        if (activeTab == "header" && ::nameInput.isInitialized) {
            widget.put("name", nameInput.text.toString().trim())
            widget.put("description", descInput.text.toString().trim())
            val colorEnabled = colorEnabledCb.isChecked
            widget.put("bgColor", if (colorEnabled) colorInput.text.toString().trim() else "")
            widget.put("enabledInDashboard", enabledDashboardCb.isChecked)
            widget.put("icon", iconDataUrl)
        }
        widget.put("filters", filters)
        widget.put("functions", functions)
    }

    // ========== Header Tab ==========

    private fun buildHeaderTab() {
        addSectionHeader("Widget Info")

        addLabel("Name")
        nameInput = makeInput("Widget name", widget.optString("name"))
        contentContainer.addView(nameInput)

        addLabel("Description")
        descInput = makeInput("Description (optional)", widget.optString("description"))
        contentContainer.addView(descInput)

        addLabel("Background Color")
        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        }

        colorEnabledCb = CheckBox(this).apply {
            isChecked = widget.optString("bgColor").isNotEmpty()
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
        }
        colorRow.addView(colorEnabledCb)

        colorInput = EditText(this).apply {
            setText(widget.optString("bgColor").ifEmpty { "#4a90d9" })
            hint = "#4a90d9"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
            isSingleLine = true
        }
        colorRow.addView(colorInput)

        // Color preview swatch
        val swatch = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(8) }
            val bg = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(parseColorSafe(colorInput.text.toString(), Color.parseColor("#4a90d9")))
            }
            background = bg
            isClickable = true
            isFocusable = true
            setOnClickListener {
                showColorPickerDialog(colorInput.text.toString().ifEmpty { "#4a90d9" }) { hex ->
                    colorInput.setText(hex)
                    colorEnabledCb.isChecked = true
                }
            }
        }
        colorRow.addView(swatch)

        colorInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val c = parseColorSafe(s.toString(), Color.parseColor("#4a90d9"))
                (swatch.background as? GradientDrawable)?.setColor(c)
            }
        })

        colorInput.setOnClickListener {
            showColorPickerDialog(colorInput.text.toString().ifEmpty { "#4a90d9" }) { hex ->
                colorInput.setText(hex)
                colorEnabledCb.isChecked = true
            }
        }
        colorInput.isFocusable = false
        colorInput.isFocusableInTouchMode = false

        contentContainer.addView(colorRow)

        // Icon
        addLabel("Widget Icon")
        val iconContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        }

        if (iconDataUrl.isNotEmpty()) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            loadBase64Image(iv, iconDataUrl)
            iconContainer.addView(iv)

            iconContainer.addView(Button(this).apply {
                text = "Remove"
                textSize = 12f
                isAllCaps = false
                setTextColor(ThemeManager.color(C.ERROR))
                background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.btn_secondary)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)).apply { marginEnd = dp(8) }
                setPadding(dp(10), dp(4), dp(10), dp(4))
                setOnClickListener {
                    iconDataUrl = ""
                    showTab("header")
                }
            })
        } else {
            iconContainer.addView(TextView(this).apply {
                text = "No icon"
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

        iconContainer.addView(Button(this).apply {
            text = "Browse"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { iconPicker.launch("image/*") }
        })

        contentContainer.addView(iconContainer)

        // Dashboard toggle
        addSpacer()
        enabledDashboardCb = CheckBox(this).apply {
            text = "Show in Dashboard"
            isChecked = widget.optBoolean("enabledInDashboard", true)
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        }
        contentContainer.addView(enabledDashboardCb)
    }

    // ========== Filters Tab ==========

    private fun buildFiltersTab() {
        addSectionHeader("Entry Filters")
        contentContainer.addView(TextView(this).apply {
            text = "All filters are combined with AND logic."
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        })

        if (filters.length() == 0) {
            contentContainer.addView(TextView(this).apply {
                text = "No filters — widget will use all entries."
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 13f
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
        } else {
            for (i in 0 until filters.length()) {
                val f = filters.optJSONObject(i) ?: continue
                contentContainer.addView(buildFilterRow(f, i))
            }
        }

        val addFilterBtn = Button(this)
        addFilterBtn.text = "+ Add Filter"
        addFilterBtn.textSize = 13f
        addFilterBtn.isAllCaps = false
        addFilterBtn.setTextColor(ThemeManager.color(C.ACCENT))
        addFilterBtn.setBackgroundColor(Color.TRANSPARENT)
        addFilterBtn.layoutParams = linParams().apply { topMargin = dp(8) }
        addFilterBtn.setOnClickListener {
            val f = JSONObject()
            f.put("field", "date")
            f.put("op", "after")
            f.put("value", "")
            f.put("value2", "")
            this.filters.put(f)
            showTab("filters")
        }
        contentContainer.addView(addFilterBtn)
    }

    private fun buildFilterRow(filter: JSONObject, index: Int): LinearLayout {
        val field = filter.optString("field", "title")
        val op = filter.optString("op", "contains")
        val value = filter.optString("value", "")
        val value2 = filter.optString("value2", "")

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.input_bg)
            layoutParams = linParams().apply { bottomMargin = dp(6) }
        }

        // Row 1: Field + Remove
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val fieldSpinner = makeSpinner(filterFields.map { it.second },
            filterFields.indexOfFirst { it.first == field }.coerceAtLeast(0))
        fieldSpinner.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        topRow.addView(fieldSpinner)

        val removeFilterBtn = Button(this)
        removeFilterBtn.text = "✕"
        removeFilterBtn.textSize = 12f
        removeFilterBtn.setTextColor(ThemeManager.color(C.ERROR))
        removeFilterBtn.setBackgroundColor(Color.TRANSPARENT)
        removeFilterBtn.layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        removeFilterBtn.setOnClickListener {
            removeJsonArrayItem(this.filters, index)
            showTab("filters")
        }
        topRow.addView(removeFilterBtn)
        card.addView(topRow)

        // Row 2: Operator
        val ops = opsForField(field)
        val opSpinner = makeSpinner(ops.map { it.second },
            ops.indexOfFirst { it.first == op }.coerceAtLeast(0))
        opSpinner.layoutParams = linParams().apply { topMargin = dp(4) }
        card.addView(opSpinner)

        // Row 3: Value input(s)
        val needsValue = !op.contains("empty")
        if (needsValue) {
            val fieldType = getFieldType(field)
            val valInput = EditText(this).apply {
                setText(value)
                hint = if (fieldType == "date") "YYYY-MM-DD" else "Value"
                textSize = 13f
                setTextColor(ThemeManager.color(C.TEXT))
                setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.input_bg)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = linParams().apply { topMargin = dp(4) }
                isSingleLine = true
                inputType = if (fieldType == "date") InputType.TYPE_CLASS_DATETIME else InputType.TYPE_CLASS_TEXT
            }
            valInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) filter.put("value", valInput.text.toString())
            }
            card.addView(valInput)

            if (op == "between") {
                val val2Input = EditText(this).apply {
                    setText(value2)
                    hint = "End value"
                    textSize = 13f
                    setTextColor(ThemeManager.color(C.TEXT))
                    setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.input_bg)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    layoutParams = linParams().apply { topMargin = dp(4) }
                    isSingleLine = true
                    inputType = InputType.TYPE_CLASS_DATETIME
                }
                val2Input.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) filter.put("value2", val2Input.text.toString())
                }
                card.addView(val2Input)
            }
        }

        // Spinner listeners
        fieldSpinner.onItemSelectedListener = spinnerListener { pos ->
            filter.put("field", filterFields[pos].first)
            val newOps = opsForField(filterFields[pos].first)
            filter.put("op", newOps[0].first)
            filter.put("value", "")
            filter.put("value2", "")
            showTab("filters")
        }
        opSpinner.onItemSelectedListener = spinnerListener { pos ->
            val currentOps = opsForField(filter.optString("field", "title"))
            if (pos < currentOps.size) {
                filter.put("op", currentOps[pos].first)
                showTab("filters")
            }
        }

        return card
    }

    // ========== Functions Tab ==========

    private fun buildFunctionsTab() {
        addSectionHeader("Aggregate Functions")
        contentContainer.addView(TextView(this).apply {
            text = "Define computations on filtered entries."
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        })

        if (functions.length() == 0) {
            contentContainer.addView(TextView(this).apply {
                text = "No functions added yet."
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 13f
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
        } else {
            for (i in 0 until functions.length()) {
                val fn = functions.optJSONObject(i) ?: continue
                contentContainer.addView(buildFuncRow(fn, i))
            }
        }

        val addFuncBtn = Button(this)
        addFuncBtn.text = "+ Add Function"
        addFuncBtn.textSize = 13f
        addFuncBtn.isAllCaps = false
        addFuncBtn.setTextColor(ThemeManager.color(C.ACCENT))
        addFuncBtn.setBackgroundColor(Color.TRANSPARENT)
        addFuncBtn.layoutParams = linParams().apply { topMargin = dp(8) }
        addFuncBtn.setOnClickListener {
            val fn = JSONObject()
            fn.put("func", "Count")
            fn.put("field", "entries")
            fn.put("value", "")
            fn.put("prefix", "")
            fn.put("postfix", "")
            this.functions.put(fn)
            showTab("functions")
        }
        contentContainer.addView(addFuncBtn)
    }

    private fun buildFuncRow(fn: JSONObject, index: Int): LinearLayout {
        val func = fn.optString("func", "Count")
        val field = fn.optString("field", "entries")
        val value = fn.optString("value", "")
        val prefix = fn.optString("prefix", "")
        val postfix = fn.optString("postfix", "")

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.input_bg)
            layoutParams = linParams().apply { bottomMargin = dp(6) }
        }

        // Row 1: Function + Field + Remove
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val funcSpinner = makeSpinner(aggFuncs, aggFuncs.indexOf(func).coerceAtLeast(0))
        funcSpinner.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        topRow.addView(funcSpinner)

        val fieldSpinner = makeSpinner(aggFields.map { it.second },
            aggFields.indexOfFirst { it.first == field }.coerceAtLeast(0))
        fieldSpinner.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        topRow.addView(fieldSpinner)

        val removeFuncBtn = Button(this)
        removeFuncBtn.text = "✕"
        removeFuncBtn.textSize = 12f
        removeFuncBtn.setTextColor(ThemeManager.color(C.ERROR))
        removeFuncBtn.setBackgroundColor(Color.TRANSPARENT)
        removeFuncBtn.layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        removeFuncBtn.setOnClickListener {
            removeJsonArrayItem(this.functions, index)
            showTab("functions")
        }
        topRow.addView(removeFuncBtn)
        card.addView(topRow)

        // Row 2: Value (for array/text target fields)
        val aggField = aggFields.find { it.first == field }
        val aggType = when (aggField?.first) {
            "entries" -> "count"
            "tags", "categories" -> "array"
            else -> "text"
        }
        if (aggType == "array" || aggType == "text") {
            val valInput = EditText(this).apply {
                setText(value)
                hint = "Match value (optional)"
                textSize = 13f
                setTextColor(ThemeManager.color(C.TEXT))
                setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.input_bg)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = linParams().apply { topMargin = dp(4) }
                isSingleLine = true
            }
            valInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) fn.put("value", valInput.text.toString())
            }
            card.addView(valInput)
        }

        // Row 3: Prefix + Postfix
        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = linParams().apply { topMargin = dp(4) }
        }

        val prefixInput = EditText(this).apply {
            setText(prefix)
            hint = "Prefix"
            textSize = 12f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.input_bg)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(4) }
            isSingleLine = true
        }
        prefixInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) fn.put("prefix", prefixInput.text.toString())
        }
        labelRow.addView(prefixInput)

        val postfixInput = EditText(this).apply {
            setText(postfix)
            hint = "Postfix"
            textSize = 12f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.input_bg)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f)
            isSingleLine = true
        }
        postfixInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) fn.put("postfix", postfixInput.text.toString())
        }
        labelRow.addView(postfixInput)
        card.addView(labelRow)

        // Spinner listeners
        funcSpinner.onItemSelectedListener = spinnerListener { pos ->
            fn.put("func", aggFuncs[pos])
        }
        fieldSpinner.onItemSelectedListener = spinnerListener { pos ->
            fn.put("field", aggFields[pos].first)
            fn.put("value", "")
            showTab("functions")
        }

        return card
    }

    // ========== Save / Delete ==========

    private fun saveWidget() {
        syncFormToWidget()

        val name = widget.optString("name", "").trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a widget name.", Toast.LENGTH_SHORT).show()
            showTab("header")
            nameInput.requestFocus()
            return
        }

        widget.put("name", name)
        widget.put("dtUpdated", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
        db.saveWidget(widget.toString())
        finish()
    }

    private fun confirmDelete() {
        val name = widget.optString("name", "Untitled")
        AlertDialog.Builder(this)
            .setMessage("Delete widget \"$name\"?")
            .setPositiveButton("Delete") { _, _ ->
                db.deleteWidget(widget.optString("id"))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== Preview ==========

    private fun showPreview() {
        syncFormToWidget()

        val name = widget.optString("name", "").ifEmpty { "Untitled" }
        val desc = widget.optString("description", "")
        val bgColor = widget.optString("bgColor", "")
        val icon = widget.optString("icon", "")

        val entries = try { JSONArray(db.getEntries()) } catch (_: Exception) { JSONArray() }
        val allEntries = mutableListOf<JSONObject>()
        for (i in 0 until entries.length()) {
            entries.optJSONObject(i)?.let { allEntries.add(it) }
        }

        val filtered = filterEntries(allEntries, filters)
        val results = computeFunctions(functions, filtered)

        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        scroll.addView(container)

        // Widget card preview
        val parsedBg = parseColorSafe(bgColor, 0)
        val hasBg = bgColor.isNotEmpty() && parsedBg != 0
        val textColor = if (hasBg) getContrastColor(parsedBg) else
            ThemeManager.color(C.TEXT)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            val bg = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                if (hasBg) setColor(parsedBg) else {
                    setColor(ThemeManager.color(C.CARD_BG))
                    setStroke(dp(1), ThemeManager.color(C.TEXT_SECONDARY))
                }
            }
            background = bg
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (icon.isNotEmpty()) {
            val iv = ImageView(this@WidgetEditorActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            loadBase64Image(iv, icon)
            header.addView(iv)
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = name
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
        })
        if (desc.isNotEmpty()) {
            titleCol.addView(TextView(this).apply {
                text = desc
                setTextColor(textColor)
                alpha = 0.75f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
        }
        header.addView(titleCol)
        card.addView(header)

        // Results
        if (results.isEmpty()) {
            card.addView(TextView(this).apply {
                text = "No functions defined"
                setTextColor(textColor)
                alpha = 0.6f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(6), 0, 0)
            })
        } else {
            for (r in results) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                val prefix = r.optString("prefix", "")
                if (prefix.isNotEmpty()) {
                    row.addView(TextView(this).apply {
                        text = "$prefix "
                        setTextColor(textColor)
                        alpha = 0.85f
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    })
                }
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
                val postfix = r.optString("postfix", "")
                val resultVal = r.optString("result", "0")
                row.addView(TextView(this).apply {
                    text = "$resultVal${if (postfix.isNotEmpty()) " $postfix" else ""}"
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(null, Typeface.BOLD)
                })
                card.addView(row)
            }
        }

        container.addView(card)

        // Match count info
        container.addView(TextView(this).apply {
            text = "${filtered.size} of ${allEntries.size} entries match filters"
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
            setPadding(0, dp(10), 0, 0)
        })

        AlertDialog.Builder(this)
            .setTitle("Widget Preview")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .show()
    }

    // ========== Computation Engine ==========

    private fun filterEntries(entries: List<JSONObject>, filters: JSONArray): List<JSONObject> {
        if (filters.length() == 0) return entries
        return entries.filter { entry ->
            var match = true
            for (i in 0 until filters.length()) {
                val f = filters.optJSONObject(i) ?: continue
                if (!matchesFilter(entry, f)) { match = false; break }
            }
            match
        }
    }

    private fun matchesFilter(entry: JSONObject, f: JSONObject): Boolean {
        val field = f.optString("field", "")
        val op = f.optString("op", "")
        val value = f.optString("value", "")
        val value2 = f.optString("value2", "")

        return when (getFieldType(field)) {
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
                val items = mutableListOf<String>()
                for (j in 0 until arr.length()) {
                    val item = arr.opt(j)
                    when (item) {
                        is JSONObject -> {
                            val fn = item.optString("firstName", "")
                            val ln = item.optString("lastName", "")
                            items.add("$fn $ln".trim().lowercase())
                        }
                        else -> items.add(item.toString().lowercase())
                    }
                }
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

    private fun computeFunctions(functions: JSONArray, filtered: List<JSONObject>): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        for (i in 0 until functions.length()) {
            val fn = functions.optJSONObject(i) ?: continue
            val result = computeFunction(fn, filtered)
            results.add(JSONObject().apply {
                put("prefix", fn.optString("prefix", ""))
                put("postfix", fn.optString("postfix", ""))
                put("result", result)
            })
        }
        return results
    }

    private fun computeFunction(fn: JSONObject, filtered: List<JSONObject>): String {
        val func = fn.optString("func", "Count")
        val field = fn.optString("field", "entries")
        val matchVal = fn.optString("value", "").lowercase()

        if (field == "entries") return filtered.size.toString()

        val aggType = when (field) {
            "tags", "categories" -> "array"
            else -> "text"
        }

        if (func == "Count") {
            if (aggType == "array") {
                if (matchVal.isNotEmpty()) {
                    return filtered.count { e ->
                        val arr = e.optJSONArray(field) ?: JSONArray()
                        var found = false
                        for (j in 0 until arr.length()) {
                            val item = arr.opt(j)
                            val s = when (item) {
                                is JSONObject -> "${item.optString("firstName", "")} ${item.optString("lastName", "")}".trim().lowercase()
                                else -> item.toString().lowercase()
                            }
                            if (s == matchVal) { found = true; break }
                        }
                        found
                    }.toString()
                }
                val set = mutableSetOf<String>()
                filtered.forEach { e ->
                    val arr = e.optJSONArray(field) ?: JSONArray()
                    for (j in 0 until arr.length()) {
                        val item = arr.opt(j)
                        val s = when (item) {
                            is JSONObject -> "${item.optString("firstName", "")} ${item.optString("lastName", "")}".trim()
                            else -> item.toString()
                        }
                        set.add(s)
                    }
                }
                return set.size.toString()
            }
            if (aggType == "text") {
                return if (matchVal.isNotEmpty()) {
                    filtered.count { (it.optString(field, "").lowercase()) == matchVal }.toString()
                } else {
                    filtered.count { it.optString(field, "").isNotEmpty() }.toString()
                }
            }
        }

        // Numeric aggregates
        val values = mutableListOf<Double>()
        filtered.forEach { e ->
            val raw = e.opt(field)
            if (raw is JSONArray) {
                for (j in 0 until raw.length()) {
                    val n = raw.optString(j, "").toDoubleOrNull()
                    if (n != null) values.add(n)
                }
            } else {
                val n = e.optString(field, "").toDoubleOrNull()
                if (n != null) values.add(n)
            }
        }
        if (values.isEmpty()) return "0"

        return when (func) {
            "Sum" -> values.sum().let { formatNum(it) }
            "Max" -> values.max().let { formatNum(it) }
            "Min" -> values.min().let { formatNum(it) }
            "Average" -> (values.sum() / values.size).let { "%.2f".format(it) }
            else -> values.size.toString()
        }
    }

    private fun formatNum(d: Double): String {
        return if (d == d.toLong().toDouble()) d.toLong().toString() else "%.2f".format(d)
    }

    // ========== Helpers ==========

    private fun getFieldType(field: String): String {
        return when (field) {
            "date" -> "date"
            "categories", "tags" -> "array"
            else -> "text"
        }
    }

    private fun opsForField(field: String): List<Pair<String, String>> {
        return when (field) {
            "date" -> dateOps
            "categories", "tags" -> arrayOps
            else -> textOps
        }
    }

    private fun getContrastColor(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.5) Color.parseColor("#1a1a2e") else Color.WHITE
    }

    private fun parseColorSafe(hex: String, fallback: Int): Int {
        return try {
            if (hex.isNotEmpty() && hex.startsWith("#")) Color.parseColor(hex) else fallback
        } catch (_: Exception) { fallback }
    }

    private fun showColorPickerDialog(currentHex: String, onColorSelected: (String) -> Unit) {
        val presetColors = listOf(
            "#e74c3c", "#e91e63", "#9b59b6", "#8e44ad",
            "#3498db", "#2196f3", "#1abc9c", "#009688",
            "#2ecc71", "#4caf50", "#8bc34a", "#cddc39",
            "#f1c40f", "#ffeb3b", "#ff9800", "#ff5722",
            "#795548", "#9e9e9e", "#607d8b", "#34495e",
            "#1a1a2e", "#16213e", "#0f3460", "#4a90d9",
        )

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }

        val previewSwatch = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)).apply {
                bottomMargin = dp(12)
            }
            val bg = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(parseColorSafe(currentHex, Color.parseColor("#4a90d9")))
            }
            background = bg
        }
        container.addView(previewSwatch)

        val hexInput = EditText(this).apply {
            setText(currentHex)
            hint = "#4a90d9"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
        }

        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val c = parseColorSafe(s.toString(), Color.parseColor("#4a90d9"))
                (previewSwatch.background as? GradientDrawable)?.setColor(c)
            }
        })
        container.addView(hexInput)

        val columns = 4
        val grid = GridLayout(this).apply {
            columnCount = columns
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        for (hex in presetColors) {
            val cell = View(this).apply {
                val size = dp(48)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(6).toFloat()
                    setColor(Color.parseColor(hex))
                }
                background = bg
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    hexInput.setText(hex)
                }
            }
            grid.addView(cell)
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(grid)
        container.addView(scroll)

        AlertDialog.Builder(this)
            .setTitle("Pick a Color")
            .setView(container)
            .setPositiveButton("OK") { _, _ ->
                val result = hexInput.text.toString().trim()
                if (result.isNotEmpty()) onColorSelected(result)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addSectionHeader(text: String) {
        contentContainer.addView(TextView(this).apply {
            this.text = text
            setTextColor(ThemeManager.color(C.ACCENT))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(4), dp(4), dp(8))
        })
    }

    private fun addLabel(text: String) {
        contentContainer.addView(TextView(this).apply {
            this.text = text
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
            setPadding(dp(4), dp(4), dp(4), dp(2))
        })
    }

    private fun addSpacer() {
        contentContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12))
        })
    }

    private fun makeInput(hint: String, text: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            setText(text)
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = linParams().apply { bottomMargin = dp(8) }
            inputType = InputType.TYPE_CLASS_TEXT
        }
    }

    private fun makeSpinner(items: List<String>, selectedIdx: Int): Spinner {
        val spinner = Spinner(this).apply {
            background = ContextCompat.getDrawable(this@WidgetEditorActivity, R.drawable.spinner_bg)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = linParams().apply { bottomMargin = dp(4) }
        }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 13f
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
        spinner.setSelection(selectedIdx.coerceIn(0, items.size - 1))
        return spinner
    }

    private fun spinnerListener(onChange: (Int) -> Unit): AdapterView.OnItemSelectedListener {
        return object : AdapterView.OnItemSelectedListener {
            private var first = true
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                onChange(pos)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadBase64Image(imageView: ImageView, dataUrl: String) {
        try {
            val base64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            if (base64.isEmpty()) return
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) imageView.setImageBitmap(bmp)
        } catch (_: Exception) {}
    }

    private fun removeJsonArrayItem(arr: JSONArray, index: Int) {
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            if (i != index) newArr.put(arr.get(i))
        }
        while (arr.length() > 0) arr.remove(0)
        for (i in 0 until newArr.length()) arr.put(newArr.get(i))
    }

    private fun generateId(): String {
        val ts = System.currentTimeMillis().toString(36)
        val rand = (Math.random() * 1e9).toLong().toString(36)
        return "$ts$rand"
    }

    private fun linParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
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

    override fun onResume() {
        super.onResume()
        if (lastThemeVersion != ThemeManager.themeVersion) {
            finish()
            return
        }
    }

}
