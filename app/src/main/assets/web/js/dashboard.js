/**
 * Dashboard: stats, aggregates, and dynamic display fields.
 */

// Store full ranked data for Show All toggle
let dashboardFullData = { tags: [], categories: [], places: [], people: [] };
let dashboardShowAll = { tags: false, categories: false, places: false, people: false };
let dashboardViewMode = { tags: 'list', categories: 'list', places: 'list', people: 'list' };

function getDateRange(period) {
    const now = new Date();
    let from, to;
    if (period === 'week') {
        const dayOfWeek = now.getDay() || 7;
        from = new Date(now.getFullYear(), now.getMonth(), now.getDate() - dayOfWeek + 1);
        to = new Date(from.getFullYear(), from.getMonth(), from.getDate() + 6);
    } else if (period === 'month') {
        from = new Date(now.getFullYear(), now.getMonth(), 1);
        to = new Date(now.getFullYear(), now.getMonth() + 1, 0);
    } else if (period === 'year') {
        from = new Date(now.getFullYear(), 0, 1);
        to = new Date(now.getFullYear(), 11, 31);
    }
    return { from: toLocalDateStr(from), to: toLocalDateStr(to) };
}

function getWeatherEmoji(code) {
    if (code === 0) return '\u2600\uFE0F';
    if (code <= 2) return '\u26C5';
    if (code === 3) return '\u2601\uFE0F';
    if (code <= 48) return '\uD83C\uDF2B\uFE0F';
    if (code <= 57) return '\uD83C\uDF27\uFE0F';
    if (code <= 67) return '\uD83C\uDF27\uFE0F';
    if (code <= 77) return '\u2744\uFE0F';
    if (code <= 82) return '\uD83C\uDF26\uFE0F';
    if (code <= 86) return '\uD83C\uDF28\uFE0F';
    return '\u26A1';
}

async function refreshDashboardWeather() {
    const container = document.getElementById('dashboard-weather');
    if (!container) return;
    const loc = Weather.getLocation();
    if (!loc) { container.style.display = 'none'; return; }
    try {
        const unit = Weather.getTempUnit();
        const w = await Weather.fetchCurrent(loc.lat, loc.lng, unit);
        container.style.display = 'flex';
        container.innerHTML =
            `<span class="dw-icon">${getWeatherEmoji(w.code)}</span>` +
            `<span class="dw-temp">${w.temp}\u00B0${w.unit}</span>` +
            `<span class="dw-desc">${w.description}</span>` +
            `<span class="dw-loc">${escapeHtml(loc.name)}</span>`;
    } catch (e) {
        container.style.display = 'none';
    }
}

function refreshDashboard() {
    // Restore saved view modes
    dashboardViewMode.tags = localStorage.getItem('dashboard_view_tags') || 'list';
    dashboardViewMode.categories = localStorage.getItem('dashboard_view_categories') || 'list';
    dashboardViewMode.places = localStorage.getItem('dashboard_view_places') || 'list';
    dashboardViewMode.people = localStorage.getItem('dashboard_view_people') || 'list';
    renderDashboardQuickActions();
    refreshDashboardWeather();
    const entries = DB.getEntries();

    // Total entries
    document.getElementById('stat-total').textContent = entries.length;

    // This week (Monday to Sunday)
    const week = getDateRange('week');
    const thisWeek = entries.filter(e => e.date >= week.from && e.date <= week.to).length;
    document.getElementById('stat-week').textContent = thisWeek;

    // This month
    const month = getDateRange('month');
    const thisMonth = entries.filter(e => e.date >= month.from && e.date <= month.to).length;
    document.getElementById('stat-month').textContent = thisMonth;

    // This year
    const year = getDateRange('year');
    const thisYear = entries.filter(e => e.date >= year.from && e.date <= year.to).length;
    document.getElementById('stat-year').textContent = thisYear;

    // Compute full ranked data
    dashboardFullData.tags = countFieldAll(entries, 'tags');
    dashboardFullData.categories = countFieldAll(entries, 'categories');
    dashboardFullData.places = countPlacesAll(entries);
    dashboardFullData.people = countPeopleAll(entries);

    // Render panels
    renderRankedPanel('tags', 'top-tags', 'tag');
    renderRankedPanel('categories', 'top-categories', 'category');
    renderRankedPanel('places', 'top-places', 'place');
    renderRankedPanel('people', 'top-people', 'person');

    // Pinned entries
    renderPinnedEntries(entries);

    // Recent entries
    renderRecentEntries(entries);
}

