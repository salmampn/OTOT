package com.example.otot

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.otot.model.HistoryModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var firestore: FirebaseFirestore
    private val historyList = mutableListOf<HistoryModel>()
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        val currentUser = auth.currentUser
        if (currentUser == null) {
            redirectToSplash()
            return
        }
        recyclerView = view.findViewById(R.id.recyclerViewHistory)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        historyAdapter = HistoryAdapter(historyList)
        recyclerView.adapter = historyAdapter
        loadHistory()
    }

    private fun redirectToSplash() {
        val intent = Intent(requireContext(), SplashActivity::class.java)
        startActivity(intent)
        activity?.finish()
    }

    private fun loadHistory() {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("runs")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { result ->
                historyList.clear()
                for (document in result) {
                    try {
                        val date = document.getDate("timestamp")?.toString() ?: "Unknown Date"
                        val distance = document.getDouble("distance") ?: 0.0

                        val avgPaceString = document.get("avgPace")?.toString() ?: "0.00/km"
                        val avgPace = try {
                            avgPaceString.replace(Regex("[^0-9.]"), "").toDouble()
                        } catch (e: NumberFormatException) {
                            0.0
                        }
                        val movingTime = document.getString("duration") ?: "00:00:00"
                        val timestamp = document.getTimestamp("timestamp") ?: Timestamp.now()
                        val runId = document.id
                        val pathPoints = document.get("pathPoints") as? List<Map<String, Double>> ?: emptyList()

                        historyList.add(
                            HistoryModel(
                                date = date,
                                avgPace = avgPace,
                                distance = distance,
                                movingTime = movingTime,
                                pathPoints = pathPoints,
                                timestamp = timestamp,
                                runId = runId
                            )
                        )
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Data error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                historyAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }



    override fun onResume() {
        super.onResume()
        historyAdapter.handleRecyclerViewLifecycle("resume")
    }

    override fun onPause() {
        super.onPause()
        historyAdapter.handleRecyclerViewLifecycle("pause")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        historyAdapter.handleRecyclerViewLifecycle("destroy")
    }
}
