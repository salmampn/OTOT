package com.example.otot

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import com.example.otot.model.PathPoint
import com.example.otot.model.HistoryModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class HistoryDetailFragment : Fragment() {

    private lateinit var mapView: MapView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var runId: String

    // UI Elements
    private lateinit var avgPaceTextView: TextView
    private lateinit var distanceTextView: TextView
    private lateinit var durationTextView: TextView
    private lateinit var timestampTextView: TextView
    private lateinit var caloriesTextView: TextView
    private lateinit var deleteButton: Button

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
        avgPaceTextView = view.findViewById(R.id.text_avg_pace_value)
        distanceTextView = view.findViewById(R.id.text_distance_value)
        durationTextView = view.findViewById(R.id.text_moving_time_value)
        timestampTextView = view.findViewById(R.id.text_date)
        caloriesTextView = view.findViewById(R.id.text_calories_value)
        deleteButton = view.findViewById(R.id.button_delete)
        mapView = view.findViewById(R.id.routeMapImage)

        // Initialize the map
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            // Load history data
            loadHistoryData(map)
        }

        // Set up delete button click listener
        deleteButton.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun loadHistoryData(map: GoogleMap) {
        firestore.collection("history").document(runId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val historyModel = HistoryModel(
                        date = document.getDate("timestamp")?.toString() ?: "Unknown Date",
                        distance = document.getDouble("distance") ?: 0.0,
                        avgPace = document.get("avgPace")?.toString()?.replace(Regex("[^0-9.]"), "")?.toDouble() ?: 0.0,
                        movingTime = document.getString("duration") ?: "00:00:00",
                        timestamp = document.getTimestamp("timestamp") ?: Timestamp.now(),
                        calories = document.getDouble("calories") ?: 0.0,
                        runId = document.id,
                        pathPoints = (document.get("pathPoints") as? List<Map<String, Any>> ?: emptyList()).mapNotNull {
                            val lat = it["lat"] as? Double
                            val lng = it["lng"] as? Double
                            val imageUrl = it["imageUrl"] as? String
                            if (lat != null && lng != null) {
                                PathPoint(lat, lng, imageUrl)
                            } else {
                                null // Skip invalid points
                            }
                        }
                    )

                    // Set data to views
                    avgPaceTextView.text = String.format("%.2f min/km", historyModel.avgPace)
                    distanceTextView.text = String.format("%.2f km", historyModel.distance)
                    caloriesTextView.text = String.format("%.2f cal", historyModel.calories)
                    durationTextView.text = historyModel.movingTime
                    val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                    timestampTextView.text = dateFormat.format(historyModel.getTimestampAsDate())

                    // Draw path on the map using the pathPoints from HistoryModel
                    drawPathOnMap(map, historyModel.getPathPointsAsLatLng())
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error loading history data: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun drawPathOnMap(map: GoogleMap, latLngList: List<LatLng>) {
        if (latLngList.isNotEmpty()) {
            val polylineOptions = PolylineOptions().color(Color.RED).width(5f)
            for (point in latLngList) {
                polylineOptions.add(point)
            }
            map.addPolyline(polylineOptions)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngList[0], 15f))

            // Add start marker
            map.addMarker(
                MarkerOptions()
                    .position(latLngList.first())
                    .title("Start")
                    .icon(getMarkerIcon(Color.parseColor("#F2801F")))
            )

            // Add end marker
            map.addMarker(
                MarkerOptions()
                    .position(latLngList.last())
                    .title("End")
                    .icon(getMarkerIcon(Color.parseColor("#D70000")))
            )
        }
    }

    private fun getMarkerIcon(color: Int): BitmapDescriptor {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return BitmapDescriptorFactory.defaultMarker(hsv[0])
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
            firestore.collection("history").document(runId)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "History deleted", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    findNavController().navigate(R.id.action_historyDetailFragment_to_historyFragment)
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(
                        requireContext(),
                        "Error deleting history: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}