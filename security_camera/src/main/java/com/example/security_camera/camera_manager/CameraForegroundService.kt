package com.example.security_camera.camera_manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.security_camera.R

class CameraForegroundService: Service() {

    private val channelId = "camera_foreground_service"
    private val notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, createNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "相机前台服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("安全监控中")
            .setContentText("后台录制和传输已启用")
            .setSmallIcon(R.drawable.ic_notifications)  // 需要添加通知图标
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


}