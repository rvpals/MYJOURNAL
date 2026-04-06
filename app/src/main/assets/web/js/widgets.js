/**
 * Widgets: configurable aggregate cards for the Dashboard.
 *
 * Each widget has filters (entry criteria) and functions (aggregate computations).
 * Widgets are stored in the DB widgets table as metadata.
 */

// ========== Widget Filter & Function Definitions ==========

const WIDGET_FIELDS = [
    { key: 'date',       label: 'Date',       type: 'date' },
    { key: 'time',       label: 'Time',       type: 'text' },
    { key: 'title',      label: 'Title',      type: 'text' },
    { key: 'content',    label: 'Content',    type: 'text' },
    { key: 'categories', label: 'Categories', type: 'array' },
    { key: 'tags',       label: 'Tags',       type: 'array' },
    { key: 'people',     label: 'People',     type: 'array' },
    { key: 'placeName',  label: 'Place Name', type: 'text' },
];

const WIDGET_FILTER_OPS = {
    date:  ['after', 'before', 'equals', 'between'],
    text:  ['contains', 'equals', 'starts with', 'ends with', 'is empty', 'is not empty'],
    array: ['includes', 'not includes', 'is empty', 'is not empty'],
};

const WIDGET_AGG_FUNCTIONS = ['Count', 'Sum', 'Max', 'Min', 'Average'];

// Fields that aggregate functions can target
const WIDGET_AGG_FIELDS = [
    { key: 'entries',    label: 'Entries (rows)',  type: 'count' },
    { key: 'tags',       label: 'Tags',            type: 'array' },
    { key: 'categories', label: 'Categories',      type: 'array' },
    { key: 'people',     label: 'People',          type: 'array' },
    { key: 'placeName',  label: 'Place Name',      type: 'text' },
    { key: 'title',      label: 'Title',           type: 'text' },
];

let _widgetEditing = null; // widget object being edited

// ========== Widget Computation Engine ==========

function widgetFilterEntries(entries, filters) {
    if (!filters || filters.length === 0) return entries;
    return entries.filter(entry => {
        return filters.every(f => {
            const field = WIDGET_FIELDS.find(fd => fd.key === f.field);
            if (!field) return true;
            const val = entry[f.field];

            if (field.type === 'date') {
                const d = val || '';
                switch (f.op) {
                    case 'after':   return d > f.value;
                    case 'before':  return d < f.value;
                    case 'equals':  return d === f.value;
                    case 'between': return d >= f.value && d <= (f.value2 || f.value);
                }
            } else if (field.type === 'array') {
                const arr = Array.isArray(val) ? val : [];
                const lower = (f.value || '').toLowerCase();
                switch (f.op) {
                    case 'includes':     return arr.some(v => v.toLowerCase() === lower);
                    case 'not includes': return !arr.some(v => v.toLowerCase() === lower);
                    case 'is empty':     return arr.length === 0;
                    case 'is not empty': return arr.length > 0;
                }
            } else { // text
                const s = (val || '').toLowerCase();
                const v = (f.value || '').toLowerCase();
                switch (f.op) {
                    case 'contains':     return s.includes(v);
                    case 'equals':       return s === v;
                    case 'starts with':  return s.startsWith(v);
                    case 'ends with':    return s.endsWith(v);
                    case 'is empty':     return s === '';
                    case 'is not empty': return s !== '';
                }
            }
            return true;
        });
    });
}

