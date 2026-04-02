/**
 * Entry CRUD and list management.
 */

let _viewEntryFromSearch = false;

function getDefaultEntryListOrder() {
    const field = localStorage.getItem('default_entry_sort_field') || 'date';
    const direction = localStorage.getItem('default_entry_sort_dir') || 'desc';
    return { field, direction };
}

let selectMode = false;
let selectedEntryIds = new Set();
let searchDebounceTimer = null;
let currentPageNum = 1;
let currentPageSize = parseInt(localStorage.getItem('entries_page_size')) || 20;
let filteredEntriesCache = [];
let entryViewMode = localStorage.getItem('entry_view_mode') || 'card';

function onSearchInput() {
    const bar = document.getElementById('search-progress');
    bar.classList.add('active');
    clearTimeout(searchDebounceTimer);
    searchDebounceTimer = setTimeout(() => {
        filterEntries();
        bar.classList.remove('active');
    }, 300);
}

// ========== Save Entry ==========

async function saveEntry(event) {
    event.preventDefault();

    const id = document.getElementById('entry-id').value;
    const date = document.getElementById('entry-date').value;
    const time = document.getElementById('entry-time').value;
    const title = document.getElementById('entry-title').value.trim();
    const content = document.getElementById('entry-content').value;
    const richContent = quillEditor ? quillEditor.root.innerHTML : '';

    // Get selected categories
    const categoryCheckboxes = document.querySelectorAll('#category-select input[type="checkbox"]:checked');
    const categories = Array.from(categoryCheckboxes).map(cb => cb.value);

    const now = new Date().toISOString();

    const images = currentEntryImages.map(img => ({
        id: img.id, name: img.name, data: img.data, thumb: img.thumb
    }));

    if (id) {
        // Update existing
        await DB.updateEntry(id, {
            date, time, title, content, richContent,
            categories,
            tags: [...currentEntryTags],
            people: [...currentEntryPeople],
            placeName: document.getElementById('entry-place-name').value.trim(),
            locations: [...currentEntryLocations],
            weather: currentEntryWeather || null,
            images: images,
            dtUpdated: now
        });
    } else {
        // Create new
        const entry = {
            id: generateId(),
            date, time, title, content, richContent,
            categories,
            tags: [...currentEntryTags],
            people: [...currentEntryPeople],
            placeName: document.getElementById('entry-place-name').value.trim(),
            locations: [...currentEntryLocations],
            weather: currentEntryWeather || null,
            images: images,
            dtCreated: now,
            dtUpdated: now
        };
        await DB.addEntry(entry);
    }

    entryFormSaved = true;

    // Refresh view if returning to entry-view
    if (previousPage === 'entry-view' && id) {
        const updated = DB.getEntries().find(e => e.id === id);
        if (updated) {
            DB.loadEntryImages(updated);
            navigateBack();
            showEntryView(updated, true);
            return;
        }
    }
    navigateBack();
}

// ========== Delete Entry ==========

async function deleteEntry() {
    const id = document.getElementById('entry-id').value;
    if (!id) return;
    if (shouldConfirmDelete() && !confirm('Are you sure you want to delete this entry?')) return;
    await DB.deleteEntryById(id);
    navigateBack();
}

// ========== Entry List ==========

function refreshEntryList() {
    populateFilterDropdowns();
    renderViewsBar();
    // Apply default entry view if no view is already active
    if (!activeViewId && typeof getDefaultEntryView === 'function') {
        const defaultView = getDefaultEntryView();
        if (defaultView) {
            activeViewId = defaultView.id;
            const viewSelect = document.getElementById('view-select');
            if (viewSelect) viewSelect.value = defaultView.id;
        }
    }
    // Restore page size selector
    const pageSel = document.getElementById('page-size-select');
    if (pageSel) pageSel.value = currentPageSize;
    updateViewModeButton();
    updateColToggleForViewMode();
    // Collapse filter fields by default on Android
    if (document.body.classList.contains('android')) {
        const header = document.querySelector('.filter-toggle-header');
        const body = document.getElementById('filter-fields-body');
        if (header && body) {
            header.classList.add('collapsed');
            body.classList.add('collapsed');
        }
    }
    currentPageNum = 1;
    filterEntries();
}

function toggleFilterFields() {
    const header = document.querySelector('.filter-toggle-header');
    const body = document.getElementById('filter-fields-body');
    if (!header || !body) return;
    const collapsed = !body.classList.contains('collapsed');
    body.classList.toggle('collapsed', collapsed);
    header.classList.toggle('collapsed', collapsed);
}

function toggleViewMode() {
    entryViewMode = entryViewMode === 'card' ? 'list' : 'card';
    localStorage.setItem('entry_view_mode', entryViewMode);
    updateViewModeButton();
    updateColToggleForViewMode();
    renderPaginatedEntries();
}

function updateViewModeButton() {
    const btn = document.getElementById('btn-view-mode');
    if (!btn) return;
    btn.innerHTML = entryViewMode === 'card' ? '&#x1F4CB; Card' : '&#x1F4C4; List';
}

function updateColToggleForViewMode() {
    const colToggle = document.getElementById('col-toggle-entries');
    if (!colToggle) return;
    if (entryViewMode === 'list') {
        colToggle.style.display = 'none';
        // Reset to 1 column to avoid table layout issues
        const target = document.getElementById('entries-container');
        if (target) {
            target.classList.remove('cols-2', 'cols-3');
            target.classList.add('cols-1');
        }
        const page = colToggle.closest('.page');
        if (page) page.classList.remove('page-wide');
    } else {
        colToggle.style.display = '';
    }
}

