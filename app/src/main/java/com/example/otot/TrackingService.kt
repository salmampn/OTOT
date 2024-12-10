package com.example.otot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.os.Binder
import android.os.SystemClock

class TrackingService : Service() {

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private var startTime = 0L
    private var running = false

    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "tracking_channel"
        val channelName = "Tracking Service"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Running Tracker")
            .setContentText("Tracking your run...")
            .setSmallIcon(R.drawable.ic_run)
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTimer()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun startTimer() {
        if (!running) {
            running = true
            handler.post(timerRunnable)
        }
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            seconds++
            handler.postDelayed(this, 1000)
        }
    }

    fun pauseTimer() {
        handler.removeCallbacks(timerRunnable)
        running = false
    }

    fun getSeconds(): Int {
        return seconds
    }

    fun stopTimer() {
        seconds = 0
        handler.removeCallbacks(timerRunnable)
        running = false
    }
}