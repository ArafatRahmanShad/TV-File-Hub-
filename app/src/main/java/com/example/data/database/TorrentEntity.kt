package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "torrents")
data class TorrentEntity(
  @PrimaryKey val id: String, // infoHash or UUID
  val name: String,
  val magnetUri: String,
  val destinationType: String = "USB_HDD",
  val destinationUri: String? = null,
  val status: String = "DOWNLOADING", // QUEUED, DOWNLOADING, SEEDING, PAUSED, COMPLETED, ERROR
  val progress: Float = 0f,
  val downloadedBytes: Long = 0L,
  val totalBytes: Long = 0L,
  val downloadSpeed: Long = 0L,
  val uploadSpeed: Long = 0L,
  val uploadedBytes: Long = 0L,
  val peers: Int = 0,
  val seeds: Int = 0,
  val ratio: Float = 0f,
  val errorMessage: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis()
)
