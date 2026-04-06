/**
 * Calendar View: monthly and weekly calendar for journal entries.
 */

let calendarDate = new Date();      // current view anchor date
let calendarMode = 'month';         // 'month' or 'week'
let calendarSelectedDate = null;    // YYYY-MM-DD string or null
let _calendarEntries = [];          // cached entries for current view

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
        label = _formatShortDate(weekStart) + ' &ndash; ' + _formatShortDate(weekEnd);
    }

    header.innerHTML =
        '<div class="calendar-nav">' +
            '<button class="btn-small" onclick="calendarPrev()" title="Previous">&laquo;</button>' +
            '<span class="calendar-title">' + label + '</span>' +
            '<button class="btn-small" onclick="calendarNext()" title="Next">&raquo;</button>' +
            '<button class="btn-small" onclick="calendarToday()">Today</button>' +
        '</div>' +
        '<div class="calendar-controls">' +
            '<input type="text" id="calendar-goto-input" class="calendar-goto-input" placeholder="YYYY-MM-DD" onkeydown="if(event.key===\'Enter\')calendarGoto()">' +
            '<button class="btn-small" onclick="calendarGoto()">Go</button>' +
            '<div class="calendar-mode-toggle">' +
                '<button class="btn-small' + (calendarMode === 'month' ? ' active' : '') + '" onclick="setCalendarMode(\'month\')">Month</button>' +
                '<button class="btn-small' + (calendarMode === 'week' ? ' active' : '') + '" onclick="setCalendarMode(\'week\')">Week</button>' +
            '</div>' +
        '</div>';
}

function _formatShortDate(d) {
    const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    return months[d.getMonth()] + ' ' + d.getDate();
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
    if (!input) return;
    const val = input.value.trim();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(val)) {
        input.style.borderColor = 'var(--danger)';
        setTimeout(() => input.style.borderColor = '', 1500);
        return;
    }
    const d = new Date(val + 'T00:00:00');
    if (isNaN(d.getTime())) {
        input.style.borderColor = 'var(--danger)';
        setTimeout(() => input.style.borderColor = '', 1500);
        return;
    }
    calendarDate = d;
    calendarSelectedDate = val;
    renderCalendarHeader();
    renderCalendar();
    _renderCalendarResults();
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

    // Day-of-week offset (Monday=0)
    let startDow = firstDay.getDay() - 1;
    if (startDow < 0) startDow = 6;

    let html = '<div class="calendar-weekdays">';
    const dayLabels = ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];
    for (const dl of dayLabels) {
        html += '<div class="calendar-weekday">' + dl + '</div>';
    }
    html += '</div><div class="calendar-month-grid">';

    // Empty cells before first day
    for (let i = 0; i < startDow; i++) {
        html += '<div class="calendar-day calendar-day-empty"></div>';
    }

    // Day cells
    for (let d = 1; d <= lastDay.getDate(); d++) {
        const dateStr = year + '-' + String(month + 1).padStart(2, '0') + '-' + String(d).padStart(2, '0');
        const count = countMap[dateStr] || 0;
        const isToday = dateStr === todayStr;
        const isSelected = dateStr === calendarSelectedDate;
        let cls = 'calendar-day';
        if (isToday) cls += ' calendar-day-today';
        if (isSelected) cls += ' calendar-day-selected';
        if (count > 0) cls += ' calendar-day-has-entries';

        html += '<div class="' + cls + '" onclick="selectCalendarDay(\'' + dateStr + '\')">';
        html += '<span class="calendar-day-num">' + d + '</span>';
        if (count > 0) {
            html += '<span class="calendar-day-badge ' + _badgeClass(count) + '">' + count + '</span>';
        }
        html += '</div>';
    }

    html += '</div>';
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

    const dayLabels = ['Mon','Tue','Wed','Thu','Fri','Sat','Sun'];
    let html = '<div class="calendar-weekdays">';
    for (const dl of dayLabels) {
        html += '<div class="calendar-weekday">' + dl + '</div>';
    }
    html += '</div><div class="calendar-week-grid">';

    for (let i = 0; i < 7; i++) {
        const d = new Date(weekStart);
        d.setDate(d.getDate() + i);
        const dateStr = toLocalDateStr(d);
        const isToday = dateStr === todayStr;
        const isSelected = dateStr === calendarSelectedDate;
        const dayEntries = byDate[dateStr] || [];

        let cls = 'calendar-week-day';
        if (isToday) cls += ' calendar-day-today';
        if (isSelected) cls += ' calendar-day-selected';

        html += '<div class="' + cls + '" onclick="selectCalendarDay(\'' + dateStr + '\')">';
        html += '<div class="calendar-week-day-header">';
        html += '<span class="calendar-day-num">' + d.getDate() + '</span>';
        if (dayEntries.length > 0) {
            html += '<span class="calendar-day-badge ' + _badgeClass(dayEntries.length) + '">' + dayEntries.length + '</span>';
        }
        html += '</div>';

        // Entry titles
        html += '<div class="calendar-week-entries">';
        for (const e of dayEntries) {
            html += '<div class="calendar-week-entry" onclick="event.stopPropagation(); viewEntry(\'' + e.id + '\')">';
            html += escapeHtml(e.title || 'Untitled');
            html += '</div>';
        }
        html += '</div></div>';
    }

    html += '</div>';
    grid.innerHTML = html;
}

// ========== Day Selection & Results ==========

function selectCalendarDay(dateStr) {
    calendarSelectedDate = dateStr;

    // Update selected state in grid
    document.querySelectorAll('#calendar-grid .calendar-day, #calendar-grid .calendar-week-day').forEach(el => {
        el.classList.remove('calendar-day-selected');
    });
    const allCells = document.querySelectorAll('#calendar-grid .calendar-day, #calendar-grid .calendar-week-day');
    allCells.forEach(el => {
        if (el.getAttribute('onclick') && el.getAttribute('onclick').includes(dateStr)) {
            el.classList.add('calendar-day-selected');
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
