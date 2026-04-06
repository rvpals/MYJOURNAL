/**
 * Main application module: login, navigation, theme, initialization.
 * Supports multiple journal files with individual passwords.
 */

let quillEditor = null;
let currentEntryTags = [];
let currentEntryLocations = [];
let currentEntryImages = [];
let currentEntryPeople = [];

// ========== Journal Selector ==========

function populateJournalSelect() {
    const select = document.getElementById('journal-select');
    const journals = DB.getJournalList();
    const deleteBtn = document.getElementById('btn-delete-journal');

    if (journals.length === 0) {
        select.innerHTML = '<option value="">-- No journals yet --</option>';
        deleteBtn.style.display = 'none';
        // Automatically show new journal input
        showNewJournalInput();
        return;
    }

    select.innerHTML = journals.map(j =>
        `<option value="${j.id}">${j.name}</option>`
    ).join('');

    // Auto-select last opened journal if preference is on
    const autoOpen = Bootstrap.get('auto_open_last_journal') === 'true';
    const lastId = Bootstrap.get('last_journal_id');
    if (autoOpen && lastId && journals.find(j => j.id === lastId)) {
        select.value = lastId;
    }

    deleteBtn.style.display = 'inline-block';
    onJournalSelect();
}

function onJournalSelect() {
    const select = document.getElementById('journal-select');
    const journalId = select.value;
    const errorEl = document.getElementById('login-error');
    errorEl.textContent = '';

    if (!journalId) return;

    const isSetup = Crypto.isSetup(journalId);
    const confirmRow = document.getElementById('login-confirm-row');

    if (isSetup) {
        confirmRow.style.display = 'none';
        document.getElementById('btn-unlock').textContent = 'Unlock';
    } else {
        confirmRow.style.display = 'block';
        document.getElementById('btn-unlock').textContent = 'Set Password';
    }

    updateBiometricLoginButton();

    // Auto-trigger biometric if enabled (skip if native login already handled auth)
    if (isSetup && Bootstrap.get('auto_biometric') === 'true' && isBiometricSupported() &&
        typeof AndroidBridge !== 'undefined' && AndroidBridge.hasCredential(journalId) &&
        !(typeof AndroidBridge.hasNativeLogin === 'function' && AndroidBridge.hasNativeLogin())) {
        setTimeout(() => biometricLogin(), 300);
    }
}

function showNewJournalInput() {
    document.getElementById('new-journal-row').style.display = 'block';
    document.getElementById('new-journal-name').focus();
}

function cancelNewJournal() {
    document.getElementById('new-journal-row').style.display = 'none';
    document.getElementById('new-journal-name').value = '';
}

function createNewJournal() {
    const nameInput = document.getElementById('new-journal-name');
    const name = nameInput.value.trim();
    if (!name) {
        alert('Please enter a journal name.');
        return;
    }

    const id = DB.generateJournalId(name);
    DB.addJournalToList(id, name);
    nameInput.value = '';
    document.getElementById('new-journal-row').style.display = 'none';
    populateJournalSelect();

    // Select the newly created journal
    document.getElementById('journal-select').value = id;
    onJournalSelect();
}

async function deleteJournal() {
    const select = document.getElementById('journal-select');
    const journalId = select.value;
    if (!journalId) return;

    const journal = DB.getJournalList().find(j => j.id === journalId);
    const name = journal ? journal.name : journalId;

    if (shouldConfirmDelete() && !confirm(`Delete journal "${name}"? This will permanently remove all entries in this journal.`)) return;

    // Remove data from IndexedDB, crypto keys, and journal list
    await DB.deleteJournalData(journalId);
    Crypto.removeJournalKeys(journalId);
    DB.removeJournalFromList(journalId);

    populateJournalSelect();
}

// ========== Login ==========

function setLoginMode(mode) {
    // Legacy - kept for compatibility but modes are now auto-detected per journal
}

async function handleLogin() {
    const select = document.getElementById('journal-select');
    const journalId = select.value;
    const password = document.getElementById('input-password').value;
    const errorEl = document.getElementById('login-error');
    errorEl.textContent = '';

    if (!journalId) {
        errorEl.textContent = 'Please select or create a journal first.';
        return;
    }

    if (!password) {
        errorEl.textContent = 'Please enter a password.';
        return;
    }

    const isSetup = Crypto.isSetup(journalId);

    if (!isSetup) {
        // New journal - need password confirmation
        const confirm = document.getElementById('input-password-confirm').value;
        if (!confirm) {
            errorEl.textContent = 'Please confirm your password.';
            return;
        }
        if (password !== confirm) {
            errorEl.textContent = 'Passwords do not match.';
            return;
        }
        try {
            errorEl.textContent = 'Creating journal...';
            await Crypto.setupPassword(password, journalId);
            DB.setPassword(password);
            DB.setJournalId(journalId);
            await DB.loadAll(password, journalId);
            errorEl.textContent = '';
        } catch (e) {
            console.error('Failed to create journal:', e);
            errorEl.textContent = 'Failed to create journal: ' + (e.message || e);
            return;
        }
        enterApp(journalId);
    } else {
        // Existing journal - verify password
        const valid = await Crypto.verifyPassword(password, journalId);
        if (!valid) {
            errorEl.textContent = 'Incorrect password.';
            return;
        }
        DB.setPassword(password);
        DB.setJournalId(journalId);
        try {
            errorEl.textContent = 'Loading journal...';
            await DB.loadAll(password, journalId);
            errorEl.textContent = '';
        } catch (e) {
            console.error('Failed to load journal:', e);
            errorEl.textContent = 'Failed to decrypt data: ' + (e.message || e);
            return;
        }
        enterApp(journalId);
    }
}

function enterApp(journalId) {
    // Remember last opened journal
    Bootstrap.set('last_journal_id', journalId);

    document.getElementById('page-login').classList.remove('active');
    document.getElementById('page-login').style.display = 'none';
    document.getElementById('app-shell').style.display = 'block';

    // Show journal name in nav
    const journal = DB.getJournalList().find(j => j.id === journalId);
    document.getElementById('nav-journal-name').textContent = journal ? `- ${journal.name}` : '';

    // Apply saved theme
    const settings = DB.getSettings();
    if (settings.theme) {
        document.documentElement.setAttribute('data-theme', settings.theme);
        const sel = document.getElementById('theme-select');
        if (sel) sel.value = settings.theme;
    }

    initQuill();
    navigateTo('dashboard');
}

