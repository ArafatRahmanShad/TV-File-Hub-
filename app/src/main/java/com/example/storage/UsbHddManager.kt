package com.example.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

object UsbHddManager {

  sealed class UsbStatus {
    object NotConfigured : UsbStatus()
    data class Connected(val name: String, val rootUri: Uri) : UsbStatus()
    data class Disconnected(val reason: String) : UsbStatus()
  }

  suspend fun takePersistablePermission(context: Context, treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
      val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      context.contentResolver.takePersistableUriPermission(treeUri, flags)
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  fun getRootDocument(context: Context, treeUriString: String?): DocumentFile? {
    if (treeUriString.isNullOrBlank()) return null
    return try {
      val uri = Uri.parse(treeUriString)
      val doc = DocumentFile.fromTreeUri(context, uri)
      if (doc != null && doc.exists() && doc.canRead()) doc else null
    } catch (e: Exception) {
      null
    }
  }

  suspend fun checkStatus(context: Context, treeUriString: String?): UsbStatus = withContext(Dispatchers.IO) {
    if (treeUriString.isNullOrBlank()) {
      return@withContext UsbStatus.NotConfigured
    }
    try {
      val uri = Uri.parse(treeUriString)
      val doc = DocumentFile.fromTreeUri(context, uri)
      if (doc != null && doc.exists() && doc.canRead()) {
        val displayName = doc.name ?: "USB HDD"
        UsbStatus.Connected(displayName, uri)
      } else {
        UsbStatus.Disconnected("USB Storage unavailable. Please reconnect your USB drive.")
      }
    } catch (e: Exception) {
      UsbStatus.Disconnected("USB Storage unavailable: ${e.localizedMessage ?: "Permission or device error"}")
    }
  }

  suspend fun listUsbDirectory(
    context: Context,
    directory: DocumentFile
  ): List<FileItem> = withContext(Dispatchers.IO) {
    try {
      if (!directory.exists() || !directory.isDirectory) return@withContext emptyList()
      val files = directory.listFiles()

      files.map { file ->
        val isDir = file.isDirectory
        val name = file.name ?: "Unnamed"
        val mime = file.type
        val category = if (isDir) FileCategory.FOLDER else StorageManager.determineCategory(name, mime)

        FileItem(
          name = name,
          path = file.uri.toString(),
          isDirectory = isDir,
          size = if (isDir) 0L else file.length(),
          lastModified = file.lastModified(),
          mimeType = mime,
          category = category,
          isUsb = true,
          uri = file.uri
        )
      }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    } catch (e: Exception) {
      e.printStackTrace()
      emptyList()
    }
  }

  suspend fun createUsbFolder(parent: DocumentFile, folderName: String): DocumentFile? = withContext(Dispatchers.IO) {
    try {
      parent.createDirectory(folderName)
    } catch (e: Exception) {
      e.printStackTrace()
      null
    }
  }

  suspend fun renameUsbFile(file: DocumentFile, newName: String): Boolean = withContext(Dispatchers.IO) {
    try {
      file.renameTo(newName)
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  suspend fun deleteUsbFile(file: DocumentFile): Boolean = withContext(Dispatchers.IO) {
    try {
      file.delete()
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }

  suspend fun copyStreamToUsb(
    context: Context,
    inputStream: InputStream,
    targetDirectory: DocumentFile,
    fileName: String,
    mimeType: String = "application/octet-stream",
    totalBytes: Long = -1L,
    onProgress: ((copied: Long, total: Long) -> Unit)? = null
  ): Boolean = withContext(Dispatchers.IO) {
    try {
      val targetFile = targetDirectory.createFile(mimeType, fileName) ?: return@withContext false
      val outputStream: OutputStream? = context.contentResolver.openOutputStream(targetFile.uri)
      if (outputStream == null) {
        targetFile.delete()
        return@withContext false
      }

      val buffer = ByteArray(64 * 1024)
      var copied = 0L
      var read: Int

      outputStream.use { out ->
        inputStream.use { input ->
          while (input.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
            copied += read
            onProgress?.invoke(copied, totalBytes)
          }
          out.flush()
        }
      }
      true
    } catch (e: Exception) {
      e.printStackTrace()
      false
    }
  }
}
