package com.journal.app

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
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
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EntryFormActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic var databaseService: DatabaseService? = null
        @JvmStatic var bootstrapService: BootstrapService? = null
        @JvmStatic var weatherService: WeatherService? = null
        @JvmStatic var pendingEntryId: String? = null
    }

    private lateinit var db: DatabaseService
    private lateinit var bs: BootstrapService
    private var ws: WeatherService? = null

    private lateinit var contentContainer: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var deleteBtn: Button

    private var entryId = ""
    private var isNew = true
    private var activeTab = "main"

    // Main tab fields
    private lateinit var dateInput: EditText
    private lateinit var timeInput: EditText
    private lateinit var titleInput: EditText
    private lateinit var contentInput: EditText

    // Data
    private var selectedCategories = mutableSetOf<String>()
    private var currentTags = mutableListOf<String>()
    private var currentLocations = mutableListOf<JSONObject>()
    private var currentImages = mutableListOf<JSONObject>()
    private var currentWeather: JSONObject? = null
    private var placeName = ""
    // Misc tab field refs
    private lateinit var placeNameInput: EditText
    private lateinit var locationSearchInput: EditText
    private lateinit var weatherDisplay: EditText

    // Metadata
    private var allCategories = listOf<String>()
    private var allCategoriesWithDesc = listOf<JSONObject>()
    private var allTags = listOf<String>()
    private var allPlaceNames = listOf<String>()
    private var tagDescriptions = mutableMapOf<String, String>()

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        for (uri in uris) processImageUri(uri)
        if (activeTab == "main") showTab("main")
    }

    private val cameraPicker = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val full = resizeBitmap(bitmap, 1920, 0.7f)
            val thumb = resizeBitmap(bitmap, 150, 0.5f)
            val img = JSONObject().apply {
                put("id", generateId())
                put("name", "camera_${System.currentTimeMillis()}.jpg")
                put("data", full)
                put("thumb", thumb)
            }
            currentImages.add(img)
            if (activeTab == "main") showTab("main")
        }
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetchGpsLocation()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(ThemeManager.fontScaledContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry_form)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        db = databaseService ?: run { finish(); return }
        bs = bootstrapService ?: run { finish(); return }
        ws = weatherService
        databaseService = null
        bootstrapService = null
        weatherService = null

        contentContainer = findViewById(R.id.content_container)
        titleView = findViewById(R.id.form_title)
        deleteBtn = findViewById(R.id.btn_delete)

        loadMetadata()

        val editId = pendingEntryId
        pendingEntryId = null

        if (editId != null && editId.isNotEmpty()) {
            val json = try { JSONObject(db.getEntryById(editId)) } catch (_: Exception) { JSONObject() }
            if (json.has("id")) {
                entryId = json.getString("id")
                isNew = false
                loadEntryData(json)
            }
        }

        if (isNew) {
            placeName = ""
        }

        titleView.text = if (isNew) "New Entry" else "Edit Entry"

        if (!isNew) {
            deleteBtn.visibility = View.VISIBLE
            deleteBtn.setOnClickListener { confirmDelete() }
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener { confirmCancel() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { saveEntry() }
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { confirmCancel() }

        setupTabs()
        showTab("main")
    }

    private fun loadMetadata() {
        try {
            val catJson = JSONArray(db.getCategories())
            allCategories = (0 until catJson.length()).map { catJson.getString(it) }
        } catch (_: Exception) {}
        try {
            val catDescJson = JSONArray(db.getCategoriesWithDesc())
            allCategoriesWithDesc = (0 until catDescJson.length()).map { catDescJson.getJSONObject(it) }
        } catch (_: Exception) {}
        try {
            val tagJson = JSONArray(db.getAllTags())
            allTags = (0 until tagJson.length()).map { tagJson.getString(it) }
        } catch (_: Exception) {}
        try {
            val tdJson = JSONObject(db.getTagDescriptions())
            for (key in tdJson.keys()) tagDescriptions[key] = tdJson.optString(key, "")
        } catch (_: Exception) {}
        try {
            val pnJson = JSONArray(db.getAllPlaceNames())
            allPlaceNames = (0 until pnJson.length()).map { pnJson.getString(it) }
        } catch (_: Exception) {}
    }

    private fun loadEntryData(entry: JSONObject) {
        dateValue = entry.optString("date", "")
        timeValue = entry.optString("time", "")
        titleValue = entry.optString("title", "")
        contentValue = entry.optString("content", "")

        // Categories
        val cats = entry.optJSONArray("categories") ?: JSONArray()
        selectedCategories = (0 until cats.length()).map { cats.getString(it) }.toMutableSet()

        // Tags
        val tags = entry.optJSONArray("tags") ?: JSONArray()
        currentTags = (0 until tags.length()).map { tags.getString(it) }.toMutableList()

        // Locations
        val locs = entry.optJSONArray("locations") ?: JSONArray()
        currentLocations = (0 until locs.length()).mapNotNull { locs.optJSONObject(it) }.toMutableList()

        // Images
        val imgs = entry.optJSONArray("images") ?: JSONArray()
        currentImages = (0 until imgs.length()).mapNotNull { imgs.optJSONObject(it) }.toMutableList()

        // Weather
        val w = entry.opt("weather")
        currentWeather = if (w is JSONObject) w else null

        placeName = entry.optString("placeName", "")
    }

    // ========== Tabs ==========

    private fun setupTabs() {
        findViewById<Button>(R.id.tab_main).setOnClickListener { showTab("main") }
        findViewById<Button>(R.id.tab_misc).setOnClickListener { showTab("misc") }
    }

    private fun showTab(tab: String) {
        syncFormData()
        activeTab = tab

        val tabs = mapOf("main" to R.id.tab_main, "misc" to R.id.tab_misc)
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
            "main" -> buildMainTab()
            "misc" -> buildMiscTab()
        }
    }

    private fun syncFormData() {
        if (activeTab == "main" && ::titleInput.isInitialized) {
            // Main tab data is stored in the input fields — read on save
        }
        if (activeTab == "misc" && ::placeNameInput.isInitialized) {
            placeName = placeNameInput.text.toString().trim()
        }
    }

    // Date/time values stored as strings
    private var dateValue = ""
    private var timeValue = ""
    private var titleValue = ""
    private var contentValue = ""

    // ========== Main Tab ==========

    private fun buildMainTab() {
        if (::titleInput.isInitialized) {
            titleValue = titleInput.text.toString()
        }
        if (::contentInput.isInitialized) {
            contentValue = contentInput.text.toString()
        }

        addSectionHeader("Main")

        // Date
        addLabel("Date")
        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        }
        dateInput = makeInput("YYYY-MM-DD", dateValue.ifEmpty {
            if (isNew) SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) else ""
        }).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
            isFocusable = false
            setOnClickListener { showDatePicker() }
        }
        if (dateValue.isEmpty() && isNew) {
            dateValue = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            dateInput.setText(dateValue)
        }
        dateRow.addView(dateInput)

        dateRow.addView(makeSecondaryButton("Pick") { showDatePicker() }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38))
        })
        contentContainer.addView(dateRow)

        // Time
        addLabel("Time (optional)")
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        }
        timeInput = makeInput("HH:MM", timeValue).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
            isFocusable = false
            setOnClickListener { showTimePicker() }
        }
        timeRow.addView(timeInput)

        timeRow.addView(makeSecondaryButton("Now") {
            val cal = Calendar.getInstance()
            timeValue = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            timeInput.setText(timeValue)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginEnd = dp(4) }
        })
        timeRow.addView(makeSecondaryButton("Pick") { showTimePicker() }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38))
        })
        contentContainer.addView(timeRow)

        // Title
        addLabel("Title")
        titleInput = makeInput("Entry title", titleValue)
        contentContainer.addView(titleInput)

        // Content
        addLabel("Content")
        contentInput = EditText(this).apply {
            hint = "Write your journal entry..."
            setText(contentValue)
            textSize = 14f
            minLines = 10
            maxLines = 20
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@EntryFormActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = linParams().apply { bottomMargin = dp(8) }
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
        }
        contentContainer.addView(contentInput)

        // Images
        addSectionHeader("Images")
        buildImageSection()
    }

    // ========== Images ==========

    private fun buildImageSection() {
        if (currentImages.isNotEmpty()) {
            val grid = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = linParams().apply { bottomMargin = dp(8) }
            }
            val scroll = HorizontalScrollView(this).apply {
                layoutParams = linParams()
            }
            scroll.addView(grid)

            for (i in currentImages.indices) {
                val img = currentImages[i]
                val thumbUrl = img.optString("thumb", img.optString("data", ""))
                val wrap = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(80), dp(80)).apply { marginEnd = dp(6) }
                }
                val iv = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    val bg = GradientDrawable().apply {
                        cornerRadius = dp(6).toFloat()
                        setColor(ThemeManager.color(C.INPUT_BG))
                    }
                    background = bg
                    clipToOutline = true
                }
                loadBase64Image(iv, thumbUrl)
                wrap.addView(iv)

                val removeBtn = Button(this).apply {
                    text = "✕"
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    val bg = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(Color.parseColor("#cc333333"))
                    }
                    background = bg
                    layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply {
                        topMargin = dp(2); marginEnd = dp(2)
                    }
                    setPadding(0, 0, 0, 0)
                    val idx = i
                    setOnClickListener {
                        currentImages.removeAt(idx)
                        showTab("main")
                    }
                }
                wrap.addView(removeBtn)
                grid.addView(wrap)
            }
            contentContainer.addView(scroll)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        }
        btnRow.addView(makeSecondaryButton("📷 Attach") { imagePicker.launch("image/*") }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginEnd = dp(4) }
        })
        btnRow.addView(makeSecondaryButton("📸 Camera") { cameraPicker.launch(null) }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38))
        })
        contentContainer.addView(btnRow)
    }

    private fun processImageUri(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri) ?: return
            val original = BitmapFactory.decodeStream(stream)
            stream.close()
            if (original != null) {
                val full = resizeBitmap(original, 1920, 0.7f)
                val thumb = resizeBitmap(original, 150, 0.5f)
                val img = JSONObject().apply {
                    put("id", generateId())
                    put("name", uri.lastPathSegment ?: "image.jpg")
                    put("data", full)
                    put("thumb", thumb)
                }
                currentImages.add(img)
            }
        } catch (_: Exception) {}
    }

    private fun resizeBitmap(bmp: android.graphics.Bitmap, maxDim: Int, quality: Float): String {
        var w = bmp.width
        var h = bmp.height
        if (w > maxDim || h > maxDim) {
            if (w > h) { h = (h.toFloat() * maxDim / w).toInt(); w = maxDim }
            else { w = (w.toFloat() * maxDim / h).toInt(); h = maxDim }
        }
        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
        val baos = ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, (quality * 100).toInt(), baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    // ========== Misc Tab ==========

    private fun buildMiscTab() {
        // Save misc values before rebuild
        if (::placeNameInput.isInitialized) {
            placeName = placeNameInput.text.toString().trim()
        }

        // Categories
        addSectionHeader("Categories")
        buildCategorySection()

        addSpacer()

        // Tags
        addSectionHeader("Tags")
        buildTagSection()

        addSpacer()

        // Place Name
        addSectionHeader("Place")
        addLabel("Place Name")
        placeNameInput = makeInput("e.g. NYC Trip, Office, Home", placeName)
        contentContainer.addView(placeNameInput)

        // Locations
        addLabel("Locations")
        buildLocationSection()

        addSpacer()

        // Weather
        addSectionHeader("Weather")
        buildWeatherSection()
    }

    // ========== Categories ==========

    private fun buildCategorySection() {
        // Quick add
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = linParams().apply { bottomMargin = dp(6) }
        }
        val newCatInput = EditText(this).apply {
            hint = "New category"
            textSize = 13f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@EntryFormActivity, R.drawable.input_bg)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
            isSingleLine = true
        }
        addRow.addView(newCatInput)
        addRow.addView(makeAccentButton("Add") {
            val name = newCatInput.text.toString().trim()
            if (name.isNotEmpty() && !allCategories.contains(name)) {
                val cats = allCategories.toMutableList()
                cats.add(name)
                db.setCategories(JSONArray(cats).toString())
                allCategories = cats
                selectedCategories.add(name)
                newCatInput.setText("")
                showTab("misc")
            } else if (name.isNotEmpty()) {
                selectedCategories.add(name)
                showTab("misc")
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36))
        })
        contentContainer.addView(addRow)

        // Checkbox list
        for (cat in allCategories) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = linParams()
            }
            val cb = CheckBox(this).apply {
                text = cat
                isChecked = selectedCategories.contains(cat)
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 14f
                buttonTintList = ThemeManager.colorStateList(C.ACCENT)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedCategories.add(cat) else selectedCategories.remove(cat)
                }
            }
            row.addView(cb)
            contentContainer.addView(row)
        }

        if (allCategories.isEmpty()) {
            contentContainer.addView(TextView(this).apply {
                text = "No categories yet."
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                textSize = 12f
                setPadding(dp(4), dp(4), dp(4), dp(4))
            })
        }
    }

    // ========== Tags ==========

    private fun buildTagSection() {
        // Tag chips
        if (currentTags.isNotEmpty()) {
            val chipFlow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = linParams().apply { bottomMargin = dp(6) }
            }
            val chipScroll = HorizontalScrollView(this).apply { layoutParams = linParams() }
            chipScroll.addView(chipFlow)

            for (tag in currentTags) {
                val chip = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(4), dp(4), dp(4))
                    val bg = GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(ThemeManager.color(C.INPUT_BG))
                    }
                    background = bg
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) }
                }
                chip.addView(TextView(this).apply {
                    text = tag
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 13f
                })
                val removeTag = tag
                chip.addView(Button(this).apply {
                    text = "✕"
                    textSize = 10f
                    setTextColor(ThemeManager.color(C.ERROR))
                    setBackgroundColor(Color.TRANSPARENT)
                    layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginStart = dp(2) }
                    setPadding(0, 0, 0, 0)
                    setOnClickListener {
                        currentTags.remove(removeTag)
                        showTab("misc")
                    }
                })
                chipFlow.addView(chip)
            }
            contentContainer.addView(chipScroll)
        }

        // Tag input with autocomplete
        val tagRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = linParams().apply { bottomMargin = dp(6) }
        }
        val tagInput = AutoCompleteTextView(this).apply {
            hint = "Type a tag and tap Add"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@EntryFormActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
            isSingleLine = true
            threshold = 1

            val adapter = ArrayAdapter(this@EntryFormActivity, android.R.layout.simple_dropdown_item_1line, allTags.filter { !currentTags.contains(it) })
            setAdapter(adapter)
            setDropDownBackgroundResource(R.color.login_input_bg)
        }
        tagRow.addView(tagInput)
        tagRow.addView(makeAccentButton("Add") {
            val name = tagInput.text.toString().trim()
            if (name.isNotEmpty() && !currentTags.contains(name)) {
                currentTags.add(name)
                tagInput.setText("")
                showTab("misc")
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36))
        })
        contentContainer.addView(tagRow)
    }

    // ========== Locations ==========

    private fun buildLocationSection() {
        for (i in currentLocations.indices) {
            val loc = currentLocations[i]
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = ContextCompat.getDrawable(this@EntryFormActivity, R.drawable.input_bg)
                layoutParams = linParams().apply { bottomMargin = dp(4) }
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val nameEdit = EditText(this).apply {
                hint = "Name (optional)"
                setText(loc.optString("name", ""))
                textSize = 13f
                setTextColor(ThemeManager.color(C.TEXT))
                setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                isSingleLine = true
                val idx = i
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) currentLocations[idx].put("name", text.toString().trim())
                }
            }
            topRow.addView(nameEdit)

            val removeLocIdx = i
            topRow.addView(Button(this).apply {
                text = "✕"
                textSize = 12f
                setTextColor(ThemeManager.color(C.ERROR))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    currentLocations.removeAt(removeLocIdx)
                    showTab("misc")
                }
            })
            card.addView(topRow)

            val addr = loc.optString("address", "")
            if (addr.isNotEmpty()) {
                card.addView(TextView(this).apply {
                    text = addr
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 12f
                    setPadding(0, dp(2), 0, 0)
                })
            }

            if (!loc.isNull("lat")) {
                card.addView(TextView(this).apply {
                    text = "${loc.optDouble("lat")}, ${loc.optDouble("lng")}"
                    setTextColor(ThemeManager.color(C.ACCENT))
                    textSize = 11f
                    setPadding(0, dp(2), 0, 0)
                })
            }

            contentContainer.addView(card)
        }

        // Location search
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = linParams().apply { topMargin = dp(4); bottomMargin = dp(4) }
        }
        locationSearchInput = EditText(this).apply {
            hint = "Name, address, or coordinates..."
            textSize = 13f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@EntryFormActivity, R.drawable.input_bg)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
            isSingleLine = true
        }
        searchRow.addView(locationSearchInput)

        searchRow.addView(makeSecondaryButton("Add") {
            addLocationManual()
        }.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(4) } })

        searchRow.addView(makeSecondaryButton("Search") {
            searchLocation()
        }.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(4) } })

        searchRow.addView(makeSecondaryButton("GPS") {
            requestGps()
        }.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)) })

        contentContainer.addView(searchRow)
    }

    private fun addLocationManual() {
        val input = locationSearchInput.text.toString().trim()
        if (input.isEmpty()) return

        val coordMatch = Regex("^(-?\\d+\\.?\\d*)\\s*,\\s*(-?\\d+\\.?\\d*)$").matchEntire(input)
        if (coordMatch != null) {
            val lat = coordMatch.groupValues[1].toDouble()
            val lng = coordMatch.groupValues[2].toDouble()
            currentLocations.add(JSONObject().apply {
                put("name", ""); put("address", ""); put("lat", lat); put("lng", lng)
            })
        } else {
            currentLocations.add(JSONObject().apply {
                put("name", input); put("address", ""); put("lat", JSONObject.NULL); put("lng", JSONObject.NULL)
            })
        }
        locationSearchInput.setText("")
        showTab("misc")
    }

    private fun searchLocation() {
        val query = locationSearchInput.text.toString().trim()
        if (query.isEmpty()) return

        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val provider = bs.get("geocoding_provider") ?: "photon"
                val results = geocodeSearch(query, provider)
                runOnUiThread {
                    if (results.isEmpty()) {
                        Toast.makeText(this, "No results found.", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    val items = results.map { "${it.optString("displayName")} (${it.optString("lat")}, ${it.optString("lng")})" }
                    AlertDialog.Builder(this)
                        .setTitle("Select Location")
                        .setItems(items.toTypedArray()) { _, which ->
                            val r = results[which]
                            currentLocations.add(JSONObject().apply {
                                put("name", "")
                                put("address", r.optString("displayName", ""))
                                put("lat", r.optDouble("lat"))
                                put("lng", r.optDouble("lng"))
                            })
                            locationSearchInput.setText("")
                            showTab("misc")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun geocodeSearch(query: String, provider: String): List<JSONObject> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = when (provider) {
            "nominatim" -> "https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&limit=5&q=$encoded"
            else -> "https://photon.komoot.io/api/?q=$encoded&limit=5&lang=en"
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val json = try { conn.inputStream.bufferedReader().readText() } finally { conn.disconnect() }

        return when (provider) {
            "nominatim" -> {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val r = arr.getJSONObject(i)
                    JSONObject().apply {
                        put("displayName", r.optString("display_name", ""))
                        put("lat", r.optString("lat", ""))
                        put("lng", r.optString("lon", ""))
                    }
                }
            }
            else -> {
                val data = JSONObject(json)
                val features = data.optJSONArray("features") ?: JSONArray()
                (0 until features.length()).map { i ->
                    val f = features.getJSONObject(i)
                    val props = f.optJSONObject("properties") ?: JSONObject()
                    val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates")
                    val parts = listOfNotNull(
                        props.optString("housenumber", "").ifEmpty { null },
                        props.optString("street", "").ifEmpty { null },
                        props.optString("name", "").ifEmpty { null },
                        props.optString("city", "").ifEmpty { null },
                        props.optString("state", "").ifEmpty { null },
                        props.optString("country", "").ifEmpty { null }
                    )
                    JSONObject().apply {
                        put("displayName", parts.joinToString(", ").ifEmpty { "Unknown" })
                        put("lat", coords?.optDouble(1)?.toString() ?: "")
                        put("lng", coords?.optDouble(0)?.toString() ?: "")
                    }
                }
            }
        }
    }

    private fun requestGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchGpsLocation()
        } else {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun fetchGpsLocation() {
        val lm = getSystemService(android.location.LocationManager::class.java) ?: return
        Toast.makeText(this, "Getting GPS location...", Toast.LENGTH_SHORT).show()

        try {
            lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    val lat = String.format(Locale.US, "%.6f", location.latitude).toDouble()
                    val lng = String.format(Locale.US, "%.6f", location.longitude).toDouble()
                    runOnUiThread {
                        currentLocations.add(JSONObject().apply {
                            put("name", ""); put("address", "$lat, $lng"); put("lat", lat); put("lng", lng)
                        })
                        showTab("misc")
                    }
                }
                @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }, mainLooper)
        } catch (e: Exception) {
            Toast.makeText(this, "GPS failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== Weather ==========

    private fun buildWeatherSection() {
        val weatherText = if (currentWeather != null) formatWeather(currentWeather!!) else ""

        weatherDisplay = EditText(this).apply {
            hint = "No weather data"
            setText(weatherText)
            textSize = 13f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@EntryFormActivity, R.drawable.input_bg)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = linParams().apply { bottomMargin = dp(4) }
            isFocusable = false
        }
        contentContainer.addView(weatherDisplay)

        val weatherBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        }
        weatherBtnRow.addView(makeSecondaryButton("Import Weather") {
            importWeather()
        }.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(4) } })

        if (currentWeather != null) {
            weatherBtnRow.addView(makeSecondaryButton("Clear") {
                currentWeather = null
                weatherDisplay.setText("")
            }.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)) })
        }

        weatherBtnRow.addView(makeSecondaryButton("GPS + Weather") {
            fetchGpsWeather()
        }.apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginStart = dp(4) } })
        contentContainer.addView(weatherBtnRow)
    }

    private fun importWeather() {
        val locLat = bs.get("weather_lat") ?: ""
        val locLng = bs.get("weather_lng") ?: ""
        val locName = bs.get("weather_location_name") ?: ""
        if (locLat.isEmpty() || locLng.isEmpty()) {
            Toast.makeText(this, "No weather location set. Configure in Settings.", Toast.LENGTH_SHORT).show()
            return
        }

        weatherDisplay.setText("Fetching weather...")
        Thread {
            try {
                val unit = bs.get("temp_unit") ?: "celsius"
                val result = ws?.fetchCurrent(locLat.toDouble(), locLng.toDouble(), unit)
                if (result != null) {
                    val w = JSONObject(result)
                    w.put("locationName", locName)
                    w.put("fetchedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
                    runOnUiThread {
                        currentWeather = w
                        weatherDisplay.setText(formatWeather(w))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    weatherDisplay.setText("")
                    Toast.makeText(this, "Weather fetch failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun fetchGpsWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }

        val lm = getSystemService(android.location.LocationManager::class.java) ?: return
        Toast.makeText(this, "Getting GPS + Weather...", Toast.LENGTH_SHORT).show()

        try {
            lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    val lat = String.format(Locale.US, "%.6f", location.latitude).toDouble()
                    val lng = String.format(Locale.US, "%.6f", location.longitude).toDouble()

                    runOnUiThread {
                        currentLocations.add(JSONObject().apply {
                            put("name", ""); put("address", "$lat, $lng"); put("lat", lat); put("lng", lng)
                        })
                    }

                    Thread {
                        try {
                            val unit = bs.get("temp_unit") ?: "celsius"
                            val result = ws?.fetchCurrent(lat, lng, unit)
                            if (result != null) {
                                val w = JSONObject(result)
                                w.put("locationName", "$lat, $lng")
                                w.put("fetchedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
                                runOnUiThread {
                                    currentWeather = w
                                    weatherDisplay.setText(formatWeather(w))
                                    showTab("misc")
                                }
                            }
                        } catch (_: Exception) {}
                    }.start()
                }
                @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }, mainLooper)
        } catch (_: Exception) {}
    }

    private fun formatWeather(w: JSONObject): String {
        val desc = w.optString("description", "")
        val temp = w.optDouble("temp", 0.0)
        val feelsLike = w.optDouble("feelsLike", 0.0)
        val unit = if (w.optString("unit") == "celsius" || w.optString("unit") == "C") "C" else "F"
        val humidity = w.optInt("humidity", 0)
        val wind = w.optDouble("windSpeed", 0.0)
        return "$desc, $temp°$unit (feels $feelsLike°$unit), Humidity $humidity%, Wind $wind mph"
    }

    // ========== Date/Time Pickers ==========

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        if (dateValue.isNotEmpty()) {
            try {
                val parts = dateValue.split("-")
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            } catch (_: Exception) {}
        }
        DatePickerDialog(this, { _, year, month, day ->
            dateValue = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
            dateInput.setText(dateValue)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        if (timeValue.isNotEmpty()) {
            try {
                val parts = timeValue.split(":")
                cal.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                cal.set(Calendar.MINUTE, parts[1].toInt())
            } catch (_: Exception) {}
        }
        TimePickerDialog(this, { _, hour, minute ->
            timeValue = String.format(Locale.US, "%02d:%02d", hour, minute)
            timeInput.setText(timeValue)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    // ========== Save / Delete ==========

    private fun saveEntry() {
        syncFormData()

        // Read main tab values
        if (::dateInput.isInitialized) {
            dateValue = dateInput.text.toString().trim()
            timeValue = timeInput.text.toString().trim()
            titleValue = titleInput.text.toString().trim()
        }
        if (::contentInput.isInitialized) {
            contentValue = contentInput.text.toString()
        }

        if (titleValue.isEmpty()) {
            Toast.makeText(this, "Please enter a title.", Toast.LENGTH_SHORT).show()
            showTab("main")
            return
        }

        if (dateValue.isEmpty()) {
            Toast.makeText(this, "Please enter a date.", Toast.LENGTH_SHORT).show()
            showTab("main")
            return
        }

        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

        val imagesArr = JSONArray()
        for (img in currentImages) imagesArr.put(img)

        val locsArr = JSONArray()
        for (loc in currentLocations) locsArr.put(loc)

        if (isNew) {
            val entry = JSONObject().apply {
                put("id", generateId())
                put("date", dateValue)
                put("time", timeValue)
                put("title", titleValue)
                put("content", contentValue)
                put("categories", JSONArray(selectedCategories.toList()))
                put("tags", JSONArray(currentTags))
                put("placeName", placeName)
                put("locations", locsArr)
                put("weather", currentWeather ?: JSONObject.NULL)
                put("images", imagesArr)
                put("dtCreated", now)
                put("dtUpdated", now)
            }
            db.addEntry(entry.toString())
        } else {
            val fields = JSONObject().apply {
                put("date", dateValue)
                put("time", timeValue)
                put("title", titleValue)
                put("content", contentValue)
                put("categories", JSONArray(selectedCategories.toList()))
                put("tags", JSONArray(currentTags))
                put("placeName", placeName)
                put("locations", locsArr)
                put("weather", currentWeather ?: JSONObject.NULL)
                put("images", imagesArr)
                put("dtUpdated", now)
            }
            db.updateEntry(entryId, fields.toString())
        }

        setResult(RESULT_OK)
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage("Are you sure you want to delete this entry?")
            .setPositiveButton("Delete") { _, _ ->
                db.deleteEntryById(entryId)
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmCancel() {
        val hasContent = (::titleInput.isInitialized && titleInput.text.isNotEmpty()) ||
                (::contentInput.isInitialized && contentInput.text.isNotEmpty())
        if (hasContent && isNew) {
            AlertDialog.Builder(this)
                .setMessage("Discard this entry?")
                .setPositiveButton("Discard") { _, _ -> finish() }
                .setNegativeButton("Keep Editing", null)
                .show()
        } else {
            finish()
        }
    }

    // ========== Helpers ==========

    private fun loadBase64Image(imageView: ImageView, dataUrl: String) {
        try {
            val base64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            if (base64.isEmpty()) return
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) imageView.setImageBitmap(bmp)
        } catch (_: Exception) {}
    }

    private fun generateId(): String {
        val ts = System.currentTimeMillis().toString(36)
        val rand = (Math.random() * 1e9).toLong().toString(36)
        return "$ts$rand"
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
            background = ContextCompat.getDrawable(this@EntryFormActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = linParams().apply { bottomMargin = dp(8) }
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
    }

    private fun makeAccentButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@EntryFormActivity, R.drawable.btn_accent)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun makeSecondaryButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.TEXT))
            background = ContextCompat.getDrawable(this@EntryFormActivity, R.drawable.btn_secondary)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { onClick() }
        }
    }

    private fun linParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        confirmCancel()
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
