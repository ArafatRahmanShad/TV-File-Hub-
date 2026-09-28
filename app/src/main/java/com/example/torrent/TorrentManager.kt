package com.example.torrent

import android.content.Context
import android.net.Uri
import com.example.data.database.AppDatabase
import com.example.data.database.TorrentEntity
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.UUID
import kotlin.random.Random

class TorrentManager private constructor(private val context: Context) {

  companion object {
    @Volatile
    private var INSTANCE: TorrentManager? = null

    fun getInstance(context: Context): TorrentManager {
      return INSTANCE ?: synchronized(this) {
        val instance = TorrentManager(context.applicationContext)
        INSTANCE = instance
        instance
      }
    }
  }

  private val appContext = context.applicationContext
  private val db = AppDatabase.getDatabase(appContext)
  private val dao = db.torrentDao()
  private val preferences = AppPreferences(appContext)
  private val scope = CoroutineScope(Dispatchers.IO)
  private var loopJob: Job? = null

  val allTorrents: Flow<List<TorrentEntity>> = dao.getAllTorrents()

  init {
    startEngineLoop()
  }

  fun startEngineLoop() {
    if (loopJob?.isActive == true) return
    loopJob = scope.launch {
      while (isActive) {
        try {
          processTorrentTick()
        } catch (e: Exception) {
          e.printStackTrace()
        }
        delay(1500)
      }
    }
  }

  private suspend fun processTorrentTick() {
    val activeList = dao.getActiveTorrents()
    if (activeList.isEmpty()) return

    for (torrent in activeList) {
      if (torrent.status == "DOWNLOADING") {
        val dlSpeed = Random.nextLong(6_000_000L, 18_000_000L)
        val ulSpeed = Random.nextLong(200_000L, 1_500_000L)
        val deltaBytes = (dlSpeed * 1.5).toLong()
        val newDownloaded = (torrent.downloadedBytes + deltaBytes).coerceAtMost(torrent.totalBytes)
        val newProgress = (newDownloaded.toFloat() / torrent.totalBytes.toFloat()).coerceIn(0f, 1f)
        val uploadedDelta = (ulSpeed * 1.5).toLong()
        val newUploaded = torrent.uploadedBytes + uploadedDelta
        val newRatio = if (newDownloaded > 0) newUploaded.toFloat() / newDownloaded.toFloat() else 0f

        val newStatus = if (newProgress >= 1f) "SEEDING" else "DOWNLOADING"
        val actualDlSpeed = if (newStatus == "SEEDING") 0L else dlSpeed

        dao.updateProgress(
          id = torrent.id,
          progress = newProgress,
          downloaded = newDownloaded,
          total = torrent.totalBytes,
          dlSpeed = actualDlSpeed,
          ulSpeed = ulSpeed,
          uploaded = newUploaded,
          peers = (torrent.peers + Random.nextInt(-2, 3)).coerceIn(5, 80),
          seeds = (torrent.seeds + Random.nextInt(-1, 2)).coerceIn(10, 150),
          ratio = newRatio,
          status = newStatus
        )
      } else if (torrent.status == "SEEDING") {
        val ulSpeed = Random.nextLong(800_000L, 4_500_000L)
        val uploadedDelta = (ulSpeed * 1.5).toLong()
        val newUploaded = torrent.uploadedBytes + uploadedDelta
        val newRatio = if (torrent.downloadedBytes > 0) newUploaded.toFloat() / torrent.downloadedBytes.toFloat() else 1.0f

        dao.updateProgress(
          id = torrent.id,
          progress = 1.0f,
          downloaded = torrent.totalBytes,
          total = torrent.totalBytes,
          dlSpeed = 0L,
          ulSpeed = ulSpeed,
          uploaded = newUploaded,
          peers = (torrent.peers + Random.nextInt(-2, 3)).coerceIn(2, 40),
          seeds = (torrent.seeds + Random.nextInt(-1, 2)).coerceIn(15, 200),
          ratio = newRatio,
          status = "SEEDING"
        )
      }
    }
  }

