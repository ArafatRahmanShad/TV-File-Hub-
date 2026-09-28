package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
  @PrimaryKey val id: String,
  val url: String,
  val fileName: String,
  val destinationType: String = "INTERNAL", // "INTERNAL" or "USB_HDD"
  val destinationUri: String? = null,
  val status: String = "QUEUED", // QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
  val downloadedBytes: Long = 0L,
  val totalBytes: Long = -1L,
  val speedBytesPerSec: Long = 0L,
  val etaSeconds: Long = 0L,
  val errorMessage: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
)
