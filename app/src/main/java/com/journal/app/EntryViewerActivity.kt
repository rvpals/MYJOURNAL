package com.journal.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Base64
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window

import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journal.app.ThemeManager.C
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class EntryViewerActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic
        var pendingEntryId: String? = null
        @JvmStatic
        var pendingEntryIds: List<String>? = null
        @JvmStatic
        var databaseService: DatabaseService? = null
        @JvmStatic
        var bootstrapService: BootstrapService? = null
    }

    private var currentEntryId = ""
    private var entryIds = listOf<String>()
    private var currentEntry: JSONObject? = null

    private var fontFamily: String = ""
    private var fontSizeSp: Float = 0f

    private var lightboxImages = JSONArray()
    private var lightboxIndex = 0
    private var lightboxDialog: Dialog? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_entry_viewer)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        currentEntryId = pendingEntryId ?: ""
        entryIds = pendingEntryIds ?: listOf()
        pendingEntryId = null
        pendingEntryIds = null

        val bs = bootstrapService
        bootstrapService = null
        fontFamily = bs?.get("ev_font_family") ?: ""
        fontSizeSp = remToPx(bs?.get("ev_font_size") ?: "")

        if (currentEntryId.isEmpty()) { finish(); return }

        setupNavbar()
        setupActions()
        setupMiscToggle()
        setupSwipe()
        loadAndShowEntry()
    }

    private fun setupNavbar() {
        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.ev_btn_back_bottom).setOnClickListener { finish() }
    }

    private fun setupActions() {
        findViewById<Button>(R.id.ev_btn_pin).setOnClickListener { togglePin() }
        findViewById<Button>(R.id.ev_btn_lock).setOnClickListener { toggleLock() }
        findViewById<Button>(R.id.ev_btn_edit).setOnClickListener { editEntry() }
        findViewById<Button>(R.id.ev_btn_delete).setOnClickListener { deleteEntry() }
        findViewById<Button>(R.id.ev_btn_prev).setOnClickListener { navigatePrev() }
        findViewById<Button>(R.id.ev_btn_next).setOnClickListener { navigateNext() }
    }

    private fun setupMiscToggle() {
        val header = findViewById<LinearLayout>(R.id.ev_misc_header)
        val body = findViewById<LinearLayout>(R.id.ev_misc_body)
        val arrow = findViewById<TextView>(R.id.ev_misc_arrow)
        var expanded = true

        header.setOnClickListener {
            expanded = !expanded
            body.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.text = if (expanded) "▼" else "◀"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipe() {
        val scrollView = findViewById<View>(R.id.content_container)
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) > 60 && abs(dy) < 80) {
                    if (dx < 0) navigateNext() else navigatePrev()
                    return true
                }
                return false
            }
        })
        scrollView.setOnTouchListener { _, event -> detector.onTouchEvent(event); false }
    }

    private fun loadAndShowEntry() {
        val db = databaseService ?: return
        val json = db.getEntryById(currentEntryId)
        val entry = try { JSONObject(json) } catch (_: Exception) { null }
        if (entry == null || !entry.has("id") || entry.optString("id").isEmpty()) {
            finish(); return
        }
        currentEntry = entry
        renderEntry(entry)
        updateNavButtons()
    }

    private fun renderEntry(entry: JSONObject) {
        val date = entry.optString("date", "")
        val time = entry.optString("time", "")
        val title = entry.optString("title", "")
        val content = entry.optString("content", "")
        val pinned = entry.optBoolean("pinned", false)
        val locked = entry.optBoolean("locked", false)

        // Header chips
        val dateView = findViewById<TextView>(R.id.ev_date)
        val timeView = findViewById<TextView>(R.id.ev_time)
        dateView.visibility = if (date.isNotEmpty()) { dateView.text = "📅 $date"; View.VISIBLE } else View.GONE
        timeView.visibility = if (time.isNotEmpty()) { timeView.text = "🕐 $time"; View.VISIBLE } else View.GONE

        // Title
        val titleView = findViewById<TextView>(R.id.ev_title)
        titleView.text = if (title.isNotEmpty()) title else "Untitled"
        applyFontToView(titleView, titleMode = true)

        // Pin button
        val pinBtn = findViewById<Button>(R.id.ev_btn_pin)
        pinBtn.alpha = if (pinned) 1.0f else 0.4f

        // Lock button + edit state
        val lockBtn = findViewById<Button>(R.id.ev_btn_lock)
        lockBtn.text = if (locked) "🔒" else "🔓"
        val editBtn = findViewById<Button>(R.id.ev_btn_edit)
        editBtn.alpha = if (locked) 0.3f else 1.0f
        editBtn.isEnabled = !locked

        // Content
        val contentLabel = findViewById<TextView>(R.id.ev_content_label)
        val contentView = findViewById<TextView>(R.id.ev_content)
        if (content.isNotEmpty()) {
            contentLabel.visibility = View.VISIBLE
            contentView.visibility = View.VISIBLE
            contentView.text = content
            applyFontToView(contentView)
        } else {
            contentLabel.visibility = View.GONE
            contentView.visibility = View.GONE
        }

        // Images
        renderImages(entry)

        // Misc info
        renderMiscInfo(entry)
    }


    private fun renderImages(entry: JSONObject) {
        val images = entry.optJSONArray("images") ?: JSONArray()
        val label = findViewById<TextView>(R.id.ev_images_label)
        val grid = findViewById<LinearLayout>(R.id.ev_images_grid)

        if (images.length() == 0) {
            label.visibility = View.GONE
            grid.visibility = View.GONE
            return
        }

        label.visibility = View.VISIBLE
        grid.visibility = View.VISIBLE
        grid.removeAllViews()

        val scroll = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        for (i in 0 until images.length()) {
            val img = images.optJSONObject(i) ?: continue
            val thumbData = img.optString("thumb", "")
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(90), dp(90)).apply {
                    marginEnd = dp(6)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = ContextCompat.getDrawable(this@EntryViewerActivity, R.drawable.input_bg)
                setPadding(dp(2), dp(2), dp(2), dp(2))
            }

            loadBase64Image(imageView, thumbData)

            val index = i
            imageView.setOnClickListener { openLightbox(index, images) }
            row.addView(imageView)
        }

        scroll.addView(row)
        grid.addView(scroll)
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

    private fun openLightbox(index: Int, images: JSONArray) {
        lightboxImages = images
        lightboxIndex = index
        showLightboxDialog()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showLightboxDialog() {
        val img = lightboxImages.optJSONObject(lightboxIndex) ?: return

        lightboxDialog?.dismiss()

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        lightboxDialog = dialog

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E6000000"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(16), dp(40), dp(16), dp(16))
        }

        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val fullData = img.optString("data", "")
        val thumbData = img.optString("thumb", "")
        loadBase64Image(imageView, if (fullData.isNotEmpty()) fullData else thumbData)
        root.addView(imageView)

        // Nav row
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }

        val btnPrev = Button(this).apply {
            text = "◀"
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#44FFFFFF"))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            isEnabled = lightboxIndex > 0
            alpha = if (lightboxIndex > 0) 1f else 0.3f
        }
        val counter = TextView(this).apply {
            text = "${lightboxIndex + 1} / ${lightboxImages.length()}"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(20), 0, dp(20), 0)
        }
        val btnNext = Button(this).apply {
            text = "▶"
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#44FFFFFF"))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            isEnabled = lightboxIndex < lightboxImages.length() - 1
            alpha = if (lightboxIndex < lightboxImages.length() - 1) 1f else 0.3f
        }
        val btnClose = Button(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#44FFFFFF"))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(20) }
        }

        btnPrev.setOnClickListener {
            if (lightboxIndex > 0) { lightboxIndex--; dialog.dismiss(); showLightboxDialog() }
        }
        btnNext.setOnClickListener {
            if (lightboxIndex < lightboxImages.length() - 1) { lightboxIndex++; dialog.dismiss(); showLightboxDialog() }
        }
        btnClose.setOnClickListener { dialog.dismiss() }

        navRow.addView(btnPrev)
        navRow.addView(counter)
        navRow.addView(btnNext)
        navRow.addView(btnClose)
        root.addView(navRow)

        // Swipe on image
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (abs(dx) > 50 && abs(e2.y - e1.y) < 80) {
                    if (dx < 0 && lightboxIndex < lightboxImages.length() - 1) {
                        lightboxIndex++; dialog.dismiss(); showLightboxDialog()
                    } else if (dx > 0 && lightboxIndex > 0) {
                        lightboxIndex--; dialog.dismiss(); showLightboxDialog()
                    }
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                dialog.dismiss()
                return true
            }
        })
        imageView.setOnTouchListener { _, event -> detector.onTouchEvent(event); true }

        dialog.setContentView(root)
        dialog.show()
    }

    private fun renderMiscInfo(entry: JSONObject) {
        val miscCard = findViewById<LinearLayout>(R.id.ev_misc_card)
        val miscBody = findViewById<LinearLayout>(R.id.ev_misc_body)
        miscBody.removeAllViews()

        val categories = jsonArrayToString(entry.optJSONArray("categories"))
        val tags = jsonArrayToString(entry.optJSONArray("tags"))
        val placeName = entry.optString("placeName", "")
        val locations = entry.optJSONArray("locations")
        val weather = entry.opt("weather")
        val dtCreated = entry.optString("dtCreated", "")
        val dtUpdated = entry.optString("dtUpdated", "")

        var hasMisc = false

        if (categories.isNotEmpty()) {
            miscBody.addView(createMiscRow("📁 Categories", categories))
            hasMisc = true
        }
        if (tags.isNotEmpty()) {
            miscBody.addView(createMiscRow("🏷️ Tags", tags))
            hasMisc = true
        }
        if (placeName.isNotEmpty()) {
            miscBody.addView(createMiscRow("📍 Place", placeName))
            hasMisc = true
        }
        if (locations != null && locations.length() > 0) {
            val locView = buildLocationView(locations)
            if (locView != null) {
                miscBody.addView(locView)
                hasMisc = true
            }
        }
        if (weather is JSONObject) {
            val weatherText = formatWeather(weather)
            if (weatherText.isNotEmpty()) {
                miscBody.addView(createMiscRow("🌤️ Weather", weatherText))
                hasMisc = true
            }
        }
        if (dtCreated.isNotEmpty()) {
            miscBody.addView(createMiscRow("Created", dtCreated))
            hasMisc = true
        }
        if (dtUpdated.isNotEmpty()) {
            miscBody.addView(createMiscRow("Updated", dtUpdated))
            hasMisc = true
        }

        miscCard.visibility = if (hasMisc) View.VISIBLE else View.GONE
    }

    private fun createMiscRow(label: String, value: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(TextView(this).apply {
            text = label
            setTextColor(ThemeManager.color(C.ACCENT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
        })
        row.addView(TextView(this).apply {
            text = value
            setTextColor(ThemeManager.color(C.TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2), 0, 0)
        })
        return row
    }

    private fun buildLocationView(locations: JSONArray): View? {
        val items = mutableListOf<Triple<String, Double, Double>>()
        for (i in 0 until locations.length()) {
            val loc = locations.optJSONObject(i) ?: continue
            val name = loc.optString("name", "")
            val address = loc.optString("address", "")
            val lat = loc.optDouble("lat", Double.NaN)
            val lng = loc.optDouble("lng", Double.NaN)
            val display = when {
                name.isNotEmpty() && address.isNotEmpty() -> "$name — $address"
                name.isNotEmpty() -> name
                address.isNotEmpty() -> address
                else -> ""
            }
            if (display.isNotEmpty() || (!lat.isNaN() && !lng.isNaN())) {
                items.add(Triple(display, lat, lng))
            }
        }
        if (items.isEmpty()) return null

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(TextView(this).apply {
            text = "🗺️ Locations"
            setTextColor(ThemeManager.color(C.ACCENT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
        })

        val fullText = items.joinToString("\n") { (display, lat, lng) ->
            val coords = if (!lat.isNaN() && !lng.isNaN()) " (${"%.4f".format(lat)}, ${"%.4f".format(lng)})" else ""
            "$display$coords"
        }
        val spannable = SpannableString(fullText)

        var offset = 0
        for ((display, lat, lng) in items) {
            val coords = if (!lat.isNaN() && !lng.isNaN()) " (${"%.4f".format(lat)}, ${"%.4f".format(lng)})" else ""
            val line = "$display$coords"
            if (!lat.isNaN() && !lng.isNaN()) {
                val coordStart = offset + display.length
                val coordEnd = offset + line.length
                val clickLat = lat
                val clickLng = lng
                val clickLabel = display
                spannable.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        val uri = if (clickLabel.isNotEmpty()) {
                            Uri.parse("geo:$clickLat,$clickLng?q=$clickLat,$clickLng(${Uri.encode(clickLabel)})")
                        } else {
                            Uri.parse("geo:$clickLat,$clickLng?q=$clickLat,$clickLng")
                        }
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                }, coordStart, coordEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            offset += line.length + 1
        }

        row.addView(TextView(this).apply {
            text = spannable
            movementMethod = LinkMovementMethod.getInstance()
            setTextColor(ThemeManager.color(C.TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(2), 0, 0)
        })
        return row
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

    // ===== Actions =====

    private fun togglePin() {
        val db = databaseService ?: return
        val entry = currentEntry ?: return
        val pinned = !entry.optBoolean("pinned", false)
        db.updateEntry(currentEntryId, JSONObject().apply { put("pinned", pinned) }.toString())
        loadAndShowEntry()
    }

    private fun toggleLock() {
        val entry = currentEntry ?: return
        val db = databaseService ?: return
        val newLocked = !entry.optBoolean("locked", false)
        val msg = if (newLocked) "Are you sure you want to lock this entry?" else "Are you sure you want to unlock this entry?"
        AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("Yes") { _, _ ->
                db.updateEntry(currentEntryId, JSONObject().apply { put("locked", newLocked) }.toString())
                loadAndShowEntry()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun editEntry() {
        val entry = currentEntry ?: return
        if (entry.optBoolean("locked", false)) return
        EntryFormActivity.databaseService = databaseService
        EntryFormActivity.bootstrapService = ServiceProvider.bootstrapService
        EntryFormActivity.weatherService = ServiceProvider.weatherService
        EntryFormActivity.pendingEntryId = currentEntryId
        startActivity(Intent(this, EntryFormActivity::class.java))
    }

    private fun deleteEntry() {
        val db = databaseService ?: return
        AlertDialog.Builder(this)
            .setMessage("Are you sure you want to delete this entry?")
            .setPositiveButton("Delete") { _, _ ->
                db.deleteEntryById(currentEntryId)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===== Navigation =====

    private fun navigatePrev() {
        val idx = entryIds.indexOf(currentEntryId)
        if (idx <= 0) return
        currentEntryId = entryIds[idx - 1]
        loadAndShowEntry()
    }

    private fun navigateNext() {
        val idx = entryIds.indexOf(currentEntryId)
        if (idx < 0 || idx >= entryIds.size - 1) return
        currentEntryId = entryIds[idx + 1]
        loadAndShowEntry()
    }

    private fun updateNavButtons() {
        val navRow = findViewById<LinearLayout>(R.id.ev_nav_row)
        val prevBtn = findViewById<Button>(R.id.ev_btn_prev)
        val nextBtn = findViewById<Button>(R.id.ev_btn_next)
        val counter = findViewById<TextView>(R.id.ev_nav_counter)

        if (entryIds.size <= 1) {
            navRow.visibility = View.GONE
            return
        }

        navRow.visibility = View.VISIBLE
        val idx = entryIds.indexOf(currentEntryId)
        prevBtn.isEnabled = idx > 0
        prevBtn.alpha = if (idx > 0) 1f else 0.3f
        nextBtn.isEnabled = idx < entryIds.size - 1
        nextBtn.alpha = if (idx < entryIds.size - 1) 1f else 0.3f
        counter.text = "${idx + 1} / ${entryIds.size}"
    }

    // ===== Helpers =====

    private fun jsonArrayToString(arr: JSONArray?): String {
        arr ?: return ""
        val items = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotEmpty()) items.add(s)
        }
        return items.joinToString(", ")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        lightboxDialog?.dismiss()
    }

    private fun remToPx(rem: String): Float {
        if (rem.isEmpty()) return 0f
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
            else -> {
                try { Typeface.create(primary, Typeface.NORMAL) } catch (_: Exception) { Typeface.DEFAULT }
            }
        }
    }

    private fun applyFontToView(view: TextView, titleMode: Boolean = false) {
        if (fontFamily.isNotEmpty()) {
            view.typeface = if (titleMode) {
                Typeface.create(cssFontFamilyToTypeface(fontFamily), Typeface.BOLD)
            } else {
                cssFontFamilyToTypeface(fontFamily)
            }
        }
        if (fontSizeSp > 0f) {
            val size = if (titleMode) fontSizeSp * 1.3f else fontSizeSp
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        }
    }

    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
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