function populateFilterDropdowns() {
    const catSelect = document.getElementById('filter-category');
    const tagSelect = document.getElementById('filter-tag');

    // Preserve current selections
    const currentCat = catSelect.value;
    const currentTag = tagSelect.value;

    const categories = DB.getCategories();
    catSelect.innerHTML = '<option value="">All Categories</option>' +
        categories.map(c => `<option value="${c}">${c}</option>`).join('');

    const tags = DB.getAllTags();
    tagSelect.innerHTML = '<option value="">All Tags</option>' +
        tags.map(t => `<option value="${t}">${t}</option>`).join('');

    catSelect.value = currentCat;
    tagSelect.value = currentTag;
}

function filterEntries() {
    currentPageNum = 1;
    const search = document.getElementById('search-input').value.toLowerCase();
    const category = document.getElementById('filter-category').value;
    const tag = document.getElementById('filter-tag').value;
    const dateFrom = document.getElementById('filter-date-from').value;
    const dateTo = document.getElementById('filter-date-to').value;

    let entries = DB.getEntries();

    // Apply active custom view (filter + sort)
    let activeView = null;
    if (activeViewId) {
        activeView = getCustomViews().find(v => v.id === activeViewId);
        if (activeView) entries = applyView(entries, activeView);
    }

    if (search) {
        entries = entries.filter(e =>
            (e.title || '').toLowerCase().includes(search) ||
            (e.content || '').toLowerCase().includes(search) ||
            (e.tags || []).some(t => t.toLowerCase().includes(search)) ||
            (e.placeName || '').toLowerCase().includes(search) ||
            (e.locations || []).some(l => (l.address || '').toLowerCase().includes(search)) ||
            (e.people || []).some(p => (p.firstName + ' ' + p.lastName).toLowerCase().includes(search))
        );
    }

    if (category) {
        entries = entries.filter(e => (e.categories || []).includes(category));
    }

    if (tag) {
        entries = entries.filter(e => (e.tags || []).includes(tag));
    }

    if (dateFrom) {
        entries = entries.filter(e => e.date >= dateFrom);
    }

    if (dateTo) {
        entries = entries.filter(e => e.date <= dateTo);
    }

    // Sort: use view's orderBy if set, otherwise use default sort setting
    if (!activeView || !activeView.orderBy || activeView.orderBy.length === 0) {
        const defaultSort = getDefaultEntryListOrder();
        entries.sort((a, b) => {
            const cmp = compareEntryField(a, b, defaultSort.field);
            return defaultSort.direction === 'desc' ? -cmp : cmp;
        });
    }

    updateFilterCriteriaBox(entries.length, { search, category, tag, dateFrom, dateTo });

    // Apply view's display mode if set, otherwise keep current
    if (activeView && activeView.displayMode) {
        entryViewMode = activeView.displayMode;
        updateViewModeButton();
        updateColToggleForViewMode();
    }

    // Cache full filtered list for pagination
    filteredEntriesCache = entries;

    // Group entries if view has groupBy (no pagination for grouped)
    if (activeView && activeView.groupBy) {
        document.getElementById('pagination-controls').style.display = 'none';
        updateEntriesPageTitle(entries.length, entries.length);
        renderGroupedEntryList(entries, activeView.groupBy);
    } else {
        renderPaginatedEntries();
    }
}

function updateFilterCriteriaBox(count, filters) {
    const box = document.getElementById('filter-criteria-box');
    const chips = [];

    if (activeViewId) {
        const view = getCustomViews().find(v => v.id === activeViewId);
        if (view) chips.push(`<span class="fc-chip fc-chip-view">View: ${escapeHtml(view.name)}</span>`);
    }
    if (filters.search) chips.push(`<span class="fc-chip">Search: "${escapeHtml(filters.search)}"</span>`);
    if (filters.category) chips.push(`<span class="fc-chip">Category: ${escapeHtml(filters.category)}</span>`);
    if (filters.tag) chips.push(`<span class="fc-chip">Tag: ${escapeHtml(filters.tag)}</span>`);
    if (filters.dateFrom) chips.push(`<span class="fc-chip">From: ${formatDate(filters.dateFrom)}</span>`);
    if (filters.dateTo) chips.push(`<span class="fc-chip">To: ${formatDate(filters.dateTo)}</span>`);

    if (chips.length === 0) {
        box.style.display = 'none';
        return;
    }

    box.style.display = 'flex';
    box.innerHTML = `<span class="fc-count">${count} ${count === 1 ? 'entry' : 'entries'}</span><span class="fc-sep">|</span>${chips.join('')}`;
}

function updateEntriesPageTitle(pageCount, totalCount) {
    const el = document.getElementById('entries-page-title');
    if (!el) return;
    let viewLabel = '';
    if (activeViewId) {
        const view = getCustomViews().find(v => v.id === activeViewId);
        if (view) viewLabel = ` \u2014 ${view.name}`;
    }
    if (pageCount === totalCount) {
        el.textContent = `${totalCount} Entries${viewLabel}`;
    } else {
        el.textContent = `${pageCount} of ${totalCount} Entries${viewLabel}`;
    }
}

