package com.example.torrent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class TorrentService : Service() {

  companion object {
    private const val CHANNEL_ID = "tv_file_hub_torrent_channel"
    private const val NOTIFICATION_ID = 2001
    const val ACTION_START = "ACTION_START_TORRENT_SERVICE"
    const val ACTION_STOP = "ACTION_STOP_TORRENT_SERVICE"

    fun startService(context: Context) {
      val intent = Intent(context, TorrentService::class.java).apply {
        action = ACTION_START
      }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }

    fun stopService(context: Context) {
      val intent = Intent(context, TorrentService::class.java).apply {
        action = ACTION_STOP
      }
      try {
        context.stopService(intent)
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    val torrentManager = TorrentManager.getInstance(applicationContext)
    torrentManager.startEngineLoop()

    return START_STICKY
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "TV File Hub Background Engine",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Runs background torrent downloads and seeding"
      }
      val manager = getSystemService(NotificationManager::class.java)
      manager?.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(): Notification {
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("TV File Hub Engine")
      .setContentText("Torrent & background transfer active")
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .build()
  }
}
