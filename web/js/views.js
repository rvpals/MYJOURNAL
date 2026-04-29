/**
 * Custom Views - saved filter combinations for the entry list.
 * Views are stored in settings.customViews as an array.
 */

let activeViewId = null;

// ========== View Data Model ==========

function getCustomViews() {
    const settings = DB.getSettings();
    return settings.customViews || [];
}

function saveCustomViews(views) {
    DB.setSettings({ customViews: views });
}

function createView(name, conditions, logic, pinToDashboard, groupBy, orderBy, displayMode, defaultEntryView) {
    const views = getCustomViews();
    const view = {
        id: generateId(),
        name: name,
        conditions: conditions,
        logic: logic || 'AND',
        pinToDashboard: !!pinToDashboard,
        groupBy: groupBy || '',
        orderBy: orderBy || [],
        displayMode: displayMode || '',
        defaultEntryView: !!defaultEntryView
    };
    views.push(view);
    saveCustomViews(views);
    return view;
}

function updateView(id, updates) {
    const views = getCustomViews();
    const idx = views.findIndex(v => v.id === id);
    if (idx >= 0) {
        views[idx] = { ...views[idx], ...updates };
        saveCustomViews(views);
    }
}

function deleteView(id) {
    const views = getCustomViews().filter(v => v.id !== id);
    saveCustomViews(views);
    if (activeViewId === id) {
        activeViewId = null;
    }
}

// ========== Field Definitions ==========

const VIEW_FIELDS = [
    { key: 'date',       label: 'Date',       type: 'date' },
    { key: 'time',       label: 'Time',       type: 'text' },
    { key: 'title',      label: 'Title',      type: 'text' },
    { key: 'content',    label: 'Content',     type: 'text' },
    { key: 'categories', label: 'Category',    type: 'select_multi' },
    { key: 'tags',       label: 'Tag',         type: 'select_multi' },
    { key: 'placeName',  label: 'Place Name',  type: 'text' },
    { key: 'weather',    label: 'Weather',     type: 'boolean' }
];

function getOperatorsForType(type) {
    switch (type) {
        case 'text':
            return [
                { key: 'contains',     label: 'contains' },
                { key: 'not_contains', label: 'does not contain' },
                { key: 'equals',       label: 'equals' },
                { key: 'not_equals',   label: 'does not equal' },
                { key: 'starts_with',  label: 'starts with' },
                { key: 'ends_with',    label: 'ends with' },
                { key: 'is_empty',     label: 'is empty' },
                { key: 'is_not_empty', label: 'is not empty' }
            ];
        case 'date':
            return [
                { key: 'equals',         label: 'is' },
                { key: 'not_equals',     label: 'is not' },
                { key: 'before',         label: 'is before' },
                { key: 'after',          label: 'is after' },
                { key: 'within_days',    label: 'within last N days' },
                { key: 'within_weeks',   label: 'within last N weeks' },
                { key: 'within_months',  label: 'within last N months' },
                { key: 'within_years',   label: 'within last N years' },
                { key: 'is_empty',       label: 'is empty' },
                { key: 'is_not_empty',   label: 'is not empty' }
            ];
        case 'select_multi':
            return [
                { key: 'includes',     label: 'includes' },
                { key: 'not_includes', label: 'does not include' },
                { key: 'is_empty',     label: 'is empty' },
                { key: 'is_not_empty', label: 'is not empty' }
            ];
        case 'text_place':
            return [
                { key: 'contains',     label: 'name contains' },
                { key: 'not_contains', label: 'name does not contain' }
            ];
        case 'boolean':
            return [
                { key: 'exists',     label: 'has data' },
                { key: 'not_exists', label: 'has no data' }
            ];
        default:
            return [];
    }
}

function getValueOptions(fieldKey) {
    if (fieldKey === 'categories') return DB.getCategories();
    if (fieldKey === 'tags') return DB.getAllTags();
    return null;
}

// ========== Condition Evaluation ==========

