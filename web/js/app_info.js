/**
 * App info - loaded from app_info.xml at runtime.
 * To update app info, edit web/app_info.xml (single source of truth).
 */

var APP_INFO = { app_name: 'My Journal', app_version: '', app_email: '', app_url: '', app_company_url: '', app_description: '' };
var APP_CHANGELOG = [];
var _appInfoReady = false;
var _appInfoCallbacks = [];

function onAppInfoReady(cb) {
    if (_appInfoReady) { cb(); return; }
    _appInfoCallbacks.push(cb);
}

function _parseAppInfoXML(xmlText) {
    var doc = new DOMParser().parseFromString(xmlText, 'text/xml');
    var root = doc.documentElement;
    if (!root) return;

    var nodes = root.childNodes;
    var map = {};
    for (var i = 0; i < nodes.length; i++) {
        if (nodes[i].nodeType === 1 && nodes[i].nodeName !== 'changelog') {
            map[nodes[i].nodeName] = nodes[i].textContent.trim();
        }
    }

    APP_INFO = {
        app_name: map['name'] || 'My Journal',
        app_version: map['version'] || '',
        app_email: map['email'] || '',
        app_url: map['url'] || '',
        app_company_url: map['company-url'] || '',
        app_description: map['description'] || ''
    };

    var releases = doc.getElementsByTagName('release');
    for (var r = 0; r < releases.length; r++) {
        var rel = releases[r];
        var changes = [];
        var cNodes = rel.getElementsByTagName('change');
        for (var c = 0; c < cNodes.length; c++) {
            changes.push(cNodes[c].textContent.trim());
        }
        APP_CHANGELOG.push({
            version: rel.getAttribute('version') || '',
            date: rel.getAttribute('date') || '',
            changes: changes
        });
    }

    _appInfoReady = true;
    for (var j = 0; j < _appInfoCallbacks.length; j++) _appInfoCallbacks[j]();
    _appInfoCallbacks = [];
}

// Load app_info.xml from the same directory as index.html
(function() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', 'app_info.xml', true);
    xhr.onload = function() {
        if (xhr.status === 200 || xhr.status === 0) {
            _parseAppInfoXML(xhr.responseText);
        }
    };
    xhr.onerror = function() {
        // Fallback: already have defaults in APP_INFO
        _appInfoReady = true;
        for (var j = 0; j < _appInfoCallbacks.length; j++) _appInfoCallbacks[j]();
        _appInfoCallbacks = [];
    };
    xhr.send();
})();
