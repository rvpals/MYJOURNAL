package com.journal.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import com.journal.app.ThemeManager.C
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic var databaseService: DatabaseService? = null
        @JvmStatic var bootstrapService: BootstrapService? = null
        @JvmStatic var cryptoService: CryptoService? = null
        @JvmStatic var weatherService: WeatherService? = null
        @JvmStatic var currentJournalId: String? = null

        private const val WALLPAPER_PICK = 2001
        private const val ICON_PICK = 2002
        private const val CSV_PICK = 2003
    }

    private lateinit var db: DatabaseService
    private lateinit var bs: BootstrapService
    private lateinit var crypto: CryptoService
    private lateinit var weather: WeatherService
    private var journalId = ""

    private lateinit var contentContainer: LinearLayout
    private var activeTab = "prefs"

    private var iconPickerTarget: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        db = databaseService ?: run { finish(); return }
        bs = bootstrapService ?: run { finish(); return }
        crypto = cryptoService ?: run { finish(); return }
        weather = weatherService ?: run { finish(); return }
        journalId = currentJournalId ?: bs.get("last_journal_id") ?: ""

        databaseService = null
        bootstrapService = null
        cryptoService = null
        weatherService = null
        currentJournalId = null

        contentContainer = findViewById(R.id.settings_content)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        setupTabs()
        showTab("prefs")
    }

    // ========== Tabs ==========

    private val tabDefs = listOf(
        Triple("prefs", "⚙️", "Prefs"),
        Triple("templates", "📝", "Templates"),
        Triple("cattags", "🏷️", "Metadata"),
        Triple("data", "💾", "Data"),
        Triple("widgets", "🧩", "Widgets"),
        Triple("dashboard", "📊", "Dashboard")
    )

    private val tabButtons = mutableMapOf<String, Button>()

    private fun setupTabs() {
        val tabBar = findViewById<LinearLayout>(R.id.settings_tabs)
        tabBar.removeAllViews()

        val tabsPerRow = 3
        val rows = tabDefs.chunked(tabsPerRow)

        for ((rowIdx, rowTabs) in rows.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (rowIdx > 0) topMargin = dp(4) }
            }

            for ((id, icon, label) in rowTabs) {
                val btn = Button(this).apply {
                    text = "$icon\n$label"
                    textSize = 11f
                    isAllCaps = false
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(8), dp(10), dp(6))
                    stateListAnimator = null
                    elevation = dp(3).toFloat()
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply { marginEnd = dp(4) }
                    setOnClickListener { showTab(id) }
                }
                tabButtons[id] = btn
                row.addView(btn)
            }

            // Fill remaining slots so buttons stay equal width
            val remaining = tabsPerRow - rowTabs.size
            for (i in 0 until remaining) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply { marginEnd = dp(4) }
                })
            }

            tabBar.addView(row)
        }
    }

    private fun showTab(tabId: String) {
        activeTab = tabId
        for ((id, btn) in tabButtons) {
            if (id == tabId) {
                btn.setTextColor(ThemeManager.color(C.CARD_BG))
                btn.background = ContextCompat.getDrawable(this, R.drawable.tab_active_3d)
                btn.elevation = dp(4).toFloat()
            } else {
                btn.setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                btn.background = ContextCompat.getDrawable(this, R.drawable.tab_inactive_3d)
                btn.elevation = dp(2).toFloat()
            }
        }

        contentContainer.removeAllViews()
        when (tabId) {
            "prefs" -> buildPrefsTab()
            "templates" -> buildTemplatesTab()
            "cattags" -> buildMetadataTab()
            "data" -> buildDataTab()
            "widgets" -> buildWidgetsTab()
            "dashboard" -> buildDashboardTab()
        }
    }

    // ========== Preferences Tab ==========

    private fun buildPrefsTab() {
        val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }

        buildSectionHeader("⚙️  Preferences")
        buildToggle("Auto-open last journal", bs.get("auto_open_last_journal") == "true") { checked ->
            bs.set("auto_open_last_journal", if (checked) "true" else "false")
        }
        buildToggle("Warn before delete", bs.get("warn_before_delete") != "false") { checked ->
            bs.set("warn_before_delete", if (checked) "true" else "false")
        }
        buildToggle("Warn if new entry not saved", bs.get("warn_unsaved_entry") != "false") { checked ->
            bs.set("warn_unsaved_entry", if (checked) "true" else "false")
        }
        buildToggle("Collapse Misc Info by default", bs.get("ev_misc_collapsed") == "1") { checked ->
            bs.set("ev_misc_collapsed", if (checked) "1" else "0")
        }

        // Biometric toggles
        if (isBiometricSupported()) {
            val hasCred = hasBiometricCredential()
            buildToggle("Fingerprint unlock for this journal", hasCred) { checked ->
                toggleBiometric(checked)
            }
            if (hasCred) {
                buildToggle("Unlock with biometric by default", bs.get("auto_biometric") == "true") { checked ->
                    bs.set("auto_biometric", if (checked) "true" else "false")
                }
            }
        }

        buildSpacer()

        // Geocoding provider
        val geocodingOptions = listOf("photon" to "Komoot Photon (free)", "nominatim" to "Nominatim / OSM (free)", "google" to "Google (API key)")
        val currentGeo = bs.get("geocoding_provider") ?: "photon"
        buildSpinner("Geocoding Provider", geocodingOptions.map { it.second }, geocodingOptions.indexOfFirst { it.first == currentGeo }.coerceAtLeast(0)) { idx ->
            bs.set("geocoding_provider", geocodingOptions[idx].first)
        }

        buildSpacer()

        // Font
        val fontFamilies = listOf(
            "" to "Default (Quicksand)",
            "'Georgia', serif" to "Georgia",
            "'Times New Roman', serif" to "Times New Roman",
            "'Garamond', serif" to "Garamond",
            "'Palatino Linotype', serif" to "Palatino",
            "'Arial', sans-serif" to "Arial",
            "'Verdana', sans-serif" to "Verdana",
            "'Trebuchet MS', sans-serif" to "Trebuchet MS",
            "'Segoe UI', sans-serif" to "Segoe UI",
            "'Roboto', sans-serif" to "Roboto",
            "'Courier New', monospace" to "Courier New",
            "'Consolas', monospace" to "Consolas"
        )
        val currentFont = bs.get("ev_font_family") ?: ""
        buildSectionHeader("🔤  Entry Viewer Font")
        buildSpinner("Font Family", fontFamilies.map { it.second }, fontFamilies.indexOfFirst { it.first == currentFont }.coerceAtLeast(0)) { idx ->
            bs.set("ev_font_family", fontFamilies[idx].first)
            updateFontPreview()
        }

        val fontSizes = listOf(
            "" to "Default",
            "0.8rem" to "Small",
            "0.95rem" to "Medium",
            "1.1rem" to "Large",
            "1.25rem" to "X-Large",
            "1.4rem" to "XX-Large"
        )
        val currentSize = bs.get("ev_font_size") ?: ""
        buildSpinner("Font Size", fontSizes.map { it.second }, fontSizes.indexOfFirst { it.first == currentSize }.coerceAtLeast(0)) { idx ->
            bs.set("ev_font_size", fontSizes[idx].first)
            updateFontPreview()
        }

        // Font preview
        fontPreview = TextView(this).apply {
            text = "The quick brown fox jumps over the lazy dog."
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        contentContainer.addView(fontPreview)
        updateFontPreview()

        buildSpacer()

        // Date & Time format
        buildSectionHeader("📅  Date & Time Display")
        val dateFmts = listOf(
            "short" to "Mar 27, 2026",
            "long" to "March 27, 2026",
            "iso" to "2026-03-27",
            "us" to "03/27/2026",
            "eu" to "27/03/2026",
            "weekday" to "Thu, Mar 27, 2026"
        )
        val currentDateFmt = bs.get("ev_date_format") ?: "short"
        buildSpinner("Date Format", dateFmts.map { it.second }, dateFmts.indexOfFirst { it.first == currentDateFmt }.coerceAtLeast(0)) { idx ->
            bs.set("ev_date_format", dateFmts[idx].first)
        }

        val timeFmts = listOf("12h" to "2:30 PM", "24h" to "14:30")
        val currentTimeFmt = bs.get("ev_time_format") ?: "12h"
        buildSpinner("Time Format", timeFmts.map { it.second }, timeFmts.indexOfFirst { it.first == currentTimeFmt }.coerceAtLeast(0)) { idx ->
            bs.set("ev_time_format", timeFmts[idx].first)
        }

        buildSpacer()

        // Dashboard & ranking
        buildSectionHeader("📊  Dashboard & Ranking")
        buildNumberInput("Max Pinned Entries", bs.get("max_pinned_entries")?.toIntOrNull() ?: 10, 1, 100) { value ->
            bs.set("max_pinned_entries", value.toString())
        }
        buildNumberInput("Max Entries in Ranking Cards", bs.get("max_ranking_entries")?.toIntOrNull() ?: 5, 1, 100) { value ->
            bs.set("max_ranking_entries", value.toString())
        }

        buildSpacer()

        // Default entry list order
        buildSectionHeader("📋  Default Entry List Order")
        val sortFields = listOf(
            "date" to "Date", "time" to "Time", "title" to "Title",
            "content" to "Content", "categories" to "Categories", "tags" to "Tags",
            "placeName" to "Place Name", "dtCreated" to "Created", "dtUpdated" to "Updated"
        )
        val currentSortField = bs.get("default_entry_sort_field") ?: "dtCreated"
        buildSpinner("Sort By", sortFields.map { it.second }, sortFields.indexOfFirst { it.first == currentSortField }.coerceAtLeast(0)) { idx ->
            bs.set("default_entry_sort_field", sortFields[idx].first)
        }

        val sortDirs = listOf("desc" to "Newest First", "asc" to "Oldest First")
        val currentSortDir = bs.get("default_entry_sort_dir") ?: "desc"
        buildSpinner("Direction", sortDirs.map { it.second }, sortDirs.indexOfFirst { it.first == currentSortDir }.coerceAtLeast(0)) { idx ->
            bs.set("default_entry_sort_dir", sortDirs[idx].first)
        }

        buildSpacer()

        // Display & Appearance
        buildSectionHeader("🎨  Display & Appearance")

        // Entry list fields
        buildLabel("Entry List Fields")
        val fieldDefs = listOf(
            "date" to "Date", "time" to "Time", "title" to "Title",
            "content" to "Content", "categories" to "Categories", "tags" to "Tags",
            "places" to "Places", "weather" to "Weather", "images" to "Images"
        )
        val fieldDefaults = mapOf(
            "date" to true, "time" to true, "title" to true,
            "content" to true, "categories" to true, "tags" to true,
            "places" to false, "weather" to true, "images" to true
        )
        val entryListFields = try {
            settings.optJSONObject("entryListFields") ?: JSONObject()
        } catch (_: Exception) { JSONObject() }

        for ((key, label) in fieldDefs) {
            val defaultVal = fieldDefaults[key] ?: true
            val isChecked = if (entryListFields.has(key)) entryListFields.optBoolean(key, defaultVal) else defaultVal
            buildToggle(label, isChecked) { checked ->
                val current = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
                val fields = current.optJSONObject("entryListFields") ?: JSONObject()
                fields.put(key, checked)
                db.setSettings(JSONObject().apply { put("entryListFields", fields) }.toString())
            }
        }

        buildSpacer()

        // Weather location
        buildLabel("Weather Location")
        val weatherLocName = bs.get("weather_location_name")
        if (!weatherLocName.isNullOrEmpty()) {
            contentContainer.addView(createChip("📍 $weatherLocName") {
                bs.set("weather_location", null)
                bs.set("weather_location_name", null)
                bs.set("weather_location_lat", null)
                bs.set("weather_location_lng", null)
                showTab("prefs")
            })
        }
        buildWeatherSearch()

        val tempUnits = listOf("fahrenheit" to "°F (Fahrenheit)", "celsius" to "°C (Celsius)")
        val currentTempUnit = bs.get("weather_temp_unit") ?: "fahrenheit"
        buildSpinner("Temperature Unit", tempUnits.map { it.second }, tempUnits.indexOfFirst { it.first == currentTempUnit }.coerceAtLeast(0)) { idx ->
            bs.set("weather_temp_unit", tempUnits[idx].first)
        }

        buildSpacer()

        // Theme
        buildLabel("Theme")
        val themes = listOf(
            "light", "dark", "ocean", "midnight", "forest", "amethyst",
            "aurora", "lavender", "frost", "navy", "sunflower", "meadow"
        )
        val currentTheme = settings.optString("theme", "dark")
        buildSpinner("Theme", themes.map { it.replaceFirstChar { c -> c.uppercase() } }, themes.indexOf(currentTheme).coerceAtLeast(0)) { idx ->
            db.setSettings(JSONObject().apply { put("theme", themes[idx]) }.toString())
            ThemeManager.setTheme(themes[idx])
            recreate()
        }

        buildSpacer()

        // Wallpaper
        buildLabel("Wallpaper")
        val wallpaper = settings.optString("wallpaper", "")
        wallpaperPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(120)
            ).apply { bottomMargin = dp(8) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            visibility = if (wallpaper.isNotEmpty()) View.VISIBLE else View.GONE
        }
        if (wallpaper.isNotEmpty()) loadBase64Image(wallpaperPreview!!, wallpaper)
        contentContainer.addView(wallpaperPreview)

        val wallRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        wallRow.addView(Button(this).apply {
            text = "Browse"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.TEXT))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_secondary)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) }
            setOnClickListener { pickWallpaper() }
        })
        wallRow.addView(Button(this).apply {
            text = "Clear"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_secondary)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
            setOnClickListener {
                db.setSettings(JSONObject().apply { put("wallpaper", "") }.toString())
                wallpaperPreview?.visibility = View.GONE
            }
        })
        contentContainer.addView(wallRow)
    }

    private var fontPreview: TextView? = null
    private var wallpaperPreview: ImageView? = null

    private fun updateFontPreview() {
        val preview = fontPreview ?: return
        val family = bs.get("ev_font_family") ?: ""
        val size = bs.get("ev_font_size") ?: ""

        if (family.isNotEmpty()) {
            preview.typeface = cssFontFamilyToTypeface(family)
        } else {
            preview.typeface = Typeface.DEFAULT
        }
        if (size.isNotEmpty()) {
            val sp = remToSp(size)
            if (sp > 0f) preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        } else {
            preview.textSize = 14f
        }
    }

    private fun remToSp(rem: String): Float {
        val num = rem.replace("rem", "").toFloatOrNull() ?: return 0f
        return num * 16f
    }

    private fun cssFontFamilyToTypeface(css: String): Typeface {
        val primary = css.split(",").firstOrNull()?.trim()?.removeSurrounding("'") ?: ""
        return when {
            primary.isEmpty() -> Typeface.DEFAULT
            primary.equals("Georgia", true) ||
            primary.equals("Times New Roman", true) ||
            primary.equals("Garamond", true) ||
            primary.equals("Palatino Linotype", true) -> Typeface.SERIF
            primary.equals("Courier New", true) ||
            primary.equals("Consolas", true) -> Typeface.MONOSPACE
            else -> try { Typeface.create(primary, Typeface.NORMAL) } catch (_: Exception) { Typeface.DEFAULT }
        }
    }

    // ========== Templates Tab ==========

    private fun buildTemplatesTab() {
        val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }

        buildSectionHeader("👁️  Custom Views")
        buildCustomViewsList(settings)

        buildSpacer()

        buildSectionHeader("📝  Pre-fill Templates")
        buildEntryTemplatesList(settings)

        buildSpacer()

        buildSectionHeader("📄  Report Templates")
        buildReportTemplatesList(settings)
    }

    // --- Custom Views ---

    private fun buildCustomViewsList(settings: JSONObject) {
        val views = settings.optJSONArray("customViews") ?: JSONArray()

        contentContainer.addView(Button(this).apply {
            text = "+ New View"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)
            ).apply { bottomMargin = dp(8) }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { launchCustomViewEditor(null) }
        })

        for (i in 0 until views.length()) {
            val view = views.optJSONObject(i) ?: continue
            val id = view.optString("id", "")
            val name = view.optString("name", "Untitled")
            val condCount = (view.optJSONArray("conditions") ?: JSONArray()).length()
            val pinned = view.optBoolean("pinToDashboard", false)
            val isDefault = view.optBoolean("defaultEntryView", false)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.entry_row_bg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = name
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            })
            val badges = mutableListOf<String>()
            if (condCount > 0) badges.add("$condCount condition${if (condCount > 1) "s" else ""}")
            if (pinned) badges.add("📌 Pinned")
            if (isDefault) badges.add("⭐ Default")
            if (badges.isNotEmpty()) {
                textCol.addView(TextView(this).apply {
                    text = badges.joinToString("  ·  ")
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 11f
                })
            }
            row.addView(textCol)

            row.addView(Button(this).apply {
                text = "✏️"
                textSize = 14f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { launchCustomViewEditor(view.optString("id")) }
            })

            row.addView(Button(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(ThemeManager.color(C.ERROR))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setMessage("Delete view \"$name\"?")
                        .setPositiveButton("Delete") { _, _ ->
                            val arr = settings.optJSONArray("customViews") ?: JSONArray()
                            val updated = JSONArray()
                            for (j in 0 until arr.length()) {
                                val v = arr.optJSONObject(j)
                                if (v != null && v.optString("id") != id) updated.put(v)
                            }
                            db.setSettings(JSONObject().apply { put("customViews", updated) }.toString())
                            showTab("templates")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            })

            contentContainer.addView(row)
        }
    }

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

    private fun opsForField(field: String): List<Pair<String, String>> {
        return when (field) {
            "date" -> dateOps
            "categories", "tags" -> arrayOps
            "weather" -> boolOps
            else -> textOps
        }
    }

    private val sortFields = listOf(
        "date" to "Date", "time" to "Time", "title" to "Title",
        "content" to "Content", "categories" to "Categories",
        "tags" to "Tags", "placeName" to "Place Name",
        "dtCreated" to "Created", "dtUpdated" to "Updated"
    )

    private fun editCustomViewDialog(settings: JSONObject, existing: JSONObject?) {
        val isNew = existing == null
        val view = existing?.let { JSONObject(it.toString()) } ?: JSONObject().apply {
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

        val conditions = view.optJSONArray("conditions") ?: JSONArray()
        val orderBy = view.optJSONArray("orderBy") ?: JSONArray()

        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        scroll.addView(container)

        // Name
        val nameInput = EditText(this).apply {
            setText(view.optString("name", ""))
            hint = "View name"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = linParams().apply { bottomMargin = dp(10) }
        }
        container.addView(nameInput)

        // Logic
        container.addView(makeLabelView("Condition Logic"))
        val logicSpinner = makeDialogSpinner(listOf("AND", "OR"),
            if (view.optString("logic") == "OR") 1 else 0)
        container.addView(logicSpinner)

        // Conditions
        container.addView(makeLabelView("Filter Conditions"))
        val condContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = linParams().apply { bottomMargin = dp(6) }
        }
        container.addView(condContainer)

        fun rebuildConditions() {
            condContainer.removeAllViews()
            for (ci in 0 until conditions.length()) {
                val cond = conditions.optJSONObject(ci) ?: continue
                val condRow = buildConditionRow(cond) { conditions.remove(ci); rebuildConditions() }
                condContainer.addView(condRow)
            }
        }
        rebuildConditions()

        container.addView(Button(this).apply {
            text = "+ Add Condition"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.ACCENT))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = linParams().apply { bottomMargin = dp(10) }
            setOnClickListener {
                conditions.put(JSONObject().apply {
                    put("field", "title")
                    put("operator", "contains")
                    put("value", "")
                    put("negate", false)
                })
                rebuildConditions()
            }
        })

        // Group By
        container.addView(makeLabelView("Group By"))
        val groupOpts = listOf("" to "None", "date" to "Date", "categories" to "Category",
            "tags" to "Tag", "placeName" to "Place Name", "weather" to "Weather")
        val groupSpinner = makeDialogSpinner(groupOpts.map { it.second },
            groupOpts.indexOfFirst { it.first == view.optString("groupBy", "") }.coerceAtLeast(0))
        container.addView(groupSpinner)

        // Order By
        container.addView(makeLabelView("Sort Order"))
        val orderContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = linParams().apply { bottomMargin = dp(6) }
        }
        container.addView(orderContainer)

        fun rebuildOrder() {
            orderContainer.removeAllViews()
            for (oi in 0 until orderBy.length()) {
                val ord = orderBy.optJSONObject(oi) ?: continue
                val ordRow = buildOrderRow(ord) { orderBy.remove(oi); rebuildOrder() }
                orderContainer.addView(ordRow)
            }
        }
        rebuildOrder()

        container.addView(Button(this).apply {
            text = "+ Add Sort Field"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.ACCENT))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = linParams().apply { bottomMargin = dp(10) }
            setOnClickListener {
                orderBy.put(JSONObject().apply {
                    put("field", "date")
                    put("direction", "desc")
                })
                rebuildOrder()
            }
        })

        // Display mode
        container.addView(makeLabelView("Display Mode"))
        val displayOpts = listOf("" to "Default", "card" to "Card", "list" to "List (table)")
        val displaySpinner = makeDialogSpinner(displayOpts.map { it.second },
            displayOpts.indexOfFirst { it.first == view.optString("displayMode", "") }.coerceAtLeast(0))
        container.addView(displaySpinner)

        // Checkboxes
        val pinCb = CheckBox(this).apply {
            text = "Pin to Dashboard"
            isChecked = view.optBoolean("pinToDashboard", false)
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
        }
        container.addView(pinCb)

        val defaultCb = CheckBox(this).apply {
            text = "Set as Default Entry View"
            isChecked = view.optBoolean("defaultEntryView", false)
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
        }
        container.addView(defaultCb)

        AlertDialog.Builder(this)
            .setTitle(if (isNew) "New Custom View" else "Edit View")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                view.put("name", name)
                view.put("logic", if (logicSpinner.selectedItemPosition == 1) "OR" else "AND")
                view.put("conditions", conditions)
                view.put("orderBy", orderBy)
                view.put("groupBy", groupOpts[groupSpinner.selectedItemPosition].first)
                view.put("displayMode", displayOpts[displaySpinner.selectedItemPosition].first)
                view.put("pinToDashboard", pinCb.isChecked)
                view.put("defaultEntryView", defaultCb.isChecked)

                val allViews = settings.optJSONArray("customViews") ?: JSONArray()
                if (isNew) {
                    allViews.put(view)
                } else {
                    for (j in 0 until allViews.length()) {
                        if (allViews.optJSONObject(j)?.optString("id") == view.optString("id")) {
                            allViews.put(j, view)
                            break
                        }
                    }
                }
                db.setSettings(JSONObject().apply { put("customViews", allViews) }.toString())
                showTab("templates")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildConditionRow(cond: JSONObject, onRemove: () -> Unit): LinearLayout {
        val field = cond.optString("field", "title")
        val op = cond.optString("operator", "contains")
        val value = cond.optString("value", "")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            layoutParams = linParams().apply { bottomMargin = dp(4) }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Field spinner
        val fieldSpinner = makeDialogSpinner(viewFields.map { it.second },
            viewFields.indexOfFirst { it.first == field }.coerceAtLeast(0))
        fieldSpinner.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        topRow.addView(fieldSpinner)

        // Remove button
        topRow.addView(Button(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(ThemeManager.color(C.ERROR))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setOnClickListener { onRemove() }
        })
        row.addView(topRow)

        // Op spinner
        val ops = opsForField(field)
        val opSpinner = makeDialogSpinner(ops.map { it.second },
            ops.indexOfFirst { it.first == op }.coerceAtLeast(0))
        opSpinner.layoutParams = linParams().apply { topMargin = dp(4) }
        row.addView(opSpinner)

        // Value input
        val needsValue = !op.contains("empty") && !op.contains("exists")
        if (needsValue) {
            val valInput = EditText(this).apply {
                setText(value)
                hint = "Value"
                textSize = 13f
                setTextColor(ThemeManager.color(C.TEXT))
                setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = linParams().apply { topMargin = dp(4) }
                isSingleLine = true
            }
            valInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) cond.put("value", valInput.text.toString())
            }
            row.addView(valInput)
        }

        // Update cond on spinner change
        fieldSpinner.onItemSelectedListener = spinnerListener { pos ->
            cond.put("field", viewFields[pos].first)
            val newOps = opsForField(viewFields[pos].first)
            cond.put("operator", newOps[0].first)
        }
        opSpinner.onItemSelectedListener = spinnerListener { pos ->
            val currentOps = opsForField(cond.optString("field", "title"))
            if (pos < currentOps.size) cond.put("operator", currentOps[pos].first)
        }

        return row
    }

    private fun buildOrderRow(ord: JSONObject, onRemove: () -> Unit): LinearLayout {
        val field = ord.optString("field", "date")
        val dir = ord.optString("direction", "desc")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = linParams().apply { bottomMargin = dp(4) }
        }

        val fieldSpinner = makeDialogSpinner(sortFields.map { it.second },
            sortFields.indexOfFirst { it.first == field }.coerceAtLeast(0))
        fieldSpinner.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        fieldSpinner.onItemSelectedListener = spinnerListener { pos ->
            ord.put("field", sortFields[pos].first)
        }
        row.addView(fieldSpinner)

        val dirOpts = listOf("desc" to "↓ Desc", "asc" to "↑ Asc")
        val dirSpinner = makeDialogSpinner(dirOpts.map { it.second },
            if (dir == "asc") 1 else 0)
        dirSpinner.layoutParams = LinearLayout.LayoutParams(dp(80), dp(36)).apply { marginEnd = dp(4) }
        dirSpinner.onItemSelectedListener = spinnerListener { pos ->
            ord.put("direction", dirOpts[pos].first)
        }
        row.addView(dirSpinner)

        row.addView(Button(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(ThemeManager.color(C.ERROR))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setOnClickListener { onRemove() }
        })

        return row
    }

    // --- Entry Templates ---

    private fun buildEntryTemplatesList(settings: JSONObject) {
        val templates = settings.optJSONArray("entryTemplates") ?: JSONArray()

        contentContainer.addView(Button(this).apply {
            text = "+ New Template"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)
            ).apply { bottomMargin = dp(8) }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { editEntryTemplateDialog(settings, null) }
        })

        for (i in 0 until templates.length()) {
            val tpl = templates.optJSONObject(i) ?: continue
            val id = tpl.optString("id", "")
            val name = tpl.optString("name", "Untitled")
            val desc = tpl.optString("description", "")

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.entry_row_bg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = name
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 14f
            })
            if (desc.isNotEmpty()) {
                textCol.addView(TextView(this).apply {
                    text = desc
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 11f
                })
            }
            row.addView(textCol)

            row.addView(Button(this).apply {
                text = "✏️"
                textSize = 14f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { editEntryTemplateDialog(settings, tpl) }
            })

            row.addView(Button(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(ThemeManager.color(C.ERROR))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setMessage("Delete template \"$name\"?")
                        .setPositiveButton("Delete") { _, _ ->
                            val arr = settings.optJSONArray("entryTemplates") ?: JSONArray()
                            val updated = JSONArray()
                            for (j in 0 until arr.length()) {
                                val t = arr.optJSONObject(j)
                                if (t != null && t.optString("id") != id) updated.put(t)
                            }
                            db.setSettings(JSONObject().apply { put("entryTemplates", updated) }.toString())
                            showTab("templates")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            })

            contentContainer.addView(row)
        }
    }

    private fun editEntryTemplateDialog(settings: JSONObject, existing: JSONObject?) {
        val isNew = existing == null
        val tpl = existing?.let { JSONObject(it.toString()) } ?: JSONObject().apply {
            put("id", "et_${generateId()}")
            put("name", "")
            put("description", "")
            put("autoDate", true)
            put("autoTime", false)
            put("title", "")
            put("content", "")
            put("categories", JSONArray())
            put("tags", JSONArray())
        }

        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        scroll.addView(container)

        val nameInput = makeDialogInput("Template name", tpl.optString("name"))
        container.addView(nameInput)
        val descInput = makeDialogInput("Description (optional)", tpl.optString("description"))
        container.addView(descInput)

        val autoDateCb = CheckBox(this).apply {
            text = "Auto-fill today's date"
            isChecked = tpl.optBoolean("autoDate", true)
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
        }
        container.addView(autoDateCb)

        val autoTimeCb = CheckBox(this).apply {
            text = "Auto-fill current time"
            isChecked = tpl.optBoolean("autoTime", false)
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
        }
        container.addView(autoTimeCb)

        val titleInput = makeDialogInput("Default title", tpl.optString("title"))
        container.addView(titleInput)

        val contentInput = makeDialogInput("Default content", tpl.optString("content"), multiLine = true)
        container.addView(contentInput)

        val tagsInput = makeDialogInput("Tags (comma-separated)",
            jsonArrayToStr(tpl.optJSONArray("tags")))
        container.addView(tagsInput)

        AlertDialog.Builder(this)
            .setTitle(if (isNew) "New Entry Template" else "Edit Template")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                tpl.put("name", name)
                tpl.put("description", descInput.text.toString().trim())
                tpl.put("autoDate", autoDateCb.isChecked)
                tpl.put("autoTime", autoTimeCb.isChecked)
                tpl.put("title", titleInput.text.toString())
                tpl.put("content", contentInput.text.toString())
                val tagStr = tagsInput.text.toString()
                val tagArr = JSONArray()
                tagStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tagArr.put(it) }
                tpl.put("tags", tagArr)

                val allTpls = settings.optJSONArray("entryTemplates") ?: JSONArray()
                if (isNew) {
                    allTpls.put(tpl)
                } else {
                    for (j in 0 until allTpls.length()) {
                        if (allTpls.optJSONObject(j)?.optString("id") == tpl.optString("id")) {
                            allTpls.put(j, tpl)
                            break
                        }
                    }
                }
                db.setSettings(JSONObject().apply { put("entryTemplates", allTpls) }.toString())
                showTab("templates")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Report Templates ---

    private val defaultReportHtml = """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title><%TITLE%></title>
    <style>
        body { font-family: sans-serif; max-width: 800px; margin: 2rem auto; padding: 0 1rem; }
        h1 { color: #333; }
        .meta { color: #666; margin-bottom: 1rem; }
        .content { line-height: 1.6; }
    </style>
</head>
<body>
    <h1><%TITLE%></h1>
    <div class="meta">
        <div>Date: <%DATE%> <%TIME%></div>
        <div>Categories: <%CATEGORIES%></div>
    </div>
    <div class="content"><%RICH_CONTENT%></div>
</body>
</html>"""

    private fun buildReportTemplatesList(settings: JSONObject) {
        val templates = settings.optJSONArray("reportTemplates") ?: JSONArray()

        contentContainer.addView(Button(this).apply {
            text = "+ New Report Template"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)
            ).apply { bottomMargin = dp(8) }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { editReportTemplateDialog(settings, null) }
        })

        // Available tags info
        contentContainer.addView(TextView(this).apply {
            text = "Tags: <%TITLE%> <%DATE%> <%TIME%> <%CONTENT%> <%RICH_CONTENT%> <%CATEGORIES%> <%TAGS%> <%WEATHER%> <%PLACES%> <%ID%> <%DT_CREATED%> <%DT_UPDATED%>"
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 10f
            setPadding(dp(4), dp(0), dp(4), dp(8))
        })

        for (i in 0 until templates.length()) {
            val tpl = templates.optJSONObject(i) ?: continue
            val id = tpl.optString("id", "")
            val name = tpl.optString("name", "Untitled")

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.entry_row_bg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            row.addView(TextView(this).apply {
                text = name
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            row.addView(Button(this).apply {
                text = "✏️"
                textSize = 14f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { editReportTemplateDialog(settings, tpl) }
            })

            row.addView(Button(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(ThemeManager.color(C.ERROR))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setMessage("Delete report template \"$name\"?")
                        .setPositiveButton("Delete") { _, _ ->
                            val arr = settings.optJSONArray("reportTemplates") ?: JSONArray()
                            val updated = JSONArray()
                            for (j in 0 until arr.length()) {
                                val t = arr.optJSONObject(j)
                                if (t != null && t.optString("id") != id) updated.put(t)
                            }
                            db.setSettings(JSONObject().apply { put("reportTemplates", updated) }.toString())
                            showTab("templates")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            })

            contentContainer.addView(row)
        }
    }

    private fun editReportTemplateDialog(settings: JSONObject, existing: JSONObject?) {
        val isNew = existing == null
        val tpl = existing?.let { JSONObject(it.toString()) } ?: JSONObject().apply {
            put("id", "tpl_${generateId()}")
            put("name", "")
            put("html", defaultReportHtml)
        }

        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        scroll.addView(container)

        val nameInput = makeDialogInput("Template name", tpl.optString("name"))
        container.addView(nameInput)

        container.addView(makeLabelView("HTML Template"))
        val htmlInput = EditText(this).apply {
            setText(tpl.optString("html"))
            textSize = 12f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 12
            gravity = Gravity.TOP or Gravity.START
            typeface = Typeface.MONOSPACE
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        }
        container.addView(htmlInput)

        AlertDialog.Builder(this)
            .setTitle(if (isNew) "New Report Template" else "Edit Report Template")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                tpl.put("name", name)
                tpl.put("html", htmlInput.text.toString())

                val allTpls = settings.optJSONArray("reportTemplates") ?: JSONArray()
                if (isNew) {
                    allTpls.put(tpl)
                } else {
                    for (j in 0 until allTpls.length()) {
                        if (allTpls.optJSONObject(j)?.optString("id") == tpl.optString("id")) {
                            allTpls.put(j, tpl)
                            break
                        }
                    }
                }
                db.setSettings(JSONObject().apply { put("reportTemplates", allTpls) }.toString())
                showTab("templates")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== Widgets Tab ==========

    private val widgetFilterFields = listOf(
        "date" to "Date", "time" to "Time", "title" to "Title",
        "content" to "Content", "categories" to "Categories",
        "tags" to "Tags", "people" to "People", "placeName" to "Place Name"
    )

    private val widgetDateOps = listOf(
        "after" to "after", "before" to "before",
        "equals" to "equals", "between" to "between"
    )
    private val widgetTextOps = listOf(
        "contains" to "contains", "equals" to "equals",
        "starts with" to "starts with", "ends with" to "ends with",
        "is empty" to "is empty", "is not empty" to "is not empty"
    )
    private val widgetArrayOps = listOf(
        "includes" to "includes", "not includes" to "not includes",
        "is empty" to "is empty", "is not empty" to "is not empty"
    )

    private fun widgetOpsForField(field: String): List<Pair<String, String>> {
        return when (field) {
            "date" -> widgetDateOps
            "categories", "tags", "people" -> widgetArrayOps
            else -> widgetTextOps
        }
    }

    private val widgetAggFuncs = listOf("Count", "Sum", "Max", "Min", "Average")
    private val widgetAggFields = listOf(
        "entries" to "Entries", "tags" to "Tags", "categories" to "Categories",
        "people" to "People", "placeName" to "Place Name", "title" to "Title"
    )

    private fun buildWidgetsTab() {
        buildSectionHeader("📦  Widgets")

        contentContainer.addView(Button(this).apply {
            text = "+ New Widget"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)
            ).apply { bottomMargin = dp(8) }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { launchWidgetEditor(null) }
        })

        val widgetsJson = db.getWidgets()
        val widgets = try { JSONArray(widgetsJson) } catch (_: Exception) { JSONArray() }

        for (i in 0 until widgets.length()) {
            val w = widgets.optJSONObject(i) ?: continue
            val id = w.optString("id", "")
            val name = w.optString("name", "Untitled")
            val desc = w.optString("description", "")
            val enabled = w.optBoolean("enabledInDashboard", true)
            val filterCount = (w.optJSONArray("filters") ?: JSONArray()).length()
            val funcCount = (w.optJSONArray("functions") ?: JSONArray()).length()
            val bgColor = w.optString("bgColor", "")

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.entry_row_bg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            // Color indicator
            if (bgColor.isNotEmpty()) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(6), dp(36)).apply { marginEnd = dp(8) }
                    try { setBackgroundColor(Color.parseColor(bgColor)) } catch (_: Exception) {}
                })
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = name
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            })
            val info = mutableListOf<String>()
            if (desc.isNotEmpty()) info.add(desc)
            if (filterCount > 0) info.add("$filterCount filter${if (filterCount > 1) "s" else ""}")
            if (funcCount > 0) info.add("$funcCount function${if (funcCount > 1) "s" else ""}")
            if (!enabled) info.add("disabled")
            if (info.isNotEmpty()) {
                textCol.addView(TextView(this).apply {
                    text = info.joinToString("  ·  ")
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 11f
                })
            }
            row.addView(textCol)

            row.addView(Button(this).apply {
                text = "✏️"
                textSize = 14f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { launchWidgetEditor(w.optString("id")) }
            })

            row.addView(Button(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(ThemeManager.color(C.ERROR))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener {
                    AlertDialog.Builder(this@SettingsActivity)
                        .setMessage("Delete widget \"$name\"?")
                        .setPositiveButton("Delete") { _, _ ->
                            db.deleteWidget(id)
                            showTab("widgets")
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            })

            contentContainer.addView(row)
        }
    }

    private fun launchCustomViewEditor(viewId: String?) {
        CustomViewEditorActivity.databaseService = db
        CustomViewEditorActivity.bootstrapService = bs
        CustomViewEditorActivity.pendingViewId = viewId
        startActivity(Intent(this, CustomViewEditorActivity::class.java))
    }

    private fun launchWidgetEditor(widgetId: String?) {
        WidgetEditorActivity.databaseService = db
        WidgetEditorActivity.bootstrapService = bs
        WidgetEditorActivity.pendingWidgetId = widgetId
        startActivity(Intent(this, WidgetEditorActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (activeTab == "widgets" || activeTab == "templates") showTab(activeTab)
    }

    private fun editWidgetDialog(existing: JSONObject?) {
        val isNew = existing == null
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            .format(java.util.Date())
        val w = existing?.let { JSONObject(it.toString()) } ?: JSONObject().apply {
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

        val filters = w.optJSONArray("filters") ?: JSONArray()
        val functions = w.optJSONArray("functions") ?: JSONArray()

        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        scroll.addView(container)

        // === Header Info ===
        container.addView(makeLabelView("Header Info").apply {
            setTypeface(null, Typeface.BOLD)
            setTextColor(ThemeManager.color(C.ACCENT))
        })

        val nameInput = makeDialogInput("Widget name", w.optString("name"))
        container.addView(nameInput)

        val descInput = makeDialogInput("Description (optional)", w.optString("description"))
        container.addView(descInput)

        val colorInput = makeDialogInput("Background color (e.g. #4a90d9)", w.optString("bgColor"))
        container.addView(colorInput)

        val enabledCb = CheckBox(this).apply {
            text = "Show in Dashboard"
            isChecked = w.optBoolean("enabledInDashboard", true)
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
            layoutParams = linParams().apply { bottomMargin = dp(10) }
        }
        container.addView(enabledCb)

        // === Filters ===
        container.addView(makeLabelView("Filters").apply {
            setTypeface(null, Typeface.BOLD)
            setTextColor(ThemeManager.color(C.ACCENT))
        })

        val filterContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = linParams().apply { bottomMargin = dp(4) }
        }
        container.addView(filterContainer)

        fun rebuildFilters() {
            filterContainer.removeAllViews()
            for (fi in 0 until filters.length()) {
                val f = filters.optJSONObject(fi) ?: continue
                filterContainer.addView(buildWidgetFilterRow(f) { filters.remove(fi); rebuildFilters() })
            }
        }
        rebuildFilters()

        container.addView(Button(this).apply {
            text = "+ Add Filter"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.ACCENT))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = linParams().apply { bottomMargin = dp(10) }
            setOnClickListener {
                filters.put(JSONObject().apply {
                    put("field", "title")
                    put("op", "contains")
                    put("value", "")
                })
                rebuildFilters()
            }
        })

        // === Functions ===
        container.addView(makeLabelView("Aggregate Functions").apply {
            setTypeface(null, Typeface.BOLD)
            setTextColor(ThemeManager.color(C.ACCENT))
        })

        val funcContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = linParams().apply { bottomMargin = dp(4) }
        }
        container.addView(funcContainer)

        fun rebuildFuncs() {
            funcContainer.removeAllViews()
            for (fi in 0 until functions.length()) {
                val fn = functions.optJSONObject(fi) ?: continue
                funcContainer.addView(buildWidgetFuncRow(fn) { functions.remove(fi); rebuildFuncs() })
            }
        }
        rebuildFuncs()

        container.addView(Button(this).apply {
            text = "+ Add Function"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.ACCENT))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = linParams().apply { bottomMargin = dp(10) }
            setOnClickListener {
                functions.put(JSONObject().apply {
                    put("func", "Count")
                    put("field", "entries")
                    put("value", "")
                    put("prefix", "")
                    put("postfix", "")
                })
                rebuildFuncs()
            }
        })

        AlertDialog.Builder(this)
            .setTitle(if (isNew) "New Widget" else "Edit Widget")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                w.put("name", name)
                w.put("description", descInput.text.toString().trim())
                w.put("bgColor", colorInput.text.toString().trim())
                w.put("enabledInDashboard", enabledCb.isChecked)
                w.put("filters", filters)
                w.put("functions", functions)
                db.saveWidget(w.toString())
                showTab("widgets")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildWidgetFilterRow(filter: JSONObject, onRemove: () -> Unit): LinearLayout {
        val field = filter.optString("field", "title")
        val op = filter.optString("op", "contains")
        val value = filter.optString("value", "")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            layoutParams = linParams().apply { bottomMargin = dp(4) }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val fieldSpinner = makeDialogSpinner(widgetFilterFields.map { it.second },
            widgetFilterFields.indexOfFirst { it.first == field }.coerceAtLeast(0))
        fieldSpinner.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        topRow.addView(fieldSpinner)

        topRow.addView(Button(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(ThemeManager.color(C.ERROR))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setOnClickListener { onRemove() }
        })
        row.addView(topRow)

        val ops = widgetOpsForField(field)
        val opSpinner = makeDialogSpinner(ops.map { it.second },
            ops.indexOfFirst { it.first == op }.coerceAtLeast(0))
        opSpinner.layoutParams = linParams().apply { topMargin = dp(4) }
        row.addView(opSpinner)

        val needsValue = !op.contains("empty")
        if (needsValue) {
            val valInput = EditText(this).apply {
                setText(value)
                hint = "Value"
                textSize = 13f
                setTextColor(ThemeManager.color(C.TEXT))
                setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                layoutParams = linParams().apply { topMargin = dp(4) }
                isSingleLine = true
            }
            valInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) filter.put("value", valInput.text.toString())
            }
            row.addView(valInput)
        }

        fieldSpinner.onItemSelectedListener = spinnerListener { pos ->
            filter.put("field", widgetFilterFields[pos].first)
            val newOps = widgetOpsForField(widgetFilterFields[pos].first)
            filter.put("op", newOps[0].first)
        }
        opSpinner.onItemSelectedListener = spinnerListener { pos ->
            val currentOps = widgetOpsForField(filter.optString("field", "title"))
            if (pos < currentOps.size) filter.put("op", currentOps[pos].first)
        }

        return row
    }

    private fun buildWidgetFuncRow(fn: JSONObject, onRemove: () -> Unit): LinearLayout {
        val func = fn.optString("func", "Count")
        val field = fn.optString("field", "entries")
        val value = fn.optString("value", "")
        val prefix = fn.optString("prefix", "")
        val postfix = fn.optString("postfix", "")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            layoutParams = linParams().apply { bottomMargin = dp(4) }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val funcSpinner = makeDialogSpinner(widgetAggFuncs,
            widgetAggFuncs.indexOf(func).coerceAtLeast(0))
        funcSpinner.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        topRow.addView(funcSpinner)

        val fieldSpinner = makeDialogSpinner(widgetAggFields.map { it.second },
            widgetAggFields.indexOfFirst { it.first == field }.coerceAtLeast(0))
        fieldSpinner.layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
        topRow.addView(fieldSpinner)

        topRow.addView(Button(this).apply {
            text = "✕"
            textSize = 12f
            setTextColor(ThemeManager.color(C.ERROR))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setOnClickListener { onRemove() }
        })
        row.addView(topRow)

        // Value filter (optional)
        val valInput = EditText(this).apply {
            setText(value)
            hint = "Match value (optional)"
            textSize = 13f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = linParams().apply { topMargin = dp(4) }
            isSingleLine = true
        }
        valInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) fn.put("value", valInput.text.toString())
        }
        row.addView(valInput)

        // Prefix / Postfix
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
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
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
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
            isSingleLine = true
        }
        postfixInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) fn.put("postfix", postfixInput.text.toString())
        }
        labelRow.addView(postfixInput)
        row.addView(labelRow)

        funcSpinner.onItemSelectedListener = spinnerListener { pos ->
            fn.put("func", widgetAggFuncs[pos])
        }
        fieldSpinner.onItemSelectedListener = spinnerListener { pos ->
            fn.put("field", widgetAggFields[pos].first)
        }

        return row
    }

    // ========== Metadata Tab ==========

    private fun buildMetadataTab() {
        val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }

        // Categories
        buildSectionHeader("📁  Categories")
        buildCategoriesList(settings)

        buildSpacer()

        // Tags
        buildSectionHeader("🏷️  Tags")
        buildTagsList(settings)

        buildSpacer()

        // People
        buildSectionHeader("👤  People")
        buildPeopleList()
    }

    private fun buildCategoriesList(settings: JSONObject) {
        val catsJson = db.getCategoriesWithDesc()
        val cats = try { JSONArray(catsJson) } catch (_: Exception) { JSONArray() }
        val categoryColors = settings.optJSONObject("categoryColors") ?: JSONObject()

        // Add new category
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val catInput = EditText(this).apply {
            hint = "New category..."
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
            inputType = InputType.TYPE_CLASS_TEXT
        }
        addRow.addView(catInput)
        addRow.addView(Button(this).apply {
            text = "Add"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(40))
            setOnClickListener {
                val name = catInput.text.toString().trim()
                if (name.isEmpty()) return@setOnClickListener
                val existing = try { JSONArray(db.getCategories()) } catch (_: Exception) { JSONArray() }
                val list = mutableListOf<String>()
                for (i in 0 until existing.length()) list.add(existing.getString(i))
                if (list.contains(name)) {
                    Toast.makeText(this@SettingsActivity, "Category already exists", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                list.add(name)
                db.setCategories(JSONArray(list).toString())
                catInput.text.clear()
                showTab("cattags")
            }
        })
        contentContainer.addView(addRow)

        // Use category colors toggle
        val useCatColor = settings.optBoolean("useCategoryColor", false)
        buildToggle("Use category colors", useCatColor) { checked ->
            db.setSettings(JSONObject().apply { put("useCategoryColor", checked) }.toString())
        }

        // List
        for (i in 0 until cats.length()) {
            val cat = cats.optJSONObject(i) ?: continue
            val name = cat.optString("name", "")
            val desc = cat.optString("description", "")
            val color = categoryColors.optString(name, "")

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.entry_row_bg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            // Color swatch
            if (color.isNotEmpty()) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) }
                    try { setBackgroundColor(Color.parseColor(color)) } catch (_: Exception) {}
                })
            }

            // Icon placeholder
            val icon = loadIconSync("category", name)
            if (icon != null) {
                row.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(icon)
                })
            }

            // Name + description
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = name
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 14f
            })
            if (desc.isNotEmpty()) {
                textCol.addView(TextView(this).apply {
                    text = desc
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 11f
                })
            }
            row.addView(textCol)

            // Edit button
            row.addView(Button(this).apply {
                text = "✏️"
                textSize = 14f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { editCategoryDialog(name, desc) }
            })

            // Delete button
            row.addView(Button(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(ThemeManager.color(C.ERROR))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { confirmDeleteCategory(name) }
            })

            contentContainer.addView(row)
        }
    }

    private fun editCategoryDialog(name: String, currentDesc: String) {
        val input = EditText(this).apply {
            setText(currentDesc)
            hint = "Description"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle("Edit: $name")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                db.setCategoryDescription(name, input.text.toString().trim())
                showTab("cattags")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteCategory(name: String) {
        AlertDialog.Builder(this)
            .setMessage("Delete category \"$name\"?")
            .setPositiveButton("Delete") { _, _ ->
                val existing = try { JSONArray(db.getCategories()) } catch (_: Exception) { JSONArray() }
                val list = mutableListOf<String>()
                for (i in 0 until existing.length()) {
                    val c = existing.getString(i)
                    if (c != name) list.add(c)
                }
                db.setCategories(JSONArray(list).toString())
                db.removeIcon("category", name)
                db.removeIcon("category_hd", name)
                showTab("cattags")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildTagsList(settings: JSONObject) {
        val tagsJson = db.getAllTags()
        val tags = try { JSONArray(tagsJson) } catch (_: Exception) { JSONArray() }
        val tagDescs = try { JSONObject(db.getTagDescriptions()) } catch (_: Exception) { JSONObject() }
        val tagColors = settings.optJSONObject("tagColors") ?: JSONObject()

        val useTagColor = settings.optBoolean("useTagColor", false)
        buildToggle("Use tag colors", useTagColor) { checked ->
            db.setSettings(JSONObject().apply { put("useTagColor", checked) }.toString())
        }

        for (i in 0 until tags.length()) {
            val name = tags.optString(i, "")
            if (name.isEmpty()) continue
            val desc = tagDescs.optString(name, "")
            val color = tagColors.optString(name, "")

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.entry_row_bg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            if (color.isNotEmpty()) {
                row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = dp(8) }
                    try { setBackgroundColor(Color.parseColor(color)) } catch (_: Exception) {}
                })
            }

            val icon = loadIconSync("tag", name)
            if (icon != null) {
                row.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(icon)
                })
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = name
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 14f
            })
            if (desc.isNotEmpty()) {
                textCol.addView(TextView(this).apply {
                    text = desc
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 11f
                })
            }
            row.addView(textCol)

            row.addView(Button(this).apply {
                text = "✏️"
                textSize = 14f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { editTagDialog(name, desc) }
            })

            contentContainer.addView(row)
        }
    }

    private fun editTagDialog(name: String, currentDesc: String) {
        val input = EditText(this).apply {
            setText(currentDesc)
            hint = "Description"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        AlertDialog.Builder(this)
            .setTitle("Edit Tag: $name")
            .setView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(4))
                addView(input)
            })
            .setPositiveButton("Save") { _, _ ->
                db.setTagDescription(name, input.text.toString().trim())
                showTab("cattags")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildPeopleList() {
        val peopleJson = db.getPeople()
        val people = try { JSONArray(peopleJson) } catch (_: Exception) { JSONArray() }

        // Add new person button
        contentContainer.addView(Button(this).apply {
            text = "+ New Person"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)
            ).apply { bottomMargin = dp(8) }
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { editPersonDialog("", "", "") }
        })

        for (i in 0 until people.length()) {
            val person = people.optJSONObject(i) ?: continue
            val firstName = person.optString("firstName", "")
            val lastName = person.optString("lastName", "")
            val desc = person.optString("description", "")
            val fullName = "$firstName $lastName".trim()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.entry_row_bg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            val icon = loadIconSync("person", fullName)
            if (icon != null) {
                row.addView(ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(icon)
                })
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = fullName
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 14f
            })
            if (desc.isNotEmpty()) {
                textCol.addView(TextView(this).apply {
                    text = desc
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    textSize = 11f
                })
            }
            row.addView(textCol)

            row.addView(Button(this).apply {
                text = "✏️"
                textSize = 14f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { editPersonDialog(firstName, lastName, desc) }
            })

            row.addView(Button(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(ThemeManager.color(C.ERROR))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setOnClickListener { confirmDeletePerson(firstName, lastName) }
            })

            contentContainer.addView(row)
        }
    }

    private fun editPersonDialog(origFirst: String, origLast: String, currentDesc: String) {
        val isNew = origFirst.isEmpty() && origLast.isEmpty()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        val firstInput = EditText(this).apply {
            setText(origFirst)
            hint = "First name"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val lastInput = EditText(this).apply {
            setText(origLast)
            hint = "Last name"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val descInput = EditText(this).apply {
            setText(currentDesc)
            hint = "Description"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        container.addView(firstInput)
        container.addView(lastInput)
        container.addView(descInput)

        AlertDialog.Builder(this)
            .setTitle(if (isNew) "New Person" else "Edit Person")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val first = firstInput.text.toString().trim()
                val last = lastInput.text.toString().trim()
                val desc = descInput.text.toString().trim()
                if (first.isEmpty()) {
                    Toast.makeText(this, "First name required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (isNew) {
                    db.addPerson(first, last, desc)
                } else {
                    db.updatePerson(origFirst, origLast, first, last, desc)
                }
                showTab("cattags")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeletePerson(firstName: String, lastName: String) {
        AlertDialog.Builder(this)
            .setMessage("Delete \"$firstName $lastName\"?")
            .setPositiveButton("Delete") { _, _ ->
                db.deletePerson(firstName, lastName)
                db.removeIcon("person", "$firstName $lastName".trim())
                db.removeIcon("person_hd", "$firstName $lastName".trim())
                showTab("cattags")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== Data Tab ==========

    private fun buildDataTab() {
        buildSectionHeader("💾  Data & Security")

        // Export
        buildLabel("Export")
        val exportOptions = listOf(
            "encrypted" to "Encrypted Backup (.sqlite.enc)",
            "csv" to "CSV Export",
            "metadata" to "Metadata (JSON)"
        )
        for ((key, label) in exportOptions) {
            contentContainer.addView(Button(this).apply {
                text = label
                textSize = 13f
                isAllCaps = false
                setTextColor(ThemeManager.color(C.TEXT))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_secondary)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
                ).apply { bottomMargin = dp(4) }
                setOnClickListener { handleExport(key) }
            })
        }

        buildSpacer()

        // Import
        buildLabel("Import")
        val importOptions = listOf(
            "data" to "Import Backup (.sqlite / .sqlite.enc)",
            "metadata" to "Import Metadata (JSON)"
        )
        for ((_, label) in importOptions) {
            contentContainer.addView(Button(this).apply {
                text = label
                textSize = 13f
                isAllCaps = false
                setTextColor(ThemeManager.color(C.TEXT))
                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_secondary)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
                ).apply { bottomMargin = dp(4) }
                setOnClickListener {
                    Toast.makeText(this@SettingsActivity, "Use the web settings for import", Toast.LENGTH_SHORT).show()
                }
            })
        }

        buildSpacer()

        // Import CSV
        buildLabel("Import CSV")
        val csvBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        csvBtnRow.addView(Button(this).apply {
            text = "Define Mapping"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.TEXT))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_secondary)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(4) }
            setOnClickListener {
                CsvMappingActivity.databaseService = db
                startActivity(Intent(this@SettingsActivity, CsvMappingActivity::class.java))
            }
        })
        csvBtnRow.addView(Button(this).apply {
            text = "Start Import"
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            setOnClickListener { pickCsvFile() }
        })
        contentContainer.addView(csvBtnRow)

        // CSV import status area
        val csvStatusText = TextView(this).apply {
            tag = "csv_import_status"
            textSize = 12f
            visibility = View.GONE
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        contentContainer.addView(csvStatusText)

        buildSpacer()

        // Change Password
        buildSectionHeader("🔑  Change Password")
        val currentPwInput = EditText(this).apply {
            hint = "Current password"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val newPwInput = EditText(this).apply {
            hint = "New password"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val confirmPwInput = EditText(this).apply {
            hint = "Confirm new password"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val pwMsg = TextView(this).apply {
            textSize = 13f
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        contentContainer.addView(currentPwInput)
        contentContainer.addView(newPwInput)
        contentContainer.addView(confirmPwInput)
        contentContainer.addView(pwMsg)

        contentContainer.addView(Button(this).apply {
            text = "Change Password"
            textSize = 14f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)
            ).apply { bottomMargin = dp(12) }
            setOnClickListener {
                val current = currentPwInput.text.toString()
                val newPw = newPwInput.text.toString()
                val confirm = confirmPwInput.text.toString()

                if (current.isEmpty() || newPw.isEmpty() || confirm.isEmpty()) {
                    pwMsg.text = "All fields are required"
                    pwMsg.setTextColor(ThemeManager.color(C.ERROR))
                    pwMsg.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (newPw != confirm) {
                    pwMsg.text = "New passwords do not match"
                    pwMsg.setTextColor(ThemeManager.color(C.ERROR))
                    pwMsg.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (!crypto.verifyPassword(current, journalId)) {
                    pwMsg.text = "Current password is incorrect"
                    pwMsg.setTextColor(ThemeManager.color(C.ERROR))
                    pwMsg.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                crypto.setupPassword(newPw, journalId)
                pwMsg.text = "Password changed successfully"
                pwMsg.setTextColor(ThemeManager.color(C.ACCENT))
                pwMsg.visibility = View.VISIBLE
                currentPwInput.text.clear()
                newPwInput.text.clear()
                confirmPwInput.text.clear()
            }
        })
    }

    private fun handleExport(type: String) {
        when (type) {
            "encrypted" -> {
                val json = db.exportJSON()
                val bytes = json.toByteArray(Charsets.UTF_8)
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                ServiceProvider.saveFileToDownloads("journal_backup.sqlite.enc", base64, "application/octet-stream")
                Toast.makeText(this, "Encrypted backup exported", Toast.LENGTH_SHORT).show()
            }
            "csv" -> {
                val csv = buildCsvExport()
                val base64 = Base64.encodeToString(csv.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                ServiceProvider.saveFileToDownloads("journal_export.csv", base64, "text/csv")
                Toast.makeText(this, "CSV exported", Toast.LENGTH_SHORT).show()
            }
            "metadata" -> {
                val metadata = buildMetadataExport()
                val base64 = Base64.encodeToString(metadata.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val filename = "${journalId}_metadata.json"
                ServiceProvider.saveFileToDownloads(filename, base64, "application/json")
                Toast.makeText(this, "Metadata exported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildCsvExport(): String {
        val entriesJson = db.getEntries()
        val entries = try { JSONArray(entriesJson) } catch (_: Exception) { JSONArray() }
        val sb = StringBuilder()
        sb.appendLine("Date,Time,Title,Content,Categories,Tags,Place Name,Created,Updated")
        for (i in 0 until entries.length()) {
            val e = entries.optJSONObject(i) ?: continue
            sb.appendLine(listOf(
                csvEscape(e.optString("date")),
                csvEscape(e.optString("time")),
                csvEscape(e.optString("title")),
                csvEscape(e.optString("content")),
                csvEscape(jsonArrayToStr(e.optJSONArray("categories"))),
                csvEscape(jsonArrayToStr(e.optJSONArray("tags"))),
                csvEscape(e.optString("placeName")),
                csvEscape(e.optString("dtCreated")),
                csvEscape(e.optString("dtUpdated"))
            ).joinToString(","))
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }

    private fun buildMetadataExport(): String {
        val result = JSONObject()
        result.put("categories", JSONArray(db.getCategories()))
        result.put("settings", JSONObject(db.getSettings()))
        result.put("tags", JSONArray(db.getAllTags()))
        result.put("people", JSONArray(db.getPeople()))
        result.put("icons", JSONArray(db.getAllIcons()))
        result.put("widgets", JSONArray(db.getWidgets()))
        return result.toString(2)
    }

    // ========== Biometric ==========

    private fun isBiometricSupported(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun hasBiometricCredential(): Boolean {
        val prefs = getSharedPreferences("biometric_prefs", MODE_PRIVATE)
        return prefs.contains("cred_$journalId")
    }

    private fun toggleBiometric(enable: Boolean) {
        if (enable) {
            Toast.makeText(this, "Enable biometric from the web settings (requires password entry)", Toast.LENGTH_LONG).show()
        } else {
            getSharedPreferences("biometric_prefs", MODE_PRIVATE)
                .edit().remove("cred_$journalId").apply()
            showTab("prefs")
        }
    }

    // ========== Weather Search ==========

    private fun buildWeatherSearch() {
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val searchInput = EditText(this).apply {
            hint = "Search city..."
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.search_bg)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(6) }
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        val resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        searchRow.addView(searchInput)
        searchRow.addView(Button(this).apply {
            text = "🔍"
            textSize = 14f
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(40))
            setOnClickListener {
                val query = searchInput.text.toString().trim()
                if (query.length < 2) return@setOnClickListener
                resultsContainer.removeAllViews()
                thread {
                    val json = try { weather.searchCity(query) } catch (_: Exception) { "[]" }
                    val results = try { JSONArray(json) } catch (_: Exception) { JSONArray() }
                    runOnUiThread {
                        for (j in 0 until results.length()) {
                            val city = results.optJSONObject(j) ?: continue
                            val name = city.optString("name", "")
                            val country = city.optString("country", "")
                            val admin = city.optString("admin1", "")
                            val lat = city.optDouble("latitude", 0.0)
                            val lng = city.optDouble("longitude", 0.0)
                            val display = listOf(name, admin, country).filter { it.isNotEmpty() }.joinToString(", ")

                            resultsContainer.addView(Button(this@SettingsActivity).apply {
                                text = display
                                textSize = 13f
                                isAllCaps = false
                                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                                setTextColor(ThemeManager.color(C.TEXT))
                                background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.entry_row_bg)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, dp(40)
                                ).apply { bottomMargin = dp(2) }
                                setPadding(dp(12), dp(4), dp(12), dp(4))
                                setOnClickListener {
                                    bs.set("weather_location_name", display)
                                    bs.set("weather_location_lat", lat.toString())
                                    bs.set("weather_location_lng", lng.toString())
                                    showTab("prefs")
                                }
                            })
                        }
                        if (results.length() == 0) {
                            resultsContainer.addView(TextView(this@SettingsActivity).apply {
                                text = "No results found"
                                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                                textSize = 13f
                            })
                        }
                    }
                }
            }
        })
        contentContainer.addView(searchRow)
        contentContainer.addView(resultsContainer)
    }

    // ========== CSV Import ==========

    @Suppress("DEPRECATION")
    private fun pickCsvFile() {
        val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
        val mapping = settings.optJSONArray("csvMapping")
        if (mapping == null || mapping.length() == 0) {
            Toast.makeText(this, "Please define a CSV mapping first", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "text/comma-separated-values", "application/csv"))
        }
        startActivityForResult(intent, CSV_PICK)
    }

    private fun handleCsvImport(uri: Uri) {
        val statusView = contentContainer.findViewWithTag<TextView>("csv_import_status") ?: return
        statusView.visibility = View.VISIBLE
        statusView.setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
        statusView.text = "Reading CSV file..."

        thread {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                if (text.isBlank()) {
                    runOnUiThread {
                        statusView.text = "CSV file is empty"
                        statusView.setTextColor(ThemeManager.color(C.ERROR))
                    }
                    return@thread
                }

                val allRows = parseCSV(text)
                if (allRows.size < 2) {
                    runOnUiThread {
                        statusView.text = "CSV must have a header row and at least one data row"
                        statusView.setTextColor(ThemeManager.color(C.ERROR))
                    }
                    return@thread
                }

                val csvHeaders = allRows[0]
                val dataRows = allRows.subList(1, allRows.size)

                val settings = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
                val mapping = settings.optJSONArray("csvMapping") ?: JSONArray()
                val separator = settings.optString("csvSeparator", ",")
                val tagsSpaceSep = settings.optBoolean("csvTagsSpaceSep", false)

                // Build column index map: csvFieldName -> (colIndex, entryField, misc)
                val colMap = mutableListOf<Triple<Int, String, String>>()
                for (i in 0 until mapping.length()) {
                    val m = mapping.optJSONObject(i) ?: continue
                    val csvField = m.optString("csvField", "")
                    val entryField = m.optString("entryField", "")
                    if (csvField.isEmpty() || entryField.isEmpty()) continue

                    val colIdx = csvHeaders.indexOfFirst { it.equals(csvField, ignoreCase = true) }
                    if (colIdx < 0) continue
                    colMap.add(Triple(colIdx, entryField, m.optString("misc", "")))
                }

                if (colMap.isEmpty()) {
                    runOnUiThread {
                        statusView.text = "No CSV columns matched the mapping. Check column names."
                        statusView.setTextColor(ThemeManager.color(C.ERROR))
                    }
                    return@thread
                }

                // Build existing entry keys for duplicate detection
                val existingEntries = try { JSONArray(db.getEntries()) } catch (_: Exception) { JSONArray() }
                val existingKeys = mutableSetOf<String>()
                for (i in 0 until existingEntries.length()) {
                    val e = existingEntries.optJSONObject(i) ?: continue
                    val d = e.optString("date", "")
                    val t = e.optString("title", "").lowercase().trim()
                    if (d.isNotEmpty() && t.isNotEmpty()) existingKeys.add("$d|$t")
                }

                // Track new metadata
                val existingCats = try {
                    val arr = JSONArray(db.getCategories())
                    (0 until arr.length()).map { arr.optString(it, "").lowercase() }.toMutableSet()
                } catch (_: Exception) { mutableSetOf() }
                val existingTags = try {
                    val s = JSONObject(db.getSettings())
                    val arr = s.optJSONArray("tags") ?: JSONArray()
                    (0 until arr.length()).map { arr.optString(it, "").lowercase() }.toMutableSet()
                } catch (_: Exception) { mutableSetOf() }
                val existingPeople = try {
                    val arr = JSONArray(db.getPeople())
                    (0 until arr.length()).map {
                        val p = arr.optJSONObject(it) ?: return@map ""
                        "${p.optString("firstName", "")} ${p.optString("lastName", "")}".trim().lowercase()
                    }.toMutableSet()
                } catch (_: Exception) { mutableSetOf() }

                val newCats = mutableSetOf<String>()
                val newTags = mutableSetOf<String>()
                val newPeople = mutableListOf<Pair<String, String>>()

                val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .format(java.util.Date())
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

                var imported = 0
                var skipped = 0
                var duplicates = 0
                val total = dataRows.size

                runOnUiThread { statusView.text = "Importing 0 / $total..." }

                for ((rowIdx, row) in dataRows.withIndex()) {
                    val entry = JSONObject().apply {
                        put("id", java.util.UUID.randomUUID().toString())
                        put("date", todayStr)
                        put("time", "")
                        put("title", "")
                        put("content", "")
                        put("richContent", "")
                        put("categories", JSONArray())
                        put("tags", JSONArray())
                        put("people", JSONArray())
                        put("placeName", "")
                        put("locations", JSONArray())
                        put("weather", JSONObject.NULL)
                        put("pinned", false)
                        put("locked", false)
                        put("dtCreated", now)
                        put("dtUpdated", now)
                    }

                    var csvPlaceName = ""
                    var csvPlaceAddress = ""
                    var csvPlaceCoords = ""

                    for ((colIdx, entryField, misc) in colMap) {
                        val value = row.getOrElse(colIdx) { "" }.trim()
                        if (value.isEmpty()) continue

                        when (entryField) {
                            "date" -> entry.put("date", parseDateWithFormat(value, misc))
                            "time" -> entry.put("time", parseTimeWithFormat(value, misc))
                            "title" -> entry.put("title", value)
                            "content" -> entry.put("content", value)
                            "richContent" -> entry.put("richContent", value)
                            "categories" -> entry.put("categories", JSONArray(value.split(separator).map { it.trim() }.filter { it.isNotEmpty() }))
                            "tags" -> {
                                val tagSep = if (tagsSpaceSep) Regex("\\s+") else Regex(Regex.escape(separator))
                                entry.put("tags", JSONArray(value.split(tagSep).map { it.trim() }.filter { it.isNotEmpty() }))
                            }
                            "people" -> {
                                val peopleArr = JSONArray()
                                value.split(separator).map { it.trim() }.filter { it.isNotEmpty() }.forEach { name ->
                                    val parts = name.split(Regex("\\s+"), limit = 2)
                                    peopleArr.put(JSONObject().apply {
                                        put("firstName", parts[0])
                                        put("lastName", parts.getOrElse(1) { "" })
                                    })
                                }
                                entry.put("people", peopleArr)
                            }
                            "placeName" -> csvPlaceName = value
                            "placeAddress" -> csvPlaceAddress = value
                            "placeCoords" -> csvPlaceCoords = value
                        }
                    }

                    entry.put("placeName", csvPlaceName)
                    if (csvPlaceAddress.isNotEmpty() || csvPlaceCoords.isNotEmpty()) {
                        val loc = JSONObject().apply {
                            put("name", "")
                            put("address", csvPlaceAddress)
                        }
                        if (csvPlaceCoords.isNotEmpty()) {
                            val m = Regex("(-?\\d+\\.?\\d*)\\s*,\\s*(-?\\d+\\.?\\d*)").find(csvPlaceCoords)
                            if (m != null) {
                                loc.put("lat", m.groupValues[1].toDouble())
                                loc.put("lng", m.groupValues[2].toDouble())
                            }
                        }
                        entry.put("locations", JSONArray().put(loc))
                    }

                    // Track new metadata
                    val cats = entry.optJSONArray("categories") ?: JSONArray()
                    for (i in 0 until cats.length()) {
                        val c = cats.optString(i, "")
                        if (c.isNotEmpty() && !existingCats.contains(c.lowercase())) {
                            newCats.add(c)
                            existingCats.add(c.lowercase())
                        }
                    }
                    val tags = entry.optJSONArray("tags") ?: JSONArray()
                    for (i in 0 until tags.length()) {
                        val t = tags.optString(i, "")
                        if (t.isNotEmpty() && !existingTags.contains(t.lowercase())) {
                            newTags.add(t)
                            existingTags.add(t.lowercase())
                        }
                    }
                    val people = entry.optJSONArray("people") ?: JSONArray()
                    for (i in 0 until people.length()) {
                        val p = people.optJSONObject(i) ?: continue
                        val key = "${p.optString("firstName", "")} ${p.optString("lastName", "")}".trim().lowercase()
                        if (key.isNotEmpty() && !existingPeople.contains(key)) {
                            newPeople.add(p.optString("firstName", "") to p.optString("lastName", ""))
                            existingPeople.add(key)
                        }
                    }

                    val title = entry.optString("title", "")
                    val content = entry.optString("content", "")
                    if (title.isEmpty() && content.isEmpty()) {
                        skipped++
                    } else {
                        val entryKey = "${entry.optString("date", "")}|${title.lowercase().trim()}"
                        if (existingKeys.contains(entryKey)) {
                            duplicates++
                        } else {
                            db.addEntry(entry.toString())
                            existingKeys.add(entryKey)
                            imported++
                        }
                    }

                    if ((rowIdx + 1) % 20 == 0) {
                        val progress = rowIdx + 1
                        runOnUiThread { statusView.text = "Importing $progress / $total..." }
                    }
                }

                // Auto-create new metadata
                if (newCats.isNotEmpty()) {
                    val allCats = try { JSONArray(db.getCategories()) } catch (_: Exception) { JSONArray() }
                    for (c in newCats) allCats.put(c)
                    db.setCategories(allCats.toString())
                }
                if (newTags.isNotEmpty()) {
                    val s = try { JSONObject(db.getSettings()) } catch (_: Exception) { JSONObject() }
                    val allTags = s.optJSONArray("tags") ?: JSONArray()
                    for (t in newTags) allTags.put(t)
                    db.setSettings(JSONObject().apply { put("tags", allTags) }.toString())
                }
                for ((first, last) in newPeople) {
                    db.addPerson(first, last, "")
                }

                val msg = StringBuilder("Import complete: $imported entries imported.")
                if (newCats.isNotEmpty()) msg.append(" ${newCats.size} new categories.")
                if (newTags.isNotEmpty()) msg.append(" ${newTags.size} new tags.")
                if (newPeople.isNotEmpty()) msg.append(" ${newPeople.size} new people.")
                if (skipped > 0) msg.append(" $skipped rows skipped.")
                if (duplicates > 0) msg.append(" $duplicates duplicates skipped.")

                runOnUiThread {
                    statusView.text = msg.toString()
                    statusView.setTextColor(ThemeManager.color(C.ACCENT))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusView.text = "Import failed: ${e.message}"
                    statusView.setTextColor(ThemeManager.color(C.ERROR))
                }
            }
        }
    }

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

    // ========== Wallpaper ==========

    @Suppress("DEPRECATION")
    private fun pickWallpaper() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }
        startActivityForResult(intent, WALLPAPER_PICK)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return

        when (requestCode) {
            WALLPAPER_PICK -> handleWallpaperResult(data.data!!)
            CSV_PICK -> handleCsvImport(data.data!!)
        }
    }

    private fun handleWallpaperResult(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return

            val maxW = 1920
            val scale = if (bitmap.width > maxW) maxW.toFloat() / bitmap.width else 1f
            val w = (bitmap.width * scale).toInt()
            val h = (bitmap.height * scale).toInt()
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, w, h, true)

            val stream = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
            val base64 = "data:image/jpeg;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

            db.setSettings(JSONObject().apply { put("wallpaper", base64) }.toString())
            wallpaperPreview?.let {
                loadBase64Image(it, base64)
                it.visibility = View.VISIBLE
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== Icon Helpers ==========

    private fun loadIconSync(type: String, name: String): android.graphics.Bitmap? {
        return try {
            val json = db.getIcon(type, name)
            if (json.isEmpty() || json == "null") return null
            val dataUrl = json.removeSurrounding("\"")
            val base64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            if (base64.isEmpty()) return null
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) { null }
    }

    // ========== UI Builders ==========

    private fun buildSectionHeader(text: String) {
        contentContainer.addView(TextView(this).apply {
            this.text = text
            setTextColor(ThemeManager.color(C.ACCENT))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        })
    }

    private fun buildLabel(text: String) {
        contentContainer.addView(TextView(this).apply {
            this.text = text
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
        })
    }

    private fun buildToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
        }

        val cb = CheckBox(this).apply {
            this.isChecked = checked
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
        }
        val tv = TextView(this).apply {
            text = label
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        }

        cb.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        tv.setOnClickListener { cb.isChecked = !cb.isChecked }

        row.addView(cb)
        row.addView(tv)
        contentContainer.addView(row)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildSpinner(label: String, items: List<String>, selectedIdx: Int, onChange: (Int) -> Unit) {
        contentContainer.addView(TextView(this).apply {
            text = label
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
            setPadding(dp(4), dp(4), dp(4), dp(2))
        })

        val spinner = Spinner(this).apply {
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.spinner_bg)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 14f
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    setBackgroundColor(ThemeManager.color(C.INPUT_BG))
                    textSize = 14f
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(selectedIdx)

        var initialSet = true
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (initialSet) { initialSet = false; return }
                onChange(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        contentContainer.addView(spinner)
    }

    private fun buildNumberInput(label: String, currentValue: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6) }
        }

        row.addView(TextView(this).apply {
            text = label
            setTextColor(ThemeManager.color(C.TEXT))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val input = EditText(this).apply {
            setText(currentValue.toString())
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(dp(70), dp(36))
            gravity = Gravity.CENTER
        }
        input.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val v = input.text.toString().toIntOrNull()?.coerceIn(min, max) ?: currentValue
                input.setText(v.toString())
                onChange(v)
            }
        }

        row.addView(input)
        contentContainer.addView(row)
    }

    private fun buildSpacer() {
        contentContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(12)
            )
        })
    }

    private fun createChip(text: String, onRemove: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(10), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }

            addView(TextView(this@SettingsActivity).apply {
                this.text = text
                setTextColor(ThemeManager.color(C.TEXT))
                textSize = 13f
            })
            addView(Button(this@SettingsActivity).apply {
                this.text = "✕"
                textSize = 12f
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(4) }
                setOnClickListener { onRemove() }
            })
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

    private fun makeDialogSpinner(items: List<String>, selectedIdx: Int): Spinner {
        val spinner = Spinner(this).apply {
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.spinner_bg)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = linParams().apply { bottomMargin = dp(6) }
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

    private fun makeDialogInput(hint: String, text: String, multiLine: Boolean = false): EditText {
        return EditText(this).apply {
            this.hint = hint
            setText(text)
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = linParams().apply { bottomMargin = dp(8) }
            inputType = if (multiLine)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else
                InputType.TYPE_CLASS_TEXT
            if (multiLine) {
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
            }
        }
    }

    private fun makeLabelView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 12f
            setPadding(dp(4), dp(4), dp(4), dp(2))
        }
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

    // ========== Dashboard Components Tab ==========

    private val defaultDashboardComponents = listOf(
        "weather_streak" to "Weather & Streak",
        "stats" to "Stats Grid",
        "quick_actions" to "Quick Actions",
        "widgets" to "Widgets",
        "pinned" to "Pinned Entries",
        "recent" to "Recent Entries",
        "today_history" to "Today in History",
        "tags" to "Top Tags",
        "categories" to "Top Categories",
        "places" to "Top Places",
        "people" to "People"
    )

    private fun loadDashboardComponents(): MutableList<Pair<String, Boolean>> {
        val json = bs.get("dashboard_components")
        if (json != null) {
            try {
                val arr = JSONArray(json)
                val result = mutableListOf<Pair<String, Boolean>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    result.add(obj.getString("id") to obj.optBoolean("enabled", true))
                }
                val existingIds = result.map { it.first }.toSet()
                for ((id, _) in defaultDashboardComponents) {
                    if (id !in existingIds) result.add(id to true)
                }
                return result
            } catch (_: Exception) {}
        }
        return defaultDashboardComponents.map { it.first to true }.toMutableList()
    }

    private fun saveDashboardComponents(components: List<Pair<String, Boolean>>) {
        val arr = JSONArray()
        for ((id, enabled) in components) {
            arr.put(JSONObject().apply {
                put("id", id)
                put("enabled", enabled)
            })
        }
        bs.set("dashboard_components", arr.toString())
    }

    private fun getComponentLabel(id: String): String {
        return defaultDashboardComponents.firstOrNull { it.first == id }?.second ?: id
    }

    private fun buildDashboardTab() {
        val components = loadDashboardComponents()

        buildSectionHeader("📊  Dashboard Components")
        buildLabel("Toggle components on/off. Use arrows to reorder.")

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        contentContainer.addView(listContainer)

        fun rebuildList() {
            listContainer.removeAllViews()
            for (i in components.indices) {
                val (id, enabled) = components[i]
                listContainer.addView(createDashboardComponentRow(id, enabled, i, components.size) { action ->
                    when (action) {
                        "toggle" -> {
                            components[i] = id to !components[i].second
                            saveDashboardComponents(components)
                            rebuildList()
                        }
                        "up" -> if (i > 0) {
                            val tmp = components[i - 1]
                            components[i - 1] = components[i]
                            components[i] = tmp
                            saveDashboardComponents(components)
                            rebuildList()
                        }
                        "down" -> if (i < components.size - 1) {
                            val tmp = components[i + 1]
                            components[i + 1] = components[i]
                            components[i] = tmp
                            saveDashboardComponents(components)
                            rebuildList()
                        }
                    }
                })
            }
        }
        rebuildList()
    }

    private fun createDashboardComponentRow(
        id: String, enabled: Boolean, index: Int, total: Int,
        onAction: (String) -> Unit
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setBackgroundColor(if (index % 2 == 0) Color.parseColor("#10FFFFFF") else Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) }
        }

        val cb = CheckBox(this).apply {
            isChecked = enabled
            buttonTintList = ThemeManager.colorStateList(C.ACCENT)
            setOnCheckedChangeListener { _, _ -> onAction("toggle") }
        }
        row.addView(cb)

        val label = TextView(this).apply {
            text = getComponentLabel(id)
            setTextColor(
                if (enabled) ThemeManager.color(C.TEXT)
                else ThemeManager.color(C.TEXT_SECONDARY)
            )
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        }
        label.setOnClickListener { cb.isChecked = !cb.isChecked }
        row.addView(label)

        val btnUp = Button(this).apply {
            text = "▲"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            minWidth = 0
            minimumWidth = 0
            isEnabled = index > 0
            alpha = if (index > 0) 1f else 0.25f
            setOnClickListener { onAction("up") }
        }
        row.addView(btnUp)

        val btnDown = Button(this).apply {
            text = "▼"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            minWidth = 0
            minimumWidth = 0
            isEnabled = index < total - 1
            alpha = if (index < total - 1) 1f else 0.25f
            setOnClickListener { onAction("down") }
        }
        row.addView(btnDown)

        return row
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
