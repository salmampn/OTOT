package com.example.otot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.example.otot.model.PathPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var userNameTextView: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var movingTimeTextView: TextView
    private lateinit var caloriesTextView: TextView
    private lateinit var totalDistanceTextView: TextView
    private lateinit var lastActivityContainer: LinearLayout
    private lateinit var lastActivityBtn: CardView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        userNameTextView = view.findViewById(R.id.user_name)
        movingTimeTextView = view.findViewById(R.id.movingTimeTextView)
        caloriesTextView = view.findViewById(R.id.caloriesTextView)
        totalDistanceTextView = view.findViewById(R.id.totalDistanceTextView)
        lastActivityContainer = view.findViewById(R.id.last_activity_container)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        lastActivityBtn = view.findViewById(R.id.last_activity_card)

        lastActivityBtn.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_historyFragment)
        }

        loadUserProfile()
        loadUserHistory()
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

    private fun loadUserHistory() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("history").whereEqualTo("userId", user.uid).get()
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
                    showToast("Failed to load history: ${exception.message}")
                }
        }
    }

    private fun loadLastActivities() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("history")
                .whereEqualTo("userId", user.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(3)
                .get()
                .addOnSuccessListener { documents ->
                    val lastActivities = documents.map { document ->
                        val distance = document.getDouble("distance") ?: 0.0
                        val duration = document.getString("duration") ?: "00:00:00"
                        val timestamp = document.get("timestamp")
                        val timeInMillis = if (timestamp is Timestamp) {
                            timestamp.toDate().time
                        } else {
                            0L
                        }
                        val pathPoints = (document.get("pathPoints") as? List<Map<String, Any>> ?: emptyList()).mapNotNull {
                            val lat = it["lat"] as? Double
                            val lng = it["lng"] as? Double
                            val imageUrl = it["imageUrl"] as? String
                            val caption = it["caption"] as? String
                            if (lat != null && lng != null) {
                                PathPoint(lat, lng, imageUrl, caption)
                            } else {
                                null // Skip invalid points
                            }
                        }

                        LastActivity(distance, duration, timeInMillis, pathPoints)
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
        lastActivityContainer.removeAllViews()

        lastActivities.forEach { activity ->
            val activityView = LayoutInflater.from(context)
                .inflate(R.layout.item_last_activity, lastActivityContainer, false)

            // Set the date and duration
            val dateTextView = activityView.findViewById<TextView>(R.id.text_date)
            val durationTextView = activityView.findViewById<TextView>(R.id.text_moving_time_value)
            val distanceTextView = activityView.findViewById<TextView>(R.id.text_distance_value)
            val mapView = activityView.findViewById<MapView>(R.id.routeMapImage)

            // Format the date from the timestamp
            dateTextView.text = formatDate(activity.timestamp)
            durationTextView.text = activity.duration
            distanceTextView.text = "${String.format("%.2f", activity.distance)} km"

            // Initialize the MapView
            mapView.onCreate(null)
            mapView.getMapAsync { googleMap ->
                googleMap.uiSettings.setAllGesturesEnabled(true) // Enable all gestures
                drawPathOnMap(googleMap, activity.pathPoints)
            }

            lastActivityContainer.addView(activityView)
        }
    }


    private fun drawPathOnMap(map: GoogleMap, pathPoints: List<PathPoint>) {
        val latLngList = pathPoints.map { LatLng(it.lat, it.lng) }

        if (latLngList.isNotEmpty()) {
            val polylineOptions = PolylineOptions().color(Color.RED).width(5f)
            for (point in latLngList) {
                polylineOptions.add(point)
            }
            map.addPolyline(polylineOptions)

            // Create bounds to include all points
            val boundsBuilder = LatLngBounds.Builder()
            latLngList.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()

            // Move the camera to fit the bounds
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))

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

            // Add markers for each path point
            for (pathPoint in pathPoints) {
                pathPoint.imageUrl?.let { imageUrl ->
                    createCustomMarker(imageUrl) { bitmapDescriptor ->
                        // Add the custom marker at the path point
                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(pathPoint.lat, pathPoint.lng))
                                .icon(bitmapDescriptor)
                        )
                    }
                }
            }
        }
    }

    private fun createCustomMarker(imageUrl: String, callback: (BitmapDescriptor) -> Unit) {
        val markerView = LayoutInflater.from(requireContext()).inflate(R.layout.custom_marker, null)
        val imageView = markerView.findViewById<ImageView>(R.id.marker_image)

        // Load the image using Glide
        Glide.with(requireContext())
            .load(imageUrl)
            .apply(RequestOptions.circleCropTransform()) // Apply circular crop
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    // Handle the error (optional)
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    // Set the loaded image into the ImageView
                    imageView.setImageDrawable(resource)

                    // Create a bitmap from the marker view
                    markerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                    markerView.layout(0, 0, markerView.measuredWidth, markerView.measuredHeight)
                    val bitmap = Bitmap.createBitmap(markerView.measuredWidth, markerView.measuredHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    markerView.draw(canvas)

                    // Call the callback with the created bitmap descriptor
                    val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap)

                    // Ensure the marker is added on the main thread
                    requireActivity().runOnUiThread {
                        callback(bitmapDescriptor)
                    }
                    return true
                }
            })
            .submit() // Ensure the image is loaded
    }

    private fun getMarkerIcon(color: Int): BitmapDescriptor {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return BitmapDescriptorFactory.defaultMarker(hsv[0])
    }

    private fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
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

    data class LastActivity(
        val distance: Double,
        val duration: String,
        val timestamp: Long,
        val pathPoints: List<PathPoint> // Change this to hold PathPoint objects
    )
}