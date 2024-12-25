package com.example.otot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
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

    // Map to hold markers and their associated PathPoint data
    private val markerMap = mutableMapOf<Marker, PathPoint>()

    // Required empty public constructor
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

            // Set a click listener for the markers
            googleMap.setOnMarkerClickListener { clickedMarker ->
                markerMap[clickedMarker]?.let { pathPoint ->
                    // Show the image dialog with the correct image URL and caption
                    showImageDialog(pathPoint.imageUrl!!, pathPoint.caption)
                    true // Return true to indicate the event was handled
                } ?: false // Return false to allow default behavior
            }
        }

        btnContinue.setOnClickListener {
            findNavController().navigate(R.id.action_postRunningFragment_to_homeFragment)
        }

        return view
    }

    // Required empty public constructor
    private fun drawRunPath(runId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("history").document(runId).get().addOnSuccessListener { document ->
            if (document != null) {
                val pathPoints = document.get("pathPoints") as? List<Map<String, Any>> ?: emptyList()
                if (pathPoints.isNotEmpty()) {
                    val polylineOptions = PolylineOptions().color(Color.RED).width(5f)
                    val boundsBuilder = LatLngBounds.Builder()

                    for (point in pathPoints) {
                        val lat = point["lat"] as? Double
                        val lng = point["lng"] as? Double
                        val imageUrl = point["imageUrl"] as? String
                        val caption = point["caption"] as? String

                        if (lat != null && lng != null) {
                            val latLng = LatLng(lat, lng)
                            polylineOptions.add(latLng)
                            boundsBuilder.include(latLng)

                            // Add a marker for the uploaded image if imageUrl is not null
                            if (imageUrl != null) {
                                val pathPoint = PathPoint(lat, lng, imageUrl, caption)
                                createCustomMarker(imageUrl) { bitmapDescriptor ->
                                    // Ensure this runs on the main thread
                                    requireActivity().runOnUiThread {
                                        // Add the marker to the map and associate it with the PathPoint
                                        val marker = googleMap.addMarker(
                                            MarkerOptions()
                                                .position(latLng)
                                                .icon(bitmapDescriptor)
                                        )
                                        marker?.let {
                                            markerMap[it] = pathPoint // Associate marker with PathPoint
                                        }
                                    }
                                }
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

    // Create a custom marker with a circular image
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

    // Show the image dialog with the image URL and caption
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

    // Required empty public constructor
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    // Required empty public constructor
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    // Required empty public constructor
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    // Required empty public constructor
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}