function lockApp() {
    DB.setPassword(null);
    DB.setJournalId(null);

    // If native login is available, return to it
    if (window.AndroidBridge && typeof AndroidBridge.hasNativeLogin === 'function' && AndroidBridge.hasNativeLogin()) {
        AndroidBridge.returnToLogin();
        return;
    }

    document.getElementById('app-shell').style.display = 'none';
    document.getElementById('page-login').style.display = 'flex';
    document.getElementById('page-login').classList.add('active');
    document.getElementById('input-password').value = '';
    document.getElementById('input-password-confirm').value = '';
    document.getElementById('login-error').textContent = '';
    populateJournalSelect();
}

// ========== Navigation ==========

let previousPage = 'dashboard';
let currentPage = 'dashboard';

function navigateTo(pageId) {
    // Warn if leaving entry form with unsaved content
    if (currentPage === 'entry-form' && pageId !== 'entry-form' && shouldWarnUnsaved()) {
        if (isEntryFormDirty()) {
            if (!confirm('You have unsaved content in the entry form. Leave without saving?')) {
                return false;
            }
        }
    }

    if (currentPage !== pageId) {
        previousPage = currentPage;
    }
    currentPage = pageId;

    document.querySelectorAll('#app-shell .page').forEach(p => p.classList.remove('active'));
    document.getElementById('page-' + pageId).classList.add('active');

    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    const activeLink = document.querySelector(`.nav-link[data-page="${pageId}"]`);
    if (activeLink) activeLink.classList.add('active');

    // Highlight menu items
    document.querySelectorAll('.nav-menu-item').forEach(l => l.classList.remove('active'));
    const activeMenu = document.querySelector(`.nav-menu-item[data-page="${pageId}"]`);
    if (activeMenu) activeMenu.classList.add('active');

    // Highlight brand button when on dashboard
    const brandBtn = document.querySelector('.nav-brand-btn');
    if (brandBtn) brandBtn.classList.toggle('nav-brand-active', pageId === 'dashboard');

    const navLinks = document.querySelector('.nav-links');
    if (navLinks) navLinks.classList.remove('open');

    // Redirect custom-views to settings > templates tab
    if (pageId === 'custom-views') {
        pageId = 'settings';
        currentPage = pageId;
        document.querySelectorAll('#app-shell .page').forEach(p => p.classList.remove('active'));
        document.getElementById('page-settings').classList.add('active');
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
        const settingsLink = document.querySelector('.nav-link[data-page="settings"]');
        if (settingsLink) settingsLink.classList.add('active');
        refreshSettings();
        initCollapsibleSettings();
        switchSettingsTab('templates');
        refreshCustomViewsPage();
        return false;
    }

    if (pageId === 'dashboard') { refreshDashboard(); if (typeof applyWallpaper === 'function') applyWallpaper(); }
    if (pageId === 'entry-list') refreshEntryList();
    if (pageId === 'entry-form') prepareEntryForm();
    if (pageId === 'reports') prepareReports();
    if (pageId === 'explorer') initExplorer();
    if (pageId === 'calendar') initCalendar();
    if (pageId === 'settings') { refreshSettings(); initCollapsibleSettings(); }

    return false;
}

function navigateBack() {
    navigateTo(previousPage || 'dashboard');
}

// Called by Android hardware back button
function handleAndroidBack() {
    // Close modals first
    const printModal = document.getElementById('print-modal');
    if (printModal && printModal.style.display !== 'none') {
        closePrintModal();
        return 'handled';
    }
    const aboutModal = document.getElementById('about-modal');
    if (aboutModal && aboutModal.style.display !== 'none') {
        closeAboutModal();
        return 'handled';
    }
    // Close mobile nav if open
    const navLinks = document.querySelector('.nav-links');
    if (navLinks && navLinks.classList.contains('open')) {
        navLinks.classList.remove('open');
        return 'handled';
    }
    // If on login, signal exit
    if (typeof currentPage === 'undefined' || currentPage === 'login') {
        return 'exit';
    }
    // If on dashboard, exit app
    if (currentPage === 'dashboard') {
        return 'exit';
    }
    // Otherwise navigate back
    navigateBack();
    return 'navigated';
}

function toggleMobileNav() {
    document.querySelector('.nav-links').classList.toggle('open');
}

function toggleNavMenu() {
    const dd = document.getElementById('nav-menu-dropdown');
    if (!dd) return;
    dd.classList.toggle('open');
}

function closeNavMenu() {
    const dd = document.getElementById('nav-menu-dropdown');
    if (dd) dd.classList.remove('open');
}

function navMenuGo(page) {
    event.preventDefault();
    closeNavMenu();
    navigateTo(page);
}

// Close menu when clicking outside
document.addEventListener('click', function(e) {
    const wrapper = document.querySelector('.nav-menu-wrapper');
    if (wrapper && !wrapper.contains(e.target)) {
        closeNavMenu();
    }
});

// ========== Theme ==========

const THEMES = ['light', 'dark', 'ocean', 'midnight', 'forest', 'amethyst', 'aurora', 'lavender', 'frost', 'navy', 'sunflower', 'meadow'];

function setTheme(theme) {
    if (!THEMES.includes(theme)) theme = 'light';
    document.documentElement.setAttribute('data-theme', theme);
    const sel = document.getElementById('theme-select');
    if (sel) sel.value = theme;
    DB.setSettings({ theme: theme });
    renderThemePreview();
}

function cycleTheme() {
    const current = document.documentElement.getAttribute('data-theme') || 'light';
    const idx = THEMES.indexOf(current);
    const next = THEMES[(idx + 1) % THEMES.length];
    setTheme(next);
}

function renderThemePreview() {
    const el = document.getElementById('theme-preview');
    if (!el) return;
    const style = getComputedStyle(document.documentElement);
    const colors = ['--bg-primary', '--bg-card', '--accent', '--text-primary', '--nav-bg'];
    el.innerHTML = colors.map(c =>
        `<span class="theme-swatch" style="background:${style.getPropertyValue(c).trim()}" title="${c}"></span>`
    ).join('');
}

// ========== Quill Editor ==========

