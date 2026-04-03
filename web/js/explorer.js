/**
 * SQL Explorer: query journal entries with field conditions.
 */

const EXPLORER_FIELDS = [
    { key: 'date',       label: 'Date',        type: 'date' },
    { key: 'time',       label: 'Time',        type: 'text' },
    { key: 'title',      label: 'Title',       type: 'text' },
    { key: 'content',    label: 'Content',     type: 'text' },
    { key: 'categories', label: 'Categories',  type: 'array' },
    { key: 'tags',       label: 'Tags',        type: 'array' },
    { key: 'placeName',  label: 'Place Name',  type: 'text' },
    { key: 'locations',  label: 'Locations',   type: 'locations' },
    { key: 'weather',    label: 'Weather',     type: 'weather' }
];

const EXPLORER_OPS = {
    text:      ['contains', 'equals', 'starts with', 'ends with', 'is empty', 'is not empty'],
    date:      ['equals', 'before', 'after', 'between'],
    array:     ['includes', 'not includes', 'is empty', 'is not empty'],
    locations: ['contains', 'is empty', 'is not empty'],
    weather:   ['has data', 'no data']
};

let explorerConditions = [];
let explorerSelectedCols = ['date', 'title', 'categories', 'tags'];
let explorerActiveTable = null;
let explorerLastResults = [];
let explorerLastRawCols = null; // set when raw SQL results are displayed

function initExplorer() {
    renderExplorerTableChips();
    renderExplorerFieldChips();
    populateExplorerViewSelect();
    if (explorerConditions.length === 0) {
        addExplorerCondition();
    }
    renderExplorerConditions();
}

function populateExplorerViewSelect() {
    const sel = document.getElementById('explorer-view-select');
    if (!sel) return;
    const views = typeof getCustomViews === 'function' ? getCustomViews() : [];
    sel.innerHTML = '<option value="">Load Custom View...</option>' +
        views.map(v => `<option value="${v.id}">${escapeHtml(v.name)}</option>`).join('');
}

function convertViewCondToExplorer(cond) {
    const opMap = {
        'contains': 'contains',
        'not_contains': 'contains', // handled via negate
        'equals': 'equals',
        'not_equals': 'equals',     // handled via negate
        'starts_with': 'starts with',
        'ends_with': 'ends with',
        'is_empty': 'is empty',
        'is_not_empty': 'is not empty',
        'before': 'before',
        'after': 'after',
        'includes': 'includes',
        'not_includes': 'not includes',
        'exists': 'has data',
        'not_exists': 'no data',
        'within_days': 'after',
        'within_weeks': 'after',
        'within_months': 'after',
        'within_years': 'after'
    };

    let op = opMap[cond.operator] || 'contains';
    let value = cond.value || '';

    // Handle "within" operators by computing the date
    if (cond.operator && cond.operator.startsWith('within_')) {
        const n = parseInt(value) || 1;
        const now = new Date();
        if (cond.operator === 'within_days') now.setDate(now.getDate() - n);
        if (cond.operator === 'within_weeks') now.setDate(now.getDate() - (n * 7));
        if (cond.operator === 'within_months') now.setMonth(now.getMonth() - n);
        if (cond.operator === 'within_years') now.setFullYear(now.getFullYear() - n);
        value = toLocalDateStr(now);
        op = 'after';
    }

    return { field: cond.field, op: op, value: value, value2: '' };
}

function loadViewIntoExplorer() {
    const sel = document.getElementById('explorer-view-select');
    const viewId = sel.value;
    if (!viewId) return;

    const views = typeof getCustomViews === 'function' ? getCustomViews() : [];
    const view = views.find(v => v.id === viewId);
    if (!view || !view.conditions || view.conditions.length === 0) {
        sel.value = '';
        return;
    }

    // Convert view conditions to explorer conditions
    explorerConditions = view.conditions.map(c => convertViewCondToExplorer(c));
    renderExplorerConditions();

    // Clear SQL text box
    const sqlInput = document.getElementById('explorer-sql-input');
    if (sqlInput) sqlInput.value = '';

    // Reset dropdown
    sel.value = '';

    // Auto-run
    runExplorerQuery();
}

