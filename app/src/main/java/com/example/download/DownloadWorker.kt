package com.example.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.database.AppDatabase
import com.example.storage.StorageManager
import com.example.storage.UsbHddManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class DownloadWorker(
  private val context: Context,
  params: WorkerParameters
) : CoroutineWorker(context, params) {

  companion object {
    const val KEY_DOWNLOAD_ID = "download_id"
    const val KEY_URL = "download_url"
    const val KEY_FILE_NAME = "download_file_name"
    const val KEY_DEST_TYPE = "download_dest_type"
    const val KEY_DEST_URI = "download_dest_uri"
  }

  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
    val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
    val rawFileName = inputData.getString(KEY_FILE_NAME) ?: "download"
    val destType = inputData.getString(KEY_DEST_TYPE) ?: "INTERNAL"
    val destUriString = inputData.getString(KEY_DEST_URI)

    val db = AppDatabase.getDatabase(context)
    val dao = db.downloadDao()

    val currentEntity = dao.getDownloadById(downloadId)
    if (currentEntity?.status == "PAUSED" || currentEntity?.status == "CANCELLED") {
      return@withContext Result.success()
    }

    dao.updateStatus(downloadId, "DOWNLOADING")

    var isPaused = false

    try {
      if (destType == "USB_HDD" && !destUriString.isNullOrBlank()) {
        downloadToUsbHdd(
          downloadId = downloadId,
          url = url,
          fileName = rawFileName,
          destUriString = destUriString,
          dao = dao
        )
      } else {
        downloadToInternalStorage(
          downloadId = downloadId,
          url = url,
          fileName = rawFileName,
          dao = dao
        )
      }
      Result.success()
    } catch (e: Exception) {
      if (isStopped) {
        val latest = dao.getDownloadById(downloadId)
        if (latest?.status != "CANCELLED") {
          dao.updateStatus(downloadId, "PAUSED")
        }
        Result.success()
      } else {
        e.printStackTrace()
        val errorMsg = when (e) {
          is UnknownHostException -> "No internet connection or server host not found"
          is SocketTimeoutException -> "Connection timed out"
          else -> e.localizedMessage ?: "Download failed"
        }
        val entity = dao.getDownloadById(downloadId)
        if (entity != null) {
          dao.insertOrUpdate(entity.copy(status = "FAILED", errorMessage = errorMsg, speedBytesPerSec = 0))
        }
        Result.failure()
      }
    }
  }

  private suspend fun downloadToInternalStorage(
    downloadId: String,
    url: String,
    fileName: String,
    dao: com.example.data.database.DownloadDao
  ) {
    val downloadDir = StorageManager.getDefaultDownloadDirectory(context)
    val sanitizedName = sanitizeFilename(fileName)
    val partFile = File(downloadDir, "$sanitizedName.part")
    val finalFile = File(downloadDir, sanitizedName)

    var existingBytes = 0L
    if (partFile.exists()) {
      existingBytes = partFile.length()
    }

    val requestBuilder = Request.Builder().url(url)
    if (existingBytes > 0) {
      requestBuilder.header("Range", "bytes=$existingBytes-")
    }

    val response = okHttpClient.newCall(requestBuilder.build()).execute()
    if (!response.isSuccessful && response.code != 206) {
      throw Exception("HTTP ${response.code}: ${response.message}")
    }

    val isResume = (response.code == 206)
    val body = response.body ?: throw Exception("Empty response body")
    val contentLength = body.contentLength()

    val totalBytes = if (contentLength > 0) {
      if (isResume) existingBytes + contentLength else contentLength
    } else {
      -1L
    }

    val fileMode = if (isResume && existingBytes > 0) "rw" else "rw"
    val randomAccessFile = RandomAccessFile(partFile, fileMode)
    if (isResume && existingBytes > 0) {
      randomAccessFile.seek(existingBytes)
    } else {
      randomAccessFile.setLength(0)
      existingBytes = 0L
    }

    body.byteStream().use { input ->
      streamWithProgress(
        input = input,
        output = { buf, len -> randomAccessFile.write(buf, 0, len) },
        downloadId = downloadId,
        alreadyDownloaded = existingBytes,
        totalBytes = totalBytes,
        dao = dao
      )
    }
    randomAccessFile.close()

    if (!isStopped) {
      if (finalFile.exists()) finalFile.delete()
      val renamed = partFile.renameTo(finalFile)
      if (!renamed) {
        partFile.copyTo(finalFile, overwrite = true)
        partFile.delete()
      }
      val current = dao.getDownloadById(downloadId)
      if (current != null) {
        dao.insertOrUpdate(
          current.copy(
            status = "COMPLETED",
            downloadedBytes = finalFile.length(),
            totalBytes = finalFile.length(),
            speedBytesPerSec = 0,
            etaSeconds = 0,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
          )
        )
      }
    }
  }

  private suspend fun downloadToUsbHdd(
    downloadId: String,
    url: String,
    fileName: String,
    destUriString: String,
    dao: com.example.data.database.DownloadDao
  ) {
    val treeUri = Uri.parse(destUriString)
    val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
      ?: throw Exception("USB HDD root folder not accessible. Please reconnect your drive.")

    var downloadsDir = rootDoc.findFile("Downloads")
    if (downloadsDir == null || !downloadsDir.isDirectory) {
      downloadsDir = rootDoc.createDirectory("Downloads") ?: rootDoc
    }

    val sanitizedName = sanitizeFilename(fileName)
    val partName = "$sanitizedName.part"

    // Temporary cache or direct stream to USB
    val existingPartDoc = downloadsDir.findFile(partName)
    var existingBytes = existingPartDoc?.length() ?: 0L

    val requestBuilder = Request.Builder().url(url)
    if (existingBytes > 0) {
      requestBuilder.header("Range", "bytes=$existingBytes-")
    }

    val response = okHttpClient.newCall(requestBuilder.build()).execute()
    if (!response.isSuccessful && response.code != 206) {
      throw Exception("HTTP ${response.code}: ${response.message}")
    }

    val isResume = (response.code == 206)
    val body = response.body ?: throw Exception("Empty response body")
    val contentLength = body.contentLength()
    val totalBytes = if (contentLength > 0) {
      if (isResume) existingBytes + contentLength else contentLength
    } else {
      -1L
    }

    val targetDoc = if (isResume && existingPartDoc != null) {
      existingPartDoc
    } else {
      existingPartDoc?.delete()
      existingBytes = 0L
      downloadsDir.createFile("application/octet-stream", partName)
        ?: throw Exception("Cannot create file on USB HDD")
    }

    val outputStream = context.contentResolver.openOutputStream(targetDoc.uri, if (isResume) "wa" else "w")
      ?: throw Exception("Unable to open write stream to USB HDD")

    outputStream.use { out ->
      body.byteStream().use { input ->
        streamWithProgress(
          input = input,
          output = { buf, len -> out.write(buf, 0, len) },
          downloadId = downloadId,
          alreadyDownloaded = existingBytes,
          totalBytes = totalBytes,
          dao = dao
        )
      }
      out.flush()
    }

    if (!isStopped) {
      val existingFinal = downloadsDir.findFile(sanitizedName)
      existingFinal?.delete()
      targetDoc.renameTo(sanitizedName)

      val current = dao.getDownloadById(downloadId)
      if (current != null) {
        val completedLen = targetDoc.length()
        dao.insertOrUpdate(
          current.copy(
            status = "COMPLETED",
            downloadedBytes = completedLen,
            totalBytes = completedLen,
            speedBytesPerSec = 0,
            etaSeconds = 0,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
          )
        )
      }
    }
  }

  private suspend fun streamWithProgress(
    input: InputStream,
    output: (ByteArray, Int) -> Unit,
    downloadId: String,
    alreadyDownloaded: Long,
    totalBytes: Long,
    dao: com.example.data.database.DownloadDao
  ) {
    val buffer = ByteArray(64 * 1024)
    var bytesRead: Int
    var totalDownloaded = alreadyDownloaded

    var lastReportTime = System.currentTimeMillis()
    var bytesSinceLastReport = 0L

    while (input.read(buffer).also { bytesRead = it } != -1) {
      if (isStopped) {
        break
      }
      output(buffer, bytesRead)
      totalDownloaded += bytesRead
      bytesSinceLastReport += bytesRead

      val now = System.currentTimeMillis()
      val elapsed = now - lastReportTime
      if (elapsed >= 1000) {
        val speedBytesPerSec = (bytesSinceLastReport * 1000) / elapsed
        val remainingBytes = if (totalBytes > 0) (totalBytes - totalDownloaded).coerceAtLeast(0) else 0L
        val etaSeconds = if (speedBytesPerSec > 0 && totalBytes > 0) remainingBytes / speedBytesPerSec else 0L

        dao.updateProgress(
          id = downloadId,
          downloaded = totalDownloaded,
          total = totalBytes,
          speed = speedBytesPerSec,
          eta = etaSeconds
        )

        val progressPercent = if (totalBytes > 0) (totalDownloaded.toFloat() / totalBytes.toFloat()) * 100f else 0f
        setProgress(
          workDataOf(
            "progress" to progressPercent,
            "downloaded" to totalDownloaded,
            "total" to totalBytes,
            "speed" to speedBytesPerSec,
            "eta" to etaSeconds
          )
        )

        lastReportTime = now
        bytesSinceLastReport = 0L
      }
    }
  }

  private fun sanitizeFilename(name: String): String {
    return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty {
      "download_${System.currentTimeMillis()}"
    }
  }
}
