package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.player.PlayerViewModel
import com.example.ui.components.TvButton
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
  mediaUri: Uri,
  title: String,
  onExitPlayer: () -> Unit
) {
  val context = LocalContext.current
  val viewModel = remember(mediaUri) {
    PlayerViewModel(context, mediaUri, title)
  }
  val uiState by viewModel.uiState.collectAsState()
  val exoPlayer = remember(viewModel) { viewModel.initPlayer() }

  BackHandler {
    onExitPlayer()
  }

  DisposableEffect(Unit) {
    onDispose {
      // Clean up when leaving screen
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .focusable()
      .onKeyEvent { event ->
        when (event.key) {
          Key.DirectionCenter, Key.Enter -> {
            viewModel.togglePlayPause()
            true
          }
          Key.DirectionLeft -> {
            viewModel.seekRelative(-10000)
            true
          }
          Key.DirectionRight -> {
            viewModel.seekRelative(10000)
            true
          }
          Key.DirectionUp, Key.DirectionDown -> {
            viewModel.toggleControls()
            true
          }
          Key.Back -> {
            onExitPlayer()
            true
          }
          else -> false
        }
      }
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) {
        viewModel.toggleControls()
      }
  ) {
    // Media3 PlayerView
    AndroidView(
      factory = { ctx ->
        PlayerView(ctx).apply {
          player = exoPlayer
          useController = false
          layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
        }
      },
      modifier = Modifier.fillMaxSize()
    )

    // Buffering indicator
    if (uiState.isBuffering) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(64.dp)
        )
      }
    }

    // Player HUD Overlay
    AnimatedVisibility(
      visible = uiState.controlsVisible,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.fillMaxSize()
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Black.copy(alpha = 0.8f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.85f)
              )
            )
          )
          .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // Top Bar
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.clickable { onExitPlayer() }
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Exit Player",
              tint = Color.White,
              modifier = Modifier.padding(12.dp)
            )
          }
          Spacer(modifier = Modifier.width(16.dp))
          Text(
            text = uiState.title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
          )
        }

        // Center Action Hint
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center
        ) {
          Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.clickable { viewModel.togglePlayPause() }
          ) {
            Icon(
              imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = "Play/Pause",
              tint = Color.White,
              modifier = Modifier.padding(24.dp).size(48.dp)
            )
          }
        }

        // Bottom Controls Bar
        Column(modifier = Modifier.fillMaxWidth()) {
          // Progress bar
          val duration = uiState.duration.coerceAtLeast(1L)
          val progress = (uiState.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

          Slider(
            value = progress,
            onValueChange = { newPercent ->
              val targetPos = (newPercent * duration).toLong()
              viewModel.seekTo(targetPos)
            },
            colors = SliderDefaults.colors(
              thumbColor = MaterialTheme.colorScheme.primary,
              activeTrackColor = MaterialTheme.colorScheme.primary,
              inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "${formatTime(uiState.currentPosition)} / ${formatTime(uiState.duration)}",
              color = Color.White,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              TvButton(
                text = "-10s",
                icon = Icons.Default.FastRewind,
                onClick = { viewModel.seekRelative(-10000) }
              )
              TvButton(
                text = if (uiState.isPlaying) "Pause" else "Play",
                icon = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                isPrimary = true,
                onClick = { viewModel.togglePlayPause() }
              )
              TvButton(
                text = "+10s",
                icon = Icons.Default.FastForward,
                onClick = { viewModel.seekRelative(10000) }
              )
              TvButton(
                text = "${uiState.playbackSpeed}x Speed",
                icon = Icons.Default.Speed,
                onClick = { viewModel.cyclePlaybackSpeed() }
              )
            }
          }
        }
      }
    }
  }
}

private fun formatTime(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  val hours = totalSec / 3600
  val minutes = (totalSec % 3600) / 60
  val seconds = totalSec % 60
  return if (hours > 0) {
    String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
  }
}
