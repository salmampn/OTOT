package com.example.otot

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import com.google.firebase.Timestamp
import java.util.*

class HistoryDetailFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var runId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve the runId from arguments
        runId = arguments?.getString("runId") ?: return

        // Initialize Firestore and Auth
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Initialize views
        val avgPaceTextView: TextView = view.findViewById(R.id.text_avg_pace_value)
        val distanceTextView: TextView = view.findViewById(R.id.text_distance_value)
        val durationTextView: TextView = view.findViewById(R.id.text_moving_time_value)
        val timestampTextView: TextView = view.findViewById(R.id.text_date)
        val caloriesTextView: TextView = view.findViewById(R.id.text_calories_value)
        val deleteButton: Button = view.findViewById(R.id.button_delete)
        mapView = view.findViewById(R.id.routeMapImage)

        // Load the history data from Firestore
        loadHistoryData(avgPaceTextView, distanceTextView, durationTextView, timestampTextView, caloriesTextView)

        // Set up delete button click listener
        deleteButton.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun loadHistoryData(
        avgPaceTextView: TextView,
        distanceTextView: TextView,
        durationTextView: TextView,
        timestampTextView: TextView,
        caloriesTextView: TextView
    ) {
        firestore.collection("runs").document(runId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val date = document.getDate("timestamp")?.toString() ?: "Unknown Date"
                    val distance = document.getDouble("distance") ?: 0.0
                    val avgPaceString = document.get("avgPace")?.toString() ?: "0.00/km"
                    val avgPace = try {
                        avgPaceString.replace(Regex("[^0-9.]"), "").toDouble()
                    } catch (e: NumberFormatException) {
                        0.0
                    }
                    val movingTime = document.getString("duration") ?: "00:00:00"
                    val timestamp = document.getTimestamp("timestamp") ?: Timestamp.now()
                    val calories = document.getDouble("calories") ?: 0.0
                    val pathPoints = document.get("pathPoints") as? List<Map<String, Double>> ?: emptyList()

                    // Set data to views
                    avgPaceTextView.text = String.format("%.2f min/km", avgPace)
                    distanceTextView.text = String.format("%.2f km", distance)
                    caloriesTextView.text = String.format("%.2f cal", calories)
                    durationTextView.text = movingTime
                    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                    timestampTextView.text = dateFormat.format(timestamp.toDate())

                    // Initialize the map
                    mapView.onCreate(null)
                    mapView.getMapAsync { map ->
                        drawPathOnMap(map, pathPoints)
                    }
                }
            }
            .addOnFailureListener { exception ->
                // Handle the error
            }
    }

    private fun drawPathOnMap(map: GoogleMap, pathPoints: List<Map<String, Double>>) {
        val latLngList = pathPoints.mapNotNull { point ->
            val lat = point["lat"]
            val lng = point["lng"]
            if (lat != null && lng != null) LatLng(lat, lng) else null
        }

        if (latLngList.isNotEmpty()) {
            val polylineOptions = PolylineOptions().color(Color.RED).width(5f)
            for (point in latLngList) {
                polylineOptions.add(point)
            }
            map.addPolyline(polylineOptions)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngList[0], 15f))
        }
    }

    override fun onMapReady(map: GoogleMap) {
        // This can be used if you want to do something when the map is ready
    }

    private fun showDeleteConfirmation() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_history_confirmation, null)
        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)

        val dialog = dialogBuilder.create()

        dialogView.findViewById<Button>(R.id.negative_button).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.positive_button).setOnClickListener {
            firestore.collection("runs").document(runId)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "History deleted", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    findNavController().navigate(R.id.action_historyDetailFragment_to_historyFragment)
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(requireContext(), "Error deleting history: ${exception.message}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
        }
        dialog.show()
        dialog.setCanceledOnTouchOutside(true) // Allow dialog to close when touching outside
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }
}