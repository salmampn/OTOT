package com.example.otot

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class HomeFragment : Fragment() {

    private lateinit var userNameTextView: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        userNameTextView = view.findViewById(R.id.user_name)
        auth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore

        loadUserProfile()

        return view
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        // Load only the user's name
                        val name = document.getString("name")
                        userNameTextView.text = name ?: "No Name Available"
                    } else {
                        Toast.makeText(context, "No data found!", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Failed to load data: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
    }
}