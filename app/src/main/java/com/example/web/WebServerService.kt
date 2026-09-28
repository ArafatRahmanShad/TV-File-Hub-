package com.example.web

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

class WebServerService : Service() {

  companion object {
    private const val CHANNEL_ID = "tv_file_hub_web_server_channel"
    private const val NOTIFICATION_ID = 2002
    const val ACTION_START = "ACTION_START_WEB_SERVER"
    const val ACTION_STOP = "ACTION_STOP_WEB_SERVER"

    private var serverInstance: TvFileHubHttpServer? = null

    fun getServer(context: Context): TvFileHubHttpServer {
      return serverInstance ?: synchronized(this) {
        val server = TvFileHubHttpServer(context.applicationContext)
        serverInstance = server
        server
      }
    }

    fun startService(context: Context, port: Int = 8765) {
      val intent = Intent(context, WebServerService::class.java).apply {
        action = ACTION_START
        putExtra("port", port)
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
      val intent = Intent(context, WebServerService::class.java).apply {
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
      serverInstance?.stop()
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }

    val port = intent?.getIntExtra("port", 8765) ?: 8765
    val server = getServer(applicationContext)
    if (!server.isRunning) {
      server.start(port)
    }

    val notification = buildNotification(server.getLocalIpAddress(), port)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      )
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }

    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    serverInstance?.stop()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "TV File Hub Web Server",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Provides phone remote control and download endpoints"
      }
      val manager = getSystemService(NotificationManager::class.java)
      manager?.createNotificationChannel(channel)
    }
  }

  private fun buildNotification(ip: String, port: Int): Notification {
    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("TV File Hub Web Server Running")
      .setContentText("Phone access: http://$ip:$port")
      .setSmallIcon(android.R.drawable.stat_sys_upload)
      .setContentIntent(pendingIntent)
      .setOngoing(true)
      .build()
  }
}
