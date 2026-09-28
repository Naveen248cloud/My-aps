package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FileCategory(val label: String) {
    ALL("All"),
    DOCUMENTS("Docs"),
    IMAGES("Images"),
    MEDIA("Audio & Video"),
    ARCHIVES("Archives"),
    OTHER("Other")
}

@Entity(tableName = "vault_files")
data class VaultFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val sizeBytes: Long,
    val uploadedAt: Long = System.currentTimeMillis(),
    val internalPath: String,
    val category: String, // String representation of FileCategory
    val isFavorite: Boolean = false,
    val downloadCount: Int = 0,
    val lastDownloadedAt: Long? = null,
    val checksum: String = "",
    val note: String = ""
) {
    fun getFormattedSize(): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "%.1f KB".format(sizeBytes / 1024.0)
            sizeBytes < 1024 * 1024 * 1024 -> "%.2f MB".format(sizeBytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
