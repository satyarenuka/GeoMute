package com.example.geofencing
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.util.Log
import android.provider.ContactsContract
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import android.location.Geocoder
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.ui.text.input.TextFieldValue
import java.util.Locale
import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*






class MainActivity : ComponentActivity() {
    private lateinit var locationCallback: LocationCallback
    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS, // For Android 13+
        Manifest.permission.READ_PHONE_STATE
    )
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Please grant all permissions to continue", Toast.LENGTH_LONG).show()
        } else {
            checkAndRequestDndPermission()
        }
    }
    private fun startHighAccuracyLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10_000
            fastestInterval = 5_000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            smallestDisplacement = 10f // only notify if user moved >10m
        }


        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation ?: return

                if (location.accuracy <= 30) { // Only trust good accuracy
                    Log.d("LocationUpdate", "Accurate Location: ${location.latitude}, ${location.longitude}, acc=${location.accuracy}")
                    // You can optionally use this to draw on map or log
                } else {
                    Log.w("LocationUpdate", "Inaccurate location skipped: acc=${location.accuracy}")
                }
            }
        }


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }
    private fun checkAndRequestDndPermission() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !notificationManager.isNotificationPolicyAccessGranted
        ) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }



    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    companion object {
        const val GEOFENCE_ID = "my_geofence"
        const val GEOFENCE_LATITUDE = 16.52361
        const val GEOFENCE_LONGITUDE = 80.61240
        const val GEOFENCE_RADIUS = 50f


    }

    private lateinit var geofencingClient: GeofencingClient
    private lateinit var googleMap: GoogleMap
    private val foregroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                requestBackgroundLocationPermission()
            } else {
                Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
            }
        }

    private val backgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                requestOtherAppPermissions()
            } else {
                Toast.makeText(this, "Background location required for geofencing", Toast.LENGTH_LONG).show()
                openAppSettings()
            }
        }

    private fun requestForegroundLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            foregroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // Proceed to background location if already granted
            requestBackgroundLocationPermission()
        }
    }



    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                requestOtherAppPermissions()
            }
        } else {
            // For pre-Q, skip background location
            requestOtherAppPermissions()
        }
    }


    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = android.net.Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }


    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            Toast.makeText(this, "Permissions already granted", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        geofencingClient = LocationServices.getGeofencingClient(this)

        val fromBoot = intent?.getBooleanExtra("re_add_geofence", false) ?: false

        if (fromBoot) {
            // ✅ Handle reboot case
            val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("geofence_added", false)) {
                addCustomGeofence()
                Toast.makeText(this, "Geofence re-added after reboot", Toast.LENGTH_SHORT).show()
            }
        } else {
            // ✅ Normal app launch
            if (isFirstTime()) {
                requestAllPermissionsSequentially()
            } else {
                if (!checkPermissions()) requestPermissions()
                checkAndRequestDndPermission()
                if (!isLocationEnabled()) {
                    Toast.makeText(this, "Please enable location services", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }

                // ✅ Re-register geofence silently if already added
                val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                val geofenceAdded = prefs.getBoolean("geofence_added", false)
                if (geofenceAdded) {
                    addCustomGeofence()
                }
            }
        }

        // ✅ Compose UI and permissions
        setContent {
            ensureDndPermission()
            CustomGeofenceScreen()
        }

        if (!checkPermissions()) {
            requestPermissions()
        }
        else {
            startHighAccuracyLocationUpdates()  // ✅ Call it here
        }
    }



    // R
    private fun isFirstTime(): Boolean {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val isFirst = prefs.getBoolean("first_time", true)
        if (isFirst) {
            prefs.edit().putBoolean("first_time", false).apply()
        }
        return isFirst
    }



    private fun ensureDndPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
            }
        }

    }

    private fun checkPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        val callLogPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

        return fineLocation && coarseLocation && backgroundLocation && notifications && callLogPermission
    }
    private val multiPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (!allGranted) {
                Toast.makeText(this, "Please grant all permissions to continue", Toast.LENGTH_LONG).show()
            } else {
                checkAndRequestDndPermission()
                startHighAccuracyLocationUpdates() // ✅ Call it here
            }
        }


    private fun requestAllPermissionsSequentially() {
        requestForegroundLocationPermission()
    }

    private fun requestOtherAppPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CALL_LOG)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            multiPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            checkAndRequestDndPermission()
        }
    }


    private fun requestPermissions() {
        val permissionsList = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsList.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionsList.add(Manifest.permission.READ_CALL_LOG)

        multiPermissionLauncher.launch(permissionsList.toTypedArray())

    }
    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val cursor = contentResolver.query(
                contactUri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val number = it.getString(0)
                    saveEmergencyContact(number)
                    Toast.makeText(this, "Contact Saved: $number", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }


    // Add inside MainActivity
    private var customGeofenceLatLng: LatLng? = null
    private var customGeofenceRadius: Float = 50f // default


    @Composable

    fun CustomGeofenceScreen() {
        val context = LocalContext.current
        val activity = context as MainActivity

        var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
        var customGeofenceLatLng by remember { mutableStateOf<LatLng?>(null) }
        var customGeofenceRadius by remember { mutableStateOf(50f) }
        var googleMapRef by remember { mutableStateOf<GoogleMap?>(null) }


        Box(modifier = Modifier.fillMaxSize()) {

            // Map View
            AndroidView(factory = {
                MapView(context).apply {
                    onCreate(Bundle())
                    onResume()
                    getMapAsync { map ->
                        googleMapRef = map
                        map.mapType = GoogleMap.MAP_TYPE_HYBRID
                        map.uiSettings.isZoomControlsEnabled = true
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            map.isMyLocationEnabled = true
                        }
                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                val currentLatLng = LatLng(location.latitude, location.longitude)
                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                            }
                        }
                        // Restore saved geofence if exists
                        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                        val lat = prefs.getFloat("custom_geofence_lat", 0f).toDouble()
                        val lng = prefs.getFloat("custom_geofence_lng", 0f).toDouble()
                        val radius = prefs.getFloat("custom_geofence_radius", 50f)

                        if (lat != 0.0 && lng != 0.0) {
                            val savedLatLng = LatLng(lat, lng)
                            customGeofenceLatLng = savedLatLng
                            customGeofenceRadius = radius
                            map.addMarker(MarkerOptions().position(savedLatLng).title("Saved Geofence"))
                            map.addCircle(
                                CircleOptions()
                                    .center(savedLatLng)
                                    .radius(radius.toDouble())
                                    .strokeColor(0xFFFF0000.toInt())
                                    .fillColor(0x44FF0000)
                                    .strokeWidth(4f)
                            )
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(savedLatLng, 15f))
                        }

                        // Map tap listener
                        map.setOnMapClickListener { latLng ->
                            customGeofenceLatLng = latLng
                            map.clear()
                            map.addMarker(MarkerOptions().position(latLng).title("Custom Geofence"))
                            map.addCircle(
                                CircleOptions()
                                    .center(latLng)
                                    .radius(customGeofenceRadius.toDouble())
                                    .strokeColor(0xFFFF0000.toInt())
                                    .fillColor(0x44FF0000)
                                    .strokeWidth(4f)
                            )
                        }
                    }
                }
            }, modifier = Modifier.fillMaxSize())

            // UI Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top
            ) {

                // Search bar
                Row(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White)
                            .padding(8.dp)
                    )
                    Button(onClick = {
                        try {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocationName(searchQuery.text, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val addr = addresses[0]
                                val latLng = LatLng(addr.latitude, addr.longitude)
                                customGeofenceLatLng = latLng

                                googleMapRef?.let { map ->
                                    map.clear()
                                    map.addMarker(MarkerOptions().position(latLng).title("Selected Location"))
                                    map.addCircle(
                                        CircleOptions()
                                            .center(latLng)
                                            .radius(customGeofenceRadius.toDouble())
                                            .strokeColor(0xFFFF0000.toInt())
                                            .fillColor(0x44FF0000)
                                            .strokeWidth(4f)
                                    )
                                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                                }
                            } else {
                                Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Search")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Radius slider
                Column {
                    Text(text = "Radius: ${customGeofenceRadius.toInt()} m")
                    Slider(
                        value = customGeofenceRadius,
                        onValueChange = { value ->
                            customGeofenceRadius = value
                            customGeofenceLatLng?.let { latLng ->
                                googleMapRef?.let { map ->
                                    map.clear()
                                    map.addMarker(MarkerOptions().position(latLng).title("Custom Geofence"))
                                    map.addCircle(
                                        CircleOptions()
                                            .center(latLng)
                                            .radius(customGeofenceRadius.toDouble())
                                            .strokeColor(0xFFFF0000.toInt())
                                            .fillColor(0x44FF0000)
                                            .strokeWidth(4f)
                                    )
                                }
                            }
                        },
                        valueRange = 10f..500f
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Save button
                Button(onClick = {
                    if (customGeofenceLatLng != null) {
                        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putFloat("custom_geofence_lat", customGeofenceLatLng!!.latitude.toFloat())
                            .putFloat("custom_geofence_lng", customGeofenceLatLng!!.longitude.toFloat())
                            .putFloat("custom_geofence_radius", customGeofenceRadius)
                            .putBoolean("custom_geofence_saved", true)
                            .apply()

                        Toast.makeText(context, "Custom geofence saved!", Toast.LENGTH_SHORT).show()
                        activity.addCustomGeofence()
                    } else {
                        Toast.makeText(context, "Select location first", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Save Geofence")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Emergency contact button
                Button(onClick = { activity.openContactPicker() }) {
                    Text("Add Emergency Contact")
                }
            }
        }
    }

    fun addCustomGeofence() {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("custom_geofence_lat", 0f).toDouble()
        val lng = prefs.getFloat("custom_geofence_lng", 0f).toDouble()
        val radius = prefs.getFloat("custom_geofence_radius", 50f)

        val geofence = Geofence.Builder()
            .setRequestId("CUSTOM_GEOFENCE")
            .setCircularRegion(lat, lng, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(1000)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)

            .addGeofence(geofence)

            .build()

        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(geofencingRequest, pendingIntent)
                .addOnSuccessListener {
                    Toast.makeText(this, " geofence added!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to add  geofence", Toast.LENGTH_SHORT).show()
                }
        }
    }




    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
    }


    fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID

        val geoLocation = LatLng(GEOFENCE_LATITUDE, GEOFENCE_LONGITUDE)
        googleMap.addMarker(MarkerOptions().position(geoLocation).title("Geofence Location"))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(geoLocation, 15f))

        googleMap.addCircle(
            CircleOptions()
                .center(geoLocation)
                .radius(GEOFENCE_RADIUS.toDouble())
                .strokeColor(0xFFFF0000.toInt())
                .fillColor(0x44FF0000)
                .strokeWidth(4f)
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
        }

        googleMap.uiSettings.isZoomControlsEnabled = true
    }
    fun saveEmergencyContact(number: String) {
        val sharedPref = getSharedPreferences("emergency_contacts", Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        val existing = sharedPref.getStringSet("contacts", mutableSetOf()) ?: mutableSetOf()
        existing.add(number)
        editor.putStringSet("contacts", existing)
        editor.apply()
    }





    @Composable
    fun MapViewComposable(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        AndroidView(
            modifier = modifier,
            factory = {
                MapView(context).apply {
                    onCreate(Bundle())
                    getMapAsync { map -> (context as MainActivity).onMapReady(map) }
                    onResume()
                }
            }
        )
    }



    @Preview
    @Composable
    fun PreviewMainScreen() {
        CustomGeofenceScreen()
    }
}