function widgetComputeFunction(fn, filteredEntries) {
    const aggField = WIDGET_AGG_FIELDS.find(f => f.key === fn.field);
    if (!aggField) return 0;

    // For "entries" field, we operate on the row count directly
    if (fn.field === 'entries') {
        switch (fn.func) {
            case 'Count': return filteredEntries.length;
            default:      return filteredEntries.length;
        }
    }

    // For array fields with a specific value filter
    if (aggField.type === 'array') {
        const matchVal = (fn.value || '').toLowerCase();
        if (fn.func === 'Count') {
            if (matchVal) {
                return filteredEntries.filter(e => {
                    const arr = Array.isArray(e[fn.field]) ? e[fn.field] : [];
                    return arr.some(v => v.toLowerCase() === matchVal);
                }).length;
            }
            // Count all unique values
            const set = new Set();
            filteredEntries.forEach(e => {
                const arr = Array.isArray(e[fn.field]) ? e[fn.field] : [];
                arr.forEach(v => set.add(v));
            });
            return set.size;
        }
    }

    // For text fields
    if (aggField.type === 'text') {
        const matchVal = (fn.value || '').toLowerCase();
        if (fn.func === 'Count') {
            if (matchVal) {
                return filteredEntries.filter(e => (e[fn.field] || '').toLowerCase() === matchVal).length;
            }
            return filteredEntries.filter(e => (e[fn.field] || '') !== '').length;
        }
    }

    // Numeric aggregates (Sum, Max, Min, Average) — extract numeric values
    let values = [];
    filteredEntries.forEach(e => {
        const raw = e[fn.field];
        if (Array.isArray(raw)) {
            raw.forEach(v => { const n = parseFloat(v); if (!isNaN(n)) values.push(n); });
        } else {
            const n = parseFloat(raw);
            if (!isNaN(n)) values.push(n);
        }
    });

    if (values.length === 0) return 0;
    switch (fn.func) {
        case 'Sum':     return values.reduce((a, b) => a + b, 0);
        case 'Max':     return Math.max(...values);
        case 'Min':     return Math.min(...values);
        case 'Average': return Math.round((values.reduce((a, b) => a + b, 0) / values.length) * 100) / 100;
        case 'Count':   return values.length;
        default:        return 0;
    }
}

function computeWidget(widget, allEntries) {
    const filtered = widgetFilterEntries(allEntries, widget.filters);
    return (widget.functions || []).map(fn => {
        const result = widgetComputeFunction(fn, filtered);
        return { ...fn, result };
    });
}

// ========== Dashboard Widget Rendering ==========

function renderDashboardWidgets(entries) {
    const container = document.getElementById('dashboard-widgets');
    if (!container) return;

    const widgets = DB.getWidgets().filter(w => w.enabledInDashboard);
    if (widgets.length === 0) {
        container.style.display = 'none';
        return;
    }

    container.style.display = '';
    let html = '';
    for (const widget of widgets) {
        const results = computeWidget(widget, entries);
        const bgStyle = widget.bgColor ? `background:${widget.bgColor};` : '';
        const textColor = widget.bgColor ? `color:${getContrastColor(widget.bgColor)};` : '';
        const iconHtml = widget.icon
            ? `<img src="${escapeHtml(widget.icon)}" class="widget-card-icon" alt="">`
            : '';

        html += `<div class="widget-card" style="${bgStyle}${textColor}" onclick="openWidgetEditor('${escapeHtml(widget.id)}')">`;
        html += `<div class="widget-card-header">`;
        html += iconHtml;
        html += `<div class="widget-card-titles">`;
        html += `<div class="widget-card-name">${escapeHtml(widget.name)}</div>`;
        if (widget.description) {
            html += `<div class="widget-card-desc">${escapeHtml(widget.description)}</div>`;
        }
        html += `</div></div>`;

        if (results.length > 0) {
            html += `<div class="widget-card-results">`;
            for (const r of results) {
                const prefix = r.prefix ? escapeHtml(r.prefix) + ' ' : '';
                const postfix = r.postfix ? ' ' + escapeHtml(r.postfix) : '';
                html += `<div class="widget-card-row">`;
                html += `<span class="widget-card-label">${prefix}</span>`;
                html += `<span class="widget-card-value">${r.result}${postfix}</span>`;
                html += `</div>`;
            }
            html += `</div>`;
        }
        html += `</div>`;
    }
    container.innerHTML = html;
}

function getContrastColor(hex) {
    if (!hex) return '#000';
    hex = hex.replace('#', '');
    if (hex.length === 3) hex = hex.split('').map(c => c + c).join('');
    const r = parseInt(hex.substr(0, 2), 16);
    const g = parseInt(hex.substr(2, 2), 16);
    const b = parseInt(hex.substr(4, 2), 16);
    const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    return luminance > 0.5 ? '#1a1a2e' : '#ffffff';
}

// ========== Widget List in Settings ==========

