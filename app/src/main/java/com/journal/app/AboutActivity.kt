package com.journal.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class AboutActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(ThemeManager.fontScaledContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        val appName = getString(R.string.app_info_name)
        val appVersion = getString(R.string.app_info_version)
        val appEmail = getString(R.string.app_info_email)
        val appUrl = getString(R.string.app_info_url)
        val appCompanyUrl = getString(R.string.app_info_company_url)
        val appDescription = getString(R.string.app_info_description)

        findViewById<TextView>(R.id.about_app_name).text = appName
        findViewById<TextView>(R.id.about_version).text = "Version $appVersion"
        findViewById<TextView>(R.id.about_description).text = appDescription

        findViewById<TextView>(R.id.about_email).apply {
            text = appEmail
            setOnClickListener {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$appEmail")
                    putExtra(Intent.EXTRA_SUBJECT, "$appName - Support")
                }
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            }
        }

        findViewById<TextView>(R.id.about_url).apply {
            text = appUrl
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appUrl)))
            }
        }

        findViewById<TextView>(R.id.about_company_url).apply {
            text = "View on Google Play"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appCompanyUrl)))
            }
        }

        findViewById<android.view.View>(R.id.about_whats_new_btn).setOnClickListener {
            val tv = TextView(this).apply {
                text = buildChangelog()
                textSize = 13f
                setTextColor(0xFF2c3145.toInt())
                setPadding(48, 32, 48, 32)
                setLineSpacing(0f, 1.4f)
            }
            val scroll = ScrollView(this).apply { addView(tv) }
            AlertDialog.Builder(this)
                .setTitle("What's New")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show()
        }

        findViewById<android.view.View>(R.id.about_back_btn).setOnClickListener { finish() }

        findViewById<View>(R.id.about_get_stats_btn).setOnClickListener { loadContentStats() }
    }

    private fun loadContentStats() {
        val progress = findViewById<ProgressBar>(R.id.about_stats_progress)
        val results = findViewById<LinearLayout>(R.id.about_stats_results)
        val btn = findViewById<View>(R.id.about_get_stats_btn)

        btn.isEnabled = false
        btn.alpha = 0.5f
        progress.visibility = View.VISIBLE
        results.visibility = View.GONE

        thread {
            val db = ServiceProvider.databaseService
            val entriesJson = db?.getEntries() ?: "[]"
            val arr = org.json.JSONArray(entriesJson)

            data class EntryLength(val id: String, val title: String, val wordCount: Int)
            val entryLengths = mutableListOf<EntryLength>()
            val wordCounts = mutableMapOf<String, Int>()
            val stopWords = setOf("the", "a", "an", "is", "are", "was", "were", "be", "been",
                "being", "have", "has", "had", "do", "does", "did", "will", "would", "could",
                "should", "may", "might", "shall", "can", "need", "dare", "ought", "used",
                "to", "of", "in", "for", "on", "with", "at", "by", "from", "as", "into",
                "through", "during", "before", "after", "above", "below", "between", "out",
                "off", "over", "under", "again", "further", "then", "once", "and", "but",
                "or", "nor", "not", "so", "yet", "both", "either", "neither", "each",
                "every", "all", "any", "few", "more", "most", "other", "some", "such",
                "no", "only", "own", "same", "than", "too", "very", "just", "because",
                "this", "that", "these", "those", "i", "me", "my", "myself", "we", "our",
                "you", "your", "he", "him", "his", "she", "her", "it", "its", "they",
                "them", "their", "what", "which", "who", "whom", "when", "where", "why",
                "how", "if", "about", "up", "also", "there", "here", "s", "t", "don",
                "didn", "won", "isn", "aren", "wasn", "weren", "hasn", "haven", "hadn")

            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val content = e.optString("content", "")
                val words = content.split(Regex("\\s+")).filter { it.isNotBlank() }
                entryLengths.add(EntryLength(e.optString("id", ""), e.optString("title", "Untitled"), words.size))
                for (word in words) {
                    val cleaned = word.lowercase().replace(Regex("[^a-z']"), "")
                    if (cleaned.length > 1 && cleaned !in stopWords) {
                        wordCounts[cleaned] = (wordCounts[cleaned] ?: 0) + 1
                    }
                }
            }

            val top10Entries = entryLengths.sortedByDescending { it.wordCount }.take(10)
            val top50 = wordCounts.entries.sortedByDescending { it.value }.take(50)
            val maxCount = top50.firstOrNull()?.value ?: 1

            runOnUiThread {
                progress.visibility = View.GONE
                results.removeAllViews()
                results.visibility = View.VISIBLE
                btn.isEnabled = true
                btn.alpha = 1.0f

                // Top 10 longest entries
                val maxLabel = TextView(this).apply {
                    text = "📝 Longest Entries (Top 10)"
                    textSize = 12f
                    setTextColor(0xFF738598.toInt())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                results.addView(maxLabel)

                val topEntryMax = top10Entries.firstOrNull()?.wordCount ?: 1
                for ((idx, entry) in top10Entries.withIndex()) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 0, 0, 8)
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            if (entry.id.isNotEmpty()) {
                                EntryViewerActivity.pendingEntryId = entry.id
                                EntryViewerActivity.databaseService = ServiceProvider.databaseService
                                EntryViewerActivity.bootstrapService = ServiceProvider.bootstrapService
                                startActivity(Intent(this@AboutActivity, EntryViewerActivity::class.java))
                            }
                        }
                    }
                    val entryRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                    val rank = TextView(this).apply {
                        text = "${idx + 1}. ${entry.title.take(30)}"
                        textSize = 13f
                        setTextColor(0xFF1cb3c8.toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    entryRow.addView(rank)
                    val count = TextView(this).apply {
                        text = "${entry.wordCount} words"
                        textSize = 13f
                        setTextColor(0xFF1cb3c8.toInt())
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    entryRow.addView(count)
                    row.addView(entryRow)

                    val entryBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = topEntryMax
                        setProgress(entry.wordCount, false)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (6 * resources.displayMetrics.density).toInt()
                        ).apply { topMargin = (3 * resources.displayMetrics.density).toInt() }
                    }
                    row.addView(entryBar)
                    results.addView(row)
                }

                // Top 50 words
                val freqLabel = TextView(this).apply {
                    text = "🔤 Most Frequent Words (Top 50)"
                    textSize = 12f
                    setTextColor(0xFF738598.toInt())
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(0, 16, 0, 8)
                }
                results.addView(freqLabel)

                for ((idx, wordEntry) in top50.withIndex()) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 0, 0, 10)
                    }

                    val wordRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }

                    val rank = TextView(this).apply {
                        text = "${idx + 1}. ${wordEntry.key}"
                        textSize = 13f
                        setTextColor(0xFF2c3145.toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    wordRow.addView(rank)

                    val count = TextView(this).apply {
                        text = "${wordEntry.value}×"
                        textSize = 13f
                        setTextColor(0xFF1cb3c8.toInt())
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    wordRow.addView(count)
                    row.addView(wordRow)

                    val wordBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = maxCount
                        setProgress(wordEntry.value, false)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (6 * resources.displayMetrics.density).toInt()
                        ).apply { topMargin = (3 * resources.displayMetrics.density).toInt() }
                    }
                    row.addView(wordBar)
                    results.addView(row)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (lastThemeVersion != ThemeManager.themeVersion) {
            recreate()
            return
        }
    }

    private fun buildChangelog(): String = """
v2.5.3 (2026-05-10)
• About screen: splash image as subtle background
• About screen: clickable top 10 longest entries to view them
• Entry form: attach button available for new entries (prompts to save first)
• Entry form: removed "Now" button, time picker defaults to current time
• SQL Explorer: double-tap empty query field to fill default query
• Fix: time display wrong in viewer, dashboard, search, entry list, and reports (replaced SimpleDateFormat with direct string formatting)

v2.5.2 (2026-05-09)
• Splash screen with portrait image on app launch
• Skip splash screen option in Settings > Prefs
• Dashboard navbar elevated 3D panel style (rounded bottom corners, layered shadow)
• Fix: Time input normalization on entry save (e.g. "930" → "09:30")

v2.5.0 (2026-05-09)
• Content Stats: Top 50 most frequent words (up from 5) and Top 10 longest entries (up from 1)
• Dashboard: ✕ button on panels to quickly hide components (syncs with Settings > Dashboard)
• Dashboard: Entry Stats panel enclosed in single bordered container
• Entry Viewer & Editor: Record ID field shown at top for debugging
• Settings > Prefs: "Record ID" toggle in Entry List Fields (off by default)
• Settings: Collapsible panels now have clear bordered containers
• Fix: Time display inconsistency across screens (times stored without colon now normalized everywhere)

v2.4.0 (2026-05-08)
• Theme management — remove unwanted themes, restore all with Reset Themes
• Complete Backup & Restore — full zip backup (database + attachments + settings) with timestamp naming
• Data tab reorganized into collapsible panels (Data Paths, Complete Data Backup, Export & Import)
• Entry form: action buttons moved to bottom dock bar
• Entry form: category selection via popup checkbox dialog
• Entry form: Place & Weather grouped in bordered card
• Dashboard: Entry Stats as collapsible panel with half-size 2x2 cards

v2.3.0 (2026-05-07)
• 8 new color themes — Rose, Copper, Slate, Ember, Sage, Dusk, Mocha, Arctic (20 total)
• Fix: Publish draft from dashboard no longer crashes app

v2.2.0 (2026-05-07)
• Draft entries — save entries as drafts for future publishing
• "Save as Draft" button in entry form navbar
• Collapsible Drafts panel on dashboard with one-click Publish
• Edit and delete drafts from dashboard
• Entry Viewer now refreshes after editing an entry

v2.0.0 (2026-05-06)
• File attachments — attach any files to entries as zip archives
• New Attachment screen with Add/Save/Download/View Entry buttons
• Configurable storage paths (app data + attachments) in Settings > Data
• Entry Viewer: new Attachments tab (view/open/delete zip contents)
• Entry Form: 📎 button to manage attachments
• 3D shadow styling on all collapsible dashboard panels
• SQL Explorer grids: visible cell borders + alternating row colors
• Category icons: aspect-ratio-preserving resize on save
• Collapsible pre-fill template panel with full-width buttons
• Fix: time display without colon (e.g., "121" now shows "1:21 AM")

v1.9.0 (2026-05-05)
• Dashboard template shortcuts — one-tap new entry with pre-fill
• Pre-fill template category picker (multi-select dialog)
• Fix: ranked panel "+N more" now expands inline
• Collapsible panels in Prefs tab
• Dashboard & Ranking moved to Dashboard tab
• Removed stale components from dashboard settings
• Edit/Delete button sizing fix across Settings

v1.8.2 (2026-05-04)
• Categories field in pre-fill templates
• Fix: template title not populating after apply

v1.8.1 (2026-05-04)
• Fix: pre-fill template title overwritten by form sync
• Centered dashboard panel headers

v1.8.0 (2026-05-04)
• Custom view filters in Entry List and Reports
• Pre-fill templates in entry form (📋 button)
• Collapsible template items in Settings

v1.7.0 (2026-05-03)
• Collapsible dashboard panels (Recent, Tags, Categories, Places, Inspiration)
• Fix: dashboard refresh after entry save/delete

v1.6.0 (2026-05-02)
• Search term highlighting
• Collapsible template sections
• Daily Inspiration decorative styling
• Fix: SQL Library load crash

v1.5.0 (2026-04-28)
• Fully native app — all 14 screens are Kotlin activities
• WebView and web SPA removed
• ServiceProvider + DashboardDataBuilder architecture
• 12 themes with runtime ThemeManager
• App font customization (family + size)
• SQL Library (save/load/edit/delete queries)
• Auto GPS & weather on new entry
• Dashboard component settings (toggle/reorder)
• Today in History panel
• Daily Inspiration panel
• Widget color picker and icon support
• CSV mapping with test preview

v1.4.0 (2026-04-03)
• Native Login, Dashboard, Settings activities
• Styled Android drawables
• Entry list with collapsible filters

v1.3.1 (2026-03-27)
• Pin/unpin entries
• Biometric authentication
• Date/time format settings
• Entry viewer font customization

v1.2.0 (2026-03-25)
• Custom Views with AND/OR/NOT logic
• Pre-fill templates
• CSV import with column mapping
• Batch deletion

v1.1.0 (2026-03-20)
• SQL Explorer
• 4 new themes (total: 12)

v1.0.0
• Initial release — encrypted journal with multi-journal support
""".trimIndent()

}