function initQuill() {
    if (quillEditor) return;
    quillEditor = new Quill('#rich-content-editor', {
        theme: 'snow',
        placeholder: 'Paste or type rich content here...',
        modules: {
            toolbar: [
                ['bold', 'italic', 'underline', 'strike'],
                [{ 'header': [1, 2, 3, false] }],
                [{ 'list': 'ordered' }, { 'list': 'bullet' }],
                [{ 'color': [] }, { 'background': [] }],
                ['link'],
                ['clean']
            ]
        }
    });
}

// ========== Entry Form Dirty Check ==========

let entryFormSaved = false;

function isEntryFormDirty() {
    if (entryFormSaved) return false;
    const content = document.getElementById('entry-content').value.trim();
    const richContent = quillEditor ? quillEditor.getText().trim() : '';
    return content.length > 0 || richContent.length > 0;
}

// ========== Entry Form Helpers ==========

function fillCurrentTime() {
    const now = new Date();
    const h = String(now.getHours()).padStart(2, '0');
    const m = String(now.getMinutes()).padStart(2, '0');
    document.getElementById('entry-time').value = `${h}:${m}`;
}

function prepareEntryForm(entry) {
    entryFormSaved = false;
    const form = document.getElementById('entry-form');
    form.reset();

    const today = toLocalDateStr(new Date());
    document.getElementById('entry-date').value = today;
    document.getElementById('entry-id').value = '';
    document.getElementById('entry-form-title').textContent = 'New Entry';
    document.getElementById('btn-delete-entry').style.display = 'none';

    currentEntryTags = [];
    currentEntryLocations = [];
    currentEntryImages = [];
    currentEntryPeople = [];
    currentEntryWeather = null;
    renderEntryImagesPreview();
    renderTags();
    renderLocations();
    populateCategorySelect();
    populatePeopleSelect();
    updateTagSuggestions();
    updatePlaceNameSuggestions();
    document.getElementById('entry-place-name').value = '';
    document.getElementById('location-search-input').value = '';
    hideLocationSearchResults();
    document.getElementById('entry-weather').value = '';
    hideWeatherDetail();

    if (quillEditor) quillEditor.setContents([]);

    // Show/hide template bar (only for new entries)
    populateEntryTemplateSelect();

    if (entry) {
        document.getElementById('entry-id').value = entry.id;
        document.getElementById('entry-date').value = entry.date || today;
        document.getElementById('entry-time').value = entry.time || '';
        document.getElementById('entry-title').value = entry.title || '';
        document.getElementById('entry-content').value = entry.content || '';
        document.getElementById('entry-form-title').textContent = 'Edit Entry';
        document.getElementById('btn-delete-entry').style.display = 'inline-block';

        if (entry.richContent && quillEditor) {
            quillEditor.root.innerHTML = entry.richContent;
        }

        currentEntryTags = [...(entry.tags || [])];
        currentEntryImages = (entry.images || []).map(img => ({ ...img }));
        renderEntryImagesPreview();
        currentEntryLocations = migrateLocations(entry);
        document.getElementById('entry-place-name').value = entry.placeName || '';
        renderTags();
        renderLocations();
        hideLocationSearchResults();
        setEntryWeatherFromData(entry.weather || null);

        (entry.categories || []).forEach(cat => {
            const cb = document.querySelector(`#category-select input[value="${CSS.escape(cat)}"]`);
            if (cb) cb.checked = true;
        });

        currentEntryPeople = [...(entry.people || [])];
        renderSelectedPeople();

        // Hide template bar when editing
        document.getElementById('entry-template-bar').style.display = 'none';
    }
}

function populateEntryTemplateSelect() {
    const bar = document.getElementById('entry-template-bar');
    const select = document.getElementById('entry-template-select');
    const templates = typeof getEntryTemplates === 'function' ? getEntryTemplates() : [];

    if (templates.length === 0) {
        bar.style.display = 'none';
        return;
    }

    bar.style.display = 'flex';
    select.innerHTML = '<option value="">-- Pre-fill Template --</option>' +
        templates.map(t => {
            const label = t.description ? `${t.name} - ${t.description}` : t.name;
            return `<option value="${t.id}">${escapeHtml(label)}</option>`;
        }).join('');
}

function loadEntryTemplate() {
    const select = document.getElementById('entry-template-select');
    const id = select.value;
    if (!id) return;

    const templates = typeof getEntryTemplates === 'function' ? getEntryTemplates() : [];
    const tpl = templates.find(t => t.id === id);
    if (!tpl) return;

    const today = toLocalDateStr(new Date());

    // Apply template values
    if (tpl.autoDate !== false) {
        document.getElementById('entry-date').value = today;
    }
    if (tpl.autoTime) {
        fillCurrentTime();
    }
    if (tpl.title) {
        document.getElementById('entry-title').value = tpl.title;
    }
    if (tpl.content) {
        document.getElementById('entry-content').value = tpl.content;
    }

    // Set categories
    populateCategorySelect();
    (tpl.categories || []).forEach(cat => {
        const cb = document.querySelector(`#category-select input[value="${CSS.escape(cat)}"]`);
        if (cb) cb.checked = true;
    });

    // Set tags
    currentEntryTags = [...(tpl.tags || [])];
    renderTags();

    document.getElementById('entry-form-title').textContent = 'New Entry - ' + tpl.name;

    // Reset select
    select.value = '';
}

function populateCategorySelect() {
    const container = document.getElementById('category-select');
    const categories = DB.getCategoriesWithDesc();
    container.innerHTML = categories.map(catObj => {
        const cat = catObj.name;
        const desc = catObj.description || '';
        return `<div class="multi-select-item">
            <input type="checkbox" id="cat-${CSS.escape(cat)}" value="${cat}" data-desc="${escapeHtml(desc)}" onchange="updateCategoryHints()">
            <label for="cat-${CSS.escape(cat)}" title="${escapeHtml(desc)}">${getIconHtml('category', cat)}${cat}</label>
        </div>`;
    }).join('');
    updateCategoryHints();
}

function updateCategoryHints() {
    const hint = document.getElementById('category-hint');
    if (!hint) return;
    const checked = document.querySelectorAll('#category-select input[type="checkbox"]:checked');
    const hints = [];
    checked.forEach(cb => {
        const desc = cb.dataset.desc;
        if (desc) hints.push(`<strong>${escapeHtml(cb.value)}</strong>: ${escapeHtml(desc)}`);
    });
    if (hints.length) {
        hint.innerHTML = hints.join('<br>');
        hint.style.display = 'block';
    } else {
        hint.style.display = 'none';
    }
}

