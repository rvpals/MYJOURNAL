package com.journal.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journal.app.ThemeManager.C
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class EntryListActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic var databaseService: DatabaseService? = null
        @JvmStatic var bootstrapService: BootstrapService? = null
        @JvmStatic var pendingWidgetFilter: String? = null
        @JvmStatic var pendingWidgetName: String? = null
    }

    private lateinit var db: DatabaseService
    private lateinit var bs: BootstrapService

    private lateinit var entriesContainer: LinearLayout
    private lateinit var paginationBar: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var entryCountView: TextView
    private lateinit var searchInput: EditText
    private lateinit var batchBar: LinearLayout
    private lateinit var selectCountView: TextView
    private lateinit var selectModeBtn: Button
    private lateinit var filterBody: LinearLayout
    private lateinit var filterToggle: TextView

    private var filterExpanded = false
    private var allEntries = JSONArray()
    private var filteredEntries = mutableListOf<JSONObject>()
    private var currentPage = 1
    private var pageSize = 20
    private var selectMode = false
    private val selectedIds = mutableSetOf<String>()

    private var filterCategory = ""
    private var filterTag = ""
    private var searchQuery = ""

    private var sortField = "dtCreated"
    private var sortDir = "desc"

    private var showFields = mutableMapOf<String, Boolean>()
    private var widgetFilters: JSONArray? = null
    private var widgetName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry_list)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        db = databaseService ?: run { finish(); return }
        bs = bootstrapService ?: run { finish(); return }
        databaseService = null
        bootstrapService = null

        widgetFilters = pendingWidgetFilter?.let {
            pendingWidgetFilter = null
            try { JSONArray(it) } catch (_: Exception) { null }
        }
        widgetName = pendingWidgetName
        pendingWidgetName = null
        Log.d("EntryListFilter", "onCreate widgetFilters=$widgetFilters widgetName=$widgetName")

        entriesContainer = findViewById(R.id.el_entries_container)
        paginationBar = findViewById(R.id.el_pagination)
        titleView = findViewById(R.id.el_title)
        entryCountView = findViewById(R.id.el_entry_count)
        searchInput = findViewById(R.id.el_search)
        batchBar = findViewById(R.id.el_batch_bar)
        selectCountView = findViewById(R.id.el_select_count)
        selectModeBtn = findViewById(R.id.btn_select_mode)
        filterBody = findViewById(R.id.el_filter_body)
        filterToggle = findViewById(R.id.el_filter_toggle)

        findViewById<LinearLayout>(R.id.el_filter_header).setOnClickListener { toggleFilterBox() }
        filterToggle.setOnClickListener { toggleFilterBox() }

        sortField = bs.get("default_entry_sort_field") ?: "dtCreated"
        sortDir = bs.get("default_entry_sort_dir") ?: "desc"
        pageSize = bs.get("entries_page_size")?.toIntOrNull() ?: 20

        loadFieldSettings()
        setupControls()
        loadEntries()
        applyFilters()
    }

    private fun toggleFilterBox() {
        filterExpanded = !filterExpanded
        filterBody.visibility = if (filterExpanded) View.VISIBLE else View.GONE
        filterToggle.text = if (filterExpanded) "▼ Filter Info" else "▶ Filter Info"
    }

    private fun loadFieldSettings() {
        val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
        val fields = settings.optJSONObject("entryListFields") ?: JSONObject()
        val defaults = mapOf(
            "date" to true, "time" to true, "title" to true,
            "content" to true, "categories" to true, "tags" to true,
            "places" to false, "weather" to true, "images" to true
        )
        for ((key, def) in defaults) {
            showFields[key] = if (fields.has(key)) fields.optBoolean(key, def) else def
        }
    }

    private fun show(key: String): Boolean = showFields[key] != false

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControls() {
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_new_entry).setOnClickListener {
            EntryFormActivity.databaseService = databaseService
            EntryFormActivity.bootstrapService = bootstrapService
            EntryFormActivity.weatherService = ServiceProvider.weatherService
            startActivity(Intent(this, EntryFormActivity::class.java))
        }

        // Search
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                currentPage = 1
                applyFilters()
            }
        })
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchQuery = searchInput.text.toString().trim()
                currentPage = 1
                applyFilters()
                true
            } else false
        }

        findViewById<Button>(R.id.btn_search).setOnClickListener {
            searchQuery = searchInput.text.toString().trim()
            currentPage = 1
            applyFilters()
        }

        // Filters
        setupCategoryFilter()
        setupTagFilter()

        findViewById<Button>(R.id.btn_clear_filters).setOnClickListener {
            searchInput.text.clear()
            searchQuery = ""
            filterCategory = ""
            filterTag = ""
            widgetFilters = null
            widgetName = null
            sortField = "dtCreated"
            sortDir = "desc"
            bs.set("default_entry_sort_field", sortField)
            bs.set("default_entry_sort_dir", sortDir)
            setupCategoryFilter()
            setupTagFilter()
            setupOrderBySpinners()
            currentPage = 1
            applyFilters()
        }

        // Order by
        setupOrderBySpinners()

        // Select mode
        selectModeBtn.setOnClickListener { toggleSelectMode() }
        findViewById<Button>(R.id.btn_select_all).setOnClickListener { selectAll() }
        findViewById<Button>(R.id.btn_deselect_all).setOnClickListener { deselectAll() }
        findViewById<Button>(R.id.btn_delete_selected).setOnClickListener { deleteSelected() }

        // Page size
        setupPageSizeSpinner()
    }

    private fun setupCategoryFilter() {
        val spinner = findViewById<Spinner>(R.id.el_filter_category)
        val cats = mutableListOf("All Categories")
        try {
            val arr = JSONArray(db.getCategories())
            for (i in 0 until arr.length()) cats.add(arr.getString(i))
        } catch (_: Exception) {}

        val adapter = makeFilterAdapter(cats)
        spinner.adapter = adapter
        spinner.setSelection(if (filterCategory.isNotEmpty()) cats.indexOf(filterCategory).coerceAtLeast(0) else 0)

        var init = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (init) { init = false; return }
                filterCategory = if (pos == 0) "" else cats[pos]
                currentPage = 1
                applyFilters()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupTagFilter() {
        val spinner = findViewById<Spinner>(R.id.el_filter_tag)
        val tags = mutableListOf("All Tags")
        try {
            val arr = JSONArray(db.getAllTags())
            for (i in 0 until arr.length()) tags.add(arr.getString(i))
        } catch (_: Exception) {}

        val adapter = makeFilterAdapter(tags)
        spinner.adapter = adapter
        spinner.setSelection(if (filterTag.isNotEmpty()) tags.indexOf(filterTag).coerceAtLeast(0) else 0)

        var init = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (init) { init = false; return }
                filterTag = if (pos == 0) "" else tags[pos]
                currentPage = 1
                applyFilters()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupPageSizeSpinner() {
        val spinner = findViewById<Spinner>(R.id.el_page_size)
        val sizes = listOf("10", "20", "50", "100", "All")
        val sizeVals = listOf(10, 20, 50, 100, 0)
        val adapter = makeFilterAdapter(sizes)
        spinner.adapter = adapter
        val idx = sizeVals.indexOf(pageSize).let { if (it < 0) 1 else it }
        spinner.setSelection(idx)

        var init = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (init) { init = false; return }
                pageSize = sizeVals[pos]
                bs.set("entries_page_size", pageSize.toString())
                currentPage = 1
                renderPage()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupOrderBySpinners() {
        val fieldSpinner = findViewById<Spinner>(R.id.el_order_field)
        val dirSpinner = findViewById<Spinner>(R.id.el_order_dir)

        val fieldLabels = listOf("Date", "Time", "Title", "Created", "Updated", "Categories", "Tags")
        val fieldValues = listOf("date", "time", "title", "dtCreated", "dtUpdated", "categories", "tags")
        val dirLabels = listOf("Desc ↓", "Asc ↑")
        val dirValues = listOf("desc", "asc")

        fieldSpinner.adapter = makeFilterAdapter(fieldLabels)
        dirSpinner.adapter = makeFilterAdapter(dirLabels)

        val fieldIdx = fieldValues.indexOf(sortField).let { if (it < 0) 3 else it }
        fieldSpinner.setSelection(fieldIdx)
        dirSpinner.setSelection(if (sortDir == "asc") 1 else 0)

        var fieldInit = true
        fieldSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (fieldInit) { fieldInit = false; return }
                sortField = fieldValues[pos]
                bs.set("default_entry_sort_field", sortField)
                currentPage = 1
                applyFilters()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        var dirInit = true
        dirSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (dirInit) { dirInit = false; return }
                sortDir = dirValues[pos]
                bs.set("default_entry_sort_dir", sortDir)
                currentPage = 1
                applyFilters()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun makeFilterAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 12f
                    setPadding(dp(6), dp(4), dp(6), dp(4))
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
        }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    // ========== Data Loading ==========

    private fun loadEntries() {
        val json = db.getEntries()
        allEntries = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
    }

    private fun applyFilters() {
        filteredEntries.clear()
        val query = searchQuery.lowercase()

        for (i in 0 until allEntries.length()) {
            val e = allEntries.optJSONObject(i) ?: continue

            // Widget filter
            if (widgetFilters != null && !matchesWidgetFilters(e, widgetFilters!!)) continue

            // Search filter
            if (query.isNotEmpty()) {
                val title = e.optString("title", "").lowercase()
                val content = e.optString("content", "").lowercase()
                val place = e.optString("placeName", "").lowercase()
                val tags = jsonArrayToStr(e.optJSONArray("tags")).lowercase()
                val cats = jsonArrayToStr(e.optJSONArray("categories")).lowercase()
                val people = peopleToStr(e.optJSONArray("people")).lowercase()
                if (!title.contains(query) && !content.contains(query) &&
                    !place.contains(query) && !tags.contains(query) &&
                    !cats.contains(query) && !people.contains(query)) continue
            }

            // Category filter
            if (filterCategory.isNotEmpty()) {
                val cats = e.optJSONArray("categories") ?: JSONArray()
                var found = false
                for (j in 0 until cats.length()) {
                    if (cats.optString(j) == filterCategory) { found = true; break }
                }
                if (!found) continue
            }

            // Tag filter
            if (filterTag.isNotEmpty()) {
                val tags = e.optJSONArray("tags") ?: JSONArray()
                var found = false
                for (j in 0 until tags.length()) {
                    if (tags.optString(j) == filterTag) { found = true; break }
                }
                if (!found) continue
            }

            filteredEntries.add(e)
        }

        // Sort
        filteredEntries.sortWith(Comparator { a, b ->
            val va = getFieldValue(a, sortField)
            val vb = getFieldValue(b, sortField)
            val cmp = va.compareTo(vb, ignoreCase = true)
            if (sortDir == "asc") cmp else -cmp
        })

        renderPage()
    }

    private fun matchesWidgetFilters(entry: JSONObject, filters: JSONArray): Boolean {
        Log.d("EntryListFilter", "Checking entry: ${entry.optString("title")} against filters: $filters")
        for (i in 0 until filters.length()) {
            val f = filters.optJSONObject(i) ?: continue
            val field = f.optString("field", "")
            val op = f.optString("op", "")
            val value = f.optString("value", "")
            val value2 = f.optString("value2", "")
            val fieldType = when (field) {
                "date" -> "date"
                "categories", "tags", "people" -> "array"
                else -> "text"
            }

            when (fieldType) {
                "date" -> {
                    val d = entry.optString(field, "")
                    val match = when (op) {
                        "after" -> d > value
                        "before" -> d < value
                        "equals" -> d == value
                        "between" -> d >= value && d <= value2.ifEmpty { value }
                        else -> true
                    }
                    if (!match) return false
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
                    Log.d("EntryListFilter", "  array field=$field items=$items op=$op value=$lower")
                    val match = when (op) {
                        "includes" -> items.any { it == lower }
                        "not includes" -> items.none { it == lower }
                        "is empty" -> items.isEmpty()
                        "is not empty" -> items.isNotEmpty()
                        else -> true
                    }
                    if (!match) return false
                }
                else -> {
                    val s = entry.optString(field, "").lowercase()
                    val v = value.lowercase()
                    val match = when (op) {
                        "contains" -> s.contains(v)
                        "equals" -> s == v
                        "starts with" -> s.startsWith(v)
                        "ends with" -> s.endsWith(v)
                        "is empty" -> s.isEmpty()
                        "is not empty" -> s.isNotEmpty()
                        else -> true
                    }
                    if (!match) return false
                }
            }
        }
        return true
    }

    private fun getFieldValue(entry: JSONObject, field: String): String {
        return when (field) {
            "categories" -> jsonArrayToStr(entry.optJSONArray("categories"))
            "tags" -> jsonArrayToStr(entry.optJSONArray("tags"))
            else -> entry.optString(field, "")
        }
    }

    // ========== Rendering ==========

    private fun renderPage() {
        val total = filteredEntries.size
        val wn = widgetName
        titleView.text = if (wn != null) "$total Entries — $wn" else "$total Entries"
        entryCountView.text = if (total == allEntries.length()) "$total entries" else "$total of ${allEntries.length()}"

        entriesContainer.removeAllViews()

        val start: Int
        val end: Int
        if (pageSize <= 0 || pageSize >= total) {
            start = 0
            end = total
            paginationBar.visibility = View.GONE
        } else {
            val totalPages = (total + pageSize - 1) / pageSize
            currentPage = currentPage.coerceIn(1, totalPages.coerceAtLeast(1))
            start = (currentPage - 1) * pageSize
            end = (start + pageSize).coerceAtMost(total)
            renderPagination(totalPages, total)
            paginationBar.visibility = View.VISIBLE
        }

        if (total == 0) {
            entriesContainer.addView(TextView(this).apply {
                text = "No entries found"
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(40), dp(16), dp(40))
            })
            return
        }

        for (i in start until end) {
            val entry = filteredEntries[i]
            val displayIdx = i - start
            entriesContainer.addView(buildEntryCard(entry, i + 1, displayIdx))
        }
    }

    private fun buildEntryCard(entry: JSONObject, rowNum: Int, displayIdx: Int): View {
        val id = entry.optString("id", "")
        val date = entry.optString("date", "")
        val time = entry.optString("time", "")
        val title = entry.optString("title", "")
        val content = entry.optString("content", "")
        val placeName = entry.optString("placeName", "")
        val categories = entry.optJSONArray("categories") ?: JSONArray()
        val tags = entry.optJSONArray("tags") ?: JSONArray()
        val images = entry.optJSONArray("images") ?: JSONArray()
        val weather = entry.opt("weather")
        val pinned = entry.optBoolean("pinned", false)
        val locked = entry.optBoolean("locked", false)

        val isEvenRow = displayIdx % 2 == 0
        val rowBgColor = if (isEvenRow) ThemeManager.color(C.CARD_BG) else ThemeManager.color(C.INPUT_BG)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(rowBgColor)
                setStroke(dp(1), ThemeManager.color(C.CARD_BORDER))
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
            isClickable = true
            isFocusable = true
        }

        if (selectMode && selectedIds.contains(id)) {
            card.setBackgroundColor(Color.parseColor("#2a4a5e"))
        }

        card.setOnClickListener {
            if (selectMode) {
                toggleSelection(id)
                if (selectedIds.contains(id)) {
                    card.setBackgroundColor(Color.parseColor("#2a4a5e"))
                } else {
                    card.background = ContextCompat.getDrawable(this, R.drawable.entry_row_bg)
                }
                updateSelectCount()
            } else {
                openEntryViewer(id)
            }
        }

        // Header: row number + date/time + badges
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
        }

        // Row number
        headerRow.addView(TextView(this).apply {
            text = "$rowNum"
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(dp(24), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        // Date + time
        val dateTime = mutableListOf<String>()
        if (show("date") && date.isNotEmpty()) dateTime.add(formatDate(date))
        if (show("time") && time.isNotEmpty()) dateTime.add(formatTime(time))
        if (dateTime.isNotEmpty()) {
            headerRow.addView(TextView(this).apply {
                text = dateTime.joinToString("  ")
                setTextColor(ThemeManager.color(C.ACCENT))
                textSize = 11f
            })
        }

        // Badges
        val badges = mutableListOf<String>()
        if (pinned) badges.add("📌")
        if (locked) badges.add("🔒")
        if (badges.isNotEmpty()) {
            headerRow.addView(TextView(this).apply {
                text = "  ${badges.joinToString(" ")}"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.END
            })
        } else {
            headerRow.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            })
        }

        // Delete button
        if (!selectMode) {
            headerRow.addView(Button(this).apply {
                text = "✕"
                textSize = 11f
                setTextColor(ThemeManager.color(C.ERROR))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                setOnClickListener { confirmDeleteEntry(id, title) }
            })
        }

        card.addView(headerRow)

        // Title
        if (show("title") && title.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = title
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(2) }
            })
        }

        // Content preview
        if (show("content") && content.isNotEmpty()) {
            val preview = if (content.length > 120) content.substring(0, 120) + "…" else content
            card.addView(TextView(this).apply {
                text = preview
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 12f
                maxLines = 3
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            })
        }

        // Place
        if (show("places") && placeName.isNotEmpty()) {
            card.addView(TextView(this).apply {
                text = "📍 $placeName"
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(2) }
            })
        }

        // Weather
        if (show("weather") && weather is JSONObject) {
            val weatherText = formatWeather(weather)
            if (weatherText.isNotEmpty()) {
                card.addView(TextView(this).apply {
                    text = "🌤️ $weatherText"
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(2) }
                })
            }
        }

        // Image thumbnails
        if (show("images") && images.length() > 0) {
            val imgRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }
            val maxThumbs = 4.coerceAtMost(images.length())
            for (j in 0 until maxThumbs) {
                val img = images.optJSONObject(j) ?: continue
                val thumbData = img.optString("thumb", "")
                if (thumbData.isEmpty()) continue
                val iv = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(4) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = ContextCompat.getDrawable(this@EntryListActivity, R.drawable.input_bg)
                }
                loadBase64Image(iv, thumbData)
                imgRow.addView(iv)
            }
            if (images.length() > 4) {
                imgRow.addView(TextView(this).apply {
                    text = "+${images.length() - 4}"
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 11f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(40))
                })
            }
            card.addView(imgRow)
        }

        // Categories + Tags
        val tagsFlow = FlowLayout(this)

        if (show("categories")) {
            for (j in 0 until categories.length()) {
                val cat = categories.optString(j, "")
                if (cat.isEmpty()) continue
                tagsFlow.addView(makeTagChip("📁 $cat", "#1cb3c8"))
            }
        }
        if (show("tags")) {
            for (j in 0 until tags.length()) {
                val tag = tags.optString(j, "")
                if (tag.isEmpty()) continue
                tagsFlow.addView(makeTagChip("🏷 $tag", "#a0a4b8"))
            }
        }

        if (tagsFlow.childCount > 0) {
            card.addView(tagsFlow)
        }

        return card
    }

    private fun makeTagChip(text: String, colorHex: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 10f
            try { setTextColor(Color.parseColor(colorHex)) } catch (_: Exception) {
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            }
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = ContextCompat.getDrawable(this@EntryListActivity, R.drawable.input_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(4)
                bottomMargin = dp(2)
            }
        }
    }

    // ========== Pagination ==========

    private fun renderPagination(totalPages: Int, totalEntries: Int) {
        paginationBar.removeAllViews()

        val start = (currentPage - 1) * pageSize + 1
        val end = (currentPage * pageSize).coerceAtMost(totalEntries)

        // Prev
        paginationBar.addView(makePagBtn("◀", currentPage > 1) {
            currentPage--; renderPage()
        })

        // Page numbers
        val range = calcPageRange(currentPage, totalPages)
        for (p in range) {
            if (p == -1) {
                paginationBar.addView(TextView(this).apply {
                    text = "…"
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(dp(4), 0, dp(4), 0)
                })
            } else {
                val isCurrent = p == currentPage
                paginationBar.addView(Button(this).apply {
                    text = "$p"
                    textSize = 12f
                    isAllCaps = false
                    if (isCurrent) {
                        setTextColor(ThemeManager.color(C.CARD_BG))
                        setBackgroundColor(ThemeManager.color(C.ACCENT))
                    } else {
                        setTextColor(ThemeManager.color(C.TEXT))
                        setBackgroundColor(Color.TRANSPARENT)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(2) }
                    setPadding(0, 0, 0, 0)
                    val page = p
                    setOnClickListener { currentPage = page; renderPage() }
                })
            }
        }

        // Next
        paginationBar.addView(makePagBtn("▶", currentPage < totalPages) {
            currentPage++; renderPage()
        })

        // Info
        paginationBar.addView(TextView(this).apply {
            text = "  $start-$end of $totalEntries"
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 11f
            setPadding(dp(8), 0, 0, 0)
        })
    }

    private fun calcPageRange(current: Int, total: Int): List<Int> {
        if (total <= 7) return (1..total).toList()
        val pages = mutableListOf<Int>()
        pages.add(1)
        if (current > 3) pages.add(-1)
        val rangeStart = (current - 1).coerceAtLeast(2)
        val rangeEnd = (current + 1).coerceAtMost(total - 1)
        for (p in rangeStart..rangeEnd) pages.add(p)
        if (current < total - 2) pages.add(-1)
        pages.add(total)
        return pages
    }

    private fun makePagBtn(text: String, enabled: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 14f
            isAllCaps = false
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.3f
            setTextColor(ThemeManager.color(C.TEXT))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(32))
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }
    }

    // ========== Select Mode ==========

    private fun toggleSelectMode() {
        selectMode = !selectMode
        selectedIds.clear()
        selectModeBtn.text = if (selectMode) "Cancel" else "Select"
        batchBar.visibility = if (selectMode) View.VISIBLE else View.GONE
        updateSelectCount()
        renderPage()
    }

    private fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
    }

    private fun selectAll() {
        val total = filteredEntries.size
        val start = if (pageSize > 0) (currentPage - 1) * pageSize else 0
        val end = if (pageSize > 0) (start + pageSize).coerceAtMost(total) else total
        for (i in start until end) {
            selectedIds.add(filteredEntries[i].optString("id", ""))
        }
        updateSelectCount()
        renderPage()
    }

    private fun deselectAll() {
        selectedIds.clear()
        updateSelectCount()
        renderPage()
    }

    private fun updateSelectCount() {
        selectCountView.text = "${selectedIds.size} selected"
    }

    private fun deleteSelected() {
        if (selectedIds.isEmpty()) return
        val count = selectedIds.size
        AlertDialog.Builder(this)
            .setMessage("Delete $count selected entries?")
            .setPositiveButton("Delete") { _, _ ->
                db.deleteEntriesByIds(JSONArray(selectedIds.toList()).toString())
                selectedIds.clear()
                selectMode = false
                selectModeBtn.text = "Select"
                batchBar.visibility = View.GONE
                loadEntries()
                applyFilters()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== Entry Actions ==========

    private fun confirmDeleteEntry(id: String, title: String) {
        val warnDelete = bs.get("warn_before_delete") != "false"
        if (warnDelete) {
            AlertDialog.Builder(this)
                .setMessage("Delete \"${title.ifEmpty { "Untitled" }}\"?")
                .setPositiveButton("Delete") { _, _ -> doDeleteEntry(id) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            doDeleteEntry(id)
        }
    }

    private fun doDeleteEntry(id: String) {
        db.deleteEntryById(id)
        loadEntries()
        applyFilters()
    }

    private fun openEntryViewer(entryId: String) {
        val ids = filteredEntries.map { it.optString("id", "") }
        EntryViewerActivity.pendingEntryId = entryId
        EntryViewerActivity.pendingEntryIds = ids
        EntryViewerActivity.databaseService = db
        EntryViewerActivity.bootstrapService = bs
        startActivity(Intent(this, EntryViewerActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (lastThemeVersion != ThemeManager.themeVersion) { finish(); return }
        loadEntries()
        applyFilters()
    }

    // ========== Formatting ==========

    private fun formatDate(dateStr: String): String {
        if (dateStr.isEmpty()) return ""
        val fmt = bs.get("ev_date_format") ?: "short"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = sdf.parse(dateStr) ?: return dateStr
            when (fmt) {
                "long" -> SimpleDateFormat("MMMM d, yyyy", Locale.US).format(d)
                "iso" -> dateStr
                "us" -> SimpleDateFormat("MM/dd/yyyy", Locale.US).format(d)
                "eu" -> SimpleDateFormat("dd/MM/yyyy", Locale.US).format(d)
                "weekday" -> SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(d)
                else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(d)
            }
        } catch (_: Exception) { dateStr }
    }

    private fun formatTime(timeStr: String): String {
        if (timeStr.isEmpty()) return ""
        val fmt = bs.get("ev_time_format") ?: "12h"
        if (fmt == "24h") return timeStr
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.US)
            val d = sdf.parse(timeStr) ?: return timeStr
            SimpleDateFormat("h:mm a", Locale.US).format(d)
        } catch (_: Exception) { timeStr }
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

    // ========== Helpers ==========

    private fun jsonArrayToStr(arr: JSONArray?): String {
        arr ?: return ""
        val items = mutableListOf<String>()
        for (i in 0 until arr.length()) items.add(arr.optString(i, ""))
        return items.filter { it.isNotEmpty() }.joinToString(", ")
    }

    private fun peopleToStr(arr: JSONArray?): String {
        arr ?: return ""
        val items = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val name = "${p.optString("firstName", "")} ${p.optString("lastName", "")}".trim()
            if (name.isNotEmpty()) items.add(name)
        }
        return items.joinToString(", ")
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

    // Simple flow layout for tags
    class FlowLayout(context: android.content.Context) : ViewGroup(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
            var x = 0; var y = 0; var rowH = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
                val cw = child.measuredWidth
                val ch = child.measuredHeight
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
                val cw = child.measuredWidth
                val ch = child.measuredHeight
                if (x + cw > maxWidth && x > 0) { x = 0; y += rowH; rowH = 0 }
                child.layout(x, y, x + cw, y + ch)
                x += cw; rowH = maxOf(rowH, ch)
            }
        }
    }
}
