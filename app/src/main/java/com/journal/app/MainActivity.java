package com.journal.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import android.webkit.JsResult;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import android.webkit.JavascriptInterface;

import android.provider.MediaStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> fileUploadCallback;
    private Uri cameraPhotoUri;
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int LOCATION_PERMISSION_REQUEST = 1002;
    private static final int CAMERA_PERMISSION_REQUEST = 1003;
    private String pendingGeolocationOrigin;
    private GeolocationPermissions.Callback pendingGeolocationCallback;

    private String autoLoginJournalId;
    private String autoLoginPassword;
    private String autoLoginSalt;
    private String autoLoginVerify;
    private String autoLoginJournalList;

    private static final String BACKUP_PREFS = "backup_prefs";
    private static final String BACKUP_FOLDER_URI_KEY = "backup_folder_uri";
    private ActivityResultLauncher<Intent> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register SAF folder picker launcher
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            // Persist permission across app restarts
                            getContentResolver().takePersistableUriPermission(treeUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE)
                                    .edit().putString(BACKUP_FOLDER_URI_KEY, treeUri.toString()).apply();
                            // Notify web layer
                            String name = getBackupFolderDisplayName(treeUri);
                            String escaped = name.replace("\\", "\\\\").replace("'", "\\'");
                            runOnUiThread(() -> webView.evaluateJavascript(
                                    "if(typeof onBackupFolderSelected==='function')onBackupFolderSelected('" + escaped + "')", null));
                        }
                    }
                });

        setContentView(R.layout.activity_main);

        // Check for auto-login from LoginActivity
        Intent intent = getIntent();
        if (intent.getBooleanExtra("auto_login", false)) {
            autoLoginJournalId = intent.getStringExtra("journal_id");
            autoLoginPassword = intent.getStringExtra("password");
            autoLoginSalt = intent.getStringExtra("crypto_salt");
            autoLoginVerify = intent.getStringExtra("crypto_verify");
            autoLoginJournalList = intent.getStringExtra("journal_list");
        }

        webView = findViewById(R.id.webView);
        configureWebView();
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        // Hide WebView when auto-login is active (native screens handle display)
        if (autoLoginJournalId != null) {
            webView.setVisibility(View.INVISIBLE);
        }

        webView.loadUrl("file:///android_asset/web/index.html");
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();

        // Core settings
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);          // localStorage
        settings.setDatabaseEnabled(true);             // IndexedDB
        settings.setAllowFileAccess(true);

        // Allow CDN resources to load from file:// origin
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // Responsive layout — let viewport meta tag control width,
        // don't shrink-to-fit wide content (allows horizontal scroll instead)
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);

        // Geolocation
        settings.setGeolocationEnabled(true);

        // Cache for offline CDN resilience
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // WebViewClient: keep navigation inside the app, open external links in browser
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith("file:///android_asset/")) {
                    return false; // load in WebView
                }
                // Open external URLs (Google Maps, CDN) in system browser
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Sync journal metadata from web Bootstrap store to SharedPreferences
                syncJournalMetadata();
                // Auto-login if launched from native LoginActivity
                if (autoLoginJournalId != null && autoLoginPassword != null) {
                    performAutoLogin();
                }
            }
        });

        // WebChromeClient: geolocation, file chooser, JS dialogs, OAuth popups
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback callback) {
                if (ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeolocationOrigin = origin;
                    pendingGeolocationCallback = callback;
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                            LOCATION_PERMISSION_REQUEST);
                }
            }

            // File chooser for import and image attach (with camera option for image inputs)
            @Override
            public boolean onShowFileChooser(WebView webView,
                    ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                try {
                    // Check if this is an image-accepting input (for camera support)
                    String[] acceptTypes = fileChooserParams.getAcceptTypes();
                    boolean isImageInput = false;
                    boolean isCaptureInput = fileChooserParams.isCaptureEnabled();
                    for (String type : acceptTypes) {
                        if (type != null && type.startsWith("image/")) {
                            isImageInput = true;
                            break;
                        }
                    }

                    // If capture is explicitly requested, go straight to camera
                    if (isCaptureInput && isImageInput) {
                        if (ContextCompat.checkSelfPermission(MainActivity.this,
                                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
                            return true;
                        }
                        launchCamera();
                        return true;
                    }

                    // For image inputs (without capture), offer both gallery and camera
                    if (isImageInput) {
                        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
                        galleryIntent.setType("image/*");

                        // Build camera intent
                        ArrayList<Intent> extraIntents = new ArrayList<>();
                        if (ContextCompat.checkSelfPermission(MainActivity.this,
                                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            Intent cameraIntent = createCameraIntent();
                            if (cameraIntent != null) {
                                extraIntents.add(cameraIntent);
                            }
                        }

                        Intent chooser = Intent.createChooser(galleryIntent, "Select Image");
                        if (!extraIntents.isEmpty()) {
                            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                                    extraIntents.toArray(new Intent[0]));
                        }
                        startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
                    } else {
                        // Non-image input: use generic file picker (for CSV, JSON, etc.)
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    }
                } catch (Exception e) {
                    fileUploadCallback = null;
                    Toast.makeText(MainActivity.this, "Cannot open file chooser", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                android.util.Log.d("WebConsole", cm.message() + " -- line " + cm.lineNumber() + " of " + cm.sourceId());
                return super.onConsoleMessage(cm);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return false; // use default dialog
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                return false; // use default dialog
            }
        });

        // Handle downloads (for CSV/PDF export)
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                    String mimeType, long contentLength) {
                handleDownload(url, contentDisposition, mimeType);
            }
        });
    }

    private void handleDownload(String url, String contentDisposition, String mimeType) {
        // Handle data: URLs (blob downloads from JS)
        if (url.startsWith("data:")) {
            try {
                String[] parts = url.split(",", 2);
                byte[] data;
                if (parts[0].contains("base64")) {
                    data = Base64.decode(parts[1], Base64.DEFAULT);
                } else {
                    data = parts[1].getBytes("UTF-8");
                }

                String filename = extractFilename(contentDisposition, mimeType);
                File downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, filename);

                FileOutputStream fos = new FileOutputStream(file);
                fos.write(data);
                fos.close();

                Toast.makeText(this, "Saved to Downloads: " + filename, Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            // Open external download URL in browser
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        }
    }

    private String extractFilename(String contentDisposition, String mimeType) {
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String name = contentDisposition.split("filename=")[1].replace("\"", "").trim();
            if (!name.isEmpty()) return name;
        }
        // Fallback based on mime type
        if ("text/csv".equals(mimeType)) return "journal_export.csv";
        if ("application/pdf".equals(mimeType)) return "journal_report.pdf";
        if ("application/json".equals(mimeType)) return "journal_data.json";
        return "journal_download";
    }

    private Intent createCameraIntent() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = createImageFile();
        if (photoFile != null) {
            cameraPhotoUri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", photoFile);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            return cameraIntent;
        }
        return null;
    }

    private void launchCamera() {
        Intent cameraIntent = createCameraIntent();
        if (cameraIntent != null) {
            startActivityForResult(cameraIntent, FILE_CHOOSER_REQUEST);
        } else {
            if (fileUploadCallback != null) {
                fileUploadCallback.onReceiveValue(null);
                fileUploadCallback = null;
            }
            Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String filename = "JOURNAL_" + timestamp;
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            return File.createTempFile(filename, ".jpg", storageDir);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (fileUploadCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK) {
                    // Check if result came from camera (no data URI but cameraPhotoUri exists)
                    if (data != null && data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    } else if (cameraPhotoUri != null) {
                        results = new Uri[]{cameraPhotoUri};
                    }
                }
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
                cameraPhotoUri = null;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST && pendingGeolocationCallback != null) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            pendingGeolocationCallback.invoke(pendingGeolocationOrigin, granted, false);
            pendingGeolocationOrigin = null;
            pendingGeolocationCallback = null;
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                launchCamera();
            } else {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                    fileUploadCallback = null;
                }
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Call into the web app's navigation system instead of WebView browser history
        webView.evaluateJavascript("handleAndroidBack()", result -> {
            if ("\"exit\"".equals(result)) {
                runOnUiThread(() -> MainActivity.super.onBackPressed());
            }
        });
    }


    // ========== Auto Login from Native LoginActivity ==========

    private void performAutoLogin() {
        String jid = autoLoginJournalId;
        String pwd = autoLoginPassword;
        String salt = autoLoginSalt;
        String verify = autoLoginVerify;
        String journalList = autoLoginJournalList;
        // Clear credentials from memory after use
        autoLoginJournalId = null;
        autoLoginPassword = null;
        autoLoginSalt = null;
        autoLoginVerify = null;
        autoLoginJournalList = null;

        // Escape single quotes in password for JS injection
        String escapedPwd = pwd.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");

        // Build JS to sync native crypto keys to web Bootstrap store before auto-login.
        // This ensures the web Crypto module sees the same salt/verify as native.
        StringBuilder js = new StringBuilder();
        js.append("(async function() {");
        js.append("  try {");

        // Wait for Bootstrap to be ready
        js.append("    if (typeof Bootstrap !== 'undefined' && !Bootstrap.isReady()) await Bootstrap.init();");

        // Sync journal list to web Bootstrap store
        if (journalList != null) {
            String escapedList = journalList.replace("\\", "\\\\").replace("'", "\\'");
            js.append("    Bootstrap.set('journal_list', '").append(escapedList).append("');");
        }

        // Sync crypto keys (salt + verify token) to web Bootstrap store
        if (salt != null) {
            js.append("    Bootstrap.set('journal_salt_").append(jid).append("', '").append(salt).append("');");
        }
        if (verify != null) {
            String escapedVerify = verify.replace("\\", "\\\\").replace("'", "\\'");
            js.append("    Bootstrap.set('journal_verify_").append(jid).append("', '").append(escapedVerify).append("');");
        }

        // Now auto-login: set credentials, load DB, enter app
        js.append("    DB.setPassword('").append(escapedPwd).append("');");
        js.append("    DB.setJournalId('").append(jid).append("');");
        js.append("    await DB.loadAll('").append(escapedPwd).append("', '").append(jid).append("');");
        js.append("    enterApp('").append(jid).append("');");
        js.append("    AndroidBridge.onAutoLoginComplete('');");
        js.append("  } catch(e) {");
        js.append("    console.error('Auto-login failed: ' + e.message);");
        js.append("    AndroidBridge.onAutoLoginFailed(e.message || 'Unknown error');");
        js.append("  }");
        js.append("})();");

        webView.evaluateJavascript(js.toString(), null);
    }

    /**
     * Sync journal metadata from WebView Bootstrap store to SharedPreferences.
     * This keeps the native LoginActivity's data in sync with the web app.
     * Uses Bootstrap (IndexedDB-backed) instead of localStorage.
     */
    private void syncJournalMetadata() {
        String js = "(function() {" +
                "  if (typeof Bootstrap === 'undefined' || !Bootstrap.isReady()) return null;" +
                "  var result = {};" +
                "  result.journal_list = Bootstrap.get('journal_list') || '[]';" +
                "  result.last_journal_id = Bootstrap.get('last_journal_id') || '';" +
                "  result.auto_open = Bootstrap.get('auto_open_last_journal') === 'true';" +
                "  result.auto_biometric = Bootstrap.get('auto_biometric') === 'true';" +
                "  var keys = {};" +
                "  var prefixes = ['journal_salt_', 'journal_verify_'];" +
                "  var journals = JSON.parse(result.journal_list);" +
                "  for (var i = 0; i < journals.length; i++) {" +
                "    var id = journals[i].id;" +
                "    var salt = Bootstrap.get('journal_salt_' + id);" +
                "    var verify = Bootstrap.get('journal_verify_' + id);" +
                "    if (salt) keys['journal_salt_' + id] = salt;" +
                "    if (verify) keys['journal_verify_' + id] = verify;" +
                "  }" +
                "  result.crypto_keys = keys;" +
                "  return JSON.stringify(result);" +
                "})();";

        webView.evaluateJavascript(js, value -> {
            if (value == null || "null".equals(value)) return;
            try {
                // Remove surrounding quotes and unescape
                String json = value;
                if (json.startsWith("\"") && json.endsWith("\"")) {
                    json = json.substring(1, json.length() - 1)
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                }
                JSONObject data = new JSONObject(json);
                SharedPreferences prefs = getSharedPreferences("journal_prefs", MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();

                editor.putString("journal_list", data.getString("journal_list"));
                editor.putString("last_journal_id", data.getString("last_journal_id"));
                editor.putBoolean("auto_open_last_journal", data.getBoolean("auto_open"));
                editor.putBoolean("auto_biometric", data.getBoolean("auto_biometric"));

                // Sync crypto keys (salt + verify tokens)
                JSONObject cryptoKeys = data.getJSONObject("crypto_keys");
                java.util.Iterator<String> iter = cryptoKeys.keys();
                while (iter.hasNext()) {
                    String k = iter.next();
                    // Convert from web key format (journal_salt_xxx) to native (salt_xxx)
                    String nativeKey;
                    if (k.startsWith("journal_salt_")) {
                        nativeKey = "salt_" + k.substring("journal_salt_".length());
                    } else if (k.startsWith("journal_verify_")) {
                        nativeKey = "verify_" + k.substring("journal_verify_".length());
                    } else {
                        continue;
                    }
                    editor.putString(nativeKey, cryptoKeys.getString(k));
                }

                editor.apply();
            } catch (JSONException e) {
                // Sync failed silently — not critical
            }
        });
    }

    // ========== Backup Folder Helpers ==========

    private String getBackupFolderDisplayName(Uri treeUri) {
        try {
            DocumentFile docFile = DocumentFile.fromTreeUri(this, treeUri);
            if (docFile != null && docFile.getName() != null) {
                return docFile.getName();
            }
        } catch (Exception ignored) {}
        // Fallback: show last segment of URI path
        String path = treeUri.getPath();
        if (path != null) {
            int idx = path.lastIndexOf('/');
            if (idx >= 0 && idx < path.length() - 1) return path.substring(idx + 1);
            // Try colon-separated (e.g. "primary:Documents/MyBackups")
            idx = path.lastIndexOf(':');
            if (idx >= 0 && idx < path.length() - 1) return path.substring(idx + 1);
        }
        return treeUri.toString();
    }

    // ========== Biometric Authentication ==========

    private static final String BIOMETRIC_PREFS = "biometric_prefs";

    private SharedPreferences getBiometricPrefs() {
        return getSharedPreferences(BIOMETRIC_PREFS, MODE_PRIVATE);
    }

    private void promptBiometric(final String callbackName) {
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
                        super.onAuthenticationSucceeded(result);
                        runOnUiThread(() ->
                            webView.evaluateJavascript(callbackName + "(true)", null)
                        );
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        runOnUiThread(() ->
                            webView.evaluateJavascript(callbackName + "(false)", null)
                        );
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // Don't callback yet — the system allows retries
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    /**
     * JavaScript interface for native file saving and biometric auth.
     * Called from web app when blob downloads don't work in WebView.
     */
    public class AndroidBridge {
        @JavascriptInterface
        public void saveFile(String filename, String base64Data, String mimeType) {
            try {
                byte[] data = Base64.decode(base64Data, Base64.DEFAULT);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // API 29+: Use MediaStore for scoped storage
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename);
                    values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS);

                    android.net.Uri uri = getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                        os.write(data);
                        os.close();
                    }
                } else {
                    // API 28 and below: write directly to Downloads
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    File file = new File(downloadsDir, filename);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(data);
                    fos.close();
                }

                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this,
                            "Saved to Downloads: " + filename, Toast.LENGTH_LONG).show()
                );
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this,
                            "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        }

        @JavascriptInterface
        public boolean isAndroid() {
            return true;
        }

        @JavascriptInterface
        public boolean hasNativeLogin() {
            return true;
        }

        @JavascriptInterface
        public void onAutoLoginComplete(String dashboardJson) {
            runOnUiThread(() -> {
                webView.setVisibility(View.VISIBLE);
            });
        }

        @JavascriptInterface
        public void onAutoLoginFailed(String error) {
            runOnUiThread(() -> {
                // Show WebView with web login as fallback
                webView.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this,
                        "Auto-login failed: " + error, Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public void onDashboardReady(String dashboardJson) {
            // No-op: native dashboard removed, web dashboard handles display
        }

        /**
         * Called from native DashboardActivity to refresh data.
         * Queries the WebView and returns updated JSON.
         */
        @JavascriptInterface
        public void requestDashboardRefresh() {
            runOnUiThread(() -> {
                webView.evaluateJavascript(
                    "(function(){ return typeof getDashboardDataJSON === 'function' ? getDashboardDataJSON() : '{}'; })()",
                    value -> {
                        // value comes back as a quoted string from evaluateJavascript
                    }
                );
            });
        }

        @JavascriptInterface
        public void returnToLogin() {
            runOnUiThread(() -> {
                // Sync metadata before returning
                syncJournalMetadata();
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }

        /**
         * Sync a single crypto key from web to SharedPreferences.
         * Called by web JS whenever salt or verify token changes.
         */
        @JavascriptInterface
        public void syncCryptoKey(String journalId, String salt, String verifyToken) {
            SharedPreferences prefs = getSharedPreferences("journal_prefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            if (salt != null) editor.putString("salt_" + journalId, salt);
            if (verifyToken != null) editor.putString("verify_" + journalId, verifyToken);
            editor.apply();
        }

        /**
         * Sync journal list from web to SharedPreferences.
         * Called by web JS whenever journal list changes.
         */
        @JavascriptInterface
        public void syncJournalList(String journalListJson) {
            SharedPreferences prefs = getSharedPreferences("journal_prefs", MODE_PRIVATE);
            prefs.edit().putString("journal_list", journalListJson).apply();
        }

        @JavascriptInterface
        public void openAbout() {
            Intent intent = new Intent(MainActivity.this, AboutActivity.class);
            startActivity(intent);
        }

        // ===== Biometric Methods =====

        @JavascriptInterface
        public boolean isBiometricAvailable() {
            BiometricManager biometricManager = BiometricManager.from(MainActivity.this);
            return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        }

        @JavascriptInterface
        public void saveCredential(String journalId, String password) {
            SharedPreferences prefs = getBiometricPrefs();
            // Store Base64-encoded password keyed by journalId
            String encoded = Base64.encodeToString(password.getBytes(), Base64.NO_WRAP);
            prefs.edit().putString("cred_" + journalId, encoded).apply();
        }

        @JavascriptInterface
        public void removeCredential(String journalId) {
            SharedPreferences prefs = getBiometricPrefs();
            prefs.edit().remove("cred_" + journalId).apply();
        }

        @JavascriptInterface
        public boolean hasCredential(String journalId) {
            SharedPreferences prefs = getBiometricPrefs();
            return prefs.contains("cred_" + journalId);
        }

        @JavascriptInterface
        public String getCredential(String journalId) {
            SharedPreferences prefs = getBiometricPrefs();
            String encoded = prefs.getString("cred_" + journalId, null);
            if (encoded == null) return null;
            return new String(Base64.decode(encoded, Base64.NO_WRAP));
        }

        @JavascriptInterface
        public void authenticate(String callbackName) {
            runOnUiThread(() -> promptBiometric(callbackName));
        }

        // ===== Backup Folder Methods =====

        @JavascriptInterface
        public void selectBackupFolder() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                folderPickerLauncher.launch(intent);
            });
        }

        @JavascriptInterface
        public String getBackupFolderName() {
            String uriStr = getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE)
                    .getString(BACKUP_FOLDER_URI_KEY, null);
            if (uriStr == null) return "";
            try {
                Uri treeUri = Uri.parse(uriStr);
                // Verify permission is still valid
                boolean hasPermission = false;
                for (android.content.UriPermission perm : getContentResolver().getPersistedUriPermissions()) {
                    if (perm.getUri().equals(treeUri) && perm.isWritePermission()) {
                        hasPermission = true;
                        break;
                    }
                }
                if (!hasPermission) {
                    getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE)
                            .edit().remove(BACKUP_FOLDER_URI_KEY).apply();
                    return "";
                }
                return getBackupFolderDisplayName(treeUri);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean hasBackupFolder() {
            String name = getBackupFolderName();
            return name != null && !name.isEmpty();
        }

        @JavascriptInterface
        public void clearBackupFolder() {
            String uriStr = getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE)
                    .getString(BACKUP_FOLDER_URI_KEY, null);
            if (uriStr != null) {
                try {
                    Uri treeUri = Uri.parse(uriStr);
                    getContentResolver().releasePersistableUriPermission(treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {}
            }
            getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE)
                    .edit().remove(BACKUP_FOLDER_URI_KEY).apply();
        }

        @JavascriptInterface
        public String listBackupFolderFiles() {
            String uriStr = getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE)
                    .getString(BACKUP_FOLDER_URI_KEY, null);
            if (uriStr == null) return "[]";
            try {
                Uri treeUri = Uri.parse(uriStr);
                DocumentFile folder = DocumentFile.fromTreeUri(MainActivity.this, treeUri);
                if (folder == null || !folder.canRead()) return "[]";

                org.json.JSONArray arr = new org.json.JSONArray();
                for (DocumentFile file : folder.listFiles()) {
                    String name = file.getName();
                    if (name != null && name.endsWith("_backup.json")) {
                        org.json.JSONObject obj = new org.json.JSONObject();
                        obj.put("name", name);
                        obj.put("size", file.length());
                        obj.put("lastModified", file.lastModified());
                        arr.put(obj);
                    }
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String readBackupFolderFile(String filename) {
            String uriStr = getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE)
                    .getString(BACKUP_FOLDER_URI_KEY, null);
            if (uriStr == null) return "";
            try {
                Uri treeUri = Uri.parse(uriStr);
                DocumentFile folder = DocumentFile.fromTreeUri(MainActivity.this, treeUri);
                if (folder == null || !folder.canRead()) return "";

                for (DocumentFile file : folder.listFiles()) {
                    if (filename.equals(file.getName())) {
                        java.io.InputStream is = getContentResolver().openInputStream(file.getUri());
                        if (is == null) return "";
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            baos.write(buf, 0, len);
                        }
                        is.close();
                        return baos.toString("UTF-8");
                    }
                }
                return "";
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean saveFileToBackupFolder(String filename, String base64Data, String mimeType) {
            String uriStr = getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE)
                    .getString(BACKUP_FOLDER_URI_KEY, null);
            if (uriStr == null) return false;
            try {
                Uri treeUri = Uri.parse(uriStr);
                DocumentFile folder = DocumentFile.fromTreeUri(MainActivity.this, treeUri);
                if (folder == null || !folder.canWrite()) return false;

                DocumentFile file = folder.createFile(mimeType, filename);
                if (file == null) return false;

                byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                java.io.OutputStream os = getContentResolver().openOutputStream(file.getUri());
                if (os == null) return false;
                os.write(data);
                os.close();

                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this,
                            "Backup saved: " + filename, Toast.LENGTH_LONG).show()
                );
                return true;
            } catch (Exception e) {
                runOnUiThread(() ->
                    Toast.makeText(MainActivity.this,
                            "Backup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
                return false;
            }
        }
    }
}
