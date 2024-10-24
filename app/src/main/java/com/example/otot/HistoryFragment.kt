package com.example.otot

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.otot.model.HistoryModel

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)

        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val historyList = getDummyData()

        historyAdapter = HistoryAdapter(historyList)
        recyclerView.adapter = historyAdapter

        return view
    }

    private fun getDummyData(): List<HistoryModel> {
        return listOf(
            HistoryModel(
                dateAndLocation = "26 April 2024 | Seattle, USA",
                distance = "18.5 km",
                steps = "7,389",
                movingTime = "1:17:05",
                avgPace = "3:19/km",
                calories = "322 Kcal",
                avgHeartRate = "132 bpm",
                routeImage = R.drawable.map1
            ),
            HistoryModel(
                dateAndLocation = "12 Mei 2024 | New York, USA",
                distance = "10.3 km",
                steps = "5,672",
                movingTime = "54:32",
                avgPace = "5:17/km",
                calories = "210 Kcal",
                avgHeartRate = "125 bpm",
                routeImage = R.drawable.map1
            ),
            HistoryModel(
                dateAndLocation = "3 Juni 2024 | Tokyo, Japan",
                distance = "12.1 km",
                steps = "6,891",
                movingTime = "1:03:22",
                avgPace = "4:15/km",
                calories = "280 Kcal",
                avgHeartRate = "130 bpm",
                routeImage = R.drawable.map1
            )
        )
    }
}
