package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    DownloadEntity::class,
    TorrentEntity::class,
    PlaybackHistoryEntity::class
  ],
  version = 1,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun downloadDao(): DownloadDao
  abstract fun torrentDao(): TorrentDao
  abstract fun playbackHistoryDao(): PlaybackHistoryDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "tv_file_hub.db"
        )
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = instance
        instance
      }
    }

    fun getInstance(context: Context): AppDatabase = getDatabase(context)
  }
}