function evaluateCondition(entry, condition) {
    const { field, operator, value, negate } = condition;
    const fieldDef = VIEW_FIELDS.find(f => f.key === field);
    if (!fieldDef) return true;
    const result = evaluateConditionInner(entry, field, operator, value, fieldDef);
    return negate ? !result : result;
}

function evaluateConditionInner(entry, field, operator, value, fieldDef) {

    switch (fieldDef.type) {
        case 'text': {
            const entryVal = (entry[field] || '').toLowerCase();
            const testVal = (value || '').toLowerCase();
            if (operator === 'contains') return entryVal.includes(testVal);
            if (operator === 'not_contains') return !entryVal.includes(testVal);
            if (operator === 'equals') return entryVal === testVal;
            if (operator === 'not_equals') return entryVal !== testVal;
            if (operator === 'starts_with') return entryVal.startsWith(testVal);
            if (operator === 'ends_with') return entryVal.endsWith(testVal);
            if (operator === 'is_empty') return !entryVal;
            if (operator === 'is_not_empty') return !!entryVal;
            return true;
        }
        case 'date': {
            const entryDate = entry.date || '';
            if (operator === 'equals') return entryDate === value;
            if (operator === 'not_equals') return entryDate !== value;
            if (operator === 'before') return entryDate < value;
            if (operator === 'after') return entryDate > value;
            if (operator === 'is_empty') return !entryDate;
            if (operator === 'is_not_empty') return !!entryDate;
            if (operator.startsWith('within_')) {
                const n = parseInt(value) || 1;
                const now = new Date();
                let past = new Date();
                if (operator === 'within_days') past.setDate(now.getDate() - n);
                if (operator === 'within_weeks') past.setDate(now.getDate() - (n * 7));
                if (operator === 'within_months') past.setMonth(now.getMonth() - n);
                if (operator === 'within_years') past.setFullYear(now.getFullYear() - n);
                const pastStr = toLocalDateStr(past);
                return entryDate >= pastStr;
            }
            return true;
        }
        case 'select_multi': {
            const arr = entry[field] || [];
            if (operator === 'includes') return arr.includes(value);
            if (operator === 'not_includes') return !arr.includes(value);
            if (operator === 'is_empty') return arr.length === 0;
            if (operator === 'is_not_empty') return arr.length > 0;
            return true;
        }
        case 'text_place': {
            // Legacy: kept for backward compatibility with old saved views
            const pn = (entry.placeName || '').toLowerCase();
            const testVal = (value || '').toLowerCase();
            if (operator === 'contains') return pn.includes(testVal);
            if (operator === 'not_contains') return !pn.includes(testVal);
            return true;
        }
        case 'boolean': {
            const has = field === 'weather' ? !!(entry.weather && entry.weather.temp !== undefined) : !!entry[field];
            if (operator === 'exists') return has;
            if (operator === 'not_exists') return !has;
            return true;
        }
    }
    return true;
}

function applyView(entries, view) {
    if (!view) return entries;

    // Filter
    if (view.conditions && view.conditions.length > 0) {
        entries = entries.filter(entry => {
            if (view.logic === 'OR') {
                return view.conditions.some(c => evaluateCondition(entry, c));
            }
            return view.conditions.every(c => evaluateCondition(entry, c));
        });
    }

    // Sort by orderBy fields
    if (view.orderBy && view.orderBy.length > 0) {
        entries = [...entries].sort((a, b) => {
            for (const sort of view.orderBy) {
                const cmp = compareEntryField(a, b, sort.field);
                if (cmp !== 0) return sort.direction === 'desc' ? -cmp : cmp;
            }
            return 0;
        });
    }

    return entries;
}

function compareEntryField(a, b, field) {
    let valA, valB;
    if (field === 'categories' || field === 'tags') {
        valA = (a[field] || []).join(', ').toLowerCase();
        valB = (b[field] || []).join(', ').toLowerCase();
    } else if (field === 'weather') {
        valA = a.weather ? (a.weather.temp || 0) : -999;
        valB = b.weather ? (b.weather.temp || 0) : -999;
        return valA - valB;
    } else {
        valA = (a[field] || '').toLowerCase();
        valB = (b[field] || '').toLowerCase();
    }
    return valA.localeCompare(valB);
}

