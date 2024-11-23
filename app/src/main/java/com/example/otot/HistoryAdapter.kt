package com.example.otot

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

class HistoryAdapter(private val historyList: List<HistoryModel>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avgPaceTextView: TextView = itemView.findViewById(R.id.text_avg_pace_value)
        private val distanceTextView: TextView = itemView.findViewById(R.id.text_distance_value)
        private val durationTextView: TextView = itemView.findViewById(R.id.text_moving_time_value)
        private val timestampTextView: TextView = itemView.findViewById(R.id.text_date)
        private val mapView: MapView = itemView.findViewById(R.id.routeMapImage)

        fun bind(history: HistoryModel) {
            avgPaceTextView.text = String.format("%.2f min/km", history.avgPace)
            distanceTextView.text = String.format("%.2f km", history.distance)
            durationTextView.text = history.movingTime
            val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            timestampTextView.text = dateFormat.format(history.getTimestampAsDate())

            // MapView initialization and map drawing
            mapView.onCreate(null)
            mapView.getMapAsync { map ->
                map.uiSettings.setAllGesturesEnabled(false)
                drawPathOnMap(map, history.getPathPointsAsLatLng())
            }
            mapView.onResume()
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
        holder.bind(historyList[position])
    }

    override fun getItemCount(): Int = historyList.size

    fun handleRecyclerViewLifecycle(event: String) {
        for (i in 0 until itemCount) {
            (historyList[i] as? HistoryViewHolder)?.handleMapLifecycle(event)
        }
    }
}