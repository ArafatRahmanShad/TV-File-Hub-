package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.TorrentEntity
import com.example.storage.StorageManager
import com.example.torrent.TorrentManager
import com.example.ui.components.TvButton
import com.example.ui.components.TvFocusableCard
import com.example.ui.components.TvHeader
import kotlinx.coroutines.launch

@Composable
fun TorrentScreen(
  torrentManager: TorrentManager,
  usbConnected: Boolean,
  usbName: String,
  onBackToHome: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val torrents by torrentManager.allTorrents.collectAsState(initial = emptyList())
  var showAddMagnetDialog by remember { mutableStateOf(false) }
  var magnetInputText by remember { mutableStateOf("") }
  var targetDest by remember { mutableStateOf(if (usbConnected) "USB_HDD" else "INTERNAL") }

  BackHandler {
    onBackToHome()
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    TvHeader(
      title = "Torrents & Persistent Seeding",
      subtitle = "BitTorrent Engine with continuous background seeding",
      onBack = onBackToHome,
      usbConnected = usbConnected,
      usbName = usbName
    )

    // Top action row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Seeding info chip
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF6B21A8).copy(alpha = 0.2f),
        border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Upload,
            contentDescription = null,
            tint = Color(0xFFA855F7),
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Persistent Seeding: ON (Survives TV Sleep/Reboot)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD8B4FE)
          )
        }
      }

      TvButton(
        text = "Add Magnet Link",
        icon = Icons.Default.Add,
        isPrimary = true,
        onClick = {
          magnetInputText = ""
          showAddMagnetDialog = true
        }
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(bottom = 32.dp)
    ) {
      if (torrents.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(48.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "No torrents active.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
              )
              Spacer(modifier = Modifier.height(12.dp))
              TvButton(
                text = "Add Magnet Link",
                icon = Icons.Default.Add,
                onClick = { showAddMagnetDialog = true }
              )
            }
          }
        }
      }

      items(torrents, key = { it.id }) { item ->
        TorrentItemCard(
          item = item,
          onPause = { torrentManager.pauseTorrent(item.id) },
          onResume = { torrentManager.resumeTorrent(item.id) },
          onDelete = { torrentManager.deleteTorrent(item.id) }
        )
      }
    }
  }

  // Add Magnet Dialog
  if (showAddMagnetDialog) {
    AlertDialog(
      onDismissRequest = { showAddMagnetDialog = false },
      title = { Text("Add Magnet Link") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = magnetInputText,
            onValueChange = { magnetInputText = it },
            label = { Text("Magnet URI") },
            placeholder = { Text("magnet:?xt=urn:btih:...") },
            singleLine = false,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth()
          )

          Text("Destination:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvButton(
              text = if (usbConnected) "USB HDD ($usbName)" else "USB HDD (Offline)",
              isPrimary = targetDest == "USB_HDD",
              onClick = { if (usbConnected) targetDest = "USB_HDD" }
            )
            TvButton(
              text = "Internal Storage",
              isPrimary = targetDest == "INTERNAL",
              onClick = { targetDest = "INTERNAL" }
            )
          }
        }
      },
      confirmButton = {
        TvButton(
          text = "Add Torrent",
          isPrimary = true,
          onClick = {
            if (magnetInputText.isNotBlank()) {
              scope.launch {
                torrentManager.addMagnet(magnetInputText.trim(), targetDest)
                showAddMagnetDialog = false
              }
            }
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showAddMagnetDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun TorrentItemCard(
  item: TorrentEntity,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onDelete: () -> Unit
) {
  val isSeeding = item.status == "SEEDING"
  val statusColor = when (item.status) {
    "DOWNLOADING" -> Color(0xFF3B82F6)
    "SEEDING" -> Color(0xFFA855F7)
    "PAUSED" -> Color(0xFFF59E0B)
    "COMPLETED" -> Color(0xFF10B981)
    else -> Color(0xFF64748B)
  }

  val percent = (item.progress * 100f).coerceIn(0f, 100f)

  TvFocusableCard(
    onClick = {
      if (item.status == "DOWNLOADING" || item.status == "SEEDING") onPause()
      else onResume()
    },
    shapeRadius = 12.dp,
    defaultBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    focusedBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
    focusedBorderColor = statusColor
  ) { isFocused ->
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Storage: ${item.destinationType} • Ratio: ${String.format("%.2f", item.ratio)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = statusColor.copy(alpha = 0.2f)
        ) {
          Text(
            text = item.status,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      LinearProgressIndicator(
        progress = { if (isSeeding) 1.0f else item.progress },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp),
        color = statusColor,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${String.format("%.1f", percent)}% • ${StorageManager.formatSize(item.downloadedBytes)} / ${StorageManager.formatSize(item.totalBytes)}",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          if (item.downloadSpeed > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(2.dp))
              Text(StorageManager.formatSpeed(item.downloadSpeed), fontSize = 12.sp, color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
            }
          }
          if (item.uploadSpeed > 0 || isSeeding) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFFC084FC), modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(2.dp))
              Text(StorageManager.formatSpeed(item.uploadSpeed), fontSize = 12.sp, color = Color(0xFFC084FC), fontWeight = FontWeight.Bold)
            }
          }
          Text(
            text = "P: ${item.peers} • S: ${item.seeds}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Action row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        if (item.status == "DOWNLOADING" || item.status == "SEEDING") {
          TvButton(
            text = "Pause",
            icon = Icons.Default.Pause,
            onClick = onPause
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
        if (item.status == "PAUSED") {
          TvButton(
            text = "Resume",
            icon = Icons.Default.PlayArrow,
            isPrimary = true,
            onClick = onResume
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
        TvButton(
          text = "Remove",
          icon = Icons.Default.Delete,
          danger = true,
          onClick = onDelete
        )
      }
    }
  }
}
