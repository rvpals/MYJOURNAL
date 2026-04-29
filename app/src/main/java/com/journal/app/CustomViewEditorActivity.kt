package com.journal.app

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CustomViewEditorActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic var databaseService: DatabaseService? = null
        @JvmStatic var bootstrapService: BootstrapService? = null
        @JvmStatic var pendingViewId: String? = null
    }

    private lateinit var db: DatabaseService
    private lateinit var bs: BootstrapService

    private lateinit var contentContainer: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var deleteBtn: Button

    private var view = JSONObject()
    private var isNew = true
    private var conditions = JSONArray()
    private var orderBy = JSONArray()
    private var activeTab = "info"

    private val viewFields = listOf(
        "date" to "Date", "time" to "Time", "title" to "Title",
        "content" to "Content", "categories" to "Categories",
        "tags" to "Tags", "placeName" to "Place Name", "weather" to "Weather"
    )

    private val textOps = listOf(
        "contains" to "contains", "not_contains" to "doesn't contain",
        "equals" to "equals", "not_equals" to "doesn't equal",
        "starts_with" to "starts with", "ends_with" to "ends with",
        "is_empty" to "is empty", "is_not_empty" to "is not empty"
    )
    private val dateOps = listOf(
        "equals" to "is", "not_equals" to "is not",
        "before" to "is before", "after" to "is after",
        "within_days" to "within last N days", "within_weeks" to "within last N weeks",
        "within_months" to "within last N months", "within_years" to "within last N years",
        "is_empty" to "is empty", "is_not_empty" to "is not empty"
    )
    private val arrayOps = listOf(
        "includes" to "includes", "not_includes" to "doesn't include",
        "is_empty" to "is empty", "is_not_empty" to "is not empty"
    )
    private val boolOps = listOf("exists" to "has data", "not_exists" to "has no data")

    private val sortFields = listOf(
        "date" to "Date", "time" to "Time", "title" to "Title",
        "content" to "Content", "categories" to "Categories",
        "tags" to "Tags", "placeName" to "Place Name",
        "dtCreated" to "Created", "dtUpdated" to "Updated"
    )

    private val groupOpts = listOf(
        "" to "None", "date" to "Date", "categories" to "Category",
        "tags" to "Tag", "placeName" to "Place Name", "weather" to "Weather"
    )

    private val displayOpts = listOf(
        "" to "Default", "card" to "Card", "list" to "List (table)"
    )

    // Info tab form refs
    private lateinit var nameInput: EditText
    private var logicPos = 0
    private var pinChecked = false
    private var defaultChecked = false

    // Display tab form refs
    private var groupByPos = 0
    private var displayModePos = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_view_editor)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        db = databaseService ?: run { finish(); return }
        bs = bootstrapService ?: run { finish(); return }
        databaseService = null
        bootstrapService = null

        contentContainer = findViewById(R.id.content_container)
        titleView = findViewById(R.id.editor_title)
        deleteBtn = findViewById(R.id.btn_delete)

        val viewId = pendingViewId
        pendingViewId = null

        if (viewId != null) {
            val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
            val views = settings.optJSONArray("customViews") ?: JSONArray()
            for (i in 0 until views.length()) {
                val v = views.optJSONObject(i)
                if (v != null && v.optString("id") == viewId) {
                    view = JSONObject(v.toString())
                    isNew = false
                    break
                }
            }
        }

        if (isNew) {
            view = JSONObject().apply {
                put("id", generateId())
                put("name", "")
                put("conditions", JSONArray())
                put("logic", "AND")
                put("orderBy", JSONArray())
                put("groupBy", "")
                put("displayMode", "")
                put("pinToDashboard", false)
                put("defaultEntryView", false)
            }
        }

        conditions = view.optJSONArray("conditions") ?: JSONArray()
        orderBy = view.optJSONArray("orderBy") ?: JSONArray()
        logicPos = if (view.optString("logic") == "OR") 1 else 0
        pinChecked = view.optBoolean("pinToDashboard", false)
        defaultChecked = view.optBoolean("defaultEntryView", false)
        groupByPos = groupOpts.indexOfFirst { it.first == view.optString("groupBy", "") }.coerceAtLeast(0)
        displayModePos = displayOpts.indexOfFirst { it.first == view.optString("displayMode", "") }.coerceAtLeast(0)

        titleView.text = if (isNew) "New View" else "Edit View"

        if (!isNew) {
            deleteBtn.visibility = View.VISIBLE
            deleteBtn.setOnClickListener { confirmDelete() }
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveView() }
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_preview).setOnClickListener { showPreview() }

        setupTabs()
        showTab("info")
    }

    // ========== Tabs ==========

    private fun setupTabs() {
        findViewById<Button>(R.id.tab_info).setOnClickListener { showTab("info") }
        findViewById<Button>(R.id.tab_conditions).setOnClickListener { showTab("conditions") }
        findViewById<Button>(R.id.tab_display).setOnClickListener { showTab("display") }
    }

    private fun showTab(tab: String) {
        syncFormToView()
        activeTab = tab

        val tabs = mapOf(
            "info" to R.id.tab_info,
            "conditions" to R.id.tab_conditions,
            "display" to R.id.tab_display
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
            "info" -> buildInfoTab()
            "conditions" -> buildConditionsTab()
            "display" -> buildDisplayTab()
        }
    }

    private fun syncFormToView() {
        if (activeTab == "info" && ::nameInput.isInitialized) {
            view.put("name", nameInput.text.toString().trim())
            view.put("logic", if (logicPos == 1) "OR" else "AND")
            view.put("pinToDashboard", pinChecked)
            view.put("defaultEntryView", defaultChecked)
        }
        if (activeTab == "display") {
            view.put("groupBy", groupOpts[groupByPos].first)
            view.put("displayMode", displayOpts[displayModePos].first)
        }
        view.put("conditions", conditions)
        view.put("orderBy", orderBy)
    }

    // ========== Info Tab ==========

    private fun buildInfoTab() {
        addSectionHeader("View Info")

        addLabel("Name")
        nameInput = makeInput("View name", view.optString("name"))
        contentContainer.addView(nameInput)

        addLabel("Condition Logic")
        val logicSpinner = makeSpinner(listOf("AND (all must match)", "OR (any can match)"), logicPos)
        logicSpinner.onItemSelectedListener = spinnerListener { pos -> logicPos = pos }
        contentContainer.addView(logicSpinner)

        addSpacer()

        val pinCb = CheckBox(this).apply {
            text = "Pin to Dashboard"
            isChecked = pinChecked
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
            layoutParams = linParams().apply { bottomMargin = dp(4) }
            setOnCheckedChangeListener { _, checked -> pinChecked = checked }
        }
        contentContainer.addView(pinCb)

        val defaultCb = CheckBox(this).apply {
            text = "Set as Default Entry View"
            isChecked = defaultChecked
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
            layoutParams = linParams().apply { bottomMargin = dp(4) }
            setOnCheckedChangeListener { _, checked -> defaultChecked = checked }
        }
        contentContainer.addView(defaultCb)

        // Summary
        addSpacer()
        contentContainer.addView(TextView(this).apply {
            text = "${conditions.length()} condition${if (conditions.length() != 1) "s" else ""}, ${orderBy.length()} sort field${if (orderBy.length() != 1) "s" else ""}"
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
        })
    }

    // ========== Conditions Tab ==========

    private fun buildConditionsTab() {
        addSectionHeader("Filter Conditions")
        contentContainer.addView(TextView(this).apply {
            text = "Logic: ${if (logicPos == 1) "OR" else "AND"} — tap Info tab to change."
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        })

        if (conditions.length() == 0) {
            contentContainer.addView(TextView(this).apply {
                text = "No conditions — add at least one."
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 13f
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
        } else {
            for (i in 0 until conditions.length()) {
                val c = conditions.optJSONObject(i) ?: continue
                contentContainer.addView(buildConditionRow(c, i))
            }
        }

        val addBtn = Button(this)
        addBtn.text = "+ Add Condition"
        addBtn.textSize = 13f
        addBtn.isAllCaps = false
        addBtn.setTextColor(ThemeManager.color(C.ACCENT))
        addBtn.setBackgroundColor(Color.TRANSPARENT)
        addBtn.layoutParams = linParams().apply { topMargin = dp(8) }
        addBtn.setOnClickListener {
            val c = JSONObject()
            c.put("field", "title")
            c.put("operator", "contains")
            c.put("value", "")
            c.put("negate", false)
            this.conditions.put(c)
            showTab("conditions")
        }
        contentContainer.addView(addBtn)
    }

    private fun buildConditionRow(cond: JSONObject, index: Int): LinearLayout {
        val field = cond.optString("field", "title")
        val op = cond.optString("operator", "contains")
        val value = cond.optString("value", "")
        val negate = cond.optBoolean("negate", false)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = ContextCompat.getDrawable(this@CustomViewEditorActivity, R.drawable.input_bg)
            layoutParams = linParams().apply { bottomMargin = dp(6) }
        }

        // Row 1: Field + NOT + Remove
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val fieldSpinner = makeSpinner(viewFields.map { it.second },
            viewFields.indexOfFirst { it.first == field }.coerceAtLeast(0))
        fieldSpinner.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        topRow.addView(fieldSpinner)

        val notCb = CheckBox(this).apply {
            text = "NOT"
            isChecked = negate
            setTextColor(ThemeManager.color(C.ERROR))
            textSize = 11f
            buttonTintList = ThemeManager.colorStateList(C.ERROR)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(4) }
            setOnCheckedChangeListener { _, checked -> cond.put("negate", checked) }
        }
        topRow.addView(notCb)

        val removeBtn = Button(this)
        removeBtn.text = "✕"
        removeBtn.textSize = 12f
        removeBtn.setTextColor(ThemeManager.color(C.ERROR))
        removeBtn.setBackgroundColor(Color.TRANSPARENT)
        removeBtn.layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        removeBtn.setOnClickListener {
            removeJsonArrayItem(this.conditions, index)
            showTab("conditions")
        }
        topRow.addView(removeBtn)
        card.addView(topRow)

        // Row 2: Operator
        val ops = opsForField(field)
        val opSpinner = makeSpinner(ops.map { it.second },
            ops.indexOfFirst { it.first == op }.coerceAtLeast(0))
        opSpinner.layoutParams = linParams().apply { topMargin = dp(4) }
        card.addView(opSpinner)

        // Row 3: Value input
        val needsValue = !op.contains("empty") && !op.contains("exists")
        if (needsValue) {
            val isWithin = op.startsWith("within_")
            val valInput = EditText(this).apply {
                setText(value)
                hint = if (isWithin) "N" else if (getFieldType(field) == "date") "YYYY-MM-DD" else "Value"
                textSize = 13f
                setTextColor(ThemeManager.color(C.TEXT))
                setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                background = ContextCompat.getDrawable(this@CustomViewEditorActivity, R.drawable.input_bg)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = linParams().apply { topMargin = dp(4) }
                isSingleLine = true
                inputType = if (isWithin) InputType.TYPE_CLASS_NUMBER
                    else if (getFieldType(field) == "date") InputType.TYPE_CLASS_DATETIME
                    else InputType.TYPE_CLASS_TEXT
            }
            valInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) cond.put("value", valInput.text.toString())
            }
            card.addView(valInput)
        }

        // Spinner listeners
        fieldSpinner.onItemSelectedListener = spinnerListener { pos ->
            cond.put("field", viewFields[pos].first)
            val newOps = opsForField(viewFields[pos].first)
            cond.put("operator", newOps[0].first)
            cond.put("value", "")
            showTab("conditions")
        }
        opSpinner.onItemSelectedListener = spinnerListener { pos ->
            val currentOps = opsForField(cond.optString("field", "title"))
            if (pos < currentOps.size) {
                cond.put("operator", currentOps[pos].first)
                showTab("conditions")
            }
        }

        return card
    }

    // ========== Display Tab ==========

    private fun buildDisplayTab() {
        addSectionHeader("Group By")
        val groupSpinner = makeSpinner(groupOpts.map { it.second }, groupByPos)
        groupSpinner.onItemSelectedListener = spinnerListener { pos -> groupByPos = pos }
        contentContainer.addView(groupSpinner)

        addSectionHeader("Sort Order")
        if (orderBy.length() == 0) {
            contentContainer.addView(TextView(this).apply {
                text = "No sort fields — entries will use default order."
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 13f
                setPadding(dp(4), dp(4), dp(4), dp(8))
            })
        } else {
            for (i in 0 until orderBy.length()) {
                val ord = orderBy.optJSONObject(i) ?: continue
                contentContainer.addView(buildOrderRow(ord, i))
            }
        }

        val addSortBtn = Button(this)
        addSortBtn.text = "+ Add Sort Field"
        addSortBtn.textSize = 13f
        addSortBtn.isAllCaps = false
        addSortBtn.setTextColor(ThemeManager.color(C.ACCENT))
        addSortBtn.setBackgroundColor(Color.TRANSPARENT)
        addSortBtn.layoutParams = linParams().apply { topMargin = dp(4); bottomMargin = dp(8) }
        addSortBtn.setOnClickListener {
            val o = JSONObject()
            o.put("field", "date")
            o.put("direction", "desc")
            this.orderBy.put(o)
            showTab("display")
        }
        contentContainer.addView(addSortBtn)

        addSectionHeader("Display Mode")
        val displaySpinner = makeSpinner(displayOpts.map { it.second }, displayModePos)
        displaySpinner.onItemSelectedListener = spinnerListener { pos -> displayModePos = pos }
        contentContainer.addView(displaySpinner)
    }

    private fun buildOrderRow(ord: JSONObject, index: Int): LinearLayout {
        val field = ord.optString("field", "date")
        val dir = ord.optString("direction", "desc")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = linParams().apply { bottomMargin = dp(4) }
        }

        val fieldSpinner = makeSpinner(sortFields.map { it.second },
            sortFields.indexOfFirst { it.first == field }.coerceAtLeast(0))
        fieldSpinner.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        fieldSpinner.onItemSelectedListener = spinnerListener { pos ->
            ord.put("field", sortFields[pos].first)
        }
        row.addView(fieldSpinner)

        val dirOpts = listOf("desc" to "↓ Desc", "asc" to "↑ Asc")
        val dirSpinner = makeSpinner(dirOpts.map { it.second }, if (dir == "asc") 1 else 0)
        dirSpinner.layoutParams = LinearLayout.LayoutParams(dp(80), dp(36)).apply { marginEnd = dp(4) }
        dirSpinner.onItemSelectedListener = spinnerListener { pos ->
            ord.put("direction", dirOpts[pos].first)
        }
        row.addView(dirSpinner)

        val removeBtn = Button(this)
        removeBtn.text = "✕"
        removeBtn.textSize = 12f
        removeBtn.setTextColor(ThemeManager.color(C.ERROR))
        removeBtn.setBackgroundColor(Color.TRANSPARENT)
        removeBtn.layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        removeBtn.setOnClickListener {
            removeJsonArrayItem(this.orderBy, index)
            showTab("display")
        }
        row.addView(removeBtn)

        return row
    }

    // ========== Save / Delete ==========

    private fun saveView() {
        syncFormToView()

        val name = view.optString("name", "").trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a view name.", Toast.LENGTH_SHORT).show()
            showTab("info")
            nameInput.requestFocus()
            return
        }

        view.put("name", name)

        if (pinChecked) {
            val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
            val allViews = settings.optJSONArray("customViews") ?: JSONArray()
            var pinnedCount = 0
            for (i in 0 until allViews.length()) {
                val v = allViews.optJSONObject(i) ?: continue
                if (v.optBoolean("pinToDashboard") && v.optString("id") != view.optString("id")) {
                    pinnedCount++
                }
            }
            if (pinnedCount >= 10) {
                Toast.makeText(this, "Max 10 pinned views. Unpin another first.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (defaultChecked) {
            val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
            val allViews = settings.optJSONArray("customViews") ?: JSONArray()
            for (i in 0 until allViews.length()) {
                val v = allViews.optJSONObject(i) ?: continue
                if (v.optBoolean("defaultEntryView") && v.optString("id") != view.optString("id")) {
                    v.put("defaultEntryView", false)
                }
            }
            db.setSettings(JSONObject().apply { put("customViews", allViews) }.toString())
        }

        val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
        val allViews = settings.optJSONArray("customViews") ?: JSONArray()

        if (isNew) {
            allViews.put(view)
        } else {
            for (i in 0 until allViews.length()) {
                if (allViews.optJSONObject(i)?.optString("id") == view.optString("id")) {
                    allViews.put(i, view)
                    break
                }
            }
        }
        db.setSettings(JSONObject().apply { put("customViews", allViews) }.toString())
        finish()
    }

    private fun confirmDelete() {
        val name = view.optString("name", "Untitled")
        AlertDialog.Builder(this)
            .setMessage("Delete view \"$name\"?")
            .setPositiveButton("Delete") { _, _ ->
                val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
                val allViews = settings.optJSONArray("customViews") ?: JSONArray()
                val updated = JSONArray()
                for (i in 0 until allViews.length()) {
                    val v = allViews.optJSONObject(i)
                    if (v != null && v.optString("id") != view.optString("id")) updated.put(v)
                }
                db.setSettings(JSONObject().apply { put("customViews", updated) }.toString())
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== Preview ==========

    private fun showPreview() {
        syncFormToView()

        val entries = try { JSONArray(db.getEntries()) } catch (_: Exception) { JSONArray() }
        val allEntries = mutableListOf<JSONObject>()
        for (i in 0 until entries.length()) {
            entries.optJSONObject(i)?.let { allEntries.add(it) }
        }

        val logic = view.optString("logic", "AND")
        val filtered = filterByConditions(allEntries, conditions, logic)

        // Apply sort
        val sorted = applySortOrder(filtered, orderBy)

        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        scroll.addView(container)

        container.addView(TextView(this).apply {
            text = "${sorted.size} of ${allEntries.size} entries match"
            setTextColor(ThemeManager.color(C.ACCENT))
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        })

        if (sorted.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No entries match these conditions."
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 13f
            })
        } else {
            val limit = sorted.size.coerceAtMost(20)
            for (i in 0 until limit) {
                val e = sorted[i]
                container.addView(buildPreviewRow(e, i + 1))
            }
            if (sorted.size > 20) {
                container.addView(TextView(this).apply {
                    text = "... and ${sorted.size - 20} more entries"
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 12f
                    setPadding(0, dp(8), 0, 0)
                })
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Preview")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun buildPreviewRow(entry: JSONObject, num: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = ContextCompat.getDrawable(this@CustomViewEditorActivity, R.drawable.entry_row_bg)
            layoutParams = linParams().apply { bottomMargin = dp(4) }
        }

        row.addView(TextView(this).apply {
            text = "$num"
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = entry.optString("title", "").ifEmpty { "Untitled" }
        textCol.addView(TextView(this).apply {
            text = title
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
        })
        val date = entry.optString("date", "")
        if (date.isNotEmpty()) {
            textCol.addView(TextView(this).apply {
                text = date
                setTextColor(ThemeManager.color(C.ACCENT))
                textSize = 11f
            })
        }
        row.addView(textCol)

        return row
    }

    // ========== Condition Evaluation ==========

    private fun filterByConditions(entries: List<JSONObject>, conditions: JSONArray, logic: String): List<JSONObject> {
        if (conditions.length() == 0) return entries
        return entries.filter { entry ->
            if (logic == "OR") {
                var any = false
                for (i in 0 until conditions.length()) {
                    val c = conditions.optJSONObject(i) ?: continue
                    if (evaluateCondition(entry, c)) { any = true; break }
                }
                any
            } else {
                var all = true
                for (i in 0 until conditions.length()) {
                    val c = conditions.optJSONObject(i) ?: continue
                    if (!evaluateCondition(entry, c)) { all = false; break }
                }
                all
            }
        }
    }

    private fun evaluateCondition(entry: JSONObject, cond: JSONObject): Boolean {
        val field = cond.optString("field", "")
        val op = cond.optString("operator", "")
        val value = cond.optString("value", "")
        val negate = cond.optBoolean("negate", false)

        val result = evaluateInner(entry, field, op, value)
        return if (negate) !result else result
    }

    private fun evaluateInner(entry: JSONObject, field: String, op: String, value: String): Boolean {
        return when (getFieldType(field)) {
            "date" -> {
                val d = entry.optString("date", "")
                when (op) {
                    "equals" -> d == value
                    "not_equals" -> d != value
                    "before" -> d < value
                    "after" -> d > value
                    "is_empty" -> d.isEmpty()
                    "is_not_empty" -> d.isNotEmpty()
                    "within_days", "within_weeks", "within_months", "within_years" -> {
                        val n = value.toIntOrNull() ?: 1
                        val cal = Calendar.getInstance()
                        when (op) {
                            "within_days" -> cal.add(Calendar.DAY_OF_MONTH, -n)
                            "within_weeks" -> cal.add(Calendar.DAY_OF_MONTH, -(n * 7))
                            "within_months" -> cal.add(Calendar.MONTH, -n)
                            "within_years" -> cal.add(Calendar.YEAR, -n)
                        }
                        val pastStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                        d >= pastStr
                    }
                    else -> true
                }
            }
            "array" -> {
                val arr = entry.optJSONArray(field) ?: JSONArray()
                val items = mutableListOf<String>()
                for (j in 0 until arr.length()) items.add(arr.optString(j, ""))
                when (op) {
                    "includes" -> items.contains(value)
                    "not_includes" -> !items.contains(value)
                    "is_empty" -> items.isEmpty()
                    "is_not_empty" -> items.isNotEmpty()
                    else -> true
                }
            }
            "boolean" -> {
                val has = if (field == "weather") {
                    val w = entry.opt("weather")
                    w is JSONObject && w.has("temp")
                } else {
                    val v = entry.opt(field)
                    v != null && v.toString().isNotEmpty()
                }
                when (op) {
                    "exists" -> has
                    "not_exists" -> !has
                    else -> true
                }
            }
            else -> {
                val s = entry.optString(field, "").lowercase()
                val v = value.lowercase()
                when (op) {
                    "contains" -> s.contains(v)
                    "not_contains" -> !s.contains(v)
                    "equals" -> s == v
                    "not_equals" -> s != v
                    "starts_with" -> s.startsWith(v)
                    "ends_with" -> s.endsWith(v)
                    "is_empty" -> s.isEmpty()
                    "is_not_empty" -> s.isNotEmpty()
                    else -> true
                }
            }
        }
    }

    private fun applySortOrder(entries: List<JSONObject>, orderBy: JSONArray): List<JSONObject> {
        if (orderBy.length() == 0) return entries
        return entries.sortedWith(Comparator { a, b ->
            for (i in 0 until orderBy.length()) {
                val ord = orderBy.optJSONObject(i) ?: continue
                val field = ord.optString("field", "date")
                val dir = ord.optString("direction", "asc")
                val va = getEntryFieldValue(a, field)
                val vb = getEntryFieldValue(b, field)
                val cmp = va.compareTo(vb, ignoreCase = true)
                if (cmp != 0) return@Comparator if (dir == "desc") -cmp else cmp
            }
            0
        })
    }

    private fun getEntryFieldValue(entry: JSONObject, field: String): String {
        return when (field) {
            "categories", "tags" -> {
                val arr = entry.optJSONArray(field) ?: JSONArray()
                val items = mutableListOf<String>()
                for (i in 0 until arr.length()) items.add(arr.optString(i, ""))
                items.joinToString(", ")
            }
            else -> entry.optString(field, "")
        }
    }

    // ========== Helpers ==========

    private fun getFieldType(field: String): String {
        return when (field) {
            "date" -> "date"
            "categories", "tags" -> "array"
            "weather" -> "boolean"
            else -> "text"
        }
    }

    private fun opsForField(field: String): List<Pair<String, String>> {
        return when (field) {
            "date" -> dateOps
            "categories", "tags" -> arrayOps
            "weather" -> boolOps
            else -> textOps
        }
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
            background = ContextCompat.getDrawable(this@CustomViewEditorActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = linParams().apply { bottomMargin = dp(8) }
            inputType = InputType.TYPE_CLASS_TEXT
        }
    }

    private fun makeSpinner(items: List<String>, selectedIdx: Int): Spinner {
        val spinner = Spinner(this).apply {
            background = ContextCompat.getDrawable(this@CustomViewEditorActivity, R.drawable.spinner_bg)
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
