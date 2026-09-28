package com.example.web

import android.content.Context
import android.net.wifi.WifiManager
import com.example.data.database.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.download.DownloadManager
import com.example.storage.StorageManager
import com.example.storage.UsbHddManager
import com.example.torrent.TorrentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Locale

class TvFileHubHttpServer(private val context: Context) {

  private val appContext = context.applicationContext
  private val db = AppDatabase.getDatabase(appContext)
  private val downloadManager = DownloadManager(appContext)
  private val torrentManager = TorrentManager.getInstance(appContext)
  private val preferences = AppPreferences(appContext)

  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO)

  var isRunning = false
    private set
  var currentPort = 8765
    private set

  fun start(port: Int = 8765) {
    if (isRunning) return
    currentPort = port
    serverJob = scope.launch {
      try {
        serverSocket = ServerSocket(port)
        isRunning = true
        while (isActive && !serverSocket!!.isClosed) {
          val client = serverSocket!!.accept()
          scope.launch { handleClient(client) }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        isRunning = false
      }
    }
  }

  fun stop() {
    isRunning = false
    try {
      serverSocket?.close()
      serverSocket = null
    } catch (e: Exception) {
      e.printStackTrace()
    }
    serverJob?.cancel()
    serverJob = null
  }

  fun getLocalIpAddress(): String {
    try {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces.hasMoreElements()) {
        val intf = interfaces.nextElement()
        if (intf.isLoopback || !intf.isUp) continue
        val addresses = intf.inetAddresses
        while (addresses.hasMoreElements()) {
          val addr = addresses.nextElement()
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            val ip = addr.hostAddress
            if (ip != null && !ip.startsWith("127.")) {
              return ip
            }
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return "127.0.0.1"
  }

  private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
    try {
      client.soTimeout = 15000
      val reader = BufferedReader(InputStreamReader(client.getInputStream()))
      val output = client.getOutputStream()

      val requestLine = reader.readLine() ?: return@withContext
      val parts = requestLine.split(" ")
      if (parts.size < 2) return@withContext

      val method = parts[0].uppercase(Locale.ROOT)
      val fullPath = parts[1]
      val path = fullPath.substringBefore('?')

      // Read headers
      var contentLength = 0
      var line: String?
      while (reader.readLine().also { line = it } != null) {
        if (line.isNullOrBlank()) break
        val header = line!!
        if (header.startsWith("Content-Length:", ignoreCase = true)) {
          contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
        }
      }

      // Read body if any
      var body = ""
      if (contentLength > 0) {
        val charBuffer = CharArray(contentLength)
        var readTotal = 0
        while (readTotal < contentLength) {
          val read = reader.read(charBuffer, readTotal, contentLength - readTotal)
          if (read == -1) break
          readTotal += read
        }
        body = String(charBuffer, 0, readTotal)
      }

      // Route request
      when {
        method == "OPTIONS" -> {
          sendResponse(output, 204, "No Content", "text/plain", "")
        }
        method == "GET" && (path == "/" || path == "/index.html") -> {
          sendResponse(output, 200, "OK", "text/html; charset=UTF-8", getWebUiHtml())
        }
        method == "GET" && path == "/api/status" -> {
          val statusJson = getStatusJson()
          sendResponse(output, 200, "OK", "application/json", statusJson.toString())
        }
        method == "GET" && path == "/api/downloads" -> {
          val downloads = db.downloadDao().getAllDownloads().first()
          val array = JSONArray()
          for (d in downloads) {
            array.put(
              JSONObject().apply {
                put("id", d.id)
                put("url", d.url)
                put("fileName", d.fileName)
                put("destinationType", d.destinationType)
                put("status", d.status)
                put("downloadedBytes", d.downloadedBytes)
                put("totalBytes", d.totalBytes)
                put("speedBytesPerSec", d.speedBytesPerSec)
                put("etaSeconds", d.etaSeconds)
                put("errorMessage", d.errorMessage ?: "")
                put("createdAt", d.createdAt)
              }
            )
          }
          sendResponse(output, 200, "OK", "application/json", array.toString())
        }
        method == "POST" && path == "/api/downloads" -> {
          val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
          val url = json.optString("url", "").trim()
          val fileName = json.optString("fileName", null)
          val dest = json.optString("destination", null)
          if (url.isNotBlank()) {
            val id = downloadManager.enqueueDownload(url, fileName, dest)
            sendResponse(output, 200, "OK", "application/json", JSONObject().put("success", true).put("id", id).toString())
          } else {
            sendResponse(output, 400, "Bad Request", "application/json", JSONObject().put("error", "URL is required").toString())
          }
        }
        method == "POST" && path.startsWith("/api/downloads/") && path.endsWith("/pause") -> {
          val id = path.substringAfter("/api/downloads/").substringBefore("/pause")
          downloadManager.pauseDownload(id)
          sendResponse(output, 200, "OK", "application/json", JSONObject().put("success", true).toString())
        }
        method == "POST" && path.startsWith("/api/downloads/") && path.endsWith("/resume") -> {
          val id = path.substringAfter("/api/downloads/").substringBefore("/resume")
          downloadManager.resumeDownload(id)
          sendResponse(output, 200, "OK", "application/json", JSONObject().put("success", true).toString())
        }
        method == "DELETE" && path.startsWith("/api/downloads/") -> {
          val id = path.substringAfter("/api/downloads/")
          downloadManager.deleteDownload(id, removeFile = true)
          sendResponse(output, 200, "OK", "application/json", JSONObject().put("success", true).toString())
        }
        method == "GET" && path == "/api/torrents" -> {
          val torrents = db.torrentDao().getAllTorrents().first()
          val array = JSONArray()
          for (t in torrents) {
            array.put(
              JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("magnetUri", t.magnetUri)
                put("status", t.status)
                put("progress", t.progress)
                put("downloadedBytes", t.downloadedBytes)
                put("totalBytes", t.totalBytes)
                put("downloadSpeed", t.downloadSpeed)
                put("uploadSpeed", t.uploadSpeed)
                put("uploadedBytes", t.uploadedBytes)
                put("peers", t.peers)
                put("seeds", t.seeds)
                put("ratio", t.ratio)
              }
            )
          }
          sendResponse(output, 200, "OK", "application/json", array.toString())
        }
        method == "POST" && path == "/api/torrents" -> {
          val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
          val magnet = json.optString("magnet", "").trim()
          val dest = json.optString("destination", null)
          if (magnet.startsWith("magnet:")) {
            val id = torrentManager.addMagnet(magnet, dest)
            sendResponse(output, 200, "OK", "application/json", JSONObject().put("success", true).put("id", id).toString())
          } else {
            sendResponse(output, 400, "Bad Request", "application/json", JSONObject().put("error", "Valid magnet URI required").toString())
          }
        }
        method == "POST" && path.startsWith("/api/torrents/") && path.endsWith("/pause") -> {
          val id = path.substringAfter("/api/torrents/").substringBefore("/pause")
          torrentManager.pauseTorrent(id)
          sendResponse(output, 200, "OK", "application/json", JSONObject().put("success", true).toString())
        }
        method == "POST" && path.startsWith("/api/torrents/") && path.endsWith("/resume") -> {
          val id = path.substringAfter("/api/torrents/").substringBefore("/resume")
          torrentManager.resumeTorrent(id)
          sendResponse(output, 200, "OK", "application/json", JSONObject().put("success", true).toString())
        }
        method == "DELETE" && path.startsWith("/api/torrents/") -> {
          val id = path.substringAfter("/api/torrents/")
          torrentManager.deleteTorrent(id)
          sendResponse(output, 200, "OK", "application/json", JSONObject().put("success", true).toString())
        }
        method == "GET" && path == "/api/files" -> {
          val downloadsDir = StorageManager.getDefaultDownloadDirectory(appContext)
          val files = StorageManager.listInternalDirectory(downloadsDir)
          val array = JSONArray()
          for (f in files) {
            array.put(
              JSONObject().apply {
                put("name", f.name)
                put("isDirectory", f.isDirectory)
                put("size", f.size)
                put("category", f.category.name)
                put("lastModified", f.lastModified)
              }
            )
          }
          sendResponse(output, 200, "OK", "application/json", array.toString())
        }
        else -> {
          sendResponse(output, 404, "Not Found", "application/json", "{\"error\":\"Not Found\"}")
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    } finally {
      try { client.close() } catch (ignored: Exception) {}
    }
  }

  private suspend fun getStatusJson(): JSONObject {
    val downloads = db.downloadDao().getAllDownloads().first()
    val torrents = db.torrentDao().getAllTorrents().first()
    val activeDownloads = downloads.count { it.status == "DOWNLOADING" || it.status == "QUEUED" }
    val activeTorrents = torrents.count { it.status == "DOWNLOADING" || it.status == "SEEDING" }
    val usbUri = preferences.getUsbTreeUriSync()
    val usbStatus = UsbHddManager.checkStatus(appContext, usbUri)
    val usbConnected = usbStatus is UsbHddManager.UsbStatus.Connected

    return JSONObject().apply {
      put("appName", "TV File Hub")
      put("version", "1.0")
      put("ip", getLocalIpAddress())
      put("port", currentPort)
      put("activeDownloads", activeDownloads)
      put("activeTorrents", activeTorrents)
      put("usbHddConnected", usbConnected)
      put("usbHddName", if (usbStatus is UsbHddManager.UsbStatus.Connected) usbStatus.name else "Not Connected")
    }
  }

  private fun sendResponse(
    out: OutputStream,
    code: Int,
    statusText: String,
    contentType: String,
    content: String
  ) {
    val bytes = content.toByteArray(Charsets.UTF_8)
    val header = buildString {
      append("HTTP/1.1 $code $statusText\r\n")
      append("Content-Type: $contentType\r\n")
      append("Content-Length: ${bytes.size}\r\n")
      append("Access-Control-Allow-Origin: *\r\n")
      append("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS\r\n")
      append("Access-Control-Allow-Headers: Content-Type\r\n")
      append("Connection: close\r\n\r\n")
    }
    out.write(header.toByteArray(Charsets.UTF_8))
    out.write(bytes)
    out.flush()
  }

  private fun getWebUiHtml(): String {
    return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>TV File Hub - Remote Controller</title>
  <style>
    :root {
      --bg: #0b0f19;
      --surface: #151d2f;
      --card: #1c273d;
      --primary: #3b82f6;
      --primary-hover: #2563eb;
      --accent: #10b981;
      --text: #f3f4f6;
      --text-muted: #9ca3af;
      --border: #2c3a58;
      --danger: #ef4444;
      --warning: #f59e0b;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
    body { background: var(--bg); color: var(--text); padding: 16px; min-height: 100vh; }
    .header { display: flex; align-items: center; justify-content: space-between; padding-bottom: 16px; border-bottom: 1px solid var(--border); margin-bottom: 20px; }
    .title-group h1 { font-size: 1.4rem; font-weight: 800; color: #fff; letter-spacing: -0.5px; }
    .badge { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 999px; font-size: 0.75rem; font-weight: 600; background: rgba(16, 185, 129, 0.15); color: #34d399; }
    .badge-dot { width: 8px; height: 8px; border-radius: 50%; background: #34d399; box-shadow: 0 0 8px #34d399; }
    .stats-row { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 20px; }
    .stat-card { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 12px 14px; }
    .stat-label { font-size: 0.75rem; color: var(--text-muted); text-transform: uppercase; margin-bottom: 4px; }
    .stat-val { font-size: 1.1rem; font-weight: 700; color: #fff; }
    .section-card { background: var(--surface); border: 1px solid var(--border); border-radius: 14px; padding: 16px; margin-bottom: 20px; }
    .section-title { font-size: 1.05rem; font-weight: 700; margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
    .input-group { margin-bottom: 12px; }
    .input-label { display: block; font-size: 0.8rem; color: var(--text-muted); margin-bottom: 6px; }
    input[type="text"], select { width: 100%; padding: 12px 14px; background: var(--bg); border: 1px solid var(--border); border-radius: 8px; color: #fff; font-size: 0.95rem; }
    input:focus, select:focus { outline: none; border-color: var(--primary); }
    .btn { display: inline-flex; align-items: center; justify-content: center; width: 100%; padding: 12px; border-radius: 8px; border: none; font-size: 0.95rem; font-weight: 700; cursor: pointer; transition: 0.2s; }
    .btn-primary { background: var(--primary); color: #fff; }
    .btn-primary:active { background: var(--primary-hover); transform: scale(0.98); }
    .btn-sm { padding: 6px 12px; font-size: 0.8rem; width: auto; border-radius: 6px; }
    .btn-danger { background: rgba(239, 68, 68, 0.15); color: var(--danger); border: 1px solid var(--danger); }
    .btn-pause { background: rgba(245, 158, 11, 0.15); color: var(--warning); border: 1px solid var(--warning); }
    .btn-resume { background: rgba(59, 130, 246, 0.15); color: var(--primary); border: 1px solid var(--primary); }
    .item-card { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 14px; margin-bottom: 10px; }
    .item-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }
    .item-title { font-size: 0.9rem; font-weight: 600; word-break: break-all; flex: 1; padding-right: 8px; }
    .status-tag { font-size: 0.7rem; font-weight: 700; padding: 2px 8px; border-radius: 6px; text-transform: uppercase; }
    .status-DOWNLOADING { background: #1e3a8a; color: #93c5fd; }
    .status-COMPLETED { background: #064e3b; color: #6ee7b7; }
    .status-SEEDING { background: #4c1d95; color: #d8b4fe; }
    .status-PAUSED { background: #78350f; color: #fde68a; }
    .status-FAILED { background: #7f1d1d; color: #fca5a5; }
    .progress-bar-bg { width: 100%; height: 8px; background: rgba(255,255,255,0.1); border-radius: 4px; overflow: hidden; margin: 8px 0; }
    .progress-bar-fill { height: 100%; background: var(--primary); border-radius: 4px; transition: width 0.3s ease; }
    .item-details { display: flex; justify-content: space-between; font-size: 0.75rem; color: var(--text-muted); margin-bottom: 10px; }
    .item-actions { display: flex; gap: 8px; }
    .empty-state { text-align: center; padding: 24px 0; color: var(--text-muted); font-size: 0.85rem; }
    .tabs { display: flex; gap: 8px; margin-bottom: 16px; border-bottom: 1px solid var(--border); padding-bottom: 8px; }
    .tab { background: transparent; border: none; color: var(--text-muted); font-weight: 600; padding: 8px 14px; border-radius: 6px; cursor: pointer; }
    .tab.active { background: var(--card); color: #fff; }
  </style>
</head>
<body>
  <div class="header">
    <div class="title-group">
      <h1>TV File Hub</h1>
      <span style="font-size: 0.75rem; color: var(--text-muted)">Remote Control & Downloader</span>
    </div>
    <div class="badge">
      <span class="badge-dot"></span>
      Connected to TV
    </div>
  </div>

  <div class="stats-row">
    <div class="stat-card">
      <div class="stat-label">USB Storage</div>
      <div class="stat-val" id="usb-status">Checking...</div>
    </div>
    <div class="stat-card">
      <div class="stat-label">Active Transfers</div>
      <div class="stat-val" id="transfers-count">0</div>
    </div>
  </div>

  <!-- Direct URL Download Card -->
  <div class="section-card">
    <div class="section-title">📥 Send Direct URL to TV</div>
    <div class="input-group">
      <label class="input-label">Download URL (Video, File, Audio)</label>
      <input type="text" id="dl-url" placeholder="https://example.com/movie.mkv">
    </div>
    <div class="input-group">
      <label class="input-label">File Name (Optional Override)</label>
      <input type="text" id="dl-filename" placeholder="Leave empty for auto-detect">
    </div>
    <div class="input-group">
      <label class="input-label">Storage Destination</label>
      <select id="dl-dest">
        <option value="USB_HDD">USB HDD (Recommended)</option>
        <option value="INTERNAL">Internal TV Storage</option>
      </select>
    </div>
    <button class="btn btn-primary" onclick="submitDirectDownload()">Start Download on TV</button>
  </div>

  <!-- Magnet / Torrent Card -->
  <div class="section-card">
    <div class="section-title">🧲 Send Magnet / Torrent to TV</div>
    <div class="input-group">
      <label class="input-label">Magnet Link</label>
      <input type="text" id="magnet-url" placeholder="magnet:?xt=urn:btih:...">
    </div>
    <button class="btn btn-primary" onclick="submitMagnet()">Start Torrent on TV</button>
  </div>

  <!-- Tabs for Downloads & Torrents -->
  <div class="tabs">
    <button class="tab active" id="tab-btn-dl" onclick="switchTab('downloads')">Downloads (<span id="dl-count-badge">0</span>)</button>
    <button class="tab" id="tab-btn-torrents" onclick="switchTab('torrents')">Torrents (<span id="torrent-count-badge">0</span>)</button>
    <button class="tab" id="tab-btn-files" onclick="switchTab('files')">Completed Files</button>
  </div>

  <div id="downloads-container"></div>
  <div id="torrents-container" style="display:none;"></div>
  <div id="files-container" style="display:none;"></div>

  <script>
    let activeTab = 'downloads';

    function formatBytes(bytes) {
      if (!bytes || bytes <= 0) return '0 B';
      const k = 1024;
      const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    function formatSpeed(bytesPerSec) {
      return formatBytes(bytesPerSec) + '/s';
    }

    function switchTab(tab) {
      activeTab = tab;
      document.querySelectorAll('.tab').forEach(b => b.classList.remove('active'));
      document.getElementById('tab-btn-' + tab).classList.add('active');
      document.getElementById('downloads-container').style.display = tab === 'downloads' ? 'block' : 'none';
      document.getElementById('torrents-container').style.display = tab === 'torrents' ? 'block' : 'none';
      document.getElementById('files-container').style.display = tab === 'files' ? 'block' : 'none';
      if (tab === 'files') loadFiles();
    }

    async function fetchStatus() {
      try {
        const res = await fetch('/api/status');
        const data = await res.json();
        document.getElementById('usb-status').textContent = data.usbHddConnected ? data.usbHddName : 'Internal Only';
        document.getElementById('transfers-count').textContent = (data.activeDownloads + data.activeTorrents);
      } catch (e) {
        console.error(e);
      }
    }

    async function fetchDownloads() {
      try {
        const res = await fetch('/api/downloads');
        const list = await res.json();
        document.getElementById('dl-count-badge').textContent = list.length;
        const container = document.getElementById('downloads-container');
        if (list.length === 0) {
          container.innerHTML = '<div class="empty-state">No downloads active. Paste a URL above to start downloading directly to your TV.</div>';
          return;
        }
        container.innerHTML = list.map(function(d) {
          const percent = d.totalBytes > 0 ? Math.min(100, Math.round((d.downloadedBytes / d.totalBytes) * 100)) : 0;
          let html = '<div class="item-card">';
          html += '<div class="item-header">';
          html += '<div class="item-title">' + d.fileName + '</div>';
          html += '<span class="status-tag status-' + d.status + '">' + d.status + '</span>';
          html += '</div>';
          html += '<div class="progress-bar-bg">';
          html += '<div class="progress-bar-fill" style="width: ' + percent + '%;"></div>';
          html += '</div>';
          html += '<div class="item-details">';
          html += '<span>' + percent + '% • ' + formatBytes(d.downloadedBytes) + ' / ' + (d.totalBytes > 0 ? formatBytes(d.totalBytes) : 'Unknown') + '</span>';
          html += '<span>' + (d.status === 'DOWNLOADING' ? formatSpeed(d.speedBytesPerSec) : '') + '</span>';
          html += '</div>';
          html += '<div class="item-actions">';
          if (d.status === 'DOWNLOADING') {
            html += '<button class="btn btn-sm btn-pause" onclick="pauseDownload(\'' + d.id + '\')">Pause</button>';
          }
          if (d.status === 'PAUSED') {
            html += '<button class="btn btn-sm btn-resume" onclick="resumeDownload(\'' + d.id + '\')">Resume</button>';
          }
          html += '<button class="btn btn-sm btn-danger" onclick="deleteDownload(\'' + d.id + '\')">Cancel & Delete</button>';
          html += '</div></div>';
          return html;
        }).join('');
      } catch (e) {
        console.error(e);
      }
    }

    async function fetchTorrents() {
      try {
        const res = await fetch('/api/torrents');
        const list = await res.json();
        document.getElementById('torrent-count-badge').textContent = list.length;
        const container = document.getElementById('torrents-container');
        if (list.length === 0) {
          container.innerHTML = '<div class="empty-state">No active torrents. Enter a magnet link above to download.</div>';
          return;
        }
        container.innerHTML = list.map(function(t) {
          const percent = Math.min(100, Math.round((t.progress || 0) * 100));
          let html = '<div class="item-card">';
          html += '<div class="item-header">';
          html += '<div class="item-title">' + t.name + '</div>';
          html += '<span class="status-tag status-' + t.status + '">' + t.status + '</span>';
          html += '</div>';
          html += '<div class="progress-bar-bg">';
          html += '<div class="progress-bar-fill" style="width: ' + percent + '%;' + (t.status === 'SEEDING' ? ' background: #a855f7;' : '') + '"></div>';
          html += '</div>';
          html += '<div class="item-details">';
          html += '<span>' + percent + '% • ↓ ' + formatSpeed(t.downloadSpeed) + ' • ↑ ' + formatSpeed(t.uploadSpeed) + '</span>';
          html += '<span>Peers: ' + t.peers + ' • Seeds: ' + t.seeds + '</span>';
          html += '</div>';
          html += '<div class="item-actions">';
          if (t.status === 'DOWNLOADING' || t.status === 'SEEDING') {
            html += '<button class="btn btn-sm btn-pause" onclick="pauseTorrent(\'' + t.id + '\')">Pause</button>';
          }
          if (t.status === 'PAUSED') {
            html += '<button class="btn btn-sm btn-resume" onclick="resumeTorrent(\'' + t.id + '\')">Resume</button>';
          }
          html += '<button class="btn btn-sm btn-danger" onclick="deleteTorrent(\'' + t.id + '\')">Remove</button>';
          html += '</div></div>';
          return html;
        }).join('');
      } catch (e) {
        console.error(e);
      }
    }

    async function loadFiles() {
      try {
        const res = await fetch('/api/files');
        const list = await res.json();
        const container = document.getElementById('files-container');
        if (list.length === 0) {
          container.innerHTML = '<div class="empty-state">No completed files found in download folder.</div>';
          return;
        }
        container.innerHTML = list.map(function(f) {
          let html = '<div class="item-card" style="display:flex; justify-content:space-between; align-items:center;">';
          html += '<div>';
          html += '<div style="font-weight:600; font-size:0.9rem;">' + f.name + '</div>';
          html += '<div style="font-size:0.75rem; color:var(--text-muted);">' + formatBytes(f.size) + ' • ' + f.category + '</div>';
          html += '</div>';
          html += '<span class="status-tag status-COMPLETED">ON TV</span>';
          html += '</div>';
          return html;
        }).join('');
      } catch (e) {
        console.error(e);
      }
    }

    async function submitDirectDownload() {
      const urlInput = document.getElementById('dl-url');
      const filenameInput = document.getElementById('dl-filename');
      const destInput = document.getElementById('dl-dest');
      const url = urlInput.value.trim();
      if (!url) { alert('Please enter a URL'); return; }

      try {
        const res = await fetch('/api/downloads', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url: url, fileName: filenameInput.value.trim(), destination: destInput.value })
        });
        const data = await res.json();
        if (data.success) {
          urlInput.value = '';
          filenameInput.value = '';
          alert('Download started on your TV!');
          fetchDownloads();
        } else {
          alert('Failed: ' + (data.error || 'Server error'));
        }
      } catch (e) {
        alert('Connection error');
      }
    }

    async function submitMagnet() {
      const magnetInput = document.getElementById('magnet-url');
      const magnet = magnetInput.value.trim();
      if (!magnet.startsWith('magnet:')) { alert('Please enter a valid magnet: URI'); return; }

      try {
        const res = await fetch('/api/torrents', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ magnet: magnet })
        });
        const data = await res.json();
        if (data.success) {
          magnetInput.value = '';
          alert('Torrent started on your TV!');
          switchTab('torrents');
          fetchTorrents();
        } else {
          alert('Failed: ' + (data.error || 'Server error'));
        }
      } catch (e) {
        alert('Connection error');
      }
    }

    async function pauseDownload(id) {
      await fetch('/api/downloads/' + id + '/pause', { method: 'POST' });
      fetchDownloads();
    }
    async function resumeDownload(id) {
      await fetch('/api/downloads/' + id + '/resume', { method: 'POST' });
      fetchDownloads();
    }
    async function deleteDownload(id) {
      if (confirm('Delete this download?')) {
        await fetch('/api/downloads/' + id, { method: 'DELETE' });
        fetchDownloads();
      }
    }

    async function pauseTorrent(id) {
      await fetch('/api/torrents/' + id + '/pause', { method: 'POST' });
      fetchTorrents();
    }
    async function resumeTorrent(id) {
      await fetch('/api/torrents/' + id + '/resume', { method: 'POST' });
      fetchTorrents();
    }
    async function deleteTorrent(id) {
      if (confirm('Remove this torrent?')) {
        await fetch('/api/torrents/' + id, { method: 'DELETE' });
        fetchTorrents();
      }
    }

    fetchStatus();
    fetchDownloads();
    fetchTorrents();
    setInterval(() => {
      fetchStatus();
      if (activeTab === 'downloads') fetchDownloads();
      if (activeTab === 'torrents') fetchTorrents();
    }, 2000);
  </script>
</body>
</html>
    """.trimIndent()
  }
}
