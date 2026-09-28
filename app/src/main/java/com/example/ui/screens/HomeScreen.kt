package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.TvFocusableCard
import com.example.ui.components.TvHeader

data class HubMenuItem(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val accentColor: Color,
  val badgeText: String? = null,
  val onClick: () -> Unit
)

@Composable
fun HomeScreen(
  usbConnected: Boolean,
  usbName: String,
  serverRunning: Boolean,
  serverIp: String,
  activeDownloadsCount: Int,
  activeTorrentsCount: Int,
  onNavigateToInternalFiles: () -> Unit,
  onNavigateToUsbFiles: () -> Unit,
  onNavigateToDownloads: () -> Unit,
  onNavigateToDirectDownload: () -> Unit,
  onNavigateToTorrents: () -> Unit,
  onNavigateToWebServer: () -> Unit,
  onNavigateToSettings: () -> Unit
) {
  val menuItems = listOf(
    HubMenuItem(
      title = "Internal Storage",
      subtitle = "Browse Movies, Series & Downloads",
      icon = Icons.Default.Folder,
      accentColor = Color(0xFF3B82F6),
      onClick = onNavigateToInternalFiles
    ),
    HubMenuItem(
      title = "USB Storage / HDD",
      subtitle = if (usbConnected) usbName else "Connect or Link 1TB External HDD",
      icon = Icons.Default.Usb,
      accentColor = Color(0xFF10B981),
      badgeText = if (usbConnected) "READY" else "OFFLINE",
      onClick = onNavigateToUsbFiles
    ),
    HubMenuItem(
      title = "Downloads Hub",
      subtitle = "Direct file transfers & progress",
      icon = Icons.Default.Download,
      accentColor = Color(0xFF6366F1),
      badgeText = if (activeDownloadsCount > 0) "$activeDownloadsCount ACTIVE" else null,
      onClick = onNavigateToDownloads
    ),
    HubMenuItem(
      title = "Direct URL Download",
      subtitle = "Download files directly to TV/HDD",
      icon = Icons.Default.Link,
      accentColor = Color(0xFFEC4899),
      onClick = onNavigateToDirectDownload
    ),
    HubMenuItem(
      title = "Torrents & Magnets",
      subtitle = "Persistent download & seeding",
      icon = Icons.Default.CloudDownload,
      accentColor = Color(0xFF8B5CF6),
      badgeText = if (activeTorrentsCount > 0) "$activeTorrentsCount RUNNING" else null,
      onClick = onNavigateToTorrents
    ),
    HubMenuItem(
      title = "Phone Remote & Web",
      subtitle = if (serverRunning && serverIp.isNotBlank()) "http://$serverIp:8765" else "Start Phone Remote Web Server",
      icon = Icons.Default.Dns,
      accentColor = Color(0xFFF59E0B),
      badgeText = if (serverRunning) "ONLINE" else "STOPPED",
      onClick = onNavigateToWebServer
    ),
    HubMenuItem(
      title = "Settings",
      subtitle = "HDD path, limits & preferences",
      icon = Icons.Default.Settings,
      accentColor = Color(0xFF64748B),
      onClick = onNavigateToSettings
    )
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    TvHeader(
      title = "TV File Hub",
      subtitle = "Personal Media, Download & Torrent Server",
      usbConnected = usbConnected,
      usbName = usbName,
      serverRunning = serverRunning,
      serverIp = serverIp
    )

    // Quick Stats Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      QuickInfoBanner(
        title = "Downloads",
        info = "$activeDownloadsCount active task(s)",
        color = Color(0xFF6366F1),
        modifier = Modifier.weight(1f)
      )
      QuickInfoBanner(
        title = "Torrents & Seeding",
        info = "$activeTorrentsCount running",
        color = Color(0xFF8B5CF6),
        modifier = Modifier.weight(1f)
      )
      QuickInfoBanner(
        title = "Phone Remote URL",
        info = if (serverRunning && serverIp.isNotBlank()) "http://$serverIp:8765" else "Offline (Click Web Server)",
        color = if (serverRunning) Color(0xFF10B981) else Color(0xFF94A3B8),
        modifier = Modifier.weight(1.2f)
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Main 3-column / 4-column TV grid
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 280.dp),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier.fillMaxSize()
    ) {
      items(menuItems.size) { index ->
        val item = menuItems[index]
        TvFocusableCard(
          onClick = item.onClick,
          shapeRadius = 14.dp,
          defaultBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          focusedBackgroundColor = item.accentColor.copy(alpha = 0.25f),
          focusedBorderColor = item.accentColor
        ) { isFocused ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isFocused) item.accentColor else item.accentColor.copy(alpha = 0.2f),
              modifier = Modifier.size(54.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = item.icon,
                  contentDescription = null,
                  tint = if (isFocused) Color.White else item.accentColor,
                  modifier = Modifier.size(28.dp)
                )
              }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = if (isFocused) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface
                )
                if (item.badgeText != null) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = item.accentColor.copy(alpha = 0.2f)
                  ) {
                    Text(
                      text = item.badgeText,
                      color = item.accentColor,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun QuickInfoBanner(
  title: String,
  info: String,
  color: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .background(color, RoundedCornerShape(4.dp))
      )
      Spacer(modifier = Modifier.width(10.dp))
      Column {
        Text(
          text = title,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.outline
        )
        Text(
          text = info,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }
  }
}