function refreshWidgetList() {
    const container = document.getElementById('widget-list');
    if (!container) return;

    const widgets = DB.getWidgets();
    if (widgets.length === 0) {
        container.innerHTML = '<p class="settings-description">No widgets created yet.</p>';
        return;
    }

    let html = '';
    for (const w of widgets) {
        const bgSwatch = w.bgColor ? `<span class="widget-swatch" style="background:${escapeHtml(w.bgColor)}"></span>` : '';
        const badge = w.enabledInDashboard ? '<span class="widget-badge">Dashboard</span>' : '';
        html += `<div class="widget-list-item" onclick="openWidgetEditor('${escapeHtml(w.id)}')">`;
        html += `<div class="widget-list-info">${bgSwatch}<span class="widget-list-name">${escapeHtml(w.name)}</span>${badge}</div>`;
        html += `<div class="widget-list-actions">`;
        html += `<button class="btn-small btn-danger-small" onclick="event.stopPropagation();deleteWidgetConfirm('${escapeHtml(w.id)}','${escapeHtml(w.name)}')">Delete</button>`;
        html += `</div></div>`;
    }
    container.innerHTML = html;
}

function deleteWidgetConfirm(id, name) {
    if (!confirm('Delete widget "' + name + '"?')) return;
    DB.deleteWidget(id);
    refreshWidgetList();
}

// ========== Widget Editor ==========

function openWidgetEditor(id) {
    if (id) {
        const widget = DB.getWidgetById(id);
        if (!widget) { alert('Widget not found.'); return; }
        _widgetEditing = { ...widget };
    } else {
        _widgetEditing = {
            id: 'w_' + Date.now() + '_' + Math.random().toString(36).substring(2, 8),
            name: '',
            description: '',
            bgColor: '',
            icon: '',
            filters: [],
            functions: [],
            enabledInDashboard: true,
            sortOrder: 0,
            dtCreated: new Date().toISOString()
        };
    }
    renderWidgetEditor();
    navigateTo('widget-editor');
}

function renderWidgetEditor() {
    const w = _widgetEditing;
    if (!w) return;

    document.getElementById('widget-editor-title').textContent = w.dtUpdated ? 'Edit Widget' : 'Create Widget';
    document.getElementById('widget-name').value = w.name;
    document.getElementById('widget-description').value = w.description;
    document.getElementById('widget-bg-color').value = w.bgColor || '#4a90d9';
    document.getElementById('widget-bg-color-enabled').checked = !!w.bgColor;
    document.getElementById('widget-enabled-dashboard').checked = w.enabledInDashboard;

    // Icon preview
    updateWidgetIconPreview();

    // Render filter rows
    renderWidgetFilters();

    // Render function rows
    renderWidgetFunctions();

    // Default to first tab
    switchWidgetTab('header');
}

function switchWidgetTab(tabId) {
    document.querySelectorAll('.widget-editor-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.widget-editor-tab-content').forEach(c => c.classList.remove('active'));
    const tab = document.getElementById('wtab-' + tabId);
    const content = document.getElementById('wtab-content-' + tabId);
    if (tab) tab.classList.add('active');
    if (content) content.classList.add('active');
}

// ----- Header Tab -----

function updateWidgetIconPreview() {
    const preview = document.getElementById('widget-icon-preview');
    if (!preview) return;
    if (_widgetEditing && _widgetEditing.icon) {
        preview.innerHTML = `<img src="${escapeHtml(_widgetEditing.icon)}" class="widget-icon-img"><button class="btn-small btn-danger-small" onclick="clearWidgetIcon()">Remove</button>`;
    } else {
        preview.innerHTML = '<span class="settings-description">No icon selected</span>';
    }
}

function browseWidgetIcon() {
    document.getElementById('widget-icon-file-input').click();
}

function onWidgetIconFileSelected(event) {
    const file = event.target.files[0];
    if (!file) return;
    event.target.value = '';

    const reader = new FileReader();
    reader.onload = function(e) {
        const img = new Image();
        img.onload = function() {
            const canvas = document.createElement('canvas');
            const size = 64;
            canvas.width = size;
            canvas.height = size;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(img, 0, 0, size, size);
            _widgetEditing.icon = canvas.toDataURL('image/png');
            updateWidgetIconPreview();
        };
        img.src = e.target.result;
    };
    reader.readAsDataURL(file);
}

function clearWidgetIcon() {
    if (_widgetEditing) _widgetEditing.icon = '';
    updateWidgetIconPreview();
}

