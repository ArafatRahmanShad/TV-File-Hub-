package com.example.storage

import android.content.Context
import android.os.Environment
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.util.Locale

object StorageManager {

  fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val df = DecimalFormat("#,##0.#")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
  }

  fun formatSpeed(bytesPerSec: Long): String {
    return "${formatSize(bytesPerSec)}/s"
  }

  fun determineCategory(fileName: String, mime: String? = null): FileCategory {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
      ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "m4v", "ts") || (mime?.startsWith("video/") == true) ->
        FileCategory.VIDEO
      ext in listOf("mp3", "flac", "aac", "wav", "m4a", "ogg", "opus", "wma") || (mime?.startsWith("audio/") == true) ->
        FileCategory.AUDIO
      ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg") || (mime?.startsWith("image/") == true) ->
        FileCategory.IMAGE
      ext in listOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub", "srt", "vtt") ->
        FileCategory.DOCUMENT
      ext in listOf("zip", "rar", "7z", "tar", "gz") ->
        FileCategory.ARCHIVE
      ext == "torrent" ->
        FileCategory.TORRENT
      else -> FileCategory.UNKNOWN
    }
  }

  fun getInternalRoot(context: Context): File {
    val external = Environment.getExternalStorageDirectory()
    return if (external != null && external.exists() && external.canRead()) {
      external
    } else {
      context.getExternalFilesDir(null) ?: context.filesDir
    }
  }

  fun getDefaultDownloadDirectory(context: Context): File {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    if (downloadDir != null && (downloadDir.exists() || downloadDir.mkdirs())) {
      return downloadDir
    }
    val appDownloads = File(context.getExternalFilesDir(null) ?: context.filesDir, "Downloads")
    if (!appDownloads.exists()) appDownloads.mkdirs()
    return appDownloads
  }

  fun getDefaultTorrentsDirectory(context: Context): File {
    val dir = File(getDefaultDownloadDirectory(context), "Torrents")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  suspend fun listInternalDirectory(directory: File): List<FileItem> = withContext(Dispatchers.IO) {
    if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()
    val files = directory.listFiles() ?: return@withContext emptyList()

    files.map { file ->
      val isDir = file.isDirectory
      val ext = file.extension.lowercase(Locale.ROOT)
      val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
      val category = if (isDir) FileCategory.FOLDER else determineCategory(file.name, mime)

      FileItem(
        name = file.name,
        path = file.absolutePath,
        isDirectory = isDir,
        size = if (isDir) 0L else file.length(),
        lastModified = file.lastModified(),
        mimeType = mime,
        category = category,
        isUsb = false,
        uri = android.net.Uri.fromFile(file)
      )
    }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
  }

  suspend fun createInternalFolder(parent: File, name: String): Boolean = withContext(Dispatchers.IO) {
    val newDir = File(parent, name)
    if (newDir.exists()) false else newDir.mkdirs()
  }

  suspend fun renameInternal(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
    if (!file.exists()) return@withContext false
    val target = File(file.parentFile, newName)
    if (target.exists()) return@withContext false
    file.renameTo(target)
  }

  suspend fun deleteInternal(file: File): Boolean = withContext(Dispatchers.IO) {
    if (!file.exists()) return@withContext false
    if (file.isDirectory) {
      file.deleteRecursively()
    } else {
      file.delete()
    }
  }

  suspend fun copyOrMove(
    source: File,
    destDir: File,
    isMove: Boolean,
    onProgress: ((copiedBytes: Long, totalBytes: Long) -> Unit)? = null
  ): Boolean = withContext(Dispatchers.IO) {
    try {
      if (!source.exists()) return@withContext false
      if (!destDir.exists()) destDir.mkdirs()
      val target = File(destDir, source.name)

      if (source.isDirectory) {
        if (!target.exists()) target.mkdirs()
        source.listFiles()?.forEach { child ->
          copyOrMove(child, target, isMove, null)
        }
        if (isMove) source.deleteRecursively()
        return@withContext true
      }

      val total = source.length()
      var copied = 0L
      val buffer = ByteArray(64 * 1024)

      FileInputStream(source).use { input ->
        FileOutputStream(target).use { output ->
          var bytesRead: Int
          while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            copied += bytesRead
            onProgress?.invoke(copied, total)
          }
          output.flush()
        }
      }

      if (isMove) {
        source.delete()
      }
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
}
