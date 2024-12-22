package com.example.otot

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.firestore.FirebaseFirestore

class PostRunningFragment : Fragment() {
    private lateinit var btnContinue: Button
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var distanceValue: TextView
    private lateinit var avgPaceValue: TextView
    private lateinit var movingTimeValue: TextView
    private lateinit var caloriesValue: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_post_running, container, false)
        mapView = view.findViewById(R.id.routeMapImage)
        btnContinue = view.findViewById(R.id.continueButton)
        distanceValue = view.findViewById(R.id.distanceValue)
        avgPaceValue = view.findViewById(R.id.avgPaceValue)
        movingTimeValue = view.findViewById(R.id.movingTimeValue)
        caloriesValue = view.findViewById(R.id.caloriesValue)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            googleMap = map
            googleMap.uiSettings.setAllGesturesEnabled(true)
            googleMap.uiSettings.setZoomGesturesEnabled(false)
            val runId = arguments?.getString("runId") ?: return@getMapAsync
            drawRunPath(runId)
        }

        btnContinue.setOnClickListener {
            findNavController().navigate(R.id.action_postRunningFragment_to_homeFragment)
        }

        return view
    }

    private fun drawRunPath(runId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("history").document(runId).get().addOnSuccessListener { document ->
            if (document != null) {
                val pathPoints = document.get("pathPoints") as? List<Map<String, Double>> ?: emptyList()
                if (pathPoints.isNotEmpty()) {
                    val polylineOptions = PolylineOptions().color(Color.RED).width(5f)
                    val boundsBuilder = LatLngBounds.Builder()

                    for (point in pathPoints) {
                        val lat = point["lat"] as? Double
                        val lng = point["lng"] as? Double
                        val imageUrl = point["imageUrl"] as? String // Get the imageUrl
                        val caption = point["caption"] as? String // Get the caption

                        if (lat != null && lng != null) {
                            val latLng = LatLng(lat, lng)
                            polylineOptions.add(latLng)
                            boundsBuilder.include(latLng)

                            // Add a marker for the uploaded image if imageUrl is not null
                            if (imageUrl != null) {
                                val markerTitle = if (caption.isNullOrEmpty()) {
                                    "Checkpoint"
                                } else {
                                    caption
                                } // Use the caption as the marker title if available
                                googleMap.addMarker(
                                    MarkerOptions()
                                        .position(latLng)
                                        .title(markerTitle)
                                        .icon(getMarkerIcon(Color.parseColor("#FFD93D"))) // Use a different color for image markers
                                )
                            }
                        }
                    }

                    if (polylineOptions.points.isNotEmpty()) {
                        googleMap.addPolyline(polylineOptions)
                        val bounds = boundsBuilder.build()
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))

                        // Add start marker
                        val startLatLng = polylineOptions.points.first()
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(startLatLng)
                                .title("Start")
                                .icon(getMarkerIcon(Color.parseColor("#F2801F")))
                        )

                        // Add end marker
                        val endLatLng = polylineOptions.points.last()
                        googleMap.addMarker(
                            MarkerOptions()
                                .position(endLatLng)
                                .title("End")
                                .icon(getMarkerIcon(Color.parseColor("#D70000")))
                        )
                    }
                }

                // Set other data
                val distance = document.getDouble("distance") ?: 0.0
                val avgPaceStr = document.get("avgPace")?.toString() ?: "0.00"
                val movingTime = document.getString("duration") ?: "00:00:00"
                val calories = document.getDouble("calories") ?: 0.0

                val avgPace = try {
                    avgPaceStr.toDouble()
                } catch (e: NumberFormatException) {
                    0.0
                }
                distanceValue.text = String.format("%.1f km", distance)
                caloriesValue.text = String.format("%.1f cal", calories)
                avgPaceValue.text = String.format("%.1f min/km", avgPace)
                movingTimeValue.text = movingTime
            }
        }.addOnFailureListener { exception ->
            Log.e("drawRunPath", "Error fetching document: ${exception.message}")
            Toast.makeText(requireContext(), "Error fetching document: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMarkerIcon(color: Int): BitmapDescriptor {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return BitmapDescriptorFactory.defaultMarker(hsv[0])
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

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}