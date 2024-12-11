package com.example.otot

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class HomeFragment : Fragment() {

    private lateinit var userNameTextView: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var totalDistanceTextView: TextView
    private lateinit var movingTimeTextView: TextView
    private lateinit var caloriesTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        userNameTextView = view.findViewById(R.id.user_name)
        totalDistanceTextView = view.findViewById(R.id.totalDistanceTextView)
        movingTimeTextView = view.findViewById(R.id.movingTimeTextView)
        caloriesTextView = view.findViewById(R.id.caloriesTextView)
        auth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore

        loadUserProfile()
        loadUserRuns()
        loadLastActivities()

        return view
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val name = document.getString("name")
                        userNameTextView.text = name ?: "No Name Available"
                    } else {
                        showToast("No data found!")
                    }
                }
                .addOnFailureListener { exception ->
                    showToast("Failed to load data: ${exception.message}")
                }
        } ?: showToast("User not authenticated")
    }

    private fun loadUserRuns() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("runs").whereEqualTo("userId", user.uid).get()
                .addOnSuccessListener { documents ->
                    var totalDistance = 0.0
                    var totalMovingTimeInSeconds = 0
                    var totalCalories = 0.0

                    for (document in documents) {
                        val distance = document.getDouble("distance") ?: 0.0
                        val duration = document.getString("duration") ?: "00:00:00"
                        val calories = document.getDouble("calories") ?: 0.0

                        totalDistance += distance
                        totalMovingTimeInSeconds += duration.toSeconds()
                        totalCalories += calories
                    }

                    // Update UI
                    totalDistanceTextView.text = "${String.format("%.2f", totalDistance)} km"
                    movingTimeTextView.text = secondsToTimeFormat(totalMovingTimeInSeconds)
                    caloriesTextView.text = "${String.format("%.2f", totalCalories)} cal"
                }
                .addOnFailureListener { exception ->
                    showToast("Failed to load runs: ${exception.message}")
                }
        }
    }

    private fun loadLastActivities() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("runs")
                .whereEqualTo("userId", user.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener { documents ->
                    val lastActivities = documents.map { document ->
                        val distance = document.getDouble("distance") ?: 0.0
                        val duration = document.getString("duration") ?: "00:00:00"
                        LastActivity(distance, duration)
                    }
                    updateLastActivitiesUI(lastActivities)
                }
                .addOnFailureListener { exception ->
                    showToast("Failed to load last activities: ${exception.message}")
                    Log.e("HomeFragment", "Error loading last activities", exception)
                }
        }
    }

    private fun updateLastActivitiesUI(lastActivities: List<LastActivity>) {
        val lastActivityContainer = view?.findViewById<LinearLayout>(R.id.last_activity_container)
        lastActivityContainer?.removeAllViews()

        lastActivities.forEach { activity ->
            val activityView = LayoutInflater.from(context).inflate(R.layout.item_last_activity, lastActivityContainer, false)
            val distanceTextView = activityView.findViewById<TextView>(R.id.distanceTextView)
            val durationTextView = activityView.findViewById<TextView>(R.id.durationTextView)

            distanceTextView.text = "${String.format("%.2f", activity.distance)} km"
            durationTextView.text = activity.duration

            lastActivityContainer?.addView(activityView)
        }
    }

    private fun String.toSeconds(): Int {
        val parts = this.split(":").map { it.toInt() }
        return parts[0] * 3600 + parts[1] * 60 + parts[2]
    }

    private fun secondsToTimeFormat(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    data class LastActivity(val distance: Double, val duration: String)
}