package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.VaultFile
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM vault_files ORDER BY uploadedAt DESC")
    fun getAllFiles(): Flow<List<VaultFile>>

    @Query("SELECT * FROM vault_files WHERE id = :id")
    fun getFileById(id: Long): Flow<VaultFile?>

    @Query("SELECT * FROM vault_files WHERE fileName LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%' ORDER BY uploadedAt DESC")
    fun searchFiles(query: String): Flow<List<VaultFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: VaultFile): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<VaultFile>): List<Long>

    @Update
    suspend fun updateFile(file: VaultFile)

    @Delete
    suspend fun deleteFile(file: VaultFile)

    @Delete
    suspend fun deleteFiles(files: List<VaultFile>)

    @Query("UPDATE vault_files SET downloadCount = downloadCount + 1, lastDownloadedAt = :timestamp WHERE id = :id")
    suspend fun recordDownload(id: Long, timestamp: Long)

    @Query("UPDATE vault_files SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE vault_files SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)
}