function renderTags() {
    const list = document.getElementById('tag-list');
    const tagDescs = DB.getTagDescriptions();
    list.innerHTML = currentEntryTags.map(tag => {
        const desc = tagDescs[tag] || '';
        return `<span class="tag-item" title="${escapeHtml(desc)}">${getIconHtml('tag', tag)}${escapeHtml(tag)}<span class="tag-remove" onclick="removeTag('${escapeHtml(tag)}')">&times;</span></span>`;
    }).join('');
    updateTagHints();
}

function updateTagHints() {
    const hint = document.getElementById('tag-hint');
    if (!hint) return;
    const tagDescs = DB.getTagDescriptions();
    const hints = [];
    for (const tag of currentEntryTags) {
        const desc = tagDescs[tag];
        if (desc) hints.push(`<strong>${escapeHtml(tag)}</strong>: ${escapeHtml(desc)}`);
    }
    if (hints.length) {
        hint.innerHTML = hints.join('<br>');
        hint.style.display = 'block';
    } else {
        hint.style.display = 'none';
    }
}

function removeTag(tag) {
    currentEntryTags = currentEntryTags.filter(t => t !== tag);
    renderTags(); // renderTags calls updateTagHints
}

function updateTagSuggestions() {
    // No longer uses datalist; dropdown is driven by input event
}

function showTagDropdown() {
    const input = document.getElementById('entry-tags-input');
    const dropdown = document.getElementById('tag-dropdown');
    const query = input.value.trim().toLowerCase();

    if (!query) {
        dropdown.style.display = 'none';
        return;
    }

    const allTags = DB.getAllTags();
    const matches = allTags.filter(t =>
        t.toLowerCase().includes(query) && !currentEntryTags.includes(t)
    );

    if (matches.length === 0) {
        dropdown.style.display = 'none';
        return;
    }

    const tagDescs = DB.getTagDescriptions();
    dropdown.innerHTML = matches.slice(0, 10).map(t => {
        const desc = tagDescs[t] ? `<span class="tag-dropdown-desc">${escapeHtml(tagDescs[t])}</span>` : '';
        return `<div class="tag-dropdown-item" onmousedown="selectTagFromDropdown('${escapeHtml(t)}')">${getIconHtml('tag', t)}${escapeHtml(t)}${desc}</div>`;
    }).join('');
    dropdown.style.display = 'block';
}

function hideTagDropdown() {
    const dropdown = document.getElementById('tag-dropdown');
    if (dropdown) dropdown.style.display = 'none';
}

function selectTagFromDropdown(tag) {
    if (tag && !currentEntryTags.includes(tag)) {
        currentEntryTags.push(tag);
        renderTags();
    }
    const input = document.getElementById('entry-tags-input');
    input.value = '';
    hideTagDropdown();
}

function updatePlaceNameSuggestions() {
    const dl = document.getElementById('place-name-suggestions');
    const names = DB.getAllPlaceNames();
    dl.innerHTML = names.map(n => `<option value="${escapeHtml(n)}">`).join('');
}

// ========== People Selector ==========

function populatePeopleSelect() {
    const container = document.getElementById('people-select');
    if (!container) return;
    const people = DB.getPeople();
    container.innerHTML = people.map(p => {
        const fullName = p.firstName + ' ' + p.lastName;
        const desc = p.description || '';
        const isSelected = currentEntryPeople.some(ep => ep.firstName === p.firstName && ep.lastName === p.lastName);
        const icon = getIconHtml('person', fullName);
        return `<div class="multi-select-item">
            <input type="checkbox" id="person-${CSS.escape(fullName)}" value="${escapeHtml(fullName)}" data-first="${escapeHtml(p.firstName)}" data-last="${escapeHtml(p.lastName)}" data-desc="${escapeHtml(desc)}" ${isSelected ? 'checked' : ''} onchange="onPersonToggle(this)">
            <label for="person-${CSS.escape(fullName)}" title="${escapeHtml(desc)}">${icon}${escapeHtml(fullName)}</label>
        </div>`;
    }).join('');
    if (people.length === 0) {
        container.innerHTML = '<p class="settings-hint">No people yet. Add people in Settings &gt; Edit Metadata.</p>';
    }
    updatePeopleHints();
}

function onPersonToggle(cb) {
    const first = cb.dataset.first;
    const last = cb.dataset.last;
    if (cb.checked) {
        if (!currentEntryPeople.some(p => p.firstName === first && p.lastName === last)) {
            currentEntryPeople.push({ firstName: first, lastName: last });
        }
    } else {
        currentEntryPeople = currentEntryPeople.filter(p => !(p.firstName === first && p.lastName === last));
    }
    renderSelectedPeople();
    updatePeopleHints();
}

function updatePeopleHints() {
    const hint = document.getElementById('people-hint');
    if (!hint) return;
    const checked = document.querySelectorAll('#people-select input[type="checkbox"]:checked');
    const hints = [];
    checked.forEach(cb => {
        const desc = cb.dataset.desc;
        if (desc) hints.push(`<strong>${escapeHtml(cb.value)}</strong>: ${escapeHtml(desc)}`);
    });
    if (hints.length) {
        hint.innerHTML = hints.join('<br>');
        hint.style.display = 'block';
    } else {
        hint.style.display = 'none';
    }
}

function renderSelectedPeople() {
    const list = document.getElementById('selected-people-list');
    if (!list) return;
    if (currentEntryPeople.length === 0) {
        list.innerHTML = '';
        return;
    }
    list.innerHTML = currentEntryPeople.map(p => {
        const name = p.firstName + ' ' + p.lastName;
        const icon = getIconHtml('person', name);
        return `<span class="tag-item people-tag">${icon || '&#x1F464; '}${escapeHtml(name)}<span class="tag-remove" onclick="removeEntryPerson('${escapeHtml(p.firstName)}', '${escapeHtml(p.lastName)}')">&times;</span></span>`;
    }).join('');
}

function removeEntryPerson(first, last) {
    currentEntryPeople = currentEntryPeople.filter(p => !(p.firstName === first && p.lastName === last));
    renderSelectedPeople();
    // Uncheck in selector
    const container = document.getElementById('people-select');
    if (container) {
        container.querySelectorAll('input[type="checkbox"]').forEach(cb => {
            if (cb.dataset.first === first && cb.dataset.last === last) cb.checked = false;
        });
    }
    updatePeopleHints();
}