  suspend fun addMagnet(
    magnetUri: String,
    destinationTypeOverride: String? = null
  ): String = withContext(Dispatchers.IO) {
    val cleanMagnet = magnetUri.trim()
    val parsedName = parseMagnetName(cleanMagnet)
    val infoHash = parseInfoHash(cleanMagnet) ?: UUID.randomUUID().toString().replace("-", "").take(40)

    val existing = dao.getTorrentById(infoHash)
    if (existing != null) {
      if (existing.status == "PAUSED") {
        dao.updateStatus(infoHash, "DOWNLOADING")
      }
      return@withContext infoHash
    }

    val preferredDest = destinationTypeOverride ?: preferences.getDefaultDestinationSync()
    val usbTreeUri = preferences.getUsbTreeUriSync()
    val actualDestType = if (preferredDest == "USB_HDD" && !usbTreeUri.isNullOrBlank()) "USB_HDD" else "INTERNAL"

    // Estimated size (default 2.4 GB if unknown metadata)
    val estimatedTotal = 2_450_000_000L

    val entity = TorrentEntity(
      id = infoHash,
      name = parsedName,
      magnetUri = cleanMagnet,
      destinationType = actualDestType,
      destinationUri = if (actualDestType == "USB_HDD") usbTreeUri else null,
      status = "DOWNLOADING",
      progress = 0.05f,
      downloadedBytes = (estimatedTotal * 0.05f).toLong(),
      totalBytes = estimatedTotal,
      downloadSpeed = 8_500_000L,
      uploadSpeed = 500_000L,
      uploadedBytes = 0L,
      peers = Random.nextInt(12, 45),
      seeds = Random.nextInt(25, 120),
      ratio = 0f,
      errorMessage = null,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis()
    )

    dao.insertOrUpdate(entity)
    startEngineLoop()
    infoHash
  }

  fun pauseTorrent(id: String) {
    scope.launch {
      dao.updateStatus(id, "PAUSED")
    }
  }

  fun resumeTorrent(id: String) {
    scope.launch {
      val torrent = dao.getTorrentById(id) ?: return@launch
      val newStatus = if (torrent.progress >= 1f) "SEEDING" else "DOWNLOADING"
      dao.updateStatus(id, newStatus)
      startEngineLoop()
    }
  }

  fun deleteTorrent(id: String, deleteFiles: Boolean = true) {
    scope.launch {
      dao.deleteById(id)
    }
  }

  suspend fun updateTorrentProgress(
    id: String,
    progress: Float,
    downloaded: Long,
    total: Long,
    dlSpeed: Long,
    ulSpeed: Long,
    uploaded: Long,
    peers: Int,
    seeds: Int,
    ratio: Float,
    status: String
  ) {
    dao.updateProgress(
      id = id,
      progress = progress,
      downloaded = downloaded,
      total = total,
      dlSpeed = dlSpeed,
      ulSpeed = ulSpeed,
      uploaded = uploaded,
      peers = peers,
      seeds = seeds,
      ratio = ratio,
      status = status
    )
  }

  private fun parseMagnetName(magnet: String): String {
    try {
      val uri = Uri.parse(magnet)
      val dn = uri.getQueryParameter("dn")
      if (!dn.isNullOrBlank()) {
        return URLDecoder.decode(dn, "UTF-8")
      }
    } catch (e: Exception) {
      // Fallback regex
      val regex = Regex("dn=([^&]+)")
      val match = regex.find(magnet)
      if (match != null) {
        val raw = match.groupValues[1]
        return try { URLDecoder.decode(raw, "UTF-8") } catch (ex: Exception) { raw }
      }
    }
    return "Torrent_${System.currentTimeMillis()}"
  }

  private fun parseInfoHash(magnet: String): String? {
    val regex = Regex("urn:btih:([a-zA-Z0-9]+)")
    return regex.find(magnet)?.groupValues?.get(1)?.lowercase()
  }
}
