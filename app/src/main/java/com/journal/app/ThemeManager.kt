package com.journal.app

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.ScrollView
import android.widget.TextView

object ThemeManager {
    enum class C {
        BG, CARD_BG, CARD_BORDER, ACCENT, ACCENT_DARK,
        TEXT, TEXT_SECONDARY, INPUT_BG, ERROR,
        BIOMETRIC_BG, BIOMETRIC_BORDER, DELETE_BG, DELETE_BORDER,
        NAV_BG, NAV_TEXT, SUCCESS, TAG_BG, TAG_TEXT
    }

    var currentTheme: String = "dark"
        private set
    var themeVersion: Int = 0
        private set
    var fontScale: Float = 1.0f
        private set
    var uiTypeface: android.graphics.Typeface = android.graphics.Typeface.DEFAULT
        private set
    var uiFontFamily: String = ""
        private set

    private var colors: Map<C, Int> = mapOf()

    private val themes: Map<String, Map<C, Int>> = mapOf(
        "light" to mapOf(
            C.BG to 0xFFeef1f5.toInt(), C.CARD_BG to 0xFFffffff.toInt(),
            C.CARD_BORDER to 0xFF738598.toInt(), C.ACCENT to 0xFF1cb3c8.toInt(),
            C.ACCENT_DARK to 0xFF17929e.toInt(), C.TEXT to 0xFF2c3145.toInt(),
            C.TEXT_SECONDARY to 0xFF5a6478.toInt(), C.INPUT_BG to 0xFFf7f8fa.toInt(),
            C.ERROR to 0xFFe04858.toInt(), C.SUCCESS to 0xFF1cb36a.toInt(),
            C.NAV_BG to 0xFF3c415e.toInt(), C.NAV_TEXT to 0xFFdfe2e2.toInt(),
            C.TAG_BG to 0xFFd6f4f9.toInt(), C.TAG_TEXT to 0xFF0e7a8a.toInt(),
            C.BIOMETRIC_BG to 0xFF1a5a3a.toInt(), C.BIOMETRIC_BORDER to 0xFF2a8a4e.toInt(),
            C.DELETE_BG to 0xFF5a2a2a.toInt(), C.DELETE_BORDER to 0xFF8a3a3a.toInt()
        ),
        "dark" to mapOf(
            C.BG to 0xFF3c415e.toInt(), C.CARD_BG to 0xFF454a6b.toInt(),
            C.CARD_BORDER to 0xFF555a7e.toInt(), C.ACCENT to 0xFF1cb3c8.toInt(),
            C.ACCENT_DARK to 0xFF179aac.toInt(), C.TEXT to 0xFFe0e0e0.toInt(),
            C.TEXT_SECONDARY to 0xFFa0a4b8.toInt(), C.INPUT_BG to 0xFF3a3f5c.toInt(),
            C.ERROR to 0xFFff6b6b.toInt(), C.SUCCESS to 0xFF2dd47a.toInt(),
            C.NAV_BG to 0xFF2a2d45.toInt(), C.NAV_TEXT to 0xFFdfe2e2.toInt(),
            C.TAG_BG to 0xFF2a5a62.toInt(), C.TAG_TEXT to 0xFF8aeaf5.toInt(),
            C.BIOMETRIC_BG to 0xFF2a5a3a.toInt(), C.BIOMETRIC_BORDER to 0xFF3a8a4e.toInt(),
            C.DELETE_BG to 0xFF5a2a2a.toInt(), C.DELETE_BORDER to 0xFF8a3a3a.toInt()
        ),
        "ocean" to mapOf(
            C.BG to 0xFF0b1a2e.toInt(), C.CARD_BG to 0xFF132d4f.toInt(),
            C.CARD_BORDER to 0xFF1a5276.toInt(), C.ACCENT to 0xFF00e5ff.toInt(),
            C.ACCENT_DARK to 0xFF00bcd4.toInt(), C.TEXT to 0xFFe8f1f8.toInt(),
            C.TEXT_SECONDARY to 0xFF8bb8d0.toInt(), C.INPUT_BG to 0xFF0f2240.toInt(),
            C.ERROR to 0xFFff5252.toInt(), C.SUCCESS to 0xFF00e676.toInt(),
            C.NAV_BG to 0xFF061325.toInt(), C.NAV_TEXT to 0xFFc0dff0.toInt(),
            C.TAG_BG to 0xFF0a3d5c.toInt(), C.TAG_TEXT to 0xFF7fecff.toInt(),
            C.BIOMETRIC_BG to 0xFF0a3d2e.toInt(), C.BIOMETRIC_BORDER to 0xFF00a058.toInt(),
            C.DELETE_BG to 0xFF4a1a1a.toInt(), C.DELETE_BORDER to 0xFF8a2a2a.toInt()
        ),
        "midnight" to mapOf(
            C.BG to 0xFF0d0d1a.toInt(), C.CARD_BG to 0xFF1a1a3e.toInt(),
            C.CARD_BORDER to 0xFF2e2e6e.toInt(), C.ACCENT to 0xFF7c6aff.toInt(),
            C.ACCENT_DARK to 0xFF9d8fff.toInt(), C.TEXT to 0xFFe0e0f0.toInt(),
            C.TEXT_SECONDARY to 0xFF9090c0.toInt(), C.INPUT_BG to 0xFF13132b.toInt(),
            C.ERROR to 0xFFff4f6e.toInt(), C.SUCCESS to 0xFF50e898.toInt(),
            C.NAV_BG to 0xFF08081a.toInt(), C.NAV_TEXT to 0xFFc0c0e0.toInt(),
            C.TAG_BG to 0xFF2a2a5c.toInt(), C.TAG_TEXT to 0xFFb8adff.toInt(),
            C.BIOMETRIC_BG to 0xFF1a3a2a.toInt(), C.BIOMETRIC_BORDER to 0xFF30a060.toInt(),
            C.DELETE_BG to 0xFF4a1520.toInt(), C.DELETE_BORDER to 0xFF8a2a38.toInt()
        ),
        "forest" to mapOf(
            C.BG to 0xFFf0f4ee.toInt(), C.CARD_BG to 0xFFffffff.toInt(),
            C.CARD_BORDER to 0xFF7a9a6e.toInt(), C.ACCENT to 0xFF3a8a4e.toInt(),
            C.ACCENT_DARK to 0xFF2e7040.toInt(), C.TEXT to 0xFF2a3a24.toInt(),
            C.TEXT_SECONDARY to 0xFF5a7050.toInt(), C.INPUT_BG to 0xFFf5f8f3.toInt(),
            C.ERROR to 0xFFc4483a.toInt(), C.SUCCESS to 0xFF3a8a4e.toInt(),
            C.NAV_BG to 0xFF2a3a24.toInt(), C.NAV_TEXT to 0xFFd4e4ce.toInt(),
            C.TAG_BG to 0xFFd4eed8.toInt(), C.TAG_TEXT to 0xFF2a6a38.toInt(),
            C.BIOMETRIC_BG to 0xFF2a5a3a.toInt(), C.BIOMETRIC_BORDER to 0xFF3a8a4e.toInt(),
            C.DELETE_BG to 0xFF5a2a2a.toInt(), C.DELETE_BORDER to 0xFF8a3a3a.toInt()
        ),
        "amethyst" to mapOf(
            C.BG to 0xFF1a1028.toInt(), C.CARD_BG to 0xFF2c1d48.toInt(),
            C.CARD_BORDER to 0xFF4a3478.toInt(), C.ACCENT to 0xFFb47aff.toInt(),
            C.ACCENT_DARK to 0xFFc99aff.toInt(), C.TEXT to 0xFFe8dff5.toInt(),
            C.TEXT_SECONDARY to 0xFFb8a0d8.toInt(), C.INPUT_BG to 0xFF231538.toInt(),
            C.ERROR to 0xFFff5080.toInt(), C.SUCCESS to 0xFF60e8a0.toInt(),
            C.NAV_BG to 0xFF110a1e.toInt(), C.NAV_TEXT to 0xFFd0c0e8.toInt(),
            C.TAG_BG to 0xFF3a2560.toInt(), C.TAG_TEXT to 0xFFd4b8ff.toInt(),
            C.BIOMETRIC_BG to 0xFF1a3028.toInt(), C.BIOMETRIC_BORDER to 0xFF40a068.toInt(),
            C.DELETE_BG to 0xFF4a1520.toInt(), C.DELETE_BORDER to 0xFF8a2a40.toInt()
        ),
        "aurora" to mapOf(
            C.BG to 0xFF0a1520.toInt(), C.CARD_BG to 0xFF132840.toInt(),
            C.CARD_BORDER to 0xFF1e4060.toInt(), C.ACCENT to 0xFF00e0c0.toInt(),
            C.ACCENT_DARK to 0xFF20ffd8.toInt(), C.TEXT to 0xFFe0f0f8.toInt(),
            C.TEXT_SECONDARY to 0xFF88c0d8.toInt(), C.INPUT_BG to 0xFF0e1d30.toInt(),
            C.ERROR to 0xFFff5070.toInt(), C.SUCCESS to 0xFF00e0c0.toInt(),
            C.NAV_BG to 0xFF060e18.toInt(), C.NAV_TEXT to 0xFFb0d8e8.toInt(),
            C.TAG_BG to 0xFF0a3848.toInt(), C.TAG_TEXT to 0xFF70ffd8.toInt(),
            C.BIOMETRIC_BG to 0xFF0a3830.toInt(), C.BIOMETRIC_BORDER to 0xFF00a080.toInt(),
            C.DELETE_BG to 0xFF4a1520.toInt(), C.DELETE_BORDER to 0xFF8a2a38.toInt()
        ),
        "lavender" to mapOf(
            C.BG to 0xFFf0ecf8.toInt(), C.CARD_BG to 0xFFffffff.toInt(),
            C.CARD_BORDER to 0xFF9880c0.toInt(), C.ACCENT to 0xFF7c5cbf.toInt(),
            C.ACCENT_DARK to 0xFF6a48a8.toInt(), C.TEXT to 0xFF2a2040.toInt(),
            C.TEXT_SECONDARY to 0xFF6858a0.toInt(), C.INPUT_BG to 0xFFf6f2fc.toInt(),
            C.ERROR to 0xFFd04858.toInt(), C.SUCCESS to 0xFF48a868.toInt(),
            C.NAV_BG to 0xFF2a2040.toInt(), C.NAV_TEXT to 0xFFd8d0e8.toInt(),
            C.TAG_BG to 0xFFe0d4f4.toInt(), C.TAG_TEXT to 0xFF5a3e9e.toInt(),
            C.BIOMETRIC_BG to 0xFF2a4a38.toInt(), C.BIOMETRIC_BORDER to 0xFF3a8a50.toInt(),
            C.DELETE_BG to 0xFF5a2a2a.toInt(), C.DELETE_BORDER to 0xFF8a3a3a.toInt()
        ),
        "frost" to mapOf(
            C.BG to 0xFFf0f6fa.toInt(), C.CARD_BG to 0xFFffffff.toInt(),
            C.CARD_BORDER to 0xFF7aaac8.toInt(), C.ACCENT to 0xFF18b8d8.toInt(),
            C.ACCENT_DARK to 0xFF10a0c0.toInt(), C.TEXT to 0xFF1a2e40.toInt(),
            C.TEXT_SECONDARY to 0xFF4878a0.toInt(), C.INPUT_BG to 0xFFf4f8fc.toInt(),
            C.ERROR to 0xFFe04858.toInt(), C.SUCCESS to 0xFF28b878.toInt(),
            C.NAV_BG to 0xFF1a2e40.toInt(), C.NAV_TEXT to 0xFFc8e0f0.toInt(),
            C.TAG_BG to 0xFFd0eef6.toInt(), C.TAG_TEXT to 0xFF107898.toInt(),
            C.BIOMETRIC_BG to 0xFF1a4a38.toInt(), C.BIOMETRIC_BORDER to 0xFF28a060.toInt(),
            C.DELETE_BG to 0xFF5a2a2a.toInt(), C.DELETE_BORDER to 0xFF8a3a3a.toInt()
        ),
        "navy" to mapOf(
            C.BG to 0xFF0c1428.toInt(), C.CARD_BG to 0xFF162040.toInt(),
            C.CARD_BORDER to 0xFF1e3a6e.toInt(), C.ACCENT to 0xFF3a7eff.toInt(),
            C.ACCENT_DARK to 0xFF5a98ff.toInt(), C.TEXT to 0xFFdce4f0.toInt(),
            C.TEXT_SECONDARY to 0xFF7a9ac0.toInt(), C.INPUT_BG to 0xFF111c38.toInt(),
            C.ERROR to 0xFFff5060.toInt(), C.SUCCESS to 0xFF30d880.toInt(),
            C.NAV_BG to 0xFF060c1e.toInt(), C.NAV_TEXT to 0xFFb0c4e0.toInt(),
            C.TAG_BG to 0xFF102850.toInt(), C.TAG_TEXT to 0xFF7ab0ff.toInt(),
            C.BIOMETRIC_BG to 0xFF0a2a1a.toInt(), C.BIOMETRIC_BORDER to 0xFF20a058.toInt(),
            C.DELETE_BG to 0xFF4a1520.toInt(), C.DELETE_BORDER to 0xFF8a2a38.toInt()
        ),
        "sunflower" to mapOf(
            C.BG to 0xFFfaf6e8.toInt(), C.CARD_BG to 0xFFfffef8.toInt(),
            C.CARD_BORDER to 0xFFc8a840.toInt(), C.ACCENT to 0xFFd4a010.toInt(),
            C.ACCENT_DARK to 0xFFb88a00.toInt(), C.TEXT to 0xFF3a3018.toInt(),
            C.TEXT_SECONDARY to 0xFF7a6830.toInt(), C.INPUT_BG to 0xFFfcf9f0.toInt(),
            C.ERROR to 0xFFc84838.toInt(), C.SUCCESS to 0xFF58a050.toInt(),
            C.NAV_BG to 0xFF3a3018.toInt(), C.NAV_TEXT to 0xFFf0e4c0.toInt(),
            C.TAG_BG to 0xFFf5e8b0.toInt(), C.TAG_TEXT to 0xFF8a7010.toInt(),
            C.BIOMETRIC_BG to 0xFF2a4a2a.toInt(), C.BIOMETRIC_BORDER to 0xFF48a048.toInt(),
            C.DELETE_BG to 0xFF5a2a2a.toInt(), C.DELETE_BORDER to 0xFF8a3a3a.toInt()
        ),
        "meadow" to mapOf(
            C.BG to 0xFFeef8ee.toInt(), C.CARD_BG to 0xFFf8fff8.toInt(),
            C.CARD_BORDER to 0xFF68b070.toInt(), C.ACCENT to 0xFF2eaa48.toInt(),
            C.ACCENT_DARK to 0xFF228a38.toInt(), C.TEXT to 0xFF1a3020.toInt(),
            C.TEXT_SECONDARY to 0xFF4a7a50.toInt(), C.INPUT_BG to 0xFFf2faf2.toInt(),
            C.ERROR to 0xFFd04848.toInt(), C.SUCCESS to 0xFF2eaa48.toInt(),
            C.NAV_BG to 0xFF1a3020.toInt(), C.NAV_TEXT to 0xFFc0e4c8.toInt(),
            C.TAG_BG to 0xFFc8f0cc.toInt(), C.TAG_TEXT to 0xFF1a7a28.toInt(),
            C.BIOMETRIC_BG to 0xFF1a4a2a.toInt(), C.BIOMETRIC_BORDER to 0xFF2a8a40.toInt(),
            C.DELETE_BG to 0xFF5a2a2a.toInt(), C.DELETE_BORDER to 0xFF8a3a3a.toInt()
        ),
        "rose" to mapOf(
            C.BG to 0xFF1e1018.toInt(), C.CARD_BG to 0xFF2e1a28.toInt(),
            C.CARD_BORDER to 0xFF5a3050.toInt(), C.ACCENT to 0xFFe8608a.toInt(),
            C.ACCENT_DARK to 0xFFf080a0.toInt(), C.TEXT to 0xFFf0e0e8.toInt(),
            C.TEXT_SECONDARY to 0xFFc8a0b8.toInt(), C.INPUT_BG to 0xFF281420.toInt(),
            C.ERROR to 0xFFff5060.toInt(), C.SUCCESS to 0xFF50d898.toInt(),
            C.NAV_BG to 0xFF140a12.toInt(), C.NAV_TEXT to 0xFFe0c8d8.toInt(),
            C.TAG_BG to 0xFF4a2040.toInt(), C.TAG_TEXT to 0xFFf8a0c8.toInt(),
            C.BIOMETRIC_BG to 0xFF1a3028.toInt(), C.BIOMETRIC_BORDER to 0xFF38a060.toInt(),
            C.DELETE_BG to 0xFF4a1520.toInt(), C.DELETE_BORDER to 0xFF8a2a38.toInt()
        ),
        "copper" to mapOf(
            C.BG to 0xFF1a1410.toInt(), C.CARD_BG to 0xFF2a2018.toInt(),
            C.CARD_BORDER to 0xFF5a4030.toInt(), C.ACCENT to 0xFFd08040.toInt(),
            C.ACCENT_DARK to 0xFFe89850.toInt(), C.TEXT to 0xFFf0e8e0.toInt(),
            C.TEXT_SECONDARY to 0xFFc0a888.toInt(), C.INPUT_BG to 0xFF221a14.toInt(),
            C.ERROR to 0xFFe85040.toInt(), C.SUCCESS to 0xFF58c868.toInt(),
            C.NAV_BG to 0xFF100c08.toInt(), C.NAV_TEXT to 0xFFe0d0c0.toInt(),
            C.TAG_BG to 0xFF4a3020.toInt(), C.TAG_TEXT to 0xFFf0b870.toInt(),
            C.BIOMETRIC_BG to 0xFF1a3820.toInt(), C.BIOMETRIC_BORDER to 0xFF38a048.toInt(),
            C.DELETE_BG to 0xFF4a1a1a.toInt(), C.DELETE_BORDER to 0xFF8a3030.toInt()
        ),
        "slate" to mapOf(
            C.BG to 0xFFf0f2f5.toInt(), C.CARD_BG to 0xFFffffff.toInt(),
            C.CARD_BORDER to 0xFF8a9aaa.toInt(), C.ACCENT to 0xFF4a7a9a.toInt(),
            C.ACCENT_DARK to 0xFF3a6a88.toInt(), C.TEXT to 0xFF2a3440.toInt(),
            C.TEXT_SECONDARY to 0xFF5a6a7a.toInt(), C.INPUT_BG to 0xFFf5f7fa.toInt(),
            C.ERROR to 0xFFd04848.toInt(), C.SUCCESS to 0xFF38a060.toInt(),
            C.NAV_BG to 0xFF2a3440.toInt(), C.NAV_TEXT to 0xFFd0dae4.toInt(),
            C.TAG_BG to 0xFFd8e4ee.toInt(), C.TAG_TEXT to 0xFF3a5a78.toInt(),
            C.BIOMETRIC_BG to 0xFF2a4a38.toInt(), C.BIOMETRIC_BORDER to 0xFF3a8a50.toInt(),
            C.DELETE_BG to 0xFF5a2a2a.toInt(), C.DELETE_BORDER to 0xFF8a3a3a.toInt()
        ),
        "ember" to mapOf(
            C.BG to 0xFF1a0e0a.toInt(), C.CARD_BG to 0xFF2a1810.toInt(),
            C.CARD_BORDER to 0xFF5a2e1a.toInt(), C.ACCENT to 0xFFe85020.toInt(),
            C.ACCENT_DARK to 0xFFf06830.toInt(), C.TEXT to 0xFFf0e4e0.toInt(),
            C.TEXT_SECONDARY to 0xFFc09888.toInt(), C.INPUT_BG to 0xFF221410.toInt(),
            C.ERROR to 0xFFff4040.toInt(), C.SUCCESS to 0xFF48c870.toInt(),
            C.NAV_BG to 0xFF100808.toInt(), C.NAV_TEXT to 0xFFe0c8c0.toInt(),
            C.TAG_BG to 0xFF4a2010.toInt(), C.TAG_TEXT to 0xFFf8a070.toInt(),
            C.BIOMETRIC_BG to 0xFF1a3820.toInt(), C.BIOMETRIC_BORDER to 0xFF38a048.toInt(),
            C.DELETE_BG to 0xFF4a1010.toInt(), C.DELETE_BORDER to 0xFF8a2020.toInt()
        ),
        "sage" to mapOf(
            C.BG to 0xFFf2f4f0.toInt(), C.CARD_BG to 0xFFfcfdfa.toInt(),
            C.CARD_BORDER to 0xFF8a9a80.toInt(), C.ACCENT to 0xFF6a8a60.toInt(),
            C.ACCENT_DARK to 0xFF587850.toInt(), C.TEXT to 0xFF2a3428.toInt(),
            C.TEXT_SECONDARY to 0xFF5a6a58.toInt(), C.INPUT_BG to 0xFFf6f8f4.toInt(),
            C.ERROR to 0xFFc84848.toInt(), C.SUCCESS to 0xFF4a9a50.toInt(),
            C.NAV_BG to 0xFF2a3428.toInt(), C.NAV_TEXT to 0xFFd0dece.toInt(),
            C.TAG_BG to 0xFFdae8d6.toInt(), C.TAG_TEXT to 0xFF3a6838.toInt(),
            C.BIOMETRIC_BG to 0xFF2a4a30.toInt(), C.BIOMETRIC_BORDER to 0xFF3a8a48.toInt(),
            C.DELETE_BG to 0xFF5a2a2a.toInt(), C.DELETE_BORDER to 0xFF8a3a3a.toInt()
        ),
        "dusk" to mapOf(
            C.BG to 0xFF181020.toInt(), C.CARD_BG to 0xFF281e38.toInt(),
            C.CARD_BORDER to 0xFF483860.toInt(), C.ACCENT to 0xFFc868a0.toInt(),
            C.ACCENT_DARK to 0xFFe080b8.toInt(), C.TEXT to 0xFFe8e0f0.toInt(),
            C.TEXT_SECONDARY to 0xFFa898c0.toInt(), C.INPUT_BG to 0xFF201530.toInt(),
            C.ERROR to 0xFFf05060.toInt(), C.SUCCESS to 0xFF50d888.toInt(),
            C.NAV_BG to 0xFF0e0818.toInt(), C.NAV_TEXT to 0xFFd0c0e0.toInt(),
            C.TAG_BG to 0xFF382850.toInt(), C.TAG_TEXT to 0xFFe0a0d0.toInt(),
            C.BIOMETRIC_BG to 0xFF182e28.toInt(), C.BIOMETRIC_BORDER to 0xFF30a060.toInt(),
            C.DELETE_BG to 0xFF4a1520.toInt(), C.DELETE_BORDER to 0xFF8a2a38.toInt()
        ),
        "mocha" to mapOf(
            C.BG to 0xFF1c1614.toInt(), C.CARD_BG to 0xFF2c2420.toInt(),
            C.CARD_BORDER to 0xFF504038.toInt(), C.ACCENT to 0xFFb88a50.toInt(),
            C.ACCENT_DARK to 0xFFd0a060.toInt(), C.TEXT to 0xFFf0e8e0.toInt(),
            C.TEXT_SECONDARY to 0xFFb8a898.toInt(), C.INPUT_BG to 0xFF241c18.toInt(),
            C.ERROR to 0xFFe05048.toInt(), C.SUCCESS to 0xFF58b860.toInt(),
            C.NAV_BG to 0xFF120e0c.toInt(), C.NAV_TEXT to 0xFFdcd0c4.toInt(),
            C.TAG_BG to 0xFF403020.toInt(), C.TAG_TEXT to 0xFFe0c080.toInt(),
            C.BIOMETRIC_BG to 0xFF1a3820.toInt(), C.BIOMETRIC_BORDER to 0xFF38a048.toInt(),
            C.DELETE_BG to 0xFF4a1a1a.toInt(), C.DELETE_BORDER to 0xFF8a3030.toInt()
        ),
        "arctic" to mapOf(
            C.BG to 0xFF0a1820.toInt(), C.CARD_BG to 0xFF122838.toInt(),
            C.CARD_BORDER to 0xFF1e4868.toInt(), C.ACCENT to 0xFF60c8e8.toInt(),
            C.ACCENT_DARK to 0xFF80d8f0.toInt(), C.TEXT to 0xFFe4f0f8.toInt(),
            C.TEXT_SECONDARY to 0xFF88b8d0.toInt(), C.INPUT_BG to 0xFF0e2030.toInt(),
            C.ERROR to 0xFFf05868.toInt(), C.SUCCESS to 0xFF48d890.toInt(),
            C.NAV_BG to 0xFF060e18.toInt(), C.NAV_TEXT to 0xFFb8d8e8.toInt(),
            C.TAG_BG to 0xFF0e3848.toInt(), C.TAG_TEXT to 0xFF90e0f8.toInt(),
            C.BIOMETRIC_BG to 0xFF0a3028.toInt(), C.BIOMETRIC_BORDER to 0xFF28a068.toInt(),
            C.DELETE_BG to 0xFF4a1520.toInt(), C.DELETE_BORDER to 0xFF8a2a38.toInt()
        )
    )