// ========== UI Rendering ==========

function renderViewsBar() {
    const views = getCustomViews();
    const bar = document.getElementById('views-bar');
    if (!bar) return;

    const viewSelect = document.getElementById('view-select');
    viewSelect.innerHTML = '<option value="">All Records (No Filter)</option>' +
        views.map(v => `<option value="${v.id}"${v.id === activeViewId ? ' selected' : ''}>${escapeHtml(v.name)}</option>`).join('');
}

function onViewSelect() {
    const viewId = document.getElementById('view-select').value;
    if (!viewId) {
        activeViewId = null;
        hideViewBuilder();
        filterEntries();
        return;
    }
    activeViewId = viewId;
    filterEntries();
}

function showNewViewBuilder() {
    activeViewId = null;
    document.getElementById('view-select').value = '';
    const builder = document.getElementById('view-builder');
    builder.style.display = 'block';
    document.getElementById('view-name-input').value = '';
    document.getElementById('view-logic-select').value = 'AND';
    document.getElementById('view-pin-dashboard').checked = false;
    document.getElementById('view-conditions-list').innerHTML = '';
    addViewConditionRow();
}

function editCurrentView() {
    if (!activeViewId) return;
    const view = getCustomViews().find(v => v.id === activeViewId);
    if (!view) return;

    const builder = document.getElementById('view-builder');
    builder.style.display = 'block';
    document.getElementById('view-name-input').value = view.name;
    document.getElementById('view-logic-select').value = view.logic || 'AND';
    document.getElementById('view-pin-dashboard').checked = !!view.pinToDashboard;

    const list = document.getElementById('view-conditions-list');
    list.innerHTML = '';
    (view.conditions || []).forEach(c => addViewConditionRow(c));
    if (!view.conditions || view.conditions.length === 0) addViewConditionRow();
}

function hideViewBuilder() {
    document.getElementById('view-builder').style.display = 'none';
}

function addViewConditionRow(condition) {
    const list = document.getElementById('view-conditions-list');
    const row = document.createElement('div');
    row.className = 'view-condition-row';

    const fieldSel = document.createElement('select');
    fieldSel.className = 'view-field-select';
    fieldSel.innerHTML = '<option value="">Field...</option>' +
        VIEW_FIELDS.map(f => `<option value="${f.key}">${f.label}</option>`).join('');
    fieldSel.onchange = () => onFieldChange(row, fieldSel.value);

    const opSel = document.createElement('select');
    opSel.className = 'view-op-select';
    opSel.innerHTML = '<option value="">Operator...</option>';

    const valContainer = document.createElement('div');
    valContainer.className = 'view-value-container';
    valContainer.innerHTML = '<input type="text" class="view-value-input" placeholder="Value...">';

    const removeBtn = document.createElement('button');
    removeBtn.className = 'btn-small view-condition-remove';
    removeBtn.innerHTML = '&times;';
    removeBtn.title = 'Remove condition';
    removeBtn.onclick = () => { row.remove(); };

    row.appendChild(fieldSel);
    row.appendChild(opSel);
    row.appendChild(valContainer);
    row.appendChild(removeBtn);
    list.appendChild(row);

    if (condition) {
        fieldSel.value = condition.field || '';
        if (condition.field) {
            onFieldChange(row, condition.field);
            opSel.value = condition.operator || '';
            onOperatorChange(row, condition.field, condition.operator || '');
            const valInput = valContainer.querySelector('.view-value-input, .view-value-select');
            if (valInput) valInput.value = condition.value || '';
        }
    }
}

function onFieldChange(row, fieldKey) {
    const opSel = row.querySelector('.view-op-select');
    const valContainer = row.querySelector('.view-value-container');
    const fieldDef = VIEW_FIELDS.find(f => f.key === fieldKey);

    if (!fieldDef) {
        opSel.innerHTML = '<option value="">Operator...</option>';
        valContainer.innerHTML = '<input type="text" class="view-value-input" placeholder="Value...">';
        return;
    }

    const ops = getOperatorsForType(fieldDef.type);
    opSel.innerHTML = ops.map(o => `<option value="${o.key}">${o.label}</option>`).join('');
    opSel.onchange = () => onOperatorChange(row, fieldKey, opSel.value);

    updateValueInput(row, fieldDef, opSel.value);
}

