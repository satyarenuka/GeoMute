// File: com/example/geofencing/MainViewModel.kt

package com.example.geofencing

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(
    private val context: Context,
    private val activity: ComponentActivity
) : AndroidViewModel(Application()) {

    val requiredPermissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
        android.Manifest.permission.ACCESS_NOTIFICATION_POLICY,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted

    private val _geofenceStatus = MutableStateFlow("Not Added")
    val geofenceStatus: StateFlow<String> = _geofenceStatus

    fun checkAndRequestPermissions() {
        val granted = requiredPermissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        _permissionsGranted.value = granted
    }

    fun handlePermissionResult(results: Map<String, Boolean>) {
        _permissionsGranted.value = results.values.all { it }
    }

    fun addGeofence() {
        _geofenceStatus.value = "Geofence Added"
    }
}