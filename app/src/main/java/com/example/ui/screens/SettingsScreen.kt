package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.AppPreferences
import com.example.ui.components.TvButton
import com.example.ui.components.TvHeader
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  preferences: AppPreferences,
  usbConnected: Boolean,
  usbName: String,
  onSelectUsbHdd: () -> Unit,
  onBackToHome: () -> Unit
) {
  val scope = rememberCoroutineScope()
  val defaultDest by preferences.defaultDestination.collectAsState(initial = "INTERNAL")
  val usbTreeUri by preferences.usbHddTreeUri.collectAsState(initial = null)
  val serverPort by preferences.webServerPort.collectAsState(initial = 8765)
  val serverEnabled by preferences.webServerEnabled.collectAsState(initial = true)
  val seedingRatio by preferences.seedingRatioTarget.collectAsState(initial = 1.5f)
  val autoResume by preferences.autoResumePlayback.collectAsState(initial = true)

  BackHandler {
    onBackToHome()
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    TvHeader(
      title = "System Settings",
      subtitle = "Storage destinations, USB HDD, seeding rules and server preferences",
      onBack = onBackToHome,
      usbConnected = usbConnected,
      usbName = usbName
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      contentPadding = PaddingValues(bottom = 32.dp)
    ) {
      // Storage Section
      item {
        SettingsCard(
          title = "Storage & USB HDD",
          icon = Icons.Default.Usb
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("Default Download Destination", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                  "Where direct downloads and torrents are saved by default",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(
                  text = "Internal Storage",
                  isPrimary = defaultDest == "INTERNAL",
                  onClick = { scope.launch { preferences.setDefaultDestination("INTERNAL") } }
                )
                TvButton(
                  text = if (usbConnected) "USB HDD ($usbName)" else "USB HDD (Offline)",
                  isPrimary = defaultDest == "USB_HDD",
                  onClick = { scope.launch { preferences.setDefaultDestination("USB_HDD") } }
                )
              }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text("1TB USB HDD Folder Linking (SAF)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                  if (usbTreeUri != null) "Linked URI: $usbTreeUri" else "No USB HDD folder linked yet",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton(
                  text = if (usbTreeUri == null) "Link USB Drive" else "Change USB Folder",
                  icon = Icons.Default.Usb,
                  isPrimary = true,
                  onClick = onSelectUsbHdd
                )
                if (usbTreeUri != null) {
                  TvButton(
                    text = "Unlink",
                    icon = Icons.Default.Delete,
                    danger = true,
                    onClick = { scope.launch { preferences.setUsbHddTreeUri(null) } }
                  )
                }
              }
            }
          }
        }
      }

      // Torrents & Seeding Section
      item {
        SettingsCard(
          title = "Torrent & Persistent Seeding",
          icon = Icons.Default.Upload
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Seeding Ratio Target", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              Text(
                "Keeps seeding in background until upload reaches ratio",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf(1.0f, 1.5f, 2.0f, 0.0f).forEach { ratio ->
                val label = if (ratio == 0.0f) "Unlimited" else "${ratio}x"
                TvButton(
                  text = label,
                  isPrimary = seedingRatio == ratio,
                  onClick = { scope.launch { preferences.setSeedingRatioTarget(ratio) } }
                )
              }
            }
          }
        }
      }

      // Media Player Section
      item {
        SettingsCard(
          title = "Media Player",
          icon = Icons.Default.PlayArrow
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Auto-Resume Playback", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              Text(
                "Automatically resume videos from the last watched position",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = autoResume,
              onCheckedChange = { scope.launch { preferences.setAutoResumePlayback(it) } }
            )
          }
        }
      }

      // Target Hardware Info Section
      item {
        SettingsCard(
          title = "Device Target & Specifications",
          icon = Icons.Default.Tv
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Target Hardware: Walton Android TV (Android 11, 4 GB RAM)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Display: 4K UHD Optimized • D-Pad Remote Navigation", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Storage: 1 TB External USB HDD via Storage Access Framework", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Engines: Media3 ExoPlayer, WorkManager, Coroutines HTTP Resume, Persistent Seeding", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsCard(
  title: String,
  icon: ImageVector,
  content: @Composable () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
      Spacer(modifier = Modifier.height(14.dp))
      content()
    }
  }
}