function onOperatorChange(row, fieldKey, operator) {
    const fieldDef = VIEW_FIELDS.find(f => f.key === fieldKey);
    if (fieldDef) updateValueInput(row, fieldDef, operator);
}

function updateValueInput(row, fieldDef, operator) {
    const valContainer = row.querySelector('.view-value-container');

    if (fieldDef.type === 'boolean' || operator === 'is_empty' || operator === 'is_not_empty') {
        valContainer.innerHTML = '';
        return;
    }

    if (fieldDef.type === 'date') {
        if (operator && operator.startsWith('within_')) {
            valContainer.innerHTML = '<input type="number" class="view-value-input" placeholder="N" min="1" value="1">';
        } else {
            valContainer.innerHTML = '<input type="date" class="view-value-input">';
        }
        return;
    }

    if (fieldDef.type === 'select_multi') {
        const options = getValueOptions(fieldDef.key) || [];
        valContainer.innerHTML = `<select class="view-value-input view-value-select">
            ${options.map(o => `<option value="${escapeHtml(o)}">${escapeHtml(o)}</option>`).join('')}
        </select>`;
        return;
    }

    valContainer.innerHTML = '<input type="text" class="view-value-input" placeholder="Value...">';
}

function collectConditions() {
    const rows = document.querySelectorAll('.view-condition-row');
    const conditions = [];
    rows.forEach(row => {
        const field = row.querySelector('.view-field-select').value;
        const operator = row.querySelector('.view-op-select').value;
        const valInput = row.querySelector('.view-value-input');
        const value = valInput ? valInput.value : '';
        if (field && operator) {
            conditions.push({ field, operator, value });
        }
    });
    return conditions;
}

function saveViewFromBuilder() {
    const name = document.getElementById('view-name-input').value.trim();
    if (!name) {
        alert('Please enter a view name.');
        return;
    }

    const logic = document.getElementById('view-logic-select').value;
    const conditions = collectConditions();
    const pinToDashboard = document.getElementById('view-pin-dashboard').checked;

    if (conditions.length === 0) {
        alert('Please add at least one condition.');
        return;
    }

    // Enforce max 10 pinned views
    if (pinToDashboard) {
        const currentPinned = getCustomViews().filter(v => v.pinToDashboard && v.id !== activeViewId);
        if (currentPinned.length >= 10) {
            alert('Maximum of 10 dashboard quick action buttons allowed. Unpin another view first.');
            return;
        }
    }

    if (activeViewId) {
        updateView(activeViewId, { name, conditions, logic, pinToDashboard });
    } else {
        const view = createView(name, conditions, logic, pinToDashboard);
        activeViewId = view.id;
    }

    hideViewBuilder();
    renderViewsBar();
    document.getElementById('view-select').value = activeViewId;
    filterEntries();
}

function deleteCurrentView() {
    if (!activeViewId) return;
    const view = getCustomViews().find(v => v.id === activeViewId);
    if (!view) return;
    if (shouldConfirmDelete() && !confirm(`Delete view "${view.name}"?`)) return;

    deleteView(activeViewId);
    activeViewId = null;
    hideViewBuilder();
    renderViewsBar();
    filterEntries();
}

function launchPinnedView(viewId) {
    activeViewId = viewId;
    navigateTo('entry-list');
    document.getElementById('view-select').value = viewId;
    filterEntries();
}

function getPinnedViews() {
    return getCustomViews().filter(v => v.pinToDashboard).slice(0, 10);
}

function getDefaultEntryView() {
    return getCustomViews().find(v => v.defaultEntryView) || null;
}

function previewViewFromBuilder() {
    const logic = document.getElementById('view-logic-select').value;
    const conditions = collectConditions();
    if (conditions.length === 0) return;

    const tempView = { conditions, logic };
    let entries = DB.getEntries();
    entries = applyView(entries, tempView);
    entries.sort((a, b) => {
        if (b.date !== a.date) return b.date.localeCompare(a.date);
        return (b.time || '').localeCompare(a.time || '');
    });
    renderEntryList(entries);
}

