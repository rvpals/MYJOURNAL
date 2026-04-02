/**
 * Settings: category management, data export/import, password change.
 */

// ========== Settings Tabs ==========

function switchSettingsTab(tabId) {
    document.querySelectorAll('.settings-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.settings-tab-content').forEach(c => c.classList.remove('active'));
    document.getElementById('stab-' + tabId).classList.add('active');
    document.getElementById('stab-content-' + tabId).classList.add('active');
    localStorage.setItem('settingsTab', tabId);
}

function restoreSettingsTab() {
    const saved = localStorage.getItem('settingsTab');
    if (saved) switchSettingsTab(saved);
}

function getSettingsSectionKey(h3) {
    return h3.textContent.replace(/[^\w]/g, '').toLowerCase();
}

function getPinnedSections() {
    return (DB.getSettings().pinnedSettingsSections || []);
}

function savePinnedSections(pinned) {
    DB.setSettings({ pinnedSettingsSections: pinned });
}

function toggleSettingsSection(h3) {
    const body = h3.nextElementSibling;
    if (!body || !body.classList.contains('settings-section-body')) return;
    const collapsed = !body.classList.contains('collapsed');
    body.classList.toggle('collapsed', collapsed);
    h3.classList.toggle('collapsed', collapsed);
}

function togglePinSection(e, btn) {
    e.stopPropagation();
    const section = btn.closest('.settings-section');
    const h3 = section.querySelector('.settings-collapsible');
    const key = getSettingsSectionKey(h3);
    let pinned = getPinnedSections();
    const isPinned = pinned.includes(key);
    if (isPinned) {
        pinned = pinned.filter(k => k !== key);
    } else {
        pinned.push(key);
    }
    savePinnedSections(pinned);
    btn.classList.toggle('pinned', !isPinned);
    btn.title = !isPinned ? 'Unpin (stay expanded)' : 'Pin (stay expanded)';

    // Expand when pinning
    if (!isPinned) {
        const body = h3.nextElementSibling;
        if (body && body.classList.contains('settings-section-body')) {
            body.classList.remove('collapsed');
            h3.classList.remove('collapsed');
        }
    }
}

function initCollapsibleSettings() {
    const pinned = getPinnedSections();
    document.querySelectorAll('.settings-collapsible').forEach(h3 => {
        const body = h3.nextElementSibling;
        if (!body || !body.classList.contains('settings-section-body')) return;
        const key = getSettingsSectionKey(h3);
        const isPinned = pinned.includes(key);

        // Add pin button if not already present
        const section = h3.closest('.settings-section');
        if (!section.querySelector('.btn-pin-section')) {
            const pin = document.createElement('span');
            pin.className = 'btn-pin-section' + (isPinned ? ' pinned' : '');
            pin.innerHTML = '&#128204;';
            pin.title = isPinned ? 'Unpin (stay expanded)' : 'Pin (stay expanded)';
            pin.onclick = function(e) { togglePinSection(e, this); };
            section.insertBefore(pin, section.firstChild);
        } else {
            const pin = section.querySelector('.btn-pin-section');
            pin.classList.toggle('pinned', isPinned);
            pin.title = isPinned ? 'Unpin (stay expanded)' : 'Pin (stay expanded)';
        }

        if (isPinned) {
            body.classList.remove('collapsed');
            h3.classList.remove('collapsed');
        } else {
            body.classList.add('collapsed');
            h3.classList.add('collapsed');
        }
    });
}

function refreshSettings() {
    renderCategoriesList();
    renderTagsIconList();
    renderPeopleList();
    refreshWeatherSettings();
    refreshAutoOpenToggle();
    refreshWarnDeleteToggle();
    refreshWarnUnsavedToggle();
    refreshMiscCollapsedToggle();
    if (typeof refreshBiometricToggle === 'function') refreshBiometricToggle();
    refreshGeocodingProvider();
    renderEntryFieldsToggles();
    refreshColorToggles();
    refreshViewerFont();
    refreshDateTimeFormat();
    refreshMaxPinnedEntries();
    refreshDefaultEntryListOrder();
    refreshThemeSelect();
    renderTemplatesList();
    renderEntryTemplatesList();
    if (typeof refreshCustomViewsPage === 'function') refreshCustomViewsPage();
    restoreSettingsTab();
}

function refreshThemeSelect() {
    const settings = DB.getSettings();
    const sel = document.getElementById('theme-select');
    if (sel) sel.value = settings.theme || 'light';
    renderThemePreview();
}

// ========== Categories ==========

// ========== Icon Picker State ==========
let _iconPickerTarget = null; // { type: 'category'|'tag', name: string }

function getCategoryColors() {
    return DB.getSettings().categoryColors || {};
}

function getTagColors() {
    return DB.getSettings().tagColors || {};
}

async function setCategoryColor(name, color) {
    const colors = getCategoryColors();
    if (color) {
        colors[name] = color;
    } else {
        delete colors[name];
    }
    await DB.setSettings({ categoryColors: colors });
}

async function setTagColor(name, color) {
    const colors = getTagColors();
    if (color) {
        colors[name] = color;
    } else {
        delete colors[name];
    }
    await DB.setSettings({ tagColors: colors });
}

function isUseCategoryColor() {
    return DB.getSettings().useCategoryColor === true;
}

function isUseTagColor() {
    return DB.getSettings().useTagColor === true;
}

function toggleUseCategoryColor() {
    const toggle = document.getElementById('use-category-color-toggle');
    DB.setSettings({ useCategoryColor: toggle.checked });
}

function toggleUseTagColor() {
    const toggle = document.getElementById('use-tag-color-toggle');
    DB.setSettings({ useTagColor: toggle.checked });
}

function refreshColorToggles() {
    const catToggle = document.getElementById('use-category-color-toggle');
    const tagToggle = document.getElementById('use-tag-color-toggle');
    if (catToggle) catToggle.checked = isUseCategoryColor();
    if (tagToggle) tagToggle.checked = isUseTagColor();
}

function renderCategoriesList() {
    const container = document.getElementById('categories-list');
    const categories = DB.getCategories();
    const colors = getCategoryColors();
    container.innerHTML = categories.map(cat => {
        const iconData = DB.getIcon('category', cat);
        const iconHtml = iconData
            ? `<img src="${iconData}" class="settings-icon-preview" alt="" onclick="browseIcon('category', '${escapeHtml(cat)}')" title="Click to change icon">`
            : `<span class="settings-icon-placeholder" onclick="browseIcon('category', '${escapeHtml(cat)}')" title="Set icon">+&#xFE0F;&#x1F5BC;</span>`;
        const currentColor = colors[cat] || '';
        const colorHtml = currentColor
            ? `<span class="settings-color-swatch" style="background:${currentColor}" onclick="this.nextElementSibling.click()" title="Click to change, right-click to clear" oncontextmenu="event.preventDefault();setCategoryColor('${escapeHtml(cat)}', '');renderCategoriesList()"></span><input type="color" class="settings-color-hidden" value="${currentColor}" onchange="setCategoryColor('${escapeHtml(cat)}', this.value);renderCategoriesList()">`
            : `<span class="settings-color-swatch settings-color-empty" onclick="this.nextElementSibling.click()" title="Set color"></span><input type="color" class="settings-color-hidden" value="#888888" onchange="setCategoryColor('${escapeHtml(cat)}', this.value);renderCategoriesList()">`;
        return `<div class="settings-list-item">
            ${iconHtml}
            <span class="settings-item-name">${escapeHtml(cat)}</span>
            ${colorHtml}
            <span class="remove-btn" onclick="removeCategory('${escapeHtml(cat)}')" title="Remove category">&times;</span>
        </div>`;
    }).join('');
}

async function addCategory() {
    const input = document.getElementById('new-category-input');
    const name = input.value.trim();
    if (!name) return;

    const categories = DB.getCategories();
    if (categories.includes(name)) {
        alert('Category already exists.');
        return;
    }

    categories.push(name);
    await DB.setCategories(categories);
    input.value = '';
    renderCategoriesList();
}

async function removeCategory(name) {
    if (!confirm(`Remove category "${name}"?`)) return;
    const categories = DB.getCategories().filter(c => c !== name);
    await DB.setCategories(categories);
    await DB.removeIcon('category', name);
    await setCategoryColor(name, '');
    renderCategoriesList();
}

// ========== Tags Icon Management ==========

