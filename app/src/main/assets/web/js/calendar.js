/**
 * Calendar View: modern Google Calendar-inspired monthly and weekly calendar.
 */

let calendarDate = new Date();      // current view anchor date
let calendarMode = 'month';         // 'month' or 'week'
let calendarSelectedDate = null;    // YYYY-MM-DD string or null
let _calendarEntries = [];          // cached entries for current view
let _calendarGotoDate = null;       // tracks the goto input date for Prev/Next Event

function initCalendar() {
    _calendarEntries = DB.getEntries();
    renderCalendarHeader();
    renderCalendar();
}

// ========== Header ==========

function renderCalendarHeader() {
    const header = document.getElementById('calendar-header');
    if (!header) return;

    const monthNames = ['January','February','March','April','May','June',
        'July','August','September','October','November','December'];

    let label;
    if (calendarMode === 'month') {
        label = monthNames[calendarDate.getMonth()] + ' ' + calendarDate.getFullYear();
    } else {
        const weekStart = _getWeekStart(calendarDate);
        const weekEnd = new Date(weekStart);
        weekEnd.setDate(weekEnd.getDate() + 6);
        label = _formatShortDate(weekStart) + ' &ndash; ' + _formatShortDate(weekEnd) + ', ' + weekEnd.getFullYear();
    }

    header.innerHTML =
        '<div class="calendar-toolbar">' +
            '<div class="calendar-toolbar-left">' +
                '<button class="cal-nav-btn" onclick="calendarPrev()" title="Previous">&#9664;</button>' +
                '<button class="cal-nav-btn" onclick="calendarNext()" title="Next">&#9654;</button>' +
                '<button class="cal-today-btn" onclick="calendarToday()">Today</button>' +
                '<span class="calendar-title">' + label + '</span>' +
            '</div>' +
            '<div class="calendar-toolbar-right">' +
                '<button class="cal-goto-btn" onclick="toggleCalendarGoto()" title="Go to date">Goto</button>' +
                '<div class="calendar-mode-toggle">' +
                    '<button class="cal-mode-btn' + (calendarMode === 'month' ? ' active' : '') + '" onclick="setCalendarMode(\'month\')">Month</button>' +
                    '<button class="cal-mode-btn' + (calendarMode === 'week' ? ' active' : '') + '" onclick="setCalendarMode(\'week\')">Week</button>' +
                '</div>' +
            '</div>' +
        '</div>';
}

function _formatShortDate(d) {
    const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    return months[d.getMonth()] + ' ' + d.getDate();
}

// ========== Goto Popup ==========

function toggleCalendarGoto() {
    const panel = document.getElementById('calendar-goto-panel');
    if (!panel) return;
    const isVisible = panel.style.display !== 'none';
    panel.style.display = isVisible ? 'none' : 'block';
    if (!isVisible) {
        const val = calendarSelectedDate || toLocalDateStr(calendarDate);
        _calendarGotoDate = val;
        const input = document.getElementById('calendar-goto-input');
        if (input) input.value = val;
        const textInput = document.getElementById('calendar-goto-text');
        if (textInput) textInput.value = _isoToMDY(val);
        if (textInput) textInput.focus();
    }
}

// Convert YYYY-MM-DD to mm/dd/yyyy
function _isoToMDY(iso) {
    if (!iso) return '';
    const parts = iso.split('-');
    if (parts.length !== 3) return '';
    return parts[1] + '/' + parts[2] + '/' + parts[0];
}

// Convert mm/dd/yyyy to YYYY-MM-DD
function _mdyToISO(mdy) {
    const m = mdy.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
    if (!m) return null;
    const month = m[1].padStart(2, '0');
    const day = m[2].padStart(2, '0');
    return m[3] + '-' + month + '-' + day;
}

