/**
 * Reusable UI components for MYJOURNAL.
 *
 * ResultGrid  — scrollable data table with configurable columns.
 * RecordViewer — full-field detail overlay for a single record with prev/next nav.
 */

const ResultGrid = {
    /**
     * Render a scrollable data table.
     *
     * @param {Object} opts
     * @param {Array<Object>} opts.data        — array of row objects
     * @param {Array<Object>} opts.columns     — column definitions:
     *   { key, label, width?, formatter?(value, row, idx), noEscape? }
     *   formatter returns display string (HTML if noEscape on column).
     *   If omitted, value is escaped and displayed as-is.
     * @param {string}   [opts.containerId]    — target element id (innerHTML replaced)
     * @param {boolean}  [opts.showRowNum=true] — show # column
     * @param {number}   [opts.rowNumOffset=0]  — starting row number offset
     * @param {Function} [opts.onRowClick]      — callback(row, index)
     * @param {string}   [opts.emptyMessage='No data.']
     * @param {string}   [opts.highlightTerm]   — term to highlight in cells
     * @param {boolean}  [opts.wholeWord=false]  — whole-word highlight matching
     * @param {number}   [opts.maxHeight=400]   — max container height in px (0 = unlimited)
     * @returns {string} HTML string (also injected into containerId if provided)
     */
    render(opts) {
        const {
            data = [],
            columns = [],
            containerId,
            showRowNum = true,
            rowNumOffset = 0,
            onRowClick,
            emptyMessage = 'No data.',
            highlightTerm,
            wholeWord = false,
            maxHeight = 400
        } = opts;

        if (!data.length) {
            const html = `<div class="rg-empty">${escapeHtml(emptyMessage)}</div>`;
            if (containerId) {
                const el = document.getElementById(containerId);
                if (el) { el.innerHTML = html; el.style.display = 'block'; }
            }
            return html;
        }

        // Store data + config for record viewer access
        this._lastData = data;
        this._lastColumns = columns;
        this._lastOnRowClick = onRowClick;
        this._lastHighlightTerm = highlightTerm;
        this._lastWholeWord = wholeWord;

        const headCells = [];
        if (showRowNum) headCells.push('<th class="rg-num">#</th>');
        for (const col of columns) {
            const style = col.width ? ` style="min-width:${col.width}"` : '';
            headCells.push(`<th${style}>${escapeHtml(col.label)}</th>`);
        }

        const bodyRows = data.map((row, i) => {
            const cells = [];
            if (showRowNum) cells.push(`<td class="rg-num">${i + 1 + rowNumOffset}</td>`);
            for (const col of columns) {
                const raw = row[col.key];
                let display;
                if (col.formatter) {
                    display = col.formatter(raw, row, i);
                } else {
                    display = escapeHtml(raw == null ? '' : String(raw));
                }
                if (highlightTerm && !col.noHighlight) {
                    display = ResultGrid._highlight(display, highlightTerm, wholeWord);
                }
                cells.push(`<td>${display}</td>`);
            }
            const clickAttr = onRowClick ? ` onclick="ResultGrid._onRowClick(${i})"` : '';
            const clickClass = onRowClick ? ' rg-clickable' : '';
            return `<tr class="rg-row${clickClass}"${clickAttr}>${cells.join('')}</tr>`;
        }).join('');

        const maxHStyle = maxHeight ? `max-height:${maxHeight}px;` : '';
        const html = `<div class="rg-container" style="${maxHStyle}">
            <table class="rg-table">
                <thead><tr>${headCells.join('')}</tr></thead>
                <tbody>${bodyRows}</tbody>
            </table>
        </div>`;

        if (containerId) {
            const el = document.getElementById(containerId);
            if (el) { el.innerHTML = html; el.style.display = 'block'; }
        }
        return html;
    },

    _onRowClick(idx) {
        if (this._lastOnRowClick) {
            this._lastOnRowClick(this._lastData[idx], idx);
        }
    },

    _highlight(html, term, wholeWord) {
        if (!term) return html;
        const escaped = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        const pattern = wholeWord ? '\\b(' + escaped + ')\\b' : '(' + escaped + ')';
        return html.replace(new RegExp(pattern, 'gi'), '<mark>$1</mark>');
    }
};


/**
 * RankedPanel — self-contained ranked list/card panel with built-in view toggle and show-all.
 *
 * Each instance manages its own state. Create via RankedPanel.create(opts) which returns an
 * instance you can call .setData() and .render() on.
 */
