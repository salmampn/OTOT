package com.example.otot.model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import java.util.Date

data class HistoryModel(
    var date: String = "",
    var distance: Double = 0.0,
    var avgPace: Double = 0.0,
    var movingTime: String = "00:00:00",
    var pathPoints: List<PathPoint> = emptyList(), // Change here
    var timestamp: Timestamp = Timestamp.now(),
    var calories: Double = 0.0,
    var runId: String = ""
) {
    fun getPathPointsAsLatLng(): List<LatLng> {
        return pathPoints.map { it.toLatLng() } // Use the new PathPoint class
    }

    fun getTimestampAsDate(): Date {
        return timestamp.toDate()
    }
}