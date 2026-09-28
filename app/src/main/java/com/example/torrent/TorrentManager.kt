package com.example.torrent

import android.content.Context
import android.net.Uri
import com.example.data.database.AppDatabase
import com.example.data.database.TorrentEntity
import com.example.data.preferences.AppPreferences
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.Sha1Hash
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

class TorrentManager private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: TorrentManager? = null

        fun getInstance(context: Context): TorrentManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TorrentManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val db = AppDatabase.getDatabase(appContext)
    private val dao = db.torrentDao()
    private val preferences = AppPreferences(appContext)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var loopJob: Job? = null

    // Real jlibtorrent Engine Instance
    private val sessionManager = SessionManager()

    val allTorrents: Flow<List<TorrentEntity>> = dao.getAllTorrents()

    init {
        initTorrentEngine()
        startEngineLoop()
    }

    // ১. প্রাইভেট ট্র্যাকার অনুযায়ী qBittorrent 4.5.2 স্পুফিং কনফিগারেশন
    private fun initTorrentEngine() {
        try {
            val settings = SettingsPack()

            // User-Agent: qBittorrent/4.5.2
            settings.setString(
                settings_pack.string_types.user_agent.swigValue(),
                "qBittorrent/4.5.2"
            )

            // Peer-ID Fingerprint: -qB4520-
            settings.setString(
                settings_pack.string_types.peer_fingerprint.swigValue(),
                "-qB4520-"
            )

            // Anonymous mode বন্ধ রাখা যেন ট্র্যাকার সঠিক Header পায়
            settings.anonymousMode(false)

            sessionManager.applySettings(settings)
            sessionManager.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
                delay(1000) // প্রতি ১ সেকেন্ড পর পর রিয়েল স্ট্যাটাস আপডেট হবে
            }
        }
    }

    // ২. jlibtorrent থেকে আসল ডাউনলোড স্পিড, প্রোগ্রেস ও সিড ডাটাবেজে আপডেট
    private suspend fun processTorrentTick() {
        val activeList = dao.getActiveTorrents()
        if (activeList.isEmpty()) return

        for (torrent in activeList) {
            val sha1 = runCatching { Sha1Hash(torrent.id) }.getOrNull() ?: continue
            val handle = sessionManager.find(sha1)

            if (handle != null && handle.isValid) {
                val status = handle.status()

                val progress = status.progress() // 0.0f to 1.0f
                val downloaded = status.totalDone()
                val total = if (status.totalWanted() > 0) status.totalWanted() else torrent.totalBytes
                val dlSpeed = status.downloadPayloadRate().toLong()
                val ulSpeed = status.uploadPayloadRate().toLong()
                val uploaded = status.totalUpload()
                val peers = status.numPeers()
                val seeds = status.numSeeds()
                val ratio = if (downloaded > 0) uploaded.toFloat() / downloaded.toFloat() else 0f

                val currentStatusStr = when {
                    status.isFinished || progress >= 1.0f -> "SEEDING"
                    torrent.status == "PAUSED" -> "PAUSED"
                    else -> "DOWNLOADING"
                }

                dao.updateProgress(
                    id = torrent.id,
                    progress = progress,
                    downloaded = downloaded,
                    total = total,
                    dlSpeed = dlSpeed,
                    ulSpeed = ulSpeed,
                    uploaded = uploaded,
                    peers = peers,
                    seeds = seeds,
                    ratio = ratio,
                    status = currentStatusStr
                )
            }
        }
    }

    // ৩. আসল ম্যাগনেট লিংক দিয়ে ডাউনলোড শুরু করা
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
                resumeTorrent(infoHash)
            }
            return@withContext infoHash
        }

        val preferredDest = destinationTypeOverride ?: preferences.getDefaultDestinationSync()
        val usbTreeUri = preferences.getUsbTreeUriSync()
        val actualDestType = if (preferredDest == "USB_HDD" && !usbTreeUri.isNullOrBlank()) "USB_HDD" else "INTERNAL"

        // সেভ ফোল্ডার পাথ নির্ধারণ
        val saveDir = if (actualDestType == "USB_HDD") {
            File(appContext.getExternalFilesDir(null), "Downloads")
        } else {
            File(appContext.getExternalFilesDir(null), "TVFileHub_Torrents")
        }
        if (!saveDir.exists()) saveDir.mkdirs()

        val estimatedTotal = 2_450_000_000L
        val entity = TorrentEntity(
            id = infoHash,
            name = parsedName,
            magnetUri = cleanMagnet,
            destinationType = actualDestType,
            destinationUri = if (actualDestType == "USB_HDD") usbTreeUri else null,
            status = "DOWNLOADING",
            progress = 0.0f,
            downloadedBytes = 0L,
            totalBytes = estimatedTotal,
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

        // jlibtorrent ইঞ্জিনে আসল ডাউনলোড স্টার্ট করা
        sessionManager.download(cleanMagnet, saveDir)

        startEngineLoop()
        infoHash
    }

    fun pauseTorrent(id: String) {
        scope.launch {
            val handle = runCatching { Sha1Hash(id) }.getOrNull()?.let { sessionManager.find(it) }
            handle?.pause()
            dao.updateStatus(id, "PAUSED")
        }
    }

    fun resumeTorrent(id: String) {
        scope.launch {
            val handle = runCatching { Sha1Hash(id) }.getOrNull()?.let { sessionManager.find(it) }
            handle?.resume()
            val torrent = dao.getTorrentById(id) ?: return@launch
            val newStatus = if (torrent.progress >= 1f) "SEEDING" else "DOWNLOADING"
            dao.updateStatus(id, newStatus)
            startEngineLoop()
        }
    }

    fun deleteTorrent(id: String, deleteFiles: Boolean = true) {
        scope.launch {
            val handle = runCatching { Sha1Hash(id) }.getOrNull()?.let { sessionManager.find(it) }
            if (handle != null && handle.isValid) {
                sessionManager.remove(handle)
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
}