const RankedPanel = {
    _instances: {},

    /**
     * Create or retrieve a panel instance.
     *
     * @param {Object} opts
     * @param {string}  opts.id            — unique panel id (used for Bootstrap key + lookup)
     * @param {string}  opts.containerId   — id of the wrapper element to render into (replaces innerHTML)
     * @param {string}  opts.title         — panel heading text
     * @param {string}  [opts.icon]        — emoji/icon before title
     * @param {string}  opts.filterType    — type key for icons/colors (e.g. 'category', 'tag')
     * @param {Function} [opts.onItemClick] — callback(name, count) when an item is clicked
     * @param {Function} [opts.getIcon]     — (name) => icon HTML; defaults to getIconHtml
     * @param {Function} [opts.getColor]    — (name) => style attr string; defaults to getItemColorStyle
     * @param {number}  [opts.topN=10]     — number of items to show before "Show All"
     * @returns {Object} panel instance
     */
    create(opts) {
        const {
            id,
            containerId,
            title,
            icon = '',
            filterType,
            onItemClick,
            getIcon,
            getColor,
            topN = 10
        } = opts;

        if (this._instances[id]) {
            // Update opts on existing instance
            Object.assign(this._instances[id]._opts, opts);
            return this._instances[id];
        }

        const instance = {
            _opts: opts,
            _data: [],          // array of [name, count]
            _showAll: false,
            _viewMode: Bootstrap.get('rp_view_' + id) || 'list',

            setData(data) {
                this._data = data;
                return this;
            },

            render() {
                const el = document.getElementById(containerId);
                if (!el) return;

                const allItems = this._data;
                const items = this._showAll ? allItems : allItems.slice(0, topN);
                const vm = this._viewMode;
                const panelId = id;

                // Build header
                const showAllBtn = allItems.length > topN
                    ? `<button class="btn-small btn-show-all" onclick="RankedPanel.toggleShowAll('${panelId}')">${this._showAll ? 'Top ' + topN : 'Show All (' + allItems.length + ')'}</button>`
                    : '';
                const viewIcon = vm === 'list' ? '&#x25A6;' : '&#x2630;';
                const viewBtn = `<button class="btn-small" onclick="RankedPanel.toggleView('${panelId}')" title="Toggle view">${viewIcon}</button>`;

                let bodyHtml;
                if (allItems.length === 0) {
                    bodyHtml = '<div class="no-data">No data yet</div>';
                } else if (vm === 'card') {
                    bodyHtml = '<div class="ranked-button-grid">' + items.map(([name, count]) => {
                        // 3-tier fallback: HD image button → small icon + text → text-only
                        const hdData = typeof getIconHD === 'function' ? getIconHD(filterType, name) : null;
                        if (hdData) {
                            const clickAttr = onItemClick ? ` onclick="RankedPanel._itemClick('${panelId}', '${escapeHtml(name)}')"` : '';
                            return `<div class="ranked-btn ranked-btn-img"${clickAttr} title="${escapeHtml(name)} (${count})">
                                <img src="${hdData}" class="ranked-btn-img-face" alt="${escapeHtml(name)}">
                                <span class="ranked-btn-img-count">${count}</span>
                            </div>`;
                        }
                        const iconFn = getIcon || (typeof getIconHtml === 'function' ? (n) => {
                            const data = DB.getIcon(filterType, n);
                            return data ? `<img src="${data}" class="ranked-btn-icon" alt="">` : `<span class="ranked-btn-text-icon">${escapeHtml(n.substring(0, 2))}</span>`;
                        } : () => '');
                        const colorFn = getColor || (typeof getItemColorStyle === 'function' ? (n) => getItemColorStyle(filterType, n) : () => '');
                        const clickAttr = onItemClick ? ` onclick="RankedPanel._itemClick('${panelId}', '${escapeHtml(name)}')"` : '';
                        return `<div class="ranked-btn"${colorFn(name)}${clickAttr} title="${escapeHtml(name)} (${count})">
                            ${iconFn(name)}
                            <span class="ranked-btn-label">${escapeHtml(name)}</span>
                            <span class="ranked-btn-count">${count}</span>
                        </div>`;
                    }).join('') + '</div>';
                } else {
                    const iconFn = getIcon || (typeof getIconHtml === 'function' ? (n) => getIconHtml(filterType, n) : () => '');
                    const colorFn = getColor || (typeof getItemColorStyle === 'function' ? (n) => getItemColorStyle(filterType, n) : () => '');
                    bodyHtml = '<div class="ranked-list">' + items.map(([name, count]) => {
                        const clickAttr = onItemClick ? ` onclick="RankedPanel._itemClick('${panelId}', '${escapeHtml(name)}')"` : '';
                        const clickClass = onItemClick ? ' ranked-item-clickable' : '';
                        return `<div class="ranked-item${clickClass}"${colorFn(name)}${clickAttr}>
                            <span class="rank-name">${iconFn(name)}${escapeHtml(name)}</span>
                            <span class="rank-count">${count}</span>
                        </div>`;
                    }).join('') + '</div>';
                }

                el.innerHTML = `<div class="rp-panel">
                    <div class="panel-header">
                        <h3>${icon ? icon + ' ' : ''}${escapeHtml(title)}</h3>
                        <div class="panel-header-actions">${showAllBtn}${viewBtn}</div>
                    </div>
                    <div class="rp-body">${bodyHtml}</div>
                </div>`;
            }
        };

        this._instances[id] = instance;
        return instance;
    },

    get(id) {
        return this._instances[id];
    },

    toggleView(id) {
        const inst = this._instances[id];
        if (!inst) return;
        inst._viewMode = inst._viewMode === 'list' ? 'card' : 'list';
        Bootstrap.set('rp_view_' + id, inst._viewMode);
        inst.render();
    },

    toggleShowAll(id) {
        const inst = this._instances[id];
        if (!inst) return;
        inst._showAll = !inst._showAll;
        inst.render();
    },

    _itemClick(id, name) {
        const inst = this._instances[id];
        if (!inst || !inst._opts.onItemClick) return;
        const item = inst._data.find(([n]) => n === name);
        inst._opts.onItemClick(name, item ? item[1] : 0);
    }
};