// ----- Filter Tab -----

function renderWidgetFilters() {
    const container = document.getElementById('widget-filters-list');
    if (!container) return;
    const filters = _widgetEditing.filters;

    if (filters.length === 0) {
        container.innerHTML = '<p class="settings-description">No filters — widget will use all entries.</p>';
        return;
    }

    let html = '';
    filters.forEach((f, i) => {
        const field = WIDGET_FIELDS.find(fd => fd.key === f.field) || WIDGET_FIELDS[0];
        const ops = WIDGET_FILTER_OPS[field.type] || WIDGET_FILTER_OPS.text;
        const needsValue = !['is empty', 'is not empty'].includes(f.op);
        const isBetween = f.op === 'between';
        const inputType = field.type === 'date' ? 'date' : 'text';

        html += `<div class="widget-filter-row">`;
        html += `<select onchange="updateWidgetFilter(${i},'field',this.value)">`;
        WIDGET_FIELDS.forEach(fd => {
            html += `<option value="${fd.key}"${fd.key === f.field ? ' selected' : ''}>${fd.label}</option>`;
        });
        html += `</select>`;
        html += `<select onchange="updateWidgetFilter(${i},'op',this.value)">`;
        ops.forEach(op => {
            html += `<option value="${op}"${op === f.op ? ' selected' : ''}>${op}</option>`;
        });
        html += `</select>`;
        if (needsValue) {
            html += `<input type="${inputType}" value="${escapeHtml(f.value || '')}" onchange="updateWidgetFilter(${i},'value',this.value)" placeholder="Value">`;
        }
        if (isBetween) {
            html += `<input type="${inputType}" value="${escapeHtml(f.value2 || '')}" onchange="updateWidgetFilter(${i},'value2',this.value)" placeholder="End value">`;
        }
        html += `<button class="btn-small btn-danger-small" onclick="removeWidgetFilter(${i})">&#x2716;</button>`;
        html += `</div>`;
    });
    container.innerHTML = html;
}

function addWidgetFilter() {
    _widgetEditing.filters.push({ field: 'date', op: 'after', value: '', value2: '' });
    renderWidgetFilters();
}

function updateWidgetFilter(idx, prop, value) {
    const f = _widgetEditing.filters[idx];
    if (!f) return;
    f[prop] = value;
    // Reset op when field type changes
    if (prop === 'field') {
        const field = WIDGET_FIELDS.find(fd => fd.key === value);
        const ops = WIDGET_FILTER_OPS[field ? field.type : 'text'];
        if (!ops.includes(f.op)) f.op = ops[0];
        f.value = '';
        f.value2 = '';
    }
    renderWidgetFilters();
}

function removeWidgetFilter(idx) {
    _widgetEditing.filters.splice(idx, 1);
    renderWidgetFilters();
}

// ----- Functions Tab -----

function renderWidgetFunctions() {
    const container = document.getElementById('widget-functions-list');
    if (!container) return;
    const fns = _widgetEditing.functions;

    if (fns.length === 0) {
        container.innerHTML = '<p class="settings-description">No functions added yet.</p>';
        return;
    }

    let html = '';
    fns.forEach((fn, i) => {
        const aggField = WIDGET_AGG_FIELDS.find(f => f.key === fn.field);
        const showValue = aggField && (aggField.type === 'array' || aggField.type === 'text');

        html += `<div class="widget-func-row">`;
        html += `<div class="widget-func-main">`;
        // Function select
        html += `<select onchange="updateWidgetFunc(${i},'func',this.value)">`;
        WIDGET_AGG_FUNCTIONS.forEach(af => {
            html += `<option value="${af}"${af === fn.func ? ' selected' : ''}>${af}</option>`;
        });
        html += `</select>`;
        // Field select
        html += `<select onchange="updateWidgetFunc(${i},'field',this.value)">`;
        WIDGET_AGG_FIELDS.forEach(af => {
            html += `<option value="${af.key}"${af.key === fn.field ? ' selected' : ''}>${af.label}</option>`;
        });
        html += `</select>`;
        // Value input (for array/text fields)
        if (showValue) {
            html += `<input type="text" value="${escapeHtml(fn.value || '')}" onchange="updateWidgetFunc(${i},'value',this.value)" placeholder="Match value (optional)">`;
        }
        html += `</div>`;
        // Prefix / Postfix
        html += `<div class="widget-func-affixes">`;
        html += `<input type="text" value="${escapeHtml(fn.prefix || '')}" onchange="updateWidgetFunc(${i},'prefix',this.value)" placeholder="Prefix label">`;
        html += `<input type="text" value="${escapeHtml(fn.postfix || '')}" onchange="updateWidgetFunc(${i},'postfix',this.value)" placeholder="Postfix">`;
        html += `<button class="btn-small btn-danger-small" onclick="removeWidgetFunc(${i})">&#x2716;</button>`;
        html += `</div>`;
        html += `</div>`;
    });
    container.innerHTML = html;
}

