package com.example.geofencing.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.geofencing.GeofenceBroadcastReceiver
import com.example.geofencing.MainViewModel
import com.example.geofencing.GeofenceData
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.*
import androidx.compose.ui.graphics.Color
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import java.util.*
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add

import android.util.Log


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHomeScreen(
    viewModel: MainViewModel,
    activity: Activity,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val geofencingClient = LocationServices.getGeofencingClient(context)
    val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    val emergencyContacts by viewModel.emergencyContacts.collectAsState()

    // -----------------------------
    // Hardcoded geofence (optional)
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("emergency_contacts", Context.MODE_PRIVATE)
        val savedContacts = prefs.getStringSet("contacts", emptySet()) ?: emptySet()

        if (savedContacts.isNotEmpty()) {
            // ✅ Cleanly format as Name:Number (kept internally)
            // but display only the Name in the UI
            val validContacts = savedContacts.filter { it.contains(":") }

            // ✅ Set properly into ViewModel (as-is)
            viewModel.setEmergencyContacts(validContacts)
        }
    }
    // -----------------------------
    val hardcoded = GeofenceData(
        id = "hardcoded_1",
        name = "Campus Zone",
        latitude = 16.484191,
        longitude = 80.692977,
        radius = 100f,
        owner = "admin",
        approved = false
    )