function renderTagsIconList() {
    const container = document.getElementById('tags-icon-list');
    if (!container) return;
    const tags = DB.getAllTags();
    if (!tags.length) {
        container.innerHTML = '<p class="settings-hint">No tags yet. Create tags in your entries.</p>';
        return;
    }
    const colors = getTagColors();
    container.innerHTML = tags.map(tag => {
        const iconData = DB.getIcon('tag', tag);
        const iconHtml = iconData
            ? `<img src="${iconData}" class="settings-icon-preview" alt="" onclick="browseIcon('tag', '${escapeHtml(tag)}')" title="Click to change icon">`
            : `<span class="settings-icon-placeholder" onclick="browseIcon('tag', '${escapeHtml(tag)}')" title="Set icon">+&#xFE0F;&#x1F5BC;</span>`;
        const currentColor = colors[tag] || '';
        const colorHtml = currentColor
            ? `<span class="settings-color-swatch" style="background:${currentColor}" onclick="this.nextElementSibling.click()" title="Click to change, right-click to clear" oncontextmenu="event.preventDefault();setTagColor('${escapeHtml(tag)}', '');renderTagsIconList()"></span><input type="color" class="settings-color-hidden" value="${currentColor}" onchange="setTagColor('${escapeHtml(tag)}', this.value);renderTagsIconList()">`
            : `<span class="settings-color-swatch settings-color-empty" onclick="this.nextElementSibling.click()" title="Set color"></span><input type="color" class="settings-color-hidden" value="#888888" onchange="setTagColor('${escapeHtml(tag)}', this.value);renderTagsIconList()">`;
        return `<div class="settings-list-item">
            ${iconHtml}
            <span class="settings-item-name">${escapeHtml(tag)}</span>
            ${colorHtml}
        </div>`;
    }).join('');
}

// ========== Icon Picker ==========

function browseIcon(type, name) {
    _iconPickerTarget = { type, name };
    document.getElementById('icon-file-input').click();
}

function onIconFileSelected(event) {
    const file = event.target.files[0];
    if (!file || !_iconPickerTarget) return;
    const reader = new FileReader();
    reader.onload = async function(e) {
        // Resize icon to max 64x64 for storage efficiency
        const img = new Image();
        img.onload = async function() {
            const size = 64;
            const canvas = document.createElement('canvas');
            canvas.width = size;
            canvas.height = size;
            const ctx = canvas.getContext('2d');
            // Draw centered and scaled to fit
            const scale = Math.min(size / img.width, size / img.height);
            const w = img.width * scale;
            const h = img.height * scale;
            ctx.drawImage(img, (size - w) / 2, (size - h) / 2, w, h);
            const dataUrl = canvas.toDataURL('image/png');
            await DB.setIcon(_iconPickerTarget.type, _iconPickerTarget.name, dataUrl);
            _iconPickerTarget = null;
            renderCategoriesList();
            renderTagsIconList();
            renderPeopleList();
        };
        img.src = e.target.result;
    };
    reader.readAsDataURL(file);
    event.target.value = '';
}

async function clearIcon(type, name) {
    await DB.removeIcon(type, name);
    renderCategoriesList();
    renderTagsIconList();
    renderPeopleList();
}

// ========== People Management ==========

let _editingPerson = null; // { firstName, lastName } or null for new

function renderPeopleList() {
    const container = document.getElementById('people-list');
    if (!container) return;
    const people = DB.getPeople();
    const filter = (document.getElementById('people-filter-input')?.value || '').toLowerCase();
    const filtered = filter
        ? people.filter(p => (p.firstName + ' ' + p.lastName + ' ' + p.description).toLowerCase().includes(filter))
        : people;

    if (filtered.length === 0) {
        container.innerHTML = people.length === 0
            ? '<div class="no-data">No people yet.</div>'
            : '<div class="no-data">No matches found.</div>';
        return;
    }
    container.innerHTML = filtered.map(p => {
        const fullName = p.firstName + ' ' + p.lastName;
        const name = escapeHtml(fullName);
        const desc = p.description ? `<span class="people-desc">${escapeHtml(p.description)}</span>` : '';
        const iconData = DB.getIcon('person', fullName);
        const iconHtml = iconData
            ? `<img src="${iconData}" class="settings-icon-preview" alt="" onclick="event.stopPropagation();browseIcon('person', '${escapeHtml(fullName)}')" title="Change icon">`
            : `<span class="settings-icon-placeholder" onclick="event.stopPropagation();browseIcon('person', '${escapeHtml(fullName)}')" title="Set icon">+&#xFE0F;&#x1F5BC;</span>`;
        const removeIconHtml = iconData ? `<span class="remove-icon-btn" onclick="event.stopPropagation();clearIcon('person', '${escapeHtml(fullName)}')" title="Remove icon">&times;</span>` : '';
        return `<div class="settings-list-item people-item" onclick="editPersonRecord('${escapeHtml(p.firstName)}', '${escapeHtml(p.lastName)}')">
            ${iconHtml}
            <span class="settings-item-name">${name}</span>
            ${desc}
            ${removeIconHtml}
        </div>`;
    }).join('');
}

function filterPeopleList() {
    renderPeopleList();
}

function newPersonForm() {
    _editingPerson = null;
    document.getElementById('person-editor-title').textContent = 'New Person';
    document.getElementById('person-first-name').value = '';
    document.getElementById('person-last-name').value = '';
    document.getElementById('person-description').value = '';
    document.getElementById('btn-delete-person').style.display = 'none';
    document.getElementById('person-editor').style.display = 'block';
}

function editPersonRecord(firstName, lastName) {
    const people = DB.getPeople();
    const p = people.find(x => x.firstName === firstName && x.lastName === lastName);
    if (!p) return;
    _editingPerson = { firstName: p.firstName, lastName: p.lastName };
    document.getElementById('person-editor-title').textContent = 'Edit Person';
    document.getElementById('person-first-name').value = p.firstName;
    document.getElementById('person-last-name').value = p.lastName;
    document.getElementById('person-description').value = p.description || '';
    document.getElementById('btn-delete-person').style.display = 'inline-block';
    document.getElementById('person-editor').style.display = 'block';
}

async function savePersonFromEditor() {
    const firstName = document.getElementById('person-first-name').value.trim();
    const lastName = document.getElementById('person-last-name').value.trim();
    const description = document.getElementById('person-description').value.trim();

    if (!firstName || !lastName) {
        alert('First name and last name are required.');
        return;
    }

    if (_editingPerson) {
        const oldName = _editingPerson.firstName + ' ' + _editingPerson.lastName;
        const newName = firstName + ' ' + lastName;
        await DB.updatePerson(_editingPerson.firstName, _editingPerson.lastName, firstName, lastName, description);
        // Move icon if name changed
        if (oldName !== newName) {
            const iconData = DB.getIcon('person', oldName);
            if (iconData) {
                await DB.setIcon('person', newName, iconData);
                await DB.removeIcon('person', oldName);
            }
        }
    } else {
        await DB.addPerson(firstName, lastName, description);
    }

    cancelPersonEditor();
    renderPeopleList();
}

async function deletePersonFromEditor() {
    if (!_editingPerson) return;
    if (shouldConfirmDelete() && !confirm(`Delete ${_editingPerson.firstName} ${_editingPerson.lastName}?`)) return;
    const fullName = _editingPerson.firstName + ' ' + _editingPerson.lastName;
    await DB.deletePerson(_editingPerson.firstName, _editingPerson.lastName);
    await DB.removeIcon('person', fullName);
    cancelPersonEditor();
    renderPeopleList();
}

function cancelPersonEditor() {
    _editingPerson = null;
    document.getElementById('person-editor').style.display = 'none';
}

// ========== Metadata Export/Import ==========

function exportMetadata() {
    const metadata = {};

    // Categories
    metadata.categories = DB.getCategories();

    // All icons (category, tag, person)
    metadata.icons = DB.getAllIcons();

    // People
    metadata.people = DB.getPeople();

    // All settings (includes reportTemplates, customViews, entryTemplates, entryListFields, theme, etc.)
    metadata.settings = DB.getSettings();

    const json = JSON.stringify(metadata, null, 2);
    const journalId = DB.getJournalId() || 'journal';
    downloadFile(json, journalId + '_metadata.json', 'application/json');
}

