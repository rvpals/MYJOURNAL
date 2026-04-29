package com.journal.app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val JOURNAL_PREFS = "journal_prefs"
        private const val BIOMETRIC_PREFS = "biometric_prefs"
    }

    private lateinit var cryptoService: CryptoService

    private lateinit var journalSpinner: Spinner
    private lateinit var inputPassword: EditText
    private lateinit var inputPasswordConfirm: EditText
    private lateinit var btnUnlock: Button
    private lateinit var btnBiometric: Button
    private lateinit var btnDeleteJournal: Button
    private lateinit var newJournalRow: LinearLayout
    private lateinit var inputNewJournalName: EditText
    private lateinit var loginError: TextView
    private lateinit var loginProgress: ProgressBar

    private val journals = mutableListOf<JournalInfo>()
    private var isNewJournal = false
    private var biometricInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        cryptoService = CryptoService(this)

        journalSpinner = findViewById(R.id.journal_spinner)
        inputPassword = findViewById(R.id.input_password)
        inputPasswordConfirm = findViewById(R.id.input_password_confirm)
        btnUnlock = findViewById(R.id.btn_unlock)
        btnBiometric = findViewById(R.id.btn_biometric)
        btnDeleteJournal = findViewById(R.id.btn_delete_journal)
        newJournalRow = findViewById(R.id.new_journal_row)
        inputNewJournalName = findViewById(R.id.input_new_journal_name)
        loginError = findViewById(R.id.login_error)
        loginProgress = findViewById(R.id.login_progress)

        setupListeners()
        loadJournals()
    }

    private fun setupListeners() {
        btnUnlock.setOnClickListener { handleLogin() }
        btnBiometric.setOnClickListener { biometricLogin() }
        findViewById<View>(R.id.btn_new_journal).setOnClickListener { showNewJournalInput() }
        btnDeleteJournal.setOnClickListener { deleteJournal() }
        findViewById<View>(R.id.btn_create_journal).setOnClickListener { createNewJournal() }
        findViewById<View>(R.id.btn_cancel_new).setOnClickListener { cancelNewJournal() }

        journalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                onJournalSelected()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleLogin()
                true
            } else {
                false
            }
        }
    }

    // ========== Journal List Management ==========

    private fun loadJournals() {
        journals.clear()
        val prefs = getJournalPrefs()
        val listJson = prefs.getString("journal_list", "[]") ?: "[]"
        try {
            val arr = JSONArray(listJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                journals.add(JournalInfo(
                    obj.getString("id"),
                    obj.getString("name"),
                    obj.optString("createdAt", "")
                ))
            }
        } catch (_: JSONException) {
        }

        populateSpinner()

        if (journals.isEmpty()) {
            showNewJournalInput()
        } else {
            val lastId = prefs.getString("last_journal_id", "") ?: ""
            val autoOpen = prefs.getBoolean("auto_open_last_journal", false)
            if (autoOpen && lastId.isNotEmpty()) {
                val index = journals.indexOfFirst { it.id == lastId }
                if (index >= 0) journalSpinner.setSelection(index)
            }
        }
    }

    private fun populateSpinner() {
        val names = journals.map { it.name }
        val adapter = ArrayAdapter(this, R.layout.spinner_item, names)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        journalSpinner.adapter = adapter
        btnDeleteJournal.visibility = if (journals.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun onJournalSelected() {
        clearError()
        val pos = journalSpinner.selectedItemPosition
        if (pos < 0 || pos >= journals.size) return

        val journal = journals[pos]
        val setup = isJournalSetup(journal.id)

        if (!setup) {
            isNewJournal = true
            inputPasswordConfirm.visibility = View.VISIBLE
            btnUnlock.text = "Set Password"
        } else {
            isNewJournal = false
            inputPasswordConfirm.visibility = View.GONE
            btnUnlock.text = "Unlock"
        }

        updateBiometricButton()

        val prefs = getJournalPrefs()
        val autoBiometric = prefs.getBoolean("auto_biometric", false)
        if (autoBiometric && setup && hasCredential(journal.id)) {
            inputPassword.postDelayed({ biometricLogin() }, 300)
        }
    }

    private fun saveJournalList() {
        val arr = JSONArray()
        for (j in journals) {
            try {
                val obj = JSONObject()
                obj.put("id", j.id)
                obj.put("name", j.name)
                obj.put("createdAt", j.createdAt)
                arr.put(obj as Any)
            } catch (_: JSONException) { }
        }
        getJournalPrefs().edit().putString("journal_list", arr.toString()).apply()
    }

    // ========== New Journal ==========

    private fun showNewJournalInput() {
        newJournalRow.visibility = View.VISIBLE
        inputNewJournalName.requestFocus()
    }

    private fun cancelNewJournal() {
        newJournalRow.visibility = View.GONE
        inputNewJournalName.setText("")
    }

    private fun createNewJournal() {
        val name = inputNewJournalName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a journal name", Toast.LENGTH_SHORT).show()
            return
        }

        val id = name.lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" +
                System.currentTimeMillis().toString(36)

        val newJournal = JournalInfo(
            id, name,
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
        )
        journals.add(newJournal)
        saveJournalList()

        populateSpinner()
        journalSpinner.setSelection(journals.size - 1)
        cancelNewJournal()
    }

    // ========== Delete Journal ==========

    private fun deleteJournal() {
        val pos = journalSpinner.selectedItemPosition
        if (pos < 0 || pos >= journals.size) return

        val journal = journals[pos]
        AlertDialog.Builder(this)
            .setTitle("Delete Journal")
            .setMessage("Delete \"${journal.name}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                getJournalPrefs().edit()
                    .remove("salt_${journal.id}")
                    .remove("verify_${journal.id}")
                    .apply()

                getBiometricPrefs().edit().remove("cred_${journal.id}").apply()

                journals.removeAt(pos)
                saveJournalList()
                populateSpinner()

                if (journals.isEmpty()) {
                    showNewJournalInput()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========== Login ==========

    private fun handleLogin() {
        clearError()
        val pos = journalSpinner.selectedItemPosition
        if (pos < 0 || pos >= journals.size) {
            showError("Please select or create a journal first.")
            return
        }

        val password = inputPassword.text.toString()
        if (password.isEmpty()) {
            showError("Please enter a password.")
            return
        }

        val journal = journals[pos]

        if (isNewJournal) {
            val confirm = inputPasswordConfirm.text.toString()
            if (password != confirm) {
                showError("Passwords do not match.")
                return
            }

            showProgress(true)
            Thread {
                try {
                    setupPassword(password, journal.id)
                    runOnUiThread {
                        showProgress(false)
                        launchApp(journal.id, password)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showProgress(false)
                        showError("Failed to create journal: ${e.message}")
                    }
                }
            }.start()
        } else {
            showProgress(true)
            Thread {
                try {
                    val valid = verifyPassword(password, journal.id)
                    runOnUiThread {
                        showProgress(false)
                        if (valid) {
                            launchApp(journal.id, password)
                        } else {
                            showError("Incorrect password.")
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showProgress(false)
                        showError("Verification failed: ${e.message}")
                    }
                }
            }.start()
        }
    }

    private fun launchApp(journalId: String, password: String) {
        val prefs = getJournalPrefs()
        prefs.edit().putString("last_journal_id", journalId).apply()

        ServiceProvider.init(this)
        val bs = ServiceProvider.bootstrapService!!
        val db = ServiceProvider.databaseService!!

        bs.set("last_journal_id", journalId)
        val journalList = prefs.getString("journal_list", "[]") ?: "[]"
        bs.set("journal_list", journalList)

        val salt = prefs.getString("salt_$journalId", null)
        val verify = prefs.getString("verify_$journalId", null)
        if (salt != null) bs.set("journal_salt_$journalId", salt)
        if (verify != null) bs.set("journal_verify_$journalId", verify)

        showProgress(true)
        Thread {
            try {
                db.open(password, journalId)
                ThemeManager.init()
                val dashboardJson = DashboardDataBuilder.build(db, bs)
                runOnUiThread {
                    showProgress(false)
                    DashboardActivity.pendingData = dashboardJson
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showProgress(false)
                    showError("Failed to open database: ${e.message}")
                }
            }
        }.start()
    }

    // ========== Biometric ==========

    private fun updateBiometricButton() {
        val pos = journalSpinner.selectedItemPosition
        if (pos < 0 || pos >= journals.size) {
            btnBiometric.visibility = View.GONE
            return
        }

        val journal = journals[pos]
        val available = isBiometricAvailable()
        val setup = isJournalSetup(journal.id)
        val hasCred = hasCredential(journal.id)

        btnBiometric.visibility = if (available && setup && hasCred) View.VISIBLE else View.GONE
    }

    private fun isBiometricAvailable(): Boolean {
        val bm = BiometricManager.from(this)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun hasCredential(journalId: String): Boolean =
        getBiometricPrefs().contains("cred_$journalId")

    private fun getCredential(journalId: String): String? {
        val encoded = getBiometricPrefs().getString("cred_$journalId", null) ?: return null
        return String(Base64.decode(encoded, Base64.NO_WRAP))
    }

    private fun biometricLogin() {
        if (biometricInProgress) return
        biometricInProgress = true

        val pos = journalSpinner.selectedItemPosition
        if (pos < 0 || pos >= journals.size) { biometricInProgress = false; return }

        val journal = journals[pos]
        if (!hasCredential(journal.id)) { biometricInProgress = false; return }

        val executor = ContextCompat.getMainExecutor(this)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Journal")
            .setSubtitle("Use your fingerprint to sign in")
            .setNegativeButtonText("Use password")
            .build()

        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onBiometricSuccess(journal.id)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricInProgress = false
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                        && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        showError("Biometric error: $errString")
                    }
                }

                override fun onAuthenticationFailed() {
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }

    private fun onBiometricSuccess(journalId: String) {
        val password = getCredential(journalId)
        if (password == null) {
            showError("No saved credential found. Please use password.")
            return
        }

        showProgress(true)
        Thread {
            try {
                val valid = verifyPassword(password, journalId)
                runOnUiThread {
                    showProgress(false)
                    if (valid) {
                        launchApp(journalId, password)
                    } else {
                        showError("Saved password is no longer valid. Please log in with password.")
                        getBiometricPrefs().edit().remove("cred_$journalId").apply()
                        updateBiometricButton()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showProgress(false)
                    showError("Verification failed: ${e.message}")
                }
            }
        }.start()
    }

    // ========== Crypto (delegated to CryptoService) ==========

    private fun isJournalSetup(journalId: String): Boolean =
        cryptoService.isSetup(journalId)

    private fun setupPassword(password: String, journalId: String) =
        cryptoService.setupPassword(password, journalId)

    private fun verifyPassword(password: String, journalId: String): Boolean =
        cryptoService.verifyPassword(password, journalId)

    // ========== Helpers ==========

    private fun getJournalPrefs(): SharedPreferences =
        getSharedPreferences(JOURNAL_PREFS, MODE_PRIVATE)

    private fun getBiometricPrefs(): SharedPreferences =
        getSharedPreferences(BIOMETRIC_PREFS, MODE_PRIVATE)

    private fun showError(msg: String) {
        loginError.text = msg
        loginError.visibility = View.VISIBLE
    }

    private fun clearError() {
        loginError.visibility = View.GONE
        loginError.text = ""
    }

    private fun showProgress(show: Boolean) {
        loginProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnUnlock.isEnabled = !show
        btnBiometric.isEnabled = !show
    }

    // ========== Journal Info ==========

    data class JournalInfo(val id: String, val name: String, val createdAt: String)
}
