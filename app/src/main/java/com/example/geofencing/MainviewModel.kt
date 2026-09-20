package com.example.geofencing

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import android.util.Log

// 🔹 Firestore data model for geofences
data class GeofenceData(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Float = 100f,
    val owner: String = "",
    val approved: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application){

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Fused Location Provider
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    // Google Sign-In
    private lateinit var googleSignInClient: GoogleSignInClient
    private val _signedInEmail = MutableStateFlow(auth.currentUser?.email)
    val signedInEmail: String? get() = _signedInEmail.value

    // Geofences
    private val _myGeofences = MutableStateFlow<List<GeofenceData>>(emptyList())
    val myGeofences: StateFlow<List<GeofenceData>> = _myGeofences


    private val _allGeofences = MutableStateFlow<List<GeofenceData>>(emptyList()) // For Admin
    val allGeofences: StateFlow<List<GeofenceData>> get() = _allGeofences

    // Store last known location
    var lastKnownLocation: LatLng? = null

    init {
        // If signed in, listen for changes
        if (auth.currentUser != null) {
            listenToMyGeofences()
            loadAllGeofencesForAdmin()
        }
    }
    // ✅ Approved geofences for Student
    private val _approvedGeofences = MutableStateFlow<List<GeofenceData>>(emptyList())
    val approvedGeofences: StateFlow<List<GeofenceData>> = _approvedGeofences
    // Store selected emergency contacts
    private val _emergencyContacts = MutableStateFlow<List<String>>(emptyList())
    val emergencyContacts: StateFlow<List<String>> = _emergencyContacts

    fun setEmergencyContacts(contacts: List<String>) {
        _emergencyContacts.value = contacts
    }

    fun loadApprovedGeofences() {
        firestore.collection("geofences")
            .whereEqualTo("approved", true)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Firestore", "Error fetching approved geofences", e)
                    return@addSnapshotListener
                }

                val geofences = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(GeofenceData::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                _approvedGeofences.value = geofences
            }
    }

    // ✅ Google Sign-In setup
    fun launchGoogleSignIn(activity: Activity) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(activity.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(activity, gso)
        val signInIntent = googleSignInClient.signInIntent
        activity.startActivityForResult(signInIntent, 1001)
    }

    // ✅ Handle Google Sign-In result


    // ✅ Sign out
    fun signOut(activity: Activity) {
        auth.signOut()
        if (this::googleSignInClient.isInitialized) {
            googleSignInClient.signOut()
        }
    }


    // ✅ Listen for Host’s geofences in Firestore (live updates)


    // ✅ Load all geofences for Admin view
    fun loadAllGeofencesForAdmin() {
        firestore.collection("geofences")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val geos = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(GeofenceData::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                _allGeofences.value = geos
            }
    }

    // ✅ Add geofence at map-tapped position (Host)
    // inside your MainViewModel (keep other functions as-is)

    private fun safeDouble(value: Any?): Double {
        return when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            is Number -> value.toDouble()
            else -> 0.0
        }
    }

    private fun safeFloat(value: Any?): Float {
        return when (value) {
            is Float -> value
            is Double -> value.toFloat()
            is Long -> value.toFloat()
            is Int -> value.toFloat()
            is Number -> value.toFloat()
            else -> 100f
        }
    }

    /** Listen for Host’s geofences and handle numeric types robustly */
    /** Listen for ALL geofences (no owner filter) */
    private fun listenToMyGeofences() {
        firestore.collection("geofences")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MainViewModel", "listenToMyGeofences error", e)
                    return@addSnapshotListener
                }

                val geos = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        val id = doc.id
                        val name = data["name"] as? String ?: (data["zoneName"] as? String) ?: "Zone"
                        val lat = safeDouble(data["latitude"])
                        val lng = safeDouble(data["longitude"])
                        val radius = safeFloat(data["radius"])
                        val owner = data["owner"] as? String ?: ""
                        val approved = (data["approved"] as? Boolean) ?: true

                        GeofenceData(
                            id = id,
                            name = name,
                            latitude = lat,
                            longitude = lng,
                            radius = radius,
                            owner = owner,
                            approved = approved
                        )
                    } catch (ex: Exception) {
                        Log.e("MainViewModel", "parse geofence doc failed: ${doc.id}", ex)
                        null
                    }
                } ?: emptyList()

                Log.d("MainViewModel", "Loaded ${geos.size} total geofences")
                _myGeofences.value = geos
            }
    }


    /** Add geofence - ensure numeric types are stored */
    /** Add geofence (no auth required, no owner filter) */
    fun addHostGeofenceAt(lat: Double, lng: Double, name: String, radius: Float) {
        val id = UUID.randomUUID().toString()
        val doc = firestore.collection("geofences").document(id)

        val ownerEmail = auth.currentUser?.email ?: "unknown"

        val geo = mapOf(
            "name" to name,
            "latitude" to lat,
            "longitude" to lng,
            "radius" to radius,
            "owner" to ownerEmail,
            "approved" to false // ⬅ Initially unapproved
        )

        doc.set(geo).addOnSuccessListener {
            Log.d("MainViewModel", "Added geofence $id by $ownerEmail")
        }.addOnFailureListener { e ->
            Log.e("MainViewModel", "Failed to add geofence", e)
        }
    }



    /** Add geofence at current location - ensures lastKnownLocation exists */
    fun addHostGeofenceAtCurrentLocation(name: String, radius: Float) {
        val loc = lastKnownLocation
        if (loc == null) {
            Log.w("MainViewModel", "addHostGeofenceAtCurrentLocation: lastKnownLocation is null")
            return
        }
        addHostGeofenceAt(loc.latitude, loc.longitude, name, radius)
    }


    // ✅ Add geofence at current location (Host)


    // ✅ Update last known location
    fun updateLocation(location: Location) {
        lastKnownLocation = LatLng(location.latitude, location.longitude)
    }

    // ✅ Admin approve geofence
    fun approveGeofence(id: String) {
        firestore.collection("geofences")
            .document(id)
            .update("approved", true)
    }

    // ✅ Admin delete geofence
    fun deleteGeofence(id: String) {
        firestore.collection("geofences")
            .document(id)
            .delete()
    }

    // ✅ After login success
    fun onAuthSuccess() {
        listenToMyGeofences()
        loadAllGeofencesForAdmin()

    }
}