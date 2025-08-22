package com.example.geofencing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.widget.Toast
import android.app.NotificationManager

class CallReceiver : BroadcastReceiver() {

    private fun setEmergencyFlag(context: Context, value: Boolean) {
        context.getSharedPreferences("GEOFENCE_PREFS", Context.MODE_PRIVATE)
            .edit().putBoolean("was_emergency_call", value).apply()
    }

    private fun getEmergencyFlag(context: Context): Boolean {
        return context.getSharedPreferences("GEOFENCE_PREFS", Context.MODE_PRIVATE)
            .getBoolean("was_emergency_call", false)
    }

    private fun isInsideGeofence(context: Context): Boolean {
        val prefs = context.getSharedPreferences("GEOFENCE_PREFS", Context.MODE_PRIVATE)
        return prefs.getBoolean("inside_geofence", false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val sharedPref = context.getSharedPreferences("emergency_contacts", Context.MODE_PRIVATE)
        val emergencyNumbers = sharedPref.getStringSet("contacts", emptySet()) ?: emptySet()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (incomingNumber != null && emergencyNumbers.isNotEmpty()) {
                    val incoming = incomingNumber.replace("\\s|-".toRegex(), "").takeLast(10)

                    val matchFound = emergencyNumbers.any { saved ->
                        incoming == saved.replace("\\s|-".toRegex(), "").takeLast(10)
                    }

                    if (matchFound) {
                        setEmergencyFlag(context, true)

                        Toast.makeText(
                            context,
                            "Emergency call detected – switching to loud ring",
                            Toast.LENGTH_SHORT
                        ).show()

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            !notificationManager.isNotificationPolicyAccessGranted
                        ) {
                            val settingsIntent =
                                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(settingsIntent)
                        }

                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_RING,
                            audioManager.getStreamMaxVolume(AudioManager.STREAM_RING),
                            AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                        )
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (getEmergencyFlag(context) && isInsideGeofence(context)) {
                    Toast.makeText(
                        context,
                        "Call ended – returning to silent mode",
                        Toast.LENGTH_SHORT
                    ).show()
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                setEmergencyFlag(context, false) // reset always
            }
        }
    }
}