async function importMetadata(event) {
    const file = event.target.files[0];
    if (!file) return;
    event.target.value = '';

    if (!confirm('This will reset and overwrite your current metadata. Categories, tags, people, icons, settings, templates, and custom views will all be replaced.\n\nWould you like to continue?')) {
        return;
    }

    try {
        const text = await file.text();
        const metadata = JSON.parse(text);

        // Validate basic structure
        if (typeof metadata !== 'object' || metadata === null) {
            throw new Error('Invalid metadata file format.');
        }

        // Import categories: clear and re-add
        if (Array.isArray(metadata.categories)) {
            await DB.setCategories(metadata.categories);
        }

        // Import icons: clear all existing, insert new
        const existingIcons = DB.getAllIcons();
        for (const icon of existingIcons) {
            await DB.removeIcon(icon.type, icon.name);
        }
        if (Array.isArray(metadata.icons)) {
            for (const icon of metadata.icons) {
                if (icon.type && icon.name && icon.data) {
                    await DB.setIcon(icon.type, icon.name, icon.data);
                }
            }
        }

        // Import people: clear all existing, insert new
        const existingPeople = DB.getPeople();
        for (const p of existingPeople) {
            await DB.deletePerson(p.firstName, p.lastName);
        }
        if (Array.isArray(metadata.people)) {
            for (const p of metadata.people) {
                if (p.firstName && p.lastName) {
                    await DB.addPerson(p.firstName, p.lastName, p.description || '');
                }
            }
        }

        // Import settings: overwrite all keys
        if (metadata.settings && typeof metadata.settings === 'object') {
            await DB.setSettings(metadata.settings);
        }

        alert('Metadata imported successfully. The app will now refresh.');

        // Re-login to apply all changes
        window.location.reload();

    } catch (err) {
        alert('Import failed: ' + err.message);
    }
}

// ========== Data Export/Import ==========

function downloadBinaryFile(uint8Array, filename, mimeType) {
    if (window.AndroidBridge && window.AndroidBridge.saveFile) {
        // Convert Uint8Array to base64 for AndroidBridge
        let binary = '';
        for (let i = 0; i < uint8Array.length; i++) {
            binary += String.fromCharCode(uint8Array[i]);
        }
        window.AndroidBridge.saveFile(filename, btoa(binary), mimeType);
    } else {
        const blob = new Blob([uint8Array], { type: mimeType });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }
}

async function exportData(encrypted) {
    try {
        if (encrypted) {
            const json = await DB.exportJSON();
            if (!json) { alert('No data to export.'); return; }
            downloadFile(json, 'journal_backup.sqlite.enc', 'application/octet-stream');
        } else {
            const bytes = DB.exportSQLiteBytes();
            if (!bytes) { alert('No data to export.'); return; }
            downloadBinaryFile(new Uint8Array(bytes), 'journal_backup.sqlite', 'application/x-sqlite3');
        }
    } catch (err) {
        alert('Export failed: ' + err.message);
    }
}

