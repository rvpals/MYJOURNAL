/**
 * Report generation: HTML view, print, CSV, PDF.
 */

function prepareReports() {
    // Populate category and tag dropdowns
    const catSelect = document.getElementById('report-category');
    const tagSelect = document.getElementById('report-tag');

    const categories = DB.getCategories();
    catSelect.innerHTML = '<option value="">All</option>' +
        categories.map(c => `<option value="${c}">${c}</option>`).join('');

    const tags = DB.getAllTags();
    tagSelect.innerHTML = '<option value="">All</option>' +
        tags.map(t => `<option value="${t}">${t}</option>`).join('');

    document.getElementById('report-output').innerHTML = '<div class="no-data">Configure filters and generate a report.</div>';

    // Populate template selector
    populateTemplateSelect();
}

function getFilteredReportEntries() {
    const dateFrom = document.getElementById('report-date-from').value;
    const dateTo = document.getElementById('report-date-to').value;
    const category = document.getElementById('report-category').value;
    const tag = document.getElementById('report-tag').value;

    let entries = DB.getEntries();

    if (dateFrom) entries = entries.filter(e => e.date >= dateFrom);
    if (dateTo) entries = entries.filter(e => e.date <= dateTo);
    if (category) entries = entries.filter(e => (e.categories || []).includes(category));
    if (tag) entries = entries.filter(e => (e.tags || []).includes(tag));

    entries.sort((a, b) => (a.date || '').localeCompare(b.date || ''));

    return entries;
}

function generateReport(format) {
    const entries = getFilteredReportEntries();

    if (entries.length === 0) {
        document.getElementById('report-output').innerHTML = '<div class="no-data">No entries match the selected filters.</div>';
        if (format !== 'html') alert('No entries to export.');
        return;
    }

    switch (format) {
        case 'html':
            renderHtmlReport(entries);
            break;
        case 'print':
            renderHtmlReport(entries);
            setTimeout(() => window.print(), 300);
            break;
        case 'csv':
            exportCsv(entries);
            break;
        case 'pdf':
            exportPdf(entries);
            break;
    }
}

function renderHtmlReport(entries) {
    const output = document.getElementById('report-output');

    const summary = `
        <div style="margin-bottom:1rem">
            <strong>Total entries:</strong> ${entries.length} |
            <strong>Date range:</strong> ${formatDate(entries[0].date)} - ${formatDate(entries[entries.length - 1].date)}
        </div>
    `;

    const table = `
        <table>
            <thead>
                <tr>
                    <th>Date</th>
                    <th>Time</th>
                    <th>Title</th>
                    <th>Content</th>
                    <th>Categories</th>
                    <th>Tags</th>
                    <th>Places</th>
                </tr>
            </thead>
            <tbody>
                ${entries.map(e => `
                    <tr>
                        <td>${formatDate(e.date)}</td>
                        <td>${e.time || ''}</td>
                        <td>${escapeHtml(e.title || '')}</td>
                        <td>${escapeHtml((e.content || '').substring(0, 100))}${(e.content || '').length > 100 ? '...' : ''}</td>
                        <td>${(e.categories || []).join(', ')}</td>
                        <td>${(e.tags || []).join(', ')}</td>
                        <td>${escapeHtml(e.placeName || '')}${(e.locations || []).length > 0 ? ': ' + (e.locations || []).map(l => {
                            const parts = [];
                            if (l.name) parts.push(l.name);
                            if (l.address) parts.push(l.address);
                            if (l.lat != null) parts.push('[' + l.lat + ',' + l.lng + ']');
                            return escapeHtml(parts.join(' — '));
                        }).join('; ') : ''}</td>
                    </tr>
                `).join('')}
            </tbody>
        </table>
    `;

    output.innerHTML = summary + table;
}

