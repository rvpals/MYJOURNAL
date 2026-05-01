package com.journal.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journal.app.ThemeManager.C
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class DashboardActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic var pendingData: String? = null
        @JvmStatic var needsRefresh = false
    }

    private lateinit var dashboardData: JSONObject

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        val json = pendingData
        pendingData = null
        if (json.isNullOrEmpty()) {
            finish()
            return
        }

        try {
            dashboardData = JSONObject(json)
        } catch (_: JSONException) {
            finish()
            return
        }

        setupNavbar()
        setupMenuButton()
        setupSearchButton()
        populateWeatherStreak()
        populateStats()
        populateQuickActions()
        populateWidgets()
        populatePinnedEntries()
        populateRecentEntries()
        populateTodayInHistory()
        populateRankedPanel(R.id.tags_list, R.id.panel_tags, "topTags", "tags")
        populateCategoriesPanel()
        populateRankedPanel(R.id.places_list, R.id.panel_places, "topPlaces", "placeName")
        populateInspiration()
        applyDashboardComponentSettings()
    }

    // ========== Dashboard Component Order & Visibility ==========

    private fun applyDashboardComponentSettings() {
        val bs = ServiceProvider.bootstrapService ?: return
        val container = findViewById<LinearLayout>(R.id.content_container)

        val componentViewMap = mapOf(
            "weather_streak" to findViewById<View>(R.id.weather_streak_row),
            "stats" to findViewById<View>(R.id.stats_container),
            "quick_actions" to findViewById<View>(R.id.quick_actions),
            "widgets" to findViewById<View>(R.id.panel_widgets),
            "pinned" to findViewById<View>(R.id.panel_pinned),
            "recent" to findViewById<View>(R.id.panel_recent),
            "tags" to findViewById<View>(R.id.panel_tags),
            "categories" to findViewById<View>(R.id.panel_categories),
            "places" to findViewById<View>(R.id.panel_places),
            "today_history" to findViewById<View>(R.id.panel_today_history),
            "inspiration" to findViewById<View>(R.id.panel_inspiration)
        )

        val defaultOrder = listOf(
            "weather_streak", "stats", "quick_actions", "widgets",
            "pinned", "recent", "today_history", "tags", "categories", "places", "inspiration"
        )

        val components = mutableListOf<Pair<String, Boolean>>()
        val json = bs.get("dashboard_components")
        if (json != null) {
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    components.add(obj.getString("id") to obj.optBoolean("enabled", true))
                }
                val existingIds = components.map { it.first }.toSet()
                for (id in defaultOrder) {
                    if (id !in existingIds) components.add(id to true)
                }
            } catch (_: Exception) {
                components.clear()
                components.addAll(defaultOrder.map { it to true })
            }
        } else {
            components.addAll(defaultOrder.map { it to true })
        }

        for ((id, _) in components) {
            val view = componentViewMap[id] ?: continue
            container.removeView(view)
        }

        for ((id, enabled) in components) {
            val view = componentViewMap[id] ?: continue
            if (enabled) {
                container.addView(view)
            }
        }
    }

    // ========== Navbar ==========

    private fun setupNavbar() {
        val journalName = dashboardData.optString("journalName", "")
        findViewById<TextView>(R.id.nav_title).text =
            if (journalName.isEmpty()) "JOURNAL" else journalName.uppercase()

        findViewById<View>(R.id.btn_new_entry).setOnClickListener {
            EntryFormActivity.databaseService = ServiceProvider.databaseService
            EntryFormActivity.bootstrapService = ServiceProvider.bootstrapService
            EntryFormActivity.weatherService = ServiceProvider.weatherService
            startActivity(Intent(this, EntryFormActivity::class.java))
        }
        findViewById<View>(R.id.btn_lock).setOnClickListener { returnToLogin() }
    }

    private fun setupMenuButton() {
        findViewById<View>(R.id.btn_menu).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, 1, 0, "📋  Entries")
            popup.menu.add(0, 2, 1, "📅  Calendar")
            popup.menu.add(0, 3, 2, "📊  Reports")
            popup.menu.add(0, 4, 3, "🔍  Explorer")
            popup.menu.add(0, 5, 4, "⚙️  Settings")
            popup.menu.add(0, 6, 5, "ℹ️  About")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> openFilteredEntryList(null, null)
                    2 -> {
                        val db = ServiceProvider.databaseService ?: return@setOnMenuItemClickListener true
                        val entriesJson = db.getEntries()
                        CalendarActivity.pendingData = entriesJson
                        startActivity(Intent(this, CalendarActivity::class.java))
                    }
                    3 -> {
                        ReportsActivity.databaseService = ServiceProvider.databaseService
                        ReportsActivity.bootstrapService = ServiceProvider.bootstrapService
                        startActivity(Intent(this, ReportsActivity::class.java))
                    }
                    4 -> {
                        ExplorerActivity.databaseService = ServiceProvider.databaseService
                        ExplorerActivity.bootstrapService = ServiceProvider.bootstrapService
                        startActivity(Intent(this, ExplorerActivity::class.java))
                    }
                    5 -> {
                        SettingsActivity.databaseService = ServiceProvider.databaseService
                        SettingsActivity.bootstrapService = ServiceProvider.bootstrapService
                        SettingsActivity.cryptoService = ServiceProvider.cryptoService
                        SettingsActivity.weatherService = ServiceProvider.weatherService
                        SettingsActivity.currentJournalId = ServiceProvider.bootstrapService?.get("last_journal_id")
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                    6 -> startActivity(Intent(this, AboutActivity::class.java))
                }
                true
            }
            popup.show()
        }
    }

    // ========== Search ==========

    private fun setupSearchButton() {
        findViewById<View>(R.id.btn_open_search).setOnClickListener {
            SearchActivity.databaseService = ServiceProvider.databaseService
            startActivity(Intent(this, SearchActivity::class.java))
        }
    }

    // ========== Weather & Streak ==========

    private fun populateWeatherStreak() {
        val row = findViewById<LinearLayout>(R.id.weather_streak_row)
        val weatherDisplay = findViewById<TextView>(R.id.weather_display)
        val streakDisplay = findViewById<TextView>(R.id.streak_display)

        var hasContent = false

        val streak = dashboardData.optInt("streak", 0)
        if (streak > 0) {
            streakDisplay.text = "\uD83D\uDD25 $streak day streak"
            streakDisplay.visibility = View.VISIBLE
            hasContent = true
        } else {
            streakDisplay.visibility = View.GONE
        }

        val weather = dashboardData.optString("weather", "")
        if (weather.isNotEmpty()) {
            weatherDisplay.text = weather
            weatherDisplay.visibility = View.VISIBLE
            hasContent = true
        } else {
            weatherDisplay.visibility = View.GONE
        }

        row.visibility = if (hasContent) View.VISIBLE else View.GONE
    }

    // ========== Stats ==========

    private fun populateStats() {
        setStatValue(R.id.stat_total_value, dashboardData.optInt("totalEntries", 0))
        setStatValue(R.id.stat_week_value, dashboardData.optInt("thisWeek", 0))
        setStatValue(R.id.stat_month_value, dashboardData.optInt("thisMonth", 0))
        setStatValue(R.id.stat_year_value, dashboardData.optInt("thisYear", 0))

        findViewById<View>(R.id.stat_total).setOnClickListener { openFilteredEntryList(null, null) }
        findViewById<View>(R.id.stat_week).setOnClickListener { openDateRangeEntryList("week", "This Week") }
        findViewById<View>(R.id.stat_month).setOnClickListener { openDateRangeEntryList("month", "This Month") }
        findViewById<View>(R.id.stat_year).setOnClickListener { openDateRangeEntryList("year", "This Year") }
    }

    private fun setStatValue(viewId: Int, value: Int) {
        findViewById<TextView>(viewId).text = value.toString()
    }

    // ========== Quick Actions (Pinned Views) ==========

    private fun populateQuickActions() {
        val views = dashboardData.optJSONArray("pinnedViews")
        val container = findViewById<LinearLayout>(R.id.quick_actions)

        if (views == null || views.length() == 0) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        container.removeAllViews()

        for (i in 0 until views.length()) {
            val view = views.optJSONObject(i) ?: continue
            val name = view.optString("name", "")
            val count = view.optInt("count", 0)

            val btn = Button(this).apply {
                text = "$name ($count)"
                setTextColor(ThemeManager.color(C.TEXT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = ContextCompat.getDrawable(this@DashboardActivity, R.drawable.btn_secondary)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    marginEnd = dp(4)
                }
                setOnClickListener { openFilteredEntryList(null, null) }
            }
            container.addView(btn)
        }
    }

    // ========== Widgets ==========

    private fun populateWidgets() {
        val widgets = dashboardData.optJSONArray("widgets")
        val panel = findViewById<LinearLayout>(R.id.panel_widgets)
        val list = findViewById<LinearLayout>(R.id.widgets_list)

        if (widgets == null || widgets.length() == 0) {
            panel.visibility = View.GONE
            return
        }

        panel.visibility = View.VISIBLE
        list.removeAllViews()

        for (i in 0 until widgets.length()) {
            val widget = widgets.optJSONObject(i) ?: continue
            list.addView(createWidgetCard(widget))
        }
    }

    private fun createWidgetCard(widget: JSONObject): View {
        val name = widget.optString("name", "")
        val description = widget.optString("description", "")
        val bgColor = widget.optString("bgColor", "")
        val icon = widget.optString("icon", "")
        val results = widget.optJSONArray("results") ?: JSONArray()
        val filtersJson = widget.optJSONArray("filters") ?: JSONArray()
        val widgetName = name.ifEmpty { "Widget" }

        val parsedBg = try {
            if (bgColor.isNotEmpty()) Color.parseColor(bgColor) else null
        } catch (_: Exception) { null }

        val textColor = if (parsedBg != null) getContrastColor(parsedBg) else
            ThemeManager.color(C.TEXT)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))

            val bg = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                if (parsedBg != null) {
                    setColor(parsedBg)
                } else {
                    setColor(ThemeManager.color(C.CARD_BG))
                    setStroke(dp(1), ThemeManager.color(C.TEXT_SECONDARY))
                }
            }
            background = bg
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        // Header row: name + edit button
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        titleCol.addView(TextView(this).apply {
            text = widgetName
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })

        if (description.isNotEmpty()) {
            titleCol.addView(TextView(this).apply {
                text = description
                setTextColor(textColor)
                alpha = 0.75f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }

        if (icon.isNotEmpty()) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            loadBase64Image(iv, icon)
            header.addView(iv)
        }

        header.addView(titleCol)

        val editBtn = Button(this).apply {
            text = "✎"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(textColor)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            setPadding(0, 0, 0, 0)
            alpha = 0.6f
            setOnClickListener {
                val widgetId = widget.optString("id", "")
                WidgetEditorActivity.databaseService = ServiceProvider.databaseService
                WidgetEditorActivity.bootstrapService = ServiceProvider.bootstrapService
                WidgetEditorActivity.pendingWidgetId = widgetId
                startActivity(Intent(this@DashboardActivity, WidgetEditorActivity::class.java))
            }
        }
        header.addView(editBtn)
        card.addView(header)

        // Results
        for (j in 0 until results.length()) {
            val r = results.optJSONObject(j) ?: continue
            val prefix = r.optString("prefix", "")
            val postfix = r.optString("postfix", "")
            val value = r.optString("result", "0")

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(3))
            }

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

            row.addView(TextView(this).apply {
                text = "$value${if (postfix.isNotEmpty()) " $postfix" else ""}"
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, Typeface.BOLD)
            })

            card.addView(row)
        }

        card.setOnClickListener {
            openFilteredEntryList(filtersJson.toString(), widgetName)
        }

        return card
    }

    private fun getContrastColor(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.5) Color.parseColor("#1a1a2e") else Color.WHITE
    }

    // ========== Pinned Entries ==========

    private fun populatePinnedEntries() {
        val pinned = dashboardData.optJSONArray("pinnedEntries")
        val panel = findViewById<LinearLayout>(R.id.panel_pinned)
        val list = findViewById<LinearLayout>(R.id.pinned_entries_list)

        if (pinned == null || pinned.length() == 0) {
            panel.visibility = View.GONE
            return
        }

        panel.visibility = View.VISIBLE
        list.removeAllViews()
        for (i in 0 until pinned.length()) {
            val entry = pinned.optJSONObject(i) ?: continue
            list.addView(createEntryRow(entry, showPreview = false))
        }
    }

    // ========== Recent Entries ==========

    private fun populateRecentEntries() {
        val recent = dashboardData.optJSONArray("recentEntries")
        val list = findViewById<LinearLayout>(R.id.recent_entries_list)
        list.removeAllViews()

        if (recent == null || recent.length() == 0) {
            val empty = TextView(this).apply {
                text = "No entries yet. Tap \u270F\uFE0F to create your first entry!"
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }
            list.addView(empty)
            return
        }

        for (i in 0 until recent.length()) {
            val entry = recent.optJSONObject(i) ?: continue
            list.addView(createEntryRow(entry, showPreview = false))
        }
    }

    // ========== Today in History ==========

    private fun populateTodayInHistory() {
        val items = dashboardData.optJSONArray("todayInHistory")
        val panel = findViewById<LinearLayout>(R.id.panel_today_history)
        val list = findViewById<LinearLayout>(R.id.today_history_list)

        if (items == null || items.length() == 0) {
            panel.visibility = View.GONE
            return
        }

        panel.visibility = View.VISIBLE
        list.removeAllViews()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val title = item.optString("title", "Untitled")
            val preview = item.optString("contentPreview", "")
            val yearsAgo = item.optInt("yearsAgo", 0)
            val entryId = item.optString("id", "")

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = ContextCompat.getDrawable(this@DashboardActivity, R.drawable.entry_row_bg)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(4) }
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            topRow.addView(TextView(this).apply {
                text = title.ifEmpty { "Untitled" }
                setTextColor(ThemeManager.color(C.TEXT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            val badge = TextView(this).apply {
                text = "$yearsAgo year${if (yearsAgo != 1) "s" else ""} ago"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(ThemeManager.color(C.ACCENT))
                }
                background = bg
                setPadding(dp(8), dp(3), dp(8), dp(3))
            }
            topRow.addView(badge)
            row.addView(topRow)

            if (preview.isNotEmpty()) {
                row.addView(TextView(this).apply {
                    text = preview
                    setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(2), 0, 0)
                })
            }

            row.setOnClickListener {
                if (entryId.isNotEmpty()) {
                    EntryViewerActivity.pendingEntryId = entryId
                    EntryViewerActivity.databaseService = ServiceProvider.databaseService
                    EntryViewerActivity.bootstrapService = ServiceProvider.bootstrapService
                    startActivity(Intent(this, EntryViewerActivity::class.java))
                }
            }

            list.addView(row)
        }
    }

    private fun createEntryRow(entry: JSONObject, showPreview: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = ContextCompat.getDrawable(this@DashboardActivity, R.drawable.entry_row_bg)
            isClickable = true
            isFocusable = true
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = entry.optString("title", "")
        val title = TextView(this).apply {
            text = if (titleText.isEmpty()) "Untitled" else titleText
            setTextColor(ThemeManager.color(C.TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(title)

        val date = entry.optString("date", "")
        if (date.isNotEmpty()) {
            val dateText = TextView(this).apply {
                text = formatDate(date)
                setTextColor(ThemeManager.color(C.ACCENT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(8), 0, 0, 0)
            }
            topRow.addView(dateText)
        }

        row.addView(topRow)

        val preview = entry.optString("contentPreview", "")
        val time = entry.optString("time", "")
        var subtitle = formatTime(time)
        if (preview.isNotEmpty()) {
            if (subtitle.isNotEmpty()) subtitle += " — "
            subtitle += preview
        }

        if (subtitle.isNotEmpty()) {
            val sub = TextView(this).apply {
                text = subtitle
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = if (showPreview) 3 else 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            }
            row.addView(sub)
        }

        val entryId = entry.optString("id", "")
        row.setOnClickListener {
            if (entryId.isNotEmpty()) {
                EntryViewerActivity.pendingEntryId = entryId
                EntryViewerActivity.databaseService = ServiceProvider.databaseService
                EntryViewerActivity.bootstrapService = ServiceProvider.bootstrapService
                startActivity(Intent(this, EntryViewerActivity::class.java))
            }
        }

        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) }

        return row
    }

    // ========== Daily Inspiration ==========

    private fun populateInspiration() {
        val db = ServiceProvider.databaseService ?: return
        val panel = findViewById<LinearLayout>(R.id.panel_inspiration)
        val content = findViewById<LinearLayout>(R.id.inspiration_content)

        panel.visibility = View.VISIBLE
        content.removeAllViews()

        val json = db.getRandomInspiration()
        val obj = if (json.isNotEmpty()) try { JSONObject(json) } catch (_: Exception) { null } else null
        val quote = obj?.optString("quote", "") ?: ""
        val source = obj?.optString("source", "") ?: ""

        if (quote.isNotEmpty()) {
            content.addView(TextView(this).apply {
                text = "“$quote”"
                setTextColor(ThemeManager.color(C.TEXT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(Typeface.SERIF, Typeface.ITALIC)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(4))
                setLineSpacing(dp(4).toFloat(), 1f)
            })

            if (source.isNotEmpty()) {
                content.addView(TextView(this).apply {
                    text = "— $source"
                    setTextColor(ThemeManager.color(C.ACCENT))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.END
                    setPadding(dp(12), dp(4), dp(16), dp(4))
                })
            }
        } else {
            content.addView(TextView(this).apply {
                text = "No quotes yet. Tap ✏️ to add some!"
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(8), dp(12), dp(8))
            })
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(6), dp(4), dp(2))
        }

        if (quote.isNotEmpty()) {
            btnRow.addView(TextView(this).apply {
                text = "🔄"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(dp(12), dp(4), dp(12), dp(4))
                isClickable = true
                isFocusable = true
                setOnClickListener { populateInspiration() }
            })
        }

        btnRow.addView(TextView(this).apply {
            text = "✏️"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            isClickable = true
            isFocusable = true
            setOnClickListener { openInspirationSettings() }
        })

        content.addView(btnRow)
    }

    private fun openInspirationSettings() {
        SettingsActivity.databaseService = ServiceProvider.databaseService
        SettingsActivity.bootstrapService = ServiceProvider.bootstrapService
        SettingsActivity.cryptoService = ServiceProvider.cryptoService
        SettingsActivity.weatherService = ServiceProvider.weatherService
        SettingsActivity.currentJournalId = ServiceProvider.bootstrapService?.get("last_journal_id")
        SettingsActivity.initialTab = "cattags"
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    // ========== Ranked Panels (Tags, Categories, Places) ==========

    private fun populateCategoriesPanel() {
        val items = dashboardData.optJSONArray("topCategories")
        val panel = findViewById<LinearLayout>(R.id.panel_categories)
        val list = findViewById<LinearLayout>(R.id.categories_list)

        if (items == null || items.length() == 0) {
            panel.visibility = View.GONE
            return
        }

        panel.visibility = View.VISIBLE

        val bs = ServiceProvider.bootstrapService
        val isCardView = bs?.get("categories_view_mode") == "card"

        // Replace the static title TextView with a row containing title + toggle
        val titleView = panel.getChildAt(0)
        if (titleView is TextView) {
            panel.removeViewAt(0)
            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
            titleRow.addView(TextView(this).apply {
                text = "📁 Top Categories"
                setTextColor(ThemeManager.color(C.TEXT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            titleRow.addView(TextView(this).apply {
                tag = "cat_view_toggle"
                text = if (isCardView) "☰" else "▦"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setPadding(dp(8), dp(2), dp(4), dp(2))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val newMode = if (isCardView) "list" else "card"
                    bs?.set("categories_view_mode", newMode)
                    populateCategoriesPanel()
                }
            })
            panel.addView(titleRow, 0)
        } else if (titleView is LinearLayout) {
            val toggle = titleView.findViewWithTag<TextView>("cat_view_toggle")
            toggle?.text = if (isCardView) "☰" else "▦"
            toggle?.setOnClickListener {
                val newMode = if (isCardView) "list" else "card"
                bs?.set("categories_view_mode", newMode)
                populateCategoriesPanel()
            }
        }

        list.removeAllViews()

        if (isCardView) {
            buildCategoryCardView(list, items)
        } else {
            val limit = minOf(items.length(), 10)
            for (i in 0 until limit) {
                val item = items.optJSONObject(i) ?: continue
                val name = item.optString("name", "")
                val count = item.optInt("count", 0)
                val color: String? = if (item.has("color")) item.optString("color") else null
                list.addView(createRankedRow(name, count, color, i + 1, "categories"))
            }
            if (items.length() > 10) {
                list.addView(TextView(this).apply {
                    text = "+${items.length() - 10} more"
                    setTextColor(ThemeManager.color(C.ACCENT))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(dp(6), dp(6), dp(6), dp(2))
                    setOnClickListener { openFilteredEntryList(null, null) }
                })
            }
        }
    }

    private fun buildCategoryCardView(container: LinearLayout, items: JSONArray) {
        val db = ServiceProvider.databaseService ?: return
        val columns = 3
        val limit = minOf(items.length(), 10)
        var currentRow: LinearLayout? = null

        for (i in 0 until limit) {
            val item = items.optJSONObject(i) ?: continue
            val name = item.optString("name", "")
            val count = item.optInt("count", 0)

            if (i % columns == 0) {
                currentRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6) }
                }
                container.addView(currentRow)
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), dp(8), dp(6), dp(8))
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(ThemeManager.color(C.CARD_BG))
                    setStroke(dp(1), ThemeManager.color(C.CARD_BORDER))
                }
                background = bg
                elevation = dp(2).toFloat()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = if ((i % columns) < columns - 1) dp(6) else 0
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val filter = JSONArray().put(JSONObject().apply {
                        put("field", "categories")
                        put("op", "includes")
                        put("value", name)
                        put("value2", "")
                    })
                    openFilteredEntryList(filter.toString(), name)
                }
            }

            // Icon
            val iconData = try { db.getIcon("category_hd", name).ifEmpty { db.getIcon("category", name) } } catch (_: Exception) { "" }
            if (iconData.isNotEmpty() && iconData != "null") {
                val iv = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(4) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                try {
                    val dataUrl = iconData.removeSurrounding("\"")
                    val b64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
                    if (b64.isNotEmpty()) {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) iv.setImageBitmap(bmp)
                    }
                } catch (_: Exception) {}
                card.addView(iv)
            } else {
                card.addView(TextView(this).apply {
                    text = "📁"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(4) }
                })
            }

            // Name
            card.addView(TextView(this).apply {
                text = name
                setTextColor(ThemeManager.color(C.TEXT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })

            // Count
            card.addView(TextView(this).apply {
                text = "$count"
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
            })

            currentRow?.addView(card)
        }

        // Fill remaining cells in last row
        val remainder = limit % columns
        if (remainder != 0) {
            for (j in remainder until columns) {
                currentRow?.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                        marginEnd = if (j < columns - 1) dp(6) else 0
                    }
                })
            }
        }
    }

    private fun populateRankedPanel(listId: Int, panelId: Int, dataKey: String, filterField: String) {
        val items = dashboardData.optJSONArray(dataKey)
        val list = findViewById<LinearLayout>(listId)
        val panel = findViewById<LinearLayout>(panelId)

        if (items == null || items.length() == 0) {
            panel.visibility = View.GONE
            return
        }

        panel.visibility = View.VISIBLE
        list.removeAllViews()

        val limit = minOf(items.length(), 10)
        for (i in 0 until limit) {
            val item = items.optJSONObject(i) ?: continue
            val name = item.optString("name", "")
            val count = item.optInt("count", 0)
            val color: String? = if (item.has("color")) item.optString("color") else null
            list.addView(createRankedRow(name, count, color, i + 1, filterField))
        }

        if (items.length() > 10) {
            val more = TextView(this).apply {
                text = "+${items.length() - 10} more"
                setTextColor(ThemeManager.color(C.ACCENT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(6), dp(6), dp(6), dp(2))
                setOnClickListener { openFilteredEntryList(null, null) }
            }
            list.addView(more)
        }
    }

    private fun createRankedRow(name: String, count: Int, color: String?, rank: Int, filterField: String): View {
        val rowBg = if (rank % 2 == 0) Color.parseColor("#10FFFFFF") else Color.TRANSPARENT
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(9), dp(8), dp(9))
            setBackgroundColor(rowBg)
            isClickable = true
            isFocusable = true
        }

        val accentColor = ThemeManager.color(C.ACCENT)
        val rankBadge = TextView(this).apply {
            text = rank.toString()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minWidth = dp(28)
            minHeight = dp(28)
            setTextColor(Color.WHITE)
            val bg = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#22d4e6"), accentColor, Color.parseColor("#12899a"))
            )
            bg.cornerRadius = dp(8).toFloat()
            background = bg
            elevation = dp(3).toFloat()
            setPadding(dp(4), dp(3), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        }
        row.addView(rankBadge)

        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), 0)
        }
        row.addView(spacer)

        val nameText = TextView(this).apply {
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val textColor = if (!color.isNullOrEmpty() && color != "null") {
                try {
                    Color.parseColor(color)
                } catch (_: Exception) {
                    ThemeManager.color(C.TEXT)
                }
            } else {
                ThemeManager.color(C.TEXT)
            }
            setTextColor(textColor)
        }
        row.addView(nameText)

        val countBadge = TextView(this).apply {
            text = count.toString()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minWidth = dp(32)
            val bg = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#5a5f80"), Color.parseColor("#454a6b"), Color.parseColor("#3a3f5c"))
            )
            bg.cornerRadius = dp(6).toFloat()
            background = bg
            elevation = dp(2).toFloat()
            setPadding(dp(6), dp(3), dp(6), dp(3))
        }
        row.addView(countBadge)

        row.setOnClickListener {
            val op = if (filterField == "placeName") "contains" else "includes"
            val filter = JSONArray().put(JSONObject().apply {
                put("field", filterField)
                put("op", op)
                put("value", name)
                put("value2", "")
            })
            openFilteredEntryList(filter.toString(), name)
        }

        return row
    }

    // ========== Navigation ==========

    private fun openFilteredEntryList(filtersJson: String?, label: String?) {
        android.util.Log.d("DashboardNav", "openFilteredEntryList filters=$filtersJson label=$label")
        EntryListActivity.databaseService = ServiceProvider.databaseService
        EntryListActivity.bootstrapService = ServiceProvider.bootstrapService
        if (filtersJson != null) {
            EntryListActivity.pendingWidgetFilter = filtersJson
            EntryListActivity.pendingWidgetName = label
        }
        startActivity(Intent(this, EntryListActivity::class.java))
    }

    private fun openDateRangeEntryList(period: String, label: String) {
        val cal = java.util.Calendar.getInstance()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val from: String
        val to: String
        when (period) {
            "week" -> {
                val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)
                val diff = if (dow == java.util.Calendar.SUNDAY) -6 else java.util.Calendar.MONDAY - dow
                cal.add(java.util.Calendar.DAY_OF_MONTH, diff)
                from = fmt.format(cal.time)
                cal.add(java.util.Calendar.DAY_OF_MONTH, 6)
                to = fmt.format(cal.time)
            }
            "month" -> {
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                from = fmt.format(cal.time)
                cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
                to = fmt.format(cal.time)
            }
            "year" -> {
                cal.set(java.util.Calendar.MONTH, java.util.Calendar.JANUARY)
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                from = fmt.format(cal.time)
                cal.set(java.util.Calendar.MONTH, java.util.Calendar.DECEMBER)
                cal.set(java.util.Calendar.DAY_OF_MONTH, 31)
                to = fmt.format(cal.time)
            }
            else -> { from = ""; to = "" }
        }
        val filter = JSONArray().put(JSONObject().apply {
            put("field", "date")
            put("op", "between")
            put("value", from)
            put("value2", to)
        })
        openFilteredEntryList(filter.toString(), label)
    }

    private fun returnToLogin() {
        ServiceProvider.databaseService?.close()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAffinity()
    }

    // ========== Utility ==========

    private fun loadBase64Image(imageView: ImageView, dataUrl: String) {
        try {
            val base64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            if (base64.isEmpty()) return
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) imageView.setImageBitmap(bmp)
        } catch (_: Exception) {}
    }

    private fun formatDate(dateStr: String): String {
        if (dateStr.isEmpty()) return ""
        val fmt = ServiceProvider.bootstrapService?.get("ev_date_format") ?: "MMMM d, yyyy"
        return try {
            val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(dateStr) ?: return dateStr
            java.text.SimpleDateFormat(fmt, java.util.Locale.US).format(d)
        } catch (_: Exception) { dateStr }
    }

    private fun formatTime(timeStr: String): String {
        if (timeStr.isEmpty()) return ""
        val fmt = ServiceProvider.bootstrapService?.get("ev_time_format") ?: "h:mm a"
        return try {
            val d = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).parse(timeStr) ?: return timeStr
            java.text.SimpleDateFormat(fmt, java.util.Locale.US).format(d)
        } catch (_: Exception) { timeStr }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onResume() {
        super.onResume()
        if (lastThemeVersion != ThemeManager.themeVersion || needsRefresh) {
            needsRefresh = false
            val db = ServiceProvider.databaseService ?: return
            val bs = ServiceProvider.bootstrapService ?: return
            Thread {
                val json = DashboardDataBuilder.build(db, bs)
                runOnUiThread {
                    pendingData = json
                    recreate()
                }
            }.start()
            return
        }
    }

}
