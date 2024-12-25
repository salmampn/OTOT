package com.example.otot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.os.Binder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

class TrackingService : Service() {

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private var running = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    // Binder class to allow access to the service
    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }

    // Initialize the service
    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationRequest()
        setupLocationCallback()
    }

    // Start the service as a foreground service
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

    // Set up the location request
    private fun setupLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = 2000 // Update interval
            fastestInterval = 1000 // Fastest update interval
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    // Set up the location callback
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                for (location in locationResult.locations) {
                    // Handle the location update here
                    // For example, you can log the location or update your UI
                    Log.d("TrackingService", "Location: ${location.latitude}, ${location.longitude}")
                }
            }
        }
    }

    // Start the service
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTimer()
        startLocationUpdates()
        return START_STICKY
    }

    // Start location updates
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {// Handle permission request if needed
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    // Bind the service
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    // Start the timer
    fun startTimer() {
        if (!running) {
            running = true
            handler.post(timerRunnable)
        }
    }

    // Timer runnable to update the timer
    private val timerRunnable = object : Runnable {
        override fun run() {
            seconds++
            handler.postDelayed(this, 1000)
        }
    }

    // Pause the timer
    fun pauseTimer() {
        handler.removeCallbacks(timerRunnable)
        running = false
    }

    // Get the current timer value
    fun getSeconds(): Int {
        return seconds
    }

    // Stop the timer
    fun stopTimer() {
        seconds = 0
        handler.removeCallbacks(timerRunnable)
        running = false
    }

    // Stop the service
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // Destroy the service
    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates() // Ensure location updates are stopped when the service is destroyed
    }
}