function renderEntryList(entries, startOffset) {
    viewEntryList = entries.map(e => e.id);
    const container = document.getElementById('entries-container');
    const offset = startOffset || 0;

    if (entries.length === 0) {
        container.innerHTML = '<div class="no-data">No entries found.</div>';
        return;
    }

    const fields = typeof getEntryListFields === 'function' ? getEntryListFields() : {};
    const show = (key) => fields[key] !== false;

    if (entryViewMode === 'list') {
        container.innerHTML = renderEntryTable(entries, offset, show);
    } else {
        container.innerHTML = entries.map((e, idx) => renderSingleEntryCard(e, offset + idx + 1, show)).join('');
    }

    updateSelectCount();
}

function renderEntryTable(entries, offset, show) {
    let html = '<table class="entry-table"><thead><tr>';
    html += '<th class="et-num">#</th>';
    if (show('date')) html += '<th>Date</th>';
    if (show('time')) html += '<th>Time</th>';
    if (show('title')) html += '<th>Title</th>';
    if (show('content')) html += '<th>Content</th>';
    if (show('categories')) html += '<th>Categories</th>';
    if (show('tags')) html += '<th>Tags</th>';
    if (show('places')) html += '<th>Place</th>';
    if (show('weather')) html += '<th>Weather</th>';
    html += '<th class="et-actions"></th>';
    html += '</tr></thead><tbody>';

    entries.forEach((e, idx) => {
        const num = offset + idx + 1;
        const checked = selectedEntryIds.has(e.id) ? ' class="et-selected"' : '';
        const clickAction = selectMode ? `toggleEntrySelect('${e.id}')` : `viewEntry('${e.id}')`;

        html += `<tr${checked} onclick="${clickAction}" style="cursor:pointer">`;
        html += `<td class="et-num">${num}</td>`;
        if (show('date')) html += `<td class="et-date">${formatDate(e.date)}</td>`;
        if (show('time')) html += `<td class="et-time">${e.time ? formatTime(e.time) : ''}</td>`;
        if (show('title')) html += `<td class="et-title">${escapeHtml(e.title)}</td>`;
        if (show('content')) html += `<td class="et-content">${escapeHtml((e.content || '').substring(0, 80))}</td>`;
        if (show('categories')) html += `<td class="et-tags">${(e.categories || []).map(c => `<span class="tag-item tag-clickable"${getItemColorStyle('category', c)} onclick="filterByCategory(event, '${escapeHtml(c)}')">${getIconHtml('category', c)}${escapeHtml(c)}</span>`).join(' ')}</td>`;
        if (show('tags')) html += `<td class="et-tags">${(e.tags || []).map(t => `<span class="tag-item tag-clickable"${getItemColorStyle('tag', t)} onclick="filterByTag(event, '${escapeHtml(t)}')">${getIconHtml('tag', t)}${escapeHtml(t)}</span>`).join(' ')}</td>`;
        if (show('places')) html += `<td class="et-place">${escapeHtml(e.placeName || '')}</td>`;
        if (show('weather')) html += `<td class="et-weather">${e.weather ? escapeHtml(Weather.formatWeather(e.weather)) : ''}</td>`;
        html += `<td class="et-actions"><button class="entry-card-delete" style="opacity:1;position:static;" onclick="event.stopPropagation(); deleteEntryFromList('${e.id}')" title="Delete entry">&#x1F5D1; DEL</button></td>`;
        html += '</tr>';
    });

    html += '</tbody></table>';
    return html;
}

function renderGroupedEntryList(entries, groupByField) {
    viewEntryList = entries.map(e => e.id);
    const container = document.getElementById('entries-container');

    if (entries.length === 0) {
        container.innerHTML = '<div class="no-data">No entries found.</div>';
        return;
    }

    // Build groups
    const groups = new Map();
    entries.forEach(e => {
        let keys;
        if (groupByField === 'categories' || groupByField === 'tags') {
            keys = (e[groupByField] || []);
            if (keys.length === 0) keys = ['(none)'];
        } else if (groupByField === 'weather') {
            keys = [e.weather ? (e.weather.description || 'Weather data') : '(no weather)'];
        } else {
            keys = [e[groupByField] || '(empty)'];
        }
        keys.forEach(key => {
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(e);
        });
    });

    const fields = typeof getEntryListFields === 'function' ? getEntryListFields() : {};
    const show = (key) => fields[key] !== false;
    let globalIdx = 0;

    let html = '';
    for (const [groupName, groupEntries] of groups) {
        html += `<div class="entry-group">`;
        html += `<div class="entry-group-header"><span class="entry-group-name">${escapeHtml(groupName)}</span><span class="entry-group-count">${groupEntries.length}</span></div>`;
        html += groupEntries.map(e => {
            globalIdx++;
            return renderSingleEntryCard(e, globalIdx, show);
        }).join('');
        html += `</div>`;
    }

    container.innerHTML = html;
    updateSelectCount();
}

