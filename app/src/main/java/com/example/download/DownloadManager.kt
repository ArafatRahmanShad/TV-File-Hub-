package com.example.download

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.database.AppDatabase
import com.example.data.database.DownloadEntity
import com.example.data.preferences.AppPreferences
import com.example.storage.StorageManager
import com.example.storage.UsbHddManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.util.UUID

class DownloadManager(private val context: Context) {

  private val appContext = context.applicationContext
  private val workManager = WorkManager.getInstance(appContext)
  private val db = AppDatabase.getDatabase(appContext)
  private val dao = db.downloadDao()
  private val preferences = AppPreferences(appContext)
  private val scope = CoroutineScope(Dispatchers.IO)

  val allDownloads: Flow<List<DownloadEntity>> = dao.getAllDownloads()

  suspend fun enqueueDownload(
    url: String,
    fileNameOverride: String? = null,
    destinationTypeOverride: String? = null
  ): String = withContext(Dispatchers.IO) {
    val cleanUrl = url.trim()
    val finalFileName = if (!fileNameOverride.isNullOrBlank()) {
      fileNameOverride.trim()
    } else {
      extractFileNameFromUrl(cleanUrl)
    }

    val preferredDest = destinationTypeOverride ?: preferences.getDefaultDestinationSync()
    val usbTreeUri = preferences.getUsbTreeUriSync()

    val actualDestType = if (preferredDest == "USB_HDD" && !usbTreeUri.isNullOrBlank()) {
      "USB_HDD"
    } else {
      "INTERNAL"
    }

    val downloadId = UUID.randomUUID().toString()
    val entity = DownloadEntity(
      id = downloadId,
      url = cleanUrl,
      fileName = finalFileName,
      destinationType = actualDestType,
      destinationUri = if (actualDestType == "USB_HDD") usbTreeUri else null,
      status = "QUEUED",
      downloadedBytes = 0L,
      totalBytes = -1L,
      speedBytesPerSec = 0L,
      etaSeconds = 0L,
      errorMessage = null,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis()
    )

    dao.insertOrUpdate(entity)
    startWork(entity)
    downloadId
  }

  fun pauseDownload(downloadId: String) {
    scope.launch {
      workManager.cancelUniqueWork("download_$downloadId")
      dao.updateStatus(downloadId, "PAUSED")
    }
  }

  fun resumeDownload(downloadId: String) {
    scope.launch {
      val entity = dao.getDownloadById(downloadId) ?: return@launch
      dao.updateStatus(downloadId, "QUEUED")
      startWork(entity)
    }
  }

  fun retryDownload(downloadId: String) {
    scope.launch {
      val entity = dao.getDownloadById(downloadId) ?: return@launch
      dao.insertOrUpdate(
        entity.copy(
          status = "QUEUED",
          errorMessage = null,
          speedBytesPerSec = 0,
          updatedAt = System.currentTimeMillis()
        )
      )
      startWork(entity)
    }
  }

  fun cancelDownload(downloadId: String) {
    scope.launch {
      workManager.cancelUniqueWork("download_$downloadId")
      dao.updateStatus(downloadId, "CANCELLED")
    }
  }

  fun deleteDownload(downloadId: String, removeFile: Boolean = true) {
    scope.launch {
      // Specifically cancel only this download's unique work
      workManager.cancelUniqueWork("download_$downloadId")
      val entity = dao.getDownloadById(downloadId)

      if (removeFile && entity != null) {
        try {
          if (entity.destinationType == "INTERNAL") {
            val downloadDir = StorageManager.getDefaultDownloadDirectory(appContext)
            val partFile = File(downloadDir, "${entity.fileName}.part")
            val fullFile = File(downloadDir, entity.fileName)
            if (partFile.exists()) partFile.delete()
            if (fullFile.exists()) fullFile.delete()
          } else if (entity.destinationType == "USB_HDD" && !entity.destinationUri.isNullOrBlank()) {
            val rootDoc = UsbHddManager.getRootDocument(appContext, entity.destinationUri)
            val downloadsDir = rootDoc?.findFile("Downloads") ?: rootDoc
            downloadsDir?.findFile("${entity.fileName}.part")?.delete()
            downloadsDir?.findFile(entity.fileName)?.delete()
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }

      dao.deleteById(downloadId)
    }
  }

  private fun startWork(entity: DownloadEntity) {
    val inputData = Data.Builder()
      .putString(DownloadWorker.KEY_DOWNLOAD_ID, entity.id)
      .putString(DownloadWorker.KEY_URL, entity.url)
      .putString(DownloadWorker.KEY_FILE_NAME, entity.fileName)
      .putString(DownloadWorker.KEY_DEST_TYPE, entity.destinationType)
      .putString(DownloadWorker.KEY_DEST_URI, entity.destinationUri)
      .build()

    val constraints = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

    val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
      .setInputData(inputData)
      .setConstraints(constraints)
      .addTag("download_${entity.id}")
      .build()

    workManager.enqueueUniqueWork(
      "download_${entity.id}",
      ExistingWorkPolicy.REPLACE,
      workRequest
    )
  }

  private fun extractFileNameFromUrl(urlStr: String): String {
    try {
      val uri = Uri.parse(urlStr)
      val lastSegment = uri.lastPathSegment
      if (!lastSegment.isNullOrBlank()) {
        val decoded = URLDecoder.decode(lastSegment, "UTF-8")
        val clean = decoded.substringBefore('?').substringBefore('#')
        if (clean.isNotBlank()) return clean
      }
    } catch (e: Exception) {
      // Fallback
    }
    return "download_${System.currentTimeMillis()}.bin"
  }
}
