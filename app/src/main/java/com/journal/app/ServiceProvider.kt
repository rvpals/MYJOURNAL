package com.journal.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

object ServiceProvider {

    var cryptoService: CryptoService? = null
        private set
    var bootstrapService: BootstrapService? = null
        private set
    var weatherService: WeatherService? = null
        private set
    var databaseService: DatabaseService? = null
        private set

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (cryptoService == null) cryptoService = CryptoService(context.applicationContext)
        if (bootstrapService == null) bootstrapService = BootstrapService(context.applicationContext)
        if (weatherService == null) weatherService = WeatherService()
        if (databaseService == null) databaseService = DatabaseService(context.applicationContext)
    }

    fun saveFileToDownloads(filename: String, base64Data: String, mimeType: String) {
        val ctx = appContext ?: return
        try {
            val data = Base64.decode(base64Data, Base64.DEFAULT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                FileOutputStream(File(downloadsDir, filename)).use { it.write(data) }
            }
        } catch (_: Exception) {
        }
    }

    fun clear() {
        databaseService?.close()
        cryptoService = null
        bootstrapService = null
        weatherService = null
        databaseService = null
        appContext = null
    }
}