function renderSingleEntryCard(e, idx, show) {
    const dateStr = show('date') ? formatDate(e.date) : '';
    const timeStr = show('time') && e.time ? (dateStr ? ' ' + formatTime(e.time) : formatTime(e.time)) : '';
    const headerDate = dateStr || timeStr ? `<span class="entry-card-date">${dateStr}${timeStr}</span>` : '';
    const checked = selectedEntryIds.has(e.id) ? 'checked' : '';

    let html = `<div class="entry-row"><span class="entry-row-num">#${idx}</span>`;
    html += `<div class="entry-card${selectMode ? ' select-mode' : ''}${checked ? ' selected' : ''}" onclick="${selectMode ? `toggleEntrySelect('${e.id}')` : `viewEntry('${e.id}')`}">`;
    html += `<button class="entry-card-delete" onclick="event.stopPropagation(); deleteEntryFromList('${e.id}')" title="Delete entry">&#x1F5D1; DEL</button>`;

    if (selectMode) {
        html += `<div class="entry-card-checkbox"><input type="checkbox" ${checked} onclick="event.stopPropagation(); toggleEntrySelect('${e.id}')"></div>`;
    }

    html += `<div class="entry-card-body">`;
    if (show('title') || headerDate) {
        html += `<div class="entry-card-header">`;
        html += headerDate;
        if (show('title')) html += `<span class="entry-card-title">${escapeHtml(e.title)}</span>`;
        html += `</div>`;
    }
    if (show('content') && e.content) {
        html += `<div class="entry-card-preview">${escapeHtml((e.content || '').substring(0, 120))}</div>`;
    }
    if (show('places') && (e.placeName || (e.locations || []).length > 0)) {
        const placeText = e.placeName ? escapeHtml(e.placeName) : '';
        const locCount = (e.locations || []).length;
        const locText = locCount > 0 ? ` (${locCount} location${locCount > 1 ? 's' : ''})` : '';
        html += `<div class="entry-card-places">${placeText}${locText}</div>`;
    }
    if (show('weather') && e.weather) {
        html += `<div class="entry-card-weather">${escapeHtml(Weather.formatWeather(e.weather))}</div>`;
    }
    if (show('images') && e.images && e.images.length > 0) {
        const thumbs = e.images.slice(0, 4).map(img =>
            `<img class="entry-card-thumb" src="${img.thumb}" alt="${escapeHtml(img.name || 'image')}">`
        ).join('');
        const more = e.images.length > 4 ? `<span class="entry-card-thumb-more">+${e.images.length - 4}</span>` : '';
        html += `<div class="entry-card-thumbs">${thumbs}${more}</div>`;
    }
    const tagItems = [];
    if (show('categories')) tagItems.push(...(e.categories || []).map(c =>
        `<span class="tag-item tag-clickable"${getItemColorStyle('category', c)} onclick="filterByCategory(event, '${escapeHtml(c)}')">${getIconHtml('category', c)}${escapeHtml(c)}</span>`));
    if (show('tags')) tagItems.push(...(e.tags || []).map(t =>
        `<span class="tag-item tag-clickable"${getItemColorStyle('tag', t)} onclick="filterByTag(event, '${escapeHtml(t)}')">${getIconHtml('tag', t)}${escapeHtml(t)}</span>`));
    if (tagItems.length > 0) {
        html += `<div class="entry-card-tags">${tagItems.join('')}</div>`;
    }
    if (e.people && e.people.length > 0) {
        const pplHtml = e.people.map(p => {
            const fullName = p.firstName + ' ' + p.lastName;
            const icon = getIconHtml('person', fullName);
            return `<span class="tag-item people-tag">${icon || '&#x1F464; '}${escapeHtml(fullName)}</span>`;
        }).join('');
        html += `<div class="entry-card-tags">${pplHtml}</div>`;
    }
    html += `</div></div></div>`;
    return html;
}

// ========== Pagination ==========

function onPageSizeChange() {
    const val = parseInt(document.getElementById('page-size-select').value);
    currentPageSize = val;
    currentPageNum = 1;
    localStorage.setItem('entries_page_size', val);
    renderPaginatedEntries();
}

