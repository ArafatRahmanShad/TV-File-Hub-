package com.example.torrent

import android.content.Context
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.swig.settings_pack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import kotlin.getValue

// প্রজেক্টের সব সাব-প্যাকেজের অটো ইম্পোর্ট
import com.example.*
import com.example.data.*
import com.example.db.*
import com.example.database.*
import com.example.model.*
import com.example.entity.*
import com.example.room.*

class TorrentManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    // App Database / DAO lookup
    private val dao by lazy {
        AppDatabase.getInstance(appContext).torrentDao()
    }

    private var sessionManager: SessionManager? = null
    private var isEngineInitialized = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var loopJob: Job? = null

    init {
        initTorrentEngine()
    }

    private fun initTorrentEngine() {
        try {
            val session = SessionManager()
            val settings = SettingsPack()

            // 1. Private Tracker Spoofing (qBittorrent 4.5.2) via SWIG
            settings.swig().set_str(
                settings_pack.string_types.user_agent.swigValue(),
                "qBittorrent/4.5.2"
            )
            settings.swig().set_str(
                settings_pack.string_types.peer_fingerprint.swigValue(),
                "-qB4520-"
            )
            settings.swig().set_bool(
                settings_pack.bool_types.anonymous_mode.swigValue(),
                false
            )

            // 2. Public Tracker Peer Discovery (DHT, PEX, LSD) via SWIG Fix
            settings.swig().set_bool(
                settings_pack.bool_types.enable_dht.swigValue(),
                true
            )
            settings.swig().set_bool(
                settings_pack.bool_types.enable_pex.swigValue(),
                true
            )
            settings.swig().set_bool(
                settings_pack.bool_types.enable_lsd.swigValue(),
                true
            )

            // 3. P2P Port Binding
            settings.listenInterfaces("0.0.0.0:6881,[::]:6881")

            session.applySettings(settings)
            session.start()

            try {
                session.postDhtStats()
            } catch (ignored: Exception) {}

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

        val activeList = try {
            dao.getActiveTorrents()
        } catch (e: Exception) {
            emptyList()
        }

        if (activeList.isEmpty()) return

        for (torrent in activeList) {
            try {
                val sha1 = try { Sha1Hash(torrent.id) } catch (e: Exception) { null } ?: continue
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
                        handle.isPaused -> "PAUSED"
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
        val saveDir = File(appContext.getExternalFilesDir(null), "Downloads")
        if (!saveDir.exists()) {
            saveDir.mkdirs()
        }

        val infoHash = extractInfoHash(cleanMagnet) ?: System.currentTimeMillis().toString()
        val torrentName = extractTorrentName(cleanMagnet) ?: "Downloading Metadata..."

        try {
            val newTorrent = TorrentEntity(
                id = infoHash,
                name = torrentName,
                status = "DOWNLOADING",
                progress = 0f,
                totalBytes = 0L,
                downloadedBytes = 0L,
                uploadedBytes = 0L,
                downloadSpeed = 0L,
                uploadSpeed = 0L,
                seeds = 0,
                peers = 0,
                ratio = 0f
            )
            dao.insert(newTorrent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
            try {
                val handle = sessionManager?.find(Sha1Hash(id))
                handle?.pause()
                dao.updateStatus(id, "PAUSED")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resumeTorrent(id: String) {
        scope.launch {
            if (!isEngineInitialized) return@launch
            try {
                val handle = sessionManager?.find(Sha1Hash(id))
                handle?.resume()
                val torrent = dao.getTorrentById(id)
                val newStatus = if (torrent != null && torrent.progress >= 1f) "SEEDING" else "DOWNLOADING"
                dao.updateStatus(id, newStatus)
                startEngineLoop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteTorrent(id: String, deleteFiles: Boolean = true) {
        scope.launch {
            if (!isEngineInitialized) return@launch
            try {
                val handle = sessionManager?.find(Sha1Hash(id))
                if (handle != null && handle.isValid) {
                    sessionManager?.remove(handle)
                }
                dao.deleteById(id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun extractInfoHash(magnet: String): String? {
        val regex = Regex("xt=urn:btih:([a-zA-F0-9]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(magnet)?.groupValues?.get(1) ?: return null

        return when (match.length) {
            40 -> match.lowercase()
            32 -> base32ToHex(match)
            else -> match.lowercase()
        }
    }

    private fun extractTorrentName(magnet: String): String? {
        val regex = Regex("dn=([^&]+)")
        val match = regex.find(magnet)?.groupValues?.get(1)
        return match?.let {
            try { URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { it }
        }
    }

    private fun base32ToHex(base32: String): String {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = ""
        for (char in base32.uppercase()) {
            val valIndex = base32Chars.indexOf(char)
            if (valIndex >= 0) {
                bits += String.format("%5s", Integer.toBinaryString(valIndex)).replace(' ', '0')
            }
        }
        val hex = StringBuilder()
        for (i in 0 until bits.length - 3 step 4) {
            val chunk = bits.substring(i, i + 4)
            hex.append(Integer.toHexString(chunk.toInt(2)))
        }
        return hex.toString()
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
