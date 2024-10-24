package com.example.otot.model

data class HistoryModel(
    val dateAndLocation: String,
    val distance: String,
    val steps: String,
    val movingTime: String,
    val avgPace: String,
    val calories: String,
    val avgHeartRate: String,
    val routeImage: Int
)