// ========== Quick Create (from entry form) ==========

function toggleQuickCreate(type) {
    const panel = document.getElementById('quick-create-' + type);
    if (!panel) return;
    const visible = panel.style.display !== 'none';
    panel.style.display = visible ? 'none' : 'flex';
    if (!visible) {
        // Focus the first input
        const input = panel.querySelector('input[type="text"]');
        if (input) { input.value = ''; input.focus(); }
    }
}

async function quickAddCategory() {
    const input = document.getElementById('quick-new-category');
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
    document.getElementById('quick-create-category').style.display = 'none';
    populateCategorySelect();
    // Auto-select the new category
    const cb = document.querySelector(`#category-select input[value="${CSS.escape(name)}"]`);
    if (cb) cb.checked = true;
}

async function quickAddTag() {
    const input = document.getElementById('quick-new-tag');
    const name = input.value.trim();
    if (!name) return;

    if (currentEntryTags.includes(name)) {
        alert('Tag already added to this entry.');
        return;
    }

    // Add to the entry's tag list
    currentEntryTags.push(name);
    renderTags();
    input.value = '';
    document.getElementById('quick-create-tag').style.display = 'none';
}

async function quickAddPerson() {
    const firstInput = document.getElementById('quick-new-person-first');
    const lastInput = document.getElementById('quick-new-person-last');
    const descInput = document.getElementById('quick-new-person-desc');
    const firstName = firstInput.value.trim();
    const lastName = lastInput.value.trim();
    const description = descInput.value.trim();

    if (!firstName || !lastName) {
        alert('First name and last name are required.');
        return;
    }

    const people = DB.getPeople();
    const exists = people.some(p => p.firstName === firstName && p.lastName === lastName);
    if (!exists) {
        await DB.addPerson(firstName, lastName, description);
    }

    // Auto-select the person for this entry
    if (!currentEntryPeople.some(p => p.firstName === firstName && p.lastName === lastName)) {
        currentEntryPeople.push({ firstName, lastName });
    }

    firstInput.value = '';
    lastInput.value = '';
    descInput.value = '';
    document.getElementById('quick-create-person').style.display = 'none';
    populatePeopleSelect();
    renderSelectedPeople();
}

// ========== Locations ==========

function migrateLocations(entry) {
    // If entry already has new-format locations, use them
    if (entry.locations && entry.locations.length > 0) {
        return [...entry.locations];
    }
    // Migrate old places format to new locations format
    if (entry.places && entry.places.length > 0) {
        return entry.places.map(p => ({
            address: p.address || p.name || '',
            lat: p.coords ? p.coords.lat : null,
            lng: p.coords ? p.coords.lng : null
        }));
    }
    return [];
}

function renderLocations() {
    const list = document.getElementById('locations-list');
    list.innerHTML = currentEntryLocations.map((loc, i) => {
        const hasCoords = loc.lat != null && loc.lng != null;
        const coordsText = hasCoords ? `<div class="location-coords">${loc.lat.toFixed(6)}, ${loc.lng.toFixed(6)}</div>` : '';
        const mapBtn = hasCoords
            ? `<a href="https://www.google.com/maps?q=${loc.lat},${loc.lng}" target="_blank" class="btn-small btn-map-link" title="Open in Google Maps">&#128205; Map</a>`
            : (loc.address
                ? `<a href="https://www.google.com/maps/search/${encodeURIComponent(loc.address)}" target="_blank" class="btn-small btn-map-link" title="Search in Google Maps">&#128205; Map</a>`
                : '');
        return `
        <div class="location-item">
            <div class="location-details">
                <div class="location-address">${escapeHtml(loc.address)}</div>
                ${coordsText}
            </div>
            <div class="location-actions">
                ${mapBtn}
                <span class="location-remove" onclick="removeLocation(${i})">&times;</span>
            </div>
        </div>`;
    }).join('');
}

function removeLocation(index) {
    currentEntryLocations.splice(index, 1);
    renderLocations();
}

function addLocationFromResult(address, lat, lng) {
    currentEntryLocations.push({ address, lat, lng });
    renderLocations();
    document.getElementById('location-search-input').value = '';
    hideLocationSearchResults();
}

let locationSearchTimeout = null;

// ========== Geocoding Providers ==========

function geocodeSearch(query) {
    const provider = typeof getGeocodingProvider === 'function' ? getGeocodingProvider() : 'photon';

    if (provider === 'nominatim') {
        return fetch('https://nominatim.openstreetmap.org/search?format=json&addressdetails=1&limit=5&q=' + encodeURIComponent(query))
            .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
            .then(results => results.map(r => ({
                displayName: r.display_name,
                lat: parseFloat(r.lat).toFixed(6),
                lng: parseFloat(r.lon).toFixed(6)
            })));
    }

    if (provider === 'google') {
        const key = typeof getGeocodingApiKey === 'function' ? getGeocodingApiKey() : '';
        if (!key) return Promise.reject(new Error('Google API key not set. Configure it in Settings > Preferences.'));
        return fetch('https://maps.googleapis.com/maps/api/geocode/json?address=' + encodeURIComponent(query) + '&key=' + encodeURIComponent(key))
            .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
            .then(data => {
                if (data.status !== 'OK') throw new Error(data.status + ': ' + (data.error_message || 'No results'));
                return (data.results || []).slice(0, 5).map(r => ({
                    displayName: r.formatted_address,
                    lat: parseFloat(r.geometry.location.lat).toFixed(6),
                    lng: parseFloat(r.geometry.location.lng).toFixed(6)
                }));
            });
    }

    // Default: Photon (Komoot)
    return fetch('https://photon.komoot.io/api/?q=' + encodeURIComponent(query) + '&limit=5&lang=en')
        .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.json(); })
        .then(data => (data.features || []).map(f => {
            const props = f.properties || {};
            const coords = f.geometry ? f.geometry.coordinates : null;
            const parts = [props.housenumber, props.street, props.name, props.city, props.state, props.country].filter(Boolean);
            return {
                displayName: parts.join(', ') || props.label || 'Unknown',
                lat: coords ? parseFloat(coords[1]).toFixed(6) : '',
                lng: coords ? parseFloat(coords[0]).toFixed(6) : ''
            };
        }));
}

