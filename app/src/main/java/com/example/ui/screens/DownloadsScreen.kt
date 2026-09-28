package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.DownloadEntity
import com.example.download.DownloadManager
import com.example.storage.StorageManager
import com.example.ui.components.TvButton
import com.example.ui.components.TvFocusableCard
import com.example.ui.components.TvHeader
import java.io.File

@Composable
fun DownloadsScreen(
  downloadManager: DownloadManager,
  usbConnected: Boolean,
  usbName: String,
  onBackToHome: () -> Unit,
  onOpenDirectDownloadDialog: () -> Unit,
  onPlayDownloadedMedia: (Uri, String) -> Unit
) {
  val context = LocalContext.current
  val downloads by downloadManager.allDownloads.collectAsState(initial = emptyList())

  BackHandler {
    onBackToHome()
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    TvHeader(
      title = "Downloads Manager",
      subtitle = "Active, Queued and Completed Transfers",
      onBack = onBackToHome,
      usbConnected = usbConnected,
      usbName = usbName
    )

    // Top action
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "${downloads.size} Total Items",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )

      TvButton(
        text = "Add Download URL",
        icon = Icons.Default.Add,
        isPrimary = true,
        onClick = onOpenDirectDownloadDialog
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
      if (downloads.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(48.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "No downloads yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
              )
              Spacer(modifier = Modifier.height(12.dp))
              TvButton(
                text = "Add Direct Download",
                icon = Icons.Default.Add,
                onClick = onOpenDirectDownloadDialog
              )
            }
          }
        }
      }

      items(downloads, key = { it.id }) { item ->
        DownloadItemCard(
          item = item,
          onPause = { downloadManager.pauseDownload(item.id) },
          onResume = { downloadManager.resumeDownload(item.id) },
          onRetry = { downloadManager.retryDownload(item.id) },
          onDelete = { downloadManager.deleteDownload(item.id, removeFile = true) },
          onPlay = {
            val downloadDir = StorageManager.getDefaultDownloadDirectory(context)
            val file = File(downloadDir, item.fileName)
            if (file.exists()) {
              onPlayDownloadedMedia(Uri.fromFile(file), item.fileName)
            }
          }
        )
      }
    }
  }
}

@Composable
private fun DownloadItemCard(
  item: DownloadEntity,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onRetry: () -> Unit,
  onDelete: () -> Unit,
  onPlay: () -> Unit
) {
  val percent = if (item.totalBytes > 0) {
    ((item.downloadedBytes.toFloat() / item.totalBytes.toFloat()) * 100f).coerceIn(0f, 100f)
  } else 0f

  val statusColor = when (item.status) {
    "DOWNLOADING" -> Color(0xFF3B82F6)
    "COMPLETED" -> Color(0xFF10B981)
    "PAUSED" -> Color(0xFFF59E0B)
    "FAILED" -> Color(0xFFEF4444)
    "QUEUED" -> Color(0xFF8B5CF6)
    else -> Color(0xFF64748B)
  }

  TvFocusableCard(
    onClick = {
      if (item.status == "COMPLETED") onPlay()
      else if (item.status == "DOWNLOADING") onPause()
      else if (item.status == "PAUSED") onResume()
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
            text = item.fileName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Destination: ${item.destinationType} • ${item.url}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
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

      // Progress bar
      LinearProgressIndicator(
        progress = { if (item.status == "COMPLETED") 1.0f else (percent / 100f) },
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
        val etaStr = if (item.etaSeconds > 0 && item.status == "DOWNLOADING") {
          val mins = item.etaSeconds / 60
          val secs = item.etaSeconds % 60
          "ETA: ${mins}m ${secs}s"
        } else ""

        val totalStr = if (item.totalBytes > 0) StorageManager.formatSize(item.totalBytes) else "Unknown"
        Text(
          text = "${String.format("%.1f", percent)}% • ${StorageManager.formatSize(item.downloadedBytes)} / $totalStr  $etaStr",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (item.status == "DOWNLOADING") {
          Text(
            text = StorageManager.formatSpeed(item.speedBytesPerSec),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }

      if (!item.errorMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Error: ${item.errorMessage}",
          color = MaterialTheme.colorScheme.error,
          fontSize = 11.sp
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
      ) {
        if (item.status == "COMPLETED") {
          TvButton(
            text = "Play",
            icon = Icons.Default.PlayArrow,
            isPrimary = true,
            onClick = onPlay
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
        if (item.status == "DOWNLOADING") {
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
        if (item.status == "FAILED") {
          TvButton(
            text = "Retry",
            icon = Icons.Default.Refresh,
            isPrimary = true,
            onClick = onRetry
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
        TvButton(
          text = "Delete",
          icon = Icons.Default.Delete,
          danger = true,
          onClick = onDelete
        )
      }
    }
  }
}