function renderExplorerTableChips() {
    const container = document.getElementById('explorer-table-chips');
    if (!container) return;
    let tables = ['entries', 'images', 'categories', 'icons', 'people', 'settings', 'schema_version'];
    try {
        const result = DB.execRawSQL("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name");
        if (result.length && result[0].values.length) {
            tables = result[0].values.map(r => r[0]);
        }
    } catch(e) { /* fallback to static list */ }
    container.innerHTML = tables.map(t =>
        `<span class="tag-item tag-clickable explorer-chip${explorerActiveTable === t ? ' explorer-chip-active' : ''}" onclick="toggleExplorerTable('${t}')">${t}</span>`
    ).join('');
}

function toggleExplorerTable(name) {
    const detail = document.getElementById('explorer-table-detail');
    if (explorerActiveTable === name) {
        explorerActiveTable = null;
        detail.style.display = 'none';
        renderExplorerTableChips();
        return;
    }
    explorerActiveTable = name;
    renderExplorerTableChips();

    // Get column info
    const colResults = DB.execRawSQL(`PRAGMA table_info(${name})`);
    // Get row count
    const countResults = DB.execRawSQL(`SELECT COUNT(*) as cnt FROM ${name}`);
    const rowCount = countResults.length && countResults[0].values.length ? countResults[0].values[0][0] : 0;
    // Get sample rows (up to 5)
    const sampleResults = DB.execRawSQL(`SELECT * FROM ${name} LIMIT 5`);

    let html = `<div class="explorer-table-info"><strong>${escapeHtml(name)}</strong> &mdash; ${rowCount} row${rowCount !== 1 ? 's' : ''}</div>`;

    // Column info table
    if (colResults.length && colResults[0].values.length) {
        const cols = colResults[0].values; // [cid, name, type, notnull, dflt, pk]
        html += '<table class="explorer-table explorer-table-compact"><thead><tr><th>Column</th><th>Type</th><th>PK</th><th>Not Null</th><th>Default</th></tr></thead><tbody>';
        cols.forEach(c => {
            html += `<tr><td><strong>${escapeHtml(c[1])}</strong></td><td>${escapeHtml(c[2] || '')}</td><td>${c[5] ? 'Yes' : ''}</td><td>${c[3] ? 'Yes' : ''}</td><td>${c[4] !== null ? escapeHtml(String(c[4])) : ''}</td></tr>`;
        });
        html += '</tbody></table>';
    }

    // Sample data
    if (sampleResults.length && sampleResults[0].values.length) {
        const sampleCols = sampleResults[0].columns;
        const sampleRows = sampleResults[0].values;
        html += `<div class="explorer-table-info" style="margin-top:0.75rem">Sample data (${sampleRows.length} row${sampleRows.length !== 1 ? 's' : ''})</div>`;
        html += '<div class="explorer-results-scroll"><table class="explorer-table explorer-table-compact"><thead><tr>';
        sampleCols.forEach(col => { html += `<th>${escapeHtml(col)}</th>`; });
        html += '</tr></thead><tbody>';
        sampleRows.forEach(row => {
            html += '<tr>';
            row.forEach(cell => {
                let val = cell === null ? '<em>NULL</em>' : escapeHtml(truncateCell(String(cell)));
                html += `<td>${val}</td>`;
            });
            html += '</tr>';
        });
        html += '</tbody></table></div>';
    } else if (rowCount === 0) {
        html += '<div class="no-data" style="margin-top:0.5rem">Table is empty</div>';
    }

    detail.innerHTML = html;
    detail.style.display = 'block';
}

function truncateCell(str) {
    if (str.length > 100) return str.substring(0, 100) + '...';
    return str;
}

function renderExplorerFieldChips() {
    const container = document.getElementById('explorer-field-chips');
    container.innerHTML = EXPLORER_FIELDS.map(f =>
        `<span class="tag-item tag-clickable explorer-chip${explorerSelectedCols.includes(f.key) ? ' explorer-chip-active' : ''}" onclick="toggleExplorerCol('${f.key}')">${f.label}</span>`
    ).join('');
}