function reverseGeocode(lat, lng) {
    const provider = typeof getGeocodingProvider === 'function' ? getGeocodingProvider() : 'photon';

    if (provider === 'nominatim') {
        return fetch(`https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}&addressdetails=1`)
            .then(r => r.json())
            .then(data => data.display_name || `${lat}, ${lng}`);
    }

    if (provider === 'google') {
        const key = typeof getGeocodingApiKey === 'function' ? getGeocodingApiKey() : '';
        if (!key) return Promise.resolve(`${lat}, ${lng}`);
        return fetch(`https://maps.googleapis.com/maps/api/geocode/json?latlng=${lat},${lng}&key=${encodeURIComponent(key)}`)
            .then(r => r.json())
            .then(data => (data.results && data.results[0]) ? data.results[0].formatted_address : `${lat}, ${lng}`);
    }

    // Default: Photon
    return fetch(`https://photon.komoot.io/reverse?lat=${lat}&lon=${lng}&limit=1&lang=en`)
        .then(r => r.json())
        .then(data => {
            const f = (data.features || [])[0];
            if (f && f.properties) {
                const p = f.properties;
                const parts = [p.housenumber, p.street, p.name, p.city, p.state, p.country].filter(Boolean);
                if (parts.length > 0) return parts.join(', ');
            }
            return `${lat}, ${lng}`;
        });
}