// Auto-insert slashes as user types
function calendarAutoFormatDate(el) {
    let v = el.value.replace(/[^\d/]/g, '');
    // Auto-insert slashes after mm and dd
    const digits = v.replace(/\//g, '');
    if (digits.length >= 2 && v.indexOf('/') === -1) {
        v = digits.substring(0, 2) + '/' + digits.substring(2);
    }
    if (digits.length >= 4 && (v.match(/\//g) || []).length < 2) {
        const parts = v.split('/');
        if (parts.length === 2 && parts[1].length > 2) {
            v = parts[0] + '/' + parts[1].substring(0, 2) + '/' + parts[1].substring(2);
        }
    }
    if (v.length > 10) v = v.substring(0, 10);
    el.value = v;
    // Auto-sync date picker when a full valid date is typed
    const iso = _mdyToISO(v);
    if (iso) {
        const d = new Date(iso + 'T00:00:00');
        if (!isNaN(d.getTime())) {
            const dateInput = document.getElementById('calendar-goto-input');
            if (dateInput) dateInput.value = iso;
            _calendarGotoDate = iso;
        }
    }
}

function calendarGotoFromText() {
    const textInput = document.getElementById('calendar-goto-text');
    if (!textInput || !textInput.value.trim()) return;
    const iso = _mdyToISO(textInput.value.trim());
    if (!iso) {
        textInput.style.borderColor = 'var(--danger)';
        setTimeout(() => textInput.style.borderColor = '', 1500);
        return;
    }
    const d = new Date(iso + 'T00:00:00');
    if (isNaN(d.getTime())) {
        textInput.style.borderColor = 'var(--danger)';
        setTimeout(() => textInput.style.borderColor = '', 1500);
        return;
    }
    // Sync date picker
    const dateInput = document.getElementById('calendar-goto-input');
    if (dateInput) dateInput.value = iso;
    calendarDate = d;
    calendarSelectedDate = iso;
    _calendarGotoDate = iso;
    renderCalendarHeader();
    renderCalendar();
    _renderCalendarResults();
    // Keep panel open
    const panel = document.getElementById('calendar-goto-panel');
    if (panel) panel.style.display = 'block';
    if (dateInput) dateInput.value = iso;
    textInput.value = _isoToMDY(iso);
}

// ========== Navigation ==========

function calendarPrev() {
    if (calendarMode === 'month') {
        calendarDate = new Date(calendarDate.getFullYear(), calendarDate.getMonth() - 1, 1);
    } else {
        calendarDate.setDate(calendarDate.getDate() - 7);
    }
    renderCalendarHeader();
    renderCalendar();
}

function calendarNext() {
    if (calendarMode === 'month') {
        calendarDate = new Date(calendarDate.getFullYear(), calendarDate.getMonth() + 1, 1);
    } else {
        calendarDate.setDate(calendarDate.getDate() + 7);
    }
    renderCalendarHeader();
    renderCalendar();
}

function calendarToday() {
    calendarDate = new Date();
    calendarSelectedDate = toLocalDateStr(new Date());
    renderCalendarHeader();
    renderCalendar();
    _renderCalendarResults();
}

function calendarGoto() {
    const input = document.getElementById('calendar-goto-input');
    if (!input || !input.value) return;
    const val = input.value.trim();
    const d = new Date(val + 'T00:00:00');
    if (isNaN(d.getTime())) {
        input.style.borderColor = 'var(--danger)';
        setTimeout(() => input.style.borderColor = '', 1500);
        return;
    }
    calendarDate = d;
    calendarSelectedDate = val;
    _calendarGotoDate = val;
    renderCalendarHeader();
    renderCalendar();
    _renderCalendarResults();
    // Keep panel open and restore inputs
    const panel = document.getElementById('calendar-goto-panel');
    if (panel) panel.style.display = 'block';
    const inp = document.getElementById('calendar-goto-input');
    if (inp) inp.value = val;
    const txt = document.getElementById('calendar-goto-text');
    if (txt) txt.value = _isoToMDY(val);
}

function calendarGotoDateChanged() {
    const input = document.getElementById('calendar-goto-input');
    if (input && input.value) {
        _calendarGotoDate = input.value.trim();
        const txt = document.getElementById('calendar-goto-text');
        if (txt) txt.value = _isoToMDY(_calendarGotoDate);
    }
}

function calendarJumpEvent(direction) {
    // Get all unique dates that have entries, sorted
    const entryDates = [...new Set(_calendarEntries.map(e => e.date))].sort();

    // Read reference date directly from the input field as source of truth
    const inputEl = document.getElementById('calendar-goto-input');
    const inputVal = (inputEl && inputEl.value) ? inputEl.value.trim() : null;
    const refDate = inputVal || _calendarGotoDate || calendarSelectedDate || toLocalDateStr(new Date());

    let target = null;
    if (direction > 0) {
        for (let i = 0; i < entryDates.length; i++) {
            if (entryDates[i] > refDate) { target = entryDates[i]; break; }
        }
    } else {
        for (let i = entryDates.length - 1; i >= 0; i--) {
            if (entryDates[i] < refDate) { target = entryDates[i]; break; }
        }
    }

    if (!target) {
        // Show feedback — no more events in this direction
        const msg = direction > 0 ? 'No more events after this date' : 'No more events before this date';
        const panel = document.getElementById('calendar-goto-panel');
        if (panel) {
            let msgEl = document.getElementById('calendar-jump-msg');
            if (!msgEl) {
                msgEl = document.createElement('div');
                msgEl.id = 'calendar-jump-msg';
                msgEl.className = 'cal-jump-msg';
                panel.appendChild(msgEl);
            }
            msgEl.textContent = msg;
            msgEl.style.display = 'block';
            setTimeout(() => { msgEl.style.display = 'none'; }, 2000);
        }
        return;
    }

    const d = new Date(target + 'T00:00:00');
    calendarDate = d;
    calendarSelectedDate = target;
    _calendarGotoDate = target;  // update reference for subsequent jumps

    renderCalendarHeader();
    renderCalendar();
    _renderCalendarResults();

    // Restore panel and inputs after re-render
    const panel = document.getElementById('calendar-goto-panel');
    if (panel) panel.style.display = 'block';
    const input = document.getElementById('calendar-goto-input');
    if (input) input.value = target;
    const txt = document.getElementById('calendar-goto-text');
    if (txt) txt.value = _isoToMDY(target);
}

function setCalendarMode(mode) {
    calendarMode = mode;
    renderCalendarHeader();
    renderCalendar();
}

// ========== Render Calendar Grid ==========

function renderCalendar() {
    const grid = document.getElementById('calendar-grid');
    if (!grid) return;

    if (calendarMode === 'month') {
        _renderMonthView(grid);
    } else {
        _renderWeekView(grid);
    }
}

function _renderMonthView(grid) {
    const year = calendarDate.getFullYear();
    const month = calendarDate.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const todayStr = toLocalDateStr(new Date());

    // Build entry count map for the month
    const from = toLocalDateStr(firstDay);
    const to = toLocalDateStr(lastDay);
    const countMap = _buildCountMap(from, to);

    // Also build a map of entry titles for showing in cells
    const titleMap = _buildTitleMap(from, to);

    // Day-of-week offset (Monday=0)
    let startDow = firstDay.getDay() - 1;
    if (startDow < 0) startDow = 6;

    let html = '<table class="cal-month-table"><thead><tr>';
    const dayLabels = ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];
    for (const dl of dayLabels) {
        html += '<th class="cal-weekday-th">' + dl + '</th>';
    }
    html += '</tr></thead><tbody><tr>';

    // Empty cells before first day
    for (let i = 0; i < startDow; i++) {
        html += '<td class="cal-day-cell cal-day-empty"></td>';
    }

    let cellCount = startDow;
    // Day cells
    for (let d = 1; d <= lastDay.getDate(); d++) {
        if (cellCount > 0 && cellCount % 7 === 0) {
            html += '</tr><tr>';
        }
        const dateStr = year + '-' + String(month + 1).padStart(2, '0') + '-' + String(d).padStart(2, '0');
        const count = countMap[dateStr] || 0;
        const isToday = dateStr === todayStr;
        const isSelected = dateStr === calendarSelectedDate;
        let cls = 'cal-day-cell';
        if (isToday) cls += ' cal-today';
        if (isSelected) cls += ' cal-selected';

        html += '<td class="' + cls + '" onclick="selectCalendarDay(\'' + dateStr + '\')">';
        html += '<div class="cal-day-header">';
        html += '<span class="cal-day-num' + (isToday ? ' cal-today-num' : '') + '">' + d + '</span>';
        html += '</div>';

        // Show entry dots/chips
        if (count > 0) {
            const titles = titleMap[dateStr] || [];
            html += '<div class="cal-day-events">';
            const showMax = 3;
            for (let i = 0; i < Math.min(titles.length, showMax); i++) {
                html += '<div class="cal-event-chip">' + escapeHtml(titles[i].substring(0, 18) || 'Untitled') + '</div>';
            }
            if (titles.length > showMax) {
                html += '<div class="cal-event-more">+' + (titles.length - showMax) + ' more</div>';
            }
            html += '</div>';
        }

        html += '</td>';
        cellCount++;
    }

    // Fill remaining cells in last row
    const remaining = cellCount % 7;
    if (remaining > 0) {
        for (let i = remaining; i < 7; i++) {
            html += '<td class="cal-day-cell cal-day-empty"></td>';
        }
    }

    html += '</tr></tbody></table>';
    grid.innerHTML = html;
}

function _renderWeekView(grid) {
    const weekStart = _getWeekStart(calendarDate);
    const todayStr = toLocalDateStr(new Date());
    const from = toLocalDateStr(weekStart);
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekEnd.getDate() + 6);
    const to = toLocalDateStr(weekEnd);

    // Get entries for the week
    const weekEntries = _calendarEntries.filter(e => e.date >= from && e.date <= to);

    // Group by date
    const byDate = {};
    for (const e of weekEntries) {
        if (!byDate[e.date]) byDate[e.date] = [];
        byDate[e.date].push(e);
    }

    const dayNames = ['Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday'];
    const dayNamesShort = ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];

    let html = '<div class="cal-week-rows">';

    for (let i = 0; i < 7; i++) {
        const d = new Date(weekStart);
        d.setDate(d.getDate() + i);
        const dateStr = toLocalDateStr(d);
        const isToday = dateStr === todayStr;
        const isSelected = dateStr === calendarSelectedDate;
        const dayEntries = byDate[dateStr] || [];

        let cls = 'cal-week-row';
        if (isToday) cls += ' cal-week-row-today';
        if (isSelected) cls += ' cal-week-row-selected';

        html += '<div class="' + cls + '" onclick="selectCalendarDay(\'' + dateStr + '\')">';
        html += '<div class="cal-week-row-date">';
        html += '<span class="cal-week-day-name">' + dayNamesShort[i] + '</span>';
        html += '<span class="cal-week-day-num' + (isToday ? ' cal-today-num' : '') + '">' + d.getDate() + '</span>';
        html += '</div>';
        html += '<div class="cal-week-row-entries">';

        if (dayEntries.length === 0) {
            html += '<span class="cal-week-no-entries">No entries</span>';
        } else {
            for (const e of dayEntries) {
                const title = (e.title || 'Untitled').substring(0, 10);
                const time = e.time ? formatTime(e.time) : '';
                html += '<div class="cal-week-entry-chip" onclick="event.stopPropagation(); viewEntry(\'' + e.id + '\')">';
                if (time) html += '<span class="cal-week-entry-time">' + time + '</span>';
                html += '<span class="cal-week-entry-title">' + escapeHtml(title) + '</span>';
                html += '</div>';
            }
        }

        html += '</div></div>';
    }

    html += '</div>';
    grid.innerHTML = html;
}

