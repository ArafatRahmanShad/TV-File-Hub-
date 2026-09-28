package com.example.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.database.AppDatabase
import com.example.data.database.PlaybackHistoryEntity
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
  val isPlaying: Boolean = false,
  val currentPosition: Long = 0L,
  val duration: Long = 0L,
  val playbackSpeed: Float = 1.0f,
  val title: String = "",
  val controlsVisible: Boolean = true,
  val isBuffering: Boolean = false,
  val errorMessage: String? = null
)

class PlayerViewModel(
  private val context: Context,
  private val mediaUri: Uri,
  private val mediaTitle: String
) : ViewModel() {

  private val appContext = context.applicationContext
  private val db = AppDatabase.getDatabase(appContext)
  private val historyDao = db.playbackHistoryDao()
  private val preferences = AppPreferences(appContext)

  private var exoPlayer: ExoPlayer? = null
  private var positionUpdateJob: Job? = null
  private var hideControlsJob: Job? = null

  private val _uiState = MutableStateFlow(PlayerUiState(title = mediaTitle))
  val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

  fun initPlayer(): ExoPlayer {
    if (exoPlayer != null) return exoPlayer!!

    val player = ExoPlayer.Builder(appContext).build().apply {
      val mediaItem = MediaItem.fromUri(mediaUri)
      setMediaItem(mediaItem)
      prepare()
      playWhenReady = true

      addListener(object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
          if (isPlaying) scheduleHideControls()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
          when (playbackState) {
            Player.STATE_BUFFERING -> {
              _uiState.value = _uiState.value.copy(isBuffering = true)
            }
            Player.STATE_READY -> {
              _uiState.value = _uiState.value.copy(
                isBuffering = false,
                duration = duration.coerceAtLeast(0L)
              )
            }
            Player.STATE_ENDED -> {
              _uiState.value = _uiState.value.copy(isPlaying = false, controlsVisible = true)
            }
            else -> {}
          }
        }
      })
    }
    exoPlayer = player

    // Check for saved playback position
    viewModelScope.launch {
      val autoResume = preferences.autoResumePlayback
      val history = historyDao.getHistoryForUri(mediaUri.toString())
      if (history != null && history.positionMs > 5000 && (history.durationMs <= 0 || history.positionMs < history.durationMs - 10000)) {
        player.seekTo(history.positionMs)
      }
    }

    startPositionTracking()
    scheduleHideControls()

    return player
  }

  fun togglePlayPause() {
    val player = exoPlayer ?: return
    if (player.isPlaying) {
      player.pause()
      showControls()
    } else {
      player.play()
      scheduleHideControls()
    }
  }

  fun seekRelative(deltaMs: Long) {
    val player = exoPlayer ?: return
    val newPos = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
    player.seekTo(newPos)
    _uiState.value = _uiState.value.copy(currentPosition = newPos)
    showControls()
    scheduleHideControls()
  }

  fun seekTo(positionMs: Long) {
    val player = exoPlayer ?: return
    player.seekTo(positionMs)
    _uiState.value = _uiState.value.copy(currentPosition = positionMs)
    showControls()
    scheduleHideControls()
  }

  fun cyclePlaybackSpeed() {
    val player = exoPlayer ?: return
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val current = _uiState.value.playbackSpeed
    val nextIndex = (speeds.indexOf(current) + 1) % speeds.size
    val nextSpeed = speeds[nextIndex]
    player.playbackParameters = PlaybackParameters(nextSpeed)
    _uiState.value = _uiState.value.copy(playbackSpeed = nextSpeed)
    showControls()
    scheduleHideControls()
  }

  fun showControls() {
    _uiState.value = _uiState.value.copy(controlsVisible = true)
    scheduleHideControls()
  }

  fun toggleControls() {
    if (_uiState.value.controlsVisible) {
      _uiState.value = _uiState.value.copy(controlsVisible = false)
    } else {
      showControls()
    }
  }

  private fun scheduleHideControls() {
    hideControlsJob?.cancel()
    hideControlsJob = viewModelScope.launch {
      delay(4000)
      if (_uiState.value.isPlaying) {
        _uiState.value = _uiState.value.copy(controlsVisible = false)
      }
    }
  }

  private fun startPositionTracking() {
    positionUpdateJob?.cancel()
    positionUpdateJob = viewModelScope.launch {
      while (isActive) {
        exoPlayer?.let { player ->
          val current = player.currentPosition
          val total = player.duration.coerceAtLeast(0L)
          _uiState.value = _uiState.value.copy(
            currentPosition = current,
            duration = total
          )
          // Periodically save history to Room
          if (current > 1000) {
            historyDao.saveHistory(
              PlaybackHistoryEntity(
                mediaUri = mediaUri.toString(),
                title = mediaTitle,
                positionMs = current,
                durationMs = total
              )
            )
          }
        }
        delay(1000)
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    positionUpdateJob?.cancel()
    hideControlsJob?.cancel()
    exoPlayer?.release()
    exoPlayer = null
  }
}
