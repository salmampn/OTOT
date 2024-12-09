package com.example.otot

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.otot.model.HistoryModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class HomeFragment : Fragment() {

    private lateinit var userNameTextView: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var totalDistanceTextView: TextView
    private lateinit var averagePaceTextView: TextView
    private lateinit var movingTimeTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        userNameTextView = view.findViewById(R.id.user_name)
        auth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore
        totalDistanceTextView = view.findViewById(R.id.totalDistanceTextView)
        averagePaceTextView = view.findViewById(R.id.averagePaceTextView)
        movingTimeTextView = view.findViewById(R.id.movingTimeTextView)

        loadUserProfile()
        loadUserRuns()
        return view
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        // Load only the user's name
                        val name = document.getString("name")
                        userNameTextView.text = name ?: "No Name Available"
                    } else {
                        Toast.makeText(context, "No data found!", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Failed to load data: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
    }
    private fun loadUserRuns() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("runs").whereEqualTo("userId", user.uid).get() // Pastikan ada field userId
                .addOnSuccessListener { documents ->
                    var totalDistance = 0.0
                    var totalMovingTimeInSeconds = 0
                    var totalPace = 0.0
                    var count = 0
                    for (document in documents) {
                        val avgPace = document.getDouble("avgPace") ?: 0.0
                        val distance = document.getDouble("distance") ?: 0.0
                        val duration = document.getString("duration") ?: "00:00:00"
                        totalDistance += distance
                        totalMovingTimeInSeconds += duration.toSeconds()
                        totalPace += avgPace
                        count++
                    }
                    val averagePace = if (count > 0) totalPace / count else 0.0
                    totalDistanceTextView.text = "${String.format("%.2f", totalDistance)} km"
                    movingTimeTextView.text = "${secondsToTimeFormat(totalMovingTimeInSeconds)}"
                    averagePaceTextView.text = "${String.format("%.2f", averagePace)} min/km"
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Failed to load runs: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
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
}