// ========== Day Selection & Results ==========

function selectCalendarDay(dateStr) {
    calendarSelectedDate = dateStr;

    // Update selected state in month view
    document.querySelectorAll('#calendar-grid .cal-day-cell').forEach(el => {
        el.classList.remove('cal-selected');
        if (el.getAttribute('onclick') && el.getAttribute('onclick').includes(dateStr)) {
            el.classList.add('cal-selected');
        }
    });
    // Update selected state in week view
    document.querySelectorAll('#calendar-grid .cal-week-row').forEach(el => {
        el.classList.remove('cal-week-row-selected');
        if (el.getAttribute('onclick') && el.getAttribute('onclick').includes(dateStr)) {
            el.classList.add('cal-week-row-selected');
        }
    });

    _renderCalendarResults();
}

function _renderCalendarResults() {
    const container = document.getElementById('calendar-results');
    if (!container) return;

    if (!calendarSelectedDate) {
        container.style.display = 'none';
        return;
    }

    const dayEntries = _calendarEntries.filter(e => e.date === calendarSelectedDate);
    container.style.display = 'block';

    // Update viewEntryList for prev/next navigation in entry viewer
    if (typeof viewEntryList !== 'undefined') {
        viewEntryList = dayEntries.map(e => e.id);
    }

    const label = document.getElementById('calendar-results-label');
    if (label) {
        label.textContent = formatDate(calendarSelectedDate) + ' \u2014 ' + dayEntries.length + ' entr' + (dayEntries.length === 1 ? 'y' : 'ies');
    }

    ResultGrid.render({
        data: dayEntries,
        containerId: 'calendar-results-grid',
        maxHeight: 0,
        emptyMessage: 'No entries on this date.',
        columns: [
            { key: 'time', label: 'Time', width: '70px', formatter: (v) => v ? formatTime(v) : '' },
            { key: 'title', label: 'Title', formatter: (v) => escapeHtml(v || 'Untitled') },
            { key: 'categories', label: 'Categories', noHighlight: true,
                formatter: (v) => (v || []).map(c =>
                    '<span class="tag-item"' + getItemColorStyle('category', c) + '>' + getIconHtml('category', c) + escapeHtml(c) + '</span>'
                ).join(' ')
            },
            { key: 'tags', label: 'Tags', noHighlight: true,
                formatter: (v) => (v || []).map(t =>
                    '<span class="tag-item"' + getItemColorStyle('tag', t) + '>' + getIconHtml('tag', t) + escapeHtml(t) + '</span>'
                ).join(' ')
            }
        ],
        onRowClick: (row) => viewEntry(row.id)
    });
}

// ========== Helpers ==========

function _buildCountMap(from, to) {
    const map = {};
    for (const e of _calendarEntries) {
        if (e.date >= from && e.date <= to) {
            map[e.date] = (map[e.date] || 0) + 1;
        }
    }
    return map;
}

function _buildTitleMap(from, to) {
    const map = {};
    for (const e of _calendarEntries) {
        if (e.date >= from && e.date <= to) {
            if (!map[e.date]) map[e.date] = [];
            map[e.date].push(e.title || 'Untitled');
        }
    }
    return map;
}

function _getWeekStart(date) {
    const d = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    let dow = d.getDay() - 1; // Monday=0
    if (dow < 0) dow = 6;
    d.setDate(d.getDate() - dow);
    return d;
}

function _badgeClass(count) {
    if (count >= 4) return 'badge-high';
    if (count >= 2) return 'badge-mid';
    return 'badge-low';
}
