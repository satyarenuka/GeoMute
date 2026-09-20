package com.example.geofencing.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.geofencing.MainActivity
import com.example.geofencing.MainViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostHomeScreen(
    viewModel: MainViewModel,
    activity: MainActivity,
    onSignOut: () -> Unit
) {
    val hostGeofences by viewModel.myGeofences.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var tappedLatLng by remember { mutableStateOf<LatLng?>(null) }

    // Default camera on Vijayawada
    val defaultLatLng = LatLng(16.5062, 80.6480)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 13f)
    }

    // FAB for adding geofence at current location
    val fab: @Composable () -> Unit = {
        FloatingActionButton(onClick = {
            tappedLatLng = null
            showAddDialog = true
        }) {
            Text("➕ Add")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📍 Host Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,  // White background
                    titleContentColor = Color.Black // Black title text
                ),
                actions = {
                    TextButton(onClick = {
                        viewModel.signOut(activity)
                        onSignOut()
                    }) {
                        Text("Sign Out",color = Color.Black )
                    }
                }
            )
        },
        floatingActionButton = fab,
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Tap on the map to add a geofence manually")
            Spacer(Modifier.height(8.dp))

            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = MapType.HYBRID,
                    isMyLocationEnabled = true
                ),
                uiSettings = MapUiSettings(zoomControlsEnabled = true),
                onMapClick = { latLng ->
                    tappedLatLng = latLng
                    showAddDialog = true
                }
            ) {
                hostGeofences.forEach { geo ->
                    val center = LatLng(geo.latitude, geo.longitude)
                    Marker(
                        state = MarkerState(center),
                        title = geo.name,
                        snippet = "${geo.radius} m radius"
                    )
                    Circle(
                        center = center,
                        radius = geo.radius.toDouble(),
                        fillColor = Color(0x44FF0000),
                        strokeColor = Color.Red,
                        strokeWidth = 3f
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Your Geofences:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (hostGeofences.isEmpty()) {
                Text("No geofences added yet.")
            } else {
                hostGeofences.forEach { geo ->
                    val status = if (geo.approved) "✅ Approved" else "⏳ Pending"
                    Text("• ${geo.name} (${geo.radius} m) - $status")
                }
            }
        }
    }

    // Add Geofence Dialog
    if (showAddDialog) {
        AddGeofenceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, radius ->
                val latLng = tappedLatLng
                if (latLng != null) {
                    viewModel.addHostGeofenceAt(latLng.latitude, latLng.longitude, name, radius)
                } else {
                    viewModel.addHostGeofenceAtCurrentLocation(name, radius)
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddGeofenceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, radius: Float) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var radiusText by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Geofence") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Zone Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { radiusText = it },
                    label = { Text("Radius (meters)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val radius = radiusText.toFloatOrNull() ?: 100f
                    if (name.isNotBlank()) onConfirm(name, radius)
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}