function goToPage(page) {
    currentPageNum = page;
    renderPaginatedEntries();
    // Scroll to top of entries
    const container = document.getElementById('entries-container');
    if (container) container.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderPaginatedEntries() {
    const entries = filteredEntriesCache;
    const total = entries.length;
    const controls = document.getElementById('pagination-controls');

    // Keep full list for prev/next navigation in entry view
    viewEntryList = entries.map(e => e.id);

    // Show all if pageSize is 0
    if (!currentPageSize || currentPageSize <= 0) {
        controls.style.display = 'none';
        updateEntriesPageTitle(total, total);
        renderEntryList(entries, 0);
        return;
    }

    const totalPages = Math.max(1, Math.ceil(total / currentPageSize));
    if (currentPageNum > totalPages) currentPageNum = totalPages;

    const start = (currentPageNum - 1) * currentPageSize;
    const pageEntries = entries.slice(start, start + currentPageSize);

    updateEntriesPageTitle(pageEntries.length, total);
    renderEntryList(pageEntries, start);
    renderPaginationControls(totalPages, total);
}

function renderPaginationControls(totalPages, totalEntries) {
    const controls = document.getElementById('pagination-controls');

    if (totalPages <= 1) {
        controls.style.display = 'none';
        return;
    }

    controls.style.display = 'flex';
    let html = '';

    // Prev button
    html += `<button class="btn-small pag-btn" onclick="goToPage(${currentPageNum - 1})" ${currentPageNum <= 1 ? 'disabled' : ''}>&#9664; Prev</button>`;

    // Page numbers
    html += '<div class="pag-pages">';
    const maxVisible = 7;
    let startPage = Math.max(1, currentPageNum - Math.floor(maxVisible / 2));
    let endPage = Math.min(totalPages, startPage + maxVisible - 1);
    if (endPage - startPage < maxVisible - 1) startPage = Math.max(1, endPage - maxVisible + 1);

    if (startPage > 1) {
        html += `<button class="btn-small pag-num" onclick="goToPage(1)">1</button>`;
        if (startPage > 2) html += `<span class="pag-ellipsis">&hellip;</span>`;
    }

    for (let i = startPage; i <= endPage; i++) {
        html += `<button class="btn-small pag-num${i === currentPageNum ? ' pag-active' : ''}" onclick="goToPage(${i})">${i}</button>`;
    }

    if (endPage < totalPages) {
        if (endPage < totalPages - 1) html += `<span class="pag-ellipsis">&hellip;</span>`;
        html += `<button class="btn-small pag-num" onclick="goToPage(${totalPages})">${totalPages}</button>`;
    }
    html += '</div>';

    // Next button
    html += `<button class="btn-small pag-btn" onclick="goToPage(${currentPageNum + 1})" ${currentPageNum >= totalPages ? 'disabled' : ''}>Next &#9654;</button>`;

    // Info
    const start = (currentPageNum - 1) * currentPageSize + 1;
    const end = Math.min(currentPageNum * currentPageSize, totalEntries);
    html += `<span class="pag-info">${start}-${end} of ${totalEntries}</span>`;

    controls.innerHTML = html;
}

async function deleteEntryFromList(id) {
    const entry = DB.getEntries().find(e => e.id === id);
    const name = entry ? (entry.title || 'Untitled') : 'this entry';
    if (shouldConfirmDelete() && !confirm(`Delete "${name}"? This cannot be undone.`)) return;
    await DB.deleteEntryById(id);
    filterEntries();
}

function viewEntry(id) {
    // Clear search highlight unless called from dashboard search
    if (typeof _dashboardSearchTerm !== 'undefined' && !_viewEntryFromSearch) {
        _dashboardSearchTerm = '';
    }
    _viewEntryFromSearch = false;
    const entry = DB.getEntries().find(e => e.id === id);
    if (!entry) return;
    DB.loadEntryImages(entry);
    showEntryView(entry);
}

function showEntryView(entry, skipNav) {
    currentViewEntryId = entry.id;
    if (!skipNav) navigateTo('entry-view');

    document.getElementById('entry-view-title').textContent = entry.title || 'Untitled';

    // Populate header date/time above card
    const dateStr = entry.date ? formatDate(entry.date) : '';
    const timeStr = entry.time || '';
    const headerDateEl = document.getElementById('ev-header-date');
    const headerTimeEl = document.getElementById('ev-header-time');
    if (headerDateEl) {
        headerDateEl.textContent = dateStr ? '\uD83D\uDCC5 ' + dateStr : '';
        headerDateEl.style.display = dateStr ? '' : 'none';
    }
    if (headerTimeEl) {
        const formattedTime = timeStr ? formatTime(timeStr) : '';
        headerTimeEl.textContent = formattedTime ? '\uD83D\uDD52 ' + formattedTime : '';
        headerTimeEl.style.display = formattedTime ? '' : 'none';
    }

    // Set pin checkbox state
    const pinToggle = document.getElementById('ev-pin-toggle');
    if (pinToggle) pinToggle.checked = !!entry.pinned;

    // === Main tab: Content, Rich Content ===
    let mainHtml = '';

    if (entry.content) {
        mainHtml += `<div class="ev-row ev-row-block"><span class="ev-label">Content</span><div class="ev-value ev-content">${escapeHtml(entry.content)}</div></div>`;
    }

    if (entry.richContent && entry.richContent !== '<p><br></p>') {
        mainHtml += `<div class="ev-row ev-row-block"><span class="ev-label">Rich Content</span><div class="ev-value ev-rich-content">${entry.richContent}</div></div>`;
    }

    if (entry.images && entry.images.length > 0) {
        const imgGrid = entry.images.map((img, i) =>
            `<div class="ev-img-thumb-wrap">` +
            `<img class="ev-img-thumb" src="${img.thumb}" alt="${escapeHtml(img.name || 'image')}" onclick="openLightbox(${i})">` +
            `<button type="button" class="ev-img-delete" onclick="deleteViewerImage(${i})" title="Delete image">&times;</button>` +
            `</div>`
        ).join('');
        mainHtml += `<div class="ev-row ev-row-block"><span class="ev-label">Images (${entry.images.length})</span><div class="ev-value ev-images-grid">${imgGrid}</div></div>`;
    }

    if (!mainHtml) mainHtml = '<div class="no-data">No main content.</div>';

    // === Misc tab: Categories, Tags, Places, Locations, Weather, Timestamps ===
    let miscHtml = '';

    if (entry.categories && entry.categories.length > 0) {
        const cats = entry.categories.map(c => `<span class="tag-item tag-clickable"${getItemColorStyle('category', c)} onclick="navigateToFilteredList('category', '${escapeHtml(c)}')">${getIconHtml('category', c)}${escapeHtml(c)}</span>`).join('');
        miscHtml += `<div class="ev-row"><span class="ev-label">Categories</span><div class="ev-value ev-tags-row">${cats}</div></div>`;
    }

    if (entry.tags && entry.tags.length > 0) {
        const tags = entry.tags.map(t => `<span class="tag-item tag-clickable"${getItemColorStyle('tag', t)} onclick="navigateToFilteredList('tag', '${escapeHtml(t)}')">${getIconHtml('tag', t)}${escapeHtml(t)}</span>`).join('');
        miscHtml += `<div class="ev-row"><span class="ev-label">Tags</span><div class="ev-value ev-tags-row">${tags}</div></div>`;
    }

    if (entry.people && entry.people.length > 0) {
        const ppl = entry.people.map(p => {
            const fullName = p.firstName + ' ' + p.lastName;
            const icon = getIconHtml('person', fullName);
            return `<span class="tag-item people-tag">${icon || '&#x1F464; '}${escapeHtml(fullName)}</span>`;
        }).join('');
        miscHtml += `<div class="ev-row"><span class="ev-label">People</span><div class="ev-value ev-tags-row">${ppl}</div></div>`;
    }

    if (entry.placeName) {
        miscHtml += `<div class="ev-row"><span class="ev-label">Place</span><span class="ev-value">${escapeHtml(entry.placeName)}</span></div>`;
    }

    const locs = entry.locations || [];
    if (locs.length > 0) {
        const locsHtml = locs.map(loc => {
            const hasCoords = loc.lat != null && loc.lng != null;
            const coordsText = hasCoords ? `<span class="ev-coords">${loc.lat.toFixed(6)}, ${loc.lng.toFixed(6)}</span>` : '';
            const mapLink = hasCoords
                ? `<a href="https://www.google.com/maps?q=${loc.lat},${loc.lng}" target="_blank" class="btn-small btn-map-link">&#128205; Map</a>`
                : (loc.address
                    ? `<a href="https://www.google.com/maps/search/${encodeURIComponent(loc.address)}" target="_blank" class="btn-small btn-map-link">&#128205; Map</a>`
                    : '');
            return `<div class="ev-location-item"><div>${escapeHtml(loc.address)}${coordsText}</div>${mapLink}</div>`;
        }).join('');
        miscHtml += `<div class="ev-row ev-row-block"><span class="ev-label">Locations</span><div class="ev-value">${locsHtml}</div></div>`;
    }

    if (entry.weather) {
        miscHtml += `<div class="ev-row"><span class="ev-label">Weather</span><span class="ev-value">${escapeHtml(Weather.formatWeather(entry.weather))}</span></div>`;
    }

    if (entry.dtCreated || entry.dtUpdated) {
        miscHtml += `<div class="ev-meta-row">`;
        if (entry.dtCreated) miscHtml += `<span class="ev-meta-item"><span class="ev-meta-label">Created</span> ${new Date(entry.dtCreated).toLocaleString()}</span>`;
        if (entry.dtUpdated) miscHtml += `<span class="ev-meta-item"><span class="ev-meta-label">Updated</span> ${new Date(entry.dtUpdated).toLocaleString()}</span>`;
        miscHtml += `</div>`;
    }

    document.getElementById('ev-main-content').innerHTML = mainHtml;

    const miscGroup = document.getElementById('ev-misc-group');
    const miscBody = document.getElementById('ev-misc-content');
    if (miscHtml) {
        miscBody.innerHTML = miscHtml;
        miscGroup.style.display = '';
    } else {
        miscGroup.style.display = 'none';
    }

    // Apply collapse-by-default preference
    const collapseDefault = localStorage.getItem('ev_misc_collapsed') === '1';
    setMiscCollapsed(collapseDefault);

    // Apply custom font settings
    if (typeof applyViewerFont === 'function') applyViewerFont();
    updateViewNavButtons();

    // Highlight search term from dashboard search
    if (typeof _dashboardSearchTerm === 'string' && _dashboardSearchTerm) {
        highlightInViewer(_dashboardSearchTerm);
    }
}

function highlightInViewer(term) {
    if (!term) return;
    const targets = document.querySelectorAll('#page-entry-view .ev-content, #page-entry-view .ev-rich-content, #page-entry-view #entry-view-title');
    const escaped = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp('(' + escaped + ')', 'gi');
    targets.forEach(el => {
        highlightTextNodes(el, regex);
    });
}

function highlightTextNodes(node, regex) {
    if (node.nodeType === 3) { // Text node
        const text = node.textContent;
        if (!regex.test(text)) return;
        regex.lastIndex = 0;
        const frag = document.createDocumentFragment();
        let lastIdx = 0;
        let match;
        while ((match = regex.exec(text)) !== null) {
            if (match.index > lastIdx) {
                frag.appendChild(document.createTextNode(text.substring(lastIdx, match.index)));
            }
            const mark = document.createElement('mark');
            mark.className = 'search-highlight';
            mark.textContent = match[1];
            frag.appendChild(mark);
            lastIdx = regex.lastIndex;
        }
        if (lastIdx < text.length) {
            frag.appendChild(document.createTextNode(text.substring(lastIdx)));
        }
        node.parentNode.replaceChild(frag, node);
    } else if (node.nodeType === 1 && node.tagName !== 'MARK') {
        // Process child nodes in reverse to handle live NodeList changes
        const children = Array.from(node.childNodes);
        children.forEach(child => highlightTextNodes(child, regex));
    }
}

function toggleMiscInfo() {
    const body = document.getElementById('ev-misc-content');
    const collapsed = body.style.display !== 'none';
    setMiscCollapsed(collapsed);
}

function setMiscCollapsed(collapsed) {
    const body = document.getElementById('ev-misc-content');
    const arrow = document.getElementById('ev-misc-arrow');
    if (collapsed) {
        body.style.display = 'none';
        if (arrow) arrow.innerHTML = '&#9654;';
    } else {
        body.style.display = '';
        if (arrow) arrow.innerHTML = '&#9660;';
    }
}

let currentViewEntryId = null;
let viewEntryList = []; // entry IDs in current list order for prev/next nav

async function togglePinEntry() {
    if (!currentViewEntryId) return;
    const pinToggle = document.getElementById('ev-pin-toggle');
    const pinned = pinToggle ? pinToggle.checked : false;
    if (pinned) {
        const max = typeof getMaxPinnedEntries === 'function' ? getMaxPinnedEntries() : 10;
        const currentPinned = DB.getEntries().filter(e => e.pinned && e.id !== currentViewEntryId).length;
        if (currentPinned >= max) {
            alert(`Maximum of ${max} pinned entries allowed. Unpin another entry first, or increase the limit in Settings > Preferences.`);
            pinToggle.checked = false;
            return;
        }
    }
    await DB.updateEntry(currentViewEntryId, { pinned: pinned });
}

async function deleteViewedEntry() {
    if (!currentViewEntryId) return;
    const entry = DB.getEntries().find(e => e.id === currentViewEntryId);
    const name = entry ? (entry.title || 'Untitled') : 'this entry';
    if (shouldConfirmDelete() && !confirm(`Delete "${name}"? This cannot be undone.`)) return;
    await DB.deleteEntryById(currentViewEntryId);
    currentViewEntryId = null;
    navigateBack();
}

function viewPrevEntry() {
    if (!currentViewEntryId || viewEntryList.length === 0) return;
    const idx = viewEntryList.indexOf(currentViewEntryId);
    if (idx <= 0) return;
    const entry = DB.getEntries().find(e => e.id === viewEntryList[idx - 1]);
    if (entry) { DB.loadEntryImages(entry); showEntryView(entry, true); }
}

function viewNextEntry() {
    if (!currentViewEntryId || viewEntryList.length === 0) return;
    const idx = viewEntryList.indexOf(currentViewEntryId);
    if (idx < 0 || idx >= viewEntryList.length - 1) return;
    const entry = DB.getEntries().find(e => e.id === viewEntryList[idx + 1]);
    if (entry) { DB.loadEntryImages(entry); showEntryView(entry, true); }
}

function updateViewNavButtons() {
    const idx = viewEntryList.indexOf(currentViewEntryId);
    const prevBtn = document.getElementById('view-prev-btn');
    const nextBtn = document.getElementById('view-next-btn');
    const counter = document.getElementById('view-nav-counter');
    if (!prevBtn || !nextBtn) return;

    if (viewEntryList.length <= 1) {
        prevBtn.style.display = 'none';
        nextBtn.style.display = 'none';
        if (counter) counter.style.display = 'none';
        return;
    }

    prevBtn.style.display = '';
    nextBtn.style.display = '';
    prevBtn.disabled = idx <= 0;
    nextBtn.disabled = idx >= viewEntryList.length - 1;
    if (counter) {
        counter.style.display = '';
        counter.textContent = `${idx + 1} / ${viewEntryList.length}`;
    }
}

// ========== Image Lightbox ==========

let lightboxImages = [];
let lightboxIndex = 0;

function openLightbox(index) {
    const entry = DB.getEntries().find(e => e.id === currentViewEntryId);
    if (!entry || !entry.images) return;
    DB.loadEntryImages(entry);
    lightboxImages = entry.images;
    lightboxIndex = index;
    renderLightbox();
    document.getElementById('image-lightbox').style.display = 'flex';
}

function closeLightbox(event) {
    if (event && event.target !== event.currentTarget) return;
    document.getElementById('image-lightbox').style.display = 'none';
}

function lightboxPrev() {
    if (lightboxIndex > 0) { lightboxIndex--; renderLightbox(); }
}

function lightboxNext() {
    if (lightboxIndex < lightboxImages.length - 1) { lightboxIndex++; renderLightbox(); }
}

function renderLightbox() {
    const img = lightboxImages[lightboxIndex];
    if (!img) return;
    document.getElementById('lightbox-img').src = img.data || img.thumb;
    document.getElementById('lightbox-counter').textContent = (lightboxIndex + 1) + ' / ' + lightboxImages.length;
    // Update nav button states
    const prevBtn = document.getElementById('lightbox-prev');
    const nextBtn = document.getElementById('lightbox-next');
    if (prevBtn) prevBtn.disabled = lightboxIndex <= 0;
    if (nextBtn) nextBtn.disabled = lightboxIndex >= lightboxImages.length - 1;
}

// ========== Delete Image from Entry Viewer ==========

async function deleteViewerImage(index) {
    if (!currentViewEntryId) return;
    const entry = DB.getEntryById(currentViewEntryId);
    if (!entry || !entry.images || index < 0 || index >= entry.images.length) return;

    const warnDelete = localStorage.getItem('journal_warn_delete') !== 'false';
    if (warnDelete) {
        const name = entry.images[index].name || ('Image ' + (index + 1));
        if (!confirm('Delete "' + name + '" from this entry?')) return;
    }

    entry.images.splice(index, 1);
    await DB.updateEntry(currentViewEntryId, { images: entry.images });
    showEntryView(entry, true);
}

// ========== Download Image from Lightbox ==========

function lightboxDownload() {
    const img = lightboxImages[lightboxIndex];
    if (!img) return;
    const data = img.data || img.thumb;
    if (!data) { alert('Image data not available. Try closing and reopening the image.'); return; }
    const filename = img.name || ('journal_image_' + (lightboxIndex + 1) + '.jpg');

    // Extract base64 and mime from data URL
    const parts = data.split(',');
    const base64 = parts.length > 1 ? parts[1] : parts[0];
    const mimeMatch = data.match(/^data:(.*?);/);
    const mime = mimeMatch ? mimeMatch[1] : 'image/jpeg';

    try {
    if (window.AndroidBridge && window.AndroidBridge.saveFile) {
        window.AndroidBridge.saveFile(filename, base64, mime);
    } else {
        // Web: decode base64 to binary blob, use same pattern as downloadFile()
        const byteString = atob(base64);
        const ab = new ArrayBuffer(byteString.length);
        const ia = new Uint8Array(ab);
        for (let i = 0; i < byteString.length; i++) ia[i] = byteString.charCodeAt(i);
        const blob = new Blob([ab], { type: mime });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }
    } catch (err) {
        alert('Save failed: ' + err.message);
    }
}

// ========== Lightbox Swipe Navigation ==========

(function initLightboxSwipe() {
    let startX = 0, startY = 0, tracking = false;
    const THRESHOLD = 50;
    const MAX_Y = 80;

    document.addEventListener('touchstart', function(e) {
        const lightbox = document.getElementById('image-lightbox');
        if (!lightbox || lightbox.style.display === 'none') return;
        if (!lightbox.contains(e.target)) return;
        startX = e.touches[0].clientX;
        startY = e.touches[0].clientY;
        tracking = true;
    }, { passive: true });

    document.addEventListener('touchend', function(e) {
        if (!tracking) return;
        tracking = false;
        const dx = e.changedTouches[0].clientX - startX;
        const dy = e.changedTouches[0].clientY - startY;
        if (Math.abs(dy) > MAX_Y || Math.abs(dx) < THRESHOLD) return;
        if (dx < 0) {
            lightboxNext();
        } else {
            lightboxPrev();
        }
    }, { passive: true });
})();

// ========== Swipe Navigation (Android) ==========

(function initSwipeNav() {
    let startX = 0, startY = 0, tracking = false;
    const THRESHOLD = 60;
    const MAX_Y = 80;

    function getTarget() {
        return document.querySelector('.entry-view-card');
    }

    document.addEventListener('touchstart', function(e) {
        if (!document.body.classList.contains('android')) return;
        const card = getTarget();
        if (!card || !card.closest('#page-entry-view') || card.closest('#page-entry-view').style.display === 'none') return;
        if (!card.contains(e.target) && e.target !== card) return;
        startX = e.touches[0].clientX;
        startY = e.touches[0].clientY;
        tracking = true;
    }, { passive: true });

    document.addEventListener('touchend', function(e) {
        if (!tracking) return;
        tracking = false;
        const dx = e.changedTouches[0].clientX - startX;
        const dy = e.changedTouches[0].clientY - startY;
        if (Math.abs(dy) > MAX_Y || Math.abs(dx) < THRESHOLD) return;
        if (dx < 0) {
            viewNextEntry();
        } else {
            viewPrevEntry();
        }
    }, { passive: true });
})();

function editViewedEntry() {
    if (!currentViewEntryId) return;
    const entry = DB.getEntries().find(e => e.id === currentViewEntryId);
    if (!entry) return;
    editEntry(entry.id);
}

function editEntry(id) {
    const entry = DB.getEntries().find(e => e.id === id);
    if (!entry) return;
    DB.loadEntryImages(entry);
    navigateTo('entry-form');
    prepareEntryForm(entry);
}

function filterByTag(event, tag) {
    event.stopPropagation();
    document.getElementById('filter-tag').value = tag;
    filterEntries();
}

function filterByCategory(event, category) {
    event.stopPropagation();
    document.getElementById('filter-category').value = category;
    filterEntries();
}

function clearFilters() {
    document.getElementById('search-input').value = '';
    document.getElementById('filter-category').value = '';
    document.getElementById('filter-tag').value = '';
    document.getElementById('filter-date-from').value = '';
    document.getElementById('filter-date-to').value = '';
    activeViewId = null;
    document.getElementById('view-select').value = '';
    hideViewBuilder();
    filterEntries();
}

// ========== Batch Selection ==========

function toggleSelectMode() {
    selectMode = !selectMode;
    selectedEntryIds.clear();
    document.getElementById('btn-select-mode').textContent = selectMode ? 'Cancel' : 'Select';
    document.getElementById('batch-actions').style.display = selectMode ? 'flex' : 'none';
    filterEntries();
}

function toggleEntrySelect(id) {
    if (selectedEntryIds.has(id)) {
        selectedEntryIds.delete(id);
    } else {
        selectedEntryIds.add(id);
    }
    filterEntries();
}

function selectAllVisible() {
    const cards = document.querySelectorAll('.entry-card');
    cards.forEach(card => {
        const onclick = card.getAttribute('onclick');
        const match = onclick && onclick.match(/toggleEntrySelect\('([^']+)'\)/);
        if (match) selectedEntryIds.add(match[1]);
    });
    filterEntries();
}

function deselectAll() {
    selectedEntryIds.clear();
    filterEntries();
}

function updateSelectCount() {
    const countEl = document.getElementById('select-count');
    if (countEl) {
        countEl.textContent = selectedEntryIds.size > 0 ? `${selectedEntryIds.size} selected` : '';
    }
}

async function deleteSelectedEntries() {
    const count = selectedEntryIds.size;
    if (count === 0) return;
    if (shouldConfirmDelete() && !confirm(`Delete ${count} selected ${count === 1 ? 'entry' : 'entries'}? This cannot be undone.`)) return;

    await DB.deleteEntriesByIds([...selectedEntryIds]);

    selectedEntryIds.clear();
    selectMode = false;
    document.getElementById('btn-select-mode').textContent = 'Select';
    document.getElementById('batch-actions').style.display = 'none';
    filterEntries();
}
