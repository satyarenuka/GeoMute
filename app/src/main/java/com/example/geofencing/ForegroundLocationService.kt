package com.example.geofencing

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import android.content.pm.PackageManager

class ForegroundLocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val geofenceList = listOf(
        Triple("GEOFENCE_ID_1", 16.48721, 80.68952),
        Triple("GEOFENCE_ID_2", 16.50500, 80.67500)
    )
    private val GEOFENCE_RADIUS = 50f

    override fun onCreate() {
        super.onCreate()

        startForegroundServiceWithNotification()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                Log.d("ForegroundService", "Location: ${location.latitude}, ${location.longitude}")
                checkAndReapplySilentMode(location)
            }
        }

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        }
    }

    private fun checkAndReapplySilentMode(location: Location) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return

        for ((_, lat, lng) in geofenceList) {
            val result = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, lat, lng, result)
            if (result[0] <= GEOFENCE_RADIUS) {
                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    notificationManager.isNotificationPolicyAccessGranted
                ) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    Log.d("ForegroundService", "Re-applied silent mode inside geofence.")
                } else {
                    Log.w("ForegroundService", "DND permission missing.")
                }
            }
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "silent_check_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Geofence Silent Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Silent Mode Monitor")
            .setContentText("Monitoring geofence to enforce silent mode.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(101, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