function toggleExplorerCol(key) {
    const idx = explorerSelectedCols.indexOf(key);
    if (idx >= 0) {
        if (explorerSelectedCols.length <= 1) return;
        explorerSelectedCols.splice(idx, 1);
    } else {
        explorerSelectedCols.push(key);
    }
    renderExplorerFieldChips();
    // Re-render results if visible
    const resultsEl = document.getElementById('explorer-results');
    if (resultsEl.style.display !== 'none') {
        runExplorerQuery();
    }
}

function addExplorerCondition() {
    explorerConditions.push({ field: 'title', op: 'contains', value: '', value2: '' });
    renderExplorerConditions();
}

function removeExplorerCondition(idx) {
    explorerConditions.splice(idx, 1);
    if (explorerConditions.length === 0) addExplorerCondition();
    else renderExplorerConditions();
}

function renderExplorerConditions() {
    const container = document.getElementById('explorer-conditions');
    container.innerHTML = explorerConditions.map((cond, i) => {
        const fieldDef = EXPLORER_FIELDS.find(f => f.key === cond.field) || EXPLORER_FIELDS[0];
        const ops = EXPLORER_OPS[fieldDef.type] || EXPLORER_OPS.text;

        const fieldSelect = `<select class="explorer-cond-field" onchange="updateExplorerCondField(${i}, this.value)">` +
            EXPLORER_FIELDS.map(f => `<option value="${f.key}"${f.key === cond.field ? ' selected' : ''}>${f.label}</option>`).join('') +
            `</select>`;

        const opSelect = `<select class="explorer-cond-op" onchange="updateExplorerCondOp(${i}, this.value)">` +
            ops.map(o => `<option value="${o}"${o === cond.op ? ' selected' : ''}>${o}</option>`).join('') +
            `</select>`;

        const noValue = ['is empty', 'is not empty', 'has data', 'no data'].includes(cond.op);
        const isBetween = cond.op === 'between';
        const isDate = fieldDef.type === 'date';
        const inputType = isDate ? 'date' : 'text';

        let valueHtml = '';
        if (!noValue) {
            valueHtml = `<input type="${inputType}" class="explorer-cond-value" value="${escapeHtml(cond.value)}" placeholder="Value..." onchange="updateExplorerCondValue(${i}, this.value)">`;
            if (isBetween) {
                valueHtml += `<span class="explorer-and">and</span><input type="${inputType}" class="explorer-cond-value" value="${escapeHtml(cond.value2)}" placeholder="Value..." onchange="updateExplorerCondValue2(${i}, this.value)">`;
            }
        }

        return `<div class="explorer-cond-row">
            ${fieldSelect}${opSelect}${valueHtml}
            <button class="btn-small btn-danger-small" onclick="removeExplorerCondition(${i})">&times;</button>
        </div>`;
    }).join('') + `<button class="btn-small" onclick="addExplorerCondition()" style="margin-top:0.35rem">+ Add Condition</button>`;
}

function updateExplorerCondField(idx, value) {
    explorerConditions[idx].field = value;
    const fieldDef = EXPLORER_FIELDS.find(f => f.key === value);
    const ops = EXPLORER_OPS[fieldDef.type] || EXPLORER_OPS.text;
    explorerConditions[idx].op = ops[0];
    explorerConditions[idx].value = '';
    explorerConditions[idx].value2 = '';
    renderExplorerConditions();
}

function updateExplorerCondOp(idx, value) {
    explorerConditions[idx].op = value;
    renderExplorerConditions();
}

function updateExplorerCondValue(idx, value) {
    explorerConditions[idx].value = value;
}

function updateExplorerCondValue2(idx, value) {
    explorerConditions[idx].value2 = value;
}

