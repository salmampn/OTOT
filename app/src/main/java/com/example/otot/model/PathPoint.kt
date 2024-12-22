package com.example.otot.model

import com.google.android.gms.maps.model.LatLng

data class PathPoint(
    val lat: Double,
    val lng: Double,
    var imageUrl: String? = null,
    var caption: String? = null
) {
    fun toLatLng(): LatLng {
        return LatLng(lat, lng)
    }
}