    fun init() {
        val settingsJson = ServiceProvider.databaseService?.getSettings() ?: "{}"
        val settings = try { org.json.JSONObject(settingsJson) } catch (_: Exception) { org.json.JSONObject() }
        val theme = settings.optString("theme", "dark").trim('"')
        setTheme(theme)
        loadFontSettings()
    }

    fun setTheme(name: String) {
        currentTheme = if (themes.containsKey(name)) name else "dark"
        colors = themes[currentTheme] ?: themes["dark"]!!
        themeVersion++
    }

    fun loadFontSettings() {
        val bs = ServiceProvider.bootstrapService ?: return
        fontScale = bs.get("ui_font_scale")?.toFloatOrNull() ?: 1.0f
        uiFontFamily = bs.get("ui_font_family") ?: ""
        uiTypeface = resolveTypeface(uiFontFamily)
        themeVersion++
    }

    private fun resolveTypeface(family: String): android.graphics.Typeface {
        if (family.isEmpty()) return android.graphics.Typeface.DEFAULT
        return when {
            family.equals("serif", true) -> android.graphics.Typeface.SERIF
            family.equals("monospace", true) -> android.graphics.Typeface.MONOSPACE
            family.equals("sans-serif", true) -> android.graphics.Typeface.SANS_SERIF
            else -> try { android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL) } catch (_: Exception) { android.graphics.Typeface.DEFAULT }
        }
    }

    fun fontScaledContext(context: android.content.Context): android.content.Context {
        if (fontScale == 1.0f) return context
        val config = android.content.res.Configuration(context.resources.configuration)
        config.fontScale = fontScale
        return context.createConfigurationContext(config)
    }

    fun color(c: C): Int = colors[c] ?: 0xFF000000.toInt()

    fun colorStateList(c: C): ColorStateList = ColorStateList.valueOf(color(c))

    private val xmlBgColor = 0xFF3c415e.toInt()
    private val xmlCardBgColor = 0xFF454a6b.toInt()
    private val xmlInputBgColor = 0xFF3a3f5c.toInt()
    private val xmlAccentColor = 0xFF1cb3c8.toInt()
    private val xmlTextColor = 0xFFe0e0e0.toInt()
    private val xmlTextSecondaryColor = 0xFFa0a4b8.toInt()

    fun applyToActivity(activity: Activity) {
        activity.window.statusBarColor = color(C.NAV_BG)
        activity.window.navigationBarColor = color(C.NAV_BG)
        activity.window.decorView.setBackgroundColor(color(C.BG))
        val contentView = activity.findViewById<View>(android.R.id.content)
        if (contentView is ViewGroup && contentView.childCount > 0) {
            recolorViewTree(contentView.getChildAt(0))
        }
        if (uiFontFamily.isNotEmpty()) {
            contentView.post { applyTypefaceToViewTree(contentView) }
        }
        val isLight = isLightTheme()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = activity.window.insetsController
            if (controller != null) {
                if (isLight) {
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                } else {
                    controller.setSystemBarsAppearance(
                        0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                }
            }
        } else {
            @Suppress("DEPRECATION")
            if (isLight) {
                activity.window.decorView.systemUiVisibility =
                    activity.window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                activity.window.decorView.systemUiVisibility =
                    activity.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
    }

    private val xmlColorRemap = mapOf(
        0xFF3c415e.toInt() to C.BG,
        0xFF454a6b.toInt() to C.CARD_BG,
        0xFF555a7e.toInt() to C.CARD_BORDER,
        0xFF3a3f5c.toInt() to C.INPUT_BG,
        0xFF1cb3c8.toInt() to C.ACCENT,
        0xFF179aac.toInt() to C.ACCENT_DARK,
        0xFFe0e0e0.toInt() to C.TEXT,
        0xFFa0a4b8.toInt() to C.TEXT_SECONDARY,
        0xFFff6b6b.toInt() to C.ERROR,
        0xFF2a5a3a.toInt() to C.BIOMETRIC_BG,
        0xFF3a8a4e.toInt() to C.BIOMETRIC_BORDER,
        0xFF5a2a2a.toInt() to C.DELETE_BG,
        0xFF8a3a3a.toInt() to C.DELETE_BORDER
    )

    private fun remapColor(original: Int): Int {
        val slot = xmlColorRemap[original or 0xFF000000.toInt()] ?: return original
        return color(slot)
    }

    private fun recolorViewTree(view: View) {
        recolorBackground(view)
        if (view is TextView) {
            val remapped = remapColor(view.currentTextColor)
            if (remapped != view.currentTextColor) view.setTextColor(remapped)
            val hintColor = view.currentHintTextColor
            val remappedHint = remapColor(hintColor)
            if (remappedHint != hintColor) view.setHintTextColor(remappedHint)
            if (uiFontFamily.isNotEmpty()) {
                val style = view.typeface?.style ?: android.graphics.Typeface.NORMAL
                view.typeface = android.graphics.Typeface.create(uiTypeface, style)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                recolorViewTree(view.getChildAt(i))
            }
        }
    }

    private fun recolorBackground(view: View) {
        val bg = view.background ?: return
        recolorDrawable(bg)
    }

    private fun recolorDrawable(d: android.graphics.drawable.Drawable) {
        when (d) {
            is ColorDrawable -> {
                val remapped = remapColor(d.color)
                if (remapped != d.color) d.color = remapped
            }
            is GradientDrawable -> {
                try {
                    val field = GradientDrawable::class.java.getDeclaredField("mGradientState")
                    field.isAccessible = true
                    val state = field.get(d)
                    val solidField = state.javaClass.getDeclaredField("mSolidColors")
                    solidField.isAccessible = true
                    val solidColors = solidField.get(state) as? android.content.res.ColorStateList
                    if (solidColors != null) {
                        val c = solidColors.defaultColor
                        val remapped = remapColor(c)
                        if (remapped != c) d.setColor(remapped)
                    }
                    val strokeColorField = state.javaClass.getDeclaredField("mStrokeColors")
                    strokeColorField.isAccessible = true
                    val strokeColors = strokeColorField.get(state) as? android.content.res.ColorStateList
                    if (strokeColors != null) {
                        val sc = strokeColors.defaultColor
                        val remapped = remapColor(sc)
                        if (remapped != sc) {
                            val strokeWidthField = state.javaClass.getDeclaredField("mStrokeWidth")
                            strokeWidthField.isAccessible = true
                            val strokeWidth = strokeWidthField.getInt(state)
                            d.setStroke(strokeWidth, remapped)
                        }
                    }
                } catch (_: Exception) {}
            }
            is LayerDrawable -> {
                for (i in 0 until d.numberOfLayers) {
                    recolorDrawable(d.getDrawable(i))
                }
            }
            is StateListDrawable -> {
                try {
                    val cField = StateListDrawable::class.java.superclass?.getDeclaredField("mDrawableContainerState")
                    cField?.isAccessible = true
                    val containerState = cField?.get(d)
                    val drawablesField = containerState?.javaClass?.getDeclaredField("mDrawables")
                    drawablesField?.isAccessible = true
                    val drawables = drawablesField?.get(containerState) as? Array<*>
                    drawables?.forEach { child ->
                        if (child is android.graphics.drawable.Drawable) recolorDrawable(child)
                    }
                } catch (_: Exception) {}
            }
            is RippleDrawable -> {
                for (i in 0 until d.numberOfLayers) {
                    recolorDrawable(d.getDrawable(i))
                }
            }
        }
    }

    fun isLightTheme(): Boolean {
        val bg = color(C.BG)
        val r = Color.red(bg)
        val g = Color.green(bg)
        val b = Color.blue(bg)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return luminance > 0.5
    }

    fun cardDrawable(radiusDp: Float = 10f): GradientDrawable = GradientDrawable().apply {
        setColor(color(C.CARD_BG))
        setStroke(2, color(C.CARD_BORDER))
        cornerRadius = radiusDp
    }

    fun inputDrawable(radiusDp: Float = 8f): GradientDrawable = GradientDrawable().apply {
        setColor(color(C.INPUT_BG))
        setStroke(2, color(C.CARD_BORDER))
        cornerRadius = radiusDp
    }

    fun accentButtonDrawable(radiusDp: Float = 8f): GradientDrawable = GradientDrawable().apply {
        setColor(color(C.ACCENT))
        cornerRadius = radiusDp
    }

    fun applyTypefaceToViewTree(view: View) {
        if (uiFontFamily.isEmpty()) return
        if (view is TextView) {
            val style = view.typeface?.style ?: android.graphics.Typeface.NORMAL
            view.typeface = android.graphics.Typeface.create(uiTypeface, style)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTypefaceToViewTree(view.getChildAt(i))
            }
        }
    }
}