// ========== Custom Views Page ==========

const CV_SORT_FIELDS = [
    { key: 'date',       label: 'Date' },
    { key: 'time',       label: 'Time' },
    { key: 'title',      label: 'Title' },
    { key: 'content',    label: 'Content' },
    { key: 'categories', label: 'Categories' },
    { key: 'tags',       label: 'Tags' },
    { key: 'placeName',  label: 'Place Name' },
    { key: 'dtCreated',  label: 'Created' },
    { key: 'dtUpdated',  label: 'Updated' }
];

let cvEditingId = null;

function refreshCustomViewsPage() {
    const views = getCustomViews();
    const list = document.getElementById('cv-list');
    if (!list) return;

    if (views.length === 0) {
        list.innerHTML = '<div class="no-data">No custom views yet. Create one to get started.</div>';
        return;
    }

    list.innerHTML = views.map(v => {
        const condCount = (v.conditions || []).length;
        const sortCount = (v.orderBy || []).length;
        const groupLabel = v.groupBy ? `Group by ${v.groupBy}` : '';
        const pinIcon = v.pinToDashboard ? ' &#x1F4CC;' : '';
        const defaultIcon = v.defaultEntryView ? ' &#x2B50;' : '';

        let details = [];
        if (condCount > 0) details.push(`${condCount} condition${condCount > 1 ? 's' : ''} (${v.logic || 'AND'})`);
        if (groupLabel) details.push(groupLabel);
        if (sortCount > 0) details.push(`${sortCount} sort field${sortCount > 1 ? 's' : ''}`);
        if (v.displayMode) details.push(v.displayMode === 'list' ? 'List view' : 'Card view');

        return `<div class="cv-card" onclick="cvEditView('${v.id}')">
            <div class="cv-card-header">
                <span class="cv-card-name">${escapeHtml(v.name)}${pinIcon}${defaultIcon}</span>
                <div class="cv-card-actions">
                    <button class="btn-small" onclick="event.stopPropagation(); cvLaunchView('${v.id}')" title="Apply to entry list">&#x25B6; Use</button>
                    <button class="btn-small" onclick="event.stopPropagation(); cvDuplicateView('${v.id}')" title="Duplicate">&#x2398;</button>
                    <button class="btn-small btn-danger-small" onclick="event.stopPropagation(); cvDeleteView('${v.id}')" title="Delete">&times;</button>
                </div>
            </div>
            <div class="cv-card-details">${details.join(' &middot; ')}</div>
            ${condCount > 0 ? `<div class="cv-card-conditions">${(v.conditions || []).map(c => {
                const fld = VIEW_FIELDS.find(f => f.key === c.field);
                const fldLabel = fld ? fld.label : c.field;
                const ops = fld ? getOperatorsForType(fld.type) : [];
                const opLabel = (ops.find(o => o.key === c.operator) || {}).label || c.operator;
                const val = (c.operator === 'is_empty' || c.operator === 'is_not_empty' || c.operator === 'exists' || c.operator === 'not_exists') ? '' : ` "${escapeHtml(c.value)}"`;
                return `<span class="cv-cond-chip">${escapeHtml(fldLabel)} ${escapeHtml(opLabel)}${val}</span>`;
            }).join(v.logic === 'OR' ? ' <span class="cv-logic-sep">OR</span> ' : ' <span class="cv-logic-sep">AND</span> ')}</div>` : ''}
        </div>`;
    }).join('');
}

