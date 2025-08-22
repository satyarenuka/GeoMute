package com.example.geofencing

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

object GeofenceUtils {

    fun addGeofences(context: Context) {
        val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

        val geofencingRequestBuilder = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)

        for ((id, lat, lng) in GeofenceConstants.geofenceList) {
            val geofence = Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(lat, lng, GeofenceConstants.GEOFENCE_RADIUS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
                )
                .setLoiteringDelay(30_000)
                .build()

            geofencingRequestBuilder.addGeofence(geofence)
        }

        val geofencingRequest = geofencingRequestBuilder.build()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("GeofenceUtils", "Location permission not granted")
            return
        }

        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
            .addOnSuccessListener {
                Log.d("GeofenceUtils", "✅ All geofences added successfully")
                Toast.makeText(context, "Geofences added", Toast.LENGTH_SHORT).show()

                context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("geofence_added", true)
                    .apply()
            }
            .addOnFailureListener { e ->
                Log.e("GeofenceUtils", "❌ Failed to add geofences: ${e.message}")
                Toast.makeText(context, "Failed to add geofences", Toast.LENGTH_SHORT).show()
            }
    }
}
