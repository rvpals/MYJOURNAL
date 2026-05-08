package com.journal.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

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
    }

    override fun onResume() {
        super.onResume()
        if (lastThemeVersion != ThemeManager.themeVersion) {
            recreate()
            return
        }
    }

    private fun buildChangelog(): String = """
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
