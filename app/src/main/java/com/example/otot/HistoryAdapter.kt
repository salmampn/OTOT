package com.example.otot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.example.otot.model.HistoryModel
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
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val historyList: MutableList<HistoryModel>,
    private val onDeleteClick: (Int) -> Unit,
    private val navController: NavController
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avgPaceTextView: TextView = itemView.findViewById(R.id.text_avg_pace_value)
        private val distanceTextView: TextView = itemView.findViewById(R.id.text_distance_value)
        private val durationTextView: TextView = itemView.findViewById(R.id.text_moving_time_value)
        private val timestampTextView: TextView = itemView.findViewById(R.id.text_date)
        private val caloriesTextView: TextView = itemView.findViewById(R.id.text_calories_value)
        private val mapView: MapView = itemView.findViewById(R.id.routeMapImage)
        private val deleteButton: TextView = itemView.findViewById(R.id.button_delete)

        fun bind(history: HistoryModel, position: Int) {
            avgPaceTextView.text = String.format("%.2f min/km", history.avgPace)
            distanceTextView.text = String.format("%.2f km", history.distance)
            caloriesTextView.text = String.format("%.2f cal", history.calories)
            durationTextView.text = history.movingTime
            val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            timestampTextView.text = dateFormat.format(history.getTimestampAsDate())

            mapView.onCreate(null)
            mapView.getMapAsync { map ->
                map.uiSettings.setAllGesturesEnabled(true)
                drawPathOnMap(map, history.getPathPointsAsLatLng(), history)
            }
            mapView.onResume()
            deleteButton.setOnClickListener {
                onDeleteClick(position)
            }

            itemView.setOnClickListener {
                val bundle = Bundle().apply {
                    putString("runId", history.runId)
                }
                navController.navigate(R.id.action_historyFragment_to_historyDetailFragment, bundle)
            }
        }

        private fun drawPathOnMap(map: GoogleMap, pathPoints: List<LatLng>, history: HistoryModel) {
            if (pathPoints.isNotEmpty()) {
                val polylineOptions = PolylineOptions().color(Color.RED).width(5f)
                val boundsBuilder = LatLngBounds.Builder()
                for (point in pathPoints) {
                    polylineOptions.add(point)
                    boundsBuilder.include(point)
                }
                map.addPolyline(polylineOptions)
                val bounds = boundsBuilder.build()
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))

                // Add start marker
                val startLatLng = pathPoints.first()
                map.addMarker(
                    MarkerOptions()
                        .position(startLatLng)
                        .title("Start")
                        .icon(getMarkerIcon(Color.parseColor("#F2801F")))
                )

                // Add end marker
                val endLatLng = pathPoints.last()
                map.addMarker(
                    MarkerOptions()
                        .position(endLatLng)
                        .title("End")
                        .icon(getMarkerIcon(Color.parseColor("#D70000")))
                )

                // Add markers for uploaded images
                for (pathPoint in history.pathPoints) {
                    pathPoint.imageUrl?.let { imageUrl ->
                        createCustomMarker(imageUrl) { bitmapDescriptor ->
                            // Ensure marker is added on the main thread
                            itemView.post {
                                map.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(pathPoint.lat, pathPoint.lng))
                                        .icon(bitmapDescriptor) // Use the custom marker
                                )
                            }
                        }
                    }
                }
            }
        }

        private fun createCustomMarker(imageUrl: String, callback: (BitmapDescriptor) -> Unit) {
            val markerView = LayoutInflater.from(itemView.context).inflate(R.layout.custom_marker, null)
            val imageView = markerView.findViewById<ImageView>(R.id.marker_image)

            // Load the image using Glide
            Glide.with(itemView.context)
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

        private fun getMarkerIcon(color: Int): BitmapDescriptor {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            return BitmapDescriptorFactory.defaultMarker(hsv[0])
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_item, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyList[position], position)
    }

    override fun getItemCount(): Int = historyList.size

    fun removeItem(position: Int) {
        historyList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, historyList.size)
    }
}
