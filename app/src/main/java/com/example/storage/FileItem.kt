package com.example.storage

import android.net.Uri

enum class FileCategory {
  FOLDER, VIDEO, AUDIO, IMAGE, DOCUMENT, ARCHIVE, TORRENT, UNKNOWN
}

data class FileItem(
  val name: String,
  val path: String, // Absolute path or DocumentFile uri string
  val isDirectory: Boolean,
  val size: Long,
  val lastModified: Long,
  val mimeType: String?,
  val category: FileCategory,
  val isUsb: Boolean = false,
  val uri: Uri? = null
)