const RecordViewer = {
    /**
     * Show a detail overlay for one record.
     *
     * @param {Object} opts
     * @param {Array<Object>} opts.data      — full data array (for prev/next nav)
     * @param {number}        opts.index     — current record index
     * @param {Array<Object>} opts.fields    — field definitions:
     *   { key, label, formatter?(value, row) }
     *   If no fields provided, all keys of the record are shown.
     * @param {string}  [opts.highlightTerm] — term to highlight in values
     * @param {boolean} [opts.wholeWord=false]
     * @param {Function} [opts.onNavigate]   — callback(row, index) on prev/next
     */
    show(opts) {
        const {
            data = [],
            index = 0,
            fields,
            highlightTerm,
            wholeWord = false,
            onNavigate
        } = opts;

        this._opts = opts;

        if (!data.length || index < 0 || index >= data.length) return;
        const record = data[index];

        // Determine fields to display
        const fieldDefs = fields || Object.keys(record).map(k => ({ key: k, label: k }));

        const rows = fieldDefs.map(f => {
            let val;
            if (f.formatter) {
                val = f.formatter(record[f.key], record);
            } else {
                const raw = record[f.key];
                if (raw == null) val = '';
                else if (typeof raw === 'object') val = JSON.stringify(raw, null, 2);
                else val = String(raw);
                val = escapeHtml(val);
            }
            if (highlightTerm) {
                val = ResultGrid._highlight(val, highlightTerm, wholeWord);
            }
            return `<tr>
                <td class="rv-label">${escapeHtml(f.label)}</td>
                <td class="rv-value">${val}</td>
            </tr>`;
        }).join('');

        const prevBtn = index > 0
            ? `<button class="btn-small" onclick="event.stopPropagation(); RecordViewer._nav(${index - 1})">&larr; Prev</button>` : '';
        const nextBtn = index < data.length - 1
            ? `<button class="btn-small" onclick="event.stopPropagation(); RecordViewer._nav(${index + 1})">Next &rarr;</button>` : '';

        const html = `<div class="rv-overlay" onclick="if(event.target===this)RecordViewer.close()">
            <div class="rv-panel">
                <div class="rv-header">
                    <span>Record ${index + 1} of ${data.length}</span>
                    <div class="rv-nav">
                        ${prevBtn}${nextBtn}
                        <button class="btn-small" onclick="event.stopPropagation(); RecordViewer.close()">&times;</button>
                    </div>
                </div>
                <div class="rv-body">
                    <table class="rv-table"><tbody>${rows}</tbody></table>
                </div>
            </div>
        </div>`;

        this.close();
        document.body.insertAdjacentHTML('beforeend', html);
    },

    _nav(newIndex) {
        const opts = this._opts;
        opts.index = newIndex;
        this.show(opts);
        if (opts.onNavigate) opts.onNavigate(opts.data[newIndex], newIndex);
    },

    close() {
        const el = document.querySelector('.rv-overlay');
        if (el) el.remove();
    }
};


