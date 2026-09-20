package com.example.geofencing.ui

data class GeofenceData(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Float = 100f,
    val owner: String = "", // 🔹 add this line
    val isInside: Boolean = false,
    val approved: Boolean = false


)
