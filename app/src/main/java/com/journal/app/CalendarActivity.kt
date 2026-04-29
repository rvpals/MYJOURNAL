package com.journal.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.journal.app.ThemeManager.C
import org.json.JSONArray
import org.json.JSONException
import java.util.Calendar

class CalendarActivity : AppCompatActivity() {

    private var lastThemeVersion = 0

    companion object {
        @JvmStatic
        var pendingData: String? = null
    }

    private val monthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    private val dayLabels = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private var entries = JSONArray()
    private var viewYear = 0
    private var viewMonth = 0
    private var selectedDate: String? = null
    private var selectedDayEntryIds = listOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        val json = pendingData
        pendingData = null
        if (json != null) {
            try {
                entries = JSONArray(json)
            } catch (_: JSONException) {}
        }

        val today = Calendar.getInstance()
        viewYear = today.get(Calendar.YEAR)
        viewMonth = today.get(Calendar.MONTH)

        findViewById<Button>(R.id.cal_btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.cal_btn_today).setOnClickListener { goToday() }
        findViewById<Button>(R.id.cal_btn_prev).setOnClickListener { navigate(-1) }
        findViewById<Button>(R.id.cal_btn_next).setOnClickListener { navigate(1) }

        renderWeekdayHeader()
        renderMonth()
    }

    private fun goToday() {
        val today = Calendar.getInstance()
        viewYear = today.get(Calendar.YEAR)
        viewMonth = today.get(Calendar.MONTH)
        selectedDate = formatDate(viewYear, viewMonth + 1, today.get(Calendar.DAY_OF_MONTH))
        renderMonth()
        renderResults()
    }

    private fun navigate(direction: Int) {
        viewMonth += direction
        if (viewMonth < 0) {
            viewMonth = 11
            viewYear--
        } else if (viewMonth > 11) {
            viewMonth = 0
            viewYear++
        }
        renderMonth()
    }

    private fun renderWeekdayHeader() {
        val header = findViewById<LinearLayout>(R.id.cal_weekday_header)
        header.removeAllViews()
        for (label in dayLabels) {
            val tv = TextView(this).apply {
                text = label
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            tv.layoutParams = params
            header.addView(tv)
        }
    }

    private fun renderMonth() {
        findViewById<TextView>(R.id.cal_month_label).text =
            "${monthNames[viewMonth]} $viewYear"

        val grid = findViewById<LinearLayout>(R.id.cal_grid)
        grid.removeAllViews()

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, viewYear)
            set(Calendar.MONTH, viewMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        var startDow = cal.get(Calendar.DAY_OF_WEEK) - 2 // Monday=0
        if (startDow < 0) startDow = 6

        val todayCal = Calendar.getInstance()
        val todayStr = formatDate(
            todayCal.get(Calendar.YEAR),
            todayCal.get(Calendar.MONTH) + 1,
            todayCal.get(Calendar.DAY_OF_MONTH)
        )

        val countMap = buildCountMap()

        var day = 1
        var cellIndex = 0
        val totalCells = startDow + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(2) }
            }

            for (col in 0 until 7) {
                if (cellIndex < startDow || day > daysInMonth) {
                    val empty = View(this)
                    empty.layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
                    rowLayout.addView(empty)
                } else {
                    val dateStr = formatDate(viewYear, viewMonth + 1, day)
                    val count = countMap[dateStr] ?: 0
                    val isToday = dateStr == todayStr
                    val isSelected = dateStr == selectedDate
                    val currentDay = day

                    val cell = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(2), dp(4), dp(2), dp(4))
                        isClickable = true
                        isFocusable = true

                        background = ContextCompat.getDrawable(
                            this@CalendarActivity,
                            when {
                                isSelected -> R.drawable.cal_selected_bg
                                isToday -> R.drawable.cal_today_bg
                                else -> R.drawable.cal_day_bg
                            }
                        )

                        setOnClickListener { selectDay(dateStr) }
                    }

                    val dayNum = TextView(this).apply {
                        text = currentDay.toString()
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        gravity = Gravity.CENTER
                        setTypeface(null, if (isToday) Typeface.BOLD else Typeface.NORMAL)
                        setTextColor(
                            ContextCompat.getColor(
                                this@CalendarActivity,
                                if (isSelected) R.color.white
                                else if (isToday) R.color.login_accent
                                else R.color.login_text
                            )
                        )
                    }
                    cell.addView(dayNum)

                    if (count > 0) {
                        val dot = TextView(this).apply {
                            text = if (count <= 3) "●".repeat(count) else "●●●+"
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 7f)
                            gravity = Gravity.CENTER
                            setTextColor(
                                ContextCompat.getColor(
                                    this@CalendarActivity,
                                    if (isSelected) R.color.white else R.color.login_accent
                                )
                            )
                        }
                        cell.addView(dot)
                    }

                    val cellParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                        marginStart = dp(1)
                        marginEnd = dp(1)
                    }
                    cell.layoutParams = cellParams
                    rowLayout.addView(cell)

                    day++
                }
                cellIndex++
            }
            grid.addView(rowLayout)
        }
    }

    private fun selectDay(dateStr: String) {
        selectedDate = dateStr
        renderMonth()
        renderResults()
    }

    private fun renderResults() {
        val panel = findViewById<LinearLayout>(R.id.cal_results_panel)
        val spacer = findViewById<View>(R.id.cal_spacer)
        val list = findViewById<LinearLayout>(R.id.cal_results_list)
        val label = findViewById<TextView>(R.id.cal_results_label)
        list.removeAllViews()

        val date = selectedDate
        if (date == null) {
            panel.visibility = View.GONE
            spacer.visibility = View.VISIBLE
            return
        }

        val dayEntries = getEntriesForDate(date)

        panel.visibility = View.VISIBLE
        spacer.visibility = View.GONE
        label.text = "$date — ${dayEntries.length()} entr${if (dayEntries.length() == 1) "y" else "ies"}"

        if (dayEntries.length() == 0) {
            selectedDayEntryIds = listOf()
            val empty = TextView(this).apply {
                text = "No entries on this date."
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }
            list.addView(empty)
            return
        }

        val ids = mutableListOf<String>()
        for (i in 0 until dayEntries.length()) {
            val entry = dayEntries.optJSONObject(i) ?: continue
            val id = entry.optString("id", "")
            if (id.isNotEmpty()) ids.add(id)
            list.addView(createEntryRow(entry))
        }
        selectedDayEntryIds = ids
    }

    private fun createEntryRow(entry: org.json.JSONObject): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = ContextCompat.getDrawable(this@CalendarActivity, R.drawable.entry_row_bg)
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

        val time = entry.optString("time", "")
        if (time.isNotEmpty()) {
            val timeView = TextView(this).apply {
                text = time
                setTextColor(ThemeManager.color(C.ACCENT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(8), 0, 0, 0)
            }
            topRow.addView(timeView)
        }

        row.addView(topRow)

        val preview = entry.optString("contentPreview", "")
        if (preview.isNotEmpty()) {
            val sub = TextView(this).apply {
                text = preview
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            }
            row.addView(sub)
        }

        val entryId = entry.optString("id", "")
        row.setOnClickListener { openEntryViewer(entryId) }

        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) }

        return row
    }

    private fun buildCountMap(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val date = entry.optString("date", "")
            if (date.isNotEmpty()) {
                map[date] = (map[date] ?: 0) + 1
            }
        }
        return map
    }

    private fun getEntriesForDate(date: String): JSONArray {
        val result = JSONArray()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            if (entry.optString("date", "") == date) {
                result.put(entry)
            }
        }
        return result
    }

    private fun formatDate(year: Int, month: Int, day: Int): String {
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun openEntryViewer(entryId: String) {
        if (entryId.isEmpty()) return
        EntryViewerActivity.pendingEntryId = entryId
        EntryViewerActivity.pendingEntryIds = selectedDayEntryIds
        EntryViewerActivity.databaseService = ServiceProvider.databaseService
        EntryViewerActivity.bootstrapService = ServiceProvider.bootstrapService
        startActivity(Intent(this, EntryViewerActivity::class.java))
    }

    private fun finishWithNav(target: String) {
        val result = Intent().apply {
            putExtra("navigate_to", target)
        }
        setResult(RESULT_OK, result)
        finish()
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
