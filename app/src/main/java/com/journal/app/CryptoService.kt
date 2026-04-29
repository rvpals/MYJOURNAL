package com.journal.app

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoService(private val context: Context) {

    companion object {
        private const val ITERATIONS = 100000
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12
        private const val SALT_LENGTH = 16
        private const val VERIFY_TOKEN = "JOURNAL_VERIFY_TOKEN"
        private const val BOOTSTRAP_PREFS = "bootstrap_prefs"
        private const val JOURNAL_PREFS = "journal_prefs"
    }

    private fun saltKey(journalId: String) = "journal_salt_$journalId"
    private fun verifyKey(journalId: String) = "journal_verify_$journalId"

    private fun getBootstrapPrefs() =
        context.getSharedPreferences(BOOTSTRAP_PREFS, Context.MODE_PRIVATE)

    private fun getJournalPrefs() =
        context.getSharedPreferences(JOURNAL_PREFS, Context.MODE_PRIVATE)

    fun getSalt(journalId: String): ByteArray {
        val prefs = getBootstrapPrefs()
        val key = saltKey(journalId)
        var saltB64 = prefs.getString(key, null)

        if (saltB64 == null) {
            val journalPrefs = getJournalPrefs()
            saltB64 = journalPrefs.getString("salt_$journalId", null)
        }

        if (saltB64 == null) {
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)
            saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs.edit().putString(key, saltB64).apply()
            getJournalPrefs().edit().putString("salt_$journalId", saltB64).apply()
            return salt
        }
        return Base64.decode(saltB64, Base64.NO_WRAP)
    }

    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encrypt(jsonData: String, password: String, journalId: String): String {
        val salt = getSalt(journalId)
        val key = deriveKey(password, salt)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(jsonData.toByteArray(StandardCharsets.UTF_8))

        val result = JSONObject()
        result.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        result.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        return result.toString()
    }

    fun decrypt(encryptedJson: String, password: String, journalId: String): String {
        val obj = JSONObject(encryptedJson)
        val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(obj.getString("data"), Base64.NO_WRAP)
        val salt = getSalt(journalId)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decrypted = cipher.doFinal(data)
        return String(decrypted, StandardCharsets.UTF_8)
    }

    fun encryptBytes(base64Data: String, password: String, journalId: String): String {
        val rawBytes = Base64.decode(base64Data, Base64.NO_WRAP)
        val salt = getSalt(journalId)
        val key = deriveKey(password, salt)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(rawBytes)

        val result = JSONObject()
        result.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        result.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        result.put("format", "sqlite")
        return result.toString()
    }

    fun decryptBytes(encryptedJson: String, password: String, journalId: String): String {
        val obj = JSONObject(encryptedJson)
        val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(obj.getString("data"), Base64.NO_WRAP)
        val salt = getSalt(journalId)
        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decrypted = cipher.doFinal(data)
        return Base64.encodeToString(decrypted, Base64.NO_WRAP)
    }

    fun setupPassword(password: String, journalId: String) {
        val salt = getSalt(journalId)
        val key = deriveKey(password, salt)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(VERIFY_TOKEN.toByteArray(StandardCharsets.UTF_8))

        val verifyObj = JSONObject()
        verifyObj.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        verifyObj.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))

        val verifyStr = verifyObj.toString()
        getBootstrapPrefs().edit().putString(verifyKey(journalId), verifyStr).apply()
        getJournalPrefs().edit()
            .putString("salt_$journalId", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("verify_$journalId", verifyStr)
            .apply()
    }

    fun verifyPassword(password: String, journalId: String): Boolean {
        val verifyStr = getBootstrapPrefs().getString(verifyKey(journalId), null)
            ?: getJournalPrefs().getString("verify_$journalId", null)
            ?: return false

        return try {
            val obj = JSONObject(verifyStr)
            val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
            val data = Base64.decode(obj.getString("data"), Base64.NO_WRAP)
            val salt = getSalt(journalId)
            val key = deriveKey(password, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decrypted = cipher.doFinal(data)
            VERIFY_TOKEN == String(decrypted, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            false
        }
    }

    fun isSetup(journalId: String): Boolean {
        return getBootstrapPrefs().contains(verifyKey(journalId)) ||
                getJournalPrefs().contains("verify_$journalId")
    }

    fun removeJournalKeys(journalId: String) {
        getBootstrapPrefs().edit()
            .remove(saltKey(journalId))
            .remove(verifyKey(journalId))
            .apply()
        getJournalPrefs().edit()
            .remove("salt_$journalId")
            .remove("verify_$journalId")
            .apply()
    }
}
