package com.journal.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.journal.app.ThemeManager.C

class RichEditorActivity : AppCompatActivity() {

    private var lastThemeVersion = 0
    private lateinit var webView: WebView
    private var editorReady = false

    companion object {
        @JvmStatic var initialHtml: String = ""
        @JvmStatic var plainContent: String = ""
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rich_editor)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        webView = findViewById(R.id.rich_webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.setBackgroundColor(ThemeManager.color(C.BG))

        webView.addJavascriptInterface(EditorBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                applyThemeToEditor()
                val html = initialHtml
                if (html.isNotEmpty()) {
                    val escaped = escapeForJs(html)
                    webView.evaluateJavascript("setContent('$escaped')", null)
                }
                editorReady = true
            }
        }
        webView.webChromeClient = WebChromeClient()

        webView.loadUrl("file:///android_asset/rich_editor.html")

        findViewById<Button>(R.id.btn_back).setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.btn_done).setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.btn_copy_content).setOnClickListener { copyPlainContent() }
    }

    private fun applyThemeToEditor() {
        val bg = colorToHex(ThemeManager.color(C.BG))
        val cardBg = colorToHex(ThemeManager.color(C.CARD_BG))
        val text = colorToHex(ThemeManager.color(C.TEXT))
        val textSecondary = colorToHex(ThemeManager.color(C.TEXT_SECONDARY))
        val accent = colorToHex(ThemeManager.color(C.ACCENT))
        val border = colorToHex(ThemeManager.color(C.CARD_BORDER))
        val inputBg = colorToHex(ThemeManager.color(C.INPUT_BG))
        webView.evaluateJavascript(
            "applyTheme('$bg','$cardBg','$text','$textSecondary','$accent','$border','$inputBg')", null
        )
    }

    private fun copyPlainContent() {
        val text = plainContent
        if (text.isEmpty()) {
            Toast.makeText(this, "Plain content is empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (!editorReady) return
        val escaped = escapeForJs(text)
        webView.evaluateJavascript("setPlainText('$escaped')", null)
        Toast.makeText(this, "Content copied to rich editor", Toast.LENGTH_SHORT).show()
    }

    private fun saveAndFinish() {
        if (!editorReady) {
            finishWithResult("")
            return
        }
        webView.evaluateJavascript("getContent()") { raw ->
            val html = unescapeJsResult(raw)
            finishWithResult(html)
        }
    }

    private fun finishWithResult(html: String) {
        val intent = Intent()
        intent.putExtra("richContentHtml", html)
        setResult(Activity.RESULT_OK, intent)
        initialHtml = ""
        plainContent = ""
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        saveAndFinish()
    }

    override fun onResume() {
        super.onResume()
        if (lastThemeVersion != ThemeManager.themeVersion) {
            finish()
            return
        }
    }

    private fun colorToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    private fun escapeForJs(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun unescapeJsResult(raw: String): String {
        if (raw == "null" || raw.isEmpty()) return ""
        var s = raw
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length - 1)
        }
        return s.replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    inner class EditorBridge {
        @JavascriptInterface
        fun onReady() {
            runOnUiThread { editorReady = true }
        }
    }
}
