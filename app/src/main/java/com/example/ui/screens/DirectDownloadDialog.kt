package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
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

  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val clipboardManager = LocalClipboardManager.current

  val safeDismiss = {
    focusManager.clearFocus(force = true)
    keyboardController?.hide()
    onDismiss()
  }

  AlertDialog(
    onDismissRequest = { safeDismiss() },
    properties = DialogProperties(
      dismissOnBackPress = true,
      dismissOnClickOutside = true
    ),
    title = { Text("Direct URL Download") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = urlText,
          onValueChange = { urlText = it },
          label = { Text("Download URL (HTTP/HTTPS)") },
          placeholder = { Text("https://example.com/video.mkv") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          trailingIcon = {
            IconButton(onClick = {
              val clip = clipboardManager.getText()?.text
              if (!clip.isNullOrBlank()) {
                urlText = clip.trim()
              }
            }) {
              Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = "Paste URL",
                tint = MaterialTheme.colorScheme.primary
              )
            }
          },
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next
          )
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          TextButton(onClick = {
            val clip = clipboardManager.getText()?.text
            if (!clip.isNullOrBlank()) {
              urlText = clip.trim()
            }
          }) {
            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Paste from Clipboard", fontSize = 12.sp)
          }
        }

        OutlinedTextField(
          value = fileNameText,
          onValueChange = { fileNameText = it },
          label = { Text("File Name (Optional Override)") },
          placeholder = { Text("Auto-detected if left empty") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
          ),
          keyboardActions = KeyboardActions(
            onDone = {
              focusManager.clearFocus(force = true)
              keyboardController?.hide()
            }
          )
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
            val finalUrl = urlText.trim()
            val finalName = if (fileNameText.isNotBlank()) fileNameText.trim() else null
            val finalDest = selectedDest
            safeDismiss()
            onStartDownload(finalUrl, finalName, finalDest)
          }
        }
      )
    },
    dismissButton = {
      TextButton(onClick = { safeDismiss() }) {
        Text("Cancel")
      }
    }
  )
}
