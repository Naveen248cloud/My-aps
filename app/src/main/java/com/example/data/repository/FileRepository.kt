package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.local.FileDao
import com.example.data.model.FileCategory
import com.example.data.model.VaultFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

class FileRepository(
    private val context: Context,
    private val fileDao: FileDao
) {
    val allFiles: Flow<List<VaultFile>> = fileDao.getAllFiles()

    private val vaultDir: File
        get() {
            val dir = File(context.filesDir, "vault")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    suspend fun uploadFromUri(uri: Uri): VaultFile? = withContext(Dispatchers.IO) {
        try {
            var displayName = "file_${System.currentTimeMillis()}"
            var reportedSize = 0L

            // Extract display name and size from content resolver
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        reportedSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val extension = getExtensionFromName(displayName)
            val category = categorize(extension, mimeType)

            // Safe unique file name on disk
            val safeBaseName = displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val targetFile = File(vaultDir, "${System.currentTimeMillis()}_$safeBaseName")

            val digest = MessageDigest.getInstance("SHA-256")
            var actualBytes = 0L

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        actualBytes += read
                    }
                    output.flush()
                }
            } ?: return@withContext null

            val checksum = digest.digest().joinToString("") { "%02x".format(it) }

            val vaultFile = VaultFile(
                fileName = targetFile.name,
                displayName = displayName,
                extension = extension.uppercase(Locale.ROOT),
                mimeType = mimeType,
                sizeBytes = if (actualBytes > 0) actualBytes else reportedSize,
                uploadedAt = System.currentTimeMillis(),
                internalPath = targetFile.absolutePath,
                category = category.name,
                checksum = checksum
            )

            val id = fileDao.insertFile(vaultFile)
            vaultFile.copy(id = id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun createTextFile(title: String, content: String, extension: String = "txt"): VaultFile? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = if (title.isBlank()) "Quick_Note_${System.currentTimeMillis()}" else title.trim()
            val fullDisplayName = if (cleanTitle.endsWith(".$extension", ignoreCase = true)) cleanTitle else "$cleanTitle.$extension"
            val safeName = "${System.currentTimeMillis()}_${fullDisplayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")}"
            val targetFile = File(vaultDir, safeName)

            val bytes = content.toByteArray(Charsets.UTF_8)
            FileOutputStream(targetFile).use { it.write(bytes) }

            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }

            val mime = when (extension.lowercase(Locale.ROOT)) {
                "md" -> "text/markdown"
                "json" -> "application/json"
                "csv" -> "text/csv"
                else -> "text/plain"
            }

            val vaultFile = VaultFile(
                fileName = safeName,
                displayName = fullDisplayName,
                extension = extension.uppercase(Locale.ROOT),
                mimeType = mime,
                sizeBytes = bytes.size.toLong(),
                uploadedAt = System.currentTimeMillis(),
                internalPath = targetFile.absolutePath,
                category = FileCategory.DOCUMENTS.name,
                checksum = hash,
                note = "Created in app"
            )

            val id = fileDao.insertFile(vaultFile)
            vaultFile.copy(id = id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportToTargetUri(vaultFile: VaultFile, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(vaultFile.internalPath)
            if (!sourceFile.exists()) return@withContext false

            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            } ?: return@withContext false

            fileDao.recordDownload(vaultFile.id, System.currentTimeMillis())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportToPublicDownloads(vaultFile: VaultFile): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(vaultFile.internalPath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source file does not exist on device storage."))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, vaultFile.displayName)
                    put(MediaStore.Downloads.MIME_TYPE, vaultFile.mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val downloadUri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext Result.failure(Exception("Failed to create download entry in MediaStore."))

                context.contentResolver.openOutputStream(downloadUri)?.use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output)
                    }
                } ?: return@withContext Result.failure(Exception("Cannot open destination stream for download."))

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(downloadUri, contentValues, null, null)

                fileDao.recordDownload(vaultFile.id, System.currentTimeMillis())
                Result.success("Saved to Downloads folder: ${vaultFile.displayName}")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                var targetFile = File(downloadsDir, vaultFile.displayName)
                if (targetFile.exists()) {
                    targetFile = File(downloadsDir, "${System.currentTimeMillis()}_${vaultFile.displayName}")
                }

                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                fileDao.recordDownload(vaultFile.id, System.currentTimeMillis())
                Result.success("Saved to ${targetFile.absolutePath}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun deleteFile(vaultFile: VaultFile) = withContext(Dispatchers.IO) {
        try {
            val physicalFile = File(vaultFile.internalPath)
            if (physicalFile.exists()) {
                physicalFile.delete()
            }
            fileDao.deleteFile(vaultFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteMultipleFiles(files: List<VaultFile>) = withContext(Dispatchers.IO) {
        try {
            files.forEach { file ->
                val physicalFile = File(file.internalPath)
                if (physicalFile.exists()) {
                    physicalFile.delete()
                }
            }
            fileDao.deleteFiles(files)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun toggleFavorite(vaultFile: VaultFile) = withContext(Dispatchers.IO) {
        fileDao.updateFavorite(vaultFile.id, !vaultFile.isFavorite)
    }

    suspend fun updateNote(fileId: Long, note: String) = withContext(Dispatchers.IO) {
        fileDao.updateNote(fileId, note)
    }

    fun getFileUri(vaultFile: VaultFile): Uri? {
        val file = File(vaultFile.internalPath)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun createViewIntent(vaultFile: VaultFile): Intent? {
        val uri = getFileUri(vaultFile) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, vaultFile.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createShareIntent(vaultFile: VaultFile): Intent? {
        val uri = getFileUri(vaultFile) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = vaultFile.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, vaultFile.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun readTextContent(vaultFile: VaultFile, maxChars: Int = 15000): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(vaultFile.internalPath)
            if (!file.exists() || file.length() > 1_000_000) return@withContext null // skip if > 1MB
            val text = file.readText(Charsets.UTF_8)
            if (text.length > maxChars) {
                text.substring(0, maxChars) + "\n\n... [Truncated for preview]"
            } else {
                text
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun seedInitialSampleFilesIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val existing = vaultDir.listFiles()
            if (existing != null && existing.isNotEmpty()) return@withContext

            createTextFile(
                title = "Welcome_To_FileVault.txt",
                content = """
                    === FileVault: Upload & Download Manager ===
                    
                    Namaste & Welcome!
                    This app allows you to securely upload any file from your device,
                    store it in your personal vault, preview it, and download/export it whenever needed.
                    
                    Features:
                    1. Upload any file (Images, Documents, Audio, Video, Zip, Code)
                    2. Download / Save directly to your phone's Downloads folder
                    3. Quick Export to custom directory with native picker
                    4. In-app preview for Images & Text files
                    5. Share files to WhatsApp, Email, or Drive
                    6. Search, sort, and tag your files
                    7. SHA-256 checksum verification
                    
                    Tap the Download icon (↓) on this card to test downloading right now!
                """.trimIndent(),
                extension = "txt"
            )

            createTextFile(
                title = "Invoice_Receipt_Template.md",
                content = """
                    # INVOICE RECEIPT
                    **Invoice Number:** INV-2026-0928
                    **Date:** September 28, 2026
                    **Status:** Paid
                    
                    ---
                    ### Bill To:
                    Customer Name: Digital Vault User
                    Email: user@example.com
                    
                    ### Items:
                    | Description | Qty | Rate | Amount |
                    |-------------|-----|------|--------|
                    | Cloud Storage Subscription | 1 | $15.00 | $15.00 |
                    | High Speed Transfer Addon | 1 | $5.00  | $5.00  |
                    
                    **Total Amount:** $20.00
                    **Payment Method:** UPI / Card
                    
                    *Thank you for your business!*
                """.trimIndent(),
                extension = "md"
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getExtensionFromName(name: String): String {
        val lastDot = name.lastIndexOf('.')
        return if (lastDot != -1 && lastDot < name.length - 1) {
            name.substring(lastDot + 1).lowercase(Locale.ROOT)
        } else {
            ""
        }
    }

    private fun categorize(extension: String, mimeType: String): FileCategory {
        val ext = extension.lowercase(Locale.ROOT)
        val mime = mimeType.lowercase(Locale.ROOT)

        return when {
            mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic") -> FileCategory.IMAGES
            mime.startsWith("video/") || mime.startsWith("audio/") || ext in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "mp4", "mkv", "mov", "avi", "webm", "3gp") -> FileCategory.MEDIA
            mime.contains("zip") || mime.contains("tar") || mime.contains("rar") || ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> FileCategory.ARCHIVES
            mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") || mime.contains("word") || mime.contains("sheet") || ext in listOf("pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "md", "json", "xml", "html", "htm") -> FileCategory.DOCUMENTS
            else -> FileCategory.OTHER
        }
    }
}
