package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
  @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
  fun getAllDownloads(): Flow<List<DownloadEntity>>

  @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
  suspend fun getDownloadById(id: String): DownloadEntity?

  @Query("SELECT * FROM downloads WHERE status = 'DOWNLOADING' OR status = 'QUEUED'")
  suspend fun getActiveDownloads(): List<DownloadEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(download: DownloadEntity)

  @Update
  suspend fun update(download: DownloadEntity)

  @Query("UPDATE downloads SET status = :status, speedBytesPerSec = 0, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

  @Query("""
    UPDATE downloads 
    SET downloadedBytes = :downloaded, totalBytes = :total, speedBytesPerSec = :speed, etaSeconds = :eta, updatedAt = :updatedAt 
    WHERE id = :id
  """)
  suspend fun updateProgress(id: String, downloaded: Long, total: Long, speed: Long, eta: Long, updatedAt: Long = System.currentTimeMillis())

  @Query("DELETE FROM downloads WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
  suspend fun deleteCompleted()
}
