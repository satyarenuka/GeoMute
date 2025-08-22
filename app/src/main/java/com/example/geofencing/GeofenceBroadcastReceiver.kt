package com.example.geofencing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import android.widget.Toast


class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "geofence_channel"
        private const val CHANNEL_NAME = "Geofence Alerts"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e("GeofenceReceiver", "Error: ${geofencingEvent.errorCode}")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                Log.w("GeofenceReceiver", "DND access NOT granted! Cannot change ringer mode.")
                Toast.makeText(context, "DND access not granted!", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val prefs = context.getSharedPreferences("GEOFENCE_PREFS", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.i("GeofenceReceiver", "Entered geofence → Silent")
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                editor.putBoolean("inside_geofence", true).apply()

                // ✅ Show Toast
                Toast.makeText(context, "Entered geofence: Silent Mode Activated", Toast.LENGTH_SHORT).show()

                showNotification(
                    context,
                    "Silent Mode Activated",
                    "You entered the geofence. Phone is now silent."
                )
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.i("GeofenceReceiver", "Exited geofence → Normal")
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                editor.putBoolean("inside_geofence", false).apply()

                // ✅ Show Toast
                Toast.makeText(context, "Exited geofence: Normal Mode Activated", Toast.LENGTH_SHORT).show()

                showNotification(
                    context,
                    "Normal Mode Activated",
                    "You exited the geofence. Phone is now normal."
                )
            }
            else -> Log.w("GeofenceReceiver", "Unknown geofence transition: $transition")
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
