package com.example.torrent

import android.content.Context
import android.net.Uri
import com.example.data.database.AppDatabase
import com.example.data.database.TorrentEntity
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.TorrentStatus
import com.frostwire.jlibtorrent.swig.settings_pack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.util.UUID

// Extension to support settings.userAgent(...)
private fun SettingsPack.userAgent(value: String): SettingsPack =
    setString(settings_pack.string_types.user_agent.swigValue(), value)

// Extension to support status.isPaused
private val TorrentStatus.isPaused: Boolean
    get() = state() == TorrentStatus.State.CHECKING_FILES || state() == TorrentStatus.State.CHECKING_RESUME_DATA

class TorrentManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    
    // ডাটাবেজ DAO ইনিশিয়ালাইজেশন
    private val dao = AppDatabase.getInstance(appContext).torrentDao()

    val allTorrents: Flow<List<TorrentEntity>> = dao.getAllTorrents()

    private var sessionManager: SessionManager? = null
    private var isEngineInitialized = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var loopJob: Job? = null

    init {
        initTorrentEngine()
        startEngineLoop()
    }

    private fun initTorrentEngine() {
        try {
            val session = SessionManager()
            val settings = SettingsPack()

            // qBittorrent 4.5.2 Client Identity Spoofing
            settings.userAgent("qBittorrent/4.5.2")
            settings.setString(
                settings_pack.string_types.peer_fingerprint.swigValue(),
                "-qB4520-"
            )
            settings.anonymousMode(false)

            // P2P নেটওয়ার্ক পোর্ট সেটআপ
            settings.listenInterfaces("0.0.0.0:6881,[::]:6881")

            session.applySettings(settings)
            session.start()

            this.sessionManager = session
            this.isEngineInitialized = true
        } catch (e: Throwable) {
            e.printStackTrace()
            isEngineInitialized = false
        }
    }

    fun startEngineLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                processTorrentTick()
                delay(1000)
            }
        }
    }

    private suspend fun processTorrentTick() {
        if (!isEngineInitialized || sessionManager == null) return

        val activeList = dao.getActiveTorrents()
        if (activeList.isEmpty()) return

        for (torrent in activeList) {
            try {
                // String ID -> Sha1Hash ফিক্স
                val sha1 = runCatching { Sha1Hash(torrent.id) }.getOrNull() ?: continue
                val handle = sessionManager?.find(sha1)

                if (handle != null && handle.isValid) {
                    val status = handle.status()

                    val progress = status.progress()
                    val downloaded = status.totalDone()
                    val total = status.totalWanted()
                    val dlSpeed = status.downloadPayloadRate().toLong()
                    val ulSpeed = status.uploadPayloadRate().toLong()
                    val uploaded = status.totalUpload()
                    val peers = status.numPeers()
                    val seeds = status.numSeeds()
                    val ratio = if (downloaded > 0) uploaded.toFloat() / downloaded.toFloat() else 0f

                    val currentStatusStr = when {
                        status.isFinished || progress >= 1.0f -> "SEEDING"
                        status.isPaused || torrent.status == "PAUSED" -> "PAUSED"
                        else -> "DOWNLOADING"
                    }

                    dao.updateProgress(
                        id = torrent.id,
                        progress = progress,
                        downloaded = downloaded,
                        total = if (total > 0) total else torrent.totalBytes,
                        dlSpeed = dlSpeed,
                        ulSpeed = ulSpeed,
                        uploaded = uploaded,
                        peers = peers,
                        seeds = seeds,
                        ratio = ratio,
                        status = currentStatusStr
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun addMagnet(magnetUri: String, destinationOverride: String? = null): String = withContext(Dispatchers.IO) {
        val cleanMagnet = magnetUri.trim()
        val infoHash = parseInfoHash(cleanMagnet) ?: UUID.randomUUID().toString().replace("-", "").take(40)
        val name = parseMagnetName(cleanMagnet)

        val saveDir = File(appContext.getExternalFilesDir(null), "Downloads")
        if (!saveDir.exists()) {
            saveDir.mkdirs()
        }

        val existing = dao.getTorrentById(infoHash)
        if (existing != null) {
            if (existing.status == "PAUSED") {
                resumeTorrent(infoHash)
            }
            return@withContext infoHash
        }

        val entity = TorrentEntity(
            id = infoHash,
            name = name,
            magnetUri = cleanMagnet,
            destinationType = "INTERNAL",
            destinationUri = null,
            status = "DOWNLOADING",
            progress = 0.0f,
            downloadedBytes = 0L,
            totalBytes = 2_450_000_000L,
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            uploadedBytes = 0L,
            peers = 0,
            seeds = 0,
            ratio = 0f,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        dao.insertOrUpdate(entity)

        if (isEngineInitialized && sessionManager != null) {
            try {
                sessionManager?.download(cleanMagnet, saveDir)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        startEngineLoop()
        infoHash
    }

    fun pauseTorrent(id: String) {
        scope.launch {
            if (!isEngineInitialized) return@launch
            val handle = runCatching { sessionManager?.find(Sha1Hash(id)) }.getOrNull()
            handle?.pause()
            dao.updateStatus(id, "PAUSED")
        }
    }

    fun resumeTorrent(id: String) {
        scope.launch {
            if (!isEngineInitialized) return@launch
            val handle = runCatching { sessionManager?.find(Sha1Hash(id)) }.getOrNull()
            handle?.resume()
            val torrent = dao.getTorrentById(id) ?: return@launch
            val newStatus = if (torrent.progress >= 1f) "SEEDING" else "DOWNLOADING"
            dao.updateStatus(id, newStatus)
            startEngineLoop()
        }
    }

    fun deleteTorrent(id: String, deleteFiles: Boolean = true) {
        scope.launch {
            if (!isEngineInitialized) return@launch
            val handle = runCatching { sessionManager?.find(Sha1Hash(id)) }.getOrNull()
            if (handle != null && handle.isValid) {
                sessionManager?.remove(handle)
            }
            dao.deleteById(id)
        }
    }

    private fun parseMagnetName(magnet: String): String {
        try {
            val uri = Uri.parse(magnet)
            val dn = uri.getQueryParameter("dn")
            if (!dn.isNullOrBlank()) {
                return URLDecoder.decode(dn, "UTF-8")
            }
        } catch (e: Exception) {
            val regex = Regex("dn=([^&]+)")
            val match = regex.find(magnet)
            if (match != null) {
                val raw = match.groupValues[1]
                return try {
                    URLDecoder.decode(raw, "UTF-8")
                } catch (ex: Exception) {
                    raw
                }
            }
        }
        return "Torrent_${System.currentTimeMillis()}"
    }

    private fun parseInfoHash(magnet: String): String? {
        val regex = Regex("urn:btih:([a-zA-Z0-9]+)")
        return regex.find(magnet)?.groupValues?.get(1)?.lowercase()
    }

    companion object {
        @Volatile
        private var INSTANCE: TorrentManager? = null

        fun getInstance(context: Context): TorrentManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TorrentManager(context).also { INSTANCE = it }
            }
        }
    }
}