function calculateStreak(entries) {
    if (entries.length === 0) return 0;

    // Get unique dates sorted descending
    const dates = [...new Set(entries.map(e => e.date).filter(Boolean))].sort().reverse();
    if (dates.length === 0) return 0;

    const today = toLocalDateStr(new Date());
    const yd = new Date();
    yd.setDate(yd.getDate() - 1);
    const yesterday = toLocalDateStr(yd);

    // Streak must include today or yesterday
    if (dates[0] !== today && dates[0] !== yesterday) return 0;

    let streak = 1;
    for (let i = 0; i < dates.length - 1; i++) {
        const current = new Date(dates[i] + 'T00:00:00');
        const prev = new Date(dates[i + 1] + 'T00:00:00');
        const diff = (current - prev) / 86400000;
        if (diff === 1) {
            streak++;
        } else {
            break;
        }
    }
    return streak;
}

function countFieldAll(entries, field) {
    const counts = {};
    entries.forEach(e => {
        (e[field] || []).forEach(val => {
            counts[val] = (counts[val] || 0) + 1;
        });
    });
    return Object.entries(counts).sort((a, b) => b[1] - a[1]);
}

function countPlacesAll(entries) {
    const counts = {};
    entries.forEach(e => {
        const name = e.placeName;
        if (name) {
            counts[name] = (counts[name] || 0) + 1;
        }
    });
    return Object.entries(counts).sort((a, b) => b[1] - a[1]);
}

function countPeopleAll(entries) {
    const counts = {};
    entries.forEach(e => {
        (e.people || []).forEach(p => {
            const name = p.firstName + ' ' + p.lastName;
            counts[name] = (counts[name] || 0) + 1;
        });
    });
    return Object.entries(counts).sort((a, b) => b[1] - a[1]);
}

function renderRankedPanel(dataKey, elementId, filterType) {
    const allItems = dashboardFullData[dataKey];
    const showAll = dashboardShowAll[dataKey];
    const items = showAll ? allItems : allItems.slice(0, 10);
    const btn = document.getElementById('btn-show-all-' + dataKey);
    const viewMode = dashboardViewMode[dataKey] || 'list';

    const container = document.getElementById(elementId);
    if (allItems.length === 0) {
        container.innerHTML = '<div class="no-data">No data yet</div>';
        if (btn) btn.style.display = 'none';
        return;
    }

    if (viewMode === 'button') {
        container.className = 'ranked-button-grid';
        container.innerHTML = items.map(([name, count]) => {
            const iconData = DB.getIcon(filterType, name);
            const iconBlock = iconData
                ? `<img src="${iconData}" class="ranked-btn-icon" alt="">`
                : `<span class="ranked-btn-text-icon">${escapeHtml(name.substring(0, 2))}</span>`;
            return `<div class="ranked-btn"${getItemColorStyle(filterType, name)} onclick="navigateToFilteredList('${filterType}', '${escapeHtml(name)}')" title="${escapeHtml(name)} (${count})">
                ${iconBlock}
                <span class="ranked-btn-label">${escapeHtml(name)}</span>
                <span class="ranked-btn-count">${count}</span>
            </div>`;
        }).join('');
    } else {
        container.className = 'ranked-list';
        container.innerHTML = items.map(([name, count]) => `
            <div class="ranked-item ranked-item-clickable"${getItemColorStyle(filterType, name)} onclick="navigateToFilteredList('${filterType}', '${escapeHtml(name)}')">
                <span class="rank-name">${getIconHtml(filterType, name)}${escapeHtml(name)}</span>
                <span class="rank-count">${count}</span>
            </div>
        `).join('');
    }

    // Show/hide "Show All" button
    if (btn) {
        if (allItems.length > 10) {
            btn.style.display = 'inline-block';
            btn.textContent = showAll ? 'Top 10' : `Show All (${allItems.length})`;
        } else {
            btn.style.display = 'none';
        }
    }
}