function cvNewView() {
    if (typeof AndroidBridge !== 'undefined' && typeof AndroidBridge.openCustomViewEditor === 'function') {
        AndroidBridge.openCustomViewEditor('');
        return;
    }
    cvEditingId = null;
    document.getElementById('cv-editor-title').textContent = 'New View';
    document.getElementById('cv-name').value = '';
    document.getElementById('cv-logic').value = 'AND';
    document.getElementById('cv-group-by').value = '';
    document.getElementById('cv-pin-dashboard').checked = false;
    document.getElementById('cv-default-entry-view').checked = false;
    document.getElementById('cv-display-mode').value = '';
    document.getElementById('cv-conditions').innerHTML = '';
    document.getElementById('cv-order-list').innerHTML = '';
    document.getElementById('cv-preview-results').style.display = 'none';
    cvAddCondition();
    cvAddOrderField({ field: 'date', direction: 'desc' });
    document.getElementById('cv-editor').style.display = 'block';
    document.getElementById('cv-list').style.display = 'none';
}

function cvEditView(id) {
    if (typeof AndroidBridge !== 'undefined' && typeof AndroidBridge.openCustomViewEditor === 'function') {
        AndroidBridge.openCustomViewEditor(id || '');
        return;
    }
    const view = getCustomViews().find(v => v.id === id);
    if (!view) return;

    cvEditingId = id;
    document.getElementById('cv-editor-title').textContent = 'Edit View';
    document.getElementById('cv-name').value = view.name;
    document.getElementById('cv-logic').value = view.logic || 'AND';
    document.getElementById('cv-group-by').value = view.groupBy || '';
    document.getElementById('cv-display-mode').value = view.displayMode || '';
    document.getElementById('cv-pin-dashboard').checked = !!view.pinToDashboard;
    document.getElementById('cv-default-entry-view').checked = !!view.defaultEntryView;
    document.getElementById('cv-preview-results').style.display = 'none';

    const condList = document.getElementById('cv-conditions');
    condList.innerHTML = '';
    (view.conditions || []).forEach(c => cvAddCondition(c));
    if (!view.conditions || view.conditions.length === 0) cvAddCondition();

    const orderList = document.getElementById('cv-order-list');
    orderList.innerHTML = '';
    (view.orderBy || []).forEach(o => cvAddOrderField(o));

    document.getElementById('cv-editor').style.display = 'block';
    document.getElementById('cv-list').style.display = 'none';
}

function cvCancelEditor() {
    document.getElementById('cv-editor').style.display = 'none';
    document.getElementById('cv-preview-results').style.display = 'none';
    document.getElementById('cv-list').style.display = '';
    cvEditingId = null;
    refreshCustomViewsPage();
}

function cvAddCondition(condition) {
    const list = document.getElementById('cv-conditions');
    const row = document.createElement('div');
    row.className = 'cv-cond-row';

    const fieldSel = document.createElement('select');
    fieldSel.className = 'cv-cond-field';
    fieldSel.innerHTML = '<option value="">Field...</option>' +
        VIEW_FIELDS.map(f => `<option value="${f.key}">${f.label}</option>`).join('');

    const opSel = document.createElement('select');
    opSel.className = 'cv-cond-op';
    opSel.innerHTML = '<option value="">Operator...</option>';

    const valContainer = document.createElement('div');
    valContainer.className = 'cv-cond-val';
    valContainer.innerHTML = '<input type="text" class="cv-val-input" placeholder="Value...">';

    const notCheck = document.createElement('label');
    notCheck.className = 'cv-not-label';
    notCheck.innerHTML = '<input type="checkbox" class="cv-not-check"> NOT';
    notCheck.title = 'Negate this condition';

    const removeBtn = document.createElement('button');
    removeBtn.className = 'btn-small cv-cond-remove';
    removeBtn.innerHTML = '&times;';
    removeBtn.title = 'Remove condition';
    removeBtn.onclick = () => row.remove();

    fieldSel.onchange = () => {
        const fld = VIEW_FIELDS.find(f => f.key === fieldSel.value);
        if (!fld) {
            opSel.innerHTML = '<option value="">Operator...</option>';
            valContainer.innerHTML = '<input type="text" class="cv-val-input" placeholder="Value...">';
            return;
        }
        const ops = getOperatorsForType(fld.type);
        opSel.innerHTML = ops.map(o => `<option value="${o.key}">${o.label}</option>`).join('');
        opSel.onchange = () => cvUpdateValue(valContainer, fld, opSel.value);
        cvUpdateValue(valContainer, fld, opSel.value);
    };

    row.appendChild(fieldSel);
    row.appendChild(opSel);
    row.appendChild(valContainer);
    row.appendChild(notCheck);
    row.appendChild(removeBtn);
    list.appendChild(row);

    if (condition) {
        fieldSel.value = condition.field || '';
        if (condition.field) {
            fieldSel.onchange();
            opSel.value = condition.operator || '';
            opSel.onchange();
            const valInput = valContainer.querySelector('.cv-val-input, .cv-val-select');
            if (valInput) valInput.value = condition.value || '';
            if (condition.negate) row.querySelector('.cv-not-check').checked = true;
        }
    }
}