function matchExplorerCondition(entry, cond) {
    const fieldDef = EXPLORER_FIELDS.find(f => f.key === cond.field);
    if (!fieldDef) return true;
    const val = cond.value.toLowerCase();

    switch (fieldDef.type) {
        case 'text': {
            const entryVal = (entry[cond.field] || '').toLowerCase();
            if (cond.op === 'contains') return entryVal.includes(val);
            if (cond.op === 'equals') return entryVal === val;
            if (cond.op === 'starts with') return entryVal.startsWith(val);
            if (cond.op === 'ends with') return entryVal.endsWith(val);
            if (cond.op === 'is empty') return !entryVal;
            if (cond.op === 'is not empty') return !!entryVal;
            return true;
        }
        case 'date': {
            const d = entry.date || '';
            if (cond.op === 'equals') return d === cond.value;
            if (cond.op === 'before') return d < cond.value;
            if (cond.op === 'after') return d > cond.value;
            if (cond.op === 'between') return d >= cond.value && d <= cond.value2;
            return true;
        }
        case 'array': {
            const arr = entry[cond.field] || [];
            if (cond.op === 'includes') return arr.some(v => v.toLowerCase().includes(val));
            if (cond.op === 'not includes') return !arr.some(v => v.toLowerCase().includes(val));
            if (cond.op === 'is empty') return arr.length === 0;
            if (cond.op === 'is not empty') return arr.length > 0;
            return true;
        }
        case 'locations': {
            const locs = entry.locations || [];
            if (cond.op === 'contains') return locs.some(l => (l.address || '').toLowerCase().includes(val));
            if (cond.op === 'is empty') return locs.length === 0;
            if (cond.op === 'is not empty') return locs.length > 0;
            return true;
        }
        case 'weather': {
            if (cond.op === 'has data') return !!entry.weather;
            if (cond.op === 'no data') return !entry.weather;
            return true;
        }
    }
    return true;
}

/** Parse a SQL-like query string into conditions array.
 *  Format: SELECT * FROM entries WHERE field OP 'value' AND field OP 'value'
 *  Also accepts: SELECT col1, col2 FROM entries WHERE ...
 */
function parseExplorerSQL(sql) {
    const result = { conditions: [], columns: null, error: null };
    let s = sql.trim();
    if (!s) { result.error = 'Empty query'; return result; }

    // Normalize whitespace
    s = s.replace(/\s+/g, ' ');

    // Match SELECT ... FROM entries [WHERE ...]
    const selectMatch = s.match(/^SELECT\s+(.+?)\s+FROM\s+entries(?:\s+WHERE\s+(.+))?$/i);
    if (!selectMatch) {
        // Try without SELECT — just treat entire input as WHERE conditions
        const whereOnly = s.replace(/^WHERE\s+/i, '');
        result.conditions = parseExplorerWhereClauses(whereOnly);
        if (result.conditions === null) result.error = 'Could not parse query. Use: field OPERATOR \'value\'';
        return result;
    }

    // Parse column list
    const colPart = selectMatch[1].trim();
    if (colPart !== '*') {
        const cols = colPart.split(',').map(c => c.trim().toLowerCase());
        const fieldKeys = EXPLORER_FIELDS.map(f => f.key.toLowerCase());
        const mapped = cols.map(c => {
            // Match by key or label
            const byKey = EXPLORER_FIELDS.find(f => f.key.toLowerCase() === c);
            if (byKey) return byKey.key;
            const byLabel = EXPLORER_FIELDS.find(f => f.label.toLowerCase() === c);
            if (byLabel) return byLabel.key;
            // Fuzzy: placename -> placeName
            const byLoose = EXPLORER_FIELDS.find(f => f.key.toLowerCase() === c.replace(/[\s_]/g, ''));
            if (byLoose) return byLoose.key;
            return null;
        }).filter(Boolean);
        if (mapped.length > 0) result.columns = mapped;
    }

    // Parse WHERE clause
    if (selectMatch[2]) {
        result.conditions = parseExplorerWhereClauses(selectMatch[2].trim());
        if (result.conditions === null) result.error = 'Could not parse WHERE clause.';
    }

    return result;
}

function parseExplorerWhereClauses(whereStr) {
    // Split by AND (case insensitive, not inside quotes)
    const parts = splitByAnd(whereStr);
    const conditions = [];

    for (const part of parts) {
        const cond = parseExplorerSingleCondition(part.trim());
        if (!cond) return null;
        conditions.push(cond);
    }
    return conditions;
}

