package com.journal.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class LoginActivity extends AppCompatActivity {

    private static final String JOURNAL_PREFS = "journal_prefs";
    private static final String BIOMETRIC_PREFS = "biometric_prefs";
    private static final String VERIFY_TOKEN = "JOURNAL_VERIFY_TOKEN";
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int GCM_TAG_LENGTH = 128;

    private Spinner journalSpinner;
    private EditText inputPassword;
    private EditText inputPasswordConfirm;
    private Button btnUnlock;
    private Button btnBiometric;
    private Button btnDeleteJournal;
    private LinearLayout newJournalRow;
    private EditText inputNewJournalName;
    private TextView loginError;
    private ProgressBar loginProgress;

    private List<JournalInfo> journals = new ArrayList<>();
    private boolean isNewJournal = false;
    private boolean biometricInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        journalSpinner = findViewById(R.id.journal_spinner);
        inputPassword = findViewById(R.id.input_password);
        inputPasswordConfirm = findViewById(R.id.input_password_confirm);
        btnUnlock = findViewById(R.id.btn_unlock);
        btnBiometric = findViewById(R.id.btn_biometric);
        btnDeleteJournal = findViewById(R.id.btn_delete_journal);
        newJournalRow = findViewById(R.id.new_journal_row);
        inputNewJournalName = findViewById(R.id.input_new_journal_name);
        loginError = findViewById(R.id.login_error);
        loginProgress = findViewById(R.id.login_progress);

        setupListeners();
        loadJournals();
    }

    private void setupListeners() {
        btnUnlock.setOnClickListener(v -> handleLogin());
        btnBiometric.setOnClickListener(v -> biometricLogin());
        findViewById(R.id.btn_new_journal).setOnClickListener(v -> showNewJournalInput());
        btnDeleteJournal.setOnClickListener(v -> deleteJournal());
        findViewById(R.id.btn_create_journal).setOnClickListener(v -> createNewJournal());
        findViewById(R.id.btn_cancel_new).setOnClickListener(v -> cancelNewJournal());

        journalSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                onJournalSelected();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Enter key on password field triggers login
        inputPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                handleLogin();
                return true;
            }
            return false;
        });
    }

    // ========== Journal List Management ==========

    private void loadJournals() {
        journals.clear();
        SharedPreferences prefs = getJournalPrefs();
        String listJson = prefs.getString("journal_list", "[]");
        try {
            JSONArray arr = new JSONArray(listJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                journals.add(new JournalInfo(
                    obj.getString("id"),
                    obj.getString("name"),
                    obj.optString("createdAt", "")
                ));
            }
        } catch (JSONException e) {
            // Corrupted list, start fresh
        }

        populateSpinner();

        if (journals.isEmpty()) {
            showNewJournalInput();
        } else {
            // Auto-select last journal
            String lastId = prefs.getString("last_journal_id", "");
            boolean autoOpen = prefs.getBoolean("auto_open_last_journal", false);
            if (autoOpen && !lastId.isEmpty()) {
                for (int i = 0; i < journals.size(); i++) {
                    if (journals.get(i).id.equals(lastId)) {
                        journalSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }
    }

    private void populateSpinner() {
        List<String> names = new ArrayList<>();
        for (JournalInfo j : journals) {
            names.add(j.name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                R.layout.spinner_item, names);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        journalSpinner.setAdapter(adapter);

        btnDeleteJournal.setVisibility(journals.size() > 0 ? View.VISIBLE : View.GONE);
    }

    private void onJournalSelected() {
        clearError();
        int pos = journalSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= journals.size()) return;

        JournalInfo journal = journals.get(pos);
        boolean setup = isJournalSetup(journal.id);

        if (!setup) {
            // New journal needs password setup
            isNewJournal = true;
            inputPasswordConfirm.setVisibility(View.VISIBLE);
            btnUnlock.setText("Set Password");
        } else {
            isNewJournal = false;
            inputPasswordConfirm.setVisibility(View.GONE);
            btnUnlock.setText("Unlock");
        }

        updateBiometricButton();

        // Auto biometric
        SharedPreferences prefs = getJournalPrefs();
        boolean autoBiometric = prefs.getBoolean("auto_biometric", false);
        if (autoBiometric && setup && hasCredential(journal.id)) {
            inputPassword.postDelayed(this::biometricLogin, 300);
        }
    }

    private void saveJournalList() {
        JSONArray arr = new JSONArray();
        for (JournalInfo j : journals) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", j.id);
                obj.put("name", j.name);
                obj.put("createdAt", j.createdAt);
                arr.put(obj);
            } catch (JSONException e) { /* skip */ }
        }
        getJournalPrefs().edit().putString("journal_list", arr.toString()).apply();
    }

    // ========== New Journal ==========

    private void showNewJournalInput() {
        newJournalRow.setVisibility(View.VISIBLE);
        inputNewJournalName.requestFocus();
    }

    private void cancelNewJournal() {
        newJournalRow.setVisibility(View.GONE);
        inputNewJournalName.setText("");
    }

    private void createNewJournal() {
        String name = inputNewJournalName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a journal name", Toast.LENGTH_SHORT).show();
            return;
        }

        String id = name.toLowerCase().replaceAll("[^a-z0-9]", "_") + "_" +
                Long.toString(System.currentTimeMillis(), 36);

        JournalInfo newJournal = new JournalInfo(id, name,
                new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                        java.util.Locale.US).format(new java.util.Date()));
        journals.add(newJournal);
        saveJournalList();

        populateSpinner();
        journalSpinner.setSelection(journals.size() - 1);
        cancelNewJournal();
    }

    // ========== Delete Journal ==========

    private void deleteJournal() {
        int pos = journalSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= journals.size()) return;

        JournalInfo journal = journals.get(pos);
        new AlertDialog.Builder(this)
            .setTitle("Delete Journal")
            .setMessage("Delete \"" + journal.name + "\"? This cannot be undone.")
            .setPositiveButton("Delete", (d, w) -> {
                // Remove crypto keys
                SharedPreferences prefs = getJournalPrefs();
                prefs.edit()
                    .remove("salt_" + journal.id)
                    .remove("verify_" + journal.id)
                    .apply();

                // Remove biometric credential
                getBiometricPrefs().edit().remove("cred_" + journal.id).apply();

                // Remove from list
                journals.remove(pos);
                saveJournalList();
                populateSpinner();

                if (journals.isEmpty()) {
                    showNewJournalInput();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // ========== Login ==========

    private void handleLogin() {
        clearError();
        int pos = journalSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= journals.size()) {
            showError("Please select or create a journal first.");
            return;
        }

        String password = inputPassword.getText().toString();
        if (password.isEmpty()) {
            showError("Please enter a password.");
            return;
        }

        JournalInfo journal = journals.get(pos);

        if (isNewJournal) {
            // Setup new journal
            String confirm = inputPasswordConfirm.getText().toString();
            if (!password.equals(confirm)) {
                showError("Passwords do not match.");
                return;
            }

            showProgress(true);
            new Thread(() -> {
                try {
                    setupPassword(password, journal.id);
                    runOnUiThread(() -> {
                        showProgress(false);
                        launchApp(journal.id, password);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        showProgress(false);
                        showError("Failed to create journal: " + e.getMessage());
                    });
                }
            }).start();

        } else {
            // Verify password for existing journal
            showProgress(true);
            new Thread(() -> {
                try {
                    boolean valid = verifyPassword(password, journal.id);
                    runOnUiThread(() -> {
                        showProgress(false);
                        if (valid) {
                            launchApp(journal.id, password);
                        } else {
                            showError("Incorrect password.");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        showProgress(false);
                        showError("Verification failed: " + e.getMessage());
                    });
                }
            }).start();
        }
    }

    private void launchApp(String journalId, String password) {
        // Save last journal
        SharedPreferences prefs = getJournalPrefs();
        prefs.edit().putString("last_journal_id", journalId).apply();

        // Launch MainActivity with auto-login + crypto keys for web sync
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("journal_id", journalId);
        intent.putExtra("password", password);
        intent.putExtra("auto_login", true);

        // Pass native crypto keys so web localStorage can be synced
        String salt = prefs.getString("salt_" + journalId, null);
        String verify = prefs.getString("verify_" + journalId, null);
        if (salt != null) intent.putExtra("crypto_salt", salt);
        if (verify != null) intent.putExtra("crypto_verify", verify);

        // Pass journal list for web sync
        intent.putExtra("journal_list", prefs.getString("journal_list", "[]"));

        startActivity(intent);
        finish();
    }

    // ========== Biometric ==========

    private void updateBiometricButton() {
        int pos = journalSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= journals.size()) {
            btnBiometric.setVisibility(View.GONE);
            return;
        }

        JournalInfo journal = journals.get(pos);
        boolean available = isBiometricAvailable();
        boolean setup = isJournalSetup(journal.id);
        boolean hasCred = hasCredential(journal.id);

        btnBiometric.setVisibility(available && setup && hasCred ? View.VISIBLE : View.GONE);
    }

    private boolean isBiometricAvailable() {
        BiometricManager bm = BiometricManager.from(this);
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private boolean hasCredential(String journalId) {
        return getBiometricPrefs().contains("cred_" + journalId);
    }

    private String getCredential(String journalId) {
        String encoded = getBiometricPrefs().getString("cred_" + journalId, null);
        if (encoded == null) return null;
        return new String(Base64.decode(encoded, Base64.NO_WRAP));
    }

    private void biometricLogin() {
        if (biometricInProgress) return;
        biometricInProgress = true;

        int pos = journalSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= journals.size()) { biometricInProgress = false; return; }

        JournalInfo journal = journals.get(pos);
        if (!hasCredential(journal.id)) { biometricInProgress = false; return; }

        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Journal")
                .setSubtitle("Use your fingerprint to sign in")
                .setNegativeButtonText("Use password")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        onBiometricSuccess(journal.id);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        biometricInProgress = false;
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            showError("Biometric error: " + errString);
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // System allows retries, don't callback yet
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    private void onBiometricSuccess(String journalId) {
        String password = getCredential(journalId);
        if (password == null) {
            showError("No saved credential found. Please use password.");
            return;
        }

        showProgress(true);
        new Thread(() -> {
            try {
                boolean valid = verifyPassword(password, journalId);
                runOnUiThread(() -> {
                    showProgress(false);
                    if (valid) {
                        launchApp(journalId, password);
                    } else {
                        showError("Saved password is no longer valid. Please log in with password.");
                        getBiometricPrefs().edit().remove("cred_" + journalId).apply();
                        updateBiometricButton();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showProgress(false);
                    showError("Verification failed: " + e.getMessage());
                });
            }
        }).start();
    }

    // ========== Native Crypto (PBKDF2 + AES-GCM) ==========
    // Compatible with Web Crypto API parameters in crypto.js

    private boolean isJournalSetup(String journalId) {
        return getJournalPrefs().contains("verify_" + journalId);
    }

    private byte[] getSalt(String journalId) {
        SharedPreferences prefs = getJournalPrefs();
        String saltB64 = prefs.getString("salt_" + journalId, null);
        if (saltB64 == null) {
            // Generate new 16-byte salt
            byte[] salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);
            saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP);
            prefs.edit().putString("salt_" + journalId, saltB64).apply();
            return salt;
        }
        return Base64.decode(saltB64, Base64.NO_WRAP);
    }

    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    private void setupPassword(String password, String journalId) throws Exception {
        byte[] salt = getSalt(journalId);
        SecretKey key = deriveKey(password, salt);

        // Encrypt verification token
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
        byte[] encrypted = cipher.doFinal(VERIFY_TOKEN.getBytes(StandardCharsets.UTF_8));

        // Store as JSON matching web format: {iv, data}
        JSONObject verifyObj = new JSONObject();
        verifyObj.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        verifyObj.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));

        getJournalPrefs().edit()
                .putString("verify_" + journalId, verifyObj.toString())
                .apply();
    }

    private boolean verifyPassword(String password, String journalId) throws Exception {
        String verifyStr = getJournalPrefs().getString("verify_" + journalId, null);
        if (verifyStr == null) return false;

        try {
            JSONObject verifyObj = new JSONObject(verifyStr);
            byte[] iv = Base64.decode(verifyObj.getString("iv"), Base64.NO_WRAP);
            byte[] data = Base64.decode(verifyObj.getString("data"), Base64.NO_WRAP);
            byte[] salt = getSalt(journalId);
            SecretKey key = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            byte[] decrypted = cipher.doFinal(data);

            return VERIFY_TOKEN.equals(new String(decrypted, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false; // Decryption failed = wrong password
        }
    }

    // ========== Helpers ==========

    private SharedPreferences getJournalPrefs() {
        return getSharedPreferences(JOURNAL_PREFS, MODE_PRIVATE);
    }

    private SharedPreferences getBiometricPrefs() {
        return getSharedPreferences(BIOMETRIC_PREFS, MODE_PRIVATE);
    }

    private void showError(String msg) {
        loginError.setText(msg);
        loginError.setVisibility(View.VISIBLE);
    }

    private void clearError() {
        loginError.setVisibility(View.GONE);
        loginError.setText("");
    }

    private void showProgress(boolean show) {
        loginProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        btnUnlock.setEnabled(!show);
        btnBiometric.setEnabled(!show);
    }

    // ========== Journal Info ==========

    static class JournalInfo {
        String id, name, createdAt;
        JournalInfo(String id, String name, String createdAt) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
        }
    }
}