function cvUpdateValue(container, fieldDef, operator) {
    if (fieldDef.type === 'boolean' || operator === 'is_empty' || operator === 'is_not_empty') {
        container.innerHTML = '';
        return;
    }
    if (fieldDef.type === 'date') {
        if (operator && operator.startsWith('within_')) {
            container.innerHTML = '<input type="number" class="cv-val-input" placeholder="N" min="1" value="1">';
        } else {
            container.innerHTML = '<input type="date" class="cv-val-input">';
        }
        return;
    }
    if (fieldDef.type === 'select_multi') {
        const options = getValueOptions(fieldDef.key) || [];
        container.innerHTML = `<select class="cv-val-input cv-val-select">
            ${options.map(o => `<option value="${escapeHtml(o)}">${escapeHtml(o)}</option>`).join('')}
        </select>`;
        return;
    }
    container.innerHTML = '<input type="text" class="cv-val-input" placeholder="Value...">';
}

function cvAddOrderField(order) {
    const list = document.getElementById('cv-order-list');
    const row = document.createElement('div');
    row.className = 'cv-order-row';

    const fieldSel = document.createElement('select');
    fieldSel.className = 'cv-order-field';
    fieldSel.innerHTML = CV_SORT_FIELDS.map(f => `<option value="${f.key}">${f.label}</option>`).join('');

    const dirSel = document.createElement('select');
    dirSel.className = 'cv-order-dir';
    dirSel.innerHTML = '<option value="asc">Ascending &#x25B2;</option><option value="desc">Descending &#x25BC;</option>';

    const removeBtn = document.createElement('button');
    removeBtn.className = 'btn-small cv-cond-remove';
    removeBtn.innerHTML = '&times;';
    removeBtn.onclick = () => row.remove();

    row.appendChild(fieldSel);
    row.appendChild(dirSel);
    row.appendChild(removeBtn);
    list.appendChild(row);

    if (order) {
        fieldSel.value = order.field || 'date';
        dirSel.value = order.direction || 'asc';
    }
}

function cvCollectConditions() {
    const rows = document.querySelectorAll('#cv-conditions .cv-cond-row');
    const conditions = [];
    rows.forEach(row => {
        const field = row.querySelector('.cv-cond-field').value;
        const operator = row.querySelector('.cv-cond-op').value;
        const valInput = row.querySelector('.cv-val-input');
        const value = valInput ? valInput.value : '';
        const negate = row.querySelector('.cv-not-check').checked;
        if (field && operator) {
            conditions.push({ field, operator, value, negate });
        }
    });
    return conditions;
}

function cvCollectOrderBy() {
    const rows = document.querySelectorAll('#cv-order-list .cv-order-row');
    const orderBy = [];
    rows.forEach(row => {
        const field = row.querySelector('.cv-order-field').value;
        const direction = row.querySelector('.cv-order-dir').value;
        if (field) orderBy.push({ field, direction });
    });
    return orderBy;
}

