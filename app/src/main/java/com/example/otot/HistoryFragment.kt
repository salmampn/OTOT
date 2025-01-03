package com.example.otot

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.otot.model.HistoryModel
import com.example.otot.model.PathPoint
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class HistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var firestore: FirebaseFirestore
    private val historyList = mutableListOf<HistoryModel>()
    private lateinit var auth: FirebaseAuth
    private lateinit var emptyView: View
    private lateinit var navController: NavController

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
        navController = findNavController()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            redirectToSplash()
            return
        }

        recyclerView = view.findViewById(R.id.recyclerViewHistory)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        emptyView = view.findViewById(R.id.empty_view)
        historyAdapter = HistoryAdapter(historyList, { position ->
            showDeleteConfirmationHistory(position)
        }, navController)

        recyclerView.adapter = historyAdapter
        loadHistory()
    }

    // Redirect to SplashActivity if user is not logged in
    private fun redirectToSplash() {
        val intent = Intent(requireContext(), SplashActivity::class.java)
        startActivity(intent)
        activity?.finish()
    }

    // Load history data from Firestore
    private fun loadHistory() {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("history")
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
                        val calories = document.getDouble("calories") ?: 0.0
                        val runId = document.id
                        val pathPoints = document.get("pathPoints") as? List<Map<String, Any>> ?: emptyList()

                        // Convert pathPoints to PathPoint objects
                        val pathPointList = pathPoints.mapNotNull {
                            val lat = it["lat"] as? Double
                            val lng = it["lng"] as? Double
                            val imageUrl = it["imageUrl"] as? String
                            if (lat != null && lng != null) {
                                PathPoint(lat, lng, imageUrl)
                            } else {
                                null // Skip invalid points
                            }
                        }

                        historyList.add(
                            HistoryModel(
                                date = date,
                                avgPace = avgPace,
                                distance = distance,
                                movingTime = movingTime,
                                pathPoints = pathPointList,
                                timestamp = timestamp,
                                calories = calories,
                                runId = runId
                            )
                        )
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Data error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                // Sort the historyList in descending order based on timestamp
                historyList.sortByDescending { it.timestamp }

                updateUI()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Update UI based on historyList
    private fun updateUI() {
        if (historyList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            historyAdapter.notifyDataSetChanged()
        }
    }

    // Show delete confirmation dialog
    private fun showDeleteConfirmationHistory(position: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_history_confirmation, null)
        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)

        val dialog = dialogBuilder.create()

        dialogView.findViewById<Button>(R.id.negative_button).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.positive_button).setOnClickListener {
            val itemToDelete = historyList[position]
            firestore.collection("history").document(itemToDelete.runId)
                .delete()
                .addOnSuccessListener {
                    historyAdapter.removeItem(position)
                    updateUI()
                    Toast.makeText(requireContext(), "History deleted", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(requireContext(), "Error deleting Data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
        }
        dialog.show()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}