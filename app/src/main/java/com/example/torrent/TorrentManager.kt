package com.example.torrent

import android.content.Context
import android.net.Uri
import com.example.data.database.AppDatabase
import com.example.data.database.TorrentEntity
import com.example.storage.StorageManager
import com.frostwire.jlibtorrent.AlertListener
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.SettingsPack
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.TorrentStatus
import com.frostwire.jlibtorrent.alerts.Alert
import com.frostwire.jlibtorrent.alerts.AlertType
import com.frostwire.jlibtorrent.alerts.MetadataReceivedAlert
import com.frostwire.jlibtorrent.alerts.TorrentAlert
import com.frostwire.jlibtorrent.swig.settings_pack
import com.frostwire.jlibtorrent.swig.string_int_pair
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

private fun SettingsPack.userAgent(value: String): SettingsPack =
    setString(settings_pack.string_types.user_agent.swigValue(), value)

private val TorrentStatus.isPaused: Boolean
    get() = state() == TorrentStatus.State.CHECKING_FILES || state() == TorrentStatus.State.CHECKING_RESUME_DATA

class TorrentManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    private val dao by lazy {
        AppDatabase.getInstance(appContext).torrentDao()
    }

    val allTorrents: Flow<List<TorrentEntity>> get() = dao.getAllTorrents()

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

            // 1. Client Identity Spoofing (qBittorrent 4.5.2)
            settings.userAgent("qBittorrent/4.5.2")
            settings.setString(
                settings_pack.string_types.peer_fingerprint.swigValue(),
                "-qB4520-"
            )
            settings.anonymousMode(false)

            // 2. Enable DHT & Bootstrap Public Routers to Fix 0% Stuck Magnets
            settings.enableDht(true)
            settings.setString(
                settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                "router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881"
            )

            // 3. Port Binding (Incoming & Outgoing Connection)
            settings.listenInterfaces("0.0.0.0:6881,[::]:6881")

            // 4. Alert Listener for metadata received, torrent completion, and error handling
            session.addListener(object : AlertListener {
                override fun types(): IntArray? = null

                override fun alert(alert: Alert<*>) {
                    try {
                        when (alert.type()) {
                            AlertType.METADATA_RECEIVED -> {
                                val metaAlert = alert as? MetadataReceivedAlert
                                val handle = metaAlert?.handle()
                                if (handle != null && handle.isValid) {
                                    val id = handle.infoHash().toHex()
                                    val info = handle.torrentFile()
                                    val torrentName = info?.name() ?: metaAlert.torrentName()
                                    val totalBytes = info?.totalSize() ?: 0L

                                    // Resume handle so piece download starts immediately
                                    handle.resume()

                                    scope.launch {
                                        if (!torrentName.isNullOrBlank() && totalBytes > 0) {
                                            dao.updateMetadata(id, torrentName, totalBytes)
                                        }
                                    }
                                }
                            }
                            AlertType.TORRENT_FINISHED -> {
                                val torrentAlert = alert as? TorrentAlert<*>
                                val handle = torrentAlert?.handle()
                                if (handle != null && handle.isValid) {
                                    val id = handle.infoHash().toHex()
                                    scope.launch {
                                        dao.updateStatus(id, "SEEDING")
                                    }
                                }
                            }
                            else -> {}
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            })

            session.applySettings(settings)
            session.start()

            // 5. Active DHT bootstrap startup & node discovery
            try {
                session.startDht()
                session.swig()?.add_dht_node(string_int_pair("router.bittorrent.com", 6881))
                session.swig()?.add_dht_node(string_int_pair("dht.transmissionbt.com", 6881))
                session.swig()?.add_dht_node(string_int_pair("router.utorrent.com", 6881))
                session.postDhtStats()
            } catch (ignored: Throwable) {}

            this.sessionManager = session
            this.isEngineInitialized = true

            // 6. Restore active torrents from Room DB upon engine initialization
            restoreActiveTorrents()
        } catch (e: Throwable) {
            e.printStackTrace()
            isEngineInitialized = false
        }
    }

    private fun restoreActiveTorrents() {
        scope.launch {
            try {
                val activeList = dao.getActiveTorrents()
                if (activeList.isEmpty() || !isEngineInitialized || sessionManager == null) return@launch

                val torrentsDir = File(appContext.filesDir, "torrents")
                for (torrent in activeList) {
                    try {
                        val sha1 = try { Sha1Hash(torrent.id) } catch (e: Exception) { null } ?: continue
                        val handle = sessionManager?.find(sha1)

                        if (handle == null || !handle.isValid) {
                            val saveDir = getSaveDir(torrent.destinationType)
                            val cachedFile = File(torrentsDir, "${torrent.id}.torrent")

                            if (cachedFile.exists() && cachedFile.length() > 0) {
                                val info = TorrentInfo(cachedFile)
                                sessionManager?.download(info, saveDir)
                            } else if (torrent.magnetUri.isNotBlank()) {
                                sessionManager?.download(torrent.magnetUri, saveDir)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
                        status.isPaused || torrent.status == "PAUSED" -> "PAUSED"
                        else -> "DOWNLOADING"
                    }

                    // Check if metadata has updated torrent name
                    val handleInfo = handle.torrentFile()
                    val finalTotal = if (handleInfo != null && handleInfo.totalSize() > 0) {
                        handleInfo.totalSize()
                    } else if (total > 0) {
                        total
                    } else {
                        torrent.totalBytes
                    }

                    dao.updateProgress(
                        id = torrent.id,
                        progress = progress,
                        downloaded = downloaded,
                        total = finalTotal,
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

    private fun getSaveDir(destinationOverride: String? = null): File {
        val destType = destinationOverride ?: "INTERNAL"
        val dir = if (destType == "USB_HDD") {
            val externalDirs = appContext.getExternalFilesDirs(null)
            if (externalDirs.size > 1 && externalDirs[1] != null && externalDirs[1].canWrite()) {
                File(externalDirs[1], "Downloads")
            } else {
                StorageManager.getDefaultDownloadDirectory(appContext)
            }
        } else {
            StorageManager.getDefaultDownloadDirectory(appContext)
        }

        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun addMagnet(magnetUri: String, destinationOverride: String? = null): String = withContext(Dispatchers.IO) {
        val cleanMagnet = magnetUri.trim()
        val saveDir = getSaveDir(destinationOverride)

        val infoHash = extractInfoHash(cleanMagnet) ?: System.currentTimeMillis().toString()
        val torrentName = extractTorrentName(cleanMagnet) ?: "Downloading Metadata..."

        try {
            val newTorrent = TorrentEntity(
                id = infoHash,
                name = torrentName,
                magnetUri = cleanMagnet,
                destinationType = destinationOverride ?: if (saveDir.path.contains("Android/data")) "USB_HDD" else "INTERNAL",
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

    suspend fun addTorrentFile(uri: Uri, destinationOverride: String? = null): String = withContext(Dispatchers.IO) {
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Cannot open stream for Uri: $uri")

        val torrentInfo = TorrentInfo(bytes)
        val infoHash = torrentInfo.infoHash().toHex()
        val torrentName = if (torrentInfo.name().isNotBlank()) torrentInfo.name() else "Torrent_$infoHash"
        val totalBytes = torrentInfo.totalSize()
        val generatedMagnet = try { torrentInfo.makeMagnetUri() ?: "" } catch (e: Exception) { "" }

        // Cache .torrent file locally so it can be restored on app/engine reboot
        try {
            val torrentsDir = File(appContext.filesDir, "torrents")
            if (!torrentsDir.exists()) {
                torrentsDir.mkdirs()
            }
            val cachedFile = File(torrentsDir, "$infoHash.torrent")
            cachedFile.writeBytes(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val saveDir = getSaveDir(destinationOverride)

        try {
            val newTorrent = TorrentEntity(
                id = infoHash,
                name = torrentName,
                magnetUri = generatedMagnet,
                destinationType = destinationOverride ?: if (saveDir.path.contains("Android/data")) "USB_HDD" else "INTERNAL",
                status = "DOWNLOADING",
                progress = 0f,
                totalBytes = totalBytes,
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
                sessionManager?.download(torrentInfo, saveDir)
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
                val cachedFile = File(File(appContext.filesDir, "torrents"), "$id.torrent")
                if (cachedFile.exists()) {
                    cachedFile.delete()
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
