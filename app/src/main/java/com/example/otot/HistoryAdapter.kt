package com.example.otot

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import com.example.otot.model.HistoryModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val historyList: MutableList<HistoryModel>,
    private val onDeleteClick: (Int) -> Unit,
    private val navController: NavController // Add NavController parameter
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
                map.uiSettings.setAllGesturesEnabled(false)
                drawPathOnMap(map, history.getPathPointsAsLatLng())
            }
            mapView.onResume()
            deleteButton.setOnClickListener {
                onDeleteClick(position)
            }

            itemView.setOnClickListener {
                try {
                    // Create a bundle to pass the runId
                    val bundle = Bundle().apply {
                        putString("runId", history.runId) // Pass only the runId
                    }
                    // Use NavController to navigate to HistoryDetailFragment
                    navController.navigate(R.id.action_historyFragment_to_historyDetailFragment, bundle)
                } catch (e: Exception) {
                    Log.e("HistoryAdapter", "Navigation error: ${e.message}")
                }
            }
        }

        private fun drawPathOnMap(map: GoogleMap, pathPoints: List<LatLng>) {
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
            }
        }

        fun handleMapLifecycle(event: String) {
            when (event) {
                "resume" -> mapView.onResume()
                "pause" -> mapView.onPause()
                "destroy" -> mapView.onDestroy()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_item, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyList[position], position) // Pass position to bind
    }

    override fun getItemCount(): Int = historyList.size

    fun handleRecyclerViewLifecycle(event: String) {
        for (i in 0 until itemCount) {
            (historyList[i] as? HistoryViewHolder)?.handleMapLifecycle(event)
        }
    }

    fun removeItem(position: Int) {
        historyList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, historyList.size)
    }
}