function splitByAnd(str) {
    // Split on AND that is not inside quotes
    const parts = [];
    let current = '';
    let inQuote = false;
    let quoteChar = '';
    const upper = str.toUpperCase();

    for (let i = 0; i < str.length; i++) {
        const ch = str[i];
        if (inQuote) {
            current += ch;
            if (ch === quoteChar) inQuote = false;
            continue;
        }
        if (ch === "'" || ch === '"') {
            inQuote = true;
            quoteChar = ch;
            current += ch;
            continue;
        }
        // Check for ' AND '
        if (upper.substring(i, i + 5) === ' AND ' && !inQuote) {
            parts.push(current);
            current = '';
            i += 4; // skip ' AND '
            continue;
        }
        current += ch;
    }
    if (current.trim()) parts.push(current);
    return parts;
}

function parseExplorerSingleCondition(str) {
    str = str.trim();

    // All supported operators, longest first for matching
    const ops = [
        'NOT INCLUDES', 'IS NOT EMPTY', 'STARTS WITH', 'ENDS WITH',
        'IS EMPTY', 'HAS DATA', 'NO DATA',
        'CONTAINS', 'INCLUDES', 'EQUALS', 'BEFORE', 'AFTER', 'BETWEEN'
    ];

    const upper = str.toUpperCase();

    for (const op of ops) {
        // Find field OP value pattern
        const opIdx = upper.indexOf(' ' + op + ' ');
        const opIdxEnd = upper.indexOf(' ' + op);

        // For no-value ops (IS EMPTY, IS NOT EMPTY, HAS DATA, NO DATA)
        const noValueOps = ['IS EMPTY', 'IS NOT EMPTY', 'HAS DATA', 'NO DATA'];
        if (noValueOps.includes(op)) {
            // field IS EMPTY (at end of string)
            if (upper.endsWith(' ' + op)) {
                const fieldStr = str.substring(0, upper.lastIndexOf(' ' + op)).trim();
                const field = resolveExplorerField(fieldStr);
                if (field) return { field, op: op.toLowerCase(), value: '', value2: '' };
            }
            continue;
        }

        if (opIdx < 0) continue;

        const fieldStr = str.substring(0, opIdx).trim();
        const field = resolveExplorerField(fieldStr);
        if (!field) continue;

        let valueStr = str.substring(opIdx + op.length + 2).trim();

        // Handle BETWEEN ... AND ...
        if (op === 'BETWEEN') {
            const andIdx = valueStr.toUpperCase().indexOf(' AND ');
            if (andIdx < 0) continue;
            const v1 = stripQuotes(valueStr.substring(0, andIdx).trim());
            const v2 = stripQuotes(valueStr.substring(andIdx + 5).trim());
            return { field, op: 'between', value: v1, value2: v2 };
        }

        const value = stripQuotes(valueStr);
        return { field, op: op.toLowerCase(), value, value2: '' };
    }

    return null;
}

