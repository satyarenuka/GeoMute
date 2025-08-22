// File: com/example/geofencing/MapViewComposable.kt

package com.example.geofencing

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import androidx.compose.ui.platform.LocalContext


@SuppressLint("MissingPermission")
@Composable
fun MapViewComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                onCreate(null)
                getMapAsync { map ->
                    activity?.onMapReady(map) // ✅ This lets MainActivity control the map
                }
                onResume()
            }
        },
        modifier = modifier
    )
}

