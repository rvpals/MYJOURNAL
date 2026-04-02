/**
 * Encryption module using Web Crypto API (AES-GCM).
 * Derives a 256-bit key from the user's password using PBKDF2.
 * Supports multiple journals, each with its own salt and verification token.
 */

const Crypto = (() => {
    const ITERATIONS = 100000;

    function saltKey(journalId) { return `journal_salt_${journalId}`; }
    function verifyKey(journalId) { return `journal_verify_${journalId}`; }

    async function getSalt(journalId) {
        let salt = localStorage.getItem(saltKey(journalId));
        if (!salt) {
            const arr = new Uint8Array(16);
            crypto.getRandomValues(arr);
            salt = arrayToBase64(arr);
            localStorage.setItem(saltKey(journalId), salt);
        }
        return base64ToArray(salt);
    }

    async function deriveKey(password, salt) {
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

    async function encrypt(data, password, journalId) {
        const salt = await getSalt(journalId);
        const key = await deriveKey(password, salt);
        const enc = new TextEncoder();
        const iv = crypto.getRandomValues(new Uint8Array(12));
        const encrypted = await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv },
            key,
            enc.encode(JSON.stringify(data))
        );
        return {
            iv: arrayToBase64(iv),
            data: arrayToBase64(new Uint8Array(encrypted))
        };
    }

    async function decrypt(encryptedObj, password, journalId) {
        const salt = await getSalt(journalId);
        const key = await deriveKey(password, salt);
        const iv = base64ToArray(encryptedObj.iv);
        const data = base64ToArray(encryptedObj.data);
        const decrypted = await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv },
            key,
            data
        );
        const dec = new TextDecoder();
        return JSON.parse(dec.decode(decrypted));
    }

    async function setupPassword(password, journalId) {
        const salt = await getSalt(journalId);
        const key = await deriveKey(password, salt);
        const verifyData = 'JOURNAL_VERIFY_TOKEN';
        const enc = new TextEncoder();
        const iv = crypto.getRandomValues(new Uint8Array(12));
        const encrypted = await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv },
            key,
            enc.encode(verifyData)
        );
        const verifyObj = {
            iv: arrayToBase64(iv),
            data: arrayToBase64(new Uint8Array(encrypted))
        };
        localStorage.setItem(verifyKey(journalId), JSON.stringify(verifyObj));

        // Sync to native SharedPreferences if available
        if (window.AndroidBridge && typeof AndroidBridge.syncCryptoKey === 'function') {
            AndroidBridge.syncCryptoKey(journalId, localStorage.getItem(saltKey(journalId)),
                JSON.stringify(verifyObj));
        }
    }

    async function verifyPassword(password, journalId) {
        const verifyStr = localStorage.getItem(verifyKey(journalId));
        if (!verifyStr) return false;
        try {
            const verifyObj = JSON.parse(verifyStr);
            const salt = await getSalt(journalId);
            const key = await deriveKey(password, salt);
            const iv = base64ToArray(verifyObj.iv);
            const data = base64ToArray(verifyObj.data);
            const decrypted = await crypto.subtle.decrypt(
                { name: 'AES-GCM', iv },
                key,
                data
            );
            const dec = new TextDecoder();
            return dec.decode(decrypted) === 'JOURNAL_VERIFY_TOKEN';
        } catch {
            return false;
        }
    }

    function isSetup(journalId) {
        return localStorage.getItem(verifyKey(journalId)) !== null;
    }

    async function changePassword(oldPassword, newPassword, journalId) {
        // Verify old password can decrypt, then re-encrypt with new password
        await DB.loadAll(oldPassword, journalId);
        await setupPassword(newPassword, journalId);
        // Re-persist the already-loaded sqlDB with the new password
        DB.setPassword(newPassword);
        await DB.save();
    }

    function removeJournalKeys(journalId) {
        localStorage.removeItem(saltKey(journalId));
        localStorage.removeItem(verifyKey(journalId));
    }

    // Helpers
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

    async function encryptBytes(uint8Array, password, journalId) {
        const salt = await getSalt(journalId);
        const key = await deriveKey(password, salt);
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
        const salt = await getSalt(journalId);
        const key = await deriveKey(password, salt);
        const iv = base64ToArray(encryptedObj.iv);
        const data = base64ToArray(encryptedObj.data);
        const decrypted = await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv }, key, data
        );
        return new Uint8Array(decrypted);
    }

    return { encrypt, decrypt, encryptBytes, decryptBytes, setupPassword, verifyPassword, isSetup, changePassword, removeJournalKeys };
})();