/**
 * CollapsiblePanel — reusable collapsible container with pin-to-expand support.
 *
 * Collapsed by default. Click header to toggle. Pin button keeps it always expanded.
 * Pin state is persisted to Bootstrap (IndexedDB).
 */
const CollapsiblePanel = {
    _storageKey: 'cp_pinned',

    _getPinned() {
        try { return JSON.parse(Bootstrap.get(this._storageKey) || '[]'); }
        catch { return []; }
    },

    _savePinned(arr) {
        Bootstrap.set(this._storageKey, JSON.stringify(arr));
    },

    /**
     * Render a collapsible panel and inject into a container element.
     *
     * @param {Object} opts
     * @param {string}  opts.id           — unique panel id (used for pin persistence)
     * @param {string}  opts.containerId  — id of wrapper element (innerHTML replaced)
     * @param {string}  opts.title        — panel heading text
     * @param {string}  [opts.icon]       — emoji/icon before title
     * @param {string}  opts.bodyHTML     — inner HTML content of the panel body
     * @param {boolean} [opts.collapsed]  — override initial state (default: true unless pinned)
     */
    render(opts) {
        const { id, containerId, title, icon = '', bodyHTML = '' } = opts;
        const el = document.getElementById(containerId);
        if (!el) return;

        const pinned = this._getPinned();
        const isPinned = pinned.includes(id);
        const collapsed = opts.collapsed !== undefined ? opts.collapsed : !isPinned;

        const colClass = collapsed ? ' collapsed' : '';
        const pinClass = isPinned ? ' pinned' : '';
        const pinTitle = isPinned ? 'Unpin (stay expanded)' : 'Pin (stay expanded)';

        el.innerHTML = `<div class="cp-section" data-cp-id="${id}">
            <span class="cp-pin${pinClass}" onclick="CollapsiblePanel._togglePin('${id}')" title="${pinTitle}">&#128204;</span>
            <h3 class="cp-header${colClass}" onclick="CollapsiblePanel._toggle('${id}')">
                ${icon ? `<span class="cp-icon">${icon}</span>` : ''}${escapeHtml(title)}<span class="collapse-arrow">&#9660;</span>
            </h3>
            <div class="cp-body${colClass}">${bodyHTML}</div>
        </div>`;
    },

    /**
     * Update only the body content without re-rendering the full panel.
     * Preserves collapsed/pinned state.
     */
    updateBody(id, bodyHTML) {
        const section = document.querySelector(`.cp-section[data-cp-id="${id}"]`);
        if (!section) return;
        const body = section.querySelector('.cp-body');
        if (body) body.innerHTML = bodyHTML;
    },

    _getSection(id) {
        return document.querySelector(`.cp-section[data-cp-id="${id}"]`);
    },

    _toggle(id) {
        const section = this._getSection(id);
        if (!section) return;
        const header = section.querySelector('.cp-header');
        const body = section.querySelector('.cp-body');
        const collapsed = !body.classList.contains('collapsed');
        header.classList.toggle('collapsed', collapsed);
        body.classList.toggle('collapsed', collapsed);
    },

    _togglePin(id) {
        let pinned = this._getPinned();
        const isPinned = pinned.includes(id);
        if (isPinned) {
            pinned = pinned.filter(k => k !== id);
        } else {
            pinned.push(id);
        }
        this._savePinned(pinned);

        const section = this._getSection(id);
        if (!section) return;
        const pin = section.querySelector('.cp-pin');
        pin.classList.toggle('pinned', !isPinned);
        pin.title = !isPinned ? 'Unpin (stay expanded)' : 'Pin (stay expanded)';

        // Expand when pinning
        if (!isPinned) {
            section.querySelector('.cp-header').classList.remove('collapsed');
            section.querySelector('.cp-body').classList.remove('collapsed');
        }
    },

    /** Check if a panel is currently pinned. */
    isPinned(id) {
        return this._getPinned().includes(id);
    },

    /** Expand a panel programmatically. */
    expand(id) {
        const section = this._getSection(id);
        if (!section) return;
        section.querySelector('.cp-header').classList.remove('collapsed');
        section.querySelector('.cp-body').classList.remove('collapsed');
    },

    /** Collapse a panel programmatically. */
    collapse(id) {
        const section = this._getSection(id);
        if (!section) return;
        section.querySelector('.cp-header').classList.add('collapsed');
        section.querySelector('.cp-body').classList.add('collapsed');
    }
};