function searchLocation() {
    const query = document.getElementById('location-search-input').value.trim();
    if (!query) return;

    const resultsDiv = document.getElementById('location-search-results');
    resultsDiv.style.display = 'block';
    resultsDiv.innerHTML = '<div class="location-searching">Searching...</div>';

    clearTimeout(locationSearchTimeout);
    locationSearchTimeout = setTimeout(() => {
        geocodeSearch(query)
            .then(results => {
                if (results.length === 0) {
                    resultsDiv.innerHTML = '<div class="location-no-results">No results found. Try a more specific address.</div>';
                    return;
                }
                resultsDiv.innerHTML = results.map(r => {
                    const safeAddr = escapeHtml(r.displayName).replace(/'/g, "\\'");
                    return `<div class="location-result-item" onclick="addLocationFromResult('${safeAddr}', ${r.lat}, ${r.lng})">
                        <div class="location-result-name">${escapeHtml(r.displayName)}</div>
                        <div class="location-result-coords">${r.lat}, ${r.lng}</div>
                    </div>`;
                }).join('');
            })
            .catch(err => {
                console.error('Location search failed:', err);
                resultsDiv.innerHTML = `<div class="location-no-results">${escapeHtml(err.message || 'Search failed. Check your internet connection.')}</div>`;
            });
    }, 300);
}

function quickFillGpsWeather() {
    if (!navigator.geolocation) {
        alert('Geolocation is not supported by this browser.');
        return;
    }
    const btn = document.querySelector('.btn-quick-gps');
    if (btn) { btn.disabled = true; btn.textContent = 'Locating...'; }

    navigator.geolocation.getCurrentPosition(
        async (pos) => {
            const lat = parseFloat(pos.coords.latitude.toFixed(6));
            const lng = parseFloat(pos.coords.longitude.toFixed(6));

            // Add GPS location
            reverseGeocode(lat, lng)
                .then(address => addLocationFromResult(address, lat, lng))
                .catch(() => addLocationFromResult(`${lat}, ${lng}`, lat, lng));

            // Fetch weather from GPS coords
            try {
                if (btn) btn.textContent = 'Fetching weather...';
                const unit = Weather.getTempUnit();
                const w = await Weather.fetchCurrent(lat, lng, unit);
                w.locationName = `${lat}, ${lng}`;
                try {
                    const addr = await reverseGeocode(lat, lng);
                    if (addr) w.locationName = addr;
                } catch {}
                w.fetchedAt = new Date().toISOString();
                currentEntryWeather = w;
                document.getElementById('entry-weather').value = Weather.formatWeather(w);
                showWeatherDetail(w);
            } catch (err) {
                console.error('Weather fetch failed:', err);
            }

            if (btn) { btn.disabled = false; btn.innerHTML = '\u{1F4CD} GPS + Weather'; }
        },
        (err) => {
            alert('Could not get location: ' + err.message);
            if (btn) { btn.disabled = false; btn.innerHTML = '\u{1F4CD} GPS + Weather'; }
        },
        { enableHighAccuracy: true, timeout: 15000 }
    );
}

function getCurrentLocationNew() {
    if (!navigator.geolocation) {
        alert('Geolocation is not supported by this browser.');
        return;
    }
    navigator.geolocation.getCurrentPosition(
        (pos) => {
            const lat = parseFloat(pos.coords.latitude.toFixed(6));
            const lng = parseFloat(pos.coords.longitude.toFixed(6));
            reverseGeocode(lat, lng)
                .then(address => addLocationFromResult(address, lat, lng))
                .catch(() => addLocationFromResult(`${lat}, ${lng}`, lat, lng));
        },
        (err) => {
            alert('Could not get location: ' + err.message);
        }
    );
}

function hideLocationSearchResults() {
    const el = document.getElementById('location-search-results');
    if (el) el.style.display = 'none';
}

// ========== Weather Import ==========

let currentEntryWeather = null;

async function importWeather() {
    const loc = Weather.getLocation();
    if (!loc) {
        alert('No weather location set. Go to Settings to configure your location.');
        return;
    }

    const input = document.getElementById('entry-weather');
    input.value = 'Fetching weather...';

    try {
        const unit = Weather.getTempUnit();
        const w = await Weather.fetchCurrent(loc.lat, loc.lng, unit);
        w.locationName = loc.name;
        w.fetchedAt = new Date().toISOString();
        currentEntryWeather = w;
        input.value = Weather.formatWeather(w);
        showWeatherDetail(w);
    } catch (err) {
        input.value = 'Failed to fetch weather';
        currentEntryWeather = null;
        hideWeatherDetail();
        alert('Weather fetch failed: ' + err.message);
    }
}

function showWeatherDetail(w) {
    const el = document.getElementById('weather-detail');
    el.style.display = 'block';
    el.innerHTML = `<span class="weather-detail-loc">${escapeHtml(w.locationName)}</span>` +
        `<span class="weather-detail-clear" onclick="clearEntryWeather()">&times;</span>`;
}

function hideWeatherDetail() {
    document.getElementById('weather-detail').style.display = 'none';
    document.getElementById('weather-detail').innerHTML = '';
}

function clearEntryWeather() {
    currentEntryWeather = null;
    document.getElementById('entry-weather').value = '';
    hideWeatherDetail();
}

function setEntryWeatherFromData(weather) {
    if (!weather) {
        currentEntryWeather = null;
        document.getElementById('entry-weather').value = '';
        hideWeatherDetail();
        return;
    }
    currentEntryWeather = weather;
    document.getElementById('entry-weather').value = Weather.formatWeather(weather);
    showWeatherDetail(weather);
}

// ========== Image Attachments ==========

function onImagesSelected(event) {
    const files = Array.from(event.target.files);
    if (!files.length) return;
    let processed = 0;
    files.forEach(file => {
        const reader = new FileReader();
        reader.onload = function(e) {
            const img = new Image();
            img.onload = function() {
                const full = resizeImage(img, 1920, 0.7);
                const thumb = resizeImage(img, 150, 0.5);
                currentEntryImages.push({
                    id: generateId(),
                    name: file.name,
                    data: full,
                    thumb: thumb
                });
                processed++;
                if (processed === files.length) renderEntryImagesPreview();
            };
            img.src = e.target.result;
        };
        reader.readAsDataURL(file);
    });
    event.target.value = '';
}

function resizeImage(img, maxDim, quality) {
    const canvas = document.createElement('canvas');
    let w = img.width, h = img.height;
    if (w > maxDim || h > maxDim) {
        if (w > h) { h = Math.round(h * maxDim / w); w = maxDim; }
        else { w = Math.round(w * maxDim / h); h = maxDim; }
    }
    canvas.width = w;
    canvas.height = h;
    canvas.getContext('2d').drawImage(img, 0, 0, w, h);
    return canvas.toDataURL('image/jpeg', quality);
}

function removeEntryImage(index) {
    currentEntryImages.splice(index, 1);
    renderEntryImagesPreview();
}

function renderEntryImagesPreview() {
    const container = document.getElementById('entry-images-preview');
    if (!container) return;
    if (currentEntryImages.length === 0) {
        container.innerHTML = '';
        return;
    }
    container.innerHTML = currentEntryImages.map((img, i) =>
        `<div class="entry-img-thumb-wrap">
            <img class="entry-img-thumb" src="${img.thumb}" alt="${escapeHtml(img.name || 'image')}">
            <button type="button" class="entry-img-remove" onclick="removeEntryImage(${i})" title="Remove">&times;</button>
        </div>`
    ).join('');
}

function cancelEntry() {
    navigateBack();
}

// ========== Biometric Authentication (Android only) ==========

function isBiometricSupported() {
    return window.AndroidBridge && typeof AndroidBridge.isBiometricAvailable === 'function'
        && AndroidBridge.isBiometricAvailable();
}

function updateBiometricLoginButton() {
    const btn = document.getElementById('btn-biometric');
    if (!btn) return;

    const select = document.getElementById('journal-select');
    const journalId = select ? select.value : '';

    if (!journalId || !isBiometricSupported()) {
        btn.style.display = 'none';
        return;
    }

    // Only show if this journal has a saved credential and is already set up
    if (Crypto.isSetup(journalId) && AndroidBridge.hasCredential(journalId)) {
        btn.style.display = '';
    } else {
        btn.style.display = 'none';
    }
}

function biometricLogin() {
    const select = document.getElementById('journal-select');
    const journalId = select ? select.value : '';
    const errorEl = document.getElementById('login-error');
    errorEl.textContent = '';

    if (!journalId || !isBiometricSupported() || !AndroidBridge.hasCredential(journalId)) {
        errorEl.textContent = 'Biometric login not available for this journal.';
        return;
    }

    // Trigger native biometric prompt — callback is onBiometricResult
    AndroidBridge.authenticate('onBiometricResult');
}

// Called from Android native code after biometric prompt completes
async function onBiometricResult(success) {
    const select = document.getElementById('journal-select');
    const journalId = select ? select.value : '';
    const errorEl = document.getElementById('login-error');

    if (!success) {
        errorEl.textContent = 'Biometric authentication failed or cancelled.';
        return;
    }

    const password = AndroidBridge.getCredential(journalId);
    if (!password) {
        errorEl.textContent = 'No saved credential found. Please use password.';
        return;
    }

    // Verify the stored password is still valid
    const valid = await Crypto.verifyPassword(password, journalId);
    if (!valid) {
        errorEl.textContent = 'Saved password is no longer valid. Please log in with password.';
        AndroidBridge.removeCredential(journalId);
        updateBiometricLoginButton();
        return;
    }

    DB.setPassword(password);
    DB.setJournalId(journalId);
    try {
        await DB.loadAll(password, journalId);
    } catch {
        errorEl.textContent = 'Failed to decrypt data.';
        return;
    }
    enterApp(journalId);
}

function refreshBiometricToggle() {
    const row = document.getElementById('biometric-toggle-row');
    const toggle = document.getElementById('biometric-toggle');
    if (!row || !toggle) return;

    if (!isBiometricSupported()) {
        row.style.display = 'none';
        return;
    }

    row.style.display = '';
    const journalId = DB.getJournalId();
    toggle.checked = journalId ? AndroidBridge.hasCredential(journalId) : false;

    // Auto-biometric toggle
    const autoRow = document.getElementById('auto-biometric-toggle-row');
    const autoToggle = document.getElementById('auto-biometric-toggle');
    if (autoRow && autoToggle) {
        autoRow.style.display = '';
        autoToggle.checked = Bootstrap.get('auto_biometric') === 'true';
    }
}

function toggleAutoBiometric() {
    const toggle = document.getElementById('auto-biometric-toggle');
    Bootstrap.set('auto_biometric', toggle.checked ? 'true' : 'false');
}

function toggleBiometric() {
    const toggle = document.getElementById('biometric-toggle');
    const journalId = DB.getJournalId();
    if (!journalId || !isBiometricSupported()) return;

    if (toggle.checked) {
        // Enable: save current password for biometric unlock
        const password = DB.getPassword();
        if (password) {
            AndroidBridge.saveCredential(journalId, password);
        } else {
            alert('Cannot enable biometric: no active password.');
            toggle.checked = false;
        }
    } else {
        // Disable: remove saved credential
        AndroidBridge.removeCredential(journalId);
    }
}

// ========== Init ==========

document.addEventListener('DOMContentLoaded', async () => {
    // Initialize Bootstrap (IndexedDB preferences store) before anything else
    await Bootstrap.init();

    // Re-initialize module-level vars that read Bootstrap before init()
    if (typeof currentPageSize !== 'undefined') currentPageSize = parseInt(Bootstrap.get('entries_page_size')) || 20;
    if (typeof entryViewMode !== 'undefined') entryViewMode = Bootstrap.get('entry_view_mode') || 'card';

    // Detect Android WebView and add class for compact navbar
    if (window.AndroidBridge) {
        document.body.classList.add('android');
    }

    // If native login is handling auth, hide the web login page entirely.
    // performAutoLogin() from MainActivity will call enterApp() directly.
    // Skip populateJournalSelect() to avoid triggering auto-biometric from web layer.
    if (window.AndroidBridge && typeof AndroidBridge.hasNativeLogin === 'function' && AndroidBridge.hasNativeLogin()) {
        document.getElementById('page-login').style.display = 'none';
    } else {
        populateJournalSelect();
    }
    initColumnToggles();

    // Tag input - Enter key and dropdown
    const tagInput = document.getElementById('entry-tags-input');
    tagInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            const val = tagInput.value.trim();
            if (val && !currentEntryTags.includes(val)) {
                currentEntryTags.push(val);
                renderTags();
            }
            tagInput.value = '';
            hideTagDropdown();
        }
        if (e.key === 'Escape') {
            hideTagDropdown();
        }
    });
    tagInput.addEventListener('input', () => showTagDropdown());
    tagInput.addEventListener('blur', () => {
        // Delay to allow mousedown on dropdown items
        setTimeout(hideTagDropdown, 150);
    });

    // Password Enter key
    document.getElementById('input-password').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') handleLogin();
    });
    document.getElementById('input-password-confirm').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') handleLogin();
    });

    // New journal name Enter key
    document.getElementById('new-journal-name').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') createNewJournal();
    });

    // Weather city search Enter key
    document.getElementById('weather-city-search').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') weatherSearchCity();
    });
});