function toggleRankedView(dataKey) {
    const current = dashboardViewMode[dataKey] || 'list';
    dashboardViewMode[dataKey] = current === 'list' ? 'button' : 'list';
    localStorage.setItem('dashboard_view_' + dataKey, dashboardViewMode[dataKey]);
    const filterTypeMap = { tags: 'tag', categories: 'category', places: 'place', people: 'person' };
    const elementIdMap = { tags: 'top-tags', categories: 'top-categories', places: 'top-places', people: 'top-people' };
    renderRankedPanel(dataKey, elementIdMap[dataKey], filterTypeMap[dataKey]);
}

function toggleShowAll(dataKey) {
    dashboardShowAll[dataKey] = !dashboardShowAll[dataKey];
    const filterTypeMap = { tags: 'tag', categories: 'category', places: 'place', people: 'person' };
    const elementIdMap = { tags: 'top-tags', categories: 'top-categories', places: 'top-places', people: 'top-people' };
    renderRankedPanel(dataKey, elementIdMap[dataKey], filterTypeMap[dataKey]);
}

function statNavigate(period) {
    navigateTo('entry-list');
    document.getElementById('search-input').value = '';
    document.getElementById('filter-category').value = '';
    document.getElementById('filter-tag').value = '';
    document.getElementById('filter-date-from').value = '';
    document.getElementById('filter-date-to').value = '';
    activeViewId = null;
    document.getElementById('view-select').value = '';

    if (period !== 'total') {
        const range = getDateRange(period);
        document.getElementById('filter-date-from').value = range.from;
        document.getElementById('filter-date-to').value = range.to;
    }

    filterEntries();
}

function navigateToFilteredList(type, value) {
    navigateTo('entry-list');
    // Clear existing filters first
    document.getElementById('search-input').value = '';
    document.getElementById('filter-category').value = '';
    document.getElementById('filter-tag').value = '';
    document.getElementById('filter-date-from').value = '';
    document.getElementById('filter-date-to').value = '';
    activeViewId = null;
    document.getElementById('view-select').value = '';

    if (type === 'tag') {
        document.getElementById('filter-tag').value = value;
    } else if (type === 'category') {
        document.getElementById('filter-category').value = value;
    } else if (type === 'place') {
        document.getElementById('search-input').value = value;
    } else if (type === 'person') {
        document.getElementById('search-input').value = value;
    }

    filterEntries();
}

function renderDashboardQuickActions() {
    const container = document.getElementById('dashboard-quick-actions');
    if (!container) return;

    const pinned = typeof getPinnedViews === 'function' ? getPinnedViews() : [];

    if (pinned.length === 0) {
        container.innerHTML = '';
        container.style.display = 'none';
        return;
    }

    const allEntries = DB.getEntries();
    container.style.display = 'flex';
    container.innerHTML = pinned.map(v => {
        const count = applyView(allEntries, v).length;
        return `<button class="btn-quick-action" onclick="launchPinnedView('${v.id}')" title="${escapeHtml(v.name)}">${escapeHtml(v.name)}<span class="quick-action-count">${count}</span></button>`;
    }).join('');
}

function renderPinnedEntries(entries) {
    const container = document.getElementById('pinned-entries');
    const panel = document.getElementById('panel-pinned-entries');
    if (!container || !panel) return;

    const pinned = entries.filter(e => e.pinned)
        .sort((a, b) => (b.date || '').localeCompare(a.date || ''));

    if (pinned.length === 0) {
        panel.style.display = 'none';
        return;
    }

    const fields = typeof getEntryListFields === 'function' ? getEntryListFields() : {};
    const showThumbs = fields['images'] !== false;

    panel.style.display = '';
    container.innerHTML = pinned.map(e => {
        const thumb = (showThumbs && e.images && e.images.length > 0)
            ? `<img class="pinned-entry-thumb" src="${e.images[0].thumb}" alt="">`
            : '';
        return `<div class="ranked-item pinned-entry-item" style="cursor:pointer" onclick="viewEntry('${e.id}')">
            ${thumb}<span class="rank-name">${escapeHtml(e.title || 'Untitled')}</span>
            <span class="rank-count">${formatDate(e.date)}</span>
        </div>`;
    }).join('');
}