function cvSaveView() {
    const name = document.getElementById('cv-name').value.trim();
    if (!name) { alert('Please enter a view name.'); return; }

    const logic = document.getElementById('cv-logic').value;
    const conditions = cvCollectConditions();
    const groupBy = document.getElementById('cv-group-by').value;
    const orderBy = cvCollectOrderBy();
    const displayMode = document.getElementById('cv-display-mode').value;
    const pinToDashboard = document.getElementById('cv-pin-dashboard').checked;
    const defaultEntryView = document.getElementById('cv-default-entry-view').checked;

    if (pinToDashboard) {
        const currentPinned = getCustomViews().filter(v => v.pinToDashboard && v.id !== cvEditingId);
        if (currentPinned.length >= 10) {
            alert('Maximum of 10 dashboard quick action buttons. Unpin another view first.');
            return;
        }
    }

    // Enforce only one default entry view
    if (defaultEntryView) {
        const views = getCustomViews();
        views.forEach(v => {
            if (v.defaultEntryView && v.id !== cvEditingId) {
                updateView(v.id, { defaultEntryView: false });
            }
        });
    }

    if (cvEditingId) {
        updateView(cvEditingId, { name, conditions, logic, groupBy, orderBy, displayMode, pinToDashboard, defaultEntryView });
    } else {
        createView(name, conditions, logic, pinToDashboard, groupBy, orderBy, displayMode, defaultEntryView);
    }

    cvCancelEditor();
}

function cvPreview() {
    const logic = document.getElementById('cv-logic').value;
    const conditions = cvCollectConditions();
    const groupBy = document.getElementById('cv-group-by').value;
    const orderBy = cvCollectOrderBy();

    const tempView = { conditions, logic, groupBy, orderBy };
    let entries = DB.getEntries();
    entries = applyView(entries, tempView);

    if (!orderBy || orderBy.length === 0) {
        entries.sort((a, b) => {
            if (b.date !== a.date) return b.date.localeCompare(a.date);
            return (b.time || '').localeCompare(a.time || '');
        });
    }

    const container = document.getElementById('cv-preview-container');
    const resultsDiv = document.getElementById('cv-preview-results');
    document.getElementById('cv-preview-title').textContent = `Preview (${entries.length} entries)`;
    resultsDiv.style.display = 'block';

    if (entries.length === 0) {
        container.innerHTML = '<div class="no-data">No entries match these conditions.</div>';
        return;
    }

    const fields = typeof getEntryListFields === 'function' ? getEntryListFields() : {};
    const show = (key) => fields[key] !== false;

    if (groupBy) {
        // Render grouped
        const groups = new Map();
        entries.forEach(e => {
            let keys;
            if (groupBy === 'categories' || groupBy === 'tags') {
                keys = (e[groupBy] || []);
                if (keys.length === 0) keys = ['(none)'];
            } else if (groupBy === 'weather') {
                keys = [e.weather ? (e.weather.description || 'Weather data') : '(no weather)'];
            } else {
                keys = [e[groupBy] || '(empty)'];
            }
            keys.forEach(key => {
                if (!groups.has(key)) groups.set(key, []);
                groups.get(key).push(e);
            });
        });
        let html = '';
        let idx = 0;
        for (const [groupName, groupEntries] of groups) {
            html += `<div class="entry-group"><div class="entry-group-header"><span class="entry-group-name">${escapeHtml(groupName)}</span><span class="entry-group-count">${groupEntries.length}</span></div>`;
            groupEntries.forEach(e => { idx++; html += renderSingleEntryCard(e, idx, show); });
            html += `</div>`;
        }
        container.innerHTML = html;
    } else {
        container.innerHTML = entries.map((e, idx) => renderSingleEntryCard(e, idx + 1, show)).join('');
    }
}

function cvLaunchView(id) {
    activeViewId = id;
    navigateTo('entry-list');
    document.getElementById('view-select').value = id;
    filterEntries();
}

function cvDuplicateView(id) {
    const view = getCustomViews().find(v => v.id === id);
    if (!view) return;
    createView(view.name + ' (copy)', view.conditions ? [...view.conditions] : [], view.logic, false, view.groupBy, view.orderBy ? [...view.orderBy] : []);
    refreshCustomViewsPage();
}

function cvDeleteView(id) {
    const view = getCustomViews().find(v => v.id === id);
    if (!view) return;
    if (shouldConfirmDelete() && !confirm(`Delete view "${view.name}"?`)) return;
    deleteView(id);
    refreshCustomViewsPage();
}
