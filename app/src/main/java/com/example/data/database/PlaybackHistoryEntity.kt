package com.example.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playback_history")
data class PlaybackHistoryEntity(
  @PrimaryKey val mediaUri: String,
  val title: String,
  val positionMs: Long,
  val durationMs: Long,
  val lastWatchedAt: Long = System.currentTimeMillis()
)

@Dao
interface PlaybackHistoryDao {
  @Query("SELECT * FROM playback_history ORDER BY lastWatchedAt DESC LIMIT 20")
  fun getRecentHistory(): Flow<List<PlaybackHistoryEntity>>

  @Query("SELECT * FROM playback_history WHERE mediaUri = :uri LIMIT 1")
  suspend fun getHistoryForUri(uri: String): PlaybackHistoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveHistory(history: PlaybackHistoryEntity)

  @Query("DELETE FROM playback_history WHERE mediaUri = :uri")
  suspend fun deleteHistory(uri: String)
}