function renderRecentEntries(entries) {
    const container = document.getElementById('recent-entries');
    const recent = [...entries]
        .sort((a, b) => (b.date || '').localeCompare(a.date || ''))
        .slice(0, 5);

    if (recent.length === 0) {
        container.innerHTML = '<div class="no-data">No entries yet</div>';
        return;
    }

    container.innerHTML = recent.map(e => `
        <div class="ranked-item" style="cursor:pointer" onclick="viewEntry('${e.id}')">
            <span class="rank-name">${escapeHtml(e.title || 'Untitled')}</span>
            <span class="rank-count">${formatDate(e.date)}</span>
        </div>
    `).join('');
}

// ========== Dashboard Search ==========

let _dashboardSearchResultIds = [];
let _dashboardSearchTerm = '';

function toggleDashboardSearch() {
    const body = document.getElementById('dashboard-search-body');
    const arrow = document.getElementById('dashboard-search-arrow');
    const collapsed = body.style.display !== 'none';
    body.style.display = collapsed ? 'none' : '';
    arrow.innerHTML = collapsed ? '&#9654;' : '&#9660;';
    if (!collapsed) {
        document.getElementById('dashboard-search-input').focus();
    }
}

function viewEntryFromSearch(id) {
    _dashboardSearchTerm = document.getElementById('dashboard-search-input').value.trim();
    viewEntryList = _dashboardSearchResultIds;
    _viewEntryFromSearch = true;
    viewEntry(id);
}

function dashboardSearch() {
    const input = document.getElementById('dashboard-search-input');
    const term = input.value.trim();
    if (!term) return;

    const statusEl = document.getElementById('dashboard-search-status');
    const resultsEl = document.getElementById('dashboard-search-results');
    const clearBtn = document.getElementById('dashboard-search-clear');

    statusEl.textContent = 'Searching, please wait...';
    statusEl.style.display = 'block';
    resultsEl.style.display = 'none';
    clearBtn.style.display = 'none';

    // Use setTimeout to let the UI update before searching
    setTimeout(() => {
        const entries = DB.getEntries();
        const lowerTerm = term.toLowerCase();
        const matches = [];

        for (const e of entries) {
            const titleMatch = (e.title || '').toLowerCase().includes(lowerTerm);
            const contentMatch = (e.content || '').toLowerCase().includes(lowerTerm);
            const richMatch = (e.richContent || '').toLowerCase().includes(lowerTerm);
            if (titleMatch || contentMatch || richMatch) {
                matches.push(e);
            }
        }

        if (matches.length === 0) {
            statusEl.textContent = `No results found for "${escapeHtml(term)}".`;
            resultsEl.style.display = 'none';
            clearBtn.style.display = 'inline-block';
            return;
        }

        statusEl.textContent = `${matches.length} result${matches.length === 1 ? '' : 's'} found.`;
        clearBtn.style.display = 'inline-block';

        // Store search result IDs for prev/next navigation in viewer
        _dashboardSearchResultIds = matches.map(e => e.id);

        resultsEl.innerHTML = matches.map(e => {
            const date = e.date ? formatDate(e.date) : '';
            const time = e.time ? formatTime(e.time) : '';
            const title = highlightTerm(escapeHtml(e.title || 'Untitled'), term);
            // Show content snippet around the match
            const contentSnippet = getSearchSnippet(e.content || '', term, 120);
            const richSnippet = (!contentSnippet && e.richContent) ? getSearchSnippet(stripHtml(e.richContent), term, 120) : '';
            const snippet = contentSnippet || richSnippet;

            return `<div class="dashboard-search-item" onclick="viewEntryFromSearch('${e.id}')">
                <div class="dsr-header">
                    <span class="dsr-date">${escapeHtml(date)}${time ? ' ' + escapeHtml(time) : ''}</span>
                    <span class="dsr-title">${title}</span>
                </div>
                ${snippet ? `<div class="dsr-content">${highlightTerm(escapeHtml(snippet), term)}</div>` : ''}
            </div>`;
        }).join('');
        resultsEl.style.display = 'block';
    }, 50);
}

