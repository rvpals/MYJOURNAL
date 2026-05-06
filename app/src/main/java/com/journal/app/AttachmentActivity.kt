package com.journal.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.journal.app.ThemeManager.C
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class AttachmentActivity : AppCompatActivity() {

    companion object {
        @JvmStatic var databaseService: DatabaseService? = null
        @JvmStatic var bootstrapService: BootstrapService? = null
        @JvmStatic var pendingEntryId: String? = null
        @JvmStatic var pendingEntryTitle: String? = null
        @JvmStatic var pendingEntryDate: String? = null
        private const val FILE_PICK = 3001
        private const val DOWNLOAD_PICK = 3002
    }

    private lateinit var db: DatabaseService
    private lateinit var bs: BootstrapService
    private var entryId = ""
    private var entryTitle = ""
    private var entryDate = ""
    private var attachmentId: String? = null
    private var existingFilename: String? = null

    private val pendingFiles = mutableListOf<Pair<String, Uri>>() // name to uri
    private lateinit var fileGrid: LinearLayout

    private var lastThemeVersion = 0

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(ThemeManager.fontScaledContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.applyToActivity(this)
        lastThemeVersion = ThemeManager.themeVersion

        db = databaseService ?: run { finish(); return }
        bs = bootstrapService ?: run { finish(); return }
        databaseService = null
        bootstrapService = null

        entryId = pendingEntryId ?: ""
        entryTitle = pendingEntryTitle ?: ""
        entryDate = pendingEntryDate ?: ""
        pendingEntryId = null
        pendingEntryTitle = null
        pendingEntryDate = null

        if (entryId.isEmpty()) { finish(); return }

        loadExistingAttachment()
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        if (ThemeManager.themeVersion != lastThemeVersion) {
            lastThemeVersion = ThemeManager.themeVersion
            recreate()
        }
    }

    private fun loadExistingAttachment() {
        val json = db.getAttachmentsByEntry(entryId)
        val arr = try { org.json.JSONArray(json) } catch (_: Exception) { return }
        if (arr.length() > 0) {
            val obj = arr.getJSONObject(0)
            attachmentId = obj.optString("id", "")
            existingFilename = obj.optString("filename", "")
        }
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ThemeManager.color(C.BG))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Navbar
        val navbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(ThemeManager.color(C.CARD_BG))
            elevation = dp(4).toFloat()
        }
        navbar.addView(Button(this).apply {
            text = "←"
            textSize = 18f
            background = ContextCompat.getDrawable(this@AttachmentActivity, R.drawable.btn_secondary)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(8) }
            setOnClickListener { finish() }
        })
        navbar.addView(TextView(this).apply {
            text = "Attachments"
            setTextColor(ThemeManager.color(C.ACCENT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(navbar)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Entry info box
        val infoBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ThemeManager.color(C.CARD_BG))
                setStroke(dp(1), ThemeManager.color(C.CARD_BORDER))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        infoBox.addView(TextView(this).apply {
            text = entryTitle.ifEmpty { "(No title)" }
            setTextColor(ThemeManager.color(C.TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
        })
        if (entryDate.isNotEmpty()) {
            infoBox.addView(TextView(this).apply {
                text = entryDate
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(2), 0, 0)
            })
        }
        infoBox.addView(Button(this).apply {
            text = "👁️ View Entry"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@AttachmentActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)
            ).apply { topMargin = dp(8) }
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { openEntryViewer() }
        })
        content.addView(infoBox)

        // Buttons row
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        btnRow.addView(Button(this).apply {
            text = "Add Files"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.TEXT))
            background = ContextCompat.getDrawable(this@AttachmentActivity, R.drawable.btn_secondary)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { pickFiles() }
        })
        btnRow.addView(Button(this).apply {
            text = "Save"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.CARD_BG))
            background = ContextCompat.getDrawable(this@AttachmentActivity, R.drawable.btn_accent)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(4) }
            setOnClickListener { saveAttachments() }
        })
        btnRow.addView(Button(this).apply {
            text = "Download Zip"
            textSize = 12f
            isAllCaps = false
            setTextColor(ThemeManager.color(C.TEXT))
            background = ContextCompat.getDrawable(this@AttachmentActivity, R.drawable.btn_secondary)
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
            setOnClickListener { downloadZip() }
        })
        content.addView(btnRow)

        // File grid
        fileGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        content.addView(fileGrid)

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)

        if (attachmentId != null) {
            loadExistingZipContents()
        }
    }

    private fun loadExistingZipContents() {
        val attachDir = bs.get("attachments_path") ?: return
        val dirUri = Uri.parse(attachDir)
        val dir = DocumentFile.fromTreeUri(this, dirUri) ?: return
        val zipName = "$attachmentId.zip"
        val zipFile = dir.findFile(zipName) ?: return

        try {
            contentResolver.openInputStream(zipFile.uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            pendingFiles.add(entry.name to Uri.EMPTY)
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (_: Exception) {}
        refreshFileGrid()
    }

    private fun refreshFileGrid() {
        fileGrid.removeAllViews()

        if (pendingFiles.isEmpty()) {
            fileGrid.addView(TextView(this).apply {
                text = "No files added yet"
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(4), dp(8), dp(4), dp(8))
            })
            return
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ThemeManager.color(C.CARD_BG))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        header.addView(TextView(this).apply {
            text = "#"
            setTextColor(ThemeManager.color(C.ACCENT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        header.addView(TextView(this).apply {
            text = "Filename"
            setTextColor(ThemeManager.color(C.ACCENT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "Action"
            setTextColor(ThemeManager.color(C.ACCENT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
        })
        fileGrid.addView(header)

        for ((idx, pair) in pendingFiles.withIndex()) {
            val rowBg = if (idx % 2 == 0) ThemeManager.color(C.CARD_BG) else ThemeManager.color(C.INPUT_BG)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(rowBg)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            row.addView(TextView(this).apply {
                text = "${idx + 1}"
                setTextColor(ThemeManager.color(C.TEXT_SECONDARY))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                layoutParams = LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = pair.first
                setTextColor(ThemeManager.color(C.TEXT))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val deleteBtn = Button(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(ThemeManager.color(C.ERROR))
                background = ContextCompat.getDrawable(this@AttachmentActivity, R.drawable.btn_secondary)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(30))
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    pendingFiles.removeAt(idx)
                    refreshFileGrid()
                }
            }
            row.addView(deleteBtn)
            fileGrid.addView(row)
        }
    }

    @Suppress("DEPRECATION")
    private fun pickFiles() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "Select files"), FILE_PICK)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            FILE_PICK -> {
                if (data == null) return
                val clipData = data.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val uri = clipData.getItemAt(i).uri
                        val name = getFileName(uri)
                        pendingFiles.add(name to uri)
                    }
                } else {
                    val uri = data.data ?: return
                    val name = getFileName(uri)
                    pendingFiles.add(name to uri)
                }
                refreshFileGrid()
            }
            DOWNLOAD_PICK -> {
                val destUri = data?.data ?: return
                copyZipToDestination(destUri)
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "file"
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = it.getString(idx) ?: "file"
            }
        }
        return name
    }

    private fun saveAttachments() {
        val attachDir = bs.get("attachments_path")
        if (attachDir.isNullOrEmpty()) {
            Toast.makeText(this, "Please set Attachments Path in Settings > Data", Toast.LENGTH_LONG).show()
            return
        }
        if (pendingFiles.isEmpty()) {
            Toast.makeText(this, "No files to save", Toast.LENGTH_SHORT).show()
            return
        }

        val hasNewFiles = pendingFiles.any { it.second != Uri.EMPTY }
        if (!hasNewFiles && attachmentId != null) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val dirUri = Uri.parse(attachDir)
            val dir = DocumentFile.fromTreeUri(this, dirUri)
                ?: run { Toast.makeText(this, "Cannot access attachments folder", Toast.LENGTH_SHORT).show(); return }

            val id = attachmentId ?: generateId()
            val zipName = "$id.zip"
            val isEdit = attachmentId != null

            // If editing, rename existing zip to _old
            var oldFile: DocumentFile? = null
            if (isEdit) {
                val existing = dir.findFile(zipName)
                if (existing != null) {
                    existing.renameTo("${id}_old.zip")
                    oldFile = dir.findFile("${id}_old.zip")
                }
            }

            // Create new zip
            val newZipFile = dir.createFile("application/zip", zipName)
                ?: run { Toast.makeText(this, "Failed to create zip file", Toast.LENGTH_SHORT).show(); return }

            val md = MessageDigest.getInstance("SHA-256")
            var totalSize = 0L

            contentResolver.openOutputStream(newZipFile.uri)?.use { os ->
                ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                    for ((name, uri) in pendingFiles) {
                        zos.putNextEntry(ZipEntry(name))
                        if (uri != Uri.EMPTY) {
                            contentResolver.openInputStream(uri)?.use { input ->
                                val buf = ByteArray(8192)
                                var len: Int
                                while (input.read(buf).also { len = it } > 0) {
                                    zos.write(buf, 0, len)
                                    md.update(buf, 0, len)
                                    totalSize += len
                                }
                            }
                        } else if (isEdit && oldFile != null) {
                            // Re-read from old zip
                            contentResolver.openInputStream(oldFile.uri)?.use { zipInput ->
                                ZipInputStream(zipInput).use { zis ->
                                    var entry = zis.nextEntry
                                    while (entry != null) {
                                        if (entry.name == name) {
                                            val buf = ByteArray(8192)
                                            var len: Int
                                            while (zis.read(buf).also { len = it } > 0) {
                                                zos.write(buf, 0, len)
                                                md.update(buf, 0, len)
                                                totalSize += len
                                            }
                                            break
                                        }
                                        entry = zis.nextEntry
                                    }
                                }
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }

            val hash = md.digest().joinToString("") { "%02x".format(it) }

            // Delete old zip
            oldFile?.delete()

            // Save to database
            if (attachmentId == null) {
                attachmentId = id
                db.saveAttachment(id, zipName, hash, totalSize, entryId)
            } else {
                db.updateAttachment(id, zipName, hash, totalSize)
            }
            existingFilename = zipName

            Toast.makeText(this, "Attachments saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun downloadZip() {
        if (attachmentId == null) {
            Toast.makeText(this, "No saved attachment to download", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "$attachmentId.zip")
        }
        startActivityForResult(intent, DOWNLOAD_PICK)
    }

    private fun copyZipToDestination(destUri: Uri) {
        val attachDir = bs.get("attachments_path") ?: return
        val dirUri = Uri.parse(attachDir)
        val dir = DocumentFile.fromTreeUri(this, dirUri) ?: return
        val zipFile = dir.findFile("$attachmentId.zip") ?: run {
            Toast.makeText(this, "Zip file not found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            contentResolver.openInputStream(zipFile.uri)?.use { input ->
                contentResolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, "Zip downloaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateId(): String {
        val ts = System.currentTimeMillis().toString(36)
        val rand = (Math.random() * 1e9).toLong().toString(36)
        return "$ts$rand"
    }

    private fun openEntryViewer() {
        EntryViewerActivity.databaseService = ServiceProvider.databaseService
        EntryViewerActivity.bootstrapService = ServiceProvider.bootstrapService
        EntryViewerActivity.pendingEntryId = entryId
        startActivity(Intent(this, EntryViewerActivity::class.java))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
