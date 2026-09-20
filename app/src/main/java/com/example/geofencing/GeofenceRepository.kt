package com.example.geofencing

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.example.geofencing.ui.*

class GeofenceRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun getUserGeofences(): List<GeofenceData> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        val snapshot = firestore.collection("geofences")
            .whereEqualTo("owner", userId)
            .get()
            .await()

        return snapshot.documents.mapNotNull { it.toObject(GeofenceData::class.java) }
    }

    suspend fun saveGeofence(geofence: GeofenceData) {
        firestore.collection("geofences")
            .document(geofence.id)
            .set(geofence)
            .await()
    }

    suspend fun deleteGeofence(id: String) {
        firestore.collection("geofences").document(id).delete().await()
    }
    suspend fun getAllGeofences(): List<GeofenceData> {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("geofences")
            .get()
            .await()

        return snapshot.documents.mapNotNull { it.toObject(GeofenceData::class.java) }
    }

    suspend fun approveGeofence(id: String) {
        FirebaseFirestore.getInstance()
            .collection("geofences")
            .document(id)
            .update("approved", true)
            .await()
    }

}
