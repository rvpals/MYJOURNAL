/**
 * Google Drive backup/restore module.
 * Uses Google Identity Services for OAuth2 and Drive REST API v3.
 * Stores encrypted journal backups in a "Journal Backups" folder on Drive.
 */

const GDrive = (() => {
    const SCOPES = 'https://www.googleapis.com/auth/drive.file';
    const FOLDER_NAME = 'Journal Backups';
    const CLIENT_ID_KEY = 'gdrive_client_id';

    let tokenClient = null;
    let accessToken = null;
    let userEmail = null;

    function getClientId() {
        return localStorage.getItem(CLIENT_ID_KEY) || '';
    }

    function setClientId(id) {
        localStorage.setItem(CLIENT_ID_KEY, id.trim());
        tokenClient = null; // force re-init
        accessToken = null;
    }

    function isConfigured() {
        return !!getClientId();
    }

    function isSignedIn() {
        return !!accessToken;
    }

    function getUserEmail() {
        return userEmail;
    }

    // Fetch the signed-in user's email from Google
    async function fetchUserEmail() {
        try {
            const resp = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
                headers: { 'Authorization': `Bearer ${accessToken}` }
            });
            if (resp.ok) {
                const info = await resp.json();
                userEmail = info.email || null;
            }
        } catch {
            userEmail = null;
        }
    }

    // Initialize the GIS token client
    function initTokenClient() {
        if (tokenClient) return tokenClient;
        if (!window.google || !google.accounts) {
            throw new Error('Google Identity Services not loaded. Check your internet connection.');
        }
        tokenClient = google.accounts.oauth2.initTokenClient({
            client_id: getClientId(),
            scope: SCOPES,
            callback: () => {} // overridden per-call
        });
        return tokenClient;
    }

    // Request an access token. selectAccount=true forces the account picker.
    function authorize(selectAccount) {
        return new Promise((resolve, reject) => {
            if (!isConfigured()) {
                reject(new Error('Google Drive Client ID not configured. Set it in Settings.'));
                return;
            }
            try {
                const client = initTokenClient();
                client.callback = async (response) => {
                    if (response.error) {
                        reject(new Error(response.error_description || response.error));
                        return;
                    }
                    accessToken = response.access_token;
                    await fetchUserEmail();
                    resolve(accessToken);
                };
                if (selectAccount) {
                    client.requestAccessToken({ prompt: 'select_account' });
                } else {
                    client.requestAccessToken({ prompt: '' });
                }
            } catch (err) {
                reject(err);
            }
        });
    }

    // Switch to a different Google account (forces account picker)
    function switchAccount() {
        if (accessToken) {
            google.accounts.oauth2.revoke(accessToken);
            accessToken = null;
            userEmail = null;
        }
        tokenClient = null; // force re-init to clear cached consent
        return authorize(true);
    }

    function signOut() {
        if (accessToken) {
            google.accounts.oauth2.revoke(accessToken);
            accessToken = null;
            userEmail = null;
        }
    }

    // Drive API helpers
    async function driveRequest(url, options = {}) {
        if (!accessToken) throw new Error('Not signed in to Google Drive.');
        const resp = await fetch(url, {
            ...options,
            headers: {
                'Authorization': `Bearer ${accessToken}`,
                ...(options.headers || {})
            }
        });
        if (resp.status === 401) {
            accessToken = null;
            throw new Error('Google Drive session expired. Please sign in again.');
        }
        if (!resp.ok) {
            const err = await resp.text();
            throw new Error(`Drive API error (${resp.status}): ${err}`);
        }
        return resp;
    }

    // Find or create the backup folder
    async function getBackupFolderId() {
        const query = `name='${FOLDER_NAME}' and mimeType='application/vnd.google-apps.folder' and trashed=false`;
        const resp = await driveRequest(
            `https://www.googleapis.com/drive/v3/files?q=${encodeURIComponent(query)}&fields=files(id,name)`
        );
        const data = await resp.json();
        if (data.files && data.files.length > 0) {
            return data.files[0].id;
        }
        // Create the folder
        const createResp = await driveRequest('https://www.googleapis.com/drive/v3/files', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name: FOLDER_NAME,
                mimeType: 'application/vnd.google-apps.folder'
            })
        });
        const folder = await createResp.json();
        return folder.id;
    }

    // Get backup filename for a journal
    function backupFilename(journalId) {
        const journal = DB.getJournalList().find(j => j.id === journalId);
        const name = journal ? journal.name : journalId;
        return `${name}_backup.json`;
    }

    // Find an existing backup file in the folder
    async function findBackupFile(folderId, filename) {
        const query = `name='${filename}' and '${folderId}' in parents and trashed=false`;
        const resp = await driveRequest(
            `https://www.googleapis.com/drive/v3/files?q=${encodeURIComponent(query)}&fields=files(id,name,modifiedTime,size)`
        );
        const data = await resp.json();
        return (data.files && data.files.length > 0) ? data.files[0] : null;
    }

    // Upload (create or update) a backup file
    async function backup() {
        const json = await DB.exportJSON();
        if (!json) throw new Error('No data to backup.');

        if (!accessToken) await authorize();

        const folderId = await getBackupFolderId();
        const filename = backupFilename(DB.getJournalId());
        const existing = await findBackupFile(folderId, filename);

        if (existing) {
            // Update existing file
            await driveRequest(
                `https://www.googleapis.com/upload/drive/v3/files/${existing.id}?uploadType=media`,
                {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: json
                }
            );
        } else {
            // Create new file with multipart upload
            const metadata = {
                name: filename,
                parents: [folderId],
                mimeType: 'application/json'
            };
            const form = new FormData();
            form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
            form.append('file', new Blob([json], { type: 'application/json' }));

            await driveRequest(
                'https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart',
                { method: 'POST', body: form }
            );
        }

        return filename;
    }

    // Download and restore a backup file
    async function restore() {
        if (!accessToken) await authorize();

        const folderId = await getBackupFolderId();
        const filename = backupFilename(DB.getJournalId());
        const file = await findBackupFile(folderId, filename);

        if (!file) {
            throw new Error(`No backup found for this journal ("${filename}") on Google Drive.`);
        }

        const resp = await driveRequest(
            `https://www.googleapis.com/drive/v3/files/${file.id}?alt=media`
        );
        const json = await resp.text();
        await DB.importJSON(json);

        return { filename, modifiedTime: file.modifiedTime };
    }

    // List all backup files in the folder
    async function listBackups() {
        if (!accessToken) await authorize();

        const folderId = await getBackupFolderId();
        const query = `'${folderId}' in parents and trashed=false`;
        const resp = await driveRequest(
            `https://www.googleapis.com/drive/v3/files?q=${encodeURIComponent(query)}&fields=files(id,name,modifiedTime,size)&orderBy=modifiedTime desc`
        );
        const data = await resp.json();
        return data.files || [];
    }

    // ========== Auto-Sync ==========

    const SYNC_ENABLED_KEY = 'gdrive_auto_sync';
    let syncDebounceTimer = null;
    let syncInProgress = false;

    function isAutoSyncEnabled() {
        return localStorage.getItem(SYNC_ENABLED_KEY) === 'true';
    }

    function setAutoSync(enabled) {
        localStorage.setItem(SYNC_ENABLED_KEY, enabled ? 'true' : 'false');
    }

    function setSyncStatus(status) {
        const el = document.getElementById('sync-status');
        if (!el) return;
        el.className = 'sync-status';
        if (status === 'syncing') {
            el.textContent = '\u21BB';
            el.title = 'Syncing...';
            el.classList.add('sync-spinning');
        } else if (status === 'done') {
            el.textContent = '\u2713';
            el.title = 'Synced';
            el.classList.add('sync-done');
            setTimeout(() => {
                el.textContent = '';
                el.className = 'sync-status';
            }, 3000);
        } else if (status === 'error') {
            el.textContent = '\u26A0';
            el.title = 'Sync failed';
            el.classList.add('sync-error');
        } else {
            el.textContent = '';
        }
    }

    // Pull from Drive if remote is newer. Returns true if data was updated.
    async function autoPull() {
        if (!isAutoSyncEnabled() || !isConfigured()) return false;

        try {
            if (!accessToken) await authorize(false);
        } catch {
            return false; // Can't auth silently, skip
        }

        try {
            setSyncStatus('syncing');
            const folderId = await getBackupFolderId();
            const filename = backupFilename(DB.getJournalId());
            const file = await findBackupFile(folderId, filename);

            if (!file) {
                // No remote file yet — push current data up
                await backup();
                setSyncStatus('done');
                return false;
            }

            // Compare timestamps: check local last sync time
            const localSyncTime = localStorage.getItem('gdrive_last_sync_' + DB.getJournalId());
            const remoteTime = new Date(file.modifiedTime).getTime();

            if (localSyncTime && remoteTime <= parseInt(localSyncTime)) {
                setSyncStatus('done');
                return false; // Local is up-to-date
            }

            // Remote is newer — download and import
            const resp = await driveRequest(
                `https://www.googleapis.com/drive/v3/files/${file.id}?alt=media`
            );
            const json = await resp.text();
            await DB.importJSON(json);
            localStorage.setItem('gdrive_last_sync_' + DB.getJournalId(), Date.now().toString());
            setSyncStatus('done');
            return true;
        } catch (err) {
            console.warn('Auto-pull failed:', err.message);
            setSyncStatus('error');
            return false;
        }
    }

    // Push to Drive (called after saves). Debounced.
    function autoPush() {
        if (!isAutoSyncEnabled() || !isConfigured() || !accessToken) return;

        if (syncDebounceTimer) clearTimeout(syncDebounceTimer);
        syncDebounceTimer = setTimeout(async () => {
            if (syncInProgress) return;
            syncInProgress = true;
            try {
                setSyncStatus('syncing');
                await backup();
                localStorage.setItem('gdrive_last_sync_' + DB.getJournalId(), Date.now().toString());
                setSyncStatus('done');
            } catch (err) {
                console.warn('Auto-push failed:', err.message);
                setSyncStatus('error');
            } finally {
                syncInProgress = false;
            }
        }, 2000); // 2 second debounce
    }

    return {
        getClientId, setClientId, isConfigured, isSignedIn, getUserEmail,
        authorize, switchAccount, signOut,
        backup, restore, listBackups,
        isAutoSyncEnabled, setAutoSync, autoPull, autoPush, setSyncStatus
    };
})();
