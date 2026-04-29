/**
 * Encryption module using AES-GCM.
 * On Android, delegates to native CryptoService via AndroidBridge.
 * In browser, uses Web Crypto API.
 */

const Crypto = (() => {
    const ITERATIONS = 100000;

    function saltKey(journalId) { return `journal_salt_${journalId}`; }
    function verifyKey(journalId) { return `journal_verify_${journalId}`; }

    function _hasNativeBridge() {
        return typeof window !== 'undefined' &&
            window.AndroidBridge &&
            typeof AndroidBridge.cryptoEncrypt === 'function';
    }

    // ========== Web Crypto helpers ==========

    function arrayToBase64(arr) {
        let binary = '';
        for (let i = 0; i < arr.length; i++) {
            binary += String.fromCharCode(arr[i]);
        }
        return btoa(binary);
    }

    function base64ToArray(b64) {
        const binary = atob(b64);
        const arr = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) {
            arr[i] = binary.charCodeAt(i);
        }
        return arr;
    }

    async function _webGetSalt(journalId) {
        let salt = Bootstrap.get(saltKey(journalId));
        if (!salt) {
            const arr = new Uint8Array(16);
            crypto.getRandomValues(arr);
            salt = arrayToBase64(arr);
            Bootstrap.set(saltKey(journalId), salt);
        }
        return base64ToArray(salt);
    }

    async function _webDeriveKey(password, salt) {
        const enc = new TextEncoder();
        const keyMaterial = await crypto.subtle.importKey(
            'raw', enc.encode(password), 'PBKDF2', false, ['deriveKey']
        );
        return crypto.subtle.deriveKey(
            { name: 'PBKDF2', salt, iterations: ITERATIONS, hash: 'SHA-256' },
            keyMaterial,
            { name: 'AES-GCM', length: 256 },
            false,
            ['encrypt', 'decrypt']
        );
    }

    // ========== Public API ==========

    async function encrypt(data, password, journalId) {
        if (_hasNativeBridge()) {
            const result = AndroidBridge.cryptoEncrypt(
                JSON.stringify(data), password, journalId
            );
            return JSON.parse(result);
        }

        const salt = await _webGetSalt(journalId);
        const key = await _webDeriveKey(password, salt);
        const enc = new TextEncoder();
        const iv = crypto.getRandomValues(new Uint8Array(12));
        const encrypted = await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv }, key, enc.encode(JSON.stringify(data))
        );
        return {
            iv: arrayToBase64(iv),
            data: arrayToBase64(new Uint8Array(encrypted))
        };
    }

    async function decrypt(encryptedObj, password, journalId) {
        if (_hasNativeBridge()) {
            const result = AndroidBridge.cryptoDecrypt(
                JSON.stringify(encryptedObj), password, journalId
            );
            return JSON.parse(result);
        }

        const salt = await _webGetSalt(journalId);
        const key = await _webDeriveKey(password, salt);
        const iv = base64ToArray(encryptedObj.iv);
        const data = base64ToArray(encryptedObj.data);
        const decrypted = await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv }, key, data
        );
        const dec = new TextDecoder();
        return JSON.parse(dec.decode(decrypted));
    }

    async function encryptBytes(uint8Array, password, journalId) {
        if (_hasNativeBridge()) {
            const b64 = arrayToBase64(uint8Array);
            const result = AndroidBridge.cryptoEncryptBytes(b64, password, journalId);
            return JSON.parse(result);
        }

        const salt = await _webGetSalt(journalId);
        const key = await _webDeriveKey(password, salt);
        const iv = crypto.getRandomValues(new Uint8Array(12));
        const encrypted = await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv }, key, uint8Array
        );
        return {
            iv: arrayToBase64(iv),
            data: arrayToBase64(new Uint8Array(encrypted)),
            format: 'sqlite'
        };
    }

    async function decryptBytes(encryptedObj, password, journalId) {
        if (_hasNativeBridge()) {
            const resultB64 = AndroidBridge.cryptoDecryptBytes(
                JSON.stringify(encryptedObj), password, journalId
            );
            return base64ToArray(resultB64);
        }

        const salt = await _webGetSalt(journalId);
        const key = await _webDeriveKey(password, salt);
        const iv = base64ToArray(encryptedObj.iv);
        const data = base64ToArray(encryptedObj.data);
        const decrypted = await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv }, key, data
        );
        return new Uint8Array(decrypted);
    }

    async function setupPassword(password, journalId) {
        if (_hasNativeBridge()) {
            AndroidBridge.cryptoSetupPassword(password, journalId);
            return;
        }

        const salt = await _webGetSalt(journalId);
        const key = await _webDeriveKey(password, salt);
        const verifyData = 'JOURNAL_VERIFY_TOKEN';
        const enc = new TextEncoder();
        const iv = crypto.getRandomValues(new Uint8Array(12));
        const encrypted = await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv }, key, enc.encode(verifyData)
        );
        const verifyObj = {
            iv: arrayToBase64(iv),
            data: arrayToBase64(new Uint8Array(encrypted))
        };
        Bootstrap.set(verifyKey(journalId), JSON.stringify(verifyObj));

        if (window.AndroidBridge && typeof AndroidBridge.syncCryptoKey === 'function') {
            AndroidBridge.syncCryptoKey(journalId, Bootstrap.get(saltKey(journalId)),
                JSON.stringify(verifyObj));
        }
    }

    async function verifyPassword(password, journalId) {
        if (_hasNativeBridge()) {
            return AndroidBridge.cryptoVerifyPassword(password, journalId);
        }

        const verifyStr = Bootstrap.get(verifyKey(journalId));
        if (!verifyStr) return false;
        try {
            const verifyObj = JSON.parse(verifyStr);
            const salt = await _webGetSalt(journalId);
            const key = await _webDeriveKey(password, salt);
            const iv = base64ToArray(verifyObj.iv);
            const data = base64ToArray(verifyObj.data);
            const decrypted = await crypto.subtle.decrypt(
                { name: 'AES-GCM', iv }, key, data
            );
            const dec = new TextDecoder();
            return dec.decode(decrypted) === 'JOURNAL_VERIFY_TOKEN';
        } catch {
            return false;
        }
    }

    function isSetup(journalId) {
        if (_hasNativeBridge()) {
            return AndroidBridge.cryptoIsSetup(journalId);
        }
        return Bootstrap.get(verifyKey(journalId)) !== null;
    }

    async function changePassword(oldPassword, newPassword, journalId) {
        await DB.loadAll(oldPassword, journalId);
        await setupPassword(newPassword, journalId);
        DB.setPassword(newPassword);
        await DB.save();
    }

    function removeJournalKeys(journalId) {
        if (_hasNativeBridge()) {
            AndroidBridge.cryptoRemoveJournalKeys(journalId);
            return;
        }
        Bootstrap.remove(saltKey(journalId));
        Bootstrap.remove(verifyKey(journalId));
    }

    return { encrypt, decrypt, encryptBytes, decryptBytes, setupPassword, verifyPassword, isSetup, changePassword, removeJournalKeys };
})();
