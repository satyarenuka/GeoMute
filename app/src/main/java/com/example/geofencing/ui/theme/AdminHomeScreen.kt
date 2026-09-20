package com.example.geofencing.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.geofencing.MainActivity
import com.example.geofencing.MainViewModel
import com.example.geofencing.ui.MapViewComposable
import com.google.android.gms.maps.model.LatLng

@Composable
fun AdminHomeScreen(
    viewModel: MainViewModel,
    activity: MainActivity,
    onSignOut: () -> Unit
) {
    val allGeofences by viewModel.allGeofences.collectAsState(initial = emptyList())
    var selectedGeofence by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🛠 Admin Dashboard", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))

        // ✅ Map displaying all geofences (approved + pending)
        MapViewComposable(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f),
            geofences = allGeofences.map {
                Triple(LatLng(it.latitude, it.longitude), it.radius, it.name)
            },
            onMapClick = {},
            satelliteView = true // ✅ always satellite with labels
        )


        Spacer(Modifier.height(12.dp))
        Text("Pending & Approved Geofences", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // ✅ List of geofences
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(allGeofences) { gf ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text("📍 ${gf.name}", style = MaterialTheme.typography.titleMedium)
                        Text("Radius: ${gf.radius}m")
                        Text("Lat: ${gf.latitude}, Lng: ${gf.longitude}")
                        Text(
                            "Status: ${if (gf.approved) "✅ Approved" else "🕓 Pending"}",
                            color = if (gf.approved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (!gf.approved) {
                                Button(onClick = {
                                    viewModel.approveGeofence(gf.id)
                                    selectedGeofence = gf.id
                                }) {
                                    Text("Approve")
                                }
                            }
                            OutlinedButton(onClick = {
                                viewModel.deleteGeofence(gf.id)
                                selectedGeofence = gf.id
                            }) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = { viewModel.signOut(activity); onSignOut() }) {
            Text("Sign Out")
        }
    }
}