function clearDashboardSearch() {
    document.getElementById('dashboard-search-input').value = '';
    document.getElementById('dashboard-search-status').style.display = 'none';
    document.getElementById('dashboard-search-results').style.display = 'none';
    document.getElementById('dashboard-search-clear').style.display = 'none';
    _dashboardSearchResultIds = [];
}

function highlightTerm(html, term) {
    if (!term) return html;
    const escaped = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp('(' + escaped + ')', 'gi');
    return html.replace(regex, '<mark>$1</mark>');
}

function getSearchSnippet(text, term, maxLen) {
    if (!text || !term) return '';
    const lower = text.toLowerCase();
    const idx = lower.indexOf(term.toLowerCase());
    if (idx === -1) return '';
    const start = Math.max(0, idx - 40);
    const end = Math.min(text.length, idx + term.length + maxLen - 40);
    let snippet = text.substring(start, end);
    if (start > 0) snippet = '...' + snippet;
    if (end < text.length) snippet = snippet + '...';
    return snippet;
}

function stripHtml(html) {
    const div = document.createElement('div');
    div.innerHTML = html;
    return div.textContent || div.innerText || '';
}

/**
 * Gather all dashboard data as a JSON object for native Android rendering.
 * Called after login to pass data to native DashboardActivity.
 */
function getDashboardDataJSON() {
    const entries = DB.getEntries();
    const week = getDateRange('week');
    const month = getDateRange('month');
    const year = getDateRange('year');

    const tags = countFieldAll(entries, 'tags');
    const categories = countFieldAll(entries, 'categories');
    const places = countPlacesAll(entries);
    const people = countPeopleAll(entries);

    // Pinned entries
    const pinned = entries.filter(e => e.pinned)
        .sort((a, b) => (b.date || '').localeCompare(a.date || ''))
        .slice(0, 20)
        .map(e => ({
            id: e.id,
            date: e.date,
            time: e.time,
            title: e.title,
            contentPreview: (e.content || '').substring(0, 100),
            thumbnail: (e.images && e.images.length > 0) ? e.images[0].thumb : null
        }));

    // Recent entries
    const recent = entries
        .sort((a, b) => (b.date || '').localeCompare(a.date || ''))
        .slice(0, 5)
        .map(e => ({
            id: e.id,
            date: e.date,
            time: e.time,
            title: e.title,
            contentPreview: (e.content || '').substring(0, 100)
        }));

    // Pinned custom views
    const pinnedViews = (typeof getPinnedViews === 'function' ? getPinnedViews() : [])
        .map(v => {
            const count = typeof applyView === 'function' ? applyView(entries, v).length : 0;
            return { id: v.id, name: v.name, count: count };
        });

    // Journal name
    const journal = DB.getJournalList().find(j => j.id === DB.getJournalId());
    const journalName = journal ? journal.name : '';

    // Theme
    const settings = DB.getSettings();
    const theme = settings.theme || 'dark';

    // Category/tag colors
    const catColors = typeof getCategoryColors === 'function' ? getCategoryColors() : {};
    const tagColors = typeof getTagColors === 'function' ? getTagColors() : {};
    const useCatColors = typeof isUseCategoryColor === 'function' ? isUseCategoryColor() : false;
    const useTagColors = typeof isUseTagColor === 'function' ? isUseTagColor() : false;

    // Streak
    const streak = calculateStreak(entries);

    return JSON.stringify({
        totalEntries: entries.length,
        thisWeek: entries.filter(e => e.date >= week.from && e.date <= week.to).length,
        thisMonth: entries.filter(e => e.date >= month.from && e.date <= month.to).length,
        thisYear: entries.filter(e => e.date >= year.from && e.date <= year.to).length,
        streak: streak,
        topTags: tags.slice(0, 20).map(t => ({ name: t[0], count: t[1], color: useTagColors ? (tagColors[t[0]] || null) : null })),
        topCategories: categories.slice(0, 20).map(c => ({ name: c[0], count: c[1], color: useCatColors ? (catColors[c[0]] || null) : null })),
        topPlaces: places.slice(0, 20).map(p => ({ name: p[0], count: p[1] })),
        topPeople: people.slice(0, 20).map(p => ({ name: p[0], count: p[1] })),
        pinnedEntries: pinned,
        recentEntries: recent,
        pinnedViews: pinnedViews,
        journalName: journalName,
        theme: theme
    });
}