// ========== Column Toggle ==========

function initColumnToggles() {
    document.querySelectorAll('.col-toggle').forEach(container => {
        const id = container.id;
        const saved = parseInt(Bootstrap.get('col_' + id)) || 1;
        container.innerHTML = `<button class="col-toggle-btn" onclick="cycleColumns('${id}')" title="${saved} column${saved > 1 ? 's' : ''}">&#9645; ${saved}</button>`;
        applyColumns(id, saved);
    });
}

function cycleColumns(toggleId) {
    const current = parseInt(Bootstrap.get('col_' + toggleId)) || 1;
    const next = current >= 3 ? 1 : current + 1;
    Bootstrap.set('col_' + toggleId, next);
    const container = document.getElementById(toggleId);
    container.querySelector('.col-toggle-btn').title = next + ' column' + (next > 1 ? 's' : '');
    container.querySelector('.col-toggle-btn').innerHTML = '&#9645; ' + next;
    applyColumns(toggleId, next);
}

function applyColumns(toggleId, cols) {
    const container = document.getElementById(toggleId);
    const targetId = container.getAttribute('data-target');
    const target = document.getElementById(targetId);
    if (!target) return;
    target.classList.remove('cols-1', 'cols-2', 'cols-3');
    target.classList.add('cols-' + cols);
    // Expand page width for multi-column
    const page = container.closest('.page');
    if (page) {
        page.classList.toggle('page-wide', cols > 1);
    }
}

// ========== Utility ==========

function toLocalDateStr(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
}

function generateId() {
    return Date.now().toString(36) + Math.random().toString(36).substr(2, 9);
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr + 'T00:00:00');
    if (isNaN(d.getTime())) return '';

    const fmt = Bootstrap.get('ev_date_format') || 'short';
    const pad = (n) => String(n).padStart(2, '0');
    const y = d.getFullYear(), m = d.getMonth(), day = d.getDate();

    switch (fmt) {
        case 'long':
            return d.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
        case 'iso':
            return `${y}-${pad(m + 1)}-${pad(day)}`;
        case 'us':
            return `${pad(m + 1)}/${pad(day)}/${y}`;
        case 'eu':
            return `${pad(day)}/${pad(m + 1)}/${y}`;
        case 'weekday':
            return d.toLocaleDateString('en-US', { weekday: 'short', year: 'numeric', month: 'short', day: 'numeric' });
        default: // 'short'
            return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
    }
}

function formatTime(timeStr) {
    if (!timeStr) return '';
    const fmt = Bootstrap.get('ev_time_format') || '12h';
    const parts = timeStr.match(/^(\d{1,2}):(\d{2})$/);
    if (!parts) return timeStr;
    const h = parseInt(parts[1]), m = parts[2];
    if (fmt === '24h') {
        return String(h).padStart(2, '0') + ':' + m;
    }
    // 12h
    const period = h >= 12 ? 'PM' : 'AM';
    const h12 = h === 0 ? 12 : h > 12 ? h - 12 : h;
    return h12 + ':' + m + ' ' + period;
}

function isValidDate(dateStr) {
    if (!dateStr || typeof dateStr !== 'string') return false;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateStr.trim())) return false;
    const d = new Date(dateStr + 'T00:00:00');
    return !isNaN(d.getTime());
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function getIconHtml(type, name) {
    const data = DB.getIcon(type, name);
    if (!data) return '';
    return `<img src="${data}" class="tag-icon" alt="">`;
}

function getIconHD(type, name) {
    return DB.getIcon(type + '_hd', name);
}

function getItemColorStyle(type, name) {
    if (type === 'category' && typeof isUseCategoryColor === 'function' && isUseCategoryColor()) {
        const colors = typeof getCategoryColors === 'function' ? getCategoryColors() : {};
        if (colors[name]) return ` style="color:${colors[name]}"`;
    }
    if (type === 'tag' && typeof isUseTagColor === 'function' && isUseTagColor()) {
        const colors = typeof getTagColors === 'function' ? getTagColors() : {};
        if (colors[name]) return ` style="color:${colors[name]}"`;
    }
    return '';
}
