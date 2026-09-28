package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.storage.FileCategory
import com.example.storage.FileItem
import com.example.storage.StorageManager
import com.example.storage.UsbHddManager
import com.example.ui.components.TvButton
import com.example.ui.components.TvFocusableCard
import com.example.ui.components.TvHeader
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileManagerScreen(
  initialIsUsb: Boolean,
  usbTreeUri: String?,
  usbConnected: Boolean,
  usbName: String,
  onBackToHome: () -> Unit,
  onPlayMedia: (uri: Uri, title: String) -> Unit,
  onSelectUsbStorage: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var isUsbMode by remember { mutableStateOf(initialIsUsb) }
  var currentInternalDir by remember { mutableStateOf(StorageManager.getInternalRoot(context)) }
  val internalPathStack = remember { mutableStateListOf<File>() }

  var currentUsbDoc by remember { mutableStateOf<DocumentFile?>(null) }
  val usbDocStack = remember { mutableStateListOf<DocumentFile>() }

  var filesList by remember { mutableStateOf<List<FileItem>>(emptyList()) }
  var isLoading by remember { mutableStateOf(false) }
  var usbErrorMessage by remember { mutableStateOf<String?>(null) }

  // Dialog states
  var selectedFileForAction by remember { mutableStateOf<FileItem?>(null) }
  var showDetailsDialog by remember { mutableStateOf<FileItem?>(null) }
  var showNewFolderDialog by remember { mutableStateOf(false) }
  var newFolderName by remember { mutableStateOf("") }
  var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
  var renameNewName by remember { mutableStateOf("") }
  var showDeleteDialog by remember { mutableStateOf<FileItem?>(null) }

  fun loadFiles() {
    scope.launch {
      isLoading = true
      usbErrorMessage = null
      if (!isUsbMode) {
        filesList = StorageManager.listInternalDirectory(currentInternalDir)
      } else {
        val rootDoc = UsbHddManager.getRootDocument(context, usbTreeUri)
        if (rootDoc == null) {
          usbErrorMessage = "USB Storage unavailable. Please reconnect your USB drive or select USB folder in Settings."
          filesList = emptyList()
        } else {
          val activeDoc = currentUsbDoc ?: rootDoc
          currentUsbDoc = activeDoc
          filesList = UsbHddManager.listUsbDirectory(context, activeDoc)
        }
      }
      isLoading = false
    }
  }

  LaunchedEffect(isUsbMode, currentInternalDir, currentUsbDoc, usbTreeUri) {
    loadFiles()
  }

  // Hardware TV Remote Back Button handling
  BackHandler {
    if (!isUsbMode) {
      if (internalPathStack.isNotEmpty()) {
        val parent = internalPathStack.removeAt(internalPathStack.size - 1)
        currentInternalDir = parent
      } else {
        onBackToHome()
      }
    } else {
      if (usbDocStack.isNotEmpty()) {
        val parent = usbDocStack.removeAt(usbDocStack.size - 1)
        currentUsbDoc = parent
      } else {
        onBackToHome()
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
  ) {
    TvHeader(
      title = if (isUsbMode) "USB HDD Manager" else "Internal Storage",
      subtitle = if (isUsbMode) (if (currentUsbDoc != null) "USB > ${currentUsbDoc?.name}" else "USB Root") else currentInternalDir.path,
      onBack = {
        if (!isUsbMode && internalPathStack.isNotEmpty()) {
          currentInternalDir = internalPathStack.removeAt(internalPathStack.size - 1)
        } else if (isUsbMode && usbDocStack.isNotEmpty()) {
          currentUsbDoc = usbDocStack.removeAt(usbDocStack.size - 1)
        } else {
          onBackToHome()
        }
      },
      usbConnected = usbConnected,
      usbName = usbName
    )

    // Action bar: Storage Toggle, New Folder, Parent, Refresh
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TvButton(
          text = if (isUsbMode) "Switch to Internal" else "Switch to USB HDD",
          icon = if (isUsbMode) Icons.Default.Folder else Icons.Default.Usb,
          onClick = {
            isUsbMode = !isUsbMode
            internalPathStack.clear()
            usbDocStack.clear()
            currentUsbDoc = null
          }
        )

        val canGoUp = (!isUsbMode && internalPathStack.isNotEmpty()) || (isUsbMode && usbDocStack.isNotEmpty())
        if (canGoUp) {
          TvButton(
            text = "Up to Parent",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = {
              if (!isUsbMode && internalPathStack.isNotEmpty()) {
                currentInternalDir = internalPathStack.removeAt(internalPathStack.size - 1)
              } else if (isUsbMode && usbDocStack.isNotEmpty()) {
                currentUsbDoc = usbDocStack.removeAt(usbDocStack.size - 1)
              }
            }
          )
        }

        TvButton(
          text = "New Folder",
          icon = Icons.Default.Add,
          onClick = {
            newFolderName = ""
            showNewFolderDialog = true
          }
        )
      }

      TvButton(
        text = "Refresh",
        icon = Icons.Default.Refresh,
        onClick = { loadFiles() }
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // USB Disconnected Banner
    if (isUsbMode && usbErrorMessage != null) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 12.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(36.dp)
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = usbErrorMessage ?: "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onErrorContainer
          )
          Spacer(modifier = Modifier.height(14.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton(
              text = "Retry Connection",
              icon = Icons.Default.Refresh,
              onClick = { loadFiles() },
              isPrimary = true
            )
            TvButton(
              text = "Select / Re-link USB HDD",
              icon = Icons.Default.Usb,
              onClick = onSelectUsbStorage
            )
          }
        }
      }
    }

    // File list
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 24.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(bottom = 32.dp)
    ) {
      if (filesList.isEmpty() && !isLoading && usbErrorMessage == null) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(48.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "This folder is empty.",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 16.sp
            )
          }
        }
      }

      items(filesList) { item ->
        FileItemRow(
          item = item,
          onOpen = {
            if (item.isDirectory) {
              if (!isUsbMode) {
                internalPathStack.add(currentInternalDir)
                currentInternalDir = File(item.path)
              } else {
                val current = currentUsbDoc ?: UsbHddManager.getRootDocument(context, usbTreeUri)
                val targetDoc = current?.findFile(item.name)
                if (targetDoc != null) {
                  current?.let { usbDocStack.add(it) }
                  currentUsbDoc = targetDoc
                }
              }
            } else {
              // Open file
              if (item.category == FileCategory.VIDEO || item.category == FileCategory.AUDIO) {
                val uri = if (item.isUsb && item.uri != null) item.uri else Uri.fromFile(File(item.path))
                onPlayMedia(uri, item.name)
              } else {
                selectedFileForAction = item
              }
            }
          },
          onAction = {
            selectedFileForAction = item
          }
        )
      }
    }
  }

  // Action Menu Dialog
  if (selectedFileForAction != null) {
    val file = selectedFileForAction!!
    AlertDialog(
      onDismissRequest = { selectedFileForAction = null },
      title = { Text(file.name) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (file.category == FileCategory.VIDEO || file.category == FileCategory.AUDIO) {
            TvButton(
              text = "Play Media",
              icon = Icons.Default.PlayArrow,
              isPrimary = true,
              onClick = {
                val uri = if (file.isUsb && file.uri != null) file.uri else Uri.fromFile(File(file.path))
                onPlayMedia(uri, file.name)
                selectedFileForAction = null
              }
            )
          }
          TvButton(
            text = "Details",
            icon = Icons.Default.Info,
            onClick = {
              showDetailsDialog = file
              selectedFileForAction = null
            }
          )
          TvButton(
            text = "Rename",
            icon = Icons.Default.Edit,
            onClick = {
              renameNewName = file.name
              showRenameDialog = file
              selectedFileForAction = null
            }
          )
          TvButton(
            text = "Delete",
            icon = Icons.Default.Delete,
            danger = true,
            onClick = {
              showDeleteDialog = file
              selectedFileForAction = null
            }
          )
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { selectedFileForAction = null }) {
          Text("Close")
        }
      }
    )
  }

  // Details Dialog
  if (showDetailsDialog != null) {
    val item = showDetailsDialog!!
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.lastModified))
    AlertDialog(
      onDismissRequest = { showDetailsDialog = null },
      title = { Text("File Details") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Name: ${item.name}", fontWeight = FontWeight.Bold)
          Text("Type: ${item.category}")
          Text("Size: ${StorageManager.formatSize(item.size)}")
          Text("Modified: $dateStr")
          Text("Path: ${item.path}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      confirmButton = {
        TextButton(onClick = { showDetailsDialog = null }) {
          Text("OK")
        }
      }
    )
  }

  // New Folder Dialog
  if (showNewFolderDialog) {
    AlertDialog(
      onDismissRequest = { showNewFolderDialog = false },
      title = { Text("Create New Folder") },
      text = {
        OutlinedTextField(
          value = newFolderName,
          onValueChange = { newFolderName = it },
          label = { Text("Folder Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        TvButton(
          text = "Create",
          isPrimary = true,
          onClick = {
            if (newFolderName.isNotBlank()) {
              scope.launch {
                if (!isUsbMode) {
                  StorageManager.createInternalFolder(currentInternalDir, newFolderName.trim())
                } else {
                  val current = currentUsbDoc ?: UsbHddManager.getRootDocument(context, usbTreeUri)
                  if (current != null) {
                    UsbHddManager.createUsbFolder(current, newFolderName.trim())
                  }
                }
                showNewFolderDialog = false
                loadFiles()
              }
            }
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showNewFolderDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Rename Dialog
  if (showRenameDialog != null) {
    val file = showRenameDialog!!
    AlertDialog(
      onDismissRequest = { showRenameDialog = null },
      title = { Text("Rename ${file.name}") },
      text = {
        OutlinedTextField(
          value = renameNewName,
          onValueChange = { renameNewName = it },
          label = { Text("New Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        TvButton(
          text = "Save",
          isPrimary = true,
          onClick = {
            if (renameNewName.isNotBlank() && renameNewName != file.name) {
              scope.launch {
                if (!file.isUsb) {
                  StorageManager.renameInternal(File(file.path), renameNewName.trim())
                } else {
                  val current = currentUsbDoc ?: UsbHddManager.getRootDocument(context, usbTreeUri)
                  current?.findFile(file.name)?.let {
                    UsbHddManager.renameUsbFile(it, renameNewName.trim())
                  }
                }
                showRenameDialog = null
                loadFiles()
              }
            }
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showRenameDialog = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // Delete Dialog
  if (showDeleteDialog != null) {
    val file = showDeleteDialog!!
    AlertDialog(
      onDismissRequest = { showDeleteDialog = null },
      title = { Text("Delete Confirmation") },
      text = {
        Text("Are you sure you want to permanently delete \"${file.name}\"?")
      },
      confirmButton = {
        TvButton(
          text = "Delete",
          danger = true,
          onClick = {
            scope.launch {
              if (!file.isUsb) {
                StorageManager.deleteInternal(File(file.path))
              } else {
                val current = currentUsbDoc ?: UsbHddManager.getRootDocument(context, usbTreeUri)
                current?.findFile(file.name)?.let {
                  UsbHddManager.deleteUsbFile(it)
                }
              }
              showDeleteDialog = null
              loadFiles()
            }
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun FileItemRow(
  item: FileItem,
  onOpen: () -> Unit,
  onAction: () -> Unit
) {
  val icon = when (item.category) {
    FileCategory.FOLDER -> Icons.Default.Folder
    FileCategory.VIDEO -> Icons.Default.Movie
    FileCategory.AUDIO -> Icons.Default.AudioFile
    FileCategory.IMAGE -> Icons.Default.Image
    FileCategory.DOCUMENT -> Icons.Default.Description
    else -> Icons.Default.Description
  }

  val iconColor = when (item.category) {
    FileCategory.FOLDER -> Color(0xFFFBBF24)
    FileCategory.VIDEO -> Color(0xFF60A5FA)
    FileCategory.AUDIO -> Color(0xFFA78BFA)
    FileCategory.IMAGE -> Color(0xFF34D399)
    else -> Color(0xFF94A3B8)
  }

  TvFocusableCard(
    onClick = onOpen,
    shapeRadius = 10.dp,
    defaultBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    focusedBackgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    focusedBorderColor = MaterialTheme.colorScheme.primary
  ) { isFocused ->
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isFocused) MaterialTheme.colorScheme.primary else iconColor,
          modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
          Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = if (item.isDirectory) "Folder" else StorageManager.formatSize(item.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      TvButton(
        text = "Options",
        icon = Icons.Default.Edit,
        onClick = onAction
      )
    }
  }
}
