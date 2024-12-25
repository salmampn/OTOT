package com.example.otot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.example.otot.model.PathPoint
import com.example.otot.model.HistoryModel
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
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

    // Load history data from Firestore
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
                            val caption = it["caption"] as? String
                            if (lat != null && lng != null) {
                                PathPoint(lat, lng, imageUrl, caption)
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
                    drawPathOnMap(map, historyModel.getPathPointsAsLatLng(), historyModel.pathPoints)
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error loading history data: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Draw the path on the map
    private fun drawPathOnMap(map: GoogleMap, latLngList: List<LatLng>, pathPoints: List<PathPoint>) {
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

            // Create a map to hold markers and their associated data
            val markerMap = mutableMapOf<Marker, PathPoint>()

            // Add markers for uploaded images
            for (pathPoint in pathPoints) {
                pathPoint.imageUrl?.let { imageUrl ->
                    createCustomMarker(imageUrl) { bitmapDescriptor ->
                        // Ensure marker is added on the main thread
                        requireActivity().runOnUiThread {
                            val marker = map.addMarker(
                                MarkerOptions()
                                    .position(LatLng(pathPoint.lat, pathPoint.lng))
                                    .icon(bitmapDescriptor) // Use the custom marker
                            )

                            // Associate the marker with its PathPoint
                            marker?.let {
                                markerMap[it] = pathPoint
                            }
                        }
                    }
                }
            }

            // Set a single click listener for the map
            map.setOnMarkerClickListener { clickedMarker ->
                markerMap[clickedMarker]?.let { pathPoint ->
                    // Show the image dialog with the correct image URL and caption
                    showImageDialog(pathPoint.imageUrl!!, pathPoint.caption)
                    true // Return true to indicate the event was handled
                } ?: false // Return false to allow default behavior
            }
        }
    }

    // Create a custom marker using a layout with an ImageView
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
                    callback(BitmapDescriptorFactory.fromBitmap(bitmap))
                    return true
                }
            })
            .submit() // Ensure the image is loaded
    }

    // Get a marker icon with a specific color
    private fun getMarkerIcon(color: Int): BitmapDescriptor {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return BitmapDescriptorFactory.defaultMarker(hsv[0])
    }

    // Show the image dialog with the specified image URL and caption
    private fun showImageDialog(imageUrl: String, caption: String?) {
        // Disable map interaction
        mapView.isEnabled = false

        val dialogView = layoutInflater.inflate(R.layout.dialog_image_view, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val captionTextView: TextView = dialogView.findViewById(R.id.caption)
        val imageView: ImageView = dialogView.findViewById(R.id.image_view)

        // Load the image from the URL using Glide
        Glide.with(requireContext())
            .load(imageUrl)
            .into(imageView)

        // Set the caption text
        captionTextView.text = caption ?: "Checkpoint" // Default text if caption is null

        dialog.setOnDismissListener {
            // Re-enable map interaction when the dialog is dismissed
            mapView.isEnabled = true
        }

        dialog.show()
        dialog.setCanceledOnTouchOutside(true) // Allow dialog to close when touching outside
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    // Show a confirmation dialog before deleting the history
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

    // Lifecycle methods for the MapView
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    // Lifecycle methods for the MapView
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    // Lifecycle methods for the MapView
    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }

    // Lifecycle methods for the MapView
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    // Lifecycle methods for the MapView
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}