function resolveExplorerField(str) {
    const s = str.trim().toLowerCase().replace(/[`"'\[\]]/g, '');
    const byKey = EXPLORER_FIELDS.find(f => f.key.toLowerCase() === s);
    if (byKey) return byKey.key;
    const byLabel = EXPLORER_FIELDS.find(f => f.label.toLowerCase() === s);
    if (byLabel) return byLabel.key;
    // placename / place_name -> placeName
    const normalized = s.replace(/[\s_-]/g, '');
    const byNorm = EXPLORER_FIELDS.find(f => f.key.toLowerCase() === normalized);
    if (byNorm) return byNorm.key;
    return null;
}

function stripQuotes(s) {
    s = s.trim();
    if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith('"') && s.endsWith('"'))) {
        return s.slice(1, -1);
    }
    return s;
}

function runExplorerQuery() {
    const sqlInput = document.getElementById('explorer-sql-input');
    const sqlText = sqlInput ? sqlInput.value.trim() : '';

    let conditions = explorerConditions;
    let columns = null;

    // If SQL text box has content, try to parse or run as raw SQL
    if (sqlText) {
        // Check if this targets a table other than entries — run as raw SQL
        const fromMatch = sqlText.match(/\bFROM\s+(\w+)/i);
        if (fromMatch && fromMatch[1].toLowerCase() !== 'entries') {
            runRawSQL(sqlText);
            return;
        }
        // Also run as raw SQL for non-SELECT statements or if it looks like raw SQL
        if (!sqlText.match(/^SELECT\b/i) && !sqlText.match(/^\w+\s+(contains|equals|starts|ends|is\s|includes|not\s|before|after|between|has\s|no\s)/i)) {
            runRawSQL(sqlText);
            return;
        }

        const parsed = parseExplorerSQL(sqlText);
        if (parsed.error && (!parsed.conditions || parsed.conditions.length === 0)) {
            document.getElementById('explorer-result-count').textContent = 'Error: ' + parsed.error;
            return;
        }
        if (parsed.conditions && parsed.conditions.length > 0) {
            conditions = parsed.conditions;
        }
        if (parsed.columns) {
            columns = parsed.columns;
            explorerSelectedCols = columns;
            renderExplorerFieldChips();
        }
    }

    let entries = DB.getEntries();

    // Apply all conditions (AND)
    entries = entries.filter(e => conditions.every(c => matchExplorerCondition(e, c)));

    // Sort by date descending
    entries.sort((a, b) => {
        if (b.date !== a.date) return b.date.localeCompare(a.date);
        return (b.time || '').localeCompare(a.time || '');
    });

    document.getElementById('explorer-result-count').textContent = entries.length + ' result' + (entries.length !== 1 ? 's' : '');

    explorerLastResults = entries;
    explorerLastRawCols = null;
    renderExplorerResults(entries);
}

function getExplorerCellValue(entry, key) {
    switch (key) {
        case 'date': return entry.date || '';
        case 'time': return entry.time || '';
        case 'title': return entry.title || '';
        case 'content': return (entry.content || '').substring(0, 80);
        case 'categories': return (entry.categories || []).join(', ');
        case 'tags': return (entry.tags || []).join(', ');
        case 'placeName': return entry.placeName || '';
        case 'locations':
            return (entry.locations || []).map(l => l.address || '').join('; ');
        case 'weather':
            return entry.weather ? Weather.formatWeather(entry.weather) : '';
        default: return '';
    }
}

function renderExplorerResults(entries) {
    const resultsEl = document.getElementById('explorer-results');
    resultsEl.style.display = 'block';

    const cols = explorerSelectedCols.filter(k => EXPLORER_FIELDS.find(f => f.key === k));

    ResultGrid.render({
        data: entries,
        containerId: 'explorer-results-grid',
        maxHeight: 0,
        emptyMessage: 'No matching entries.',
        columns: cols.map(k => {
            const f = EXPLORER_FIELDS.find(fd => fd.key === k);
            return { key: k, label: f.label, formatter: (v, row) => escapeHtml(getExplorerCellValue(row, k)) };
        }),
        onRowClick: (row, idx) => _showExplorerRecord(idx)
    });
}

function _showExplorerRecord(idx) {
    let fields;
    if (explorerLastRawCols) {
        fields = explorerLastRawCols.map(col => ({
            key: col, label: col,
            formatter: (v) => escapeHtml(v === null ? 'NULL' : String(v ?? ''))
        }));
    } else {
        fields = EXPLORER_FIELDS.map(f => ({
            key: f.key, label: f.label,
            formatter: (v, row) => escapeHtml(getExplorerCellValueFull(row, f.key))
        }));
    }
    RecordViewer.show({
        data: explorerLastResults,
        index: idx,
        fields: fields
    });
}

async function deleteExplorerEntry(id) {
    const entry = DB.getEntries().find(e => e.id === id);
    const name = entry ? (entry.title || 'Untitled') : 'this entry';
    if (shouldConfirmDelete() && !confirm(`Delete "${name}"?`)) return;
    await DB.deleteEntryById(id);
    runExplorerQuery();
}


function getExplorerCellValueFull(entry, key) {
    switch (key) {
        case 'date': return entry.date || '';
        case 'time': return entry.time || '';
        case 'title': return entry.title || '';
        case 'content': return (entry.content || '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
        case 'categories': return (entry.categories || []).join(', ');
        case 'tags': return (entry.tags || []).join(', ');
        case 'placeName': return entry.placeName || '';
        case 'locations':
            return (entry.locations || []).map(l => {
                const parts = [];
                if (l.address) parts.push(l.address);
                if (l.lat != null && l.lng != null) parts.push(`(${l.lat}, ${l.lng})`);
                return parts.join(' ');
            }).join('; ');
        case 'weather':
            return entry.weather ? Weather.formatWeather(entry.weather) : '';
        default: return '';
    }
}

function runRawSQL(sql) {
    const resultsEl = document.getElementById('explorer-results');
    const countEl = document.getElementById('explorer-result-count');

    try {
        const results = DB.execRawSQL(sql);
        if (!results || results.length === 0 || !results[0].values || results[0].values.length === 0) {
            countEl.textContent = '0 results';
            resultsEl.style.display = 'block';
            ResultGrid.render({ data: [], containerId: 'explorer-results-grid', emptyMessage: 'No results.' });
            explorerLastResults = [];
            return;
        }

        const cols = results[0].columns;
        const rows = results[0].values;
        countEl.textContent = rows.length + ' result' + (rows.length !== 1 ? 's' : '');

        // Convert to objects keyed by column name
        explorerLastResults = rows.map(row => {
            const obj = {};
            cols.forEach((c, idx) => obj[c] = row[idx]);
            return obj;
        });
        explorerSelectedCols = cols.slice();
        explorerLastRawCols = cols.slice();

        ResultGrid.render({
            data: explorerLastResults,
            containerId: 'explorer-results-grid',
            maxHeight: 0,
            emptyMessage: 'No results.',
            columns: cols.map(c => ({
                key: c, label: c,
                formatter: (v) => v === null ? '<em>NULL</em>' : escapeHtml(truncateCell(String(v)))
            })),
            onRowClick: (row, idx) => _showExplorerRecord(idx)
        });

        resultsEl.style.display = 'block';
    } catch (e) {
        countEl.textContent = 'Error: ' + e.message;
        resultsEl.style.display = 'none';
        explorerLastResults = [];
    }
}

function exportExplorerCSV() {
    if (!explorerLastResults || explorerLastResults.length === 0) {
        alert('No results to export. Run a query first.');
        return;
    }
    const csvEscape = (val) => '"' + String(val === null || val === undefined ? '' : val).replace(/"/g, '""') + '"';

    // Determine if results are from raw SQL (plain objects) or entry-based query
    const isRawResult = explorerLastResults.length > 0 && !explorerLastResults[0].id;
    let cols, headers;

    if (isRawResult) {
        cols = explorerSelectedCols;
        headers = cols;
    } else {
        cols = explorerSelectedCols.filter(k => EXPLORER_FIELDS.find(f => f.key === k));
        headers = cols.map(k => EXPLORER_FIELDS.find(f => f.key === k).label);
    }

    const lines = [headers.map(h => csvEscape(h)).join(',')];
    explorerLastResults.forEach(entry => {
        const row = cols.map(k => isRawResult
            ? csvEscape(entry[k])
            : csvEscape(getExplorerCellValue(entry, k)));
        lines.push(row.join(','));
    });

    const csv = lines.join('\n');
    downloadFile(csv, 'explorer_results_' + (new Date().toISOString().slice(0, 10)) + '.csv', 'text/csv');
}

function clearExplorerQuery() {
    explorerConditions = [];
    addExplorerCondition();
    const sqlInput = document.getElementById('explorer-sql-input');
    if (sqlInput) sqlInput.value = '';
    document.getElementById('explorer-results').style.display = 'none';
    document.getElementById('explorer-result-count').textContent = '';
    const gridEl = document.getElementById('explorer-results-grid');
    if (gridEl) gridEl.innerHTML = '';
    explorerLastResults = [];
    explorerLastRawCols = null;
}
