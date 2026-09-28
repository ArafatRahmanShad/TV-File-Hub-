package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.TvButton

@Composable
fun DirectDownloadDialog(
  usbConnected: Boolean,
  usbName: String,
  onDismiss: () -> Unit,
  onStartDownload: (url: String, fileName: String?, destination: String) -> Unit
) {
  var urlText by remember { mutableStateOf("") }
  var fileNameText by remember { mutableStateOf("") }
  var selectedDest by remember { mutableStateOf(if (usbConnected) "USB_HDD" else "INTERNAL") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Direct URL Download") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = urlText,
          onValueChange = { urlText = it },
          label = { Text("Download URL (HTTP/HTTPS)") },
          placeholder = { Text("https://example.com/video.mkv") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = fileNameText,
          onValueChange = { fileNameText = it },
          label = { Text("File Name (Optional Override)") },
          placeholder = { Text("Auto-detected if left empty") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        Text(
          text = "Target Storage:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TvButton(
            text = "Internal Storage",
            isPrimary = selectedDest == "INTERNAL",
            onClick = { selectedDest = "INTERNAL" }
          )
          TvButton(
            text = if (usbConnected) "USB HDD ($usbName)" else "USB HDD (Offline)",
            isPrimary = selectedDest == "USB_HDD",
            onClick = { if (usbConnected) selectedDest = "USB_HDD" }
          )
        }
      }
    },
    confirmButton = {
      TvButton(
        text = "Start Download",
        icon = Icons.Default.Download,
        isPrimary = true,
        onClick = {
          if (urlText.isNotBlank()) {
            onStartDownload(
              urlText.trim(),
              if (fileNameText.isNotBlank()) fileNameText.trim() else null,
              selectedDest
            )
            onDismiss()
          }
        }
      )
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}
