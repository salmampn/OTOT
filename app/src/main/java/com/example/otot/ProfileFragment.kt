package com.example.otot

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class ProfileFragment : Fragment() {

    private lateinit var btnLogout: LinearLayout
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var btnEditProfile: LinearLayout
    private lateinit var btnChangePassword: LinearLayout
    private lateinit var btnDeleteAccount: LinearLayout
    private lateinit var tvUsername: TextView
    private lateinit var profileImage: ImageView

    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        btnEditProfile = view.findViewById(R.id.edit_profile)
        btnLogout = view.findViewById(R.id.logout)
        tvUsername = view.findViewById(R.id.tv_username)
        btnChangePassword = view.findViewById(R.id.change_password)
        profileImage = view.findViewById(R.id.profile_image)
        btnDeleteAccount = view.findViewById(R.id.delete_account)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = Firebase.firestore

        loadUserProfile()

        btnLogout.setOnClickListener {
            // Inflate custom dialog layout
            val dialogView = layoutInflater.inflate(R.layout.dialog_logout_confirmation, null)

            // Create an AlertDialog to confirm logout
            val builder = AlertDialog.Builder(requireContext())
                .setView(dialogView) // Set the custom layout
                .setCancelable(false) // Prevent dialog from being canceled by tapping outside

            val dialog: AlertDialog = builder.create()

            dialogView.findViewById<TextView>(R.id.dialog_title).text
            dialogView.findViewById<TextView>(R.id.dialog_message).text

            dialogView.findViewById<Button>(R.id.positive_button).setOnClickListener {
                // User confirmed, proceed to log out
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(requireContext(), SplashActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                requireActivity().finish()
                dialog.dismiss() // Dismiss dialog after logout
            }

            dialogView.findViewById<Button>(R.id.negative_button).setOnClickListener {
                // User cancelled the logout, dismiss the dialog
                dialog.dismiss()
            }

            // Show the dialog
            dialog.show()
            dialog.setCanceledOnTouchOutside(true) // Allow dialog to close when touching outside
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
            dialog.window?.setGravity(Gravity.BOTTOM)
        }

        btnDeleteAccount.setOnClickListener {
            // Inflate custom dialog layout
            val dialogView = layoutInflater.inflate(R.layout.dialog_delete_account_confirmation, null)

            // Create an AlertDialog to confirm account deletion
            val builder = AlertDialog.Builder(requireContext())
                .setView(dialogView) // Set the custom layout
                .setCancelable(false) // Prevent dialog from being canceled by tapping outside

            val dialog: AlertDialog = builder.create()

            dialogView.findViewById<TextView>(R.id.dialog_title).text = "Delete your account?"
            dialogView.findViewById<TextView>(R.id.dialog_message).text = "We’ll miss you! Hope to see you again."

            dialogView.findViewById<Button>(R.id.positive_button).setOnClickListener {
                // User confirmed, proceed to delete account and related data
                val user = firebaseAuth.currentUser
                user?.let {
                    val uid = it.uid
                    // Step 1: Remove related data from Firestore
                    firestore.collection("users").document(uid).delete()
                        .addOnSuccessListener {
                            // User data deleted successfully
                            // Step 2: Delete user from Firebase Authentication
                            user.delete()
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        // Account deletion successful
                                        val intent = Intent(requireContext(), SplashActivity::class.java)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        requireActivity().finish()
                                    } else {
                                        // Handle failure in account deletion
                                        Log.e("DeleteAccount", "Error deleting account: ${task.exception?.message}")
                                    }
                                }
                        }
                        .addOnFailureListener { e ->
                            // Handle any errors in deleting user data
                            Log.e("DeleteAccount", "Error deleting user data: ${e.message}")
                        }
                }

                dialog.dismiss() // Dismiss dialog after operation
            }

            dialogView.findViewById<Button>(R.id.negative_button).setOnClickListener {
                // User cancelled the deletion, dismiss the dialog
                dialog.dismiss()
            }

            // Show the dialog
            dialog.show()
            dialog.setCanceledOnTouchOutside(true) // Allow dialog to close when touching outside
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
            dialog.window?.setGravity(Gravity.BOTTOM)
        }

        btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_editprofileFragment)
        }

        btnChangePassword.setOnClickListener{
            findNavController().navigate(R.id.action_profileFragment_to_changePasswordFragment)
        }

        return view
    }

    private fun loadUserProfile() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            firestore.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val username = document.getString("username")
                        val profileImageUrl = document.getString("profileImageUrl")

                        tvUsername.text = username ?: "No username found"

                        if (!profileImageUrl.isNullOrEmpty()) {
                            Glide.with(this)
                                .load(profileImageUrl)
                                .placeholder(R.drawable.circle_profile) // Add a placeholder image in your resources
                                .error(R.drawable.default_profile)  // Add an error image in your resources
                                .centerInside()
                                .circleCrop()
                                .into(profileImage)
                        } else {
                            // Set a default circular image or handle missing image case
                            profileImage.setImageResource(R.drawable.circle_profile)
                        }
                    } else {
                        tvUsername.text = "No data available"
                    }
                }
                .addOnFailureListener {
                    tvUsername.text = "Failed to load username"
                }
        } else {
            tvUsername.text = "User not authenticated"
        }
    }
}
