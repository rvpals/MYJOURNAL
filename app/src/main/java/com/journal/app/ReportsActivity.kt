package com.journal.app

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journal.app.ThemeManager.C
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic var databaseService: DatabaseService? = null
        @JvmStatic var bootstrapService: BootstrapService? = null
    }

    private lateinit var db: DatabaseService
    private lateinit var bs: BootstrapService

    private lateinit var contentContainer: LinearLayout

    private lateinit var dateFromInput: EditText
    private lateinit var dateToInput: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var tagSpinner: Spinner
    private lateinit var templateSpinner: Spinner

    private var categories = listOf<String>()
    private var tags = listOf<String>()
    private var templates = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        db = databaseService ?: run { finish(); return }
        bs = bootstrapService ?: run { finish(); return }
        databaseService = null
        bootstrapService = null

        contentContainer = findViewById(R.id.content_container)
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        loadMetadata()
        buildUI()
    }

    private fun loadMetadata() {
        try {
            val catJson = JSONArray(db.getCategories())
            categories = (0 until catJson.length()).map { catJson.getString(it) }
        } catch (_: Exception) {}

        try {
            val tagJson = JSONArray(db.getAllTags())
            tags = (0 until tagJson.length()).map { tagJson.getString(it) }
        } catch (_: Exception) {}

        try {
            val settings = JSONObject(db.getSettings())
            templates = settings.optJSONArray("reportTemplates") ?: JSONArray()
        } catch (_: Exception) {}
    }

    // ========== UI Construction ==========

    private fun buildUI() {
        contentContainer.removeAllViews()

        addSectionHeader("Filters")

        // Date From
        addLabel("Date From")
        dateFromInput = EditText(this).apply {
            hint = "YYYY-MM-DD"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@ReportsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = linParams().apply { bottomMargin = dp(8) }
            inputType = InputType.TYPE_CLASS_DATETIME
            isSingleLine = true
        }
        contentContainer.addView(dateFromInput)

        // Date To
        addLabel("Date To")
        dateToInput = EditText(this).apply {
            hint = "YYYY-MM-DD"
            textSize = 14f
            setTextColor(ThemeManager.color(C.TEXT))
            setHintTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            background = ContextCompat.getDrawable(this@ReportsActivity, R.drawable.input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = linParams().apply { bottomMargin = dp(8) }
            inputType = InputType.TYPE_CLASS_DATETIME
            isSingleLine = true
        }
        contentContainer.addView(dateToInput)

        // Category
        addLabel("Category")
        val catItems = listOf("All") + categories
        categorySpinner = makeSpinner(catItems, 0)
        contentContainer.addView(categorySpinner)

        // Tag
        addLabel("Tag")
        val tagItems = listOf("All") + tags
        tagSpinner = makeSpinner(tagItems, 0)
        contentContainer.addView(tagSpinner)

        addSpacer()

        // Standard report buttons
        addSectionHeader("Generate Report")

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        }

        btnRow.addView(makeAccentButton("HTML") { generateReport("html") }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) }
        })
        btnRow.addView(makeSecondaryButton("CSV") { generateReport("csv") }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) }
        })
        btnRow.addView(makeSecondaryButton("PDF") { generateReport("pdf") }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
        })
        contentContainer.addView(btnRow)

        addSpacer()

        // Template section
        if (templates.length() > 0) {
            addSectionHeader("Template Report")

            addLabel("Template")
            val tplItems = mutableListOf("-- Select a template --")
            for (i in 0 until templates.length()) {
                val tpl = templates.optJSONObject(i) ?: continue
                tplItems.add(tpl.optString("name", "Untitled"))
            }
            templateSpinner = makeSpinner(tplItems, 0)
            contentContainer.addView(templateSpinner)

            val tplBtnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = linParams().apply { topMargin = dp(8); bottomMargin = dp(8) }
            }

            tplBtnRow.addView(makeAccentButton("Preview") { generateTemplateReport("preview") }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) }
            })
            tplBtnRow.addView(makeSecondaryButton("Download HTML") { generateTemplateReport("download") }.apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            })
            contentContainer.addView(tplBtnRow)
        }

        addSpacer()

        // Report output placeholder
        contentContainer.addView(TextView(this).apply {
            text = "Configure filters and generate a report."
            setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(20), dp(12), dp(20))
            tag = "report_placeholder"
        })
    }

    // ========== Filtering ==========

    private fun getFilteredEntries(): List<JSONObject> {
        val dateFrom = dateFromInput.text.toString().trim()
        val dateTo = dateToInput.text.toString().trim()
        val catIdx = categorySpinner.selectedItemPosition
        val tagIdx = tagSpinner.selectedItemPosition
        val selectedCat = if (catIdx > 0) categories[catIdx - 1] else ""
        val selectedTag = if (tagIdx > 0) tags[tagIdx - 1] else ""

        val allJson = try { JSONArray(db.getEntries()) } catch (_: Exception) { JSONArray() }
        val entries = mutableListOf<JSONObject>()
        for (i in 0 until allJson.length()) {
            allJson.optJSONObject(i)?.let { entries.add(it) }
        }

        var filtered = entries.toList()
        if (dateFrom.isNotEmpty()) filtered = filtered.filter { it.optString("date", "") >= dateFrom }
        if (dateTo.isNotEmpty()) filtered = filtered.filter { it.optString("date", "") <= dateTo }
        if (selectedCat.isNotEmpty()) {
            filtered = filtered.filter { e ->
                val arr = e.optJSONArray("categories") ?: JSONArray()
                (0 until arr.length()).any { arr.optString(it) == selectedCat }
            }
        }
        if (selectedTag.isNotEmpty()) {
            filtered = filtered.filter { e ->
                val arr = e.optJSONArray("tags") ?: JSONArray()
                (0 until arr.length()).any { arr.optString(it) == selectedTag }
            }
        }

        return filtered.sortedBy { it.optString("date", "") }
    }

    // ========== Report Generation ==========

    private fun generateReport(format: String) {
        val entries = getFilteredEntries()
        if (entries.isEmpty()) {
            Toast.makeText(this, "No entries match the selected filters.", Toast.LENGTH_SHORT).show()
            return
        }

        when (format) {
            "html" -> showHtmlReport(entries)
            "csv" -> exportCsv(entries)
            "pdf" -> exportPdf(entries)
        }
    }

    // ========== HTML Report ==========

    private fun showHtmlReport(entries: List<JSONObject>) {
        val dateRange = "${formatDateDisplay(entries.first().optString("date"))} — ${formatDateDisplay(entries.last().optString("date"))}"

        val sb = StringBuilder()
        sb.append("""
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
                body { font-family: sans-serif; background: #3c415e; color: #e0e0f0; padding: 12px; margin: 0; }
                .summary { margin-bottom: 12px; font-size: 14px; }
                table { width: 100%; border-collapse: collapse; font-size: 12px; }
                th { background: #454a6b; color: #1cb3c8; padding: 8px 6px; text-align: left; border-bottom: 2px solid #1cb3c8; }
                td { padding: 6px; border-bottom: 1px solid #555; vertical-align: top; }
                tr:hover { background: #454a6b; }
            </style></head><body>
        """.trimIndent())

        sb.append("<div class='summary'><strong>Total entries:</strong> ${entries.size} | <strong>Date range:</strong> $dateRange</div>")

        sb.append("<table><thead><tr><th>Date</th><th>Time</th><th>Title</th><th>Content</th><th>Categories</th><th>Tags</th><th>Places</th></tr></thead><tbody>")

        for (e in entries) {
            val content = e.optString("content", "")
            val truncated = if (content.length > 100) escapeHtml(content.substring(0, 100)) + "..." else escapeHtml(content)
            val cats = jsonArrayToStringList(e.optJSONArray("categories")).joinToString(", ")
            val tagsList = jsonArrayToStringList(e.optJSONArray("tags")).joinToString(", ")
            val places = formatPlaces(e)

            sb.append("<tr>")
            sb.append("<td>${formatDateDisplay(e.optString("date"))}</td>")
            sb.append("<td>${escapeHtml(formatTimeDisplay(e.optString("time", "")))}</td>")
            sb.append("<td>${escapeHtml(e.optString("title", ""))}</td>")
            sb.append("<td>$truncated</td>")
            sb.append("<td>${escapeHtml(cats)}</td>")
            sb.append("<td>${escapeHtml(tagsList)}</td>")
            sb.append("<td>${escapeHtml(places)}</td>")
            sb.append("</tr>")
        }

        sb.append("</tbody></table></body></html>")

        val html = sb.toString()
        val uri = saveHtmlToDownloads("journal_report.html", html.toByteArray(Charsets.UTF_8))
        if (uri != null) {
            openHtmlInBrowser(uri)
        }
        showHtmlInOutput(html)
    }

    private fun showHtmlInOutput(html: String) {
        removePlaceholder()

        val scrollView = HorizontalScrollView(this).apply {
            layoutParams = linParams().apply {
                height = dp(600)
            }
        }
        val textView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                android.text.Html.fromHtml(html)
            }
            setTextColor(Color.parseColor("#e0e0e0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.parseColor("#3c415e"))
        }
        scrollView.addView(textView)
        contentContainer.addView(scrollView)
    }

    private fun saveHtmlToDownloads(filename: String, data: ByteArray): Uri? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "text/html")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(data) }
                    Toast.makeText(this, "Saved: $filename", Toast.LENGTH_SHORT).show()
                    return uri
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        }
        return null
    }

    private fun openHtmlInBrowser(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/html")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No app found to open HTML file.", Toast.LENGTH_SHORT).show()
        }
    }

    // ========== CSV Export ==========

    private fun exportCsv(entries: List<JSONObject>) {
        val sb = StringBuilder()
        sb.appendLine("\"Date\",\"Time\",\"Title\",\"Content\",\"Categories\",\"Tags\",\"Places\",\"Created\",\"Updated\"")

        for (e in entries) {
            sb.appendLine(listOf(
                csvEscape(e.optString("date", "")),
                csvEscape(e.optString("time", "")),
                csvEscape(e.optString("title", "")),
                csvEscape(e.optString("content", "")),
                csvEscape(jsonArrayToStringList(e.optJSONArray("categories")).joinToString("; ")),
                csvEscape(jsonArrayToStringList(e.optJSONArray("tags")).joinToString("; ")),
                csvEscape(formatPlaces(e)),
                csvEscape(e.optString("dtCreated", "")),
                csvEscape(e.optString("dtUpdated", ""))
            ).joinToString(","))
        }

        val csv = sb.toString()
        saveFileToDownloads("journal_report.csv", csv.toByteArray(Charsets.UTF_8), "text/csv")
    }

    private fun csvEscape(str: String): String {
        if (str.isEmpty()) return "\"\""
        return "\"" + str.replace("\"", "\"\"") + "\""
    }

    // ========== PDF Export ==========

    private fun exportPdf(entries: List<JSONObject>) {
        val doc = PdfDocument()
        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        val margin = 40f
        val maxWidth = pageWidth - margin * 2
        var pageNum = 1
        var y = 50f

        val titlePaint = Paint().apply {
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val headerPaint = Paint().apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val bodyPaint = Paint().apply {
            textSize = 9f
            color = Color.DKGRAY
        }
        val metaPaint = Paint().apply {
            textSize = 9f
            color = Color.GRAY
        }

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        // Title
        canvas.drawText("Journal Report", margin, y, titlePaint)
        y += 22f
        canvas.drawText("Total entries: ${entries.size}", margin, y, bodyPaint)
        y += 16f

        fun newPage(): Canvas {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page = doc.startPage(pageInfo)
            y = 40f
            return page.canvas
        }

        for (e in entries) {
            if (y > pageHeight - 80) {
                canvas = newPage()
            }

            // Entry header: date + title
            val dateTime = "${e.optString("date", "")}${if (e.optString("time", "").isNotEmpty()) " " + e.optString("time") else ""}"
            val entryTitle = "$dateTime — ${e.optString("title", "Untitled")}"
            canvas.drawText(entryTitle, margin, y, headerPaint)
            y += 14f

            // Content (truncated)
            val content = e.optString("content", "")
            if (content.isNotEmpty()) {
                val lines = wrapText(content, bodyPaint, maxWidth, 6)
                for (line in lines) {
                    if (y > pageHeight - 40) canvas = newPage()
                    canvas.drawText(line, margin, y, bodyPaint)
                    y += 12f
                }
            }

            // Categories
            val cats = jsonArrayToStringList(e.optJSONArray("categories"))
            if (cats.isNotEmpty()) {
                if (y > pageHeight - 40) canvas = newPage()
                canvas.drawText("Categories: ${cats.joinToString(", ")}", margin, y, metaPaint)
                y += 12f
            }

            // Tags
            val tagsList = jsonArrayToStringList(e.optJSONArray("tags"))
            if (tagsList.isNotEmpty()) {
                if (y > pageHeight - 40) canvas = newPage()
                canvas.drawText("Tags: ${tagsList.joinToString(", ")}", margin, y, metaPaint)
                y += 12f
            }

            // Places
            val places = formatPlaces(e)
            if (places.isNotEmpty()) {
                if (y > pageHeight - 40) canvas = newPage()
                val placeLines = wrapText("Places: $places", metaPaint, maxWidth, 2)
                for (line in placeLines) {
                    canvas.drawText(line, margin, y, metaPaint)
                    y += 12f
                }
            }

            y += 8f
        }

        doc.finishPage(page)

        val bytes = java.io.ByteArrayOutputStream()
        doc.writeTo(bytes)
        doc.close()

        saveFileToDownloads("journal_report.pdf", bytes.toByteArray(), "application/pdf")
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        val lines = mutableListOf<String>()
        val cleanText = text.replace("\n", " ").replace("\r", "")
        var remaining = cleanText

        while (remaining.isNotEmpty() && lines.size < maxLines) {
            val count = paint.breakText(remaining, true, maxWidth, null)
            if (count <= 0) break
            var breakAt = count
            if (breakAt < remaining.length) {
                val lastSpace = remaining.lastIndexOf(' ', breakAt - 1)
                if (lastSpace > breakAt / 2) breakAt = lastSpace + 1
            }
            lines.add(remaining.substring(0, breakAt).trim())
            remaining = remaining.substring(breakAt).trim()
        }
        if (remaining.isNotEmpty() && lines.isNotEmpty()) {
            lines[lines.size - 1] = lines.last() + "..."
        }
        return lines
    }

    // ========== Single Entry PDF ==========

    fun exportSingleEntryPdf(entry: JSONObject) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val maxWidth = pageWidth - margin * 2
        var pageNum = 1
        var y = 50f

        val titlePaint = Paint().apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val datePaint = Paint().apply {
            textSize = 10f
            color = Color.GRAY
        }
        val sectionPaint = Paint().apply {
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
        }
        val bodyPaint = Paint().apply {
            textSize = 10f
            color = Color.DKGRAY
        }
        val metaPaint = Paint().apply {
            textSize = 8f
            color = Color.GRAY
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        fun newPage(): Canvas {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page = doc.startPage(pageInfo)
            y = 40f
            return page.canvas
        }

        // Title
        val title = entry.optString("title", "Untitled")
        val titleLines = wrapText(title, titlePaint, maxWidth, 3)
        for (line in titleLines) {
            canvas.drawText(line, margin, y, titlePaint)
            y += 20f
        }

        // Date/Time
        val dateTime = "${formatDateDisplay(entry.optString("date", ""))}${if (entry.optString("time", "").isNotEmpty()) " " + formatTimeDisplay(entry.optString("time")) else ""}"
        if (dateTime.trim().isNotEmpty()) {
            canvas.drawText(dateTime.trim(), margin, y, datePaint)
            y += 14f
        }

        // Line separator
        y += 4f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 10f

        // Content
        val content = entry.optString("content", "")
        if (content.isNotEmpty()) {
            canvas.drawText("Content", margin, y, sectionPaint)
            y += 14f
            val contentLines = wrapText(content, bodyPaint, maxWidth, 100)
            for (line in contentLines) {
                if (y > pageHeight - 40) canvas = newPage()
                canvas.drawText(line, margin, y, bodyPaint)
                y += 14f
            }
            y += 6f
        }

        // Categories
        val cats = jsonArrayToStringList(entry.optJSONArray("categories"))
        if (cats.isNotEmpty()) {
            if (y > pageHeight - 60) canvas = newPage()
            canvas.drawText("Categories", margin, y, sectionPaint)
            y += 14f
            canvas.drawText(cats.joinToString(", "), margin, y, bodyPaint)
            y += 14f
        }

        // Tags
        val tagsList = jsonArrayToStringList(entry.optJSONArray("tags"))
        if (tagsList.isNotEmpty()) {
            if (y > pageHeight - 60) canvas = newPage()
            canvas.drawText("Tags", margin, y, sectionPaint)
            y += 14f
            canvas.drawText(tagsList.joinToString(", "), margin, y, bodyPaint)
            y += 14f
        }

        // Place
        val placeName = entry.optString("placeName", "")
        if (placeName.isNotEmpty()) {
            if (y > pageHeight - 60) canvas = newPage()
            canvas.drawText("Place", margin, y, sectionPaint)
            y += 14f
            canvas.drawText(placeName, margin, y, bodyPaint)
            y += 14f
        }

        // Locations
        val locs = entry.optJSONArray("locations") ?: JSONArray()
        if (locs.length() > 0) {
            if (y > pageHeight - 60) canvas = newPage()
            canvas.drawText("Locations", margin, y, sectionPaint)
            y += 14f
            for (j in 0 until locs.length()) {
                val loc = locs.optJSONObject(j) ?: continue
                val parts = mutableListOf<String>()
                if (loc.optString("name", "").isNotEmpty()) parts.add(loc.optString("name"))
                if (loc.optString("address", "").isNotEmpty()) parts.add(loc.optString("address"))
                if (!loc.isNull("lat")) parts.add("(${loc.optDouble("lat")}, ${loc.optDouble("lng")})")
                val locStr = parts.joinToString(" — ")
                val locLines = wrapText(locStr, bodyPaint, maxWidth, 3)
                for (line in locLines) {
                    if (y > pageHeight - 40) canvas = newPage()
                    canvas.drawText(line, margin, y, bodyPaint)
                    y += 13f
                }
            }
            y += 4f
        }

        // Weather
        val weather = entry.optJSONObject("weather")
        if (weather != null) {
            if (y > pageHeight - 60) canvas = newPage()
            canvas.drawText("Weather", margin, y, sectionPaint)
            y += 14f
            canvas.drawText(formatWeather(weather), margin, y, bodyPaint)
            y += 14f
        }

        // Timestamps
        if (y > pageHeight - 60) canvas = newPage()
        y += 8f
        val dtCreated = entry.optString("dtCreated", "")
        if (dtCreated.isNotEmpty()) {
            canvas.drawText("Created: ${formatTimestamp(dtCreated)}", margin, y, metaPaint)
            y += 12f
        }
        val dtUpdated = entry.optString("dtUpdated", "")
        if (dtUpdated.isNotEmpty()) {
            canvas.drawText("Updated: ${formatTimestamp(dtUpdated)}", margin, y, metaPaint)
        }

        doc.finishPage(page)

        val bytes = java.io.ByteArrayOutputStream()
        doc.writeTo(bytes)
        doc.close()

        val filename = (entry.optString("title", "entry"))
            .replace(Regex("[^a-zA-Z0-9 ]"), "")
            .replace(Regex("\\s+"), "_") + ".pdf"

        saveFileToDownloads(filename, bytes.toByteArray(), "application/pdf")
    }

    // ========== Template Reports ==========

    private fun generateTemplateReport(action: String) {
        if (!::templateSpinner.isInitialized) return
        val idx = templateSpinner.selectedItemPosition
        if (idx <= 0) {
            Toast.makeText(this, "Please select a template.", Toast.LENGTH_SHORT).show()
            return
        }

        val tpl = templates.optJSONObject(idx - 1)
        if (tpl == null) {
            Toast.makeText(this, "Template not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val entries = getFilteredEntries()
        if (entries.isEmpty()) {
            Toast.makeText(this, "No entries match the selected filters.", Toast.LENGTH_SHORT).show()
            return
        }

        val templateHtml = tpl.optString("html", "")
        val allHtml = entries.joinToString("\n<hr style=\"page-break-after:always; margin:2rem 0;\">\n") { e ->
            applyTemplateToEntry(templateHtml, e)
        }

        when (action) {
            "download" -> {
                val filename = tpl.optString("name", "template")
                    .replace(Regex("[^a-zA-Z0-9]"), "_") + "_report.html"
                saveFileToDownloads(filename, allHtml.toByteArray(Charsets.UTF_8), "text/html")
            }
            "preview" -> showHtmlInOutput(wrapTemplateHtml(allHtml))
        }
    }

    private fun wrapTemplateHtml(bodyHtml: String): String {
        return """<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body { font-family: sans-serif; padding: 12px; margin: 0; }</style></head>
            <body>$bodyHtml</body></html>"""
    }

    private fun applyTemplateToEntry(templateHtml: String, entry: JSONObject): String {
        val locs = entry.optJSONArray("locations") ?: JSONArray()
        val locStrs = (0 until locs.length()).mapNotNull { i ->
            val loc = locs.optJSONObject(i) ?: return@mapNotNull null
            val parts = mutableListOf<String>()
            if (loc.optString("name", "").isNotEmpty()) parts.add(loc.optString("name"))
            if (loc.optString("address", "").isNotEmpty()) parts.add(loc.optString("address"))
            if (!loc.isNull("lat")) parts.add("(${loc.optDouble("lat")}, ${loc.optDouble("lng")})")
            parts.joinToString(" — ")
        }

        val placesFormatted = (entry.optString("placeName", "") +
                if (locStrs.isNotEmpty()) (if (entry.optString("placeName", "").isNotEmpty()) ": " else "") + locStrs.joinToString("; ")
                else "")

        val placeAddresses = (0 until locs.length()).mapNotNull { i ->
            val loc = locs.optJSONObject(i) ?: return@mapNotNull null
            listOfNotNull(
                loc.optString("name", "").ifEmpty { null },
                loc.optString("address", "").ifEmpty { null }
            ).joinToString(" — ").ifEmpty { null }
        }.joinToString("; ")

        val placeCoords = (0 until locs.length()).mapNotNull { i ->
            val loc = locs.optJSONObject(i) ?: return@mapNotNull null
            if (!loc.isNull("lat")) "${loc.optDouble("lat")},${loc.optDouble("lng")}" else null
        }.joinToString("; ")

        val weather = entry.optJSONObject("weather")
        val weatherStr = if (weather != null) formatWeather(weather) else ""
        val weatherTemp = if (weather != null) "${weather.optDouble("temp")}°${if (weather.optString("unit") == "celsius") "C" else "F"}" else ""
        val weatherDesc = weather?.optString("description", "") ?: ""

        val replacements = mapOf(
            "<%ID%>" to (entry.optString("id", "")),
            "<%TITLE%>" to escapeHtml(entry.optString("title", "")),
            "<%DATE%>" to formatDateDisplay(entry.optString("date", "")),
            "<%TIME%>" to formatTimeDisplay(entry.optString("time", "")),
            "<%CONTENT%>" to escapeHtml(entry.optString("content", "")).replace("\n", "<br>"),

            "<%CATEGORIES%>" to jsonArrayToStringList(entry.optJSONArray("categories")).joinToString(", "),
            "<%TAGS%>" to jsonArrayToStringList(entry.optJSONArray("tags")).joinToString(", "),
            "<%PLACES%>" to escapeHtml(placesFormatted),
            "<%PLACE_NAMES%>" to escapeHtml(entry.optString("placeName", "")),
            "<%PLACE_ADDRESSES%>" to escapeHtml(placeAddresses),
            "<%PLACE_COORDS%>" to placeCoords,
            "<%WEATHER%>" to escapeHtml(weatherStr),
            "<%WEATHER_TEMP%>" to escapeHtml(weatherTemp),
            "<%WEATHER_DESC%>" to escapeHtml(weatherDesc),
            "<%DT_CREATED%>" to formatTimestamp(entry.optString("dtCreated", "")),
            "<%DT_UPDATED%>" to formatTimestamp(entry.optString("dtUpdated", ""))
        )

        var result = templateHtml
        for ((tag, value) in replacements) {
            result = result.replace(tag, value)
        }
        return result
    }

    // ========== File Saving ==========

    private fun saveFileToDownloads(filename: String, data: ByteArray, mimeType: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { it.write(data) }
                }
            }
            Toast.makeText(this, "Saved: $filename", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ========== Helpers ==========

    private fun removePlaceholder() {
        val placeholder = contentContainer.findViewWithTag<View>("report_placeholder")
        if (placeholder != null) contentContainer.removeView(placeholder)
        val existing = (0 until contentContainer.childCount).mapNotNull { i ->
            val v = contentContainer.getChildAt(i)
            if (v is HorizontalScrollView) v else null
        }
        for (sv in existing) contentContainer.removeView(sv)
    }

    private fun formatDateDisplay(dateStr: String): String {
        if (dateStr.isEmpty()) return ""
        val fmt = bs.get("ev_date_format") ?: "MMMM d, yyyy"
        return try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr) ?: return dateStr
            SimpleDateFormat(fmt, Locale.US).format(d)
        } catch (_: Exception) { dateStr }
    }

    private fun formatTimeDisplay(timeStr: String): String {
        if (timeStr.isEmpty()) return ""
        val fmt = bs.get("ev_time_format") ?: "h:mm a"
        return try {
            val d = SimpleDateFormat("HH:mm", Locale.US).parse(timeStr) ?: return timeStr
            SimpleDateFormat(fmt, Locale.US).format(d)
        } catch (_: Exception) { timeStr }
    }

    private fun formatTimestamp(ts: String): String {
        if (ts.isEmpty()) return ""
        val dateFmt = bs.get("ev_date_format") ?: "MMMM d, yyyy"
        val timeFmt = bs.get("ev_time_format") ?: "h:mm a"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            val d = sdf.parse(ts) ?: return ts
            SimpleDateFormat("$dateFmt $timeFmt", Locale.US).format(d)
        } catch (_: Exception) { ts }
    }

    private fun formatWeather(w: JSONObject): String {
        val desc = w.optString("description", "")
        val temp = w.optDouble("temp", 0.0)
        val feelsLike = w.optDouble("feelsLike", 0.0)
        val unit = if (w.optString("unit") == "celsius") "C" else "F"
        val humidity = w.optInt("humidity", 0)
        val wind = w.optDouble("windSpeed", 0.0)
        return "$desc, $temp°$unit (feels $feelsLike°$unit), Humidity $humidity%, Wind $wind mph"
    }

    private fun formatPlaces(entry: JSONObject): String {
        val placeName = entry.optString("placeName", "")
        val locs = entry.optJSONArray("locations") ?: JSONArray()
        if (placeName.isEmpty() && locs.length() == 0) return ""

        val locStrs = (0 until locs.length()).mapNotNull { i ->
            val loc = locs.optJSONObject(i) ?: return@mapNotNull null
            val parts = mutableListOf<String>()
            if (loc.optString("name", "").isNotEmpty()) parts.add(loc.optString("name"))
            if (loc.optString("address", "").isNotEmpty()) parts.add(loc.optString("address"))
            if (!loc.isNull("lat")) parts.add("[${loc.optDouble("lat")},${loc.optDouble("lng")}]")
            parts.joinToString(" — ")
        }

        return placeName + if (locStrs.isNotEmpty()) ": " + locStrs.joinToString("; ") else ""
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotEmpty() }
    }

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
    }

    private fun addSectionHeader(text: String) {
        contentContainer.addView(TextView(this).apply {
            this.text = text
            setTextColor(ThemeManager.color(C.ACCENT))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
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

    private fun makeAccentButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@ReportsActivity, R.drawable.btn_accent)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onClick() }
        }
    }

    private fun makeSecondaryButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 13f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.TEXT))
            background = ContextCompat.getDrawable(this@ReportsActivity, R.drawable.btn_secondary)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onClick() }
        }
    }

    private fun makeSpinner(items: List<String>, selectedIdx: Int): Spinner {
        val spinner = Spinner(this).apply {
            background = ContextCompat.getDrawable(this@ReportsActivity, R.drawable.spinner_bg)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            layoutParams = linParams().apply { bottomMargin = dp(8) }
        }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    textSize = 14f
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(ThemeManager.color(C.TEXT))
                    setBackgroundColor(ThemeManager.color(C.INPUT_BG))
                    textSize = 14f
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                }
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(selectedIdx.coerceIn(0, items.size - 1))
        return spinner
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
