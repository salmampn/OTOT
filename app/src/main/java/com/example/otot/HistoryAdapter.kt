package com.example.otot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.otot.model.HistoryModel

class HistoryAdapter(private val historyList: List<HistoryModel>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateAndLocation: TextView = view.findViewById(R.id.text_date_location)
        val distance: TextView = view.findViewById(R.id.text_distance)
        val steps: TextView = view.findViewById(R.id.text_steps)
        val movingTime: TextView = view.findViewById(R.id.text_moving_time)
        val avgPace: TextView = view.findViewById(R.id.text_avg_pace)
        val calories: TextView = view.findViewById(R.id.text_calories)
        val avgHeartRate: TextView = view.findViewById(R.id.text_avg_heart_rate)
        val routeImage: ImageView = view.findViewById(R.id.image_route_map)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.history_item, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val historyItem = historyList[position]
        holder.dateAndLocation.text = historyItem.dateAndLocation
        holder.distance.text = historyItem.distance
        holder.steps.text = historyItem.steps
        holder.movingTime.text = historyItem.movingTime
        holder.avgPace.text = historyItem.avgPace
        holder.calories.text = historyItem.calories
        holder.avgHeartRate.text = historyItem.avgHeartRate
        holder.routeImage.setImageResource(historyItem.routeImage)
    }

    override fun getItemCount(): Int {
        return historyList.size
    }
}