function exportCsv(entries) {
    const headers = ['Date', 'Time', 'Title', 'Content', 'Categories', 'Tags', 'Places', 'Created', 'Updated'];
    const rows = entries.map(e => [
        csvEscape(e.date || ''),
        csvEscape(e.time || ''),
        csvEscape(e.title || ''),
        csvEscape(e.content || ''),
        csvEscape((e.categories || []).join('; ')),
        csvEscape((e.tags || []).join('; ')),
        csvEscape((e.placeName || '') + ((e.locations || []).length > 0 ? ': ' + (e.locations || []).map(l => {
            const parts = [];
            if (l.name) parts.push(l.name);
            if (l.address) parts.push(l.address);
            if (l.lat != null) parts.push(l.lat + ',' + l.lng);
            return parts.join(' | ');
        }).join('; ') : '')),
        csvEscape(e.dtCreated || ''),
        csvEscape(e.dtUpdated || '')
    ]);

    const csv = [headers.map(h => csvEscape(h)).join(','), ...rows.map(r => r.join(','))].join('\n');
    downloadFile(csv, 'journal_report.csv', 'text/csv');
}

function csvEscape(str) {
    if (!str) return '""';
    return '"' + str.replace(/"/g, '""') + '"';
}

function exportPdf(entries) {
    const { jsPDF } = window.jspdf;
    const doc = new jsPDF();
    const pageWidth = doc.internal.pageSize.getWidth();
    const margin = 15;
    const maxWidth = pageWidth - margin * 2;
    let y = 20;

    doc.setFontSize(18);
    doc.text('Journal Report', margin, y);
    y += 10;

    doc.setFontSize(10);
    doc.text(`Total entries: ${entries.length}`, margin, y);
    y += 8;

    entries.forEach((e, i) => {
        if (y > 260) {
            doc.addPage();
            y = 20;
        }

        doc.setFontSize(11);
        doc.setFont(undefined, 'bold');
        const titleLine = `${e.date || ''}${e.time ? ' ' + e.time : ''} - ${e.title || 'Untitled'}`;
        doc.text(titleLine, margin, y);
        y += 6;

        doc.setFont(undefined, 'normal');
        doc.setFontSize(9);

        if (e.content) {
            const lines = doc.splitTextToSize(e.content, maxWidth);
            const displayLines = lines.slice(0, 6); // Limit content lines per entry
            doc.text(displayLines, margin, y);
            y += displayLines.length * 4 + 2;
        }

        if ((e.categories || []).length > 0) {
            doc.text('Categories: ' + e.categories.join(', '), margin, y);
            y += 4;
        }

        if ((e.tags || []).length > 0) {
            doc.text('Tags: ' + e.tags.join(', '), margin, y);
            y += 4;
        }

        if (e.placeName || (e.locations || []).length > 0) {
            let placeStr = e.placeName || '';
            if ((e.locations || []).length > 0) {
                const locStrs = e.locations.map(l => {
                    const parts = [];
                    if (l.name) parts.push(l.name);
                    if (l.address) parts.push(l.address);
                    if (l.lat != null) parts.push('(' + l.lat + ', ' + l.lng + ')');
                    return parts.join(' — ');
                });
                placeStr += (placeStr ? ': ' : '') + locStrs.join('; ');
            }
            const placeText = doc.splitTextToSize('Places: ' + placeStr, maxWidth);
            doc.text(placeText, margin, y);
            y += placeText.length * 4;
        }

        y += 4; // Gap between entries
    });

    // Android WebView: use native bridge for PDF save
    if (window.AndroidBridge) {
        const base64 = doc.output('datauristring').split(',')[1];
        window.AndroidBridge.saveFile('journal_report.pdf', base64, 'application/pdf');
        return;
    }
    doc.save('journal_report.pdf');
}

// ========== Custom Template Reports ==========

function populateTemplateSelect() {
    const select = document.getElementById('report-template-select');
    const settings = DB.getSettings();
    const templates = settings.reportTemplates || [];
    select.innerHTML = '<option value="">-- Select a template --</option>' +
        templates.map(t => `<option value="${t.id}">${escapeHtml(t.name)}</option>`).join('');
    onReportTemplateChange();
}

function onReportTemplateChange() {
    const select = document.getElementById('report-template-select');
    const editBtn = document.getElementById('btn-toggle-tpl-editor');
    const editorDiv = document.getElementById('report-template-editor');
    if (select.value) {
        editBtn.style.display = '';
        // If editor is open, load the newly selected template
        if (editorDiv.style.display !== 'none') {
            loadReportTemplateIntoEditor();
        }
    } else {
        editBtn.style.display = 'none';
        editorDiv.style.display = 'none';
    }
}

function toggleReportTemplateEditor() {
    const editorDiv = document.getElementById('report-template-editor');
    if (editorDiv.style.display === 'none') {
        loadReportTemplateIntoEditor();
        editorDiv.style.display = 'block';
    } else {
        editorDiv.style.display = 'none';
    }
}

function loadReportTemplateIntoEditor() {
    const templateId = document.getElementById('report-template-select').value;
    if (!templateId) return;
    const settings = DB.getSettings();
    const tpl = (settings.reportTemplates || []).find(t => t.id === templateId);
    if (tpl) {
        document.getElementById('report-template-textarea').value = tpl.html;
    }
}

async function saveReportTemplateFromEditor() {
    const templateId = document.getElementById('report-template-select').value;
    if (!templateId) return;
    const settings = DB.getSettings();
    const templates = settings.reportTemplates || [];
    const tpl = templates.find(t => t.id === templateId);
    if (!tpl) return;

    tpl.html = document.getElementById('report-template-textarea').value;
    await DB.setSettings({ reportTemplates: templates });
    alert('Template saved.');
}

function applyTemplateToEntry(templateHtml, entry) {
    const locStrs = (entry.locations || []).map(l => {
        const parts = [];
        if (l.name) parts.push(l.name);
        if (l.address) parts.push(l.address);
        if (l.lat != null) parts.push('(' + l.lat + ', ' + l.lng + ')');
        return parts.join(' — ');
    });
    const placesFormatted = (entry.placeName || '') + (locStrs.length > 0 ? (entry.placeName ? ': ' : '') + locStrs.join('; ') : '');

    const placeNames = entry.placeName || '';
    const placeAddresses = (entry.locations || []).map(l => [l.name, l.address].filter(Boolean).join(' — ')).filter(Boolean).join('; ');
    const placeCoords = (entry.locations || []).map(l => l.lat != null ? l.lat + ',' + l.lng : '').filter(Boolean).join('; ');

    const weatherStr = entry.weather ? Weather.formatWeather(entry.weather) : '';
    const weatherTemp = entry.weather ? (entry.weather.temp + '\u00B0' + (entry.weather.unit === 'celsius' ? 'C' : 'F')) : '';
    const weatherDesc = entry.weather ? (entry.weather.description || '') : '';

    const replacements = {
        '<%ID%>': entry.id || '',
        '<%TITLE%>': escapeHtml(entry.title || ''),
        '<%DATE%>': formatDate(entry.date) || '',
        '<%TIME%>': entry.time || '',
        '<%CONTENT%>': escapeHtml(entry.content || '').replace(/\n/g, '<br>'),
        '<%CATEGORIES%>': (entry.categories || []).join(', '),
        '<%TAGS%>': (entry.tags || []).join(', '),
        '<%PLACES%>': escapeHtml(placesFormatted),
        '<%PLACE_NAMES%>': escapeHtml(placeNames),
        '<%PLACE_ADDRESSES%>': escapeHtml(placeAddresses),
        '<%PLACE_COORDS%>': placeCoords,
        '<%WEATHER%>': escapeHtml(weatherStr),
        '<%WEATHER_TEMP%>': escapeHtml(weatherTemp),
        '<%WEATHER_DESC%>': escapeHtml(weatherDesc),
        '<%DT_CREATED%>': entry.dtCreated ? new Date(entry.dtCreated).toLocaleString() : '',
        '<%DT_UPDATED%>': entry.dtUpdated ? new Date(entry.dtUpdated).toLocaleString() : ''
    };

    let result = templateHtml;
    for (const [tag, value] of Object.entries(replacements)) {
        result = result.split(tag).join(value);
    }
    return result;
}

function generateTemplateReport(action) {
    const select = document.getElementById('report-template-select');
    const templateId = select.value;
    if (!templateId) {
        alert('Please select a template.');
        return;
    }

    const settings = DB.getSettings();
    const templates = settings.reportTemplates || [];
    const tpl = templates.find(t => t.id === templateId);
    if (!tpl) {
        alert('Template not found.');
        return;
    }

    const entries = getFilteredReportEntries();
    if (entries.length === 0) {
        alert('No entries match the selected filters.');
        return;
    }

    // Generate one HTML page per entry, concatenated
    const allHtml = entries.map(e => applyTemplateToEntry(tpl.html, e)).join('\n<hr style="page-break-after:always; margin:2rem 0;">\n');

    if (action === 'download') {
        downloadFile(allHtml, tpl.name.replace(/[^a-zA-Z0-9]/g, '_') + '_report.html', 'text/html');
    } else {
        // Show in report output area via iframe
        const output = document.getElementById('report-output');
        const iframe = document.createElement('iframe');
        iframe.className = 'template-report-iframe';
        iframe.sandbox = 'allow-same-origin';
        output.innerHTML = '';
        output.appendChild(iframe);
        iframe.srcdoc = allHtml;
    }
}

// ========== Print PDF from Entry View ==========

function openPrintModal() {
    if (!currentViewEntryId) return;

    const select = document.getElementById('print-template-select');
    const settings = DB.getSettings();
    const templates = settings.reportTemplates || [];
    select.innerHTML = '<option value="">-- Default (no template) --</option>' +
        templates.map(t => `<option value="${t.id}">${escapeHtml(t.name)}</option>`).join('');

    const preview = document.getElementById('print-template-preview');
    preview.classList.remove('active');
    preview.innerHTML = '';

    select.onchange = function () {
        if (!this.value) {
            preview.classList.remove('active');
            preview.innerHTML = '';
            return;
        }
        const tpl = templates.find(t => t.id === this.value);
        if (tpl) {
            const entry = DB.getEntries().find(e => e.id === currentViewEntryId);
            if (entry) {
                const html = applyTemplateToEntry(tpl.html, entry);
                const iframe = document.createElement('iframe');
                iframe.sandbox = 'allow-same-origin';
                iframe.style.cssText = 'width:100%;height:180px;border:none;border-radius:4px;background:white;';
                preview.innerHTML = '';
                preview.appendChild(iframe);
                iframe.srcdoc = html;
                preview.classList.add('active');
            }
        }
    };

    document.getElementById('print-modal').style.display = 'flex';
}

function closePrintModal(event) {
    if (event && event.target !== event.currentTarget) return;
    document.getElementById('print-modal').style.display = 'none';
}

function printEntryPdf() {
    const entry = DB.getEntries().find(e => e.id === currentViewEntryId);
    if (!entry) return;

    const templateId = document.getElementById('print-template-select').value;

    if (templateId) {
        // Template-based PDF: render HTML to an iframe and print
        const settings = DB.getSettings();
        const tpl = (settings.reportTemplates || []).find(t => t.id === templateId);
        if (!tpl) { alert('Template not found.'); return; }

        const html = applyTemplateToEntry(tpl.html, entry);
        const printWindow = window.open('', '_blank', 'width=800,height=600');
        if (!printWindow) { alert('Pop-up blocked. Please allow pop-ups for this site.'); return; }
        printWindow.document.write(`<!DOCTYPE html><html><head><title>${escapeHtml(entry.title || 'Entry')}</title></head><body>${html}</body></html>`);
        printWindow.document.close();
        printWindow.onload = function () {
            printWindow.focus();
            printWindow.print();
            printWindow.close();
        };
    } else {
        // Default PDF using jsPDF
        exportSingleEntryPdf(entry);
    }

    closePrintModal();
}

function exportSingleEntryPdf(entry) {
    const { jsPDF } = window.jspdf;
    const doc = new jsPDF();
    const pageWidth = doc.internal.pageSize.getWidth();
    const margin = 15;
    const maxWidth = pageWidth - margin * 2;
    let y = 20;

    // Title
    doc.setFontSize(16);
    doc.setFont(undefined, 'bold');
    const titleLines = doc.splitTextToSize(entry.title || 'Untitled', maxWidth);
    doc.text(titleLines, margin, y);
    y += titleLines.length * 7 + 2;

    // Date / Time
    const dateTime = (entry.date ? formatDate(entry.date) : '') + (entry.time ? ' ' + entry.time : '');
    if (dateTime.trim()) {
        doc.setFontSize(10);
        doc.setFont(undefined, 'normal');
        doc.setTextColor(100);
        doc.text(dateTime.trim(), margin, y);
        doc.setTextColor(0);
        y += 6;
    }

    y += 2;
    doc.setDrawColor(180);
    doc.line(margin, y, pageWidth - margin, y);
    y += 6;

    doc.setFontSize(10);
    doc.setFont(undefined, 'normal');

    // Content
    if (entry.content) {
        doc.setFont(undefined, 'bold');
        doc.text('Content', margin, y);
        y += 5;
        doc.setFont(undefined, 'normal');
        const lines = doc.splitTextToSize(entry.content, maxWidth);
        doc.text(lines, margin, y);
        y += lines.length * 4.5 + 4;
    }

    // Categories
    if ((entry.categories || []).length > 0) {
        if (y > 270) { doc.addPage(); y = 20; }
        doc.setFont(undefined, 'bold');
        doc.text('Categories', margin, y);
        y += 5;
        doc.setFont(undefined, 'normal');
        doc.text(entry.categories.join(', '), margin, y);
        y += 6;
    }

    // Tags
    if ((entry.tags || []).length > 0) {
        if (y > 270) { doc.addPage(); y = 20; }
        doc.setFont(undefined, 'bold');
        doc.text('Tags', margin, y);
        y += 5;
        doc.setFont(undefined, 'normal');
        doc.text(entry.tags.join(', '), margin, y);
        y += 6;
    }

    // Place
    if (entry.placeName) {
        if (y > 270) { doc.addPage(); y = 20; }
        doc.setFont(undefined, 'bold');
        doc.text('Place', margin, y);
        y += 5;
        doc.setFont(undefined, 'normal');
        doc.text(entry.placeName, margin, y);
        y += 6;
    }

    // Locations
    const locs = entry.locations || [];
    if (locs.length > 0) {
        if (y > 270) { doc.addPage(); y = 20; }
        doc.setFont(undefined, 'bold');
        doc.text('Locations', margin, y);
        y += 5;
        doc.setFont(undefined, 'normal');
        locs.forEach(loc => {
            const parts = [];
            if (loc.name) parts.push(loc.name);
            if (loc.address) parts.push(loc.address);
            if (loc.lat != null) parts.push('(' + loc.lat + ', ' + loc.lng + ')');
            const s = parts.join(' — ');
            const locLines = doc.splitTextToSize(s, maxWidth);
            if (y + locLines.length * 4.5 > 280) { doc.addPage(); y = 20; }
            doc.text(locLines, margin, y);
            y += locLines.length * 4.5 + 1;
        });
        y += 3;
    }

    // Weather
    if (entry.weather) {
        if (y > 270) { doc.addPage(); y = 20; }
        doc.setFont(undefined, 'bold');
        doc.text('Weather', margin, y);
        y += 5;
        doc.setFont(undefined, 'normal');
        doc.text(Weather.formatWeather(entry.weather), margin, y);
        y += 6;
    }

    // Timestamps
    if (y > 270) { doc.addPage(); y = 20; }
    y += 4;
    doc.setFontSize(8);
    doc.setTextColor(140);
    if (entry.dtCreated) doc.text('Created: ' + new Date(entry.dtCreated).toLocaleString(), margin, y);
    if (entry.dtCreated) y += 4;
    if (entry.dtUpdated) doc.text('Updated: ' + new Date(entry.dtUpdated).toLocaleString(), margin, y);
    doc.setTextColor(0);

    const filename = (entry.title || 'entry').replace(/[^a-zA-Z0-9 ]/g, '').replace(/\s+/g, '_') + '.pdf';

    if (window.AndroidBridge) {
        const base64 = doc.output('datauristring').split(',')[1];
        window.AndroidBridge.saveFile(filename, base64, 'application/pdf');
        return;
    }
    doc.save(filename);
}

function downloadFile(content, filename, type) {
    // Android WebView: use native bridge (blob URLs don't work in WebView)
    if (window.AndroidBridge) {
        const base64 = btoa(unescape(encodeURIComponent(content)));
        window.AndroidBridge.saveFile(filename, base64, type);
        return;
    }
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}