function exportCsvFromSettings() {
    const entries = DB.getEntries();
    if (entries.length === 0) {
        alert('No entries to export.');
        return;
    }

    const headers = [
        'Date', 'Time', 'Title', 'Content', 'Rich Content',
        'Categories', 'Tags', 'Place Name', 'Locations',
        'Weather', 'Created', 'Updated'
    ];

    const esc = (str) => {
        if (!str) return '""';
        return '"' + str.replace(/"/g, '""') + '"';
    };

    const rows = entries.map(e => {
        const locs = (e.locations || []).map(l => {
            let s = l.address || '';
            if (l.lat != null && l.lng != null) s += ' (' + l.lat + ', ' + l.lng + ')';
            return s;
        }).join('; ');

        const weather = e.weather ? Weather.formatWeather(e.weather) : '';

        return [
            esc(e.date || ''),
            esc(e.time || ''),
            esc(e.title || ''),
            esc(e.content || ''),
            esc(e.richContent || ''),
            esc((e.categories || []).join('; ')),
            esc((e.tags || []).join('; ')),
            esc(e.placeName || ''),
            esc(locs),
            esc(weather),
            esc(e.dtCreated || ''),
            esc(e.dtUpdated || '')
        ].join(',');
    });

    const csv = [headers.map(h => esc(h)).join(','), ...rows].join('\n');
    downloadFile(csv, 'journal_export.csv', 'text/csv');
}

async function importData(event) {
    const file = event.target.files[0];
    if (!file) return;

    if (!confirm('Importing will replace ALL current data. Are you sure?')) {
        event.target.value = '';
        return;
    }

    try {
        if (file.name.endsWith('.sqlite') && !file.name.endsWith('.sqlite.enc')) {
            // Unencrypted SQLite file
            const bytes = new Uint8Array(await file.arrayBuffer());
            await DB.importSQLiteBytes(bytes);
        } else {
            // Encrypted (.sqlite.enc or legacy .json)
            const text = await file.text();
            await DB.importJSON(text);
        }
        alert('Data imported successfully.');
        navigateTo('dashboard');
    } catch (err) {
        alert('Import failed. Make sure the file was exported with the same password.\n\n' + err.message);
    }

    event.target.value = '';
}

async function importSqliteFile(event) {
    const file = event.target.files[0];
    if (!file) return;

    if (!confirm('Importing will replace ALL current data. Are you sure?')) {
        event.target.value = '';
        return;
    }

    try {
        const bytes = new Uint8Array(await file.arrayBuffer());
        await DB.importSQLiteBytes(bytes);
        alert('SQLite data imported successfully.');
        navigateTo('dashboard');
    } catch (err) {
        alert('Import failed: ' + err.message);
    }

    event.target.value = '';
}

// ========== CSV Import ==========

let csvParsedData = null; // { headers: [], rows: [[]] }

const CSV_JOURNAL_FIELDS = [
    { key: '',             label: '-- Skip --' },
    { key: 'date',         label: 'Date' },
    { key: 'time',         label: 'Time' },
    { key: 'title',        label: 'Title' },
    { key: 'content',      label: 'Content' },
    { key: 'richContent',  label: 'Rich Content (HTML)' },
    { key: 'categories',   label: 'Categories' },
    { key: 'tags',         label: 'Tags' },
    { key: 'people',       label: 'People' },
    { key: 'placeName',    label: 'Place Name' },
    { key: 'placeAddress', label: 'Place Address' },
    { key: 'placeCoords',  label: 'Place Coords (lat, lng)' }
];

function parseCSV(text) {
    const rows = [];
    let current = '';
    let inQuotes = false;
    let row = [];

    for (let i = 0; i < text.length; i++) {
        const ch = text[i];
        const next = text[i + 1];

        if (inQuotes) {
            if (ch === '"' && next === '"') {
                current += '"';
                i++;
            } else if (ch === '"') {
                inQuotes = false;
            } else {
                current += ch;
            }
        } else {
            if (ch === '"') {
                inQuotes = true;
            } else if (ch === ',') {
                row.push(current.trim());
                current = '';
            } else if (ch === '\r' && next === '\n') {
                row.push(current.trim());
                current = '';
                rows.push(row);
                row = [];
                i++;
            } else if (ch === '\n') {
                row.push(current.trim());
                current = '';
                rows.push(row);
                row = [];
            } else {
                current += ch;
            }
        }
    }
    // Last field/row
    if (current || row.length > 0) {
        row.push(current.trim());
        rows.push(row);
    }

    // Filter empty rows
    return rows.filter(r => r.some(cell => cell !== ''));
}

function startCsvImport(event) {
    const file = event.target.files[0];
    if (!file) return;
    event.target.value = '';

    file.text().then(text => {
        const allRows = parseCSV(text);
        if (allRows.length < 2) {
            alert('CSV file must have a header row and at least one data row.');
            return;
        }

        const headers = allRows[0];
        const dataRows = allRows.slice(1);
        csvParsedData = { headers, rows: dataRows };

        showCsvMappingUI();
    });
}

function showCsvMappingUI() {
    if (!csvParsedData) return;
    const { headers, rows } = csvParsedData;

    document.getElementById('csv-import-section').style.display = 'block';
    document.getElementById('csv-import-msg').textContent = '';

    // Info
    document.getElementById('csv-preview-info').textContent =
        `Found ${headers.length} columns, ${rows.length} data rows.`;

    // Mapping dropdowns
    const list = document.getElementById('csv-mapping-list');
    list.innerHTML = headers.map((h, i) => {
        const autoMatch = autoDetectField(h);
        return `
        <div class="csv-mapping-row">
            <span class="csv-col-name" title="${escapeHtml(h)}">${escapeHtml(h)}</span>
            <span class="csv-arrow">&#8594;</span>
            <select class="csv-field-select" data-col="${i}" onchange="onCsvFieldChange()">
                ${CSV_JOURNAL_FIELDS.map(f =>
                    `<option value="${f.key}"${f.key === autoMatch ? ' selected' : ''}>${f.label}</option>`
                ).join('')}
            </select>
        </div>`;
    }).join('');

    // Pre-populate date/time format from saved settings
    const settings = DB.getSettings();
    document.getElementById('csv-date-format').value = settings.csvDateFormat || '';
    document.getElementById('csv-time-format').value = settings.csvTimeFormat || '';

    // Preview table (first 4 rows)
    renderCsvPreviewTable();
    onCsvFieldChange();
}

function autoDetectField(header) {
    const h = header.toLowerCase().replace(/[^a-z0-9]/g, '');
    if (/^date$|^entrydate$/.test(h)) return 'date';
    if (/^time$|^entrytime$/.test(h)) return 'time';
    if (/^title$|^subject$|^heading$/.test(h)) return 'title';
    if (/^content$|^body$|^text$|^description$|^note$/.test(h)) return 'content';
    if (/^richcontent$|^html$|^htmlcontent$/.test(h)) return 'richContent';
    if (/^categor/.test(h)) return 'categories';
    if (/^tag/.test(h)) return 'tags';
    if (/^place$|^placename$|^location$|^locationname$/.test(h)) return 'placeName';
    if (/^address$|^placeaddress$/.test(h)) return 'placeAddress';
    if (/^coord|^gps|^latl|^placecoord/.test(h)) return 'placeCoords';
    return '';
}

function renderCsvPreviewTable() {
    if (!csvParsedData) return;
    const { headers, rows } = csvParsedData;
    const preview = rows.slice(0, 4);

    const container = document.getElementById('csv-preview-table-container');
    let html = '<table class="csv-preview-table"><thead><tr>';
    html += headers.map(h => `<th>${escapeHtml(h)}</th>`).join('');
    html += '</tr></thead><tbody>';
    preview.forEach(row => {
        html += '<tr>';
        html += headers.map((_, i) => `<td>${escapeHtml(row[i] || '')}</td>`).join('');
        html += '</tr>';
    });
    html += '</tbody></table>';
    if (rows.length > 4) html += `<p class="settings-description">...and ${rows.length - 4} more rows</p>`;
    container.innerHTML = html;
}

function onCsvFieldChange() {
    const selects = document.querySelectorAll('.csv-field-select');
    let hasDate = false, hasTime = false;
    selects.forEach(sel => {
        if (sel.value === 'date') hasDate = true;
        if (sel.value === 'time') hasTime = true;
    });

    const dateFmtEl = document.getElementById('csv-date-format-group');
    const timeFmtEl = document.getElementById('csv-time-format-group');
    if (dateFmtEl) dateFmtEl.style.display = hasDate ? 'block' : 'none';
    if (timeFmtEl) timeFmtEl.style.display = hasTime ? 'block' : 'none';
}

function parseDateWithFormat(val, fmt) {
    if (!val || !fmt) return normalizeDate(val);
    // Build regex from format: YYYY, YY, MM, DD, M, D
    // Replace tokens with capture groups
    let pattern = fmt
        .replace(/[.*+?^${}()|[\]\\]/g, '\\$&') // escape regex chars first
        .replace('YYYY', '(?<Y>\\d{4})')
        .replace('YY', '(?<Y>\\d{2})')
        .replace('MM', '(?<M>\\d{1,2})')
        .replace('DD', '(?<D>\\d{1,2})')
        .replace('M', '(?<M>\\d{1,2})')
        .replace('D', '(?<D>\\d{1,2})');

    const re = new RegExp('^' + pattern + '$');
    const match = val.match(re);
    if (match && match.groups) {
        let year = match.groups.Y;
        const month = match.groups.M;
        const day = match.groups.D;
        if (!year || !month || !day) return normalizeDate(val);
        if (year.length === 2) year = (parseInt(year) > 50 ? '19' : '20') + year;
        return `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
    }
    return normalizeDate(val);
}

function parseTimeWithFormat(val, fmt) {
    if (!val || !fmt) return normalizeTime(val);
    // Tokens: HH, H, mm, A/a (AM/PM)
    let pattern = fmt
        .replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
        .replace('HH', '(?<H>\\d{1,2})')
        .replace('H', '(?<H>\\d{1,2})')
        .replace('mm', '(?<m>\\d{2})')
        .replace('A', '(?<A>[AaPp][Mm])')
        .replace('a', '(?<A>[AaPp][Mm])');

    const re = new RegExp('^' + pattern + '$');
    const match = val.trim().match(re);
    if (match && match.groups) {
        let h = parseInt(match.groups.H);
        const min = match.groups.m || '00';
        if (match.groups.A) {
            const pm = match.groups.A.toLowerCase() === 'pm';
            if (pm && h < 12) h += 12;
            if (!pm && h === 12) h = 0;
        }
        return String(h).padStart(2, '0') + ':' + min;
    }
    return normalizeTime(val);
}

function normalizeDate(val) {
    if (!val) return '';
    // Try ISO format first (YYYY-MM-DD)
    if (/^\d{4}-\d{2}-\d{2}$/.test(val)) return val;
    // Try MM/DD/YYYY or M/D/YYYY
    let m = val.match(/^(\d{1,2})[\/\-](\d{1,2})[\/\-](\d{4})$/);
    if (m) return `${m[3]}-${m[1].padStart(2,'0')}-${m[2].padStart(2,'0')}`;
    // Try parsing with Date
    const d = new Date(val);
    if (!isNaN(d.getTime())) return toLocalDateStr(d);
    return val;
}

function normalizeTime(val) {
    if (!val) return '';
    // Already HH:MM
    if (/^\d{2}:\d{2}$/.test(val)) return val;
    // H:MM AM/PM
    let m = val.match(/^(\d{1,2}):(\d{2})\s*(am|pm)?$/i);
    if (m) {
        let h = parseInt(m[1]);
        const min = m[2];
        if (m[3]) {
            const pm = m[3].toLowerCase() === 'pm';
            if (pm && h < 12) h += 12;
            if (!pm && h === 12) h = 0;
        }
        return String(h).padStart(2, '0') + ':' + min;
    }
    return val;
}

async function executeCsvImport() {
    if (!csvParsedData) return;
    const { headers, rows } = csvParsedData;
    const msgEl = document.getElementById('csv-import-msg');
    const progressContainer = document.getElementById('csv-progress-container');
    const progressBar = document.getElementById('csv-progress-bar');
    const progressText = document.getElementById('csv-progress-text');
    msgEl.textContent = '';
    msgEl.style.color = '';

    // Collect mappings
    const selects = document.querySelectorAll('.csv-field-select');
    const mappings = {}; // colIndex -> fieldKey
    selects.forEach(sel => {
        const col = parseInt(sel.dataset.col);
        const field = sel.value;
        if (field) mappings[col] = field;
    });

    if (Object.keys(mappings).length === 0) {
        msgEl.textContent = 'Please map at least one column to a journal field.';
        msgEl.style.color = 'var(--danger)';
        return;
    }

    // Disable buttons during import
    const importBtn = document.querySelector('.csv-import-btn');
    const cancelBtn = importBtn.nextElementSibling;
    importBtn.disabled = true;
    cancelBtn.disabled = true;
    importBtn.textContent = 'Importing...';

    // Show progress
    progressContainer.style.display = 'block';
    progressBar.style.width = '0%';
    progressText.textContent = `0 / ${rows.length}`;

    // Build existing entry key set for duplicate detection (Date+Title)
    const existingKeys = new Set();
    DB.getEntries().forEach(e => {
        if (e.date && e.title) {
            existingKeys.add(e.date + '|' + e.title.toLowerCase().trim());
        }
    });

    const separator = document.getElementById('csv-separator').value || ',';
    const tagsSpaceSep = document.getElementById('csv-tags-space-sep').checked;
    const customDateFmt = (document.getElementById('csv-date-format').value || '').trim();
    const customTimeFmt = (document.getElementById('csv-time-format').value || '').trim();

    // Save formats as defaults for next import
    DB.setSettings({ csvDateFormat: customDateFmt, csvTimeFormat: customTimeFmt });
    const now = new Date().toISOString();
    let imported = 0;
    let skipped = 0;
    let duplicates = [];
    const total = rows.length;

    // Track new categories, tags, and people to auto-create
    const existingCategories = new Set(DB.getCategories().map(c => c.toLowerCase()));
    const existingTags = new Set((DB.getSettings().tags || []).map(t => t.toLowerCase()));
    const existingPeople = new Set(DB.getPeople().map(p => (p.firstName + ' ' + p.lastName).trim().toLowerCase()));
    const newCategories = new Set();
    const newTags = new Set();
    const newPeople = []; // array of {firstName, lastName}

    for (let i = 0; i < rows.length; i++) {
        const row = rows[i];
        const entry = {
            id: generateId(),
            date: toLocalDateStr(new Date()),
            time: '',
            title: '',
            content: '',
            richContent: '',
            categories: [],
            tags: [],
            people: [],
            placeName: '',
            locations: [],
            weather: null,
            dtCreated: now,
            dtUpdated: now
        };

        let csvPlaceName = '', csvPlaceAddress = '', csvPlaceCoords = '';

        for (const [colStr, field] of Object.entries(mappings)) {
            const col = parseInt(colStr);
            const val = (row[col] || '').trim();
            if (!val) continue;

            switch (field) {
                case 'date':
                    entry.date = customDateFmt ? parseDateWithFormat(val, customDateFmt) : normalizeDate(val);
                    break;
                case 'time':
                    entry.time = customTimeFmt ? parseTimeWithFormat(val, customTimeFmt) : normalizeTime(val);
                    break;
                case 'title':
                    entry.title = val;
                    break;
                case 'content':
                    entry.content = val;
                    break;
                case 'richContent':
                    entry.richContent = val;
                    break;
                case 'categories':
                    entry.categories = val.split(separator).map(s => s.trim()).filter(Boolean);
                    break;
                case 'tags':
                    if (tagsSpaceSep) {
                        entry.tags = val.split(/\s+/).map(s => s.trim()).filter(Boolean);
                    } else {
                        entry.tags = val.split(separator).map(s => s.trim()).filter(Boolean);
                    }
                    break;
                case 'people':
                    entry.people = val.split(separator).map(s => s.trim()).filter(Boolean).map(name => {
                        const parts = name.split(/\s+/);
                        return { firstName: parts[0] || '', lastName: parts.slice(1).join(' ') || '' };
                    });
                    break;
                case 'placeName':
                    csvPlaceName = val;
                    break;
                case 'placeAddress':
                    csvPlaceAddress = val;
                    break;
                case 'placeCoords':
                    csvPlaceCoords = val;
                    break;
            }
        }

        entry.placeName = csvPlaceName;
        // Build location if address or coords were mapped
        if (csvPlaceAddress || csvPlaceCoords) {
            const loc = { address: csvPlaceAddress, lat: null, lng: null };
            if (csvPlaceCoords) {
                const match = csvPlaceCoords.match(/(-?\d+\.?\d*)\s*,\s*(-?\d+\.?\d*)/);
                if (match) {
                    loc.lat = parseFloat(match[1]);
                    loc.lng = parseFloat(match[2]);
                }
            }
            entry.locations.push(loc);
        }

        // Collect new categories, tags, people for auto-creation
        for (const cat of entry.categories) {
            if (!existingCategories.has(cat.toLowerCase())) {
                newCategories.add(cat);
                existingCategories.add(cat.toLowerCase());
            }
        }
        for (const tag of entry.tags) {
            if (!existingTags.has(tag.toLowerCase())) {
                newTags.add(tag);
                existingTags.add(tag.toLowerCase());
            }
        }
        for (const person of (entry.people || [])) {
            const key = (person.firstName + ' ' + person.lastName).trim().toLowerCase();
            if (!existingPeople.has(key)) {
                newPeople.push(person);
                existingPeople.add(key);
            }
        }

        // Skip rows with no title and no content
        if (!entry.title && !entry.content) {
            skipped++;
        } else {
            // Check for duplicate (Date + Title)
            const entryKey = entry.date + '|' + entry.title.toLowerCase().trim();
            if (existingKeys.has(entryKey)) {
                duplicates.push({ row: i + 2, date: entry.date, title: entry.title });
            } else {
                await DB.addEntry(entry);
                existingKeys.add(entryKey);
                imported++;
            }
        }

        // Update progress
        const pct = Math.round(((i + 1) / total) * 100);
        progressBar.style.width = pct + '%';
        progressText.textContent = `${i + 1} / ${total}`;

        // Yield to UI every 10 rows so progress bar actually renders
        if ((i + 1) % 10 === 0) {
            await new Promise(r => setTimeout(r, 0));
        }
    }

    // Auto-create new categories, tags, and people
    if (newCategories.size > 0) {
        const allCats = [...DB.getCategories(), ...newCategories];
        DB.setCategories(allCats);
    }
    if (newTags.size > 0) {
        const settings = DB.getSettings();
        const allTags = [...(settings.tags || []), ...newTags];
        DB.setSettings({ tags: allTags });
    }
    for (const person of newPeople) {
        DB.addPerson(person.firstName, person.lastName, '');
    }

    // Done
    progressBar.style.width = '100%';
    progressText.textContent = `${total} / ${total}`;

    let msg = `Import complete: ${imported} entries imported.`;
    if (newCategories.size > 0) msg += ` ${newCategories.size} new categories created.`;
    if (newTags.size > 0) msg += ` ${newTags.size} new tags created.`;
    if (newPeople.length > 0) msg += ` ${newPeople.length} new people created.`;
    if (skipped > 0) msg += ` ${skipped} rows skipped (no title or content).`;
    if (duplicates.length > 0) msg += ` ${duplicates.length} duplicates skipped.`;
    msgEl.textContent = msg;
    msgEl.style.color = 'var(--success)';

    // Show duplicate log if any
    if (duplicates.length > 0) {
        showCsvDuplicateLog(duplicates);
    }

    importBtn.disabled = false;
    cancelBtn.disabled = false;
    importBtn.textContent = 'Import Entries';
    csvParsedData = null;
}

function showCsvDuplicateLog(duplicates) {
    const container = document.getElementById('csv-duplicate-log');
    if (!container) return;
    container.style.display = 'block';
    let html = `<h4>Skipped Duplicates (${duplicates.length})</h4>`;
    html += '<div class="csv-duplicate-list">';
    html += duplicates.map(d =>
        `<div class="csv-duplicate-item"><span class="csv-dup-row">Row ${d.row}</span><span class="csv-dup-date">${escapeHtml(d.date)}</span><span class="csv-dup-title">${escapeHtml(d.title)}</span></div>`
    ).join('');
    html += '</div>';
    container.innerHTML = html;
}

function cancelCsvImport() {
    csvParsedData = null;
    document.getElementById('csv-import-section').style.display = 'none';
    document.getElementById('csv-import-msg').textContent = '';
    document.getElementById('csv-progress-container').style.display = 'none';
    const dupLog = document.getElementById('csv-duplicate-log');
    if (dupLog) { dupLog.style.display = 'none'; dupLog.innerHTML = ''; }
}

// ========== Password Change ==========

async function changePassword() {
    const current = document.getElementById('current-password').value;
    const newPw = document.getElementById('new-password').value;
    const confirmPw = document.getElementById('confirm-new-password').value;
    const msgEl = document.getElementById('password-change-msg');

    msgEl.textContent = '';
    msgEl.style.color = '';

    if (!current || !newPw || !confirmPw) {
        msgEl.textContent = 'Please fill in all fields.';
        msgEl.style.color = 'var(--danger)';
        return;
    }

    if (newPw !== confirmPw) {
        msgEl.textContent = 'New passwords do not match.';
        msgEl.style.color = 'var(--danger)';
        return;
    }

    const valid = await Crypto.verifyPassword(current, DB.getJournalId());
    if (!valid) {
        msgEl.textContent = 'Current password is incorrect.';
        msgEl.style.color = 'var(--danger)';
        return;
    }

    try {
        await Crypto.changePassword(current, newPw, DB.getJournalId());
        DB.setPassword(newPw);
        // Update biometric credential if enabled
        if (window.AndroidBridge && typeof AndroidBridge.hasCredential === 'function'
            && AndroidBridge.hasCredential(DB.getJournalId())) {
            AndroidBridge.saveCredential(DB.getJournalId(), newPw);
        }
        msgEl.textContent = 'Password changed successfully.';
        msgEl.style.color = 'var(--success)';
        document.getElementById('current-password').value = '';
        document.getElementById('new-password').value = '';
        document.getElementById('confirm-new-password').value = '';
    } catch (err) {
        msgEl.textContent = 'Failed to change password: ' + err.message;
        msgEl.style.color = 'var(--danger)';
    }
}

// ========== Weather Location ==========

function refreshWeatherSettings() {
    const loc = Weather.getLocation();
    const display = document.getElementById('weather-location-display');
    const nameEl = document.getElementById('weather-location-name');
    const coordsEl = document.getElementById('weather-location-coords');
    const unitSelect = document.getElementById('weather-temp-unit');

    if (loc) {
        display.style.display = 'flex';
        nameEl.textContent = loc.name;
        coordsEl.textContent = `(${loc.lat.toFixed(4)}, ${loc.lng.toFixed(4)})`;
    } else {
        display.style.display = 'none';
    }

    if (unitSelect) {
        unitSelect.value = Weather.getTempUnit();
    }

    document.getElementById('weather-search-results').innerHTML = '';
    document.getElementById('weather-city-search').value = '';
}

async function weatherSearchCity() {
    const input = document.getElementById('weather-city-search').value.trim();
    const resultsEl = document.getElementById('weather-search-results');
    const msgEl = document.getElementById('weather-settings-msg');
    msgEl.textContent = '';

    if (input.length < 2) {
        msgEl.textContent = 'Enter at least 2 characters.';
        msgEl.style.color = 'var(--danger)';
        return;
    }

    resultsEl.innerHTML = '<div class="weather-searching">Searching...</div>';

    try {
        const results = await Weather.searchCity(input);
        if (results.length === 0) {
            resultsEl.innerHTML = '<div class="weather-no-results">No cities found.</div>';
            return;
        }
        resultsEl.innerHTML = results.map((r, i) => {
            const label = [r.name, r.admin1, r.country].filter(Boolean).join(', ');
            return `<div class="weather-result-item" onclick="selectWeatherLocation(${i})" data-lat="${r.lat}" data-lng="${r.lng}" data-name="${escapeHtml(label)}">${escapeHtml(label)}</div>`;
        }).join('');
        // Store results for selection
        resultsEl._results = results;
    } catch (err) {
        resultsEl.innerHTML = '';
        msgEl.textContent = 'Search failed: ' + err.message;
        msgEl.style.color = 'var(--danger)';
    }
}

function selectWeatherLocation(index) {
    const resultsEl = document.getElementById('weather-search-results');
    const results = resultsEl._results;
    if (!results || !results[index]) return;

    const r = results[index];
    const name = [r.name, r.admin1, r.country].filter(Boolean).join(', ');
    Weather.setLocation({ name, lat: r.lat, lng: r.lng });

    const msgEl = document.getElementById('weather-settings-msg');
    msgEl.textContent = `Location set to ${name}.`;
    msgEl.style.color = 'var(--success)';

    refreshWeatherSettings();
}

function clearWeatherLocation() {
    Weather.setLocation(null);
    const msgEl = document.getElementById('weather-settings-msg');
    msgEl.textContent = 'Weather location cleared.';
    msgEl.style.color = '';
    refreshWeatherSettings();
}

function saveWeatherTempUnit() {
    const unit = document.getElementById('weather-temp-unit').value;
    Weather.setTempUnit(unit);
}

// ========== Auto-Open Last Journal ==========

function refreshAutoOpenToggle() {
    const toggle = document.getElementById('auto-open-toggle');
    if (toggle) {
        toggle.checked = localStorage.getItem('auto_open_last_journal') === 'true';
    }
}

function toggleAutoOpenJournal() {
    const toggle = document.getElementById('auto-open-toggle');
    localStorage.setItem('auto_open_last_journal', toggle.checked ? 'true' : 'false');
}

function refreshWarnDeleteToggle() {
    const toggle = document.getElementById('warn-delete-toggle');
    const val = localStorage.getItem('warn_before_delete');
    // Default to checked (warn) if not set
    toggle.checked = val !== 'false';
}

function toggleWarnBeforeDelete() {
    const toggle = document.getElementById('warn-delete-toggle');
    localStorage.setItem('warn_before_delete', toggle.checked ? 'true' : 'false');
}

function shouldConfirmDelete() {
    return localStorage.getItem('warn_before_delete') !== 'false';
}

// ========== Warn Unsaved Entry ==========

function refreshWarnUnsavedToggle() {
    const toggle = document.getElementById('warn-unsaved-toggle');
    const val = localStorage.getItem('warn_unsaved_entry');
    toggle.checked = val !== 'false';
}

function toggleWarnUnsaved() {
    const toggle = document.getElementById('warn-unsaved-toggle');
    localStorage.setItem('warn_unsaved_entry', toggle.checked ? 'true' : 'false');
}

function shouldWarnUnsaved() {
    return localStorage.getItem('warn_unsaved_entry') !== 'false';
}

// ========== Misc Info Collapse Default ==========

function refreshMiscCollapsedToggle() {
    const toggle = document.getElementById('misc-collapsed-toggle');
    if (toggle) toggle.checked = localStorage.getItem('ev_misc_collapsed') === '1';
}

function toggleMiscCollapsedDefault() {
    const toggle = document.getElementById('misc-collapsed-toggle');
    localStorage.setItem('ev_misc_collapsed', toggle.checked ? '1' : '0');
}

// ========== Default Entry List Order ==========

function refreshDefaultEntryListOrder() {
    const fieldSel = document.getElementById('default-sort-field');
    const dirSel = document.getElementById('default-sort-dir');
    if (fieldSel) fieldSel.value = localStorage.getItem('default_entry_sort_field') || 'dtCreated';
    if (dirSel) dirSel.value = localStorage.getItem('default_entry_sort_dir') || 'desc';
}

function saveDefaultEntryListOrder() {
    const fieldSel = document.getElementById('default-sort-field');
    const dirSel = document.getElementById('default-sort-dir');
    localStorage.setItem('default_entry_sort_field', fieldSel.value);
    localStorage.setItem('default_entry_sort_dir', dirSel.value);
}

// ========== Geocoding Provider ==========

function refreshGeocodingProvider() {
    const sel = document.getElementById('geocoding-provider');
    const provider = localStorage.getItem('geocoding_provider') || 'photon';
    if (sel) sel.value = provider;
    toggleGeocodingApiKeyRow();

    const keyInput = document.getElementById('geocoding-api-key');
    if (keyInput) keyInput.value = localStorage.getItem('geocoding_api_key') || '';
}

function saveGeocodingProvider() {
    const sel = document.getElementById('geocoding-provider');
    localStorage.setItem('geocoding_provider', sel.value);
    toggleGeocodingApiKeyRow();
}

function toggleGeocodingApiKeyRow() {
    const sel = document.getElementById('geocoding-provider');
    const row = document.getElementById('geocoding-api-key-row');
    if (row) row.style.display = sel && sel.value === 'google' ? 'block' : 'none';
}

function saveGeocodingApiKey() {
    const input = document.getElementById('geocoding-api-key');
    localStorage.setItem('geocoding_api_key', input.value.trim());
}

function getGeocodingProvider() {
    return localStorage.getItem('geocoding_provider') || 'photon';
}

function getGeocodingApiKey() {
    return localStorage.getItem('geocoding_api_key') || '';
}

// ========== Entry Viewer Font ==========

function refreshViewerFont() {
    const fontFamily = localStorage.getItem('ev_font_family') || '';
    const fontSize = localStorage.getItem('ev_font_size') || '';
    const famSel = document.getElementById('ev-font-family');
    const sizeSel = document.getElementById('ev-font-size');
    if (famSel) famSel.value = fontFamily;
    if (sizeSel) sizeSel.value = fontSize;
    updateFontPreview();
}

function saveViewerFont() {
    const famSel = document.getElementById('ev-font-family');
    const sizeSel = document.getElementById('ev-font-size');
    localStorage.setItem('ev_font_family', famSel.value);
    localStorage.setItem('ev_font_size', sizeSel.value);
    updateFontPreview();
}

function updateFontPreview() {
    const preview = document.getElementById('ev-font-preview');
    if (!preview) return;
    const fontFamily = localStorage.getItem('ev_font_family') || '';
    const fontSize = localStorage.getItem('ev_font_size') || '';
    preview.style.fontFamily = fontFamily || "'Quicksand', sans-serif";
    preview.style.fontSize = fontSize || '0.95rem';
}

function applyViewerFont() {
    const card = document.querySelector('.entry-view-card');
    if (!card) return;
    const fontFamily = localStorage.getItem('ev_font_family') || '';
    const fontSize = localStorage.getItem('ev_font_size') || '';
    if (fontFamily || fontSize) {
        card.classList.add('ev-custom-font');
        card.style.setProperty('--ev-font-family', fontFamily || "'Quicksand', sans-serif");
        card.style.setProperty('--ev-font-size', fontSize || '0.95rem');
    } else {
        card.classList.remove('ev-custom-font');
    }
}

// ========== Date & Time Display Format ==========

function refreshDateTimeFormat() {
    const dateFmt = localStorage.getItem('ev_date_format') || 'short';
    const timeFmt = localStorage.getItem('ev_time_format') || '12h';
    const dateSel = document.getElementById('ev-date-format');
    const timeSel = document.getElementById('ev-time-format');
    if (dateSel) dateSel.value = dateFmt;
    if (timeSel) timeSel.value = timeFmt;
    updateDateTimePreview();
}

function saveDateTimeFormat() {
    const dateSel = document.getElementById('ev-date-format');
    const timeSel = document.getElementById('ev-time-format');
    localStorage.setItem('ev_date_format', dateSel.value);
    localStorage.setItem('ev_time_format', timeSel.value);
    updateDateTimePreview();
}

function updateDateTimePreview() {
    const preview = document.getElementById('ev-datetime-preview');
    if (!preview) return;
    const now = new Date();
    const dateStr = toLocalDateStr(now);
    const timeStr = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0');
    preview.textContent = formatDate(dateStr) + '  \u2022  ' + formatTime(timeStr);
}

// ========== Max Pinned Entries ==========

function refreshMaxPinnedEntries() {
    const input = document.getElementById('max-pinned-entries');
    if (input) input.value = getMaxPinnedEntries();
}

function saveMaxPinnedEntries() {
    const input = document.getElementById('max-pinned-entries');
    const val = Math.max(1, Math.min(100, parseInt(input.value) || 10));
    input.value = val;
    localStorage.setItem('max_pinned_entries', val);
}

function getMaxPinnedEntries() {
    return parseInt(localStorage.getItem('max_pinned_entries')) || 10;
}

// ========== Entry List Fields ==========

const ENTRY_LIST_FIELDS = [
    { key: 'date',       label: 'Date',            default: true },
    { key: 'time',       label: 'Time',            default: true },
    { key: 'title',      label: 'Title',           default: true },
    { key: 'content',    label: 'Content Preview',  default: true },
    { key: 'categories', label: 'Categories',       default: true },
    { key: 'tags',       label: 'Tags',             default: true },
    { key: 'places',     label: 'Place / Locations', default: false },
    { key: 'weather',    label: 'Weather',          default: true },
    { key: 'images',     label: 'Image Thumbnails',  default: true }
];

function getEntryListFields() {
    const settings = DB.getSettings();
    if (settings.entryListFields) return settings.entryListFields;
    // Return defaults
    const defaults = {};
    ENTRY_LIST_FIELDS.forEach(f => { defaults[f.key] = f.default; });
    return defaults;
}

function renderEntryFieldsToggles() {
    const container = document.getElementById('entry-fields-list');
    if (!container) return;
    const fields = getEntryListFields();

    container.innerHTML = ENTRY_LIST_FIELDS.map(f => `
        <label class="toggle-label entry-field-toggle">
            <input type="checkbox" ${fields[f.key] !== false ? 'checked' : ''} onchange="toggleEntryListField('${f.key}', this.checked)">
            <span>${f.label}</span>
        </label>
    `).join('');
}

function toggleEntryListField(key, checked) {
    const fields = getEntryListFields();
    fields[key] = checked;
    DB.setSettings({ entryListFields: fields });
}

// ========== Entry Templates ==========

let editingEntryTemplateId = null;

function getEntryTemplates() {
    const settings = DB.getSettings();
    return settings.entryTemplates || [];
}

async function saveEntryTemplates(templates) {
    await DB.setSettings({ entryTemplates: templates });
}

function renderEntryTemplatesList() {
    const container = document.getElementById('entry-templates-list');
    if (!container) return;
    const templates = getEntryTemplates();
    if (templates.length === 0) {
        container.innerHTML = '<div class="no-data">No pre-fill templates yet.</div>';
    } else {
        container.innerHTML = templates.map(t => `
            <div class="settings-list-item">
                <span>${escapeHtml(t.name)}</span>
                <button class="btn-small" onclick="editEntryTemplate('${t.id}')">Edit</button>
            </div>
        `).join('');
    }
    cancelEntryTemplateEdit();
}

function createEntryTemplate() {
    const nameInput = document.getElementById('new-entry-template-name');
    const name = nameInput.value.trim();
    if (!name) {
        alert('Please enter a template name.');
        return;
    }

    const id = 'et_' + generateId();
    const templates = getEntryTemplates();
    templates.push({
        id, name,
        description: '',
        autoDate: true,
        autoTime: false,
        title: '',
        content: '',
        categories: [],
        tags: []
    });
    saveEntryTemplates(templates);
    nameInput.value = '';
    renderEntryTemplatesList();
    editEntryTemplate(id);
}

function editEntryTemplate(id) {
    const templates = getEntryTemplates();
    const tpl = templates.find(t => t.id === id);
    if (!tpl) return;

    editingEntryTemplateId = id;
    const editor = document.getElementById('entry-template-editor');
    editor.style.display = 'block';
    document.getElementById('entry-template-editor-title').textContent = 'Edit: ' + tpl.name;
    document.getElementById('et-name').value = tpl.name;
    document.getElementById('et-description').value = tpl.description || '';
    document.getElementById('et-auto-date').checked = tpl.autoDate !== false;
    document.getElementById('et-auto-time').checked = !!tpl.autoTime;
    document.getElementById('et-title').value = tpl.title || '';
    document.getElementById('et-content').value = tpl.content || '';
    document.getElementById('et-tags').value = (tpl.tags || []).join(', ');

    // Populate category checkboxes
    const catContainer = document.getElementById('et-category-select');
    const categories = DB.getCategories();
    catContainer.innerHTML = categories.map(cat => `
        <div class="multi-select-item">
            <input type="checkbox" id="et-cat-${CSS.escape(cat)}" value="${cat}"${(tpl.categories || []).includes(cat) ? ' checked' : ''}>
            <label for="et-cat-${CSS.escape(cat)}">${cat}</label>
        </div>
    `).join('');
}

async function saveEntryTemplate() {
    if (!editingEntryTemplateId) return;
    const templates = getEntryTemplates();
    const tpl = templates.find(t => t.id === editingEntryTemplateId);
    if (!tpl) return;

    tpl.name = document.getElementById('et-name').value.trim() || tpl.name;
    tpl.description = document.getElementById('et-description').value.trim();
    tpl.autoDate = document.getElementById('et-auto-date').checked;
    tpl.autoTime = document.getElementById('et-auto-time').checked;
    tpl.title = document.getElementById('et-title').value;
    tpl.content = document.getElementById('et-content').value;

    const catCheckboxes = document.querySelectorAll('#et-category-select input[type="checkbox"]:checked');
    tpl.categories = Array.from(catCheckboxes).map(cb => cb.value);

    const tagsStr = document.getElementById('et-tags').value;
    tpl.tags = tagsStr.split(',').map(s => s.trim()).filter(Boolean);

    await saveEntryTemplates(templates);
    alert('Entry template saved.');
    renderEntryTemplatesList();
}

function cancelEntryTemplateEdit() {
    editingEntryTemplateId = null;
    const editor = document.getElementById('entry-template-editor');
    if (editor) editor.style.display = 'none';
}

async function deleteEntryTemplate() {
    if (!editingEntryTemplateId) return;
    if (shouldConfirmDelete() && !confirm('Delete this pre-fill template?')) return;
    const templates = getEntryTemplates().filter(t => t.id !== editingEntryTemplateId);
    await saveEntryTemplates(templates);
    editingEntryTemplateId = null;
    renderEntryTemplatesList();
}

// ========== Report Templates ==========

let editingTemplateId = null;

function getTemplates() {
    const settings = DB.getSettings();
    return settings.reportTemplates || [];
}

async function saveTemplates(templates) {
    await DB.setSettings({ reportTemplates: templates });
}

function renderTemplatesList() {
    const container = document.getElementById('templates-list');
    const templates = getTemplates();
    if (templates.length === 0) {
        container.innerHTML = '<div class="no-data">No templates yet.</div>';
    } else {
        container.innerHTML = templates.map(t => `
            <div class="settings-list-item">
                <span>${escapeHtml(t.name)}</span>
                <button class="btn-small" onclick="editTemplate('${t.id}')">Edit</button>
            </div>
        `).join('');
    }
    // Hide editor when refreshing list
    cancelTemplateEdit();
}

function createTemplate() {
    const nameInput = document.getElementById('new-template-name');
    const name = nameInput.value.trim();
    if (!name) {
        alert('Please enter a template name.');
        return;
    }

    const id = 'tpl_' + generateId();
    const defaultHtml = `<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title><%TITLE%></title>
    <style>
        body { font-family: sans-serif; max-width: 800px; margin: 2rem auto; padding: 0 1rem; }
        h1 { color: #333; }
        .meta { color: #666; margin-bottom: 1rem; }
        .content { line-height: 1.6; }
        .tags { margin-top: 1rem; }
        .tag { display: inline-block; background: #e0f0f0; padding: 2px 8px; border-radius: 4px; margin: 2px; }
    </style>
</head>
<body>
    <h1><%TITLE%></h1>
    <div class="meta">
        <div>Date: <%DATE%> <%TIME%></div>
        <div>Categories: <%CATEGORIES%></div>
        <div>Weather: <%WEATHER%></div>
    </div>
    <div class="content"><%CONTENT%></div>
    <div class="content"><%RICH_CONTENT%></div>
    <div class="tags">Tags: <%TAGS%></div>
    <div class="meta">Places: <%PLACES%></div>
</body>
</html>`;

    const templates = getTemplates();
    templates.push({ id, name, html: defaultHtml });
    saveTemplates(templates);
    nameInput.value = '';
    renderTemplatesList();
    editTemplate(id);
}

function editTemplate(id) {
    const templates = getTemplates();
    const tpl = templates.find(t => t.id === id);
    if (!tpl) return;

    editingTemplateId = id;
    document.getElementById('template-editor-title').textContent = 'Edit: ' + tpl.name;
    document.getElementById('template-editor').value = tpl.html;
    document.getElementById('template-editor-section').style.display = 'block';
}

async function saveTemplate() {
    if (!editingTemplateId) return;
    const templates = getTemplates();
    const tpl = templates.find(t => t.id === editingTemplateId);
    if (!tpl) return;

    tpl.html = document.getElementById('template-editor').value;
    await saveTemplates(templates);
    alert('Template saved.');
}

function cancelTemplateEdit() {
    editingTemplateId = null;
    document.getElementById('template-editor-section').style.display = 'none';
    document.getElementById('template-editor').value = '';
}

async function deleteTemplate() {
    if (!editingTemplateId) return;
    if (shouldConfirmDelete() && !confirm('Delete this template?')) return;
    const templates = getTemplates().filter(t => t.id !== editingTemplateId);
    await saveTemplates(templates);
    editingTemplateId = null;
    renderTemplatesList();
}

function exportTemplate() {
    if (!editingTemplateId) return;
    const templates = getTemplates();
    const tpl = templates.find(t => t.id === editingTemplateId);
    if (!tpl) return;
    downloadFile(tpl.html, tpl.name.replace(/[^a-zA-Z0-9]/g, '_') + '.html', 'text/html');
}

function importTemplate(event) {
    const file = event.target.files[0];
    if (!file) return;
    file.text().then(text => {
        document.getElementById('template-editor').value = text;
    });
    event.target.value = '';
}

// ========== Cleanup Dateless Entries ==========

async function cleanupDatelessEntries() {
    const entries = DB.getEntries();
    let totalDeleted = 0;

    // --- Phase 1: Dateless entries ---
    const dateless = entries.filter(e => !isValidDate(e.date));

    if (dateless.length > 0) {
        const examples = dateless.slice(0, 3).map((e, i) => {
            const title = e.title || '(Untitled)';
            const dateInfo = e.date ? ` [date: "${e.date}"]` : ' [no date]';
            const content = (e.content || '').substring(0, 50);
            const preview = content ? ` — "${content}${e.content && e.content.length > 50 ? '...' : ''}"` : '';
            return `  ${i + 1}. "${title}"${dateInfo}${preview}`;
        }).join('\n');
        const moreText = dateless.length > 3 ? `\n  ...and ${dateless.length - 3} more` : '';

        if (confirm(
            `Found ${dateless.length} ${dateless.length === 1 ? 'entry' : 'entries'} with missing or invalid date.\n\n` +
            `Examples:\n${examples}${moreText}\n\n` +
            `Delete ${dateless.length === 1 ? 'this entry' : 'all ' + dateless.length + ' entries'}? This cannot be undone.`
        )) {
            await DB.deleteEntriesByIds(dateless.map(e => e.id));
            totalDeleted += dateless.length;
        }
    }

    // --- Phase 2: Duplicate entries (same date + same content) ---
    const remaining = DB.getEntries(); // re-read after phase 1
    const seen = new Map(); // key -> first entry id
    const duplicateIds = [];
    const duplicateExamples = [];

    for (const e of remaining) {
        const key = (e.date || '') + '|' + (e.content || '').trim();
        if (seen.has(key)) {
            duplicateIds.push(e.id);
            if (duplicateExamples.length < 3) {
                const title = e.title || '(Untitled)';
                const date = e.date ? formatDate(e.date) : '(no date)';
                const content = (e.content || '').substring(0, 40);
                const preview = content ? ` — "${content}${e.content && e.content.length > 40 ? '...' : ''}"` : '';
                duplicateExamples.push(`  ${duplicateExamples.length + 1}. "${title}" (${date})${preview}`);
            }
        } else {
            seen.set(key, e.id);
        }
    }

    if (duplicateIds.length > 0) {
        const moreText = duplicateIds.length > 3 ? `\n  ...and ${duplicateIds.length - 3} more` : '';
        if (confirm(
            `Found ${duplicateIds.length} duplicate ${duplicateIds.length === 1 ? 'entry' : 'entries'} (same date + same content).\n\n` +
            `Examples:\n${duplicateExamples.join('\n')}${moreText}\n\n` +
            `Delete ${duplicateIds.length === 1 ? 'this duplicate' : 'all ' + duplicateIds.length + ' duplicates'}? The original entries will be kept. This cannot be undone.`
        )) {
            await DB.deleteEntriesByIds(duplicateIds);
            totalDeleted += duplicateIds.length;
        }
    }

    // --- Summary ---
    if (totalDeleted > 0) {
        alert(`Cleanup complete: ${totalDeleted} ${totalDeleted === 1 ? 'entry' : 'entries'} deleted.`);
    } else if (dateless.length === 0 && duplicateIds.length === 0) {
        alert('No issues found. All entries have valid dates and no duplicates.');
    }
}

// ========== About ==========

function openAbout() {
    // Use the web About modal on all platforms (includes Change History)
    const info = APP_INFO;
    document.getElementById('about-modal-name').textContent = info.app_name;
    document.getElementById('about-modal-version').textContent = 'Version ' + info.app_version;
    document.getElementById('about-modal-description').textContent = info.app_description;

    const emailEl = document.getElementById('about-modal-email');
    emailEl.textContent = info.app_email;
    emailEl.href = 'mailto:' + info.app_email;

    const urlEl = document.getElementById('about-modal-url');
    urlEl.textContent = info.app_url;
    urlEl.href = info.app_url;

    const companyEl = document.getElementById('about-modal-company-url');
    companyEl.textContent = 'View on Google Play';
    companyEl.href = info.app_company_url;

    // Populate DB stats
    const statsCard = document.getElementById('about-db-stats');
    const statsContent = document.getElementById('about-db-stats-content');
    const stats = DB.getDBStats();
    if (stats) {
        const fmt = (n) => {
            if (n >= 1048576) return (n / 1048576).toFixed(2) + ' MB';
            if (n >= 1024) return (n / 1024).toFixed(1) + ' KB';
            return n + ' B';
        };
        let html = '';
        html += `<div class="about-row"><span class="about-label">Journal ID</span><span class="about-value">${escapeHtml(stats.journalId)}</span></div>`;
        html += `<div class="about-divider"></div>`;
        html += `<div class="about-row"><span class="about-label">Database Size</span><span class="about-value">${fmt(stats.fileSize)}</span></div>`;
        html += `<div class="about-divider"></div>`;
        html += `<div class="about-row"><span class="about-label">Entries</span><span class="about-value">${stats.tables.entries.rows}</span></div>`;
        html += `<div class="about-divider"></div>`;
        html += `<div class="about-row"><span class="about-label">Images</span><span class="about-value">${stats.tables.images.rows} (${fmt(stats.tables.images.dataSize)})</span></div>`;
        html += `<div class="about-divider"></div>`;
        html += `<div class="about-row"><span class="about-label">Categories</span><span class="about-value">${stats.tables.categories.rows}</span></div>`;
        html += `<div class="about-divider"></div>`;
        html += `<div class="about-row"><span class="about-label">Settings Keys</span><span class="about-value">${stats.tables.settings.rows}</span></div>`;
        if (stats.dateRange) {
            html += `<div class="about-divider"></div>`;
            html += `<div class="about-row"><span class="about-label">Date Range</span><span class="about-value">${stats.dateRange.earliest} — ${stats.dateRange.latest}</span></div>`;
        }
        html += `<div class="about-divider"></div>`;
        html += `<div class="about-row"><span class="about-label">Storage</span><span class="about-value">SQLite (sql.js WASM) → encrypted IndexedDB</span></div>`;
        statsContent.innerHTML = html;
        statsCard.style.display = '';
    } else {
        statsCard.style.display = 'none';
    }

    document.getElementById('about-changelog').style.display = 'none';
    document.getElementById('about-modal').style.display = 'flex';
}

function toggleChangelog() {
    const el = document.getElementById('about-changelog');
    if (el.style.display !== 'none') {
        el.style.display = 'none';
        return;
    }
    let html = '';
    (typeof APP_CHANGELOG !== 'undefined' ? APP_CHANGELOG : []).forEach(release => {
        const title = release.date ? `${release.version} (${release.date})` : release.version;
        html += `<div class="changelog-release"><h4>${escapeHtml(title)}</h4><ul>`;
        release.changes.forEach(c => {
            html += `<li>${escapeHtml(c)}</li>`;
        });
        html += '</ul></div>';
    });
    el.innerHTML = html || '<p>No changelog available.</p>';
    el.style.display = 'block';
}

function closeAboutModal(event) {
    if (!event || event.target.id === 'about-modal') {
        document.getElementById('about-modal').style.display = 'none';
    }
}

