package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TorrentDao {
  @Query("SELECT * FROM torrents ORDER BY createdAt DESC")
  fun getAllTorrents(): Flow<List<TorrentEntity>>

  @Query("SELECT * FROM torrents WHERE id = :id LIMIT 1")
  suspend fun getTorrentById(id: String): TorrentEntity?

  @Query("SELECT * FROM torrents WHERE status = 'DOWNLOADING' OR status = 'SEEDING'")
  suspend fun getActiveTorrents(): List<TorrentEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(torrent: TorrentEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(torrent: TorrentEntity)

  @Update
  suspend fun update(torrent: TorrentEntity)

  @Query("UPDATE torrents SET status = :status, downloadSpeed = 0, uploadSpeed = 0, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

  @Query("UPDATE torrents SET name = :name, totalBytes = :totalBytes, updatedAt = :updatedAt WHERE id = :id")
  suspend fun updateMetadata(id: String, name: String, totalBytes: Long, updatedAt: Long = System.currentTimeMillis())

  @Query("""
    UPDATE torrents 
    SET progress = :progress, downloadedBytes = :downloaded, totalBytes = :total, 
        downloadSpeed = :dlSpeed, uploadSpeed = :ulSpeed, uploadedBytes = :uploaded,
        peers = :peers, seeds = :seeds, ratio = :ratio, status = :status, updatedAt = :updatedAt 
    WHERE id = :id
  """)
  suspend fun updateProgress(
    id: String,
    progress: Float,
    downloaded: Long,
    total: Long,
    dlSpeed: Long,
    ulSpeed: Long,
    uploaded: Long,
    peers: Int,
    seeds: Int,
    ratio: Float,
    status: String,
    updatedAt: Long = System.currentTimeMillis()
  )

  @Query("DELETE FROM torrents WHERE id = :id")
  suspend fun deleteById(id: String)
}
