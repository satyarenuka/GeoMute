package com.example.geofencing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.geofencing.ui.GeofenceData

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "geofence_channel"
        private const val CHANNEL_NAME = "Geofence Alerts"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "GeofenceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent.fromIntent returned null")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofence error: ${geofencingEvent.errorCode}")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // DND / Notification policy access check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                Log.w(TAG, "DND access NOT granted; cannot change ringer mode.")
                showNotificationOpenDnd(context)
                Toast.makeText(context, "DND access not granted! Please allow DND access.", Toast.LENGTH_LONG).show()
                return
            }
        }

        // Load saved geofences list and update isInside flags for triggered geofences
        try {
            val prefs = context.getSharedPreferences("prefs_geofences", Context.MODE_PRIVATE)
            val gson = Gson()
            val json = prefs.getString("geofences_json", null)
            if (!json.isNullOrEmpty()) {
                val type = object : TypeToken<MutableList<GeofenceData>>() {}.type
                val list: MutableList<GeofenceData> = gson.fromJson(json, type)

                for (gfTriggered in triggeringGeofences) {
                    val requestId = gfTriggered.requestId
                    for (i in list.indices) {
                        val gf = list[i]
                        if (gf.id == requestId) {
                            val nowInside = (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER)
                            if (gf.isInside != nowInside) {
                                list[i] = gf.copy(isInside = nowInside)
                                Log.i(TAG, "Updated geofence ${gf.id} isInside -> $nowInside")
                            }
                        }
                    }
                }

                prefs.edit().putString("geofences_json", gson.toJson(list)).apply()
            } else {
                Log.d(TAG, "No saved geofences JSON found")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed updating geofence prefs", ex)
        }

        val prefsInside = context.getSharedPreferences("GEOFENCE_PREFS", Context.MODE_PRIVATE)

        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.i(TAG, "Transition ENTER received; setting SILENT")
                try {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    prefsInside.edit().putBoolean("inside_geofence", true).apply()
                    Toast.makeText(context, "Entered geofence: Silent Mode Activated", Toast.LENGTH_SHORT).show()
                    showNotification(context, "Silent Mode Activated", "You entered a geofence. Phone set to silent.")
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to set ringer to silent", ex)
                }
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.i(TAG, "Transition EXIT received; attempting to restore NORMAL")

                // IMPORTANT: don't switch to normal while there's an active call (incoming or ongoing)
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val callState = tm.callState
                if (callState != TelephonyManager.CALL_STATE_IDLE) {
                    Log.i(TAG, "Call state not idle ($callState). Will NOT set to NORMAL to avoid interrupting call.")
                    // Save the fact we exited but do not change ringer mode now
                    prefsInside.edit().putBoolean("inside_geofence", false).apply()
                    showNotification(context, "Exited geofence", "You exited a geofence; will restore sound after calls end.")
                    return
                }

                try {
                    // Load all geofences
                    val prefs = context.getSharedPreferences("prefs_geofences", Context.MODE_PRIVATE)
                    val gson = Gson()
                    val json = prefs.getString("geofences_json", null)

                    var stillInside = false

                    if (!json.isNullOrEmpty()) {
                        val type = object : TypeToken<MutableList<GeofenceData>>() {}.type
                        val list: MutableList<GeofenceData> = gson.fromJson(json, type)

                        // Check if ANY geofence is still active
                        stillInside = list.any { it.isInside }
                    }

                    if (!stillInside) {
                        // Switch back to normal only when outside ALL geofences
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        prefsInside.edit().putBoolean("inside_geofence", false).apply()
                        Toast.makeText(context, "Exited geofence: Normal Mode Activated", Toast.LENGTH_SHORT).show()
                        showNotification(context, "Normal Mode Activated", "You exited a geofence. Phone is now normal.")
                    } else {
                        Log.i(TAG, "Still inside another geofence → stay silent")
                    }



                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to set ringer to normal", ex)
                }
            }

            else -> {
                Log.w(TAG, "Unknown/unsupported geofence transition: $transitionType")
            }
        }
    }

    private fun showNotificationOpenDnd(context: Context) {
        // Small notification guiding user to DND settings
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

        val openDndIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_IMMUTABLE
            } else 0

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            12345,
            openDndIntent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Grant DND access")
            .setContentText("App needs Do Not Disturb access to change ringer mode automatically.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
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