// Permission check before launch
    val pickContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri == null) {
            Toast.makeText(context, "No contact selected", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        try {
            // Step 1: Query the contact ID
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME
                ),
                null, null, null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val contactId =
                        it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                    val displayName =
                        it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))

                    // Step 2: Query phone number for that contact
                    val phoneCursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )

                    phoneCursor?.use { pc ->
                        if (pc.moveToFirst()) {
                            val number = pc.getString(
                                pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            ).replace("\\s|-".toRegex(), "")

                            // ✅ Save to SharedPreferences
                            val prefs = context.getSharedPreferences("emergency_contacts", Context.MODE_PRIVATE)
                            val saved = prefs.getStringSet("contacts", emptySet()) ?: emptySet()
                            val newSaved = saved.toMutableSet()
                            newSaved.add("$displayName:$number")
                            prefs.edit().putStringSet("contacts", newSaved.toSet()).apply() // ✅ always save a new set



                            // ✅ Update ViewModel
                            val updated = viewModel.emergencyContacts.value.toMutableList()
                            updated.add("$displayName:$number")
                            viewModel.setEmergencyContacts(updated)

                            Toast.makeText(context, "$displayName added as Emergency Contact", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No phone number found", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Contact not found", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (ex: Exception) {
            Log.e("StudentHomeScreen", "Error reading contact", ex)
            Toast.makeText(context, "Failed to add contact", Toast.LENGTH_SHORT).show()
        }
    }



// 🚀 FAB with permission check
    val fab: @Composable () -> Unit = {
        FloatingActionButton(onClick = {
            pickContactLauncher.launch(null)
        }) {
            Text("Add Emergency Contacts")
        }

    }




    // ----------------------------------
    // Handle Back press correctly
    // ----------------------------------
    BackHandler(enabled = true) {
        // Do nothing (prevents exit to login accidentally)
    }

    // ----------------------------------
    // Location & Map logic
    // ----------------------------------
    LaunchedEffect(Unit) {
        viewModel.loadApprovedGeofences()
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return@LaunchedEffect

        fusedLocationProviderClient.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build(),
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    currentLocation = result.lastLocation
                }
            },
            Looper.getMainLooper()
        )
    }

    val approvedGeofences by viewModel.approvedGeofences.collectAsState()
    val geofencesToShow = remember(approvedGeofences) {
        val list = approvedGeofences.toMutableList()
        if (hardcoded.approved)
            list.add(hardcoded)
        list }


    LaunchedEffect(geofencesToShow) {
        if (geofencesToShow.isNotEmpty()) {
            addGeofencesToClient(context, geofencingClient, geofencesToShow)
        }
    }


    // -----------------------------
    // Scaffold & Map
    // -----------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User View") },
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text("Sign Out", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },

        ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (currentLocation == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(
                        LatLng(currentLocation!!.latitude, currentLocation!!.longitude),
                        16f
                    )
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = true,
                        mapType = MapType.SATELLITE
                    )
                ) {
                    Marker(
                        state = MarkerState(
                            LatLng(
                                currentLocation!!.latitude,
                                currentLocation!!.longitude
                            )
                        ),
                        title = "You are here",
                        snippet = getAddressFromLatLng(
                            context,
                            currentLocation!!.latitude,
                            currentLocation!!.longitude
                        )
                    )

                    geofencesToShow.forEach { gf ->
                        val pos = LatLng(gf.latitude, gf.longitude)
                        Marker(
                            state = MarkerState(pos),
                            title = gf.name,
                            snippet = getAddressFromLatLng(context, gf.latitude, gf.longitude)
                        )
                        Circle(
                            center = pos,
                            radius = gf.radius.toDouble(),
                            strokeColor = MaterialTheme.colorScheme.primary,
                            fillColor = Color(0x2200FF00)
                        )
                    }
                }
                // ✅ Emergency Contacts List (below map)
                // ✅ Emergency Contacts List (below map)
                // ✅ Emergency Contacts List (Improved UI)
                // ✅ Improved Emergency Contacts UI (Transparent & Non-overlapping)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xAA1E1E1E)) // semi-transparent dark background
                        .padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        Text(
                            text = "Emergency Contacts:",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (emergencyContacts.isEmpty()) {
                            Text("No contacts added", color = Color.LightGray)
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .padding(bottom = 72.dp) // ✅ Space for FAB
                            ) {
                                items(emergencyContacts) { contact ->
                                    // Fix for showing only contact name (no "Saved")
                                    val contactName = contact.substringBefore(":").ifEmpty { "Unknown" }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF2A2A2A) // dark card background
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = contactName,
                                                color = Color(0xFFB3E5FC), // light blue text
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            TextButton(
                                                onClick = {
                                                    val prefs = context.getSharedPreferences("emergency_contacts", Context.MODE_PRIVATE)
                                                    val saved = prefs.getStringSet("contacts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

                                                    saved.remove(contact)

                                                    prefs.edit().putStringSet("contacts", saved).apply()

                                                    val updated = emergencyContacts.toMutableList()
                                                    updated.remove(contact)
                                                    viewModel.setEmergencyContacts(updated)

                                                    Toast.makeText(context, "Removed $contactName", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Text("Remove", color = Color(0xFFFF5252))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // ✅ Add FAB bottom-right corner, outside the contact Box
                FloatingActionButton(
                    onClick = { pickContactLauncher.launch(null) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text("Add Emergency Contacts" +
                            "") // simple plus icon look
                }
            }
        }
    }

    // -----------------------------
    // Manual Add Contact Dialog
    // -----------------------------
    if (showAddContactDialog) {
        var name by remember { mutableStateOf(TextFieldValue("")) }
        var number by remember { mutableStateOf(TextFieldValue("")) }

        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("Add Emergency Contact") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it },
                        label = { Text("Phone Number") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.text.isNotBlank() && number.text.isNotBlank()) {
                        // ✅ Clean number
                        val cleanNumber = number.text.replace("\\s|-".toRegex(), "")

                        // ✅ Save name:number pair
                        val prefs = context.getSharedPreferences("emergency_contacts", Context.MODE_PRIVATE)
                        val saved = prefs.getStringSet("contacts", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                        saved.add("${name.text}:$cleanNumber")
                        prefs.edit().putStringSet("contacts", saved).apply()

                        // ✅ Update ViewModel immediately
                        val existingContacts = viewModel.emergencyContacts.value.toMutableList()
                        existingContacts.add("${name.text}:$cleanNumber")
                        viewModel.setEmergencyContacts(existingContacts)

                        Toast.makeText(context, "${name.text} added", Toast.LENGTH_SHORT).show()
                        showAddContactDialog = false
                    } else {
                        Toast.makeText(context, "Fill both fields", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Add")
                }
            },

            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -----------------------------
// Geofencing Helper
// -----------------------------
private fun addGeofencesToClient(
    context: Context,
    client: GeofencingClient,
    geofences: List<GeofenceData>
) {
    if (ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) return

    val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    val geofenceObjects = geofences.map {
        com.google.android.gms.location.Geofence.Builder()
            .setRequestId(it.id)
            .setCircularRegion(it.latitude, it.longitude, it.radius)
            .setExpirationDuration(com.google.android.gms.location.Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER or
                        com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()
    }

    val request = GeofencingRequest.Builder()
        .addGeofences(geofenceObjects)
        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
        .build()

    client.addGeofences(request, pendingIntent)
        .addOnSuccessListener {
            Toast.makeText(context, "Geofences active", Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener {
            Toast.makeText(context, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
        }
}

// -----------------------------
// Reverse Geocode Helper
// -----------------------------
private fun getAddressFromLatLng(context: Context, lat: Double, lon: Double): String {
    return try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address>? = geocoder.getFromLocation(lat, lon, 1)
        addresses?.firstOrNull()?.getAddressLine(0) ?: "Unknown location"
    } catch (e: Exception) {
        "Unknown"
    }
}