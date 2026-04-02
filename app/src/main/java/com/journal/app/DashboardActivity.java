package com.journal.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private JSONObject dashboardData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        String json = getIntent().getStringExtra("dashboard_data");
        if (json == null || json.isEmpty()) {
            finishWithNav("dashboard");
            return;
        }

        try {
            dashboardData = new JSONObject(json);
        } catch (JSONException e) {
            finishWithNav("dashboard");
            return;
        }

        setupNavbar();
        setupBottomNav();
        setupSearch();
        populateWeatherStreak();
        populateStats();
        populateQuickActions();
        populatePinnedEntries();
        populateRecentEntries();
        populateRankedPanel(R.id.tags_list, R.id.panel_tags, "topTags");
        populateRankedPanel(R.id.categories_list, R.id.panel_categories, "topCategories");
        populateRankedPanel(R.id.places_list, R.id.panel_places, "topPlaces");
        populateRankedPanel(R.id.people_list, R.id.panel_people, "topPeople");
    }

    // ========== Navbar ==========

    private void setupNavbar() {
        String journalName = dashboardData.optString("journalName", "");
        TextView navTitle = findViewById(R.id.nav_title);
        navTitle.setText(journalName.isEmpty() ? "JOURNAL" : journalName.toUpperCase());

        findViewById(R.id.btn_new_entry).setOnClickListener(v -> finishWithNav("entry-form"));
        findViewById(R.id.btn_lock).setOnClickListener(v -> finishWithNav("lock"));
    }

    private void setupBottomNav() {
        findViewById(R.id.nav_entries).setOnClickListener(v -> finishWithNav("entry-list"));
        findViewById(R.id.nav_reports).setOnClickListener(v -> finishWithNav("reports"));
        findViewById(R.id.nav_explorer).setOnClickListener(v -> finishWithNav("explorer"));
        findViewById(R.id.nav_settings).setOnClickListener(v -> finishWithNav("settings"));
    }

    // ========== Search ==========

    private void setupSearch() {
        EditText searchInput = findViewById(R.id.search_input);
        Button btnSearch = findViewById(R.id.btn_search);
        Button btnClear = findViewById(R.id.btn_clear_search);

        btnSearch.setOnClickListener(v -> performSearch(searchInput.getText().toString().trim()));

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchInput.getText().toString().trim());
                return true;
            }
            return false;
        });

        btnClear.setOnClickListener(v -> {
            searchInput.setText("");
            findViewById(R.id.search_results_panel).setVisibility(View.GONE);
        });
    }

    private void performSearch(String term) {
        if (term.isEmpty()) return;

        LinearLayout panel = findViewById(R.id.search_results_panel);
        LinearLayout list = findViewById(R.id.search_results_list);
        TextView title = findViewById(R.id.search_results_title);
        list.removeAllViews();

        // Search through recent entries and pinned entries in the dashboard data
        List<JSONObject> matches = new ArrayList<>();
        String lowerTerm = term.toLowerCase();

        // Search recent entries
        searchInArray(dashboardData.optJSONArray("recentEntries"), lowerTerm, matches);
        // Search pinned entries
        searchInArray(dashboardData.optJSONArray("pinnedEntries"), lowerTerm, matches);

        // Remove duplicates by id
        List<String> seenIds = new ArrayList<>();
        List<JSONObject> unique = new ArrayList<>();
        for (JSONObject m : matches) {
            String id = m.optString("id", "");
            if (!seenIds.contains(id)) {
                seenIds.add(id);
                unique.add(m);
            }
        }

        if (unique.isEmpty()) {
            title.setText("No results for \"" + term + "\"");
            // Add a hint to use full search in web explorer
            TextView hint = new TextView(this);
            hint.setText("Use Explorer for full search across all entries");
            hint.setTextColor(ContextCompat.getColor(this, R.color.login_text_secondary));
            hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            hint.setPadding(0, dp(4), 0, 0);
            hint.setOnClickListener(v -> finishWithNav("explorer"));
            list.addView(hint);
        } else {
            title.setText(unique.size() + " result" + (unique.size() != 1 ? "s" : "") + " for \"" + term + "\"");
            for (JSONObject entry : unique) {
                list.addView(createEntryRow(entry, true));
            }
        }

        panel.setVisibility(View.VISIBLE);
    }

    private void searchInArray(JSONArray arr, String lowerTerm, List<JSONObject> matches) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject entry = arr.optJSONObject(i);
            if (entry == null) continue;
            String titleStr = entry.optString("title", "").toLowerCase();
            String preview = entry.optString("contentPreview", "").toLowerCase();
            if (titleStr.contains(lowerTerm) || preview.contains(lowerTerm)) {
                matches.add(entry);
            }
        }
    }

    // ========== Weather & Streak ==========

    private void populateWeatherStreak() {
        LinearLayout row = findViewById(R.id.weather_streak_row);
        TextView weatherDisplay = findViewById(R.id.weather_display);
        TextView streakDisplay = findViewById(R.id.streak_display);

        boolean hasContent = false;

        // Streak
        int streak = dashboardData.optInt("streak", 0);
        if (streak > 0) {
            streakDisplay.setText("\uD83D\uDD25 " + streak + " day streak");
            streakDisplay.setVisibility(View.VISIBLE);
            hasContent = true;
        } else {
            streakDisplay.setVisibility(View.GONE);
        }

        // Weather
        String weather = dashboardData.optString("weather", "");
        if (!weather.isEmpty()) {
            weatherDisplay.setText(weather);
            weatherDisplay.setVisibility(View.VISIBLE);
            hasContent = true;
        } else {
            weatherDisplay.setVisibility(View.GONE);
        }

        row.setVisibility(hasContent ? View.VISIBLE : View.GONE);
    }

    // ========== Stats ==========

    private void populateStats() {
        setStatValue(R.id.stat_total_value, dashboardData.optInt("totalEntries", 0));
        setStatValue(R.id.stat_week_value, dashboardData.optInt("thisWeek", 0));
        setStatValue(R.id.stat_month_value, dashboardData.optInt("thisMonth", 0));
        setStatValue(R.id.stat_year_value, dashboardData.optInt("thisYear", 0));

        findViewById(R.id.stat_total).setOnClickListener(v -> finishWithNav("entry-list"));
        findViewById(R.id.stat_week).setOnClickListener(v -> finishWithNav("entry-list"));
        findViewById(R.id.stat_month).setOnClickListener(v -> finishWithNav("entry-list"));
        findViewById(R.id.stat_year).setOnClickListener(v -> finishWithNav("entry-list"));
    }

    private void setStatValue(int viewId, int value) {
        ((TextView) findViewById(viewId)).setText(String.valueOf(value));
    }

    // ========== Quick Actions (Pinned Views) ==========

    private void populateQuickActions() {
        JSONArray views = dashboardData.optJSONArray("pinnedViews");
        LinearLayout container = findViewById(R.id.quick_actions);

        if (views == null || views.length() == 0) {
            container.setVisibility(View.GONE);
            return;
        }

        container.setVisibility(View.VISIBLE);
        container.removeAllViews();

        for (int i = 0; i < views.length(); i++) {
            JSONObject view = views.optJSONObject(i);
            if (view == null) continue;

            String name = view.optString("name", "");
            int count = view.optInt("count", 0);

            Button btn = new Button(this);
            btn.setText(name + " (" + count + ")");
            btn.setTextColor(ContextCompat.getColor(this, R.color.login_text));
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btn.setBackground(ContextCompat.getDrawable(this, R.drawable.btn_secondary));
            btn.setAllCaps(false);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, dp(38), 1f);
            params.setMarginEnd(dp(4));
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> finishWithNav("entry-list"));
            container.addView(btn);
        }
    }

    // ========== Pinned Entries ==========

    private void populatePinnedEntries() {
        JSONArray pinned = dashboardData.optJSONArray("pinnedEntries");
        LinearLayout panel = findViewById(R.id.panel_pinned);
        LinearLayout list = findViewById(R.id.pinned_entries_list);

        if (pinned == null || pinned.length() == 0) {
            panel.setVisibility(View.GONE);
            return;
        }

        panel.setVisibility(View.VISIBLE);
        list.removeAllViews();
        for (int i = 0; i < pinned.length(); i++) {
            JSONObject entry = pinned.optJSONObject(i);
            if (entry == null) continue;
            list.addView(createEntryRow(entry, false));
        }
    }

    // ========== Recent Entries ==========

    private void populateRecentEntries() {
        JSONArray recent = dashboardData.optJSONArray("recentEntries");
        LinearLayout list = findViewById(R.id.recent_entries_list);
        list.removeAllViews();

        if (recent == null || recent.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No entries yet. Tap ✏️ to create your first entry!");
            empty.setTextColor(ContextCompat.getColor(this, R.color.login_text_secondary));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setPadding(dp(4), dp(8), dp(4), dp(8));
            list.addView(empty);
            return;
        }

        for (int i = 0; i < recent.length(); i++) {
            JSONObject entry = recent.optJSONObject(i);
            if (entry == null) continue;
            list.addView(createEntryRow(entry, false));
        }
    }

    private View createEntryRow(JSONObject entry, boolean showPreview) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setBackground(ContextCompat.getDrawable(this, R.drawable.entry_row_bg));
        row.setClickable(true);
        row.setFocusable(true);

        // Top line: title + date
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        String titleText = entry.optString("title", "");
        title.setText(titleText.isEmpty() ? "Untitled" : titleText);
        title.setTextColor(ContextCompat.getColor(this, R.color.login_text));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        topRow.addView(title);

        // Date badge
        String date = entry.optString("date", "");
        if (!date.isEmpty()) {
            TextView dateText = new TextView(this);
            dateText.setText(date);
            dateText.setTextColor(ContextCompat.getColor(this, R.color.login_accent));
            dateText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            dateText.setPadding(dp(8), 0, 0, 0);
            topRow.addView(dateText);
        }

        row.addView(topRow);

        // Content preview
        String preview = entry.optString("contentPreview", "");
        String time = entry.optString("time", "");
        String subtitle = "";
        if (!time.isEmpty()) subtitle = time;
        if (!preview.isEmpty()) {
            if (!subtitle.isEmpty()) subtitle += " — ";
            subtitle += preview;
        }

        if (!subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextColor(ContextCompat.getColor(this, R.color.login_text_secondary));
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            sub.setMaxLines(showPreview ? 3 : 1);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setPadding(0, dp(2), 0, 0);
            row.addView(sub);
        }

        // Click to view entry
        row.setOnClickListener(v -> finishWithNav("entry-list"));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(4);
        row.setLayoutParams(params);

        return row;
    }

    // ========== Ranked Panels (Tags, Categories, Places, People) ==========

    private void populateRankedPanel(int listId, int panelId, String dataKey) {
        JSONArray items = dashboardData.optJSONArray(dataKey);
        LinearLayout list = findViewById(listId);
        LinearLayout panel = findViewById(panelId);

        if (items == null || items.length() == 0) {
            panel.setVisibility(View.GONE);
            return;
        }

        panel.setVisibility(View.VISIBLE);
        list.removeAllViews();

        int limit = Math.min(items.length(), 10);
        for (int i = 0; i < limit; i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;

            String name = item.optString("name", "");
            int count = item.optInt("count", 0);
            String color = item.optString("color", null);

            list.addView(createRankedRow(name, count, color, i + 1));
        }

        // "Show all" hint if more than 10
        if (items.length() > 10) {
            TextView more = new TextView(this);
            more.setText("+" + (items.length() - 10) + " more");
            more.setTextColor(ContextCompat.getColor(this, R.color.login_accent));
            more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            more.setPadding(dp(6), dp(6), dp(6), dp(2));
            more.setOnClickListener(v -> finishWithNav("entry-list"));
            list.addView(more);
        }
    }

    private View createRankedRow(String name, int count, String color, int rank) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(9), dp(8), dp(9));
        row.setBackground(ContextCompat.getDrawable(this, R.drawable.ranked_row_bg));
        row.setClickable(true);
        row.setFocusable(true);

        // Rank badge
        TextView rankBadge = new TextView(this);
        rankBadge.setText(String.valueOf(rank));
        rankBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        rankBadge.setTypeface(null, Typeface.BOLD);
        rankBadge.setGravity(Gravity.CENTER);
        rankBadge.setMinWidth(dp(24));
        rankBadge.setMinHeight(dp(24));

        // Color the top 3 ranks
        if (rank <= 3) {
            rankBadge.setTextColor(ContextCompat.getColor(this, R.color.login_card_bg));
            rankBadge.setBackgroundColor(ContextCompat.getColor(this, R.color.login_accent));
            rankBadge.setPadding(dp(4), dp(2), dp(4), dp(2));
        } else {
            rankBadge.setTextColor(ContextCompat.getColor(this, R.color.login_text_secondary));
        }
        row.addView(rankBadge);

        // Spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(8), 0));
        row.addView(spacer);

        // Name
        TextView nameText = new TextView(this);
        nameText.setText(name);
        nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        nameText.setMaxLines(1);
        nameText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameText.setLayoutParams(nameParams);

        if (color != null && !color.isEmpty() && !"null".equals(color)) {
            try {
                nameText.setTextColor(Color.parseColor(color));
            } catch (Exception e) {
                nameText.setTextColor(ContextCompat.getColor(this, R.color.login_text));
            }
        } else {
            nameText.setTextColor(ContextCompat.getColor(this, R.color.login_text));
        }
        row.addView(nameText);

        // Count badge
        TextView countBadge = new TextView(this);
        countBadge.setText(String.valueOf(count));
        countBadge.setTextColor(ContextCompat.getColor(this, R.color.login_accent));
        countBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        countBadge.setTypeface(null, Typeface.BOLD);
        countBadge.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);
        countBadge.setMinWidth(dp(28));
        row.addView(countBadge);

        row.setOnClickListener(v -> finishWithNav("entry-list"));

        return row;
    }

    // ========== Navigation ==========

    private void finishWithNav(String target) {
        Intent result = new Intent();
        result.putExtra("navigate_to", target);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Back from dashboard = exit app
        finishAffinity();
    }

    // ========== Utility ==========

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
