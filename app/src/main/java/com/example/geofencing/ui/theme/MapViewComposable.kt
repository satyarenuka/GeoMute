package com.example.geofencing.ui

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun MapViewComposable(
    modifier: Modifier = Modifier,
    geofences: List<Triple<LatLng, Float, String>>, // center, radius, custom name
    onMapClick: (LatLng) -> Unit,
    satelliteView: Boolean = true,
    initialLatLng: LatLng = LatLng(16.5062, 80.6480)
) {
    val context = LocalContext.current
    var map by remember { mutableStateOf<GoogleMap?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                onCreate(null)
                onResume()

                getMapAsync { googleMap ->
                    map = googleMap
                    googleMap.mapType = if (satelliteView)
                        GoogleMap.MAP_TYPE_HYBRID
                    else
                        GoogleMap.MAP_TYPE_NORMAL

                    googleMap.uiSettings.isZoomControlsEnabled = true
                    googleMap.uiSettings.isCompassEnabled = true

                    val fineLocationGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (fineLocationGranted) {
                        googleMap.isMyLocationEnabled = true
                        googleMap.uiSettings.isMyLocationButtonEnabled = true

                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            val currentLatLng = location?.let { LatLng(it.latitude, it.longitude) } ?: initialLatLng
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                        }
                    } else {
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 14f))
                    }

                    googleMap.setOnMapClickListener { latLng -> onMapClick(latLng) }
                }
            }
        },
        update = { mapView ->
            map?.let { googleMap ->
                googleMap.clear()

                val geocoder = Geocoder(context, Locale.getDefault())

                geofences.forEach { (center, radius, customName) ->
                    val locationName = try {
                        val addresses = geocoder.getFromLocation(center.latitude, center.longitude, 1)
                        addresses?.get(0)?.getAddressLine(0) ?: customName
                    } catch (e: Exception) {
                        customName
                    }

                    // Marker for geofence location
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(center)
                            .title(locationName)
                    )

                    // Red transparent circle for geofence area
                    googleMap.addCircle(
                        CircleOptions()
                            .center(center)
                            .radius(radius.toDouble())
                            .strokeWidth(4f)
                            .strokeColor(0xFFFF0000.toInt()) // red border
                            .fillColor(0x33FF0000.toInt())           // semi-transparent red fill
                    )
                }
            }
        }
    )
}
