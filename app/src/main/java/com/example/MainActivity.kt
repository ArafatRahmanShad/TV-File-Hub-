package com.example

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.preferences.AppPreferences
import com.example.download.DownloadManager
import com.example.storage.UsbHddManager
import com.example.torrent.TorrentManager
import com.example.torrent.TorrentService
import com.example.ui.screens.DirectDownloadDialog
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TorrentScreen
import com.example.ui.screens.WebServerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.web.WebServerService
import kotlinx.coroutines.launch

enum class Screen {
  HOME,
  INTERNAL_FILES,
  USB_FILES,
  DOWNLOADS,
  TORRENTS,
  WEB_SERVER,
  SETTINGS,
  MEDIA_PLAYER
}

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Start background torrent engine and web server services
    try {
      TorrentService.startService(this)
      WebServerService.startService(this, 8765)
    } catch (e: Exception) {
      e.printStackTrace()
    }

    setContent {
      MyApplicationTheme {
        TvFileHubApp()
      }
    }
  }
}

@Composable
fun TvFileHubApp() {
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()

  val preferences = remember { AppPreferences(context) }
  val downloadManager = remember { DownloadManager(context) }
  val torrentManager = remember { TorrentManager.getInstance(context) }
  val webServer = remember { WebServerService.getServer(context) }

  val savedUsbUri by preferences.usbHddTreeUri.collectAsState(initial = null)
  val serverPort by preferences.webServerPort.collectAsState(initial = 8765)
  val serverEnabled by preferences.webServerEnabled.collectAsState(initial = true)

  var usbStatus by remember { mutableStateOf<UsbHddManager.UsbStatus>(UsbHddManager.UsbStatus.NotConfigured) }

  LaunchedEffect(savedUsbUri) {
    usbStatus = UsbHddManager.checkStatus(context, savedUsbUri)
  }

  val usbConnected = usbStatus is UsbHddManager.UsbStatus.Connected
  val usbName = if (usbStatus is UsbHddManager.UsbStatus.Connected) {
    (usbStatus as UsbHddManager.UsbStatus.Connected).name
  } else {
    "USB Drive"
  }

  // Active counts
  val allDownloads by downloadManager.allDownloads.collectAsState(initial = emptyList())
  val allTorrents by torrentManager.allTorrents.collectAsState(initial = emptyList())

  val activeDownloadsCount = allDownloads.count { it.status == "DOWNLOADING" || it.status == "QUEUED" }
  val activeTorrentsCount = allTorrents.count { it.status == "DOWNLOADING" || it.status == "SEEDING" }

  var currentScreen by remember { mutableStateOf(Screen.HOME) }
  var playerMediaUri by remember { mutableStateOf<Uri?>(null) }
  var playerMediaTitle by remember { mutableStateOf("") }
  var showDirectDownloadDialog by remember { mutableStateOf(false) }

  // SAF Folder Picker launcher for USB HDD
  val openDocumentTreeLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        UsbHddManager.takePersistablePermission(context, uri)
        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
        val name = doc?.name ?: "USB Drive"
        preferences.setUsbHddTreeUri(uri.toString(), name)
        usbStatus = UsbHddManager.checkStatus(context, uri.toString())
      }
    }
  }

  Surface(
    modifier = Modifier
      .fillMaxSize()
      .safeDrawingPadding(),
    color = MaterialTheme.colorScheme.background
  ) {
    when (currentScreen) {
      Screen.HOME -> {
        HomeScreen(
          usbConnected = usbConnected,
          usbName = usbName,
          serverRunning = webServer.isRunning,
          serverIp = webServer.getLocalIpAddress(),
          activeDownloadsCount = activeDownloadsCount,
          activeTorrentsCount = activeTorrentsCount,
          onNavigateToInternalFiles = { currentScreen = Screen.INTERNAL_FILES },
          onNavigateToUsbFiles = { currentScreen = Screen.USB_FILES },
          onNavigateToDownloads = { currentScreen = Screen.DOWNLOADS },
          onNavigateToDirectDownload = { showDirectDownloadDialog = true },
          onNavigateToTorrents = { currentScreen = Screen.TORRENTS },
          onNavigateToWebServer = { currentScreen = Screen.WEB_SERVER },
          onNavigateToSettings = { currentScreen = Screen.SETTINGS }
        )
      }

      Screen.INTERNAL_FILES -> {
        FileManagerScreen(
          initialIsUsb = false,
          usbTreeUri = savedUsbUri,
          usbConnected = usbConnected,
          usbName = usbName,
          onBackToHome = { currentScreen = Screen.HOME },
          onPlayMedia = { uri, title ->
            playerMediaUri = uri
            playerMediaTitle = title
            currentScreen = Screen.MEDIA_PLAYER
          },
          onSelectUsbStorage = { openDocumentTreeLauncher.launch(null) }
        )
      }

      Screen.USB_FILES -> {
        FileManagerScreen(
          initialIsUsb = true,
          usbTreeUri = savedUsbUri,
          usbConnected = usbConnected,
          usbName = usbName,
          onBackToHome = { currentScreen = Screen.HOME },
          onPlayMedia = { uri, title ->
            playerMediaUri = uri
            playerMediaTitle = title
            currentScreen = Screen.MEDIA_PLAYER
          },
          onSelectUsbStorage = { openDocumentTreeLauncher.launch(null) }
        )
      }

      Screen.DOWNLOADS -> {
        DownloadsScreen(
          downloadManager = downloadManager,
          usbConnected = usbConnected,
          usbName = usbName,
          onBackToHome = { currentScreen = Screen.HOME },
          onOpenDirectDownloadDialog = { showDirectDownloadDialog = true },
          onPlayDownloadedMedia = { uri, title ->
            playerMediaUri = uri
            playerMediaTitle = title
            currentScreen = Screen.MEDIA_PLAYER
          }
        )
      }

      Screen.TORRENTS -> {
        TorrentScreen(
          torrentManager = torrentManager,
          usbConnected = usbConnected,
          usbName = usbName,
          onBackToHome = { currentScreen = Screen.HOME }
        )
      }

      Screen.WEB_SERVER -> {
        WebServerScreen(
          serverRunning = webServer.isRunning,
          serverIp = webServer.getLocalIpAddress(),
          serverPort = serverPort,
          usbConnected = usbConnected,
          usbName = usbName,
          onToggleServer = { shouldRun ->
            if (shouldRun) {
              WebServerService.startService(context, serverPort)
            } else {
              WebServerService.stopService(context)
            }
          },
          onBackToHome = { currentScreen = Screen.HOME }
        )
      }

      Screen.SETTINGS -> {
        SettingsScreen(
          preferences = preferences,
          usbConnected = usbConnected,
          usbName = usbName,
          onSelectUsbHdd = { openDocumentTreeLauncher.launch(null) },
          onBackToHome = { currentScreen = Screen.HOME }
        )
      }

      Screen.MEDIA_PLAYER -> {
        if (playerMediaUri != null) {
          PlayerScreen(
            mediaUri = playerMediaUri!!,
            title = playerMediaTitle,
            onExitPlayer = {
              currentScreen = Screen.HOME
            }
          )
        } else {
          currentScreen = Screen.HOME
        }
      }
    }

    if (showDirectDownloadDialog) {
      DirectDownloadDialog(
        usbConnected = usbConnected,
        usbName = usbName,
        onDismiss = { showDirectDownloadDialog = false },
        onStartDownload = { url, fileName, destination ->
          scope.launch {
            downloadManager.enqueueDownload(url, fileName, destination)
            currentScreen = Screen.DOWNLOADS
          }
        }
      )
    }
  }
}