function addWidgetFunc() {
    _widgetEditing.functions.push({ func: 'Count', field: 'entries', value: '', prefix: '', postfix: '' });
    renderWidgetFunctions();
}

function updateWidgetFunc(idx, prop, value) {
    const fn = _widgetEditing.functions[idx];
    if (!fn) return;
    fn[prop] = value;
    if (prop === 'field') {
        fn.value = '';
    }
    renderWidgetFunctions();
}

function removeWidgetFunc(idx) {
    _widgetEditing.functions.splice(idx, 1);
    renderWidgetFunctions();
}

// ----- Save / Cancel -----

function saveWidgetEditor() {
    const w = _widgetEditing;
    if (!w) return;

    w.name = document.getElementById('widget-name').value.trim();
    if (!w.name) {
        alert('Please enter a widget name.');
        switchWidgetTab('header');
        document.getElementById('widget-name').focus();
        return;
    }
    w.description = document.getElementById('widget-description').value.trim();
    const colorEnabled = document.getElementById('widget-bg-color-enabled').checked;
    w.bgColor = colorEnabled ? document.getElementById('widget-bg-color').value : '';
    w.enabledInDashboard = document.getElementById('widget-enabled-dashboard').checked;

    DB.saveWidget(w);
    _widgetEditing = null;
    navigateTo('settings');
    switchSettingsTab('widgets');
    refreshWidgetList();
}

function cancelWidgetEditor() {
    _widgetEditing = null;
    navigateTo('settings');
    switchSettingsTab('widgets');
}

// ========== Widget Preview ==========

function previewWidget() {
    const w = _widgetEditing;
    if (!w) return;

    // Sync current form values
    w.name = document.getElementById('widget-name').value.trim();
    w.description = document.getElementById('widget-description').value.trim();
    const colorEnabled = document.getElementById('widget-bg-color-enabled').checked;
    w.bgColor = colorEnabled ? document.getElementById('widget-bg-color').value : '';

    const entries = DB.getEntries();
    const results = computeWidget(w, entries);

    const previewEl = document.getElementById('widget-preview-output');
    if (!previewEl) return;

    const bgStyle = w.bgColor ? `background:${w.bgColor};` : 'background:var(--bg-card);';
    const textColor = w.bgColor ? `color:${getContrastColor(w.bgColor)};` : '';
    const iconHtml = w.icon ? `<img src="${escapeHtml(w.icon)}" class="widget-card-icon" alt="">` : '';

    let html = `<div class="widget-card widget-card-preview" style="${bgStyle}${textColor}">`;
    html += `<div class="widget-card-header">${iconHtml}<div class="widget-card-titles">`;
    html += `<div class="widget-card-name">${escapeHtml(w.name || 'Untitled')}</div>`;
    if (w.description) html += `<div class="widget-card-desc">${escapeHtml(w.description)}</div>`;
    html += `</div></div>`;

    if (results.length > 0) {
        html += `<div class="widget-card-results">`;
        for (const r of results) {
            const prefix = r.prefix ? escapeHtml(r.prefix) + ' ' : '';
            const postfix = r.postfix ? ' ' + escapeHtml(r.postfix) : '';
            html += `<div class="widget-card-row"><span class="widget-card-label">${prefix}</span><span class="widget-card-value">${r.result}${postfix}</span></div>`;
        }
        html += `</div>`;
    } else {
        html += `<div class="widget-card-results"><div class="widget-card-row"><span class="widget-card-label">No functions defined</span></div></div>`;
    }
    html += `</div>`;

    previewEl.innerHTML = html;
    previewEl.style.display = '';
}
