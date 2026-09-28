package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.VaultFile
import com.example.ui.theme.ColorAudio
import com.example.ui.theme.ColorCode
import com.example.ui.theme.ColorDoc
import com.example.ui.theme.ColorGeneric
import com.example.ui.theme.ColorImage
import com.example.ui.theme.ColorPdf
import com.example.ui.theme.ColorSheet
import com.example.ui.theme.ColorVideo
import com.example.ui.theme.ColorZip

object FileCategoryHelper {

    data class FileVisual(
        val icon: ImageVector,
        val color: Color
    )

    fun getVisualForFile(file: VaultFile): FileVisual {
        val ext = file.extension.lowercase()
        return when {
            ext == "pdf" -> FileVisual(Icons.Default.PictureAsPdf, ColorPdf)
            ext in listOf("png", "jpg", "jpeg", "webp", "gif", "svg", "bmp") -> FileVisual(Icons.Default.Image, ColorImage)
            ext in listOf("mp4", "mkv", "mov", "avi", "webm") -> FileVisual(Icons.Default.VideoFile, ColorVideo)
            ext in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a") -> FileVisual(Icons.Default.AudioFile, ColorAudio)
            ext in listOf("zip", "rar", "7z", "tar", "gz") -> FileVisual(Icons.Default.FolderZip, ColorZip)
            ext in listOf("xls", "xlsx", "csv") -> FileVisual(Icons.Default.TableChart, ColorSheet)
            ext in listOf("kt", "java", "json", "xml", "js", "html", "css", "py", "c", "cpp") -> FileVisual(Icons.Default.Code, ColorCode)
            ext in listOf("doc", "docx", "txt", "md", "rtf", "odt") -> FileVisual(Icons.Default.Description, ColorDoc)
            else -> FileVisual(Icons.Default.InsertDriveFile, ColorGeneric)
        }
    }
}
