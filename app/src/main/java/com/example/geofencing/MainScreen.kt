package com.example.geofencing.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.geofencing.MainViewModel
import com.example.geofencing.MapViewComposable

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit
) {
    val permissionGranted by viewModel.permissionsGranted.collectAsState()
    val geofenceStatus by viewModel.geofenceStatus.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkAndRequestPermissions()
    }

    if (!permissionGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Permissions are required to continue.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRequestPermissions) {
                Text("Grant Permissions")
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            Text("Welcome to Auto Silent Geofence App", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.addGeofence() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Add Geofence")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Status: $geofenceStatus")

            Spacer(modifier = Modifier.height(16.dp))

            MapViewComposable(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }
    }
}