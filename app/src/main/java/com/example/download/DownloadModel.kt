package com.example.download

data class DownloadProgress(
  val downloadId: String,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val speedBytesPerSec: Long,
  val etaSeconds: Long,
  val progressPercent: Float,
  val status: String,
  val errorMessage: String? = null
)
