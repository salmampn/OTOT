package com.example.otot.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import java.util.Date

data class HistoryModel(
    var date: String = "",
    var distance: Double = 0.0,
    var avgPace: Double = 0.0,
    var movingTime: String = "00:00:00",
    var pathPoints: List<Map<String, Double>> = emptyList(),
    var timestamp: Timestamp = Timestamp.now(),
    var runId: String = ""
) {
    fun getPathPointsAsLatLng(): List<LatLng> {
        return pathPoints.mapNotNull { point ->
            val lat = point["lat"]
            val lng = point["lng"]
            if (lat != null && lng != null) LatLng(lat, lng) else null
        }
    }

    fun getTimestampAsDate(): Date {
        return timestamp.toDate()
    }
}