package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TvFocusableCard(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shapeRadius: Dp = 16.dp,
  defaultBackgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
  focusedBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
  focusedBorderColor: Color = MaterialTheme.colorScheme.primary,
  content: @Composable ColumnScope.(isFocused: Boolean) -> Unit
) {
  var isFocused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, animationSpec = tween(150), label = "card_scale")
  val borderCol by animateColorAsState(targetValue = if (isFocused) focusedBorderColor else Color.Transparent, label = "card_border")
  val bgCol by animateColorAsState(targetValue = if (isFocused) focusedBackgroundColor else defaultBackgroundColor, label = "card_bg")

  Card(
    modifier = modifier
      .scale(scale)
      .onFocusChanged { isFocused = it.isFocused }
      .focusable()
      .onKeyEvent { event ->
        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
          onClick()
          true
        } else false
      }
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
      ),
    shape = RoundedCornerShape(shapeRadius),
    colors = CardDefaults.cardColors(containerColor = bgCol),
    border = BorderStroke(if (isFocused) 3.dp else 1.dp, if (isFocused) borderCol else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 10.dp else 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      content(isFocused)
    }
  }
}

@Composable
fun TvButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  isPrimary: Boolean = false,
  danger: Boolean = false
) {
  var isFocused by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, animationSpec = tween(150), label = "btn_scale")

  val normalBg = when {
    danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
    isPrimary -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.surfaceVariant
  }
  val focusedBg = when {
    danger -> MaterialTheme.colorScheme.error
    isPrimary -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.primary
  }
  val textColor = when {
    isFocused && !danger && isPrimary -> MaterialTheme.colorScheme.onPrimaryContainer
    isFocused && !danger -> MaterialTheme.colorScheme.onPrimary
    isFocused && danger -> MaterialTheme.colorScheme.onError
    isPrimary -> MaterialTheme.colorScheme.onPrimary
    danger -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }

  Surface(
    modifier = modifier
      .scale(scale)
      .onFocusChanged { isFocused = it.isFocused }
      .focusable()
      .onKeyEvent { event ->
        if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
          onClick()
          true
        } else false
      }
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
      ),
    shape = RoundedCornerShape(10.dp),
    color = if (isFocused) focusedBg else normalBg,
    border = BorderStroke(
      width = if (isFocused) 2.5.dp else 1.dp,
      color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent
    ),
    tonalElevation = if (isFocused) 8.dp else 0.dp
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = textColor,
          modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
      }
      Text(
        text = text,
        color = textColor,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
      )
    }
  }
}

@Composable
fun TvHeader(
  title: String,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  usbConnected: Boolean = false,
  usbName: String = "",
  serverRunning: Boolean = false,
  serverIp: String = ""
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (onBack != null) {
        var backFocused by remember { mutableStateOf(false) }
        Surface(
          modifier = Modifier
            .onFocusChanged { backFocused = it.isFocused }
            .focusable()
            .clickable { onBack() },
          shape = CircleShape,
          color = if (backFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(if (backFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.onPrimary)
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = if (backFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(10.dp)
          )
        }
        Spacer(modifier = Modifier.width(16.dp))
      }
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.onBackground
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // Status badges on top right
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // USB HDD Badge
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (usbConnected) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (usbConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Usb,
            contentDescription = null,
            tint = if (usbConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (usbConnected) (if (usbName.isNotBlank()) usbName else "USB HDD") else "Internal Only",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (usbConnected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.outline
          )
        }
      }

      // Web Server Badge
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (serverRunning) Color(0xFF064E3B) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (serverRunning) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Dns,
            contentDescription = null,
            tint = if (serverRunning) Color(0xFF34D399) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (serverRunning && serverIp.isNotBlank()) "Web: $serverIp" else "Web Offline",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (serverRunning) Color(0xFFE6FFFA) else MaterialTheme.colorScheme.outline
          )
        }
      }
    }
  }
}
