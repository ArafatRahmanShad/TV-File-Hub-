package com.example.torrent

data class TorrentInfo(
  val id: String,
  val name: String,
  val magnetUri: String,
  val status: String, // DOWNLOADING, SEEDING, PAUSED, COMPLETED, ERROR
  val progress: Float,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val downloadSpeed: Long,
  val uploadSpeed: Long,
  val uploadedBytes: Long,
  val peers: Int,
  val seeds: Int,
  val ratio: Float,
  val errorMessage: String? = null
)
