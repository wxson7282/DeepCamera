package com.example.security_camera.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * 视频流前台服务
 *
 * 职责：
 * 1. 保持 WebSocket 服务器在后台持续运行
 * 2. 显示前台通知，防止系统杀进程
 * 3. 管理流媒体服务的启动和停止
 *
 * 启动方式：
 *   val intent = Intent(context, VideoStreamService::class.java)
 *   intent.putExtra("port", 8080)
 *   context.startForegroundService(intent)
 *
 * 停止方式：
 *   context.stopService(Intent(context, VideoStreamService::class.java))
 */
class VideoStreamService : Service() {

    companion object {
        private const val TAG = "VideoStreamService"
        private const val CHANNEL_ID = "video_stream_channel"
        private const val CHANNEL_NAME = "视频流传输"
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_PORT = "port"
        const val DEFAULT_PORT = 8080

        /**
         * 启动流媒体服务
         */
        fun start(context: Context, port: Int = DEFAULT_PORT) {
            val intent = Intent(context, VideoStreamService::class.java).apply {
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止流媒体服务
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, VideoStreamService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "VideoStreamService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT

        // 启动前台通知
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        // 启动 WebSocket 服务器
        StreamManager.getInstance().start(port)

        Log.i(TAG, "视频流服务已启动，端口: $port")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        StreamManager.getInstance().stop()
        Log.i(TAG, "视频流服务已停止")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ==================== 通知 ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "视频流传输服务运行中"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("安全摄像头")
            .setContentText("视频流传输服务运行中")
            .setContentIntent(pendingIntent)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
