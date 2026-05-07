package com.journal.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journal.app.ThemeManager.C
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SearchActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic
        var databaseService: DatabaseService? = null
    }

    private var entries = JSONArray()
    private var lastMatchIds = listOf<String>()
    private var entryIdsWithAttachments = emptySet<String>()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(ThemeManager.fontScaledContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        val db = databaseService ?: ServiceProvider.databaseService
        if (db != null) {
            try {
                entries = JSONArray(db.getEntries())
            } catch (_: JSONException) {}
            entryIdsWithAttachments = db.getEntryIdsWithAttachments()
        }

        setupNavbar()
        setupSearch()
    }

    private fun setupNavbar() {
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
    }

    private fun setupSearch() {
        val searchInput = findViewById<EditText>(R.id.search_input)
        val btnSearch = findViewById<Button>(R.id.btn_search)
        val btnClear = findViewById<Button>(R.id.btn_clear)

        btnSearch.setOnClickListener { performSearch(searchInput.text.toString().trim()) }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.text.toString().trim())
                true
            } else false
        }

        btnClear.setOnClickListener {
            searchInput.setText("")
            findViewById<LinearLayout>(R.id.search_results_list).removeAllViews()
            findViewById<TextView>(R.id.search_status).visibility = View.GONE
            btnClear.visibility = View.GONE
        }

        searchInput.requestFocus()
    }

    private fun performSearch(term: String) {
        if (term.isEmpty()) return

        val list = findViewById<LinearLayout>(R.id.search_results_list)
        val statusEl = findViewById<TextView>(R.id.search_status)
        val btnClear = findViewById<Button>(R.id.btn_clear)
        list.removeAllViews()

        val wholeWord = findViewById<CheckBox>(R.id.cb_whole_word).isChecked
        val matches = mutableListOf<JSONObject>()

        if (wholeWord) {
            val escaped = Regex.escape(term)
            val regex = Regex("\\b$escaped\\b", RegexOption.IGNORE_CASE)
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val title = entry.optString("title", "")
                val content = entry.optString("content", "")
                if (regex.containsMatchIn(title) || regex.containsMatchIn(content)) {
                    matches.add(entry)
                }
            }
        } else {
            val lowerTerm = term.lowercase()
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val title = entry.optString("title", "").lowercase()
                val content = entry.optString("content", "").lowercase()
                if (title.contains(lowerTerm) || content.contains(lowerTerm)) {
                    matches.add(entry)
                }
            }
        }

        statusEl.visibility = View.VISIBLE
        btnClear.visibility = View.VISIBLE

        if (matches.isEmpty()) {
            statusEl.text = "No results found for \"$term\"."
            return
        }

        statusEl.text = "${matches.size} result${if (matches.size != 1) "s" else ""} found."
        lastMatchIds = matches.map { it.optString("id", "") }.filter { it.isNotEmpty() }

        for (entry in matches) {
            list.addView(createEntryRow(entry, term, wholeWord))
        }
    }

    private fun createEntryRow(entry: JSONObject, term: String, wholeWord: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = ContextCompat.getDrawable(this@SearchActivity, R.drawable.entry_row_bg)
            isClickable = true
            isFocusable = true
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = entry.optString("title", "")
        val title = TextView(this).apply {
            text = if (titleText.isEmpty()) "Untitled" else highlightTerm(titleText, term, wholeWord)
            setTextColor(ThemeManager.color(C.TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(title)

        val date = entry.optString("date", "")
        val time = entry.optString("time", "")
        val dateTime = listOf(formatDate(date), formatTime(time)).filter { it.isNotEmpty() }.joinToString("  ")
        if (dateTime.isNotEmpty()) {
            val dateView = TextView(this).apply {
                text = dateTime
                setTextColor(ThemeManager.color(C.ACCENT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(8), 0, 0, 0)
            }
            topRow.addView(dateView)
        }

        val entryId = entry.optString("id", "")
        if (entryIdsWithAttachments.contains(entryId)) {
            topRow.addView(TextView(this).apply {
                text = "📎"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(6), 0, 0, 0)
                isClickable = true
                setOnClickListener { openAttachmentScreen(entryId, entry.optString("title", ""), entry.optString("date", "")) }
            })
        }

        row.addView(topRow)

        val content = entry.optString("content", "")
        val snippet = getSearchSnippet(content, term, wholeWord, 120)
        if (snippet.isNotEmpty()) {
            val sub = TextView(this).apply {
                text = highlightTerm(snippet, term, wholeWord)
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            }
            row.addView(sub)
        }

        val categories = jsonArrayToString(entry.optJSONArray("categories"))
        val tags = jsonArrayToString(entry.optJSONArray("tags"))
        val place = entry.optString("placeName", "")
        val meta = listOf(categories, tags, place).filter { it.isNotEmpty() }.joinToString(" · ")
        if (meta.isNotEmpty()) {
            val metaView = TextView(this).apply {
                text = meta
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            }
            row.addView(metaView)
        }

        row.setOnClickListener { finishWithViewEntry(entryId) }

        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) }

        return row
    }

    private fun highlightTerm(text: String, term: String, wholeWord: Boolean): SpannableString {
        val spannable = SpannableString(text)
        val highlightColor = (ThemeManager.color(C.ACCENT) and 0x00FFFFFF) or 0x40000000
        val regex = if (wholeWord) {
            Regex("\\b${Regex.escape(term)}\\b", RegexOption.IGNORE_CASE)
        } else {
            Regex(Regex.escape(term), RegexOption.IGNORE_CASE)
        }
        for (match in regex.findAll(text)) {
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                match.range.first, match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }

    private fun getSearchSnippet(text: String, term: String, wholeWord: Boolean, maxLen: Int): String {
        if (text.isEmpty() || term.isEmpty()) return ""
        val idx = if (wholeWord) {
            val escaped = Regex.escape(term)
            val match = Regex("\\b$escaped\\b", RegexOption.IGNORE_CASE).find(text)
            match?.range?.first ?: -1
        } else {
            text.lowercase().indexOf(term.lowercase())
        }
        if (idx == -1) return text.take(maxLen).let { if (text.length > maxLen) "$it..." else it }
        val start = maxOf(0, idx - 40)
        val end = minOf(text.length, idx + term.length + maxLen - 40)
        var snippet = text.substring(start, end)
        if (start > 0) snippet = "...$snippet"
        if (end < text.length) snippet = "$snippet..."
        return snippet
    }

    private fun jsonArrayToString(arr: JSONArray?): String {
        arr ?: return ""
        val items = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotEmpty()) items.add(s)
        }
        return items.joinToString(", ")
    }

    private fun finishWithViewEntry(entryId: String) {
        EntryViewerActivity.pendingEntryId = entryId
        EntryViewerActivity.pendingEntryIds = lastMatchIds
        EntryViewerActivity.databaseService = ServiceProvider.databaseService
        EntryViewerActivity.bootstrapService = ServiceProvider.bootstrapService
        startActivity(Intent(this, EntryViewerActivity::class.java))
    }

    private fun openAttachmentScreen(entryId: String, title: String, date: String) {
        AttachmentActivity.databaseService = ServiceProvider.databaseService
        AttachmentActivity.bootstrapService = ServiceProvider.bootstrapService
        AttachmentActivity.pendingEntryId = entryId
        AttachmentActivity.pendingEntryTitle = title
        AttachmentActivity.pendingEntryDate = date
        startActivity(Intent(this, AttachmentActivity::class.java))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
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
        if (lastThemeVersion != ThemeManager.themeVersion) {
            recreate()
            return
        }
    }

}
