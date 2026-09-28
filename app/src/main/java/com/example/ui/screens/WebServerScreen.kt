package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.TvButton
import com.example.ui.components.TvHeader

@Composable
fun WebServerScreen(
  serverRunning: Boolean,
  serverIp: String,
  serverPort: Int,
  usbConnected: Boolean,
  usbName: String,
  onToggleServer: (Boolean) -> Unit,
  onBackToHome: () -> Unit
) {
  BackHandler {
    onBackToHome()
  }

  val fullUrl = "http://$serverIp:$serverPort"

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    TvHeader(
      title = "Phone Remote & Web Server",
      subtitle = "Send direct URLs and manage downloads from any phone browser on your Wi-Fi",
      onBack = onBackToHome,
      usbConnected = usbConnected,
      usbName = usbName,
      serverRunning = serverRunning,
      serverIp = serverIp
    )

    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      // Left Column: Server Status & Big URL Card
      Column(
        modifier = Modifier.weight(1.2f),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          border = BorderStroke(
            2.dp,
            if (serverRunning) Color(0xFF10B981) else Color(0xFFEF4444)
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(24.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                  shape = CircleShape,
                  color = if (serverRunning) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                  modifier = Modifier.size(42.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = if (serverRunning) Icons.Default.Dns else Icons.Default.NetworkCheck,
                      contentDescription = null,
                      tint = if (serverRunning) Color(0xFF34D399) else Color(0xFFFCA5A5),
                      modifier = Modifier.size(22.dp)
                    )
                  }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                  Text(
                    text = if (serverRunning) "Server Running" else "Server Stopped",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = if (serverRunning) "Listening on port $serverPort" else "Disabled in Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              TvButton(
                text = if (serverRunning) "Stop Server" else "Start Server",
                icon = if (serverRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                danger = serverRunning,
                isPrimary = !serverRunning,
                onClick = { onToggleServer(!serverRunning) }
              )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
              text = "PHONE BROWSER URL:",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Giant TV readable URL Box
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color.Black.copy(alpha = 0.4f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = if (serverRunning) fullUrl else "http://[TV_IP]:$serverPort (Stopped)",
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (serverRunning) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
              )
            }
          }
        }

        // Live capabilities
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            ServerFeatureBadge("REST API", "Full JSON")
            ServerFeatureBadge("Web UI", "Responsive Touch")
            ServerFeatureBadge("Security", "LAN Only / No CORS Leak")
            ServerFeatureBadge("Path Shield", "Traversal Guard")
          }
        }
      }

      // Right Column: Step by step instructions for TV users
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = "How to connect your phone:",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        InstructionStep(
          number = "1",
          title = "Same Wi-Fi Network",
          description = "Make sure your phone and Walton Android TV are connected to the same Wi-Fi router."
        )

        InstructionStep(
          number = "2",
          title = "Open Phone Browser",
          description = "Launch Chrome, Safari, or Samsung Internet on your phone."
        )

        InstructionStep(
          number = "3",
          title = "Enter TV Address",
          description = "Type $fullUrl into your phone's address bar."
        )

        InstructionStep(
          number = "4",
          title = "Paste & Download",
          description = "Paste any download link or magnet on your phone. The TV will download it directly to the USB HDD!"
        )
      }
    }
  }
}

@Composable
private fun ServerFeatureBadge(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      fontWeight = FontWeight.Bold,
      fontSize = 13.sp,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = label,
      fontSize = 11.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun InstructionStep(
  number: String,
  title: String,
  description: String
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.Top
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(28.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            text = number,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
        }
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column {
        Text(
          text = title,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = description,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}
