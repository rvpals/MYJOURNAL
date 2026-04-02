package com.journal.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import android.webkit.JsResult;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.webkit.JavascriptInterface;

import android.provider.MediaStore;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        configureWebView();
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
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

        // Responsive layout
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

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
    }
}
